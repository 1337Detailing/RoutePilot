package com.routix.app;
import java.util.*;
/** Deterministic arc-route seed optimizer. Required collection segments are kept; deadhead connectors are minimized greedily. */
final class RouteOptimizer{
 static final class Segment{final String id;final double aLat,aLon,bLat,bLon;Segment(String i,double a,double o,double b,double p){id=i;aLat=a;aLon=o;bLat=b;bLon=p;}Segment reversed(){return new Segment(id,bLat,bLon,aLat,aLon);}}
 static List<Segment> optimize(List<Segment> input,double startLat,double startLon){List<Segment> left=new ArrayList<>(input==null?Collections.emptyList():input),out=new ArrayList<>();double lat=startLat,lon=startLon;while(!left.isEmpty()){int best=-1;boolean reverse=false;double bd=Double.MAX_VALUE;for(int i=0;i<left.size();i++){Segment s=left.get(i);double da=d(lat,lon,s.aLat,s.aLon),db=d(lat,lon,s.bLat,s.bLon);if(da<bd){bd=da;best=i;reverse=false;}if(db<bd){bd=db;best=i;reverse=true;}}Segment s=left.remove(best);if(reverse)s=s.reversed();out.add(s);lat=s.bLat;lon=s.bLon;}return out;}
 static double deadhead(List<Segment>s,double lat,double lon){double n=0;for(Segment x:s){n+=d(lat,lon,x.aLat,x.aLon);lat=x.bLat;lon=x.bLon;}return n;}
 private static double d(double a,double o,double b,double p){double x=Math.toRadians(p-o)*Math.cos(Math.toRadians((a+b)/2)),y=Math.toRadians(b-a);return 6371000*Math.sqrt(x*x+y*y);}
}