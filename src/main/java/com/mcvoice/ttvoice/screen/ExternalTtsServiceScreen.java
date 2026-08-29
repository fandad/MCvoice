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
    private final Screen parent;
    private EditBox urlBox;
    private EditBox keyBox;
    private EditBox voiceBox;
    private EditBox modelBox;
    private Button voiceChoiceButton;

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
        int y = 36;

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

        addRenderableWidget(new StringWidget(x, y, buttonWidth, 10,
            Component.literal(urlLabel()), font));
        y += 11;
        urlBox = new EditBox(font, x, y, buttonWidth, 20,
            Component.literal(urlHint()));
        urlBox.setMaxLength(1000);
        urlBox.setValue(ModConfig.get().serviceUrl);
        urlBox.setResponder(text -> {
            ModConfig.get().serviceUrl = text;
            if (freeMode() && !freeVoiceOptions().contains(ModConfig.get().serviceVoice)) {
                ModConfig.get().serviceVoice = freeVoiceOptions().get(0);
            }
            ModConfig.save();
        });
        urlBox.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.url.tooltip")));
        addRenderableWidget(urlBox);
        y += 30;

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

        addRenderableWidget(Button.builder(Component.literal("返回"),
                button -> ScreenUtil.setScreen(parent))
            .pos(centerX - 50, height - 32)
            .size(100, 20)
            .build());
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
        if (freeMode() && !freeVoiceOptions().contains(ModConfig.get().serviceVoice)) {
            ModConfig.get().serviceVoice = freeVoiceOptions().get(0);
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
            return "https://v1.apizero.cn/api/tts";
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
        super.tick();
    }

    private void cycleFreeVoice() {
        List<String> options = freeVoiceOptions();
        String current = currentFreeVoice();
        int index = options.indexOf(current);
        ModConfig.get().serviceVoice = options.get((index + 1) % options.size());
        ModConfig.save();
    }

    private List<String> freeVoiceOptions() {
        String url = ModConfig.get().serviceUrl == null ? "" : ModConfig.get().serviceUrl.toLowerCase();
        if (url.contains("ttsapi") || url.contains("ttsbox") || url.contains("edge.text-to-speech.cn")) {
            return List.of(
                "zh-CN-XiaoyiNeural",
                "zh-CN-XiaoxiaoNeural",
                "zh-CN-YunxiNeural",
                "zh-CN-YunjianNeural",
                "zh-CN-XiaoshuangNeural"
            );
        }
        return List.of(
            "female_zhubo",
            "male_zhubo",
            "male_rap",
            "female_sichuan",
            "male_db"
        );
    }

    private String currentFreeVoice() {
        List<String> options = freeVoiceOptions();
        String configured = ModConfig.get().serviceVoice;
        return options.contains(configured) ? configured : options.get(0);
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
