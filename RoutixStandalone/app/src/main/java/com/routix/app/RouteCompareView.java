package com.routix.app;
import android.content.Context;import android.graphics.*;import android.view.View;import java.util.*;
/** Lightweight before/after preview; no map/network dependency. */
final class RouteCompareView extends View{
 private List<RouteStore.Point>a=Collections.emptyList(),b=Collections.emptyList();private int ca=Color.GRAY,cb=Color.WHITE;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 RouteCompareView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
 void setRoutes(List<RouteStore.Point>x,List<RouteStore.Point>y,int original,int improved){a=x==null?Collections.emptyList():x;b=y==null?Collections.emptyList():y;ca=original;cb=improved;invalidate();}
 protected void onDraw(Canvas c){super.onDraw(c);if(a.size()<2&&b.size()<2)return;double[] z=bounds();draw(c,a,z,ca,5,.42f);draw(c,b,z,cb,7,.92f);}
 private void draw(Canvas c,List<RouteStore.Point>x,double[]z,int color,float width,float alpha){if(x.size()<2)return;p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeWidth(width*getResources().getDisplayMetrics().density);p.setColor(color);p.setAlpha((int)(255*alpha));Path path=new Path();for(int i=0;i<x.size();i++){RouteStore.Point q=x.get(i);float px=(float)(14+(q.lon-z[1])/(z[3]-z[1])*(getWidth()-28)),py=(float)(14+(z[2]-q.lat)/(z[2]-z[0])*(getHeight()-28));if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}c.drawPath(path,p);}
 private double[] bounds(){double s=90,w=180,n=-90,e=-180;for(List<RouteStore.Point>x:Arrays.asList(a,b))for(RouteStore.Point q:x){s=Math.min(s,q.lat);n=Math.max(n,q.lat);w=Math.min(w,q.lon);e=Math.max(e,q.lon);}if(n-s<.0001){n+=.00005;s-=.00005;}if(e-w<.0001){e+=.00005;w-=.00005;}return new double[]{s,w,n,e};}
}