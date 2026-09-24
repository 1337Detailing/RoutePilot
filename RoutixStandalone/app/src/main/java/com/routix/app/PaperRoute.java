package com.routix.app;

import java.util.*;

/** Local metric geometry; never replaces the recorded route with a calculated itinerary. */
final class PaperRoute {
    static final class Point { final double x,y; Point(double x,double y){this.x=x;this.y=y;} }
    static final class Road {
        final String name,key; final List<Point> points;
        Road(String id,String name,List<Point> points){this.name=name.isEmpty()?"Voie sans nom":name;this.key=name.isEmpty()?id:name;this.points=points;}
    }
    static final class Step { final int index,number; final String name; Step(int index,int number,String name){this.index=index;this.number=number;this.name=name;} }

    static double distance(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    static double segmentDistance(Point p,Point a,Point b){double dx=b.x-a.x,dy=b.y-a.y,d=dx*dx+dy*dy;double t=d==0?0:Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/d));return Math.hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);}

    static final class Candidate { final Road road; final double score,distance; Candidate(Road road,double score,double distance){this.road=road;this.score=score;this.distance=distance;} }

    /**
     * Local map matching for paper plans. It deliberately favours continuity:
     * a GPS fix must provide meaningful evidence before Routix switches streets.
     * This avoids rapid A/B/A oscillations at junctions and parallel roads.
     */
    static List<Step> steps(List<Point> trace,List<Road> roads){
        Map<String,List<Road>> grid=roadGrid(roads);List<Step> raw=new ArrayList<>();
        String previous=null;Road previousRoad=null;int lastSwitch=-99;
        for(int i=0;i<trace.size();i++){
            Point p=trace.get(i),before=trace.get(Math.max(0,i-2)),after=trace.get(Math.min(trace.size()-1,i+2));
            double ux=after.x-before.x,uy=after.y-before.y,len=Math.hypot(ux,uy);
            List<Candidate> candidates=candidates(p,ux,uy,len,grid);
            Candidate best=candidates.isEmpty()?null:candidates.get(0),same=null;
            if(previousRoad!=null)for(Candidate x:candidates)if(x.road.key.equals(previousRoad.key)){same=x;break;}
            // Hysteresis: remain on the current road unless the alternative is clearly better.
            if(same!=null&&best!=null&&!same.road.key.equals(best.road.key)){
                double required=(i-lastSwitch<4?9:5);if(same.distance<=28&&best.score+required>=same.score)best=same;
            }
            String key=best==null?"?":best.road.key;String label=best==null?"Rue non identifiée — vérifier le tracé":best.road.name;
            if(best!=null&&candidates.size()>1){Candidate second=candidates.get(1);if(!second.road.key.equals(best.road.key)&&second.score-best.score<1.5)label+=" (à vérifier)";}
            if(!key.equals(previous)){raw.add(new Step(i,0,label));previous=key;previousRoad=best==null?null:best.road;lastSwitch=i;}
        }
        // Remove one-fix street flicker: A/B/A becomes A when B lasted only a tiny trace section.
        List<Step> clean=new ArrayList<>(raw);
        for(int i=1;i+1<clean.size();){Step a=clean.get(i-1),b=clean.get(i),d=clean.get(i+1);int span=d.index-b.index;
            if(a.name.equals(d.name)&&span<=3){clean.remove(i);clean.remove(i);continue;}i++;}
        List<Step> out=new ArrayList<>();for(Step x:clean)out.add(new Step(x.index,out.size()+1,x.name));return out;
    }

    private static Map<String,List<Road>> roadGrid(List<Road> roads){
        Map<String,List<Road>> grid=new HashMap<>();for(Road road:roads)for(int j=1;j<road.points.size();j++){Point a=road.points.get(j-1),b=road.points.get(j);Road segment=new Road(road.key,road.name.equals("Voie sans nom")?"":road.name,Arrays.asList(a,b));
            for(int x=(int)Math.floor((Math.min(a.x,b.x)-40)/100);x<=(int)Math.floor((Math.max(a.x,b.x)+40)/100);x++)for(int y=(int)Math.floor((Math.min(a.y,b.y)-40)/100);y<=(int)Math.floor((Math.max(a.y,b.y)+40)/100);y++)grid.computeIfAbsent(x+":"+y,k->new ArrayList<>()).add(segment);}return grid;
    }
    private static List<Candidate> candidates(Point p,double ux,double uy,double len,Map<String,List<Road>> grid){
        Map<String,Candidate> unique=new HashMap<>();for(Road road:grid.getOrDefault((int)Math.floor(p.x/100)+":"+(int)Math.floor(p.y/100),Collections.emptyList())){
            Point a=road.points.get(0),b=road.points.get(1);double dist=segmentDistance(p,a,b);if(dist>40)continue;double vx=b.x-a.x,vy=b.y-a.y,vl=Math.hypot(vx,vy);double alignment=len<3||vl==0?1:Math.abs((ux*vx+uy*vy)/(len*vl));
            // Distance dominates; heading disambiguates parallel/crossing streets.
            double score=dist+Math.min(16,16*(1-alignment));Candidate old=unique.get(road.key);if(old==null||score<old.score)unique.put(road.key,new Candidate(road,score,dist));
        }List<Candidate> out=new ArrayList<>(unique.values());out.sort(Comparator.comparingDouble(x->x.score));return out;
    }

    /** Keeps paper plans readable and bounded to three complete sheets. */
    static List<int[]> sheets(List<Point> trace,List<Step> steps){List<int[]> natural=naturalSheets(trace,steps);if(natural.size()<=3)return natural;return compactSheets(trace,steps,3);}

    private static List<int[]> naturalSheets(List<Point> trace,List<Step> steps){
        List<int[]> out=new ArrayList<>();int start=0;while(start<trace.size()-1){Point p=trace.get(start);double minX=p.x,maxX=p.x,minY=p.y,maxY=p.y;int end=start+1,count=0;for(;end<trace.size();end++){Point q=trace.get(end);double nx=Math.min(minX,q.x),xx=Math.max(maxX,q.x),ny=Math.min(minY,q.y),xy=Math.max(maxY,q.y);boolean change=false;for(Step s:steps)if(s.index==end){change=true;break;}if(end>start+1&&(Math.max(xx-nx,xy-ny)>1200||(change&&count>=9)))break;minX=nx;maxX=xx;minY=ny;maxY=xy;if(change)count++;}int last=Math.max(start+1,end-1);out.add(new int[]{start,last});start=last;}return out;
    }

    /**
     * When a long route must be compressed to three sheets, prefer a nearby street
     * transition instead of cutting a street in the middle. The 18% window keeps
     * page lengths balanced while making the printed instructions much easier to follow.
     */
    private static List<int[]> compactSheets(List<Point> trace,List<Step> steps,int maxPages){
        List<int[]> out=new ArrayList<>();if(trace.size()<2)return out;double[] cumulative=new double[trace.size()];for(int i=1;i<trace.size();i++)cumulative[i]=cumulative[i-1]+distance(trace.get(i-1),trace.get(i));double total=cumulative[cumulative.length-1];int start=0;
        for(int page=1;page<=maxPages&&start<trace.size()-1;page++){
            int end;if(page==maxPages)end=trace.size()-1;else{double target=total*page/maxPages,tolerance=Math.max(80,total/maxPages*.18);end=start+1;while(end<trace.size()-1&&cumulative[end]<target)end++;int best=-1;double bestDelta=Double.POSITIVE_INFINITY;for(Step s:steps){if(s.index<=start||s.index>=trace.size()-1)continue;double delta=Math.abs(cumulative[s.index]-target);if(delta<=tolerance&&delta<bestDelta){best=s.index;bestDelta=delta;}}if(best>start)end=best;}
            if(end<=start)end=Math.min(trace.size()-1,start+1);out.add(new int[]{start,end});start=end;
        }
        return out;
    }
}
