package com.dstteam.zhuoctopus.airvirtuoso.analysis;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

/**
 * Detects hand gestures from MediaPipe hand landmarks.
 * Currently supports hi-five gesture detection for voice command activation.
 */
public class GestureDetector {

    // MediaPipe landmark indices
    private static final int WRIST = 0;
    private static final int THUMB_TIP = 4;
    private static final int INDEX_TIP = 8;
    private static final int INDEX_MCP = 5;
    private static final int MIDDLE_TIP = 12;
    private static final int MIDDLE_MCP = 9;
    private static final int RING_TIP = 16;
    private static final int RING_MCP = 13;
    private static final int PINKY_TIP = 20;
    private static final int PINKY_MCP = 17;

    /**
     * Detects if the hand is performing a "hi-five" gesture.
     * A hi-five gesture is characterized by:
     * 1. Hand raised (fingertips above wrist)
     * 2. All fingers extended (fingertips far from MCP joints)
     * 3. Palm facing forward (fingertips closer to camera than wrist)
     *
     * @param landmarks Hand landmarks from MediaPipe (21 points)
     * @return true if hi-five gesture is detected
     */
    public static boolean isHiFiveGesture(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 21) {
            return false;
        }

        NormalizedLandmark wrist = landmarks.get(WRIST);
        NormalizedLandmark thumbTip = landmarks.get(THUMB_TIP);
        NormalizedLandmark indexTip = landmarks.get(INDEX_TIP);
        NormalizedLandmark middleTip = landmarks.get(MIDDLE_TIP);
        NormalizedLandmark ringTip = landmarks.get(RING_TIP);
        NormalizedLandmark pinkyTip = landmarks.get(PINKY_TIP);

        NormalizedLandmark indexMcp = landmarks.get(INDEX_MCP);
        NormalizedLandmark middleMcp = landmarks.get(MIDDLE_MCP);
        NormalizedLandmark ringMcp = landmarks.get(RING_MCP);
        NormalizedLandmark pinkyMcp = landmarks.get(PINKY_MCP);

        // 1. Check if hand is raised (fingertips above wrist in Y coordinate)
        // In normalized coordinates, Y increases downward, so smaller Y = higher position
        float wristY = wrist.y();
        float avgFingertipY = (indexTip.y() + middleTip.y() + ringTip.y() + pinkyTip.y()) / 4.0f;
        
        // Hand is raised if average fingertip Y is significantly smaller (higher) than wrist Y
        // Threshold increased from 0.05f to 0.15f to require a more vertical/raised hand
        boolean handRaised = (wristY - avgFingertipY) > 0.15f;

        // 2. Check if all fingers are extended
        // Fingers are extended if fingertips are far from their MCP joints
        // Increased thresholds to ensure fingers are fully straight
        boolean thumbExtended = isFingerExtended(thumbTip, wrist, 0.12f); // Thumb uses wrist as reference
        boolean indexExtended = isFingerExtended(indexTip, indexMcp, 0.15f);
        boolean middleExtended = isFingerExtended(middleTip, middleMcp, 0.15f);
        boolean ringExtended = isFingerExtended(ringTip, ringMcp, 0.15f);
        boolean pinkyExtended = isFingerExtended(pinkyTip, pinkyMcp, 0.12f);

        // Require ALL 5 fingers to be extended (Stricter than before)
        int extendedCount = 0;
        if (thumbExtended) extendedCount++;
        if (indexExtended) extendedCount++;
        if (middleExtended) extendedCount++;
        if (ringExtended) extendedCount++;
        if (pinkyExtended) extendedCount++;

        boolean fingersExtended = extendedCount == 5;

        // 3. Check if palm is facing forward (using Z-coordinate depth)
        // In MediaPipe, Z represents depth relative to wrist
        // Palm facing forward means fingertips are closer to camera (smaller Z) than wrist
        float wristZ = wrist.z();
        float avgFingertipZ = (indexTip.z() + middleTip.z() + ringTip.z() + pinkyTip.z()) / 4.0f;
        
        // Palm facing forward if fingertips are closer (smaller Z) than wrist
        // Threshold increased to 0.05f to reduce false positives from slight tilts
        boolean palmForward = (wristZ - avgFingertipZ) > 0.05f;

        // Alternative check: Finger spread
        // In a hi-five, fingers should be spread out
        float fingerSpread = calculateFingerSpread(indexTip, middleTip, ringTip, pinkyTip);
        // Increased spread threshold
        boolean fingersSpread = fingerSpread > 0.04f; 

        // Combine checks: hand raised AND fingers extended AND palm forward AND fingers spread
        // Requiring ALL conditions makes it much less sensitive
        boolean isHiFive = handRaised && fingersExtended && palmForward && fingersSpread;

        return isHiFive;
    }

    /**
     * Checks if a finger is extended by measuring distance from tip to MCP joint.
     *
     * @param tip Fingertip landmark
     * @param mcp MCP joint landmark (or wrist for thumb)
     * @param threshold Minimum distance for finger to be considered extended
     * @return true if finger is extended
     */
    private static boolean isFingerExtended(NormalizedLandmark tip, NormalizedLandmark mcp, float threshold) {
        float dx = tip.x() - mcp.x();
        float dy = tip.y() - mcp.y();
        float dz = tip.z() - mcp.z();
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return distance > threshold;
    }

    /**
     * Calculates how spread out the fingers are (excluding thumb).
     * Measures the average distance between adjacent fingertips.
     *
     * @param index Index fingertip
     * @param middle Middle fingertip
     * @param ring Ring fingertip
     * @param pinky Pinky fingertip
     * @return Average spread distance
     */
    private static float calculateFingerSpread(
            NormalizedLandmark index,
            NormalizedLandmark middle,
            NormalizedLandmark ring,
            NormalizedLandmark pinky) {
        
        float dist1 = distance(index, middle);
        float dist2 = distance(middle, ring);
        float dist3 = distance(ring, pinky);
        
        return (dist1 + dist2 + dist3) / 3.0f;
    }

    /**
     * Calculates 3D distance between two landmarks.
     */
    private static float distance(NormalizedLandmark a, NormalizedLandmark b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float dz = a.z() - b.z();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

