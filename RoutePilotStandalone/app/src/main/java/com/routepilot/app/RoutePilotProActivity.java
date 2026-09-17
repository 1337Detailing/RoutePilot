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
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
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

public class RoutePilotProActivity extends AppCompatActivity implements LocationListener {
    private static final int LOCATION_PERMISSION = 42;
    private static final int BLUE = Color.rgb(10,132,255), GREEN = Color.rgb(48,209,88), ORANGE = Color.rgb(255,159,10), RED = Color.rgb(255,69,58), PURPLE = Color.rgb(191,90,242), CYAN = Color.rgb(100,210,255);
    private static final int BG = Color.rgb(10,11,14), GLASS = Color.argb(232,26,27,32), GLASS2 = Color.argb(242,39,40,46), MUTED = Color.argb(175,255,255,255);

    private final List<Point> points = new ArrayList<>();
    private final List<Flag> flags = new ArrayList<>();
    private final Handler timer = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private MapView map;
    private MyLocationNewOverlay me;
    private LocationManager lm;
    private Polyline liveTrack, previewTrack;
    private SharedPreferences prefs;
    private Location lastLocation;
    private boolean recording, paused, collapsed;
    private long startedAt, pauseStarted, pausedTotal;
    private double liveDistance;
    private File lastRoute;

    private View topBar, recordSheet, dock, page, planSheet;
    private LinearLayout recordBody;
    private TextView gpsChip, timeText, distanceText, flagsText, pointsText, recordBtn, reverseBtn, sidesBtn, pauseBtn, undoBtn, collapseBtn;
    private TextView navMap, navHistory, navSettings;

    private final Runnable tick = new Runnable() {
        @Override public void run() { refreshRecordingUi(); if (recording) timer.postDelayed(this, 500); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("routepilot", MODE_PRIVATE);
        lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (prefs.getBoolean("keep_screen_on", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Configuration.getInstance().setUserAgentValue(getPackageName()+"/0.4 RoutePilot");
        Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(),"osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(),"osmdroid/tiles"));
        MapsForgeTileSource.createInstance(getApplication());

        root = new FrameLayout(this); root.setBackgroundColor(BG);
        buildMap(); buildChrome(); setContentView(root);
        ensureLocation();
    }

    private void buildMap() {
        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setUseDataConnection(true);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(3.0); map.setMaxZoomLevel(20.0); map.getController().setZoom(18.2);
        map.setHorizontalMapRepetitionEnabled(false); map.setVerticalMapRepetitionEnabled(false);
        root.addView(map,new FrameLayout.LayoutParams(-1,-1));
        me = new MyLocationNewOverlay(new GpsMyLocationProvider(this),map); me.setDrawAccuracyEnabled(true); map.getOverlays().add(me);
        liveTrack = new Polyline(); liveTrack.getOutlinePaint().setStrokeWidth(dp(7)); liveTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND); liveTrack.getOutlinePaint().setColor(accent()); map.getOverlays().add(liveTrack);
        restoreOfflineMapIfAvailable();
    }

    private void buildChrome() {
        topBar = topBar(); recordSheet = recordSheet(); dock = dock();
        root.addView(topBar,topLp()); root.addView(recordSheet,sheetLp()); root.addView(dock,dockLp());
        selectTab("map");
    }

    private View topBar() {
        LinearLayout v=new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(16),dp(11),dp(10),dp(11)); v.setBackground(card(GLASS,28)); v.setElevation(dp(15));
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.addView(text("RoutePilot",21,Typeface.BOLD,Color.WHITE)); brand.addView(text("Tournées professionnelles",11,Typeface.NORMAL,MUTED)); v.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        gpsChip=chip("GPS…",GLASS2); LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-2,dp(38)); gp.rightMargin=dp(7); v.addView(gpsChip,gp);
        TextView locate=circle("◎",accent()); locate.setOnClickListener(x->{press(x);recenter();}); v.addView(locate,new LinearLayout.LayoutParams(dp(42),dp(42)));
        return v;
    }

    private View recordSheet() {
        LinearLayout s=new LinearLayout(this); s.setOrientation(LinearLayout.VERTICAL); s.setPadding(dp(15),dp(11),dp(15),dp(14)); s.setBackground(card(GLASS,30)); s.setElevation(dp(20));
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.addView(text("TOURNÉE",10,Typeface.BOLD,accent())); timeText=text("Prêt à enregistrer",14,Typeface.BOLD,Color.WHITE); titles.addView(timeText); head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        collapseBtn=circle("⌄",GLASS2); collapseBtn.setOnClickListener(v->toggleCollapse()); head.addView(collapseBtn,new LinearLayout.LayoutParams(dp(40),dp(40))); s.addView(head);
        recordBody=new LinearLayout(this); recordBody.setOrientation(LinearLayout.VERTICAL); s.addView(recordBody);

        LinearLayout stats=new LinearLayout(this); stats.setPadding(0,dp(11),0,dp(11));
        distanceText=stat("0 m","DISTANCE"); flagsText=stat("0","REPÈRES"); pointsText=stat("0","POINTS GPS");
        stats.addView(statCell(distanceText),new LinearLayout.LayoutParams(0,-2,1)); stats.addView(statCell(flagsText),new LinearLayout.LayoutParams(0,-2,1)); stats.addView(statCell(pointsText),new LinearLayout.LayoutParams(0,-2,1)); recordBody.addView(stats);

        recordBtn=pill("Commencer l’enregistrement",accent(),16); recordBtn.setOnClickListener(v->{press(v);if(recording)finishRecording();else startRecording();}); recordBody.addView(recordBtn,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout business=new LinearLayout(this); business.setPadding(0,dp(9),0,0);
        reverseBtn=pill("↶  Marche arrière",GLASS2,13); sidesBtn=pill("⇆  2 côtés",GLASS2,13);
        reverseBtn.setOnClickListener(v->flag("REVERSE","Marche arrière",ORANGE)); sidesBtn.setOnClickListener(v->flag("TWO_SIDES","2 côtés",GREEN));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(52),1); a.rightMargin=dp(5); LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(0,dp(52),1); c.leftMargin=dp(5); business.addView(reverseBtn,a);business.addView(sidesBtn,c);recordBody.addView(business);

        LinearLayout tools=new LinearLayout(this); tools.setPadding(0,dp(8),0,0); undoBtn=pill("↩ Annuler",GLASS2,12); pauseBtn=pill("Ⅱ Pause",GLASS2,12); undoBtn.setOnClickListener(v->undoFlag()); pauseBtn.setOnClickListener(v->togglePause()); LinearLayout.LayoutParams t1=new LinearLayout.LayoutParams(0,dp(44),1);t1.rightMargin=dp(5);LinearLayout.LayoutParams t2=new LinearLayout.LayoutParams(0,dp(44),1);t2.leftMargin=dp(5);tools.addView(undoBtn,t1);tools.addView(pauseBtn,t2);recordBody.addView(tools);
        enableWorkButtons(false); return s;
    }

    private View dock(){ LinearLayout d=new LinearLayout(this);d.setGravity(Gravity.CENTER);d.setPadding(dp(7),dp(6),dp(7),dp(6));d.setBackground(card(Color.argb(248,22,23,28),26));d.setElevation(dp(24));navMap=nav("⌖","Carte");navHistory=nav("≡","Historique");navSettings=nav("⚙","Réglages");navMap.setOnClickListener(v->showMap());navHistory.setOnClickListener(v->showHistory());navSettings.setOnClickListener(v->showSettings());d.addView(navMap,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navHistory,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navSettings,new LinearLayout.LayoutParams(0,dp(58),1));return d; }

    private void showMap(){clearPage();map.setVisibility(View.VISIBLE);topBar.setVisibility(View.VISIBLE);recordSheet.setVisibility(View.VISIBLE);selectTab("map");}
    private void showHistory(){clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);page=historyPage();root.addView(page,pageLp());selectTab("history");fade(page);}
    private void showSettings(){clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);page=settingsPage();root.addView(page,pageLp());selectTab("settings");fade(page);}

    private View historyPage(){
        LinearLayout p=pageBase(); LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout hb=new LinearLayout(this);hb.setOrientation(LinearLayout.VERTICAL);List<File> files=routeFiles();hb.addView(text("Historique",30,Typeface.BOLD,Color.WHITE));hb.addView(text(files.size()+" tournée(s) • plans • statistiques",12,Typeface.NORMAL,MUTED));head.addView(hb,new LinearLayout.LayoutParams(0,-2,1));TextView plus=circle("＋",accent());plus.setOnClickListener(v->showMap());head.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(46)));p.addView(head);
        ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(18);p.addView(sc,sp);
        if(files.isEmpty()){LinearLayout e=new LinearLayout(this);e.setOrientation(LinearLayout.VERTICAL);e.setGravity(Gravity.CENTER);e.setPadding(0,dp(90),0,0);e.addView(text("⌁",46,Typeface.NORMAL,Color.argb(100,255,255,255)));e.addView(text("Aucune tournée",19,Typeface.BOLD,Color.WHITE));e.addView(text("Enregistre ta première tournée depuis la carte.",12,Typeface.NORMAL,MUTED));list.addView(e);}else for(File f:files){Summary s=parse(f);list.addView(historyCard(s),bottom(12));}
        return p;
    }

    private View historyCard(Summary s){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(14));c.setBackground(card(GLASS2,23));
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text(name(s.file),17,Typeface.BOLD,Color.WHITE));names.addView(text(date(s.file.lastModified()),11,Typeface.NORMAL,MUTED));h.addView(names,new LinearLayout.LayoutParams(0,-2,1));TextView menu=circle("•••",Color.argb(75,255,255,255));menu.setOnClickListener(v->routeMenu(s));h.addView(menu,new LinearLayout.LayoutParams(dp(42),dp(42)));c.addView(h);
        LinearLayout st=new LinearLayout(this);st.setPadding(0,dp(14),0,dp(12));st.addView(summaryStat("DURÉE",duration(s.duration)),new LinearLayout.LayoutParams(0,-2,1));st.addView(summaryStat("DISTANCE",distance(s.distance)),new LinearLayout.LayoutParams(0,-2,1));st.addView(summaryStat("REPÈRES",String.valueOf(s.flags.size())),new LinearLayout.LayoutParams(0,-2,1));c.addView(st);
        LinearLayout actions=new LinearLayout(this);TextView plan=pill("Voir le plan",accent(),12);TextView share=pill("Partager",GLASS2,12);plan.setOnClickListener(v->openPlan(s));share.setOnClickListener(v->share(s.file));LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(42),1.2f);p1.rightMargin=dp(5);LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(42),.8f);p2.leftMargin=dp(5);actions.addView(plan,p1);actions.addView(share,p2);c.addView(actions);c.setOnClickListener(v->openPlan(s));return c;
    }

    private void routeMenu(Summary s){new AlertDialog.Builder(this).setTitle(name(s.file)).setItems(new String[]{"Voir le plan","Renommer","Partager","Supprimer"},(d,w)->{if(w==0)openPlan(s);else if(w==1)rename(s.file);else if(w==2)share(s.file);else delete(s.file);}).show();}

    private void openPlan(Summary s){if(s.points.isEmpty()){toast("Trace vide");return;}if(page!=null)page.setVisibility(View.GONE);map.setVisibility(View.VISIBLE);clearPreview();previewTrack=new Polyline();previewTrack.getOutlinePaint().setColor(accent());previewTrack.getOutlinePaint().setStrokeWidth(dp(8));previewTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);List<GeoPoint> pts=new ArrayList<>();for(Point x:s.points)pts.add(new GeoPoint(x.lat,x.lon));previewTrack.setPoints(pts);map.getOverlays().add(previewTrack);addMarker(pts.get(0),"D",GREEN,"preview");for(int i=0;i<s.flags.size();i++){Flag f=s.flags.get(i);addMarker(new GeoPoint(f.lat,f.lon),String.valueOf(i+1),"REVERSE".equals(f.type)?ORANGE:GREEN,"preview");}addMarker(pts.get(pts.size()-1),"A",RED,"preview");map.zoomToBoundingBox(previewTrack.getBounds(),true,dp(74));
        LinearLayout sh=new LinearLayout(this);sh.setOrientation(LinearLayout.VERTICAL);sh.setPadding(dp(15),dp(12),dp(15),dp(14));sh.setBackground(card(Color.argb(248,25,26,31),29));sh.setElevation(dp(24));LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);LinearLayout title=new LinearLayout(this);title.setOrientation(LinearLayout.VERTICAL);title.addView(text(name(s.file),17,Typeface.BOLD,Color.WHITE));title.addView(text(duration(s.duration)+" • "+distance(s.distance)+" • "+s.flags.size()+" repères",11,Typeface.NORMAL,MUTED));h.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView x=circle("×",GLASS2);x.setOnClickListener(v->closePlan());h.addView(x,new LinearLayout.LayoutParams(dp(40),dp(40)));sh.addView(h);ScrollView scroll=new ScrollView(this);LinearLayout timeline=new LinearLayout(this);timeline.setOrientation(LinearLayout.VERTICAL);timeline.addView(timeline("D","Départ","Début de tournée",GREEN));for(int i=0;i<s.flags.size();i++){Flag f=s.flags.get(i);timeline.addView(timeline(String.valueOf(i+1),f.label,f.time>0&&s.first>0?"+"+duration(f.time-s.first):"Repère métier","REVERSE".equals(f.type)?ORANGE:GREEN));}timeline.addView(timeline("A","Arrivée","Fin de tournée",RED));scroll.addView(timeline);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,0,1);sl.topMargin=dp(8);sh.addView(scroll,sl);planSheet=sh;root.addView(sh,planLp());fade(sh);
    }

    private View timeline(String badge,String title,String sub,int color){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(7),0,dp(7));TextView b=circle(badge,color);r.addView(b,new LinearLayout.LayoutParams(dp(34),dp(34)));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,13,Typeface.BOLD,Color.WHITE));t.addView(text(sub,11,Typeface.NORMAL,MUTED));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(10);r.addView(t,lp);return r;}
    private void closePlan(){if(planSheet!=null){root.removeView(planSheet);planSheet=null;}clearPreview();map.setVisibility(View.GONE);if(page!=null)page.setVisibility(View.VISIBLE);}

    private View settingsPage(){
        LinearLayout p=pageBase();p.addView(text("Réglages",30,Typeface.BOLD,Color.WHITE));TextView sub=text("Personnalisation • GPS • cartes hors ligne",12,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams spl=new LinearLayout.LayoutParams(-1,-2);spl.bottomMargin=dp(18);p.addView(sub,spl);ScrollView sc=new ScrollView(this);LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);sc.addView(c);p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        c.addView(section("APPARENCE"));c.addView(choice("Couleur d’accent",prefs.getString("accent","Bleu"),v->accentDialog()),bottom(9));c.addView(toggle("Interface compacte","Réduit la taille du panneau d’enregistrement","compact",false),bottom(9));
        c.addView(section("GPS & CONFORT"),top(12));c.addView(toggle("Suivre ma position","Recentre pendant l’enregistrement","follow",true),bottom(9));c.addView(toggle("Écran toujours allumé","Évite la mise en veille","keep_screen_on",true),bottom(9));c.addView(toggle("Retour haptique","Vibration légère sur les actions","haptic",true),bottom(9));
        c.addView(section("CARTES HORS LIGNE • FRANCE"),top(12));c.addView(infoCard("Mode online fiable","OpenStreetMap standard sans clé API. Le message APIKEYREQUIRED est supprimé : RoutePilot n’utilise plus de fournisseur tiers nécessitant une clé."),bottom(9));for(FranceOfflineManager.Pack pack:FranceOfflineManager.PACKS)c.addView(offlineRow(pack),bottom(8));
        c.addView(section("STOCKAGE"),top(12));c.addView(infoCard("Cartes installées",bytes(FranceOfflineManager.installedBytes(this))+" • Les cartes régionales restent séparées de l’APK pour éviter une application de plusieurs gigaoctets."),bottom(20));return p;
    }

    private View offlineRow(FranceOfflineManager.Pack pack){LinearLayout r=row();LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(text(pack.label,15,Typeface.BOLD,Color.WHITE));boolean ok=FranceOfflineManager.isInstalled(this,pack);labels.addView(text(ok?"Installée • "+bytes(FranceOfflineManager.file(this,pack).length()):"≈ "+pack.sizeMb+" Mo • vectorielle",11,Typeface.NORMAL,ok?GREEN:MUTED));r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView action=pill(ok?"Activer":"Télécharger",ok?GREEN:accent(),11);action.setPadding(dp(12),0,dp(12),0);action.setOnClickListener(v->{if(ok)activateOffline(pack);else downloadPack(pack);});r.addView(action,new LinearLayout.LayoutParams(-2,dp(38)));return r;}
    private void downloadPack(FranceOfflineManager.Pack p){try{FranceOfflineManager.download(this,p);toast("Téléchargement de "+p.label+" lancé");}catch(Exception e){toast("Téléchargement impossible");}}
    private void activateOffline(FranceOfflineManager.Pack p){try{File f=FranceOfflineManager.file(this,p);MapsForgeTileSource src=MapsForgeTileSource.createFromFiles(new File[]{f});MapsForgeTileProvider provider=new MapsForgeTileProvider(new SimpleRegisterReceiver(this),src,null);map.setTileProvider(provider);map.setUseDataConnection(false);prefs.edit().putString("offline_pack",p.id).apply();toast(p.label+" activée hors ligne");showMap();}catch(Exception e){toast("Carte hors ligne illisible");}}
    private void restoreOfflineMapIfAvailable(){String id=prefs.getString("offline_pack",null);if(id==null)return;for(FranceOfflineManager.Pack p:FranceOfflineManager.PACKS)if(p.id.equals(id)&&FranceOfflineManager.isInstalled(this,p)){map.postDelayed(()->activateOfflineSilent(p),150);break;}}
    private void activateOfflineSilent(FranceOfflineManager.Pack p){try{MapsForgeTileSource src=MapsForgeTileSource.createFromFiles(new File[]{FranceOfflineManager.file(this,p)});map.setTileProvider(new MapsForgeTileProvider(new SimpleRegisterReceiver(this),src,null));map.setUseDataConnection(false);}catch(Exception e){map.setTileSource(TileSourceFactory.MAPNIK);map.setUseDataConnection(true);prefs.edit().remove("offline_pack").apply();}}

    private void startRecording(){if(!hasLocation()){ensureLocation();return;}points.clear();flags.clear();liveDistance=0;liveTrack.setPoints(new ArrayList<>());clearLiveMarkers();recording=true;paused=false;startedAt=System.currentTimeMillis();pausedTotal=0;recordBtn.setText("Terminer et sauvegarder");recordBtn.setBackground(card(RED,18));enableWorkButtons(true);timer.removeCallbacks(tick);timer.post(tick);haptic(recordBtn);toast("Tournée démarrée");}
    private void finishRecording(){if(!recording)return;if(paused&&pauseStarted>0)pausedTotal+=System.currentTimeMillis()-pauseStarted;recording=false;paused=false;timer.removeCallbacks(tick);lastRoute=save();recordBtn.setText("Commencer une nouvelle tournée");recordBtn.setBackground(card(accent(),18));enableWorkButtons(false);refreshRecordingUi();toast(lastRoute!=null?"Tournée sauvegardée":"Erreur de sauvegarde");}
    private void togglePause(){if(!recording){toast("Aucune tournée en cours");return;}paused=!paused;if(paused){pauseStarted=System.currentTimeMillis();pauseBtn.setText("▶ Reprendre");}else{pausedTotal+=System.currentTimeMillis()-pauseStarted;pauseStarted=0;pauseBtn.setText("Ⅱ Pause");}refreshRecordingUi();}
    private void flag(String type,String label,int color){if(!recording||paused){toast(paused?"Reprends la tournée":"Commence une tournée");return;}if(lastLocation==null){toast("GPS indisponible");return;}Flag f=new Flag(type,label,lastLocation.getLatitude(),lastLocation.getLongitude(),System.currentTimeMillis());flags.add(f);addMarker(new GeoPoint(f.lat,f.lon),String.valueOf(flags.size()),color,"live");haptic("REVERSE".equals(type)?reverseBtn:sidesBtn);refreshRecordingUi();}
    private void undoFlag(){if(flags.isEmpty()){toast("Aucun repère à annuler");return;}flags.remove(flags.size()-1);clearLiveMarkers();for(int i=0;i<flags.size();i++){Flag f=flags.get(i);addMarker(new GeoPoint(f.lat,f.lon),String.valueOf(i+1),"REVERSE".equals(f.type)?ORANGE:GREEN,"live");}refreshRecordingUi();}

    @Override public void onLocationChanged(@NonNull Location l){lastLocation=l;if(gpsChip!=null){int a=Math.round(l.getAccuracy());gpsChip.setText("±"+a+" m");gpsChip.setTextColor(a<=8?GREEN:a<=20?ORANGE:RED);}if(recording&&!paused&&l.getAccuracy()<=60&&append(l)){if(!points.isEmpty()){Point p=points.get(points.size()-1);float[] d=new float[1];Location.distanceBetween(p.lat,p.lon,l.getLatitude(),l.getLongitude(),d);if(d[0]<200)liveDistance+=d[0];}points.add(new Point(l.getLatitude(),l.getLongitude(),l.getTime()>0?l.getTime():System.currentTimeMillis()));List<GeoPoint> g=new ArrayList<>(liveTrack.getActualPoints());g.add(new GeoPoint(l.getLatitude(),l.getLongitude()));liveTrack.setPoints(g);if(prefs.getBoolean("follow",true))map.getController().animateTo(g.get(g.size()-1));refreshRecordingUi();}map.invalidate();}
    private boolean append(Location l){if(points.isEmpty())return true;Point p=points.get(points.size()-1);float[] d=new float[1];Location.distanceBetween(p.lat,p.lon,l.getLatitude(),l.getLongitude(),d);return d[0]>=1||l.getTime()-p.time>=2000;}
    private void refreshRecordingUi(){if(timeText==null)return;if(recording){long now=paused?pauseStarted:System.currentTimeMillis();timeText.setText((paused?"En pause • ":"En cours • ")+duration(now-startedAt-pausedTotal));}else timeText.setText(lastRoute==null?"Prêt à enregistrer":"Dernière tournée sauvegardée");distanceText.setText(distance(liveDistance));flagsText.setText(String.valueOf(flags.size()));pointsText.setText(String.valueOf(points.size()));}

    private File save(){try{File d=routesDir();String stamp=new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.FRANCE).format(new Date(startedAt));File f=new File(d,"RoutePilot_"+stamp+".gpx");StringBuilder x=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"RoutePilot\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:rp=\"https://routepilot.local/gpx/1\">\n");for(Flag q:flags)x.append("<wpt lat=\"").append(q.lat).append("\" lon=\"").append(q.lon).append("\"><time>").append(Instant.ofEpochMilli(q.time)).append("</time><name>").append(q.label).append("</name><extensions><rp:event>").append(q.type).append("</rp:event></extensions></wpt>\n");x.append("<trk><trkseg>\n");for(Point p:points)x.append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\"><time>").append(Instant.ofEpochMilli(p.time)).append("</time></trkpt>\n");x.append("</trkseg></trk></gpx>");try(FileOutputStream o=new FileOutputStream(f)){o.write(x.toString().getBytes(StandardCharsets.UTF_8));}return f;}catch(Exception e){return null;}}
    private Summary parse(File f){Summary s=new Summary(f);try(FileInputStream in=new FileInputStream(f)){XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(in,"UTF-8");int ev=p.getEventType();boolean w=false,t=false;double lat=0,lon=0;String label="Repère",type="EVENT";long wt=0,tt=0;while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG){String n=p.getName();if("wpt".equals(n)){w=true;lat=dbl(p.getAttributeValue(null,"lat"));lon=dbl(p.getAttributeValue(null,"lon"));label="Repère";type="EVENT";}else if("trkpt".equals(n)){t=true;lat=dbl(p.getAttributeValue(null,"lat"));lon=dbl(p.getAttributeValue(null,"lon"));tt=0;}else if("name".equals(n)&&w)label=p.nextText();else if("time".equals(n)){long z=time(p.nextText());if(w)wt=z;else if(t)tt=z;}else if("event".equals(n)&&w)type=p.nextText();}else if(ev==XmlPullParser.END_TAG){if("wpt".equals(p.getName())){s.flags.add(new Flag(type,label,lat,lon,wt));w=false;}else if("trkpt".equals(p.getName())){s.points.add(new Point(lat,lon,tt));if(tt>0){if(s.first==0)s.first=tt;s.last=tt;}t=false;}}ev=p.next();}}catch(Exception ignored){}for(int i=1;i<s.points.size();i++){Point a=s.points.get(i-1),b=s.points.get(i);float[] d=new float[1];Location.distanceBetween(a.lat,a.lon,b.lat,b.lon,d);if(d[0]<500)s.distance+=d[0];}s.duration=s.last>s.first?s.last-s.first:0;return s;}

    private List<File> routeFiles(){File[] a=routesDir().listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".gpx"));if(a==null)return new ArrayList<>();List<File> l=new ArrayList<>(Arrays.asList(a));Collections.sort(l,(x,y)->Long.compare(y.lastModified(),x.lastModified()));return l;}
    private File routesDir(){File d=new File(getFilesDir(),"routes");if(!d.exists())d.mkdirs();return d;}
    private String name(File f){String n=prefs.getString("name_"+f.getName(),null);return n!=null?n:f.getName().replace("RoutePilot_","Tournée ").replace(".gpx","").replace('_',' ');}
    private void rename(File f){EditText e=new EditText(this);e.setText(name(f));e.setSelectAllOnFocus(true);e.setSingleLine();e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);new AlertDialog.Builder(this).setTitle("Renommer").setView(e).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty())prefs.edit().putString("name_"+f.getName(),n).apply();showHistory();}).show();}
    private void delete(File f){new AlertDialog.Builder(this).setTitle("Supprimer cette tournée ?").setMessage("Le fichier GPX local sera supprimé définitivement.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{prefs.edit().remove("name_"+f.getName()).apply();if(f.delete())showHistory();}).show();}
    private void share(File f){Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/gpx+xml");i.putExtra(Intent.EXTRA_STREAM,FileProvider.getUriForFile(this,getPackageName()+".files",f));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Partager la tournée"));}

    private void toggleCollapse(){collapsed=!collapsed;if(collapsed){recordBody.animate().alpha(0).setDuration(120).withEndAction(()->recordBody.setVisibility(View.GONE)).start();collapseBtn.setText("⌃");}else{recordBody.setVisibility(View.VISIBLE);recordBody.setAlpha(0);recordBody.animate().alpha(1).setDuration(170).start();collapseBtn.setText("⌄");}}
    private void clearPage(){if(page!=null){root.removeView(page);page=null;}if(planSheet!=null){root.removeView(planSheet);planSheet=null;}clearPreview();}
    private void clearPreview(){if(previewTrack!=null){map.getOverlays().remove(previewTrack);previewTrack=null;}map.getOverlays().removeIf(o->o instanceof Marker&&"preview".equals(((Marker)o).getRelatedObject()));map.invalidate();}
    private void clearLiveMarkers(){map.getOverlays().removeIf(o->o instanceof Marker&&"live".equals(((Marker)o).getRelatedObject()));map.invalidate();}
    private void addMarker(GeoPoint p,String badge,int color,String tag){Marker m=new Marker(map);m.setPosition(p);m.setAnchor(.5f,.5f);m.setIcon(marker(badge,color));m.setRelatedObject(tag);map.getOverlays().add(m);}
    private BitmapDrawable marker(String s,int color){int z=dp(38);Bitmap b=Bitmap.createBitmap(z,z,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);c.drawCircle(z/2f,z/2f,z*.45f,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.WHITE);c.drawCircle(z/2f,z/2f,z*.40f,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(12));Paint.FontMetrics fm=p.getFontMetrics();c.drawText(s,z/2f,z/2f-(fm.ascent+fm.descent)/2f,p);return new BitmapDrawable(getResources(),b);}

    private View infoCard(String title,String body){LinearLayout r=row();r.setOrientation(LinearLayout.VERTICAL);r.addView(text(title,15,Typeface.BOLD,Color.WHITE));TextView b=text(body,11,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);r.addView(b,lp);return r;}
    private View choice(String title,String value,View.OnClickListener l){LinearLayout r=row();LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,15,Typeface.BOLD,Color.WHITE));t.addView(text(value,11,Typeface.NORMAL,MUTED));r.addView(t,new LinearLayout.LayoutParams(0,-2,1));r.addView(text("›",25,Typeface.NORMAL,MUTED));r.setOnClickListener(l);return r;}
    private View toggle(String title,String sub,String key,boolean def){LinearLayout r=row();LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,15,Typeface.BOLD,Color.WHITE));t.addView(text(sub,11,Typeface.NORMAL,MUTED));r.addView(t,new LinearLayout.LayoutParams(0,-2,1));SwitchCompat sw=new SwitchCompat(this);sw.setChecked(prefs.getBoolean(key,def));sw.setOnCheckedChangeListener((b,c)->{prefs.edit().putBoolean(key,c).apply();if("keep_screen_on".equals(key)){if(c)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}});r.addView(sw);return r;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(15),dp(13),dp(13),dp(13));r.setBackground(card(GLASS2,20));return r;}
    private TextView section(String s){TextView v=text(s,10,Typeface.BOLD,accent());v.setPadding(dp(2),0,0,dp(8));return v;}
    private LinearLayout pageBase(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(18),dp(24),dp(18),dp(86));p.setBackgroundColor(BG);return p;}
    private TextView nav(String icon,String label){TextView v=text(icon+"\n"+label,11,Typeface.BOLD,MUTED);v.setGravity(Gravity.CENTER);return v;}
    private TextView text(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private TextView pill(String s,int color,float sp){TextView v=text(s,sp,Typeface.BOLD,Color.WHITE);v.setGravity(Gravity.CENTER);v.setBackground(card(color,18));return v;}
    private TextView chip(String s,int color){TextView v=pill(s,color,11);v.setPadding(dp(11),0,dp(11),0);return v;}
    private TextView circle(String s,int color){TextView v=pill(s,color,16);return v;}
    private TextView stat(String value,String tag){TextView v=text(value,15,Typeface.BOLD,Color.WHITE);v.setTag(tag);return v;}
    private View statCell(TextView v){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text((String)v.getTag(),9,Typeface.BOLD,Color.argb(135,255,255,255)));b.addView(v);return b;}
    private View summaryStat(String title,String value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text(title,9,Typeface.BOLD,Color.argb(135,255,255,255)));b.addView(text(value,14,Typeface.BOLD,Color.WHITE));return b;}
    private GradientDrawable card(int color,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(Math.min(255,Color.alpha(color)+10),Color.red(color)+Math.min(8,255-Color.red(color)),Color.green(color)+Math.min(8,255-Color.green(color)),Color.blue(color)+Math.min(8,255-Color.blue(color))),color});g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.argb(38,255,255,255));return g;}
    private void press(View v){v.animate().scaleX(.97f).scaleY(.97f).setDuration(60).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(130).setInterpolator(new DecelerateInterpolator()).start()).start();}
    private void fade(View v){v.setAlpha(0);v.animate().alpha(1).setDuration(180).start();}
    private void haptic(View v){if(prefs.getBoolean("haptic",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void enableWorkButtons(boolean e){for(TextView v:new TextView[]{reverseBtn,sidesBtn,pauseBtn,undoBtn}){v.setEnabled(e);v.setAlpha(e?1:.34f);}}
    private void selectTab(String s){for(TextView v:new TextView[]{navMap,navHistory,navSettings}){boolean on=(v==navMap&&"map".equals(s))||(v==navHistory&&"history".equals(s))||(v==navSettings&&"settings".equals(s));v.setTextColor(on?Color.WHITE:MUTED);v.setBackground(on?card(Color.argb(95,Color.red(accent()),Color.green(accent()),Color.blue(accent())),18):null);}}
    private void accentDialog(){String[] n={"Bleu","Violet","Vert","Orange","Cyan"};new AlertDialog.Builder(this).setTitle("Couleur d’accent").setItems(n,(d,w)->{prefs.edit().putString("accent",n[w]).apply();recreate();}).show();}
    private int accent(){String s=prefs==null?"Bleu":prefs.getString("accent","Bleu");if("Violet".equals(s))return PURPLE;if("Vert".equals(s))return GREEN;if("Orange".equals(s))return ORANGE;if("Cyan".equals(s))return CYAN;return BLUE;}

    private void recenter(){if(lastLocation==null){toast("Position GPS en attente");return;}map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()));map.getController().setZoom(18.6);}
    private void ensureLocation(){if(hasLocation())enableLocation();else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);}
    private boolean hasLocation(){return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void enableLocation(){me.enableMyLocation();try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);}catch(SecurityException ignored){}}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==LOCATION_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)enableLocation();else new AlertDialog.Builder(this).setTitle("Localisation requise").setMessage("La localisation précise est nécessaire pour enregistrer une tournée.").setPositiveButton("Réglages",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))).setNegativeButton("Plus tard",null).show();}

    private FrameLayout.LayoutParams topLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(70),Gravity.TOP);p.setMargins(dp(12),dp(12),dp(12),0);return p;}
    private FrameLayout.LayoutParams sheetLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),dp(86));return p;}
    private FrameLayout.LayoutParams dockLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(70),Gravity.BOTTOM);p.setMargins(dp(24),0,dp(24),dp(8));return p;}
    private FrameLayout.LayoutParams pageLp(){return new FrameLayout.LayoutParams(-1,-1);}
    private FrameLayout.LayoutParams planLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(330),Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),dp(86));return p;}
    private LinearLayout.LayoutParams bottom(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(x);return p;}
    private LinearLayout.LayoutParams top(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(x);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String duration(long ms){long x=Math.max(0,ms/1000),h=x/3600,m=x%3600/60,s=x%60;return h>0?String.format(Locale.FRANCE,"%02d:%02d:%02d",h,m,s):String.format(Locale.FRANCE,"%02d:%02d",m,s);}
    private String distance(double m){return m>=1000?String.format(Locale.FRANCE,"%.2f km",m/1000):String.format(Locale.FRANCE,"%.0f m",m);}
    private String date(long ms){return new SimpleDateFormat("EEEE d MMMM • HH:mm",Locale.FRANCE).format(new Date(ms));}
    private String bytes(long b){return b<1024*1024?(b/1024)+" Ko":String.format(Locale.FRANCE,"%.0f Mo",b/1048576.0);}
    private static double dbl(String s){try{return Double.parseDouble(s);}catch(Exception e){return 0;}}
    private static long time(String s){try{return Instant.parse(s).toEpochMilli();}catch(DateTimeParseException e){return 0;}}

    @Override protected void onResume(){super.onResume();map.onResume();if(hasLocation())enableLocation();}
    @Override protected void onPause(){map.onPause();super.onPause();}
    @Override protected void onDestroy(){timer.removeCallbacks(tick);try{lm.removeUpdates(this);}catch(SecurityException ignored){}if(me!=null)me.disableMyLocation();super.onDestroy();}

    static final class Point{final double lat,lon;final long time;Point(double a,double b,long c){lat=a;lon=b;time=c;}}
    static final class Flag{final String type,label;final double lat,lon;final long time;Flag(String a,String b,double c,double d,long e){type=a;label=b;lat=c;lon=d;time=e;}}
    static final class Summary{final File file;final List<Point> points=new ArrayList<>();final List<Flag> flags=new ArrayList<>();long first,last,duration;double distance;Summary(File f){file=f;}}
}
