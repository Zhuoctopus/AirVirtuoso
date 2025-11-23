package com.dstteam.zhuoctopus.airvirtuoso.model;

public class Note {
    private String name; // e.g., "A", "B", "C"
    private int octave; // e.g., 3, 4, 5
    private double frequency;
    private int durationMs; // Duration in milliseconds for demo playback
    private int recommendedFinger; // 0-4 for fingers, -1 for none

    public Note(String name, int octave, double frequency) {
        this(name, octave, frequency, 500, -1); // Default 500ms, no finger
    }

    public Note(String name, int octave, double frequency, int durationMs) {
        this(name, octave, frequency, durationMs, -1); // Default no finger
    }

    public Note(String name, int octave, double frequency, int durationMs, int recommendedFinger) {
        this.name = name;
        this.octave = octave;
        this.frequency = frequency;
        this.durationMs = durationMs;
        this.recommendedFinger = recommendedFinger;
    }

    public String getName() {
        return name;
    }

    public int getOctave() {
        return octave;
    }

    public double getFrequency() {
        return frequency;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public int getRecommendedFinger() {
        return recommendedFinger;
    }

    public String getDisplayName() {
        return name + octave;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
