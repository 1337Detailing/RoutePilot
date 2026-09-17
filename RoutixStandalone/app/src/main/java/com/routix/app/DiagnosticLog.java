package com.routix.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.nio.file.Files;
import java.util.Date;

/** Bounded local diagnostics. No GPS coordinates or route names are logged. */
final class DiagnosticLog {
    private static File file;
    static synchronized void init(Context c) {
        file=new File(c.getFilesDir(),"diagnostics.log");
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            error("uncaught/"+thread.getName(),error);
            if(previous!=null)previous.uncaughtException(thread,error);
            else {android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
        info("start Android="+Build.VERSION.SDK_INT+" model="+Build.MODEL);
    }
    static void info(String message){write(message);}
    static void error(String component,Throwable error){Log.e("Routix",component,error);write(component+"\n"+Log.getStackTraceString(error));}
    private static synchronized void write(String message){
        if(file==null)return;
        try {
            if(file.length()>512*1024){File old=new File(file.getParentFile(),"diagnostics.previous.log");Files.move(file.toPath(),old.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            try(FileWriter w=new FileWriter(file,true)){w.write(new Date()+" "+message+"\n");}
        } catch(IOException e){Log.e("Routix","Diagnostic write failed",e);}
    }
    static synchronized File export(Context c)throws IOException {
        File dir=new File(c.getCacheDir(),"reports");dir.mkdirs();File out=new File(dir,"Routix-diagnostic.txt");
        try(FileOutputStream stream=new FileOutputStream(out)){
            stream.write(("Routix diagnostic — Android "+Build.VERSION.SDK_INT+" / "+Build.MODEL+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for(File f:new File[]{new File(c.getFilesDir(),"diagnostics.previous.log"),new File(c.getFilesDir(),"diagnostics.log")})if(f.isFile())Files.copy(f.toPath(),stream);
        }return out;
    }
}
