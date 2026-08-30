package com.mcvoice.ttvoice.command;

import com.mcvoice.ttvoice.ModConfig;
import com.mcvoice.ttvoice.TtVoiceClient;
import com.mcvoice.ttvoice.screen.ConfigScreen;
import com.mcvoice.ttvoice.screen.ScreenUtil;
import com.mcvoice.ttvoice.tts.TtsManager;
import com.mcvoice.ttvoice.tts.Voice;
import com.mcvoice.ttvoice.tts.VoiceRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class McVoiceCommands {
    private McVoiceCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("mcvoice")
                .then(ClientCommandManager.literal("say")
                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> speak(ctx))))
                .then(ClientCommandManager.literal("stop")
                    .executes(ctx -> stop(ctx)))
                .then(ClientCommandManager.literal("test")
                    .executes(ctx -> test(ctx)))
                .then(ClientCommandManager.literal("auto")
                    .executes(ctx -> autoToggle(ctx))
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> autoSet(ctx, true)))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> autoSet(ctx, false))))
                .then(ClientCommandManager.literal("volume")
                    .then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 200))
                        .executes(ctx -> volumeSet(ctx))))
                .then(ClientCommandManager.literal("distance")
                    .then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> distanceSet(ctx))))
                .then(ClientCommandManager.literal("external")
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> externalSet(ctx, true)))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> externalSet(ctx, false)))
                    .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                            .executes(ctx -> externalCommand(ctx)))))
                .then(ClientCommandManager.literal("config")
                    .executes(ctx -> config(ctx)))
                .then(ClientCommandManager.literal("voice")
                    .then(ClientCommandManager.literal("list")
                        .executes(ctx -> voiceList(ctx)))
                    .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument("voice", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                VoiceRegistry.listVoices().stream().map(Voice::getId).toList(), builder))
                            .executes(ctx -> voiceSet(ctx)))))
                .then(ClientCommandManager.literal("help")
                    .executes(ctx -> help(ctx))));
        });
    }

    private static int speak(CommandContext<FabricClientCommandSource> ctx) {
        String text = StringArgumentType.getString(ctx, "text");
        if (text.isBlank()) {
            ctx.getSource().sendError(Component.translatable("mcvoice.command.say.usage"));
            return 0;
        }
        TtsManager.speak(text);
        return 1;
    }

    private static int stop(CommandContext<FabricClientCommandSource> ctx) {
        TtsManager.stop();
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.stop"));
        return 1;
    }

    private static int test(CommandContext<FabricClientCommandSource> ctx) {
        TtsManager.test();
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.test"));
        return 1;
    }

    private static int config(CommandContext<FabricClientCommandSource> ctx) {
        ScreenUtil.setScreen(new ConfigScreen(null));
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.config"));
        return 1;
    }

    private static int autoToggle(CommandContext<FabricClientCommandSource> ctx) {
        return autoSet(ctx, !ModConfig.get().autoSpeak);
    }

    private static int autoSet(CommandContext<FabricClientCommandSource> ctx, boolean enabled) {
        ModConfig.get().autoSpeak = enabled;
        ModConfig.save();
        ctx.getSource().sendFeedback(Component.translatable(
            enabled ? "mcvoice.command.auto.on" : "mcvoice.command.auto.off"
        ));
        return 1;
    }

    private static int volumeSet(CommandContext<FabricClientCommandSource> ctx) {
        int percent = IntegerArgumentType.getInteger(ctx, "percent");
        ModConfig.get().volume = percent / 100.0f;
        ModConfig.save();
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.volume", percent));
        return 1;
    }

    private static int distanceSet(CommandContext<FabricClientCommandSource> ctx) {
        int blocks = IntegerArgumentType.getInteger(ctx, "blocks");
        ModConfig.get().distance = blocks;
        ModConfig.save();
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.distance", blocks));
        return 1;
    }

    private static int externalSet(CommandContext<FabricClientCommandSource> ctx, boolean enabled) {
        ModConfig.get().externalTts = enabled;
        ModConfig.save();
        TtsManager.stop();
        ctx.getSource().sendFeedback(Component.translatable(
            enabled ? "mcvoice.command.external.on" : "mcvoice.command.external.off"
        ));
        return 1;
    }

    private static int externalCommand(CommandContext<FabricClientCommandSource> ctx) {
        String command = StringArgumentType.getString(ctx, "command");
        ModConfig.get().externalCommand = command;
        ModConfig.get().externalTts = true;
        ModConfig.save();
        TtsManager.stop();
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.external.set"));
        return 1;
    }

    private static int voiceList(CommandContext<FabricClientCommandSource> ctx) {
        List<Voice> voices = VoiceRegistry.listVoices();
        if (voices.isEmpty()) {
            ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.voice.empty"));
            return 1;
        }
        Component message = Component.literal("");
        for (Voice voice : voices) {
            message = message.copy().append(Component.literal(" - " + voice.getDisplayName() + " (" + voice.getId() + ")\n"));
        }
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.voice.list").copy().append(message));
        return 1;
    }

    private static int voiceSet(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "voice");
        Voice voice = VoiceRegistry.findByVoiceId(id);
        if (voice == null) {
            ctx.getSource().sendError(Component.translatable("mcvoice.command.voice.not_found", id));
            return 0;
        }
        ModConfig.get().selectedVoice = id;
        ModConfig.save();
        TtsManager.stop();
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.voice.set", voice.getDisplayName()));
        return 1;
    }

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.translatable("mcvoice.command.help"));
        return 1;
    }
}
