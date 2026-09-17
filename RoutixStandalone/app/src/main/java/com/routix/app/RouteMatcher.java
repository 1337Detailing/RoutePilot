package com.routix.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Road-aware import enhancer. Network failure or low confidence always falls back to the cleaned GPX. */
final class RouteMatcher {
    static final class Result {
        final List<RouteStore.Point> points;
        final List<RouteStore.Event> events;
        final boolean matched;
        final double confidence;
        Result(List<RouteStore.Point> p,List<RouteStore.Event> e,boolean m,double c){points=p;events=e;matched=m;confidence=c;}
    }

    static Result matchBlocking(List<RouteStore.Point> source,List<RouteStore.Event> originalEvents){
        if(source==null||source.size()<3)return new Result(source,originalEvents,false,0);
        ExecutorService ex=Executors.newSingleThreadExecutor();
        try{
            Future<Result> f=ex.submit((Callable<Result>)()->match(source,originalEvents));
            return f.get(10,TimeUnit.SECONDS);
        }catch(Exception ignored){return new Result(source,originalEvents,false,0);}
        finally{ex.shutdownNow();}
    }

    private static Result match(List<RouteStore.Point> source,List<RouteStore.Event> originalEvents)throws Exception{
        List<RouteStore.Point> sampled=sample(source,95);
        StringBuilder coords=new StringBuilder();
        for(RouteStore.Point p:sampled){if(coords.length()>0)coords.append(';');coords.append(p.lon).append(',').append(p.lat);}
        String u="https://router.project-osrm.org/match/v1/driving/"+coords+"?overview=full&geometries=geojson&steps=true&annotations=false&tidy=true&gaps=split";
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(3500);c.setReadTimeout(5500);c.setRequestProperty("User-Agent","Routix/1.5 Android");
        if(c.getResponseCode()!=200)throw new IllegalStateException();
        StringBuilder body=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()))){String line;while((line=r.readLine())!=null)body.append(line);}
        JSONObject root=new JSONObject(body.toString());
        if(!"Ok".equals(root.optString("code")))throw new IllegalStateException();
        JSONArray ms=root.optJSONArray("matchings");if(ms==null||ms.length()==0)throw new IllegalStateException();
        List<RouteStore.Point> out=new ArrayList<>();List<RouteStore.Event> turns=new ArrayList<>();double conf=0;int mc=0;
        for(int mi=0;mi<ms.length();mi++){
            JSONObject m=ms.getJSONObject(mi);conf+=m.optDouble("confidence",0);mc++;
            JSONArray cs=m.getJSONObject("geometry").getJSONArray("coordinates");
            for(int i=0;i<cs.length();i++){JSONArray q=cs.getJSONArray(i);RouteStore.Point p=new RouteStore.Point(q.getDouble(1),q.getDouble(0),0,0);if(out.isEmpty()||dist(out.get(out.size()-1),p)>0.8)out.add(p);}
            JSONArray legs=m.optJSONArray("legs");if(legs!=null)for(int li=0;li<legs.length();li++){
                JSONArray steps=legs.getJSONObject(li).optJSONArray("steps");if(steps==null)continue;
                String prev="";
                for(int si=0;si<steps.length();si++){
                    JSONObject s=steps.getJSONObject(si),man=s.optJSONObject("maneuver");if(man==null)continue;
                    String name=s.optString("name","").trim(),type=man.optString("type",""),modifier=man.optString("modifier","");
                    JSONArray loc=man.optJSONArray("location");if(loc==null||loc.length()<2)continue;
                    boolean useful=(!name.isEmpty()&&!name.equals(prev))||"turn".equals(type)||"roundabout".equals(type)||"rotary".equals(type)||"uturn".equals(modifier);
                    if(useful&&si>0){String label=instruction(type,modifier,name);turns.add(new RouteStore.Event("TURN",label,loc.getDouble(1),loc.getDouble(0),0,0));}
                    if(!name.isEmpty())prev=name;
                }
            }
        }
        conf=mc==0?0:conf/mc;
        if(out.size()<2||conf<0.45)return new Result(source,originalEvents,false,conf);
        double rawLen=length(source),matchLen=length(out),ratio=rawLen>1?matchLen/rawLen:1;
        if(ratio<0.62||ratio>1.55)return new Result(source,originalEvents,false,conf);
        // Keep explicit Routix work events and add generated road instructions.
        List<RouteStore.Event> events=new ArrayList<>();if(originalEvents!=null)events.addAll(originalEvents);events.addAll(turns);
        return new Result(out,events,true,conf);
    }

    private static List<RouteStore.Point> sample(List<RouteStore.Point> p,int max){if(p.size()<=max)return p;List<RouteStore.Point> o=new ArrayList<>();for(int i=0;i<max;i++){int ix=(int)Math.round(i*(p.size()-1.0)/(max-1.0));o.add(p.get(ix));}return o;}
    private static String instruction(String type,String mod,String road){String dir="";if(mod.contains("left"))dir=" à gauche";else if(mod.contains("right"))dir=" à droite";else if(mod.contains("straight"))dir=" tout droit";if("roundabout".equals(type)||"rotary".equals(type))return "Prendre le rond-point"+(road.isEmpty()?"":" vers "+road);if("uturn".equals(mod))return "Faire demi-tour";return "Tourner"+dir+(road.isEmpty()?"":" • "+road);}
    private static double length(List<RouteStore.Point> p){double d=0;for(int i=1;i<p.size();i++)d+=dist(p.get(i-1),p.get(i));return d;}
    private static double dist(RouteStore.Point a,RouteStore.Point b){double r=6371000,p1=Math.toRadians(a.lat),p2=Math.toRadians(b.lat),dp=Math.toRadians(b.lat-a.lat),dl=Math.toRadians(b.lon-a.lon);double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return 2*r*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));}
}
