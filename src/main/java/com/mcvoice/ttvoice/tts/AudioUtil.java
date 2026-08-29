package com.mcvoice.ttvoice.tts;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AudioUtil {
    public static final int OUTPUT_SAMPLE_RATE = 48_000;
    public static final int FRAME_SIZE = OUTPUT_SAMPLE_RATE / 50;

    private AudioUtil() {
    }

    public static short[] resample(short[] input, int inputRate) {
        if (inputRate == OUTPUT_SAMPLE_RATE) {
            return input;
        }
        double ratio = (double) OUTPUT_SAMPLE_RATE / inputRate;
        short[] output = new short[(int) Math.round(input.length * ratio)];
        for (int i = 0; i < output.length; i++) {
            double srcPos = i / ratio;
            int i0 = Math.max(0, Math.min(input.length - 1, (int) srcPos));
            int i1 = Math.max(0, Math.min(input.length - 1, i0 + 1));
            double t = srcPos - i0;
            output[i] = (short) Math.round(input[i0] * (1.0 - t) + input[i1] * t);
        }
        return output;
    }

    public static short[] applyVolume(short[] input, float volume) {
        if (volume <= 0.0f) {
            return new short[input.length];
        }
        if (Math.abs(volume - 1.0f) < 0.001f) {
            return input;
        }
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            int value = Math.round(input[i] * volume);
            output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
        return output;
    }

    public static short[] floatToShort(float[] input) {
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            float value = input[i] * 32767.0f;
            output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
        }
        return output;
    }

    public static byte[] toBytes(short[] pcm) {
        ByteBuffer buffer = ByteBuffer.allocate(pcm.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (short value : pcm) {
            buffer.putShort(value);
        }
        return buffer.array();
    }

    public static short[] readWav(byte[] wavBytes) throws Exception {
        return readAudio(wavBytes);
    }

    public static short[] readAudio(byte[] audioBytes) throws Exception {
        if (isMp3(audioBytes)) {
            return readMp3(audioBytes);
        }
        AudioFormat format = new AudioFormat(OUTPUT_SAMPLE_RATE, 16, 1, true, false);
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioBytes));
             AudioInputStream converted = AudioSystem.getAudioInputStream(format, source)) {
            byte[] bytes = converted.readAllBytes();
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            short[] pcm = new short[bytes.length / Short.BYTES];
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = buffer.getShort();
            }
            return pcm;
        }
    }

    private static boolean isMp3(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            return false;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 'I'
                && (bytes[1] & 0xFF) == 'D'
                && (bytes[2] & 0xFF) == '3') {
            return true;
        }
        return (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0;
    }

    private static short[] readMp3(byte[] mp3Bytes) throws Exception {
        Bitstream bitstream = new Bitstream(new BufferedInputStream(new ByteArrayInputStream(mp3Bytes)));
        try {
            Decoder decoder = new Decoder();
            List<short[]> frames = new ArrayList<>();
            int sampleRate = 0;
            int channels = 1;
            int totalSamples = 0;

            while (true) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                int frameLength = output.getBufferLength();
                if (frameLength > 0) {
                    int frameChannels = Math.max(1, output.getChannelCount());
                    frames.add(Arrays.copyOf(output.getBuffer(), frameLength));
                    channels = frameChannels;
                    sampleRate = output.getSampleFrequency();
                    totalSamples += frameLength / frameChannels;
                }
                bitstream.closeFrame();
            }

            if (frames.isEmpty() || totalSamples == 0 || sampleRate <= 0) {
                throw new IllegalStateException("MP3 中没有可解码的音频");
            }

            short[] mono = new short[totalSamples];
            int out = 0;
            for (short[] frame : frames) {
                int samples = frame.length / channels;
                if (channels == 1) {
                    System.arraycopy(frame, 0, mono, out, samples);
                } else {
                    for (int i = 0; i < samples; i++) {
                        int left = frame[i * channels];
                        int right = frame[i * channels + 1];
                        mono[out + i] = (short) ((left + right) / 2);
                    }
                }
                out += samples;
            }
            return resample(mono, sampleRate);
        } finally {
            bitstream.close();
        }
    }
}
