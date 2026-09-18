package com.routix.app;

import android.location.Location;
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
    interface Callback { void ready(Result result); }

    static final class Result {
        final List<GeoPoint> points;
        final double distanceM,durationS;
        final boolean roadRouted;
        Result(List<GeoPoint> points,double distanceM,double durationS,boolean roadRouted){
            this.points=points==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(points));
            this.distanceM=distanceM;this.durationS=durationS;this.roadRouted=roadRouted;
        }
        boolean usable(){return points.size()>=2;}
    }

    static void calculate(Location origin,RouteStore.Point target,Callback callback){
        if(callback==null)return;
        if(origin==null||target==null){callback.ready(new Result(Collections.emptyList(),0,0,false));return;}
        new Thread(()->callback.ready(request(origin,target)),"routix-approach-route").start();
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
                JSONObject route=new JSONObject(b.toString()).getJSONArray("routes").getJSONObject(0);
                JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");
                List<GeoPoint> points=new ArrayList<>(coords.length());
                for(int i=0;i<coords.length();i++){
                    JSONArray c=coords.getJSONArray(i);
                    points.add(new GeoPoint(c.getDouble(1),c.getDouble(0)));
                }
                if(points.size()>=2)return new Result(points,route.optDouble("distance",0),route.optDouble("duration",0),true);
            }
        }catch(Exception e){DiagnosticLog.error("internal departure routing",e);}
        finally{if(connection!=null)connection.disconnect();}
        List<GeoPoint> direct=new ArrayList<>(2);
        direct.add(new GeoPoint(origin.getLatitude(),origin.getLongitude()));
        direct.add(new GeoPoint(target.lat,target.lon));
        float[] d=new float[1];Location.distanceBetween(origin.getLatitude(),origin.getLongitude(),target.lat,target.lon,d);
        return new Result(direct,d[0],0,false);
    }

    /** Find the closest remaining approach point without ever moving the route backwards. */
    static int advanceIndex(List<GeoPoint> points,int from,Location fix){
        if(points==null||points.isEmpty()||fix==null)return Math.max(0,from);
        int start=Math.max(0,Math.min(from,points.size()-1));
        int end=Math.min(points.size()-1,start+180);
        int best=start;float bestDistance=Float.MAX_VALUE;
        for(int i=start;i<=end;i++){
            GeoPoint p=points.get(i);float[] d=new float[1];
            Location.distanceBetween(fix.getLatitude(),fix.getLongitude(),p.getLatitude(),p.getLongitude(),d);
            if(d[0]<bestDistance){bestDistance=d[0];best=i;}
        }
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
