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

    private static final class SegmentMatch {
        final int index;
        final float fraction, distanceM, alongRouteM;
        SegmentMatch(int index,float fraction,float distanceM,float alongRouteM) {
            this.index=index;this.fraction=fraction;this.distanceM=distanceM;this.alongRouteM=alongRouteM;
        }
    }

    private final List<Point> points;
    private final List<Event> events;
    private final float[] eventAlongRouteM;
    private final float[] cumulative;
    private int progressIndex;

    GuidanceEngine(List<Point> points,List<Event> events) {
        this.points=points==null?Collections.emptyList():new ArrayList<>(points);
        this.events=events==null?Collections.emptyList():new ArrayList<>(events);
        cumulative=new float[this.points.size()];
        for(int i=1;i<this.points.size();i++) cumulative[i]=cumulative[i-1]+distance(this.points.get(i-1),this.points.get(i));
        eventAlongRouteM=new float[this.events.size()];
        for(int i=0;i<this.events.size();i++) {
            Event e=this.events.get(i);
            eventAlongRouteM[i]=nearestSegmentOnWholeRoute(e.lat,e.lon).alongRouteM;
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
        int end=Math.min(points.size()-2,Math.max(progressIndex+350,350));
        SegmentMatch match=nearestSegment(lat,lon,start,end);

        // Match against the line between recorded fixes, not only the fixes themselves.
        // This prevents sparse but valid recordings from looking off-route halfway between points.
        int candidate=match.fraction>=0.5f?match.index+1:match.index;
        if(candidate>=progressIndex&&match.distanceM<=60f) progressIndex=candidate;
        float distanceToTrace=match.distanceM;
        float alongNow=Math.max(cumulative[progressIndex],match.alongRouteM);

        int target=progressIndex;float ahead=0;
        while(target<points.size()-1&&ahead<35){ahead+=distance(points.get(target),points.get(target+1));target++;}
        float remaining=Math.max(0,totalDistanceM()-alongNow);
        int percent=totalDistanceM()<=1?0:Math.min(100,Math.round(alongNow*100/totalDistanceM()));

        Event next=null;float eventD=Float.MAX_VALUE;
        for(int i=0;i<events.size();i++) {
            float eventAlong=eventAlongRouteM[i];
            // Keep a tiny tolerance so an event remains visible while the truck is passing it,
            // then discard it once it is clearly behind the current trace position.
            if(eventAlong+8f<alongNow)continue;
            float along=Math.max(0,eventAlong-alongNow);
            if(along<eventD){eventD=along;next=events.get(i);}
        }
        boolean off=distanceToTrace>45;
        boolean done=progressIndex>=points.size()-2||remaining<12;
        return new State(progressIndex,target,percent,distanceToTrace,remaining,eventD,next,off,done);
    }

    Point targetPoint(State s){return points.get(Math.max(0,Math.min(points.size()-1,s.targetIndex)));}

    private SegmentMatch nearestSegment(double lat,double lon,int start,int end) {
        SegmentMatch best=null;
        for(int i=start;i<=end;i++) {
            Point a=points.get(i),b=points.get(i+1);
            double lat0=Math.toRadians(lat);
            double mx=111320.0*Math.cos(lat0),my=110540.0;
            double ax=(a.lon-lon)*mx,ay=(a.lat-lat)*my;
            double bx=(b.lon-lon)*mx,by=(b.lat-lat)*my;
            double vx=bx-ax,vy=by-ay;
            double vv=vx*vx+vy*vy;
            float t=vv<=0.0001?0f:(float)Math.max(0,Math.min(1,-(ax*vx+ay*vy)/vv));
            double px=ax+t*vx,py=ay+t*vy;
            float d=(float)Math.sqrt(px*px+py*py);
            float segmentLength=cumulative[i+1]-cumulative[i];
            float along=cumulative[i]+segmentLength*t;
            if(best==null||d<best.distanceM)best=new SegmentMatch(i,t,d,along);
        }
        return best==null?new SegmentMatch(progressIndex,0,Float.MAX_VALUE,cumulative[progressIndex]):best;
    }

    private SegmentMatch nearestSegmentOnWholeRoute(double lat,double lon) {
        if(points.size()<2)return new SegmentMatch(0,0,Float.MAX_VALUE,0);
        return nearestSegment(lat,lon,0,points.size()-2);
    }

    private static float distance(Point a,Point b){return distance(a.lat,a.lon,b.lat,b.lon);}
    private static float distance(double a,double b,double c,double d){float[] o=new float[1];Location.distanceBetween(a,b,c,d,o);return o[0];}
}
