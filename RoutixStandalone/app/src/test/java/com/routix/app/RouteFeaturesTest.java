package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.io.File;
import java.lang.reflect.*;
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
    private Object field(Object a,String key)throws Exception{Field f=a.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(a);}
    private void set(Object a,String key,Object value)throws Exception{Field f=a.getClass().getDeclaredField(key);f.setAccessible(true);f.set(a,value);}
    @Test public void detourCannotConsumeCollectionProgressAndReturnDoesNotResetIt()throws Exception{
        try(org.robolectric.android.controller.ActivityController<RoutixActivity> controller=Robolectric.buildActivity(RoutixActivity.class).create()){
            RoutixActivity a=controller.get();((org.osmdroid.views.MapView)field(a,"map")).setUseDataConnection(false);RouteStore store=(RouteStore)field(a,"store");SharedPreferences prefs=(SharedPreferences)field(a,"prefs");
            File f=store.createRoute(points(),Collections.emptyList(),4000);RouteStore.Summary summary=store.parse(f);GuidanceEngine engine=new GuidanceEngine(Arrays.asList(new GuidanceEngine.Point(48,7),new GuidanceEngine.Point(48.005,7),new GuidanceEngine.Point(48.01,7)),Collections.emptyList());GuidanceEngine.Point anchor=engine.pointAt(250);
            DumpCheckpoint cp=new DumpCheckpoint(f.getName(),StreetIndex.fingerprint(f),false,250,anchor.lat,anchor.lon,false);cp.save(prefs);
            Method start=RoutixActivity.class.getDeclaredMethod("startGuidance",RouteStore.Summary.class,boolean.class,DumpCheckpoint.class);start.setAccessible(true);start.invoke(a,summary,false,cp);
            Method update=RoutixActivity.class.getDeclaredMethod("updateGuidance",Location.class);update.setAccessible(true);Location location=new Location("gps");location.setLatitude(48.006);location.setLongitude(7);location.setAccuracy(5);location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());update.invoke(a,location);GuidanceEngine active=(GuidanceEngine)field(a,"guidance");assertEquals(250,active.progressM(),.1);
            set(a,"dumpCheckpoint",cp.returning());set(a,"approachingStart",true);location.setLatitude(anchor.lat);location.setLongitude(anchor.lon);update.invoke(a,location);assertTrue(active.progressM()>=250);assertTrue(active.progressM()<260);assertNull(field(a,"dumpCheckpoint"));assertNull(DumpCheckpoint.load(prefs));
        }
    }
}
