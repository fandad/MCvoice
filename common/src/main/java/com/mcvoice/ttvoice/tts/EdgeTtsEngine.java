package com.mcvoice.ttvoice.tts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class EdgeTtsEngine implements TtsEngine {
    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String GEC_VERSION = "1-143.0.3650.75";
    private static final String WSS_BASE =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String DEFAULT_VOICE = "zh-CN-XiaoyiNeural";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(
        "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
        Locale.ENGLISH
    ).withZone(ZoneOffset.UTC);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final String voice;
    private final int volumePercent;

    public EdgeTtsEngine(String voice, float serviceVolume) {
        this.voice = normalizeVoice(voice);
        this.volumePercent = Math.max(0, Math.min(200, Math.round(serviceVolume)));
    }

    @Override
    public short[] synthesize(String text) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Edge TTS 文本不能为空");
        }

        String connectionId = UUID.randomUUID().toString().replace("-", "");
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now()) + "Z";
        URI uri = buildUri(connectionId);
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                String message = data.toString();
                if (message.contains("Path:turn.end")) {
                    done.countDown();
                } else if (message.contains("Path:response") && message.contains("\"error\"")) {
                    failure.set(new IOException("Edge TTS 返回错误"));
                    done.countDown();
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                if (bytes.length >= 2) {
                    int headerLength = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
                    int headerEnd = Math.min(bytes.length, 2 + headerLength);
                    String header = new String(
                        bytes, 2, Math.max(0, headerEnd - 2), StandardCharsets.ISO_8859_1
                    );
                    if (header.contains("Content-Type:audio/mpeg") && bytes.length > headerEnd) {
                        audio.write(bytes, headerEnd, bytes.length - headerEnd);
                    }
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                done.countDown();
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                failure.set(error);
                done.countDown();
            }
        };

        WebSocket socket = CLIENT.newWebSocketBuilder()
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Cookie", "muid=" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT) + ";")
            .buildAsync(uri, listener)
            .join();

        try {
            socket.sendText(configMessage(connectionId, timestamp), true).join();
            socket.sendText(ssmlMessage(connectionId, timestamp, text), true).join();
            if (!done.await(35, TimeUnit.SECONDS)) {
                throw new IOException("Edge TTS 请求超时");
            }
            Throwable error = failure.get();
            if (error != null) {
                if (error instanceof IOException ioError) {
                    throw ioError;
                }
                throw new IOException("Edge TTS 连接失败：" + error.getMessage(), error);
            }
            byte[] mp3 = audio.toByteArray();
            if (mp3.length == 0) {
                throw new IOException("Edge TTS 没有返回音频");
            }
            short[] pcm = AudioUtil.readAudio(mp3);
            return AudioUtil.applyVolume(pcm, volumePercent / 100.0f);
        } finally {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // 连接已关闭或超时时无需再次关闭。
            }
        }
    }

    private static URI buildUri(String connectionId) throws Exception {
        String token = secMsGec();
        return URI.create(WSS_BASE
            + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN
            + "&ConnectionId=" + connectionId
            + "&Sec-MS-GEC=" + token
            + "&Sec-MS-GEC-Version=" + GEC_VERSION);
    }

    private static String secMsGec() throws Exception {
        long unix = Instant.now().getEpochSecond();
        long ticks = unix + 11644473600L;
        ticks -= Math.floorMod(ticks, 300);
        String input = Long.toString(ticks * 10_000_000L) + TRUSTED_CLIENT_TOKEN;
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(input.getBytes(StandardCharsets.US_ASCII));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }

    private static String configMessage(String connectionId, String timestamp) {
        return "X-RequestId:" + connectionId + "\r\n"
            + "Content-Type:application/json; charset=utf-8\r\n"
            + "X-Timestamp:" + timestamp + "\r\n"
            + "Path:speech.config\r\n\r\n"
            + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
            + "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
            + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
    }

    private String ssmlMessage(String connectionId, String timestamp, String text) {
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>"
            + "<voice name='" + voice + "'>"
            + "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>"
            + escapeXml(text)
            + "</prosody></voice></speak>";
        return "X-RequestId:" + connectionId + "\r\n"
            + "Content-Type:application/ssml+xml\r\n"
            + "X-Timestamp:" + timestamp + "\r\n"
            + "Path:ssml\r\n\r\n"
            + ssml;
    }

    private static String normalizeVoice(String configured) {
        String value = configured == null || configured.isBlank()
            ? DEFAULT_VOICE
            : configured.trim();
        return value.startsWith("zh-CN-") ? value : DEFAULT_VOICE;
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    @Override
    public void close() {
    }
}
