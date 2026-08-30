package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.McVoiceConstants;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class SapiVoices {
    private static final String LIST_SCRIPT =
        "$voice = New-Object -ComObject SAPI.SpVoice; "
        + "$voice.GetVoices() | ForEach-Object { $_.GetDescription() }";
    private static final long CACHE_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final Object LOCK = new Object();
    private static volatile List<String> cached = List.of();
    private static volatile long cachedAt;

    private SapiVoices() {
    }

    public static List<String> list() {
        if (!isWindows()) {
            return List.of();
        }
        long now = System.nanoTime();
        List<String> current = cached;
        if (now - cachedAt < CACHE_NANOS) {
            return current;
        }
        synchronized (LOCK) {
            now = System.nanoTime();
            current = cached;
            if (now - cachedAt < CACHE_NANOS) {
                return current;
            }
            List<String> loaded = load();
            cached = List.copyOf(loaded);
            cachedAt = now;
            return loaded;
        }
    }

    private static List<String> load() {
        List<String> voices = new ArrayList<>();
        if (!isWindows()) {
            return voices;
        }
        try {
            Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", LIST_SCRIPT)
                .redirectErrorStream(true)
                .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String voice = line.trim();
                    if (!voice.isBlank() && !voice.equalsIgnoreCase("True")) {
                        voices.add(voice);
                    }
                }
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            McVoiceConstants.LOGGER.warn("无法读取 Windows SAPI 声线", e);
        }
        return voices;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }
}
