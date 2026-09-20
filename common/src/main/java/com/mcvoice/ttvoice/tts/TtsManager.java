package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.McVoiceConstants;
import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.VoiceChatBridge;
import com.mcvoice.ttvoice.plasmo.PlasmoVoiceBridge;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class TtsManager {
    private static final int MAX_PENDING_SPEECH = 8;
    private static final int MAX_TEXT_CHUNK = 200;

    private static final Queue<short[]> SVC_QUEUE = new ArrayDeque<>();
    private static final AtomicBoolean SPEAKING = new AtomicBoolean(false);
    private static final AtomicLong RUN_ID = new AtomicLong();
    private static SourceDataLine localLine;
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(MAX_PENDING_SPEECH),
        r -> {
            Thread thread = new Thread(r, "MCVoice-TTS");
            thread.setDaemon(true);
            return thread;
        },
        (runnable, executor) -> {
            // 积压过多时丢掉最旧的请求，避免慢速网络下任务无限堆积。
            if (!executor.isShutdown()) {
                executor.getQueue().poll();
                executor.execute(runnable);
            }
        }
    );

    private static volatile TtsEngine engine;
    private static volatile Voice activeVoice;
    private static boolean externalMode;

    private TtsManager() {
    }

    public static void init() {
    }

    public static void onWorldJoin() {
        PlasmoVoiceBridge.onWorldJoin();
        McVoiceConstants.LOGGER.info("MCvoice joined the world");
    }

    public static void onWorldLeave() {
        PlasmoVoiceBridge.onWorldLeave();
        stop();
    }

    public static void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        long runId = RUN_ID.get();
        WORKER.execute(() -> speakTask(runId, text));
    }

    private static void speakTask(long runId, String text) {
        try {
            ensureEngine();
            List<String> parts = splitText(text);
            for (String part : parts) {
                if (RUN_ID.get() != runId) {
                    return;
                }
                short[] pcm = engine.synthesize(part);
                if (RUN_ID.get() != runId) {
                    return;
                }
                play(pcm);
            }
        } catch (Exception e) {
            McVoiceConstants.LOGGER.error("TTS synthesis failed", e);
        }
    }

    /** 超长文本按句子边界分段，避免一次解码整段音频造成过高的内存峰值。 */
    private static List<String> splitText(String text) {
        List<String> parts = new ArrayList<>();
        String remaining = text.trim();
        while (remaining.length() > MAX_TEXT_CHUNK) {
            int cut = findChunkCut(remaining);
            parts.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).trim();
        }
        if (!remaining.isEmpty()) {
            parts.add(remaining);
        }
        return parts;
    }

    private static int findChunkCut(String text) {
        int cut = MAX_TEXT_CHUNK;
        for (int i = MAX_TEXT_CHUNK; i > 4; i--) {
            char c = text.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '，' || c == '、'
                    || c == '.' || c == '!' || c == '?' || c == ';' || c == ',' || c == '\n'
                    || Character.isWhitespace(c)) {
                cut = i;
                break;
            }
        }
        if (cut < text.length() && Character.isLowSurrogate(text.charAt(cut))) {
            cut--;
        } else if (cut > 0 && Character.isHighSurrogate(text.charAt(cut - 1))) {
            cut--;
        }
        return Math.max(1, cut);
    }

    public static void test() {
        speak("你好，这是 MCvoice 的本地中文测试。");
    }

    public static void stop() {
        RUN_ID.incrementAndGet();
        PlasmoVoiceBridge.stop();
        synchronized (SVC_QUEUE) {
            SVC_QUEUE.clear();
        }
        SPEAKING.set(false);
    }

    public static boolean isSpeaking() {
        return SPEAKING.get();
    }

    public static void shutdown() {
        stop();
        WORKER.shutdownNow();
        closeEngine();
    }

    public static short[] nextSvcFrame() {
        synchronized (SVC_QUEUE) {
            return SVC_QUEUE.poll();
        }
    }

    private static synchronized void ensureEngine() throws Exception {
        if (ModConfig.get().externalServiceTts) {
            if (!ExternalServiceEngine.isFreeMode(ModConfig.get().serviceMode)
                    && (ModConfig.get().serviceUrl == null || ModConfig.get().serviceUrl.isBlank())) {
                throw new IllegalStateException("External TTS service URL is not configured");
            }
            closeEngine();
            engine = new ExternalServiceEngine(
                ModConfig.get().serviceMode,
                ModConfig.get().serviceUrl,
                ModConfig.get().serviceApiKey,
                ModConfig.get().serviceVoice,
                ModConfig.get().serviceModel,
                ModConfig.get().serviceVolume
            );
            externalMode = true;
            return;
        }
        if (ModConfig.get().externalTts) {
            String command = ModConfig.get().externalCommand;
            if (command == null || command.isBlank()) {
                throw new IllegalStateException("External TTS command is not configured");
            }
            closeEngine();
            engine = new ExternalCommandEngine(command);
            externalMode = true;
            return;
        }
        if (externalMode) {
            closeEngine();
        }

        Voice requested = VoiceRegistry.findByVoiceId(ModConfig.get().selectedVoice);
        if (requested == null) {
            List<Voice> voices = VoiceRegistry.listVoices();
            requested = voices.isEmpty() ? null : voices.get(0);
        }
        if (requested == null) {
            throw new IllegalStateException("No available voice engine");
        }
        if (activeVoice != null && activeVoice.getId().equals(requested.getId())) {
            return;
        }
        closeEngine();
        if (requested.getEngine() == Voice.Engine.SAPI) {
            engine = new SapiEngine(requested);
            activeVoice = requested;
            return;
        }
        if (requested.getEngine() == Voice.Engine.PIPER) {
            engine = new PiperEngine(requested);
            activeVoice = requested;
            return;
        }
        if (requested.getEngine() == Voice.Engine.SHERPA) {
            engine = new SherpaEngine(requested);
            activeVoice = requested;
            return;
        }
        throw new IllegalStateException("Unknown voice engine");
    }

    private static void play(short[] pcm) {
        short[] audio = AudioUtil.applyVolume(
            AudioUtil.resample(pcm, AudioUtil.OUTPUT_SAMPLE_RATE),
            ModConfig.get().volume
        );
        int frameSize = AudioUtil.FRAME_SIZE;
        boolean svcConnected = VoiceChatBridge.isConnected();
        boolean pvAvailable = PlasmoVoiceBridge.isAvailable();
        boolean sendToSvc = ModConfig.get().routeThroughVoiceChat && svcConnected;
        boolean sendToPv = ModConfig.get().routeThroughVoiceChat && pvAvailable;
        boolean sendAny = sendToSvc || sendToPv;
        boolean playLocally = !sendAny || (ModConfig.get().hearSelf && (svcConnected || pvAvailable));
        // 音频输出设置：把同一段 TTS 再并联一路送到所选设备（典型用途：虚拟声卡当麦克风）。
        String outMode = ModConfig.get().audioOutMode == null ? "game" : ModConfig.get().audioOutMode;
        boolean deviceOnly = "device".equals(outMode);
        boolean deviceMode = deviceOnly || "both".equals(outMode);
        AudioOutputs.Sink deviceSink = null;
        if (deviceMode) {
            String deviceName = ModConfig.get().audioOutDevice;
            if (deviceName == null || deviceName.isBlank()) {
                McVoiceConstants.LOGGER.warn("音频输出模式选了设备，但没有选择任何设备，本次按原方式播放");
            } else {
                try {
                    deviceSink = AudioOutputs.Sink.open(deviceName, ModConfig.get().audioOutVolume);
                } catch (Exception e) {
                    McVoiceConstants.LOGGER.warn("无法打开音频输出设备 {}，本次按原方式播放", deviceName, e);
                }
            }
        }
        // 只有设备那一路真的打开了，才允许把游戏内播放关掉；否则用户会彻底听不到声音。
        if (deviceOnly && deviceSink != null) {
            playLocally = false;
        }
        // SVC 连着、但它的本地通道没建好（例如分类注册出错）时，必须回落成直写本地音频线，
        // 否则 playLocal 是空操作 —— 表现就是"连自己都听不到"。
        boolean useSvcLocal = playLocally && svcConnected && VoiceChatBridge.isLocalReady();
        // 每次都记一行：排查"没声音"时先看这一行就知道走了哪条路（设备是否真的打开了）。
        McVoiceConstants.LOGGER.info(
            "TTS 播放：输出方式={} 设备=[{}] 设备已打开={} 游戏内播放={} 走SVC本地={} 送给SVC={} 送给PV={} 采样数={}",
            outMode, ModConfig.get().audioOutDevice, deviceSink != null,
            playLocally, useSvcLocal, sendToSvc, sendToPv, audio.length);
        boolean interrupted = false;
        SPEAKING.set(true);
        // 播放节拍：按绝对时间对齐，而不是"做完工作再睡 20ms"。
        // 后者会让每帧变成"工作量 + 20ms"（加了 PV 发送或设备写入就必然超时），
        // 音频引擎持续欠载 —— 这就是历史上"说话卡顿"的根因。
        // 只有直写本地音频线时才靠阻塞写入定速；其余情况（SVC 本地通道 / 只送设备）由我们定速。
        boolean pacedByLine = playLocally && !useSvcLocal;
        final long frameNanos = Math.round(frameSize * 1_000_000_000.0 / AudioUtil.OUTPUT_SAMPLE_RATE);
        long nextFrameAt = System.nanoTime();
        long worstGapNanos = 0L;
        long totalGapNanos = 0L;
        int gapCount = 0;
        int svcDropped = 0;
        try {
            if (playLocally && !useSvcLocal) {
                openLocalLine();
            }
            for (int offset = 0; offset < audio.length && SPEAKING.get(); offset += frameSize) {
                int end = Math.min(audio.length, offset + frameSize);
                short[] frame = new short[frameSize];
                System.arraycopy(audio, offset, frame, 0, end - offset);
                if (playLocally) {
                    if (useSvcLocal) {
                        VoiceChatBridge.playLocal(frame);
                    } else {
                        localLine.write(AudioUtil.toBytes(frame), 0, frame.length * Short.BYTES);
                    }
                }
                if (deviceSink != null) {
                    deviceSink.write(frame);
                }
                if (sendToPv) {
                    PlasmoVoiceBridge.sendFrame(frame, ModConfig.get().distance);
                }
                if (sendToSvc) {
                    synchronized (SVC_QUEUE) {
                        while (SVC_QUEUE.size() >= 50) {
                            SVC_QUEUE.poll();
                            svcDropped++;
                        }
                        SVC_QUEUE.add(frame);
                    }
                }
                if (!pacedByLine) {
                    nextFrameAt += frameNanos;
                    long remain = nextFrameAt - System.nanoTime();
                    if (remain > 0) {
                        Thread.sleep(remain / 1_000_000L, (int) (remain % 1_000_000L));
                    } else {
                        // remain < 0：这一帧比预期晚了，记下来用于发现卡顿。
                        totalGapNanos += -remain;
                        if (-remain > worstGapNanos) {
                            worstGapNanos = -remain;
                        }
                        gapCount++;
                        if (-remain > frameNanos * 4) {
                            // 落后超过 4 帧（80ms）就重新对齐，避免累积成"越播越慢"。
                            nextFrameAt = System.nanoTime();
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            McVoiceConstants.LOGGER.warn("Local audio playback failed", e);
        } finally {
            PlasmoVoiceBridge.sendEnd(ModConfig.get().distance);
            if (deviceSink != null) {
                deviceSink.close();
            }
            closeLocalLine(interrupted);
            SPEAKING.set(false);
            if (gapCount > 0) {
                long avgMs = Math.round(totalGapNanos / (double) gapCount / 1_000_000.0);
                long worstMs = Math.round(worstGapNanos / 1_000_000.0);
                if (avgMs >= 3 || worstMs >= 40) {
                    McVoiceConstants.LOGGER.warn(
                        "播放节拍偏慢（可能卡顿）：落后帧数={} 平均落后={}ms 最大落后={}ms 帧大小={}",
                        gapCount, avgMs, worstMs, frameSize);
                }
            }
            if (svcDropped > 0) {
                // 这条队列是"别人听你说话"那一路；溢出丢帧 = 远处的人会听到断裂。
                McVoiceConstants.LOGGER.warn(
                    "SVC 播放队列溢出丢帧 {} 帧（这一路是别人听你说话，丢帧会让对方听到断裂）", svcDropped);
            }
        }
    }

    private static synchronized void closeEngine() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                McVoiceConstants.LOGGER.warn("Failed to close voice engine", e);
            }
            engine = null;
            activeVoice = null;
            externalMode = false;
        }
    }

    private static void openLocalLine() throws Exception {
        if (localLine == null) {
            AudioFormat format = new AudioFormat(AudioUtil.OUTPUT_SAMPLE_RATE, 16, 1, true, false);
            localLine = AudioSystem.getSourceDataLine(format);
            int bufferSize = AudioUtil.FRAME_SIZE * Short.BYTES * 12;
            localLine.open(format, bufferSize);
            localLine.start();
        }
    }

    private static void closeLocalLine(boolean interrupted) {
        if (localLine != null) {
            if (interrupted) {
                localLine.flush();
            } else {
                localLine.drain();
            }
            localLine.stop();
            localLine.close();
            localLine = null;
        }
    }
}
