package com.routix.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;

/** A header-owned speed display, never a floating window over map controls. */
final class CompactSpeedometer extends View {
    private final CatppuccinTheme.Tokens theme;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float speed;
    private long fixNanos;
    private boolean valid;
    private Location previousFix;
    private final Runnable expire=()->{valid=false;setContentDescription("Vitesse indisponible, GPS en attente");invalidate();};
    CompactSpeedometer(Context context){super(context);theme=CatppuccinTheme.from(context);setContentDescription("Vitesse indisponible, GPS en attente");}
    void update(Location location){
        removeCallbacks(expire);
        if(location==null){expire.run();return;}
        long now=SystemClock.elapsedRealtimeNanos(),age=now-location.getElapsedRealtimeNanos();
        boolean fresh=location.getElapsedRealtimeNanos()>0&&age>=0&&age<5000000000L;
        boolean precise=location.hasAccuracy()&&location.getAccuracy()<=35;
        float metresPerSecond=Float.NaN;
        if(fresh&&precise&&location.hasSpeed()&&Float.isFinite(location.getSpeed())&&location.getSpeed()>=0)metresPerSecond=location.getSpeed();
        else if(fresh&&precise&&previousFix!=null&&previousFix.hasAccuracy()&&previousFix.getAccuracy()<=35){
            long dt=location.getElapsedRealtimeNanos()-previousFix.getElapsedRealtimeNanos();
            if(dt>=500000000L&&dt<=10000000000L){
                float d=previousFix.distanceTo(location);
                float candidate=d/(dt/1000000000f);
                if(Float.isFinite(candidate)&&candidate>=0&&candidate<=70)metresPerSecond=candidate;
            }
        }
        valid=Float.isFinite(metresPerSecond);
        speed=valid?Math.max(0,metresPerSecond*3.6f):0;
        fixNanos=location.getElapsedRealtimeNanos();
        if(fresh&&precise)previousFix=new Location(location);
        setContentDescription(valid?Math.round(speed)+" kilomètres par heure":"Vitesse indisponible, GPS en attente");
        invalidate();if(valid)postDelayed(expire,Math.max(1,5000-age/1000000));
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(valid){long remaining=5000-(SystemClock.elapsedRealtimeNanos()-fixNanos)/1000000;if(remaining<=0)expire.run();else postDelayed(expire,remaining);}}
    @Override protected void onDetachedFromWindow(){removeCallbacks(expire);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float scale=Math.min(getWidth()/112f,getHeight()/58f);canvas.save();canvas.translate(0,(getHeight()-58*scale)/2);canvas.scale(scale,scale);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(3);paint.setColor(theme.surface1);
        RectF arc=new RectF(3,4,53,54);canvas.drawArc(arc,140,260,false,paint);paint.setColor(theme.sapphire);canvas.drawArc(arc,140,valid?260*Math.min(speed/100,1):0,false,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(theme.text);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(valid&&speed>=100?22:27);
        canvas.drawText(valid?String.valueOf(Math.round(speed)):"—",28,37,paint);
        paint.setTextAlign(Paint.Align.LEFT);paint.setTextSize(12);paint.setColor(theme.text);canvas.drawText("km/h",63,27,paint);
        paint.setTextSize(9);paint.setColor(valid?theme.sapphire:theme.muted());canvas.drawText(valid?"EN DIRECT":"GPS…",63,43,paint);canvas.restore();
    }
}
