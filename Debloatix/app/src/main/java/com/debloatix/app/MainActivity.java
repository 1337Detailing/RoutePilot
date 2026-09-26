package com.debloatix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int C_BASE = Color.parseColor("#1E1E2E");
    private static final int C_MANTLE = Color.parseColor("#181825");
    private static final int C_SURFACE = Color.parseColor("#313244");
    private static final int C_LINE = Color.parseColor("#45475A");
    private static final int C_TEXT = Color.parseColor("#CDD6F4");
    private static final int C_MUTED = Color.parseColor("#A6ADC8");
    private static final int C_MAUVE = Color.parseColor("#CBA6F7");
    private static final int C_PINK = Color.parseColor("#F5C2E7");
    private static final int C_BLUE = Color.parseColor("#89B4FA");
    private static final int C_GREEN = Color.parseColor("#A6E3A1");
    private static final int C_PEACH = Color.parseColor("#FAB387");
    private static final int C_RED = Color.parseColor("#F38BA8");

    private static final int SHIZUKU_PERMISSION_REQUEST = 3117;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> visibleApps = new ArrayList<>();
    private final Collator collator = Collator.getInstance(Locale.getDefault());

    private PackageManager packageManager;
    private AppAdapter adapter;
    private EditText search;
    private TextView shizukuStatus;
    private TextView stats;
    private Button filterAll;
    private Button filterSystem;
    private Button filterUser;
    private Button connectButton;
    private String activeFilter = "all";
    private volatile IShellService shellService;
    private Shizuku.UserServiceArgs userServiceArgs;
    private SharedPreferences prefs;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        refreshShizukuUi();
        if (hasShizukuPermission()) bindShellService();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        shellService = null;
        refreshShizukuUi();
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        if (requestCode != SHIZUKU_PERMISSION_REQUEST) return;
        refreshShizukuUi();
        if (grantResult == PackageManager.PERMISSION_GRANTED) bindShellService();
    };

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            shellService = IShellService.Stub.asInterface(service);
            runOnUiThread(() -> {
                refreshShizukuUi();
                toast("Shizuku prêt");
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            shellService = null;
            runOnUiThread(MainActivity.this::refreshShizukuUi);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(C_BASE);
        window.setNavigationBarColor(C_MANTLE);

        packageManager = getPackageManager();
        prefs = getSharedPreferences("debloatix", MODE_PRIVATE);
        userServiceArgs = new Shizuku.UserServiceArgs(new ComponentName(this, ShellService.class))
                .daemon(false)
                .processNameSuffix("privileged")
                .tag("debloatix-shell")
                .version(1)
                .debuggable(BuildConfig.DEBUG);

        buildUi();

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);

        loadApps();
        refreshShizukuUi();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        if (Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, false);
            } catch (Throwable ignored) {}
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BASE);
        root.setPadding(dp(18), dp(12), dp(18), 0);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text("Debloatix", 31, C_TEXT, Typeface.BOLD);
        TextView subtitle = text("Système propre. Contrôle total.", 13, C_MUTED, Typeface.NORMAL);
        titleStack.addView(title);
        titleStack.addView(subtitle);

        shizukuStatus = pill("Shizuku", C_PEACH, C_MANTLE);
        titleRow.addView(titleStack);
        titleRow.addView(shizukuStatus);
        root.addView(titleRow);

        LinearLayout statusCard = card();
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout statusText = new LinearLayout(this);
        statusText.setOrientation(LinearLayout.VERTICAL);
        statusText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView statusTitle = text("Accès privilégié", 15, C_TEXT, Typeface.BOLD);
        TextView statusHint = text("Shizuku exécute les actions avec l'identité shell/root.", 12, C_MUTED, Typeface.NORMAL);
        statusText.addView(statusTitle);
        statusText.addView(statusHint);
        connectButton = actionButton("Connecter", C_MAUVE, C_MANTLE);
        connectButton.setOnClickListener(v -> connectShizuku());
        statusCard.addView(statusText);
        statusCard.addView(connectButton);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(20), 0, dp(14));
        root.addView(statusCard, statusParams);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Rechercher une app ou un package");
        search.setHintTextColor(C_MUTED);
        search.setTextColor(C_TEXT);
        search.setTextSize(15);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(roundRect(C_MANTLE, C_LINE, 1, 14));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        root.addView(search, searchParams);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        fp.setMargins(0, dp(12), dp(8), 0);
        filterAll = filterButton("Toutes", true);
        filterSystem = filterButton("Système", false);
        filterUser = filterButton("Utilisateur", false);
        filters.addView(filterAll, fp);
        filters.addView(filterSystem, fp);
        LinearLayout.LayoutParams lastFp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lastFp.setMargins(0, dp(12), 0, 0);
        filters.addView(filterUser, lastFp);
        root.addView(filters);

        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        stats = text("Chargement…", 12, C_MUTED, Typeface.NORMAL);
        stats.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button history = smallGhostButton("Historique");
        history.setOnClickListener(v -> showHistory());
        meta.addView(stats);
        meta.addView(history);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.setMargins(0, dp(10), 0, dp(8));
        root.addView(meta, metaParams);

        ListView list = new ListView(this);
        list.setDividerHeight(dp(8));
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(Color.TRANSPARENT);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showAppSheet(visibleApps.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        filterAll.setOnClickListener(v -> setFilter("all"));
        filterSystem.setOnClickListener(v -> setFilter("system"));
        filterUser.setOnClickListener(v -> setFilter("user"));

        setContentView(root);
    }

    private void loadApps() {
        stats.setText("Analyse des applications…");
        executor.execute(() -> {
            List<ApplicationInfo> raw;
            try {
                raw = packageManager.getInstalledApplications(PackageManager.GET_META_DATA | PackageManager.MATCH_UNINSTALLED_PACKAGES);
            } catch (Throwable t) {
                raw = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            }

            List<AppItem> loaded = new ArrayList<>();
            for (ApplicationInfo ai : raw) {
                if (ai.packageName.equals(getPackageName())) continue;
                String label;
                try {
                    CharSequence cs = ai.loadLabel(packageManager);
                    label = cs == null ? ai.packageName : cs.toString();
                } catch (Throwable t) {
                    label = ai.packageName;
                }
                boolean system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                loaded.add(new AppItem(label, ai.packageName, system, ai.enabled, ai));
            }
            Collections.sort(loaded, (a, b) -> collator.compare(a.label, b.label));

            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(loaded);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleApps.clear();
        int systemCount = 0;
        int userCount = 0;

        for (AppItem item : allApps) {
            if (item.system) systemCount++; else userCount++;
            if ("system".equals(activeFilter) && !item.system) continue;
            if ("user".equals(activeFilter) && item.system) continue;
            if (!q.isEmpty() && !item.label.toLowerCase(Locale.ROOT).contains(q)
                    && !item.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
            visibleApps.add(item);
        }

        adapter.notifyDataSetChanged();
        stats.setText(visibleApps.size() + " affichées · " + systemCount + " système · " + userCount + " utilisateur");
    }

    private void setFilter(String filter) {
        activeFilter = filter;
        styleFilter(filterAll, "all".equals(filter));
        styleFilter(filterSystem, "system".equals(filter));
        styleFilter(filterUser, "user".equals(filter));
        applyFilter();
    }

    private void connectShizuku() {
        if (!Shizuku.pingBinder()) {
            Intent launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (launch != null) {
                startActivity(launch);
                toast("Démarre Shizuku puis reviens dans Debloatix");
            } else {
                toast("Shizuku n'est pas installé ou n'est pas démarré");
            }
            return;
        }

        if (!hasShizukuPermission()) {
            try {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    toast("Autorise Debloatix dans Shizuku");
                } else {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                }
            } catch (Throwable t) {
                toast("Permission Shizuku impossible");
            }
            return;
        }
        bindShellService();
    }

    private boolean hasShizukuPermission() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void bindShellService() {
        if (shellService != null || !hasShizukuPermission()) return;
        try {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection);
            refreshShizukuUi();
        } catch (Throwable t) {
            toast("Impossible de lancer le service Shizuku");
        }
    }

    private void refreshShizukuUi() {
        if (shizukuStatus == null || connectButton == null) return;
        boolean binder = Shizuku.pingBinder();
        boolean permission = hasShizukuPermission();
        boolean ready = shellService != null;

        if (ready) {
            shizukuStatus.setText("● PRÊT");
            shizukuStatus.setTextColor(C_MANTLE);
            shizukuStatus.setBackground(roundRect(C_GREEN, Color.TRANSPARENT, 0, 20));
            connectButton.setText("Connecté");
            connectButton.setEnabled(false);
            connectButton.setAlpha(.75f);
        } else if (binder && permission) {
            shizukuStatus.setText("● CONNEXION");
            shizukuStatus.setTextColor(C_MANTLE);
            shizukuStatus.setBackground(roundRect(C_BLUE, Color.TRANSPARENT, 0, 20));
            connectButton.setText("Finaliser");
            connectButton.setEnabled(true);
            connectButton.setAlpha(1f);
        } else if (binder) {
            shizukuStatus.setText("● AUTORISATION");
            shizukuStatus.setTextColor(C_MANTLE);
            shizukuStatus.setBackground(roundRect(C_PEACH, Color.TRANSPARENT, 0, 20));
            connectButton.setText("Autoriser");
            connectButton.setEnabled(true);
            connectButton.setAlpha(1f);
        } else {
            shizukuStatus.setText("● HORS LIGNE");
            shizukuStatus.setTextColor(C_MANTLE);
            shizukuStatus.setBackground(roundRect(C_RED, Color.TRANSPARENT, 0, 20));
            connectButton.setText("Ouvrir Shizuku");
            connectButton.setEnabled(true);
            connectButton.setAlpha(1f);
        }
    }

    private void showAppSheet(AppItem item) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(18));
        box.setBackgroundColor(C_BASE);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(item.info.loadIcon(packageManager)); } catch (Throwable ignored) {}
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(52), dp(52));
        ip.setMargins(0, 0, dp(14), 0);
        head.addView(icon, ip);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(text(item.label, 20, C_TEXT, Typeface.BOLD));
        TextView pkg = text(item.packageName, 12, C_MUTED, Typeface.NORMAL);
        pkg.setTextIsSelectable(true);
        labels.addView(pkg);
        head.addView(labels);
        box.addView(head);

        TextView classification;
        if (isCritical(item.packageName)) {
            classification = pill("PROTÉGÉ", C_RED, C_MANTLE);
        } else if (item.system) {
            classification = pill("SYSTÈME · PRUDENCE", C_PEACH, C_MANTLE);
        } else {
            classification = pill("UTILISATEUR", C_BLUE, C_MANTLE);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, dp(18), 0, dp(10));
        box.addView(classification, cp);

        TextView warning = text(
                isCritical(item.packageName)
                        ? "Debloatix bloque les actions privilégiées sur ce composant car il fait partie du cœur Android."
                        : item.system
                        ? "Retirer pour l'utilisateur 0 conserve normalement l'APK système et permet une restauration. Certains composants constructeur restent néanmoins indispensables."
                        : "Pour une application utilisateur, Debloatix utilise le désinstalleur Android standard.",
                13, C_MUTED, Typeface.NORMAL);
        warning.setLineSpacing(0, 1.15f);
        box.addView(warning);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(0, dp(18), 0, 0);
        box.addView(actions, ap);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(roundRect(C_BASE, C_LINE, 1, 22));
        });

        if (item.system && !isCritical(item.packageName)) {
            Button remove = dialogButton("Retirer pour l'utilisateur 0", C_MAUVE, C_MANTLE);
            remove.setOnClickListener(v -> confirmPrivilegedAction(item, "remove", dialog));
            actions.addView(remove, actionLp());

            Button disable = dialogButton("Désactiver", C_SURFACE, C_TEXT);
            disable.setOnClickListener(v -> confirmPrivilegedAction(item, "disable", dialog));
            actions.addView(disable, actionLp());

            Button enable = dialogButton("Réactiver", C_SURFACE, C_TEXT);
            enable.setOnClickListener(v -> runPackageAction(item, "enable", dialog));
            actions.addView(enable, actionLp());
        } else if (!item.system) {
            Button uninstall = dialogButton("Désinstaller", C_MAUVE, C_MANTLE);
            uninstall.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + item.packageName));
                startActivity(intent);
                dialog.dismiss();
            });
            actions.addView(uninstall, actionLp());
        }

        Button details = dialogButton("Informations Android", C_SURFACE, C_TEXT);
        details.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + item.packageName));
            startActivity(i);
        });
        actions.addView(details, actionLp());

        dialog.show();
    }

    private void confirmPrivilegedAction(AppItem item, String action, AlertDialog parent) {
        String verb = "remove".equals(action) ? "retirer" : "désactiver";
        new AlertDialog.Builder(this)
                .setTitle("Confirmer")
                .setMessage("Tu vas " + verb + " « " + item.label + " » (" + item.packageName + ").\n\nUn composant système peut être nécessaire à OriginOS/Android même s'il semble inutile.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Continuer", (d, which) -> runPackageAction(item, action, parent))
                .show();
    }

    private void runPackageAction(AppItem item, String action, DialogInterface dialog) {
        if (!item.system || isCritical(item.packageName)) {
            toast("Action bloquée par la protection Debloatix");
            return;
        }
        if (!PACKAGE_PATTERN.matcher(item.packageName).matches()) {
            toast("Nom de package invalide");
            return;
        }
        if (shellService == null) {
            toast("Connecte Shizuku d'abord");
            connectShizuku();
            return;
        }

        String cmd;
        if ("remove".equals(action)) {
            cmd = "pm uninstall --user 0 " + item.packageName;
        } else if ("disable".equals(action)) {
            cmd = "pm disable-user --user 0 " + item.packageName;
        } else {
            cmd = "pm enable " + item.packageName;
        }

        toast("Action en cours…");
        executor.execute(() -> {
            try {
                String result = shellService.exec(cmd);
                boolean ok = result != null && result.startsWith("EXIT=0");
                if (ok && "remove".equals(action)) rememberRemoved(item);
                runOnUiThread(() -> {
                    if (ok) {
                        toast("Terminé");
                        if (dialog != null) dialog.dismiss();
                        loadApps();
                    } else {
                        showResult("Échec", result);
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    shellService = null;
                    refreshShizukuUi();
                    showResult("Erreur Shizuku", t.getClass().getSimpleName() + ": " + t.getMessage());
                });
            }
        });
    }

    private void showHistory() {
        Set<String> removed = new HashSet<>(prefs.getStringSet("removed", Collections.emptySet()));
        if (removed.isEmpty()) {
            toast("Aucune application retirée par Debloatix");
            return;
        }

        String[] entries = removed.toArray(new String[0]);
        java.util.Arrays.sort(entries);
        new AlertDialog.Builder(this)
                .setTitle("Applications retirées")
                .setItems(entries, (dialog, which) -> restorePackage(entries[which]))
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void restorePackage(String packageName) {
        if (!PACKAGE_PATTERN.matcher(packageName).matches() || shellService == null) {
            toast(shellService == null ? "Connecte Shizuku d'abord" : "Package invalide");
            return;
        }
        executor.execute(() -> {
            try {
                String result = shellService.exec("cmd package install-existing --user 0 " + packageName);
                boolean ok = result != null && result.startsWith("EXIT=0");
                if (ok) {
                    Set<String> removed = new HashSet<>(prefs.getStringSet("removed", Collections.emptySet()));
                    removed.remove(packageName);
                    prefs.edit().putStringSet("removed", removed).apply();
                }
                runOnUiThread(() -> {
                    if (ok) {
                        toast("Application restaurée");
                        loadApps();
                    } else {
                        showResult("Restauration impossible", result);
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> showResult("Erreur Shizuku", String.valueOf(t.getMessage())));
            }
        });
    }

    private void rememberRemoved(AppItem item) {
        Set<String> removed = new HashSet<>(prefs.getStringSet("removed", Collections.emptySet()));
        removed.add(item.packageName);
        prefs.edit().putStringSet("removed", removed).apply();
    }

    private boolean isCritical(String p) {
        return p.equals("android")
                || p.equals("com.android.systemui")
                || p.equals("com.android.settings")
                || p.equals("com.android.phone")
                || p.equals("com.android.providers.settings")
                || p.equals("com.android.permissioncontroller")
                || p.equals("com.google.android.permissioncontroller")
                || p.startsWith("com.android.providers.")
                || p.contains(".launcher")
                || p.contains(".systemui");
    }

    private void showResult(String title, String result) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(result == null || result.trim().isEmpty() ? "Aucun détail retourné." : result)
                .setPositiveButton("OK", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(roundRect(C_MANTLE, C_LINE, 1, 16));
        return l;
    }

    private Button filterButton(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setPadding(dp(8), 0, dp(8), 0);
        styleFilter(b, active);
        return b;
    }

    private void styleFilter(Button b, boolean active) {
        b.setTextColor(active ? C_MANTLE : C_MUTED);
        b.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        b.setBackground(roundRect(active ? C_MAUVE : C_MANTLE, active ? Color.TRANSPARENT : C_LINE, active ? 0 : 1, 12));
    }

    private Button actionButton(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(roundRect(bg, Color.TRANSPARENT, 0, 12));
        b.setMinHeight(dp(42));
        return b;
    }

    private Button smallGhostButton(String label) {
        Button b = actionButton(label, C_MANTLE, C_MUTED);
        b.setBackground(roundRect(C_MANTLE, C_LINE, 1, 12));
        return b;
    }

    private Button dialogButton(String label, int bg, int fg) {
        Button b = actionButton(label, bg, fg);
        b.setTextSize(14);
        b.setMinHeight(dp(50));
        return b;
    }

    private LinearLayout.LayoutParams actionLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.setMargins(0, 0, 0, dp(9));
        return p;
    }

    private TextView pill(String label, int bg, int fg) {
        TextView tv = text(label, 11, fg, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(11), dp(7), dp(11), dp(7));
        tv.setBackground(roundRect(bg, Color.TRANSPARENT, 0, 20));
        return tv;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        return tv;
    }

    private GradientDrawable roundRect(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleApps.size(); }
        @Override public Object getItem(int position) { return visibleApps.get(position); }
        @Override public long getItemId(int position) { return visibleApps.get(position).packageName.hashCode(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppItem item = visibleApps.get(position);

            LinearLayout row = card();
            row.setPadding(dp(14), dp(12), dp(12), dp(12));

            ImageView icon = new ImageView(MainActivity.this);
            try { icon.setImageDrawable(item.info.loadIcon(packageManager)); } catch (Throwable ignored) {}
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(44), dp(44));
            ip.setMargins(0, 0, dp(13), 0);
            row.addView(icon, ip);

            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView label = text(item.label, 15, C_TEXT, Typeface.BOLD);
            label.setMaxLines(1);
            TextView pkg = text(item.packageName, 11, C_MUTED, Typeface.NORMAL);
            pkg.setMaxLines(1);
            labels.addView(label);
            labels.addView(pkg);
            row.addView(labels);

            TextView badge;
            if (isCritical(item.packageName)) badge = pill("PROTÉGÉ", C_RED, C_MANTLE);
            else if (item.system) badge = pill("SYS", C_PEACH, C_MANTLE);
            else badge = pill("USER", C_BLUE, C_MANTLE);
            row.addView(badge);

            return row;
        }
    }

    private static final class AppItem {
        final String label;
        final String packageName;
        final boolean system;
        final boolean enabled;
        final ApplicationInfo info;

        AppItem(String label, String packageName, boolean system, boolean enabled, ApplicationInfo info) {
            this.label = label;
            this.packageName = packageName;
            this.system = system;
            this.enabled = enabled;
            this.info = info;
        }
    }
}
