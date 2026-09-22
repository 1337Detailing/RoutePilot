package com.routix.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/** First launch is intentionally reduced to the two required confirmations. */
public class OnboardingActivity extends AppCompatActivity {
    private static final int BG=Color.rgb(7,8,12),ACCENT=Color.rgb(203,166,247),GLASS=Color.argb(178,31,34,44),MUTED=Color.argb(184,255,255,255);
    private SharedPreferences prefs;private boolean locationConsent,dataConsent;private TextView action;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("routix",MODE_PRIVATE);
        if(prefs.getBoolean("onboarding_complete",false)){launchApp();return;}
        if(state!=null){locationConsent=state.getBoolean("locationConsent");dataConsent=state.getBoolean("dataConsent");}
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(BG);

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(42),dp(22),dp(24));root.addView(page,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.routix_logo_v2);logo.setScaleType(ImageView.ScaleType.CENTER_CROP);logo.setBackground(liquid(Color.argb(70,137,180,250),18));brand.addView(logo,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout bt=new LinearLayout(this);bt.setOrientation(LinearLayout.VERTICAL);bt.setPadding(dp(12),0,0,0);bt.addView(label("Routix",19,Typeface.BOLD,Color.WHITE));bt.addView(label("Prêt pour le terrain",10,Typeface.NORMAL,MUTED));brand.addView(bt,new LinearLayout.LayoutParams(0,-2,1));page.addView(brand);

        TextView title=label("Tu gardes le contrôle",31,Typeface.BOLD,Color.WHITE);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(46);page.addView(title,tp);
        TextView intro=label("Deux confirmations sont nécessaires avant le premier lancement. Aucun écran marketing supplémentaire.",14,Typeface.NORMAL,MUTED);intro.setLineSpacing(dp(4),1);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(10);ip.bottomMargin=dp(24);page.addView(intro,ip);

        page.addView(consentRow("Localisation précise","Enregistre ta position pendant les tournées et permet le guidage.",true),rowLp());
        page.addView(consentRow("Données & réseau","Cartes OpenFreeMap et calcul d’itinéraire OSRM nécessitent le réseau. Les GPX restent locaux jusqu’au partage.",false),rowLp());

        View spacer=new View(this);page.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        action=label("Commencer avec Routix",16,Typeface.BOLD,Color.WHITE);action.setGravity(Gravity.CENTER);action.setBackground(liquid(ACCENT,22));action.setOnClickListener(v->complete());page.addView(action,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView note=label("Ces choix sont mémorisés localement sur cet appareil.",10,Typeface.NORMAL,Color.argb(120,255,255,255));note.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(12);page.addView(note,np);
        setContentView(root);updateAction();
    }

    private View consentRow(String title,String body,boolean location){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(15),dp(15),dp(15),dp(15));row.setBackground(liquid(GLASS,22));
        TextView check=label("",16,Typeface.BOLD,Color.WHITE);check.setGravity(Gravity.CENTER);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(32),dp(32));cp.rightMargin=dp(13);row.addView(check,cp);
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(label(title,15,Typeface.BOLD,Color.WHITE));TextView b=label(body,11.5f,Typeface.NORMAL,MUTED);b.setLineSpacing(dp(3),1);tx.addView(b);row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        Runnable paint=()->{boolean on=location?locationConsent:dataConsent;check.setText(on?"✓":"");check.setBackground(liquid(on?ACCENT:Color.argb(60,255,255,255),99));};paint.run();
        row.setOnClickListener(v->{press(v);if(location)locationConsent=!locationConsent;else dataConsent=!dataConsent;paint.run();updateAction();});return row;
    }

    private void complete(){if(!locationConsent||!dataConsent)return;press(action);prefs.edit().putBoolean("onboarding_complete",true).putBoolean("privacy_notice_seen",true).putBoolean("location_usage_accepted",true).putBoolean("network_usage_accepted",true).apply();action.postDelayed(this::launchApp,170);}
    private void updateAction(){if(action==null)return;boolean on=locationConsent&&dataConsent;action.setEnabled(on);action.animate().alpha(on?1:.36f).setDuration(160).start();}
    private void launchApp(){startActivity(new Intent(this,RoutixActivity.class));finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
    private void press(View v){if(v==null)return;v.animate().scaleX(.965f).scaleY(.965f).setDuration(70).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(160).start()).start();}
    private LinearLayout.LayoutParams rowLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);return p;}
    private GradientDrawable liquid(int c,int r){int a=Color.alpha(c);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(Math.min(255,a+18),Math.min(255,Color.red(c)+24),Math.min(255,Color.green(c)+24),Math.min(255,Color.blue(c)+28)),c});g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.argb(58,255,255,255));return g;}
    private TextView label(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("locationConsent",locationConsent);out.putBoolean("dataConsent",dataConsent);}
}
