package com.mcvoice.ttvoice;

import com.mcvoice.ttvoice.command.McVoiceCommands;
import com.mcvoice.ttvoice.screen.ConfigScreen;
import com.mcvoice.ttvoice.screen.SpeechScreen;
import com.mcvoice.ttvoice.screen.ScreenUtil;
import com.mcvoice.ttvoice.tts.TtsManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TtVoiceClient implements ClientModInitializer {
    public static final String MOD_ID = "mcvoice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping speakKey;
    private static KeyMapping configKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        TtsManager.init();
        registerKeybind();
        registerEvents();
        McVoiceCommands.register();
    }

    private static void registerKeybind() {
        KeyMapping.Category generalCategory = KeyMapping.Category.register(
            Identifier.tryBuild(MOD_ID, "general")
        );
        speakKey = new KeyMapping(
            "key." + MOD_ID + ".speak",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            generalCategory
        );
        KeyMappingHelper.registerKeyMapping(speakKey);

        configKey = new KeyMapping(
            "key." + MOD_ID + ".config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            generalCategory
        );
        KeyMappingHelper.registerKeyMapping(configKey);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (speakKey.consumeClick()) {
                ScreenUtil.setScreen(new SpeechScreen(null));
            }
            while (configKey.consumeClick()) {
                ScreenUtil.setScreen(new ConfigScreen(null));
            }
        });
    }

    private static void registerEvents() {
        ClientSendMessageEvents.CHAT.register(message -> {
            if (ModConfig.get().autoSpeak) {
                TtsManager.speak(message);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TtsManager.onWorldJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, sender) -> {
            TtsManager.onWorldLeave();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ModConfig.save();
            TtsManager.shutdown();
        });
    }
}
