package com.routix.app;

import android.location.Location;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Lightweight in-app road route used only to reach the first point of a collection route. */
final class DepartureNavigation {
    static final float MAX_APPROACH_DISTANCE_M=20_000f;
    static final float MAX_ADVANCE_DISTANCE_M=120f;
    static final long MAX_FIX_AGE_MS=15_000;
    interface Callback { void ready(Result result); }

    static final class Result {
        final List<GeoPoint> points;
        final double distanceM,durationS;
        final boolean roadRouted;
        final double originLat,originLon,targetLat,targetLon;
        Result(List<GeoPoint> points,double distanceM,double durationS,boolean roadRouted,
               double originLat,double originLon,double targetLat,double targetLon){
            this.points=points==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(points));
            this.distanceM=distanceM;this.durationS=durationS;this.roadRouted=roadRouted;
            this.originLat=originLat;this.originLon=originLon;this.targetLat=targetLat;this.targetLon=targetLon;
        }
        boolean usable(){return points.size()>=2;}
        boolean matchesTarget(RouteStore.Point p){
            return p!=null&&Math.abs(targetLat-p.lat)<1e-6&&Math.abs(targetLon-p.lon)<1e-6;
        }
    }

    static boolean isFreshFix(Location fix){
        if(fix==null||!RouteStore.validCoordinates(fix.getLatitude(),fix.getLongitude())||!fix.hasAccuracy()||!Float.isFinite(fix.getAccuracy())||fix.getAccuracy()<0||fix.getAccuracy()>80)return false;
        long elapsed=fix.getElapsedRealtimeNanos(),nowElapsed=SystemClock.elapsedRealtimeNanos();
        long ageMs;
        if(elapsed>0&&nowElapsed>=elapsed)ageMs=(nowElapsed-elapsed)/1_000_000L;
        else if(fix.getTime()>0)ageMs=Math.max(0,System.currentTimeMillis()-fix.getTime());
        else return false;
        return ageMs<=MAX_FIX_AGE_MS;
    }

    static float distanceTo(Location origin,RouteStore.Point target){
        if(origin==null||target==null)return Float.MAX_VALUE;
        float[] d=new float[1];Location.distanceBetween(origin.getLatitude(),origin.getLongitude(),target.lat,target.lon,d);return d[0];
    }

    static boolean plausible(Location origin,RouteStore.Point target){
        float d=distanceTo(origin,target);return Float.isFinite(d)&&d<=MAX_APPROACH_DISTANCE_M;
    }

    static void calculate(Location origin,RouteStore.Point target,Callback callback){
        if(callback==null)return;
        if(!isFreshFix(origin)||target==null||!plausible(origin,target)){
            callback.ready(new Result(Collections.emptyList(),0,0,false,
                    origin==null?Double.NaN:origin.getLatitude(),origin==null?Double.NaN:origin.getLongitude(),
                    target==null?Double.NaN:target.lat,target==null?Double.NaN:target.lon));return;
        }
        Location frozenOrigin=new Location(origin);
        RouteStore.Point frozenTarget=new RouteStore.Point(target.lat,target.lon,target.time,target.accuracy);
        new Thread(()->callback.ready(request(frozenOrigin,frozenTarget)),"routix-approach-route").start();
    }

    private static Result request(Location origin,RouteStore.Point target){
        HttpURLConnection connection=null;
        try{
            String url=String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=false",
                    origin.getLongitude(),origin.getLatitude(),target.lon,target.lat);
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(5500);connection.setReadTimeout(7000);
            connection.setRequestProperty("User-Agent","Routix/2.1");
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream()))){
                StringBuilder b=new StringBuilder();String line;
                while((line=reader.readLine())!=null&&b.length()<2_000_000)b.append(line);
                JSONObject response=new JSONObject(b.toString());
                if(!"Ok".equalsIgnoreCase(response.optString("code","Ok")))throw new IllegalStateException("OSRM "+response.optString("code"));
                JSONObject route=response.getJSONArray("routes").getJSONObject(0);
                JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
                List<GeoPoint> points=new ArrayList<>(coords.length());
                for(int i=0;i<coords.length();i++){
                    JSONArray x=coords.getJSONArray(i);points.add(new GeoPoint(x.getDouble(1),x.getDouble(0)));
                }
                double distance=route.optDouble("distance",0);
                if(points.size()>=2&&distance<=MAX_APPROACH_DISTANCE_M*1.35)
                    return new Result(points,distance,route.optDouble("duration",0),true,
                            origin.getLatitude(),origin.getLongitude(),target.lat,target.lon);
                DiagnosticLog.info("approach route rejected: distance="+distance+" points="+points.size());
            }
        }catch(Exception e){DiagnosticLog.error("internal departure routing",e);}
        finally{if(connection!=null)connection.disconnect();}

        // Network/routing failure: keep a safe direct line only when both endpoints were validated.
        List<GeoPoint> direct=new ArrayList<>(2);
        direct.add(new GeoPoint(origin.getLatitude(),origin.getLongitude()));direct.add(new GeoPoint(target.lat,target.lon));
        float d=distanceTo(origin,target);
        return new Result(direct,d,0,false,origin.getLatitude(),origin.getLongitude(),target.lat,target.lon);
    }

    /** Find the closest remaining approach point without ever moving the route backwards. */
    static int advanceIndex(List<GeoPoint> points,int from,Location fix){
        if(points==null||points.isEmpty()||fix==null)return Math.max(0,from);
        int start=Math.max(0,Math.min(from,points.size()-1));
        int end=Math.min(points.size()-1,start+240);
        int best=start;float bestDistance=Float.MAX_VALUE;
        for(int i=start;i<=end;i++){
            GeoPoint p=points.get(i);float[] d=new float[1];
            Location.distanceBetween(fix.getLatitude(),fix.getLongitude(),p.getLatitude(),p.getLongitude(),d);
            if(d[0]<bestDistance){bestDistance=d[0];best=i;}
        }
        // A stale approach route can otherwise snap hundreds of points forward simply because
        // the current GPS fix happens to be marginally closer to a distant segment. Keep the
        // current anchor until the vehicle is actually near the calculated road route.
        if(bestDistance>MAX_ADVANCE_DISTANCE_M)return start;
        return Math.max(start,best);
    }

    static List<GeoPoint> remaining(Result result,int from,Location fix){
        if(result==null||!result.usable())return Collections.emptyList();
        int index=advanceIndex(result.points,from,fix);
        List<GeoPoint> out=new ArrayList<>(result.points.size()-index+1);
        if(fix!=null)out.add(new GeoPoint(fix.getLatitude(),fix.getLongitude()));
        for(int i=index;i<result.points.size();i++)out.add(result.points.get(i));
        return out;
    }
}
