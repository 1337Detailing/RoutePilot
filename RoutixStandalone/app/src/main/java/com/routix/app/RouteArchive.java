package com.routix.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** GPX and its display name are stored together; restore always saves the current version first. */
final class RouteArchive {
    static final class Version {
        final File file;final String name,reason;final long time;
        Version(File f,JSONObject j){file=f;name=j.optString("name");reason=j.optString("reason");time=j.optLong("time");}
    }
    private final File root;
    RouteArchive(Context context){root=new File(context.getFilesDir(),"route_versions");}
    synchronized void capture(File route,String name,String reason)throws Exception{captureAs(route,route,name,reason);}
    synchronized void captureAs(File route,File source,String name,String reason)throws Exception{
        if(!source.isFile())return;
        File dir=new File(root,route.getName());if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Historique indisponible");
        String gpx=new String(Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8);
        List<Version> versions=versions(route);
        if(!versions.isEmpty()){JSONObject previous=read(versions.get(0).file);if(gpx.equals(previous.optString("gpx"))&&name.equals(previous.optString("name")))return;}
        JSONObject data=new JSONObject().put("gpx",gpx).put("name",name).put("reason",reason).put("time",System.currentTimeMillis());
        atomic(new File(dir,System.currentTimeMillis()+"-"+UUID.randomUUID()+".json"),data.toString().getBytes(StandardCharsets.UTF_8));
    }
    List<Version> versions(File route)throws Exception{
        List<Version> out=new ArrayList<>();File[] files=new File(root,route.getName()).listFiles((d,n)->n.endsWith(".json"));
        if(files!=null)for(File f:files)out.add(new Version(f,read(f)));
        out.sort((a,b)->Long.compare(b.time,a.time));return out;
    }
    byte[] content(Version v)throws Exception{return read(v.file).getString("gpx").getBytes(StandardCharsets.UTF_8);}
    private JSONObject read(File f)throws Exception{return new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}
    static void atomic(File file,byte[] bytes)throws IOException{
        AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(bytes);atomic.finishWrite(out);}catch(IOException e){if(out!=null)atomic.failWrite(out);throw e;}
    }
}
