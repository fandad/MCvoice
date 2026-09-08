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
        boolean interrupted = false;
        SPEAKING.set(true);
        try {
            if (playLocally) {
                openLocalLine();
            }
            for (int offset = 0; offset < audio.length && SPEAKING.get(); offset += frameSize) {
                int end = Math.min(audio.length, offset + frameSize);
                short[] frame = new short[frameSize];
                System.arraycopy(audio, offset, frame, 0, end - offset);
                if (playLocally) {
                    if (svcConnected) {
                        VoiceChatBridge.playLocal(frame);
                    } else {
                        localLine.write(AudioUtil.toBytes(frame), 0, frame.length * Short.BYTES);
                    }
                }
                if (sendToPv) {
                    PlasmoVoiceBridge.sendFrame(frame, ModConfig.get().distance);
                }
                if (sendToSvc) {
                    synchronized (SVC_QUEUE) {
                        while (SVC_QUEUE.size() >= 50) {
                            SVC_QUEUE.poll();
                        }
                        SVC_QUEUE.add(frame);
                    }
                }
                if (!(playLocally && !svcConnected)) {
                    Thread.sleep(20);
                }
            }
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            McVoiceConstants.LOGGER.warn("Local audio playback failed", e);
        } finally {
            PlasmoVoiceBridge.sendEnd(ModConfig.get().distance);
            closeLocalLine(interrupted);
            SPEAKING.set(false);
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
