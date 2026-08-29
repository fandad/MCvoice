package com.mcvoice.ttvoice.tts;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcvoice.ttvoice.TtVoiceClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExternalServiceEngine implements TtsEngine {
    private static final Gson GSON = new Gson();
    private static final String DEFAULT_FREE_BASE = "https://ttsapi.cn";
    private static final String DEFAULT_FREE_VOICE = "zh-CN-XiaoyiNeural";
    private static final String DEFAULT_APIZERO_VOICE = "female_zhubo";
    private static final Pattern NONCE_PATTERN =
        Pattern.compile("id=\"_wpnonce\" value=\"([^\"]+)\"");
    private static final Pattern CSRF_PATTERN =
        Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"");

    private final String mode;
    private final String url;
    private final String apiKey;
    private final String voice;
    private final String model;
    private final float serviceVolume;

    public ExternalServiceEngine(String mode, String url, String apiKey, String voice, String model, float serviceVolume) {
        this.mode = mode;
        this.url = url;
        this.apiKey = apiKey;
        this.voice = voice;
        this.model = model;
        this.serviceVolume = serviceVolume;
    }

    @Override
    public short[] synthesize(String text) throws Exception {
        if (!isFreeMode(mode) && (url == null || url.isBlank())) {
            throw new IllegalStateException("未设置外部TTS服务地址");
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        if (isFreeMode(mode)) {
            return synthesizeFree(client, text);
        }
        if ("openai".equalsIgnoreCase(mode)) {
            return synthesizeOpenAi(client, text);
        }
        return synthesizeUrl(client, text);
    }

    public static boolean isFreeMode(String mode) {
        if (mode == null) {
            return false;
        }
        return mode.equalsIgnoreCase("edge")
            || mode.equalsIgnoreCase("free")
            || mode.equalsIgnoreCase("ttsapi");
    }

    private short[] synthesizeFree(HttpClient client, String text) throws Exception {
        if (isApizero()) {
            try {
                return synthesizeApizero(client, text);
            } catch (Exception e) {
                if (!isRateLimit(e)) {
                    throw e;
                }
                return synthesizeFreeFallback(client, text);
            }
        }
        String base = normalizeBase(url);
        boolean edgeHost = base.toLowerCase().contains("edge.text-to-speech.cn");
        return synthesizeGenericFree(client, text, base, edgeHost, emptyTo(voice, DEFAULT_FREE_VOICE));
    }

    private short[] synthesizeGenericFree(HttpClient client, String text, String base, boolean edgeHost, String voice)
            throws Exception {
        String token = fetchToken(client, base, edgeHost);
        String payload = buildFreePayload(text, token, edgeHost, voice);
        String json = sendFreeRequest(client, base, payload, token, edgeHost);

        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        if (object.has("code") && object.get("code").getAsInt() != 0) {
            throw new IllegalStateException(freeErrorMessage(object));
        }
        if (!object.has("audio_url") || object.get("audio_url").isJsonNull()) {
            throw new IllegalStateException("免费TTS服务没有返回音频地址");
        }

        URI audioUri = URI.create(base + "/").resolve(object.get("audio_url").getAsString());
        HttpRequest audioRequest = HttpRequest.newBuilder(audioUri)
            .timeout(Duration.ofSeconds(30))
            .header("Referer", base + "/")
            .GET()
            .build();
        HttpResponse<byte[]> audioResponse = client.send(audioRequest, HttpResponse.BodyHandlers.ofByteArray());
        checkResponse(audioResponse);
        return AudioUtil.readAudio(audioResponse.body());
    }

    private short[] synthesizeFreeFallback(HttpClient client, String text) throws Exception {
        List<String> fallbackHosts = List.of(
            "https://ttsapi.cn",
            "https://ttsbox.cn",
            "https://edge.text-to-speech.cn"
        );
        Exception lastError = null;
        for (String base : fallbackHosts) {
            boolean edgeHost = base.contains("edge.text-to-speech.cn");
            try {
                return synthesizeGenericFree(client, text, base, edgeHost, fallbackVoice());
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new IOException("所有免费TTS服务都失败：" + lastError.getMessage(), lastError);
    }

    private boolean isApizero() {
        return url != null && url.toLowerCase().contains("apizero.cn");
    }

    private short[] synthesizeApizero(HttpClient client, String text) throws Exception {
        String endpoint = emptyTo(url, DEFAULT_FREE_BASE);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text);
        payload.put("voice_type", emptyTo(voice, DEFAULT_APIZERO_VOICE));

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("免费TTS服务返回 " + response.statusCode());
        }
        if (response.body() == null || response.body().isBlank()) {
            throw new IllegalStateException("免费TTS服务返回空响应");
        }

        JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
        if (object.has("code") && object.get("code").getAsInt() != 0) {
            throw new IllegalStateException(freeErrorMessage(object));
        }
        if (!object.has("data") || object.get("data").isJsonNull()) {
            throw new IllegalStateException("免费TTS服务没有返回音频");
        }
        JsonObject data = object.getAsJsonObject("data");
        if (!data.has("audio") || data.get("audio").isJsonNull()) {
            throw new IllegalStateException("免费TTS服务没有返回音频");
        }
        byte[] mp3 = Base64.getDecoder().decode(data.get("audio").getAsString());
        return AudioUtil.applyVolume(AudioUtil.readAudio(mp3), serviceVolumeMultiplier());
    }

    private static String fetchToken(HttpClient client, String base, boolean edgeHost) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/"))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("免费TTS服务页面返回 " + response.statusCode());
        }
        String html = response.body() == null ? "" : response.body();
        Matcher matcher = (edgeHost ? CSRF_PATTERN : NONCE_PATTERN).matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("无法从免费TTS服务页面获取请求口令");
        }
        return matcher.group(1);
    }

    private String buildFreePayload(String text, String token, boolean edgeHost, String voice) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text);
        payload.put("voice", voice);
        payload.put("rate", 1);
        payload.put("pitch", 1);
        payload.put("volume", serviceVolumePercent());
        if (!edgeHost) {
            payload.put("nonce", token);
        }
        return GSON.toJson(payload);
    }

    private String fallbackVoice() {
        String configured = emptyTo(voice, DEFAULT_FREE_VOICE);
        if (configured.startsWith("zh-CN-")) {
            return configured;
        }
        return DEFAULT_FREE_VOICE;
    }

    private static boolean isRateLimit(Exception e) {
        return e != null
            && e instanceof IllegalStateException
            && e.getMessage() != null
            && e.getMessage().contains("429");
    }

    private static String sendFreeRequest(HttpClient client, String base, String payload, String token, boolean edgeHost)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/generate.php"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Referer", base + "/")
            .header("Origin", base)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", "Mozilla/5.0")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (edgeHost) {
            builder.header("X-CSRF-TOKEN", token);
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("免费TTS服务返回 " + response.statusCode());
        }
        if (response.body() == null || response.body().isBlank()) {
            throw new IllegalStateException("免费TTS服务返回空响应");
        }
        return response.body();
    }

    private static String freeErrorMessage(JsonObject object) {
        if (object.has("msg") && !object.get("msg").isJsonNull()) {
            return object.get("msg").getAsString();
        }
        return "免费TTS服务返回错误";
    }

    private static String normalizeBase(String configured) {
        String base = configured == null || configured.isBlank()
            ? DEFAULT_FREE_BASE
            : configured.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/generate.php")) {
            base = base.substring(0, base.length() - "/generate.php".length());
        }
        return base;
    }

    private short[] synthesizeUrl(HttpClient client, String text) throws Exception {
        String voice = emptyTo(this.voice, "");
        String model = emptyTo(this.model, "");
        boolean usesVolumePlaceholder = this.url != null && this.url.contains("{volume}");
        String volume = Integer.toString(serviceVolumePercent());
        String url = this.url
            .replace("{text}", encode(text))
            .replace("{voice}", encode(voice))
            .replace("{model}", encode(model))
            .replace("{volume}", encode(volume));

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET();
        applyAuth(builder);

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        checkResponse(response);
        short[] pcm = AudioUtil.readAudio(response.body());
        return usesVolumePlaceholder ? pcm : AudioUtil.applyVolume(pcm, serviceVolumeMultiplier());
    }

    private short[] synthesizeOpenAi(HttpClient client, String text) throws Exception {
        String model = emptyTo(this.model, "tts-1");
        String voice = emptyTo(this.voice, "alloy");
        String body = GSON.toJson(Map.of(
            "model", model,
            "input", text,
            "voice", voice,
            "response_format", "wav"
        ));

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        applyAuth(builder);

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        checkResponse(response);
        return AudioUtil.applyVolume(AudioUtil.readAudio(response.body()), serviceVolumeMultiplier());
    }

    private int serviceVolumePercent() {
        return (int) Math.max(0.0f, Math.min(200.0f, Math.round(serviceVolume)));
    }

    private float serviceVolumeMultiplier() {
        return serviceVolumePercent() / 100.0f;
    }

    private void applyAuth(HttpRequest.Builder builder) {
        String key = apiKey;
        if (key != null && !key.isBlank()) {
            builder.header("Authorization", "Bearer " + key);
        }
    }

    private static void checkResponse(HttpResponse<byte[]> response) throws Exception {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("外部TTS服务返回 " + response.statusCode());
        }
        if (response.body() == null || response.body().length == 0) {
            throw new IllegalStateException("外部TTS服务返回空音频");
        }
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
    }
}
