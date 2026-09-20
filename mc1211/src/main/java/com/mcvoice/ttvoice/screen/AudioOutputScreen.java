package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.tts.AudioOutputs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 音频输出设置：把 TTS 额外送到指定的音频输出设备。
 *
 * <p>典型用途是虚拟声卡（VB-CABLE / VoiceMeeter）：mod 写进 "CABLE Input"，其他软件把
 * 麦克风选成 "CABLE Output"，就等于把游戏内打字当成麦克风了。Windows 上"录音设备"必须
 * 由驱动提供，程序无法凭空出现在麦克风列表里，所以只能走这条路。
 *
 * <p>滚动布局遵循项目既有约定：{@code contentBottomY} 只用**未滚动**的布局常量计算，
 * 绝不让 {@code scrollY} 参与，否则越滚上限越小、永远到不了底。
 */
public class AudioOutputScreen extends Screen {
    private static final int ROW_STEP = 20;
    private static final int BUTTON_HEIGHT = 18;
    /** 设备列表的起始内容坐标（未滚动）。 */
    private static final int LIST_TOP = 100;
    /** 底部按钮预留高度，保证内容能被滚到、也不与返回按钮重叠。 */
    private static final int BOTTOM_RESERVE = 96;
    /** 说明文字的预留行数（多留一行，宁多勿少，避免溢出屏幕）。 */
    private static final int HINT_ROWS = 5;

    private final Screen parent;
    private List<AudioOutputs.Device> devices = List.of();
    private int scrollY;
    private int maxScrollY;
    private int contentBottomY;

    public AudioOutputScreen(Screen parent) {
        super(Component.translatable("config.mcvoice.audioout.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonWidth = Math.min(360, width - 40);
        int x = centerX - buttonWidth / 2;

        // 每次显示/滚动都重新枚举：设备可能被热插拔（init 在每次 rebuildWidgets 时都会跑）。
        devices = AudioOutputs.list();
        List<AudioOutputs.Device> ordered = new ArrayList<>(devices);
        ordered.sort((a, b) -> Boolean.compare(b.virtualCable(), a.virtualCable()));

        addRenderableWidget(new StringWidget(x, 10 - scrollY, buttonWidth, 20,
            Component.translatable("config.mcvoice.audioout.title"), font));

        Button modeButton = Button.builder(modeLabel(), button -> cycleMode())
            .pos(x, 32 - scrollY)
            .size(buttonWidth, 20)
            .build();
        modeButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.audioout.mode.tooltip")));
        addRenderableWidget(modeButton);

        AbstractSliderButton volumeSlider = new AbstractSliderButton(
            x, 56 - scrollY, buttonWidth, 20,
            volumeLabel(ModConfig.get().audioOutVolume),
            Math.max(0.0, Math.min(1.0, ModConfig.get().audioOutVolume / 2.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(volumeLabel((float) (value * 2.0)));
            }

            @Override
            protected void applyValue() {
                ModConfig.get().audioOutVolume = (float) Math.round(value * 200) / 100.0f;
                ModConfig.save();
            }
        };
        volumeSlider.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.audioout.volume.tooltip")));
        addRenderableWidget(volumeSlider);

        addRenderableWidget(new StringWidget(x, 80 - scrollY, buttonWidth, 10,
            Component.translatable("config.mcvoice.audioout.section"), font));

        // contentY 只用于算滚动上限（不含 scrollY）；listY 才是实际摆放坐标。
        int contentY = LIST_TOP;
        int listY = contentY - scrollY;
        String current = ModConfig.get().audioOutDevice == null ? "" : ModConfig.get().audioOutDevice;
        if (ordered.isEmpty()) {
            MultiLineTextWidget empty = new MultiLineTextWidget(
                Component.translatable("config.mcvoice.audioout.empty"), font);
            empty.setX(x);
            empty.setY(listY);
            empty.setMaxWidth(buttonWidth);
            empty.setMaxRows(3);
            empty.setCentered(false);
            addRenderableWidget(empty);
            contentY += 3 * (font.lineHeight + 1) + 6;
            listY = contentY - scrollY;
        } else {
            for (AudioOutputs.Device device : ordered) {
                boolean selected = device.name().equals(current);
                String label = device.virtualCable()
                    ? device.name() + "  ★ " + Component.translatable("config.mcvoice.audioout.recommended").getString()
                    : device.name();
                Button deviceButton = Button.builder(Component.literal(label), button -> {
                        ModConfig.get().audioOutDevice = device.name();
                        ModConfig.save();
                        rebuildWidgets();
                    })
                    .pos(x, listY)
                    .size(buttonWidth, BUTTON_HEIGHT)
                    .build();
                deviceButton.active = !selected;
                deviceButton.setTooltip(Tooltip.create(Component.literal(device.name())
                    .append(Component.literal("\n"))
                    .append(Component.translatable(selected
                        ? "config.mcvoice.audioout.device.current"
                        : "config.mcvoice.audioout.device.select"))
                    .append(Component.literal("\n"))
                    .append(Component.translatable(device.virtualCable()
                        ? "config.mcvoice.audioout.device.virtual"
                        : "config.mcvoice.audioout.device.physical"))));
                addRenderableWidget(deviceButton);
                contentY += ROW_STEP;
                listY += ROW_STEP;
            }
        }

        int hintY = listY + 6;
        contentY += 6 + HINT_ROWS * (font.lineHeight + 1);
        MultiLineTextWidget hint = new MultiLineTextWidget(
            Component.translatable("config.mcvoice.audioout.hint"), font);
        hint.setX(x);
        hint.setY(hintY);
        hint.setMaxWidth(buttonWidth);
        hint.setMaxRows(HINT_ROWS);
        hint.setCentered(false);
        addRenderableWidget(hint);

        contentBottomY = contentY;
        int availableHeight = Math.max(80, height - BOTTOM_RESERVE);
        maxScrollY = Math.max(0, contentBottomY - availableHeight);
        int oldScroll = scrollY;
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
        if (scrollY != oldScroll) {
            rebuildWidgets();
            return;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.mcvoice.back"),
                button -> ScreenUtil.setScreen(parent))
            .pos(centerX - 50, height - 32)
            .size(100, 20)
            .build());
    }

    private static String currentMode() {
        String mode = ModConfig.get().audioOutMode;
        return mode == null || mode.isBlank() ? "game" : mode;
    }

    private static Component modeLabel() {
        String key = switch (currentMode()) {
            case "device" -> "config.mcvoice.audioout.mode.device";
            case "both" -> "config.mcvoice.audioout.mode.both";
            default -> "config.mcvoice.audioout.mode.game";
        };
        return Component.translatable("config.mcvoice.audioout.mode.label", Component.translatable(key));
    }

    private static Component volumeLabel(float volume) {
        return Component.translatable("config.mcvoice.audioout.volume.label", Math.round(volume * 100));
    }

    private void cycleMode() {
        ModConfig.get().audioOutMode = switch (currentMode()) {
            case "game" -> "device";
            case "device" -> "both";
            default -> "game";
        };
        ModConfig.save();
        rebuildWidgets();
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA101018);
        super.render(context, mouseX, mouseY, delta);
        drawScrollBar(context);
    }

    private void drawScrollBar(GuiGraphics context) {
        if (maxScrollY <= 0) {
            return;
        }
        int barX = width - 8;
        int trackTop = 10;
        int trackBottom = height - 40;
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
