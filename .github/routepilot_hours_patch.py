from pathlib import Path

activity = Path('RoutePilotStandalone/app/src/main/java/com/routepilot/app/RoutePilotProActivity.java')
s = activity.read_text()


def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'missing replacement: {label}')
    s = s.replace(old, new, 1)


rep('import java.util.ArrayList;\nimport java.util.Collections;\nimport java.util.Date;\nimport java.util.List;\nimport java.util.Locale;',
    'import java.util.ArrayList;\nimport java.util.Calendar;\nimport java.util.Collections;\nimport java.util.Date;\nimport java.util.List;\nimport java.util.Locale;\nimport java.util.Map;', 'imports')
rep('private RouteStore store;', 'private RouteStore store;\n    private WorkHoursStore hoursStore;', 'hours store field')
rep('private TextView navMap,navHistory,navSettings;', 'private TextView navMap,navHistory,navHours,navSettings;', 'hours nav field')
rep('store=new RouteStore(this,prefs);', 'store=new RouteStore(this,prefs);\n        hoursStore=new WorkHoursStore(prefs);', 'hours store init')
rep('root=new FrameLayout(this);root.setBackgroundColor(BG);', 'root=new FrameLayout(this);root.setBackground(appBackground());', 'app background')

old_dock = '''    private View buildDock(){
        LinearLayout d=new LinearLayout(this);d.setGravity(Gravity.CENTER);d.setPadding(dp(7),dp(6),dp(7),dp(6));d.setBackground(glass(Color.argb(247,21,22,27),27));d.setElevation(dp(25));navMap=nav("⌖","Carte");navHistory=nav("≡","Tournées");navSettings=nav("⚙","Réglages");navMap.setOnClickListener(v->showMap());navHistory.setOnClickListener(v->showHistory());navSettings.setOnClickListener(v->showSettings());d.addView(navMap,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navHistory,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navSettings,new LinearLayout.LayoutParams(0,dp(58),1));return d;
    }

    private void showMap(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.VISIBLE);topBar.setVisibility(View.VISIBLE);recordSheet.setVisibility(View.VISIBLE);dock.setVisibility(View.VISIBLE);selectTab("map");}
    private void showHistory(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=historyPage();root.addView(page,pageLp());selectTab("history");fade(page);}
    private void showSettings(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=settingsPage();root.addView(page,pageLp());selectTab("settings");fade(page);}
'''
new_dock = '''    private View buildDock(){
        LinearLayout d=new LinearLayout(this);d.setGravity(Gravity.CENTER);d.setPadding(dp(7),dp(6),dp(7),dp(6));d.setBackground(glass(Color.argb(205,21,24,32),27));d.setElevation(dp(25));navMap=nav("⌖","Carte");navHistory=nav("≡","Tournées");navHours=nav("◷","Heures");navSettings=nav("⚙","Réglages");navMap.setOnClickListener(v->showMap());navHistory.setOnClickListener(v->showHistory());navHours.setOnClickListener(v->showHours());navSettings.setOnClickListener(v->showSettings());d.addView(navMap,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navHistory,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navHours,new LinearLayout.LayoutParams(0,dp(58),1));d.addView(navSettings,new LinearLayout.LayoutParams(0,dp(58),1));return d;
    }

    private void showMap(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.VISIBLE);topBar.setVisibility(View.VISIBLE);recordSheet.setVisibility(View.VISIBLE);dock.setVisibility(View.VISIBLE);selectTab("map");}
    private void showHistory(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=historyPage();root.addView(page,pageLp());selectTab("history");fade(page);}
    private void showHours(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=hoursPage();root.addView(page,pageLp());selectTab("hours");fade(page);}
    private void showSettings(){if(guiding)return;exitFocusMode();clearPage();map.setVisibility(View.GONE);topBar.setVisibility(View.GONE);recordSheet.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);page=settingsPage();root.addView(page,pageLp());selectTab("settings");fade(page);}
'''
rep(old_dock, new_dock, 'dock')

hours_methods = r'''    private View hoursPage(){
        LinearLayout p=pageBase();
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("Heures",30,Typeface.BOLD,Color.WHITE));titles.addView(text("Suivi local • calcul mensuel automatique",12,Typeface.NORMAL,MUTED));head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView plus=circle("＋",accent());plus.setOnClickListener(v->{press(v);showHoursEntrySheet();});head.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(46)));p.addView(head);

        String month=currentMonthKey();int total=hoursStore.totalForMonth(month);LinearLayout totalCard=new LinearLayout(this);totalCard.setOrientation(LinearLayout.VERTICAL);totalCard.setPadding(dp(18),dp(17),dp(18),dp(17));totalCard.setBackground(glass(Color.argb(150,28,52,84),27));totalCard.setElevation(dp(12));
        totalCard.addView(text(monthLabel(month).toUpperCase(Locale.FRANCE),10,Typeface.BOLD,CYAN));TextView big=text(formatWorkMinutes(total),32,Typeface.BOLD,Color.WHITE);LinearLayout.LayoutParams bigLp=new LinearLayout.LayoutParams(-1,-2);bigLp.topMargin=dp(5);totalCard.addView(big,bigLp);totalCard.addView(text("temps net après déduction des pauses",11,Typeface.NORMAL,MUTED));LinearLayout.LayoutParams tc=new LinearLayout.LayoutParams(-1,-2);tc.topMargin=dp(16);tc.bottomMargin=dp(18);p.addView(totalCard,tc);

        ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        Map<String,Integer> totals=hoursStore.totalsByMonth();if(!totals.isEmpty()){body.addView(section("TOTAL PAR MOIS"));for(Map.Entry<String,Integer> e:totals.entrySet()){LinearLayout r=row();r.addView(text(monthLabel(e.getKey()),14,Typeface.BOLD,Color.WHITE),new LinearLayout.LayoutParams(0,-2,1));r.addView(text(formatWorkMinutes(e.getValue()),15,Typeface.BOLD,accent()));body.addView(r,bottom(8));}}
        body.addView(section("JOURNÉES"),top(10));List<WorkHoursStore.Entry> entries=hoursStore.entries();if(entries.isEmpty()){LinearLayout empty=new LinearLayout(this);empty.setOrientation(LinearLayout.VERTICAL);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(58),0,dp(30));empty.addView(text("◷",44,Typeface.NORMAL,Color.argb(90,255,255,255)));empty.addView(text("Aucune heure enregistrée",18,Typeface.BOLD,Color.WHITE));empty.addView(text("Appuie sur + pour ajouter ta première journée.",12,Typeface.NORMAL,MUTED));body.addView(empty);}else for(WorkHoursStore.Entry e:entries)body.addView(hoursCard(e),bottom(9));
        return p;
    }

    private View hoursCard(WorkHoursStore.Entry e){
        LinearLayout c=row();c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(14),dp(14));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);LinearLayout date=new LinearLayout(this);date.setOrientation(LinearLayout.VERTICAL);date.addView(text(dayLabel(e.date),15,Typeface.BOLD,Color.WHITE));date.addView(text(e.date,10,Typeface.NORMAL,MUTED));top.addView(date,new LinearLayout.LayoutParams(0,-2,1));TextView net=pill(formatWorkMinutes(e.netMinutes()),Color.argb(115,10,132,255),12);top.addView(net,new LinearLayout.LayoutParams(-2,dp(36)));c.addView(top);
        LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(12),0,0);stats.addView(summaryStat("DÉBUT",timeLabel(e.startMinutes)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("FIN",timeLabel(e.endMinutes)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("PAUSE",e.pauseMinutes+" min"),new LinearLayout.LayoutParams(0,-2,1));c.addView(stats);
        TextView del=text("Supprimer",11,Typeface.BOLD,Color.argb(220,255,90,82));del.setGravity(Gravity.END);del.setPadding(dp(10),dp(10),0,0);del.setOnClickListener(v->{press(v);hoursStore.delete(e.id);showHours();});c.addView(del,new LinearLayout.LayoutParams(-1,-2));return c;
    }

    private void showHoursEntrySheet(){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));String date=todayKey();LinearLayout panel=sheetPanel("Ajouter des heures",dayLabel(date)+" • "+date);
        EditText start=hoursInput("Heure de début • HH:mm",prefs.getString("hours_last_start",""),InputType.TYPE_CLASS_DATETIME);EditText end=hoursInput("Heure de fin • HH:mm",prefs.getString("hours_last_end",""),InputType.TYPE_CLASS_DATETIME);EditText pause=hoursInput("Temps de pause • minutes",String.valueOf(prefs.getInt("hours_last_pause",0)),InputType.TYPE_CLASS_NUMBER);
        panel.addView(start,new LinearLayout.LayoutParams(-1,dp(52)));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(8);panel.addView(end,ep);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(52));pp.topMargin=dp(8);panel.addView(pause,pp);
        TextView hint=text("La date du jour est ajoutée automatiquement. Si la fin est avant le début, le service est considéré comme se terminant le lendemain.",10,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(10);hp.bottomMargin=dp(12);panel.addView(hint,hp);
        LinearLayout quick=new LinearLayout(this);for(int m:new int[]{0,20,30,45,60}){TextView q=pill(m+" min",Color.argb(42,255,255,255),10);q.setOnClickListener(v->{press(v);pause.setText(String.valueOf(m));});LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,dp(36),1);qp.rightMargin=dp(4);quick.addView(q,qp);}panel.addView(quick);
        TextView save=sheetAction("✓  Enregistrer la journée",accent());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.topMargin=dp(12);panel.addView(save,sp);save.setOnClickListener(v->{int a=parseClock(start.getText().toString());int b=parseClock(end.getText().toString());int breakM=parsePositive(pause.getText().toString());if(a<0||b<0){shake(start);shake(end);toast("Entre des heures au format HH:mm");return;}int gross=(b>=a?b-a:b+1440-a);if(breakM>=gross){shake(pause);toast("La pause doit être plus courte que le service");return;}hoursStore.add(date,a,b,breakM);prefs.edit().putString("hours_last_start",start.getText().toString().trim()).putString("hours_last_end",end.getText().toString().trim()).putInt("hours_last_pause",breakM).apply();press(v);d.dismiss();root.postDelayed(this::showHours,120);});
        presentBottomSheet(d,shell,panel);root.postDelayed(()->start.requestFocus(),180);
    }

    private EditText hoursInput(String hint,String value,int type){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setHintTextColor(Color.argb(115,255,255,255));e.setTextColor(Color.WHITE);e.setTextSize(15);e.setInputType(type);e.setPadding(dp(15),0,dp(15),0);e.setBackground(glass(Color.argb(62,255,255,255),18));if(value!=null)e.setText(value);return e;}
    private int parseClock(String raw){try{String[] p=raw.trim().replace('.',':').split(":");if(p.length!=2)return-1;int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);return h>=0&&h<=23&&m>=0&&m<=59?h*60+m:-1;}catch(Exception e){return-1;}}
    private int parsePositive(String raw){try{return Math.max(0,Integer.parseInt(raw.trim()));}catch(Exception e){return 0;}}
    private String todayKey(){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date());}
    private String currentMonthKey(){return new java.text.SimpleDateFormat("yyyy-MM",Locale.FRANCE).format(new Date());}
    private String timeLabel(int m){return String.format(Locale.FRANCE,"%02d:%02d",(m/60)%24,m%60);}
    private String formatWorkMinutes(int m){return String.format(Locale.FRANCE,"%d h %02d",m/60,m%60);}
    private String monthLabel(String key){try{return new java.text.SimpleDateFormat("MMMM yyyy",Locale.FRANCE).format(new java.text.SimpleDateFormat("yyyy-MM",Locale.FRANCE).parse(key));}catch(Exception e){return key;}}
    private String dayLabel(String key){try{return new java.text.SimpleDateFormat("EEEE d MMMM",Locale.FRANCE).format(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).parse(key));}catch(Exception e){return key;}}
    private void shake(View v){v.animate().translationX(dp(8)).setDuration(55).withEndAction(()->v.animate().translationX(-dp(8)).setDuration(55).withEndAction(()->v.animate().translationX(0).setDuration(55).start()).start()).start();}

'''
rep('    private View settingsPage(){\n', hours_methods + '    private View settingsPage(){\n', 'hours methods')

old_sel='private void selectTab(String s){for(TextView v:new TextView[]{navMap,navHistory,navSettings}){boolean on=(v==navMap&&"map".equals(s))||(v==navHistory&&"history".equals(s))||(v==navSettings&&"settings".equals(s));v.setTextColor(on?Color.WHITE:MUTED);v.setBackground(on?glass(Color.argb(100,Color.red(accent()),Color.green(accent()),Color.blue(accent())),18):null);}}'
new_sel='private void selectTab(String s){for(TextView v:new TextView[]{navMap,navHistory,navHours,navSettings}){boolean on=(v==navMap&&"map".equals(s))||(v==navHistory&&"history".equals(s))||(v==navHours&&"hours".equals(s))||(v==navSettings&&"settings".equals(s));v.setTextColor(on?Color.WHITE:MUTED);v.setBackground(on?glass(Color.argb(105,Color.red(accent()),Color.green(accent()),Color.blue(accent())),18):null);v.animate().scaleX(on?1.03f:1f).scaleY(on?1.03f:1f).setDuration(180).start();}}'
rep(old_sel,new_sel,'select tab')

old_glass='private GradientDrawable glass(int color,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{shade(color,18),color,shade(color,-10)});g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.argb(45,255,255,255));return g;}'
new_glass='private GradientDrawable glass(int color,int radius){int a=Color.alpha(color);int hi=Color.argb(Math.min(255,a+18),Math.min(255,Color.red(color)+34),Math.min(255,Color.green(color)+34),Math.min(255,Color.blue(color)+38));int lo=Color.argb(Math.max(24,a-28),Math.max(0,Color.red(color)-14),Math.max(0,Color.green(color)-14),Math.max(0,Color.blue(color)-9));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{hi,color,lo});g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.argb(72,255,255,255));return g;}'
rep(old_glass,new_glass,'liquid glass')

old_page='private LinearLayout pageBase(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(18),safeTop()+dp(18),dp(18),safeBottom()+dp(86));p.setBackgroundColor(BG);return p;}'
new_page='private LinearLayout pageBase(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(18),safeTop()+dp(18),dp(18),safeBottom()+dp(86));p.setBackground(appBackground());return p;}\n    private GradientDrawable appBackground(){return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(6,9,15),Color.rgb(14,11,22),Color.rgb(7,13,18)});}'
rep(old_page,new_page,'page background')

# Android 12+ gives the modern sheets a true blurred backdrop in addition to the liquid translucent surfaces.
s=s.replace('w.setAttributes(a);}}','w.setAttributes(a);if(Build.VERSION.SDK_INT>=31)w.setBackgroundBlurRadius(dp(28));}}')
activity.write_text(s)

work_store = r'''package com.routepilot.app;

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
'''
Path('RoutePilotStandalone/app/src/main/java/com/routepilot/app/WorkHoursStore.java').write_text(work_store)

onboarding = r'''package com.routepilot.app;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {
    private static final int BG=Color.rgb(7,8,12),BLUE=Color.rgb(10,132,255),CYAN=Color.rgb(82,210,255),GLASS=Color.argb(178,31,34,44),MUTED=Color.argb(184,255,255,255);
    private SharedPreferences prefs; private FrameLayout root; private LinearLayout pageHost,dots; private TextView action; private int step=0; private boolean locationConsent,dataConsent;
    @Override protected void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("routepilot",MODE_PRIVATE);if(prefs.getBoolean("onboarding_complete",false)){launchApp();return;}getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(BG);getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);root=new FrameLayout(this);root.setBackground(background());addOrb(Color.rgb(25,112,255),320,Gravity.TOP|Gravity.END,5200);addOrb(Color.rgb(160,65,255),280,Gravity.BOTTOM|Gravity.START,6100);LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(20),dp(34),dp(20),dp(20));root.addView(shell,new FrameLayout.LayoutParams(-1,-1));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(10),dp(8));top.setBackground(liquid(Color.argb(135,34,38,49),24));TextView mark=label("RP",15,Typeface.BOLD,Color.WHITE);mark.setGravity(Gravity.CENTER);mark.setBackground(liquid(BLUE,17));top.addView(mark,new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.addView(label("RoutePilot",18,Typeface.BOLD,Color.WHITE));brand.addView(label("Tournées terrain • données sous ton contrôle",9,Typeface.NORMAL,MUTED));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=dp(11);top.addView(brand,bp);TextView skip=label("Passer",12,Typeface.BOLD,MUTED);skip.setGravity(Gravity.CENTER);skip.setBackground(liquid(Color.argb(42,255,255,255),15));skip.setOnClickListener(v->{press(v);step=3;render();});top.addView(skip,new LinearLayout.LayoutParams(dp(68),dp(38)));shell.addView(top);pageHost=new LinearLayout(this);pageHost.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams ph=new LinearLayout.LayoutParams(-1,0,1);ph.topMargin=dp(25);shell.addView(pageHost,ph);dots=new LinearLayout(this);dots.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,dp(24));dl.bottomMargin=dp(10);shell.addView(dots,dl);action=label("Continuer",16,Typeface.BOLD,Color.WHITE);action.setGravity(Gravity.CENTER);action.setBackground(liquid(BLUE,22));action.setOnClickListener(v->next());shell.addView(action,new LinearLayout.LayoutParams(-1,dp(58)));TextView version=label("RoutePilot 1.0.3 • stockage local • sans compte RoutePilot",10,Typeface.NORMAL,Color.argb(118,255,255,255));version.setGravity(Gravity.CENTER);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(12);shell.addView(version,vp);setContentView(root);render();}
    private void render(){pageHost.animate().alpha(0).translationX(-dp(16)).scaleX(.985f).scaleY(.985f).setDuration(120).withEndAction(()->{pageHost.removeAllViews();pageHost.setAlpha(0);pageHost.setTranslationX(dp(22));if(step==0)intro();else if(step==1)features();else if(step==2)offline();else if(step==3)privacy();else consent();pageHost.animate().alpha(1).translationX(0).scaleX(1).scaleY(1).setDuration(330).setInterpolator(new DecelerateInterpolator()).start();dots();}).start();}
    private void intro(){hero("⌁","La tournée, sans le chaos.","Enregistre précisément ton parcours réel, mémorise les actions métier et rejoue la tournée plus tard avec une interface pensée pour le terrain.");card("Trace GPS fidèle","Les points sont stockés avec leur précision et leur heure.");action.setText("Découvrir RoutePilot");enable(true);}
    private void features(){hero("◎","Ton trajet devient un guide","Marche arrière et 2 côtés sont enregistrés exactement là où tu les déclenches, puis réapparaissent au bon moment pendant le guidage.");card("↶  Marche arrière","Repère géolocalisé et alerte à l’approche.");card("⇆  2 côtés","Repère métier discret sur la carte, clair dans le HUD.");card("▶  Relecture visuelle","La trace enregistrée reste la référence, sans navigation vocale.");action.setText("Continuer");enable(true);}
    private void offline(){hero("▧","Online ou hors ligne","OpenStreetMap sert de fond en ligne. Les packs vectoriels français peuvent rester sur le téléphone pour travailler sans réseau.");card("Cartes séparées de l’APK","Télécharge uniquement les zones utiles depuis Réglages.");card("Tournées locales","Les fichiers GPX restent dans le stockage privé de RoutePilot jusqu’à une action de partage.");action.setText("Voir la confidentialité");enable(true);}
    private void privacy(){hero("◉","Confidentialité, sans texte flou","Voici concrètement ce qui sort du téléphone et ce qui reste local dans cette V1.");privacyCard("Position précise","Utilisée quand RoutePilot est ouvert pour enregistrer et guider. Aucune permission de localisation en arrière-plan n’est demandée.",Color.rgb(48,209,88));privacyCard("Tournées & heures","GPX, repères métier et heures sont enregistrés localement. Ils ne sont exportés que lorsque tu déclenches une action de partage.",CYAN);privacyCard("Services réseau","En OSM en ligne, les requêtes de tuiles vont à OpenStreetMap. Pour rejoindre le départ, les coordonnées sont envoyées au routeur public OSRM afin de calculer cet itinéraire.",Color.rgb(191,90,242));privacyCard("Compte & cloud","Cette V1 n’intègre ni compte RoutePilot ni synchronisation cloud des tournées.",Color.rgb(48,209,88));action.setText("J’ai compris");enable(true);}
    private void consent(){hero("✓","Tu gardes le contrôle","Deux confirmations avant d’activer RoutePilot. Elles sont mémorisées localement sur cet appareil.");pageHost.addView(consentRow("Localisation précise","Utilisée pour enregistrer et guider une tournée.",true),lp());pageHost.addView(consentRow("Données & réseau","J’ai compris le stockage local, le partage manuel, OSM et OSRM.",false),lp());action.setText("Commencer avec RoutePilot");enable(locationConsent&&dataConsent);}
    private void hero(String icon,String title,String body){TextView orb=label(icon,35,Typeface.BOLD,Color.WHITE);orb.setGravity(Gravity.CENTER);orb.setBackground(liquid(Color.argb(118,10,132,255),34));pageHost.addView(orb,new LinearLayout.LayoutParams(dp(76),dp(76)));orb.setScaleX(.7f);orb.setScaleY(.7f);orb.setAlpha(0);orb.animate().scaleX(1).scaleY(1).alpha(1).setDuration(420).setInterpolator(new OvershootInterpolator(.9f)).start();TextView t=label(title,31,Typeface.BOLD,Color.WHITE);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(25);pageHost.addView(t,tp);TextView b=label(body,14.5f,Typeface.NORMAL,MUTED);b.setLineSpacing(dp(5),1);LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);bl.topMargin=dp(12);bl.bottomMargin=dp(18);pageHost.addView(b,bl);}
    private void card(String title,String body){LinearLayout c=glassCard();c.addView(label(title,15,Typeface.BOLD,Color.WHITE));c.addView(label(body,12,Typeface.NORMAL,MUTED));pageHost.addView(c,lp());}
    private void privacyCard(String title,String body,int color){LinearLayout c=glassCard();TextView t=label("●  "+title,15,Typeface.BOLD,Color.WHITE);t.setCompoundDrawablePadding(dp(6));c.addView(t);TextView b=label(body,11.5f,Typeface.NORMAL,MUTED);b.setLineSpacing(dp(3),1);c.addView(b);pageHost.addView(c,lp());}
    private View consentRow(String title,String body,boolean loc){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(14),dp(13),dp(14),dp(13));r.setBackground(liquid(GLASS,20));TextView check=label("",16,Typeface.BOLD,Color.WHITE);check.setGravity(Gravity.CENTER);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(30),dp(30));cp.rightMargin=dp(12);r.addView(check,cp);LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(label(title,14,Typeface.BOLD,Color.WHITE));tx.addView(label(body,11,Typeface.NORMAL,MUTED));r.addView(tx,new LinearLayout.LayoutParams(0,-2,1));Runnable paint=()->{boolean on=loc?locationConsent:dataConsent;check.setText(on?"✓":"");check.setBackground(liquid(on?BLUE:Color.argb(65,255,255,255),99));};paint.run();r.setOnClickListener(v->{press(v);if(loc)locationConsent=!locationConsent;else dataConsent=!dataConsent;paint.run();enable(locationConsent&&dataConsent);});return r;}
    private LinearLayout glassCard(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(13),dp(15),dp(13));c.setBackground(liquid(GLASS,21));return c;} private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);return p;}
    private void dots(){dots.removeAllViews();for(int i=0;i<5;i++){View d=new View(this);d.setBackground(liquid(i==step?BLUE:Color.argb(55,255,255,255),99));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(i==step?25:7),dp(7));p.setMargins(dp(4),0,dp(4),0);dots.addView(d,p);}}
    private void next(){press(action);if(step<4){step++;render();return;}if(!action.isEnabled())return;prefs.edit().putBoolean("onboarding_complete",true).putBoolean("privacy_notice_seen",true).putBoolean("location_usage_accepted",true).apply();root.animate().alpha(0).scaleX(1.015f).scaleY(1.015f).setDuration(260).withEndAction(this::launchApp).start();}
    private void enable(boolean on){action.setEnabled(on);action.animate().alpha(on?1:.35f).setDuration(150).start();} private void launchApp(){startActivity(new Intent(this,RoutePilotProActivity.class));finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);} private void press(View v){v.animate().scaleX(.965f).scaleY(.965f).setDuration(70).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(200).setInterpolator(new OvershootInterpolator(.7f)).start()).start();}
    private void addOrb(int color,int sizeDp,int gravity,long duration){View o=new View(this);GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setGradientType(GradientDrawable.RADIAL_GRADIENT);g.setColors(new int[]{Color.argb(92,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT});g.setGradientRadius(dp(sizeDp)*.52f);o.setBackground(g);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(sizeDp),dp(sizeDp),gravity);p.setMargins(-dp(80),-dp(65),-dp(80),dp(20));root.addView(o,p);ValueAnimator a=ValueAnimator.ofFloat(0,1);a.setDuration(duration);a.setRepeatCount(ValueAnimator.INFINITE);a.setRepeatMode(ValueAnimator.REVERSE);a.addUpdateListener(v->{float q=(float)v.getAnimatedValue();o.setTranslationX(dp(22)*q);o.setTranslationY(dp(28)*q);o.setScaleX(1+.08f*q);o.setScaleY(1+.08f*q);});a.start();}
    private GradientDrawable background(){return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(5,8,15),Color.rgb(13,10,22),Color.rgb(6,12,18)});} private GradientDrawable liquid(int c,int r){int a=Color.alpha(c);int hi=Color.argb(Math.min(255,a+18),Math.min(255,Color.red(c)+32),Math.min(255,Color.green(c)+32),Math.min(255,Color.blue(c)+36));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{hi,c,Color.argb(Math.max(24,a-28),Math.max(0,Color.red(c)-12),Math.max(0,Color.green(c)-12),Math.max(0,Color.blue(c)-8))});g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.argb(68,255,255,255));return g;} private TextView label(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
'''
Path('RoutePilotStandalone/app/src/main/java/com/routepilot/app/OnboardingActivity.java').write_text(onboarding)

build=Path('RoutePilotStandalone/app/build.gradle')
b=build.read_text()
if 'versionCode 13' not in b or "versionName '1.0.2'" not in b:
    raise SystemExit('unexpected version before bump')
b=b.replace('versionCode 13','versionCode 14').replace("versionName '1.0.2'","versionName '1.0.3'")
build.write_text(b)
