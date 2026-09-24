package com.routix.app;
import android.content.Context;
import org.json.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;

/** Durable knowledge learned during a recording: ordered street passages and visit metrics. */
final class StreetPassageStore {
 static final class Passage {final String id,name;long entered,exited;double meters;int fixes;double confidence;double startLat,startLon,endLat,endLon;int direction;Passage(String i,String n,long t,double c){id=i;name=n;entered=exited=t;confidence=c;}}
 private final File file;private final List<Passage> passages=new ArrayList<>();private Passage active;
 StreetPassageStore(Context c){file=new File(c.getFilesDir(),"street_passages.json");}
 void resetSession(){passages.clear();active=null;}
 void observe(RouteAwareAnalyzer.Observation o,long time,double delta){if(o==null||o.roadId==null||o.confidence<.48||o.location==null)return;if(active==null||!active.id.equals(o.roadId)){active=new Passage(o.roadId,o.street,time,o.confidence);active.startLat=o.location.getLatitude();active.startLon=o.location.getLongitude();passages.add(active);}active.exited=time;active.meters+=Math.max(0,delta);active.fixes++;active.confidence=(active.confidence*(active.fixes-1)+o.confidence)/active.fixes;active.endLat=o.location.getLatitude();active.endLon=o.location.getLongitude();if(active.meters>12){float[] d=new float[1];android.location.Location.distanceBetween(active.startLat,active.startLon,active.endLat,active.endLon,d);if(d[0]>8){double east=(active.endLon-active.startLon)*Math.cos(Math.toRadians(active.startLat)),north=active.endLat-active.startLat;active.direction=(int)Math.round((Math.toDegrees(Math.atan2(east,north))+360)%360);}}}
 List<Passage> snapshot(){return new ArrayList<>(passages);}
 int uniqueStreetCount(){Set<String>s=new HashSet<>();for(Passage p:passages)s.add(p.name);return s.size();}
 int repeatedPassageCount(){Map<String,Integer> m=new HashMap<>();for(Passage p:passages)m.put(p.name,m.getOrDefault(p.name,0)+1);int n=0;for(int v:m.values())if(v>1)n+=v-1;return n;}
 String currentStreet(){return active==null?null:active.name;}
 boolean alreadyVisitedCurrentStreet(){if(active==null)return false;int n=0;for(Passage p:passages)if(p.name.equals(active.name))n++;return n>1;}
 void save(String routeName){try{JSONArray a=new JSONArray();for(Passage p:passages){JSONObject j=new JSONObject();j.put("roadId",p.id);j.put("street",p.name);j.put("entered",p.entered);j.put("exited",p.exited);j.put("meters",Math.round(p.meters));j.put("confidence",Math.round(p.confidence*100));j.put("direction",p.direction);j.put("fixes",p.fixes);a.put(j);}JSONObject root=new JSONObject();root.put("route",routeName);root.put("updated",System.currentTimeMillis());root.put("passages",a);JSONObject stats=new JSONObject();Map<String,Integer> visits=new LinkedHashMap<>();Map<String,Double> meters=new LinkedHashMap<>();for(Passage p:passages){visits.put(p.name,visits.getOrDefault(p.name,0)+1);meters.put(p.name,meters.getOrDefault(p.name,0d)+p.meters);}stats.put("uniqueStreets",visits.size());stats.put("passageCount",passages.size());int repeats=0;for(int v:visits.values())if(v>1)repeats+=v-1;stats.put("repeatedPassages",repeats);JSONArray streets=new JSONArray();for(String n:visits.keySet()){JSONObject x=new JSONObject();x.put("street",n);x.put("visits",visits.get(n));x.put("meters",Math.round(meters.get(n)));streets.put(x);}stats.put("streets",streets);root.put("stats",stats);RouteArchive.atomic(file,root.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){DiagnosticLog.error("street passage save",e);}}
}
