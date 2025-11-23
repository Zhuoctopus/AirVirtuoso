package com.dstteam.zhuoctopus.airvirtuoso.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class WavyProgressIndicator extends View {

    private final Paint wavePaint = new Paint();
    private final Paint backgroundPaint = new Paint();
    private final Path wavePath = new Path();
    private float progress = 0f; // 0.0 to 1.0
    private int waveColor = Color.parseColor("#6750A4"); // Default Purple
    private int trackColor = Color.parseColor("#EADDFF"); // Default Light Purple

    public WavyProgressIndicator(Context context) {
        super(context);
        init();
    }

    public WavyProgressIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WavyProgressIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        wavePaint.setColor(waveColor);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(8f);
        wavePaint.setAntiAlias(true);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);

        backgroundPaint.setColor(trackColor);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(8f);
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float midH = height / 2;
        float amplitude = height / 4;
        float frequency = 2 * (float) Math.PI / (width / 4); // 4 waves across width

        // Draw Background Track (Full Wave)
        drawWave(canvas, width, midH, amplitude, frequency, backgroundPaint, 1.0f);

        // Draw Progress Wave (Partial Wave)
        drawWave(canvas, width, midH, amplitude, frequency, wavePaint, progress);
    }

    private void drawWave(Canvas canvas, float width, float midH, float amplitude, float frequency, Paint paint,
            float progressRatio) {
        wavePath.reset();
        float endX = width * progressRatio;

        wavePath.moveTo(0, midH);
        for (float x = 0; x <= endX; x += 5) {
            float y = midH + amplitude * (float) Math.sin(x * frequency);
            wavePath.lineTo(x, y);
        }
        canvas.drawPath(wavePath, paint);
    }
}
