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
    private static final int COLOR_GREY = 0xFFAAAAAA;
    /** 英文吐槽的绘制缩放（MC 没有小号字体，只能自己缩放）。 */
    private static final float NOTE_SCALE = 0.75f;

    private String enOnlyNoteText = "";
    private int enOnlyNoteX;
    private int enOnlyNoteY;

    private final Screen parent;
    private List<Voice> voices = List.of();
    private int scrollY;
    private int maxScrollY;
    private StringWidget svcStatusWidget;
    private StringWidget pvStatusWidget;
    private MultiLineTextWidget pvWarningWidget;
    private MultiLineTextWidget localWarningWidget;
    private Button voiceButtonRef;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("config.mcvoice.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 每次显示都重新扫描，避免下载新模型或换过声线后列表过期。
        this.voices = VoiceRegistry.listVoices();
        int centerX = width / 2;
        int buttonWidth = Math.min(320, width - 40);
        int x = centerX - buttonWidth / 2;

        addRenderableWidget(new StringWidget(x, 10 - scrollY, buttonWidth, 20,
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

        Button voiceButton = Button.builder(currentVoiceLabel(),
                button -> ScreenUtil.setScreen(new VoiceSelectScreen(this)))
            .pos(x, y)
            .size(buttonWidth, 20)
            .build();
        voiceButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.voice.tooltip")));
        voiceButtonRef = voiceButton;
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

        // 英文界面限定的吐槽：中文语言文件里这个 key 是空串，所以中文玩家完全看不到、也不占位。
        // MC 没有小号字体，这里只记坐标，真正的 0.75 缩放绘制放在渲染方法里。
        enOnlyNoteText = Component.translatable("config.mcvoice.en_only_note").getString();
        if (!enOnlyNoteText.isEmpty()) {
            enOnlyNoteX = x;
            enOnlyNoteY = y;
            int noteLines = font.split(Component.literal(enOnlyNoteText), (int) (buttonWidth / NOTE_SCALE)).size();
            // 按缩放后的真实高度预留空间，避免与下方内容重叠或顶出屏幕。
            y += (int) Math.ceil(noteLines * (font.lineHeight + 1) * NOTE_SCALE) + 6;
        }

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
        addRenderableWidget(Button.builder(Component.translatable("gui.mcvoice.back"),
                button -> ScreenUtil.setScreen(parent))
            .pos(centerX + 5, height - 38)
            .size(buttonWidth / 2 - 5, 20)
            .build());
    }

    private Component currentVoiceLabel() {
        if (voices.isEmpty()) {
            return Component.translatable("config.mcvoice.voice.none");
        }
        for (Voice voice : voices) {
            if (voice.getId().equals(ModConfig.get().selectedVoice)) {
                return Component.translatable("config.mcvoice.voice.current", ScreenUtil.voiceName(voice));
            }
        }
        // 配置里记录的声线当前不存在（例如模型被删了），如实显示 ID，不冒充别的声线。
        return Component.translatable("config.mcvoice.voice.current", ModConfig.get().selectedVoice);
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
            localWarningWidget.setMessage(Component.translatable("config.mcvoice.warn.local_only").withColor(COLOR_RED));
        } else {
            localWarningWidget.setMessage(Component.literal(""));
        }

        if (pvInstalled && !pvConnected) {
            pvWarningWidget.setMessage(
                Component.translatable("config.mcvoice.warn.pv_needs_server").withColor(COLOR_YELLOW));
        } else {
            pvWarningWidget.setMessage(Component.literal(""));
        }
    }

    private static Component statusComponent(String name, int state) {
        return switch (state) {
            case 0 -> Component.translatable("config.mcvoice.state.disconnected", name).withColor(COLOR_RED);
            case 1 -> Component.translatable("config.mcvoice.state.installed", name).withColor(COLOR_YELLOW);
            default -> Component.translatable("config.mcvoice.state.connected", name).withColor(COLOR_GREEN);
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
        if (voiceButtonRef != null) {
            voiceButtonRef.setMessage(currentVoiceLabel());
        }
        super.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA101018);
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (!enOnlyNoteText.isEmpty()) {
            context.pose().pushMatrix();
            context.pose().scale(NOTE_SCALE, NOTE_SCALE);
            int noteLineY = enOnlyNoteY;
            for (var noteLine : font.split(Component.literal(enOnlyNoteText), (int) (Math.min(320, width - 40) / NOTE_SCALE))) {
                context.text(font, noteLine, (int) (enOnlyNoteX / NOTE_SCALE), (int) (noteLineY / NOTE_SCALE), COLOR_GREY);
                noteLineY += (int) ((font.lineHeight + 1) * NOTE_SCALE);
            }
            context.pose().popMatrix();
        }
        drawScrollBar(context);
    }

    private void drawScrollBar(GuiGraphicsExtractor context) {
        if (maxScrollY <= 0) {
            return;
        }
        int barX = width - 8;
        int trackTop = 10;
        int trackBottom = height - 46;
        int trackHeight = trackBottom - trackTop;
        if (trackHeight < 24) {
            return;
        }
        int contentHeight = maxScrollY + trackHeight;
        float fraction = Math.min(1.0f, (float) scrollY / maxScrollY);
        int thumbHeight = Math.max(16,
            (int) (trackHeight * ((float) trackHeight / contentHeight)));
        if (thumbHeight > trackHeight) {
            thumbHeight = trackHeight;
        }
        int thumbY = trackTop + (int) ((trackHeight - thumbHeight) * fraction);
        context.fill(barX, trackTop, barX + 3, trackBottom, 0x40000000);
        context.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, 0xC0FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
