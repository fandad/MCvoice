package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.TtVoiceClient;
import com.mcvoice.ttvoice.VcPlugin;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TtsManager {
    private static final Queue<short[]> SVC_QUEUE = new ArrayDeque<>();
    private static final AtomicBoolean SPEAKING = new AtomicBoolean(false);
    private static SourceDataLine localLine;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MCVoice-TTS");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile TtsEngine engine;
    private static volatile Voice activeVoice;
    private static boolean externalMode;

    private TtsManager() {
    }

    public static void init() {
    }

    public static void onWorldJoin() {
        TtVoiceClient.LOGGER.info("MC语音已进入游戏");
    }

    public static void onWorldLeave() {
        stop();
    }

    public static void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        WORKER.submit(() -> {
            try {
                ensureEngine();
                short[] pcm = engine.synthesize(text);
                play(pcm);
            } catch (Exception e) {
                TtVoiceClient.LOGGER.error("语音生成失败", e);
            }
        });
    }

    public static void test() {
        speak("你好，这是 MC语音 的本地中文测试。");
    }

    public static void stop() {
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
                throw new IllegalStateException("未设置外部TTS服务地址");
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
                throw new IllegalStateException("未设置外部TTS命令");
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
            throw new IllegalStateException("没有可用的语音引擎");
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
        throw new IllegalStateException("未知语音引擎");
    }

    private static void play(short[] pcm) {
        short[] audio = AudioUtil.applyVolume(
            AudioUtil.resample(pcm, AudioUtil.OUTPUT_SAMPLE_RATE),
            ModConfig.get().volume
        );
        int frameSize = AudioUtil.FRAME_SIZE;
        boolean svcConnected = VcPlugin.isConnected();
        boolean sendToSvc = ModConfig.get().routeThroughVoiceChat && svcConnected;
        boolean playLocally = !sendToSvc || (ModConfig.get().hearSelf && svcConnected);
        SPEAKING.set(true);
        try {
            if (playLocally) {
                openLocalLine();
            }
            for (int offset = 0; offset < audio.length && SPEAKING.get(); offset += frameSize) {
                int end = Math.min(audio.length, offset + frameSize);
                short[] frame = new short[frameSize];
                System.arraycopy(audio, offset, frame, 0, end - offset);
                if (sendToSvc) {
                    synchronized (SVC_QUEUE) {
                        while (SVC_QUEUE.size() >= 50) {
                            SVC_QUEUE.poll();
                        }
                        SVC_QUEUE.add(frame);
                    }
                }
                if (playLocally) {
                    if (svcConnected) {
                        VcPlugin.playLocal(frame);
                    } else {
                        localLine.write(AudioUtil.toBytes(frame), 0, frame.length * Short.BYTES);
                    }
                }
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            TtVoiceClient.LOGGER.warn("本地音频播放失败", e);
        } finally {
            closeLocalLine();
            SPEAKING.set(false);
        }
    }

    private static synchronized void closeEngine() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                TtVoiceClient.LOGGER.warn("关闭语音引擎失败", e);
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
            localLine.open(format, AudioUtil.FRAME_SIZE * 2);
            localLine.start();
        }
    }

    private static void closeLocalLine() {
        if (localLine != null) {
            localLine.flush();
            localLine.stop();
            localLine.close();
            localLine = null;
        }
    }
}
