package com.routix.app;
import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

public class RouteComparisonTest {
    private RouteStore.Point p(double x,double y){return new RouteStore.Point(48+y/111320,x/(111320*Math.cos(Math.toRadians(48))),0,0);}
    @Test public void densityDoesNotCreateFalseChanges(){RouteComparison.Result r=RouteComparison.compare(Arrays.asList(p(0,0),p(300,0)),Arrays.asList(p(0,0),p(50,0),p(160,0),p(300,0)));assertEquals(0,r.addedM,.1);assertEquals(0,r.removedM,.1);assertEquals(0,r.reversedM,.1);assertFalse(r.orderChanged);}
    @Test public void reverseDirectionIsVisible(){RouteComparison.Result r=RouteComparison.compare(Arrays.asList(p(0,0),p(300,0)),Arrays.asList(p(300,0),p(0,0)));assertEquals(0,r.addedM,.1);assertTrue(r.reversedM>290);}
    @Test public void removedAndAddedRoadsAreDifferent(){RouteComparison.Result r=RouteComparison.compare(Arrays.asList(p(0,0),p(300,0)),Arrays.asList(p(0,100),p(300,100)));assertTrue(r.addedM>290);assertTrue(r.removedM>290);}
    @Test public void parityUsesIsoWeekYearAtNewYear(){assertTrue(CollectionSchedule.occurs(LocalDate.of(2021,1,1),1<<4,2));assertFalse(CollectionSchedule.occurs(LocalDate.of(2021,1,1),1<<4,1));assertTrue(CollectionSchedule.occurs(LocalDate.of(2021,1,11),1,1));assertFalse(CollectionSchedule.occurs(LocalDate.of(2021,1,12),1,0));}
    @Test public void accentAndPunctuationDoNotBlockStreetSearch(){assertEquals("rue de l eglise",StreetIndex.normalize("Rue de l’Église"));}
    @Test public void restoredProgressKeepsTheCorrectPassageOnALoop(){List<GuidanceEngine.Point> points=Arrays.asList(new GuidanceEngine.Point(48,7),new GuidanceEngine.Point(48.001,7),new GuidanceEngine.Point(48,7),new GuidanceEngine.Point(48.001,7));GuidanceEngine e=new GuidanceEngine(points,Collections.emptyList());e.restoreProgress(230);GuidanceEngine.State s=e.update(48.0001,7,1000,5);assertTrue(s.alongRouteM>=230);assertTrue(s.remainingM<110);}
}
