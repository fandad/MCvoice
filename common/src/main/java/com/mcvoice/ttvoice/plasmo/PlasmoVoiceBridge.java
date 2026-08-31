package com.mcvoice.ttvoice.plasmo;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.tts.AudioUtil;
import net.fabricmc.loader.api.FabricLoader;

public final class PlasmoVoiceBridge {
    private static boolean pvLoaded;

    private PlasmoVoiceBridge() {
    }

    public static void onWorldJoin() {
        pvLoaded = FabricLoader.getInstance().isModLoaded("plasmovoice");
    }

    public static void onWorldLeave() {
        if (pvLoaded) {
            PvNetwork.sendEnd(ModConfig.get().distance);
        }
        pvLoaded = false;
    }

    public static boolean isAvailable() {
        return pvLoaded && PvNetwork.canSend();
    }

    public static boolean isPvInstalled() {
        return FabricLoader.getInstance().isModLoaded("plasmovoice");
    }

    public static boolean isPvServerConnected() {
        return pvLoaded && PvNetwork.canSend();
    }

    public static void sendFrame(short[] frame, float distance) {
        if (isAvailable()) {
            PvNetwork.sendFrame(AudioUtil.toBytes(frame), distance);
        }
    }

    public static void sendEnd(float distance) {
        if (isAvailable()) {
            PvNetwork.sendEnd(distance);
        }
    }

    public static void stop() {
        if (pvLoaded) {
            PvNetwork.sendEnd(ModConfig.get().distance);
        }
    }
}
