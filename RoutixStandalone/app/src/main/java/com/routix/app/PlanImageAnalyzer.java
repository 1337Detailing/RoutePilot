package com.routix.app;

import android.graphics.*;
import java.util.*;

/** First-stage plan vision: detects saturated/inked collection strokes without any cloud API. */
final class PlanImageAnalyzer {
 static final class PixelPoint {final int x,y;PixelPoint(int x,int y){this.x=x;this.y=y;}}
 static final class Result {final int width,height;final List<PixelPoint> stroke;final Rect bounds;final double coverage;Result(int w,int h,List<PixelPoint>s,Rect b,double c){width=w;height=h;stroke=s;bounds=b;coverage=c;}boolean usable(){return stroke.size()>=20&&coverage>.0002;}}
 static Result analyze(Bitmap source){
  if(source==null||source.getWidth()<32||source.getHeight()<32)return new Result(0,0,new ArrayList<>(),new Rect(),0);
  int max=1400,w=source.getWidth(),h=source.getHeight();double scale=Math.min(1d,max/(double)Math.max(w,h));Bitmap b=scale<1?Bitmap.createScaledBitmap(source,(int)(w*scale),(int)(h*scale),true):source;w=b.getWidth();h=b.getHeight();
  List<PixelPoint> pts=new ArrayList<>();int minX=w,minY=h,maxX=0,maxY=0,step=Math.max(2,Math.max(w,h)/700);
  for(int y=0;y<h;y+=step)for(int x=0;x<w;x+=step){int c=b.getPixel(x,y),r=Color.red(c),g=Color.green(c),bl=Color.blue(c);int mx=Math.max(r,Math.max(g,bl)),mn=Math.min(r,Math.min(g,bl));float sat=mx==0?0:(mx-mn)/(float)mx;boolean colored=sat>.30f&&mx>85;boolean marker=(r>150&&g>80&&bl<150)||(r>140&&bl>130&&g<150)||(g>125&&bl>100&&r<150);if(colored&&marker){pts.add(new PixelPoint(x,y));minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}}
  Rect bounds=pts.isEmpty()?new Rect():new Rect(minX,minY,maxX+1,maxY+1);double cov=pts.size()/(double)Math.max(1,(w/step)*(h/step));return new Result(w,h,pts,bounds,cov);
 }
 static Bitmap overlay(Bitmap src,Result r){Bitmap out=src.copy(Bitmap.Config.ARGB_8888,true);Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.argb(150,255,80,120));p.setStrokeWidth(Math.max(3,src.getWidth()/300f));float sx=src.getWidth()/(float)Math.max(1,r.width),sy=src.getHeight()/(float)Math.max(1,r.height);for(PixelPoint q:r.stroke)c.drawCircle(q.x*sx,q.y*sy,p.getStrokeWidth(),p);return out;}
}