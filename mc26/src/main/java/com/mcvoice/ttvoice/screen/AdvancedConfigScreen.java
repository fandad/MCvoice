package com.mcvoice.ttvoice.screen;

import com.mcvoice.ttvoice.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AdvancedConfigScreen extends Screen {
    private final Screen parent;
    private EditBox commandBox;

    public AdvancedConfigScreen(Screen parent) {
        super(Component.translatable("config.mcvoice.advanced"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonWidth = Math.min(360, width - 40);
        int x = centerX - buttonWidth / 2;
        int y = 38;

        addRenderableWidget(new StringWidget(centerX - buttonWidth / 2, 14, buttonWidth, 20,
            Component.translatable("config.mcvoice.advanced"), font));

        AbstractSliderButton volumeSlider = new AbstractSliderButton(
            x, y, buttonWidth, 20,
            Component.literal("音量：" + Math.round(ModConfig.get().volume * 100) + "%"),
            Math.max(0.0, Math.min(1.0, ModConfig.get().volume / 2.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("音量：" + Math.round(value * 200) + "%"));
            }

            @Override
            protected void applyValue() {
                ModConfig.get().volume = (float) Math.round(value * 200) / 100.0f;
                ModConfig.save();
            }
        };
        volumeSlider.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.volume.tooltip")));
        addRenderableWidget(volumeSlider);
        y += 24;

        AbstractSliderButton distanceSlider = new AbstractSliderButton(
            x, y, buttonWidth, 20,
            Component.literal("传播距离：" + Math.round(ModConfig.get().distance) + " 格"),
            Math.max(0.0, Math.min(1.0, (ModConfig.get().distance - 1.0) / 127.0))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("传播距离：" + Math.round(1 + value * 127) + " 格"));
            }

            @Override
            protected void applyValue() {
                ModConfig.get().distance = 1.0f + (float) Math.round(value * 127);
                ModConfig.save();
            }
        };
        distanceSlider.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.distance.tooltip")));
        addRenderableWidget(distanceSlider);
        y += 24;

        Checkbox externalCheckbox = Checkbox.builder(
                Component.translatable("config.mcvoice.external.command.enable"), font)
            .selected(ModConfig.get().externalTts)
            .pos(x, y)
            .onValueChange((checkbox, selected) -> {
                ModConfig.get().externalTts = selected;
                if (selected) {
                    ModConfig.get().externalServiceTts = false;
                }
                ModConfig.save();
                commandBox.setEditable(selected);
            })
            .build();
        externalCheckbox.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.command.tooltip")));
        addRenderableWidget(externalCheckbox);
        y += 24;

        addRenderableWidget(new StringWidget(x, y, buttonWidth, 10,
            Component.translatable("config.mcvoice.external.command.label"), font));
        y += 11;
        commandBox = new EditBox(font, x, y, buttonWidth, 20,
            Component.translatable("config.mcvoice.external.command.hint"));
        commandBox.setMaxLength(2000);
        commandBox.setValue(ModConfig.get().externalCommand);
        commandBox.setEditable(ModConfig.get().externalTts);
        commandBox.setResponder(text -> {
            ModConfig.get().externalCommand = text;
            ModConfig.save();
        });
        commandBox.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.command.tooltip")));
        addRenderableWidget(commandBox);
        y += 31;

        Button serviceButton = Button.builder(
                Component.translatable("config.mcvoice.external.service.open"),
                button -> ScreenUtil.setScreen(new ExternalTtsServiceScreen(this)))
            .pos(x, y)
            .size(buttonWidth, 20)
            .build();
        serviceButton.setTooltip(Tooltip.create(Component.translatable("config.mcvoice.external.service.tooltip")));
        addRenderableWidget(serviceButton);

        MultiLineTextWidget explanation = new MultiLineTextWidget(
            Component.translatable("config.mcvoice.external.command.explain"), font);
        explanation.setX(x);
        explanation.setY(Math.max(y + 24, height - 86));
        explanation.setMaxWidth(buttonWidth);
        explanation.setMaxRows(3);
        explanation.setCentered(false);
        addRenderableWidget(explanation);

        addRenderableWidget(Button.builder(Component.literal("返回"),
                button -> ScreenUtil.setScreen(parent))
            .pos(centerX - 50, height - 32)
            .size(100, 20)
            .build());
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
