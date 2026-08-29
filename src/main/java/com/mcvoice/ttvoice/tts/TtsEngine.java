package com.mcvoice.ttvoice.tts;

public interface TtsEngine extends AutoCloseable {
    short[] synthesize(String text) throws Exception;
}
