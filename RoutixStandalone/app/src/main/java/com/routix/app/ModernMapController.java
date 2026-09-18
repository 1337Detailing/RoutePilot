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
    private static final int MAX_ROUTE_RENDER_POINTS=6000,MAX_ARROW_RENDER_POINTS=1600;
    private final MapView view;private final SharedPreferences prefs;private MapLibreMap map;private Style style;
    private List<org.osmdroid.util.GeoPoint> route=Collections.emptyList(),approach=Collections.emptyList();private Location location;
    private long routeSignature=Long.MIN_VALUE,approachSignature=Long.MIN_VALUE;
    private boolean follow=true,heading=true,destroyed,visible=true,bearingReady;private double bearing;private String styleUri;
    private List<RouteStore.Event> events=Collections.emptyList();private int eventsSignature=Integer.MIN_VALUE;
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
            s.addSource(empty("route"));s.addSource(empty("arrows"));s.addSource(empty("approach"));s.addSource(empty("approach-arrows"));
            s.addSource(empty("events"));s.addSource(empty("position"));
            s.addImage("truck",userIcon);s.addImage("direction",arrow());
            int routeColor=prefs.getInt("route_color",prefs.getInt("accent_color",0xff89b4fa));
            String routeHex=String.format(Locale.US,"#%06X",(0xFFFFFF&routeColor));
            s.addLayer(new LineLayer("route-casing","route").withProperties(lineColor(MapStyles.dark(prefs)?"#171925":"#ffffff"),lineWidth(12f),lineOpacity(.95f),lineJoin("round"),lineCap("round")));
            s.addLayer(new LineLayer("route-line","route").withProperties(lineColor(routeHex),lineWidth(7f),lineJoin("round"),lineCap("round")));
            s.addLayer(new SymbolLayer("route-arrows","arrows").withProperties(symbolPlacement("line"),symbolSpacing(54f),iconImage("direction"),iconSize(.86f),iconAllowOverlap(false),iconKeepUpright(false),iconRotationAlignment("map")));
            s.addLayer(new LineLayer("approach-casing","approach").withProperties(lineColor("#16252b"),lineWidth(10f),lineOpacity(.92f),lineJoin("round"),lineCap("round")));
            s.addLayer(new LineLayer("approach-line","approach").withProperties(lineColor("#94e2d5"),lineWidth(6f),lineJoin("round"),lineCap("round")));
            s.addLayer(new SymbolLayer("approach-arrows-layer","approach-arrows").withProperties(symbolPlacement("line"),symbolSpacing(62f),iconImage("direction"),iconSize(.78f),iconAllowOverlap(false),iconKeepUpright(false),iconRotationAlignment("map")));
            s.addLayer(new SymbolLayer("user","position").withProperties(iconImage("truck"),iconAllowOverlap(true),iconIgnorePlacement(true),iconSize(.78f)));
            s.addLayer(new CircleLayer("marker-dots","events").withProperties(circleColor("#fab387"),circleRadius(6f),circleStrokeColor("#181825"),circleStrokeWidth(2f)));
            s.addLayer(new SymbolLayer("marker-labels","events").withProperties(textField(org.maplibre.android.style.expressions.Expression.get("label")),textSize(12f),textColor(MapStyles.dark(prefs)?"#f5f3ff":"#312d40"),textHaloColor(MapStyles.dark(prefs)?"#181825":"#ffffff"),textHaloWidth(2f),textOffset(new Float[]{0f,1.5f})));
            renderRoute();renderApproach();renderEvents();update(location,false);
        });
    }
    private static GeoJsonSource empty(String id){return new GeoJsonSource(id,FeatureCollection.fromFeatures(new Feature[0]));}
    private Bitmap arrow(){Bitmap b=Bitmap.createBitmap(36,28,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();path.moveTo(10,5);path.lineTo(24,14);path.lineTo(10,23);c.drawPath(path,p);return b;}
    void setRoute(List<org.osmdroid.util.GeoPoint> points){List<org.osmdroid.util.GeoPoint> next=points==null?Collections.emptyList():points;long signature=signature(next);if(signature==routeSignature)return;routeSignature=signature;route=new ArrayList<>(next);if(visible)renderRoute();}
    void setApproach(List<org.osmdroid.util.GeoPoint> points){List<org.osmdroid.util.GeoPoint> next=points==null?Collections.emptyList():points;long signature=signature(next);if(signature==approachSignature)return;approachSignature=signature;approach=new ArrayList<>(next);if(visible)renderApproach();}
    void setEvents(List<RouteStore.Event> values){List<RouteStore.Event> next=values==null?Collections.emptyList():values;int signature=eventsSignature(next);if(signature==eventsSignature)return;eventsSignature=signature;events=new ArrayList<>(next);if(visible)renderEvents();}
    private static long signature(List<org.osmdroid.util.GeoPoint> points){if(points==null||points.isEmpty())return 0;long h=points.size();int[] index={0,points.size()/2,points.size()-1};for(int i:index){org.osmdroid.util.GeoPoint p=points.get(i);h=31*h+Double.doubleToLongBits(p.getLatitude());h=31*h+Double.doubleToLongBits(p.getLongitude());}return h;}
    private static int eventsSignature(List<RouteStore.Event> values){int h=1;if(values!=null)for(RouteStore.Event e:values){h=31*h+(e.type==null?0:e.type.hashCode());h=31*h+(e.label==null?0:e.label.hashCode());long lat=Double.doubleToLongBits(e.lat),lon=Double.doubleToLongBits(e.lon);h=31*h+(int)(lat^(lat>>>32));h=31*h+(int)(lon^(lon>>>32));h=31*h+(int)(e.time^(e.time>>>32));}return h;}
    private void renderEvents(){if(style==null)return;GeoJsonSource source=style.getSourceAs("events");if(source==null)return;List<Feature> features=new ArrayList<>();for(RouteStore.Event e:events){Feature f=Feature.fromGeometry(Point.fromLngLat(e.lon,e.lat));f.addStringProperty("label",e.label);features.add(f);}source.setGeoJson(FeatureCollection.fromFeatures(features));}
    private void renderRoute(){renderLine("route","arrows",route,950);}
    private void renderApproach(){renderLine("approach","approach-arrows",approach,1200);}
    private void renderLine(String sourceId,String arrowsId,List<org.osmdroid.util.GeoPoint> input,double arrowHorizon){
        if(style==null||!style.isFullyLoaded())return;GeoJsonSource source=style.getSourceAs(sourceId);if(source==null)return;
        List<Point> p=new ArrayList<>();int stride=Math.max(1,(int)Math.ceil(input.size()/(double)MAX_ROUTE_RENDER_POINTS));
        for(int i=0;i<input.size();i+=stride){org.osmdroid.util.GeoPoint x=input.get(i);p.add(Point.fromLngLat(x.getLongitude(),x.getLatitude()));}
        if(!input.isEmpty()&&((input.size()-1)%stride)!=0){org.osmdroid.util.GeoPoint x=input.get(input.size()-1);p.add(Point.fromLngLat(x.getLongitude(),x.getLatitude()));}
        int aheadEnd=0;double distance=0;for(int i=1;i<p.size();i++){float[] d=new float[1];Location.distanceBetween(p.get(i-1).latitude(),p.get(i-1).longitude(),p.get(i).latitude(),p.get(i).longitude(),d);distance+=d[0];aheadEnd=i;if(distance>arrowHorizon)break;}
        List<Point> ahead=new ArrayList<>();int aheadCount=aheadEnd+1,aheadStride=Math.max(1,(int)Math.ceil(aheadCount/(double)MAX_ARROW_RENDER_POINTS));for(int i=0;i<=aheadEnd&&i<p.size();i+=aheadStride)ahead.add(p.get(i));
        if(aheadEnd>0&&aheadEnd<p.size()&&(ahead.isEmpty()||ahead.get(ahead.size()-1)!=p.get(aheadEnd)))ahead.add(p.get(aheadEnd));
        GeoJsonSource arrows=style.getSourceAs(arrowsId);if(arrows!=null)arrows.setGeoJson(ahead.size()<2?FeatureCollection.fromFeatures(new Feature[0]):FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(LineString.fromLngLats(ahead))}));
        source.setGeoJson(p.size()<2?FeatureCollection.fromFeatures(new Feature[0]):FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(LineString.fromLngLats(p))}));
    }
    void update(Location fix,boolean guiding){location=fix;if(!visible||map==null||style==null||fix==null||destroyed)return;
        if(!MapStyles.uri(prefs).equals(styleUri)){refreshStyle();return;}
        GeoJsonSource source=style.getSourceAs("position");if(source!=null)source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(fix.getLongitude(),fix.getLatitude())));
        if(heading&&fix.hasBearing()&&fix.hasSpeed()&&fix.getSpeed()>.9f&&fix.getAccuracy()<35){double target=fix.getBearing();bearing=bearingReady?MapStyles.smoothBearing(bearing,target):target;bearingReady=true;}
        if(follow){CameraPosition next=new CameraPosition.Builder(map.getCameraPosition()).target(new LatLng(fix.getLatitude(),fix.getLongitude())).zoom(guiding?17.5:16.8).bearing(heading&&guiding?bearing:0).tilt(guiding?28:0).build();map.easeCamera(CameraUpdateFactory.newCameraPosition(next),540);}
    }
    void recenter(Location l,boolean guiding){follow=true;update(l,guiding);}
    void heading(boolean enabled){heading=enabled;follow=true;if(map!=null&&!enabled)map.easeCamera(CameraUpdateFactory.bearingTo(0),350);}
    void setVisible(boolean enabled){visible=enabled;view.setVisibility(enabled?View.VISIBLE:View.GONE);if(enabled){renderRoute();renderApproach();renderEvents();}}
    void inset(int top){if(map!=null)map.getUiSettings().setAttributionMargins(12,top+8,0,0);}
    void onStart(){view.onStart();}void onResume(){view.onResume();}void onPause(){view.onPause();}void onStop(){view.onStop();}
    void onSaveInstanceState(Bundle state){view.onSaveInstanceState(state);}void onLowMemory(){view.onLowMemory();}
    void onDestroy(){destroyed=true;style=null;map=null;location=null;route=Collections.emptyList();approach=Collections.emptyList();events=Collections.emptyList();routeSignature=Long.MIN_VALUE;approachSignature=Long.MIN_VALUE;eventsSignature=Integer.MIN_VALUE;view.onDestroy();if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}
}
