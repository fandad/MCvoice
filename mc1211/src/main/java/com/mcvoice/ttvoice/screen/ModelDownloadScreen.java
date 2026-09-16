package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.tts.ModelDownloadManager;
import com.mcvoice.ttvoice.tts.Voice;
import com.mcvoice.ttvoice.tts.VoiceRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
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
            "download.mcvoice.sherpa.xiaoya"),
        new ModelSpec("vits-cantonese-hf-xiaomaiiwn", true,
            "download.mcvoice.sherpa.cantonese"),
        new ModelSpec("matcha-icefall-zh-baker", true,
            "download.mcvoice.sherpa.matcha"),
        new ModelSpec("kokoro-int8-multi-lang-v1_0", true,
            "download.mcvoice.sherpa.kokoro")
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

        addRenderableWidget(new StringWidget(x, 14 - scrollY, buttonWidth, 20,
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

        // 顶部提醒：部分模型没有稳定的国内下载源
        MultiLineTextWidget sourceNotice = new MultiLineTextWidget(
            Component.translatable("download.mcvoice.source.notice").withColor(0xFFFF55),
            font);
        sourceNotice.setX(x);
        sourceNotice.setY(30 - scrollY);
        sourceNotice.setMaxWidth(buttonWidth);
        sourceNotice.setMaxRows(2);
        sourceNotice.setCentered(false);
        addRenderableWidget(sourceNotice);

        int columnWidth = (buttonWidth - 8) / 2;
        int leftX = x;
        int rightX = x + columnWidth + 8;
        final int buttonH = 18;   // 按钮实际高度
        final int headerH = 20;   // 区块标题占位（含行高与余量）
        final int rowStep = 20;   // 行距
        final int sectionGap = 12;// 两块之间的额外留白
        int topY = 56 - scrollY;  // 第一块标题的顶边（为顶部提醒让出一行）
        int cursorY = topY;       // 当前元素的顶边
        List<Voice> installed = VoiceRegistry.listVoices();

        List<String> modelIds = new ArrayList<>();
        List<String> labelKeys = new ArrayList<>();
        List<Boolean> sherpaFlags = new ArrayList<>();
        modelIds.add("zh_CN-huayan-medium");
        labelKeys.add("download.mcvoice.medium");
        sherpaFlags.add(false);
        modelIds.add("zh_CN-huayan-x_low");
        labelKeys.add("download.mcvoice.low");
        sherpaFlags.add(false);
        for (ModelSpec spec : MODEL_SPECS) {
            if (!spec.sherpa()) {
                continue;
            }
            modelIds.add(spec.id());
            labelKeys.add(spec.labelKey());
            sherpaFlags.add(true);
        }

        // 2 列网格：同一行的左右两个按钮顶部对齐，逐行向下排。
        // 注意：每个模型都必须无条件建按钮（未下载的模型也要能点去下载），
        // 已安装声线只用于把多音色模型（Kokoro）的多个 speaker 指向同一个按钮。
        // 用 cursorY 逐个元素推进，标题也占位，避免标题压住按钮。
        boolean inSherpaGroup = false;
        for (int i = 0; i < modelIds.size(); i += 2) {
            boolean currentIsSherpa = sherpaFlags.get(i);
            if (!currentIsSherpa && i == 0) {
                addRenderableWidget(new StringWidget(leftX, cursorY, buttonWidth, headerH,
                    Component.translatable("download.mcvoice.section.piper"), font));
                cursorY += headerH;
            } else if (currentIsSherpa && !inSherpaGroup) {
                cursorY += sectionGap;
                addRenderableWidget(new StringWidget(leftX, cursorY, buttonWidth, headerH,
                    Component.translatable("download.mcvoice.section.sherpa"), font));
                cursorY += headerH;
            }
            inSherpaGroup = currentIsSherpa;
            addModelButtons(installed, leftX, cursorY, columnWidth, labelKeys.get(i),
                modelIds.get(i), sherpaFlags.get(i));
            if (i + 1 < modelIds.size()) {
                addModelButtons(installed, rightX, cursorY, columnWidth, labelKeys.get(i + 1),
                    modelIds.get(i + 1), sherpaFlags.get(i + 1));
            }
            cursorY += rowStep;
        }

        int folderY = cursorY + (buttonH - rowStep) + 18;
        int contentBottomY = folderY + 18 + 44;
        addRenderableWidget(Button.builder(
                Component.translatable("download.mcvoice.openFolder"),
                button -> VoiceRegistry.openMcVoiceFolder())
            .pos(centerX - buttonWidth / 2, folderY)
            .size(buttonWidth, 18)
            .build());

        statusWidget = new MultiLineTextWidget(Component.literal(status), font);
        statusWidget.setX(x);
        statusWidget.setY(folderY + 24);
        statusWidget.setMaxWidth(buttonWidth);
        statusWidget.setMaxRows(2);
        statusWidget.setCentered(false);
        addRenderableWidget(statusWidget);

        int availableHeight = Math.max(80, height - 96);
        maxScrollY = Math.max(0, contentBottomY - availableHeight);
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
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (int) Math.round(verticalAmount * 24)));
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

    /**
     * 为每个模型无条件建一个按钮——未下载的模型也要能点去下载。
     * 同时按“模型 ID”登记（供 tick() 更新下载状态），
     * 并把该模型已安装的每个 speaker 声线 id 也指向同一个按钮，
     * 这样多音色模型（Kokoro）不会重复建按钮，配置页选任一条声线时状态也一致。
     */
    private void addModelButtons(List<Voice> installed, int x, int y, int width,
                                 String labelKey, String modelId, boolean sherpa) {
        Button button = addButton(x, y, width, labelKey, modelId, sherpa);
        modelButtons.put(modelId, button);
        for (Voice voice : installed) {
            if (matchesModel(voice, modelId, sherpa)) {
                modelButtons.put(voice.getId(), button);
            }
        }
    }

    private static boolean matchesModel(Voice voice, String modelId, boolean sherpa) {
        String id = voice.getId();
        if (!sherpa) {
            return id.equals("piper:" + modelId);
        }
        return id.equals("sherpa:" + modelId)
            || id.startsWith("kokoro:" + modelId + ":")
            || id.startsWith("sherpa:" + modelId + "#");
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
