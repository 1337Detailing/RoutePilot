package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class RouteStore {
    static final class Point {
        final double lat, lon;
        final long time;
        final float accuracy;
        Point(double lat, double lon, long time, float accuracy) { this.lat=lat; this.lon=lon; this.time=time; this.accuracy=accuracy; }
    }

    static final class Event {
        final String type, label;
        final double lat, lon;
        final long time;
        final float accuracy;
        Event(String type, String label, double lat, double lon, long time, float accuracy) {
            this.type=type; this.label=label; this.lat=lat; this.lon=lon; this.time=time; this.accuracy=accuracy;
        }
    }

    static final class Summary {
        final File file;
        final List<Point> points = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        long firstTime, lastTime, durationMs;
        double distanceM;
        Summary(File file) { this.file=file; }
    }

    private final Context context;
    private final SharedPreferences prefs;

    RouteStore(Context context, SharedPreferences prefs) {
        this.context=context.getApplicationContext();
        this.prefs=prefs;
    }

    File routesDir() {
        File d = new File(context.getFilesDir(), "routes");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File originalsDir() {
        File d=new File(context.getFilesDir(),"route_originals");
        if(!d.exists())d.mkdirs();
        return d;
    }

    File draftFile() { return new File(context.getFilesDir(), "routix_draft.gpx"); }
    boolean hasDraft() { return prefs.getBoolean("draft_active", false) && draftFile().exists() && draftFile().length() > 256; }
    Summary draftSummary() { return parse(draftFile()); }

    void saveDraft(List<Point> points, List<Event> events, long startedAt) {
        if (points.isEmpty() && events.isEmpty()) return;
        if (writeGpx(draftFile(), points, events, startedAt, "Tournée interrompue"))
            prefs.edit().putBoolean("draft_active", true).putLong("draft_started_at", startedAt).apply();
    }

    void clearDraft() {
        File f=draftFile(); if (f.exists()) f.delete();
        prefs.edit().remove("draft_active").remove("draft_started_at").apply();
    }

    File createRoute(List<Point> points, List<Event> events, long startedAt) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE).format(new Date(startedAt));
        File out = new File(routesDir(), "Routix_" + stamp + ".gpx");
        int n=2; while (out.exists()) out = new File(routesDir(), "Routix_"+stamp+"_"+(n++)+".gpx");
        return writeGpx(out, points, events, startedAt, "Tournée " + stamp) ? out : null;
    }

    boolean writeGpx(File out, List<Point> points, List<Event> events, long startedAt, String title) {
        try {
            StringBuilder x=new StringBuilder();
            x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            x.append("<gpx version=\"1.1\" creator=\"Routix 1.4\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:rp=\"https://routix.local/gpx/1\">\n");
            x.append("<metadata><name>").append(escape(title)).append("</name><time>").append(iso(startedAt)).append("</time></metadata>\n");
            for (Event e:events) {
                x.append("<wpt lat=\"").append(e.lat).append("\" lon=\"").append(e.lon).append("\">");
                x.append("<time>").append(iso(e.time)).append("</time><name>").append(escape(e.label)).append("</name><type>Routix</type>");
                x.append("<extensions><rp:event>").append(escape(e.type)).append("</rp:event><rp:accuracy>").append(e.accuracy).append("</rp:accuracy></extensions></wpt>\n");
            }
            x.append("<trk><name>").append(escape(title)).append("</name><trkseg>\n");
            for (Point p:points) {
                x.append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">");
                x.append("<time>").append(iso(p.time)).append("</time><extensions><rp:accuracy>").append(p.accuracy).append("</rp:accuracy></extensions></trkpt>\n");
            }
            x.append("</trkseg></trk></gpx>\n");
            try(FileOutputStream o=new FileOutputStream(out)) { o.write(x.toString().getBytes(StandardCharsets.UTF_8)); }
            return true;
        } catch(Exception e) { return false; }
    }

    List<File> routeFiles() {
        File[] a=routesDir().listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".gpx"));
        if(a==null)return new ArrayList<>();
        List<File> l=new ArrayList<>(Arrays.asList(a));
        Collections.sort(l,(x,y)->Long.compare(y.lastModified(),x.lastModified()));
        return l;
    }

    List<Summary> summaries() {
        List<Summary> out=new ArrayList<>(); for(File f:routeFiles()) out.add(parse(f)); return out;
    }

    Summary parse(File f) {
        Summary s=new Summary(f);
        if (f==null || !f.exists()) return s;
        try(FileInputStream in=new FileInputStream(f)) {
            XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(in,"UTF-8");
            int ev=p.getEventType(); boolean w=false,t=false; String pointTag=null;
            double lat=0,lon=0; String label="Repère",type="EVENT"; long wt=0,tt=0; float wa=0,ta=0;
            while(ev!=XmlPullParser.END_DOCUMENT) {
                if(ev==XmlPullParser.START_TAG) {
                    String n=p.getName();
                    if("wpt".equals(n)) { w=true; lat=dbl(p.getAttributeValue(null,"lat")); lon=dbl(p.getAttributeValue(null,"lon")); label="Repère"; type="EVENT"; wt=0; wa=0; }
                    else if("trkpt".equals(n)||"rtept".equals(n)) { t=true; pointTag=n; lat=dbl(p.getAttributeValue(null,"lat")); lon=dbl(p.getAttributeValue(null,"lon")); tt=0; ta=0; }
                    else if("name".equals(n)&&w) label=p.nextText();
                    else if("time".equals(n)) { long z=time(p.nextText()); if(w)wt=z; else if(t)tt=z; }
                    else if("event".equals(n)&&w) type=p.nextText();
                    else if("accuracy".equals(n)) { float a=flt(p.nextText()); if(w)wa=a; else if(t)ta=a; }
                } else if(ev==XmlPullParser.END_TAG) {
                    String n=p.getName();
                    if("wpt".equals(n)) { s.events.add(new Event(type,label,lat,lon,wt,wa)); w=false; }
                    else if(t && n.equals(pointTag)) { s.points.add(new Point(lat,lon,tt,ta)); if(tt>0){if(s.firstTime==0)s.firstTime=tt;s.lastTime=tt;} t=false; pointTag=null; }
                }
                ev=p.next();
            }
        } catch(Exception ignored) {}
        for(int i=1;i<s.points.size();i++) {
            Point a=s.points.get(i-1),b=s.points.get(i); float[] d=new float[1];
            Location.distanceBetween(a.lat,a.lon,b.lat,b.lon,d); if(d[0]<500)s.distanceM+=d[0];
        }
        s.durationMs=s.lastTime>s.firstTime?s.lastTime-s.firstTime:0;
        return s;
    }

    File importGpx(Uri uri) {
        if(uri==null)return null;
        long stamp=System.currentTimeMillis();
        File original=new File(originalsDir(),"Original_"+stamp+".gpx");
        File out=new File(routesDir(),"Import_"+stamp+".gpx");
        try(InputStream in=context.getContentResolver().openInputStream(uri); FileOutputStream os=new FileOutputStream(original)) {
            if(in==null)return null;
            byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)os.write(b,0,n);
        } catch(Exception e){ if(original.exists())original.delete(); return null; }

        Summary raw=parse(original);
        RouteNormalizer.Result clean=RouteNormalizer.normalize(raw.points);
        if(clean.points.size()<2){ original.delete(); return null; }
        long started=raw.firstTime>0?raw.firstTime:stamp;
        if(!writeGpx(out,clean.points,raw.events,started,"Import optimisé Routix")) { original.delete(); return null; }
        prefs.edit()
                .putString("original_"+out.getName(),original.getAbsolutePath())
                .putInt("import_input_"+out.getName(),clean.inputCount)
                .putInt("import_output_"+out.getName(),clean.points.size())
                .putInt("import_invalid_"+out.getName(),clean.invalidRemoved)
                .putInt("import_duplicates_"+out.getName(),clean.duplicateRemoved)
                .putInt("import_simplified_"+out.getName(),clean.simplifiedRemoved)
                .apply();
        return out;
    }

    File originalFor(File f) {
        if(f==null)return null;
        String path=prefs.getString("original_"+f.getName(),null);
        if(path==null)return null;
        File original=new File(path); return original.exists()?original:null;
    }

    String displayName(File f) {
        String n=prefs.getString("route_name_"+f.getName(),null);
        if(n!=null&&!n.trim().isEmpty())return n;
        return f.getName().replace("Routix_","Tournée ").replace("Import_","Import ").replace(".gpx","").replace('_',' ');
    }

    void rename(File f,String name) { if(f!=null&&name!=null&&!name.trim().isEmpty()) prefs.edit().putString("route_name_"+f.getName(),name.trim()).apply(); }

    boolean delete(File f) {
        if(f==null)return false;
        File original=originalFor(f); if(original!=null)original.delete();
        prefs.edit().remove("route_name_"+f.getName()).remove("favorite_"+f.getName()).remove("original_"+f.getName())
                .remove("import_input_"+f.getName()).remove("import_output_"+f.getName()).remove("import_invalid_"+f.getName())
                .remove("import_duplicates_"+f.getName()).remove("import_simplified_"+f.getName()).apply();
        return f.delete();
    }

    File duplicate(File f) {
        if(f==null||!f.exists())return null;
        String base=f.getName().replace(".gpx",""); File out=new File(routesDir(),base+"_copie.gpx"); int i=2;
        while(out.exists())out=new File(routesDir(),base+"_copie_"+(i++)+".gpx");
        try(FileInputStream in=new FileInputStream(f);FileOutputStream os=new FileOutputStream(out)) {
            byte[] b=new byte[8192];int n;while((n=in.read(b))>0)os.write(b,0,n);
            rename(out,displayName(f)+" • copie"); return out;
        }catch(Exception e){return null;}
    }

    boolean isFavorite(File f){return f!=null&&prefs.getBoolean("favorite_"+f.getName(),false);}
    void setFavorite(File f,boolean value){if(f!=null)prefs.edit().putBoolean("favorite_"+f.getName(),value).apply();}

    static String iso(long ms){return Instant.ofEpochMilli(Math.max(0,ms)).toString();}
    static long time(String s){try{return Instant.parse(s).toEpochMilli();}catch(DateTimeParseException e){return 0;}}
    static double dbl(String s){try{return Double.parseDouble(s);}catch(Exception e){return 0;}}
    static float flt(String s){try{return Float.parseFloat(s);}catch(Exception e){return 0;}}
    static String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
