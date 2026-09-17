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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
import androidx.core.view.WindowCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
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
import java.io.InputStream;
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

public class RoutePilotActivity extends AppCompatActivity implements LocationListener {

    private static final int LOCATION_PERMISSION = 42;
    private static final int IMPORT_GPX = 1337;
    private static final int BLUE = Color.rgb(10, 132, 255);
    private static final int GREEN = Color.rgb(48, 209, 88);
    private static final int ORANGE = Color.rgb(255, 159, 10);
    private static final int RED = Color.rgb(255, 69, 58);
    private static final int PURPLE = Color.rgb(175, 82, 222);
    private static final int CYAN = Color.rgb(100, 210, 255);
    private static final int BG = Color.rgb(13, 14, 17);
    private static final int GLASS = Color.argb(232, 31, 32, 38);
    private static final int GLASS_2 = Color.argb(218, 45, 46, 53);
    private static final int GLASS_3 = Color.argb(246, 23, 24, 29);
    private static final int SECONDARY = Color.argb(185, 255, 255, 255);

    private final List<RoutePoint> routePoints = new ArrayList<>();
    private final List<RouteEvent> routeEvents = new ArrayList<>();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private MapView map;
    private MyLocationNewOverlay locationOverlay;
    private LocationManager locationManager;
    private Polyline liveTrack;
    private Polyline previewTrack;
    private SharedPreferences prefs;

    private View topBar;
    private View recordSheet;
    private View dock;
    private View pageOverlay;
    private View planSheet;
    private LinearLayout recordContent;

    private TextView gpsChip;
    private TextView durationChip;
    private TextView recordButton;
    private TextView reverseButton;
    private TextView twoSidesButton;
    private TextView undoButton;
    private TextView collapseButton;
    private TextView navMap;
    private TextView navHistory;
    private TextView navSettings;
    private TextView liveDistance;
    private TextView liveEvents;
    private TextView livePoints;

    private boolean recording;
    private boolean paused;
    private boolean collapsed;
    private long startedAt;
    private long pausedAt;
    private long totalPausedMs;
    private Location lastLocation;
    private File lastSavedRoute;
    private double sessionDistanceM;
    private String selectedTab = "map";

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            updateLiveUi();
            if (recording) timerHandler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);

        Configuration.getInstance().setUserAgentValue(getPackageName() + "/0.3 RoutePilot");
        Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));

        prefs = getSharedPreferences("routepilot", MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        applyKeepScreenOn();

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        buildMap();
        buildChrome();
        setContentView(root);

        ensureLocationPermission();
        updateTab("map");
    }

    private void buildMap() {
        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.setUseDataConnection(true);
        map.setHorizontalMapRepetitionEnabled(false);
        map.setVerticalMapRepetitionEnabled(false);
        map.setMinZoomLevel(3.0);
        map.setMaxZoomLevel(20.0);
        map.getController().setZoom(18.0);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        locationOverlay.setDrawAccuracyEnabled(true);
        map.getOverlays().add(locationOverlay);

        liveTrack = new Polyline();
        liveTrack.getOutlinePaint().setColor(accent());
        liveTrack.getOutlinePaint().setStrokeWidth(dp(7));
        liveTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        map.getOverlays().add(liveTrack);
    }

    private void buildChrome() {
        topBar = buildTopBar();
        recordSheet = buildRecordSheet();
        dock = buildDock();
        root.addView(topBar, topParams());
        root.addView(recordSheet, sheetParams());
        root.addView(dock, dockParams());
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(10), dp(10));
        bar.setBackground(glass(GLASS, 28));
        bar.setElevation(dp(15));

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);
        TextView brand = label("RoutePilot", 20, Typeface.BOLD, Color.WHITE);
        TextView sub = label("Navigation de tournée", 11, Typeface.NORMAL, SECONDARY);
        brandBox.addView(brand);
        brandBox.addView(sub);
        bar.addView(brandBox, new LinearLayout.LayoutParams(0, -2, 1f));

        gpsChip = chip("GPS…", GLASS_2);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(38));
        cp.rightMargin = dp(7);
        bar.addView(gpsChip, cp);

        TextView recenter = circle("◎", accent());
        recenter.setOnClickListener(v -> { press(v); recenter(); });
        bar.addView(recenter, new LinearLayout.LayoutParams(dp(42), dp(42)));
        return bar;
    }

    private View buildRecordSheet() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(15), dp(10), dp(15), dp(14));
        sheet.setBackground(glass(GLASS, 30));
        sheet.setElevation(dp(20));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(label("TOURNÉE", 10, Typeface.BOLD, accent()));
        durationChip = label("Prêt", 13, Typeface.BOLD, Color.WHITE);
        titleBox.addView(durationChip);
        head.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

        collapseButton = circle("⌄", GLASS_2);
        collapseButton.setOnClickListener(v -> toggleSheet());
        head.addView(collapseButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        sheet.addView(head);

        recordContent = new LinearLayout(this);
        recordContent.setOrientation(LinearLayout.VERTICAL);
        sheet.addView(recordContent);

        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(0, dp(10), 0, dp(10));
        liveDistance = statValue("0 m", "DISTANCE");
        liveEvents = statValue("0", "REPÈRES");
        livePoints = statValue("0", "POINTS GPS");
        stats.addView(statCell(liveDistance), new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(statCell(liveEvents), new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(statCell(livePoints), new LinearLayout.LayoutParams(0, -2, 1f));
        recordContent.addView(stats);

        recordButton = pill("Commencer l’enregistrement", accent(), 17);
        recordButton.setOnClickListener(v -> { press(v); toggleRecording(); });
        recordContent.addView(recordButton, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout eventRow = new LinearLayout(this);
        eventRow.setPadding(0, dp(9), 0, 0);
        reverseButton = pill("↶  Marche arrière", GLASS_2, 13);
        twoSidesButton = pill("⇆  2 côtés", GLASS_2, 13);
        reverseButton.setOnClickListener(v -> addEvent("REVERSE", "Marche arrière", ORANGE));
        twoSidesButton.setOnClickListener(v -> addEvent("TWO_SIDES", "2 côtés", GREEN));
        LinearLayout.LayoutParams h1 = new LinearLayout.LayoutParams(0, dp(52), 1f); h1.rightMargin = dp(5);
        LinearLayout.LayoutParams h2 = new LinearLayout.LayoutParams(0, dp(52), 1f); h2.leftMargin = dp(5);
        eventRow.addView(reverseButton, h1);
        eventRow.addView(twoSidesButton, h2);
        recordContent.addView(eventRow);

        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(0, dp(8), 0, 0);
        undoButton = pill("↩ Annuler dernier repère", GLASS_2, 12);
        TextView pause = pill("Ⅱ Pause", GLASS_2, 12);
        pause.setTag("pause_button");
        undoButton.setOnClickListener(v -> undoLastEvent());
        pause.setOnClickListener(v -> togglePause((TextView) v));
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(0, dp(44), 1.25f); t1.rightMargin = dp(5);
        LinearLayout.LayoutParams t2 = new LinearLayout.LayoutParams(0, dp(44), .75f); t2.leftMargin = dp(5);
        tools.addView(undoButton, t1);
        tools.addView(pause, t2);
        recordContent.addView(tools);

        setRecordingButtons(false);
        return sheet;
    }

    private View buildDock() {
        LinearLayout d = new LinearLayout(this);
        d.setGravity(Gravity.CENTER);
        d.setPadding(dp(7), dp(6), dp(7), dp(6));
        d.setBackground(glass(Color.argb(246, 25, 26, 31), 27));
        d.setElevation(dp(24));
        navMap = nav("⌖", "Carte");
        navHistory = nav("≡", "Historique");
        navSettings = nav("⚙", "Réglages");
        navMap.setOnClickListener(v -> showMap());
        navHistory.setOnClickListener(v -> showHistory());
        navSettings.setOnClickListener(v -> showSettings());
        d.addView(navMap, new LinearLayout.LayoutParams(0, dp(58), 1f));
        d.addView(navHistory, new LinearLayout.LayoutParams(0, dp(58), 1f));
        d.addView(navSettings, new LinearLayout.LayoutParams(0, dp(58), 1f));
        return d;
    }

    private void showMap() {
        selectedTab = "map";
        clearPage();
        topBar.setVisibility(View.VISIBLE);
        recordSheet.setVisibility(View.VISIBLE);
        map.setVisibility(View.VISIBLE);
        updateTab("map");
    }

    private void showHistory() {
        selectedTab = "history";
        clearPage();
        topBar.setVisibility(View.GONE);
        recordSheet.setVisibility(View.GONE);
        map.setVisibility(View.GONE);
        pageOverlay = historyPage();
        root.addView(pageOverlay, pageParams());
        updateTab("history");
        fadeIn(pageOverlay);
    }

    private View historyPage() {
        LinearLayout page = pageBase();
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(label("Historique", 30, Typeface.BOLD, Color.WHITE));
        titleBox.addView(label(routeFiles().size() + " tournée(s) enregistrée(s)", 12, Typeface.NORMAL, SECONDARY));
        titleRow.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView importBtn = circle("＋", accent());
        importBtn.setOnClickListener(v -> importGpx());
        titleRow.addView(importBtn, new LinearLayout.LayoutParams(dp(46), dp(46)));
        page.addView(titleRow);

        EditText search = new EditText(this);
        search.setHint("Rechercher une tournée");
        search.setHintTextColor(Color.argb(120,255,255,255));
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(glass(GLASS_2, 18));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(48)); slp.topMargin = dp(16); slp.bottomMargin = dp(13);
        page.addView(search, slp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        List<RouteSummary> all = summaries();
        renderHistory(list, all, "");
        search.addTextChangedListener(new SimpleWatcher(s -> renderHistory(list, all, s)));
        return page;
    }

    private void renderHistory(LinearLayout list, List<RouteSummary> all, String query) {
        list.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (RouteSummary s : all) {
            if (!q.isEmpty() && !displayName(s.file).toLowerCase(Locale.ROOT).contains(q)) continue;
            list.addView(historyCard(s), marginBottom(12));
            count++;
        }
        if (count == 0) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(70), 0, dp(30));
            empty.addView(label("⌁", 44, Typeface.NORMAL, Color.argb(110,255,255,255)));
            empty.addView(label(q.isEmpty() ? "Aucune tournée" : "Aucun résultat", 18, Typeface.BOLD, Color.WHITE));
            empty.addView(label(q.isEmpty() ? "Lance un enregistrement depuis la carte." : "Essaie un autre nom.", 12, Typeface.NORMAL, SECONDARY));
            list.addView(empty);
        }
    }

    private View historyCard(RouteSummary s) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(14));
        card.setBackground(glass(GLASS_2, 23));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(displayName(s.file), 17, Typeface.BOLD, Color.WHITE));
        texts.addView(label(formatDate(s.file.lastModified()), 11, Typeface.NORMAL, SECONDARY));
        head.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView menu = circle("•••", Color.argb(80,255,255,255));
        menu.setOnClickListener(v -> routeActions(s));
        head.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(42)));
        card.addView(head);

        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(0, dp(14), 0, dp(12));
        stats.addView(summaryStat("DURÉE", formatDurationExact(s.durationMs)), new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(summaryStat("DISTANCE", formatDistance(s.distanceM)), new LinearLayout.LayoutParams(0, -2, 1f));
        stats.addView(summaryStat("MOYENNE", formatSpeed(s)), new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(stats);

        LinearLayout footer = new LinearLayout(this);
        TextView plan = pill("Voir le plan", accent(), 12);
        TextView share = pill("Partager", GLASS_2, 12);
        plan.setOnClickListener(v -> openPlan(s));
        share.setOnClickListener(v -> shareRoute(s.file));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(42), 1.25f); p1.rightMargin = dp(5);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(42), .75f); p2.leftMargin = dp(5);
        footer.addView(plan, p1); footer.addView(share, p2);
        card.addView(footer);
        card.setOnClickListener(v -> openPlan(s));
        return card;
    }

    private void routeActions(RouteSummary s) {
        new AlertDialog.Builder(this)
                .setTitle(displayName(s.file))
                .setItems(new String[]{"Voir le plan", "Renommer", "Partager", "Dupliquer", "Supprimer"}, (d, which) -> {
                    if (which == 0) openPlan(s);
                    else if (which == 1) renameRoute(s.file);
                    else if (which == 2) shareRoute(s.file);
                    else if (which == 3) duplicateRoute(s.file);
                    else deleteRoute(s.file);
                }).show();
    }

    private void openPlan(RouteSummary s) {
        if (s.points.isEmpty()) { toast("Cette tournée ne contient pas de trace"); return; }
        if (pageOverlay != null) pageOverlay.setVisibility(View.GONE);
        map.setVisibility(View.VISIBLE);
        topBar.setVisibility(View.GONE);
        recordSheet.setVisibility(View.GONE);
        clearPreview();

        previewTrack = new Polyline();
        previewTrack.getOutlinePaint().setColor(accent());
        previewTrack.getOutlinePaint().setStrokeWidth(dp(8));
        previewTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        List<GeoPoint> pts = new ArrayList<>();
        for (RoutePoint p : s.points) pts.add(new GeoPoint(p.lat, p.lon));
        previewTrack.setPoints(pts);
        map.getOverlays().add(previewTrack);
        addMarker(pts.get(0), "Départ", "D", GREEN, 0);
        for (int i = 0; i < s.events.size(); i++) {
            RouteEvent e = s.events.get(i);
            addMarker(new GeoPoint(e.lat, e.lon), e.label, String.valueOf(i + 1), "REVERSE".equals(e.type) ? ORANGE : GREEN, i + 1);
        }
        addMarker(pts.get(pts.size() - 1), "Arrivée", "A", RED, s.events.size() + 1);
        map.zoomToBoundingBox(previewTrack.getBounds(), true, dp(76));
        map.invalidate();

        planSheet = planSheet(s);
        root.addView(planSheet, planParams());
        fadeIn(planSheet);
    }

    private View planSheet(RouteSummary s) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(15), dp(12), dp(15), dp(14));
        sheet.setBackground(glass(GLASS_3, 30));
        sheet.setElevation(dp(25));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(label(displayName(s.file), 17, Typeface.BOLD, Color.WHITE));
        box.addView(label(formatDurationExact(s.durationMs) + "  •  " + formatDistance(s.distanceM) + "  •  " + s.events.size() + " repères", 11, Typeface.NORMAL, SECONDARY));
        head.addView(box, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = circle("×", GLASS_2);
        close.setOnClickListener(v -> closePlan());
        head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        sheet.addView(head);

        ScrollView scroll = new ScrollView(this);
        LinearLayout timeline = new LinearLayout(this); timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.addView(timelineRow("D", "Départ", "Début de la tournée", GREEN));
        for (int i = 0; i < s.events.size(); i++) {
            RouteEvent e = s.events.get(i);
            String when = e.time > 0 && s.firstTime > 0 ? "+" + formatDurationExact(e.time - s.firstTime) : "Repère " + (i + 1);
            View row = timelineRow(String.valueOf(i + 1), e.label, when, "REVERSE".equals(e.type) ? ORANGE : GREEN);
            final int index = i;
            row.setOnClickListener(v -> {
                map.getController().animateTo(new GeoPoint(s.events.get(index).lat, s.events.get(index).lon));
                map.getController().setZoom(19.0);
            });
            timeline.addView(row);
        }
        timeline.addView(timelineRow("A", "Arrivée", "Fin de la tournée", RED));
        scroll.addView(timeline);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1f); sp.topMargin = dp(7);
        sheet.addView(scroll, sp);

        LinearLayout actions = new LinearLayout(this); actions.setPadding(0, dp(8), 0, 0);
        TextView share = pill("Partager", GLASS_2, 12);
        TextView rename = pill("Renommer", GLASS_2, 12);
        TextView center = pill("Vue complète", accent(), 12);
        share.setOnClickListener(v -> shareRoute(s.file));
        rename.setOnClickListener(v -> renameRoute(s.file));
        center.setOnClickListener(v -> map.zoomToBoundingBox(previewTrack.getBounds(), true, dp(76)));
        actions.addView(share, weighted(1f, 4)); actions.addView(rename, weighted(1f, 4)); actions.addView(center, weighted(1.2f, 0));
        sheet.addView(actions);
        return sheet;
    }

    private View timelineRow(String badge, String title, String subtitle, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView b = circle(badge, color);
        row.addView(b, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout t = new LinearLayout(this); t.setOrientation(LinearLayout.VERTICAL);
        t.addView(label(title, 13, Typeface.BOLD, Color.WHITE));
        t.addView(label(subtitle, 11, Typeface.NORMAL, SECONDARY));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f); tp.leftMargin = dp(10);
        row.addView(t, tp);
        return row;
    }

    private void closePlan() {
        if (planSheet != null) { root.removeView(planSheet); planSheet = null; }
        clearPreview();
        map.setVisibility(View.GONE);
        if (pageOverlay != null) pageOverlay.setVisibility(View.VISIBLE);
    }

    private void showSettings() {
        selectedTab = "settings";
        clearPage();
        topBar.setVisibility(View.GONE);
        recordSheet.setVisibility(View.GONE);
        map.setVisibility(View.GONE);
        pageOverlay = settingsPage();
        root.addView(pageOverlay, pageParams());
        updateTab("settings");
        fadeIn(pageOverlay);
    }

    private View settingsPage() {
        LinearLayout page = pageBase();
        page.addView(label("Réglages", 30, Typeface.BOLD, Color.WHITE));
        TextView sub = label("Personnalisation, GPS et données", 12, Typeface.NORMAL, SECONDARY);
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(-1, -2); subp.bottomMargin = dp(20);
        page.addView(sub, subp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content.addView(section("APPARENCE"));
        content.addView(choice("Couleur d’accent", prefs.getString("accent_name", "Bleu"), v -> accentDialog()), marginBottom(9));
        content.addView(choice("Carte", "OpenStreetMap • sans clé API", v -> toast("Carte OpenStreetMap active")), marginBottom(9));
        content.addView(toggle("Interface compacte", "Réduit les panneaux sur la carte", "compact_ui", false), marginBottom(9));

        content.addView(section("GPS & ENREGISTREMENT"), topMargin(12));
        content.addView(toggle("Suivre ma position", "Recentre automatiquement pendant la tournée", "follow_position", true), marginBottom(9));
        content.addView(toggle("Écran toujours allumé", "Évite la mise en veille pendant le travail", "keep_screen_on", true), marginBottom(9));
        content.addView(toggle("Retour haptique", "Vibration légère sur les actions métier", "haptics", true), marginBottom(9));
        content.addView(choice("Fréquence GPS", "1 s • précision élevée", v -> toast("Mode haute précision actif")), marginBottom(9));

        content.addView(section("DONNÉES"), topMargin(12));
        content.addView(choice("Importer une tournée GPX", "Depuis le stockage du téléphone", v -> importGpx()), marginBottom(9));
        content.addView(choice("Stockage des cartes", cacheSize(), v -> clearMapCache()), marginBottom(9));
        content.addView(choice("Dossier des tournées", routeFiles().size() + " fichier(s) GPX", v -> showHistory()), marginBottom(9));

        content.addView(section("À PROPOS"), topMargin(12));
        LinearLayout about = new LinearLayout(this); about.setOrientation(LinearLayout.VERTICAL); about.setPadding(dp(15),dp(14),dp(15),dp(14)); about.setBackground(glass(GLASS_2,20));
        about.addView(label("RoutePilot 0.3", 15, Typeface.BOLD, Color.WHITE));
        about.addView(label("Enregistreur GPS de tournées • historique • plans ordonnés • partage GPX • personnalisation.\n\nLes cartes utilisent OpenStreetMap sans clé API obligatoire.", 12, Typeface.NORMAL, SECONDARY));
        content.addView(about, marginBottom(20));
        return page;
    }

    private void toggleRecording() {
        if (!recording) startRecording(); else finishRecording();
    }

    private void startRecording() {
        if (!hasLocationPermission()) { ensureLocationPermission(); return; }
        routePoints.clear(); routeEvents.clear(); sessionDistanceM = 0;
        liveTrack.setPoints(new ArrayList<>());
        clearRouteMarkers();
        startedAt = System.currentTimeMillis(); totalPausedMs = 0; pausedAt = 0; paused = false; recording = true;
        recordButton.setText("Terminer et sauvegarder"); recordButton.setBackground(glass(RED, 18));
        setRecordingButtons(true);
        startGpsUpdates();
        timerHandler.removeCallbacks(timerTick); timerHandler.post(timerTick);
        haptic(recordButton); toast("Tournée démarrée");
    }

    private void finishRecording() {
        if (!recording) return;
        if (paused && pausedAt > 0) totalPausedMs += System.currentTimeMillis() - pausedAt;
        recording = false; paused = false;
        timerHandler.removeCallbacks(timerTick);
        lastSavedRoute = saveGpx();
        recordButton.setText("Commencer une nouvelle tournée"); recordButton.setBackground(glass(accent(), 18));
        setRecordingButtons(false);
        updateLiveUi();
        if (lastSavedRoute != null) toast("Tournée sauvegardée"); else toast("Erreur pendant la sauvegarde");
    }

    private void togglePause(TextView button) {
        if (!recording) { toast("Aucune tournée en cours"); return; }
        paused = !paused;
        if (paused) { pausedAt = System.currentTimeMillis(); button.setText("▶ Reprendre"); durationChip.setText("En pause"); }
        else { if (pausedAt > 0) totalPausedMs += System.currentTimeMillis() - pausedAt; pausedAt = 0; button.setText("Ⅱ Pause"); }
        haptic(button);
    }

    private void addEvent(String type, String label, int color) {
        if (!recording || paused) { toast(paused ? "Reprends la tournée d’abord" : "Commence une tournée d’abord"); return; }
        if (lastLocation == null) { toast("Position GPS indisponible"); return; }
        RouteEvent e = new RouteEvent(type, label, lastLocation.getLatitude(), lastLocation.getLongitude(), lastLocation.getAccuracy(), System.currentTimeMillis());
        routeEvents.add(e);
        addMarker(new GeoPoint(e.lat, e.lon), e.label, String.valueOf(routeEvents.size()), color, routeEvents.size());
        updateLiveUi();
        haptic("REVERSE".equals(type) ? reverseButton : twoSidesButton);
        toast(label + " enregistré");
    }

    private void undoLastEvent() {
        if (!recording || routeEvents.isEmpty()) { toast("Aucun repère à annuler"); return; }
        routeEvents.remove(routeEvents.size() - 1);
        clearRouteMarkers();
        for (int i = 0; i < routeEvents.size(); i++) {
            RouteEvent e = routeEvents.get(i);
            addMarker(new GeoPoint(e.lat, e.lon), e.label, String.valueOf(i + 1), "REVERSE".equals(e.type) ? ORANGE : GREEN, i + 1);
        }
        updateLiveUi(); toast("Dernier repère annulé");
    }

    @Override public void onLocationChanged(@NonNull Location location) {
        lastLocation = location;
        if (gpsChip != null) {
            int a = Math.round(location.getAccuracy());
            gpsChip.setText("±" + a + " m");
            gpsChip.setTextColor(a <= 8 ? GREEN : a <= 20 ? ORANGE : RED);
        }
        GeoPoint gp = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (recording && !paused && shouldAppend(location)) {
            if (!routePoints.isEmpty()) {
                RoutePoint prev = routePoints.get(routePoints.size() - 1);
                float[] out = new float[1];
                Location.distanceBetween(prev.lat, prev.lon, location.getLatitude(), location.getLongitude(), out);
                if (out[0] < 250) sessionDistanceM += out[0];
            }
            routePoints.add(new RoutePoint(location.getLatitude(), location.getLongitude(), location.hasAltitude() ? location.getAltitude() : null, location.getAccuracy(), location.getTime() > 0 ? location.getTime() : System.currentTimeMillis()));
            List<GeoPoint> pts = new ArrayList<>(liveTrack.getActualPoints()); pts.add(gp); liveTrack.setPoints(pts);
            if (prefs.getBoolean("follow_position", true)) map.getController().animateTo(gp);
            updateLiveUi();
        }
        map.invalidate();
    }

    private void updateLiveUi() {
        if (durationChip == null) return;
        if (recording) {
            long now = paused ? pausedAt : System.currentTimeMillis();
            durationChip.setText((paused ? "En pause • " : "En cours • ") + formatDurationExact(Math.max(0, now - startedAt - totalPausedMs)));
        } else if (lastSavedRoute != null) durationChip.setText("Dernière tournée sauvegardée");
        else durationChip.setText("Prêt à enregistrer");
        liveDistance.setText(formatDistance(sessionDistanceM));
        liveEvents.setText(String.valueOf(routeEvents.size()));
        livePoints.setText(String.valueOf(routePoints.size()));
    }

    private boolean shouldAppend(Location l) {
        if (l.getAccuracy() > 80f) return false;
        if (routePoints.isEmpty()) return true;
        RoutePoint p = routePoints.get(routePoints.size() - 1);
        float[] d = new float[1]; Location.distanceBetween(p.lat, p.lon, l.getLatitude(), l.getLongitude(), d);
        return d[0] >= 1.0f || l.getTime() - p.time >= 2000L;
    }

    private File saveGpx() {
        try {
            File dir = routesDir();
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE).format(new Date(startedAt));
            File f = new File(dir, "RoutePilot_" + stamp + ".gpx");
            StringBuilder x = new StringBuilder();
            x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            x.append("<gpx version=\"1.1\" creator=\"RoutePilot\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:rp=\"https://routepilot.local/gpx/1\">\n");
            x.append("<metadata><name>").append(escape("Tournée " + stamp)).append("</name></metadata>\n");
            for (RouteEvent e : routeEvents) {
                x.append("<wpt lat=\"").append(e.lat).append("\" lon=\"").append(e.lon).append("\"><time>").append(iso(e.time)).append("</time><name>").append(escape(e.label)).append("</name><type>RoutePilot</type><extensions><rp:event>").append(e.type).append("</rp:event><rp:accuracy>").append(e.accuracy).append("</rp:accuracy></extensions></wpt>\n");
            }
            x.append("<trk><name>").append(escape("RoutePilot " + stamp)).append("</name><trkseg>\n");
            for (RoutePoint p : routePoints) {
                x.append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">");
                if (p.altitude != null) x.append("<ele>").append(p.altitude).append("</ele>");
                x.append("<time>").append(iso(p.time)).append("</time><extensions><rp:accuracy>").append(p.accuracy).append("</rp:accuracy></extensions></trkpt>\n");
            }
            x.append("</trkseg></trk></gpx>\n");
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(x.toString().getBytes(StandardCharsets.UTF_8)); }
            return f;
        } catch (Exception e) { return null; }
    }

    private List<RouteSummary> summaries() {
        List<RouteSummary> list = new ArrayList<>();
        for (File f : routeFiles()) list.add(parseRoute(f));
        return list;
    }

    private List<File> routeFiles() {
        File[] arr = routesDir().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".gpx"));
        if (arr == null) return new ArrayList<>();
        List<File> list = new ArrayList<>(Arrays.asList(arr));
        Collections.sort(list, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        return list;
    }

    private File routesDir() {
        File d = new File(getFilesDir(), "routes"); if (!d.exists()) d.mkdirs(); return d;
    }

    private RouteSummary parseRoute(File file) {
        RouteSummary s = new RouteSummary(file);
        try (FileInputStream in = new FileInputStream(file)) {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser(); p.setInput(in, "UTF-8");
            int ev = p.getEventType(); boolean wpt = false, trk = false; double lat = 0, lon = 0; String n = "Repère", type = "EVENT"; long wt = 0, tt = 0;
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = p.getName();
                    if ("wpt".equals(name)) { wpt = true; lat = dbl(p.getAttributeValue(null,"lat")); lon = dbl(p.getAttributeValue(null,"lon")); n = "Repère"; type = "EVENT"; wt = 0; }
                    else if ("trkpt".equals(name)) { trk = true; lat = dbl(p.getAttributeValue(null,"lat")); lon = dbl(p.getAttributeValue(null,"lon")); tt = 0; }
                    else if ("name".equals(name) && wpt) n = p.nextText();
                    else if ("time".equals(name)) { long t = time(p.nextText()); if (wpt) wt = t; else if (trk) tt = t; }
                    else if ("event".equals(name) && wpt) type = p.nextText();
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("wpt".equals(p.getName())) { s.events.add(new RouteEvent(type,n,lat,lon,0,wt)); wpt=false; }
                    else if ("trkpt".equals(p.getName())) { s.points.add(new RoutePoint(lat,lon,null,0,tt)); if (tt > 0) { if (s.firstTime == 0) s.firstTime = tt; s.lastTime = tt; } trk=false; }
                }
                ev = p.next();
            }
        } catch (Exception ignored) {}
        for (int i=1;i<s.points.size();i++) { RoutePoint a=s.points.get(i-1), b=s.points.get(i); float[] o=new float[1]; Location.distanceBetween(a.lat,a.lon,b.lat,b.lon,o); if(o[0] < 500) s.distanceM += o[0]; }
        s.durationMs = s.lastTime > s.firstTime ? s.lastTime - s.firstTime : 0;
        return s;
    }

    private void renameRoute(File f) {
        EditText input = new EditText(this); input.setText(displayName(f)); input.setSelectAllOnFocus(true); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this).setTitle("Renommer la tournée").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{
            String n=input.getText().toString().trim(); if(!n.isEmpty()) prefs.edit().putString("route_name_"+f.getName(),n).apply(); showHistory();
        }).show();
    }

    private void duplicateRoute(File f) {
        try {
            String base = f.getName().replace(".gpx",""); File out = new File(routesDir(), base + "_copie.gpx"); int i=2; while(out.exists()) out=new File(routesDir(),base+"_copie_"+(i++)+".gpx");
            try (FileInputStream in=new FileInputStream(f); FileOutputStream os=new FileOutputStream(out)) { byte[] b=new byte[8192]; int n; while((n=in.read(b))>0) os.write(b,0,n); }
            prefs.edit().putString("route_name_"+out.getName(), displayName(f)+" • copie").apply(); toast("Tournée dupliquée"); showHistory();
        } catch(Exception e){ toast("Impossible de dupliquer"); }
    }

    private void deleteRoute(File f) {
        new AlertDialog.Builder(this).setTitle("Supprimer la tournée ?").setMessage("Cette action supprimera définitivement le fichier GPX local.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{
            prefs.edit().remove("route_name_"+f.getName()).apply(); if(f.delete()) { toast("Tournée supprimée"); showHistory(); } else toast("Suppression impossible");
        }).show();
    }

    private void shareRoute(File f) {
        if(f==null || !f.exists()){ toast("Aucune tournée à partager"); return; }
        Intent i=new Intent(Intent.ACTION_SEND); i.setType("application/gpx+xml"); i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this,getPackageName()+".files",f)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i,"Partager la tournée"));
    }

    private void importGpx() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/gpx+xml","application/xml","text/xml","text/plain"}); startActivityForResult(i,IMPORT_GPX);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==IMPORT_GPX && resultCode==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData(); try(InputStream in=getContentResolver().openInputStream(uri)){
                File out=new File(routesDir(),"Import_"+System.currentTimeMillis()+".gpx"); try(FileOutputStream os=new FileOutputStream(out)){ byte[] b=new byte[8192]; int n; while((n=in.read(b))>0) os.write(b,0,n); }
                RouteSummary s=parseRoute(out); if(s.points.isEmpty()){ out.delete(); toast("Fichier GPX invalide ou sans trace"); } else { toast("Tournée importée"); showHistory(); }
            }catch(Exception e){ toast("Import impossible"); }
        }
    }

    private String displayName(File f){ String n=prefs.getString("route_name_"+f.getName(),null); if(n!=null&&!n.trim().isEmpty())return n; return f.getName().replace("RoutePilot_","Tournée ").replace("Import_","Import ").replace(".gpx","").replace('_',' '); }

    private void clearMapCache(){ new AlertDialog.Builder(this).setTitle("Vider le cache des cartes ?").setMessage("Les tuiles seront retéléchargées au prochain affichage.").setNegativeButton("Annuler",null).setPositiveButton("Vider",(d,w)->{ deleteRecursive(new File(getCacheDir(),"osmdroid")); Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(),"osmdroid")); Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(),"osmdroid/tiles")); toast("Cache vidé"); showSettings(); }).show(); }
    private void deleteRecursive(File f){ if(f==null||!f.exists())return; if(f.isDirectory()){ File[] c=f.listFiles(); if(c!=null)for(File x:c)deleteRecursive(x);} f.delete(); }
    private String cacheSize(){ long b=sizeOf(new File(getCacheDir(),"osmdroid")); return b<1024*1024 ? (b/1024)+" Ko" : String.format(Locale.FRANCE,"%.1f Mo",b/1048576.0); }
    private long sizeOf(File f){ if(f==null||!f.exists())return 0; if(f.isFile())return f.length(); long s=0; File[] c=f.listFiles(); if(c!=null)for(File x:c)s+=sizeOf(x); return s; }

    private void accentDialog(){ final String[] names={"Bleu","Violet","Vert","Orange","Cyan"}; new AlertDialog.Builder(this).setTitle("Couleur d’accent").setSingleChoiceItems(names,accentIndex(),(d,w)->{ prefs.edit().putString("accent_name",names[w]).apply(); d.dismiss(); toast("Couleur appliquée"); recreate(); }).show(); }
    private int accentIndex(){ String s=prefs.getString("accent_name","Bleu"); for(int i=0;i<new String[]{"Bleu","Violet","Vert","Orange","Cyan"}.length;i++)if(new String[]{"Bleu","Violet","Vert","Orange","Cyan"}[i].equals(s))return i; return 0; }
    private int accent(){ String s=prefs==null?"Bleu":prefs.getString("accent_name","Bleu"); if("Violet".equals(s))return PURPLE; if("Vert".equals(s))return GREEN; if("Orange".equals(s))return ORANGE; if("Cyan".equals(s))return CYAN; return BLUE; }

    private View choice(String title,String value,View.OnClickListener click){ LinearLayout r=row(); LinearLayout t=new LinearLayout(this); t.setOrientation(LinearLayout.VERTICAL); t.addView(label(title,15,Typeface.BOLD,Color.WHITE)); t.addView(label(value,11,Typeface.NORMAL,SECONDARY)); r.addView(t,new LinearLayout.LayoutParams(0,-2,1f)); r.addView(label("›",25,Typeface.NORMAL,SECONDARY)); r.setOnClickListener(click); return r; }
    private View toggle(String title,String subtitle,String key,boolean def){ LinearLayout r=row(); LinearLayout t=new LinearLayout(this); t.setOrientation(LinearLayout.VERTICAL); t.addView(label(title,15,Typeface.BOLD,Color.WHITE)); t.addView(label(subtitle,11,Typeface.NORMAL,SECONDARY)); r.addView(t,new LinearLayout.LayoutParams(0,-2,1f)); SwitchCompat sw=new SwitchCompat(this); sw.setChecked(prefs.getBoolean(key,def)); sw.setOnCheckedChangeListener((b,c)->{prefs.edit().putBoolean(key,c).apply(); if("keep_screen_on".equals(key))applyKeepScreenOn();}); r.addView(sw); return r; }
    private LinearLayout row(){ LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(15),dp(13),dp(13),dp(13)); r.setBackground(glass(GLASS_2,20)); return r; }
    private TextView section(String s){ TextView v=label(s,10,Typeface.BOLD,accent()); v.setPadding(dp(2),0,0,dp(8)); return v; }

    private void toggleSheet(){ collapsed=!collapsed; if(collapsed){ recordContent.animate().alpha(0f).setDuration(120).withEndAction(()->recordContent.setVisibility(View.GONE)).start(); collapseButton.setText("⌃"); } else { recordContent.setVisibility(View.VISIBLE); recordContent.setAlpha(0f); recordContent.animate().alpha(1f).setDuration(170).start(); collapseButton.setText("⌄"); } }

    private void clearPage(){ if(pageOverlay!=null){root.removeView(pageOverlay);pageOverlay=null;} if(planSheet!=null){root.removeView(planSheet);planSheet=null;} clearPreview(); }
    private void clearPreview(){ if(previewTrack!=null){map.getOverlays().remove(previewTrack);previewTrack=null;} map.getOverlays().removeIf(o->o instanceof Marker && "preview".equals(((Marker)o).getRelatedObject())); map.invalidate(); }
    private void clearRouteMarkers(){ map.getOverlays().removeIf(o->o instanceof Marker && "live".equals(((Marker)o).getRelatedObject())); map.invalidate(); }

    private void addMarker(GeoPoint p,String title,String badge,int color,int order){ Marker m=new Marker(map); m.setPosition(p); m.setTitle(title); m.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER); m.setIcon(markerDrawable(badge,color)); m.setRelatedObject(selectedTab.equals("map")?"live":"preview"); map.getOverlays().add(m); }
    private BitmapDrawable markerDrawable(String text,int color){ int s=dp(38); Bitmap b=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); c.drawCircle(s/2f,s/2f,s*.45f,p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.WHITE); c.drawCircle(s/2f,s/2f,s*.40f,p); p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(text.length()>1?10:13)); Paint.FontMetrics fm=p.getFontMetrics(); c.drawText(text,s/2f,s/2f-(fm.ascent+fm.descent)/2f,p); return new BitmapDrawable(getResources(),b); }

    private void recenter(){ if(lastLocation!=null){map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()));map.getController().setZoom(18.5);}else toast("Position GPS en attente"); }
    private void ensureLocationPermission(){ if(hasLocationPermission())enableLocation(); else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION); }
    private boolean hasLocationPermission(){ return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED; }
    private void enableLocation(){ locationOverlay.enableMyLocation(); startGpsUpdates(); }
    private void startGpsUpdates(){ if(!hasLocationPermission())return; try{ locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,this);}catch(SecurityException ignored){} }
    private void stopGpsUpdates(){ try{locationManager.removeUpdates(this);}catch(SecurityException ignored){} }
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==LOCATION_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)enableLocation();else new AlertDialog.Builder(this).setTitle("Localisation requise").setMessage("RoutePilot a besoin de la localisation précise pour enregistrer une tournée.").setNegativeButton("Plus tard",null).setPositiveButton("Réglages",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))).show();}

    private void applyKeepScreenOn(){ if(prefs.getBoolean("keep_screen_on",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
    private void setRecordingButtons(boolean e){ reverseButton.setEnabled(e);twoSidesButton.setEnabled(e);undoButton.setEnabled(e);reverseButton.setAlpha(e?1f:.35f);twoSidesButton.setAlpha(e?1f:.35f);undoButton.setAlpha(e?1f:.35f); }
    private void updateTab(String tab){ styleNav(navMap,"map".equals(tab));styleNav(navHistory,"history".equals(tab));styleNav(navSettings,"settings".equals(tab)); }
    private void styleNav(TextView t,boolean sel){t.setTextColor(sel?Color.WHITE:Color.argb(145,255,255,255));t.setBackground(sel?glass(Color.argb(100,Color.red(accent()),Color.green(accent()),Color.blue(accent())),18):null);}

    private LinearLayout pageBase(){ LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(18),dp(60),dp(18),dp(94));p.setBackgroundColor(BG);return p; }
    private TextView nav(String icon,String text){ TextView v=label(icon+"\n"+text,11,Typeface.BOLD,SECONDARY);v.setGravity(Gravity.CENTER);return v; }
    private TextView chip(String s,int color){TextView v=label(s,11,Typeface.BOLD,Color.WHITE);v.setGravity(Gravity.CENTER);v.setPadding(dp(11),0,dp(11),0);v.setBackground(glass(color,16));return v;}
    private TextView pill(String s,int color,float size){TextView v=label(s,size,Typeface.BOLD,Color.WHITE);v.setGravity(Gravity.CENTER);v.setBackground(glass(color,18));return v;}
    private TextView circle(String s,int color){TextView v=label(s,16,Typeface.BOLD,Color.WHITE);v.setGravity(Gravity.CENTER);v.setBackground(glass(color,18));return v;}
    private TextView statValue(String value,String tag){TextView v=label(value,15,Typeface.BOLD,Color.WHITE);v.setTag(tag);return v;}
    private View statCell(TextView value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(label((String)value.getTag(),9,Typeface.BOLD,Color.argb(135,255,255,255)));b.addView(value);return b;}
    private View summaryStat(String title,String value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(label(title,9,Typeface.BOLD,Color.argb(135,255,255,255)));b.addView(label(value,14,Typeface.BOLD,Color.WHITE));return b;}
    private TextView label(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private GradientDrawable glass(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),Color.argb(34,255,255,255));return d;}
    private void press(View v){v.animate().scaleX(.97f).scaleY(.97f).setDuration(60).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(130).setInterpolator(new DecelerateInterpolator()).start()).start();}
    private void haptic(View v){if(prefs.getBoolean("haptics",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}
    private void fadeIn(View v){v.setAlpha(0f);v.animate().alpha(1f).setDuration(180).start();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private FrameLayout.LayoutParams topParams(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(70),Gravity.TOP);p.setMargins(dp(12),dp(34),dp(12),0);return p;}
    private FrameLayout.LayoutParams sheetParams(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),dp(87));return p;}
    private FrameLayout.LayoutParams dockParams(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(70),Gravity.BOTTOM);p.setMargins(dp(24),0,dp(24),dp(10));return p;}
    private FrameLayout.LayoutParams pageParams(){return new FrameLayout.LayoutParams(-1,-1);}
    private FrameLayout.LayoutParams planParams(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(330),Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),dp(87));return p;}
    private LinearLayout.LayoutParams marginBottom(int d){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(d);return p;}
    private LinearLayout.LayoutParams topMargin(int d){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(d);return p;}
    private LinearLayout.LayoutParams weighted(float w,int mr){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),w);p.rightMargin=dp(mr);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private String formatDate(long ms){return new SimpleDateFormat("EEEE d MMMM • HH:mm",Locale.FRANCE).format(new Date(ms));}
    private String formatDurationExact(long ms){long t=Math.max(0,ms/1000);long h=t/3600,m=(t%3600)/60,s=t%60;return h>0?String.format(Locale.FRANCE,"%02d:%02d:%02d",h,m,s):String.format(Locale.FRANCE,"%02d:%02d",m,s);}
    private String formatDistance(double m){return m>=1000?String.format(Locale.FRANCE,"%.2f km",m/1000d):String.format(Locale.FRANCE,"%.0f m",m);}
    private String formatSpeed(RouteSummary s){if(s.durationMs<=0)return "—";double kmh=(s.distanceM/1000d)/(s.durationMs/3600000d);return String.format(Locale.FRANCE,"%.1f km/h",kmh);}
    private static String iso(long ms){return Instant.ofEpochMilli(ms).toString();}
    private static long time(String s){try{return Instant.parse(s).toEpochMilli();}catch(DateTimeParseException e){return 0;}}
    private static double dbl(String s){try{return Double.parseDouble(s);}catch(Exception e){return 0;}}
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}

    @Override protected void onResume(){super.onResume();map.onResume();if(hasLocationPermission())startGpsUpdates();}
    @Override protected void onPause(){super.onPause();map.onPause();}
    @Override protected void onDestroy(){timerHandler.removeCallbacks(timerTick);stopGpsUpdates();if(locationOverlay!=null)locationOverlay.disableMyLocation();super.onDestroy();}

    private static class RoutePoint { final double lat,lon; final Double altitude; final float accuracy; final long time; RoutePoint(double a,double b,Double c,float d,long e){lat=a;lon=b;altitude=c;accuracy=d;time=e;} }
    private static class RouteEvent { final String type,label; final double lat,lon; final float accuracy; final long time; RouteEvent(String a,String b,double c,double d,float e,long f){type=a;label=b;lat=c;lon=d;accuracy=e;time=f;} }
    private static class RouteSummary { final File file; final List<RoutePoint> points=new ArrayList<>(); final List<RouteEvent> events=new ArrayList<>(); long firstTime,lastTime,durationMs; double distanceM; RouteSummary(File f){file=f;} }

    private interface TextConsumer { void accept(String s); }
    private static class SimpleWatcher implements android.text.TextWatcher {
        final TextConsumer c; SimpleWatcher(TextConsumer c){this.c=c;}
        public void beforeTextChanged(CharSequence s,int st,int c1,int a){}
        public void onTextChanged(CharSequence s,int st,int b,int c1){c.accept(s.toString());}
        public void afterTextChanged(android.text.Editable e){}
    }
}
