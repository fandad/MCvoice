package com.mcvoice.ttvoice.tts;

import com.mcvoice.ttvoice.TtVoiceClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class SapiEngine implements TtsEngine {
    private static final String SYNTH_SCRIPT =
        "$ErrorActionPreference = 'Stop'; "
        + "$voice = New-Object -ComObject SAPI.SpVoice; "
        + "$voice.Volume = 100; "
        + "if (-not [string]::IsNullOrWhiteSpace($env:MCVOICE_VOICE)) { "
        + "foreach ($token in $voice.GetVoices()) { "
        + "if ($token.GetDescription() -eq $env:MCVOICE_VOICE) { $voice.Voice = $token; break } } }; "
        + "$stream = New-Object -ComObject SAPI.SpFileStream; "
        + "$stream.Open($env:MCVOICE_OUT, 3); "
        + "try { $voice.AudioOutputStream = $stream; $voice.Speak($env:MCVOICE_TEXT) } "
        + "finally { $stream.Close() }";

    private final String voiceName;

    public SapiEngine(Voice voiceData) {
        this.voiceName = voiceData.getModelPath();
    }

    @Override
    public short[] synthesize(String text) throws Exception {
        Path wav = Files.createTempFile("mcvoice-sapi-", ".wav");
        try {
            ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", SYNTH_SCRIPT);
            builder.environment().put("MCVOICE_VOICE", voiceName);
            builder.environment().put("MCVOICE_TEXT", text);
            builder.environment().put("MCVOICE_OUT", wav.toAbsolutePath().toString());
            builder.redirectErrorStream(true);

            Process process = builder.start();
            Thread drainThread = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                }
            }, "MCVoice-SAPI-Output");
            drainThread.setDaemon(true);
            drainThread.start();

            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("SAPI 语音生成超时");
            }
            if (process.exitValue() != 0) {
                throw new IOException("SAPI 语音生成失败，退出码 " + process.exitValue());
            }

            byte[] wavBytes = Files.readAllBytes(wav);
            if (wavBytes.length < 44) {
                throw new IOException("SAPI 没有生成有效的 WAV 文件");
            }
            return AudioUtil.readWav(wavBytes);
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    @Override
    public void close() {
    }
}
