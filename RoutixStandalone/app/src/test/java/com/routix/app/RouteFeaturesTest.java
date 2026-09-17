package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class RouteFeaturesTest {
    private final Context context=RuntimeEnvironment.getApplication();
    private SharedPreferences prefs(){return context.getSharedPreferences("feature-"+UUID.randomUUID(),0);}
    private List<RouteStore.Point> points(){return Arrays.asList(new RouteStore.Point(48,7,1000,5),new RouteStore.Point(48.005,7,2000,5),new RouteStore.Point(48.01,7,3000,5));}
    @Test public void restorationPreservesPreviousNameGeometryAndAnUndoVersion()throws Exception{
        RouteStore store=new RouteStore(context,prefs());File f=store.createRoute(points(),Collections.emptyList(),1000);assertNotNull(f);String original=store.displayName(f);store.rename(f,"Nouvelle tournée");
        RouteArchive.Version version=store.archive.versions(f).get(0);assertTrue(store.restore(f,version));assertEquals(original,store.displayName(f));assertEquals(3,store.parse(f).points.size());
        boolean undo=false;for(RouteArchive.Version v:store.archive.versions(f))if(v.name.equals("Nouvelle tournée"))undo=true;assertTrue(undo);
    }
    @Test public void overwritingATraceAutomaticallyArchivesItsPreviousGeometry()throws Exception{
        RouteStore store=new RouteStore(context,prefs());File f=store.createRoute(points(),Collections.emptyList(),2000);assertTrue(store.writeGpx(f,points().subList(0,2),Collections.emptyList(),2000,"Modifiée"));assertEquals(2,store.parse(f).points.size());assertTrue(store.restore(f,store.archive.versions(f).get(0)));assertEquals(3,store.parse(f).points.size());
    }
    @Test public void checkpointSurvivesRestartAndRejectsChangedRoute()throws Exception{
        SharedPreferences prefs=prefs();RouteStore store=new RouteStore(context,prefs);File f=store.createRoute(points(),Collections.emptyList(),3000);DumpCheckpoint cp=new DumpCheckpoint(f.getName(),StreetIndex.fingerprint(f),true,250,48.002,7,false);assertTrue(cp.save(prefs));DumpCheckpoint restored=DumpCheckpoint.load(prefs);assertTrue(restored.matches(f));assertTrue(restored.reverse);assertEquals(250,restored.progress,0);assertFalse(restored.returning);assertTrue(restored.returning().save(prefs));assertTrue(DumpCheckpoint.load(prefs).returning);
        store.writeGpx(f,points().subList(0,2),Collections.emptyList(),3000,"Changed");assertFalse(restored.matches(f));
    }
    @Test public void restoredGuidanceProgressCannotMoveBackwards(){
        GuidanceEngine engine=new GuidanceEngine(Arrays.asList(new GuidanceEngine.Point(48,7),new GuidanceEngine.Point(48.005,7),new GuidanceEngine.Point(48.01,7)),Collections.emptyList());
        engine.restoreProgress(250);assertEquals(250,engine.progressM(),.1);
        GuidanceEngine.Point behind=engine.pointAt(150);engine.update(behind.lat,behind.lon,1000,5);assertTrue(engine.progressM()>=250);
        GuidanceEngine.Point ahead=engine.pointAt(300);engine.update(ahead.lat,ahead.lon,2000,5);assertTrue(engine.progressM()>=250);
    }
}
