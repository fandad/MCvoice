package com.mcvoice.ttvoice.plasmo;

import com.mcvoice.ttvoice.McVoiceConstants;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class McvoicePvCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        PvNetwork.registerPayload();
        PvNetwork.registerServerReceiver();

        if (FabricLoader.getInstance().isModLoaded("plasmovoice")) {
            PvAddonLoader.load();
        } else {
            McVoiceConstants.LOGGER.info("Plasmo Voice not installed, PV bridge disabled");
        }
    }
}
