package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.tts.ExternalServiceEngine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ExternalTtsServiceScreen extends Screen {
    private static final String EDGE_DIRECT = "edge-direct";
    private static final String APIZERO_URL = "https://v1.apizero.cn/api/tts";

    private final Screen parent;
    private EditBox urlBox;
    private EditBox keyBox;
    private EditBox voiceBox;
    private EditBox modelBox;
    private Button voiceChoiceButton;
    private Button freeRouteButton;
    private boolean updatingUrl;
    private int scrollY;
    private int maxScrollY;

    public ExternalTtsServiceScreen(Screen parent) {
        super(Component.translatable("config.mcvoice.external.service.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        voiceChoiceButton = null;
        int centerX = width / 2;
        int buttonWidth = Math.min(360, width - 40);
        int x = centerX - buttonWidth / 2;
        int y = 36 - scrollY;

        addRenderableWidget(new StringWidget(centerX - buttonWidth / 2, 14, buttonWidth, 20,
            Component.translatable("config.mcvoice.external.service.title"), font));

        Checkbox enableCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.external.service.enable"), font)
            .selected(ModConfig.get().externalServiceTts)
            .pos(x, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().externalServiceTts = selected;
                if (selected) {
                    ModConfig.get().externalTts = false;
                }
                ModConfig.save();
            })
            .build();
        enableCheckbox.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.enable.tooltip")));
        addRenderableWidget(enableCheckbox);
        y += 20;

        Button modeButton = Button.builder(Component.literal(modeLabel()), button -> cycleMode())
            .pos(x, y)
            .size(buttonWidth, 20)
            .build();
        modeButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.mode.tooltip")));
        addRenderableWidget(modeButton);
        y += 20;

        if (freeMode()) {
            String url = ModConfig.get().serviceUrl;
            if (isApizeroUrl(url)) {
                if (!apizeroVoiceOptions().contains(ModConfig.get().serviceVoice)) {
                    ModConfig.get().serviceVoice = apizeroVoiceOptions().get(0);
                }
            } else {
                ModConfig.get().serviceUrl = EDGE_DIRECT;
                if (!edgeVoiceOptions().contains(ModConfig.get().serviceVoice)) {
                    ModConfig.get().serviceVoice = edgeVoiceOptions().get(0);
                }
            }
            ModConfig.save();
            freeRouteButton = Button.builder(
                    Component.literal(freeRouteLabel()),
                    button -> cycleFreeRoute())
                .pos(x, y)
                .size(buttonWidth, 20)
                .build();
            freeRouteButton.setTooltip(Tooltip.create(
                Component.translatable("config.mcvoice.external.service.route.tooltip")));
            addRenderableWidget(freeRouteButton);
            y += 20;
        }

        AbstractSliderButton serviceVolumeSlider = new AbstractSliderButton(
            x, y, buttonWidth, 20,
            Component.literal("服务输出音量：" + Math.round(ModConfig.get().serviceVolume) + "%"),
            Math.max(0.0, Math.min(1.0, ModConfig.get().serviceVolume / 200.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("服务输出音量：" + Math.round(value * 200) + "%"));
            }

            @Override
            protected void applyValue() {
                ModConfig.get().serviceVolume = (float) Math.round(value * 200);
                ModConfig.save();
            }
        };
        serviceVolumeSlider.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.volume.tooltip")));
        addRenderableWidget(serviceVolumeSlider);
        y += 24;

        if (!freeMode()) {
            addRenderableWidget(new StringWidget(x, y, buttonWidth, 10,
                Component.literal(urlLabel()), font));
            y += 11;
            urlBox = new EditBox(font, x, y, buttonWidth, 20,
                Component.literal(urlHint()));
            urlBox.setMaxLength(1000);
            urlBox.setValue(ModConfig.get().serviceUrl);
            urlBox.setResponder(text -> {
                if (updatingUrl) {
                    return;
                }
                String value = text.isBlank() ? freeRouteUrls().get(0) : text;
                ModConfig.get().serviceUrl = value;
                if (!freeVoiceOptions().contains(ModConfig.get().serviceVoice)) {
                    ModConfig.get().serviceVoice = freeVoiceOptions().get(0);
                }
                ModConfig.save();
                if (!text.equals(value)) {
                    updatingUrl = true;
                    urlBox.setValue(value);
                    updatingUrl = false;
                }
            });
            urlBox.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.url.tooltip")));
            addRenderableWidget(urlBox);
            y += 30;
        }

        if (freeMode()) {
            addRenderableWidget(new StringWidget(x, y, buttonWidth, 10,
                Component.translatable("config.mcvoice.external.service.voice.label"), font));
            y += 11;
            voiceChoiceButton = Button.builder(
                    Component.literal(freeVoiceLabel(currentFreeVoice())),
                    button -> cycleFreeVoice())
                .pos(x, y)
                .size(buttonWidth, 20)
                .build();
            voiceChoiceButton.setTooltip(Tooltip.create(
                Component.translatable("config.mcvoice.external.service.voice.free.tooltip")));
            addRenderableWidget(voiceChoiceButton);
        } else {
            addRenderableWidget(new StringWidget(x, y, buttonWidth, 10,
                Component.literal(keyLabel()), font));
            y += 11;
            keyBox = new EditBox(font, x, y, buttonWidth, 20,
                Component.literal(keyHint()));
            keyBox.setMaxLength(1000);
            keyBox.setValue(ModConfig.get().serviceApiKey);
            keyBox.setResponder(text -> {
                ModConfig.get().serviceApiKey = text;
                ModConfig.save();
            });
            keyBox.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.key.tooltip")));
            addRenderableWidget(keyBox);
            y += 30;

            addRenderableWidget(new StringWidget(x, y, buttonWidth, 10,
                Component.literal(voiceLabel()), font));
            y += 11;
            voiceBox = new EditBox(font, x, y, buttonWidth, 20,
                Component.literal(voiceHint()));
            voiceBox.setMaxLength(200);
            voiceBox.setValue(ModConfig.get().serviceVoice);
            voiceBox.setResponder(text -> {
                ModConfig.get().serviceVoice = text;
                ModConfig.save();
            });
            voiceBox.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.voice.tooltip")));
            addRenderableWidget(voiceBox);
            y += 30;

            addRenderableWidget(new StringWidget(x, y, buttonWidth, 10,
                Component.literal(modelLabel()), font));
            y += 11;
            modelBox = new EditBox(font, x, y, buttonWidth, 20,
                Component.literal(modelHint()));
            modelBox.setMaxLength(200);
            modelBox.setValue(ModConfig.get().serviceModel);
            modelBox.setResponder(text -> {
                ModConfig.get().serviceModel = text;
                ModConfig.save();
            });
            modelBox.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.model.tooltip")));
            addRenderableWidget(modelBox);
        }

        int availableHeight = Math.max(80, height - 66);
        maxScrollY = Math.max(0, (y + 90) - availableHeight);
        int oldScroll = scrollY;
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
        if (scrollY != oldScroll) {
            rebuildWidgets();
            return;
        }

        addRenderableWidget(Button.builder(Component.literal("返回"),
                button -> ScreenUtil.setScreen(parent))
            .pos(centerX - 50, height - 32)
            .size(100, 20)
            .build());
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

    private String modeLabel() {
        if (ExternalServiceEngine.isFreeMode(ModConfig.get().serviceMode)) {
            return "请求方式：免费 TTS";
        }
        return "openai".equalsIgnoreCase(ModConfig.get().serviceMode)
            ? "请求方式：OpenAI兼容"
            : "请求方式：URL模板";
    }

    private void cycleMode() {
        String mode = ModConfig.get().serviceMode;
        if ("url".equalsIgnoreCase(mode)) {
            ModConfig.get().serviceMode = "openai";
        } else if ("openai".equalsIgnoreCase(mode)) {
            ModConfig.get().serviceMode = "edge";
        } else {
            ModConfig.get().serviceMode = "url";
        }
        if (freeMode()) {
            ModConfig.get().serviceUrl = EDGE_DIRECT;
            if (!edgeVoiceOptions().contains(ModConfig.get().serviceVoice)) {
                ModConfig.get().serviceVoice = edgeVoiceOptions().get(0);
            }
        }
        ModConfig.save();
        rebuildWidgets();
    }

    private boolean freeMode() {
        return ExternalServiceEngine.isFreeMode(ModConfig.get().serviceMode);
    }

    private String urlLabel() {
        return "服务地址";
    }

    private String urlHint() {
        if (freeMode()) {
            return "https://ttsapi.cn";
        }
        return "openai".equalsIgnoreCase(ModConfig.get().serviceMode)
            ? "https://api.openai.com/v1/audio/speech"
            : "http://127.0.0.1:9880?text={text}&voice={voice}";
    }

    private String keyLabel() {
        return "API Key";
    }

    private String keyHint() {
        return "留空表示不需要鉴权";
    }

    private String voiceLabel() {
        return "音色";
    }

    private String voiceHint() {
        return freeMode() ? "例如 female_zhubo" : "例如 zh-CN-XiaoyiNeural";
    }

    private String modelLabel() {
        return "模型";
    }

    private String modelHint() {
        return "例如 tts-1，留空也可以";
    }

    @Override
    public void tick() {
        if (voiceChoiceButton != null) {
            voiceChoiceButton.setMessage(Component.literal(freeVoiceLabel(currentFreeVoice())));
        }
        if (freeRouteButton != null) {
            freeRouteButton.setMessage(Component.literal(freeRouteLabel()));
        }
        super.tick();
    }

    private void cycleFreeRoute() {
        List<String> routes = freeRouteUrls();
        String current = ModConfig.get().serviceUrl == null ? "" : ModConfig.get().serviceUrl;
        int index = routes.indexOf(normalizeRoute(current));
        if (index < 0) {
            index = 0;
        }
        ModConfig.get().serviceUrl = routes.get((index + 1) % routes.size());
        if (!freeVoiceOptions().contains(ModConfig.get().serviceVoice)) {
            ModConfig.get().serviceVoice = freeVoiceOptions().get(0);
        }
        ModConfig.save();
        rebuildWidgets();
    }

    private void cycleFreeVoice() {
        List<String> options = freeVoiceOptions();
        String current = currentFreeVoice();
        int index = options.indexOf(current);
        ModConfig.get().serviceVoice = options.get((index + 1) % options.size());
        ModConfig.save();
    }

    private List<String> freeVoiceOptions() {
        if (isEdgeDirect()) {
            return edgeVoiceOptions();
        }
        return apizeroVoiceOptions();
    }

    private List<String> edgeVoiceOptions() {
        return List.of(
            "zh-CN-XiaoyiNeural",
            "zh-CN-XiaoxiaoNeural",
            "zh-CN-YunxiNeural",
            "zh-CN-YunjianNeural",
            "zh-CN-XiaoshuangNeural",
            "zh-CN-YunyangNeural"
        );
    }

    private List<String> apizeroVoiceOptions() {
        return List.of(
            "female_sichuan",
            "female_zhubo",
            "male_zhubo",
            "male_rap",
            "male_db",
            "zh-CN-XiaoyiNeural",
            "zh-CN-XiaoxiaoNeural",
            "zh-CN-YunxiNeural",
            "zh-CN-YunjianNeural",
            "zh-CN-XiaoshuangNeural",
            "zh-CN-YunyangNeural"
        );
    }

    private List<String> freeRouteUrls() {
        return List.of(
            EDGE_DIRECT,
            APIZERO_URL
        );
    }

    private String freeRouteLabel() {
        String url = ModConfig.get().serviceUrl == null ? "" : ModConfig.get().serviceUrl;
        String route = normalizeRoute(url);
        if (EDGE_DIRECT.equals(route)) {
            return "免费线路：微软 Edge 直连";
        }
        if (APIZERO_URL.equals(route)) {
            return "免费线路：apizero（四川话等）";
        }
        return "免费线路：" + route;
    }

    private String currentFreeVoice() {
        List<String> options = freeVoiceOptions();
        String configured = ModConfig.get().serviceVoice;
        return options.contains(configured) ? configured : options.get(0);
    }

    private boolean isEdgeDirect() {
        return isEdgeDirectUrl(ModConfig.get().serviceUrl);
    }

    private boolean isEdgeDirectUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        String value = url.toLowerCase();
        return value.contains(EDGE_DIRECT)
            || value.contains("ttsapi.cn")
            || value.contains("ttsbox.cn")
            || value.contains("edge.text-to-speech.cn");
    }

    private boolean isApizeroUrl(String url) {
        return url != null && url.toLowerCase().contains("apizero.cn");
    }

    private String normalizeRoute(String url) {
        if (isEdgeDirectUrl(url)) {
            return EDGE_DIRECT;
        }
        if (isApizeroUrl(url)) {
            return APIZERO_URL;
        }
        return url == null ? "" : url;
    }

    private String freeVoiceLabel(String voice) {
        switch (voice) {
            case "female_zhubo":
                return "音色：女声主播";
            case "male_zhubo":
                return "音色：男声主播";
            case "male_rap":
                return "音色：男声说唱";
            case "female_sichuan":
                return "音色：女声四川话";
            case "male_db":
                return "音色：男声低沉";
            case "zh-CN-XiaoyiNeural":
                return "音色：晓伊";
            case "zh-CN-XiaoxiaoNeural":
                return "音色：晓晓";
            case "zh-CN-YunxiNeural":
                return "音色：云希";
            case "zh-CN-YunjianNeural":
                return "音色：云健";
            case "zh-CN-XiaoshuangNeural":
                return "音色：晓双";
            case "zh-CN-YunyangNeural":
                return "音色：云扬";
            default:
                return "音色：" + voice;
        }
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
