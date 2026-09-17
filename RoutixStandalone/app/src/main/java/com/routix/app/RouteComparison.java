package com.routix.app;

import java.util.*;

/** Approximate, density-independent comparison. Never changes either route. */
final class RouteComparison {
    static final int SAME=0, ADDED=1, REMOVED=2, REVERSED=3;
    static final class Segment {
        final RouteStore.Point a,b; final double along; int kind;
        Segment(RouteStore.Point a,RouteStore.Point b,double along){this.a=a;this.b=b;this.along=along;}
    }
    static final class Result {
        final List<Segment> before,after; double addedM,removedM,reversedM; boolean orderChanged;
        Result(List<Segment> a,List<Segment> b){before=a;after=b;}
    }
    static double distance(RouteStore.Point a,RouteStore.Point b){
        double dy=Math.toRadians(b.lat-a.lat),dx=Math.toRadians(b.lon-a.lon);
        double h=Math.pow(Math.sin(dy/2),2)+Math.cos(Math.toRadians(a.lat))*Math.cos(Math.toRadians(b.lat))*Math.pow(Math.sin(dx/2),2);
        return 12742000*Math.asin(Math.min(1,Math.sqrt(h)));
    }
    static List<Segment> segments(List<RouteStore.Point> points){
        List<Segment> out=new ArrayList<>();double along=0;
        for(int i=1;i<points.size();i++){
            RouteStore.Point a=points.get(i-1),b=points.get(i);double len=distance(a,b);
            int n=Math.max(1,(int)Math.ceil(len/20));
            if(out.size()+n>100000)throw new IllegalArgumentException("Tournée trop longue pour la comparaison");
            for(int j=0;j<n;j++)out.add(new Segment(interpolate(a,b,j/(double)n),interpolate(a,b,(j+1d)/n),along+len*j/n));
            along+=len;
        }return out;
    }
    static RouteStore.Point interpolate(RouteStore.Point a,RouteStore.Point b,double t){return new RouteStore.Point(a.lat+(b.lat-a.lat)*t,a.lon+(b.lon-a.lon)*t,0,0);}
    private static final class Grid {
        final Map<String,List<Segment>> cells=new HashMap<>();final double kx;
        Grid(List<Segment> segments,double lat){kx=111320*Math.cos(Math.toRadians(lat));for(Segment s:segments){RouteStore.Point p=interpolate(s.a,s.b,.5);cells.computeIfAbsent(key(p.lon*kx,p.lat*111320),k->new ArrayList<>()).add(s);}}
        String key(double x,double y){return (int)Math.floor(x/50)+":"+(int)Math.floor(y/50);}
        Segment nearest(Segment s){RouteStore.Point p=interpolate(s.a,s.b,.5);double x=p.lon*kx,y=p.lat*111320,best=30;Segment match=null;
            for(int ix=-1;ix<=1;ix++)for(int iy=-1;iy<=1;iy++)for(Segment c:cells.getOrDefault(key(x+ix*50,y+iy*50),Collections.emptyList())){
                double ax=c.a.lon*kx-x,ay=c.a.lat*111320-y,vx=(c.b.lon-c.a.lon)*kx,vy=(c.b.lat-c.a.lat)*111320;
                double t=Math.max(0,Math.min(1,-(ax*vx+ay*vy)/Math.max(.0001,vx*vx+vy*vy))),d=Math.hypot(ax+t*vx,ay+t*vy);
                if(d<best){best=d;match=c;}
            }return match;
        }
    }
    static Result compare(List<RouteStore.Point> a,List<RouteStore.Point> b){
        if(a.size()<2||b.size()<2)throw new IllegalArgumentException("Deux tracés d’au moins deux points sont nécessaires");
        Result r=new Result(segments(a),segments(b));Grid ga=new Grid(r.before,a.get(0).lat),gb=new Grid(r.after,a.get(0).lat);
        for(Segment s:r.before)if(gb.nearest(s)==null){s.kind=REMOVED;r.removedM+=distance(s.a,s.b);}
        double last=-1;
        for(Segment s:r.after){Segment m=ga.nearest(s);if(m==null){s.kind=ADDED;r.addedM+=distance(s.a,s.b);continue;}
            double scale=Math.cos(Math.toRadians(s.a.lat));double dot=(s.b.lat-s.a.lat)*(m.b.lat-m.a.lat)+(s.b.lon-s.a.lon)*(m.b.lon-m.a.lon)*scale*scale;
            if(dot<0){s.kind=REVERSED;r.reversedM+=distance(s.a,s.b);}else{if(last>m.along+100)r.orderChanged=true;last=m.along;}
        }return r;
    }
}
