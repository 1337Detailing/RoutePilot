package com.routix.app;
import java.util.*;
/** Tracks actually driven route segments during replay and identifies holes independently of monotonic progress. */
final class GuidanceCoverage{
 private final boolean[] done;private final float[] length;private final List<GuidanceEngine.Point> points;private int doneCount;
 GuidanceCoverage(List<GuidanceEngine.Point> p){points=p==null?Collections.emptyList():p;done=new boolean[Math.max(0,points.size()-1)];length=new float[done.length];for(int i=0;i<done.length;i++)length[i]=distance(points.get(i),points.get(i+1));}
 void observe(int nearest,float distanceToTrace){if(nearest<0||nearest>=done.length||distanceToTrace>28)return;mark(nearest);if(nearest>0&&distanceToTrace<16)mark(nearest-1);}
 private void mark(int i){if(i>=0&&i<done.length&&!done[i]){done[i]=true;doneCount++;}}
 boolean isDone(int i){return i>=0&&i<done.length&&done[i];}
 int doneSegments(){return doneCount;}int totalSegments(){return done.length;}
 float coveredMeters(){float n=0;for(int i=0;i<done.length;i++)if(done[i])n+=length[i];return n;}
 float totalMeters(){float n=0;for(float x:length)n+=x;return n;}
 int percent(){float t=totalMeters();return t<=0?0:Math.min(100,Math.round(coveredMeters()*100/t));}
 List<Integer> missedBefore(int index){List<Integer> r=new ArrayList<>();for(int i=0;i<Math.min(index,done.length);i++)if(!done[i]&&length[i]>6)r.add(i);return r;}
 int missedCountBefore(int index){return missedBefore(index).size();}
 private static float distance(GuidanceEngine.Point a,GuidanceEngine.Point b){double dlat=Math.toRadians(b.lat-a.lat),dlon=Math.toRadians(b.lon-a.lon),h=Math.pow(Math.sin(dlat/2),2)+Math.cos(Math.toRadians(a.lat))*Math.cos(Math.toRadians(b.lat))*Math.pow(Math.sin(dlon/2),2);return(float)(6371000*2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h)))));}
}