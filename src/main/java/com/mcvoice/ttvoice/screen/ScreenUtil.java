package com.mcvoice.ttvoice.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenUtil {
    private ScreenUtil() {
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }
}
