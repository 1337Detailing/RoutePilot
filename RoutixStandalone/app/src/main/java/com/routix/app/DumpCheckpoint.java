package com.routix.app;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;

/** A detour is separate from route progress, including after process death. */
final class DumpCheckpoint {
    final String file,hash;final boolean reverse,returning;final float progress;final double lat,lon;
    DumpCheckpoint(String file,String hash,boolean reverse,float progress,double lat,double lon,boolean returning){this.file=file;this.hash=hash;this.reverse=reverse;this.progress=progress;this.lat=lat;this.lon=lon;this.returning=returning;}
    boolean save(SharedPreferences prefs){try{return prefs.edit().putString("dump_checkpoint",new JSONObject().put("file",file).put("hash",hash).put("reverse",reverse).put("progress",progress).put("lat",lat).put("lon",lon).put("returning",returning).toString()).commit();}catch(Exception e){return false;}}
    DumpCheckpoint returning(){return new DumpCheckpoint(file,hash,reverse,progress,lat,lon,true);}
    static DumpCheckpoint load(SharedPreferences prefs){try{JSONObject j=new JSONObject(prefs.getString("dump_checkpoint",""));return new DumpCheckpoint(j.getString("file"),j.getString("hash"),j.getBoolean("reverse"),(float)j.getDouble("progress"),j.getDouble("lat"),j.getDouble("lon"),j.optBoolean("returning"));}catch(Exception e){return null;}}
    boolean matches(File route){try{return file.equals(route.getName())&&hash.equals(StreetIndex.fingerprint(route));}catch(Exception e){return false;}}
    static void clear(SharedPreferences prefs){prefs.edit().remove("dump_checkpoint").commit();}
}
