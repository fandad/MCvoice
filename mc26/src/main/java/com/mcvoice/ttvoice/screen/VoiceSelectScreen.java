package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.tts.TtsManager;
import com.mcvoice.ttvoice.tts.Voice;
import com.mcvoice.ttvoice.tts.VoiceRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 声线选择界面：列出识别到的全部声线（多音色模型会按 speaker 分成多个条目）。
 * 点击即选中并返回配置页；当前生效的那条按钮会变暗且不可点击。
 */
public class VoiceSelectScreen extends Screen {
    /** 行距（按钮高 18 + 间隔）。 */
    private static final int ROW_STEP = 20;
    /** 列表顶部（固定标题之下）。 */
    private static final int LIST_TOP = 40;
    /** 底部预留：返回按钮 + 其上方空白，保证滚到底时最后一项不会被按钮压住。 */
    private static final int BOTTOM_RESERVE = 96;

    private final Screen parent;
    private final List<Voice> voices;
    private int scrollY;
    private int maxScrollY;

    public VoiceSelectScreen(Screen parent) {
        super(Component.translatable("config.mcvoice.voice.select.title"));
        this.parent = parent;
        this.voices = VoiceRegistry.listVoices();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonWidth = Math.min(360, width - 40);
        int x = centerX - buttonWidth / 2;

        addRenderableWidget(new StringWidget(x, 14 - scrollY, buttonWidth, 20,
            Component.translatable("config.mcvoice.voice.select.title"), font));

        // 内容底与滚动上限都按「未滚动的内容坐标」计算，
        // 绝不能把 scrollY 混进来，否则越滚上限越小、永远到不了底。
        int contentBottomY = LIST_TOP + Math.max(1, voices.size()) * ROW_STEP;
        int availableHeight = Math.max(80, height - BOTTOM_RESERVE);
        maxScrollY = Math.max(0, contentBottomY - availableHeight);
        int oldScroll = scrollY;
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
        if (scrollY != oldScroll) {
            rebuildWidgets();
            return;
        }

        if (voices.isEmpty()) {
            addRenderableWidget(new StringWidget(x, LIST_TOP, buttonWidth, 20,
                Component.translatable("config.mcvoice.voice.empty"), font));
            addBackButton(centerX, buttonWidth);
            return;
        }

        String selected = ModConfig.get().selectedVoice;
        int cursorY = LIST_TOP - scrollY;
        for (Voice voice : voices) {
            boolean current = voice.getId().equals(selected);
            Button button = Button.builder(ScreenUtil.voiceName(voice), b -> choose(voice))
                .pos(x, cursorY)
                .size(buttonWidth, 18)
                .build();
            // 当前生效的那条：禁用（渲染成灰暗色、不可点击），与“已下载”按钮一致。
            button.active = !current;
            addRenderableWidget(button);
            cursorY += ROW_STEP;
        }

        addBackButton(centerX, buttonWidth);
    }

    private void addBackButton(int centerX, int buttonWidth) {
        addRenderableWidget(Button.builder(Component.translatable("speech.mcvoice.back"),
                button -> ScreenUtil.setScreen(parent))
            .pos(centerX - buttonWidth / 2, height - 32)
            .size(buttonWidth, 20)
            .build());
    }

    private void choose(Voice voice) {
        if (!voice.getId().equals(ModConfig.get().selectedVoice)) {
            ModConfig.get().selectedVoice = voice.getId();
            ModConfig.save();
            TtsManager.stop();
        }
        ScreenUtil.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int oldScroll = scrollY;
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (int) Math.round(verticalAmount * 24)));
        if (scrollY != oldScroll) {
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA101018);
        super.extractRenderState(context, mouseX, mouseY, delta);
        drawScrollBar(context);
    }

    private void drawScrollBar(GuiGraphicsExtractor context) {
        if (maxScrollY <= 0) {
            return;
        }
        int barX = width - 8;
        int trackTop = LIST_TOP;
        int trackBottom = height - 44;
        int trackHeight = trackBottom - trackTop;
        if (trackHeight < 24) {
            return;
        }
        int contentHeight = maxScrollY + trackHeight;
        float fraction = Math.min(1.0f, (float) scrollY / maxScrollY);
        int thumbHeight = Math.max(16, (int) (trackHeight * ((float) trackHeight / contentHeight)));
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
