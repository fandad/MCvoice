package com.mcvoice.ttvoice.tts;

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
                statusText = "检测到上次未完成的模型下载，点击对应按钮可继续";
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
            statusText = "已完成：" + label + " 已放入 mcvoice/models";
            return;
        }
        if (entry.state == State.DOWNLOADING || entry.state == State.DOWNLOADED) {
            return;
        }

        boolean resumed = PiperModelDownloader.hasPartialDownload(modelId);
        entry.state = State.DOWNLOADING;
        statusText = (resumed ? "准备继续下载：" : "准备下载：") + label;

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
                statusText = "已完成：" + label + " 已放入 mcvoice/models";
            } catch (Exception e) {
                String error = e.getMessage() == null ? e.toString() : e.getMessage();
                boolean resumable = PiperModelDownloader.hasPartialDownload(modelId);
                entry.state = resumable ? State.RESUMABLE : State.FAILED;
                statusText = "下载失败：" + error
                    + (resumable ? " · 已保留断点，点击按钮可继续下载" : "");
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
