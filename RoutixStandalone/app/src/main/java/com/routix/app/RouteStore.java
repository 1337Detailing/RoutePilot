package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

final class RouteStore {
    static final class Point { final double lat,lon; final long time; final float accuracy; Point(double a,double o,long t,float c){lat=a;lon=o;time=t;accuracy=c;} }
    static final class Event { final String type,label; final double lat,lon; final long time; final float accuracy; Event(String y,String l,double a,double o,long t,float c){type=y;label=l;lat=a;lon=o;time=t;accuracy=c;} }
    static final class Summary { final File file; final List<Point> points=new ArrayList<>(); final List<Event> events=new ArrayList<>(); long firstTime,lastTime,durationMs; double distanceM; Summary(File f){file=f;} }

    private final Context context; private final SharedPreferences prefs; final RouteArchive archive; private final PersistentRouteBackup backup;
    RouteStore(Context c,SharedPreferences p){context=c.getApplicationContext();prefs=p;archive=new RouteArchive(c);backup=new PersistentRouteBackup(c);backup.restoreInto(routesDir());}

    File routesDir(){File d=new File(context.getFilesDir(),"routes");if(!d.exists())d.mkdirs();return d;}
    private File originalsDir(){File d=new File(context.getFilesDir(),"route_originals");if(!d.exists())d.mkdirs();return d;}
    File draftFile(){return new File(context.getFilesDir(),"routix_draft.gpx");}
    boolean hasDraft(){return prefs.getBoolean("draft_active",false)&&draftFile().exists()&&draftFile().length()>256;}
    Summary draftSummary(){return parse(draftFile());}
    void saveDraft(List<Point> p,List<Event> e,long start){if((p==null||p.isEmpty())&&(e==null||e.isEmpty()))return;if(writeGpx(draftFile(),copyP(p),copyE(e),start,"Tournée interrompue"))prefs.edit().putBoolean("draft_active",true).putLong("draft_started_at",start).apply();}
    void clearDraft(){draftFile().delete();prefs.edit().remove("draft_active").remove("draft_started_at").apply();}

    File createRoute(List<Point> points,List<Event> events,long startedAt){
        RouteNormalizer.Result clean=RouteNormalizer.normalize(copyP(points));if(clean.points.size()<2)return null;
        List<Point> p=clean.points;List<Event> e=copyE(events);
        String stamp=new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.FRANCE).format(new Date(startedAt));File out=new File(routesDir(),"Routix_"+stamp+".gpx");int n=2;while(out.exists())out=new File(routesDir(),"Routix_"+stamp+"_"+(n++)+".gpx");
        if(!writeGpx(out,p,e,startedAt,"Tournée "+stamp))return null;
        prefs.edit().putInt("import_input_"+out.getName(),clean.inputCount).putInt("import_output_"+out.getName(),p.size()).putInt("import_invalid_"+out.getName(),clean.invalidRemoved).putInt("import_duplicates_"+out.getName(),clean.duplicateRemoved).putInt("import_simplified_"+out.getName(),clean.simplifiedRemoved).putBoolean("import_matched_"+out.getName(),false).putInt("import_confidence_"+out.getName(),100).putInt("import_generated_steps_"+out.getName(),0).apply();
        return out;
    }

    boolean writeGpx(File out,List<Point> points,List<Event> events,long startedAt,String title){
        try{StringBuilder x=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Routix 2.0\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:rp=\"https://routix.local/gpx/1\">\n");
            x.append("<metadata><name>").append(escape(title)).append("</name><time>").append(iso(startedAt)).append("</time></metadata>\n");
            for(Event e:copyE(events)){x.append("<wpt lat=\"").append(e.lat).append("\" lon=\"").append(e.lon).append("\">");if(e.time>0)x.append("<time>").append(iso(e.time)).append("</time>");x.append("<name>").append(escape(e.label)).append("</name><type>Routix</type><extensions><rp:event>").append(escape(e.type)).append("</rp:event><rp:accuracy>").append(e.accuracy).append("</rp:accuracy></extensions></wpt>\n");}
            x.append("<trk><name>").append(escape(title)).append("</name><trkseg>\n");for(Point p:copyP(points)){x.append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">");if(p.time>0)x.append("<time>").append(iso(p.time)).append("</time>");x.append("<extensions><rp:accuracy>").append(p.accuracy).append("</rp:accuracy></extensions></trkpt>\n");}x.append("</trkseg></trk></gpx>\n");
            boolean route=out.getParentFile()!=null&&out.getParentFile().equals(routesDir());if(route&&out.exists())archive.capture(out,displayName(out),"Avant modification du tracé");RouteArchive.atomic(out,x.toString().getBytes(StandardCharsets.UTF_8));if(route)backup.publish(out);return true;
        }catch(Exception ex){return false;}
    }

    List<File> routeFiles(){File[] a=routesDir().listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".gpx"));if(a==null)return new ArrayList<>();List<File> l=new ArrayList<>(Arrays.asList(a));l.sort((x,y)->Long.compare(y.lastModified(),x.lastModified()));return l;}
    List<Summary> summaries(){List<Summary> r=new ArrayList<>();for(File f:routeFiles())r.add(parse(f));return r;}

    Summary parse(File f){
        Summary s=new Summary(f);if(f==null||!f.exists())return s;
        try(FileInputStream in=new FileInputStream(f)){XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(in,"UTF-8");int ev=p.getEventType();boolean w=false,t=false;String pointTag=null;double lat=0,lon=0;String label="Repère",type="EVENT";long wt=0,tt=0;float wa=0,ta=0;
            while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG){String n=p.getName();if("wpt".equals(n)){w=true;lat=dbl(p.getAttributeValue(null,"lat"));lon=dbl(p.getAttributeValue(null,"lon"));label="Repère";type="EVENT";wt=0;wa=0;}else if("trkpt".equals(n)||"rtept".equals(n)){t=true;pointTag=n;lat=dbl(p.getAttributeValue(null,"lat"));lon=dbl(p.getAttributeValue(null,"lon"));tt=0;ta=0;}else if("name".equals(n)&&w)label=p.nextText();else if("time".equals(n)){long z=time(p.nextText());if(w)wt=z;else if(t)tt=z;}else if("event".equals(n)&&w)type=p.nextText();else if("accuracy".equals(n)){float a=flt(p.nextText());if(w)wa=a;else if(t)ta=a;}}else if(ev==XmlPullParser.END_TAG){String n=p.getName();if("wpt".equals(n)){s.events.add(new Event(type,label,lat,lon,wt,wa));w=false;}else if(t&&n.equals(pointTag)){s.points.add(new Point(lat,lon,tt,ta));if(tt>0){if(s.firstTime==0)s.firstTime=tt;s.lastTime=tt;}t=false;pointTag=null;}}ev=p.next();}}
        catch(Exception ignored){}
        for(int i=1;i<s.points.size();i++){Point a=s.points.get(i-1),b=s.points.get(i);float[] d=new float[1];Location.distanceBetween(a.lat,a.lon,b.lat,b.lon,d);if(d[0]<500)s.distanceM+=d[0];}s.durationMs=s.lastTime>s.firstTime?s.lastTime-s.firstTime:0;return s;
    }

    File importGpx(Uri uri){
        if(uri==null)return null;long stamp=System.currentTimeMillis();File original=new File(originalsDir(),"Original_"+stamp+".gpx"),out=new File(routesDir(),"Import_"+stamp+".gpx");
        try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream os=new FileOutputStream(original)){if(in==null)return null;byte[] b=new byte[8192];int n;while((n=in.read(b))>0)os.write(b,0,n);}catch(Exception ex){original.delete();return null;}
        Summary raw=parse(original);RouteNormalizer.Result clean=RouteNormalizer.normalize(raw.points);if(clean.points.size()<2){original.delete();return null;}
        boolean matchRoads=prefs.getBoolean("gpx_match_roads",true);RouteMatcher.Result match=matchRoads?RouteMatcher.matchBlocking(clean.points,raw.events):new RouteMatcher.Result(clean.points,raw.events,false,0);
        List<Point> p=match.matched?match.points:clean.points;List<Event> e=match.matched?match.events:raw.events;long start=raw.firstTime>0?raw.firstTime:stamp;String title=match.matched?"Import Routix • routes reconnues":"Import optimisé Routix";
        try{archive.captureAs(out,original,title,"Fichier importé original");}catch(Exception ex){original.delete();return null;}if(!writeGpx(out,p,e,start,title)){original.delete();return null;}prefs.edit().putString("original_"+out.getName(),original.getAbsolutePath()).apply();putImportStats(out,clean,match,p.size(),Math.max(0,e.size()-raw.events.size()));return out;
    }
    private void putImportStats(File out,RouteNormalizer.Result c,RouteMatcher.Result m,int output,int generated){prefs.edit().putInt("import_input_"+out.getName(),c.inputCount).putInt("import_output_"+out.getName(),output).putInt("import_invalid_"+out.getName(),c.invalidRemoved).putInt("import_duplicates_"+out.getName(),c.duplicateRemoved).putInt("import_simplified_"+out.getName(),c.simplifiedRemoved).putBoolean("import_matched_"+out.getName(),m.matched).putInt("import_confidence_"+out.getName(),(int)Math.round(m.confidence*100)).putInt("import_generated_steps_"+out.getName(),Math.max(0,generated)).apply();}

    File originalFor(File f){if(f==null)return null;String p=prefs.getString("original_"+f.getName(),null);if(p==null)return null;File o=new File(p);return o.exists()?o:null;}
    String displayName(File f){if(f==null)return "Tournée";String n=prefs.getString("route_name_"+f.getName(),null);if(n!=null&&!n.trim().isEmpty())return n;return f.getName().replace("Routix_","Tournée ").replace("Import_","Import ").replace(".gpx","").replace('_',' ');}
    void rename(File f,String name){if(f==null||name==null||name.trim().isEmpty())return;try{archive.capture(f,displayName(f),"Avant renommage");}catch(Exception ex){return;}prefs.edit().putString("route_name_"+f.getName(),name.trim()).apply();backup.publish(f);}
    boolean restore(File route,RouteArchive.Version v){try{byte[] c=archive.content(v);File check=new File(context.getCacheDir(),"restore-check.gpx");RouteArchive.atomic(check,c);if(parse(check).points.size()<2){check.delete();return false;}check.delete();archive.capture(route,displayName(route),"Avant restauration");RouteArchive.atomic(route,c);prefs.edit().putString("route_name_"+route.getName(),v.name).apply();backup.publish(route);return true;}catch(Exception ex){return false;}}
    boolean delete(File f){if(f==null)return false;File o=originalFor(f);if(o!=null)o.delete();prefs.edit().remove("route_name_"+f.getName()).remove("favorite_"+f.getName()).remove("original_"+f.getName()).apply();return f.delete();}
    File duplicate(File f){if(f==null||!f.exists())return null;String base=f.getName().replace(".gpx","");File out=new File(routesDir(),base+"_copie.gpx");int i=2;while(out.exists())out=new File(routesDir(),base+"_copie_"+(i++)+".gpx");try(FileInputStream in=new FileInputStream(f);FileOutputStream os=new FileOutputStream(out)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)os.write(b,0,n);prefs.edit().putString("route_name_"+out.getName(),displayName(f)+" • copie").apply();backup.publish(out);return out;}catch(Exception ex){return null;}}
    boolean isFavorite(File f){return f!=null&&prefs.getBoolean("favorite_"+f.getName(),false);} void setFavorite(File f,boolean v){if(f!=null)prefs.edit().putBoolean("favorite_"+f.getName(),v).apply();}

    private static List<Point> copyP(List<Point> p){return p==null?new ArrayList<>():new ArrayList<>(p);}private static List<Event> copyE(List<Event> e){return e==null?new ArrayList<>():new ArrayList<>(e);}
    static String iso(long ms){return Instant.ofEpochMilli(Math.max(0,ms)).toString();}static long time(String s){try{return Instant.parse(s).toEpochMilli();}catch(DateTimeParseException ex){return 0;}}static double dbl(String s){try{return Double.parseDouble(s);}catch(Exception ex){return 0;}}static float flt(String s){try{return Float.parseFloat(s);}catch(Exception ex){return 0;}}static String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
