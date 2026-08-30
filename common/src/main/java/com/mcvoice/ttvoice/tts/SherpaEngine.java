package com.mcvoice.ttvoice.tts;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

public final class SherpaEngine implements TtsEngine {
    private final OfflineTts tts;
    private final int speakerId;

    public SherpaEngine(Voice voiceData) throws Exception {
        OfflineTtsVitsModelConfig.Builder vits = OfflineTtsVitsModelConfig.builder()
            .setModel(voiceData.getModelPath())
            .setTokens(voiceData.getTokensPath());
        if (!voiceData.getLexiconPath().isBlank()) {
            vits.setLexicon(voiceData.getLexiconPath());
        }
        if (voiceData.getDataDir().endsWith("dict")) {
            vits.setDictDir(voiceData.getDataDir());
        } else if (!voiceData.getDataDir().isBlank()) {
            vits.setDataDir(voiceData.getDataDir());
        }

        OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
            .setVits(vits.build())
            .setNumThreads(2)
            .build();
        OfflineTtsConfig config = OfflineTtsConfig.builder()
            .setModel(modelConfig)
            .build();
        this.tts = new OfflineTts(config);
        this.speakerId = voiceData.getSpeakerId();
    }

    @Override
    public short[] synthesize(String text) throws Exception {
        GeneratedAudio audio = tts.generate(text, speakerId, 1.0f);
        if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
            throw new IllegalStateException("Sherpa 没有生成音频");
        }
        return AudioUtil.resample(AudioUtil.floatToShort(audio.getSamples()), audio.getSampleRate());
    }

    @Override
    public void close() throws Exception {
        tts.release();
    }
}
