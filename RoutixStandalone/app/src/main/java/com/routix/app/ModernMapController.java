package com.routix.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Optional modern vector-map layer used by the main Routix screen. */
final class ModernMapController implements LocationListener {
    private static final String STYLE_DAY="https://tiles.openfreemap.org/styles/liberty";
    private static final String STYLE_NIGHT="https://tiles.openfreemap.org/styles/dark";
    private static final int BLUE=Color.rgb(10,132,255);

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final View legacyMap;
    private MapView mapView;
    private MapLibreMap map;
    private LocationManager lm;
    private Marker userMarker;
    private Polyline routeLine;
    private boolean active;
    private boolean firstFix=true;
    private boolean followUser=true;
    private org.maplibre.android.annotations.Icon userIcon;

    ModernMapController(Activity activity){
        this.activity=activity;
        prefs=activity.getSharedPreferences("routix",Activity.MODE_PRIVATE);
        root=(FrameLayout)getField(activity,"root");
        legacyMap=(View)getField(activity,"map");
        if(root==null||legacyMap==null)return;
        MapLibre.getInstance(activity);
        mapView=new MapView(activity);
        mapView.onCreate((Bundle)null);
        root.addView(mapView,1,new FrameLayout.LayoutParams(-1,-1));
        mapView.setOnTouchListener((v,e)->{if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)followUser=false;return false;});
        mapView.getMapAsync(m->{map=m;map.getUiSettings().setLogoEnabled(false);map.getUiSettings().setAttributionEnabled(true);map.getUiSettings().setAttributionGravity(android.view.Gravity.TOP|android.view.Gravity.LEFT);applyStyle();});
        lm=(LocationManager)activity.getSystemService(Activity.LOCATION_SERVICE);
        setActive("modern".equals(prefs.getString("map_engine","legacy")));

    }

    void setActive(boolean enable){
        active=enable;
        prefs.edit().putString("map_engine",enable?"modern":"legacy").apply();
        if(mapView!=null)mapView.setVisibility(enable&&legacyMap!=null&&legacyMap.getVisibility()==View.VISIBLE?View.VISIBLE:View.GONE);
        if(legacyMap!=null)legacyMap.setAlpha(enable&&!isGuiding()?0f:1f);
        TextView chip=(TextView)getField(activity,"mapChip");
        if(chip!=null){chip.setText(enable?"Moderne":"Carte");chip.setTextColor(enable?Color.rgb(100,210,255):Color.WHITE);}
        if(enable)startLocation();else stopLocation();
    }

    boolean isActive(){return active;}

    void refreshStyle(){applyStyle();}
    private boolean isGuiding(){return Boolean.TRUE.equals(getField(activity,"guiding"));}
    void recenter(Location location){followUser=true;if(map!=null)map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),location.getLongitude()),17.5));}

    private void applyStyle(){
        if(map==null)return;
        boolean night="dark".equals(prefs.getString("map_appearance","light"));
        map.setStyle(new Style.Builder().fromUri(night?STYLE_NIGHT:STYLE_DAY),s->refreshRoute());
    }

    private void startLocation(){
        if(lm==null)return;
        if(ContextCompat.checkSelfPermission(activity,android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&ContextCompat.checkSelfPermission(activity,android.Manifest.permission.ACCESS_COARSE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,750L,0f,this);Location l=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(l!=null)onLocationChanged(l);}catch(SecurityException ignored){}
    }
    private void stopLocation(){if(lm!=null)try{lm.removeUpdates(this);}catch(Exception ignored){}}

    @Override public void onLocationChanged(Location l){
        if(!active||map==null||l==null)return;
        LatLng p=new LatLng(l.getLatitude(),l.getLongitude());
        if(userMarker==null)userMarker=map.addMarker(new MarkerOptions().position(p).icon(positionIcon()).title("Ma position"));else userMarker.setPosition(p);
        if(firstFix||followUser){map.animateCamera(CameraUpdateFactory.newLatLngZoom(p,17.2));firstFix=false;}
        refreshRoute();
    }

    private void refreshRoute(){
        if(!active||map==null)return;
        Object raw=getField(activity,"points");
        if(!(raw instanceof List))return;
        List<?> pts=(List<?>)raw;
        ArrayList<LatLng> ll=new ArrayList<>();
        for(Object p:pts){try{Field lat=p.getClass().getDeclaredField("lat"),lon=p.getClass().getDeclaredField("lon");lat.setAccessible(true);lon.setAccessible(true);ll.add(new LatLng(lat.getDouble(p),lon.getDouble(p)));}catch(Exception ignored){}}
        if(routeLine!=null){map.removePolyline(routeLine);routeLine=null;}
        if(ll.size()>1)routeLine=map.addPolyline(new PolylineOptions().addAll(ll).color(BLUE).width(6f));
    }

    private org.maplibre.android.annotations.Icon positionIcon(){
        if(userIcon==null){
            int size=Math.round(36*activity.getResources().getDisplayMetrics().density);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(bitmap);android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.argb(55,10,132,255));c.drawCircle(size*.5f,size*.5f,size*.49f,p);p.setColor(Color.WHITE);c.drawCircle(size*.5f,size*.5f,size*.32f,p);p.setColor(BLUE);c.drawCircle(size*.5f,size*.5f,size*.23f,p);userIcon=org.maplibre.android.annotations.IconFactory.getInstance(activity).fromBitmap(bitmap);
        }return userIcon;
    }
    private final Runnable syncVisibility=new Runnable(){@Override public void run(){
        boolean visible=active&&!isGuiding()&&legacyMap!=null&&legacyMap.getVisibility()==View.VISIBLE;
        if(mapView!=null)mapView.setVisibility(visible?View.VISIBLE:View.GONE);
        if(legacyMap!=null)legacyMap.setAlpha(visible?0f:1f);
        if(map!=null){View header=(View)getField(activity,"topBar");int top=header==null?0:header.getBottom();int margin=Math.round(12*activity.getResources().getDisplayMetrics().density);map.getUiSettings().setAttributionMargins(margin,top+margin,0,0);}
        if(visible)refreshRoute();
        handler.postDelayed(this,500);
    }};

    void onStart(){if(mapView!=null)mapView.onStart();}
    void onResume(){if(mapView!=null)mapView.onResume();if(active)startLocation();handler.removeCallbacks(syncVisibility);handler.post(syncVisibility);}
    void onPause(){handler.removeCallbacks(syncVisibility);if(mapView!=null)mapView.onPause();stopLocation();}
    void onStop(){if(mapView!=null)mapView.onStop();}
    void onLowMemory(){if(mapView!=null)mapView.onLowMemory();}
    void onDestroy(){handler.removeCallbacksAndMessages(null);stopLocation();if(mapView!=null)mapView.onDestroy();}

    private static Object getField(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){return null;}}
}
