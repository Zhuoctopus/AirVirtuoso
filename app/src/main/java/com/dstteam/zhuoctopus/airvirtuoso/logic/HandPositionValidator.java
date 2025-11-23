package com.dstteam.zhuoctopus.airvirtuoso.logic;

import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;

public class HandPositionValidator {

    // Key indices for our 17 white keys (A3-C6)
    // 0=A3, 1=B3, 2=C4, 3=D4, 4=E4, 5=F4, 6=G4, 7=A4, 8=B4, 9=C5, 10=D5, 11=E5,
    // 12=F5, 13=G5, 14=A5, 15=B5, 16=C6

    private static final int LEFT_HAND_MAX_KEY_INDEX = 9; // Up to C5 allowed for Left Hand
    private static final int RIGHT_HAND_MIN_KEY_INDEX = 2;  // Down to C4 allowed for Right Hand

    public enum HandType {
        LEFT,
        RIGHT,
        UNKNOWN
    }

    public static class ValidationResult {
        public boolean isCorrect;
        public HandType detectedHand;
        public String reason;

        public ValidationResult(boolean isCorrect, HandType detectedHand, String reason) {
            this.isCorrect = isCorrect;
            this.detectedHand = detectedHand;
            this.reason = reason;
        }
    }

    /**
     * Validate if a finger on a specific key is correct based on piano conventions
     * 
     * @param handLandmarkerResult MediaPipe result containing handedness
     * @param handIndex            Which hand (0 for first detected hand, 1 for
     *                             second)
     * @param fingerIndex          Which fingertip (4=thumb, 8=index, 12=middle,
     *                             16=ring, 20=pinky)
     * @param keyIndex             Which key (0-16 for A3-C6)
     * @param wristX               Wrist X position (normalized 0-1, for left/right
     *                             validation)
     * @return ValidationResult with correctness and reason
     */
    public ValidationResult validateFingerPosition(
            HandLandmarkerResult handLandmarkerResult,
            int handIndex,
            int fingerIndex,
            int keyIndex,
            float wristX) {

        if (handLandmarkerResult == null || handIndex >= handLandmarkerResult.handedness().size()) {
            return new ValidationResult(false, HandType.UNKNOWN, "No hand detected");
        }

        // Get MediaPipe's handedness detection
        List<Category> handedness = handLandmarkerResult.handedness().get(handIndex);
        HandType detectedHand = HandType.UNKNOWN;

        if (!handedness.isEmpty()) {
            String label = handedness.get(0).categoryName();
            // MediaPipe returns "Left" or "Right" from camera perspective
            // We need to flip this because camera is mirrored
            if (label.equals("Left")) {
                detectedHand = HandType.RIGHT; // Camera's left = user's right
            } else if (label.equals("Right")) {
                detectedHand = HandType.LEFT; // Camera's right = user's left
            }
        }

        // Double-check with wrist position if handedness confidence is low or ambiguous
        // But generally trust the classifier first unless it completely contradicts position
        // (e.g. "Right Hand" on far left of screen)
        if (wristX < 0.3 && detectedHand == HandType.RIGHT) {
             // Possible error, but user could be crossing hands. 
             // For now, let's stick to detectedHand but maybe add a warning.
        }

        // Convert MediaPipe finger index to finger number (1-5)
        int fingerNumber = getFingerNumber(fingerIndex);

        // Validate hand zone (Relaxed)
        boolean isLeftZone = keyIndex <= LEFT_HAND_MAX_KEY_INDEX;
        boolean isRightZone = keyIndex >= RIGHT_HAND_MIN_KEY_INDEX;

        // Check if hand is in correct zone
        if (detectedHand == HandType.LEFT && !isLeftZone) {
            return new ValidationResult(false, detectedHand, "Left hand too far right");
        }
        if (detectedHand == HandType.RIGHT && !isRightZone) {
            return new ValidationResult(false, detectedHand, "Right hand too far left");
        }

        // Check finger appropriateness for the key
        boolean fingerIsAppropriate = isFingerAppropriateForKey(detectedHand, fingerNumber, keyIndex);

        if (!fingerIsAppropriate) {
            return new ValidationResult(false, detectedHand, "Comfortable reach exceeded");
        }

        return new ValidationResult(true, detectedHand, "Correct position");
    }

    /**
     * Convert MediaPipe landmark index to piano finger number (1-5)
     */
    private int getFingerNumber(int landmarkIndex) {
        switch (landmarkIndex) {
            case 4:
                return 1; // Thumb
            case 8:
                return 2; // Index
            case 12:
                return 3; // Middle
            case 16:
                return 4; // Ring
            case 20:
                return 5; // Pinky
            default:
                return 0; // Unknown
        }
    }

    /**
     * Check if finger is appropriate for the key based on hand and position.
     * Ranges are relaxed to allow for hand movement and comfortable reaches.
     */
    private boolean isFingerAppropriateForKey(HandType hand, int fingerNumber, int keyIndex) {
        if (hand == HandType.LEFT) {
            // Left hand finger mapping (A3=0 ... C6=16)
            // Tighter ranges than before, but covering C4 for Pinky
            switch (fingerNumber) {
                case 5: // Pinky
                    return keyIndex >= 0 && keyIndex <= 4; // A3 - E4 (Includes C4)
                case 4: // Ring
                    return keyIndex >= 0 && keyIndex <= 5; // A3 - F4
                case 3: // Middle
                    return keyIndex >= 1 && keyIndex <= 6; // B3 - G4
                case 2: // Index
                    return keyIndex >= 2 && keyIndex <= 7; // C4 - A4
                case 1: // Thumb
                    return keyIndex >= 2 && keyIndex <= 9; // C4 - C5
                default:
                    return false;
            }
        } else if (hand == HandType.RIGHT) {
            // Right hand finger mapping (A3=0 ... C6=16)
            switch (fingerNumber) {
                case 1: // Thumb
                    return keyIndex >= 2 && keyIndex <= 9; // C4 - C5
                case 2: // Index
                    return keyIndex >= 3 && keyIndex <= 10; // D4 - D5
                case 3: // Middle
                    return keyIndex >= 4 && keyIndex <= 12; // E4 - F5
                case 4: // Ring
                    return keyIndex >= 5 && keyIndex <= 14; // F4 - A5
                case 5: // Pinky
                    return keyIndex >= 7 && keyIndex <= 16; // A4 - C6
                default:
                    return false;
            }
        }

        return false;
    }
}
