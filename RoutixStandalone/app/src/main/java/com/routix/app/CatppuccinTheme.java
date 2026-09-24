package com.routix.app;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Central semantic design tokens. No screen should own palette values. */
final class CatppuccinTheme {
    static final class Tokens {
        final String flavor; final int base,mantle,crust,surface0,surface1,surface2,overlay0,overlay1,overlay2,text,subtext1,subtext0;
        final int rosewater,flamingo,pink,mauve,red,maroon,peach,yellow,green,teal,sky,sapphire,blue,lavender,accent;
        Tokens(String f,String[] n,String accentName){
            flavor=f;base=c(n[0]);mantle=c(n[1]);crust=c(n[2]);surface0=c(n[3]);surface1=c(n[4]);surface2=c(n[5]);
            overlay0=c(n[6]);overlay1=c(n[7]);overlay2=c(n[8]);text=c(n[9]);subtext1=c(n[10]);subtext0=c(n[11]);
            rosewater=c(n[12]);flamingo=c(n[13]);pink=c(n[14]);mauve=c(n[15]);red=c(n[16]);maroon=c(n[17]);peach=c(n[18]);yellow=c(n[19]);
            green=c(n[20]);teal=c(n[21]);sky=c(n[22]);sapphire=c(n[23]);blue=c(n[24]);lavender=c(n[25]);accent=accent(accentName);
        }
        int accent(String name){String x=name==null?"mauve":name.toLowerCase(Locale.ROOT);switch(x){
            case "rosewater":return rosewater;case "flamingo":return flamingo;case "pink":return pink;case "red":return red;case "maroon":return maroon;
            case "peach":return peach;case "yellow":return yellow;case "green":return green;case "teal":return teal;case "sky":return sky;
            case "sapphire":return sapphire;case "blue":return blue;case "lavender":return lavender;default:return mauve;}}
        boolean light(){return "latte".equals(flavor);}
    }
    private static int c(String h){return Color.parseColor(h);}
    private static final Map<String,String[]> P=new LinkedHashMap<>();
    static{
        P.put("mocha",new String[]{"#1e1e2e","#181825","#11111b","#313244","#45475a","#585b70","#6c7086","#7f849c","#9399b2","#cdd6f4","#bac2de","#a6adc8","#f5e0dc","#f2cdcd","#f5c2e7","#cba6f7","#f38ba8","#eba0ac","#fab387","#f9e2af","#a6e3a1","#94e2d5","#89dceb","#74c7ec","#89b4fa","#b4befe"});
        P.put("macchiato",new String[]{"#24273a","#1e2030","#181926","#363a4f","#494d64","#5b6078","#6e738d","#8087a2","#939ab7","#cad3f5","#b8c0e0","#a5adcb","#f4dbd6","#f0c6c6","#f5bde6","#c6a0f6","#ed8796","#ee99a0","#f5a97f","#eed49f","#a6da95","#8bd5ca","#91d7e3","#7dc4e4","#8aadf4","#b7bdf8"});
        P.put("frappe",new String[]{"#303446","#292c3c","#232634","#414559","#51576d","#626880","#737994","#838ba7","#949cbb","#c6d0f5","#b5bfe2","#a5adce","#f2d5cf","#eebebe","#f4b8e4","#ca9ee6","#e78284","#ea999c","#ef9f76","#e5c890","#a6d189","#81c8be","#99d1db","#85c1dc","#8caaee","#babbf1"});
        P.put("latte",new String[]{"#eff1f5","#e6e9ef","#dce0e8","#ccd0da","#bcc0cc","#acb0be","#9ca0b0","#8c8fa1","#7c7f93","#4c4f69","#5c5f77","#6c6f85","#dc8a78","#dd7878","#ea76cb","#8839ef","#d20f39","#e64553","#fe640b","#df8e1d","#40a02b","#179299","#04a5e5","#209fb5","#1e66f5","#7287fd"});
    }
    static Tokens from(SharedPreferences prefs){String f=prefs.getString("theme_flavor","mocha");if(!P.containsKey(f))f="mocha";return new Tokens(f,P.get(f),prefs.getString("accent_name","mauve"));}
    static void setFlavor(SharedPreferences p,String flavor){p.edit().putString("theme_flavor",flavor).apply();}
    static void setAccent(SharedPreferences p,String accent){p.edit().putString("accent_name",accent).apply();}
    static String[] flavors(){return new String[]{"Mocha","Macchiato","Frappé","Latte"};}
    static String[] accents(){return new String[]{"Mauve","Pink","Blue","Sapphire","Sky","Teal","Green","Yellow","Peach","Red","Maroon","Lavender","Flamingo","Rosewater"};}
    static GradientDrawable surface(Tokens t,int color,int radiusDp,float density){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radiusDp*density);return g;}
}
