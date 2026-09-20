package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.tts.DownloadStatus;
import com.mcvoice.ttvoice.tts.Voice;
import com.mcvoice.ttvoice.tts.VoiceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ScreenUtil {
    private ScreenUtil() {
    }

    /** 下载状态：解码 common 传来的 key+参数 token（见 DownloadStatus），组装成可翻译文本。 */
    public static Component statusComponent(String token) {
        if (token == null || token.isEmpty()) {
            return Component.literal("");
        }
        String[] parts = DownloadStatus.decode(token);
        if (parts.length <= 1) {
            return Component.translatable(parts[0]);
        }
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            args[i - 1] = statusArg(parts[i]);
        }
        return Component.translatable(parts[0], args);
    }

    private static Component statusArg(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.literal("");
        }
        if (raw.startsWith("voice.mcvoice.") || raw.startsWith("download.mcvoice.")) {
            return statusComponent(raw);
        }
        return Component.literal(raw);
    }

    /** 声线显示名：优先用翻译 key（见 VoiceRegistry.label），未知模型退回原名。 */
    public static Component voiceName(Voice voice) {
        VoiceRegistry.VoiceLabel label = VoiceRegistry.label(voice.getId());
        if (label == null) {
            return Component.literal(voice.getDisplayName());
        }
        String key = "voice.mcvoice." + label.suffix();
        return label.arg() == null
            ? Component.translatable(key)
            : Component.translatable(key, label.arg());
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
