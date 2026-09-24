package com.routix.app;
import android.location.Location;
import org.junit.Test;
import static org.junit.Assert.*;
public class RealtimeTraceFilterTest {
    private Location p(double lat,double lon,long ms,float accuracy,float speed,float bearing){Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setTime(ms);l.setElapsedRealtimeNanos(ms*1000000L);l.setAccuracy(accuracy);l.setSpeed(speed);l.setBearing(bearing);return l;}
    @Test public void noisyStraightTraceStaysNearCenterline(){RealtimeTraceFilter f=new RealtimeTraceFilter();double base=48.830000;Location a=f.update(p(base,7.980000,1000,5,8,90));Location b=f.update(p(base+.000045,7.980100,2000,18,8,90));Location c=f.update(p(base-.000040,7.980200,3000,18,8,90));assertTrue(Math.abs(c.getLatitude()-base)<.000040);assertTrue(c.getLongitude()>b.getLongitude());}
    @Test public void resetStartsExactlyAtNewFix(){RealtimeTraceFilter f=new RealtimeTraceFilter();f.update(p(48.83,7.98,1000,5,0,0));f.reset();Location x=f.update(p(48.84,7.99,2000,5,0,0));assertEquals(48.84,x.getLatitude(),1e-9);assertEquals(7.99,x.getLongitude(),1e-9);}
    @Test public void longGapReanchorsInsteadOfExtrapolating(){RealtimeTraceFilter f=new RealtimeTraceFilter();f.update(p(48.83,7.98,1000,5,10,90));Location x=f.update(p(48.831,7.99,20000,5,10,90));assertEquals(48.831,x.getLatitude(),1e-9);assertEquals(7.99,x.getLongitude(),1e-9);}
}
