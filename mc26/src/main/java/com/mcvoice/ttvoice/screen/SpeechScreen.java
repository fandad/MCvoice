package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.tts.TtsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SpeechScreen extends Screen {
    private static final List<String> HISTORY = new ArrayList<>();

    private final Screen parent;
    private EditBox textBox;
    private Button speakButton;
    private Button stopButton;
    private MultiLineTextWidget historyWidget;

    public SpeechScreen(Screen parent) {
        super(Component.translatable("speech.mcvoice.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int boxWidth = Math.min(360, width - 40);
        boolean compact = boxWidth < 3 * 60 + 20;
        int textBoxY = height - 48 - (compact ? 24 : 0);

        textBox = new EditBox(font, centerX - boxWidth / 2, textBoxY, boxWidth, 20,
            Component.translatable("speech.mcvoice.placeholder"));
        textBox.setMaxLength(500);
        textBox.setValue("");
        addRenderableWidget(textBox);
        setInitialFocus(textBox);

        int buttonY = textBoxY + 24;
        int buttonWidth;
        if (compact) {
            buttonWidth = Math.max(70, (boxWidth - 5) / 2);
        } else {
            buttonWidth = Math.max(60, (boxWidth - 10) / 3);
        }
        speakButton = Button.builder(Component.translatable("speech.mcvoice.speak"),
                button -> speak())
            .pos(centerX - boxWidth / 2, buttonY)
            .size(buttonWidth, 20)
            .build();
        stopButton = Button.builder(Component.translatable("speech.mcvoice.stop"),
                button -> TtsManager.stop())
            .pos(centerX - boxWidth / 2 + buttonWidth + 5, buttonY)
            .size(buttonWidth, 20)
            .build();
        addRenderableWidget(speakButton);
        addRenderableWidget(stopButton);

        int backY = compact ? buttonY + 24 : buttonY;
        int backX = compact ? centerX - 30 : centerX + boxWidth / 2 - buttonWidth;
        addRenderableWidget(Button.builder(Component.literal("返回"),
                button -> ScreenUtil.setScreen(parent))
            .pos(backX, backY)
            .size(compact ? 60 : buttonWidth, 20)
            .build());

        historyWidget = new MultiLineTextWidget(Component.literal(buildHistory()), font);
        historyWidget.setX(centerX - boxWidth / 2);
        historyWidget.setY(20);
        historyWidget.setMaxWidth(boxWidth);
        historyWidget.setMaxRows(Math.max(1, (textBoxY - 30) / (font.lineHeight + 2)));
        historyWidget.setCentered(false);
        addRenderableWidget(historyWidget);
    }

    private void speak() {
        String text = textBox.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        HISTORY.add(text);
        while (HISTORY.size() > 50) {
            HISTORY.remove(0);
        }
        TtsManager.speak(text);
        historyWidget.setMessage(Component.literal(buildHistory()));
        if (parent != null) {
            ScreenUtil.setScreen(parent);
        } else {
            ScreenUtil.setScreen(null);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            speak();
            return true;
        }
        return super.keyPressed(event);
    }

    private String buildHistory() {
        if (HISTORY.isEmpty()) {
            return ModConfig.get().viewHistory ? "还没有历史记录" : "";
        }
        return String.join("\n", HISTORY);
    }

    @Override
    public void tick() {
        stopButton.active = TtsManager.isSpeaking();
        super.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA0E1620);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
