package com.routix.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class GuidanceJumpFilterTest {
    private GuidanceEngine.Point p(double east){ return new GuidanceEngine.Point(0,east/111320); }

    @Test public void fastTeleportOnSameTraceDoesNotConsumeRouteAndRecoveryContinues(){
        GuidanceEngine e=new GuidanceEngine(Arrays.asList(p(0),p(1000)),Collections.emptyList());
        e.update(p(0).lat,p(0).lon,1000,5);
        GuidanceEngine.State normal=e.update(p(20).lat,p(20).lon,2000,5);
        GuidanceEngine.State teleport=e.update(p(320).lat,p(320).lon,3000,5);
        assertTrue(teleport.poorAccuracy);
        assertEquals(normal.alongRouteM,teleport.alongRouteM,.1);
        GuidanceEngine.State recovered=e.update(p(40).lat,p(40).lon,4000,5);
        assertFalse(recovered.poorAccuracy);
        assertEquals(40,recovered.alongRouteM,2);
    }

    @Test public void plausibleHighwayMotionIsNotRejected(){
        GuidanceEngine e=new GuidanceEngine(Arrays.asList(p(0),p(1000)),Collections.emptyList());
        e.update(p(0).lat,p(0).lon,1000,5);
        GuidanceEngine.State s=e.update(p(100).lat,p(100).lon,4000,5);
        assertFalse(s.poorAccuracy);
        assertEquals(100,s.alongRouteM,2);
    }
}
