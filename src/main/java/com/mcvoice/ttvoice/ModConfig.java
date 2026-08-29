package com.mcvoice.ttvoice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    public static final class Data {
        public boolean routeThroughVoiceChat = true;
        public boolean hearSelf = true;
        public String selectedVoice = "piper:zh_CN-huayan-medium";
        public boolean autoSpeak = false;
        public boolean viewHistory = true;
        public float volume = 1.0f;
        public float distance = 16.0f;
        public boolean externalTts = false;
        public String externalCommand = "";
        public boolean externalServiceTts = false;
        public float serviceVolume = 100.0f;
        public String serviceMode = "edge";
        public String serviceUrl = "edge-direct";
        public String serviceApiKey = "";
        public String serviceVoice = "zh-CN-XiaoyiNeural";
        public String serviceModel = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve(TtVoiceClient.MOD_ID + ".json");
    private static Data data = new Data();

    private ModConfig() {
    }

    public static Data get() {
        return data;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            data = new Data();
            save();
            return;
        }
        try {
            data = GSON.fromJson(Files.readString(CONFIG_PATH), Data.class);
            if (data == null) {
                data = new Data();
            }
        } catch (IOException | RuntimeException e) {
            TtVoiceClient.LOGGER.error("无法读取配置，将使用默认配置", e);
            data = new Data();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            TtVoiceClient.LOGGER.error("无法保存配置", e);
        }
    }
}
