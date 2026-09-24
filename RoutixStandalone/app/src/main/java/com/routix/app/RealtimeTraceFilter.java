package com.routix.app;

import android.location.Location;

/**
 * Causal GPS smoother for recording. Uses an accuracy-weighted alpha-beta filter:
 * prediction keeps corners responsive, measurement confidence removes lateral GPS noise.
 * It never map-matches to a road, so private roads/yards and real collection manoeuvres remain intact.
 */
final class RealtimeTraceFilter {
    private static final double EARTH=6371000d;
    private boolean ready; private double lat0,lon0,cosLat,x,y,vx,vy; private long tNanos;
    private float lastAccuracy=10f;

    Location update(Location raw){
        if(raw==null)return null;
        if(!ready){reset(raw);return new Location(raw);}
        double dt=(raw.getElapsedRealtimeNanos()-tNanos)/1e9;
        if(!(dt>0)||dt>12){reset(raw);return new Location(raw);}
        double mx=(Math.toRadians(raw.getLongitude()-lon0))*EARTH*cosLat,my=Math.toRadians(raw.getLatitude()-lat0)*EARTH;
        double px=x+vx*dt,py=y+vy*dt,rx=mx-px,ry=my-py,residual=Math.hypot(rx,ry);
        float acc=Math.max(2.5f,Math.min(60f,raw.getAccuracy()));
        // Trust clean fixes and real turns more; suppress noisy stationary/low-speed wandering.
        double speed=raw.hasSpeed()?Math.max(0,raw.getSpeed()):Math.hypot(vx,vy);
        double confidence=1.0/(1.0+Math.pow(acc/7.0,2));
        double alpha=clamp(.16+.68*confidence+(speed>7?.08:0),.16,.88);
        if(speed<1.0&&residual<Math.max(4,acc*.65))alpha*=.45;
        if(residual>Math.max(18,acc*1.5))alpha=Math.min(.92,alpha+.18);
        x=px+alpha*rx;y=py+alpha*ry;
        double beta=clamp(.035+.18*confidence,.035,.22);vx+=beta*rx/dt;vy+=beta*ry/dt;
        // Blend Android's Doppler speed/bearing into velocity when available.
        if(raw.hasSpeed()&&raw.hasBearing()&&raw.getSpeed()>.7f){double b=Math.toRadians(raw.getBearing()),svx=raw.getSpeed()*Math.sin(b),svy=raw.getSpeed()*Math.cos(b);double w=acc<12?.28:.12;vx=vx*(1-w)+svx*w;vy=vy*(1-w)+svy*w;}
        tNanos=raw.getElapsedRealtimeNanos();lastAccuracy=acc;
        Location out=new Location(raw);out.setLatitude(lat0+Math.toDegrees(y/EARTH));out.setLongitude(lon0+Math.toDegrees(x/(EARTH*cosLat)));out.setAccuracy(Math.max(2.5f,Math.min(raw.getAccuracy(),(float)(lastAccuracy*.85+residual*.15))));
        return out;
    }
    void reset(){ready=false;}
    private void reset(Location l){lat0=l.getLatitude();lon0=l.getLongitude();cosLat=Math.max(.15,Math.cos(Math.toRadians(lat0)));x=y=vx=vy=0;tNanos=l.getElapsedRealtimeNanos();lastAccuracy=l.getAccuracy();ready=true;}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
}
