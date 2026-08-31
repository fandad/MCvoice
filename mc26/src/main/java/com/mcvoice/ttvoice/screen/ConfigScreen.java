package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.VoiceChatBridge;
import com.mcvoice.ttvoice.plasmo.PlasmoVoiceBridge;
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
    private static final int COLOR_RED = 0xFFFF5555;
    private static final int COLOR_YELLOW = 0xFFFFFF55;
    private static final int COLOR_GREEN = 0xFF55FF55;

    private final Screen parent;
    private final List<Voice> voices;
    private int voiceIndex;
    private int scrollY;
    private int maxScrollY;
    private StringWidget svcStatusWidget;
    private StringWidget pvStatusWidget;
    private MultiLineTextWidget pvWarningWidget;
    private MultiLineTextWidget localWarningWidget;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("config.mcvoice.title"));
        this.parent = parent;
        this.voices = VoiceRegistry.listVoices();
        this.voiceIndex = findCurrentVoice();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonWidth = Math.min(320, width - 40);
        int x = centerX - buttonWidth / 2;

        addRenderableWidget(new StringWidget(x, 10, buttonWidth, 20,
            Component.translatable("config.mcvoice.title"), font));

        if (!VoiceRegistry.isWindowsSupported()) {
            MultiLineTextWidget unsupported = new MultiLineTextWidget(
                Component.translatable("config.mcvoice.unsupported"), font);
            unsupported.setX(x);
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

        int statusWidth = buttonWidth;
        svcStatusWidget = new StringWidget(x, 24 - scrollY, statusWidth, 14,
            Component.literal("SimpleVoiceChat"), font);
        pvStatusWidget = new StringWidget(x, 40 - scrollY, statusWidth, 14,
            Component.literal("PlasmoVoice"), font);
        addRenderableWidget(svcStatusWidget);
        addRenderableWidget(pvStatusWidget);

        pvWarningWidget = new MultiLineTextWidget(Component.literal(""), font);
        pvWarningWidget.setX(x);
        pvWarningWidget.setY(54 - scrollY);
        pvWarningWidget.setMaxWidth(buttonWidth);
        pvWarningWidget.setMaxRows(1);
        pvWarningWidget.setCentered(false);
        addRenderableWidget(pvWarningWidget);

        localWarningWidget = new MultiLineTextWidget(Component.literal(""), font);
        localWarningWidget.setX(x);
        localWarningWidget.setY(64 - scrollY);
        localWarningWidget.setMaxWidth(buttonWidth);
        localWarningWidget.setMaxRows(1);
        localWarningWidget.setCentered(false);
        addRenderableWidget(localWarningWidget);

        int y = 72 - scrollY;

        Button voiceButton = Button.builder(currentVoiceLabel(), button -> cycleVoice())
            .pos(x, y)
            .size(buttonWidth, 20)
            .build();
        addRenderableWidget(voiceButton);
        y += 20;

        int halfWidth = (buttonWidth - 5) / 2;
        Button openFolderButton = Button.builder(
                Component.translatable("config.mcvoice.models.open"),
                button -> VoiceRegistry.openMcVoiceFolder())
            .pos(x, y)
            .size(halfWidth, 20)
            .build();
        openFolderButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.models.open.tooltip")));
        addRenderableWidget(openFolderButton);

        Button downloadButton = Button.builder(
                Component.translatable("config.mcvoice.models.download"),
                button -> ScreenUtil.setScreen(new ModelDownloadScreen(this)))
            .pos(x + halfWidth + 5, y)
            .size(halfWidth, 20)
            .build();
        downloadButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.models.download.tooltip")));
        addRenderableWidget(downloadButton);
        y += 20;

        Checkbox routeCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.general.routeThroughVoiceChat"), font)
            .selected(ModConfig.get().routeThroughVoiceChat)
            .pos(x, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().routeThroughVoiceChat = selected;
                ModConfig.save();
            })
            .build();
        addRenderableWidget(routeCheckbox);
        y += 20;

        Checkbox hearSelfCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.general.hearSelf"), font)
            .selected(ModConfig.get().hearSelf)
            .pos(x, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().hearSelf = selected;
                ModConfig.save();
            })
            .build();
        addRenderableWidget(hearSelfCheckbox);

        Checkbox autoSpeakCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.general.autoSpeak"), font)
            .selected(ModConfig.get().autoSpeak)
            .pos(x + halfWidth + 5, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().autoSpeak = selected;
                ModConfig.save();
            })
            .build();
        addRenderableWidget(autoSpeakCheckbox);
        y += 20;

        Checkbox historyCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.ui.viewHistory"), font)
            .selected(ModConfig.get().viewHistory)
            .pos(x, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().viewHistory = selected;
                ModConfig.save();
            })
            .build();
        addRenderableWidget(historyCheckbox);
        y += 20;

        Button advancedButton = Button.builder(
                Component.translatable("config.mcvoice.advanced"),
                button -> ScreenUtil.setScreen(new AdvancedConfigScreen(this)))
            .pos(x, y)
            .size(buttonWidth, 20)
            .build();
        advancedButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.advanced.tooltip")));
        addRenderableWidget(advancedButton);
        y += 20;

        int availableHeight = Math.max(80, height - 66);
        maxScrollY = Math.max(0, (y - 14) - availableHeight);
        int oldScroll = scrollY;
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
        if (scrollY != oldScroll) {
            rebuildWidgets();
            return;
        }

        refreshConnectionStatus();

        addRenderableWidget(Button.builder(Component.translatable("speech.mcvoice.stop"),
                button -> TtsManager.stop())
            .pos(x, height - 38)
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

    private void refreshConnectionStatus() {
        if (svcStatusWidget == null || pvStatusWidget == null
                || pvWarningWidget == null || localWarningWidget == null) {
            return;
        }
        boolean svcInstalled = VoiceChatBridge.isInstalled();
        boolean svcConnected = VoiceChatBridge.isConnected();
        boolean pvInstalled = PlasmoVoiceBridge.isPvInstalled();
        boolean pvConnected = PlasmoVoiceBridge.isPvServerConnected();

        svcStatusWidget.setMessage(statusComponent("SimpleVoiceChat",
            svcConnected ? 2 : svcInstalled ? 1 : 0));
        pvStatusWidget.setMessage(statusComponent("PlasmoVoice",
            pvConnected ? 2 : pvInstalled ? 1 : 0));

        if (!svcConnected && !pvConnected) {
            localWarningWidget.setMessage(Component.literal("模组仅可以在本地生效，他人无法听到").withColor(COLOR_RED));
        } else {
            localWarningWidget.setMessage(Component.literal(""));
        }

        if (pvInstalled && !pvConnected) {
            pvWarningWidget.setMessage(
                Component.literal("Plasmo语音需要服务器安装MCvoice才可生效").withColor(COLOR_YELLOW));
        } else {
            pvWarningWidget.setMessage(Component.literal(""));
        }
    }

    private static Component statusComponent(String name, int state) {
        return switch (state) {
            case 0 -> Component.literal(name + " (未连接)").withColor(COLOR_RED);
            case 1 -> Component.literal(name + " (已安装,未连接)").withColor(COLOR_YELLOW);
            default -> Component.literal(name + " (已连接)").withColor(COLOR_GREEN);
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int oldScroll = scrollY;
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (int) Math.round(verticalAmount * 18)));
        if (scrollY != oldScroll) {
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void tick() {
        refreshConnectionStatus();
        super.tick();
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
