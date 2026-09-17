package com.routepilot.app;

import android.location.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class GuidanceEngine {
    static final class Point {
        final double lat, lon;
        Point(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }
    static final class Event {
        final String type, label;
        final double lat, lon;
        Event(String type, String label, double lat, double lon) {
            this.type = type; this.label = label; this.lat = lat; this.lon = lon;
        }
    }
    static final class State {
        final int nearestIndex, targetIndex, progressPercent;
        final float distanceToTraceM, remainingM, distanceToNextEventM;
        final Event nextEvent;
        final boolean offRoute, finished;
        State(int nearestIndex, int targetIndex, int progressPercent, float distanceToTraceM,
              float remainingM, float distanceToNextEventM, Event nextEvent, boolean offRoute, boolean finished) {
            this.nearestIndex=nearestIndex; this.targetIndex=targetIndex; this.progressPercent=progressPercent;
            this.distanceToTraceM=distanceToTraceM; this.remainingM=remainingM;
            this.distanceToNextEventM=distanceToNextEventM; this.nextEvent=nextEvent;
            this.offRoute=offRoute; this.finished=finished;
        }
    }

    private final List<Point> points;
    private final List<Event> events;
    private final float[] cumulative;
    private int progressIndex;

    GuidanceEngine(List<Point> points, List<Event> events) {
        this.points = points == null ? Collections.emptyList() : new ArrayList<>(points);
        this.events = events == null ? Collections.emptyList() : new ArrayList<>(events);
        cumulative = new float[this.points.size()];
        for (int i=1;i<this.points.size();i++) cumulative[i]=cumulative[i-1]+distance(this.points.get(i-1),this.points.get(i));
    }

    boolean isUsable(){ return points.size()>=2; }
    float totalDistanceM(){ return cumulative.length==0?0:cumulative[cumulative.length-1]; }

    State update(double lat,double lon){
        if(!isUsable()) return new State(0,0,0,Float.MAX_VALUE,0,Float.MAX_VALUE,null,true,false);
        int start=Math.max(0,progressIndex-20), end=Math.min(points.size()-1,Math.max(progressIndex+250,250));
        int nearest=start; float nearestD=Float.MAX_VALUE;
        for(int i=start;i<=end;i++){ Point p=points.get(i); float d=distance(lat,lon,p.lat,p.lon); if(d<nearestD){nearestD=d;nearest=i;} }
        if(nearest>=progressIndex-8) progressIndex=Math.max(progressIndex,nearest);
        nearest=progressIndex;
        int target=nearest; float ahead=0;
        while(target<points.size()-1 && ahead<28){ ahead+=distance(points.get(target),points.get(target+1)); target++; }
        float remaining=Math.max(0,totalDistanceM()-cumulative[nearest]);
        int percent=totalDistanceM()<=1?0:Math.min(100,Math.round(cumulative[nearest]*100/totalDistanceM()));
        Event next=null; float eventD=Float.MAX_VALUE;
        for(Event e:events){ int ei=nearestIndex(e.lat,e.lon,nearest); if(ei+3<nearest)continue; float along=Math.max(0,cumulative[ei]-cumulative[nearest]); if(along<eventD){eventD=along;next=e;} }
        return new State(nearest,target,percent,nearestD,remaining,eventD,next,nearestD>45,nearest>=points.size()-2||remaining<12);
    }

    Point targetPoint(State s){ return points.get(Math.max(0,Math.min(points.size()-1,s.targetIndex))); }
    private int nearestIndex(double lat,double lon,int start){ int best=start; float bd=Float.MAX_VALUE; for(int i=start;i<points.size();i++){Point p=points.get(i);float d=distance(lat,lon,p.lat,p.lon);if(d<bd){bd=d;best=i;}}return best; }
    private static float distance(Point a,Point b){return distance(a.lat,a.lon,b.lat,b.lon);}
    private static float distance(double a,double b,double c,double d){float[] o=new float[1];Location.distanceBetween(a,b,c,d,o);return o[0];}
}
