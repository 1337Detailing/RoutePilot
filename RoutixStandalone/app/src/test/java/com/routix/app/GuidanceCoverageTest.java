package com.routix.app;import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class GuidanceCoverageTest{
 private GuidanceCoverage e(){return new GuidanceCoverage(Arrays.asList(new GuidanceEngine.Point(48.83,7.98),new GuidanceEngine.Point(48.83,7.981),new GuidanceEngine.Point(48.83,7.982),new GuidanceEngine.Point(48.83,7.983)));}
 @Test public void drivenSegmentsCountOnce(){GuidanceCoverage g=e();g.observe(0,4);g.observe(0,3);assertEquals(1,g.doneSegments());assertTrue(g.coveredMeters()>60);}
 @Test public void offTraceDoesNotCompleteSegment(){GuidanceCoverage g=e();g.observe(1,50);assertEquals(0,g.doneSegments());}
 @Test public void skippedEarlierSegmentIsReported(){GuidanceCoverage g=e();g.observe(2,4);assertTrue(g.missedCountBefore(2)>0);}
}