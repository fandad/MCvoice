package com.mcvoice.ttvoice.tts;

public final class Voice {
    public enum Engine {
        PIPER,
        SHERPA,
        KOKORO,
        SAPI
    }

    private final String id;
    private final String displayName;
    private final Engine engine;
    private final String modelPath;
    private final String configPath;
    private final String tokensPath;
    private final String lexiconPath;
    private final String dataDir;
    private final int speakerId;
    private final String voicesPath;
    private final String dictDir;
    private final String lang;

    public Voice(String id, String displayName, Engine engine, String modelPath, String configPath) {
        this(id, displayName, engine, modelPath, configPath, "", "", "", 0, "", "", "");
    }

    public Voice(String id, String displayName, Engine engine, String modelPath, String configPath,
                 String tokensPath, String lexiconPath, String dataDir, int speakerId) {
        this(id, displayName, engine, modelPath, configPath, tokensPath, lexiconPath, dataDir,
            speakerId, "", "", "");
    }

    public Voice(String id, String displayName, Engine engine, String modelPath, String configPath,
                 String tokensPath, String lexiconPath, String dataDir, int speakerId,
                 String voicesPath, String dictDir, String lang) {
        this.id = id;
        this.displayName = displayName;
        this.engine = engine;
        this.modelPath = modelPath;
        this.configPath = configPath;
        this.tokensPath = tokensPath;
        this.lexiconPath = lexiconPath;
        this.dataDir = dataDir;
        this.speakerId = speakerId;
        this.voicesPath = voicesPath;
        this.dictDir = dictDir;
        this.lang = lang;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Engine getEngine() {
        return engine;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getConfigPath() {
        return configPath;
    }

    public String getTokensPath() {
        return tokensPath;
    }

    public String getLexiconPath() {
        return lexiconPath;
    }

    public String getDataDir() {
        return dataDir;
    }

    public int getSpeakerId() {
        return speakerId;
    }

    public String getVoicesPath() {
        return voicesPath;
    }

    public String getDictDir() {
        return dictDir;
    }

    public String getLang() {
        return lang;
    }
}
