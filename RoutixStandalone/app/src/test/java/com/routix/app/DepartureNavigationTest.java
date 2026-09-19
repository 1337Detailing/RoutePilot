package com.routix.app;

import android.location.Location;
import android.os.SystemClock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class DepartureNavigationTest {
    private Location fix(double lat,double lon,long ageMs){
        Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAccuracy(8);
        l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-ageMs*1_000_000L);return l;
    }
    @Test public void staleFixCannotStartDepartureGuidance(){
        assertFalse(DepartureNavigation.isFreshFix(fix(48.75,7.95,DepartureNavigation.MAX_FIX_AGE_MS+1000)));
        assertTrue(DepartureNavigation.isFreshFix(fix(48.75,7.95,1000)));
    }
    @Test public void implausiblyDistantStartIsRejected(){
        Location here=fix(48.77,7.86,1000);
        RouteStore.Point near=new RouteStore.Point(48.78,7.88,0,5);
        RouteStore.Point far=new RouteStore.Point(48.95,7.60,0,5);
        assertTrue(DepartureNavigation.plausible(here,near));
        assertFalse(DepartureNavigation.plausible(here,far));
    }
}
