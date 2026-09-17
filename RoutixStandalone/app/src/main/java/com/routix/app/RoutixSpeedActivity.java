package com.routix.app;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Routix shell with a dedicated, animated speedometer overlay.
 * Kept separate from RoutixActivity so the navigation engine stays untouched.
 */
public class RoutixSpeedActivity extends RoutixActivity {
    private SpeedometerView speedometer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        speedometer = new SpeedometerView();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(116), dp(116), Gravity.END | Gravity.BOTTOM);
        lp.setMargins(dp(12), dp(12), dp(14), dp(176));
        addContentView(speedometer, lp);
        speedometer.setElevation(dp(18));
    }

    @Override public void onLocationChanged(Location location) {
        super.onLocationChanged(location);
        if (speedometer == null || location == null) return;
        float kmh = location.hasSpeed() ? Math.max(0f, location.getSpeed() * 3.6f) : 0f;
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 99f;
        speedometer.setSpeed(kmh, accuracy);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class SpeedometerView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private float displayedSpeed = 0f;
        private float targetSpeed = 0f;
        private float accuracyM = 99f;
        private ValueAnimator animator;

        SpeedometerView() {
            super(RoutixSpeedActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setSpeed(float kmh, float accuracy) {
            targetSpeed = Math.min(199f, kmh);
            accuracyM = accuracy;
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(displayedSpeed, targetSpeed);
            animator.setDuration(260);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                displayedSpeed = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float pad = dp(7);

            // Shadow + liquid glass body.
            p.setStyle(Paint.Style.FILL);
            p.setShadowLayer(dp(15), 0, dp(6), Color.argb(150, 0, 0, 0));
            p.setColor(Color.argb(235, 14, 17, 24));
            c.drawRoundRect(dp(2), dp(2), w - dp(2), h - dp(2), dp(30), dp(30), p);
            p.clearShadowLayer();

            LinearGradient glass = new LinearGradient(0, 0, w, h,
                    new int[]{Color.argb(82, 255, 255, 255), Color.argb(18, 255, 255, 255), Color.argb(55, 10, 132, 255)},
                    null, Shader.TileMode.CLAMP);
            p.setShader(glass);
            c.drawRoundRect(dp(3), dp(3), w - dp(3), h - dp(3), dp(29), dp(29), p);
            p.setShader(null);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.argb(70, 255, 255, 255));
            c.drawRoundRect(dp(3), dp(3), w - dp(3), h - dp(3), dp(29), dp(29), p);

            // Speed arc.
            float ringPad = dp(15);
            arc.set(ringPad, ringPad, w - ringPad, h - ringPad);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(dp(5));
            p.setColor(Color.argb(44, 255, 255, 255));
            c.drawArc(arc, 145f, 250f, false, p);

            int accent = accentForSpeed(displayedSpeed);
            p.setColor(accent);
            p.setShadowLayer(dp(7), 0, 0, Color.argb(130, Color.red(accent), Color.green(accent), Color.blue(accent)));
            float progress = Math.min(1f, displayedSpeed / 90f);
            c.drawArc(arc, 145f, 250f * progress, false, p);
            p.clearShadowLayer();

            // Main numeric value.
            text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Color.WHITE);
            text.setTextSize(dp(displayedSpeed >= 100 ? 29 : 34));
            String number = String.valueOf(Math.round(displayedSpeed));
            Paint.FontMetrics fm = text.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f - dp(5);
            c.drawText(number, cx, baseline, text);

            text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            text.setTextSize(dp(9));
            text.setColor(Color.argb(185, 255, 255, 255));
            c.drawText("km/h", cx, cy + dp(24), text);

            // GPS status chip.
            boolean goodGps = accuracyM <= 20f;
            float chipY = h - dp(15);
            p.setStyle(Paint.Style.FILL);
            p.setColor(goodGps ? Color.argb(42, 48, 209, 88) : Color.argb(38, 255, 159, 10));
            c.drawRoundRect(cx - dp(21), chipY - dp(8), cx + dp(21), chipY + dp(6), dp(8), dp(8), p);
            text.setTextSize(dp(7));
            text.setColor(goodGps ? Color.rgb(93, 230, 126) : Color.rgb(255, 184, 71));
            c.drawText(goodGps ? "GPS • OK" : "GPS • …", cx, chipY + dp(2), text);
        }

        private int accentForSpeed(float speed) {
            if (speed >= 80f) return Color.rgb(255, 69, 58);
            if (speed >= 50f) return Color.rgb(255, 159, 10);
            return Color.rgb(100, 210, 255);
        }
    }
}
