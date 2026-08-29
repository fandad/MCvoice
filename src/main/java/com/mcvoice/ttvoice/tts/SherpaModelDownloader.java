package com.mcvoice.ttvoice.tts;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

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

public final class SherpaModelDownloader {
    public interface ProgressListener {
        void update(String text);
    }

    private static final String RELEASE_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models";

    private static final Map<String, ModelDef> MODELS = Map.of(
        "vits-melo-tts-zh_en", new ModelDef(
            "MeloTTS 中英女声",
            "vits-melo-tts-zh_en.tar.bz2"),
        "vits-zh-hf-theresa", new ModelDef(
            "寒冰（多音色女声）",
            "vits-zh-hf-theresa.tar.bz2"),
        "vits-zh-hf-eula", new ModelDef(
            "伊拉（多音色女声）",
            "vits-zh-hf-eula.tar.bz2"),
        "vits-zh-hf-fanchen-wnj", new ModelDef(
            "繁辰 WNJ（男声）",
            "vits-zh-hf-fanchen-wnj.tar.bz2"),
        "sherpa-onnx-vits-zh-ll", new ModelDef(
            "小爱风格（多音色）",
            "sherpa-onnx-vits-zh-ll.tar.bz2")
    );

    private SherpaModelDownloader() {
    }

    public static void download(String modelId, Path targetDir, ProgressListener listener) throws Exception {
        ModelDef model = MODELS.get(modelId);
        if (model == null) {
            throw new IllegalArgumentException("未知模型: " + modelId);
        }
        Files.createDirectories(targetDir);

        Path archive = targetDir.resolve(model.archive());
        listener.update("正在下载 " + model.displayName() + " · " + model.archive());
        downloadFile(model.archive(), archive, listener);
        if (Files.size(archive) < 1_000_000L) {
            Files.deleteIfExists(archive);
            throw new IOException("下载的模型压缩包过小，可能不是有效模型");
        }

        listener.update("正在解压 " + model.displayName() + " ...");
        extract(archive, targetDir);
        Files.deleteIfExists(archive);
        listener.update("完成：" + model.displayName() + " 已放入 mcvoice/models/sherpa");
    }

    private static void downloadFile(String fileName, Path target, ProgressListener listener) throws Exception {
        List<URI> uris = List.of(
            URI.create(RELEASE_BASE + "/" + fileName),
            URI.create("https://ghfast.top/" + RELEASE_BASE + "/" + fileName),
            URI.create("https://gh-proxy.com/" + RELEASE_BASE + "/" + fileName)
        );

        Exception lastError = null;
        for (URI uri : uris) {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
                HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(20))
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

    private static void extract(Path archive, Path targetDir) throws IOException {
        Path base = targetDir.toAbsolutePath().normalize();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new BZip2CompressorInputStream(Files.newInputStream(archive)))) {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                Path out = base.resolve(name).normalize();
                if (!out.startsWith(base)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private record ModelDef(String displayName, String archive) {
    }
}
