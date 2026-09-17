package com.routix.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RobolectricTestRunner;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ChromeLayoutTest {
    @Test public void rehearsalAndComparisonKeepControlsVisibleOnSmallScreens()throws Exception{
        try(ActivityController<RoutixActivity> controller=Robolectric.buildActivity(RoutixActivity.class).create()){
            RoutixActivity activity=controller.get();((org.osmdroid.views.MapView)field(activity,"map")).setUseDataConnection(false);
            RouteStore store=(RouteStore)field(activity,"store");RouteStore.Summary route=new RouteStore.Summary(new File(activity.getFilesDir(),"sample.gpx"));
            route.points.add(new RouteStore.Point(48,7,1000,5));route.points.add(new RouteStore.Point(48.002,7,2000,5));route.points.add(new RouteStore.Point(48.002,7.003,3000,5));route.events.add(new RouteStore.Event("REVERSE","Marche arrière · passage étroit",48.002,7,2000,5));
            for(boolean comparison:new boolean[]{false,true}){
                RouteStudyMap study=new RouteStudyMap(activity,store,route,comparison?route:null,comparison?RouteComparison.compare(route.points,route.points):null,null,null);study.show();
                Field mf=RouteStudyMap.class.getDeclaredField("map");mf.setAccessible(true);org.osmdroid.views.MapView map=(org.osmdroid.views.MapView)mf.get(study);map.setUseDataConnection(false);
                Field df=RouteStudyMap.class.getDeclaredField("dialog");df.setAccessible(true);android.app.Dialog dialog=(android.app.Dialog)df.get(study);
                ViewGroup content=dialog.findViewById(android.R.id.content);content.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));content.layout(0,0,320,600);
                assertTrue("Map must remain usable above playback controls",map.getHeight()>120);
                Bitmap bitmap=Bitmap.createBitmap(320,600,Bitmap.Config.ARGB_8888);content.draw(new Canvas(bitmap));File dir=new File("build/reports/chrome");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"chrome-"+(comparison?"comparison":"rehearsal")+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();study.close();
            }
        }
    }
    private Object field(Object a,String n)throws Exception{Field f=RoutixActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(a);}
    private void set(Object a,String n,int value)throws Exception{Field f=RoutixActivity.class.getDeclaredField(n);f.setAccessible(true);f.setInt(a,value);}
    private void invoke(Object a,String n)throws Exception{Method m=RoutixActivity.class.getDeclaredMethod(n);m.setAccessible(true);m.invoke(a);}
    @Test public void headerAndRecordingPanelNeverOverlapTheSingleDock()throws Exception{
        // Create, but do not start native MapLibre: this test verifies actual Android chrome.
        try(ActivityController<RoutixActivity> controller=Robolectric.buildActivity(RoutixActivity.class).create()){
            RoutixActivity activity=controller.get();FrameLayout root=(FrameLayout)field(activity,"root");
            ((org.osmdroid.views.MapView)field(activity,"map")).setUseDataConnection(false);
            set(activity,"systemTop",28);set(activity,"systemBottom",24);
            for(int[] size:new int[][]{{320,640},{390,844},{430,932}}){
                root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);invoke(activity,"positionChrome");
                root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);
                ViewGroup header=(ViewGroup)field(activity,"topBar");ViewGroup dock=(ViewGroup)field(activity,"dock");View panel=(View)field(activity,"recordSheet");
                assertEquals(5,dock.getChildCount());assertTrue(panel.getBottom()<dock.getTop());assertTrue(panel.getTop()>header.getBottom());assertTrue(dock.getBottom()<=size[1]-24);
                assertSame(header,((View)field(activity,"headerSpeed")).getParent());
                for(int i=0;i<header.getChildCount();i++){View child=header.getChildAt(i);assertTrue(child.getRight()<=header.getWidth());assertTrue(child.getTop()>=0);}
                Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
                File dir=new File("build/reports/chrome");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"chrome-"+size[0]+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            }
        }
    }
}
