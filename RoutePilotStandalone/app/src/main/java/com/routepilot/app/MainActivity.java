package com.routepilot.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int LOCATION_PERMISSION = 42;
    private static final int BLUE = Color.rgb(10, 132, 255);
    private static final int GREEN = Color.rgb(48, 209, 88);
    private static final int ORANGE = Color.rgb(255, 159, 10);
    private static final int RED = Color.rgb(255, 69, 58);
    private static final int GLASS = Color.argb(225, 25, 26, 30);
    private static final int GLASS_BUTTON = Color.argb(235, 55, 56, 62);

    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private LocationManager locationManager;
    private Polyline liveTrack;

    private TextView status;
    private TextView recordButton;
    private TextView reverseButton;
    private TextView twoSidesButton;
    private TextView shareButton;

    private boolean recording;
    private long startedAt;
    private Location lastLocation;
    private File lastSavedRoute;

    private final List<RoutePoint> routePoints = new ArrayList<>();
    private final List<RouteEvent> routeEvents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File osmdroidBase = new File(getCacheDir(), "osmdroid");
        File tileCache = new File(osmdroidBase, "tiles");
        if (!tileCache.exists()) {
            tileCache.mkdirs();
        }
        Configuration.getInstance().setOsmdroidBasePath(osmdroidBase);
        Configuration.getInstance().setOsmdroidTileCache(tileCache);
        Configuration.getInstance().setUserAgentValue("RoutePilot/0.1 Android");
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(17, 18, 20));

        mapView = new MapView(this);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setUseDataConnection(true);
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);
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
        liveTrack.getOutlinePaint().setColor(BLUE);
        liveTrack.getOutlinePaint().setStrokeWidth(dp(6));
        mapView.getOverlays().add(liveTrack);

        root.addView(buildTopBar(), topParams());
        root.addView(buildBottomPanel(), bottomParams());
        setContentView(root);

        ensureLocationPermission();
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(12), dp(12));
        bar.setBackground(roundRect(GLASS, 26));
        bar.setElevation(dp(12));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView brand = text("RoutePilot", 20, Typeface.BOLD, Color.WHITE);
        TextView subtitle = text("Enregistreur de tournée", 12, Typeface.NORMAL, Color.argb(180,255,255,255));
        titles.addView(brand);
        titles.addView(subtitle);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(titles, titleParams);

        TextView recenter = pill("◎", BLUE);
        recenter.setTextSize(22);
        recenter.setOnClickListener(v -> recenter());
        bar.addView(recenter, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return bar;
    }

    private View buildBottomPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(15), dp(16), dp(16));
        panel.setBackground(roundRect(GLASS, 30));
        panel.setElevation(dp(18));

        TextView section = text("TOURNÉE", 11, Typeface.BOLD, BLUE);
        panel.addView(section);

        status = text("GPS en attente…", 13, Typeface.NORMAL, Color.argb(190,255,255,255));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(5);
        statusParams.bottomMargin = dp(12);
        panel.addView(status, statusParams);

        recordButton = pill("Commencer l’enregistrement", BLUE);
        recordButton.setOnClickListener(v -> toggleRecording());
        panel.addView(recordButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout eventRow = new LinearLayout(this);
        eventRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);
        panel.addView(eventRow, rowParams);

        reverseButton = pill("↶  Marche arrière", GLASS_BUTTON);
        reverseButton.setOnClickListener(v -> addEvent("REVERSE", "Marche arrière", ORANGE));
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, dp(54), 1f);
        half1.rightMargin = dp(5);
        eventRow.addView(reverseButton, half1);

        twoSidesButton = pill("⇆  2 côtés", GLASS_BUTTON);
        twoSidesButton.setOnClickListener(v -> addEvent("TWO_SIDES", "2 côtés", GREEN));
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(54), 1f);
        half2.leftMargin = dp(5);
        eventRow.addView(twoSidesButton, half2);

        shareButton = pill("Partager la dernière tournée", GLASS_BUTTON);
        shareButton.setOnClickListener(v -> shareLastRoute());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        shareParams.topMargin = dp(10);
        panel.addView(shareButton, shareParams);

        setRecordingControls(false);
        shareButton.setEnabled(false);
        shareButton.setAlpha(0.35f);
        return panel;
    }

    private void toggleRecording() {
        if (recording) {
            finishRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (!hasFineLocation()) {
            ensureLocationPermission();
            return;
        }
        routePoints.clear();
        routeEvents.clear();
        liveTrack.setPoints(new ArrayList<>());
        startedAt = System.currentTimeMillis();
        recording = true;
        recordButton.setText("Terminer et sauvegarder");
        recordButton.setBackground(roundRect(RED, 18));
        status.setText("Enregistrement GPS en cours • 0 point");
        setRecordingControls(true);
        startGpsUpdates();
        Toast.makeText(this, "Tournée démarrée", Toast.LENGTH_SHORT).show();
    }

    private void finishRecording() {
        if (!recording) return;
        recording = false;
        stopGpsUpdates();
        lastSavedRoute = saveGpx();
        recordButton.setText("Commencer une nouvelle tournée");
        recordButton.setBackground(roundRect(BLUE, 18));
        setRecordingControls(false);
        if (lastSavedRoute != null) {
            status.setText("Tournée sauvegardée • " + routePoints.size() + " points • " + routeEvents.size() + " repères");
            shareButton.setEnabled(true);
            shareButton.setAlpha(1f);
            Toast.makeText(this, "Tournée sauvegardée", Toast.LENGTH_SHORT).show();
        } else {
            status.setText("Erreur pendant la sauvegarde");
        }
    }

    private void addEvent(String type, String label, int color) {
        if (!recording) {
            Toast.makeText(this, "Commence d’abord une tournée", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastLocation == null) {
            Toast.makeText(this, "Position GPS indisponible", Toast.LENGTH_SHORT).show();
            return;
        }
        RouteEvent event = new RouteEvent(type, label, lastLocation.getLatitude(), lastLocation.getLongitude(), lastLocation.getAccuracy(), System.currentTimeMillis());
        routeEvents.add(event);
        org.osmdroid.views.overlay.Marker marker = new org.osmdroid.views.overlay.Marker(mapView);
        marker.setPosition(new GeoPoint(event.lat, event.lon));
        marker.setTitle(label);
        marker.setSubDescription("RoutePilot");
        marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(marker);
        mapView.invalidate();
        status.setText(label + " ajouté • " + routeEvents.size() + " repère" + (routeEvents.size() > 1 ? "s" : ""));
        Toast.makeText(this, label + " enregistré", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        lastLocation = location;
        GeoPoint gp = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (recording && shouldAppend(location)) {
            routePoints.add(new RoutePoint(location.getLatitude(), location.getLongitude(), location.hasAltitude() ? location.getAltitude() : null, location.getAccuracy(), location.getTime() > 0 ? location.getTime() : System.currentTimeMillis()));
            List<GeoPoint> points = new ArrayList<>(liveTrack.getActualPoints());
            points.add(gp);
            liveTrack.setPoints(points);
            status.setText("Enregistrement GPS en cours • " + routePoints.size() + " points • ±" + Math.round(location.getAccuracy()) + " m");
        } else if (!recording) {
            status.setText("GPS prêt • précision ±" + Math.round(location.getAccuracy()) + " m");
        }
        if (mapView.getMapCenter() == null || recording) {
            mapView.getController().animateTo(gp);
        }
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
            File dir = new File(getFilesDir(), "routes");
            if (!dir.exists() && !dir.mkdirs()) return null;
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE).format(new Date(startedAt));
            File file = new File(dir, "RoutePilot_" + stamp + ".gpx");
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<gpx version=\"1.1\" creator=\"RoutePilot\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:rp=\"https://routepilot.local/gpx/1\">\n");
            xml.append("  <metadata><name>").append(escape("Tournée " + stamp)).append("</name></metadata>\n");
            for (RouteEvent event : routeEvents) {
                xml.append("  <wpt lat=\"").append(event.lat).append("\" lon=\"").append(event.lon).append("\">\n");
                xml.append("    <time>").append(iso(event.time)).append("</time>\n");
                xml.append("    <name>").append(escape(event.label)).append("</name>\n");
                xml.append("    <type>RoutePilot</type>\n");
                xml.append("    <extensions><rp:event>").append(event.type).append("</rp:event><rp:accuracy>").append(event.accuracy).append("</rp:accuracy></extensions>\n");
                xml.append("  </wpt>\n");
            }
            xml.append("  <trk><name>").append(escape("RoutePilot " + stamp)).append("</name><trkseg>\n");
            for (RoutePoint point : routePoints) {
                xml.append("    <trkpt lat=\"").append(point.lat).append("\" lon=\"").append(point.lon).append("\">\n");
                if (point.altitude != null) xml.append("      <ele>").append(point.altitude).append("</ele>\n");
                xml.append("      <time>").append(iso(point.time)).append("</time>\n");
                xml.append("      <extensions><rp:accuracy>").append(point.accuracy).append("</rp:accuracy></extensions>\n");
                xml.append("    </trkpt>\n");
            }
            xml.append("  </trkseg></trk>\n</gpx>\n");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(xml.toString().getBytes(StandardCharsets.UTF_8));
            }
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void shareLastRoute() {
        if (lastSavedRoute == null || !lastSavedRoute.exists()) {
            Toast.makeText(this, "Aucune tournée à partager", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/gpx+xml");
        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getPackageName() + ".files", lastSavedRoute));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Partager la tournée"));
    }

    private void recenter() {
        if (lastLocation != null) {
            mapView.getController().animateTo(new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude()));
            mapView.getController().setZoom(18.5);
        } else {
            Toast.makeText(this, "Position GPS en attente", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureLocationPermission() {
        if (hasFineLocation()) {
            enableLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
        }
    }

    private boolean hasFineLocation() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableLocation() {
        locationOverlay.enableMyLocation();
        locationOverlay.enableFollowLocation();
        startGpsUpdates();
    }

    private void startGpsUpdates() {
        if (!hasFineLocation()) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
        } catch (SecurityException ignored) {
        }
    }

    private void stopGpsUpdates() {
        if (!hasFineLocation()) return;
        try {
            locationManager.removeUpdates(this);
            if (!recording) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500L, 0f, this);
            }
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION && hasFineLocation()) {
            enableLocation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        if (hasFineLocation()) startGpsUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!recording && hasFineLocation()) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
            }
        }
        if (mapView != null) mapView.onPause();
    }

    private void setRecordingControls(boolean enabled) {
        reverseButton.setEnabled(enabled);
        twoSidesButton.setEnabled(enabled);
        reverseButton.setAlpha(enabled ? 1f : 0.35f);
        twoSidesButton.setAlpha(enabled ? 1f : 0.35f);
    }

    private TextView pill(String label, int color) {
        TextView v = text(label, 15, Typeface.BOLD, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setBackground(roundRect(color, 18));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", style));
        return v;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private FrameLayout.LayoutParams topParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        p.setMargins(dp(14), dp(42), dp(14), 0);
        return p;
    }

    private FrameLayout.LayoutParams bottomParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        p.setMargins(dp(14), 0, dp(14), dp(18));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String iso(long time) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new Date(time));
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static class RoutePoint {
        final double lat;
        final double lon;
        final Double altitude;
        final float accuracy;
        final long time;
        RoutePoint(double lat, double lon, Double altitude, float accuracy, long time) {
            this.lat = lat;
            this.lon = lon;
            this.altitude = altitude;
            this.accuracy = accuracy;
            this.time = time;
        }
    }

    private static class RouteEvent {
        final String type;
        final String label;
        final double lat;
        final double lon;
        final float accuracy;
        final long time;
        RouteEvent(String type, String label, double lat, double lon, float accuracy, long time) {
            this.type = type;
            this.label = label;
            this.lat = lat;
            this.lon = lon;
            this.accuracy = accuracy;
            this.time = time;
        }
    }
}
