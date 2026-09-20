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
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SherpaModelDownloader {
    public interface ProgressListener {
        void update(String text);
    }

    private static final String RELEASE_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models";

    /** 解析 Content-Range，例如 "bytes 100-999/1000"。 */
    private static final Pattern CONTENT_RANGE_PATTERN =
        Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)");

    private static final Map<String, ModelDef> MODELS = Map.of(
        "vits-melo-tts-zh_en", new ModelDef(
            "voice.mcvoice.sherpa.melo",
            "vits-melo-tts-zh_en.tar.bz2"),
        "vits-zh-hf-theresa", new ModelDef(
            "voice.mcvoice.sherpa.theresa",
            "vits-zh-hf-theresa.tar.bz2"),
        "vits-zh-hf-eula", new ModelDef(
            "voice.mcvoice.sherpa.eula",
            "vits-zh-hf-eula.tar.bz2"),
        "vits-zh-hf-fanchen-wnj", new ModelDef(
            "voice.mcvoice.sherpa.fanchen",
            "vits-zh-hf-fanchen-wnj.tar.bz2"),
        "sherpa-onnx-vits-zh-ll", new ModelDef(
            "voice.mcvoice.sherpa.xiaomi",
            "sherpa-onnx-vits-zh-ll.tar.bz2"),
        "vits-piper-zh_CN-chaowen-medium", new ModelDef(
            "voice.mcvoice.sherpa.chaowen",
            "vits-piper-zh_CN-chaowen-medium.tar.bz2"),
        "vits-piper-zh_CN-xiao_ya-medium", new ModelDef(
            "voice.mcvoice.sherpa.xiaoya",
            "vits-piper-zh_CN-xiao_ya-medium.tar.bz2"),
        "vits-cantonese-hf-xiaomaiiwn", new ModelDef(
            "voice.mcvoice.sherpa.cantonese",
            "vits-cantonese-hf-xiaomaiiwn.tar.bz2"),
        "matcha-icefall-zh-baker", new ModelDef(
            "voice.mcvoice.sherpa.matcha",
            "matcha-icefall-zh-baker.tar.bz2"),
        "kokoro-int8-multi-lang-v1_0", new ModelDef(
            "voice.mcvoice.sherpa.kokoro",
            "kokoro-int8-multi-lang-v1_0.tar.bz2")
    );

    private SherpaModelDownloader() {
    }

    public static void download(String modelId, Path targetDir, ProgressListener listener) throws Exception {
        ModelDef model = MODELS.get(modelId);
        if (model == null) {
            throw new IllegalArgumentException(DownloadStatus.encode("download.mcvoice.error.unknown_model", modelId));
        }
        Files.createDirectories(targetDir);

        Path archive = targetDir.resolve(model.archive());
        Path partial = targetDir.resolve(model.archive() + ".part");
        Path modelDir = targetDir.resolve(modelId);
        try {
            listener.update(DownloadStatus.encode("download.mcvoice.status.downloading", model.nameKey(), model.archive()));
            // 断点续传：下载写到 .part，成功后再改名，中途失败保留 .part 供下次继续。
            downloadFile(model.archive(), partial, listener);
            if (Files.size(partial) < 1_000_000L) {
                Files.deleteIfExists(partial);
                throw new IOException(DownloadStatus.encode("download.mcvoice.error.archive_too_small"));
            }
            Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING);

            listener.update(DownloadStatus.encode("download.mcvoice.status.extracting", model.nameKey()));
            extract(archive, targetDir, listener);
            Files.deleteIfExists(archive);
            if (VoiceRegistry.findSherpaModelFile(modelDir) == null) {
                throw new IOException(DownloadStatus.encode("download.mcvoice.error.verify_failed"));
            }
            listener.update(DownloadStatus.encode("download.mcvoice.status.done_sherpa", model.nameKey()));
        } catch (Exception e) {
            cleanup(targetDir, modelDir, archive);
            throw e;
        }
    }

    /** 是否存在可续传的半成品（.part）。 */
    public static boolean hasPartialDownload(String modelId) {
        ModelDef model = MODELS.get(modelId);
        if (model == null) {
            return false;
        }
        Path part = VoiceRegistry.getSherpaModelDir().resolve(model.archive() + ".part");
        try {
            return Files.isRegularFile(part) && Files.size(part) > 0L;
        } catch (IOException e) {
            return false;
        }
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
                downloadFromUri(uri, target, fileName, listener);
                return;
            } catch (Exception e) {
                lastError = e;
                // 保留 .part，交给下一个镜像继续续传（而不是删掉重来）。
            }
        }
        throw new IOException(DownloadStatus.encode("download.mcvoice.error.all_sources_failed", lastError.getMessage()), lastError);
    }

    /**
     * 从单个源下载，支持 HTTP Range 断点续传：
     * 已有 .part 时发 Range 请求，校验 Content-Range 起点后再追加写入；
     * 若源不支持续传或返回 416，则清空重下。
     */
    private static void downloadFromUri(URI uri, Path target, String fileName, ProgressListener listener)
            throws Exception {
        long existing = Files.isRegularFile(target) ? Files.size(target) : 0L;
        boolean restarted = false;

        while (true) {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(30))
                .header("User-Agent", "Mozilla/5.0")
                .GET();
            if (existing > 0L) {
                requestBuilder.header("Range", "bytes=" + existing + "-");
            }

            HttpResponse<InputStream> response = client.send(
                requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();

            if (status == 416) {
                if (existing > 0L && !restarted) {
                    listener.update(DownloadStatus.encode("download.mcvoice.status.restart_norange", fileName));
                    Files.deleteIfExists(target);
                    existing = 0L;
                    restarted = true;
                    continue;
                }
                try (InputStream in = response.body()) {
                    in.transferTo(OutputStream.nullOutputStream());
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
                throw new IOException(DownloadStatus.encode("download.mcvoice.error.html_not_file"));
            }

            long total = contentLength(response, status);
            boolean append = false;
            if (status == 206 && existing > 0L) {
                Optional<Long> rangeStart = contentRangeStart(response);
                if (rangeStart.isPresent() && rangeStart.get() != existing && !restarted) {
                    listener.update(DownloadStatus.encode("download.mcvoice.status.restart_noresume", fileName));
                    Files.deleteIfExists(target);
                    existing = 0L;
                    restarted = true;
                    continue;
                }
                if (rangeStart.isPresent() && rangeStart.get() != existing) {
                    throw new IOException(DownloadStatus.encode("download.mcvoice.error.bad_range"));
                }
                append = true;
            } else {
                if (existing > 0L) {
                    listener.update(DownloadStatus.encode("download.mcvoice.status.restart_noresume", fileName));
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
                            "download.mcvoice.status.progress\u001f%s\u001f%s",
                            fileName, String.format("%.1f / %.1f MB", done / 1024.0 / 1024.0, total / 1024.0 / 1024.0)));
                    } else {
                        listener.update(String.format(
                            "download.mcvoice.status.progress\u001f%s\u001f%s",
                            fileName, String.format("%.1f MB", done / 1024.0 / 1024.0)));
                    }
                }
            }
            return;
        }
    }

    private static long contentLength(HttpResponse<?> response, int status) {
        if (status == 206) {
            return contentRangeTotal(response).orElse(-1L);
        }
        return response.headers().firstValueAsLong("Content-Length").orElse(-1L);
    }

    private static Optional<Long> contentRangeStart(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Range").flatMap(value -> {
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
        return response.headers().firstValue("Content-Range").flatMap(value -> {
            Matcher matcher = CONTENT_RANGE_PATTERN.matcher(value);
            if (!matcher.find() || "*".equals(matcher.group(3))) {
                return Optional.empty();
            }
            try {
                return Optional.of(Long.parseLong(matcher.group(3)));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static void extract(Path archive, Path targetDir, ProgressListener listener) throws IOException {
        if (trySystemTar(archive, targetDir)) {
            return;
        }
        extractWithJava(archive, targetDir, listener);
    }

    private static void cleanup(Path targetDir, Path modelDir, Path archive) {
        try {
            Files.deleteIfExists(archive);
            // 注意：不删 archive + ".part"，那是断点续传的半成品，下次点按钮可继续。
        } catch (IOException ignored) {
        }
        if (VoiceRegistry.findSherpaModelFile(modelDir) == null) {
            deleteRecursively(modelDir);
        }
        deleteIfEmpty(targetDir);
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void deleteIfEmpty(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            if (stream.findAny().isEmpty()) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean trySystemTar(Path archive, Path targetDir) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                "tar",
                "-xjf",
                archive.toAbsolutePath().toString(),
                "-C",
                targetDir.toAbsolutePath().toString()
            );
            builder.redirectErrorStream(true);
            Process process = builder.start();
            Thread drainThread = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                }
            }, "MCVoice-SherpaTarOutput");
            drainThread.setDaemon(true);
            drainThread.start();
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void extractWithJava(Path archive, Path targetDir, ProgressListener listener) throws IOException {
        Path base = targetDir.toAbsolutePath().normalize();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new BZip2CompressorInputStream(Files.newInputStream(archive)))) {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                listener.update(DownloadStatus.encode("download.mcvoice.status.extracting_file", name));
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

    private record ModelDef(String nameKey, String archive) {
    }
}
