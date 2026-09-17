package com.routix.app;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PaperRouteTest {
    private PaperRoute.Point p(double x,double y){return new PaperRoute.Point(x,y);}
    private PaperRoute.Road road(String id,String name,PaperRoute.Point... pts){return new PaperRoute.Road(id,name,Arrays.asList(pts));}
    @Test public void streetsAreNumberedInTravelOrderIncludingReturn(){
        List<PaperRoute.Point> trace=Arrays.asList(p(0,0),p(50,0),p(100,0),p(100,50),p(100,100),p(100,50),p(100,0),p(50,0),p(0,0));
        List<PaperRoute.Step> steps=PaperRoute.steps(trace,Arrays.asList(road("1","Rue A",p(0,0),p(100,0)),road("2","Rue B",p(100,0),p(100,100))));
        assertEquals(3,steps.size());assertEquals(1,steps.get(0).number);assertEquals(2,steps.get(1).number);assertEquals(3,steps.get(2).number);assertTrue(steps.get(2).name.startsWith("Rue A"));
    }
    @Test public void osmWaySplitsOnSameStreetDoNotCreateNewNumbers(){List<PaperRoute.Step> s=PaperRoute.steps(Arrays.asList(p(0,0),p(50,0),p(100,0)),Arrays.asList(road("1","Rue A",p(0,0),p(50,0)),road("2","Rue A",p(50,0),p(100,0))));assertEquals(1,s.size());}
    @Test public void missingStreetIsExplicit(){assertTrue(PaperRoute.steps(Arrays.asList(p(0,0),p(100,0)),Collections.emptyList()).get(0).name.contains("non identifiée"));}
    @Test public void pagesCoverWholeRouteWithSharedBoundaryAndNeverExceedThree(){
        List<PaperRoute.Point> pts=new ArrayList<>();for(int i=0;i<100;i++)pts.add(p(i*100,0));List<PaperRoute.Step> steps=new ArrayList<>();for(int i=0;i<60;i++)steps.add(new PaperRoute.Step(Math.min(i,98),i+1,"Rue "+i));
        List<int[]> pages=PaperRoute.sheets(pts,steps);assertTrue(pages.size()>=1);assertTrue(pages.size()<=3);assertEquals(0,pages.get(0)[0]);assertEquals(99,pages.get(pages.size()-1)[1]);for(int i=1;i<pages.size();i++)assertEquals(pages.get(i-1)[1],pages.get(i)[0]);
    }
}
