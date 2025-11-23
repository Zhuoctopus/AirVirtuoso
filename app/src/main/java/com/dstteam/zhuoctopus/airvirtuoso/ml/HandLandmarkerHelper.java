package com.dstteam.zhuoctopus.airvirtuoso.ml;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

public class HandLandmarkerHelper {
    private static final String TAG = "HandLandmarkerHelper";
    private static final String MP_HAND_LANDMARKER_TASK = "hand_landmarker.task";

    private final Context context;
    private HandLandmarker handLandmarker;
    private final LandmarkerListener listener;

    public HandLandmarkerHelper(Context context, LandmarkerListener listener) {
        this.context = context;
        this.listener = listener;
        setupHandLandmarker();
    }

    private void setupHandLandmarker() {
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(MP_HAND_LANDMARKER_TASK)
                .setDelegate(Delegate.GPU);

        HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setMinHandDetectionConfidence(0.7f)
                .setMinHandPresenceConfidence(0.7f)
                .setMinTrackingConfidence(0.7f)
                .setNumHands(2)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
                .build();

        try {
            handLandmarker = HandLandmarker.createFromOptions(context, options);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Hand Landmarker failed to initialize. See error logs for details", e);
            listener.onError("Hand Landmarker failed to initialize.");
        }
    }

    public void detectLiveStream(ImageProxy imageProxy, boolean isFrontCamera) {
        if (handLandmarker == null)
            return;

        long frameTime = SystemClock.uptimeMillis();

        // Note: Creating a new Bitmap every frame is expensive.
        // Ideally, we should reuse a bitmap or use the ImageProxy directly if possible.
        // But for rotation/mirroring, we need a transformation.

        Bitmap bitmap = imageProxy.toBitmap();
        Matrix matrix = new Matrix();
        matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, imageProxy.getWidth() / 2f, imageProxy.getHeight() / 2f);
        }

        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        MPImage mpImage = new BitmapImageBuilder(rotatedBitmap).build();

        detectAsync(mpImage, frameTime);
    }

    @VisibleForTesting
    public void detectAsync(MPImage image, long frameTime) {
        if (handLandmarker != null) {
            handLandmarker.detectAsync(image, frameTime);
        }
    }

    private void returnLivestreamResult(HandLandmarkerResult result, MPImage input) {
        listener.onResults(result, input.getWidth(), input.getHeight());
    }

    private void returnLivestreamError(RuntimeException error) {
        listener.onError(error.getMessage() != null ? error.getMessage() : "An unknown error has occurred");
    }

    public void clear() {
        if (handLandmarker != null) {
            handLandmarker.close();
            handLandmarker = null;
        }
    }

    public interface LandmarkerListener {
        void onError(String error);

        void onResults(HandLandmarkerResult result, int inputImageWidth, int inputImageHeight);
    }
}
