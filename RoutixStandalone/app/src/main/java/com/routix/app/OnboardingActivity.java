package com.routix.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** First launch only asks for the permission Routix actually needs on the road. */
public class OnboardingActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST=41;
    private static final int BG=Color.rgb(7,8,12),ACCENT=Color.rgb(203,166,247),GLASS=Color.argb(178,31,34,44),MUTED=Color.argb(184,255,255,255);
    private SharedPreferences prefs;private TextView action,status;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("routix",MODE_PRIVATE);
        if(prefs.getBoolean("onboarding_complete",false)&&hasLocation()){launchApp();return;}
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(BG);

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(42),dp(22),dp(24));root.addView(page,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.routix_logo_v2);logo.setScaleType(ImageView.ScaleType.CENTER_CROP);logo.setBackground(liquid(Color.argb(70,137,180,250),18));brand.addView(logo,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout bt=new LinearLayout(this);bt.setOrientation(LinearLayout.VERTICAL);bt.setPadding(dp(12),0,0,0);bt.addView(label("Routix",19,Typeface.BOLD,Color.WHITE));bt.addView(label("Prêt pour le terrain",10,Typeface.NORMAL,MUTED));brand.addView(bt,new LinearLayout.LayoutParams(0,-2,1));page.addView(brand);

        TextView title=label("Autoriser la localisation",30,Typeface.BOLD,Color.WHITE);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(48);page.addView(title,tp);
        TextView intro=label("Routix utilise la position précise pour enregistrer une tournée et te guider sur son tracé.",14,Typeface.NORMAL,MUTED);intro.setLineSpacing(dp(4),1);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(10);ip.bottomMargin=dp(24);page.addView(intro,ip);

        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(17),dp(17),dp(17),dp(17));card.setBackground(liquid(GLASS,22));
        card.addView(label("Position précise",15,Typeface.BOLD,Color.WHITE));TextView body=label("Nécessaire au suivi GPS. Tes tournées restent sur l’appareil tant que tu ne les partages pas.",12,Typeface.NORMAL,MUTED);body.setLineSpacing(dp(3),1);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(7);card.addView(body,bp);
        status=label("",11,Typeface.BOLD,ACCENT);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(12);card.addView(status,sp);page.addView(card,new LinearLayout.LayoutParams(-1,-2));

        View spacer=new View(this);page.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        action=label("Autoriser et continuer",16,Typeface.BOLD,Color.WHITE);action.setGravity(Gravity.CENTER);action.setBackground(liquid(ACCENT,22));action.setOnClickListener(v->{press(v);requestLocation();});page.addView(action,new LinearLayout.LayoutParams(-1,dp(58)));
        setContentView(root);refresh();
    }

    private boolean hasLocation(){return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void requestLocation(){
        if(hasLocation()){complete();return;}
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode!=LOCATION_REQUEST)return;
        if(hasLocation())complete();else refresh();
    }
    @Override protected void onResume(){super.onResume();if(action!=null){if(hasLocation())complete();else refresh();}}
    private void complete(){
        if(!hasLocation())return;
        prefs.edit().putBoolean("onboarding_complete",true).putBoolean("privacy_notice_seen",true).putBoolean("location_usage_accepted",true).apply();
        launchApp();
    }
    private void refresh(){if(action==null)return;boolean denied=ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION);status.setText(denied?"Autorisation refusée • réessaie pour utiliser le GPS":"Android te demandera l’autorisation au prochain appui");action.setText(denied?"Réessayer":"Autoriser et continuer");}
    private void launchApp(){startActivity(new Intent(this,RoutixActivity.class));finish();overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
    private void press(View v){if(v==null)return;v.animate().scaleX(.965f).scaleY(.965f).setDuration(70).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(160).start()).start();}
    private GradientDrawable liquid(int c,int r){int a=Color.alpha(c);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(Math.min(255,a+18),Math.min(255,Color.red(c)+24),Math.min(255,Color.green(c)+24),Math.min(255,Color.blue(c)+28)),c});g.setCornerRadius(dp(r));g.setStroke(dp(1),Color.argb(58,255,255,255));return g;}
    private TextView label(String s,float sp,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
