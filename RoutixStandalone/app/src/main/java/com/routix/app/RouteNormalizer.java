package com.routix.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative geometry cleanup for imported routes.
 * It never reorders points and never globally merges repeated streets/loops.
 */
final class RouteNormalizer {
    static final class Result {
        final List<RouteStore.Point> points;
        final int inputCount, invalidRemoved, duplicateRemoved, simplifiedRemoved;
        Result(List<RouteStore.Point> points, int inputCount, int invalidRemoved, int duplicateRemoved, int simplifiedRemoved) {
            this.points=points; this.inputCount=inputCount; this.invalidRemoved=invalidRemoved;
            this.duplicateRemoved=duplicateRemoved; this.simplifiedRemoved=simplifiedRemoved;
        }
    }

    private static final double MIN_POINT_SPACING_M = 1.5;
    private static final double SIMPLIFY_TOLERANCE_M = 2.5;

    static Result normalize(List<RouteStore.Point> source) {
        if(source==null || source.isEmpty()) return new Result(Collections.emptyList(),0,0,0,0);
        int invalid=0, dup=0;
        List<RouteStore.Point> valid=new ArrayList<>();
        for(RouteStore.Point p:source) {
            if(!valid(p)){invalid++;continue;}
            if(!valid.isEmpty() && distanceM(valid.get(valid.size()-1),p)<MIN_POINT_SPACING_M){dup++;continue;}
            valid.add(p);
        }
        if(valid.size()<3) return new Result(valid,source.size(),invalid,dup,0);

        boolean[] keep=new boolean[valid.size()];
        keep[0]=true; keep[valid.size()-1]=true;
        simplify(valid,0,valid.size()-1,keep);
        List<RouteStore.Point> out=new ArrayList<>();
        for(int i=0;i<valid.size();i++) if(keep[i]) out.add(valid.get(i));
        return new Result(out,source.size(),invalid,dup,valid.size()-out.size());
    }

    private static boolean valid(RouteStore.Point p){
        return p!=null && Double.isFinite(p.lat) && Double.isFinite(p.lon)
                && p.lat>=-90 && p.lat<=90 && p.lon>=-180 && p.lon<=180;
    }

    private static void simplify(List<RouteStore.Point> p,int first,int last,boolean[] keep){
        if(last<=first+1)return;
        double best=-1; int index=-1;
        for(int i=first+1;i<last;i++){
            double d=segmentDistanceM(p.get(i),p.get(first),p.get(last));
            if(d>best){best=d;index=i;}
        }
        if(best>SIMPLIFY_TOLERANCE_M){
            keep[index]=true;
            simplify(p,first,index,keep);
            simplify(p,index,last,keep);
        }
    }

    private static double distanceM(RouteStore.Point a,RouteStore.Point b){
        double r=6371000.0;
        double p1=Math.toRadians(a.lat),p2=Math.toRadians(b.lat);
        double dp=Math.toRadians(b.lat-a.lat),dl=Math.toRadians(b.lon-a.lon);
        double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 2*r*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
    }

    private static double segmentDistanceM(RouteStore.Point p,RouteStore.Point a,RouteStore.Point b){
        double lat0=Math.toRadians((a.lat+b.lat+p.lat)/3.0);
        double kx=111320.0*Math.cos(lat0), ky=110540.0;
        double ax=a.lon*kx, ay=a.lat*ky, bx=b.lon*kx, by=b.lat*ky, px=p.lon*kx, py=p.lat*ky;
        double dx=bx-ax,dy=by-ay;
        double den=dx*dx+dy*dy;
        if(den<1e-6)return Math.hypot(px-ax,py-ay);
        double t=((px-ax)*dx+(py-ay)*dy)/den;
        t=Math.max(0,Math.min(1,t));
        return Math.hypot(px-(ax+t*dx),py-(ay+t*dy));
    }
}
