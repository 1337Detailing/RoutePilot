package com.routix.app;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.util.Arrays;
import java.util.List;

final class FranceOfflineManager {
    static final class Pack {
        final String id,label,fileName,url;
        final int sizeMb;
        Pack(String id,String label,String fileName,int sizeMb) {
            this.id=id;this.label=label;this.fileName=fileName;this.sizeMb=sizeMb;
            this.url="https://download.mapsforge.org/maps/v5/europe/france/"+fileName;
        }
    }

    static final List<Pack> PACKS=Arrays.asList(
            new Pack("alsace","Alsace","alsace.map",85),
            new Pack("lorraine","Lorraine","lorraine.map",121),
            new Pack("champagne","Champagne-Ardenne","champagne-ardenne.map",79),
            new Pack("franche_comte","Franche-Comté","franche-comte.map",87),
            new Pack("bourgogne","Bourgogne","bourgogne.map",150),
            new Pack("ile_de_france","Île-de-France","ile-de-france.map",197),
            new Pack("centre","Centre","centre.map",172),
            new Pack("auvergne","Auvergne","auvergne.map",109),
            new Pack("rhone_alpes","Rhône-Alpes","rhone-alpes.map",335),
            new Pack("paca","Provence-Alpes-Côte d’Azur","provence-alpes-cote-d-azur.map",229),
            new Pack("aquitaine","Aquitaine","aquitaine.map",209),
            new Pack("bretagne","Bretagne","bretagne.map",206),
            new Pack("corse","Corse","corse.map",25)
    );

    private FranceOfflineManager(){}

    static File mapsDir(Context c){
        File base=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(base==null)base=c.getFilesDir();
        File d=new File(base,"maps");if(!d.exists())d.mkdirs();return d;
    }

    static File file(Context c,Pack p){return new File(mapsDir(c),p.fileName);}
    static boolean isInstalled(Context c,Pack p){File f=file(c,p);long id=c.getSharedPreferences("routix",0).getLong("download_"+p.id,0);if(id>0){DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);try(Cursor cursor=dm.query(new DownloadManager.Query().setFilterById(id))){if(cursor!=null&&cursor.moveToFirst()&&cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))!=DownloadManager.STATUS_SUCCESSFUL)return false;}catch(RuntimeException e){return false;}}return f.exists()&&f.length()>1024*1024;}

    static Pack find(String id){if(id==null)return null;for(Pack p:PACKS)if(id.equals(p.id))return p;return null;}

    static long download(Context c,Pack p){
        long previous=c.getSharedPreferences("routix",0).getLong("download_"+p.id,0);DownloadManager manager=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);if(previous>0)manager.remove(previous);
        File dest=file(c,p);
        if(dest.exists()&&!isInstalled(c,p))dest.delete();
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(p.url));
        r.setTitle("Routix • "+p.label);
        r.setDescription("Carte vectorielle hors ligne");
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setAllowedOverMetered(true);r.setAllowedOverRoaming(false);
        r.setDestinationUri(Uri.fromFile(dest));
        long id=dm.enqueue(r);c.getSharedPreferences("routix",0).edit().putLong("download_"+p.id,id).apply();return id;
    }

    static int downloadProgress(Context c,long id){
        if(id<=0)return -1;
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        try(Cursor cur=dm.query(new DownloadManager.Query().setFilterById(id))){
            if(cur!=null&&cur.moveToFirst()){
                int total=cur.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int done=cur.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                long t=total>=0?cur.getLong(total):0,d=done>=0?cur.getLong(done):0;
                if(t>0)return (int)Math.min(100,Math.round(d*100f/t));
            }
        }catch(Exception ignored){}
        return -1;
    }

    static boolean delete(Context c,Pack p){File f=file(c,p);return !f.exists()||f.delete();}

    static long installedBytes(Context c){long total=0;for(Pack p:PACKS)if(isInstalled(c,p))total+=file(c,p).length();return total;}
}
