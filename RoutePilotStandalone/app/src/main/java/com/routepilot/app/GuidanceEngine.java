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
            this.type=type; this.label=label; this.lat=lat; this.lon=lon;
        }
    }

    static final class State {
        final int nearestIndex, targetIndex, progressPercent;
        final float distanceToTraceM, remainingM, distanceToNextEventM;
        final Event nextEvent;
        final boolean offRoute, finished;
        State(int nearestIndex,int targetIndex,int progressPercent,float distanceToTraceM,
              float remainingM,float distanceToNextEventM,Event nextEvent,boolean offRoute,boolean finished) {
            this.nearestIndex=nearestIndex;this.targetIndex=targetIndex;this.progressPercent=progressPercent;
            this.distanceToTraceM=distanceToTraceM;this.remainingM=remainingM;
            this.distanceToNextEventM=distanceToNextEventM;this.nextEvent=nextEvent;
            this.offRoute=offRoute;this.finished=finished;
        }
    }

    private final List<Point> points;
    private final List<Event> events;
    private final int[] eventPointIndexes;
    private final float[] cumulative;
    private int progressIndex;

    GuidanceEngine(List<Point> points,List<Event> events) {
        this.points=points==null?Collections.emptyList():new ArrayList<>(points);
        this.events=events==null?Collections.emptyList():new ArrayList<>(events);
        cumulative=new float[this.points.size()];
        for(int i=1;i<this.points.size();i++) cumulative[i]=cumulative[i-1]+distance(this.points.get(i-1),this.points.get(i));
        eventPointIndexes=new int[this.events.size()];
        for(int i=0;i<this.events.size();i++) {
            Event e=this.events.get(i);
            eventPointIndexes[i]=nearestIndexOnWholeRoute(e.lat,e.lon);
        }
    }

    boolean isUsable(){return points.size()>=2;}
    float totalDistanceM(){return cumulative.length==0?0:cumulative[cumulative.length-1];}
    int currentIndex(){return progressIndex;}
    void reset(){progressIndex=0;}
    void jumpPoints(int delta){if(points.isEmpty())return;progressIndex=Math.max(0,Math.min(points.size()-1,progressIndex+delta));}

    State update(double lat,double lon) {
        if(!isUsable()) return new State(0,0,0,Float.MAX_VALUE,0,Float.MAX_VALUE,null,true,false);
        int start=Math.max(0,progressIndex-25);
        int end=Math.min(points.size()-1,Math.max(progressIndex+350,350));
        int candidate=start;float candidateD=Float.MAX_VALUE;
        for(int i=start;i<=end;i++) {
            Point p=points.get(i);float d=distance(lat,lon,p.lat,p.lon);
            if(d<candidateD){candidateD=d;candidate=i;}
        }
        if(candidate>=progressIndex&&candidateD<=60f) progressIndex=candidate;
        Point progressPoint=points.get(progressIndex);
        float distanceToTrace=distance(lat,lon,progressPoint.lat,progressPoint.lon);

        int target=progressIndex;float ahead=0;
        while(target<points.size()-1&&ahead<35){ahead+=distance(points.get(target),points.get(target+1));target++;}
        float remaining=Math.max(0,totalDistanceM()-cumulative[progressIndex]);
        int percent=totalDistanceM()<=1?0:Math.min(100,Math.round(cumulative[progressIndex]*100/totalDistanceM()));

        Event next=null;float eventD=Float.MAX_VALUE;
        for(int i=0;i<events.size();i++) {
            int eventIndex=eventPointIndexes[i];
            if(eventIndex+4<progressIndex)continue;
            float along=Math.max(0,cumulative[eventIndex]-cumulative[progressIndex]);
            if(along<eventD){eventD=along;next=events.get(i);}
        }
        boolean off=distanceToTrace>45;
        boolean done=progressIndex>=points.size()-2||remaining<12;
        return new State(progressIndex,target,percent,distanceToTrace,remaining,eventD,next,off,done);
    }

    Point targetPoint(State s){return points.get(Math.max(0,Math.min(points.size()-1,s.targetIndex)));}

    private int nearestIndexOnWholeRoute(double lat,double lon) {
        if(points.isEmpty())return 0;
        int best=0;float bd=Float.MAX_VALUE;
        for(int i=0;i<points.size();i++) {
            Point p=points.get(i);float d=distance(lat,lon,p.lat,p.lon);
            if(d<bd){bd=d;best=i;}
        }
        return best;
    }

    private static float distance(Point a,Point b){return distance(a.lat,a.lon,b.lat,b.lon);}
    private static float distance(double a,double b,double c,double d){float[] o=new float[1];Location.distanceBetween(a,b,c,d,o);return o[0];}
}
