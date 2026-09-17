package com.routix.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class RoutixApp extends Application implements Application.ActivityLifecycleCallbacks {
    private Activity activeActivity;
    private SpeedometerView speedometer;
    private LinearLayout customDock;
    private LocationManager locationManager;
    private LocationListener speedListener;
    private ModernMapController modernMap;
    private final Handler uiHandler=new Handler(Looper.getMainLooper());

    @Override public void onCreate(){
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
    }

    @Override public void onActivityResumed(Activity activity){
        if(!(activity instanceof RoutixActivity))return;
        activeActivity=activity;
        attachSpeedometer(activity);
        attachFiveTabDock(activity);
        if(modernMap==null){modernMap=new ModernMapController(activity);modernMap.onStart();}
        modernMap.onResume();
        installMapSourceSelector(activity);
        startSpeedUpdates();
        uiHandler.post(syncChrome);
    }

    @Override public void onActivityPaused(Activity activity){
        if(activity!=activeActivity)return;
        stopSpeedUpdates();
        if(modernMap!=null)modernMap.onPause();
        activeActivity=null;
        uiHandler.removeCallbacks(syncChrome);
    }

    private void attachSpeedometer(Activity activity){
        if(speedometer!=null&&speedometer.getParent()!=null)return;
        speedometer=new SpeedometerView(activity);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(activity,84),dp(activity,84),Gravity.END|Gravity.BOTTOM);
        lp.setMargins(0,0,dp(activity,18),dp(activity,102));
        activity.addContentView(speedometer,lp);
        speedometer.setElevation(dp(activity,10));
    }

    private void attachFiveTabDock(Activity activity){
        if(customDock!=null&&customDock.getParent()!=null)return;
        customDock=new LinearLayout(activity);
        customDock.setGravity(Gravity.CENTER);
        customDock.setPadding(dp(activity,7),dp(activity,6),dp(activity,7),dp(activity,6));
        customDock.setBackground(glass(activity,Color.argb(244,22,25,33),28));
        customDock.setElevation(dp(activity,22));

        customDock.addView(tab(activity,"⌖","Carte",()->invoke(activity,"showMap")),weight(activity));
        customDock.addView(tab(activity,"≡","Tournées",()->invoke(activity,"showHistory")),weight(activity));
        customDock.addView(tab(activity,"⌾","GPS",()->activity.startActivity(new Intent(activity,ClassicNavigationActivity.class))),weight(activity));
        customDock.addView(tab(activity,"◷","Heures",()->invoke(activity,"showHours")),weight(activity));
        customDock.addView(tab(activity,"⚙","Réglages",()->invoke(activity,"showSettings")),weight(activity));

        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(activity,70),Gravity.BOTTOM);
        lp.setMargins(dp(activity,22),0,dp(activity,22),dp(activity,14));
        activity.addContentView(customDock,lp);
    }

    private TextView tab(Activity a,String icon,String label,Runnable action){
        TextView v=new TextView(a);
        v.setText(icon+"\n"+label);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.argb(220,255,255,255));
        v.setTextSize(11);
        v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        v.setBackground(glass(a,Color.argb(0,0,0,0),20));
        v.setOnClickListener(x->{x.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);action.run();});
        return v;
    }

    private LinearLayout.LayoutParams weight(Activity a){return new LinearLayout.LayoutParams(0,dp(a,58),1f);}

    private void installMapSourceSelector(Activity activity){
        TextView chip=(TextView)getField(activity,"mapChip");
        if(chip==null)return;
        chip.setOnClickListener(v->showMapSelector(activity));
        if(modernMap!=null&&modernMap.isActive()){chip.setText("MODERN");chip.setTextColor(Color.rgb(100,210,255));}
    }

    private void showMapSelector(Activity activity){
        String[] items={"Carte moderne • MapLibre","OpenStreetMap classique","Cartes hors ligne…"};
        new AlertDialog.Builder(activity)
                .setTitle("Source de carte")
                .setItems(items,(d,which)->{
                    if(which==0){if(modernMap!=null)modernMap.setActive(true);}
                    else if(which==1){if(modernMap!=null)modernMap.setActive(false);invoke(activity,"activateOnline");}
                    else {if(modernMap!=null)modernMap.setActive(false);invoke(activity,"mapSourceMenu");}
                })
                .setNegativeButton("Fermer",null)
                .show();
    }

    private final Runnable syncChrome=new Runnable(){@Override public void run(){
        Activity a=activeActivity;
        if(a==null)return;
        View originalDock=(View)getField(a,"dock");
        if(customDock!=null&&originalDock!=null){
            customDock.setVisibility(originalDock.getVisibility());
            customDock.bringToFront();
        }
        if(speedometer!=null){
            boolean visible=originalDock==null||originalDock.getVisibility()==View.VISIBLE;
            speedometer.setVisibility(visible?View.VISIBLE:View.GONE);
            if(visible)speedometer.bringToFront();
            if(customDock!=null)customDock.bringToFront();
        }
        uiHandler.postDelayed(this,350);
    }};

    private void startSpeedUpdates(){
        if(activeActivity==null||locationManager==null)return;
        if(ContextCompat.checkSelfPermission(activeActivity,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&ContextCompat.checkSelfPermission(activeActivity,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        speedListener=location->{
            if(speedometer==null||location==null)return;
            float kmh=location.hasSpeed()?Math.max(0f,location.getSpeed()*3.6f):0f;
            float accuracy=location.hasAccuracy()?location.getAccuracy():99f;
            speedometer.setSpeed(kmh,accuracy);
        };
        try{
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,500L,0f,speedListener);
            Location last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(last!=null)speedListener.onLocationChanged(last);
        }catch(SecurityException ignored){}
    }

    private void stopSpeedUpdates(){
        if(locationManager!=null&&speedListener!=null)try{locationManager.removeUpdates(speedListener);}catch(SecurityException ignored){}
        speedListener=null;
    }

    private static Object getField(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){return null;}}
    private static void invoke(Object target,String name){try{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}catch(Exception ignored){}}

    private static GradientDrawable glass(Activity a,int color,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(Math.min(255,Color.alpha(color)+8),Math.min(255,Color.red(color)+8),Math.min(255,Color.green(color)+8),Math.min(255,Color.blue(color)+8)),color});
        g.setCornerRadius(dp(a,radius));g.setStroke(dp(a,1),Color.argb(65,255,255,255));return g;
    }

    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}

    @Override public void onActivityCreated(Activity a,Bundle b){}
    @Override public void onActivityStarted(Activity a){if(a==activeActivity&&modernMap!=null)modernMap.onStart();}
    @Override public void onActivityStopped(Activity a){if(a==activeActivity&&modernMap!=null)modernMap.onStop();}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    @Override public void onActivityDestroyed(Activity a){
        if(a==activeActivity||a instanceof RoutixActivity){
            stopSpeedUpdates();uiHandler.removeCallbacks(syncChrome);
            if(modernMap!=null)modernMap.onDestroy();
            modernMap=null;activeActivity=null;speedometer=null;customDock=null;
        }
    }

    private static final class SpeedometerView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc=new RectF();
        private float displayedSpeed,accuracyM=99f;
        private ValueAnimator animator;

        SpeedometerView(Activity activity){super(activity);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}

        void setSpeed(float kmh,float accuracy){
            float target=Math.min(199f,Math.max(0f,kmh));accuracyM=accuracy;
            if(animator!=null)animator.cancel();
            animator=ValueAnimator.ofFloat(displayedSpeed,target);animator.setDuration(210);animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a->{displayedSpeed=(float)a.getAnimatedValue();invalidate();});animator.start();
        }

        private int d(float v){return Math.round(v*getResources().getDisplayMetrics().density);}

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;
            p.setStyle(Paint.Style.FILL);p.setShadowLayer(d(9),0,d(3),Color.argb(125,0,0,0));p.setColor(Color.argb(236,13,16,23));c.drawRoundRect(d(1),d(1),w-d(1),h-d(1),d(23),d(23),p);p.clearShadowLayer();
            LinearGradient glass=new LinearGradient(0,0,w,h,new int[]{Color.argb(82,255,255,255),Color.argb(14,255,255,255),Color.argb(48,10,132,255)},null,Shader.TileMode.CLAMP);p.setShader(glass);c.drawRoundRect(d(2),d(2),w-d(2),h-d(2),d(22),d(22),p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(d(4));arc.set(d(11),d(11),w-d(11),h-d(11));p.setColor(Color.argb(42,255,255,255));c.drawArc(arc,145f,250f,false,p);
            int accent=displayedSpeed>=80f?Color.rgb(255,69,58):displayedSpeed>=50f?Color.rgb(255,159,10):Color.rgb(100,210,255);p.setColor(accent);c.drawArc(arc,145f,250f*Math.min(1f,displayedSpeed/90f),false,p);
            text.setTextAlign(Paint.Align.CENTER);text.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));text.setColor(Color.WHITE);text.setTextSize(d(displayedSpeed>=100?22:25));Paint.FontMetrics fm=text.getFontMetrics();float base=cy-(fm.ascent+fm.descent)/2f-d(4);c.drawText(String.valueOf(Math.round(displayedSpeed)),cx,base,text);
            text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));text.setTextSize(d(7));text.setColor(Color.argb(190,255,255,255));c.drawText("km/h",cx,cy+d(18),text);
            boolean good=accuracyM<=20f;p.setStyle(Paint.Style.FILL);p.setColor(good?Color.argb(55,48,209,88):Color.argb(48,255,159,10));c.drawRoundRect(cx-d(17),h-d(14),cx+d(17),h-d(5),d(5),d(5),p);text.setTextSize(d(5.5f));text.setColor(good?Color.rgb(93,230,126):Color.rgb(255,184,71));c.drawText(good?"GPS OK":"GPS…",cx,h-d(7),text);
        }
    }
}
