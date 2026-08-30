package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.McVoiceConstants;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class VoiceRegistry {
    private static final Map<String, String> PIPER_NAMES = Map.of(
        "zh_CN-huayan-medium", "中文 · 花颜（女声）",
        "zh_CN-huayan-x_low", "中文 · 花颜（低配）",
        "zh_CN-chaowen-medium", "中文 · 超文（男声）"
    );
    private static final Map<String, String> SHERPA_NAMES = Map.of(
        "vits-melo-tts-zh_en", "中文 · MeloTTS 中英女声",
        "vits-zh-hf-theresa", "中文 · 寒冰（多音色女声）",
        "vits-zh-hf-eula", "中文 · 伊拉（多音色女声）",
        "vits-zh-hf-fanchen-wnj", "中文 · 繁辰 WNJ（男声）",
        "sherpa-onnx-vits-zh-ll", "中文 · 小爱风格（多音色）"
    );

    private VoiceRegistry() {
    }

    public static Path getMcVoiceDir() {
        return FabricLoader.getInstance().getGameDir().resolve(McVoiceConstants.MOD_ID);
    }

    public static Path getModelDir() {
        return getMcVoiceDir().resolve("models");
    }

    public static Path getSherpaModelDir() {
        return getModelDir().resolve("sherpa");
    }

    public static List<Voice> listVoices() {
        if (!isWindowsSupported()) {
            return List.of();
        }
        List<Voice> voices = new ArrayList<>();
        Path modelDir = getModelDir();
        if (Files.isDirectory(modelDir)) {
            try (var stream = Files.list(modelDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".onnx"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(path -> {
                        String id = path.getFileName().toString().replaceFirst("\\.onnx$", "");
                        Path config = path.resolveSibling(id + ".onnx.json");
                        if (!isUsableModel(path, config)) {
                            return;
                        }
                        String display = PIPER_NAMES.getOrDefault(id, "中文 · " + id);
                        voices.add(new Voice("piper:" + id, display, Voice.Engine.PIPER,
                            path.toAbsolutePath().toString(), config.toAbsolutePath().toString()));
                    });
            } catch (IOException e) {
                McVoiceConstants.LOGGER.error("无法读取语音模型目录", e);
            }
        }

        Path sherpaDir = getSherpaModelDir();
        if (Files.isDirectory(sherpaDir)) {
            try (var stream = Files.walk(sherpaDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".onnx"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(path -> addSherpaVoice(voices, path));
            } catch (IOException e) {
                McVoiceConstants.LOGGER.error("无法读取 Sherpa 语音模型目录", e);
            }
        }

        for (String voiceName : SapiVoices.list()) {
            voices.add(new Voice("sapi:" + voiceName, "系统声线 · " + voiceName,
                Voice.Engine.SAPI, voiceName, ""));
        }

        voices.sort(Comparator.comparing(Voice::getDisplayName));
        return voices;
    }

    public static Voice findByVoiceId(String id) {
        for (Voice voice : listVoices()) {
            if (voice.getId().equals(id)) {
                return voice;
            }
        }
        return null;
    }

    public static void openMcVoiceFolder() {
        Path dir = getMcVoiceDir();
        try {
            Files.createDirectories(dir);
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            } else {
                new ProcessBuilder("explorer.exe", dir.toAbsolutePath().toString()).start();
            }
        } catch (IOException e) {
            McVoiceConstants.LOGGER.error("无法打开 mcvoice 文件夹", e);
        }
    }

    public static boolean isModelDownloaded(String modelId, boolean sherpa) {
        if (sherpa) {
            Path dir = getSherpaModelDir().resolve(modelId);
            return isUsableSherpaModel(dir.resolve("model.onnx"), dir.resolve("tokens.txt"));
        }
        Path modelFile = getModelDir().resolve(modelId + ".onnx");
        Path configFile = getModelDir().resolve(modelId + ".onnx.json");
        return isUsableModel(modelFile, configFile);
    }

    public static boolean isUsableModel(Path modelFile, Path configFile) {
        try {
            return Files.isRegularFile(modelFile)
                && Files.size(modelFile) > 1_000_000L
                && Files.isRegularFile(configFile)
                && Files.size(configFile) > 10L
                && startsWithJsonObject(configFile);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean startsWithJsonObject(Path path) {
        try {
            String content = Files.readString(path);
            return content != null && !content.isBlank() && content.stripLeading().startsWith("{");
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isWindowsSupported() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void addSherpaVoice(List<Voice> voices, Path modelFile) {
        Path dir = modelFile.getParent();
        if (dir == null) {
            return;
        }
        Path tokens = dir.resolve("tokens.txt");
        if (!isUsableSherpaModel(modelFile, tokens)) {
            return;
        }
        String id = dir.getFileName().toString();
        Path lexicon = dir.resolve("lexicon.txt");
        Path dataDir = Files.isDirectory(dir.resolve("dict"))
            ? dir.resolve("dict")
            : (Files.isDirectory(dir.resolve("espeak-ng-data"))
                ? dir.resolve("espeak-ng-data")
                : null);
        String display = SHERPA_NAMES.getOrDefault(id, "中文 · " + id);
        voices.add(new Voice(
            "sherpa:" + id,
            display,
            Voice.Engine.SHERPA,
            modelFile.toAbsolutePath().toString(),
            "",
            tokens.toAbsolutePath().toString(),
            Files.isRegularFile(lexicon) ? lexicon.toAbsolutePath().toString() : "",
            dataDir == null ? "" : dataDir.toAbsolutePath().toString(),
            0
        ));
    }

    public static boolean isUsableSherpaModel(Path modelFile, Path tokensFile) {
        try {
            return Files.isRegularFile(modelFile)
                && Files.size(modelFile) > 1_000_000L
                && Files.isRegularFile(tokensFile)
                && Files.size(tokensFile) > 10L;
        } catch (IOException e) {
            return false;
        }
    }
}
