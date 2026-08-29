package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.tts.TtsManager;
import com.mcvoice.ttvoice.tts.Voice;
import com.mcvoice.ttvoice.tts.VoiceRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private final List<Voice> voices;
    private int voiceIndex;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("config.mcvoice.title"));
        this.parent = parent;
        this.voices = VoiceRegistry.listVoices();
        this.voiceIndex = findCurrentVoice();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = 42;
        int buttonWidth = Math.min(320, width - 40);

        addRenderableWidget(new StringWidget(centerX - buttonWidth / 2, 14, buttonWidth, 20,
            Component.translatable("config.mcvoice.title"), font));

        if (!VoiceRegistry.isWindowsSupported()) {
            MultiLineTextWidget unsupported = new MultiLineTextWidget(
                Component.translatable("config.mcvoice.unsupported"), font);
            unsupported.setX(centerX - buttonWidth / 2);
            unsupported.setY(50);
            unsupported.setMaxWidth(buttonWidth);
            unsupported.setMaxRows(4);
            unsupported.setCentered(false);
            addRenderableWidget(unsupported);

            addRenderableWidget(Button.builder(Component.translatable("speech.mcvoice.back"),
                    button -> ScreenUtil.setScreen(parent))
                .pos(centerX - 50, height - 32)
                .size(100, 20)
                .build());
            return;
        }

        Button voiceButton = Button.builder(currentVoiceLabel(), button -> cycleVoice())
            .pos(centerX - buttonWidth / 2, y)
            .size(buttonWidth, 20)
            .build();
        addRenderableWidget(voiceButton);
        y += 28;

        int halfWidth = (buttonWidth - 5) / 2;
        Button openFolderButton = Button.builder(
                Component.translatable("config.mcvoice.models.open"),
                button -> VoiceRegistry.openMcVoiceFolder())
            .pos(centerX - buttonWidth / 2, y)
            .size(halfWidth, 20)
            .build();
        openFolderButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.models.open.tooltip")));
        addRenderableWidget(openFolderButton);

        Button downloadButton = Button.builder(
                Component.translatable("config.mcvoice.models.download"),
                button -> ScreenUtil.setScreen(new ModelDownloadScreen(this)))
            .pos(centerX - buttonWidth / 2 + halfWidth + 5, y)
            .size(halfWidth, 20)
            .build();
        downloadButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.models.download.tooltip")));
        addRenderableWidget(downloadButton);
        y += 28;

        Checkbox routeCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.general.routeThroughVoiceChat"), font)
            .selected(ModConfig.get().routeThroughVoiceChat)
            .pos(centerX - buttonWidth / 2, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().routeThroughVoiceChat = selected;
                ModConfig.save();
            })
            .build();
        addRenderableWidget(routeCheckbox);
        y += 24;

        Checkbox hearSelfCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.general.hearSelf"), font)
            .selected(ModConfig.get().hearSelf)
            .pos(centerX - buttonWidth / 2, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().hearSelf = selected;
                ModConfig.save();
            })
            .build();
        addRenderableWidget(hearSelfCheckbox);
        y += 24;

        Checkbox autoSpeakCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.general.autoSpeak"), font)
            .selected(ModConfig.get().autoSpeak)
            .pos(centerX - buttonWidth / 2, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().autoSpeak = selected;
                ModConfig.save();
            })
            .build();
        addRenderableWidget(autoSpeakCheckbox);
        y += 24;

        Checkbox historyCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.ui.viewHistory"), font)
            .selected(ModConfig.get().viewHistory)
            .pos(centerX - buttonWidth / 2, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().viewHistory = selected;
                ModConfig.save();
            })
            .build();
        addRenderableWidget(historyCheckbox);
        y += 24;

        Button advancedButton = Button.builder(
                Component.translatable("config.mcvoice.advanced"),
                button -> ScreenUtil.setScreen(new AdvancedConfigScreen(this)))
            .pos(centerX - buttonWidth / 2, y)
            .size(buttonWidth, 20)
            .build();
        advancedButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.advanced.tooltip")));
        addRenderableWidget(advancedButton);

        addRenderableWidget(Button.builder(Component.translatable("speech.mcvoice.stop"),
                button -> TtsManager.stop())
            .pos(centerX - buttonWidth / 2, height - 38)
            .size(buttonWidth / 2 - 5, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("返回"),
                button -> ScreenUtil.setScreen(parent))
            .pos(centerX + 5, height - 38)
            .size(buttonWidth / 2 - 5, 20)
            .build());
    }

    private void cycleVoice() {
        if (voices.isEmpty()) {
            return;
        }
        voiceIndex = (voiceIndex + 1) % voices.size();
        ModConfig.get().selectedVoice = voices.get(voiceIndex).getId();
        ModConfig.save();
        TtsManager.stop();
        rebuildWidgets();
    }

    private Component currentVoiceLabel() {
        if (voices.isEmpty()) {
            return Component.literal("没有可用声线");
        }
        return Component.literal("当前声线：" + voices.get(voiceIndex).getDisplayName());
    }

    private int findCurrentVoice() {
        for (int i = 0; i < voices.size(); i++) {
            if (voices.get(i).getId().equals(ModConfig.get().selectedVoice)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA101018);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
