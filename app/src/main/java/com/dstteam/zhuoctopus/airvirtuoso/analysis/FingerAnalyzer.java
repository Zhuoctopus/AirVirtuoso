package com.dstteam.zhuoctopus.airvirtuoso.analysis;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

/**
 * Analyzes hand landmarks to determine which finger is being used to press
 * keys.
 * Maps MediaPipe's 21 hand landmarks to 5 finger IDs.
 */
public class FingerAnalyzer {

    // Finger IDs
    public static final int THUMB = 0;
    public static final int INDEX = 1;
    public static final int MIDDLE = 2;
    public static final int RING = 3;
    public static final int PINKY = 4;

    // MediaPipe landmark indices for fingertips
    private static final int THUMB_TIP = 4;
    private static final int INDEX_TIP = 8;
    private static final int MIDDLE_TIP = 12;
    private static final int RING_TIP = 16;
    private static final int PINKY_TIP = 20;

    /**
     * Result containing finger ID and confidence score.
     */
    public static class FingerResult {
        public final int fingerId;
        public final float confidence;

        public FingerResult(int fingerId, float confidence) {
            this.fingerId = fingerId;
            this.confidence = confidence;
        }

        public String getFingerName() {
            switch (fingerId) {
                case THUMB:
                    return "Thumb";
                case INDEX:
                    return "Index";
                case MIDDLE:
                    return "Middle";
                case RING:
                    return "Ring";
                case PINKY:
                    return "Pinky";
                default:
                    return "Unknown";
            }
        }
    }

    /**
     * Detects which finger is closest to a given point (key position).
     *
     * @param landmarks Hand landmarks from MediaPipe (21 points)
     * @param targetX   X coordinate of the key
     * @param targetY   Y coordinate of the key
     * @return FingerResult with finger ID and confidence
     */
    public static FingerResult detectFinger(List<NormalizedLandmark> landmarks, float targetX, float targetY) {
        if (landmarks == null || landmarks.size() < 21) {
            return new FingerResult(-1, 0.0f);
        }

        // Get fingertip positions
        NormalizedLandmark thumbTip = landmarks.get(THUMB_TIP);
        NormalizedLandmark indexTip = landmarks.get(INDEX_TIP);
        NormalizedLandmark middleTip = landmarks.get(MIDDLE_TIP);
        NormalizedLandmark ringTip = landmarks.get(RING_TIP);
        NormalizedLandmark pinkyTip = landmarks.get(PINKY_TIP);

        // Calculate distances to target point
        float thumbDist = distance(thumbTip.x(), thumbTip.y(), targetX, targetY);
        float indexDist = distance(indexTip.x(), indexTip.y(), targetX, targetY);
        float middleDist = distance(middleTip.x(), middleTip.y(), targetX, targetY);
        float ringDist = distance(ringTip.x(), ringTip.y(), targetX, targetY);
        float pinkyDist = distance(pinkyTip.x(), pinkyTip.y(), targetX, targetY);

        // Find closest finger
        float minDist = Math.min(thumbDist, Math.min(indexDist, Math.min(middleDist, Math.min(ringDist, pinkyDist))));

        int fingerId;
        if (minDist == thumbDist) {
            fingerId = THUMB;
        } else if (minDist == indexDist) {
            fingerId = INDEX;
        } else if (minDist == middleDist) {
            fingerId = MIDDLE;
        } else if (minDist == ringDist) {
            fingerId = RING;
        } else {
            fingerId = PINKY;
        }

        // Calculate confidence (inverse of distance, clamped)
        float confidence = Math.max(0.0f, Math.min(1.0f, 1.0f - (minDist * 10.0f)));

        return new FingerResult(fingerId, confidence);
    }

    /**
     * Get fingertip position for a specific finger.
     */
    public static NormalizedLandmark getFingertip(List<NormalizedLandmark> landmarks, int fingerId) {
        if (landmarks == null || landmarks.size() < 21) {
            return null;
        }

        switch (fingerId) {
            case THUMB:
                return landmarks.get(THUMB_TIP);
            case INDEX:
                return landmarks.get(INDEX_TIP);
            case MIDDLE:
                return landmarks.get(MIDDLE_TIP);
            case RING:
                return landmarks.get(RING_TIP);
            case PINKY:
                return landmarks.get(PINKY_TIP);
            default:
                return null;
        }
    }

    /**
     * Calculate Euclidean distance between two points.
     */
    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
