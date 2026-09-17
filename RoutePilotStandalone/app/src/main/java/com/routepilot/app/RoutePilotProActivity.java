package com.routepilot.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RoutePilotProActivity extends AppCompatActivity implements LocationListener {
    private static final int LOCATION_PERMISSION=42;
    private static final int IMPORT_GPX=1337;
    private static final int BLUE=Color.rgb(10,132,255),GREEN=Color.rgb(48,209,88),ORANGE=Color.rgb(255,159,10),RED=Color.rgb(255,69,58),PURPLE=Color.rgb(191,90,242),CYAN=Color.rgb(100,210,255),PINK=Color.rgb(255,55,95);
    private static final int BG=Color.rgb(8,9,12),GLASS=Color.argb(225,27,28,34),GLASS2=Color.argb(239,42,43,50),GLASS3=Color.argb(248,20,21,26),MUTED=Color.argb(180,255,255,255);

    private final List<RouteStore.Point> points=new ArrayList<>();
    private final List<RouteStore.Event> events=new ArrayList<>();
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final Handler previewTimer=new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private RouteStore store;
    private FrameLayout root;
    private MapView map;
    private MyLocationNewOverlay me;
    private LocationManager lm;
    private Polyline liveTrack,previewTrack,approachTrack;
    private Marker previewCursor;
    private Location lastLocation;

    private View topBar,recordSheet,dock,page,planSheet,focusRestore,guidanceHud,planLegend;
    private LinearLayout recordBody;
    private TextView gpsChip,mapChip,timeText,distanceText,eventsText,pointsText,recordBtn,reverseBtn,sidesBtn,pauseBtn,undoBtn,collapseBtn;
    private TextView navMap,navHistory,navSettings;

    private boolean recording,paused,collapsed,focusMode,guiding,guidanceCameraFollow=true,recordingCameraFollow=true,approachingStart;
    private long startedAt,pauseStarted,pausedTotal,lastDraftWrite;
    private double liveDistance,approachDistanceM;
    private String approachInstruction="Calcul de l’itinéraire vers le départ…";
    private File lastRoute;

    private GuidanceEngine guidance;
    private RouteStore.Summary guidingRoute;
    private boolean guidingReverse;
    private TextView guideTitle,guideSubtitle,guideProgress,guideRemaining,guideNext,guideDeviation,guideEventAlert;

    private Runnable previewRunnable;
    private int previewIndex;
    private RouteStore.Summary previewSummary;
    private String lastEventAlertKey="";

    private final Runnable tick=new Runnable(){@Override public void run(){refreshRecordingUi();if(recording)timer.postDelayed(this,500);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("routepilot",MODE_PRIVATE);
        store=new RouteStore(this,prefs);
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        applyKeepScreen();

        Configuration.getInstance().setUserAgentValue(getPackageName()+"/1.0 RoutePilot");
        Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(),"osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(),"osmdroid/tiles"));
        MapsForgeTileSource.createInstance(getApplication());

        root=new FrameLayout(this);root.setBackgroundColor(BG);
        buildMap();buildChrome();setContentView(root);
        List<File> files=store.routeFiles();if(!files.isEmpty())lastRoute=files.get(0);
        ensureLocation();
        prefs.edit().remove("compact_ui").remove("reduce_motion").apply();
        root.postDelayed(this::checkDraftRecovery,650);
    }

    private void buildMap(){
        map=new MapView(this);map.setTileSource(TileSourceFactory.MAPNIK);map.setUseDataConnection(true);map.setMultiTouchControls(true);map.setTilesScaledToDpi(true);map.setMinZoomLevel(3.0);map.setMaxZoomLevel(20.0);map.getController().setZoom(18.2);map.setHorizontalMapRepetitionEnabled(false);map.setVerticalMapRepetitionEnabled(false);map.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(guiding)guidanceCameraFollow=false;if(recording)recordingCameraFollow=false;}return false;});root.addView(map,new FrameLayout.LayoutParams(-1,-1));
        me=new MyLocationNewOverlay(new GpsMyLocationProvider(this),map);me.setDrawAccuracyEnabled(true);map.getOverlays().add(me);
        liveTrack=new Polyline();liveTrack.getOutlinePaint().setStrokeWidth(dp(7));liveTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);liveTrack.getOutlinePaint().setColor(traceColor());map.getOverlays().add(liveTrack);
        restoreOfflineMap();
    }

    private void buildChrome(){
        topBar=buildTopBar();recordSheet=buildRecordSheet();dock=buildDock();root.addView(topBar,topLp());root.addView(recordSheet,sheetLp());root.addView(dock,dockLp());selectTab("map");
    }

    private View buildTopBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(15),dp(9),dp(9),dp(9));bar.setBackground(glass(GLASS,28));bar.setElevation(dp(16));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.addView(text("RoutePilot",20,Typeface.BOLD,Color.WHITE));brand.addView(text("Navigation de tournée",10,Typeface.NORMAL,MUTED));bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1f));
        mapChip=chip("OSM",GLASS2);mapChip.setOnClickListener(v->mapSourceMenu());LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,dp(36));mp.rightMargin=dp(6);bar.addView(mapChip,mp);
        gpsChip=chip("GPS…",GLASS2);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-2,dp(36));gp.rightMargin=dp(6);bar.addView(gpsChip,gp);
        TextView focus=circle("▣",GLASS2);focus.setOnClickListener(v->enterFocusMode());LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(40),dp(40));fp.rightMargin=dp(6);bar.addView(focus,fp);
        TextView locate=circle("◎",accent());locate.setOnClickListener(v->{press(v);recenter();});bar.addView(locate,new LinearLayout.LayoutParams(dp(40),dp(40)));
        updateMapChip();return bar;
    }

    private View buildRecordSheet(){
        LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setPadding(dp(15),dp(10),dp(15),dp(14));sheet.setBackground(glass(GLASS,30));sheet.setElevation(dp(22));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("TOURNÉE",10,Typeface.BOLD,accent()));timeText=text("Prêt à enregistrer",14,Typeface.BOLD,Color.WHITE);titles.addView(timeText);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        collapseBtn=circle("⌄",GLASS2);collapseBtn.setOnClickListener(v->setCollapsed(!collapsed));head.addView(collapseBtn,new LinearLayout.LayoutParams(dp(40),dp(40)));sheet.addView(head);
        recordBody=new LinearLayout(this);recordBody.setOrientation(LinearLayout.VERTICAL);sheet.addView(recordBody);

        LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(10),0,dp(10));distanceText=stat("0 m","DISTANCE");eventsText=stat("0","REPÈRES");pointsText=stat("0","POINTS GPS");stats.addView(statCell(distanceText),new LinearLayout.LayoutParams(0,-2,1));stats.addView(statCell(eventsText),new LinearLayout.LayoutParams(0,-2,1));stats.addView(statCell(pointsText),new LinearLayout.LayoutParams(0,-2,1));recordBody.addView(stats);

        recordBtn=pill("Commencer l’enregistrement",accent(),16);recordBtn.setOnClickListener(v->{press(v);if(recording)requestFinishRecording();else startRecording();});recordBody.addView(recordBtn,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout work=new LinearLayout(this);work.setPadding(0,dp(9),0,0);reverseBtn=pill("↶  Marche arrière",GLASS2,13);sidesBtn=pill("⇆  2 côtés",GLASS2,13);reverseBtn.setOnClickListener(v->addEvent("REVERSE","Marche arrière",ORANGE));sidesBtn.setOnClickListener(v->addEvent("TWO_SIDES","2 côtés",GREEN));reverseBtn.setOnLongClickListener(v->{eventWithNote("REVERSE","Marche arrière",ORANGE);return true;});sidesBtn.setOnLongClickListener(v->{eventWithNote("TWO_SIDES","2 côtés",GREEN);return true;});LinearLayout.LayoutParams w1=new LinearLayout.LayoutParams(0,dp(54),1);w1.rightMargin=dp(5);LinearLayout.LayoutParams w2=new LinearLayout.LayoutParams(0,dp(54),1);w2.leftMargin=dp(5);work.addView(reverseBtn,w1);work.addView(sidesBtn,w2);recordBody.addView(work);
        LinearLayout tools=new LinearLayout(this);tools.setPadding(0,dp(8),0,0);undoBtn=pill("↩ Annuler repère",GLASS2,12);pauseBtn=pill("Ⅱ Pause",GLASS2,12);undoBtn.setOnClickListener(v->undoEvent());pauseBtn.setOnClickListener(v->togglePause());LinearLayout.LayoutParams t1=new LinearLayout.LayoutParams(0,dp(44),1.25f);t1.rightMargin=dp(5);LinearLayout.LayoutParams t2=new LinearLayout.LayoutParams(0,dp(44),.75f);t2.leftMargin=dp(5);tools.addView(undoBtn,t1);tools.addView(pauseBtn,t2);recordBody.addView(tools);
        enableWorkButtons(false);return sheet;
    }

    private View buildDock(){
        LinearLayout d=new LinearLayout(this);d.setGravity(Gravity.CENTER);d.setPadding(dp(7),dp(6),dp(7),dp(6));d.setBackground(glass(Color.argb(247,21,22,27),27));d.setElevation(dp(25));navMap=nav("⌖","Carte");navHistory=nav("≡","Tournées");navSettings=nav("⚙","Réglages");navMap.setOnClickListener(v->showMap());navHistory.setOnClickListener(v->showHistory());navSettings.setOnClickListener(v->showSettings());d.addView(navMap,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navHistory,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navSettings,new LinearLayout.LayoutParams(0,dp(58),1));return d;
    }

    private void showMap(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.VISIBLE);topBar.setVisibility(View.VISIBLE);recordSheet.setVisibility(View.VISIBLE);dock.setVisibility(View.VISIBLE);selectTab("map");}
    private void showHistory(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=historyPage();root.addView(page,pageLp());selectTab("history");fade(page);}
    private void showSettings(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=settingsPage();root.addView(page,pageLp());selectTab("settings");fade(page);}
    private void openNewRecording(){showMap();setCollapsed(false);if(recordBtn!=null)recordBtn.post(()->recordBtn.requestFocus());}

    private View historyPage(){
        LinearLayout p=pageBase();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout hb=new LinearLayout(this);hb.setOrientation(LinearLayout.VERTICAL);hb.addView(text("Tournées",30,Typeface.BOLD,Color.WHITE));hb.addView(text(store.routeFiles().size()+" enregistrée(s) • plans • relecture guidée",12,Typeface.NORMAL,MUTED));head.addView(hb,new LinearLayout.LayoutParams(0,-2,1));TextView plus=circle("＋",accent());plus.setOnClickListener(v->{press(v);showToursActionSheet();});head.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(46)));p.addView(head);
        EditText search=new EditText(this);search.setHint("Rechercher une tournée");search.setHintTextColor(Color.argb(115,255,255,255));search.setTextColor(Color.WHITE);search.setTextSize(14);search.setSingleLine(true);search.setPadding(dp(15),0,dp(15),0);search.setBackground(glass(GLASS2,18));LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(48));sl.topMargin=dp(15);sl.bottomMargin=dp(12);p.addView(search,sl);
        ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));List<RouteStore.Summary> summaries=store.summaries();renderHistory(list,summaries,"");search.addTextChangedListener(new SimpleWatcher(q->renderHistory(list,summaries,q)));return p;
    }

    private void renderHistory(LinearLayout list,List<RouteStore.Summary> all,String query){
        list.removeAllViews();String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);int shown=0;
        for(RouteStore.Summary s:all){String name=store.displayName(s.file).toLowerCase(Locale.ROOT);if(!q.isEmpty()&&!name.contains(q))continue;list.addView(historyCard(s),bottom(11));shown++;}
        if(shown==0){LinearLayout e=new LinearLayout(this);e.setOrientation(LinearLayout.VERTICAL);e.setGravity(Gravity.CENTER);e.setPadding(0,dp(82),0,0);e.addView(text("⌁",46,Typeface.NORMAL,Color.argb(100,255,255,255)));e.addView(text(q.isEmpty()?"Aucune tournée":"Aucun résultat",19,Typeface.BOLD,Color.WHITE));e.addView(text(q.isEmpty()?"Enregistre ta première tournée depuis la carte.":"Essaie un autre nom.",12,Typeface.NORMAL,MUTED));list.addView(e);}
    }

    private View historyCard(RouteStore.Summary s){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(14));c.setBackground(glass(GLASS2,23));
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);TextView fav=text(store.isFavorite(s.file)?"★":"☆",20,Typeface.BOLD,store.isFavorite(s.file)?ORANGE:MUTED);fav.setGravity(Gravity.CENTER);fav.setOnClickListener(v->{store.setFavorite(s.file,!store.isFavorite(s.file));showHistory();});h.addView(fav,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text(store.displayName(s.file),17,Typeface.BOLD,Color.WHITE));names.addView(text(formatDate(s.file.lastModified()),11,Typeface.NORMAL,MUTED));h.addView(names,new LinearLayout.LayoutParams(0,-2,1));TextView menu=circle("•••",Color.argb(72,255,255,255));menu.setOnClickListener(v->routeMenu(s));h.addView(menu,new LinearLayout.LayoutParams(dp(42),dp(42)));c.addView(h);
        LinearLayout st=new LinearLayout(this);st.setPadding(0,dp(13),0,dp(12));st.addView(summaryStat("DURÉE",formatDuration(s.durationMs)),new LinearLayout.LayoutParams(0,-2,1));st.addView(summaryStat("DISTANCE",formatDistance(s.distanceM)),new LinearLayout.LayoutParams(0,-2,1));st.addView(summaryStat("REPÈRES",String.valueOf(s.events.size())),new LinearLayout.LayoutParams(0,-2,1));c.addView(st);
        LinearLayout actions=new LinearLayout(this);TextView plan=pill("Voir le plan",GLASS2,12);TextView guide=pill("▶  Démarrer",accent(),12);plan.setOnClickListener(v->openPlan(s));guide.setOnClickListener(v->chooseGuidanceDirection(s));LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(44),.85f);p1.rightMargin=dp(5);LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(44),1.15f);p2.leftMargin=dp(5);actions.addView(plan,p1);actions.addView(guide,p2);c.addView(actions);c.setOnClickListener(v->openPlan(s));return c;
    }

    private void routeMenu(RouteStore.Summary s){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel(store.displayName(s.file),formatDuration(s.durationMs)+" • "+formatDistance(s.distanceM));
        String[] labels={"▣  Voir le plan","▶  Démarrer la tournée","✎  Renommer","↗  Exporter / partager","⧉  Dupliquer",store.isFavorite(s.file)?"☆  Retirer des favoris":"★  Ajouter aux favoris","⌫  Supprimer"};
        for(int i=0;i<labels.length;i++){final int index=i;TextView action=sheetAction(labels[i],i==6?Color.argb(120,255,69,58):Color.argb(48,255,255,255));action.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(()->{if(index==0)openPlan(s);else if(index==1)chooseGuidanceDirection(s);else if(index==2)rename(s.file);else if(index==3)share(s.file);else if(index==4){if(store.duplicate(s.file)!=null){toast("Tournée dupliquée");showHistory();}}else if(index==5){store.setFavorite(s.file,!store.isFavorite(s.file));showHistory();}else deleteRoute(s.file);},130);});panel.addView(action,bottom(7));}
        presentBottomSheet(d,shell,panel);
    }

    private void openPlan(RouteStore.Summary s){
        if(s.points.size()<2){toast("Cette tournée ne contient pas assez de points GPS");return;}stopPreviewPlayback();if(page!=null)page.setVisibility(View.GONE);map.setVisibility(View.VISIBLE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);drawSummary(s,false);
        LinearLayout sh=new LinearLayout(this);sh.setOrientation(LinearLayout.VERTICAL);sh.setPadding(dp(15),dp(12),dp(15),dp(14));sh.setBackground(glass(GLASS3,29));sh.setElevation(dp(26));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout title=new LinearLayout(this);title.setOrientation(LinearLayout.VERTICAL);title.addView(text(store.displayName(s.file),18,Typeface.BOLD,Color.WHITE));title.addView(text(formatDuration(s.durationMs)+"  •  "+formatDistance(s.distanceM)+"  •  "+s.events.size()+" repères",11,Typeface.NORMAL,MUTED));head.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView focus=circle("▣",GLASS2);focus.setOnClickListener(v->enterFocusMode());LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(dp(40),dp(40));fl.rightMargin=dp(6);head.addView(focus,fl);TextView close=pill("Fermer",Color.argb(70,255,255,255),11);close.setOnClickListener(v->{press(v);closePlan();});head.addView(close,new LinearLayout.LayoutParams(dp(68),dp(40)));sh.addView(head);
        LinearLayout quick=new LinearLayout(this);quick.setPadding(0,dp(10),0,dp(5));quick.addView(summaryStat("DÉPART",timeOf(s.firstTime)),new LinearLayout.LayoutParams(0,-2,1));quick.addView(summaryStat("ARRIVÉE",timeOf(s.lastTime)),new LinearLayout.LayoutParams(0,-2,1));quick.addView(summaryStat("MOYENNE",avgSpeed(s)),new LinearLayout.LayoutParams(0,-2,1));sh.addView(quick);
        ScrollView scroll=new ScrollView(this);LinearLayout timeline=new LinearLayout(this);timeline.setOrientation(LinearLayout.VERTICAL);View start=timeline("D","Départ","Début de tournée",GREEN);start.setOnClickListener(v->centerPoint(s.points.get(0),19));timeline.addView(start);for(int i=0;i<s.events.size();i++){RouteStore.Event e=s.events.get(i);String sub=e.time>0&&s.firstTime>0?"+"+formatDuration(e.time-s.firstTime):"Repère "+(i+1);View row=timeline(String.valueOf(i+1),e.label,sub,"REVERSE".equals(e.type)?ORANGE:GREEN);final int index=i;row.setOnClickListener(v->centerEvent(s.events.get(index)));timeline.addView(row);}View end=timeline("A","Arrivée","Fin de tournée",RED);end.setOnClickListener(v->centerPoint(s.points.get(s.points.size()-1),19));timeline.addView(end);scroll.addView(timeline);LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,0,1);scrollLp.topMargin=dp(5);sh.addView(scroll,scrollLp);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(8),0,0);TextView preview=pill("▶ Aperçu animé",GLASS2,11);TextView full=pill("⛶ Plein plan",GLASS2,11);TextView startGuide=pill("➜ Démarrer",accent(),12);preview.setOnClickListener(v->enterPlanPreview(s));full.setOnClickListener(v->enterPlanOverview(s));startGuide.setOnClickListener(v->chooseGuidanceDirection(s));actions.addView(preview,weighted(.9f,4));actions.addView(full,weighted(.9f,4));actions.addView(startGuide,weighted(1.2f,0));sh.addView(actions);planSheet=sh;root.addView(sh,planLp());fade(sh);
    }

    private void drawSummary(RouteStore.Summary s,boolean reverse){
        clearPreview();previewTrack=new Polyline();previewTrack.getOutlinePaint().setColor(traceColor());previewTrack.getOutlinePaint().setStrokeWidth(dp(9));previewTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);List<GeoPoint> pts=new ArrayList<>();if(reverse){for(int i=s.points.size()-1;i>=0;i--)pts.add(new GeoPoint(s.points.get(i).lat,s.points.get(i).lon));}else for(RouteStore.Point p:s.points)pts.add(new GeoPoint(p.lat,p.lon));previewTrack.setPoints(pts);map.getOverlays().add(previewTrack);addMarker(pts.get(0),"D",GREEN,"preview");for(RouteStore.Event e:s.events){String badge="REVERSE".equals(e.type)?"MA":"2C";addMarker(new GeoPoint(e.lat,e.lon),badge,"REVERSE".equals(e.type)?ORANGE:CYAN,"preview-event");}addMarker(pts.get(pts.size()-1),"A",RED,"preview");fitSummary(s);map.invalidate();
    }

    private void fitSummary(RouteStore.Summary s){if(previewTrack!=null)map.post(()->{try{map.zoomToBoundingBox(previewTrack.getBounds(),true,dp(110));map.invalidate();}catch(Exception ignored){}});}
    private void centerPoint(RouteStore.Point p,double zoom){map.getController().animateTo(new GeoPoint(p.lat,p.lon));map.getController().setZoom(zoom);}
    private void centerEvent(RouteStore.Event e){map.getController().animateTo(new GeoPoint(e.lat,e.lon));map.getController().setZoom(19.2);}
    private void enterPlanOverview(RouteStore.Summary s){stopPreviewPlayback();enterFocusMode();showPlanLegend();map.postDelayed(()->fitSummary(s),130);}
    private void enterPlanPreview(RouteStore.Summary s){removePlanLegend();enterFocusMode();startPreviewPlayback(s);}

    private void startPreviewPlayback(RouteStore.Summary s){
        stopPreviewPlayback();if(s.points.isEmpty())return;previewSummary=s;previewIndex=0;RouteStore.Point first=s.points.get(0);previewCursor=new Marker(map);previewCursor.setPosition(new GeoPoint(first.lat,first.lon));previewCursor.setAnchor(.5f,.5f);previewCursor.setIcon(marker("◆",accent()));previewCursor.setRelatedObject("preview-cursor");map.getOverlays().add(previewCursor);toast("Aperçu animé • suis le repère lumineux");int step=Math.max(1,s.points.size()/150);previewRunnable=new Runnable(){@Override public void run(){if(previewSummary==null||previewIndex>=previewSummary.points.size()){stopPreviewPlayback();return;}RouteStore.Point p=previewSummary.points.get(previewIndex);if(previewCursor!=null){previewCursor.setPosition(new GeoPoint(p.lat,p.lon));previewCursor.setIcon(marker(previewIndex%2==0?"◆":"●",accent()));}map.getController().animateTo(new GeoPoint(p.lat,p.lon));if(previewIndex==0)map.getController().setZoom(17.4);map.invalidate();previewIndex+=step;previewTimer.postDelayed(this,150);}};previewTimer.post(previewRunnable);
    }
    private void stopPreviewPlayback(){if(previewRunnable!=null)previewTimer.removeCallbacks(previewRunnable);previewRunnable=null;previewSummary=null;if(previewCursor!=null){map.getOverlays().remove(previewCursor);previewCursor=null;map.invalidate();}}

    private void closePlan(){stopPreviewPlayback();if(planSheet!=null){root.removeView(planSheet);planSheet=null;}clearPreview();map.setVisibility(View.GONE);if(page!=null)page.setVisibility(View.VISIBLE);}

    private void chooseGuidanceDirection(RouteStore.Summary s){
        if(s.points.size()<2){toast("Trace insuffisante");return;}showGuidanceStartSheet(s);
    }

    private void startGuidance(RouteStore.Summary s,boolean reverse){
        stopPreviewPlayback();if(planSheet!=null){root.removeView(planSheet);planSheet=null;}if(page!=null)page.setVisibility(View.GONE);guiding=true;guidingRoute=s;guidingReverse=reverse;guidanceCameraFollow=true;lastEventAlertKey="";drawSummary(s,reverse);makeGuidanceEventsSubtle();map.setVisibility(View.VISIBLE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.GONE);
        List<GuidanceEngine.Point> gp=new ArrayList<>();if(reverse){for(int i=s.points.size()-1;i>=0;i--){RouteStore.Point p=s.points.get(i);gp.add(new GuidanceEngine.Point(p.lat,p.lon));}}else for(RouteStore.Point p:s.points)gp.add(new GuidanceEngine.Point(p.lat,p.lon));List<GuidanceEngine.Event> ge=new ArrayList<>();for(RouteStore.Event e:s.events)ge.add(new GuidanceEngine.Event(e.type,e.label,e.lat,e.lon));guidance=new GuidanceEngine(gp,ge);guidanceHud=buildGuidanceHud();root.addView(guidanceHud,new FrameLayout.LayoutParams(-1,-1));fade(guidanceHud);
        RouteStore.Point start=reverse?s.points.get(s.points.size()-1):s.points.get(0);if(lastLocation!=null){float[] d=new float[1];Location.distanceBetween(lastLocation.getLatitude(),lastLocation.getLongitude(),start.lat,start.lon,d);approachingStart=d[0]>70;approachDistanceM=d[0];if(approachingStart)requestApproachRoute(lastLocation,new GeoPoint(start.lat,start.lon));}
        if(lastLocation!=null)updateGuidance(lastLocation);else{guideTitle.setText("Position GPS en attente");guideSubtitle.setText("Le guidage démarrera dès que la position est disponible.");}
    }

    private View buildGuidanceHud(){
        FrameLayout layer=new FrameLayout(this);layer.setClickable(false);
        guideEventAlert=pill("",Color.argb(225,255,159,10),13);guideEventAlert.setVisibility(View.GONE);guideEventAlert.setElevation(dp(24));FrameLayout.LayoutParams alertP=new FrameLayout.LayoutParams(-1,dp(54),Gravity.TOP);alertP.setMargins(dp(18),safeTop()+dp(92),dp(18),0);layer.addView(guideEventAlert,alertP);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(18),dp(15),dp(18),dp(15));top.setBackground(glass(Color.argb(220,17,19,25),30));top.setElevation(dp(18));guideTitle=text("Suivre la trace",22,Typeface.BOLD,Color.WHITE);guideSubtitle=text("Recherche du prochain repère…",12,Typeface.NORMAL,MUTED);top.addView(guideTitle);LinearLayout.LayoutParams sub=new LinearLayout.LayoutParams(-1,-2);sub.topMargin=dp(4);top.addView(guideSubtitle,sub);FrameLayout.LayoutParams topP=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);topP.setMargins(dp(12),safeTop(),dp(12),0);layer.addView(top,topP);

        LinearLayout camera=new LinearLayout(this);camera.setOrientation(LinearLayout.VERTICAL);camera.setPadding(dp(5),dp(5),dp(5),dp(5));camera.setBackground(glass(Color.argb(210,22,24,31),22));camera.setElevation(dp(16));TextView locate=circle("◎",accent());TextView zoomIn=circle("＋",GLASS2);TextView zoomOut=circle("−",GLASS2);locate.setOnClickListener(v->{guidanceCameraFollow=true;if(lastLocation!=null){map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()));map.getController().setZoom(17.8);}});zoomIn.setOnClickListener(v->{guidanceCameraFollow=false;map.getController().zoomIn();});zoomOut.setOnClickListener(v->{guidanceCameraFollow=false;map.getController().zoomOut();});camera.addView(locate,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout.LayoutParams zi=new LinearLayout.LayoutParams(dp(44),dp(44));zi.topMargin=dp(5);camera.addView(zoomIn,zi);LinearLayout.LayoutParams zo=new LinearLayout.LayoutParams(dp(44),dp(44));zo.topMargin=dp(5);camera.addView(zoomOut,zo);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(54),-2,Gravity.END|Gravity.CENTER_VERTICAL);cp.rightMargin=dp(12);layer.addView(camera,cp);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(16),dp(14),dp(16),dp(15));bottom.setBackground(glass(Color.argb(225,17,19,25),30));bottom.setElevation(dp(20));LinearLayout stats=new LinearLayout(this);guideProgress=stat("0 %","PROGRESSION");guideRemaining=stat("—","RESTANT");guideDeviation=stat("—","ÉCART TRACE");stats.addView(statCell(guideProgress),new LinearLayout.LayoutParams(0,-2,1));stats.addView(statCell(guideRemaining),new LinearLayout.LayoutParams(0,-2,1));stats.addView(statCell(guideDeviation),new LinearLayout.LayoutParams(0,-2,1));bottom.addView(stats);guideNext=text("Prochain repère : —",13,Typeface.BOLD,Color.WHITE);LinearLayout.LayoutParams nx=new LinearLayout.LayoutParams(-1,-2);nx.topMargin=dp(11);nx.bottomMargin=dp(11);bottom.addView(guideNext,nx);LinearLayout controls=new LinearLayout(this);TextView back=pill("← Étape",GLASS2,12);TextView next=pill("Étape →",GLASS2,12);TextView quit=pill("Quitter",RED,12);back.setOnClickListener(v->{guidance.jumpPoints(-45);if(lastLocation!=null)updateGuidance(lastLocation);});next.setOnClickListener(v->{guidance.jumpPoints(45);if(lastLocation!=null)updateGuidance(lastLocation);});quit.setOnClickListener(v->confirmStopGuidance());controls.addView(back,weighted(1f,4));controls.addView(next,weighted(1f,4));controls.addView(quit,weighted(.9f,0));bottom.addView(controls);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);bp.setMargins(dp(12),0,dp(12),safeBottom()+dp(10));layer.addView(bottom,bp);return layer;
    }

    private void updateGuidance(Location l){
        if(!guiding||guidance==null||!guidance.isUsable())return;
        if(approachingStart&&guidingRoute!=null){RouteStore.Point target=guidingReverse?guidingRoute.points.get(guidingRoute.points.size()-1):guidingRoute.points.get(0);float[] dd=new float[1];Location.distanceBetween(l.getLatitude(),l.getLongitude(),target.lat,target.lon,dd);if(dd[0]<=45){approachingStart=false;clearApproachRoute();toast("Départ rejoint • guidage de tournée actif");}else{guideTitle.setText("Rejoins le départ");guideTitle.setTextColor(CYAN);guideSubtitle.setText(formatDistance(approachDistanceM>0?approachDistanceM:dd[0])+" jusqu’au départ");guideProgress.setText("—");guideRemaining.setText(formatDistance(approachDistanceM>0?approachDistanceM:dd[0]));guideDeviation.setText("GPS");guideNext.setText(approachInstruction);if(guidanceCameraFollow)map.getController().animateTo(new GeoPoint(l.getLatitude(),l.getLongitude()));map.invalidate();return;}}
        GuidanceEngine.State s=guidance.update(l.getLatitude(),l.getLongitude());if(s.nextEvent!=null&&s.distanceToNextEventM<=60)showEventAlert(s.nextEvent.type,s.nextEvent.label);else hideEventAlert();guideProgress.setText(s.progressPercent+" %");guideRemaining.setText(formatDistance(s.remainingM));guideDeviation.setText(Math.round(s.distanceToTraceM)+" m");
        if(s.finished){guideTitle.setText("Tournée terminée");guideTitle.setTextColor(GREEN);guideSubtitle.setText("Tu as atteint la fin de la trace enregistrée.");guideNext.setText("✓ Fin de la tournée");}
        else if(s.offRoute){guideTitle.setText("Rejoins la tournée");guideTitle.setTextColor(RED);guideSubtitle.setText("Hors trace • "+Math.round(s.distanceToTraceM)+" m");guideNext.setText(s.nextEvent==null?"Retrouve la ligne de tournée":"Ensuite : "+s.nextEvent.label);}
        else{guideTitle.setTextColor(Color.WHITE);if(s.nextEvent!=null&&s.distanceToNextEventM<Float.MAX_VALUE){String icon="REVERSE".equals(s.nextEvent.type)?"↶ ":"⇆ ";guideTitle.setText(icon+s.nextEvent.label);guideSubtitle.setText("dans "+formatDistance(s.distanceToNextEventM));guideNext.setText("Prochain repère • "+s.nextEvent.label);}else{guideTitle.setText("Suivre la trace");guideSubtitle.setText("Reste sur le parcours enregistré");guideNext.setText("Aucun autre repère métier");}}
        if(guidanceCameraFollow)map.getController().animateTo(new GeoPoint(l.getLatitude(),l.getLongitude()));map.invalidate();
    }

    private void confirmStopGuidance(){new AlertDialog.Builder(this).setTitle("Quitter le guidage ?").setMessage("La tournée enregistrée ne sera pas modifiée.").setNegativeButton("Continuer",null).setPositiveButton("Quitter",(d,w)->stopGuidance()).show();}
    private void stopGuidance(){guiding=false;guidanceCameraFollow=true;approachingStart=false;clearApproachRoute();guidance=null;guidingRoute=null;guideEventAlert=null;lastEventAlertKey="";if(guidanceHud!=null){root.removeView(guidanceHud);guidanceHud=null;}clearPreview();showHistory();}

    private View timeline(String badge,String title,String sub,int color){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(7),0,dp(7));TextView b=circle(badge,color);r.addView(b,new LinearLayout.LayoutParams(dp(34),dp(34)));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,13,Typeface.BOLD,Color.WHITE));t.addView(text(sub,11,Typeface.NORMAL,MUTED));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(10);r.addView(t,lp);r.setBackground(rippleLike());return r;}

    private View settingsPage(){
        LinearLayout p=pageBase();p.addView(text("Réglages",30,Typeface.BOLD,Color.WHITE));TextView sub=text("Personnalisation • GPS • cartes hors ligne",12,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams spl=new LinearLayout.LayoutParams(-1,-2);spl.bottomMargin=dp(18);p.addView(sub,spl);ScrollView sc=new ScrollView(this);LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);sc.addView(c);p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        c.addView(section("APPARENCE"));c.addView(choice("Couleur d’accent",prefs.getString("accent","Bleu"),v->accentDialog()),bottom(9));c.addView(choice("Couleur de la trace",prefs.getString("trace_color","Orange"),v->traceColorDialog()),bottom(9));
        c.addView(section("GPS & CONFORT"),top(12));c.addView(toggle("Suivre ma position","Recentre pendant l’enregistrement et le guidage","follow",true),bottom(9));c.addView(toggle("Écran toujours allumé","Évite la mise en veille pendant le travail","keep_screen_on",true),bottom(9));c.addView(toggle("Retour haptique","Vibration légère sur les actions métier","haptic",true),bottom(9));c.addView(infoCard("Précision de trace","1 seconde • filtrage des points GPS aberrants • brouillon de secours automatique"),bottom(9));
        c.addView(section("CARTES HORS LIGNE • FRANCE"),top(12));c.addView(onlineMapRow(),bottom(9));for(FranceOfflineManager.Pack pack:FranceOfflineManager.PACKS)c.addView(offlineRow(pack),bottom(8));c.addView(infoCard("Stockage cartes",bytes(FranceOfflineManager.installedBytes(this))+" installés. Les cartes sont séparées de l’APK et restent disponibles sans réseau."),bottom(9));
        c.addView(section("DONNÉES"),top(12));c.addView(choice("Importer une tournée GPX","Depuis le stockage du téléphone",v->importGpx()),bottom(9));c.addView(choice("Mes tournées",store.routeFiles().size()+" fichier(s) GPX",v->showHistory()),bottom(9));c.addView(choice("Revoir l’introduction","Relance le parcours de première ouverture",v->resetOnboarding()),bottom(20));return p;
    }

    private View onlineMapRow(){LinearLayout r=row();LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);boolean online=prefs.getString("offline_pack",null)==null;labels.addView(text("OpenStreetMap en ligne",15,Typeface.BOLD,Color.WHITE));labels.addView(text(online?"Active • sans clé API":"Disponible",11,Typeface.NORMAL,online?GREEN:MUTED));r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView a=pill(online?"Active":"Activer",online?GREEN:accent(),11);a.setPadding(dp(12),0,dp(12),0);a.setOnClickListener(v->activateOnline());r.addView(a,new LinearLayout.LayoutParams(-2,dp(38)));return r;}

    private View offlineRow(FranceOfflineManager.Pack pack){
        LinearLayout r=row();LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);boolean installed=FranceOfflineManager.isInstalled(this,pack);boolean active=pack.id.equals(prefs.getString("offline_pack",null));String pending=prefs.getString("pending_map_id",null);long did=prefs.getLong("pending_download_id",0);int progress=pack.id.equals(pending)?FranceOfflineManager.downloadProgress(this,did):-1;String status=installed?(active?"Active • hors ligne":"Installée • "+bytes(FranceOfflineManager.file(this,pack).length())):(pack.id.equals(pending)?"Téléchargement"+(progress>=0?" • "+progress+" %":" en cours"):"≈ "+pack.sizeMb+" Mo • vectorielle");labels.addView(text(pack.label,15,Typeface.BOLD,Color.WHITE));labels.addView(text(status,11,Typeface.NORMAL,active?GREEN:MUTED));r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView action=pill(active?"Active":installed?"Activer":pack.id.equals(pending)?"…":"Télécharger",active?GREEN:accent(),11);action.setPadding(dp(11),0,dp(11),0);action.setEnabled(!active&&!pack.id.equals(pending));action.setAlpha(action.isEnabled()?1f:.55f);action.setOnClickListener(v->{if(installed)activateOffline(pack,true);else downloadPack(pack);});r.addView(action,new LinearLayout.LayoutParams(-2,dp(38)));if(installed){TextView menu=circle("•••",Color.argb(70,255,255,255));LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(dp(38),dp(38));ml.leftMargin=dp(6);r.addView(menu,ml);menu.setOnClickListener(v->offlineMenu(pack,active));}return r;
    }

    private void offlineMenu(FranceOfflineManager.Pack pack,boolean active){new AlertDialog.Builder(this).setTitle(pack.label).setItems(active?new String[]{"Revenir à la carte en ligne","Supprimer la carte"}:new String[]{"Activer","Supprimer la carte"},(d,w)->{if(w==0){if(active)activateOnline();else activateOffline(pack,true);}else new AlertDialog.Builder(this).setTitle("Supprimer "+pack.label+" ?").setMessage("Le fichier hors ligne sera supprimé du téléphone.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(x,y)->{if(active)activateOnline();if(FranceOfflineManager.delete(this,pack)){toast("Carte supprimée");showSettings();}}).show();}).show();}

    private void downloadPack(FranceOfflineManager.Pack p){try{long id=FranceOfflineManager.download(this,p);prefs.edit().putString("pending_map_id",p.id).putLong("pending_download_id",id).putBoolean("auto_activate_maps",true).apply();toast("Téléchargement de "+p.label+" lancé");showSettings();}catch(Exception e){toast("Téléchargement impossible");}}
    private void activateOffline(FranceOfflineManager.Pack p,boolean notify){if(applyOfflineProvider(p)){prefs.edit().putString("offline_pack",p.id).apply();updateMapChip();if(notify)toast(p.label+" activée hors ligne");showMap();}else toast("Carte hors ligne illisible");}
    private boolean applyOfflineProvider(FranceOfflineManager.Pack p){try{MapsForgeTileSource src=MapsForgeTileSource.createFromFiles(new File[]{FranceOfflineManager.file(this,p)},InternalRenderTheme.DEFAULT,"RoutePilotDefault");src.setUserScaleFactor(1.0f);map.setTileProvider(new MapsForgeTileProvider(new SimpleRegisterReceiver(this),src,null));map.setTilesScaledToDpi(false);map.resetTilesScaleFactor();map.setUseDataConnection(false);map.invalidate();return true;}catch(Exception e){return false;}}
    private void applyOnlineProvider(){MapTileProviderBasic online=new MapTileProviderBasic(getApplicationContext(),TileSourceFactory.MAPNIK);map.setTileProvider(online);map.setTileSource(TileSourceFactory.MAPNIK);map.setTilesScaledToDpi(true);map.resetTilesScaleFactor();map.setUseDataConnection(true);map.invalidate();}
    private void activateOnline(){applyOnlineProvider();prefs.edit().remove("offline_pack").apply();updateMapChip();toast("OpenStreetMap en ligne activée");showMap();}
    private void restoreOfflineMap(){FranceOfflineManager.Pack p=FranceOfflineManager.find(prefs.getString("offline_pack",null));if(p!=null&&FranceOfflineManager.isInstalled(this,p)){map.postDelayed(()->{if(!applyOfflineProvider(p)){prefs.edit().remove("offline_pack").apply();applyOnlineProvider();}updateMapChip();},120);}}
    private void checkPendingMapInstall(){String id=prefs.getString("pending_map_id",null);FranceOfflineManager.Pack p=FranceOfflineManager.find(id);if(p!=null&&FranceOfflineManager.isInstalled(this,p)){prefs.edit().remove("pending_map_id").remove("pending_download_id").apply();if(prefs.getBoolean("auto_activate_maps",true)){applyOfflineProvider(p);prefs.edit().putString("offline_pack",p.id).apply();toast(p.label+" installée et activée");}updateMapChip();}}
    private void mapSourceMenu(){List<String> labels=new ArrayList<>();List<FranceOfflineManager.Pack> packs=new ArrayList<>();labels.add("OpenStreetMap en ligne");packs.add(null);for(FranceOfflineManager.Pack p:FranceOfflineManager.PACKS)if(FranceOfflineManager.isInstalled(this,p)){labels.add(p.label+" • hors ligne");packs.add(p);}new AlertDialog.Builder(this).setTitle("Source de carte").setItems(labels.toArray(new String[0]),(d,w)->{if(packs.get(w)==null)activateOnline();else activateOffline(packs.get(w),true);}).show();}
    private void updateMapChip(){if(mapChip==null)return;FranceOfflineManager.Pack p=FranceOfflineManager.find(prefs.getString("offline_pack",null));mapChip.setText(p==null?"OSM":"OFF • "+p.label);mapChip.setTextColor(p==null?Color.WHITE:GREEN);}

    private void startRecording(){
        if(guiding){toast("Quitte d’abord le guidage");return;}if(!hasLocation()){ensureLocation();return;}points.clear();events.clear();liveDistance=0;liveTrack.setPoints(new ArrayList<>());clearLiveMarkers();store.clearDraft();recording=true;recordingCameraFollow=true;paused=false;startedAt=System.currentTimeMillis();pausedTotal=0;pauseStarted=0;lastDraftWrite=0;recordBtn.setText("Terminer et sauvegarder");recordBtn.setBackground(glass(RED,18));enableWorkButtons(true);timer.removeCallbacks(tick);timer.post(tick);haptic(recordBtn);toast("Tournée démarrée");
    }

    private void requestFinishRecording(){if(!recording)return;new AlertDialog.Builder(this).setTitle("Terminer la tournée ?").setMessage("La trace et les repères seront sauvegardés localement.").setNegativeButton("Continuer",null).setPositiveButton("Terminer",(d,w)->finishRecording()).show();}
    private void finishRecording(){
        if(!recording)return;if(paused&&pauseStarted>0)pausedTotal+=System.currentTimeMillis()-pauseStarted;recording=false;paused=false;timer.removeCallbacks(tick);if(points.size()<2){store.clearDraft();recordBtn.setText("Commencer l’enregistrement");recordBtn.setBackground(glass(accent(),18));enableWorkButtons(false);toast("Tournée trop courte : rien à sauvegarder");return;}lastRoute=store.createRoute(points,events,startedAt);store.clearDraft();recordBtn.setText("Commencer une nouvelle tournée");recordBtn.setBackground(glass(accent(),18));enableWorkButtons(false);refreshRecordingUi();if(lastRoute!=null)showFinishSummary(store.parse(lastRoute));else toast("Erreur de sauvegarde");
    }

    private void showFinishSummary(RouteStore.Summary s){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Tournée enregistrée",formatDuration(s.durationMs)+" • "+formatDistance(s.distanceM)+" • "+s.events.size()+" repères");EditText name=new EditText(this);name.setSingleLine(true);name.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);name.setText(store.displayName(s.file));name.setSelectAllOnFocus(true);name.setTextColor(Color.WHITE);name.setHintTextColor(MUTED);name.setTextSize(16);name.setPadding(dp(15),0,dp(15),0);name.setBackground(glass(Color.argb(65,255,255,255),18));panel.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));TextView save=sheetAction("✓  Enregistrer le nom",accent());save.setOnClickListener(v->{press(v);store.rename(s.file,name.getText().toString());d.dismiss();toast("Nom enregistré");});panel.addView(save,top(10));TextView plan=sheetAction("▣  Voir le plan",Color.argb(65,255,255,255));plan.setOnClickListener(v->{press(v);store.rename(s.file,name.getText().toString());d.dismiss();root.postDelayed(()->openPlan(store.parse(s.file)),130);});panel.addView(plan,top(7));TextView later=sheetAction("Plus tard",Color.argb(35,255,255,255));later.setOnClickListener(v->d.dismiss());panel.addView(later,top(7));presentBottomSheet(d,shell,panel);
    }

    private void togglePause(){if(!recording){toast("Aucune tournée en cours");return;}paused=!paused;if(paused){pauseStarted=System.currentTimeMillis();pauseBtn.setText("▶ Reprendre");saveDraft(true);}else{pausedTotal+=System.currentTimeMillis()-pauseStarted;pauseStarted=0;pauseBtn.setText("Ⅱ Pause");}refreshRecordingUi();}
    private void addEvent(String type,String label,int color){if(!recording||paused){toast(paused?"Reprends la tournée":"Commence une tournée");return;}if(lastLocation==null){toast("GPS indisponible");return;}RouteStore.Event e=new RouteStore.Event(type,label,lastLocation.getLatitude(),lastLocation.getLongitude(),System.currentTimeMillis(),lastLocation.getAccuracy());events.add(e);addSubtleMarker(new GeoPoint(e.lat,e.lon),color,"live");haptic("REVERSE".equals(type)?reverseBtn:sidesBtn);saveDraft(true);refreshRecordingUi();toast(label+" enregistré");}
    private void eventWithNote(String type,String label,int color){if(!recording||paused){toast("Commence ou reprends la tournée");return;}EditText input=new EditText(this);input.setHint("Ex. impasse étroite, portail…");input.setSingleLine(true);new AlertDialog.Builder(this).setTitle(label+" • ajouter une note").setView(input).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{String note=input.getText().toString().trim();addEvent(type,note.isEmpty()?label:label+" • "+note,color);}).show();}
    private void undoEvent(){if(events.isEmpty()){toast("Aucun repère à annuler");return;}events.remove(events.size()-1);clearLiveMarkers();for(int i=0;i<events.size();i++){RouteStore.Event e=events.get(i);addSubtleMarker(new GeoPoint(e.lat,e.lon),"REVERSE".equals(e.type)?ORANGE:CYAN,"live");}saveDraft(true);refreshRecordingUi();toast("Dernier repère annulé");}

    @Override public void onLocationChanged(@NonNull Location l){
        lastLocation=l;if(gpsChip!=null){int a=Math.round(l.getAccuracy());gpsChip.setText("±"+a+" m");gpsChip.setTextColor(a<=8?GREEN:a<=20?ORANGE:RED);}if(guiding){updateGuidance(l);return;}if(recording&&!paused&&l.getAccuracy()<=60&&append(l)){if(!points.isEmpty()){RouteStore.Point p=points.get(points.size()-1);float[] d=new float[1];Location.distanceBetween(p.lat,p.lon,l.getLatitude(),l.getLongitude(),d);if(d[0]<200)liveDistance+=d[0];}points.add(new RouteStore.Point(l.getLatitude(),l.getLongitude(),l.getTime()>0?l.getTime():System.currentTimeMillis(),l.getAccuracy()));List<GeoPoint> g=new ArrayList<>(liveTrack.getActualPoints());g.add(new GeoPoint(l.getLatitude(),l.getLongitude()));liveTrack.setPoints(g);if(recordingCameraFollow&&prefs.getBoolean("follow",true))map.getController().animateTo(g.get(g.size()-1));saveDraft(false);refreshRecordingUi();}map.invalidate();
    }
    private boolean append(Location l){if(points.isEmpty())return true;RouteStore.Point p=points.get(points.size()-1);float[] d=new float[1];Location.distanceBetween(p.lat,p.lon,l.getLatitude(),l.getLongitude(),d);long dt=(l.getTime()>0?l.getTime():System.currentTimeMillis())-p.time;return d[0]>=1.2||dt>=2000;}
    private void saveDraft(boolean force){long now=System.currentTimeMillis();if(!force&&now-lastDraftWrite<10000)return;lastDraftWrite=now;store.saveDraft(points,events,startedAt);}
    private void refreshRecordingUi(){if(timeText==null)return;if(recording){long now=paused?pauseStarted:System.currentTimeMillis();timeText.setText((paused?"En pause • ":"En cours • ")+formatDuration(Math.max(0,now-startedAt-pausedTotal)));}else timeText.setText(lastRoute==null?"Prêt à enregistrer":"Dernière tournée sauvegardée");distanceText.setText(formatDistance(liveDistance));eventsText.setText(String.valueOf(events.size()));pointsText.setText(String.valueOf(points.size()));}

    private void checkDraftRecovery(){if(recording||!store.hasDraft())return;RouteStore.Summary s=store.draftSummary();if(s.points.size()<2){store.clearDraft();return;}new AlertDialog.Builder(this).setTitle("Tournée interrompue trouvée").setMessage(formatDuration(s.durationMs)+" • "+formatDistance(s.distanceM)+" • "+s.events.size()+" repères\n\nTu peux reprendre exactement là où RoutePilot s’est arrêté.").setNegativeButton("Supprimer",(d,w)->store.clearDraft()).setNeutralButton("Enregistrer",(d,w)->saveRecoveredDraft(s)).setPositiveButton("Reprendre",(d,w)->resumeDraft(s)).show();}
    private void resumeDraft(RouteStore.Summary s){points.clear();events.clear();points.addAll(s.points);events.addAll(s.events);liveDistance=s.distanceM;startedAt=prefs.getLong("draft_started_at",s.firstTime>0?s.firstTime:System.currentTimeMillis());recording=true;paused=false;pausedTotal=0;liveTrack.setPoints(toGeo(points));clearLiveMarkers();for(int i=0;i<events.size();i++){RouteStore.Event e=events.get(i);addSubtleMarker(new GeoPoint(e.lat,e.lon),"REVERSE".equals(e.type)?ORANGE:CYAN,"live");}recordBtn.setText("Terminer et sauvegarder");recordBtn.setBackground(glass(RED,18));enableWorkButtons(true);timer.post(tick);refreshRecordingUi();toast("Tournée reprise");}
    private void saveRecoveredDraft(RouteStore.Summary s){File f=store.createRoute(s.points,s.events,s.firstTime>0?s.firstTime:System.currentTimeMillis());if(f!=null){lastRoute=f;store.clearDraft();toast("Tournée récupérée et sauvegardée");}else toast("Récupération impossible");}

    private void rename(File f){EditText e=new EditText(this);e.setText(store.displayName(f));e.setSelectAllOnFocus(true);e.setSingleLine();e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);new AlertDialog.Builder(this).setTitle("Renommer la tournée").setView(e).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{store.rename(f,e.getText().toString());showHistory();}).show();}
    private void deleteRoute(File f){new AlertDialog.Builder(this).setTitle("Supprimer cette tournée ?").setMessage("Le fichier GPX local sera supprimé définitivement.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{if(store.delete(f)){toast("Tournée supprimée");showHistory();}else toast("Suppression impossible");}).show();}
    private void share(File f){if(f==null||!f.exists()){toast("Fichier introuvable");return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/gpx+xml");i.putExtra(Intent.EXTRA_STREAM,FileProvider.getUriForFile(this,getPackageName()+".files",f));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Partager la tournée"));}
    private void importGpx(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/gpx+xml","application/xml","text/xml","text/plain"});startActivityForResult(i,IMPORT_GPX);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==IMPORT_GPX&&resultCode==RESULT_OK&&data!=null){File f=store.importGpx(data.getData());if(f!=null){toast("Tournée importée");showHistory();}else toast("GPX invalide ou sans trace");}}

    private void enterFocusMode(){if(guiding)return;focusMode=true;topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.GONE);if(planSheet!=null)planSheet.setVisibility(View.GONE);if(focusRestore==null){TextView restore=pill("← Retour",Color.argb(225,25,27,34),12);restore.setOnClickListener(v->exitFocusMode());focusRestore=restore;FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(102),dp(48),Gravity.BOTTOM|Gravity.END);p.setMargins(0,0,dp(16),safeBottom()+dp(16));root.addView(restore,p);}fade(focusRestore);}
    private void exitFocusMode(){focusMode=false;removePlanLegend();if(focusRestore!=null){root.removeView(focusRestore);focusRestore=null;}if(guiding)return;if(planSheet!=null){planSheet.setVisibility(View.VISIBLE);dock.setVisibility(View.VISIBLE);return;}if(page==null){topBar.setVisibility(View.VISIBLE);recordSheet.setVisibility(View.VISIBLE);dock.setVisibility(View.VISIBLE);}}
    private void setCollapsed(boolean value){collapsed=value;if(recordBody==null)return;if(collapsed){recordBody.setVisibility(View.GONE);collapseBtn.setText("⌃");}else{recordBody.setVisibility(View.VISIBLE);recordBody.setAlpha(1);collapseBtn.setText("⌄");}}

    private void clearPage(){stopPreviewPlayback();if(page!=null){root.removeView(page);page=null;}if(planSheet!=null){root.removeView(planSheet);planSheet=null;}clearPreview();}
    private void clearPreview(){if(previewTrack!=null){map.getOverlays().remove(previewTrack);previewTrack=null;}map.getOverlays().removeIf(o->o instanceof Marker&&((Marker)o).getRelatedObject() instanceof String&&((String)((Marker)o).getRelatedObject()).startsWith("preview"));previewCursor=null;map.invalidate();}
    private void clearLiveMarkers(){map.getOverlays().removeIf(o->o instanceof Marker&&"live".equals(((Marker)o).getRelatedObject()));map.invalidate();}
    private void addMarker(GeoPoint p,String badge,int color,String tag){Marker m=new Marker(map);m.setPosition(p);m.setAnchor(.5f,.5f);m.setIcon(marker(badge,color));m.setRelatedObject(tag);map.getOverlays().add(m);}
    private BitmapDrawable marker(String s,int color){int z=dp(40);Bitmap b=Bitmap.createBitmap(z,z,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShadowLayer(dp(5),0,dp(2),Color.argb(130,0,0,0));p.setColor(color);c.drawCircle(z/2f,z/2f,z*.44f,p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.WHITE);c.drawCircle(z/2f,z/2f,z*.39f,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(s.length()>1?10:13));Paint.FontMetrics fm=p.getFontMetrics();c.drawText(s,z/2f,z/2f-(fm.ascent+fm.descent)/2f,p);return new BitmapDrawable(getResources(),b);}
    private List<GeoPoint> toGeo(List<RouteStore.Point> pts){List<GeoPoint> out=new ArrayList<>();for(RouteStore.Point p:pts)out.add(new GeoPoint(p.lat,p.lon));return out;}

    private View infoCard(String title,String body){LinearLayout r=row();r.setOrientation(LinearLayout.VERTICAL);r.addView(text(title,15,Typeface.BOLD,Color.WHITE));TextView b=text(body,11,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);r.addView(b,lp);return r;}
    private View choice(String title,String value,View.OnClickListener l){LinearLayout r=row();LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,15,Typeface.BOLD,Color.WHITE));t.addView(text(value,11,Typeface.NORMAL,MUTED));r.addView(t,new LinearLayout.LayoutParams(0,-2,1));r.addView(text("›",25,Typeface.NORMAL,MUTED));r.setOnClickListener(l);return r;}
    private View toggle(String title,String sub,String key,boolean def){LinearLayout r=row();LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,15,Typeface.BOLD,Color.WHITE));t.addView(text(sub,11,Typeface.NORMAL,MUTED));r.addView(t,new LinearLayout.LayoutParams(0,-2,1));FrameLayout track=new FrameLayout(this);track.setPadding(dp(3),dp(3),dp(3),dp(3));View thumb=new View(this);thumb.setBackground(glass(Color.WHITE,99));track.addView(thumb,new FrameLayout.LayoutParams(dp(24),dp(24)));boolean[] checked={prefs.getBoolean(key,def)};applyToggle(track,thumb,checked[0],false);track.setOnClickListener(v->{checked[0]=!checked[0];prefs.edit().putBoolean(key,checked[0]).apply();applyToggle(track,thumb,checked[0],true);haptic(track);if("keep_screen_on".equals(key))applyKeepScreen();});r.addView(track,new LinearLayout.LayoutParams(dp(50),dp(30)));return r;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(15),dp(13),dp(13),dp(13));r.setBackground(glass(GLASS2,20));return r;}
    private TextView section(String s){TextView v=text(s,10,Typeface.BOLD,accent());v.setPadding(dp(2),0,0,dp(8));return v;}
    private LinearLayout pageBase(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(18),safeTop()+dp(18),dp(18),safeBottom()+dp(86));p.setBackgroundColor(BG);return p;}
    private TextView nav(String icon,String label){TextView v=text(icon+"\n"+label,11,Typeface.BOLD,MUTED);v.setGravity(Gravity.CENTER);return v;}
    private TextView text(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private TextView pill(String s,int color,float sp){TextView v=text(s,sp,Typeface.BOLD,Color.WHITE);v.setGravity(Gravity.CENTER);v.setBackground(glass(color,18));return v;}
    private TextView chip(String s,int color){TextView v=pill(s,color,10);v.setPadding(dp(9),0,dp(9),0);return v;}
    private TextView circle(String s,int color){return pill(s,color,15);}
    private TextView stat(String value,String tag){TextView v=text(value,15,Typeface.BOLD,Color.WHITE);v.setTag(tag);return v;}
    private View statCell(TextView v){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text((String)v.getTag(),9,Typeface.BOLD,Color.argb(135,255,255,255)));b.addView(v);return b;}
    private View summaryStat(String title,String value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text(title,9,Typeface.BOLD,Color.argb(135,255,255,255)));b.addView(text(value,14,Typeface.BOLD,Color.WHITE));return b;}
    private GradientDrawable glass(int color,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{shade(color,18),color,shade(color,-10)});g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.argb(45,255,255,255));return g;}
    private int shade(int c,int delta){return Color.argb(Color.alpha(c),Math.max(0,Math.min(255,Color.red(c)+delta)),Math.max(0,Math.min(255,Color.green(c)+delta)),Math.max(0,Math.min(255,Color.blue(c)+delta)));}
    private GradientDrawable rippleLike(){GradientDrawable g=new GradientDrawable();g.setColor(Color.TRANSPARENT);g.setCornerRadius(dp(14));return g;}
    private void press(View v){v.animate().scaleX(.955f).scaleY(.955f).alpha(.86f).setDuration(70).withEndAction(()->v.animate().scaleX(1).scaleY(1).alpha(1).setDuration(210).setInterpolator(new OvershootInterpolator(.8f)).start()).start();}
    private void fade(View v){v.setAlpha(0);v.setScaleX(.985f);v.setScaleY(.985f);v.setTranslationY(dp(14));v.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator()).start();}
    private void haptic(View v){if(prefs.getBoolean("haptic",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void enableWorkButtons(boolean e){for(TextView v:new TextView[]{reverseBtn,sidesBtn,pauseBtn,undoBtn}){v.setEnabled(e);v.setAlpha(e?1:.34f);}}
    private void selectTab(String s){for(TextView v:new TextView[]{navMap,navHistory,navSettings}){boolean on=(v==navMap&&"map".equals(s))||(v==navHistory&&"history".equals(s))||(v==navSettings&&"settings".equals(s));v.setTextColor(on?Color.WHITE:MUTED);v.setBackground(on?glass(Color.argb(100,Color.red(accent()),Color.green(accent()),Color.blue(accent())),18):null);}}


    private LinearLayout sheetPanel(String title,String subtitle){LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(8),dp(18),dp(18));panel.setBackground(glass(Color.argb(248,20,22,29),30));TextView handle=text("—",28,Typeface.BOLD,Color.argb(90,255,255,255));handle.setGravity(Gravity.CENTER);panel.addView(handle,new LinearLayout.LayoutParams(-1,dp(26)));panel.addView(text(title,22,Typeface.BOLD,Color.WHITE));if(subtitle!=null&&!subtitle.isEmpty()){TextView sub=text(subtitle,11,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(4);sp.bottomMargin=dp(14);panel.addView(sub,sp);}return panel;}
    private TextView sheetAction(String label,int color){TextView v=pill(label,color,13);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(16),0,dp(16),0);return v;}
    private void presentBottomSheet(Dialog d,FrameLayout shell,LinearLayout panel){shell.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));d.setContentView(shell);setBackdropBlur(true);d.setOnDismissListener(x->setBackdropBlur(false));d.show();android.view.Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.48f;w.setAttributes(a);}panel.setAlpha(0);panel.setTranslationY(dp(34));panel.setScaleX(.985f);panel.setScaleY(.985f);panel.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();}
    private void showToursActionSheet(){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Tournées","Créer, importer ou exporter une tournée");TextView fresh=sheetAction("＋  Nouvelle tournée",accent());fresh.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(this::openNewRecording,130);});panel.addView(fresh,bottom(8));TextView imp=sheetAction("⇩  Importer une tournée GPX",Color.argb(62,255,255,255));imp.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(this::importGpx,130);});panel.addView(imp,bottom(8));TextView exp=sheetAction("⇧  Exporter une tournée",Color.argb(62,255,255,255));exp.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(this::showExportRoutePicker,130);});panel.addView(exp,bottom(8));TextView cancel=sheetAction("Fermer",Color.argb(30,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel);presentBottomSheet(d,shell,panel);}
    private void showExportRoutePicker(){List<RouteStore.Summary> routes=store.summaries();if(routes.isEmpty()){toast("Aucune tournée à exporter");return;}Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Exporter une tournée","Choisis le plan GPX à partager ou enregistrer ailleurs");ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);for(RouteStore.Summary r:routes){TextView a=sheetAction("↗  "+store.displayName(r.file),Color.argb(52,255,255,255));a.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(()->share(r.file),130);});list.addView(a,bottom(7));}sc.addView(list);panel.addView(sc,new LinearLayout.LayoutParams(-1,Math.min(dp(360),dp(64)*routes.size())));presentBottomSheet(d,shell,panel);}
    private void showPlanLegend(){removePlanLegend();LinearLayout legend=new LinearLayout(this);legend.setOrientation(LinearLayout.VERTICAL);legend.setPadding(dp(13),dp(10),dp(13),dp(10));legend.setBackground(glass(Color.argb(225,18,20,26),20));legend.addView(text("LÉGENDE DU PLAN",9,Typeface.BOLD,accent()));legend.addView(text("D  Départ   •   A  Arrivée",11,Typeface.BOLD,Color.WHITE),top(4));legend.addView(text("MA  Marche arrière   •   2C  Deux côtés",11,Typeface.BOLD,Color.WHITE),top(3));planLegend=legend;FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);lp.setMargins(dp(14),safeTop()+dp(8),0,0);root.addView(planLegend,lp);fade(planLegend);}
    private void removePlanLegend(){if(planLegend!=null){root.removeView(planLegend);planLegend=null;}}
    private void addSubtleMarker(GeoPoint p,int color,String tag){Marker m=new Marker(map);m.setPosition(p);m.setAnchor(.5f,.5f);m.setIcon(subtleMarker(color));m.setRelatedObject(tag);map.getOverlays().add(m);}
    private BitmapDrawable subtleMarker(int color){int z=dp(16);Bitmap b=Bitmap.createBitmap(z,z,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.argb(75,Color.red(color),Color.green(color),Color.blue(color)));c.drawCircle(z/2f,z/2f,z*.32f,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(Color.argb(110,255,255,255));c.drawCircle(z/2f,z/2f,z*.32f,p);return new BitmapDrawable(getResources(),b);}
    private void makeGuidanceEventsSubtle(){for(org.osmdroid.views.overlay.Overlay o:map.getOverlays())if(o instanceof Marker&&"preview-event".equals(((Marker)o).getRelatedObject()))((Marker)o).setIcon(subtleMarker(accent()));map.invalidate();}
    private void showEventAlert(String type,String label){if(guideEventAlert==null)return;String key=type+"|"+label;if(!key.equals(lastEventAlertKey)){lastEventAlertKey=key;guideEventAlert.setText(("REVERSE".equals(type)?"↶  MARCHE ARRIÈRE":"⇆  2 CÔTÉS")+"  •  maintenant");guideEventAlert.setBackground(glass("REVERSE".equals(type)?Color.argb(235,255,159,10):Color.argb(235,100,210,255),20));guideEventAlert.setVisibility(View.VISIBLE);guideEventAlert.setAlpha(0);guideEventAlert.setTranslationY(-dp(10));guideEventAlert.animate().alpha(1).translationY(0).setDuration(220).setInterpolator(new OvershootInterpolator(.65f)).start();haptic(guideEventAlert);}else if(guideEventAlert.getVisibility()!=View.VISIBLE)guideEventAlert.setVisibility(View.VISIBLE);}
    private void hideEventAlert(){lastEventAlertKey="";if(guideEventAlert!=null&&guideEventAlert.getVisibility()==View.VISIBLE)guideEventAlert.animate().alpha(0).translationY(-dp(8)).setDuration(160).withEndAction(()->{if(guideEventAlert!=null)guideEventAlert.setVisibility(View.GONE);}).start();}

    private void accentDialog(){showColorPicker("Couleur d’accent","accent",new String[]{"Bleu","Violet","Vert","Orange","Cyan","Rose"});}
    private void traceColorDialog(){showColorPicker("Couleur de la trace","trace_color",new String[]{"Orange","Bleu","Vert","Violet","Cyan","Rose"});}
    private int namedColor(String s){if("Violet".equals(s))return PURPLE;if("Vert".equals(s))return GREEN;if("Orange".equals(s))return ORANGE;if("Cyan".equals(s))return CYAN;if("Rose".equals(s))return PINK;return BLUE;}
    private int accent(){return namedColor(prefs==null?"Bleu":prefs.getString("accent","Bleu"));}
    private int traceColor(){return namedColor(prefs==null?"Orange":prefs.getString("trace_color","Orange"));}

    private void showColorPicker(String title,String key,String[] names){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(10),dp(18),dp(18));panel.setBackground(glass(Color.argb(246,23,25,32),30));TextView handle=text("—",28,Typeface.BOLD,Color.argb(100,255,255,255));handle.setGravity(Gravity.CENTER);panel.addView(handle,new LinearLayout.LayoutParams(-1,dp(24)));panel.addView(text(title,23,Typeface.BOLD,Color.WHITE),bottom(10));String current=prefs.getString(key,names[0]);for(String name:names){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(11),dp(12),dp(11));TextView dot=text("●",24,Typeface.BOLD,namedColor(name));row.addView(dot,new LinearLayout.LayoutParams(dp(38),-2));TextView label=text(name,16,Typeface.BOLD,Color.WHITE);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView check=text(name.equals(current)?"✓":"",18,Typeface.BOLD,accent());row.addView(check);row.setBackground(glass(Color.argb(name.equals(current)?85:36,255,255,255),18));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));rp.bottomMargin=dp(7);panel.addView(row,rp);row.setOnClickListener(v->{press(v);prefs.edit().putString(key,name).apply();d.dismiss();root.postDelayed(this::recreate,150);});}shell.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));d.setContentView(shell);setBackdropBlur(true);d.setOnDismissListener(x->setBackdropBlur(false));d.show();android.view.Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.42f;w.setAttributes(a);}}

    private void showGuidanceStartSheet(RouteStore.Summary s){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(10),dp(18),dp(18));panel.setBackground(glass(Color.argb(246,23,25,32),30));TextView handle=text("—",28,Typeface.BOLD,Color.argb(100,255,255,255));handle.setGravity(Gravity.CENTER);panel.addView(handle,new LinearLayout.LayoutParams(-1,dp(24)));panel.addView(text("Démarrer la tournée",24,Typeface.BOLD,Color.WHITE));RouteStore.Point start=s.points.get(0);float distance=-1;if(lastLocation!=null){float[] dd=new float[1];Location.distanceBetween(lastLocation.getLatitude(),lastLocation.getLongitude(),start.lat,start.lon,dd);distance=dd[0];}String info=distance>70?"RoutePilot te guidera d’abord jusqu’au départ ("+formatDistance(distance)+"), puis suivra exactement la trace enregistrée.":"Le guidage suivra exactement la trace GPS enregistrée, sans la recalculer.";TextView sub=text(info,12,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(7);sp.bottomMargin=dp(16);panel.addView(sub,sp);TextView go=pill("➜ Démarrer",accent(),15);go.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(()->startGuidance(s,false),110);});panel.addView(go,new LinearLayout.LayoutParams(-1,dp(56)));TextView reverse=pill("↶ Sens inverse",GLASS2,13);reverse.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(()->startGuidance(s,true),110);});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48));rp.topMargin=dp(9);panel.addView(reverse,rp);TextView cancel=pill("Annuler",Color.argb(45,255,255,255),12);cancel.setOnClickListener(v->d.dismiss());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.topMargin=dp(8);panel.addView(cancel,cp);shell.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));d.setContentView(shell);setBackdropBlur(true);d.setOnDismissListener(x->setBackdropBlur(false));d.show();android.view.Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.42f;w.setAttributes(a);}}

    private void applyToggle(FrameLayout track,View thumb,boolean on,boolean animate){int target=on?accent():Color.argb(120,85,88,98);if(!animate){track.setBackground(glass(target,99));thumb.setTranslationX(on?dp(20):0);return;}int from=on?Color.argb(120,85,88,98):accent();ValueAnimator colors=ValueAnimator.ofArgb(from,target);colors.setDuration(220);colors.addUpdateListener(a->track.setBackground(glass((Integer)a.getAnimatedValue(),99)));colors.start();thumb.animate().translationX(on?dp(20):0).scaleX(.92f).scaleY(.92f).setDuration(110).withEndAction(()->thumb.animate().scaleX(1).scaleY(1).setDuration(180).setInterpolator(new OvershootInterpolator(.8f)).start()).start();}
    private void setBackdropBlur(boolean on){if(Build.VERSION.SDK_INT>=31&&root!=null)root.setRenderEffect(on?RenderEffect.createBlurEffect(dp(8),dp(8),Shader.TileMode.CLAMP):null);}

    private void requestApproachRoute(Location from,GeoPoint target){approachInstruction="Calcul de l’itinéraire routier…";new Thread(()->{List<GeoPoint> pts=new ArrayList<>();double roadDistance=-1;String instruction="Suis l’itinéraire bleu jusqu’au départ";try{String q=String.format(Locale.US,"https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",from.getLongitude(),from.getLatitude(),target.getLongitude(),target.getLatitude());HttpURLConnection c=(HttpURLConnection)new URL(q).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(9000);c.setRequestProperty("User-Agent",getPackageName()+" RoutePilot/1.0");BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder b=new StringBuilder();String line;while((line=br.readLine())!=null)b.append(line);br.close();JSONObject json=new JSONObject(b.toString());JSONArray routes=json.optJSONArray("routes");if(routes!=null&&routes.length()>0){JSONObject route=routes.getJSONObject(0);roadDistance=route.optDouble("distance",-1);JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");for(int i=0;i<coords.length();i++){JSONArray p=coords.getJSONArray(i);pts.add(new GeoPoint(p.getDouble(1),p.getDouble(0)));}JSONArray legs=route.optJSONArray("legs");if(legs!=null&&legs.length()>0){JSONArray steps=legs.getJSONObject(0).optJSONArray("steps");if(steps!=null&&steps.length()>1){JSONObject st=steps.getJSONObject(1);String name=st.optString("name","");instruction=name.isEmpty()?"Continue vers le départ":"Continue sur "+name;}}}}catch(Exception ignored){}double finalDistance=roadDistance;String finalInstruction=instruction;List<GeoPoint> finalPts=pts;runOnUiThread(()->{if(!guiding||!approachingStart)return;clearApproachRoute();if(finalPts.size()<2){finalPts.add(new GeoPoint(from.getLatitude(),from.getLongitude()));finalPts.add(target);approachInstruction="Trajet routier indisponible • cap direct vers le départ";}else approachInstruction=finalInstruction;if(finalDistance>0)approachDistanceM=finalDistance;approachTrack=new Polyline();approachTrack.getOutlinePaint().setColor(BLUE);approachTrack.getOutlinePaint().setStrokeWidth(dp(8));approachTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);approachTrack.setPoints(finalPts);map.getOverlays().add(approachTrack);map.invalidate();if(guidanceCameraFollow)map.postDelayed(()->{try{map.zoomToBoundingBox(approachTrack.getBounds(),true,dp(90));}catch(Exception ignored){}},100);});}).start();}
    private void clearApproachRoute(){if(approachTrack!=null){map.getOverlays().remove(approachTrack);approachTrack=null;if(map!=null)map.invalidate();}}

    private void recenter(){if(lastLocation==null){toast("Position GPS en attente");return;}if(guiding)guidanceCameraFollow=true;if(recording)recordingCameraFollow=true;map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()));if(guiding)map.getController().setZoom(17.4);else if(!recording)map.getController().setZoom(18.2);}
    private void ensureLocation(){if(hasLocation())enableLocation();else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);}
    private boolean hasLocation(){return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void enableLocation(){me.enableMyLocation();try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);}catch(SecurityException ignored){}}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==LOCATION_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)enableLocation();else new AlertDialog.Builder(this).setTitle("Localisation précise requise").setMessage("RoutePilot en a besoin pour enregistrer et rejouer fidèlement une tournée.").setPositiveButton("Réglages",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))).setNegativeButton("Plus tard",null).show();}
    private void applyKeepScreen(){if(prefs.getBoolean("keep_screen_on",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void resetOnboarding(){prefs.edit().putBoolean("onboarding_complete",false).apply();startActivity(new Intent(this,OnboardingActivity.class));finish();}

    private FrameLayout.LayoutParams topLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(68),Gravity.TOP);p.setMargins(dp(12),safeTop(),dp(12),0);return p;}
    private FrameLayout.LayoutParams sheetLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),safeBottom()+dp(84));return p;}
    private FrameLayout.LayoutParams dockLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(68),Gravity.BOTTOM);p.setMargins(dp(24),0,dp(24),safeBottom()+dp(7));return p;}
    private FrameLayout.LayoutParams pageLp(){return new FrameLayout.LayoutParams(-1,-1);}
    private FrameLayout.LayoutParams planLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(390),Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),safeBottom()+dp(84));return p;}
    private LinearLayout.LayoutParams bottom(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(x);return p;}
    private LinearLayout.LayoutParams top(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(x);return p;}
    private LinearLayout.LayoutParams weighted(float w,int mr){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),w);p.rightMargin=dp(mr);return p;}
    private int safeTop(){int id=getResources().getIdentifier("status_bar_height","dimen","android");return id>0?getResources().getDimensionPixelSize(id)+dp(7):dp(26);}
    private int safeBottom(){int id=getResources().getIdentifier("navigation_bar_height","dimen","android");int raw=id>0?getResources().getDimensionPixelSize(id):0;return Math.min(raw,dp(28));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private String formatDuration(long ms){long x=Math.max(0,ms/1000),h=x/3600,m=x%3600/60,s=x%60;return h>0?String.format(Locale.FRANCE,"%02d:%02d:%02d",h,m,s):String.format(Locale.FRANCE,"%02d:%02d",m,s);}
    private String formatDistance(double m){return m>=1000?String.format(Locale.FRANCE,"%.2f km",m/1000d):String.format(Locale.FRANCE,"%.0f m",m);}
    private String formatDate(long ms){return new java.text.SimpleDateFormat("EEEE d MMMM • HH:mm",Locale.FRANCE).format(new Date(ms));}
    private String timeOf(long ms){return ms>0?new java.text.SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date(ms)):"—";}
    private String avgSpeed(RouteStore.Summary s){if(s.durationMs<=0)return"—";return String.format(Locale.FRANCE,"%.1f km/h",(s.distanceM/1000d)/(s.durationMs/3600000d));}
    private String bytes(long b){return b<1024*1024?(b/1024)+" Ko":b<1024L*1024*1024?String.format(Locale.FRANCE,"%.0f Mo",b/1048576d):String.format(Locale.FRANCE,"%.1f Go",b/1073741824d);}

    @Override protected void onResume(){super.onResume();map.onResume();checkPendingMapInstall();if(hasLocation())enableLocation();}
    @Override protected void onPause(){if(recording)saveDraft(true);map.onPause();super.onPause();}
    @Override protected void onDestroy(){timer.removeCallbacks(tick);stopPreviewPlayback();try{lm.removeUpdates(this);}catch(SecurityException ignored){}if(me!=null)me.disableMyLocation();super.onDestroy();}
    @Override public void onBackPressed(){if(guiding){confirmStopGuidance();return;}if(focusMode){exitFocusMode();return;}if(planSheet!=null){closePlan();return;}if(page!=null){showMap();return;}if(recording){new AlertDialog.Builder(this).setTitle("Tournée en cours").setMessage("L’enregistrement continue. Tu peux revenir à la carte ou terminer la tournée.").setNegativeButton("Rester",null).setPositiveButton("Terminer",(d,w)->finishRecording()).show();return;}super.onBackPressed();}

    private interface TextConsumer{void accept(String s);}
    private static class SimpleWatcher implements android.text.TextWatcher{final TextConsumer c;SimpleWatcher(TextConsumer c){this.c=c;}public void beforeTextChanged(CharSequence s,int st,int c1,int a){}public void onTextChanged(CharSequence s,int st,int b,int c1){c.accept(s.toString());}public void afterTextChanged(android.text.Editable e){}}
}
