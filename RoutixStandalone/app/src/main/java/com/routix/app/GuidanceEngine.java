package com.routix.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Monotonic, segment-based guidance. */
final class GuidanceEngine {
    static final class Point { final double lat,lon; Point(double lat,double lon){this.lat=lat;this.lon=lon;} }
    static final class Event {
        final String type,label;final double lat,lon;final int routeIndex;
        Event(String type,String label,double lat,double lon){this(type,label,lat,lon,-1);}
        Event(String type,String label,double lat,double lon,int routeIndex){this.type=type;this.label=label;this.lat=lat;this.lon=lon;this.routeIndex=routeIndex;}
    }
    static final class State {
        final int nearestIndex,targetIndex,progressPercent; final float distanceToTraceM,remainingM,distanceToNextEventM,alongRouteM;
        final Point position; final Event nextEvent; final boolean offRoute,finished,poorAccuracy;
        State(int index,int target,int percent,float deviation,float remaining,float nextDistance,Event event,boolean off,boolean done,float along,Point position,boolean poor){nearestIndex=index;targetIndex=target;progressPercent=percent;distanceToTraceM=deviation;remainingM=remaining;distanceToNextEventM=nextDistance;nextEvent=event;offRoute=off;finished=done;alongRouteM=along;this.position=position;poorAccuracy=poor;}
    }
    static final class Maneuver { final float distanceM; final String instruction; final int direction; Maneuver(float distanceM,String instruction,int direction){this.distanceM=distanceM;this.instruction=instruction;this.direction=direction;} }
    private static final class Match { final float along,distance; Match(float along,float distance){this.along=along;this.distance=distance;} }

    private static final float MAX_PLAUSIBLE_SPEED_MPS=55f; // 198 km/h: generous for road GPS, rejects teleport fixes.
    private static final float MIN_JUMP_FILTER_DISTANCE_M=80f;
    private final List<Point> points; private final List<Event> events; private final float[] cumulative,eventAlong;
    private float progress; private Point lastFix; private long lastTime; private float offRouteThreshold=45f;

    GuidanceEngine(List<Point> points,List<Event> events){
        this.points=points==null?Collections.emptyList():new ArrayList<>(points); this.events=events==null?Collections.emptyList():new ArrayList<>(events); cumulative=new float[this.points.size()];
        for(int i=1;i<cumulative.length;i++)cumulative[i]=cumulative[i-1]+distance(this.points.get(i-1),this.points.get(i));
        eventAlong=new float[this.events.size()]; for(int i=0;i<eventAlong.length;i++){Event e=this.events.get(i);eventAlong[i]=e.routeIndex>=0&&e.routeIndex<cumulative.length?cumulative[e.routeIndex]:match(e.lat,e.lon,0,totalDistanceM(),0,false,Float.NaN).along;}
    }
    boolean isUsable(){return points.size()>=2&&totalDistanceM()>1;} float totalDistanceM(){return cumulative.length==0?0:cumulative[cumulative.length-1];} int currentIndex(){return segmentAt(progress);} void reset(){progress=0;lastFix=null;lastTime=0;} float progressM(){return progress;}
    void setOffRouteThreshold(float meters){if(Float.isFinite(meters))offRouteThreshold=Math.max(25,Math.min(90,meters));}
    void restoreProgress(float meters){if(Float.isFinite(meters)){progress=Math.max(0,Math.min(totalDistanceM(),meters));lastFix=null;lastTime=0;}}
    void jumpPoints(int delta){if(!points.isEmpty())progress=cumulative[Math.max(0,Math.min(points.size()-1,currentIndex()+delta))];}
    boolean reposition(double lat,double lon){if(!isUsable()||!Double.isFinite(lat)||!Double.isFinite(lon))return false;Match m=match(lat,lon,progress,totalDistanceM(),0,false,Float.NaN);if(m.distance>offRouteThreshold)return false;progress=Math.max(progress,m.along);lastFix=new Point(lat,lon);lastTime=0;return true;}
    State update(double lat,double lon){return update(lat,lon,System.currentTimeMillis(),5);}
    State update(double lat,double lon,long time,float accuracy){
        if(!isUsable())return new State(0,0,0,Float.MAX_VALUE,0,Float.MAX_VALUE,null,true,false,0,new Point(lat,lon),false);
        boolean poor=!Double.isFinite(lat)||!Double.isFinite(lon)||!Float.isFinite(accuracy)||accuracy>35||accuracy<0; boolean stale=lastTime>0&&time<=lastTime; Point fix=new Point(lat,lon);
        float moved=lastFix==null||poor||stale?0:distance(lastFix,fix); long dt=lastTime>0&&time>lastTime?time-lastTime:0; float speed=dt>0?moved/(dt/1000f):Float.NaN;
        boolean implausible=!poor&&!stale&&dt>0&&moved>=MIN_JUMP_FILTER_DISTANCE_M&&Float.isFinite(speed)&&speed>MAX_PLAUSIBLE_SPEED_MPS;
        float bearing=lastFix==null||moved<3?Float.NaN:bearing(lastFix,fix); float advance=Math.max(60,Math.min(360,moved*2.2f+25+(Float.isFinite(speed)?speed*4:0)));
        Match m=(poor||implausible)?new Match(progress,Float.MAX_VALUE):match(lat,lon,Math.max(0,progress-28),Math.min(totalDistanceM(),progress+advance),Math.min(moved,advance),true,bearing); boolean off=m.distance>offRouteThreshold;
        if(!poor&&!stale){ if(!off&&!implausible)progress=Math.max(progress,m.along); lastFix=fix;lastTime=time; }
        float remaining=Math.max(0,totalDistanceM()-progress); int index=segmentAt(progress),target=segmentAt(Math.min(totalDistanceM(),progress+38))+1; Event next=null;float nextDistance=Float.MAX_VALUE;
        for(int i=0;i<events.size();i++){if(eventAlong[i]+8<progress)continue;float d=Math.max(0,eventAlong[i]-progress);if(d<nextDistance){nextDistance=d;next=events.get(i);}}
        boolean done=!poor&&!implausible&&!off&&remaining<6&&distance(fix,points.get(points.size()-1))<20;
        return new State(index,Math.min(points.size()-1,target),Math.min(100,(int)(progress*100/totalDistanceM())),m.distance,remaining,nextDistance,next,off,done,progress,pointAt(progress),poor||stale||implausible);
    }
    Maneuver nextManeuver(State s){if(s==null||points.size()<3)return new Maneuver(Math.max(0,s==null?0:s.remainingM),"Continuez tout droit",0);float from=Math.max(0,s.alongRouteM);int start=Math.max(1,segmentAt(from)+1);for(int i=start;i<points.size()-1;i++){float ahead=cumulative[i]-from;if(ahead<12)continue;if(ahead>850)break;float in=bearing(points.get(i-1),points.get(i)),out=bearing(points.get(i),points.get(i+1));float delta=angleDelta(out,in),abs=Math.abs(delta);if(abs<28)continue;if(abs>150)return new Maneuver(ahead,"Faites demi-tour",2);return new Maneuver(ahead,delta>0?"Tournez à droite":"Tournez à gauche",delta>0?1:-1);}return new Maneuver(Math.min(Math.max(0,s.remainingM),350),"Continuez tout droit",0);}
    Point targetPoint(State s){return pointAt(Math.min(totalDistanceM(),s.alongRouteM+38));}
    Point pointAt(float along){if(points.isEmpty())return new Point(0,0);if(points.size()==1)return points.get(0);int i=segmentAt(along);float len=cumulative[i+1]-cumulative[i];double t=len<=0?0:Math.max(0,Math.min(1,(along-cumulative[i])/len));Point a=points.get(i),b=points.get(i+1);return new Point(a.lat+(b.lat-a.lat)*t,a.lon+(b.lon-a.lon)*t);}
    private int segmentAt(float along){int low=0,high=Math.max(0,points.size()-2);while(low<high){int mid=(low+high+1)/2;if(cumulative[mid]<=along)low=mid;else high=mid-1;}return low;}
    private Match match(double lat,double lon,float from,float to,float moved,boolean continuity,float travelBearing){Match best=new Match(progress,Float.MAX_VALUE);float bestScore=Float.MAX_VALUE;if(points.size()<2)return best;double mx=111320*Math.cos(Math.toRadians(lat)),my=111320;for(int i=segmentAt(from);i<points.size()-1&&cumulative[i]<=to;i++){float len=cumulative[i+1]-cumulative[i];if(len<.01f)continue;Point a=points.get(i),b=points.get(i+1);double ax=(a.lon-lon)*mx,ay=(a.lat-lat)*my,vx=(b.lon-a.lon)*mx,vy=(b.lat-a.lat)*my;double vv=vx*vx+vy*vy;double t=vv==0?0:-(ax*vx+ay*vy)/vv;double lo=Math.max(0,(from-cumulative[i])/len),hi=Math.min(1,(to-cumulative[i])/len);if(lo>hi)continue;t=Math.max(lo,Math.min(hi,t));float along=cumulative[i]+len*(float)t;float d=(float)Math.hypot(ax+t*vx,ay+t*vy);float score=d;if(continuity)score+=.12f*Math.abs(along-progress-moved);if(Float.isFinite(travelBearing)&&moved>4){float segBearing=bearing(a,b);float delta=Math.abs(angleDelta(travelBearing,segBearing));score+=Math.min(18f,delta/10f);}if(score<bestScore-.01f){bestScore=score;best=new Match(along,d);}}return best;}
    private static float bearing(Point a,Point b){double lat1=Math.toRadians(a.lat),lat2=Math.toRadians(b.lat),dl=Math.toRadians(b.lon-a.lon);double y=Math.sin(dl)*Math.cos(lat2);double x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dl);double deg=Math.toDegrees(Math.atan2(y,x));return (float)((deg+360)%360);}
    private static float angleDelta(float a,float b){float d=(a-b)%360;if(d>180)d-=360;if(d<-180)d+=360;return d;}
    private static float distance(Point a,Point b){double dlat=Math.toRadians(b.lat-a.lat),dlon=Math.toRadians(b.lon-a.lon);double h=Math.pow(Math.sin(dlat/2),2)+Math.cos(Math.toRadians(a.lat))*Math.cos(Math.toRadians(b.lat))*Math.pow(Math.sin(dlon/2),2);return (float)(6371000*2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h)))));}
}
