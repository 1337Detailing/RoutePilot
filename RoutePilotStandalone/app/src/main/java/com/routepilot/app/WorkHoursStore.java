package com.routepilot.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WorkHoursStore {
    static final class Entry {
        final long id; final String date; final int startMinutes,endMinutes,pauseMinutes;
        Entry(long id,String date,int startMinutes,int endMinutes,int pauseMinutes){this.id=id;this.date=date;this.startMinutes=startMinutes;this.endMinutes=endMinutes;this.pauseMinutes=Math.max(0,pauseMinutes);}
        int netMinutes(){int end=endMinutes;if(end<startMinutes)end+=1440;return Math.max(0,end-startMinutes-pauseMinutes);}
        String monthKey(){return date!=null&&date.length()>=7?date.substring(0,7):"";}
    }
    private static final String KEY="work_hours_v1"; private final SharedPreferences prefs;
    WorkHoursStore(SharedPreferences prefs){this.prefs=prefs;}
    List<Entry> entries(){List<Entry> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(KEY,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String date=o.optString("date","");int start=o.optInt("start",-1),end=o.optInt("end",-1),pause=o.optInt("pause",0);if(date.length()!=10||start<0||end<0)continue;out.add(new Entry(o.optLong("id",i+1),date,start,end,pause));}}catch(Exception ignored){}Collections.sort(out,(a,b)->{int d=b.date.compareTo(a.date);return d!=0?d:Long.compare(b.id,a.id);});return out;}
    Entry add(String date,int start,int end,int pause){Entry e=new Entry(System.currentTimeMillis(),date,start,end,pause);List<Entry> all=entries();all.add(e);save(all);return e;}
    void delete(long id){List<Entry> all=entries();all.removeIf(e->e.id==id);save(all);}
    int totalForMonth(String month){int total=0;for(Entry e:entries())if(month.equals(e.monthKey()))total+=e.netMinutes();return total;}
    Map<String,Integer> totalsByMonth(){Map<String,Integer> totals=new LinkedHashMap<>();for(Entry e:entries())totals.put(e.monthKey(),totals.getOrDefault(e.monthKey(),0)+e.netMinutes());return totals;}
    private void save(List<Entry> all){JSONArray a=new JSONArray();try{for(Entry e:all){JSONObject o=new JSONObject();o.put("id",e.id);o.put("date",e.date);o.put("start",e.startMinutes);o.put("end",e.endMinutes);o.put("pause",e.pauseMinutes);a.put(o);}}catch(Exception ignored){}prefs.edit().putString(KEY,a.toString()).apply();}
}
