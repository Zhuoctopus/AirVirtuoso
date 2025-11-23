package com.dstteam.zhuoctopus.airvirtuoso.analysis;

/**
 * Analyzes timing accuracy of played notes.
 * Tracks when notes are pressed and compares to expected timing.
 */
public class TimingAnalyzer {

    private long songStartTime = 0;
    private long lastNoteTime = 0;
    private int notesPlayed = 0;
    private float totalDeviation = 0.0f;

    // Timing thresholds (in milliseconds)
    private static final long PERFECT_WINDOW = 50; // Within 50ms = perfect
    private static final long GOOD_WINDOW = 150; // Within 150ms = good

    public enum TimingFeedback {
        PERFECT, // Within perfect window
        GOOD, // Within good window
        EARLY, // Too early
        LATE // Too late
    }

    /**
     * Result containing timing feedback and deviation.
     */
    public static class TimingResult {
        public final TimingFeedback feedback;
        public final long deviation; // In milliseconds (positive = late, negative = early)
        public final float accuracy; // 0.0 to 1.0

        public TimingResult(TimingFeedback feedback, long deviation, float accuracy) {
            this.feedback = feedback;
            this.deviation = deviation;
            this.accuracy = accuracy;
        }

        public String getMessage() {
            switch (feedback) {
                case PERFECT:
                    return "🎯 Perfect timing!";
                case GOOD:
                    return "👍 Good timing";
                case EARLY:
                    return "⏪ Too early";
                case LATE:
                    return "⏩ Too late";
                default:
                    return "";
            }
        }
    }

    /**
     * Start tracking a new song.
     */
    public void startSong() {
        songStartTime = System.currentTimeMillis();
        lastNoteTime = songStartTime;
        notesPlayed = 0;
        totalDeviation = 0.0f;
    }

    /**
     * Analyze timing of a note press.
     *
     * @param expectedTimeOffset Expected time offset from song start (in ms)
     * @return TimingResult with feedback and accuracy
     */
    public TimingResult analyzeNoteTiming(long expectedTimeOffset) {
        long currentTime = System.currentTimeMillis();
        long actualOffset = currentTime - songStartTime;
        long deviation = actualOffset - expectedTimeOffset;

        // Determine feedback
        TimingFeedback feedback;
        if (Math.abs(deviation) <= PERFECT_WINDOW) {
            feedback = TimingFeedback.PERFECT;
        } else if (Math.abs(deviation) <= GOOD_WINDOW) {
            feedback = TimingFeedback.GOOD;
        } else if (deviation < 0) {
            feedback = TimingFeedback.EARLY;
        } else {
            feedback = TimingFeedback.LATE;
        }

        // Calculate accuracy (1.0 = perfect, decreases with deviation)
        float accuracy = Math.max(0.0f, 1.0f - (Math.abs(deviation) / 1000.0f));

        // Track statistics
        notesPlayed++;
        totalDeviation += Math.abs(deviation);
        lastNoteTime = currentTime;

        return new TimingResult(feedback, deviation, accuracy);
    }

    /**
     * Get average timing accuracy for the current song.
     *
     * @return Average accuracy (0.0 to 1.0)
     */
    public float getAverageAccuracy() {
        if (notesPlayed == 0) {
            return 1.0f;
        }

        float avgDeviation = totalDeviation / notesPlayed;
        return Math.max(0.0f, 1.0f - (avgDeviation / 1000.0f));
    }

    /**
     * Get percentage accuracy (0-100%).
     */
    public int getAccuracyPercentage() {
        return Math.round(getAverageAccuracy() * 100);
    }

    /**
     * Reset the analyzer.
     */
    public void reset() {
        songStartTime = 0;
        lastNoteTime = 0;
        notesPlayed = 0;
        totalDeviation = 0.0f;
    }
}
