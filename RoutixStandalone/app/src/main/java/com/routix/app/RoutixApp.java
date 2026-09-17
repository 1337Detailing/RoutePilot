package com.routix.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public final class RoutixApp extends Application implements Application.ActivityLifecycleCallbacks {
    private Activity activeActivity;
    private SpeedometerView speedometer;
    private TextView classicGpsButton;
    private LocationManager locationManager;
    private LocationListener speedListener;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!(activity instanceof RoutixActivity)) return;
        activeActivity = activity;
        attachSpeedometer(activity);
        attachClassicGpsButton(activity);
        startSpeedUpdates();
    }

    @Override public void onActivityPaused(Activity activity) {
        if (activity != activeActivity) return;
        stopSpeedUpdates();
        activeActivity = null;
    }

    private void attachSpeedometer(Activity activity) {
        if (speedometer != null && speedometer.getParent() != null) return;
        speedometer = new SpeedometerView(activity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(activity, 116), dp(activity, 116), Gravity.END | Gravity.BOTTOM);
        lp.setMargins(dp(activity, 12), dp(activity, 12), dp(activity, 14), dp(activity, 176));
        activity.addContentView(speedometer, lp);
        speedometer.setElevation(dp(activity, 18));
    }

    private void attachClassicGpsButton(Activity activity) {
        if (classicGpsButton != null && classicGpsButton.getParent() != null) return;
        classicGpsButton = new TextView(activity);
        classicGpsButton.setText("GPS");
        classicGpsButton.setTextColor(Color.WHITE);
        classicGpsButton.setTextSize(13);
        classicGpsButton.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        classicGpsButton.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(88, 190, 255), Color.rgb(10,132,255), Color.rgb(35,86,210)});
        bg.setCornerRadius(dp(activity, 22));
        bg.setStroke(dp(activity,1), Color.argb(105,255,255,255));
        classicGpsButton.setBackground(bg);
        classicGpsButton.setElevation(dp(activity,18));
        classicGpsButton.setOnClickListener(v -> activity.startActivity(new Intent(activity, ClassicNavigationActivity.class)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(activity, 64), dp(activity, 48), Gravity.START | Gravity.BOTTOM);
        lp.setMargins(dp(activity,14),0,0,dp(activity,190));
        activity.addContentView(classicGpsButton, lp);
    }

    private void startSpeedUpdates() {
        if (activeActivity == null || locationManager == null) return;
        if (ContextCompat.checkSelfPermission(activeActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activeActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        speedListener = location -> {
            if (speedometer == null || location == null) return;
            float kmh = location.hasSpeed() ? Math.max(0f, location.getSpeed() * 3.6f) : 0f;
            float accuracy = location.hasAccuracy() ? location.getAccuracy() : 99f;
            speedometer.setSpeed(kmh, accuracy);
        };
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, speedListener);
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) speedListener.onLocationChanged(last);
        } catch (SecurityException ignored) {}
    }

    private void stopSpeedUpdates() {
        if (locationManager != null && speedListener != null) {
            try { locationManager.removeUpdates(speedListener); } catch (SecurityException ignored) {}
        }
        speedListener = null;
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityStarted(Activity a) {}
    @Override public void onActivityStopped(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {
        if (a == activeActivity) {
            stopSpeedUpdates();
            activeActivity = null;
            speedometer = null;
            classicGpsButton = null;
        }
    }

    private static final class SpeedometerView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private float displayedSpeed;
        private float accuracyM = 99f;
        private ValueAnimator animator;

        SpeedometerView(Activity activity) {
            super(activity);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setSpeed(float kmh, float accuracy) {
            float target = Math.min(199f, Math.max(0f, kmh));
            accuracyM = accuracy;
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(displayedSpeed, target);
            animator.setDuration(240);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                displayedSpeed = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        private int d(int v) {
            return Math.round(v * getResources().getDisplayMetrics().density);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;

            p.setStyle(Paint.Style.FILL);
            p.setShadowLayer(d(15), 0, d(6), Color.argb(150, 0, 0, 0));
            p.setColor(Color.argb(238, 13, 16, 23));
            c.drawRoundRect(d(2), d(2), w - d(2), h - d(2), d(31), d(31), p);
            p.clearShadowLayer();

            LinearGradient glass = new LinearGradient(0, 0, w, h,
                    new int[]{Color.argb(94, 255, 255, 255), Color.argb(20, 255, 255, 255), Color.argb(58, 10, 132, 255)},
                    null, Shader.TileMode.CLAMP);
            p.setShader(glass);
            c.drawRoundRect(d(3), d(3), w - d(3), h - d(3), d(30), d(30), p);
            p.setShader(null);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d(1));
            p.setColor(Color.argb(74, 255, 255, 255));
            c.drawRoundRect(d(3), d(3), w - d(3), h - d(3), d(30), d(30), p);

            arc.set(d(15), d(15), w - d(15), h - d(15));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(d(5));
            p.setColor(Color.argb(46, 255, 255, 255));
            c.drawArc(arc, 145f, 250f, false, p);

            int accent = displayedSpeed >= 80f ? Color.rgb(255, 69, 58)
                    : displayedSpeed >= 50f ? Color.rgb(255, 159, 10)
                    : Color.rgb(100, 210, 255);
            p.setColor(accent);
            p.setShadowLayer(d(7), 0, 0, Color.argb(135, Color.red(accent), Color.green(accent), Color.blue(accent)));
            c.drawArc(arc, 145f, 250f * Math.min(1f, displayedSpeed / 90f), false, p);
            p.clearShadowLayer();

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setColor(Color.WHITE);
            text.setTextSize(d(displayedSpeed >= 100f ? 29 : 34));
            Paint.FontMetrics fm = text.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f - d(5);
            c.drawText(String.valueOf(Math.round(displayedSpeed)), cx, baseline, text);

            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setTextSize(d(9));
            text.setColor(Color.argb(190, 255, 255, 255));
            c.drawText("km/h", cx, cy + d(24), text);

            boolean goodGps = accuracyM <= 20f;
            float chipY = h - d(15);
            p.setStyle(Paint.Style.FILL);
            p.setColor(goodGps ? Color.argb(46, 48, 209, 88) : Color.argb(42, 255, 159, 10));
            c.drawRoundRect(cx - d(22), chipY - d(8), cx + d(22), chipY + d(6), d(8), d(8), p);
            text.setTextSize(d(7));
            text.setColor(goodGps ? Color.rgb(93, 230, 126) : Color.rgb(255, 184, 71));
            c.drawText(goodGps ? "GPS • OK" : "GPS • …", cx, chipY + d(2), text);
        }
    }
}
