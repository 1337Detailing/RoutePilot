package net.osmand.plus.routepilot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import net.osmand.Location;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.monitoring.OsmandMonitoringPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * First RoutePilot-specific HUD.
 *
 * OsmAnd remains responsible for GPS acquisition and GPX track recording.
 * This view provides the simplified RoutePilot workflow on top of the map.
 */
public class RoutePilotControlsView extends LinearLayout {

    private static final int ACCENT = Color.rgb(24, 122, 255);
    private static final int DANGER = Color.rgb(255, 69, 58);
    private static final int GLASS = Color.argb(215, 28, 28, 30);
    private static final int GLASS_BUTTON = Color.argb(235, 58, 58, 60);

    private final OsmandApplication app;
    @Nullable
    private final MapActivity mapActivity;

    private final TextView statusView;
    private final TextView mainButton;
    private final TextView reverseButton;
    private final TextView twoSidesButton;

    private boolean recording;
    private long sessionStartedAt;
    @Nullable
    private File eventsFile;

    public RoutePilotControlsView(Context context) {
        this(context, null);
    }

    public RoutePilotControlsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        app = (OsmandApplication) context.getApplicationContext();
        mapActivity = context instanceof MapActivity ? (MapActivity) context : null;

        setOrientation(VERTICAL);
        setPadding(dp(14), dp(12), dp(14), dp(14));
        setBackground(roundRect(GLASS, 28));
        setElevation(dp(12));

        TextView title = label("RoutePilot", 20, Typeface.BOLD, Color.WHITE);
        addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        statusView = label("Prêt à enregistrer", 13, Typeface.NORMAL, Color.argb(190, 255, 255, 255));
        LayoutParams statusParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(2);
        statusParams.bottomMargin = dp(12);
        addView(statusView, statusParams);

        mainButton = actionButton("Commencer l’enregistrement", ACCENT);
        mainButton.setOnClickListener(v -> toggleRecording());
        addView(mainButton, buttonParams());

        LinearLayout eventRow = new LinearLayout(context);
        eventRow.setOrientation(HORIZONTAL);
        eventRow.setGravity(Gravity.CENTER);
        LayoutParams eventRowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        eventRowParams.topMargin = dp(10);
        addView(eventRow, eventRowParams);

        reverseButton = actionButton("Marche arrière", GLASS_BUTTON);
        reverseButton.setOnClickListener(v -> flagEvent("REVERSE", "Marche arrière"));
        LayoutParams half = new LayoutParams(0, dp(52), 1f);
        half.rightMargin = dp(5);
        eventRow.addView(reverseButton, half);

        twoSidesButton = actionButton("2 côtés", GLASS_BUTTON);
        twoSidesButton.setOnClickListener(v -> flagEvent("TWO_SIDES", "2 côtés"));
        LayoutParams half2 = new LayoutParams(0, dp(52), 1f);
        half2.leftMargin = dp(5);
        eventRow.addView(twoSidesButton, half2);

        updateEventButtons(false);
    }

    private void toggleRecording() {
        if (!recording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        if (mapActivity == null) {
            toast("Impossible de démarrer l’enregistrement");
            return;
        }

        OsmandMonitoringPlugin plugin = PluginsHelper.getPlugin(OsmandMonitoringPlugin.class);
        if (plugin == null) {
            toast("Module d’enregistrement GPS indisponible");
            return;
        }

        if (!plugin.isEnabled()) {
            PluginsHelper.enablePluginIfNeeded(mapActivity, app, plugin, true);
        }

        plugin.startRecording(mapActivity);
        sessionStartedAt = System.currentTimeMillis();
        eventsFile = prepareEventsFile(sessionStartedAt);
        recording = true;

        mainButton.setText("Terminer l’enregistrement");
        mainButton.setBackground(roundRect(DANGER, 18));
        statusView.setText("Enregistrement GPS en cours");
        updateEventButtons(true);
        toast("Tournée démarrée");
    }

    private void stopRecording() {
        OsmandMonitoringPlugin plugin = PluginsHelper.getPlugin(OsmandMonitoringPlugin.class);
        if (plugin == null) {
            toast("Module d’enregistrement GPS indisponible");
            return;
        }

        plugin.saveCurrentTrack(() -> {
            post(() -> {
                recording = false;
                mainButton.setText("Commencer l’enregistrement");
                mainButton.setBackground(roundRect(ACCENT, 18));
                statusView.setText("Tournée enregistrée");
                updateEventButtons(false);
                toast("Tournée sauvegardée");
            });
        }, mapActivity);
    }

    private void flagEvent(String type, String displayName) {
        if (!recording) {
            toast("Commence d’abord une tournée");
            return;
        }

        Location location = app.getLocationProvider().getLastKnownLocation();
        if (location == null) {
            toast("Position GPS indisponible");
            return;
        }

        if (eventsFile == null) {
            eventsFile = prepareEventsFile(sessionStartedAt > 0 ? sessionStartedAt : System.currentTimeMillis());
        }

        if (eventsFile != null) {
            appendEvent(eventsFile, type, location);
        }

        statusView.setText(displayName + " ajouté à la tournée");
        toast(displayName + " enregistré");
    }

    @Nullable
    private File prepareEventsFile(long startedAt) {
        File dir = new File(app.getFilesDir(), "routepilot");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(startedAt));
        File file = new File(dir, "routepilot-events-" + stamp + ".csv");
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write("session_started_at,event_time,type,latitude,longitude,accuracy\n");
            } catch (IOException e) {
                return null;
            }
        }
        return file;
    }

    private void appendEvent(File file, String type, Location location) {
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(sessionStartedAt + ","
                    + System.currentTimeMillis() + ","
                    + type + ","
                    + location.getLatitude() + ","
                    + location.getLongitude() + ","
                    + location.getAccuracy() + "\n");
        } catch (IOException e) {
            toast("Impossible d’enregistrer le repère");
        }
    }

    private void updateEventButtons(boolean enabled) {
        reverseButton.setEnabled(enabled);
        twoSidesButton.setEnabled(enabled);
        reverseButton.setAlpha(enabled ? 1f : 0.45f);
        twoSidesButton.setAlpha(enabled ? 1f : 0.45f);
    }

    private TextView actionButton(String text, int color) {
        TextView view = label(text, 15, Typeface.BOLD, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundRect(color, 18));
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(14), 0, dp(14), 0);
        return view;
    }

    private LayoutParams buttonParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, dp(54));
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
