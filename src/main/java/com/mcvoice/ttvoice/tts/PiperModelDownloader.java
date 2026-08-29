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
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class PiperModelDownloader {
    public interface ProgressListener {
        void update(String text);
    }

    private static final String MIRROR_BASE =
        "https://hf-mirror.com/rhasspy/piper-voices/resolve/main";
    private static final String OFFICIAL_BASE =
        "https://huggingface.co/rhasspy/piper-voices/resolve/main";

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
            "zh_CN-huayan-x_low.onnx.json"),
        "zh_CN-chaowen-medium", new ModelDef(
            "超文（男声）",
            "zh/zh_CN/chaowen/medium",
            "zh_CN-chaowen-medium.onnx",
            "zh_CN-chaowen-medium.onnx.json")
    );

    private PiperModelDownloader() {
    }

    public static void download(String modelId, Path modelDir, ProgressListener listener) throws Exception {
        ModelDef model = MODELS.get(modelId);
        if (model == null) {
            throw new IllegalArgumentException("未知模型: " + modelId);
        }
        Files.createDirectories(modelDir);

        for (String fileName : model.files()) {
            List<URI> uris = List.of(
                URI.create(MIRROR_BASE + "/" + model.path() + "/" + fileName),
                URI.create(OFFICIAL_BASE + "/" + model.path() + "/" + fileName)
            );
            Path target = modelDir.resolve(fileName);
            Path part = modelDir.resolve(fileName + ".part");
            listener.update("正在下载 " + model.displayName() + " · " + fileName);
            downloadFile(uris, part, fileName, listener);
            if (fileName.endsWith(".onnx") && Files.size(part) < 1_000_000L) {
                throw new IOException("下载的 ONNX 模型文件过小，可能不是有效模型");
            }
            if (fileName.endsWith(".onnx.json")) {
                String content = Files.readString(part);
                if (content == null || content.isBlank() || !content.stripLeading().startsWith("{")) {
                    throw new IOException("下载的模型配置不是有效 JSON");
                }
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }

        listener.update("完成：" + model.displayName() + " 已放入 mcvoice/models");
    }

    private static void downloadFile(List<URI> uris, Path target, String fileName, ProgressListener listener)
            throws Exception {
        Exception lastError = null;
        for (URI uri : uris) {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
                HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(10))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
                HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    try (InputStream in = response.body()) {
                        in.transferTo(OutputStream.nullOutputStream());
                    }
                    throw new IOException("HTTP " + response.statusCode());
                }

                String contentType = response.headers()
                    .firstValue("Content-Type").orElse("").toLowerCase();
                if (contentType.contains("text/html")) {
                    try (InputStream in = response.body()) {
                        in.transferTo(OutputStream.nullOutputStream());
                    }
                    throw new IOException("服务返回了网页而不是模型文件");
                }

                long total = response.headers()
                    .firstValueAsLong("Content-Length").orElse(-1L);
                Files.deleteIfExists(target);
                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    long done = 0;
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
            } catch (Exception e) {
                lastError = e;
                Files.deleteIfExists(target);
            }
        }
        throw new IOException("所有下载源都失败了：" + lastError.getMessage(), lastError);
    }

    private record ModelDef(String displayName, String path, String modelFile, String configFile) {
        private List<String> files() {
            return List.of(modelFile, configFile);
        }
    }
}
