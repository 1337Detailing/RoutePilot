package com.routix.app;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SessionJournalTest {
    @Test public void committedFixesEventsAndProgressSurviveReopen(){Context c=RuntimeEnvironment.getApplication();c.deleteDatabase("active-session.db");SessionJournal first=new SessionJournal(c);first.state("recording","true");first.point(new RouteStore.Point(48,7,1000,5));first.event(new RouteStore.Event("REVERSE","Marche arrière",48,7,1000,5));first.state("guidance","tour.gpx");first.state("progress","185.5");first.close();SessionJournal reopened=new SessionJournal(c);List<RouteStore.Point> p=new ArrayList<>();List<RouteStore.Event> e=new ArrayList<>();reopened.read(p,e);assertEquals(1,p.size());assertEquals("REVERSE",e.get(0).type);assertEquals("185.5",reopened.state("progress","0"));reopened.clearRecording();p.clear();e.clear();reopened.read(p,e);assertTrue(p.isEmpty());assertTrue(e.isEmpty());assertEquals("tour.gpx",reopened.state("guidance",""));reopened.close();}
    @Test public void gpxNamespacedMarkersRoundTrip(){Context c=RuntimeEnvironment.getApplication();RouteStore store=new RouteStore(c,c.getSharedPreferences("roundtrip",0));java.io.File file=new java.io.File(c.getCacheDir(),"roundtrip.gpx");assertTrue(store.writeGpx(file,Arrays.asList(new RouteStore.Point(48,7,1000,5),new RouteStore.Point(48.001,7,2000,5)),Collections.singletonList(new RouteStore.Event("TWO_SIDES","2 côtés",48,7,1000,5)),1000,"Test"));assertEquals("TWO_SIDES",store.parse(file).events.get(0).type);}
    @Test public void bearingCrossesNorthByShortestArc(){assertEquals(359.44,MapStyles.smoothBearing(359,1),.001);assertEquals(.56,MapStyles.smoothBearing(1,359),.001);}
}
