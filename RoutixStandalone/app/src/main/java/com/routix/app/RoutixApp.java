package com.routix.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Map lifecycle only. Activity chrome belongs to RoutixActivity's view tree. */
public final class RoutixApp extends Application implements Application.ActivityLifecycleCallbacks {
    private final java.util.Map<Activity,ModernMapController> maps=new java.util.HashMap<>();
    @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);}
    ModernMapController mapFor(Activity activity){return maps.get(activity);}
    @Override public void onActivityCreated(Activity activity,Bundle state){}
    @Override public void onActivityStarted(Activity activity){if(activity instanceof RoutixActivity){ModernMapController map=maps.get(activity);if(map==null){map=new ModernMapController(activity);maps.put(activity,map);}map.onStart();}}
    @Override public void onActivityResumed(Activity activity){ModernMapController map=maps.get(activity);if(map!=null)map.onResume();}
    @Override public void onActivityPaused(Activity activity){ModernMapController map=maps.get(activity);if(map!=null)map.onPause();}
    @Override public void onActivityStopped(Activity activity){ModernMapController map=maps.get(activity);if(map!=null)map.onStop();}
    @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    @Override public void onActivityDestroyed(Activity activity){ModernMapController map=maps.remove(activity);if(map!=null)map.onDestroy();}
    @Override public void onLowMemory(){super.onLowMemory();for(ModernMapController map:maps.values())map.onLowMemory();}
}
