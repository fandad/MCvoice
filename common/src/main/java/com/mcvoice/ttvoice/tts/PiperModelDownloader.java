package com.mcvoice.ttvoice.tts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PiperModelDownloader {
    public interface ProgressListener {
        void update(String text);
    }

    private static final String MODELSCOPE_BASE =
        "https://modelscope.cn/models/rhasspy/piper-voices/resolve/master";
    private static final String MIRROR_BASE =
        "https://hf-mirror.com/rhasspy/piper-voices/resolve/main";
    private static final String OFFICIAL_BASE =
        "https://huggingface.co/rhasspy/piper-voices/resolve/main";

    private static final Pattern CONTENT_RANGE_PATTERN =
        Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)");

    private static final Map<String, ModelDef> MODELS = Map.of(
        "zh_CN-huayan-medium", new ModelDef(
            "花颜（中）",
            "zh/zh_CN/huayan/medium",
            "zh_CN-huayan-medium.onnx",
            "zh_CN-huayan-medium.onnx.json"),
        "zh_CN-huayan-x_low", new ModelDef(
            "花颜（低配）",
            "zh/zh_CN/huayan/x_low",
            "zh_CN-huayan-x_low.onnx",
            "zh_CN-huayan-x_low.onnx.json")
    );

    private PiperModelDownloader() {
    }

    public static boolean hasPartialDownload(String modelId) {
        ModelDef model = MODELS.get(modelId);
        if (model == null) {
            return false;
        }
        for (String fileName : model.files()) {
            Path part = VoiceRegistry.getModelDir().resolve(fileName + ".part");
            try {
                if (Files.isRegularFile(part) && Files.size(part) > 0L) {
                    return true;
                }
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    public static void download(String modelId, Path modelDir, ProgressListener listener) throws Exception {
        ModelDef model = MODELS.get(modelId);
        if (model == null) {
            throw new IllegalArgumentException("未知模型: " + modelId);
        }
        Files.createDirectories(modelDir);

        Path modelFile = modelDir.resolve(model.modelFile());
        Path configFile = modelDir.resolve(model.configFile());
        try {
            for (String fileName : model.files()) {
                Path target = modelDir.resolve(fileName);
                Path part = modelDir.resolve(fileName + ".part");
                if (Files.isRegularFile(target) && isValidDownloadedFile(fileName, target)) {
                    listener.update("已存在：" + model.displayName() + " · " + fileName);
                    continue;
                }
                if (Files.isRegularFile(target)) {
                    Files.deleteIfExists(target);
                }

                List<URI> uris = downloadUris(model.path(), fileName);
                listener.update("正在下载 " + model.displayName() + " · " + fileName);
                downloadFile(uris, part, fileName, listener);
                validateDownloadedFile(part, fileName);
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                if (fileName.endsWith(".onnx.json")) {
                    PiperConfigSupport.normalizeVoiceIfNeeded(target);
                }
            }
            if (!VoiceRegistry.isUsableModel(modelFile, configFile)) {
                throw new IOException("模型下载完成但校验未通过");
            }
            listener.update("完成：" + model.displayName() + " 已放入 mcvoice/models");
        } catch (Exception e) {
            cleanup(modelDir, model);
            throw e;
        }
    }

    private static void cleanup(Path modelDir, ModelDef model) {
        for (String fileName : model.files()) {
            Path target = modelDir.resolve(fileName);
            try {
                if (!isValidDownloadedFile(fileName, target)) {
                    Files.deleteIfExists(target);
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean isValidDownloadedFile(String fileName, Path target) {
        try {
            if (!Files.isRegularFile(target)) {
                return false;
            }
            if (fileName.endsWith(".onnx")) {
                return Files.size(target) >= 1_000_000L;
            }
            if (fileName.endsWith(".onnx.json")) {
                return Files.size(target) > 10L && startsWithJsonObject(target);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean startsWithJsonObject(Path path) {
        try {
            String content = Files.readString(path);
            return content != null && !content.isBlank() && content.stripLeading().startsWith("{");
        } catch (IOException e) {
            return false;
        }
    }

    private static void validateDownloadedFile(Path part, String fileName) throws IOException {
        if (fileName.endsWith(".onnx") && Files.size(part) < 1_000_000L) {
            Files.deleteIfExists(part);
            throw new IOException("下载的 ONNX 模型文件过小，可能不是有效模型");
        }
        if (fileName.endsWith(".onnx.json")) {
            if (Files.size(part) <= 10L || !startsWithJsonObject(part)) {
                Files.deleteIfExists(part);
                throw new IOException("下载的模型配置不是有效 JSON");
            }
        }
    }

    private static void downloadFile(List<URI> uris, Path target, String fileName, ProgressListener listener)
            throws Exception {
        Exception lastError = null;
        for (int index = 0; index < uris.size(); index++) {
            URI uri = uris.get(index);
            String host = uri.getHost();
            listener.update("正在连接 " + host + "，下载 " + fileName
                + "（第 " + (index + 1) + " 个下载源）...");
            try {
                downloadFromUri(uri, target, fileName, listener);
                return;
            } catch (Exception e) {
                lastError = e;
                String detail = Files.isRegularFile(target)
                    ? "，已保留 " + Files.size(target) / 1024.0 / 1024.0 + " MB"
                    : "";
                listener.update(host + " 下载失败：" + briefError(e) + detail
                    + (index + 1 < uris.size() ? "，继续尝试备用源" : ""));
            }
        }
        throw new IOException("所有下载源都失败了：" + lastError.getMessage(), lastError);
    }

    private static List<URI> downloadUris(String path, String fileName) {
        String relative = "/" + path + "/" + fileName;
        return List.of(
            URI.create(MODELSCOPE_BASE + relative),
            URI.create(MIRROR_BASE + relative),
            URI.create(MIRROR_BASE + relative + "?download=true"),
            URI.create(OFFICIAL_BASE + relative),
            URI.create(OFFICIAL_BASE + relative + "?download=true")
        );
    }

    private static void downloadFromUri(URI uri, Path target, String fileName, ProgressListener listener)
            throws Exception {
        long existing = Files.isRegularFile(target) ? Files.size(target) : 0L;
        boolean restartedAfter416 = false;

        while (true) {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "Mozilla/5.0")
                .GET();
            if (existing > 0L) {
                requestBuilder.header("Range", "bytes=" + existing + "-");
            }

            HttpResponse<InputStream> response = client.send(
                requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status == 416) {
                if (existing > 0L && !restartedAfter416) {
                    listener.update("服务器已没有匹配的断点，正在重新下载 " + fileName);
                    Files.deleteIfExists(target);
                    existing = 0L;
                    restartedAfter416 = true;
                    continue;
                }
                throw new IOException("HTTP 416 Range Not Satisfiable");
            }
            if (status < 200 || status >= 300) {
                try (InputStream in = response.body()) {
                    in.transferTo(OutputStream.nullOutputStream());
                }
                throw new IOException("HTTP " + status);
            }

            String contentType = response.headers()
                .firstValue("Content-Type").orElse("").toLowerCase();
            if (contentType.contains("text/html")) {
                try (InputStream in = response.body()) {
                    in.transferTo(OutputStream.nullOutputStream());
                }
                throw new IOException("服务返回了网页而不是模型文件");
            }

            long total = contentLength(response, status, existing);
            boolean append = false;
            if (status == 206 && existing > 0L) {
                Optional<Long> rangeStart = contentRangeStart(response);
                if (rangeStart.isPresent()
                        && rangeStart.get() != existing
                        && !restartedAfter416) {
                    listener.update("下载源不支持续传，正在重新下载 " + fileName);
                    Files.deleteIfExists(target);
                    existing = 0L;
                    restartedAfter416 = true;
                    continue;
                }
                if (rangeStart.isPresent() && rangeStart.get() != existing) {
                    throw new IOException("下载源返回了不连续的断点范围");
                }
                append = true;
            } else {
                if (existing > 0L) {
                    listener.update("下载源不支持续传，正在重新下载 " + fileName);
                }
                Files.deleteIfExists(target);
                existing = 0L;
            }

            try (InputStream in = response.body();
                 OutputStream out = append
                     ? Files.newOutputStream(target, StandardOpenOption.APPEND)
                     : Files.newOutputStream(target)) {
                byte[] buffer = new byte[8192];
                long done = existing;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        listener.update(String.format(
                            "正在下载 %s · %.1f / %.1f MB",
                            fileName, done / 1024.0 / 1024.0, total / 1024.0 / 1024.0));
                    } else {
                        listener.update(String.format(
                            "正在下载 %s · %.1f MB",
                            fileName, done / 1024.0 / 1024.0));
                    }
                }
            }
            return;
        }
    }

    private static long contentLength(HttpResponse<?> response, int status, long existing) {
        if (status == 206) {
            return contentRangeTotal(response).orElse(-1L);
        }
        return response.headers().firstValueAsLong("Content-Length").orElse(-1L);
    }

    private static Optional<Long> contentRangeStart(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Range")
            .flatMap(value -> {
                Matcher matcher = CONTENT_RANGE_PATTERN.matcher(value);
                if (!matcher.find()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(Long.parseLong(matcher.group(1)));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            });
    }

    private static Optional<Long> contentRangeTotal(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Range")
            .flatMap(value -> {
                Matcher matcher = CONTENT_RANGE_PATTERN.matcher(value);
                if (!matcher.find()) {
                    return Optional.empty();
                }
                String total = matcher.group(3);
                if ("*".equals(total)) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(Long.parseLong(total));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            });
    }

    private static String briefError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }

    private record ModelDef(String displayName, String path, String modelFile, String configFile) {
        private List<String> files() {
            return List.of(modelFile, configFile);
        }
    }
}
