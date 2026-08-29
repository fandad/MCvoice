package com.mcvoice.ttvoice.tts;

import io.github.jvoiceproject.piperjni.PiperJNI;
import io.github.jvoiceproject.piperjni.PiperVoice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PiperEngine implements TtsEngine {
    private static final Path RUNTIME_DIR = resolveRuntimeDir();

    private final PiperJNI piper;
    private final PiperVoice voice;
    private final int sampleRate;

    public PiperEngine(Voice voiceData) throws Exception {
        this.piper = new PiperJNI();
        this.piper.initialize(true);
        try {
            Path modelFile = prepareFile(Path.of(voiceData.getModelPath()));
            Path configFile = prepareFile(Path.of(voiceData.getConfigPath()));
            String configText = Files.readString(configFile);
            if (configText == null || configText.isBlank() || !configText.stripLeading().startsWith("{")) {
                throw new IOException("Piper 模型配置无效或为空: " + configFile);
            }
            this.voice = piper.loadVoice(
                modelFile,
                configFile
            );
        } catch (Exception e) {
            piper.close();
            throw e;
        }
        this.sampleRate = this.voice.getSampleRate();
    }

    @Override
    public short[] synthesize(String text) throws Exception {
        short[] raw = piper.textToAudio(voice, text);
        return AudioUtil.resample(raw, sampleRate);
    }

    @Override
    public void close() throws Exception {
        try {
            voice.close();
        } finally {
            piper.close();
        }
    }

    private static Path resolveRuntimeDir() {
        String[] candidates = {
            System.getProperty("java.io.tmpdir"),
            System.getProperty("user.home"),
            System.getProperty("user.dir")
        };
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && isAscii(candidate)) {
                return Path.of(candidate, "mcvoice-models");
            }
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "mcvoice-models");
    }

    private static Path prepareFile(Path source) throws IOException {
        if (isAscii(source.toAbsolutePath().toString())) {
            return source;
        }

        Path target = RUNTIME_DIR.resolve(source.getFileName().toString());
        if (Files.isRegularFile(target) && Files.size(target) == Files.size(source)) {
            return target;
        }

        Files.createDirectories(RUNTIME_DIR);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }
}
