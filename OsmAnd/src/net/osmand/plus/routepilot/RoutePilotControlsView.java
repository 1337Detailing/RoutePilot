package net.osmand.plus.routepilot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import net.osmand.Location;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.monitoring.OsmandMonitoringPlugin;

public class RoutePilotControlsView extends LinearLayout {

    private static final int ACCENT = Color.rgb(10, 132, 255);
    private static final int DANGER = Color.rgb(255, 69, 58);
    private static final int GLASS = Color.argb(230, 24, 24, 27);
    private static final int GLASS_BUTTON = Color.argb(245, 44, 44, 48);
    private static final int SECONDARY_TEXT = Color.argb(185, 255, 255, 255);

    private final OsmandApplication app;
    @Nullable
    private final MapActivity mapActivity;

    private final TextView eyebrowView;
    private final TextView statusView;
    private final TextView mainButton;
    private final TextView reverseButton;
    private final TextView twoSidesButton;

    private boolean recording;
    private int reverseCount;
    private int twoSidesCount;

    public RoutePilotControlsView(Context context) {
        this(context, null);
    }

    public RoutePilotControlsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        app = (OsmandApplication) context.getApplicationContext();
        mapActivity = context instanceof MapActivity ? (MapActivity) context : null;

        setOrientation(VERTICAL);
        setPadding(dp(18), dp(16), dp(18), dp(18));
        setBackground(roundRect(GLASS, 30));
        setElevation(dp(18));

        eyebrowView = label("TOURNÉE", 11, Typeface.BOLD, ACCENT);
        addView(eyebrowView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = label("Nouvelle tournée", 23, Typeface.BOLD, Color.WHITE);
        LayoutParams titleParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(4);
        addView(title, titleParams);

        statusView = label("GPS prêt • aucun enregistrement", 13, Typeface.NORMAL, SECONDARY_TEXT);
        LayoutParams statusParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(4);
        statusParams.bottomMargin = dp(14);
        addView(statusView, statusParams);

        mainButton = actionButton("Commencer l’enregistrement", ACCENT);
        mainButton.setOnClickListener(v -> toggleRecording());
        addView(mainButton, new LayoutParams(LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout eventRow = new LinearLayout(context);
        eventRow.setOrientation(HORIZONTAL);
        eventRow.setGravity(Gravity.CENTER);
        LayoutParams eventRowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        eventRowParams.topMargin = dp(10);
        addView(eventRow, eventRowParams);

        reverseButton = actionButton("↶  Marche arrière", GLASS_BUTTON);
        reverseButton.setOnClickListener(v -> flagEvent("REVERSE", "Marche arrière", Color.rgb(255, 159, 10)));
        LayoutParams half = new LayoutParams(0, dp(54), 1f);
        half.rightMargin = dp(5);
        eventRow.addView(reverseButton, half);

        twoSidesButton = actionButton("⇆  2 côtés", GLASS_BUTTON);
        twoSidesButton.setOnClickListener(v -> flagEvent("TWO_SIDES", "2 côtés", Color.rgb(48, 209, 88)));
        LayoutParams half2 = new LayoutParams(0, dp(54), 1f);
        half2.leftMargin = dp(5);
        eventRow.addView(twoSidesButton, half2);

        updateEventButtons(false);
    }

    private void toggleRecording() {
        if (recording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (mapActivity == null) {
            toast("Impossible de démarrer la tournée");
            return;
        }

        OsmandMonitoringPlugin plugin = PluginsHelper.getPlugin(OsmandMonitoringPlugin.class);
        if (plugin == null) {
            toast("Moteur GPS indisponible");
            return;
        }

        if (!plugin.isEnabled()) {
            PluginsHelper.enablePluginIfNeeded(mapActivity, app, plugin, true);
        }

        plugin.startRecording(mapActivity);
        recording = true;
        reverseCount = 0;
        twoSidesCount = 0;

        eyebrowView.setText("ENREGISTREMENT EN COURS");
        eyebrowView.setTextColor(Color.rgb(48, 209, 88));
        statusView.setText("Trace GPS active • 0 repère");
        mainButton.setText("Terminer et sauvegarder");
        mainButton.setBackground(roundRect(DANGER, 18));
        updateEventButtons(true);
        toast("Tournée démarrée");
    }

    private void stopRecording() {
        OsmandMonitoringPlugin plugin = PluginsHelper.getPlugin(OsmandMonitoringPlugin.class);
        if (plugin == null) {
            toast("Moteur GPS indisponible");
            return;
        }

        mainButton.setEnabled(false);
        statusView.setText("Sauvegarde de la tournée…");

        plugin.saveCurrentTrack(() -> post(() -> {
            recording = false;
            mainButton.setEnabled(true);
            mainButton.setText("Commencer une nouvelle tournée");
            mainButton.setBackground(roundRect(ACCENT, 18));
            eyebrowView.setText("TOURNÉE SAUVEGARDÉE");
            eyebrowView.setTextColor(ACCENT);
            statusView.setText("Trace GPX enregistrée • " + totalFlags() + " repère" + (totalFlags() > 1 ? "s" : ""));
            updateEventButtons(false);
            toast("Tournée sauvegardée");
        }), mapActivity);
    }

    private void flagEvent(String type, String displayName, int color) {
        if (!recording) {
            toast("Commence d’abord une tournée");
            return;
        }

        Location location = app.getLocationProvider().getLastKnownLocation();
        if (location == null) {
            toast("Position GPS indisponible");
            return;
        }

        String description = "routepilot_event=" + type;
        app.getSavingTrackHelper().insertPointData(
                location.getLatitude(),
                location.getLongitude(),
                description,
                displayName,
                "RoutePilot",
                color
        );

        if ("REVERSE".equals(type)) {
            reverseCount++;
        } else if ("TWO_SIDES".equals(type)) {
            twoSidesCount++;
        }

        statusView.setText(displayName + " ajouté • " + totalFlags() + " repère" + (totalFlags() > 1 ? "s" : ""));
        toast(displayName + " ajouté à la tournée");
    }

    private int totalFlags() {
        return reverseCount + twoSidesCount;
    }

    private void updateEventButtons(boolean enabled) {
        reverseButton.setEnabled(enabled);
        twoSidesButton.setEnabled(enabled);
        reverseButton.setAlpha(enabled ? 1f : 0.38f);
        twoSidesButton.setAlpha(enabled ? 1f : 0.38f);
    }

    private TextView actionButton(String text, int color) {
        TextView view = label(text, 15, Typeface.BOLD, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundRect(color, 18));
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(12), 0, dp(12), 0);
        return view;
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

    private void toast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
}
