package com.routix.app;
import android.location.Location;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import java.util.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class RouteAwareAnalyzerTest {
 private Location fix(double lat,double lon,float bearing){Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAccuracy(6);l.setSpeed(8);l.setBearing(bearing);return l;}
 @Test public void recognizesAndSnapsToLikelyStreet(){RouteAwareAnalyzer a=new RouteAwareAnalyzer();RouteAwareAnalyzer.Road r=new RouteAwareAnalyzer.Road("1","Rue Principale",48.8300,7.9800,48.8300,7.9900);RouteAwareAnalyzer.Observation o=a.update(fix(48.830025,7.985,90),Arrays.asList(r));assertEquals("Rue Principale",o.street);assertTrue(o.confidence>.6);assertTrue(o.snapped);}
 @Test public void headingDisambiguatesCrossingRoads(){RouteAwareAnalyzer a=new RouteAwareAnalyzer();List<RouteAwareAnalyzer.Road> roads=Arrays.asList(new RouteAwareAnalyzer.Road("h","Est Ouest",48.83,7.98,48.83,7.99),new RouteAwareAnalyzer.Road("v","Nord Sud",48.825,7.985,48.835,7.985));RouteAwareAnalyzer.Observation o=a.update(fix(48.83,7.985,90),roads);assertEquals("Est Ouest",o.street);}
 @Test public void doesNotSnapFarOffRoad(){RouteAwareAnalyzer a=new RouteAwareAnalyzer();RouteAwareAnalyzer.Observation o=a.update(fix(48.831,7.985,90),Arrays.asList(new RouteAwareAnalyzer.Road("1","Rue",48.83,7.98,48.83,7.99)));assertFalse(o.snapped);assertNull(o.roadId);}
}
