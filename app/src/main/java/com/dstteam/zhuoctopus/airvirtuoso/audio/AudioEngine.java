package com.dstteam.zhuoctopus.airvirtuoso.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import com.dstteam.zhuoctopus.airvirtuoso.R;

import java.util.HashMap;
import java.util.Map;

public class AudioEngine {
    private static final String TAG = "AudioEngine";
    private SoundPool soundPool;
    private final Map<Double, Integer> frequencySoundMap = new HashMap<>(); // Frequency -> SoundPool ID

    private static class KeySample {
        final double frequency;
        final int resourceId;

        KeySample(double frequency, int resourceId) {
            this.frequency = frequency;
            this.resourceId = resourceId;
        }
    }

    private static final KeySample[] KEY_SAMPLES = {
            new KeySample(220.00, R.raw.a3),
            new KeySample(246.94, R.raw.b3),
            new KeySample(261.63, R.raw.c4),
            new KeySample(293.66, R.raw.d4),
            new KeySample(329.63, R.raw.e4),
            new KeySample(349.23, R.raw.f4),
            new KeySample(392.00, R.raw.g4),
            new KeySample(440.00, R.raw.a4),
            new KeySample(493.88, R.raw.b4),
            new KeySample(523.25, R.raw.c5),
            new KeySample(587.33, R.raw.d5),
            new KeySample(659.25, R.raw.e5),
            new KeySample(698.46, R.raw.f5),
            new KeySample(783.99, R.raw.g5),
            new KeySample(880.00, R.raw.a5),
            new KeySample(987.77, R.raw.b5),
            new KeySample(1046.50, R.raw.c6)
    };

    public AudioEngine(Context context) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build();

        loadSamples(context);
    }

    private void loadSamples(Context context) {
        for (KeySample sample : KEY_SAMPLES) {
            int soundId = soundPool.load(context, sample.resourceId, 1);
            frequencySoundMap.put(sample.frequency, soundId);
        }
    }

    public void playNote(double frequency) {
        int soundId = -1;
        double minDiff = Double.MAX_VALUE;

        for (Map.Entry<Double, Integer> entry : frequencySoundMap.entrySet()) {
            double diff = Math.abs(entry.getKey() - frequency);
            if (diff < minDiff) {
                minDiff = diff;
                soundId = entry.getValue();
            }
        }

        if (soundId != -1 && minDiff < 1.0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        } else {
            Log.w(TAG, "No sample found for frequency: " + frequency);
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
