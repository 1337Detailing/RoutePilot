package com.routix.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Classic point-to-point GPS, independent from recorded tour guidance. */
public final class ClassicNavigationActivity extends AppCompatActivity implements LocationListener {
    private static final int LOCATION_PERMISSION = 84;
    private static final String STYLE_DAY = "https://tiles.openfreemap.org/styles/liberty";
    private static final String STYLE_NIGHT = "https://tiles.openfreemap.org/styles/dark";
    private static final int BLUE = Color.rgb(10,132,255), CYAN = Color.rgb(100,210,255);
    private static final int BG = Color.rgb(8,9,13), GLASS = Color.argb(226,23,25,32), MUTED = Color.argb(180,255,255,255);

    private FrameLayout root;
    private MapView mapView;
    private MapLibreMap map;
    private LocationManager locationManager;
    private Location lastLocation;
    private Marker userMarker, destinationMarker;
    private Polyline routeLine;
    private boolean nightMode, followCamera = true;
    private EditText search;
    private TextView modeButton, speedText, instructionTitle, instructionSub, remainingText, etaText, recenter;
    private LatLng destination;
    private String destinationName = "Destination";
    private final List<LatLng> routePoints = new ArrayList<>();
    private final List<NavStep> steps = new ArrayList<>();
    private GuidanceEngine routeEngine;
    private int stepIndex;
    private long lastReroute;
    private double routeDistanceM, routeDurationS;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        nightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        MapLibre.getInstance(this);
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);

        root = new FrameLayout(this); root.setBackgroundColor(BG);
        mapView = new MapView(this); mapView.onCreate(state); root.addView(mapView, new FrameLayout.LayoutParams(-1,-1));
        buildUi(); setContentView(root);
        mapView.getMapAsync(m -> { map=m; map.getUiSettings().setLogoEnabled(true); map.getUiSettings().setAttributionEnabled(true); applyMapStyle(false); });
        ensureLocation();
    }

    private void buildUi() {
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(10),dp(8),dp(10),dp(8)); top.setBackground(glass(GLASS,25)); top.setElevation(dp(18));
        TextView back = button("‹",44); back.setTextSize(30); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        search = new EditText(this); search.setSingleLine(true); search.setTextColor(Color.WHITE); search.setHintTextColor(Color.argb(130,255,255,255)); search.setHint("Où aller ?"); search.setTextSize(15); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH); search.setPadding(dp(14),0,dp(10),0); search.setBackground(glass(Color.argb(115,53,56,66),18)); search.setOnEditorActionListener((v,id,e)->{ if(id==EditorInfo.IME_ACTION_SEARCH){ searchPlaces(); return true;} return false; });
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(46),1); sp.leftMargin=dp(7); sp.rightMargin=dp(7); top.addView(search,sp);
        TextView go=button("➜",46); go.setTextColor(CYAN); go.setOnClickListener(v->searchPlaces()); top.addView(go,new LinearLayout.LayoutParams(dp(46),dp(46)));
        modeButton=button(nightMode?"☾":"☀",46); modeButton.setOnClickListener(v->{nightMode=!nightMode;applyMapStyle(true);}); LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(dp(46),dp(46));mlp.leftMargin=dp(6);top.addView(modeButton,mlp);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);tp.setMargins(dp(10),safeTop(),dp(10),0);root.addView(top,tp);

        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.VERTICAL); nav.setPadding(dp(15),dp(12),dp(15),dp(13)); nav.setBackground(glass(Color.argb(238,16,18,25),26)); nav.setElevation(dp(20));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView arrow=label("↑",34,Typeface.BOLD,CYAN);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout it=new LinearLayout(this);it.setOrientation(LinearLayout.VERTICAL);instructionTitle=label("GPS classique",19,Typeface.BOLD,Color.WHITE);instructionSub=label("Recherche une destination pour commencer",11,Typeface.NORMAL,MUTED);it.addView(instructionTitle);it.addView(instructionSub);row.addView(it,new LinearLayout.LayoutParams(0,-2,1));nav.addView(row);
        LinearLayout stats=new LinearLayout(this);stats.setGravity(Gravity.CENTER_VERTICAL);stats.setPadding(0,dp(9),0,0);
        remainingText=metric("—","RESTANT");etaText=metric("—","ARRIVÉE");speedText=metric("0","KM/H");
        stats.addView(remainingText,new LinearLayout.LayoutParams(0,-2,1));stats.addView(etaText,new LinearLayout.LayoutParams(0,-2,1));stats.addView(speedText,new LinearLayout.LayoutParams(0,-2,1));nav.addView(stats);
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);np.setMargins(dp(10),0,dp(10),dp(18));root.addView(nav,np);

        recenter=button("◎",50);recenter.setTextSize(25);recenter.setOnClickListener(v->{followCamera=true;if(lastLocation!=null)centerOn(lastLocation,17.2);});FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(50),dp(50),Gravity.END|Gravity.BOTTOM);rp.setMargins(0,0,dp(16),dp(175));root.addView(recenter,rp);
    }

    private void applyMapStyle(boolean keepCamera) {
        if(map==null)return;
        CameraPosition camera=map.getCameraPosition();
        modeButton.setText(nightMode?"☾":"☀");
        map.setStyle(new Style.Builder().fromUri(nightMode?STYLE_NIGHT:STYLE_DAY), style->{
            routeLine=null;userMarker=null;destinationMarker=null;
            if(!routePoints.isEmpty()) drawRoute();
            if(lastLocation!=null) drawUser(lastLocation);
            if(destination!=null) destinationMarker=map.addMarker(new MarkerOptions().position(destination).title(destinationName));
            if(keepCamera)map.setCameraPosition(camera);
        });
    }

    private void searchPlaces() {
        String q=search.getText().toString().trim(); if(q.length()<2){toast("Entre une adresse ou un lieu");return;}
        instructionTitle.setText("Recherche…");instructionSub.setText(q);
        new Thread(()->{
            List<SearchPlace> found=new ArrayList<>();
            try{
                String url="https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=fr&accept-language=fr&q="+URLEncoder.encode(q,"UTF-8");
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent",getPackageName()+" Routix/1.7");
                JSONArray a=new JSONArray(read(c));
                for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);found.add(new SearchPlace(o.optString("display_name","Destination"),Double.parseDouble(o.getString("lat")),Double.parseDouble(o.getString("lon"))));}
            }catch(Exception ignored){}
            runOnUiThread(()->showSearchResults(found));
        }).start();
    }

    private void showSearchResults(List<SearchPlace> found){
        if(found.isEmpty()){instructionTitle.setText("Aucun résultat");instructionSub.setText("Essaie une adresse plus précise");return;}
        String[] labels=new String[found.size()];for(int i=0;i<labels.length;i++)labels[i]=found.get(i).name;
        new AlertDialog.Builder(this).setTitle("Choisir la destination").setItems(labels,(d,w)->{
            SearchPlace p=found.get(w);destination=new LatLng(p.lat,p.lon);destinationName=p.name;search.setText(shortName(p.name));if(map!=null){if(destinationMarker!=null)map.removeMarker(destinationMarker);destinationMarker=map.addMarker(new MarkerOptions().position(destination).title(shortName(p.name)));map.animateCamera(CameraUpdateFactory.newLatLngZoom(destination,15.5));}
            if(lastLocation==null){instructionTitle.setText("Destination prête");instructionSub.setText("En attente du GPS…");}else requestRoute(lastLocation,destination,true);
        }).show();
    }

    private void requestRoute(Location from,LatLng to,boolean fit) {
        final long request=System.currentTimeMillis();lastReroute=request;instructionTitle.setText("Calcul de l’itinéraire…");instructionSub.setText(shortName(destinationName));
        new Thread(()->{
            RouteResult result=null;
            try{
                String q=String.format(Locale.US,"https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true&alternatives=false",from.getLongitude(),from.getLatitude(),to.getLongitude(),to.getLatitude());
                HttpURLConnection c=(HttpURLConnection)new URL(q).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent",getPackageName()+" Routix/1.7");
                JSONObject json=new JSONObject(read(c));JSONArray routes=json.optJSONArray("routes");if(routes!=null&&routes.length()>0){JSONObject r=routes.getJSONObject(0);RouteResult rr=new RouteResult();rr.distance=r.optDouble("distance",0);rr.duration=r.optDouble("duration",0);JSONArray coords=r.getJSONObject("geometry").getJSONArray("coordinates");for(int i=0;i<coords.length();i++){JSONArray p=coords.getJSONArray(i);rr.points.add(new LatLng(p.getDouble(1),p.getDouble(0)));}JSONArray legs=r.optJSONArray("legs");if(legs!=null&&legs.length()>0){JSONArray st=legs.getJSONObject(0).optJSONArray("steps");if(st!=null)for(int i=0;i<st.length();i++){JSONObject s=st.getJSONObject(i),m=s.optJSONObject("maneuver");if(m==null)continue;JSONArray loc=m.optJSONArray("location");if(loc==null)continue;rr.steps.add(new NavStep(m.optString("type","turn"),m.optString("modifier","straight"),s.optString("name",""),s.optDouble("distance",0),new LatLng(loc.getDouble(1),loc.getDouble(0))));}}result=rr;}
            }catch(Exception ignored){}
            RouteResult finalResult=result;runOnUiThread(()->{if(request!=lastReroute)return;if(finalResult==null||finalResult.points.size()<2){instructionTitle.setText("Itinéraire indisponible");instructionSub.setText("Vérifie la connexion réseau");return;}applyRoute(finalResult,fit);});
        }).start();
    }

    private void applyRoute(RouteResult r,boolean fit){
        routePoints.clear();routePoints.addAll(r.points);steps.clear();steps.addAll(r.steps);stepIndex=0;routeDistanceM=r.distance;routeDurationS=r.duration;
        List<GuidanceEngine.Point> gp=new ArrayList<>();for(LatLng p:routePoints)gp.add(new GuidanceEngine.Point(p.getLatitude(),p.getLongitude()));routeEngine=new GuidanceEngine(gp,new ArrayList<>());if(lastLocation!=null)routeEngine.reposition(lastLocation.getLatitude(),lastLocation.getLongitude());
        drawRoute();updateInstruction();remainingText.setText(formatDistance(routeDistanceM)+"\nRESTANT");etaText.setText(formatEta(routeDurationS)+"\nARRIVÉE");
        if(fit&&map!=null&&routePoints.size()>1){LatLngBoundsLite b=new LatLngBoundsLite(routePoints);map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.toBounds(),dp(58)),700);}
    }

    private void drawRoute(){
        if(map==null||routePoints.size()<2)return;if(routeLine!=null)map.removePolyline(routeLine);routeLine=map.addPolyline(new PolylineOptions().addAll(routePoints).color(nightMode?Color.rgb(90,195,255):BLUE).width(8f));
    }

    @Override public void onLocationChanged(@NonNull Location l){
        lastLocation=l;speedText.setText(Math.round(Math.max(0,l.hasSpeed()?l.getSpeed()*3.6f:0))+"\nKM/H");drawUser(l);if(followCamera)centerOn(l,l.hasSpeed()&&l.getSpeed()>2?17.5:16.5);
        if(destination!=null&&routePoints.isEmpty())requestRoute(l,destination,true);
        if(routeEngine!=null){GuidanceEngine.State state=routeEngine.update(l.getLatitude(),l.getLongitude(),l.getTime()>0?l.getTime():System.currentTimeMillis(),l.hasAccuracy()?l.getAccuracy():8);remainingText.setText(formatDistance(state.remainingM)+"\nRESTANT");double ratio=routeDistanceM<=0?0:Math.max(0,Math.min(1,state.remainingM/routeDistanceM));etaText.setText(formatEta(routeDurationS*ratio)+"\nARRIVÉE");advanceSteps(l);if(state.offRoute&&!state.poorAccuracy&&System.currentTimeMillis()-lastReroute>8000&&destination!=null)requestRoute(l,destination,false);if(state.finished){instructionTitle.setText("Destination atteinte");instructionSub.setText(shortName(destinationName));}}
    }

    private void advanceSteps(Location l){
        while(stepIndex<steps.size()-1&&distance(l,steps.get(stepIndex).point)<32)stepIndex++;
        updateInstruction();
        if(stepIndex<steps.size()){NavStep s=steps.get(stepIndex);instructionSub.setText(formatDistance(distance(l,s.point))+" • "+(s.name.isEmpty()?"prochaine manœuvre":s.name));}
    }

    private void updateInstruction(){
        if(steps.isEmpty()){instructionTitle.setText(destination==null?"GPS classique":"Suis l’itinéraire");return;}NavStep s=steps.get(Math.min(stepIndex,steps.size()-1));instructionTitle.setText(turnText(s));
    }

    private String turnText(NavStep s){String road=s.name.isEmpty()?"":(" sur "+s.name);String mod=s.modifier; if("arrive".equals(s.type))return"Arrivée";if("depart".equals(s.type))return"Pars"+road;if("roundabout".equals(s.type)||"rotary".equals(s.type))return"Prends le rond-point"+road;if(mod.contains("left"))return(mod.contains("slight")?"Légèrement à gauche":"Tourne à gauche")+road;if(mod.contains("right"))return(mod.contains("slight")?"Légèrement à droite":"Tourne à droite")+road;if("uturn".equals(mod))return"Fais demi-tour";return"Continue"+road;}

    private void drawUser(Location l){if(map==null)return;LatLng p=new LatLng(l.getLatitude(),l.getLongitude());if(userMarker==null)userMarker=map.addMarker(new MarkerOptions().position(p).title("Ta position"));else userMarker.setPosition(p);}
    private void centerOn(Location l,double zoom){if(map==null)return;CameraPosition.Builder b=new CameraPosition.Builder().target(new LatLng(l.getLatitude(),l.getLongitude())).zoom(zoom);if(l.hasBearing()&&l.hasSpeed()&&l.getSpeed()>2)b.bearing(l.getBearing()).tilt(38);map.animateCamera(CameraUpdateFactory.newCameraPosition(b.build()),500);}

    private void ensureLocation(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);return;}startLocation();}
    private void startLocation(){try{locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,500,0,this);Location x=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(x!=null)onLocationChanged(x);}catch(SecurityException ignored){}}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==LOCATION_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startLocation();}

    private String read(HttpURLConnection c)throws Exception{BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder b=new StringBuilder();String line;while((line=br.readLine())!=null)b.append(line);br.close();return b.toString();}
    private float distance(Location l,LatLng p){float[] d=new float[1];Location.distanceBetween(l.getLatitude(),l.getLongitude(),p.getLatitude(),p.getLongitude(),d);return d[0];}
    private String formatDistance(double m){return m>=1000?String.format(Locale.FRANCE,"%.1f km",m/1000d):String.format(Locale.FRANCE,"%.0f m",m);}
    private String formatEta(double seconds){long when=System.currentTimeMillis()+(long)(Math.max(0,seconds)*1000);return new java.text.SimpleDateFormat("HH:mm",Locale.FRANCE).format(new java.util.Date(when));}
    private String shortName(String s){if(s==null)return"Destination";int i=s.indexOf(',');return i>0?s.substring(0,i):s;}
    private int safeTop(){int id=getResources().getIdentifier("status_bar_height","dimen","android");return id>0?getResources().getDimensionPixelSize(id)+dp(5):dp(28);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private TextView label(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private TextView button(String s,int h){TextView t=label(s,17,Typeface.BOLD,Color.WHITE);t.setGravity(Gravity.CENTER);t.setBackground(glass(Color.argb(155,43,46,55),18));return t;}
    private TextView metric(String value,String title){TextView t=label(value+"\n"+title,16,Typeface.BOLD,Color.WHITE);t.setGravity(Gravity.CENTER);t.setLineSpacing(dp(2),1);return t;}
    private GradientDrawable glass(int c,int r){int a=Color.alpha(c);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(Math.min(255,a+15),Math.min(255,Color.red(c)+25),Math.min(255,Color.green(c)+25),Math.min(255,Color.blue(c)+30)),c,Color.argb(Math.max(20,a-25),Math.max(0,Color.red(c)-8),Math.max(0,Color.green(c)-8),Math.max(0,Color.blue(c)-5))});g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.argb(62,255,255,255));return g;}

    @Override protected void onStart(){super.onStart();mapView.onStart();}
    @Override protected void onResume(){super.onResume();mapView.onResume();}
    @Override protected void onPause(){mapView.onPause();super.onPause();}
    @Override protected void onStop(){mapView.onStop();super.onStop();}
    @Override public void onLowMemory(){super.onLowMemory();mapView.onLowMemory();}
    @Override protected void onSaveInstanceState(@NonNull Bundle out){super.onSaveInstanceState(out);mapView.onSaveInstanceState(out);}
    @Override protected void onDestroy(){try{locationManager.removeUpdates(this);}catch(Exception ignored){}mapView.onDestroy();super.onDestroy();}

    private static final class SearchPlace{final String name;final double lat,lon;SearchPlace(String n,double a,double o){name=n;lat=a;lon=o;}}
    private static final class NavStep{final String type,modifier,name;final double distance;final LatLng point;NavStep(String t,String m,String n,double d,LatLng p){type=t;modifier=m;name=n;distance=d;point=p;}}
    private static final class RouteResult{final List<LatLng> points=new ArrayList<>();final List<NavStep> steps=new ArrayList<>();double distance,duration;}
    private static final class LatLngBoundsLite{double minLat=90,maxLat=-90,minLon=180,maxLon=-180;LatLngBoundsLite(List<LatLng> p){for(LatLng x:p){minLat=Math.min(minLat,x.getLatitude());maxLat=Math.max(maxLat,x.getLatitude());minLon=Math.min(minLon,x.getLongitude());maxLon=Math.max(maxLon,x.getLongitude());}}org.maplibre.android.geometry.LatLngBounds toBounds(){return new org.maplibre.android.geometry.LatLngBounds.Builder().include(new LatLng(minLat,minLon)).include(new LatLng(maxLat,maxLon)).build();}}
}
