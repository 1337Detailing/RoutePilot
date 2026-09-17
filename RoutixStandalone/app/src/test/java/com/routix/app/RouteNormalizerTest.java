package com.routix.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class RouteNormalizerTest {
    private static RouteStore.Point p(double lat,double lon){return new RouteStore.Point(lat,lon,0,0);}

    @Test public void removesInvalidAndConsecutiveNoiseWithoutReordering(){
        List<RouteStore.Point> in=new ArrayList<>();
        in.add(p(48.0,7.0));
        in.add(p(48.0,7.000001));
        in.add(p(Double.NaN,7.0));
        in.add(p(48.0,7.001));
        RouteNormalizer.Result r=RouteNormalizer.normalize(in);
        assertEquals(1,r.invalidRemoved);
        assertEquals(1,r.duplicateRemoved);
        assertEquals(2,r.points.size());
        assertEquals(7.0,r.points.get(0).lon,0.000001);
        assertEquals(7.001,r.points.get(1).lon,0.000001);
    }

    @Test public void simplifiesStraightDenseTrace(){
        List<RouteStore.Point> in=new ArrayList<>();
        for(int i=0;i<100;i++) in.add(p(48.0,7.0+i*0.00005));
        RouteNormalizer.Result r=RouteNormalizer.normalize(in);
        assertTrue(r.points.size()<20);
        assertEquals(in.get(0).lon,r.points.get(0).lon,0.0);
        assertEquals(in.get(in.size()-1).lon,r.points.get(r.points.size()-1).lon,0.0);
    }

    @Test public void preservesOutAndBackLoopShape(){
        List<RouteStore.Point> in=new ArrayList<>();
        in.add(p(48.0,7.0));
        in.add(p(48.0,7.01));
        in.add(p(48.0,7.02));
        in.add(p(48.0,7.01));
        in.add(p(48.0,7.0));
        RouteNormalizer.Result r=RouteNormalizer.normalize(in);
        assertTrue(r.points.size()>=3);
        double maxLon=7.0;
        for(RouteStore.Point q:r.points) maxLon=Math.max(maxLon,q.lon);
        assertEquals(7.02,maxLon,0.00001);
        assertEquals(7.0,r.points.get(0).lon,0.00001);
        assertEquals(7.0,r.points.get(r.points.size()-1).lon,0.00001);
    }
}
