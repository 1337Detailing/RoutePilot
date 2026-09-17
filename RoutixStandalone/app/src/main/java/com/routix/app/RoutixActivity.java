package com.routix.app;

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
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoutixActivity extends AppCompatActivity implements LocationListener {
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
    private WorkHoursStore hoursStore;
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
    private TextView navMap,navHistory,navGps,navHours,navSettings;
    private CompactSpeedometer headerSpeed;
    private BoundedScrollView recordingScroll;
    private int systemTop=-1,systemBottom=-1;

    private boolean recording,paused,collapsed,focusMode,guiding,guidanceCameraFollow=true,recordingCameraFollow=true,approachingStart;
    private long startedAt,pauseStarted,pausedTotal,lastDraftWrite;
    private double liveDistance,approachDistanceM;
    private String approachInstruction="Calcul de l’itinéraire vers le départ…";
    private File lastRoute;

    private GuidanceEngine guidance,approachGuidance;
    private RouteArrowsOverlay routeArrows;
    private long guidanceSession;
    private boolean guidanceStartChecked;
    private final List<GuidanceEngine.Event> guidanceEvents=new ArrayList<>();
    private RouteStore.Summary guidingRoute;
    private boolean guidingReverse;
    private TextView guideArrow,guideTitle,guideSubtitle,guideProgress,guideRemaining,guideNext,guideDeviation,guideEventAlert,guideSpeed;

    private Runnable previewRunnable;
    private int previewIndex;
    private RouteStore.Summary previewSummary;
    private String lastEventAlertKey="";
    private final List<GuidanceEngine.Point> guidancePath=new ArrayList<>();
    private final List<GuidanceEngine.Point> approachPath=new ArrayList<>();

    private final Runnable tick=new Runnable(){@Override public void run(){refreshRecordingUi();if(recording)timer.postDelayed(this,500);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("routix",MODE_PRIVATE);
        store=new RouteStore(this,prefs);
        hoursStore=new WorkHoursStore(prefs);
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        applyKeepScreen();

        Configuration.getInstance().setUserAgentValue(getPackageName()+"/1.0 Routix");
        Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(),"osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(),"osmdroid/tiles"));
        MapsForgeTileSource.createInstance(getApplication());

        root=new FrameLayout(this);root.setBackground(appBackground());
        buildMap();buildChrome();setContentView(root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            systemTop=bars.top;systemBottom=bars.bottom;positionChrome();return insets;
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)positionChrome();});
        androidx.core.view.ViewCompat.requestApplyInsets(root);
        List<File> files=store.routeFiles();if(!files.isEmpty())lastRoute=files.get(0);
        ensureLocation();
        prefs.edit().remove("compact_ui").remove("reduce_motion").apply();
        root.postDelayed(this::checkDraftRecovery,650);
    }

    private void buildMap(){
        map=new MapView(this);map.setTileSource(TileSourceFactory.MAPNIK);map.setUseDataConnection(true);map.setMultiTouchControls(true);map.setTilesScaledToDpi(true);map.setMinZoomLevel(3.0);map.setMaxZoomLevel(20.0);map.getController().setZoom(18.2);map.setHorizontalMapRepetitionEnabled(false);map.setVerticalMapRepetitionEnabled(false);map.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(guiding)guidanceCameraFollow=false;if(recording)recordingCameraFollow=false;}return false;});root.addView(map,new FrameLayout.LayoutParams(-1,-1));
        me=new MyLocationNewOverlay(new GpsMyLocationProvider(this),map);me.setDrawAccuracyEnabled(true);applyPositionIcon();map.getOverlays().add(me);
        liveTrack=new Polyline();liveTrack.getOutlinePaint().setStrokeWidth(dp(7));liveTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);liveTrack.getOutlinePaint().setColor(traceColor());map.getOverlays().add(liveTrack);
        restoreOfflineMap();
    }

    private void buildChrome(){
        topBar=buildTopBar();recordSheet=buildRecordSheet();dock=buildDock();root.addView(topBar,topLp());root.addView(recordSheet,sheetLp());root.addView(dock,dockLp());selectTab("map");
    }

    private View buildTopBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(8),dp(8),dp(8));bar.setBackground(glass(GLASS,24));bar.setElevation(dp(16));
        headerSpeed=new CompactSpeedometer(this);bar.addView(headerSpeed,new LinearLayout.LayoutParams(0,dp(58),1));
        mapChip=chip("Carte",GLASS2);mapChip.setTextSize(11);mapChip.setSingleLine(true);mapChip.setEllipsize(android.text.TextUtils.TruncateAt.END);mapChip.setPadding(dp(4),0,dp(4),0);mapChip.setContentDescription("Choisir le fond de carte");mapChip.setOnClickListener(v->mapSourceMenu());
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(68),dp(44));mp.leftMargin=dp(6);bar.addView(mapChip,mp);
        // GPS quality is represented inside the speedometer, not a competing header badge.
        gpsChip=new TextView(this);
        TextView focus=circle("▣",GLASS2);focus.setContentDescription("Afficher la carte en plein écran");focus.setOnClickListener(v->enterFocusMode());LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(44),dp(44));fp.leftMargin=dp(6);bar.addView(focus,fp);
        TextView locate=circle("◎",accent());locate.setContentDescription("Recentrer sur ma position");locate.setOnClickListener(v->{press(v);recenter();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(44),dp(44));lp.leftMargin=dp(6);bar.addView(locate,lp);
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
        enableWorkButtons(false);recordingScroll=new BoundedScrollView(this);recordingScroll.setBackground(glass(GLASS,26));recordingScroll.addView(sheet);return recordingScroll;
    }

    private View buildDock(){
        LinearLayout dockRow=new LinearLayout(this);dockRow.setGravity(Gravity.CENTER);dockRow.setPadding(dp(6),dp(6),dp(6),dp(6));dockRow.setBackground(glass(Color.argb(245,21,24,32),25));dockRow.setElevation(dp(25));
        navMap=nav("⌖","Carte");navHistory=nav("≡","Tournées");navGps=nav("⌾","GPS");navHours=nav("◷","Heures");navSettings=nav("⚙","Réglages");
        navMap.setOnClickListener(v->showMap());navHistory.setOnClickListener(v->showHistory());navGps.setOnClickListener(v->startActivity(new Intent(this,ClassicNavigationActivity.class)));navHours.setOnClickListener(v->showHours());navSettings.setOnClickListener(v->showSettings());
        for(TextView tab:new TextView[]{navMap,navHistory,navGps,navHours,navSettings}){tab.setTextSize(10);tab.setMaxLines(2);tab.setMinWidth(0);tab.setPadding(0,0,0,0);dockRow.addView(tab,new LinearLayout.LayoutParams(0,dp(56),1));}
        return dockRow;
    }

    private void showMap(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.VISIBLE);topBar.setVisibility(View.VISIBLE);recordSheet.setVisibility(View.VISIBLE);dock.setVisibility(View.VISIBLE);selectTab("map");}
    private void showHistory(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=historyPage();root.addView(page,pageLp());selectTab("history");fade(page);}
    private void showHours(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=hoursPage();root.addView(page,pageLp());selectTab("hours");fade(page);}
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
        PrintablePlan.open(this,s,store.displayName(s.file));
    }

    private void openInteractivePlan(RouteStore.Summary s){
        if(s.points.size()<2){toast("Cette tournée ne contient pas assez de points GPS");return;}stopPreviewPlayback();if(page!=null)page.setVisibility(View.GONE);map.setVisibility(View.VISIBLE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);drawSummary(s,false);
        LinearLayout sh=new LinearLayout(this);sh.setOrientation(LinearLayout.VERTICAL);sh.setPadding(dp(15),dp(12),dp(15),dp(14));sh.setBackground(glass(GLASS3,29));sh.setElevation(dp(26));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout title=new LinearLayout(this);title.setOrientation(LinearLayout.VERTICAL);title.addView(text(store.displayName(s.file),18,Typeface.BOLD,Color.WHITE));title.addView(text(formatDuration(s.durationMs)+"  •  "+formatDistance(s.distanceM)+"  •  "+s.events.size()+" repères",11,Typeface.NORMAL,MUTED));head.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView focus=circle("▣",GLASS2);focus.setOnClickListener(v->enterFocusMode());LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(dp(40),dp(40));fl.rightMargin=dp(6);head.addView(focus,fl);TextView close=pill("Fermer",Color.argb(70,255,255,255),11);close.setOnClickListener(v->{press(v);closePlan();});head.addView(close,new LinearLayout.LayoutParams(dp(68),dp(40)));sh.addView(head);
        LinearLayout quick=new LinearLayout(this);quick.setPadding(0,dp(10),0,dp(5));quick.addView(summaryStat("DÉPART",timeOf(s.firstTime)),new LinearLayout.LayoutParams(0,-2,1));quick.addView(summaryStat("ARRIVÉE",timeOf(s.lastTime)),new LinearLayout.LayoutParams(0,-2,1));quick.addView(summaryStat("MOYENNE",avgSpeed(s)),new LinearLayout.LayoutParams(0,-2,1));sh.addView(quick);
        ScrollView scroll=new ScrollView(this);LinearLayout timeline=new LinearLayout(this);timeline.setOrientation(LinearLayout.VERTICAL);View start=timeline("D","Départ","Début de tournée",GREEN);start.setOnClickListener(v->centerPoint(s.points.get(0),19));timeline.addView(start);for(int i=0;i<s.events.size();i++){RouteStore.Event e=s.events.get(i);String sub=e.time>0&&s.firstTime>0?"+"+formatDuration(e.time-s.firstTime):"Repère "+(i+1);View row=timeline(String.valueOf(i+1),e.label,sub,"REVERSE".equals(e.type)?ORANGE:GREEN);final int index=i;row.setOnClickListener(v->centerEvent(s.events.get(index)));timeline.addView(row);}View end=timeline("A","Arrivée","Fin de tournée",RED);end.setOnClickListener(v->centerPoint(s.points.get(s.points.size()-1),19));timeline.addView(end);scroll.addView(timeline);LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,0,1);scrollLp.topMargin=dp(5);sh.addView(scroll,scrollLp);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(8),0,0);TextView preview=pill("▶ Aperçu animé",GLASS2,11);TextView full=pill("⛶ Plein plan",GLASS2,11);TextView startGuide=pill("➜ Démarrer",accent(),12);preview.setOnClickListener(v->enterPlanPreview(s));full.setOnClickListener(v->enterPlanOverview(s));startGuide.setOnClickListener(v->chooseGuidanceDirection(s));actions.addView(preview,weighted(.9f,4));actions.addView(full,weighted(.9f,4));actions.addView(startGuide,weighted(1.2f,0));sh.addView(actions);planSheet=sh;root.addView(sh,planLp());fade(sh);
    }

    private void drawSummary(RouteStore.Summary s,boolean reverse){
        clearPreview();previewTrack=new Polyline();previewTrack.getOutlinePaint().setColor(traceColor());previewTrack.getOutlinePaint().setStrokeWidth(dp(9));previewTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);List<GeoPoint> pts=new ArrayList<>();if(reverse){for(int i=s.points.size()-1;i>=0;i--)pts.add(new GeoPoint(s.points.get(i).lat,s.points.get(i).lon));}else for(RouteStore.Point p:s.points)pts.add(new GeoPoint(p.lat,p.lon));previewTrack.setPoints(pts);map.getOverlays().add(previewTrack);addMarker(pts.get(0),"D",GREEN,"preview");for(RouteStore.Event e:s.events){String badge="REVERSE".equals(e.type)?"MA":"2C";addMarker(new GeoPoint(e.lat,e.lon),badge,"REVERSE".equals(e.type)?ORANGE:CYAN,"preview-event");}addMarker(pts.get(pts.size()-1),"A",RED,"preview");if(!guiding)fitSummary(s);map.invalidate();
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
        stopPreviewPlayback();if(planSheet!=null){root.removeView(planSheet);planSheet=null;}if(page!=null)page.setVisibility(View.GONE);
        guidanceSession++;guidanceStartChecked=false;approachingStart=false;clearApproachRoute();
        guiding=true;guidingRoute=s;guidingReverse=reverse;guidanceCameraFollow=true;lastEventAlertKey="";
        drawSummary(s,reverse);liveTrack.setPoints(new ArrayList<>());
        previewTrack.getOutlinePaint().setColor(BLUE);previewTrack.getOutlinePaint().setStrokeWidth(dp(11));
        map.setVisibility(View.VISIBLE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.GONE);
        guidancePath.clear();
        if(reverse){for(int i=s.points.size()-1;i>=0;i--){RouteStore.Point p=s.points.get(i);guidancePath.add(new GuidanceEngine.Point(p.lat,p.lon));}}
        else for(RouteStore.Point p:s.points)guidancePath.add(new GuidanceEngine.Point(p.lat,p.lon));
        guidanceEvents.clear();
        map.getOverlays().removeIf(o->o instanceof Marker&&"preview-event".equals(((Marker)o).getRelatedObject()));
        for(RouteStore.Event event:s.events){
            int index=0;double best=Double.MAX_VALUE;
            for(int i=0;i<s.points.size();i++){
                RouteStore.Point p=s.points.get(i);
                double d=event.time>0&&p.time>0?Math.abs((double)event.time-p.time):Math.hypot(event.lat-p.lat,(event.lon-p.lon)*Math.cos(Math.toRadians(event.lat)));
                if(d<best){best=d;index=i;}
            }
            GuidanceEngine.Event e=new GuidanceEngine.Event(event.type,event.label,event.lat,event.lon,reverse?s.points.size()-1-index:index);guidanceEvents.add(e);
            Marker marker=new Marker(map);marker.setPosition(new GeoPoint(e.lat,e.lon));marker.setAnchor(.5f,.5f);marker.setIcon(marker("REVERSE".equals(e.type)?"MA":"2C","REVERSE".equals(e.type)?ORANGE:CYAN));marker.setRelatedObject(e);map.getOverlays().add(marker);
        }
        guidance=new GuidanceEngine(guidancePath,guidanceEvents);
        routeArrows=new RouteArrowsOverlay();routeArrows.setPoints(previewTrack.getActualPoints());map.getOverlays().add(routeArrows);
        map.getOverlays().remove(me);map.getOverlays().add(me);
        guidanceHud=buildGuidanceHud();root.addView(guidanceHud,new FrameLayout.LayoutParams(-1,-1));fade(guidanceHud);
        if(lastLocation!=null)updateGuidance(lastLocation);else{guideTitle.setText("Position GPS en attente");guideSubtitle.setText("La carte te guidera avec des flèches.");}
    }

    private View buildGuidanceHud(){
        FrameLayout layer=new FrameLayout(this);layer.setClickable(false);
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(8),dp(12),dp(8));card.setBackground(glass(Color.argb(235,15,18,25),22));
        guideArrow=text("↑",32,Typeface.BOLD,CYAN);guideArrow.setGravity(Gravity.CENTER);card.addView(guideArrow,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);guideTitle=text("Suis les flèches",20,Typeface.BOLD,Color.WHITE);guideSubtitle=text("La trace s’efface après ton passage",12,Typeface.NORMAL,MUTED);info.addView(guideTitle);info.addView(guideSubtitle);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);cp.setMargins(dp(10),safeTop(),dp(10),0);layer.addView(card,cp);
        guideEventAlert=pill("",Color.argb(245,255,159,10),14);guideEventAlert.setVisibility(View.GONE);FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-1,dp(48),Gravity.TOP);ep.setMargins(dp(12),safeTop()+dp(80),dp(12),0);layer.addView(guideEventAlert,ep);
        guideSpeed=new TextView(this);guideDeviation=new TextView(this);
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(14),dp(10),dp(14),dp(10));bottom.setBackground(glass(Color.argb(235,15,18,25),24));
        LinearLayout stats=new LinearLayout(this);stats.setGravity(Gravity.CENTER_VERTICAL);
        guideRemaining=text("—",27,Typeface.BOLD,Color.WHITE);guideRemaining.setTag("DISTANCE RESTANTE");stats.addView(statCell(guideRemaining),new LinearLayout.LayoutParams(0,-2,1));
        guideProgress=text("0 %",18,Typeface.BOLD,CYAN);stats.addView(guideProgress);bottom.addView(stats);
        guideNext=text("Suis la ligne bleue et ses flèches",12,Typeface.NORMAL,MUTED);bottom.addView(guideNext,top(5));
        LinearLayout controls=new LinearLayout(this);controls.setPadding(0,dp(8),0,0);
        TextView follow=pill("Me suivre",accent(),12),resume=pill("Reprendre ici",GLASS2,11),quit=pill("Quitter",Color.argb(160,255,69,58),12);
        follow.setOnClickListener(v->{guidanceCameraFollow=true;map.getController().setZoom(18.0);if(lastLocation!=null)updateGuidance(lastLocation);});
        resume.setOnClickListener(v->resumeGuidanceHere());quit.setOnClickListener(v->confirmStopGuidance());
        controls.addView(follow,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(resume,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(quit,new LinearLayout.LayoutParams(0,dp(48),.75f));bottom.addView(controls);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);bp.setMargins(dp(10),0,dp(10),safeBottom()+dp(8));layer.addView(bottom,bp);
        map.getController().setZoom(18.0);return layer;
    }

    private void resumeGuidanceHere(){
        if(lastLocation==null||lastLocation.getAccuracy()>35){toast("Attends une position GPS précise");return;}
        confirmSheet("Reprendre à ta position ?","La partie avant le point le plus proche sera marquée comme parcourue. Si la tournée passe plusieurs fois ici, vérifie le passage choisi.","Annuler","Reprendre ici",BLUE,()->{
            if(!guiding||guidance==null||lastLocation==null)return;
            if(!guidance.reposition(lastLocation.getLatitude(),lastLocation.getLongitude())){toast("Rapproche-toi du tracé (moins de 45 m)");return;}
            approachingStart=false;guidanceStartChecked=true;clearApproachRoute();guidanceCameraFollow=true;updateGuidance(lastLocation);
        });
    }

    private void updateGuidance(Location l){
        if(!guiding||guidance==null||!guidance.isUsable())return;
        if(!l.hasAccuracy()||l.getAccuracy()>35||android.os.SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos()>15000000000L){
            guideArrow.setText("◎");guideTitle.setText("GPS imprécis ou en attente");guideTitle.setTextColor(ORANGE);
            guideSubtitle.setText("Progression conservée • attends un signal fiable");hideEventAlert();return;
        }
        if(!guidanceStartChecked){
            guidanceStartChecked=true;
            GuidanceEngine.Point first=guidancePath.get(0);float[] d=new float[1];Location.distanceBetween(l.getLatitude(),l.getLongitude(),first.lat,first.lon,d);
            approachingStart=d[0]>70;
            if(approachingStart)requestApproachRoute(l,new GeoPoint(first.lat,first.lon));
        }
        if(approachingStart){
            GuidanceEngine.Point first=guidancePath.get(0);float[] d=new float[1];Location.distanceBetween(l.getLatitude(),l.getLongitude(),first.lat,first.lon,d);
            if(d[0]<=25){approachingStart=false;clearApproachRoute();guidance.reset();toast("Départ rejoint");}
            else{
                float remaining=d[0];boolean alongRoad=false;
                if(approachGuidance!=null){
                    GuidanceEngine.State a=approachGuidance.update(l.getLatitude(),l.getLongitude(),l.getElapsedRealtimeNanos()/1000000,l.getAccuracy());
                    alongRoad=!a.offRoute;remaining=alongRoad?a.remainingM:d[0];
                    if(alongRoad&&approachTrack!=null)updateRemainingTrace(approachTrack,approachGuidance,a);
                }
                guideArrow.setText("↑");guideTitle.setText("Rejoins le départ");guideTitle.setTextColor(Color.WHITE);
                guideRemaining.setText(formatDistance(remaining));guideProgress.setText("DÉPART");
                guideSubtitle.setText(alongRoad?"Suis les flèches bleues":"Distance directe au départ • pas un itinéraire routier");
                guideNext.setText("Déjà sur la tournée ? Appuie sur Reprendre ici");
                if(previewTrack!=null)previewTrack.setEnabled(false);
                if(routeArrows!=null&&!alongRoad)routeArrows.setPoints(Collections.emptyList());
                followGuidanceCamera(l);map.invalidate();return;
            }
        }
        GuidanceEngine.State state=guidance.update(l.getLatitude(),l.getLongitude(),l.getElapsedRealtimeNanos()/1000000,l.getAccuracy());
        if(previewTrack!=null){previewTrack.setEnabled(true);updateRemainingTrace(previewTrack,guidance,state);}
        if(state.nextEvent!=null&&state.distanceToNextEventM<=35&&!state.offRoute)showEventAlert(state.nextEvent.type,state.nextEvent.label);else hideEventAlert();
        guideRemaining.setText(formatDistance(state.remainingM));guideProgress.setText(state.progressPercent+" %");
        if(state.finished){guideArrow.setText("✓");guideTitle.setText("Tournée terminée");guideTitle.setTextColor(GREEN);guideSubtitle.setText("Arrivée atteinte");guideNext.setText("Tu peux quitter le guidage");}
        else if(state.offRoute){guideArrow.setText("↩");guideTitle.setText("Rejoins la ligne bleue");guideTitle.setTextColor(ORANGE);guideSubtitle.setText(Math.round(state.distanceToTraceM)+" m du parcours suivi");guideNext.setText("Progression conservée • Reprendre ici pour te recaler");}
        else{guideArrow.setText("↑");guideTitle.setText("Suis les flèches");guideTitle.setTextColor(Color.WHITE);guideSubtitle.setText("La trace derrière toi est effacée");guideNext.setText(state.nextEvent==null?"Ligne bleue = parcours restant":state.nextEvent.label+" dans "+formatDistance(state.distanceToNextEventM));}
        map.getOverlays().removeIf(o->o instanceof Marker&&((Marker)o).getRelatedObject() instanceof GuidanceEngine.Event&&((GuidanceEngine.Event)((Marker)o).getRelatedObject()).routeIndex<state.nearestIndex);
        followGuidanceCamera(l);map.invalidate();
    }

    private void updateRemainingTrace(Polyline line,GuidanceEngine engine,GuidanceEngine.State state){
        List<GeoPoint> remaining=new ArrayList<>();
        if(!state.finished){
            remaining.add(new GeoPoint(state.position.lat,state.position.lon));
            List<GuidanceEngine.Point> source=engine==guidance?guidancePath:approachPath;
            for(int i=state.nearestIndex+1;i<source.size();i++)remaining.add(new GeoPoint(source.get(i).lat,source.get(i).lon));
        }
        line.setPoints(remaining);if(routeArrows!=null)routeArrows.setPoints(remaining);
    }

    private void followGuidanceCamera(Location l){
        if(!guidanceCameraFollow)return;
        map.getController().animateTo(new GeoPoint(l.getLatitude(),l.getLongitude()));
        if(l.hasBearing()&&l.hasSpeed()&&l.getSpeed()>1.5f)map.setMapOrientation(-l.getBearing());
    }

    private static final class TurnCue{final String arrow,label;final float distanceM;TurnCue(String arrow,String label,float distanceM){this.arrow=arrow;this.label=label;this.distanceM=distanceM;}}
    private TurnCue nextTurnCue(GuidanceEngine.State state){
        if(guidancePath.size()<4)return new TurnCue("↑","Continue sur la trace",Math.min(120,state.remainingM));
        int start=Math.max(0,Math.min(guidancePath.size()-2,state.nearestIndex));float along=0;
        for(int i=start+1;i<guidancePath.size()-2;i++){
            along+=pathDistance(guidancePath.get(i-1),guidancePath.get(i));if(along>650)break;if(along<16)continue;
            int mid=indexAhead(i,18);int far=indexAhead(mid,24);if(mid<=i||far<=mid)continue;float h1=pathBearing(guidancePath.get(i),guidancePath.get(mid));float h2=pathBearing(guidancePath.get(mid),guidancePath.get(far));float delta=normalizeTurn(h2-h1);float abs=Math.abs(delta);if(abs<38)continue;
            if(abs>145)return new TurnCue("↶","Demi-tour",along);
            if(delta>0)return new TurnCue(abs>78?"↱":"↗",abs>78?"Tourne franchement à droite":"Tourne à droite",along);
            return new TurnCue(abs>78?"↰":"↖",abs>78?"Tourne franchement à gauche":"Tourne à gauche",along);
        }
        return new TurnCue("↑","Continue sur la trace",Math.min(220,state.remainingM));
    }
    private int indexAhead(int start,float meters){float d=0;for(int i=start+1;i<guidancePath.size();i++){d+=pathDistance(guidancePath.get(i-1),guidancePath.get(i));if(d>=meters)return i;}return guidancePath.size()-1;}
    private float pathDistance(GuidanceEngine.Point a,GuidanceEngine.Point b){float[] out=new float[1];Location.distanceBetween(a.lat,a.lon,b.lat,b.lon,out);return out[0];}
    private float pathBearing(GuidanceEngine.Point a,GuidanceEngine.Point b){double lat1=Math.toRadians(a.lat),lat2=Math.toRadians(b.lat),dLon=Math.toRadians(b.lon-a.lon);double y=Math.sin(dLon)*Math.cos(lat2),x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);return (float)Math.toDegrees(Math.atan2(y,x));}
    private float normalizeTurn(float a){while(a>180)a-=360;while(a<-180)a+=360;return a;}

    private void confirmStopGuidance(){confirmSheet("Quitter le guidage ?","La tournée enregistrée ne sera pas modifiée.","Continuer","Quitter",RED,this::stopGuidance);}
    private void stopGuidance(){guidanceSession++;guiding=false;guidanceCameraFollow=true;approachingStart=false;clearApproachRoute();guidance=null;guidancePath.clear();guidingRoute=null;guideEventAlert=null;lastEventAlertKey="";map.setMapOrientation(0);if(guidanceHud!=null){root.removeView(guidanceHud);guidanceHud=null;}clearPreview();showHistory();}

    private View timeline(String badge,String title,String sub,int color){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(7),0,dp(7));TextView b=circle(badge,color);r.addView(b,new LinearLayout.LayoutParams(dp(34),dp(34)));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,13,Typeface.BOLD,Color.WHITE));t.addView(text(sub,11,Typeface.NORMAL,MUTED));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(10);r.addView(t,lp);r.setBackground(rippleLike());return r;}

    private View hoursPage(){
        LinearLayout p=pageBase();
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("Heures",30,Typeface.BOLD,Color.WHITE));titles.addView(text("Suivi local • calcul mensuel automatique",12,Typeface.NORMAL,MUTED));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView plus=circle("＋",accent());plus.setOnClickListener(v->{press(v);showHoursEntrySheet();});head.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(46)));p.addView(head);

        String month=currentMonthKey();int total=hoursStore.totalForMonth(month);LinearLayout totalCard=new LinearLayout(this);totalCard.setOrientation(LinearLayout.VERTICAL);totalCard.setPadding(dp(18),dp(17),dp(18),dp(17));totalCard.setBackground(glass(Color.argb(150,28,52,84),27));totalCard.setElevation(dp(12));
        totalCard.addView(text(monthLabel(month).toUpperCase(Locale.FRANCE),10,Typeface.BOLD,CYAN));TextView big=text(formatWorkMinutes(total),32,Typeface.BOLD,Color.WHITE);LinearLayout.LayoutParams bigLp=new LinearLayout.LayoutParams(-1,-2);bigLp.topMargin=dp(5);totalCard.addView(big,bigLp);totalCard.addView(text("temps net après déduction des pauses",11,Typeface.NORMAL,MUTED));LinearLayout.LayoutParams tc=new LinearLayout.LayoutParams(-1,-2);tc.topMargin=dp(16);tc.bottomMargin=dp(18);p.addView(totalCard,tc);

        ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        Map<String,Integer> totals=hoursStore.totalsByMonth();if(!totals.isEmpty()){body.addView(section("TOTAL PAR MOIS"));for(Map.Entry<String,Integer> e:totals.entrySet()){LinearLayout r=row();r.addView(text(monthLabel(e.getKey()),14,Typeface.BOLD,Color.WHITE),new LinearLayout.LayoutParams(0,-2,1));r.addView(text(formatWorkMinutes(e.getValue()),15,Typeface.BOLD,accent()));body.addView(r,bottom(8));}}
        body.addView(section("JOURNÉES"),top(10));List<WorkHoursStore.Entry> entries=hoursStore.entries();if(entries.isEmpty()){LinearLayout empty=new LinearLayout(this);empty.setOrientation(LinearLayout.VERTICAL);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(58),0,dp(30));empty.addView(text("◷",44,Typeface.NORMAL,Color.argb(90,255,255,255)));empty.addView(text("Aucune heure enregistrée",18,Typeface.BOLD,Color.WHITE));empty.addView(text("Appuie sur + pour ajouter ta première journée.",12,Typeface.NORMAL,MUTED));body.addView(empty);}else for(WorkHoursStore.Entry e:entries)body.addView(hoursCard(e),bottom(9));
        return p;
    }

    private View hoursCard(WorkHoursStore.Entry e){
        LinearLayout c=row();c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(14),dp(14));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);LinearLayout date=new LinearLayout(this);date.setOrientation(LinearLayout.VERTICAL);date.addView(text(dayLabel(e.date),15,Typeface.BOLD,Color.WHITE));date.addView(text(e.date,10,Typeface.NORMAL,MUTED));top.addView(date,new LinearLayout.LayoutParams(0,-2,1));TextView net=pill(formatWorkMinutes(e.netMinutes()),Color.argb(115,10,132,255),12);top.addView(net,new LinearLayout.LayoutParams(-2,dp(36)));c.addView(top);
        LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(12),0,0);stats.addView(summaryStat("DÉBUT",timeLabel(e.startMinutes)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("FIN",timeLabel(e.endMinutes)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("PAUSE",e.pauseMinutes+" min"),new LinearLayout.LayoutParams(0,-2,1));c.addView(stats);
        TextView del=text("Supprimer",11,Typeface.BOLD,Color.argb(220,255,90,82));del.setGravity(Gravity.END);del.setPadding(dp(10),dp(10),0,0);del.setOnClickListener(v->{press(v);hoursStore.delete(e.id);showHours();});c.addView(del,new LinearLayout.LayoutParams(-1,-2));return c;
    }

    private void showHoursEntrySheet(){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));String date=todayKey();LinearLayout panel=sheetPanel("Ajouter des heures",dayLabel(date)+" • "+date);
        EditText start=hoursInput("Début • ex. 730",prefs.getString("hours_last_start",""),InputType.TYPE_CLASS_NUMBER);EditText end=hoursInput("Fin • ex. 1530",prefs.getString("hours_last_end",""),InputType.TYPE_CLASS_NUMBER);EditText pause=hoursInput("Pause • minutes",String.valueOf(prefs.getInt("hours_last_pause",0)),InputType.TYPE_CLASS_NUMBER);
        start.setOnFocusChangeListener((v,has)->{if(!has){int m=parseClock(start.getText().toString());if(m>=0)start.setText(timeLabel(m));}});end.setOnFocusChangeListener((v,has)->{if(!has){int m=parseClock(end.getText().toString());if(m>=0)end.setText(timeLabel(m));}});
        panel.addView(start,new LinearLayout.LayoutParams(-1,dp(52)));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(8);panel.addView(end,ep);
        LinearLayout now=new LinearLayout(this);TextView startNow=pill("Début = maintenant",Color.argb(48,255,255,255),10);TextView endNow=pill("Fin = maintenant",Color.argb(48,255,255,255),10);startNow.setOnClickListener(v->{press(v);start.setText(timeLabel(nowMinutes()));});endNow.setOnClickListener(v->{press(v);end.setText(timeLabel(nowMinutes()));});LinearLayout.LayoutParams n1=new LinearLayout.LayoutParams(0,dp(38),1);n1.rightMargin=dp(4);LinearLayout.LayoutParams n2=new LinearLayout.LayoutParams(0,dp(38),1);n2.leftMargin=dp(4);now.addView(startNow,n1);now.addView(endNow,n2);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(8);panel.addView(now,np);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(52));pp.topMargin=dp(10);panel.addView(pause,pp);
        TextView hint=text("Pas besoin de taper « : » : 730, 0730 ou 7:30 deviennent 07:30 automatiquement. La date du jour est ajoutée toute seule.",10,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(9);hp.bottomMargin=dp(10);panel.addView(hint,hp);
        LinearLayout quick=new LinearLayout(this);for(int m:new int[]{0,20,30,45,60}){TextView q=pill(m+" min",Color.argb(42,255,255,255),10);q.setOnClickListener(v->{press(v);pause.setText(String.valueOf(m));});LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,dp(36),1);qp.rightMargin=dp(4);quick.addView(q,qp);}panel.addView(quick);
        TextView save=sheetAction("✓  Enregistrer la journée",accent());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.topMargin=dp(12);panel.addView(save,sp);save.setOnClickListener(v->{int a=parseClock(start.getText().toString());int b=parseClock(end.getText().toString());int breakM=parsePositive(pause.getText().toString());if(a<0||b<0){shake(start);shake(end);toast("Heure invalide • tape par exemple 730 ou 1530");return;}int gross=(b>=a?b-a:b+1440-a);if(breakM>=gross){shake(pause);toast("La pause doit être plus courte que le service");return;}hoursStore.add(date,a,b,breakM);prefs.edit().putString("hours_last_start",timeLabel(a)).putString("hours_last_end",timeLabel(b)).putInt("hours_last_pause",breakM).apply();press(v);d.dismiss();root.postDelayed(this::showHours,120);});
        presentBottomSheet(d,shell,panel);root.postDelayed(()->start.requestFocus(),180);
    }

    private EditText hoursInput(String hint,String value,int type){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setHintTextColor(Color.argb(115,255,255,255));e.setTextColor(Color.WHITE);e.setTextSize(15);e.setInputType(type);e.setPadding(dp(15),0,dp(15),0);e.setBackground(glass(Color.argb(62,255,255,255),18));if(value!=null)e.setText(value);return e;}
    private int parseClock(String raw){try{String t=raw.trim().replace('.',':');int h,m;if(t.contains(":")){String[] p=t.split(":");if(p.length!=2)return-1;h=Integer.parseInt(p[0]);m=Integer.parseInt(p[1]);}else{String digits=t.replaceAll("[^0-9]","");if(digits.length()==1||digits.length()==2){h=Integer.parseInt(digits);m=0;}else if(digits.length()==3){h=Integer.parseInt(digits.substring(0,1));m=Integer.parseInt(digits.substring(1));}else if(digits.length()==4){h=Integer.parseInt(digits.substring(0,2));m=Integer.parseInt(digits.substring(2));}else return-1;}return h>=0&&h<=23&&m>=0&&m<=59?h*60+m:-1;}catch(Exception e){return-1;}}
    private int parsePositive(String raw){try{return Math.max(0,Integer.parseInt(raw.trim()));}catch(Exception e){return 0;}}
    private int nowMinutes(){Calendar c=Calendar.getInstance();return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);}
    private String todayKey(){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date());}
    private String currentMonthKey(){return new java.text.SimpleDateFormat("yyyy-MM",Locale.FRANCE).format(new Date());}
    private String timeLabel(int m){return String.format(Locale.FRANCE,"%02d:%02d",(m/60)%24,m%60);}
    private String formatWorkMinutes(int m){return String.format(Locale.FRANCE,"%d h %02d",m/60,m%60);}
    private String monthLabel(String key){try{return new java.text.SimpleDateFormat("MMMM yyyy",Locale.FRANCE).format(new java.text.SimpleDateFormat("yyyy-MM",Locale.FRANCE).parse(key));}catch(Exception e){return key;}}
    private String dayLabel(String key){try{return new java.text.SimpleDateFormat("EEEE d MMMM",Locale.FRANCE).format(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).parse(key));}catch(Exception e){return key;}}
    private void shake(View v){v.animate().translationX(dp(8)).setDuration(55).withEndAction(()->v.animate().translationX(-dp(8)).setDuration(55).withEndAction(()->v.animate().translationX(0).setDuration(55).start()).start()).start();}

    private View settingsPage(){
        LinearLayout p=pageBase();p.addView(text("Réglages",30,Typeface.BOLD,Color.WHITE));TextView sub=text("Personnalisation • GPS • cartes hors ligne",12,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams spl=new LinearLayout.LayoutParams(-1,-2);spl.bottomMargin=dp(18);p.addView(sub,spl);ScrollView sc=new ScrollView(this);LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);sc.addView(c);p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        c.addView(section("APPARENCE"));c.addView(choice("Couleur d’accent",prefs.getString("accent","Bleu"),v->accentDialog()),bottom(9));c.addView(choice("Couleur de la trace",prefs.getString("trace_color","Orange"),v->traceColorDialog()),bottom(9));c.addView(choice("Icône sur la carte",prefs.getString("position_icon","Camion"),v->positionIconDialog()),bottom(9));
        c.addView(section("GPS & CONFORT"),top(12));c.addView(toggle("Suivre ma position","Recentre pendant l’enregistrement et le guidage","follow",true),bottom(9));c.addView(toggle("Écran toujours allumé","Évite la mise en veille pendant le travail","keep_screen_on",true),bottom(9));c.addView(toggle("Retour haptique","Vibration légère sur les actions métier","haptic",true),bottom(9));c.addView(infoCard("Précision de trace","1 seconde • filtrage des points GPS aberrants • brouillon de secours automatique"),bottom(9));
        c.addView(section("CARTES HORS LIGNE • FRANCE"),top(12));c.addView(onlineMapRow(),bottom(9));for(FranceOfflineManager.Pack pack:FranceOfflineManager.PACKS)c.addView(offlineRow(pack),bottom(8));c.addView(infoCard("Stockage cartes",bytes(FranceOfflineManager.installedBytes(this))+" installés. Les cartes sont séparées de l’APK et restent disponibles sans réseau."),bottom(9));
        c.addView(section("DONNÉES"),top(12));c.addView(choice("Importer une tournée GPX","Depuis le stockage du téléphone",v->importGpx()),bottom(9));c.addView(choice("Mes tournées",store.routeFiles().size()+" fichier(s) GPX",v->showHistory()),bottom(9));c.addView(choice("Revoir l’introduction","Relance le parcours de première ouverture",v->resetOnboarding()),bottom(20));return p;
    }

    private View onlineMapRow(){LinearLayout r=row();LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);boolean online=prefs.getString("offline_pack",null)==null;labels.addView(text("OpenStreetMap en ligne",15,Typeface.BOLD,Color.WHITE));labels.addView(text(online?"Active • sans clé API":"Disponible",11,Typeface.NORMAL,online?GREEN:MUTED));r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView a=pill(online?"Active":"Activer",online?GREEN:accent(),11);a.setPadding(dp(12),0,dp(12),0);a.setOnClickListener(v->activateOnline());r.addView(a,new LinearLayout.LayoutParams(-2,dp(38)));return r;}

    private View offlineRow(FranceOfflineManager.Pack pack){
        LinearLayout r=row();LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);boolean installed=FranceOfflineManager.isInstalled(this,pack);boolean active=pack.id.equals(prefs.getString("offline_pack",null));String pending=prefs.getString("pending_map_id",null);long did=prefs.getLong("pending_download_id",0);int progress=pack.id.equals(pending)?FranceOfflineManager.downloadProgress(this,did):-1;String status=installed?(active?"Active • hors ligne":"Installée • "+bytes(FranceOfflineManager.file(this,pack).length())):(pack.id.equals(pending)?"Téléchargement"+(progress>=0?" • "+progress+" %":" en cours"):"≈ "+pack.sizeMb+" Mo • vectorielle");labels.addView(text(pack.label,15,Typeface.BOLD,Color.WHITE));labels.addView(text(status,11,Typeface.NORMAL,active?GREEN:MUTED));r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView action=pill(active?"Active":installed?"Activer":pack.id.equals(pending)?"…":"Télécharger",active?GREEN:accent(),11);action.setPadding(dp(11),0,dp(11),0);action.setEnabled(!active&&!pack.id.equals(pending));action.setAlpha(action.isEnabled()?1f:.55f);action.setOnClickListener(v->{if(installed)activateOffline(pack,true);else downloadPack(pack);});r.addView(action,new LinearLayout.LayoutParams(-2,dp(38)));if(installed){TextView menu=circle("•••",Color.argb(70,255,255,255));LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(dp(38),dp(38));ml.leftMargin=dp(6);r.addView(menu,ml);menu.setOnClickListener(v->offlineMenu(pack,active));}return r;
    }

    private void offlineMenu(FranceOfflineManager.Pack pack,boolean active){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel(pack.label,active?"Carte hors ligne actuellement active":"Carte hors ligne installée");TextView primary=sheetAction(active?"◉  Revenir à la carte en ligne":"✓  Activer cette carte",accent());primary.setOnClickListener(v->{press(v);d.dismiss();if(active)activateOnline();else activateOffline(pack,true);});panel.addView(primary,bottom(8));TextView del=sheetAction("⌫  Supprimer la carte",Color.argb(125,255,69,58));del.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(()->confirmSheet("Supprimer "+pack.label+" ?","Le fichier hors ligne sera supprimé du téléphone.","Annuler","Supprimer",RED,()->{if(active)activateOnline();if(FranceOfflineManager.delete(this,pack)){toast("Carte supprimée");showSettings();}}),120);});panel.addView(del,bottom(8));TextView cancel=sheetAction("Fermer",Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel);presentBottomSheet(d,shell,panel);
    }

    private void downloadPack(FranceOfflineManager.Pack p){try{long id=FranceOfflineManager.download(this,p);prefs.edit().putString("pending_map_id",p.id).putLong("pending_download_id",id).putBoolean("auto_activate_maps",true).apply();toast("Téléchargement de "+p.label+" lancé");showSettings();}catch(Exception e){toast("Téléchargement impossible");}}
    private void activateOffline(FranceOfflineManager.Pack p,boolean notify){if(applyOfflineProvider(p)){ModernMapController modern=modernMap();if(modern!=null)modern.setActive(false);prefs.edit().putString("offline_pack",p.id).apply();updateMapChip();if(notify)toast(p.label+" activée hors ligne");showMap();}else toast("Carte hors ligne illisible");}
    private boolean applyOfflineProvider(FranceOfflineManager.Pack p){try{MapsForgeTileSource src=MapsForgeTileSource.createFromFiles(new File[]{FranceOfflineManager.file(this,p)},InternalRenderTheme.DEFAULT,"RoutixDefault");src.setUserScaleFactor(1.0f);map.setTileProvider(new MapsForgeTileProvider(new SimpleRegisterReceiver(this),src,null));map.setTilesScaledToDpi(false);map.resetTilesScaleFactor();map.setUseDataConnection(false);map.invalidate();return true;}catch(Exception e){return false;}}
    private void applyOnlineProvider(){MapTileProviderBasic online=new MapTileProviderBasic(getApplicationContext(),TileSourceFactory.MAPNIK);map.setTileProvider(online);map.setTileSource(TileSourceFactory.MAPNIK);map.setTilesScaledToDpi(true);map.resetTilesScaleFactor();map.setUseDataConnection(true);map.invalidate();}
    private void activateOnline(){ModernMapController modern=modernMap();if(modern!=null)modern.setActive(false);applyOnlineProvider();prefs.edit().remove("offline_pack").apply();updateMapChip();toast("OpenStreetMap en ligne activée");showMap();}
    private void restoreOfflineMap(){FranceOfflineManager.Pack p=FranceOfflineManager.find(prefs.getString("offline_pack",null));if(p!=null&&FranceOfflineManager.isInstalled(this,p)){map.postDelayed(()->{if(!applyOfflineProvider(p)){prefs.edit().remove("offline_pack").apply();applyOnlineProvider();}updateMapChip();},120);}}
    private void checkPendingMapInstall(){String id=prefs.getString("pending_map_id",null);FranceOfflineManager.Pack p=FranceOfflineManager.find(id);if(p!=null&&FranceOfflineManager.isInstalled(this,p)){prefs.edit().remove("pending_map_id").remove("pending_download_id").apply();if(prefs.getBoolean("auto_activate_maps",true)){applyOfflineProvider(p);prefs.edit().putString("offline_pack",p.id).apply();toast(p.label+" installée et activée");}updateMapChip();}}
    private ModernMapController modernMap(){return ((RoutixApp)getApplication()).mapFor(this);}

    private View mapChoice(String title,String subtitle,boolean selected,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setMinimumHeight(dp(64));row.setBackground(glass(selected?Color.argb(100,10,132,255):Color.argb(40,255,255,255),18));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(text(title,15,Typeface.BOLD,Color.WHITE));TextView description=text(subtitle,12,Typeface.NORMAL,MUTED);labels.addView(description,top(4));row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView check=text(selected?"✓":"›",22,Typeface.BOLD,selected?CYAN:MUTED);check.setGravity(Gravity.CENTER);row.addView(check,new LinearLayout.LayoutParams(dp(32),dp(40)));row.setOnClickListener(v->{press(v);action.run();});return row;
    }

    private void mapSourceMenu(){
        Dialog dialog=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(12),0,dp(12),dp(12));
        LinearLayout panel=sheetPanel("Fond de carte","Choisis le style qui te convient");
        ModernMapController modern=modernMap();boolean active=modern!=null&&modern.isActive();boolean offline=prefs.getString("offline_pack",null)!=null;
        panel.addView(mapChoice("Carte moderne","Rues nettes et carte vectorielle",active,()->{dialog.dismiss();if(modern!=null){modern.setActive(true);updateMapChip();}}),bottom(8));
        panel.addView(mapChoice("OpenStreetMap","Fond classique en ligne",!active&&!offline,()->{dialog.dismiss();activateOnline();}),bottom(8));
        panel.addView(mapChoice("Cartes hors ligne","Utiliser ou télécharger une région",!active&&offline,()->{dialog.dismiss();root.postDelayed(this::offlineMapSourceMenu,120);}),bottom(14));
        TextView appearance=text("APPARENCE DE LA CARTE MODERNE",10,Typeface.BOLD,MUTED);panel.addView(appearance,bottom(8));LinearLayout modes=new LinearLayout(this);
        String mode=prefs.getString("map_appearance","light");
        for(String name:new String[]{"light","dark"}){
            TextView choice=pill((name.equals(mode)?"✓  ":"")+("light".equals(name)?"Clair":"Sombre"),name.equals(mode)?accent():GLASS2,13);
            choice.setOnClickListener(v->{prefs.edit().putString("map_appearance",name).apply();if(modern!=null)modern.refreshStyle();dialog.dismiss();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);if("light".equals(name))lp.rightMargin=dp(8);modes.addView(choice,lp);
        }
        panel.addView(modes,bottom(12));TextView close=pill("Fermer",GLASS2,14);close.setOnClickListener(v->dialog.dismiss());panel.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));
        presentBottomSheet(dialog,shell,panel);
    }

    private void offlineMapSourceMenu(){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(12),0,dp(12),dp(12));LinearLayout panel=sheetPanel("Cartes hors ligne","Disponibles sans connexion une fois téléchargées");
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        for(FranceOfflineManager.Pack p:FranceOfflineManager.PACKS)if(FranceOfflineManager.isInstalled(this,p))list.addView(mapChoice(p.label,"Carte téléchargée",p.id.equals(prefs.getString("offline_pack",null)),()->{d.dismiss();activateOffline(p,true);}),bottom(8));
        list.addView(mapChoice("Gérer les cartes","Télécharger une région dans les réglages",false,()->{d.dismiss();showSettings();}));scroll.addView(list);panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(320),getResources().getDisplayMetrics().heightPixels/2)));
        TextView close=pill("Fermer",GLASS2,14);close.setOnClickListener(v->d.dismiss());panel.addView(close,top(12));presentBottomSheet(d,shell,panel);
    }

    private void updateMapChip(){
        if(mapChip==null)return;ModernMapController modern=modernMap();
        boolean active=modern!=null&&modern.isActive();boolean offline=prefs.getString("offline_pack",null)!=null;
        mapChip.setText(active?"Moderne":offline?"Hors ligne":"Carte");mapChip.setTextColor(active?CYAN:offline?GREEN:Color.WHITE);
    }

    private void startRecording(){
        if(guiding){toast("Quitte d’abord le guidage");return;}if(!hasLocation()){ensureLocation();return;}points.clear();events.clear();liveDistance=0;liveTrack.setPoints(new ArrayList<>());clearLiveMarkers();store.clearDraft();recording=true;recordingCameraFollow=true;paused=false;startedAt=System.currentTimeMillis();pausedTotal=0;pauseStarted=0;lastDraftWrite=0;recordBtn.setText("Terminer et sauvegarder");recordBtn.setBackground(glass(RED,18));enableWorkButtons(true);timer.removeCallbacks(tick);timer.post(tick);haptic(recordBtn);toast("Tournée démarrée");
    }

    private void requestFinishRecording(){if(!recording)return;confirmSheet("Terminer la tournée ?","La trace GPS et les repères métier seront sauvegardés localement.","Continuer","Terminer et sauvegarder",RED,this::finishRecording);}
    private void finishRecording(){
        if(!recording)return;if(paused&&pauseStarted>0)pausedTotal+=System.currentTimeMillis()-pauseStarted;recording=false;paused=false;timer.removeCallbacks(tick);if(points.size()<2){store.clearDraft();recordBtn.setText("Commencer l’enregistrement");recordBtn.setBackground(glass(accent(),18));enableWorkButtons(false);toast("Tournée trop courte : rien à sauvegarder");return;}lastRoute=store.createRoute(points,events,startedAt);store.clearDraft();recordBtn.setText("Commencer une nouvelle tournée");recordBtn.setBackground(glass(accent(),18));enableWorkButtons(false);refreshRecordingUi();if(lastRoute!=null)showFinishSummary(store.parse(lastRoute));else toast("Erreur de sauvegarde");
    }

    private void showFinishSummary(RouteStore.Summary s){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Tournée enregistrée",formatDuration(s.durationMs)+" • "+formatDistance(s.distanceM)+" • "+s.events.size()+" repères");EditText name=new EditText(this);name.setSingleLine(true);name.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);name.setText(store.displayName(s.file));name.setSelectAllOnFocus(true);name.setTextColor(Color.WHITE);name.setHintTextColor(MUTED);name.setTextSize(16);name.setPadding(dp(15),0,dp(15),0);name.setBackground(glass(Color.argb(65,255,255,255),18));panel.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));TextView save=sheetAction("✓  Enregistrer le nom",accent());save.setOnClickListener(v->{press(v);store.rename(s.file,name.getText().toString());d.dismiss();toast("Nom enregistré");});panel.addView(save,top(10));TextView plan=sheetAction("▣  Voir le plan",Color.argb(65,255,255,255));plan.setOnClickListener(v->{press(v);store.rename(s.file,name.getText().toString());d.dismiss();root.postDelayed(()->openPlan(store.parse(s.file)),130);});panel.addView(plan,top(7));TextView later=sheetAction("Plus tard",Color.argb(35,255,255,255));later.setOnClickListener(v->d.dismiss());panel.addView(later,top(7));presentBottomSheet(d,shell,panel);
    }

    private void togglePause(){if(!recording){toast("Aucune tournée en cours");return;}paused=!paused;if(paused){pauseStarted=System.currentTimeMillis();pauseBtn.setText("▶ Reprendre");saveDraft(true);}else{pausedTotal+=System.currentTimeMillis()-pauseStarted;pauseStarted=0;pauseBtn.setText("Ⅱ Pause");}refreshRecordingUi();}
    private void addEvent(String type,String label,int color){if(!recording||paused){toast(paused?"Reprends la tournée":"Commence une tournée");return;}if(lastLocation==null){toast("GPS indisponible");return;}RouteStore.Event e=new RouteStore.Event(type,label,lastLocation.getLatitude(),lastLocation.getLongitude(),System.currentTimeMillis(),lastLocation.getAccuracy());events.add(e);addSubtleMarker(new GeoPoint(e.lat,e.lon),color,"live");haptic("REVERSE".equals(type)?reverseBtn:sidesBtn);saveDraft(true);refreshRecordingUi();toast(label+" enregistré");}
    private void eventWithNote(String type,String label,int color){
        if(!recording||paused){toast("Commence ou reprends la tournée");return;}Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel(label,"Ajouter une note facultative au repère");EditText input=new EditText(this);input.setHint("Ex. impasse étroite, portail…");input.setHintTextColor(Color.argb(115,255,255,255));input.setTextColor(Color.WHITE);input.setSingleLine(true);input.setPadding(dp(15),0,dp(15),0);input.setBackground(glass(Color.argb(62,255,255,255),18));panel.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));TextView save=sheetAction("✓  Enregistrer le repère",accent());save.setOnClickListener(v->{String note=input.getText().toString().trim();press(v);d.dismiss();addEvent(type,note.isEmpty()?label:label+" • "+note,color);});panel.addView(save,top(10));TextView cancel=sheetAction("Annuler",Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel,top(7));presentBottomSheet(d,shell,panel);root.postDelayed(input::requestFocus,180);
    }

    private void undoEvent(){if(events.isEmpty()){toast("Aucun repère à annuler");return;}events.remove(events.size()-1);clearLiveMarkers();for(int i=0;i<events.size();i++){RouteStore.Event e=events.get(i);addSubtleMarker(new GeoPoint(e.lat,e.lon),"REVERSE".equals(e.type)?ORANGE:CYAN,"live");}saveDraft(true);refreshRecordingUi();toast("Dernier repère annulé");}

    @Override public void onLocationChanged(@NonNull Location l){
        lastLocation=l;if(headerSpeed!=null)headerSpeed.update(l);if(gpsChip!=null){int a=Math.round(l.getAccuracy());gpsChip.setText("±"+a+" m");gpsChip.setTextColor(a<=8?GREEN:a<=20?ORANGE:RED);}if(guiding){updateGuidance(l);return;}if(recording&&!paused&&l.getAccuracy()<=60&&append(l)){if(!points.isEmpty()){RouteStore.Point p=points.get(points.size()-1);float[] d=new float[1];Location.distanceBetween(p.lat,p.lon,l.getLatitude(),l.getLongitude(),d);if(d[0]<200)liveDistance+=d[0];}points.add(new RouteStore.Point(l.getLatitude(),l.getLongitude(),l.getTime()>0?l.getTime():System.currentTimeMillis(),l.getAccuracy()));List<GeoPoint> g=new ArrayList<>(liveTrack.getActualPoints());g.add(new GeoPoint(l.getLatitude(),l.getLongitude()));liveTrack.setPoints(g);if(recordingCameraFollow&&prefs.getBoolean("follow",true))map.getController().animateTo(g.get(g.size()-1));saveDraft(false);refreshRecordingUi();}map.invalidate();
    }
    private boolean append(Location l){if(points.isEmpty())return true;RouteStore.Point p=points.get(points.size()-1);float[] d=new float[1];Location.distanceBetween(p.lat,p.lon,l.getLatitude(),l.getLongitude(),d);long dt=(l.getTime()>0?l.getTime():System.currentTimeMillis())-p.time;return d[0]>=1.2||dt>=2000;}
    private void saveDraft(boolean force){long now=System.currentTimeMillis();if(!force&&now-lastDraftWrite<10000)return;lastDraftWrite=now;store.saveDraft(points,events,startedAt);}
    private void refreshRecordingUi(){if(timeText==null)return;if(recording){long now=paused?pauseStarted:System.currentTimeMillis();timeText.setText((paused?"En pause • ":"En cours • ")+formatDuration(Math.max(0,now-startedAt-pausedTotal)));}else timeText.setText(lastRoute==null?"Prêt à enregistrer":"Dernière tournée sauvegardée");distanceText.setText(formatDistance(liveDistance));eventsText.setText(String.valueOf(events.size()));pointsText.setText(String.valueOf(points.size()));}

    private void checkDraftRecovery(){
        if(recording||!store.hasDraft())return;RouteStore.Summary sum=store.draftSummary();if(sum.points.size()<2){store.clearDraft();return;}Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Tournée interrompue trouvée",formatDuration(sum.durationMs)+" • "+formatDistance(sum.distanceM)+" • "+sum.events.size()+" repères\nTu peux reprendre exactement là où Routix s’est arrêté.");TextView resume=sheetAction("▶  Reprendre la tournée",accent());resume.setOnClickListener(v->{press(v);d.dismiss();resumeDraft(sum);});panel.addView(resume,bottom(8));TextView save=sheetAction("✓  Sauvegarder maintenant",Color.argb(65,255,255,255));save.setOnClickListener(v->{press(v);d.dismiss();saveRecoveredDraft(sum);});panel.addView(save,bottom(8));TextView del=sheetAction("Supprimer le brouillon",Color.argb(125,255,69,58));del.setOnClickListener(v->{press(v);d.dismiss();store.clearDraft();});panel.addView(del);presentBottomSheet(d,shell,panel);
    }

    private void resumeDraft(RouteStore.Summary s){points.clear();events.clear();points.addAll(s.points);events.addAll(s.events);liveDistance=s.distanceM;startedAt=prefs.getLong("draft_started_at",s.firstTime>0?s.firstTime:System.currentTimeMillis());recording=true;paused=false;pausedTotal=0;liveTrack.setPoints(toGeo(points));clearLiveMarkers();for(int i=0;i<events.size();i++){RouteStore.Event e=events.get(i);addSubtleMarker(new GeoPoint(e.lat,e.lon),"REVERSE".equals(e.type)?ORANGE:CYAN,"live");}recordBtn.setText("Terminer et sauvegarder");recordBtn.setBackground(glass(RED,18));enableWorkButtons(true);timer.post(tick);refreshRecordingUi();toast("Tournée reprise");}
    private void saveRecoveredDraft(RouteStore.Summary s){File f=store.createRoute(s.points,s.events,s.firstTime>0?s.firstTime:System.currentTimeMillis());if(f!=null){lastRoute=f;store.clearDraft();toast("Tournée récupérée et sauvegardée");}else toast("Récupération impossible");}

    private void rename(File f){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Renommer la tournée","Choisis un nom clair pour la retrouver rapidement");EditText e=new EditText(this);e.setText(store.displayName(f));e.setSelectAllOnFocus(true);e.setSingleLine();e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setTextColor(Color.WHITE);e.setHintTextColor(MUTED);e.setTextSize(16);e.setPadding(dp(15),0,dp(15),0);e.setBackground(glass(Color.argb(62,255,255,255),18));panel.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));TextView save=sheetAction("✓  Enregistrer",accent());save.setOnClickListener(v->{press(v);store.rename(f,e.getText().toString());d.dismiss();root.postDelayed(this::showHistory,100);});panel.addView(save,top(10));TextView cancel=sheetAction("Annuler",Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel,top(7));presentBottomSheet(d,shell,panel);root.postDelayed(e::requestFocus,180);
    }

    private void deleteRoute(File f){confirmSheet("Supprimer cette tournée ?","Le fichier GPX local sera supprimé définitivement.","Annuler","Supprimer",RED,()->{if(store.delete(f)){toast("Tournée supprimée");showHistory();}else toast("Suppression impossible");});}
    private void share(File f){if(f==null||!f.exists()){toast("Fichier introuvable");return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/gpx+xml");i.putExtra(Intent.EXTRA_STREAM,FileProvider.getUriForFile(this,getPackageName()+".files",f));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Partager la tournée"));}
    private void importGpx(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/gpx+xml","application/xml","text/xml","text/plain"});startActivityForResult(i,IMPORT_GPX);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==IMPORT_GPX&&resultCode==RESULT_OK&&data!=null){File f=store.importGpx(data.getData());if(f!=null){toast("Tournée importée");showHistory();}else toast("GPX invalide ou sans trace");}}

    private void enterFocusMode(){if(guiding)return;focusMode=true;topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.GONE);if(planSheet!=null)planSheet.setVisibility(View.GONE);if(focusRestore==null){TextView restore=pill("← Retour",Color.argb(225,25,27,34),12);restore.setOnClickListener(v->exitFocusMode());focusRestore=restore;FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(102),dp(48),Gravity.BOTTOM|Gravity.END);p.setMargins(0,0,dp(16),safeBottom()+dp(16));root.addView(restore,p);}fade(focusRestore);}
    private void exitFocusMode(){focusMode=false;removePlanLegend();if(focusRestore!=null){root.removeView(focusRestore);focusRestore=null;}if(guiding)return;if(planSheet!=null){planSheet.setVisibility(View.VISIBLE);dock.setVisibility(View.VISIBLE);return;}if(page==null){topBar.setVisibility(View.VISIBLE);recordSheet.setVisibility(View.VISIBLE);dock.setVisibility(View.VISIBLE);}}
    private void setCollapsed(boolean value){collapsed=value;if(recordBody==null)return;if(collapsed){recordBody.setVisibility(View.GONE);collapseBtn.setText("⌃");}else{recordBody.setVisibility(View.VISIBLE);recordBody.setAlpha(1);collapseBtn.setText("⌄");}}

    private void clearPage(){stopPreviewPlayback();if(page!=null){root.removeView(page);page=null;}if(planSheet!=null){root.removeView(planSheet);planSheet=null;}clearPreview();}
    private void clearPreview(){if(routeArrows!=null){map.getOverlays().remove(routeArrows);routeArrows=null;}map.getOverlays().removeIf(o->o instanceof Marker&&((Marker)o).getRelatedObject() instanceof GuidanceEngine.Event);if(previewTrack!=null){map.getOverlays().remove(previewTrack);previewTrack=null;}map.getOverlays().removeIf(o->o instanceof Marker&&((Marker)o).getRelatedObject() instanceof String&&((String)((Marker)o).getRelatedObject()).startsWith("preview"));previewCursor=null;map.invalidate();}
    private void clearLiveMarkers(){map.getOverlays().removeIf(o->o instanceof Marker&&"live".equals(((Marker)o).getRelatedObject()));map.invalidate();}
    private void addMarker(GeoPoint p,String badge,int color,String tag){Marker m=new Marker(map);m.setPosition(p);m.setAnchor(.5f,.5f);m.setIcon(marker(badge,color));m.setRelatedObject(tag);map.getOverlays().add(m);}
    private BitmapDrawable marker(String s,int color){int z=dp(40);Bitmap b=Bitmap.createBitmap(z,z,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShadowLayer(dp(5),0,dp(2),Color.argb(130,0,0,0));p.setColor(color);c.drawCircle(z/2f,z/2f,z*.44f,p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.WHITE);c.drawCircle(z/2f,z/2f,z*.39f,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(s.length()>1?10:13));Paint.FontMetrics fm=p.getFontMetrics();c.drawText(s,z/2f,z/2f-(fm.ascent+fm.descent)/2f,p);return new BitmapDrawable(getResources(),b);}
    private List<GeoPoint> toGeo(List<RouteStore.Point> pts){List<GeoPoint> out=new ArrayList<>();for(RouteStore.Point p:pts)out.add(new GeoPoint(p.lat,p.lon));return out;}

    private View infoCard(String title,String body){LinearLayout r=row();r.setOrientation(LinearLayout.VERTICAL);r.addView(text(title,15,Typeface.BOLD,Color.WHITE));TextView b=text(body,11,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);r.addView(b,lp);return r;}
    private View choice(String title,String value,View.OnClickListener l){LinearLayout r=row();LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,15,Typeface.BOLD,Color.WHITE));t.addView(text(value,11,Typeface.NORMAL,MUTED));r.addView(t,new LinearLayout.LayoutParams(0,-2,1));r.addView(text("›",25,Typeface.NORMAL,MUTED));r.setOnClickListener(l);return r;}
    private View toggle(String title,String sub,String key,boolean def){LinearLayout r=row();LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(title,15,Typeface.BOLD,Color.WHITE));t.addView(text(sub,11,Typeface.NORMAL,MUTED));r.addView(t,new LinearLayout.LayoutParams(0,-2,1));FrameLayout track=new FrameLayout(this);track.setPadding(dp(3),dp(3),dp(3),dp(3));View thumb=new View(this);thumb.setBackground(glass(Color.WHITE,99));track.addView(thumb,new FrameLayout.LayoutParams(dp(24),dp(24)));boolean[] checked={prefs.getBoolean(key,def)};applyToggle(track,thumb,checked[0],false);track.setOnClickListener(v->{checked[0]=!checked[0];prefs.edit().putBoolean(key,checked[0]).apply();applyToggle(track,thumb,checked[0],true);haptic(track);if("keep_screen_on".equals(key))applyKeepScreen();});r.addView(track,new LinearLayout.LayoutParams(dp(50),dp(30)));return r;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(15),dp(13),dp(13),dp(13));r.setBackground(glass(GLASS2,20));return r;}
    private TextView section(String s){TextView v=text(s,10,Typeface.BOLD,accent());v.setPadding(dp(2),0,0,dp(8));return v;}
    private LinearLayout pageBase(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(18),safeTop()+dp(18),dp(18),safeBottom()+dp(86));p.setBackground(appBackground());return p;}
    private GradientDrawable appBackground(){return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(6,9,15),Color.rgb(14,11,22),Color.rgb(7,13,18)});}
    private TextView nav(String icon,String label){TextView v=text(icon+"\n"+label,11,Typeface.BOLD,MUTED);v.setGravity(Gravity.CENTER);return v;}
    private TextView text(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private TextView pill(String s,int color,float sp){TextView v=text(s,sp,Typeface.BOLD,Color.WHITE);v.setGravity(Gravity.CENTER);v.setBackground(glass(color,18));return v;}
    private TextView chip(String s,int color){TextView v=pill(s,color,10);v.setPadding(dp(9),0,dp(9),0);return v;}
    private TextView circle(String s,int color){return pill(s,color,15);}
    private TextView stat(String value,String tag){TextView v=text(value,15,Typeface.BOLD,Color.WHITE);v.setTag(tag);return v;}
    private View statCell(TextView v){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text((String)v.getTag(),9,Typeface.BOLD,Color.argb(135,255,255,255)));b.addView(v);return b;}
    private View summaryStat(String title,String value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text(title,9,Typeface.BOLD,Color.argb(135,255,255,255)));b.addView(text(value,14,Typeface.BOLD,Color.WHITE));return b;}
    private GradientDrawable glass(int color,int radius){int a=Color.alpha(color);int hi=Color.argb(Math.min(255,a+18),Math.min(255,Color.red(color)+34),Math.min(255,Color.green(color)+34),Math.min(255,Color.blue(color)+38));int lo=Color.argb(Math.max(24,a-28),Math.max(0,Color.red(color)-14),Math.max(0,Color.green(color)-14),Math.max(0,Color.blue(color)-9));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{hi,color,lo});g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.argb(72,255,255,255));return g;}
    private int shade(int c,int delta){return Color.argb(Color.alpha(c),Math.max(0,Math.min(255,Color.red(c)+delta)),Math.max(0,Math.min(255,Color.green(c)+delta)),Math.max(0,Math.min(255,Color.blue(c)+delta)));}
    private GradientDrawable rippleLike(){GradientDrawable g=new GradientDrawable();g.setColor(Color.TRANSPARENT);g.setCornerRadius(dp(14));return g;}
    private void press(View v){v.animate().scaleX(.955f).scaleY(.955f).alpha(.86f).setDuration(70).withEndAction(()->v.animate().scaleX(1).scaleY(1).alpha(1).setDuration(210).setInterpolator(new OvershootInterpolator(.8f)).start()).start();}
    private void fade(View v){v.setAlpha(0);v.setScaleX(.985f);v.setScaleY(.985f);v.setTranslationY(dp(14));v.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator()).start();}
    private void haptic(View v){if(prefs.getBoolean("haptic",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void enableWorkButtons(boolean e){for(TextView v:new TextView[]{reverseBtn,sidesBtn,pauseBtn,undoBtn}){v.setEnabled(e);v.setAlpha(e?1:.34f);}}
    private void selectTab(String s){for(TextView v:new TextView[]{navMap,navHistory,navGps,navHours,navSettings}){boolean on=(v==navMap&&"map".equals(s))||(v==navHistory&&"history".equals(s))||(v==navHours&&"hours".equals(s))||(v==navSettings&&"settings".equals(s));v.setTextColor(on?Color.WHITE:MUTED);v.setBackground(on?glass(Color.argb(105,Color.red(accent()),Color.green(accent()),Color.blue(accent())),18):null);v.setScaleX(1f);v.setScaleY(1f);}}


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

    private void confirmSheet(String title,String message,String cancelLabel,String confirmLabel,int confirmColor,Runnable action){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel(title,message);TextView confirm=sheetAction(confirmLabel,confirmColor);confirm.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(action,100);});panel.addView(confirm,bottom(8));TextView cancel=sheetAction(cancelLabel,Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel);presentBottomSheet(d,shell,panel);}

    private void positionIconDialog(){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Icône sur la carte","Choisis ce qui représente ta position pendant les tournées");String current=prefs.getString("position_icon","Camion");for(String name:new String[]{"Bonhomme","Voiture","Camion"}){String symbol="Bonhomme".equals(name)?"●":"Voiture".equals(name)?"▰":"▣";TextView row=sheetAction((name.equals(current)?"✓  ":symbol+"  ")+name,name.equals(current)?accent():Color.argb(52,255,255,255));row.setOnClickListener(v->{press(v);prefs.edit().putString("position_icon",name).apply();applyPositionIcon();d.dismiss();root.postDelayed(this::showSettings,100);});panel.addView(row,bottom(7));}presentBottomSheet(d,shell,panel);}
    private void applyPositionIcon(){if(me==null)return;Bitmap icon=createPositionIcon(prefs==null?"Camion":prefs.getString("position_icon","Camion"));me.setPersonIcon(icon);me.setDirectionArrow(icon,icon);me.setPersonHotspot(icon.getWidth()/2f,icon.getHeight()/2f);if(map!=null)map.invalidate();}
    private Bitmap createPositionIcon(String style){int z=dp(58);Bitmap b=Bitmap.createBitmap(z,z,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShadowLayer(dp(5),0,dp(2),Color.argb(150,0,0,0));p.setColor(Color.argb(218,Color.red(accent()),Color.green(accent()),Color.blue(accent())));c.drawCircle(z/2f,z/2f,z*.43f,p);p.clearShadowLayer();p.setColor(Color.WHITE);if("Bonhomme".equals(style)){c.drawCircle(z*.5f,z*.31f,z*.09f,p);c.drawRoundRect(z*.43f,z*.40f,z*.57f,z*.67f,dp(5),dp(5),p);p.setStrokeWidth(dp(4));p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(z*.45f,z*.51f,z*.34f,z*.61f,p);c.drawLine(z*.55f,z*.51f,z*.66f,z*.61f,p);c.drawLine(z*.47f,z*.65f,z*.40f,z*.78f,p);c.drawLine(z*.53f,z*.65f,z*.60f,z*.78f,p);}else if("Voiture".equals(style)){c.drawRoundRect(z*.31f,z*.20f,z*.69f,z*.80f,dp(8),dp(8),p);p.setColor(Color.argb(210,25,32,44));c.drawRoundRect(z*.36f,z*.31f,z*.64f,z*.49f,dp(4),dp(4),p);p.setColor(Color.WHITE);c.drawRect(z*.27f,z*.30f,z*.32f,z*.45f,p);c.drawRect(z*.68f,z*.30f,z*.73f,z*.45f,p);c.drawRect(z*.27f,z*.58f,z*.32f,z*.73f,p);c.drawRect(z*.68f,z*.58f,z*.73f,z*.73f,p);}else{c.drawRoundRect(z*.29f,z*.17f,z*.71f,z*.78f,dp(7),dp(7),p);p.setColor(Color.argb(210,25,32,44));c.drawRoundRect(z*.34f,z*.22f,z*.66f,z*.38f,dp(3),dp(3),p);p.setColor(Color.argb(235,210,235,245));c.drawRoundRect(z*.34f,z*.44f,z*.66f,z*.69f,dp(4),dp(4),p);p.setColor(Color.WHITE);c.drawRect(z*.25f,z*.26f,z*.31f,z*.43f,p);c.drawRect(z*.69f,z*.26f,z*.75f,z*.43f,p);c.drawRect(z*.25f,z*.57f,z*.31f,z*.74f,p);c.drawRect(z*.69f,z*.57f,z*.75f,z*.74f,p);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.argb(220,255,255,255));c.drawCircle(z/2f,z/2f,z*.43f,p);return b;}

    private void accentDialog(){showColorPicker("Couleur d’accent","accent",new String[]{"Bleu","Violet","Vert","Orange","Cyan","Rose"});}
    private void traceColorDialog(){showColorPicker("Couleur de la trace","trace_color",new String[]{"Orange","Bleu","Vert","Violet","Cyan","Rose"});}
    private int namedColor(String s){if("Violet".equals(s))return PURPLE;if("Vert".equals(s))return GREEN;if("Orange".equals(s))return ORANGE;if("Cyan".equals(s))return CYAN;if("Rose".equals(s))return PINK;return BLUE;}
    private int accent(){return namedColor(prefs==null?"Bleu":prefs.getString("accent","Bleu"));}
    private int traceColor(){return namedColor(prefs==null?"Orange":prefs.getString("trace_color","Orange"));}

    private void showColorPicker(String title,String key,String[] names){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(10),dp(18),dp(18));panel.setBackground(glass(Color.argb(246,23,25,32),30));TextView handle=text("—",28,Typeface.BOLD,Color.argb(100,255,255,255));handle.setGravity(Gravity.CENTER);panel.addView(handle,new LinearLayout.LayoutParams(-1,dp(24)));panel.addView(text(title,23,Typeface.BOLD,Color.WHITE),bottom(10));String current=prefs.getString(key,names[0]);for(String name:names){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(11),dp(12),dp(11));TextView dot=text("●",24,Typeface.BOLD,namedColor(name));row.addView(dot,new LinearLayout.LayoutParams(dp(38),-2));TextView label=text(name,16,Typeface.BOLD,Color.WHITE);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView check=text(name.equals(current)?"✓":"",18,Typeface.BOLD,accent());row.addView(check);row.setBackground(glass(Color.argb(name.equals(current)?85:36,255,255,255),18));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));rp.bottomMargin=dp(7);panel.addView(row,rp);row.setOnClickListener(v->{press(v);prefs.edit().putString(key,name).apply();d.dismiss();root.postDelayed(this::recreate,150);});}shell.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));d.setContentView(shell);setBackdropBlur(true);d.setOnDismissListener(x->setBackdropBlur(false));d.show();android.view.Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.42f;w.setAttributes(a);if(Build.VERSION.SDK_INT>=31)w.setBackgroundBlurRadius(dp(28));}}

    private void showGuidanceStartSheet(RouteStore.Summary s){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(10),dp(18),dp(18));panel.setBackground(glass(Color.argb(246,23,25,32),30));TextView handle=text("—",28,Typeface.BOLD,Color.argb(100,255,255,255));handle.setGravity(Gravity.CENTER);panel.addView(handle,new LinearLayout.LayoutParams(-1,dp(24)));panel.addView(text("Démarrer la tournée",24,Typeface.BOLD,Color.WHITE));RouteStore.Point start=s.points.get(0);float distance=-1;if(lastLocation!=null){float[] dd=new float[1];Location.distanceBetween(lastLocation.getLatitude(),lastLocation.getLongitude(),start.lat,start.lon,dd);distance=dd[0];}String info=distance>70?"Routix te guidera d’abord jusqu’au départ ("+formatDistance(distance)+"), puis suivra exactement la trace enregistrée.":"Le guidage suivra exactement la trace GPS enregistrée, sans la recalculer.";TextView sub=text(info,12,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(7);sp.bottomMargin=dp(16);panel.addView(sub,sp);TextView go=pill("➜ Démarrer",accent(),15);go.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(()->startGuidance(s,false),110);});panel.addView(go,new LinearLayout.LayoutParams(-1,dp(56)));TextView reverse=pill("↶ Sens inverse",GLASS2,13);reverse.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(()->startGuidance(s,true),110);});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48));rp.topMargin=dp(9);panel.addView(reverse,rp);TextView cancel=pill("Annuler",Color.argb(45,255,255,255),12);cancel.setOnClickListener(v->d.dismiss());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.topMargin=dp(8);panel.addView(cancel,cp);shell.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));d.setContentView(shell);setBackdropBlur(true);d.setOnDismissListener(x->setBackdropBlur(false));d.show();android.view.Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.42f;w.setAttributes(a);if(Build.VERSION.SDK_INT>=31)w.setBackgroundBlurRadius(dp(28));}}

    private void applyToggle(FrameLayout track,View thumb,boolean on,boolean animate){int target=on?accent():Color.argb(120,85,88,98);if(!animate){track.setBackground(glass(target,99));thumb.setTranslationX(on?dp(20):0);return;}int from=on?Color.argb(120,85,88,98):accent();ValueAnimator colors=ValueAnimator.ofArgb(from,target);colors.setDuration(220);colors.addUpdateListener(a->track.setBackground(glass((Integer)a.getAnimatedValue(),99)));colors.start();thumb.animate().translationX(on?dp(20):0).scaleX(.92f).scaleY(.92f).setDuration(110).withEndAction(()->thumb.animate().scaleX(1).scaleY(1).setDuration(180).setInterpolator(new OvershootInterpolator(.8f)).start()).start();}
    private void setBackdropBlur(boolean on){if(Build.VERSION.SDK_INT>=31&&root!=null)root.setRenderEffect(on?RenderEffect.createBlurEffect(dp(8),dp(8),Shader.TileMode.CLAMP):null);}

    private void requestApproachRoute(Location from,GeoPoint target){final long session=guidanceSession;approachInstruction="Calcul de l’itinéraire routier…";new Thread(()->{List<GeoPoint> pts=new ArrayList<>();double roadDistance=-1;String instruction="Suis l’itinéraire bleu jusqu’au départ";try{String q=String.format(Locale.US,"https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",from.getLongitude(),from.getLatitude(),target.getLongitude(),target.getLatitude());HttpURLConnection c=(HttpURLConnection)new URL(q).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(9000);c.setRequestProperty("User-Agent",getPackageName()+" Routix/1.0");BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder b=new StringBuilder();String line;while((line=br.readLine())!=null)b.append(line);br.close();JSONObject json=new JSONObject(b.toString());JSONArray routes=json.optJSONArray("routes");if(routes!=null&&routes.length()>0){JSONObject route=routes.getJSONObject(0);roadDistance=route.optDouble("distance",-1);JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");for(int i=0;i<coords.length();i++){JSONArray p=coords.getJSONArray(i);pts.add(new GeoPoint(p.getDouble(1),p.getDouble(0)));}JSONArray legs=route.optJSONArray("legs");if(legs!=null&&legs.length()>0){JSONArray steps=legs.getJSONObject(0).optJSONArray("steps");if(steps!=null&&steps.length()>1){JSONObject st=steps.getJSONObject(1);String name=st.optString("name","");instruction=name.isEmpty()?"Continue vers le départ":"Continue sur "+name;}}}}catch(Exception ignored){}double finalDistance=roadDistance;String finalInstruction=instruction;List<GeoPoint> finalPts=pts;runOnUiThread(()->{if(!guiding||!approachingStart||session!=guidanceSession)return;clearApproachRoute();if(finalPts.size()<2){finalPts.add(new GeoPoint(from.getLatitude(),from.getLongitude()));finalPts.add(target);approachInstruction="Trajet routier indisponible • cap direct vers le départ";}else approachInstruction=finalInstruction;if(finalDistance>0){
                    for(GeoPoint p:finalPts)approachPath.add(new GuidanceEngine.Point(p.getLatitude(),p.getLongitude()));
                    approachGuidance=new GuidanceEngine(approachPath,Collections.emptyList());
                    approachGuidance.reposition(from.getLatitude(),from.getLongitude());
                    approachTrack=new Polyline();approachTrack.getOutlinePaint().setColor(BLUE);approachTrack.getOutlinePaint().setStrokeWidth(dp(11));approachTrack.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);approachTrack.setPoints(finalPts);
                    int arrowIndex=map.getOverlays().indexOf(routeArrows);map.getOverlays().add(Math.max(0,arrowIndex),approachTrack);
                }
                if(lastLocation!=null)updateGuidance(lastLocation);map.invalidate();});}).start();}
    private void clearApproachRoute(){approachGuidance=null;approachPath.clear();if(approachTrack!=null){map.getOverlays().remove(approachTrack);approachTrack=null;if(map!=null)map.invalidate();}}

    private void recenter(){ModernMapController modern=modernMap();if(modern!=null&&modern.isActive()&&lastLocation!=null&&!guiding){modern.recenter(lastLocation);return;}if(lastLocation==null){toast("Position GPS en attente");return;}if(guiding)guidanceCameraFollow=true;if(recording)recordingCameraFollow=true;map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()));if(guiding)map.getController().setZoom(17.4);else if(!recording)map.getController().setZoom(18.2);}
    private void ensureLocation(){if(hasLocation())enableLocation();else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);}
    private boolean hasLocation(){return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void enableLocation(){me.enableMyLocation();try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);}catch(SecurityException ignored){}}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==LOCATION_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)enableLocation();else new AlertDialog.Builder(this).setTitle("Localisation précise requise").setMessage("Routix en a besoin pour enregistrer et rejouer fidèlement une tournée.").setPositiveButton("Réglages",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())))).setNegativeButton("Plus tard",null).show();}
    private void applyKeepScreen(){if(prefs.getBoolean("keep_screen_on",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void resetOnboarding(){prefs.edit().putBoolean("onboarding_complete",false).apply();startActivity(new Intent(this,OnboardingActivity.class));finish();}

    private FrameLayout.LayoutParams topLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(76),Gravity.TOP);p.setMargins(dp(12),safeTop(),dp(12),0);return p;}
    private FrameLayout.LayoutParams sheetLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),safeBottom()+dp(88));return p;}
    private FrameLayout.LayoutParams dockLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(68),Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),safeBottom()+dp(8));return p;}
    private FrameLayout.LayoutParams pageLp(){return new FrameLayout.LayoutParams(-1,-1);}
    private FrameLayout.LayoutParams planLp(){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(390),Gravity.BOTTOM);p.setMargins(dp(12),0,dp(12),safeBottom()+dp(84));return p;}
    private LinearLayout.LayoutParams bottom(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(x);return p;}
    private LinearLayout.LayoutParams top(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(x);return p;}
    private LinearLayout.LayoutParams weighted(float w,int mr){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),w);p.rightMargin=dp(mr);return p;}
    private void positionChrome(){
        if(topBar!=null)topBar.setLayoutParams(topLp());if(dock!=null)dock.setLayoutParams(dockLp());if(recordSheet!=null)recordSheet.setLayoutParams(sheetLp());if(planSheet!=null)planSheet.setLayoutParams(planLp());
        if(recordingScroll!=null)recordingScroll.setMaximumHeight(Math.max(dp(80),root.getHeight()-safeTop()-dp(76)-safeBottom()-dp(104)));
        if(page!=null)page.setPadding(dp(18),safeTop()+dp(18),dp(18),safeBottom()+dp(88));
    }
    private int safeTop(){if(systemTop>=0)return systemTop+dp(8);int id=getResources().getIdentifier("status_bar_height","dimen","android");return (id>0?getResources().getDimensionPixelSize(id):dp(24))+dp(8);}
    private int safeBottom(){if(systemBottom>=0)return systemBottom;int id=getResources().getIdentifier("navigation_bar_height","dimen","android");return id>0?getResources().getDimensionPixelSize(id):0;}
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
