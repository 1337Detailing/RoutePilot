package com.routix.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Road-aware trace enhancer shared by imported and recorded routes.
 * Matching is performed in ordered overlapping chunks so loops and repeated
 * streets keep their original traversal order. Any weak/partial result falls
 * back to the cleaned source trace.
 */
final class RouteMatcher {
    static final class Result {
        final List<RouteStore.Point> points;
        final List<RouteStore.Event> events;
        final boolean matched;
        final double confidence;
        Result(List<RouteStore.Point> p,List<RouteStore.Event> e,boolean m,double c){points=p;events=e;matched=m;confidence=c;}
    }

    private static final int CHUNK = 82;
    private static final int OVERLAP = 2;
    private static final int MAX_CHUNKS = 10;

    static Result matchBlocking(List<RouteStore.Point> source,List<RouteStore.Event> originalEvents){
        if(source==null||source.size()<3)return new Result(source,safeEvents(originalEvents),false,0);
        ExecutorService ex=Executors.newSingleThreadExecutor();
        try{
            Future<Result> f=ex.submit((Callable<Result>)()->match(source,originalEvents));
            return f.get(22,TimeUnit.SECONDS);
        }catch(Exception ignored){return new Result(source,safeEvents(originalEvents),false,0);}
        finally{ex.shutdownNow();}
    }

    private static Result match(List<RouteStore.Point> source,List<RouteStore.Event> originalEvents)throws Exception{
        int chunks=(int)Math.ceil((source.size()-1.0)/(CHUNK-OVERLAP-1.0));
        if(chunks>MAX_CHUNKS)return new Result(source,safeEvents(originalEvents),false,0);

        List<RouteStore.Point> out=new ArrayList<>();
        List<RouteStore.Event> turns=new ArrayList<>();
        double confidenceSum=0, weightSum=0;
        int start=0;
        while(start<source.size()-1){
            int end=Math.min(source.size(),start+CHUNK);
            List<RouteStore.Point> piece=source.subList(start,end);
            ChunkResult c=matchChunk(piece);
            if(!c.ok)return new Result(source,safeEvents(originalEvents),false,c.confidence);
            double raw=length(piece),matched=length(c.points);
            double ratio=raw>2?matched/raw:1;
            if(ratio<0.68||ratio>1.42)return new Result(source,safeEvents(originalEvents),false,c.confidence);
            appendDistinct(out,c.points);
            turns.addAll(c.events);
            double weight=Math.max(1,raw);
            confidenceSum+=c.confidence*weight;weightSum+=weight;
            if(end==source.size())break;
            start=end-OVERLAP;
        }

        double confidence=weightSum==0?0:confidenceSum/weightSum;
        if(out.size()<2||confidence<0.50)return new Result(source,safeEvents(originalEvents),false,confidence);

        retime(out,source);
        List<RouteStore.Event> events=new ArrayList<>(safeEvents(originalEvents));
        dedupeTurns(events,turns);
        return new Result(out,events,true,confidence);
    }

    private static final class ChunkResult {
        final List<RouteStore.Point> points=new ArrayList<>();
        final List<RouteStore.Event> events=new ArrayList<>();
        boolean ok; double confidence;
    }

    private static ChunkResult matchChunk(List<RouteStore.Point> source)throws Exception{
        ChunkResult result=new ChunkResult();
        StringBuilder coords=new StringBuilder();
        for(RouteStore.Point p:source){if(coords.length()>0)coords.append(';');coords.append(p.lon).append(',').append(p.lat);}
        String u="https://router.project-osrm.org/match/v1/driving/"+coords+"?overview=full&geometries=geojson&steps=true&annotations=false&tidy=true&gaps=split";
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(3500);c.setReadTimeout(6000);c.setRequestProperty("User-Agent","Routix/2.0 Android");
        try{
            if(c.getResponseCode()!=200)return result;
            StringBuilder body=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()))){String line;while((line=r.readLine())!=null)body.append(line);}
            JSONObject root=new JSONObject(body.toString());
            if(!"Ok".equals(root.optString("code")))return result;
            JSONArray ms=root.optJSONArray("matchings");if(ms==null||ms.length()==0)return result;
            double conf=0;int count=0;
            for(int mi=0;mi<ms.length();mi++){
                JSONObject m=ms.getJSONObject(mi);conf+=m.optDouble("confidence",0);count++;
                JSONArray cs=m.getJSONObject("geometry").getJSONArray("coordinates");
                for(int i=0;i<cs.length();i++){
                    JSONArray q=cs.getJSONArray(i);
                    RouteStore.Point p=new RouteStore.Point(q.getDouble(1),q.getDouble(0),0,0);
                    if(result.points.isEmpty()||RouteNormalizer.distanceM(result.points.get(result.points.size()-1),p)>.75)result.points.add(p);
                }
                JSONArray legs=m.optJSONArray("legs");
                if(legs!=null)for(int li=0;li<legs.length();li++){
                    JSONArray steps=legs.getJSONObject(li).optJSONArray("steps");if(steps==null)continue;
                    String prev="";
                    for(int si=0;si<steps.length();si++){
                        JSONObject s=steps.getJSONObject(si),man=s.optJSONObject("maneuver");if(man==null)continue;
                        String name=s.optString("name","").trim(),type=man.optString("type",""),modifier=man.optString("modifier","");
                        JSONArray loc=man.optJSONArray("location");if(loc==null||loc.length()<2)continue;
                        boolean useful=(!name.isEmpty()&&!name.equals(prev))||"turn".equals(type)||"roundabout".equals(type)||"rotary".equals(type)||"uturn".equals(modifier);
                        if(useful&&si>0)result.events.add(new RouteStore.Event("TURN",instruction(type,modifier,name),loc.getDouble(1),loc.getDouble(0),0,0));
                        if(!name.isEmpty())prev=name;
                    }
                }
            }
            result.confidence=count==0?0:conf/count;
            result.ok=result.points.size()>=2&&result.confidence>=0.42;
            return result;
        }finally{c.disconnect();}
    }

    private static void appendDistinct(List<RouteStore.Point> out,List<RouteStore.Point> add){
        for(RouteStore.Point p:add)if(out.isEmpty()||RouteNormalizer.distanceM(out.get(out.size()-1),p)>1.0)out.add(p);
    }

    private static void dedupeTurns(List<RouteStore.Event> base,List<RouteStore.Event> generated){
        Set<String> seen=new HashSet<>();
        for(RouteStore.Event e:base)seen.add(key(e));
        for(RouteStore.Event e:generated){
            String k=key(e);
            if(seen.add(k))base.add(e);
        }
    }

    private static String key(RouteStore.Event e){
        return e.type+"|"+e.label+"|"+Math.round(e.lat*10000)+"|"+Math.round(e.lon*10000);
    }

    private static List<RouteStore.Event> safeEvents(List<RouteStore.Event> e){return e==null?new ArrayList<>():new ArrayList<>(e);}

    private static void retime(List<RouteStore.Point> matched,List<RouteStore.Point> source){
        if(matched.isEmpty()||source==null||source.isEmpty())return;
        long first=source.get(0).time,last=source.get(source.size()-1).time;
        float firstAcc=source.get(0).accuracy,lastAcc=source.get(source.size()-1).accuracy;
        double total=length(matched),along=0;
        for(int i=0;i<matched.size();i++){
            if(i>0)along+=RouteNormalizer.distanceM(matched.get(i-1),matched.get(i));
            double t=total<=0?0:along/total;
            long time=first>0&&last>=first?first+Math.round((last-first)*t):0;
            float acc=(float)(firstAcc+(lastAcc-firstAcc)*t);
            RouteStore.Point p=matched.get(i);
            matched.set(i,new RouteStore.Point(p.lat,p.lon,time,acc));
        }
    }

    private static String instruction(String type,String mod,String road){
        String dir="";
        if(mod.contains("left"))dir=" à gauche";
        else if(mod.contains("right"))dir=" à droite";
        else if(mod.contains("straight"))dir=" tout droit";
        if("roundabout".equals(type)||"rotary".equals(type))return "Prendre le rond-point"+(road.isEmpty()?"":" vers "+road);
        if("uturn".equals(mod))return "Faire demi-tour";
        if("merge".equals(type))return "S’insérer"+(road.isEmpty()?"":" sur "+road);
        return "Tourner"+dir+(road.isEmpty()?"":" • "+road);
    }
    private static double length(List<RouteStore.Point> p){double d=0;for(int i=1;i<p.size();i++)d+=RouteNormalizer.distanceM(p.get(i-1),p.get(i));return d;}
}
