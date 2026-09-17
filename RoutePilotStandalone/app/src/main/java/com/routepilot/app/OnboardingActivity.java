package com.routepilot.app;

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
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(8, 9, 12);
    private static final int BLUE = Color.rgb(10, 132, 255);
    private static final int GLASS = Color.argb(235, 31, 32, 38);
    private static final int MUTED = Color.argb(180, 255, 255, 255);

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout pageHost;
    private LinearLayout dots;
    private TextView action;
    private int step = 0;
    private CheckBox terms;
    private CheckBox localData;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("routepilot", MODE_PRIVATE);
        if (prefs.getBoolean("onboarding_complete", false)) {
            launchApp();
            return;
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(24), dp(34), dp(24), dp(22));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = label("RP", 15, Typeface.BOLD, Color.WHITE);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(BLUE, 18));
        top.addView(mark, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView brand = label("RoutePilot", 18, Typeface.BOLD, Color.WHITE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f); bp.leftMargin = dp(11);
        top.addView(brand, bp);
        TextView skip = label("Passer", 13, Typeface.BOLD, MUTED);
        skip.setPadding(dp(12), dp(10), dp(12), dp(10));
        skip.setOnClickListener(v -> { step = 3; render(); });
        top.addView(skip);
        shell.addView(top);

        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ph = new LinearLayout.LayoutParams(-1, 0, 1f); ph.topMargin = dp(30);
        shell.addView(pageHost, ph);

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, dp(24)); dl.bottomMargin = dp(12);
        shell.addView(dots, dl);

        action = label("Continuer", 16, Typeface.BOLD, Color.WHITE);
        action.setGravity(Gravity.CENTER);
        action.setBackground(round(BLUE, 20));
        action.setOnClickListener(v -> next());
        shell.addView(action, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView version = label("RoutePilot 1.0 • conçu pour les tournées terrain", 10, Typeface.NORMAL, Color.argb(110,255,255,255));
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2); vp.topMargin = dp(13);
        shell.addView(version, vp);

        setContentView(root);
        render();
    }

    private void render() {
        pageHost.animate().alpha(0f).translationX(-dp(12)).setDuration(110).withEndAction(() -> {
            pageHost.removeAllViews();
            pageHost.setAlpha(0f);
            pageHost.setTranslationX(dp(18));
            if (step == 0) intro();
            else if (step == 1) features();
            else if (step == 2) offline();
            else consent();
            pageHost.animate().alpha(1f).translationX(0).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
            updateDots();
        }).start();
    }

    private void intro() {
        hero("⌁", "Enregistre. Rejoue.\nMaîtrise tes tournées.",
                "RoutePilot mémorise précisément le trajet de ta tournée et les actions métier pour te permettre de la refaire plus tard.");
        action.setText("Découvrir RoutePilot");
        action.setEnabled(true); action.setAlpha(1f);
    }

    private void features() {
        hero("◎", "Un GPS pensé pour le terrain",
                "Enregistre la trace réelle, ajoute Marche arrière et 2 côtés au bon endroit, puis retrouve chaque repère dans l’ordre.");
        featureCard("↶  Marche arrière", "Un repère géolocalisé au moment exact où tu appuies.");
        featureCard("⇆  2 côtés", "Conservé dans la tournée et visible pendant la relecture.");
        featureCard("▶  Relecture guidée", "Suis la trace d’origine sans recalcul qui change ta tournée.");
        action.setText("Continuer");
        action.setEnabled(true); action.setAlpha(1f);
    }

    private void offline() {
        hero("▧", "Online ou hors ligne",
                "OpenStreetMap est utilisé en ligne sans clé API. Tu peux aussi télécharger des cartes vectorielles françaises et les activer depuis Réglages.");
        featureCard("France hors ligne", "Installe uniquement les régions nécessaires et garde plusieurs cartes sur le téléphone.");
        featureCard("Données locales", "Les tournées GPX restent sur ton appareil tant que tu ne les partages pas.");
        action.setText("Continuer");
        action.setEnabled(true); action.setAlpha(1f);
    }

    private void consent() {
        hero("✓", "Dernière étape",
                "RoutePilot utilise ta position précise pour enregistrer et rejouer une tournée. Tu gardes le contrôle sur les fichiers créés.");

        terms = check("J’ai lu et j’accepte les conditions d’utilisation de RoutePilot.");
        localData = check("Je comprends que mes tournées sont enregistrées localement sur cet appareil.");
        pageHost.addView(terms, checkParams());
        pageHost.addView(localData, checkParams());
        terms.setOnCheckedChangeListener((b,c) -> updateConsentButton());
        localData.setOnCheckedChangeListener((b,c) -> updateConsentButton());
        action.setText("Commencer avec RoutePilot");
        updateConsentButton();
    }

    private void hero(String icon, String title, String body) {
        TextView orb = label(icon, 36, Typeface.BOLD, Color.WHITE);
        orb.setGravity(Gravity.CENTER);
        orb.setBackground(round(Color.argb(120, 10, 132, 255), 34));
        pageHost.addView(orb, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView t = label(title, 31, Typeface.BOLD, Color.WHITE);
        t.setLineSpacing(0, 0.94f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2); tp.topMargin = dp(28);
        pageHost.addView(t, tp);
        TextView b = label(body, 15, Typeface.NORMAL, MUTED);
        b.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2); bl.topMargin = dp(14); bl.bottomMargin = dp(22);
        pageHost.addView(b, bl);
    }

    private void featureCard(String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(GLASS, 22));
        card.addView(label(title, 15, Typeface.BOLD, Color.WHITE));
        TextView b = label(body, 12, Typeface.NORMAL, MUTED);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.topMargin = dp(4);
        card.addView(b, bp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.bottomMargin = dp(9);
        pageHost.addView(card, cp);
    }

    private CheckBox check(String s) {
        CheckBox c = new CheckBox(this);
        c.setText(s); c.setTextColor(Color.WHITE); c.setTextSize(13); c.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
        c.setPadding(dp(12), dp(10), dp(12), dp(10)); c.setBackground(round(GLASS, 18));
        return c;
    }

    private LinearLayout.LayoutParams checkParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(10); return p;
    }

    private void updateConsentButton() {
        boolean ok = terms != null && localData != null && terms.isChecked() && localData.isChecked();
        action.setEnabled(ok); action.setAlpha(ok ? 1f : .38f);
    }

    private void updateDots() {
        dots.removeAllViews();
        for (int i=0;i<4;i++) {
            View d = new View(this);
            d.setBackground(round(i == step ? BLUE : Color.argb(70,255,255,255), 9));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(i == step ? 24 : 7), dp(7)); p.setMargins(dp(4),0,dp(4),0);
            dots.addView(d,p);
        }
    }

    private void next() {
        action.animate().scaleX(.97f).scaleY(.97f).setDuration(60).withEndAction(() -> action.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
        if (step < 3) { step++; render(); return; }
        if (!action.isEnabled()) return;
        prefs.edit().putBoolean("onboarding_complete", true).putBoolean("terms_accepted", true).apply();
        launchApp();
    }

    private void launchApp() {
        startActivity(new Intent(this, RoutePilotProActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private TextView label(String s, float sp, int style, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTypeface(Typeface.create("sans-serif", style)); t.setTextColor(color); t.setIncludeFontPadding(false); return t;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),Color.argb(30,255,255,255)); return g; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
