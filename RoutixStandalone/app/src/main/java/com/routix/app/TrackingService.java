package com.routix.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** One GPS subscription; UI listeners exist only while the activity is visible. */
public final class TrackingService extends Service implements LocationListener {
    interface Listener {void changed();}
    final class LocalBinder extends Binder {TrackingService service(){return TrackingService.this;}}
    final List<RouteStore.Point> points=new ArrayList<>();
    final List<RouteStore.Event> events=new ArrayList<>();
    boolean recording,paused,ready,saving;long started;double distance;
    Location location;GuidanceEngine guidance;GuidanceEngine.State guidanceState;RouteStore.Summary route;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SessionJournal journal;private RouteStore store;private SharedPreferences prefs;
    private LocationManager manager;private boolean subscribed,foreground,visible,closed;
    private Listener listener;private TextToSpeech speech;private boolean speechReady;private GuidanceEngine.Event spoken;
    private long lastProgressWriteMs;private float lastProgressWriteM=-1;
    private final java.util.concurrent.atomic.AtomicBoolean draftPending=new java.util.concurrent.atomic.AtomicBoolean();
    private final Runnable draft=new Runnable(){public void run(){if(recording)saveDraft();if(!closed)main.postDelayed(this,15000);}};
    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("routix",MODE_PRIVATE);journal=new SessionJournal(this);store=new RouteStore(this,prefs);manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("tracking","Tournée en cours",NotificationManager.IMPORTANCE_LOW));
        io.execute(()->{try{
            List<RouteStore.Point> p=new ArrayList<>();List<RouteStore.Event> e=new ArrayList<>();journal.read(p,e);
            boolean rec=Boolean.parseBoolean(journal.readState("recording","false")),pause=Boolean.parseBoolean(journal.readState("paused","false"));long start=Long.parseLong(journal.readState("started","0"));
            String file=journal.readState("guidance","");float progress=Float.parseFloat(journal.readState("progress","0"));
            RouteStore.Summary recovered=file.isEmpty()?null:store.parse(new File(store.routesDir(),new File(file).getName()));boolean validGuidance=recovered!=null&&recovered.points.size()>1;
            if(!file.isEmpty()&&!validGuidance){journal.state("guidance","");journal.state("progress","0");DiagnosticLog.info("stale guidance checkpoint cleared");}
            main.post(()->{if(closed)return;points.addAll(p);events.addAll(e);recording=rec;paused=pause;started=start;for(int i=1;i<points.size();i++)distance+=RouteNormalizer.distanceM(points.get(i-1),points.get(i));if(validGuidance)installGuidance(recovered,progress);ready=true;if(recording||guidance!=null){if(visible&&!foreground)activate();else if(visible||foreground)subscribe();}else idle();publish();if(visible&&!subscribed)subscribe();});
        }catch(Exception ex){DiagnosticLog.error("session recovery",ex);main.post(()->{if(closed)return;ready=true;if(foreground&&!recording&&guidance==null)idle();publish();if(visible&&!subscribed)subscribe();});}});
        main.postDelayed(draft,15000);
    }
    @Override public IBinder onBind(Intent intent){return new LocalBinder();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        try{promote();if(ready)subscribe();return START_STICKY;}catch(RuntimeException e){DiagnosticLog.error("foreground start",e);stopSelf();return START_NOT_STICKY;}
    }
    private void promote(){if(foreground)return;Intent open=new Intent(this,RoutixActivity.class);PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new NotificationCompat.Builder(this,"tracking").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Routix • tournée active").setContentText("Suivi GPS actif, même écran éteint. Toucher pour reprendre.").setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(71,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(71,n);foreground=true;
    }
    private boolean activate(){try{ContextCompat.startForegroundService(this,new Intent(this,TrackingService.class));promote();subscribe();return subscribed;}catch(RuntimeException ex){DiagnosticLog.error("tracking activation",ex);return false;}}
    void observe(Listener l){listener=l;visible=l!=null;if(visible){if(recording||guidance!=null)activate();else subscribe();publish();}else if(!recording&&guidance==null)unsubscribe();}
    private void publish(){if(listener!=null)try{listener.changed();}catch(RuntimeException e){DiagnosticLog.error("UI observer",e);}}
    private void write(Runnable task){try{io.execute(()->{try{task.run();}catch(RuntimeException e){DiagnosticLog.error("session persistence",e);if(!closed)main.post(()->android.widget.Toast.makeText(this,"Sauvegarde impossible : vérifie le stockage. Exporte le diagnostic.",android.widget.Toast.LENGTH_LONG).show());}});}catch(RejectedExecutionException e){DiagnosticLog.error("session persistence rejected",e);}}
    private void ensureSpeech(){if(speech!=null||closed||!prefs.getBoolean("voice_markers",true))return;speechReady=false;speech=new TextToSpeech(this,status->{if(closed)return;speechReady=status==TextToSpeech.SUCCESS;if(speechReady&&speech!=null)speech.setLanguage(Locale.FRANCE);});}
    private void releaseSpeech(){speechReady=false;if(speech!=null){try{speech.stop();speech.shutdown();}catch(RuntimeException e){DiagnosticLog.error("speech shutdown",e);}speech=null;}spoken=null;}
    private void persistProgress(float progress,boolean force){long now=SystemClock.elapsedRealtime();if(!force&&now-lastProgressWriteMs<3000&&lastProgressWriteM>=0&&Math.abs(progress-lastProgressWriteM)<10f)return;lastProgressWriteMs=now;lastProgressWriteM=progress;write(()->journal.state("progress",Float.toString(progress)));}
    boolean beginRecording(){if(!ready||saving||recording||!activate())return false;points.clear();events.clear();distance=0;started=System.currentTimeMillis();recording=true;paused=false;long start=started;write(()->{journal.clearRecording();journal.state("started",Long.toString(start));journal.state("recording","true");store.clearDraft();});publish();return true;}
    void recoverDraft(RouteStore.Summary draft){if(!beginRecording())return;points.addAll(draft.points);events.addAll(draft.events);distance=draft.distanceM;started=draft.firstTime;List<RouteStore.Point> p=new ArrayList<>(points);List<RouteStore.Event> e=new ArrayList<>(events);long start=started;write(()->{journal.state("started",Long.toString(start));for(RouteStore.Point x:p)journal.point(x);for(RouteStore.Event x:e)journal.event(x);});publish();}
    void togglePause(){if(!recording||saving)return;paused=!paused;boolean value=paused;write(()->journal.state("paused",Boolean.toString(value)));saveDraft();publish();}
    void addEvent(String type,String label){if(!recording||paused||location==null)return;RouteStore.Event e=new RouteStore.Event(type,label,location.getLatitude(),location.getLongitude(),System.currentTimeMillis(),location.getAccuracy());events.add(e);write(()->journal.event(e));publish();}
    void finish(java.util.function.Consumer<File> result){if(saving||points.size()<2)return;saving=true;boolean wasPaused=paused;paused=true;List<RouteStore.Point> p=new ArrayList<>(points);List<RouteStore.Event> e=new ArrayList<>(events);long start=started;publish();io.execute(()->{File f=null;try{f=store.createRoute(p,e,start);if(f!=null){journal.clearRecording();store.clearDraft();}}catch(RuntimeException ex){DiagnosticLog.error("finish recording",ex);}File saved=f;main.post(()->{saving=false;if(saved!=null){recording=false;paused=false;points.clear();events.clear();distance=0;idle();}else paused=wasPaused;publish();result.accept(saved);});});}
    void beginGuidance(RouteStore.Summary s){if(!ready||!activate())return;installGuidance(s,0);String name=s.file.getName();write(()->{journal.state("progress","0");journal.state("guidance",name);});publish();}
    private void installGuidance(RouteStore.Summary s,float progress){route=s;lastProgressWriteM=progress;lastProgressWriteMs=SystemClock.elapsedRealtime();List<GuidanceEngine.Point> p=new ArrayList<>();for(RouteStore.Point x:s.points)p.add(new GuidanceEngine.Point(x.lat,x.lon));List<GuidanceEngine.Event> e=new ArrayList<>();for(RouteStore.Event x:s.events){int index=-1;if(x.time>0&&x.time>=s.firstTime&&x.time<=s.lastTime){long best=Long.MAX_VALUE;for(int i=0;i<s.points.size();i++){long t=s.points.get(i).time;if(t>0&&Math.abs(t-x.time)<best){best=Math.abs(t-x.time);index=i;}}}e.add(new GuidanceEngine.Event(x.type,x.label,x.lat,x.lon,index));}guidance=new GuidanceEngine(p,e);guidance.restoreProgress(progress);GuidanceEngine.Point restored=guidance.pointAt(progress);guidanceState=guidance.update(restored.lat,restored.lon,0,1000);spoken=null;}
    void addGuidanceEvent(String type,String label){if(route==null||location==null)return;RouteStore.Summary current=route;float progress=guidance.progressM();RouteStore.Event event=new RouteStore.Event(type,label,location.getLatitude(),location.getLongitude(),current.points.get(guidance.currentIndex()).time,location.getAccuracy());current.events.add(event);installGuidance(current,progress);List<RouteStore.Event> copy=new ArrayList<>(current.events);write(()->{if(!store.writeGpx(current.file,current.points,copy,current.firstTime,store.displayName(current.file)))throw new IllegalStateException("Marker save failed");});publish();}
    void stopGuidance(){guidance=null;guidanceState=null;route=null;write(()->{journal.state("guidance","");journal.state("progress","0");});releaseSpeech();idle();publish();}
    boolean reposition(){if(location==null||guidance==null||location.getAccuracy()>35)return false;boolean ok=guidance.reposition(location.getLatitude(),location.getLongitude());if(ok){float progress=guidance.progressM();persistProgress(progress,true);onLocationChanged(location);}return ok;}
    private void idle(){if(!recording&&guidance==null){releaseSpeech();if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();}if(!visible)unsubscribe();}}
    void saveDraft(){if(!recording||saving||!draftPending.compareAndSet(false,true))return;List<RouteStore.Point> p=new ArrayList<>(points);List<RouteStore.Event> e=new ArrayList<>(events);long start=started;write(()->{try{store.saveDraft(p,e,start);}finally{draftPending.set(false);}});}
    private void subscribe(){if(subscribed||(!visible&&!foreground))return;if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;try{manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this,Looper.getMainLooper());subscribed=true;DiagnosticLog.info("GPS subscribed");}catch(RuntimeException e){DiagnosticLog.error("GPS subscribe",e);}}
    private void unsubscribe(){try{if(manager!=null)manager.removeUpdates(this);}catch(RuntimeException e){DiagnosticLog.error("GPS unsubscribe",e);}subscribed=false;DiagnosticLog.info("GPS unsubscribed");}
    @Override public void onLocationChanged(Location fix){
        if(!ready||!valid(fix))return;if(location!=null&&fix.getElapsedRealtimeNanos()<location.getElapsedRealtimeNanos())return;location=new Location(fix);
        try{if(recording&&!paused&&!saving&&fix.getAccuracy()<=prefs.getInt("record_accuracy",45)){
            RouteStore.Point p=new RouteStore.Point(fix.getLatitude(),fix.getLongitude(),fix.getTime(),fix.getAccuracy());double d=points.isEmpty()?0:RouteNormalizer.distanceM(points.get(points.size()-1),p);
            if(points.isEmpty()||d>=prefs.getInt("record_spacing",2)){if(d<250)distance+=d;points.add(p);write(()->journal.point(p));}
        }}catch(RuntimeException e){DiagnosticLog.error("record fix",e);}
        try{if(guidance!=null){guidance.setOffRouteThreshold(prefs.getInt("offroute_m",45));guidanceState=guidance.update(fix.getLatitude(),fix.getLongitude(),fix.getElapsedRealtimeNanos()/1000000,fix.getAccuracy());float progress=guidance.progressM();persistProgress(progress,false);GuidanceEngine.Event next=guidanceState.nextEvent;
            if(next!=null&&next!=spoken&&!guidanceState.poorAccuracy&&!guidanceState.offRoute&&guidanceState.distanceToNextEventM<55&&prefs.getBoolean("voice_markers",true)){ensureSpeech();if(speechReady&&speech!=null){speech.speak(next.label+" dans "+Math.round(guidanceState.distanceToNextEventM)+" mètres",TextToSpeech.QUEUE_FLUSH,null,"marker");spoken=next;}}
        }}catch(RuntimeException e){DiagnosticLog.error("guidance fix",e);}publish();
    }
    static boolean valid(Location l){return l!=null&&Double.isFinite(l.getLatitude())&&Double.isFinite(l.getLongitude())&&Math.abs(l.getLatitude())<=90&&Math.abs(l.getLongitude())<=180&&l.hasAccuracy()&&Float.isFinite(l.getAccuracy())&&l.getAccuracy()>=0;}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public void onProviderDisabled(String provider){DiagnosticLog.info("GPS disabled");publish();}
    @Override public void onProviderEnabled(String provider){DiagnosticLog.info("GPS enabled");publish();}
    @Override public void onTaskRemoved(Intent rootIntent){if(recording)saveDraft();if(guidance!=null)persistProgress(guidance.progressM(),true);DiagnosticLog.info("task removed while tracking="+(recording||guidance!=null));super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){if(recording)saveDraft();if(guidance!=null)persistProgress(guidance.progressM(),true);closed=true;listener=null;main.removeCallbacksAndMessages(null);unsubscribe();releaseSpeech();try{io.execute(journal::close);}catch(RejectedExecutionException e){DiagnosticLog.error("journal close rejected",e);}io.shutdown();super.onDestroy();}
}
