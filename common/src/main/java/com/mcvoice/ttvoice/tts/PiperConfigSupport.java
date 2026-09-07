package com.mcvoice.ttvoice.tts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class PiperConfigSupport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PiperConfigSupport() {
    }

    static void normalizeVoiceIfNeeded(Path configFile) throws IOException {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        JsonObject root = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
        JsonElement espeak = root.get("espeak");
        if (espeak == null || !espeak.isJsonObject()) {
            return;
        }
        JsonElement voice = espeak.getAsJsonObject().get("voice");
        if (voice != null && "zh".equals(voice.getAsString())) {
            espeak.getAsJsonObject().addProperty("voice", "cmn");
            Files.writeString(configFile, GSON.toJson(root));
        }
    }
}
