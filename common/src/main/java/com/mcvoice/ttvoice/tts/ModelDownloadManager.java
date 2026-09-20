package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.McVoiceConstants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelDownloadManager {
    public enum State {
        NOT_DOWNLOADED,
        RESUMABLE,
        DOWNLOADING,
        DOWNLOADED,
        FAILED
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile String statusText = "";

    private ModelDownloadManager() {
    }

    public static ModelDownloadManager get() {
        return Holder.INSTANCE;
    }

    public synchronized void prepare(String modelId, boolean sherpa) {
        Entry entry = entry(modelId, sherpa);
        if (VoiceRegistry.isModelDownloaded(modelId, sherpa)) {
            entry.state = State.DOWNLOADED;
            return;
        }
        if (PiperModelDownloader.hasPartialDownload(modelId)) {
            entry.state = State.RESUMABLE;
            if (statusText == null || statusText.isBlank()) {
                statusText = DownloadStatus.encode("download.mcvoice.status.resumable");
            }
            return;
        }
        if (entry.state != State.DOWNLOADING && entry.state != State.FAILED) {
            entry.state = State.NOT_DOWNLOADED;
        }
    }

    public synchronized void start(String modelId, boolean sherpa, String label) {
        Entry entry = entry(modelId, sherpa);
        if (VoiceRegistry.isModelDownloaded(modelId, sherpa)) {
            entry.state = State.DOWNLOADED;
            statusText = DownloadStatus.encode("download.mcvoice.status.done", nameToken(modelId));
            return;
        }
        if (entry.state == State.DOWNLOADING || entry.state == State.DOWNLOADED) {
            return;
        }

        boolean resumed = sherpa
            ? SherpaModelDownloader.hasPartialDownload(modelId)
            : PiperModelDownloader.hasPartialDownload(modelId);
        entry.state = State.DOWNLOADING;
        statusText = DownloadStatus.encode(resumed ? "download.mcvoice.status.prepare_resume" : "download.mcvoice.status.prepare", nameToken(modelId));

        Thread thread = new Thread(() -> {
            try {
                if (sherpa) {
                    SherpaModelDownloader.download(modelId, VoiceRegistry.getSherpaModelDir(),
                        text -> statusText = text);
                } else {
                    PiperModelDownloader.download(modelId, VoiceRegistry.getModelDir(),
                        text -> statusText = text);
                }
                entry.state = State.DOWNLOADED;
                statusText = DownloadStatus.encode("download.mcvoice.status.done", nameToken(modelId));
                McVoiceConstants.LOGGER.info("模型下载完成：{}", label);
            } catch (Exception e) {
                String error = e.getMessage() == null ? e.toString() : e.getMessage();
                boolean resumable = sherpa
                    ? SherpaModelDownloader.hasPartialDownload(modelId)
                    : PiperModelDownloader.hasPartialDownload(modelId);
                entry.state = resumable ? State.RESUMABLE : State.FAILED;
                statusText = DownloadStatus.encode(resumable ? "download.mcvoice.status.failed_resumable" : "download.mcvoice.status.failed", error
                    );
                McVoiceConstants.LOGGER.warn(
                    "模型下载失败：{}（{}，{}）", label, error, resumable ? "已保留断点" : "无断点", e);
            }
        }, "MCVoice-ModelDownload-" + modelId);
        thread.setDaemon(true);
        thread.start();
    }

    public State stateOf(String modelId, boolean sherpa) {
        return entry(modelId, sherpa).state;
    }

    public String statusText() {
        return statusText;
    }

    /** 状态文案里的模型名：已知模型给翻译 key，未知退回原始 id。 */
    private static String nameToken(String modelId) {
        String key = VoiceRegistry.modelNameKey(modelId);
        return key == null ? modelId : key;
    }

    private Entry entry(String modelId, boolean sherpa) {
        return entries.computeIfAbsent(modelId + ":" + sherpa, ignored -> new Entry());
    }

    private static final class Entry {
        private volatile State state = State.NOT_DOWNLOADED;
    }

    private static final class Holder {
        private static final ModelDownloadManager INSTANCE = new ModelDownloadManager();
    }
}
