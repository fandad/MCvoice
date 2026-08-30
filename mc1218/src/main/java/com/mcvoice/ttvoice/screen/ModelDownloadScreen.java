package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.tts.PiperModelDownloader;
import com.mcvoice.ttvoice.tts.SherpaModelDownloader;
import com.mcvoice.ttvoice.tts.VoiceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelDownloadScreen extends Screen {
    private enum DownloadState {
        NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED
    }

    private record ModelSpec(String id, boolean sherpa, String labelKey) {
    }

    private static final List<ModelSpec> MODEL_SPECS = List.of(
        new ModelSpec("zh_CN-huayan-medium", false, "download.mcvoice.medium"),
        new ModelSpec("zh_CN-huayan-x_low", false, "download.mcvoice.low"),
        new ModelSpec("zh_CN-chaowen-medium", false, "download.mcvoice.chaowen"),
        new ModelSpec("vits-melo-tts-zh_en", true, "download.mcvoice.sherpa.melo"),
        new ModelSpec("vits-zh-hf-theresa", true, "download.mcvoice.sherpa.theresa"),
        new ModelSpec("vits-zh-hf-eula", true, "download.mcvoice.sherpa.eula"),
        new ModelSpec("vits-zh-hf-fanchen-wnj", true, "download.mcvoice.sherpa.fanchen"),
        new ModelSpec("sherpa-onnx-vits-zh-ll", true, "download.mcvoice.sherpa.xiaomi")
    );

    private final Screen parent;
    private final Map<String, DownloadState> modelStates = new HashMap<>();
    private final Map<String, Button> modelButtons = new HashMap<>();
    private MultiLineTextWidget statusWidget;
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
        for (ModelSpec spec : MODEL_SPECS) {
            modelStates.put(spec.id, VoiceRegistry.isModelDownloaded(spec.id, spec.sherpa)
                ? DownloadState.DOWNLOADED
                : DownloadState.NOT_DOWNLOADED);
        }

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
        modelButtons.put("zh_CN-huayan-medium", addButton(leftX, buttonY, columnWidth,
            "download.mcvoice.medium", "zh_CN-huayan-medium", false));
        modelButtons.put("zh_CN-huayan-x_low", addButton(leftX, buttonY + 20, columnWidth,
            "download.mcvoice.low", "zh_CN-huayan-x_low", false));
        modelButtons.put("zh_CN-chaowen-medium", addButton(leftX, buttonY + 40, columnWidth,
            "download.mcvoice.chaowen", "zh_CN-chaowen-medium", false));

        addRenderableWidget(new StringWidget(rightX, 31, columnWidth, 10,
            Component.translatable("download.mcvoice.section.sherpa"), font));
        modelButtons.put("vits-melo-tts-zh_en", addButton(rightX, buttonY, columnWidth,
            "download.mcvoice.sherpa.melo", "vits-melo-tts-zh_en", true));
        modelButtons.put("vits-zh-hf-theresa", addButton(rightX, buttonY + 20, columnWidth,
            "download.mcvoice.sherpa.theresa", "vits-zh-hf-theresa", true));
        modelButtons.put("vits-zh-hf-eula", addButton(rightX, buttonY + 40, columnWidth,
            "download.mcvoice.sherpa.eula", "vits-zh-hf-eula", true));
        modelButtons.put("vits-zh-hf-fanchen-wnj", addButton(rightX, buttonY + 60, columnWidth,
            "download.mcvoice.sherpa.fanchen", "vits-zh-hf-fanchen-wnj", true));
        modelButtons.put("sherpa-onnx-vits-zh-ll", addButton(rightX, buttonY + 80, columnWidth,
            "download.mcvoice.sherpa.xiaomi", "sherpa-onnx-vits-zh-ll", true));

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
        for (ModelSpec spec : MODEL_SPECS) {
            Button button = modelButtons.get(spec.id);
            if (button == null) {
                continue;
            }
            DownloadState state = modelStates.getOrDefault(spec.id, DownloadState.NOT_DOWNLOADED);
            button.active = state == DownloadState.NOT_DOWNLOADED || state == DownloadState.FAILED;
            button.setMessage(buttonMessage(spec, state));
        }
        if (statusWidget != null) {
            statusWidget.setMessage(Component.literal(status));
        }
        super.tick();
    }

    private Button addButton(int x, int y, int width, String labelKey, String modelId, boolean sherpa) {
        Button button = Button.builder(Component.translatable(labelKey), b -> startDownload(modelId, sherpa))
            .pos(x, y)
            .size(width, 18)
            .build();
        addRenderableWidget(button);
        return button;
    }

    private void startDownload(String modelId, boolean sherpa) {
        DownloadState state = modelStates.getOrDefault(modelId, DownloadState.NOT_DOWNLOADED);
        if (state == DownloadState.DOWNLOADING) {
            return;
        }
        ModelSpec spec = specById(modelId);
        String label = Component.translatable(spec.labelKey).getString();
        modelStates.put(modelId, DownloadState.DOWNLOADING);
        status = "准备下载：" + label;
        Thread thread = new Thread(() -> {
            try {
                if (sherpa) {
                    SherpaModelDownloader.download(modelId, VoiceRegistry.getSherpaModelDir(),
                        text -> Minecraft.getInstance().execute(() -> status = text));
                } else {
                    PiperModelDownloader.download(modelId, VoiceRegistry.getModelDir(),
                        text -> Minecraft.getInstance().execute(() -> status = text));
                }
                Minecraft.getInstance().execute(() -> {
                    modelStates.put(modelId, DownloadState.DOWNLOADED);
                    status = "已完成：" + label + " 已放入 mcvoice/models";
                });
            } catch (Exception e) {
                String error = e.getMessage() == null ? e.toString() : e.getMessage();
                Minecraft.getInstance().execute(() -> {
                    modelStates.put(modelId, DownloadState.FAILED);
                    status = "下载失败：" + error;
                });
            }
        }, "MCVoice-ModelDownload");
        thread.setDaemon(true);
        thread.start();
    }

    private static ModelSpec specById(String modelId) {
        for (ModelSpec spec : MODEL_SPECS) {
            if (spec.id.equals(modelId)) {
                return spec;
            }
        }
        throw new IllegalArgumentException("未知模型: " + modelId);
    }

    private static Component buttonMessage(ModelSpec spec, DownloadState state) {
        return switch (state) {
            case DOWNLOADING -> Component.translatable("download.mcvoice.downloading");
            case DOWNLOADED -> Component.translatable("download.mcvoice.downloaded");
            case FAILED -> Component.translatable("download.mcvoice.failed");
            case NOT_DOWNLOADED -> Component.translatable(spec.labelKey);
        };
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA101018);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
