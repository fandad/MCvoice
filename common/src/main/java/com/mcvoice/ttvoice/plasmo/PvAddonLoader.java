package com.mcvoice.ttvoice.plasmo;

import com.mcvoice.ttvoice.McVoiceConstants;

public final class PvAddonLoader {
    private PvAddonLoader() {
    }

    public static void load() {
        try {
            Class<?> voiceServerClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
            Object loader = voiceServerClass.getMethod("getAddonsLoader").invoke(null);
            Object addon = Class.forName("com.mcvoice.ttvoice.plasmo.McvoicePvAddon")
                .getDeclaredConstructor()
                .newInstance();
            loader.getClass().getMethod("load", Object.class).invoke(loader, addon);
        } catch (ReflectiveOperationException | RuntimeException e) {
            McVoiceConstants.LOGGER.warn("Failed to load Plasmo Voice addon", e);
        }
    }
}
