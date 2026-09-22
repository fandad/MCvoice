package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.McVoiceConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VoiceRegistry {
    private static final Map<String, String> PIPER_NAMES = Map.of(
        "zh_CN-huayan-medium", "piper.huayan_medium",
        "zh_CN-huayan-x_low", "piper.huayan_xlow"
    );
    private static final Map<String, String> SHERPA_NAMES = Map.of(
        "vits-melo-tts-zh_en", "sherpa.melo",
        "vits-zh-hf-theresa", "sherpa.theresa",
        "vits-zh-hf-eula", "sherpa.eula",
        "vits-zh-hf-fanchen-wnj", "sherpa.fanchen",
        "sherpa-onnx-vits-zh-ll", "sherpa.xiaomi",
        "vits-piper-zh_CN-chaowen-medium", "sherpa.chaowen",
        "vits-piper-zh_CN-xiao_ya-medium", "sherpa.xiaoya",
        "vits-cantonese-hf-xiaomaiiwn", "sherpa.cantonese",
        "matcha-icefall-zh-baker", "sherpa.matcha",
        "kokoro-int8-multi-lang-v1_0", "sherpa.kokoro"
    );

    /**
     * Kokoro 多语言包（kokoro-multi-lang-v1_0）里中文音色的 speaker id 与显示名。
     * 映射来自 sherpa-onnx 官方文档（speaker id 45-52 为中文音色组）。
     */
    private static final Map<Integer, String> KOKORO_ZH_SPEAKERS = Map.of(
        45, ".spk45",
        46, ".spk46",
        47, ".spk47",
        48, ".spk48",
        49, ".spk49",
        50, ".spk50",
        51, ".spk51",
        52, ".spk52"
    );

    private static final Set<String> KOKORO_DIR_IDS = Set.of("kokoro-int8-multi-lang-v1_0");

    /**
     * 多说话人 VITS 模型：每个模型挑若干 speaker 单独作为一条声线。
     * 这些 id 与试听样本一一对应（用户听过后留下的那批），并标注了男女声。
     * 以后要换音色，改这里的数字即可（speaker 是模型内置的，模型不用重下）。
     */
    private static final Map<String, Map<Integer, String>> VITS_MULTI_SPEAKERS = Map.of(
        "vits-zh-hf-theresa", Map.of(
            66, ".spk66",
            436, ".spk436",
            249, ".spk249"),
        "vits-zh-hf-eula", Map.of(
            66, ".spk66",
            376, ".spk376",
            436, ".spk436",
            623, ".spk623"),
        "sherpa-onnx-vits-zh-ll", Map.of(
            0, ".spk0",
            2, ".spk2",
            1, ".spk1",
            3, ".spk3",
            4, ".spk4")
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
                        if (!isPiperRuntimeCompatible(config)) {
                            return;
                        }
                        String display = id;
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
            voices.add(new Voice("sapi:" + voiceName, voiceName,
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
            return findSherpaModelFile(dir) != null;
        }
        Path modelFile = getModelDir().resolve(modelId + ".onnx");
        Path configFile = getModelDir().resolve(modelId + ".onnx.json");
        return isUsableModel(modelFile, configFile);
    }

    public static Path findSherpaModelFile(Path modelDir) {
        if (!Files.isDirectory(modelDir)) {
            return null;
        }
        try (var stream = Files.list(modelDir)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".onnx"))
                .filter(path -> isUsableSherpaModel(path, path.getParent().resolve("tokens.txt")))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
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

    private static boolean isPiperRuntimeCompatible(Path configFile) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
            String phonemeType = root.has("phoneme_type")
                ? root.get("phoneme_type").getAsString()
                : "";
            return !"pinyin".equals(phonemeType);
        } catch (Exception e) {
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
        boolean kokoro = KOKORO_DIR_IDS.contains(id) && Files.isRegularFile(dir.resolve("voices.bin"));
        if (kokoro) {
            addKokoroVoices(voices, dir, modelFile, tokens, dataDir, id);
            return;
        }
        String display = id;
        String lexPath = Files.isRegularFile(lexicon) ? lexicon.toAbsolutePath().toString() : "";
        String dataPath = dataDir == null ? "" : dataDir.toAbsolutePath().toString();

        // 多说话人 VITS：按挑好的 speaker 展开成多条声线。
        Map<Integer, String> speakers = VITS_MULTI_SPEAKERS.get(id);
        if (speakers != null) {
            for (Map.Entry<Integer, String> entry : speakers.entrySet()) {
                voices.add(new Voice(
                    "sherpa:" + id + "#" + entry.getKey(),
                    display + entry.getValue(),
                    Voice.Engine.SHERPA,
                    modelFile.toAbsolutePath().toString(),
                    "",
                    tokens.toAbsolutePath().toString(),
                    lexPath,
                    dataPath,
                    entry.getKey()
                ));
            }
            return;
        }

        voices.add(new Voice(
            "sherpa:" + id,
            display,
            Voice.Engine.SHERPA,
            modelFile.toAbsolutePath().toString(),
            "",
            tokens.toAbsolutePath().toString(),
            lexPath,
            dataPath,
            0
        ));
    }

    /** Kokoro 是单模型多音色：每个中文 speaker 单独作为一条声线，靠 speakerId 区分。 */
    private static void addKokoroVoices(List<Voice> voices, Path dir, Path modelFile, Path tokens, Path dictDir, String id) {
        Path voicesBin = dir.resolve("voices.bin");
        Path lexicon = Files.isRegularFile(dir.resolve("lexicon.txt"))
            ? dir.resolve("lexicon.txt")
            : dir.resolve("lexicon-zh.txt");
        Path espeakData = Files.isDirectory(dir.resolve("espeak-ng-data"))
            ? dir.resolve("espeak-ng-data")
            : null;
        String baseName = id;
        for (Map.Entry<Integer, String> entry : KOKORO_ZH_SPEAKERS.entrySet()) {
            int speakerId = entry.getKey();
            voices.add(new Voice(
                "kokoro:" + id + ":" + speakerId,
                baseName + entry.getValue(),
                Voice.Engine.KOKORO,
                modelFile.toAbsolutePath().toString(),
                "",
                tokens.toAbsolutePath().toString(),
                Files.isRegularFile(lexicon) ? lexicon.toAbsolutePath().toString() : "",
                espeakData == null ? "" : espeakData.toAbsolutePath().toString(),
                speakerId,
                voicesBin.toAbsolutePath().toString(),
                dictDir == null ? "" : dictDir.toAbsolutePath().toString(),
                "zh"
            ));
        }
    }

    /**
     * 声线显示名的翻译信息：key 后缀（完整 key 为 {@code voice.mcvoice.<suffix>}）与可选参数。
     * common 模块不能依赖 Minecraft 的 Component，所以这里只产出 key，组装交给界面层。
     */
    public record VoiceLabel(String suffix, String arg) {
    }

    /**
     * 由声线 id 反推显示名的翻译 key。返回 {@code null} 时调用方退回 {@link Voice#getDisplayName()}。
     */
    public static VoiceLabel label(String voiceId) {
        if (voiceId == null) {
            return null;
        }
        if (voiceId.startsWith("sapi:")) {
            return new VoiceLabel("sapi", voiceId.substring("sapi:".length()));
        }
        if (voiceId.startsWith("kokoro:")) {
            int last = voiceId.lastIndexOf(':');
            String speaker = last > 0 ? voiceId.substring(last + 1) : "";
            return new VoiceLabel("kokoro.spk" + speaker, null);
        }
        if (voiceId.startsWith("sherpa:")) {
            String rest = voiceId.substring("sherpa:".length());
            int hash = rest.indexOf('#');
            String dir = hash < 0 ? rest : rest.substring(0, hash);
            String base = SHERPA_NAMES.get(dir);
            if (base == null) {
                return new VoiceLabel("unknown", dir);
            }
            return hash < 0
                ? new VoiceLabel(base, null)
                : new VoiceLabel(base + ".spk" + rest.substring(hash + 1), null);
        }
        // Piper 的声线 id 形如 "piper:zh_CN-huayan-medium"，而 PIPER_NAMES 的键是裸目录名，
        // 必须先去掉前缀再查表，否则会落到 unknown 分支、在选择界面显示成原始 id（0.2.7 修过）。
        String piperModelId = voiceId.startsWith("piper:")
            ? voiceId.substring("piper:".length())
            : voiceId;
        String piper = PIPER_NAMES.get(piperModelId);
        return piper == null ? new VoiceLabel("unknown", piperModelId) : new VoiceLabel(piper, null);
    }

    /**
     * 下载模型 id（模型目录名）对应的短名翻译 key；未知模型返回 {@code null}，调用方退回原始 id。
     */
    public static String modelNameKey(String modelId) {
        String sherpa = SHERPA_NAMES.get(modelId);
        if (sherpa != null) {
            return "voice.mcvoice." + sherpa;
        }
        String piper = PIPER_NAMES.get(modelId);
        return piper == null ? null : "voice.mcvoice." + piper;
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
