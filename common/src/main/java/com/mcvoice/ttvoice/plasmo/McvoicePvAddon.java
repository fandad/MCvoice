package com.mcvoice.ttvoice.plasmo;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;

@Addon(
    id = "mcvoice",
    name = "MCvoice",
    scope = AddonLoaderScope.ANY_SERVER,
    version = "0.1.9",
    authors = {"MCvoice"},
    dependencies = {}
)
public class McvoicePvAddon implements AddonInitializer {
    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    @Override
    public void onAddonInitialize() {
        PvServerBridge.setVoiceServer(voiceServer);
    }

    @Override
    public void onAddonShutdown() {
        PvServerBridge.clear();
    }
}
