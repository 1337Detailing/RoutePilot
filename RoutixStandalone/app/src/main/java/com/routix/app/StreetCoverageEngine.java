package com.routix.app;
import java.util.*;

/** Learns which directed 20 m road cells were actually driven, not just which street name was seen. */
final class StreetCoverageEngine {
 static final double CELL_M=20;
 static final class Cell {final String roadId,street,key;final int direction;final double lat,lon;Cell(String r,String s,String k,int d,double a,double o){roadId=r;street=s;key=k;direction=d;lat=a;lon=o;}}
 private final LinkedHashMap<String,Cell> visited=new LinkedHashMap<>();private Cell last;
 Cell observe(RouteAwareAnalyzer.Observation o){if(o==null||o.roadId==null||o.location==null||o.confidence<.52)return null;double lat=o.location.getLatitude(),lon=o.location.getLongitude();long y=Math.round(lat*111320/CELL_M),x=Math.round(lon*111320*Math.cos(Math.toRadians(lat))/CELL_M);int dir=0;if(last!=null){double east=(lon-last.lon)*Math.cos(Math.toRadians(lat)),north=lat-last.lat;if(Math.hypot(east,north)*111320>5)dir=(int)Math.round(((Math.toDegrees(Math.atan2(east,north))+360)%360)/45)%8;}String key=o.roadId+"@"+x+":"+y+":"+dir;Cell c=new Cell(o.roadId,o.street,key,dir,lat,lon);visited.put(key,c);last=c;return c;}
 int cells(){return visited.size();}double coveredMeters(){return visited.size()*CELL_M;}
 int oppositeDirectionCells(){int n=0;Set<String> seen=new HashSet<>();for(Cell c:visited.values()){String base=c.key.substring(0,c.key.lastIndexOf(':')+1);String opp=base+((c.direction+4)%8);if(visited.containsKey(opp)&&seen.add(base+Math.min(c.direction,(c.direction+4)%8)))n++;}return n;}
 boolean visited(String key){return visited.containsKey(key);}Collection<Cell> snapshot(){return new ArrayList<>(visited.values());}void reset(){visited.clear();last=null;}
}
