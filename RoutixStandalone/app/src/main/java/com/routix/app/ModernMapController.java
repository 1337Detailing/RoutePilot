package com.routix.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import android.location.Location;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.*;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.*;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.layers.*;
import org.maplibre.geojson.*;
import org.maplibre.geojson.Point;
import java.util.*;
import static org.maplibre.android.style.layers.PropertyFactory.*;

/** Explicit lifecycle and data ownership. No GPS subscription, reflection or polling. */
final class ModernMapController {
    private final MapView view;private final SharedPreferences prefs;private MapLibreMap map;private Style style;
    private List<org.osmdroid.util.GeoPoint> route=Collections.emptyList();private Location location;
    private boolean follow=true,heading=true,destroyed,visible=true;private double bearing;private String styleUri;
    private final Bitmap userIcon;
    ModernMapController(Activity activity,FrameLayout root,Bitmap icon,Bundle state){
        prefs=activity.getSharedPreferences("routix",0);userIcon=icon;MapLibre.getInstance(activity);
        view=new MapView(activity,MapLibreMapOptions.createFromAttributes(activity).textureMode(true));view.onCreate(state);
        root.addView(view,1,new FrameLayout.LayoutParams(-1,-1));
        view.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)follow=false;return false;});
        view.addOnDidFailLoadingMapListener(error->DiagnosticLog.info("map style load failed: "+error));
        view.getMapAsync(m->{if(destroyed)return;map=m;map.getUiSettings().setAttributionGravity(Gravity.TOP|Gravity.LEFT);map.getUiSettings().setLogoEnabled(false);refreshStyle();});
    }
    void refreshStyle(){if(map==null||destroyed)return;styleUri=MapStyles.uri(prefs);style=null;
        map.setStyle(new Style.Builder().fromUri(styleUri),s->{if(destroyed)return;style=s;
            s.addSource(new GeoJsonSource("route",FeatureCollection.fromFeatures(new Feature[0])));
            s.addSource(new GeoJsonSource("position",FeatureCollection.fromFeatures(new Feature[0])));
            s.addImage("truck",userIcon);s.addImage("direction",arrow());
            s.addLayer(new LineLayer("route-casing","route").withProperties(lineColor("#ffffff"),lineWidth(9f),lineJoin("round"),lineCap("round")));
            s.addLayer(new LineLayer("route-line","route").withProperties(lineColor(prefs.getInt("accent_color",0xffcba6f7)),lineWidth(6f),lineJoin("round"),lineCap("round")));
            s.addLayer(new SymbolLayer("route-arrows","route").withProperties(symbolPlacement("line"),symbolSpacing(75f),iconImage("direction"),iconSize(.65f),iconAllowOverlap(true),iconRotationAlignment("map")));
            s.addLayer(new SymbolLayer("user","position").withProperties(iconImage("truck"),iconAllowOverlap(true),iconIgnorePlacement(true),iconSize(.75f)));
            renderRoute();update(location,false);
        });
    }
    private Bitmap arrow(){Bitmap b=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);Path path=new Path();path.moveTo(10,5);path.lineTo(20,12);path.lineTo(10,19);c.drawPath(path,p);return b;}
    void setRoute(List<org.osmdroid.util.GeoPoint> points){route=points;if(visible)renderRoute();}
    private void renderRoute(){if(style==null||!style.isFullyLoaded())return;GeoJsonSource source=style.getSourceAs("route");if(source==null)return;
        List<Point> p=new ArrayList<>();for(org.osmdroid.util.GeoPoint x:route)p.add(Point.fromLngLat(x.getLongitude(),x.getLatitude()));
        source.setGeoJson(p.size()<2?FeatureCollection.fromFeatures(new Feature[0]):FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(LineString.fromLngLats(p))}));
    }
    void update(Location fix,boolean guiding){location=fix;if(!visible||map==null||style==null||fix==null||destroyed)return;
        if(!MapStyles.uri(prefs).equals(styleUri)){refreshStyle();return;}
        GeoJsonSource source=style.getSourceAs("position");if(source!=null)source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(fix.getLongitude(),fix.getLatitude())));
        if(heading&&fix.hasBearing()&&fix.hasSpeed()&&fix.getSpeed()>.9f&&fix.getAccuracy()<35)bearing=MapStyles.smoothBearing(bearing,fix.getBearing());
        if(follow){CameraPosition next=new CameraPosition.Builder(map.getCameraPosition()).target(new LatLng(fix.getLatitude(),fix.getLongitude())).zoom(guiding?17.7:16.8).bearing(heading&&guiding?bearing:0).tilt(guiding?30:0).build();map.easeCamera(CameraUpdateFactory.newCameraPosition(next),650);}
    }
    void recenter(Location l,boolean guiding){follow=true;update(l,guiding);}
    void heading(boolean enabled){heading=enabled;follow=true;if(map!=null&&!enabled)map.easeCamera(CameraUpdateFactory.bearingTo(0),400);}
    void setVisible(boolean enabled){visible=enabled;view.setVisibility(enabled?View.VISIBLE:View.GONE);if(enabled)renderRoute();}
    void inset(int top){if(map!=null)map.getUiSettings().setAttributionMargins(12,top+8,0,0);}
    void onStart(){view.onStart();}void onResume(){view.onResume();}void onPause(){view.onPause();}void onStop(){view.onStop();}
    void onSaveInstanceState(Bundle state){view.onSaveInstanceState(state);}void onLowMemory(){view.onLowMemory();}
    void onDestroy(){destroyed=true;view.onDestroy();if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}
}
