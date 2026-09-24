package com.routix.app;

import android.content.SharedPreferences;

final class MapStyles {
    static boolean dark(SharedPreferences prefs){return !CatppuccinTheme.from(prefs).light();}
    static String uri(SharedPreferences prefs){return "asset://maps/"+(dark(prefs)?"dark":"light")+".json";}
    static double smoothBearing(double previous,double target){double delta=((target-previous+540)%360)-180;return (previous+delta*.22+360)%360;}
}
