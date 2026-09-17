package com.routepilot.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int LOCATION_PERMISSION = 42;
    private static final int BLUE = Color.rgb(10, 132, 255);
    private static final int GREEN = Color.rgb(48, 209, 88);
    private static final int ORANGE = Color.rgb(255, 159, 10);
    private static final int RED = Color.rgb(255, 69, 58);
    private static final int PURPLE = Color.rgb(191, 90, 242);
    private static final int BG = Color.rgb(14, 15, 18);
    private static final int GLASS = Color.argb(226, 31, 32, 37);
    private static final int GLASS_SOFT = Color.argb(205, 43, 44, 50);
    private static final int GLASS_BUTTON = Color.argb(235, 56, 57, 64);
    private static final int TEXT_SECONDARY = Color.argb(185, 255, 255, 255);

    private final List<RoutePoint> routePoints = new ArrayList<>();
    private final List<RouteEvent> routeEvents = new ArrayList<>();

    private FrameLayout root;
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private LocationManager locationManager;
    private Polyline liveTrack;
    private Polyline historyTrack;

    private View topBar;
    private View bottomPanel;
    private View bottomNav;
    private View historyScreen;
    private View settingsScreen;
    private View routePlanOverlay;

    private TextView status;
    private TextView recordButton;
    private TextView reverseButton;
    private TextView twoSidesButton;
    private TextView shareButton;
    private TextView collapseButton;
    private TextView navMap;
    private TextView navHistory;
    private TextView navSettings;

    private boolean recording;
    private boolean recordingPanelCollapsed;
    private long startedAt;
    private Location lastLocation;
    private File lastSavedRoute;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName() + "/0.2");
        Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));

        prefs = getSharedPreferences("routepilot", MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (prefs.getBoolean("keep_screen_on", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        buildMap();
        topBar = buildTopBar();
        bottomPanel = buildRecordingPanel();
        bottomNav = buildBottomNav();

        root.addView(topBar, topParams());
        root.addView(bottomPanel, recordingPanelParams());
        root.addView(bottomNav, navParams());
        setContentView(root);

        applyMapStyle(prefs.getString("map_style", "Clair"));
        ensureLocationPermission();
        updateNavState("map");
    }

    private void buildMap() {
        mapView = new MapView(this);
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);
        mapView.getController().setZoom(18.0);
        root.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.setDrawAccuracyEnabled(true);
        mapView.getOverlays().add(locationOverlay);

        liveTrack = new Polyline();
        liveTrack.getOutlinePaint().setStrokeWidth(dp(7));
        liveTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        mapView.getOverlays().add(liveTrack);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(17), dp(12), dp(10), dp(12));
        bar.setBackground(roundRect(GLASS, 29));
        bar.setElevation(dp(16));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("RoutePilot", 21, Typeface.BOLD, Color.WHITE);
        TextView subtitle = text("Tournées intelligentes", 12, Typeface.NORMAL, TEXT_SECONDARY);
        titles.addView(brand);
        titles.addView(subtitle);
        bar.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView style = circleButton("◐", GLASS_BUTTON);
        style.setOnClickListener(v -> showMapStyleQuickMenu());
        bar.addView(style, squareParams(44));

        TextView recenter = circleButton("◎", accent());
        recenter.setTextSize(21);
        recenter.setOnClickListener(v -> recenter());
        LinearLayout.LayoutParams rp = squareParams(44);
        rp.leftMargin = dp(8);
        bar.addView(recenter, rp);
        return bar;
    }

    private View buildRecordingPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(15));
        panel.setBackground(roundRect(GLASS, 31));
        panel.setElevation(dp(20));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView section = text("TOURNÉE", 11, Typeface.BOLD, accent());
        header.addView(section, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        collapseButton = text("⌄", 21, Typeface.BOLD, Color.WHITE);
        collapseButton.setGravity(Gravity.CENTER);
        collapseButton.setOnClickListener(v -> toggleRecordingPanel());
        header.addView(collapseButton, new LinearLayout.LayoutParams(dp(44), dp(38)));
        panel.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setId(View.generateViewId());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("recording_content");
        panel.addView(content);

        status = text("GPS en attente…", 13, Typeface.NORMAL, TEXT_SECONDARY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(2);
        statusParams.bottomMargin = dp(11);
        content.addView(status, statusParams);

        recordButton = pill("Commencer l’enregistrement", accent());
        recordButton.setTextSize(16);
        recordButton.setOnClickListener(v -> { press(v); toggleRecording(); });
        content.addView(recordButton, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout eventRow = new LinearLayout(this);
        eventRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(10);
        content.addView(eventRow, rowParams);

        reverseButton = pill("↶  Marche arrière", GLASS_BUTTON);
        reverseButton.setOnClickListener(v -> { press(v); addEvent("REVERSE", "Marche arrière", ORANGE); });
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, dp(54), 1f);
        half1.rightMargin = dp(5);
        eventRow.addView(reverseButton, half1);

        twoSidesButton = pill("⇆  2 côtés", GLASS_BUTTON);
        twoSidesButton.setOnClickListener(v -> { press(v); addEvent("TWO_SIDES", "2 côtés", GREEN); });
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(54), 1f);
        half2.leftMargin = dp(5);
        eventRow.addView(twoSidesButton, half2);

        shareButton = pill("Partager la dernière tournée", GLASS_BUTTON);
        shareButton.setOnClickListener(v -> shareRoute(lastSavedRoute));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(-1, dp(48));
        shareParams.topMargin = dp(10);
        content.addView(shareButton, shareParams);

        setRecordingControls(false);
        shareButton.setEnabled(false);
        shareButton.setAlpha(0.35f);
        return panel;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.setBackground(roundRect(Color.argb(240, 28, 29, 34), 27));
        nav.setElevation(dp(24));

        navMap = navItem("⌖\nCarte");
        navHistory = navItem("≡\nHistorique");
        navSettings = navItem("⚙\nRéglages");
        navMap.setOnClickListener(v -> showMapScreen());
        navHistory.setOnClickListener(v -> showHistoryScreen());
        navSettings.setOnClickListener(v -> showSettingsScreen());
        nav.addView(navMap, new LinearLayout.LayoutParams(0, dp(58), 1f));
        nav.addView(navHistory, new LinearLayout.LayoutParams(0, dp(58), 1f));
        nav.addView(navSettings, new LinearLayout.LayoutParams(0, dp(58), 1f));
        return nav;
    }

    private TextView navItem(String label) {
        TextView t = text(label, 11, Typeface.BOLD, TEXT_SECONDARY);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void showMapScreen() {
        removeScreenOverlays();
        topBar.setVisibility(View.VISIBLE);
        bottomPanel.setVisibility(View.VISIBLE);
        mapView.setVisibility(View.VISIBLE);
        updateNavState("map");
        fadeIn(bottomPanel);
    }

    private void showHistoryScreen() {
        removeScreenOverlays();
        mapView.setVisibility(View.VISIBLE);
        topBar.setVisibility(View.GONE);
        bottomPanel.setVisibility(View.GONE);
        historyScreen = buildHistoryScreen();
        root.addView(historyScreen, contentParams());
        updateNavState("history");
        fadeIn(historyScreen);
    }

    private View buildHistoryScreen() {
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(Color.argb(238, 14, 15, 18));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(54), dp(20), dp(96));

        TextView title = text("Historique", 30, Typeface.BOLD, Color.WHITE);
        TextView sub = text("Tes tournées enregistrées, plans et repères", 13, Typeface.NORMAL, TEXT_SECONDARY);
        page.addView(title);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(4); sp.bottomMargin = dp(18);
        page.addView(sub, sp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        List<File> files = routeFiles();
        if (files.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(80), dp(20), dp(20));
            TextView icon = text("⌁", 45, Typeface.NORMAL, Color.argb(120,255,255,255));
            TextView msg = text("Aucune tournée pour l’instant", 17, Typeface.BOLD, Color.WHITE);
            TextView hint = text("Enregistre ta première tournée depuis la carte.", 13, Typeface.NORMAL, TEXT_SECONDARY);
            empty.addView(icon); empty.addView(msg); empty.addView(hint);
            list.addView(empty);
        } else {
            for (File file : files) {
                RouteSummary summary = parseRoute(file);
                list.addView(historyCard(summary), cardMarginParams());
            }
        }
        layer.addView(page, new FrameLayout.LayoutParams(-1, -1));
        return layer;
    }

    private View historyCard(RouteSummary s) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(14));
        card.setBackground(roundRect(GLASS_SOFT, 24));
        card.setElevation(dp(5));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(displayName(s.file), 17, Typeface.BOLD, Color.WHITE);
        TextView date = text(formatRouteDate(s.file.lastModified()), 12, Typeface.NORMAL, TEXT_SECONDARY);
        texts.addView(name); texts.addView(date);
        top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView chevron = text("›", 29, Typeface.NORMAL, TEXT_SECONDARY);
        top.addView(chevron);
        card.addView(top);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, dp(13));
        stats.addView(statBlock("DURÉE", formatDuration(s.durationMs)), new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(statBlock("DISTANCE", formatDistance(s.distanceM)), new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(statBlock("REPÈRES", String.valueOf(s.events.size())), new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(stats);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView plan = smallPill("Voir le plan", accent());
        plan.setOnClickListener(v -> openRoutePlan(s));
        TextView rename = smallPill("Renommer", GLASS_BUTTON);
        rename.setOnClickListener(v -> renameRoute(s.file));
        TextView share = smallPill("Partager", GLASS_BUTTON);
        share.setOnClickListener(v -> shareRoute(s.file));
        actions.addView(plan, weightedAction(1.2f, 4));
        actions.addView(rename, weightedAction(1f, 4));
        actions.addView(share, weightedAction(1f, 0));
        card.addView(actions);
        card.setOnClickListener(v -> openRoutePlan(s));
        return card;
    }

    private View statBlock(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView l = text(label, 10, Typeface.BOLD, Color.argb(150,255,255,255));
        TextView v = text(value, 15, Typeface.BOLD, Color.WHITE);
        box.addView(l); box.addView(v);
        return box;
    }

    private void openRoutePlan(RouteSummary s) {
        if (s.points.isEmpty()) {
            Toast.makeText(this, "Cette tournée ne contient pas de trace", Toast.LENGTH_SHORT).show();
            return;
        }
        if (historyScreen != null) historyScreen.setVisibility(View.GONE);
        mapView.setVisibility(View.VISIBLE);
        mapView.getOverlays().remove(historyTrack);
        clearPlanMarkers();

        historyTrack = new Polyline();
        historyTrack.getOutlinePaint().setColor(accent());
        historyTrack.getOutlinePaint().setStrokeWidth(dp(8));
        historyTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        List<GeoPoint> pts = new ArrayList<>();
        for (RoutePoint p : s.points) pts.add(new GeoPoint(p.lat, p.lon));
        historyTrack.setPoints(pts);
        mapView.getOverlays().add(historyTrack);

        addNumberMarker(pts.get(0), "Départ", 0, GREEN);
        for (int i = 0; i < s.events.size(); i++) {
            RouteEvent e = s.events.get(i);
            addNumberMarker(new GeoPoint(e.lat, e.lon), e.label, i + 1,
                    "REVERSE".equals(e.type) ? ORANGE : GREEN);
        }
        addNumberMarker(pts.get(pts.size() - 1), "Arrivée", s.events.size() + 1, RED);

        mapView.zoomToBoundingBox(historyTrack.getBounds(), true, dp(70));
        mapView.invalidate();

        routePlanOverlay = buildRoutePlanOverlay(s);
        root.addView(routePlanOverlay, planOverlayParams());
        fadeIn(routePlanOverlay);
    }

    private View buildRoutePlanOverlay(RouteSummary s) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(14), dp(16), dp(15));
        sheet.setBackground(roundRect(Color.argb(242, 27, 28, 33), 29));
        sheet.setElevation(dp(24));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text(displayName(s.file), 17, Typeface.BOLD, Color.WHITE));
        titleBox.addView(text(formatDuration(s.durationMs) + "  •  " + formatDistance(s.distanceM) + "  •  " + s.events.size() + " repères", 12, Typeface.NORMAL, TEXT_SECONDARY));
        head.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = circleButton("×", GLASS_BUTTON);
        close.setOnClickListener(v -> closeRoutePlan());
        head.addView(close, squareParams(40));
        sheet.addView(head);

        if (!s.events.isEmpty()) {
            ScrollView scroll = new ScrollView(this);
            LinearLayout eventList = new LinearLayout(this);
            eventList.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < s.events.size(); i++) {
                RouteEvent e = s.events.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(9), 0, dp(3));
                TextView num = badge(String.valueOf(i + 1), "REVERSE".equals(e.type) ? ORANGE : GREEN);
                row.addView(num, squareParams(30));
                TextView lbl = text(e.label, 13, Typeface.BOLD, Color.WHITE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.leftMargin = dp(10);
                row.addView(lbl, lp);
                eventList.addView(row);
            }
            scroll.addView(eventList);
            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
            scrollLp.topMargin = dp(7);
            sheet.addView(scroll, scrollLp);
        }
        return sheet;
    }

    private void closeRoutePlan() {
        if (routePlanOverlay != null) {
            root.removeView(routePlanOverlay);
            routePlanOverlay = null;
        }
        mapView.getOverlays().remove(historyTrack);
        clearPlanMarkers();
        if (historyScreen != null) historyScreen.setVisibility(View.VISIBLE);
        mapView.invalidate();
    }

    private void showSettingsScreen() {
        removeScreenOverlays();
        topBar.setVisibility(View.GONE);
        bottomPanel.setVisibility(View.GONE);
        settingsScreen = buildSettingsScreen();
        root.addView(settingsScreen, contentParams());
        updateNavState("settings");
        fadeIn(settingsScreen);
    }

    private View buildSettingsScreen() {
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(Color.argb(247, 14, 15, 18));
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(54), dp(20), dp(104));
        scroll.addView(page);

        page.addView(text("Réglages", 30, Typeface.BOLD, Color.WHITE));
        TextView sub = text("Personnalise RoutePilot pour tes tournées", 13, Typeface.NORMAL, TEXT_SECONDARY);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2); subLp.bottomMargin = dp(22);
        page.addView(sub, subLp);

        page.addView(sectionLabel("APPARENCE"));
        page.addView(settingsChoice("Style de carte", prefs.getString("map_style", "Clair"), v -> showMapStyleDialog()), settingsMargin());
        page.addView(settingsChoice("Couleur d’accent", prefs.getString("accent_name", "Bleu"), v -> showAccentDialog()), settingsMargin());

        page.addView(sectionLabel("NAVIGATION & GPS"), sectionTopMargin());
        page.addView(toggleRow("Suivre ma position", "Recentre la carte pendant l’enregistrement", "follow_position", true), settingsMargin());
        page.addView(toggleRow("Écran toujours allumé", "Pratique pendant une tournée", "keep_screen_on", true), settingsMargin());
        page.addView(toggleRow("Retour haptique", "Petite vibration sur les actions métier", "haptics", true), settingsMargin());

        page.addView(sectionLabel("ENREGISTREMENT"), sectionTopMargin());
        page.addView(settingsChoice("Précision de trace", "1 m / 2 s", v -> Toast.makeText(this, "Mode précision maximale activé", Toast.LENGTH_SHORT).show()), settingsMargin());
        page.addView(settingsChoice("Format d’export", "GPX RoutePilot", v -> Toast.makeText(this, "Le GPX conserve la trace et les repères", Toast.LENGTH_SHORT).show()), settingsMargin());

        page.addView(sectionLabel("ROUTEPILOT"), sectionTopMargin());
        TextView about = text("Version 0.2 • aperçu développement\nEnregistrement GPS, repères métier, historique, plans et partage local.", 12, Typeface.NORMAL, TEXT_SECONDARY);
        about.setPadding(dp(2), dp(8), dp(2), dp(20));
        page.addView(about);
        layer.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return layer;
    }

    private View settingsChoice(String title, String value, View.OnClickListener listener) {
        LinearLayout row = glassRow();
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, Typeface.BOLD, Color.WHITE));
        labels.addView(text(value, 12, Typeface.NORMAL, TEXT_SECONDARY));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(text("›", 26, Typeface.NORMAL, TEXT_SECONDARY));
        row.setOnClickListener(listener);
        return row;
    }

    private View toggleRow(String title, String subtitle, String key, boolean def) {
        LinearLayout row = glassRow();
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, Typeface.BOLD, Color.WHITE));
        labels.addView(text(subtitle, 12, Typeface.NORMAL, TEXT_SECONDARY));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).apply();
            if ("keep_screen_on".equals(key)) {
                if (isChecked) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        row.addView(sw);
        return row;
    }

    private LinearLayout glassRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(13), dp(13), dp(13));
        row.setBackground(roundRect(GLASS_SOFT, 20));
        return row;
    }

    private void showMapStyleQuickMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Style de carte")
                .setItems(new String[]{"Clair", "Sombre", "Standard"}, (d, which) -> {
                    String style = new String[]{"Clair", "Sombre", "Standard"}[which];
                    prefs.edit().putString("map_style", style).apply();
                    applyMapStyle(style);
                }).show();
    }

    private void showMapStyleDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Style de carte")
                .setSingleChoiceItems(new String[]{"Clair moderne", "Sombre moderne", "OpenStreetMap standard"},
                        mapStyleIndex(), (dialog, which) -> {
                            String style = which == 0 ? "Clair" : which == 1 ? "Sombre" : "Standard";
                            prefs.edit().putString("map_style", style).apply();
                            applyMapStyle(style);
                            dialog.dismiss();
                            refreshSettings();
                        }).show();
    }

    private void showAccentDialog() {
        final String[] names = {"Bleu", "Violet", "Vert", "Orange"};
        new AlertDialog.Builder(this).setTitle("Couleur d’accent")
                .setSingleChoiceItems(names, accentIndex(), (dialog, which) -> {
                    prefs.edit().putString("accent_name", names[which]).apply();
                    dialog.dismiss();
                    rebuildChrome();
                }).show();
    }

    private void rebuildChrome() {
        root.removeView(topBar); root.removeView(bottomPanel); root.removeView(bottomNav);
        topBar = buildTopBar(); bottomPanel = buildRecordingPanel(); bottomNav = buildBottomNav();
        root.addView(topBar, topParams()); root.addView(bottomPanel, recordingPanelParams()); root.addView(bottomNav, navParams());
        showSettingsScreen();
    }

    private void refreshSettings() {
        if (settingsScreen != null) root.removeView(settingsScreen);
        settingsScreen = buildSettingsScreen();
        root.addView(settingsScreen, contentParams());
    }

    private void applyMapStyle(String style) {
        try {
            if ("Sombre".equals(style)) {
                mapView.setTileSource(new XYTileSource("CartoDark", 0, 20, 256, ".png",
                        new String[]{"https://a.basemaps.cartocdn.com/dark_all/", "https://b.basemaps.cartocdn.com/dark_all/"},
                        "© OpenStreetMap contributors © CARTO"));
            } else if ("Standard".equals(style)) {
                mapView.setTileSource(TileSourceFactory.MAPNIK);
            } else {
                mapView.setTileSource(new XYTileSource("CartoLight", 0, 20, 256, ".png",
                        new String[]{"https://a.basemaps.cartocdn.com/light_all/", "https://b.basemaps.cartocdn.com/light_all/"},
                        "© OpenStreetMap contributors © CARTO"));
            }
            liveTrack.getOutlinePaint().setColor(accent());
            mapView.invalidate();
        } catch (Exception e) {
            mapView.setTileSource(TileSourceFactory.MAPNIK);
        }
    }

    private int mapStyleIndex() {
        String s = prefs.getString("map_style", "Clair");
        return "Sombre".equals(s) ? 1 : "Standard".equals(s) ? 2 : 0;
    }

    private int accentIndex() {
        String s = prefs.getString("accent_name", "Bleu");
        if ("Violet".equals(s)) return 1;
        if ("Vert".equals(s)) return 2;
        if ("Orange".equals(s)) return 3;
        return 0;
    }

    private int accent() {
        String s = prefs == null ? "Bleu" : prefs.getString("accent_name", "Bleu");
        if ("Violet".equals(s)) return PURPLE;
        if ("Vert".equals(s)) return GREEN;
        if ("Orange".equals(s)) return ORANGE;
        return BLUE;
    }

    private void toggleRecordingPanel() {
        View content = findByTag(bottomPanel, "recording_content");
        if (content == null) return;
        recordingPanelCollapsed = !recordingPanelCollapsed;
        if (recordingPanelCollapsed) {
            content.animate().alpha(0f).setDuration(140).withEndAction(() -> content.setVisibility(View.GONE)).start();
            collapseButton.setText("⌃");
            bottomPanel.animate().translationY(dp(4)).setDuration(180).start();
        } else {
            content.setVisibility(View.VISIBLE);
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(180).start();
            collapseButton.setText("⌄");
        }
    }

    private View findByTag(View view, String tag) {
        if (tag.equals(view.getTag())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View f = findByTag(vg.getChildAt(i), tag);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void toggleRecording() {
        if (recording) finishRecording(); else startRecording();
    }

    private void startRecording() {
        if (!hasFineLocation()) { ensureLocationPermission(); return; }
        routePoints.clear(); routeEvents.clear();
        liveTrack.setPoints(new ArrayList<>());
        startedAt = System.currentTimeMillis();
        recording = true;
        recordButton.setText("Terminer et sauvegarder");
        recordButton.setBackground(roundRect(RED, 18));
        status.setText("Enregistrement en cours • 0 point");
        setRecordingControls(true);
        startGpsUpdates();
        haptic(recordButton);
        Toast.makeText(this, "Tournée démarrée", Toast.LENGTH_SHORT).show();
    }

    private void finishRecording() {
        if (!recording) return;
        recording = false;
        stopGpsUpdates();
        lastSavedRoute = saveGpx();
        recordButton.setText("Commencer une nouvelle tournée");
        recordButton.setBackground(roundRect(accent(), 18));
        setRecordingControls(false);
        if (lastSavedRoute != null) {
            long duration = System.currentTimeMillis() - startedAt;
            status.setText("Sauvegardée • " + formatDuration(duration) + " • " + routeEvents.size() + " repères");
            shareButton.setEnabled(true); shareButton.setAlpha(1f);
            Toast.makeText(this, "Tournée sauvegardée", Toast.LENGTH_SHORT).show();
        } else status.setText("Erreur pendant la sauvegarde");
    }

    private void addEvent(String type, String label, int color) {
        if (!recording) { Toast.makeText(this, "Commence d’abord une tournée", Toast.LENGTH_SHORT).show(); return; }
        if (lastLocation == null) { Toast.makeText(this, "Position GPS indisponible", Toast.LENGTH_SHORT).show(); return; }
        RouteEvent event = new RouteEvent(type, label, lastLocation.getLatitude(), lastLocation.getLongitude(), lastLocation.getAccuracy(), System.currentTimeMillis());
        routeEvents.add(event);
        addNumberMarker(new GeoPoint(event.lat, event.lon), label, routeEvents.size(), color);
        status.setText(label + " ajouté • " + routeEvents.size() + " repère" + (routeEvents.size() > 1 ? "s" : ""));
        haptic(type.equals("REVERSE") ? reverseButton : twoSidesButton);
        Toast.makeText(this, label + " enregistré", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        lastLocation = location;
        GeoPoint gp = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (recording && shouldAppend(location)) {
            routePoints.add(new RoutePoint(location.getLatitude(), location.getLongitude(), location.hasAltitude() ? location.getAltitude() : null,
                    location.getAccuracy(), location.getTime() > 0 ? location.getTime() : System.currentTimeMillis()));
            List<GeoPoint> points = new ArrayList<>(liveTrack.getActualPoints()); points.add(gp); liveTrack.setPoints(points);
            status.setText("Enregistrement • " + formatDuration(System.currentTimeMillis() - startedAt) + " • ±" + Math.round(location.getAccuracy()) + " m");
        } else if (!recording && status != null) {
            status.setText("GPS prêt • précision ±" + Math.round(location.getAccuracy()) + " m");
        }
        if (prefs.getBoolean("follow_position", true) && recording) mapView.getController().animateTo(gp);
        mapView.invalidate();
    }

    private boolean shouldAppend(Location location) {
        if (routePoints.isEmpty()) return true;
        RoutePoint previous = routePoints.get(routePoints.size() - 1);
        float[] result = new float[1];
        Location.distanceBetween(previous.lat, previous.lon, location.getLatitude(), location.getLongitude(), result);
        return result[0] >= 1.0f || location.getTime() - previous.time >= 2000;
    }

    private File saveGpx() {
        try {
            File dir = new File(getFilesDir(), "routes"); if (!dir.exists() && !dir.mkdirs()) return null;
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE).format(new Date(startedAt));
            File file = new File(dir, "RoutePilot_" + stamp + ".gpx");
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<gpx version=\"1.1\" creator=\"RoutePilot\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:rp=\"https://routepilot.local/gpx/1\">\n");
            xml.append("  <metadata><name>").append(escape("Tournée " + stamp)).append("</name></metadata>\n");
            for (RouteEvent event : routeEvents) {
                xml.append("  <wpt lat=\"").append(event.lat).append("\" lon=\"").append(event.lon).append("\">\n");
                xml.append("    <time>").append(iso(event.time)).append("</time><name>").append(escape(event.label)).append("</name><type>RoutePilot</type>\n");
                xml.append("    <extensions><rp:event>").append(event.type).append("</rp:event><rp:accuracy>").append(event.accuracy).append("</rp:accuracy></extensions>\n  </wpt>\n");
            }
            xml.append("  <trk><name>").append(escape("RoutePilot " + stamp)).append("</name><trkseg>\n");
            for (RoutePoint point : routePoints) {
                xml.append("    <trkpt lat=\"").append(point.lat).append("\" lon=\"").append(point.lon).append("\">\n");
                if (point.altitude != null) xml.append("      <ele>").append(point.altitude).append("</ele>\n");
                xml.append("      <time>").append(iso(point.time)).append("</time><extensions><rp:accuracy>").append(point.accuracy).append("</rp:accuracy></extensions>\n    </trkpt>\n");
            }
            xml.append("  </trkseg></trk>\n</gpx>\n");
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(xml.toString().getBytes(StandardCharsets.UTF_8)); }
            return file;
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    private List<File> routeFiles() {
        File dir = new File(getFilesDir(), "routes");
        File[] arr = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".gpx"));
        if (arr == null) return new ArrayList<>();
        List<File> list = new ArrayList<>(Arrays.asList(arr));
        Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return list;
    }

    private RouteSummary parseRoute(File file) {
        RouteSummary s = new RouteSummary(file);
        try (FileInputStream in = new FileInputStream(file)) {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(in, "UTF-8");
            int event = p.getEventType();
            boolean inWpt = false, inTrkpt = false;
            double lat = 0, lon = 0;
            String wptName = null, wptType = null;
            long wptTime = 0;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = p.getName();
                    if ("wpt".equals(name)) { inWpt = true; lat = parseDouble(p.getAttributeValue(null, "lat")); lon = parseDouble(p.getAttributeValue(null, "lon")); wptName = "Repère"; wptType = "EVENT"; wptTime = 0; }
                    else if ("trkpt".equals(name)) { inTrkpt = true; lat = parseDouble(p.getAttributeValue(null, "lat")); lon = parseDouble(p.getAttributeValue(null, "lon")); }
                    else if ("name".equals(name) && inWpt) wptName = p.nextText();
                    else if ("time".equals(name)) {
                        long t = parseTime(p.nextText());
                        if (inWpt) wptTime = t;
                        else if (inTrkpt) {
                            if (s.firstTime == 0) s.firstTime = t;
                            s.lastTime = t;
                        }
                    } else if ("event".equals(name) && inWpt) wptType = p.nextText();
                } else if (event == XmlPullParser.END_TAG) {
                    if ("trkpt".equals(p.getName())) {
                        s.points.add(new RoutePoint(lat, lon, null, 0, 0)); inTrkpt = false;
                    } else if ("wpt".equals(p.getName())) {
                        s.events.add(new RouteEvent(wptType, wptName, lat, lon, 0, wptTime)); inWpt = false;
                    }
                }
                event = p.next();
            }
        } catch (Exception ignored) {}
        for (int i = 1; i < s.points.size(); i++) {
            RoutePoint a = s.points.get(i - 1), b = s.points.get(i);
            float[] out = new float[1]; Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, out); s.distanceM += out[0];
        }
        s.durationMs = s.lastTime > s.firstTime ? s.lastTime - s.firstTime : 0;
        return s;
    }

    private void renameRoute(File file) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(displayName(file));
        input.setSelectAllOnFocus(true);
        int pad = dp(22); input.setPadding(pad, dp(8), pad, dp(8));
        new AlertDialog.Builder(this).setTitle("Renommer la tournée").setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) prefs.edit().putString("route_name_" + file.getName(), name).apply();
                    showHistoryScreen();
                }).show();
    }

    private String displayName(File file) {
        String saved = prefs.getString("route_name_" + file.getName(), null);
        if (saved != null && !saved.trim().isEmpty()) return saved;
        String n = file.getName().replace("RoutePilot_", "Tournée ").replace(".gpx", "").replace('_', ' ');
        return n;
    }

    private void shareRoute(File file) {
        if (file == null || !file.exists()) { Toast.makeText(this, "Aucune tournée à partager", Toast.LENGTH_SHORT).show(); return; }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/gpx+xml");
        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getPackageName() + ".files", file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Partager la tournée"));
    }

    private void addNumberMarker(GeoPoint point, String title, int number, int color) {
        Marker marker = new Marker(mapView);
        marker.setPosition(point); marker.setTitle(title);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setIcon(numberDrawable(number, color));
        marker.setRelatedObject("routepilot_marker");
        mapView.getOverlays().add(marker);
    }

    private void clearPlanMarkers() {
        mapView.getOverlays().removeIf(o -> o instanceof Marker && "routepilot_marker".equals(((Marker) o).getRelatedObject()));
    }

    private BitmapDrawable numberDrawable(int number, int color) {
        int size = dp(number == 0 ? 34 : 38);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); c.drawCircle(size/2f, size/2f, size*0.44f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.WHITE); c.drawCircle(size/2f, size/2f, size*0.40f, p);
        if (number > 0) {
            p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(number > 9 ? 11 : 13)); p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = p.getFontMetrics(); c.drawText(String.valueOf(number), size/2f, size/2f - (fm.ascent + fm.descent)/2f, p);
        }
        return new BitmapDrawable(getResources(), b);
    }

    private void recenter() {
        if (lastLocation != null) {
            mapView.getController().animateTo(new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude()));
            mapView.getController().setZoom(18.5);
        } else Toast.makeText(this, "Position GPS en attente", Toast.LENGTH_SHORT).show();
    }

    private void ensureLocationPermission() {
        if (hasFineLocation()) enableLocation();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
    }

    private boolean hasFineLocation() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableLocation() {
        locationOverlay.enableMyLocation(); locationOverlay.enableFollowLocation(); startGpsUpdates();
    }

    private void startGpsUpdates() {
        if (!hasFineLocation()) return;
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this); } catch (SecurityException ignored) {}
    }

    private void stopGpsUpdates() {
        if (!hasFineLocation()) return;
        try {
            locationManager.removeUpdates(this);
            if (!recording) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500L, 0f, this);
        } catch (SecurityException ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enableLocation();
        else Toast.makeText(this, "La localisation précise est nécessaire pour enregistrer une tournée", Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); if (hasFineLocation()) startGpsUpdates(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onDestroy() { stopGpsUpdates(); if (locationOverlay != null) locationOverlay.disableMyLocation(); super.onDestroy(); }

    private void removeScreenOverlays() {
        if (historyScreen != null) { root.removeView(historyScreen); historyScreen = null; }
        if (settingsScreen != null) { root.removeView(settingsScreen); settingsScreen = null; }
        if (routePlanOverlay != null) { root.removeView(routePlanOverlay); routePlanOverlay = null; }
        mapView.getOverlays().remove(historyTrack);
        clearPlanMarkers();
    }

    private void updateNavState(String selected) {
        if (navMap == null) return;
        styleNav(navMap, "map".equals(selected)); styleNav(navHistory, "history".equals(selected)); styleNav(navSettings, "settings".equals(selected));
    }

    private void styleNav(TextView v, boolean selected) {
        v.setTextColor(selected ? Color.WHITE : Color.argb(145,255,255,255));
        v.setBackground(selected ? roundRect(Color.argb(115, Color.red(accent()), Color.green(accent()), Color.blue(accent())), 19) : null);
    }

    private void setRecordingControls(boolean enabled) {
        if (reverseButton == null) return;
        reverseButton.setEnabled(enabled); twoSidesButton.setEnabled(enabled);
        reverseButton.setAlpha(enabled ? 1f : 0.34f); twoSidesButton.setAlpha(enabled ? 1f : 0.34f);
    }

    private void press(View v) {
        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(new DecelerateInterpolator()).start()).start();
    }

    private void haptic(View v) { if (prefs.getBoolean("haptics", true)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); }
    private void fadeIn(View v) { v.setAlpha(0f); v.animate().alpha(1f).setDuration(190).start(); }

    private TextView pill(String label, int color) {
        TextView v = text(label, 14, Typeface.BOLD, Color.WHITE); v.setGravity(Gravity.CENTER); v.setBackground(roundRect(color, 18)); return v;
    }
    private TextView smallPill(String label, int color) { TextView v = pill(label, color); v.setTextSize(12); return v; }
    private TextView circleButton(String label, int color) { TextView v = text(label, 17, Typeface.BOLD, Color.WHITE); v.setGravity(Gravity.CENTER); v.setBackground(roundRect(color, 18)); return v; }
    private TextView badge(String label, int color) { TextView v = text(label, 12, Typeface.BOLD, Color.WHITE); v.setGravity(Gravity.CENTER); v.setBackground(roundRect(color, 30)); return v; }

    private TextView text(String value, float sizeSp, int style, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sizeSp); t.setTypeface(Typeface.create("sans-serif", style)); t.setTextColor(color); t.setIncludeFontPadding(false); return t;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp)); d.setStroke(dp(1), Color.argb(30,255,255,255)); return d;
    }

    private TextView sectionLabel(String s) { TextView v = text(s, 11, Typeface.BOLD, accent()); v.setPadding(dp(2), 0, 0, dp(8)); return v; }
    private LinearLayout.LayoutParams settingsMargin() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(9); return p; }
    private LinearLayout.LayoutParams sectionTopMargin() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(13); return p; }
    private LinearLayout.LayoutParams cardMarginParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); return p; }
    private LinearLayout.LayoutParams weightedAction(float weight, int rightMargin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), weight); p.rightMargin = dp(rightMargin); return p; }
    private LinearLayout.LayoutParams squareParams(int size) { return new LinearLayout.LayoutParams(dp(size), dp(size)); }

    private FrameLayout.LayoutParams topParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, dp(78), Gravity.TOP); p.setMargins(dp(14), dp(18), dp(14), 0); return p;
    }
    private FrameLayout.LayoutParams recordingPanelParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); p.setMargins(dp(14), 0, dp(14), dp(89)); return p;
    }
    private FrameLayout.LayoutParams navParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM); p.setMargins(dp(28), 0, dp(28), dp(10)); return p;
    }
    private FrameLayout.LayoutParams contentParams() { return new FrameLayout.LayoutParams(-1, -1); }
    private FrameLayout.LayoutParams planOverlayParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, dp(250), Gravity.BOTTOM); p.setMargins(dp(14),0,dp(14),dp(89)); return p;
    }

    private String formatRouteDate(long ms) { return new SimpleDateFormat("EEEE d MMMM • HH:mm", Locale.FRANCE).format(new Date(ms)); }
    private String formatDuration(long ms) { long total = Math.max(0, ms / 1000); long h = total / 3600; long m = (total % 3600) / 60; long s = total % 60; return h > 0 ? String.format(Locale.FRANCE, "%dh %02d", h, m) : String.format(Locale.FRANCE, "%d min %02d s", m, s); }
    private String formatDistance(double m) { return m >= 1000 ? String.format(Locale.FRANCE, "%.1f km", m / 1000.0) : String.format(Locale.FRANCE, "%.0f m", m); }
    private static String iso(long ms) { return Instant.ofEpochMilli(ms).toString(); }
    private static long parseTime(String s) { try { return Instant.parse(s).toEpochMilli(); } catch (DateTimeParseException e) { return 0; } }
    private static double parseDouble(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static class RoutePoint {
        final double lat, lon; final Double altitude; final float accuracy; final long time;
        RoutePoint(double lat, double lon, Double altitude, float accuracy, long time) { this.lat = lat; this.lon = lon; this.altitude = altitude; this.accuracy = accuracy; this.time = time; }
    }
    private static class RouteEvent {
        final String type, label; final double lat, lon; final float accuracy; final long time;
        RouteEvent(String type, String label, double lat, double lon, float accuracy, long time) { this.type = type; this.label = label; this.lat = lat; this.lon = lon; this.accuracy = accuracy; this.time = time; }
    }
    private static class RouteSummary {
        final File file; final List<RoutePoint> points = new ArrayList<>(); final List<RouteEvent> events = new ArrayList<>();
        long firstTime, lastTime, durationMs; double distanceM;
        RouteSummary(File file) { this.file = file; }
    }
}