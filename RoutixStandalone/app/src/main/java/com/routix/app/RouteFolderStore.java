package com.routix.app;

import android.content.SharedPreferences;
import java.io.File;
import java.util.*;

/** Lightweight folder metadata; GPX files stay in the same physical routes directory. */
final class RouteFolderStore {
    static final String ROOT="";
    private static final String KEY_FOLDERS="route_folders";
    private final SharedPreferences prefs;

    RouteFolderStore(SharedPreferences prefs){this.prefs=prefs;}

    List<String> folders(){
        Set<String> raw=prefs.getStringSet(KEY_FOLDERS,Collections.emptySet());
        List<String> out=new ArrayList<>();for(String s:raw)if(s!=null&&!s.trim().isEmpty())out.add(s);
        out.sort(String.CASE_INSENSITIVE_ORDER);return out;
    }

    boolean create(String name){
        String n=clean(name);if(n.isEmpty())return false;
        Set<String> set=new HashSet<>(prefs.getStringSet(KEY_FOLDERS,Collections.emptySet()));
        for(String x:set)if(x.equalsIgnoreCase(n))return false;
        set.add(n);prefs.edit().putStringSet(KEY_FOLDERS,set).apply();return true;
    }

    boolean rename(String oldName,String newName){
        String old=clean(oldName),n=clean(newName);if(old.isEmpty()||n.isEmpty())return false;
        Set<String> set=new HashSet<>(prefs.getStringSet(KEY_FOLDERS,Collections.emptySet()));
        boolean found=false;for(String x:new ArrayList<>(set))if(x.equalsIgnoreCase(old)){set.remove(x);found=true;break;}
        if(!found)return false;
        for(String x:set)if(x.equalsIgnoreCase(n))return false;
        Set<String> renamedSet=new HashSet<>(set);renamedSet.add(n);SharedPreferences.Editor e=prefs.edit().putStringSet(KEY_FOLDERS,renamedSet);
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){
            if(entry.getKey().startsWith("route_folder_")&&old.equalsIgnoreCase(String.valueOf(entry.getValue())))e.putString(entry.getKey(),n);
        }
        e.apply();return true;
    }

    void delete(String name){
        String n=clean(name);Set<String> set=new HashSet<>(prefs.getStringSet(KEY_FOLDERS,Collections.emptySet()));
        String remove=null;for(String x:set)if(x.equalsIgnoreCase(n)){remove=x;break;}if(remove!=null)set.remove(remove);SharedPreferences.Editor e=prefs.edit().putStringSet(KEY_FOLDERS,set);
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(entry.getKey().startsWith("route_folder_")&&n.equalsIgnoreCase(String.valueOf(entry.getValue())))e.remove(entry.getKey());
        e.remove("route_folder_open_"+n).apply();
    }

    String folderOf(File file){
        if(file==null)return ROOT;
        String v=clean(prefs.getString("route_folder_"+file.getName(),ROOT));
        if(v.isEmpty())return ROOT;
        for(String folder:folders())if(folder.equalsIgnoreCase(v))return folder;
        prefs.edit().remove("route_folder_"+file.getName()).apply();
        return ROOT;
    }

    boolean move(File file,String folder){
        if(file==null)return false;
        String n=clean(folder);
        if(n.isEmpty()){prefs.edit().remove("route_folder_"+file.getName()).apply();return true;}
        String canonical=null;for(String existing:folders())if(existing.equalsIgnoreCase(n)){canonical=existing;break;}
        if(canonical==null)return false;
        prefs.edit().putString("route_folder_"+file.getName(),canonical).apply();return true;
    }

    void migrateFileName(String oldName,String newName){
        if(oldName==null||newName==null||oldName.equals(newName))return;String key="route_folder_"+oldName;if(!prefs.contains(key))return;String folder=prefs.getString(key,ROOT);prefs.edit().remove(key).putString("route_folder_"+newName,folder).apply();
    }
    void forget(File file){if(file!=null)prefs.edit().remove("route_folder_"+file.getName()).apply();}
    boolean expanded(String folder){return prefs.getBoolean("route_folder_open_"+folder,true);}
    void setExpanded(String folder,boolean open){prefs.edit().putBoolean("route_folder_open_"+folder,open).apply();}
    private static String clean(String s){if(s==null)return "";return s.trim().replaceAll("\\s+"," ");}
}
