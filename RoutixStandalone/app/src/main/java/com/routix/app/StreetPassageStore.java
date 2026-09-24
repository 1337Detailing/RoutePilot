package com.routix.app;
import android.content.Context;
import org.json.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;

/** Durable knowledge learned during a recording: ordered street passages and visit metrics. */
final class StreetPassageStore {
 static final class Passage {final String id,name;long entered,exited;double meters;int fixes;double confidence;Passage(String i,String n,long t,double c){id=i;name=n;entered=exited=t;confidence=c;}}
 private final File file;private final List<Passage> passages=new ArrayList<>();private Passage active;
 StreetPassageStore(Context c){file=new File(c.getFilesDir(),"street_passages.json");}
 void resetSession(){passages.clear();active=null;}
 void observe(RouteAwareAnalyzer.Observation o,long time,double delta){if(o==null||o.roadId==null||o.confidence<.48)return;if(active==null||!active.id.equals(o.roadId)){active=new Passage(o.roadId,o.street,time,o.confidence);passages.add(active);}active.exited=time;active.meters+=Math.max(0,delta);active.fixes++;active.confidence=(active.confidence*(active.fixes-1)+o.confidence)/active.fixes;}
 List<Passage> snapshot(){return new ArrayList<>(passages);}
 void save(String routeName){try{JSONArray a=new JSONArray();for(Passage p:passages){JSONObject j=new JSONObject();j.put("roadId",p.id);j.put("street",p.name);j.put("entered",p.entered);j.put("exited",p.exited);j.put("meters",Math.round(p.meters));j.put("confidence",Math.round(p.confidence*100));a.put(j);}JSONObject root=new JSONObject();root.put("route",routeName);root.put("updated",System.currentTimeMillis());root.put("passages",a);RouteArchive.atomic(file,root.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){DiagnosticLog.error("street passage save",e);}}
}
