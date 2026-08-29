package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.tts.PiperModelDownloader;
import com.mcvoice.ttvoice.tts.SherpaModelDownloader;
import com.mcvoice.ttvoice.tts.VoiceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModelDownloadScreen extends Screen {
    private final Screen parent;
    private final List<Button> modelButtons = new ArrayList<>();
    private MultiLineTextWidget statusWidget;
    private final AtomicBoolean downloading = new AtomicBoolean(false);
    private volatile String status = "";

    public ModelDownloadScreen(Screen parent) {
        super(Component.translatable("download.mcvoice.title"));
        this.parent = parent;
        this.status = Component.translatable("download.mcvoice.status.ready").getString();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonWidth = Math.min(360, width - 40);
        int x = centerX - buttonWidth / 2;
        modelButtons.clear();

        addRenderableWidget(new StringWidget(x, 14, buttonWidth, 20,
            Component.translatable("download.mcvoice.title"), font));

        if (!VoiceRegistry.isWindowsSupported()) {
            MultiLineTextWidget unsupported = new MultiLineTextWidget(
                Component.translatable("download.mcvoice.unsupported"), font);
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

        int columnWidth = (buttonWidth - 8) / 2;
        int leftX = x;
        int rightX = x + columnWidth + 8;
        int buttonY = 42;

        addRenderableWidget(new StringWidget(leftX, 31, columnWidth, 10,
            Component.translatable("download.mcvoice.section.piper"), font));
        modelButtons.add(addButton(leftX, buttonY, columnWidth,
            Component.translatable("download.mcvoice.medium"), "zh_CN-huayan-medium", false));
        modelButtons.add(addButton(leftX, buttonY + 20, columnWidth,
            Component.translatable("download.mcvoice.low"), "zh_CN-huayan-x_low", false));
        modelButtons.add(addButton(leftX, buttonY + 40, columnWidth,
            Component.translatable("download.mcvoice.chaowen"), "zh_CN-chaowen-medium", false));

        addRenderableWidget(new StringWidget(rightX, 31, columnWidth, 10,
            Component.translatable("download.mcvoice.section.sherpa"), font));
        modelButtons.add(addButton(rightX, buttonY, columnWidth,
            Component.translatable("download.mcvoice.sherpa.melo"), "vits-melo-tts-zh_en", true));
        modelButtons.add(addButton(rightX, buttonY + 20, columnWidth,
            Component.translatable("download.mcvoice.sherpa.theresa"), "vits-zh-hf-theresa", true));
        modelButtons.add(addButton(rightX, buttonY + 40, columnWidth,
            Component.translatable("download.mcvoice.sherpa.eula"), "vits-zh-hf-eula", true));
        modelButtons.add(addButton(rightX, buttonY + 60, columnWidth,
            Component.translatable("download.mcvoice.sherpa.fanchen"), "vits-zh-hf-fanchen-wnj", true));
        modelButtons.add(addButton(rightX, buttonY + 80, columnWidth,
            Component.translatable("download.mcvoice.sherpa.xiaomi"), "sherpa-onnx-vits-zh-ll", true));

        addRenderableWidget(Button.builder(
                Component.translatable("download.mcvoice.openFolder"),
                button -> VoiceRegistry.openMcVoiceFolder())
            .pos(centerX - buttonWidth / 2, 150)
            .size(buttonWidth, 18)
            .build());

        statusWidget = new MultiLineTextWidget(Component.literal(status), font);
        statusWidget.setX(x);
        statusWidget.setY(174);
        statusWidget.setMaxWidth(buttonWidth);
        statusWidget.setMaxRows(3);
        statusWidget.setCentered(false);
        addRenderableWidget(statusWidget);

        addRenderableWidget(Button.builder(Component.translatable("speech.mcvoice.back"),
                button -> ScreenUtil.setScreen(parent))
            .pos(centerX - 50, height - 32)
            .size(100, 20)
            .build());
    }

    @Override
    public void tick() {
        for (Button button : modelButtons) {
            button.active = !downloading.get();
        }
        if (statusWidget != null) {
            statusWidget.setMessage(Component.literal(status));
        }
        super.tick();
    }

    private Button addButton(int x, int y, int width, Component label, String modelId, boolean sherpa) {
        Button button = Button.builder(label, b -> startDownload(modelId, sherpa))
            .pos(x, y)
            .size(width, 18)
            .build();
        addRenderableWidget(button);
        return button;
    }

    private void startDownload(String modelId, boolean sherpa) {
        if (!downloading.compareAndSet(false, true)) {
            return;
        }
        status = "准备下载...";
        Thread thread = new Thread(() -> {
            try {
                if (sherpa) {
                    SherpaModelDownloader.download(modelId, VoiceRegistry.getSherpaModelDir(),
                        text -> Minecraft.getInstance().execute(() -> status = text));
                } else {
                    PiperModelDownloader.download(modelId, VoiceRegistry.getModelDir(),
                        text -> Minecraft.getInstance().execute(() -> status = text));
                }
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status = "下载失败：" + e.getMessage());
            } finally {
                Minecraft.getInstance().execute(() -> downloading.set(false));
            }
        }, "MCVoice-ModelDownload");
        thread.setDaemon(true);
        thread.start();
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
