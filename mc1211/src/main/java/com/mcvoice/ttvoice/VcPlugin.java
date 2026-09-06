package com.mcvoice.ttvoice;

import com.mcvoice.ttvoice.tts.TtsManager;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.ClientEntityAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public class VcPlugin implements VoicechatPlugin {
    private static VoicechatClientApi api;
    private static ClientEntityAudioChannel localChannel;
    private static VolumeCategory category;

    public static boolean isConnected() {
        return api != null && !api.isDisabled() && !api.isDisconnected();
    }

    public static void playLocal(short[] frame) {
        if (localChannel != null) {
            localChannel.setDistance(ModConfig.get().distance);
            localChannel.play(frame);
        }
    }

    @Override
    public String getPluginId() {
        return TtVoiceClient.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
            api = event.getVoicechat();
            if (event.isConnected() && api != null) {
                category = api.volumeCategoryBuilder()
                    .setId(TtVoiceClient.MOD_ID + "_voice")
                    .setName("MC语音")
                    .build();
                api.registerClientVolumeCategory(category);
                UUID playerId = Minecraft.getInstance().getUser().getProfileId();
                var playerEntity = api.fromEntity(Minecraft.getInstance().player);
                localChannel = api.createEntityAudioChannel(playerId, playerEntity);
                localChannel.setDistance(ModConfig.get().distance);
                localChannel.setCategory(category.getId());
            } else {
                localChannel = null;
                category = null;
            }
        }, 10);

        registration.registerEvent(MergeClientSoundEvent.class, event -> {
            short[] frame = TtsManager.nextSvcFrame();
            if (frame != null) {
                event.mergeAudio(frame);
            }
        }, 10);
    }
}
