package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.TtVoiceClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ExternalCommandEngine implements TtsEngine {
    private final String command;

    public ExternalCommandEngine(String command) {
        this.command = command;
    }

    @Override
    public short[] synthesize(String text) throws Exception {
        Path wav = Files.createTempFile("mcvoice-external-", ".wav");
        try {
            List<String> commandTokens = parseCommand(command, text, wav);
            if (commandTokens.isEmpty()) {
                throw new IllegalStateException("外部TTS命令为空");
            }

            ProcessBuilder builder = new ProcessBuilder(commandTokens);
            builder.environment().put("MCVOICE_TEXT", text);
            builder.environment().put("MCVOICE_OUT", wav.toAbsolutePath().toString());
            builder.redirectErrorStream(true);

            TtVoiceClient.LOGGER.info("运行外部TTS命令: {}", command);
            Process process = builder.start();
            Thread drainThread = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                }
            }, "MCVoice-ExternalTTS-Output");
            drainThread.setDaemon(true);
            drainThread.start();
            try {
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException("外部TTS命令超时");
                }
                if (process.exitValue() != 0) {
                    throw new IOException("外部TTS命令退出码 " + process.exitValue());
                }
            } finally {
                process.destroyForcibly();
            }

            byte[] wavBytes = Files.readAllBytes(wav);
            if (wavBytes.length < 44) {
                throw new IOException("外部TTS没有生成有效的WAV文件");
            }
            return AudioUtil.readWav(wavBytes);
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    @Override
    public void close() {
    }

    private static List<String> parseCommand(String template, String text, Path wav) {
        String command = template
            .replace("{text}", text)
            .replace("{file}", wav.toAbsolutePath().toString());

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (inQuotes) {
                if (c == '\\' && i + 1 < command.length()) {
                    current.append(command.charAt(++i));
                } else if (c == quote) {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
