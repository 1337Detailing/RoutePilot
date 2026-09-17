package com.routix.app;

import android.content.SharedPreferences;
import java.util.Calendar;

final class MapStyles {
    static boolean dark(SharedPreferences prefs){if(prefs.getBoolean("auto_night",false)){int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);return h<7||h>=20;}return "dark".equals(prefs.getString("map_appearance","dark"));}
    static String uri(SharedPreferences prefs){return "asset://maps/"+(dark(prefs)?"dark":"light")+".json";}
    static double smoothBearing(double previous,double target){double delta=((target-previous+540)%360)-180;return (previous+delta*.22+360)%360;}
}
