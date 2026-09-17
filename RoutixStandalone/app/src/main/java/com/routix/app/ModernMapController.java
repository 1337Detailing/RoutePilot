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
    private static final int MAX_ROUTE_RENDER_POINTS=6000,MAX_ARROW_RENDER_POINTS=1200;
    private final MapView view;private final SharedPreferences prefs;private MapLibreMap map;private Style style;
    private List<org.osmdroid.util.GeoPoint> route=Collections.emptyList();private Location location;
    private boolean follow=true,heading=true,destroyed,visible=true,bearingReady;private double bearing;private String styleUri;
    private List<RouteStore.Event> events=Collections.emptyList();
    private final Bitmap userIcon;
    ModernMapController(Activity activity,FrameLayout root,Bitmap icon,Bundle state){
        prefs=activity.getSharedPreferences("routix",0);userIcon=icon;MapLibre.getInstance(activity);
        view=new MapView(activity,MapLibreMapOptions.createFromAttributes(activity).textureMode(true));view.onCreate(state);
        root.addView(view,1,new FrameLayout.LayoutParams(-1,-1));
        view.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)follow=false;return false;});
        view.addOnDidFailLoadingMapListener(error->DiagnosticLog.info("map style load failed: "+error));
        view.getMapAsync(m->{if(destroyed)return;map=m;map.getUiSettings().setAttributionGravity(Gravity.TOP|Gravity.LEFT);map.getUiSettings().setLogoEnabled(false);refreshStyle();});
    }
    void refreshStyle(){if(map==null||destroyed)return;String requested=MapStyles.uri(prefs);styleUri=requested;style=null;
        map.setStyle(new Style.Builder().fromUri(requested),s->{if(destroyed||!requested.equals(styleUri))return;style=s;
            s.addSource(new GeoJsonSource("route",FeatureCollection.fromFeatures(new Feature[0])));
            s.addSource(new GeoJsonSource("arrows",FeatureCollection.fromFeatures(new Feature[0])));s.addSource(new GeoJsonSource("events",FeatureCollection.fromFeatures(new Feature[0])));
            s.addSource(new GeoJsonSource("position",FeatureCollection.fromFeatures(new Feature[0])));
            s.addImage("truck",userIcon);s.addImage("direction",arrow());
            s.addLayer(new LineLayer("route-casing","route").withProperties(lineColor("#ffffff"),lineWidth(9f),lineJoin("round"),lineCap("round")));
            s.addLayer(new LineLayer("route-line","route").withProperties(lineColor(prefs.getInt("accent_color",0xffcba6f7)),lineWidth(6f),lineJoin("round"),lineCap("round")));
            s.addLayer(new SymbolLayer("route-arrows","arrows").withProperties(symbolPlacement("line"),symbolSpacing(75f),iconImage("direction"),iconSize(.65f),iconAllowOverlap(false),iconKeepUpright(false),iconRotationAlignment("map")));
            s.addLayer(new SymbolLayer("user","position").withProperties(iconImage("truck"),iconAllowOverlap(true),iconIgnorePlacement(true),iconSize(.75f)));
            s.addLayer(new CircleLayer("marker-dots","events").withProperties(circleColor("#fab387"),circleRadius(6f),circleStrokeColor("#181825"),circleStrokeWidth(2f)));
            s.addLayer(new SymbolLayer("marker-labels","events").withProperties(textField(org.maplibre.android.style.expressions.Expression.get("label")),textSize(12f),textColor(MapStyles.dark(prefs)?"#f5f3ff":"#312d40"),textHaloColor(MapStyles.dark(prefs)?"#181825":"#ffffff"),textHaloWidth(2f),textOffset(new Float[]{0f,1.5f})));
            renderRoute();renderEvents();update(location,false);
        });
    }
    private Bitmap arrow(){Bitmap b=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);Path path=new Path();path.moveTo(10,5);path.lineTo(20,12);path.lineTo(10,19);c.drawPath(path,p);return b;}
    void setRoute(List<org.osmdroid.util.GeoPoint> points){route=points==null?Collections.emptyList():new ArrayList<>(points);if(visible)renderRoute();}
    void setEvents(List<RouteStore.Event> values){events=values==null?Collections.emptyList():new ArrayList<>(values);if(visible)renderEvents();}
    private void renderEvents(){if(style==null)return;GeoJsonSource source=style.getSourceAs("events");if(source==null)return;List<Feature> features=new ArrayList<>();for(RouteStore.Event e:events){Feature f=Feature.fromGeometry(Point.fromLngLat(e.lon,e.lat));f.addStringProperty("label",e.label);features.add(f);}source.setGeoJson(FeatureCollection.fromFeatures(features));}
    private void renderRoute(){if(style==null||!style.isFullyLoaded())return;GeoJsonSource source=style.getSourceAs("route");if(source==null)return;
        List<Point> p=new ArrayList<>();int stride=Math.max(1,(int)Math.ceil(route.size()/(double)MAX_ROUTE_RENDER_POINTS));for(int i=0;i<route.size();i+=stride){org.osmdroid.util.GeoPoint x=route.get(i);p.add(Point.fromLngLat(x.getLongitude(),x.getLatitude()));}if(!route.isEmpty()&&((route.size()-1)%stride)!=0){org.osmdroid.util.GeoPoint x=route.get(route.size()-1);p.add(Point.fromLngLat(x.getLongitude(),x.getLatitude()));}
        List<Point> ahead=new ArrayList<>();double distance=0;org.osmdroid.util.GeoPoint previous=null;for(org.osmdroid.util.GeoPoint x:route){if(previous!=null){float[] d=new float[1];Location.distanceBetween(previous.getLatitude(),previous.getLongitude(),x.getLatitude(),x.getLongitude(),d);distance+=d[0];}if(ahead.size()<MAX_ARROW_RENDER_POINTS)ahead.add(Point.fromLngLat(x.getLongitude(),x.getLatitude()));previous=x;if(distance>350)break;}
        GeoJsonSource arrows=style.getSourceAs("arrows");if(arrows!=null)arrows.setGeoJson(ahead.size()<2?FeatureCollection.fromFeatures(new Feature[0]):FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(LineString.fromLngLats(ahead))}));
        source.setGeoJson(p.size()<2?FeatureCollection.fromFeatures(new Feature[0]):FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(LineString.fromLngLats(p))}));
    }
    void update(Location fix,boolean guiding){location=fix;if(!visible||map==null||style==null||fix==null||destroyed)return;
        if(!MapStyles.uri(prefs).equals(styleUri)){refreshStyle();return;}
        GeoJsonSource source=style.getSourceAs("position");if(source!=null)source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(fix.getLongitude(),fix.getLatitude())));
        if(heading&&fix.hasBearing()&&fix.hasSpeed()&&fix.getSpeed()>.9f&&fix.getAccuracy()<35){double target=fix.getBearing();bearing=bearingReady?MapStyles.smoothBearing(bearing,target):target;bearingReady=true;}
        if(follow){CameraPosition next=new CameraPosition.Builder(map.getCameraPosition()).target(new LatLng(fix.getLatitude(),fix.getLongitude())).zoom(guiding?17.7:16.8).bearing(heading&&guiding?bearing:0).tilt(guiding?30:0).build();map.easeCamera(CameraUpdateFactory.newCameraPosition(next),650);}
    }
    void recenter(Location l,boolean guiding){follow=true;update(l,guiding);}
    void heading(boolean enabled){heading=enabled;follow=true;if(map!=null&&!enabled)map.easeCamera(CameraUpdateFactory.bearingTo(0),400);}
    void setVisible(boolean enabled){visible=enabled;view.setVisibility(enabled?View.VISIBLE:View.GONE);if(enabled){renderRoute();renderEvents();}}
    void inset(int top){if(map!=null)map.getUiSettings().setAttributionMargins(12,top+8,0,0);}
    void onStart(){view.onStart();}void onResume(){view.onResume();}void onPause(){view.onPause();}void onStop(){view.onStop();}
    void onSaveInstanceState(Bundle state){view.onSaveInstanceState(state);}void onLowMemory(){view.onLowMemory();}
    void onDestroy(){destroyed=true;style=null;map=null;location=null;route=Collections.emptyList();events=Collections.emptyList();view.onDestroy();if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}
}
