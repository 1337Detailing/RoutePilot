package net.osmand.plus.routepilot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import net.osmand.plus.activities.MapActivity;

public class RoutePilotTopBarView extends LinearLayout {

    private static final int GLASS = Color.argb(225, 24, 24, 27);
    private static final int ACCENT = Color.rgb(10, 132, 255);

    @Nullable
    private final MapActivity mapActivity;

    public RoutePilotTopBarView(Context context) {
        this(context, null);
    }

    public RoutePilotTopBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mapActivity = context instanceof MapActivity ? (MapActivity) context : null;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(16), dp(10), dp(10), dp(10));
        setBackground(roundRect(GLASS, 24));
        setElevation(dp(14));

        LinearLayout textBlock = new LinearLayout(context);
        textBlock.setOrientation(VERTICAL);

        TextView title = label("RoutePilot", 20, Typeface.BOLD, Color.WHITE);
        TextView subtitle = label("Navigation de tournée", 12, Typeface.NORMAL, Color.argb(175, 255, 255, 255));
        textBlock.addView(title);
        textBlock.addView(subtitle);

        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        addView(textBlock, textParams);

        TextView locationButton = label("◎  Ma position", 13, Typeface.BOLD, Color.WHITE);
        locationButton.setGravity(Gravity.CENTER);
        locationButton.setPadding(dp(14), 0, dp(14), 0);
        locationButton.setBackground(roundRect(Color.argb(245, 44, 44, 48), 16));
        locationButton.setOnClickListener(v -> {
            if (mapActivity != null) {
                mapActivity.getMapViewTrackingUtilities().backToLocationImpl();
            }
        });
        LayoutParams buttonParams = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(44));
        addView(locationButton, buttonParams);
    }

    private TextView label(String text, int sizeSp, int style, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
