package com.snakearena.core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SoundManager tổng hợp âm thanh 8-bit (procedural synthesized sound) thuần Java,
 * không cần tải file ngoài, phát âm thanh tức thì trên thread riêng biệt.
 */
public class SoundManager {
    private static final float SAMPLE_RATE = 22050f;
    private static final ExecutorService soundPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundThread");
        t.setDaemon(true);
        return t;
    });

    private static boolean soundEnabled = true;

    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static void playEat() {
        if (!soundEnabled) return;
        soundPool.submit(() -> playToneSweep(520, 880, 70, 0.4f, WaveType.SQUARE));
    }

    public static void playDash() {
        if (!soundEnabled) return;
        soundPool.submit(() -> playToneSweep(300, 150, 90, 0.35f, WaveType.NOISE));
    }

    public static void playHit() {
        if (!soundEnabled) return;
        soundPool.submit(() -> playToneSweep(250, 80, 110, 0.45f, WaveType.SAWTOOTH));
    }

    public static void playEvolve() {
        if (!soundEnabled) return;
        soundPool.submit(() -> {
            int[] notes = {440, 554, 659, 880};
            for (int note : notes) {
                playTone(note, 65, 0.5f, WaveType.TRIANGLE);
            }
        });
    }

    public static void playEagle() {
        if (!soundEnabled) return;
        soundPool.submit(() -> playToneSweep(950, 550, 180, 0.45f, WaveType.SAWTOOTH));
    }

    public static void playEagleDive() {
        if (!soundEnabled) return;
        soundPool.submit(() -> playToneSweep(850, 180, 240, 0.5f, WaveType.SAWTOOTH));
    }

    public static void playVenomSpit() {
        if (!soundEnabled) return;
        soundPool.submit(() -> playToneSweep(680, 240, 120, 0.45f, WaveType.SAWTOOTH));
    }

    public static void playRattleTail() {
        if (!soundEnabled) return;
        soundPool.submit(() -> {
            for (int i = 0; i < 4; i++) {
                playTone(850 + (i % 2) * 350, 32, 0.45f, WaveType.NOISE);
            }
        });
    }

    public static void playGameOver() {
        if (!soundEnabled) return;
        soundPool.submit(() -> {
            int[] notes = {350, 310, 260, 190};
            for (int note : notes) {
                playTone(note, 120, 0.5f, WaveType.SQUARE);
            }
        });
    }

    public static void playVictory() {
        if (!soundEnabled) return;
        soundPool.submit(() -> {
            int[] notes = {523, 659, 784, 1046, 1318, 1568};
            int[] durations = {90, 90, 90, 120, 120, 360};
            for (int i = 0; i < notes.length; i++) {
                playTone(notes[i], durations[i], 0.55f, WaveType.TRIANGLE);
            }
        });
    }

    public static void playButtonClick() {
        if (!soundEnabled) return;
        soundPool.submit(() -> playToneSweep(440, 660, 45, 0.35f, WaveType.SQUARE));
    }

    private enum WaveType { SQUARE, TRIANGLE, SAWTOOTH, NOISE }

    private static void playTone(int frequency, int durationMs, float volume, WaveType wave) {
        try {
            int numSamples = (int) (SAMPLE_RATE * (durationMs / 1000.0));
            byte[] buffer = new byte[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double time = i / SAMPLE_RATE;
                double sample = generateSample(wave, frequency, time);
                // Apply attack and decay envelope
                double envelope = 1.0;
                if (i < numSamples * 0.1) envelope = (double) i / (numSamples * 0.1);
                else if (i > numSamples * 0.7) envelope = (double) (numSamples - i) / (numSamples * 0.3);
                buffer[i] = (byte) (sample * envelope * volume * 127);
            }
            writeAudio(buffer);
        } catch (Exception ignored) {
        }
    }

    private static void playToneSweep(int startFreq, int endFreq, int durationMs, float volume, WaveType wave) {
        try {
            int numSamples = (int) (SAMPLE_RATE * (durationMs / 1000.0));
            byte[] buffer = new byte[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double progress = (double) i / numSamples;
                double freq = startFreq + (endFreq - startFreq) * progress;
                double time = i / SAMPLE_RATE;
                double sample = generateSample(wave, freq, time);
                double envelope = 1.0 - progress;
                buffer[i] = (byte) (sample * envelope * volume * 127);
            }
            writeAudio(buffer);
        } catch (Exception ignored) {
        }
    }

    private static double generateSample(WaveType wave, double freq, double time) {
        double cycle = (time * freq) % 1.0;
        return switch (wave) {
            case SQUARE -> cycle < 0.5 ? 1.0 : -1.0;
            case TRIANGLE -> 2.0 * Math.abs(2.0 * cycle - 1.0) - 1.0;
            case SAWTOOTH -> 2.0 * cycle - 1.0;
            case NOISE -> (Math.random() * 2.0) - 1.0;
        };
    }

    private static void writeAudio(byte[] buffer) {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, buffer.length);
                line.start();
                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {
        }
    }
}
