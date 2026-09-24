package com.routix.app;

import android.location.Location;
import android.os.SystemClock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.util.GeoPoint;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class DepartureNavigationTest {
    private Location fix(double lat,double lon,long ageMs){
        Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAccuracy(8);l.setTime(System.currentTimeMillis()-ageMs);
        long now=SystemClock.elapsedRealtimeNanos();if(now>ageMs*1_000_000L)l.setElapsedRealtimeNanos(now-ageMs*1_000_000L);return l;
    }
    @Test public void staleFixCannotStartDepartureGuidance(){
        assertFalse(DepartureNavigation.isFreshFix(fix(48.75,7.95,DepartureNavigation.MAX_FIX_AGE_MS+1000)));
        assertTrue(DepartureNavigation.isFreshFix(fix(48.75,7.95,1000)));
    }
    @Test public void invalidAccuracyCannotStartDepartureGuidance(){
        Location nan=fix(48.75,7.95,1000);nan.setAccuracy(Float.NaN);
        Location infinite=fix(48.75,7.95,1000);infinite.setAccuracy(Float.POSITIVE_INFINITY);
        assertFalse(DepartureNavigation.isFreshFix(nan));
        assertFalse(DepartureNavigation.isFreshFix(infinite));
    }
    @Test public void implausiblyDistantStartIsRejected(){
        Location here=fix(48.77,7.86,1000);
        RouteStore.Point near=new RouteStore.Point(48.78,7.88,0,5);
        RouteStore.Point far=new RouteStore.Point(48.95,7.60,0,5);
        assertTrue(DepartureNavigation.plausible(here,near));
        assertFalse(DepartureNavigation.plausible(here,far));
    }
    @Test public void farOffRouteFixCannotJumpApproachProgress(){
        List<GeoPoint> route=Arrays.asList(
                new GeoPoint(48.7500,7.9500),
                new GeoPoint(48.7510,7.9500),
                new GeoPoint(48.7520,7.9500),
                new GeoPoint(48.7530,7.9500));
        Location farAway=fix(48.7600,7.9600,500);
        assertEquals(1,DepartureNavigation.advanceIndex(route,1,farAway));
    }
    @Test public void nearbyFixStillAdvancesApproachProgress(){
        List<GeoPoint> route=Arrays.asList(
                new GeoPoint(48.7500,7.9500),
                new GeoPoint(48.7510,7.9500),
                new GeoPoint(48.7520,7.9500),
                new GeoPoint(48.7530,7.9500));
        Location nearby=fix(48.75205,7.95002,500);
        assertEquals(2,DepartureNavigation.advanceIndex(route,1,nearby));
    }
}
