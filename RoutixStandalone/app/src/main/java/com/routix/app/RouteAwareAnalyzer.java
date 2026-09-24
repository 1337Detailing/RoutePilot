package com.routix.app;

import android.location.Location;
import java.util.*;

/**
 * Online road-context inference. Consumes nearby road segments (from OSM/offline map data)
 * and turns noisy fixes into stable street observations without forcing off-road manoeuvres.
 */
final class RouteAwareAnalyzer {
    static final class Road {final String id,name;final double aLat,aLon,bLat,bLon;Road(String i,String n,double a,double o,double b,double p){id=i;name=n==null||n.trim().isEmpty()?"Voie sans nom":n;aLat=a;aLon=o;bLat=b;bLon=p;}}
    static final class Observation {final String roadId,street;final double confidence,distanceM;final boolean snapped,changed;final Location location;Observation(String i,String s,double c,double d,boolean snap,boolean ch,Location l){roadId=i;street=s;confidence=c;distanceM=d;snapped=snap;changed=ch;location=l;}}
    private String currentId,currentStreet;private int support;private double currentScore=999;

    Observation update(Location fix,List<Road> roads){
        if(fix==null||roads==null||roads.isEmpty())return raw(fix);
        Candidate best=null,current=null;for(Road r:roads){Candidate c=score(fix,r);if(c.distance>45)continue;if(best==null||c.score<best.score)best=c;if(r.id.equals(currentId))current=c;}
        if(best==null){if(++support>3){currentId=currentStreet=null;currentScore=999;}return raw(fix);}
        if(current!=null&&!best.road.id.equals(currentId)&&current.distance<32&&best.score+6>=current.score)best=current;
        boolean changed=currentId!=null&&!currentId.equals(best.road.id);if(currentId==null||!currentId.equals(best.road.id)){if(changed&&best.score>18&&support<2){support++;return new Observation(currentId,currentStreet,confidence(currentScore),current==null?best.distance:current.distance,false,false,new Location(fix));}currentId=best.road.id;currentStreet=best.road.name;currentScore=best.score;support=0;}else{currentScore=.65*currentScore+.35*best.score;support=0;}
        double conf=confidence(best.score),snapLimit=Math.max(5,Math.min(14,fix.getAccuracy()*.65));boolean snap=best.distance<=snapLimit&&conf>=.62;
        Location out=new Location(fix);if(snap){out.setLatitude(best.lat);out.setLongitude(best.lon);}
        return new Observation(currentId,currentStreet,conf,best.distance,snap,changed,out);
    }
    void reset(){currentId=currentStreet=null;support=0;currentScore=999;}
    private Observation raw(Location l){return new Observation(null,null,0,Double.POSITIVE_INFINITY,false,false,l==null?null:new Location(l));}
    private static double confidence(double score){return Math.max(0,Math.min(1,1-score/42.0));}
    private static final class Candidate {Road road;double score,distance,lat,lon;Candidate(Road r,double s,double d,double a,double o){road=r;score=s;distance=d;lat=a;lon=o;}}
    private static Candidate score(Location f,Road r){
        double lat0=Math.toRadians(f.getLatitude()),kLat=111320d,kLon=111320d*Math.cos(lat0);
        double ax=(r.aLon-f.getLongitude())*kLon,ay=(r.aLat-f.getLatitude())*kLat,bx=(r.bLon-f.getLongitude())*kLon,by=(r.bLat-f.getLatitude())*kLat,dx=bx-ax,dy=by-ay;
        double den=dx*dx+dy*dy,t=den==0?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/den)),x=ax+t*dx,y=ay+t*dy,dist=Math.hypot(x,y);
        double headingPenalty=0;if(f.hasBearing()&&f.hasSpeed()&&f.getSpeed()>.8f&&den>1){double roadBearing=(Math.toDegrees(Math.atan2(dx,dy))+360)%360,d=Math.abs(((f.getBearing()-roadBearing+540)%360)-180);d=Math.min(d,180-d);headingPenalty=Math.min(16,d/90*16);}
        double score=dist+headingPenalty+Math.max(0,f.getAccuracy()-12)*.12;double slat=f.getLatitude()+y/kLat,slon=f.getLongitude()+x/kLon;return new Candidate(r,score,dist,slat,slon);
    }
}
