package com.mcvoice.ttvoice.tts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcvoice.ttvoice.McVoiceConstants;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
    private static final String REGION_TOKEN_URL =
        "https://dev.microsofttranslator.com/apps/endpoint?api-version=1.0";
    private static final String REGION_SIGN_KEY_B64 =
        "oik6PdDdMnOXemTbwvMn9de/h9lFnfBaCWbGMMZqqoSaQaqUOqjVGm5NqsmjcBI1x+sS9ugjB55HEJWRiFXYFw==";
    private static final String DEFAULT_VOICE = "zh-CN-XiaoyiNeural";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(
        "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
        Locale.ENGLISH
    ).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter REGION_DATE_FORMAT = DateTimeFormatter.ofPattern(
        "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
        Locale.ENGLISH
    ).withZone(ZoneOffset.UTC);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /** 直连连续失败后，短暂时间内优先走区域 HTTP 线路，避免每次都反复撞墙。 */
    private static volatile long wssDownUntil;
    private static volatile RegionToken cachedRegionToken;

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

        boolean wssCoolingDown = System.currentTimeMillis() < wssDownUntil;
        Exception wssError = null;
        if (!wssCoolingDown) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    return synthesizeDirect(text);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (Exception e) {
                    wssError = e;
                    McVoiceConstants.LOGGER.warn("Edge 直连第 {} 次失败：{}", attempt, e.getMessage());
                    if (attempt == 1) {
                        sleepQuietly(350);
                    }
                }
            }
            wssDownUntil = System.currentTimeMillis() + 120_000L;
        }

        try {
            short[] pcm = synthesizeRegionHttp(text);
            // 区域线路成功说明网络恢复，下次仍优先尝试直连。
            wssDownUntil = 0L;
            return pcm;
        } catch (Exception e) {
            if (wssError != null) {
                e.addSuppressed(wssError);
            }
            throw e;
        }
    }

    private static void sleepQuietly(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private short[] synthesizeDirect(String text) throws Exception {
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
            if (!done.await(25, TimeUnit.SECONDS)) {
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
                throw new IOException("Edge 直连返回空音频，可能被限流");
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

    private short[] synthesizeRegionHttp(String text) throws Exception {
        boolean forceRefresh = false;
        for (int attempt = 0; attempt < 2; attempt++) {
            RegionToken token = regionToken(forceRefresh);
            try {
                byte[] mp3 = postRegionAudio(token, text);
                if (mp3.length == 0) {
                    throw new IOException("Edge 区域线路返回空音频，可能被限流");
                }
                short[] pcm = AudioUtil.readAudio(mp3);
                return AudioUtil.applyVolume(pcm, volumePercent / 100.0f);
            } catch (IOException e) {
                if (!forceRefresh) {
                    forceRefresh = true;
                    McVoiceConstants.LOGGER.warn("Edge 区域线路失败，刷新令牌重试：{}", e.getMessage());
                    continue;
                }
                throw e;
            }
        }
        throw new IOException("Edge 区域线路连续失败");
    }

    private byte[] postRegionAudio(RegionToken token, String text) throws Exception {
        String ssml = "<speak xmlns='http://www.w3.org/2001/10/synthesis' "
            + "xmlns:mstts='http://www.w3.org/2001/mstts' version='1.0' xml:lang='zh-CN'> "
            + "<voice name='" + voice + "'> "
            + "<mstts:express-as style='general' styledegree='1.0' role='default'> "
            + "<prosody rate='0%' pitch='0%' volume='50'>"
            + escapeXml(text)
            + "</prosody> </mstts:express-as> </voice> </speak>";

        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://" + token.region + ".tts.speech.microsoft.com/cognitiveservices/v1"))
            .timeout(Duration.ofSeconds(25))
            .header("Authorization", token.token)
            .header("Content-Type", "application/ssml+xml")
            .header("User-Agent", "okhttp/4.5.0")
            .header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
            .POST(HttpRequest.BodyPublishers.ofString(ssml, StandardCharsets.UTF_8))
            .build();

        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8);
            if (detail.length() > 300) {
                detail = detail.substring(0, 300);
            }
            throw new IOException("Edge 区域线路返回 " + response.statusCode() + "：" + detail);
        }
        byte[] body = response.body() == null ? new byte[0] : response.body();
        if (body.length > 0 && (body[0] == '{' || body[0] == '[')) {
            String detail = new String(body, 0, Math.min(body.length, 300), StandardCharsets.UTF_8);
            throw new IOException("Edge 区域线路返回错误响应：" + detail);
        }
        return body;
    }

    private static RegionToken regionToken(boolean forceRefresh) throws Exception {
        RegionToken cached = cachedRegionToken;
        if (!forceRefresh && cached != null && cached.expiryMillis > System.currentTimeMillis()) {
            return cached;
        }
        synchronized (EdgeTtsEngine.class) {
            cached = cachedRegionToken;
            if (!forceRefresh && cached != null && cached.expiryMillis > System.currentTimeMillis()) {
                return cached;
            }
            RegionToken fresh = fetchRegionToken();
            cachedRegionToken = fresh;
            return fresh;
        }
    }

    private static RegionToken fetchRegionToken() throws Exception {
        String uuid = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        String date = REGION_DATE_FORMAT.format(Instant.now()).toLowerCase(Locale.ROOT);
        String encodedUrl = uriEncode(REGION_TOKEN_URL.substring("https://".length()));
        String bytesToSign = ("MSTranslatorAndroidApp" + encodedUrl + date + uuid).toLowerCase(Locale.ROOT);

        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] key = Base64.getDecoder().decode(REGION_SIGN_KEY_B64);
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(bytesToSign.getBytes(StandardCharsets.US_ASCII)));
        String authHeader = "MSTranslatorAndroidApp::" + signature + "::" + date + "::" + uuid;

        HttpRequest request = HttpRequest.newBuilder(URI.create(REGION_TOKEN_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Accept-Language", "zh-Hans")
            .header("X-ClientVersion", "4.0.530a 5fe1dc6c")
            .header("X-UserId", "0f04d16a175c411e")
            .header("X-HomeGeographicRegion", "zh-Hans-CN")
            .header("X-ClientTraceId", uuid)
            .header("X-MT-Signature", authHeader)
            .header("User-Agent", "okhttp/4.5.0")
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("获取 Edge 区域令牌失败：" + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException("获取 Edge 区域令牌失败：响应为空");
        }
        JsonObject object = JsonParser.parseString(body).getAsJsonObject();
        if (!object.has("r") || !object.has("t")) {
            throw new IOException("获取 Edge 区域令牌失败：响应缺少字段");
        }
        return new RegionToken(
            object.get("r").getAsString(),
            object.get("t").getAsString(),
            System.currentTimeMillis() + 50 * 60 * 1000L
        );
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

    private static String uriEncode(String value) {
        StringBuilder result = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
                result.append((char) c);
            } else {
                result.append(String.format("%%%02X", c));
            }
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

    private static final class RegionToken {
        private final String region;
        private final String token;
        private final long expiryMillis;

        private RegionToken(String region, String token, long expiryMillis) {
            this.region = region;
            this.token = token;
            this.expiryMillis = expiryMillis;
        }
    }
}
