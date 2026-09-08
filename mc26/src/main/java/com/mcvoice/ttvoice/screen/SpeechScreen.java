package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.tts.TtsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SpeechScreen extends Screen {
    private static final List<String> HISTORY = new ArrayList<>();
    private static String DRAFT = "";
    private static final int ROW_STEP = 22;
    private static final int HISTORY_TOP = 20;

    private final Screen parent;
    private EditBox textBox;
    private Button speakButton;
    private Button stopButton;
    private Button lingerButton;

    private String pendingText = "";
    private int historyScroll = -1;
    private int selectedHistory = -1;
    private int historyLeft;
    private int historyWidth;
    private int textBoxY;

    public SpeechScreen(Screen parent) {
        super(Component.translatable("speech.mcvoice.title"));
        this.parent = parent;
        this.pendingText = ModConfig.get().lingerMode ? DRAFT : "";
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        historyWidth = Math.min(360, width - 40);
        historyLeft = centerX - historyWidth / 2;
        textBoxY = Math.max(120, height - 72);

        textBox = new EditBox(font, historyLeft, textBoxY, historyWidth, 20,
            Component.translatable("speech.mcvoice.placeholder"));
        textBox.setMaxLength(500);
        textBox.setResponder(value -> pendingText = value);
        textBox.setValue(pendingText);
        addRenderableWidget(textBox);
        setInitialFocus(textBox);

        int gap = 5;
        int columnWidth = (historyWidth - gap) / 2;
        int buttonRow1 = textBoxY + 24;
        int buttonRow2 = textBoxY + 44;

        speakButton = Button.builder(Component.translatable("speech.mcvoice.speak"),
                button -> speak())
            .pos(historyLeft, buttonRow1)
            .size(columnWidth, 20)
            .build();
        stopButton = Button.builder(Component.translatable("speech.mcvoice.stop"),
                button -> TtsManager.stop())
            .pos(historyLeft + columnWidth + gap, buttonRow1)
            .size(columnWidth, 20)
            .build();
        addRenderableWidget(speakButton);
        addRenderableWidget(stopButton);

        lingerButton = Button.builder(Component.literal(lingerLabel(columnWidth >= 100)),
                button -> toggleLinger())
            .pos(historyLeft, buttonRow2)
            .size(columnWidth, 20)
            .build();
        lingerButton.setTooltip(Tooltip.create(
            Component.translatable("speech.mcvoice.linger.tooltip")));
        addRenderableWidget(lingerButton);

        addRenderableWidget(Button.builder(Component.literal("返回"),
                button -> closeToParent())
            .pos(historyLeft + columnWidth + gap, buttonRow2)
            .size(columnWidth, 20)
            .build());

        int visibleRows = visibleRows();
        int maxScroll = Math.max(0, HISTORY.size() - visibleRows);
        if (historyScroll < 0 || historyScroll > maxScroll) {
            historyScroll = maxScroll;
        }

        if (!ModConfig.get().viewHistory) {
            return;
        }
        if (HISTORY.isEmpty()) {
            Button hint = Button.builder(Component.literal("还没有历史记录"),
                    button -> {
                    })
                .pos(historyLeft, HISTORY_TOP)
                .size(historyWidth, 20)
                .build();
            hint.active = false;
            addRenderableWidget(hint);
            return;
        }
        for (int i = historyScroll; i < Math.min(HISTORY.size(), historyScroll + visibleRows); i++) {
            String entry = HISTORY.get(i);
            int index = i;
            String shown = font.plainSubstrByWidth(entry, historyWidth - 16);
            Button row = Button.builder(Component.literal(shown),
                    button -> replayHistory(index))
                .pos(historyLeft, HISTORY_TOP + (i - historyScroll) * ROW_STEP)
                .size(historyWidth, 20)
                .build();
            row.setTooltip(Tooltip.create(Component.literal(entry)));
            addRenderableWidget(row);
        }
    }

    private int visibleRows() {
        return Math.max(1, (textBoxY - 26) / ROW_STEP);
    }

    private void speak() {
        String text = textBox.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        addHistory(text);
        TtsManager.speak(text);
        DRAFT = "";
        pendingText = "";
        closeScreen();
    }

    private void replayHistory(int index) {
        if (index < 0 || index >= HISTORY.size()) {
            return;
        }
        selectedHistory = index;
        TtsManager.speak(HISTORY.get(index));
    }

    private void toggleLinger() {
        ModConfig.get().lingerMode = !ModConfig.get().lingerMode;
        if (!ModConfig.get().lingerMode) {
            DRAFT = "";
        }
        ModConfig.save();
        rebuildWidgets();
    }

    private void saveDraftOnClose() {
        if (textBox != null) {
            pendingText = textBox.getValue();
        }
        if (ModConfig.get().lingerMode) {
            DRAFT = pendingText;
        } else {
            DRAFT = "";
        }
    }

    private void closeToParent() {
        saveDraftOnClose();
        ScreenUtil.setScreen(parent);
    }

    private void closeScreen() {
        if (parent != null) {
            ScreenUtil.setScreen(parent);
        } else {
            ScreenUtil.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        saveDraftOnClose();
        super.onClose();
    }

    private static void addHistory(String text) {
        HISTORY.add(text);
        while (HISTORY.size() > 50) {
            HISTORY.remove(0);
        }
    }

    private static String lingerLabel(boolean wide) {
        boolean on = ModConfig.get().lingerMode;
        if (wide) {
            return on ? "滞留模式：开" : "滞留模式：关";
        }
        return on ? "滞留:开" : "滞留:关";
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            speak();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
            double horizontalAmount, double verticalAmount) {
        int oldScroll = historyScroll;
        int delta = (int) Math.round(verticalAmount);
        int maxScroll = Math.max(0, HISTORY.size() - visibleRows());
        historyScroll = Math.max(0, Math.min(maxScroll, historyScroll - delta));
        if (historyScroll != oldScroll) {
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void tick() {
        if (stopButton != null) {
            stopButton.active = TtsManager.isSpeaking();
        }
        super.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context,
            int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA0E1620);
        super.extractRenderState(context, mouseX, mouseY, delta);
        drawHistorySelection(context);
        drawHistoryScrollBar(context);
    }

    private void drawHistorySelection(GuiGraphicsExtractor context) {
        if (selectedHistory < 0 || !ModConfig.get().viewHistory) {
            return;
        }
        int visible = visibleRows();
        if (selectedHistory < historyScroll || selectedHistory >= historyScroll + visible) {
            return;
        }
        int row = selectedHistory - historyScroll;
        int x = historyLeft - 1;
        int y = HISTORY_TOP + row * ROW_STEP - 1;
        int w = historyWidth + 2;
        int h = 22;
        int color = 0xFFFFFF55;
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawHistoryScrollBar(GuiGraphicsExtractor context) {
        if (!ModConfig.get().viewHistory || HISTORY.isEmpty()) {
            return;
        }
        int visible = visibleRows();
        if (HISTORY.size() <= visible) {
            return;
        }
        int trackTop = HISTORY_TOP;
        int trackBottom = textBoxY - 8;
        int trackHeight = trackBottom - trackTop;
        if (trackHeight < 20) {
            return;
        }
        int barX = Math.min(width - 8, historyLeft + historyWidth + 5);
        int maxScroll = HISTORY.size() - visible;
        float fraction = maxScroll <= 0 ? 0.0f : (float) historyScroll / maxScroll;
        int thumbHeight = Math.max(12,
            (int) (trackHeight * ((float) visible / HISTORY.size())));
        if (thumbHeight > trackHeight) {
            thumbHeight = trackHeight;
        }
        int thumbY = trackTop + (int) ((trackHeight - thumbHeight) * fraction);
        context.fill(barX, trackTop, barX + 3, trackBottom, 0x38000000);
        context.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, 0xB0FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
