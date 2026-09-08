package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.tts.ModelDownloadManager;
import com.mcvoice.ttvoice.tts.VoiceRegistry;
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
    private record ModelSpec(String id, boolean sherpa, String labelKey) {
    }

    private static final List<ModelSpec> MODEL_SPECS = List.of(
        new ModelSpec("zh_CN-huayan-medium", false, "download.mcvoice.medium"),
        new ModelSpec("zh_CN-huayan-x_low", false, "download.mcvoice.low"),
        new ModelSpec("vits-melo-tts-zh_en", true, "download.mcvoice.sherpa.melo"),
        new ModelSpec("vits-zh-hf-theresa", true, "download.mcvoice.sherpa.theresa"),
        new ModelSpec("vits-zh-hf-eula", true, "download.mcvoice.sherpa.eula"),
        new ModelSpec("vits-zh-hf-fanchen-wnj", true, "download.mcvoice.sherpa.fanchen"),
        new ModelSpec("sherpa-onnx-vits-zh-ll", true, "download.mcvoice.sherpa.xiaomi"),
        new ModelSpec("vits-piper-zh_CN-chaowen-medium", true,
            "download.mcvoice.sherpa.chaowen"),
        new ModelSpec("vits-piper-zh_CN-xiao_ya-medium", true,
            "download.mcvoice.sherpa.xiaoya")
    );

    private static final ModelDownloadManager MODEL_DOWNLOADS = ModelDownloadManager.get();

    private final Screen parent;
    private final Map<String, Button> modelButtons = new HashMap<>();
    private MultiLineTextWidget statusWidget;
    private volatile String status;
    private int scrollY;
    private int maxScrollY;

    public ModelDownloadScreen(Screen parent) {
        super(Component.translatable("download.mcvoice.title"));
        this.parent = parent;
        this.status = MODEL_DOWNLOADS.statusText();
        if (this.status == null || this.status.isBlank()) {
            this.status = Component.translatable("download.mcvoice.status.ready").getString();
        }
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonWidth = Math.min(360, width - 40);
        int x = centerX - buttonWidth / 2;
        modelButtons.clear();
        for (ModelSpec spec : MODEL_SPECS) {
            MODEL_DOWNLOADS.prepare(spec.id, spec.sherpa);
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
        int buttonY = 42 - scrollY;

        addRenderableWidget(new StringWidget(leftX, 31 - scrollY, columnWidth, 10,
            Component.translatable("download.mcvoice.section.piper"), font));
        modelButtons.put("zh_CN-huayan-medium", addButton(leftX, buttonY, columnWidth,
            "download.mcvoice.medium", "zh_CN-huayan-medium", false));
        modelButtons.put("zh_CN-huayan-x_low", addButton(leftX, buttonY + 20, columnWidth,
            "download.mcvoice.low", "zh_CN-huayan-x_low", false));

        addRenderableWidget(new StringWidget(rightX, 31 - scrollY, columnWidth, 10,
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
        modelButtons.put("vits-piper-zh_CN-chaowen-medium", addButton(rightX, buttonY + 100, columnWidth,
            "download.mcvoice.sherpa.chaowen", "vits-piper-zh_CN-chaowen-medium", true));
        modelButtons.put("vits-piper-zh_CN-xiao_ya-medium", addButton(rightX, buttonY + 120, columnWidth,
            "download.mcvoice.sherpa.xiaoya", "vits-piper-zh_CN-xiao_ya-medium", true));

        addRenderableWidget(Button.builder(
                Component.translatable("download.mcvoice.openFolder"),
                button -> VoiceRegistry.openMcVoiceFolder())
            .pos(centerX - buttonWidth / 2, 184 - scrollY)
            .size(buttonWidth, 18)
            .build());

        statusWidget = new MultiLineTextWidget(Component.literal(status), font);
        statusWidget.setX(x);
        statusWidget.setY(210 - scrollY);
        statusWidget.setMaxWidth(buttonWidth);
        statusWidget.setMaxRows(3);
        statusWidget.setCentered(false);
        addRenderableWidget(statusWidget);

        int availableHeight = Math.max(100, height - 50);
        maxScrollY = Math.max(0, 242 - availableHeight);
        int oldScroll = scrollY;
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
        if (scrollY != oldScroll) {
            rebuildWidgets();
            return;
        }

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
            ModelDownloadManager.State state = MODEL_DOWNLOADS.stateOf(spec.id, spec.sherpa);
            button.active = state == ModelDownloadManager.State.NOT_DOWNLOADED
                || state == ModelDownloadManager.State.RESUMABLE
                || state == ModelDownloadManager.State.FAILED;
            button.setMessage(buttonMessage(spec, state));
        }
        String currentStatus = MODEL_DOWNLOADS.statusText();
        if (currentStatus != null && !currentStatus.isBlank()) {
            status = currentStatus;
        }
        if (statusWidget != null) {
            statusWidget.setMessage(Component.literal(status));
        }
        super.tick();
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

    private Button addButton(int x, int y, int width, String labelKey, String modelId, boolean sherpa) {
        Button button = Button.builder(Component.translatable(labelKey), b -> startDownload(modelId, sherpa))
            .pos(x, y)
            .size(width, 18)
            .build();
        addRenderableWidget(button);
        return button;
    }

    private void startDownload(String modelId, boolean sherpa) {
        ModelSpec spec = specById(modelId);
        String label = Component.translatable(spec.labelKey).getString();
        MODEL_DOWNLOADS.start(modelId, sherpa, label);
    }

    private static ModelSpec specById(String modelId) {
        for (ModelSpec spec : MODEL_SPECS) {
            if (spec.id.equals(modelId)) {
                return spec;
            }
        }
        throw new IllegalArgumentException("未知模型: " + modelId);
    }

    private static Component buttonMessage(ModelSpec spec, ModelDownloadManager.State state) {
        return switch (state) {
            case DOWNLOADING -> Component.translatable("download.mcvoice.downloading");
            case DOWNLOADED -> Component.translatable("download.mcvoice.downloaded");
            case RESUMABLE -> Component.translatable("download.mcvoice.resumable");
            case FAILED -> Component.translatable("download.mcvoice.failed");
            case NOT_DOWNLOADED -> Component.translatable(spec.labelKey);
        };
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
