package com.routix.app;
import android.location.Location;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) public class StreetCoverageEngineTest{
 private RouteAwareAnalyzer.Observation o(double lat,double lon){Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);return new RouteAwareAnalyzer.Observation("r1","Rue Test",.9,2,true,false,l);}
 @Test public void repeatedFixDoesNotInflateCoverage(){StreetCoverageEngine e=new StreetCoverageEngine();e.observe(o(48.83,7.98));e.observe(o(48.83,7.98));assertEquals(1,e.cells());assertEquals(20,e.coveredMeters(),.01);}
 @Test public void movementCreatesNewCoverageCells(){StreetCoverageEngine e=new StreetCoverageEngine();e.observe(o(48.83,7.98));e.observe(o(48.83,7.9805));assertTrue(e.cells()>=2);assertTrue(e.coveredMeters()>=40);}
 @Test public void lowConfidenceIsIgnored(){StreetCoverageEngine e=new StreetCoverageEngine();Location l=new Location("gps");l.setLatitude(48.83);l.setLongitude(7.98);e.observe(new RouteAwareAnalyzer.Observation("r","Rue",.2,3,false,false,l));assertEquals(0,e.cells());}
}