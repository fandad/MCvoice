package com.mcvoice.ttvoice.plasmo;

import com.mcvoice.ttvoice.McVoiceConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class PvNetwork {
    private PvNetwork() {
    }

    public static void registerPayload() {
        PayloadTypeRegistry.serverboundPlay().register(McvoicePvPayload.TYPE, McvoicePvPayload.CODEC);
    }

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(McvoicePvPayload.TYPE, (payload, context) ->
            PvServerBridge.receive(
                context.player().getUUID(),
                payload.audio(),
                payload.distance(),
                payload.end()
            )
        );
    }

    public static boolean canSend() {
        try {
            return ClientPlayNetworking.canSend(McvoicePvPayload.TYPE);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void sendFrame(byte[] audio, float distance) {
        try {
            if (ClientPlayNetworking.canSend(McvoicePvPayload.TYPE)) {
                ClientPlayNetworking.send(new McvoicePvPayload(audio, distance, false));
            }
        } catch (RuntimeException e) {
            McVoiceConstants.LOGGER.warn("Failed to send PV audio frame", e);
        }
    }

    public static void sendEnd(float distance) {
        try {
            if (ClientPlayNetworking.canSend(McvoicePvPayload.TYPE)) {
                ClientPlayNetworking.send(new McvoicePvPayload(new byte[0], distance, true));
            }
        } catch (RuntimeException e) {
            McVoiceConstants.LOGGER.warn("Failed to send PV audio end", e);
        }
    }
}
