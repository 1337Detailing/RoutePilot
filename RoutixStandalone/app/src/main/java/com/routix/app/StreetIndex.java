package com.routix.app;

import android.app.Activity;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

final class StreetIndex {
    private final File dir;
    StreetIndex(Activity a){dir=new File(a.getFilesDir(),"street_index");dir.mkdirs();}
    static String normalize(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    static String fingerprint(File f)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    List<PaperRoute.Step> read(File route){
        try{File file=new File(dir,fingerprint(route)+".json");if(!file.exists())return null;
            JSONArray a=new JSONArray(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));List<PaperRoute.Step> result=new ArrayList<>();
            for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);result.add(new PaperRoute.Step(j.getInt("index"),i+1,j.getString("name")));}return result;
        }catch(Exception e){return null;}
    }
    void build(Activity a,RouteStore.Summary route)throws Exception{
        String fingerprint=fingerprint(route.file);List<PaperRoute.Step> steps=PrintablePlan.streetSteps(a,route);JSONArray array=new JSONArray();
        for(PaperRoute.Step s:steps)array.put(new JSONObject().put("index",s.index).put("name",s.name));
        RouteArchive.atomic(new File(dir,fingerprint+".json"),array.toString().getBytes(StandardCharsets.UTF_8));
    }
}
