package com.routix.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class GuidanceEngineTest {
    private GuidanceEngine.Point p(double east,double north){return new GuidanceEngine.Point(north/111320,east/111320);}
    private GuidanceEngine engine(GuidanceEngine.Point... points){return new GuidanceEngine(Arrays.asList(points),Collections.emptyList());}
    private GuidanceEngine.State fix(GuidanceEngine e,double east,double north,long t){GuidanceEngine.Point p=p(east,north);return e.update(p.lat,p.lon,t,5);}
    @Test public void sparseSegmentProgressIsContinuousNotRoundedToNextPoint(){
        GuidanceEngine e=engine(p(0,0),p(1000,0));fix(e,0,0,1000);
        GuidanceEngine.State a=fix(e,100,0,2000),b=fix(e,130,0,3000);
        assertEquals(900,a.remainingM,3);assertEquals(870,b.remainingM,3);assertFalse(b.finished);assertEquals(0,b.nearestIndex);assertEquals(p(130,0).lon,b.position.lon,.000001);
    }
    @Test public void closedLoopDoesNotFinishAtStartOrJumpToLastVisit(){
        GuidanceEngine e=engine(p(0,0),p(100,0),p(100,100),p(0,100),p(0,0));
        GuidanceEngine.State s=fix(e,0,0,1000);assertFalse(s.finished);assertTrue(s.remainingM>390);
        fix(e,60,0,2000);fix(e,100,0,3000);s=fix(e,100,50,4000);assertEquals(250,s.remainingM,3);
    }
    @Test public void outAndBackDoesNotEraseReturnBeforeTurnaround(){
        GuidanceEngine e=engine(p(0,0),p(100,0),p(0,0));fix(e,0,0,1000);
        assertEquals(150,fix(e,50,0,2000).remainingM,2);fix(e,100,0,3000);
        assertEquals(50,fix(e,50,0,4000).remainingM,2);assertTrue(fix(e,0,0,5000).finished);
    }
    @Test public void offRouteAndBadAccuracyDoNotConsumeRoute(){
        GuidanceEngine e=engine(p(0,0),p(1000,0));fix(e,0,0,1000);GuidanceEngine.State a=fix(e,50,0,2000),b=fix(e,150,100,3000);
        assertTrue(b.offRoute);assertEquals(a.remainingM,b.remainingM,.01);
        GuidanceEngine.Point q=p(400,0);GuidanceEngine.State bad=e.update(q.lat,q.lon,4000,80);assertTrue(bad.poorAccuracy);assertEquals(a.remainingM,bad.remainingM,.01);
    }
    @Test public void explicitResumptionHandlesMissedGpsAndNeverRunsAutomatically(){
        GuidanceEngine e=engine(p(0,0),p(2000,0));fix(e,0,0,1000);
        GuidanceEngine.State jump=fix(e,1000,0,2000);assertTrue(jump.offRoute);assertTrue(jump.remainingM>1990);
        GuidanceEngine.Point q=p(1000,0);assertTrue(e.reposition(q.lat,q.lon));assertEquals(1000,fix(e,1000,0,3000).remainingM,3);
    }
    @Test public void reverseDirectionUsesSameContinuousDistance(){
        GuidanceEngine e=engine(p(1000,0),p(0,0));fix(e,1000,0,1000);assertEquals(900,fix(e,900,0,2000).remainingM,3);
    }
    @Test public void duplicatePointsAndOldFixesCannotBreakProgress(){
        GuidanceEngine e=engine(p(0,0),p(0,0),p(100,0));fix(e,0,0,1000);GuidanceEngine.State a=fix(e,40,0,3000),old=fix(e,90,0,2000);assertEquals(a.remainingM,old.remainingM,.01);assertFalse(Float.isNaN(old.remainingM));
    }
    @Test public void eventsOnReturnVisitAreNotMistakenForOutboundVisit(){
        List<GuidanceEngine.Point> p=Arrays.asList(p(0,0),p(50,0),p(100,0),p(50,0),p(0,0));GuidanceEngine.Point q=p(50,0);
        GuidanceEngine e=new GuidanceEngine(p,Collections.singletonList(new GuidanceEngine.Event("REVERSE","Retour",q.lat,q.lon,3)));
        GuidanceEngine.State s=fix(e,0,0,1000);assertEquals(150,s.distanceToNextEventM,2);
    }

    @Test public void resumeOnReturnLegNeverResetsToOutboundLeg(){
        GuidanceEngine e=engine(p(0,0),p(100,0),p(0,0));
        fix(e,0,0,1000);fix(e,50,0,2000);fix(e,100,0,3000);
        GuidanceEngine.State before=fix(e,50,0,4000);
        GuidanceEngine.Point here=p(50,0);
        assertTrue(e.reposition(here.lat,here.lon));
        GuidanceEngine.State after=fix(e,50,0,5000);
        assertEquals(before.remainingM,after.remainingM,.1);
        assertTrue(after.alongRouteM>=before.alongRouteM);
        assertEquals(50,after.remainingM,2);
    }
    @Test public void resumeAtLoopArrivalDoesNotRestoreWholeRoute(){
        GuidanceEngine e=engine(p(0,0),p(100,0),p(100,100),p(0,100),p(0,0));
        fix(e,0,0,1000);fix(e,100,0,2000);fix(e,100,100,3000);fix(e,0,100,4000);
        assertTrue(fix(e,0,0,5000).finished);
        GuidanceEngine.Point here=p(0,0);assertTrue(e.reposition(here.lat,here.lon));
        GuidanceEngine.State after=fix(e,0,0,6000);
        assertTrue(after.finished);assertEquals(0,after.remainingM,.1);
    }
    @Test public void resumeBehindProgressCannotBringBackTravelledTrace(){
        GuidanceEngine e=engine(p(0,0),p(1000,0));fix(e,0,0,1000);
        GuidanceEngine.State before=fix(e,100,0,2000);
        GuidanceEngine.Point slightlyBehind=p(90,0);assertTrue(e.reposition(slightlyBehind.lat,slightlyBehind.lon));
        GuidanceEngine.State after=fix(e,90,0,3000);assertEquals(before.remainingM,after.remainingM,.1);
        GuidanceEngine.Point farBehind=p(0,0);assertFalse(e.reposition(farBehind.lat,farBehind.lon));
        assertEquals(before.remainingM,fix(e,100,0,4000).remainingM,.1);
    }
    @Test public void repeatedResumeAndInvalidFixDoNotResetProgress(){
        GuidanceEngine e=engine(p(1000,0),p(0,0));fix(e,1000,0,1000);fix(e,800,0,2000);
        GuidanceEngine.Point here=p(800,0);
        for(int i=0;i<3;i++)assertTrue(e.reposition(here.lat,here.lon));
        assertFalse(e.reposition(Double.NaN,here.lon));
        assertEquals(800,fix(e,800,0,3000).remainingM,3);
    }
    @Test public void nextManeuverFindsRightAndLeftTurnsAhead(){
        GuidanceEngine right=engine(p(0,0),p(100,0),p(100,-100));
        GuidanceEngine.State rs=fix(right,0,0,1000);
        GuidanceEngine.Maneuver rm=right.nextManeuver(rs);
        assertEquals(1,rm.direction);assertTrue(rm.distanceM>90&&rm.distanceM<110);

        GuidanceEngine left=engine(p(0,0),p(100,0),p(100,100));
        GuidanceEngine.State ls=fix(left,0,0,1000);
        GuidanceEngine.Maneuver lm=left.nextManeuver(ls);
        assertEquals(-1,lm.direction);assertTrue(lm.distanceM>90&&lm.distanceM<110);
    }

    @Test public void oneHourReplayAndRepeatedProcessRecoveryKeepSameProgress(){
        GuidanceEngine continuous=engine(p(0,0),p(12000,0)),restored=engine(p(0,0),p(12000,0));
        for(int second=0;second<=3600;second++){
            GuidanceEngine.State expected=fix(continuous,second*3,0,(second+1)*1000L);
            if(second>0&&second%900==0){restored=engine(p(0,0),p(12000,0));restored.restoreProgress(expected.alongRouteM);}
            GuidanceEngine.State actual=fix(restored,second*3,0,(second+1)*1000L);
            assertTrue(Float.isFinite(actual.remainingM));assertEquals(expected.remainingM,actual.remainingM,.1);
            assertTrue(actual.alongRouteM<=expected.alongRouteM+.1f);
        }
    }
}
