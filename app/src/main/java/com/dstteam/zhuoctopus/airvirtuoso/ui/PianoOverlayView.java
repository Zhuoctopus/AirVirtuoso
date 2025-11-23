package com.dstteam.zhuoctopus.airvirtuoso.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.dstteam.zhuoctopus.airvirtuoso.analysis.PostureAnalyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PianoOverlayView extends View {

    private final List<Key> keys = new ArrayList<>();
    private final Paint correctLandmarkPaint = new Paint(); // Blue for correct
    private final Paint incorrectLandmarkPaint = new Paint(); // Red for incorrect
    private final Paint idleLandmarkPaint = new Paint(); // White for idle (not touching)
    private final Paint glovePaint = new Paint();
    private final Paint palmPaint = new Paint();
    private final Paint layerPaint = new Paint();
    private final Path palmPath = new Path();

    // Pastel Colors - Updated to match new theme
    private static final int COLOR_MINT = Color.rgb(165, 214, 167); // Pastel Mint (Tertiary)
    private static final int COLOR_BEIGE = Color.rgb(244, 143, 177); // Pastel Pink (Secondary)

    // Hand connections (pairs of landmark indices)
    private static final int[][] HAND_CONNECTIONS = {
            { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 4 }, // Thumb
            { 0, 5 }, { 5, 6 }, { 6, 7 }, { 7, 8 }, // Index
            { 0, 9 }, { 9, 10 }, { 10, 11 }, { 11, 12 }, // Middle
            { 0, 13 }, { 13, 14 }, { 14, 15 }, { 15, 16 }, // Ring
            { 0, 17 }, { 17, 18 }, { 18, 19 }, { 19, 20 } // Pinky
    };

    private HandLandmarkerResult handLandmarkerResult;
    private int imageWidth;
    private int imageHeight;
    private com.dstteam.zhuoctopus.airvirtuoso.logic.HandPositionValidator validator;
    private boolean isFrontCamera = true; // Default front camera mirroring

    // Smoothing state
    // Key: "handIndex_landmarkIndex", Value: PointF(x, y) in view coordinates
    private final Map<String, PointF> smoothedLandmarks = new HashMap<>();

    private static final float SMOOTHING_ALPHA = 0.5f; // Reduced for stronger smoothing (less jitter)

    // Track per-finger tap state to prevent sliding retriggers
    private static class FingerState {
        int activeKeyIndex = -1;
        boolean isArmed = true; // True when finger is above keyboard and ready to trigger
    }

    private final Map<String, FingerState> fingerStates = new HashMap<>();

    // AI/CV Features
    private final Paint fingeringPaint = new Paint();
    private final Paint fingertipNumberPaint = new Paint(); // For numbers inside fingertip dots
    private PostureAnalyzer.PostureResult currentPosture = null;
    private boolean isFistDetected = false;
    private int recommendedFinger = -1; // Display number (1-5)
    private double targetNoteFrequency = -1.0; // Frequency of the note to show fingering for

    // Depth/Distance Logic
    private static final float MIN_HAND_SCALE = 0.08f; // Lowered threshold to allow playing from further away
    private static final float HAND_SPREAD_FACTOR = 1.10f; // Spread hands horizontally to fix "pinched" alignment
    private static final float HAND_SPREAD_FACTOR_Y = 1.10f; // Spread hands vertically to fix alignment

    public PianoOverlayView(Context context) {
        super(context);
        init();
    }

    public PianoOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PianoOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        correctLandmarkPaint.setColor(Color.parseColor("#89CFF0")); // Pastel Blue
        correctLandmarkPaint.setStrokeWidth(10);
        correctLandmarkPaint.setStyle(Paint.Style.FILL);

        incorrectLandmarkPaint.setColor(Color.parseColor("#EF9A9A")); // Pastel Red
        incorrectLandmarkPaint.setStrokeWidth(10);
        incorrectLandmarkPaint.setStyle(Paint.Style.FILL);

        idleLandmarkPaint.setColor(Color.WHITE);
        idleLandmarkPaint.setStrokeWidth(10);
        idleLandmarkPaint.setStyle(Paint.Style.FILL);

        glovePaint.setColor(Color.WHITE);
        glovePaint.setStrokeWidth(70); // Restored thickness per user request
        glovePaint.setStyle(Paint.Style.STROKE);
        glovePaint.setStrokeCap(Paint.Cap.ROUND);
        glovePaint.setStrokeJoin(Paint.Join.ROUND);
        glovePaint.setAntiAlias(true);

        palmPaint.setColor(Color.WHITE);
        palmPaint.setStyle(Paint.Style.FILL);
        palmPaint.setAntiAlias(true);

        // Layer paint controls global transparency
        layerPaint.setAlpha(120); // Reduced opacity for cleaner view

        // Fingering display paint
        fingeringPaint.setColor(Color.WHITE); // White for visibility
        fingeringPaint.setTextSize(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16, getResources().getDisplayMetrics()));
        fingeringPaint.setTextAlign(Paint.Align.CENTER);
        fingeringPaint.setFakeBoldText(true);
        fingeringPaint.setAntiAlias(true);

        // Fingertip number paint (inside dots)
        fingertipNumberPaint.setColor(Color.BLACK);
        fingertipNumberPaint.setTextSize(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics()));
        fingertipNumberPaint.setTextAlign(Paint.Align.CENTER);
        fingertipNumberPaint.setFakeBoldText(true);

        validator = new com.dstteam.zhuoctopus.airvirtuoso.logic.HandPositionValidator();
    }

    private OnKeyListener onKeyListener;

    public interface OnKeyListener {
        void onKeyPressed(double frequency);
    }

    public void setOnKeyListener(OnKeyListener listener) {
        this.onKeyListener = listener;
    }

    public void setKeys(List<Key> newKeys) {
        this.keys.clear();
        this.keys.addAll(newKeys);
        // No need to invalidate as we don't draw keys anymore, but we might want to for
        // debugging
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw Landmarks (Smoothed Fingertips Only with validation)
        if (handLandmarkerResult != null && imageWidth > 0 && imageHeight > 0) {
            int handIndex = 0;
            List<NormalizedLandmark> firstHandWrist = null;

            for (int i = 0; i < handLandmarkerResult.landmarks().size(); i++) {
                List<NormalizedLandmark> landmarks = handLandmarkerResult.landmarks().get(i);

                // Ghost Hand Filter: Check distance to previous hand
                if (firstHandWrist != null) {
                    NormalizedLandmark currentWrist = landmarks.get(0);
                    NormalizedLandmark prevWrist = firstHandWrist.get(0);
                    double dist = Math.hypot(currentWrist.x() - prevWrist.x(), currentWrist.y() - prevWrist.y());
                    if (dist < 0.1) { // If hands are closer than 10% of screen, skip duplicate
                        continue;
                    }
                }
                if (firstHandWrist == null) {
                    firstHandWrist = landmarks;
                }

                // Determine Handedness Color
                int gloveColor = Color.WHITE; // Default
                if (i < handLandmarkerResult.handedness().size()) {
                    String label = handLandmarkerResult.handedness().get(i).get(0).categoryName();
                    // Note: MediaPipe Front Camera: "Left" label = User's Right Hand (Beige)
                    // "Right" label = User's Left Hand (Mint)
                    if (label.equals("Left")) {
                        gloveColor = Color.parseColor("#A8E6CF"); // Pastel Green
                    } else {
                        gloveColor = Color.parseColor("#A0C4FF"); // Pastel Blue
                    }
                }

                glovePaint.setColor(gloveColor);
                palmPaint.setColor(gloveColor);

                // Depth Check for Visualization
                float handScale = getHandScale(landmarks);
                boolean isTooFar = handScale < MIN_HAND_SCALE;

                int originalAlpha = 255;
                if (isTooFar) {
                    glovePaint.setAlpha(50); // Ghost mode
                    palmPaint.setAlpha(50);
                } else {
                    glovePaint.setAlpha(255);
                    palmPaint.setAlpha(255);
                }

                // Save layer for seamless transparency
                int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), layerPaint);

                // 1. Draw Palm Fill
                palmPath.reset();
                int[] palmIndices = { 0, 1, 5, 9, 13, 17 };
                boolean first = true;
                for (int index : palmIndices) {
                    String key = handIndex + "_" + index;
                    PointF point = smoothedLandmarks.get(key);
                    if (point != null) {
                        if (first) {
                            palmPath.moveTo(point.x, point.y);
                            first = false;
                        } else {
                            palmPath.lineTo(point.x, point.y);
                        }
                    }
                }
                palmPath.close();
                canvas.drawPath(palmPath, palmPaint);

                // 2. Draw Glove Fingers (Thick Lines)
                for (int[] connection : HAND_CONNECTIONS) {
                    String startKey = handIndex + "_" + connection[0];
                    String endKey = handIndex + "_" + connection[1];
                    PointF start = smoothedLandmarks.get(startKey);
                    PointF end = smoothedLandmarks.get(endKey);

                    if (start != null && end != null) {
                        canvas.drawLine(start.x, start.y, end.x, end.y, glovePaint);
                    }
                }

                // Restore layer (applies transparency to the whole glove)
                canvas.restoreToCount(saveCount);

                // 3. Draw Validation Dots (Fingertips only - ON TOP)
                int[] fingertips = { 4, 8, 12, 16, 20 };
                for (int fingerIndex : fingertips) {
                    String key = handIndex + "_" + fingerIndex;
                    PointF smoothedPoint = smoothedLandmarks.get(key);
                    if (smoothedPoint != null) {
                        // Get wrist position for validation
                        NormalizedLandmark wrist = landmarks.get(0); // Wrist is index 0

                        // Find which key this fingertip is on
                        int keyIndexPressed = getKeyIndexAtPosition(smoothedPoint.x, smoothedPoint.y);

                        // Validate finger position
                        Paint dotPaint = idleLandmarkPaint; // Default White (Idle)
                        if (keyIndexPressed >= 0) {
                            com.dstteam.zhuoctopus.airvirtuoso.logic.HandPositionValidator.ValidationResult result = validator
                                    .validateFingerPosition(
                                            handLandmarkerResult,
                                            handIndex,
                                            fingerIndex,
                                            keyIndexPressed,
                                            wrist.x());

                            if (result.isCorrect) {
                                dotPaint = correctLandmarkPaint; // Blue for correct
                            } else {
                                dotPaint = incorrectLandmarkPaint; // Red for incorrect
                            }
                        }

                        // Draw larger circle for fingertip
                        float dotRadius = 25; // Increased from 10
                        canvas.drawCircle(smoothedPoint.x, smoothedPoint.y, dotRadius, dotPaint);

                        // Draw finger number inside the dot
                        // Map fingerIndex to finger number: 4->1(thumb), 8->2(index), 12->3(middle),
                        // 16->4(ring), 20->5(pinky)
                        int fingerNumber = (fingerIndex / 4); // 4->1, 8->2, 12->3, 16->4, 20->5
                        String fingerText = String.valueOf(fingerNumber);
                        // Center text vertically (add textSize/3 to y position)
                        float textY = smoothedPoint.y + (fingertipNumberPaint.getTextSize() / 3);
                        canvas.drawText(fingerText, smoothedPoint.x, textY, fingertipNumberPaint);
                    }
                }
                handIndex++;
            }
        }

        // Draw Fingering Numbers (only above the specific next note's key)
        if (recommendedFinger >= 1 && recommendedFinger <= 5 && targetNoteFrequency > 0) {
            for (Key key : keys) {
                // Only show fingering above the key that matches the target frequency
                if (Math.abs(key.frequency - targetNoteFrequency) < 1.0) {
                    float centerX = (key.rect.left + key.rect.right) / 2;
                    float fingerY = key.rect.top - 20; // Above the key
                    String fingerNumber = String.valueOf(recommendedFinger);
                    canvas.drawText(fingerNumber, centerX, fingerY, fingeringPaint);
                    break; // Only one key should match
                }
            }
        }

    }

    public void setHandLandmarkerResult(HandLandmarkerResult result, int imageWidth, int imageHeight) {
        this.handLandmarkerResult = result;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

        updateSmoothedLandmarks();
        checkCollisions();
        invalidate();
    }

    private void updateSmoothedLandmarks() {
        if (handLandmarkerResult == null)
            return;

        if (handLandmarkerResult.landmarks().isEmpty()) {
            smoothedLandmarks.clear();
            return;
        }

        int handIndex = 0;

        for (List<NormalizedLandmark> landmarks : handLandmarkerResult.landmarks()) {
            // Process ALL 21 landmarks for the glove
            for (int index = 0; index < 21; index++) {
                if (index >= landmarks.size())
                    continue;
                if (index >= landmarks.size())
                    continue;

                NormalizedLandmark landmark = landmarks.get(index);
                float[] coords = transformCoordinates(landmark.x(), landmark.y());
                float targetX = coords[0];
                float targetY = coords[1];

                String key = handIndex + "_" + index;
                PointF previous = smoothedLandmarks.get(key);

                float newX, newY;
                if (previous == null) {
                    newX = targetX;
                    newY = targetY;
                } else {
                    // EMA Filter: smoothed = alpha * target + (1 - alpha) * previous
                    newX = SMOOTHING_ALPHA * targetX + (1 - SMOOTHING_ALPHA) * previous.x;
                    newY = SMOOTHING_ALPHA * targetY + (1 - SMOOTHING_ALPHA) * previous.y;
                }

                smoothedLandmarks.put(key, new PointF(newX, newY));
            }
            handIndex++;
        }
    }

    // Helper to transform normalized coordinates to view coordinates, accounting
    // for center crop and aspect ratio
    private float[] transformCoordinates(float xNorm, float yNorm) {
        // Get view dimensions
        int viewW = getWidth();
        int viewH = getHeight();
        if (imageWidth == 0 || imageHeight == 0 || viewW == 0 || viewH == 0) {
            // Fallback simple scaling
            return new float[] { xNorm * viewW, yNorm * viewH };
        }
        // Compute scale factor for center-crop (fill view)
        float scale = Math.max((float) viewW / imageWidth, (float) viewH / imageHeight);
        // Compute offset due to cropping
        float offsetX = (viewW - imageWidth * scale) / 2f;
        float offsetY = (viewH - imageHeight * scale) / 2f;
        // Map normalized coordinates (0..1) to image pixel space then to view space
        float xImg = xNorm * imageWidth;
        float yImg = yNorm * imageHeight;
        float xView = xImg * scale + offsetX;
        float yView = yImg * scale + offsetY;

        // Apply Horizontal Spread to fix alignment
        // Shift X away from center
        float centerX = viewW / 2f;
        xView = (xView - centerX) * HAND_SPREAD_FACTOR + centerX;

        // Apply Vertical Spread to fix alignment
        // Shift Y away from center
        float centerY = viewH / 2f;
        yView = (yView - centerY) * HAND_SPREAD_FACTOR_Y + centerY;

        return new float[] { xView, yView };
    }

    private void checkCollisions() {
        if (handLandmarkerResult == null) {
            if (isFistDetected) {
                resetKeyPressStates();
            }
            return;
        }

        if (isFistDetected) {
            resetKeyPressStates();
            return;
        }

        List<Key> nextActiveKeys = new ArrayList<>();
        List<Key> newlyTriggeredKeys = new ArrayList<>();

        int handIndex = 0;
        for (List<NormalizedLandmark> landmarks : handLandmarkerResult.landmarks()) {
            // Check if this hand is close enough
            if (getHandScale(landmarks) < MIN_HAND_SCALE) {
                handIndex++;
                continue; // Ignore hands that are too far
            }

            int[] fingertips = { 4, 8, 12, 16, 20 };
            for (int index : fingertips) {
                String fingerId = handIndex + "_" + index;
                PointF point = smoothedLandmarks.get(fingerId);
                FingerState state = fingerStates.computeIfAbsent(fingerId, id -> new FingerState());

                int currentKeyIndex = -1;
                if (point != null) {
                    currentKeyIndex = getKeyIndexAtPosition(point.x, point.y);
                }

                if (currentKeyIndex == -1) {
                    // Finger lifted or is above the keyboard.
                    state.activeKeyIndex = -1;
                    state.isArmed = true;
                    continue;
                }

                Key currentKey = keys.get(currentKeyIndex);

                if (state.activeKeyIndex == currentKeyIndex) {
                    if (!nextActiveKeys.contains(currentKey)) {
                        nextActiveKeys.add(currentKey);
                    }
                } else if (state.activeKeyIndex == -1 && state.isArmed) {
                    state.activeKeyIndex = currentKeyIndex;
                    state.isArmed = false;

                    if (!nextActiveKeys.contains(currentKey)) {
                        nextActiveKeys.add(currentKey);
                    }
                    if (!newlyTriggeredKeys.contains(currentKey)) {
                        newlyTriggeredKeys.add(currentKey);
                    }
                }
            }
            handIndex++;
        }

        for (Key key : keys) {
            boolean isNowPressed = nextActiveKeys.contains(key);

            if (newlyTriggeredKeys.contains(key) && onKeyListener != null) {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                onKeyListener.onKeyPressed(key.frequency);
            }

            key.isPressed = isNowPressed;
        }
    }

    public void highlightKey(double frequency, boolean isCorrect) {
        // Just for visual feedback if needed, but now keys are external views.
        // We can still use this to trigger particles or overlay effects if we want.
    }

    /**
     * Get the key index at a given screen position
     */
    private int getKeyIndexAtPosition(float x, float y) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).rect.contains(x, y)) {
                return i;
            }
        }
        return -1; // Not on any key
    }

    public static class Key {
        public RectF rect;
        public boolean isPressed;
        public double frequency; // For audio mapping
        public String label;

        public Key(RectF rect, double frequency, String label) {
            this.rect = rect;
            this.frequency = frequency;
            this.label = label;
        }
    }

    /**
     * Set the recommended finger for the current note.
     */
    public void setRecommendedFinger(int fingerDisplayNumber, double frequency) {
        this.recommendedFinger = fingerDisplayNumber;
        this.targetNoteFrequency = frequency;
        invalidate();
    }

    public void clearRecommendedFinger() {
        this.recommendedFinger = -1;
        this.targetNoteFrequency = -1.0;
        invalidate();
    }

    /**
     * Analyze and update posture for all visible hands.
     */
    public void updatePosture() {
        if (handLandmarkerResult != null && !handLandmarkerResult.landmarks().isEmpty()) {
            // Analyze first hand for now
            List<NormalizedLandmark> landmarks = handLandmarkerResult.landmarks().get(0);

            // 1. Check Distance (Scale)
            float scale = getHandScale(landmarks);
            if (scale < MIN_HAND_SCALE) {
                isFistDetected = false;
                currentPosture = new PostureAnalyzer.PostureResult(
                        PostureAnalyzer.PostureStatus.WARNING,
                        0.0f,
                        "👋 Move closer to piano");
            }
            // 2. Check Fist
            else if (PostureAnalyzer.isFist(landmarks)) {
                isFistDetected = true;
                currentPosture = new PostureAnalyzer.PostureResult(
                        PostureAnalyzer.PostureStatus.DANGER,
                        0.0f,
                        "✋ Open your hand in a curved shape");
            } else {
                isFistDetected = false;
                currentPosture = PostureAnalyzer.analyzePosture(landmarks);
            }
        } else {
            isFistDetected = false;
            currentPosture = null;
        }
        invalidate();
    }

    private float getHandScale(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() <= 9)
            return 0;
        NormalizedLandmark wrist = landmarks.get(0);
        NormalizedLandmark middleMcp = landmarks.get(9);
        return (float) Math.hypot(wrist.x() - middleMcp.x(), wrist.y() - middleMcp.y());
    }

    /**
     * Get current posture result.
     */
    public PostureAnalyzer.PostureResult getCurrentPosture() {
        return currentPosture;
    }

    /**
     * Set whether the camera is front-facing. When true, X coordinates are
     * mirrored.
     */
    public void setFrontCamera(boolean isFront) {
        this.isFrontCamera = isFront;
    }

    private void resetKeyPressStates() {
        for (Key key : keys) {
            key.isPressed = false;
        }
    }
}
