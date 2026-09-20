package com.mcvoice.ttvoice;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class VoiceChatBridge {
    private static boolean checked;
    private static Method isConnectedMethod;
    private static Method playLocalMethod;
    private static Method isLocalReadyMethod;

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

    /**
     * SVC 的本地播放通道是否已经建好。没建好（例如连接事件里注册出错）时调用方要
     * 回落成直写本地音频线，否则会变成"连自己都听不到"。
     */
    public static boolean isLocalReady() {
        ensureLoaded();
        if (isLocalReadyMethod == null) {
            return false;
        }
        try {
            return (Boolean) isLocalReadyMethod.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
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
            isLocalReadyMethod = type.getMethod("isLocalReady");
        } catch (ReflectiveOperationException | LinkageError e) {
            McVoiceConstants.LOGGER.warn("Failed to load SVC bridge", e);
        }
    }
}
