package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.McVoiceConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;

public final class ScreenUtil {
    private ScreenUtil() {
    }

    public static void setScreen(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Minecraft.class.getMethod("setScreen", Screen.class).invoke(minecraft, screen);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field guiField = Minecraft.class.getDeclaredField("gui");
            guiField.setAccessible(true);
            Object gui = guiField.get(minecraft);
            gui.getClass().getMethod("setScreen", Screen.class).invoke(gui, screen);
        } catch (Exception e) {
            McVoiceConstants.LOGGER.warn("无法打开屏幕", e);
        }
    }
}
