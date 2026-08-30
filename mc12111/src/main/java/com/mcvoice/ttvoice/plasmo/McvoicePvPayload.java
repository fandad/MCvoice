package com.mcvoice.ttvoice.plasmo;

import com.mcvoice.ttvoice.McVoiceConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record McvoicePvPayload(byte[] audio, float distance, boolean end) implements CustomPacketPayload {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(
        McVoiceConstants.MOD_ID,
        "pv_audio"
    );
    public static final Type<McvoicePvPayload> TYPE = new Type<>(CHANNEL);
    public static final StreamCodec<RegistryFriendlyByteBuf, McvoicePvPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeByteArray(payload.audio());
            buf.writeFloat(payload.distance());
            buf.writeBoolean(payload.end());
        },
        buf -> new McvoicePvPayload(
            buf.readByteArray(4096),
            buf.readFloat(),
            buf.readBoolean()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
