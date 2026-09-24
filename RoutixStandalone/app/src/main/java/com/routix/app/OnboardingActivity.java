package com.routix.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Permission-only onboarding, styled from the Routix website design system. */
public class OnboardingActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST=41;
    private SharedPreferences prefs; private CatppuccinTheme.Tokens theme; private TextView action,status;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("routix",MODE_PRIVATE);theme=CatppuccinTheme.from(prefs);
        if(prefs.getBoolean("onboarding_complete",false)&&hasLocation()){launchApp();return;}
        getWindow().setStatusBarColor(theme.base);getWindow().setNavigationBarColor(theme.base);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(54),dp(24),dp(28));page.setBackgroundColor(theme.base);

        TextView brand=label("routix.",25,Typeface.BOLD,theme.text);page.addView(brand);
        TextView eyebrow=label("ANDROID   ·   TOURNÉES DE COLLECTE",11,Typeface.BOLD,theme.green);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(42);page.addView(eyebrow,ep);
        TextView title=label("Autoriser\nla localisation.",36,Typeface.BOLD,theme.text);title.setLetterSpacing(-.025f);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(20);page.addView(title,tp);
        TextView intro=label("Routix utilise ta position précise pour enregistrer tes parcours et te guider sur le tracé.",15,Typeface.NORMAL,theme.subtext0);intro.setLineSpacing(dp(5),1);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(14);page.addView(intro,ip);

        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(CatppuccinTheme.surface(theme,theme.mantle,18,getResources().getDisplayMetrics().density));
        card.addView(label("01   POSITION PRÉCISE",12,Typeface.BOLD,theme.mauve));TextView body=label("Nécessaire au suivi GPS. Tes tournées restent sur ton appareil tant que tu ne les partages pas.",13,Typeface.NORMAL,theme.subtext1);body.setLineSpacing(dp(4),1);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(10);card.addView(body,bp);
        status=label("",11,Typeface.BOLD,theme.peach);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(14);card.addView(status,sp);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(30);page.addView(card,cp);

        page.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        action=label("Autoriser et continuer   →",15,Typeface.BOLD,theme.mantle);action.setGravity(Gravity.CENTER);action.setBackground(CatppuccinTheme.surface(theme,theme.accent,14,getResources().getDisplayMetrics().density));action.setOnClickListener(v->{press(v);requestLocation();});page.addView(action,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView foot=label("Enregistre.   Retrouve.   Repars.",11,Typeface.NORMAL,theme.subtext0);foot.setGravity(Gravity.CENTER);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(18);page.addView(foot,fp);
        setContentView(page);refresh();
    }
    private boolean hasLocation(){return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void requestLocation(){if(hasLocation()){complete();return;}ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==LOCATION_REQUEST){if(hasLocation())complete();else refresh();}}
    @Override protected void onResume(){super.onResume();if(action!=null){if(hasLocation())complete();else refresh();}}
    private void complete(){if(!hasLocation())return;prefs.edit().putBoolean("onboarding_complete",true).putBoolean("privacy_notice_seen",true).putBoolean("location_usage_accepted",true).apply();launchApp();}
    private void refresh(){if(action==null)return;boolean denied=ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION);status.setText(denied?"Autorisation refusée · réessaie pour utiliser le GPS":"Android demandera l’autorisation au prochain appui");action.setText(denied?"Réessayer   →":"Autoriser et continuer   →");}
    private void launchApp(){startActivity(new Intent(this,RoutixActivity.class));finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
    private void press(View v){v.animate().scaleX(.97f).scaleY(.97f).setDuration(75).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(150).start()).start();}
    private TextView label(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
