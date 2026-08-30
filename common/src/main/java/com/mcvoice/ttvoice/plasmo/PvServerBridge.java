package com.mcvoice.ttvoice.plasmo;

import com.mcvoice.ttvoice.McVoiceConstants;
import net.fabricmc.loader.api.FabricLoader;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.provider.ArrayAudioFrameProvider;
import su.plo.voice.api.server.audio.source.AudioSender;
import su.plo.voice.api.server.audio.source.ServerBroadcastSource;
import su.plo.voice.api.server.audio.source.ServerPlayerSource;
import su.plo.voice.api.server.player.VoiceServerPlayer;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PvServerBridge {
    private static final int MAX_FRAMES = 50 * 60;

    private static final Map<UUID, PendingPlay> PENDING = new ConcurrentHashMap<>();
    private static volatile PlasmoVoiceServer voiceServer;

    private PvServerBridge() {
    }

    public static void setVoiceServer(PlasmoVoiceServer server) {
        voiceServer = server;
    }

    public static void clear() {
        PENDING.clear();
        voiceServer = null;
    }

    public static void receive(UUID playerId, byte[] pcm, float distance, boolean end) {
        PlasmoVoiceServer server = voiceServer;
        if (server == null || pcm == null || (pcm.length & 1) != 0) {
            return;
        }

        float clampedDistance = Math.max(1.0f, Math.min(128.0f, distance));
        PendingPlay pending = PENDING.computeIfAbsent(playerId, id -> new PendingPlay(clampedDistance));
        pending.distance = clampedDistance;

        if (pcm.length > 0) {
            if (pending.frames.size() >= MAX_FRAMES) {
                pending.frames.clear();
            }
            pending.frames.add(pcm);
        }

        if (end) {
            PENDING.remove(playerId);
            play(server, playerId, pending);
        }
    }

    private static void play(PlasmoVoiceServer server, UUID playerId, PendingPlay pending) {
        try {
            short[] samples = toSamples(pending.frames);
            if (samples.length == 0) {
                return;
            }

            ArrayAudioFrameProvider frameProvider = new ArrayAudioFrameProvider(server, false);
            frameProvider.addSamples(samples);

            ServerBroadcastSource groupSource = findGroupSource(playerId);
            if (groupSource != null) {
                AudioSender audioSender = groupSource.createAudioSender(frameProvider);
                startAndClose(audioSender, frameProvider, null);
                return;
            }

            ServerSourceLine line = server.getSourceLineManager()
                .getLineByName("proximity")
                .orElse(null);
            if (line == null) {
                frameProvider.close();
                return;
            }

            VoiceServerPlayer voicePlayer = server.getPlayerManager()
                .getPlayerById(playerId, true)
                .orElse(null);
            if (voicePlayer == null) {
                frameProvider.close();
                return;
            }

            ServerPlayerSource source = line.createPlayerSource(voicePlayer, false);
            AudioSender audioSender = source.createAudioSender(
                frameProvider,
                (short) Math.round(pending.distance)
            );
            startAndClose(audioSender, frameProvider, source);
        } catch (Exception e) {
            McVoiceConstants.LOGGER.warn("PV bridge playback failed", e);
        }
    }

    private static void startAndClose(
            AudioSender audioSender,
            ArrayAudioFrameProvider frameProvider,
            ServerPlayerSource source
    ) {
        try {
            audioSender.start();
        } catch (RuntimeException e) {
            frameProvider.close();
            if (source != null) {
                source.remove();
            }
            throw e;
        }
        audioSender.onStop(() -> {
            try {
                frameProvider.close();
            } finally {
                if (source != null) {
                    source.remove();
                }
            }
        });
    }

    private static short[] toSamples(List<byte[]> frames) {
        int byteCount = 0;
        for (byte[] frame : frames) {
            byteCount += frame.length;
        }
        if (byteCount == 0) {
            return new short[0];
        }

        ByteBuffer buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN);
        for (byte[] frame : frames) {
            buffer.put(frame);
        }
        buffer.flip();

        short[] samples = new short[byteCount / Short.BYTES];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort();
        }
        return samples;
    }

    private static ServerBroadcastSource findGroupSource(UUID playerId) {
        try {
            if (!FabricLoader.getInstance().isModLoaded("pv-addon-groups")) {
                return null;
            }

            Object addonManager = getAddonManager();
            if (addonManager == null) {
                return null;
            }

            Optional<?> addonContainer = (Optional<?>) addonManager.getClass()
                .getMethod("getAddon", String.class)
                .invoke(addonManager, "pv-addon-groups");
            if (addonContainer.isEmpty()) {
                return null;
            }

            Optional<?> addonInstance = (Optional<?>) addonContainer.get().getClass()
                .getMethod("getInstance")
                .invoke(addonContainer.get());
            if (addonInstance.isEmpty()) {
                return null;
            }

            Object groupManager = addonInstance.get().getClass()
                .getMethod("getGroupManager")
                .invoke(addonInstance.get());
            Object sources = groupManager.getClass()
                .getMethod("getSourceByPlayer")
                .invoke(groupManager);
            Object source = ((Map<?, ?>) sources).get(playerId);
            return source instanceof ServerBroadcastSource ? (ServerBroadcastSource) source : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object getAddonManager() {
        try {
            Object loader = PlasmoVoiceServer.getAddonsLoader();
            Class<?> type = loader.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField("addonManager");
                    field.setAccessible(true);
                    return field.get(loader);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    private static final class PendingPlay {
        private final List<byte[]> frames = new ArrayList<>();
        private float distance;

        private PendingPlay(float distance) {
            this.distance = distance;
        }
    }
}
