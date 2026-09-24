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

    static List<Step> steps(List<Point> trace,List<Road> roads){
        Map<String,List<Road>> grid=new HashMap<>();
        for(Road road:roads)for(int j=1;j<road.points.size();j++){
            Point a=road.points.get(j-1),b=road.points.get(j);Road segment=new Road(road.key,road.name,Arrays.asList(a,b));if(road.name.equals("Voie sans nom"))segment=new Road(road.key,"",Arrays.asList(a,b));
            for(int x=(int)Math.floor((Math.min(a.x,b.x)-35)/100);x<=(int)Math.floor((Math.max(a.x,b.x)+35)/100);x++)for(int y=(int)Math.floor((Math.min(a.y,b.y)-35)/100);y<=(int)Math.floor((Math.max(a.y,b.y)+35)/100);y++)grid.computeIfAbsent(x+":"+y,k->new ArrayList<>()).add(segment);
        }
        List<Step> out=new ArrayList<>();String previous=null;
        for(int i=0;i<trace.size();i++){
            Point p=trace.get(i),before=trace.get(Math.max(0,i-1)),after=trace.get(Math.min(trace.size()-1,i+1));double ux=after.x-before.x,uy=after.y-before.y,len=Math.hypot(ux,uy);Road best=null;double score=Double.POSITIVE_INFINITY,second=Double.POSITIVE_INFINITY;
            for(Road road:grid.getOrDefault((int)Math.floor(p.x/100)+":"+(int)Math.floor(p.y/100),Collections.emptyList())){
                double roadScore=Double.POSITIVE_INFINITY;for(int j=1;j<road.points.size();j++){Point a=road.points.get(j-1),b=road.points.get(j);if(p.x<Math.min(a.x,b.x)-35||p.x>Math.max(a.x,b.x)+35||p.y<Math.min(a.y,b.y)-35||p.y>Math.max(a.y,b.y)+35)continue;double dist=segmentDistance(p,a,b);if(dist>35)continue;double vx=b.x-a.x,vy=b.y-a.y,vl=Math.hypot(vx,vy);double alignment=len<2||vl==0?1:Math.abs((ux*vx+uy*vy)/(len*vl));roadScore=Math.min(roadScore,dist+12*(1-alignment));}
                if(roadScore<score){if(best!=null&&!road.key.equals(best.key))second=score;best=road;score=roadScore;}else if(best!=null&&!road.key.equals(best.key))second=Math.min(second,roadScore);
            }
            String key=best==null?"?":best.key;String label=best==null?"Rue non identifiée — vérifier le tracé":best.name;if(best!=null&&second-score<2)label+=" (à vérifier)";if(!key.equals(previous)){out.add(new Step(i,out.size()+1,label));previous=key;}
        }
        return out;
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
