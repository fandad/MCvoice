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

    /** 本地播放通道是否就绪（在 SVC 连接事件里创建）。 */
    public static boolean isLocalReady() {
        return localChannel != null;
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
                // 先把本地播放通道建好：即使音量分类注册失败，也必须能听到自己。
                UUID playerId = Minecraft.getInstance().getUser().getProfileId();
                var playerEntity = api.fromEntity(Minecraft.getInstance().player);
                try {
                    localChannel = api.createEntityAudioChannel(playerId, playerEntity);
                    localChannel.setDistance(ModConfig.get().distance);
                } catch (Exception e) {
                    McVoiceConstants.LOGGER.warn("创建 SVC 本地播放通道失败，游戏内将听不到自己的 TTS", e);
                    localChannel = null;
                }
                try {
                    // name 是 SVC 的必需字段（build() 里为 null 会抛 "name missing"），
                    // nameTranslationKey 才是界面显示用的（getDisplayName 走 translatableWithFallback）。
                    category = api.volumeCategoryBuilder()
                        .setId(TtVoiceClient.MOD_ID + "_voice")
                        .setName(TtVoiceClient.MOD_ID)
                        .setNameTranslationKey("mcvoice.key.category")
                        .build();
                    api.registerClientVolumeCategory(category);
                    localChannel.setCategory(category.getId());
                } catch (Exception e) {
                    McVoiceConstants.LOGGER.warn("注册 SVC 音量分类失败，不影响播放", e);
                    category = null;
                }
                McVoiceConstants.LOGGER.info("SVC 音频通道就绪：本地播放通道={} 音量分类={}",
                    localChannel != null, category == null ? "无" : category.getId());
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
