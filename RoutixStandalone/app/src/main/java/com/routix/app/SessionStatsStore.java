package com.routix.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Persistent, bounded history of completed guidance sessions. */
final class SessionStatsStore {
    static final class Entry {
        final long started,ended,durationMs; final double distanceM; final int stops,reverse,twoSides; final String route;
        Entry(long s,long e,long d,double m,int st,int r,int t,String n){started=s;ended=e;durationMs=d;distanceM=m;stops=st;reverse=r;twoSides=t;route=n==null?"":n;}
    }
    private static final String KEY="guidance_session_history_v1";
    private static final int MAX=250;
    private final SharedPreferences prefs;
    SessionStatsStore(SharedPreferences p){prefs=p;}

    synchronized void add(Entry e){
        if(e==null||e.started<=0||e.ended<e.started)return;
        List<Entry> all=entries();
        for(Entry x:all)if(x.started==e.started&&x.route.equals(e.route))return;
        all.add(0,e);if(all.size()>MAX)all=new ArrayList<>(all.subList(0,MAX));
        JSONArray a=new JSONArray();try{for(Entry x:all)a.put(json(x));prefs.edit().putString(KEY,a.toString()).apply();}catch(Exception ignored){}
    }
    synchronized List<Entry> entries(){
        List<Entry> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(KEY,"[]"));for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;long s=j.optLong("s"),e=j.optLong("e"),d=j.optLong("d");double m=j.optDouble("m");if(s>0&&e>=s&&d>=0&&Double.isFinite(m)&&m>=0)out.add(new Entry(s,e,d,m,Math.max(0,j.optInt("st")),Math.max(0,j.optInt("r")),Math.max(0,j.optInt("t")),j.optString("n")));}}catch(Exception ignored){}
        out.sort((a,b)->Long.compare(b.started,a.started));return out;
    }
    synchronized void clear(){prefs.edit().remove(KEY).apply();}
    private static JSONObject json(Entry e)throws Exception{return new JSONObject().put("s",e.started).put("e",e.ended).put("d",e.durationMs).put("m",e.distanceM).put("st",e.stops).put("r",e.reverse).put("t",e.twoSides).put("n",e.route);}
    static long totalDuration(List<Entry> xs){long n=0;for(Entry e:xs)n+=Math.max(0,e.durationMs);return n;}
    static double totalDistance(List<Entry> xs){double n=0;for(Entry e:xs)n+=Math.max(0,e.distanceM);return n;}
}