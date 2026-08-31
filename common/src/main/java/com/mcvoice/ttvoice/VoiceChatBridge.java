package com.mcvoice.ttvoice;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class VoiceChatBridge {
    private static boolean checked;
    private static Method isConnectedMethod;
    private static Method playLocalMethod;

    private VoiceChatBridge() {
    }

    public static boolean isConnected() {
        ensureLoaded();
        if (isConnectedMethod == null) {
            return false;
        }
        try {
            return (Boolean) isConnectedMethod.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static boolean isInstalled() {
        ensureLoaded();
        return isConnectedMethod != null;
    }

    public static void playLocal(short[] frame) {
        ensureLoaded();
        if (playLocalMethod == null) {
            return;
        }
        try {
            playLocalMethod.invoke(null, (Object) frame);
        } catch (ReflectiveOperationException | RuntimeException e) {
            McVoiceConstants.LOGGER.warn("SVC local playback failed", e);
        }
    }

    private static void ensureLoaded() {
        if (checked) {
            return;
        }
        checked = true;
        if (!FabricLoader.getInstance().isModLoaded("voicechat")) {
            return;
        }
        try {
            Class<?> type = Class.forName("com.mcvoice.ttvoice.VcPlugin");
            isConnectedMethod = type.getMethod("isConnected");
            playLocalMethod = type.getMethod("playLocal", short[].class);
        } catch (ReflectiveOperationException | LinkageError e) {
            McVoiceConstants.LOGGER.warn("Failed to load SVC bridge", e);
        }
    }
}
