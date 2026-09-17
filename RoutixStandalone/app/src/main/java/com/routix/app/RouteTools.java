package com.routix.app;

import android.app.Dialog;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Route library tools. Slow geometry, disk scans and street requests run off the UI thread. */
final class RouteTools {
    private final RoutixActivity host;private final RouteStore store;private final SharedPreferences prefs;
    private final StreetIndex streets;private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Dialog> dialogs=new ArrayList<>();private RouteStudyMap study;
    private boolean closed;
    RouteTools(RoutixActivity host,RouteStore store,SharedPreferences prefs){this.host=host;this.store=store;this.prefs=prefs;streets=new StreetIndex(host);}
    int dp(int n){return (int)(n*host.getResources().getDisplayMetrics().density+.5f);}
    TextView text(String value,int size){TextView v=new TextView(host);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,dp(8),0,dp(8));return v;}
    LinearLayout column(){LinearLayout l=new LinearLayout(host);l.setOrientation(LinearLayout.VERTICAL);return l;}
    void note(String value){Toast.makeText(host,value,Toast.LENGTH_LONG).show();}
    final class Sheet {
        final Dialog dialog=new Dialog(host);final LinearLayout body=column();
        Sheet(String title){
            LinearLayout panel=column();panel.setPadding(dp(18),dp(12),dp(18),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(22,25,33));bg.setCornerRadius(dp(24));panel.setBackground(bg);
            TextView heading=text(title,22);heading.setTypeface(null,Typeface.BOLD);panel.addView(heading);
            ScrollView scroll=new ScrollView(host);scroll.setFillViewport(false);scroll.addView(body);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            Button close=new Button(host);close.setText("Fermer");close.setOnClickListener(v->dialog.dismiss());panel.addView(close);
            dialog.setContentView(panel);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setLayout(-1,(int)(host.getResources().getDisplayMetrics().heightPixels*.86));}
            dialogs.add(dialog);dialog.setOnDismissListener(d->dialogs.remove(dialog));
        }
        void info(String value){body.addView(text(value,14));}
        void action(String label,Runnable action){Button b=new Button(host);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(35,65,93));bg.setCornerRadius(dp(16));b.setBackground(bg);b.setMinHeight(dp(50));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(7);body.addView(b,lp);b.setOnClickListener(v->action.run());}
    }
    interface Task<T>{T run()throws Exception;}
    interface Done<T>{void accept(T value);}
    private <T> void job(String title,Task<T> task,Done<T> done){
        Sheet progress=new Sheet(title);progress.info("Traitement en cours… Tu peux fermer cette fenêtre pour ignorer le résultat.");
        worker.execute(()->{try{T result=task.run();host.runOnUiThread(()->{if(closed||!progress.dialog.isShowing())return;progress.dialog.dismiss();done.accept(result);});}
            catch(Exception e){host.runOnUiThread(()->{if(closed||!progress.dialog.isShowing())return;progress.dialog.dismiss();note(e.getMessage()==null?"Opération impossible. Réessaie.":e.getMessage());});}});
    }
    void library(){Sheet s=new Sheet("Outils de tournée");s.action("Rechercher une rue",()->{s.dialog.dismiss();search();});s.action("Calendrier de collecte",()->{s.dialog.dismiss();calendar(LocalDate.now());});s.action("Comparer deux tournées",()->{s.dialog.dismiss();pick("Tournée de référence",null,this::comparePicker);});s.action("Reconnaissance virtuelle",()->{s.dialog.dismiss();pick("Reconnaître une tournée",null,this::recon);});s.action("Historique des versions",()->{s.dialog.dismiss();pick("Historique d’une tournée",null,this::history);});if(DumpCheckpoint.load(prefs)!=null)s.action("Point de vidage sauvegardé",()->{s.dialog.dismiss();host.offerDumpRecovery();});}
    void route(RouteStore.Summary route){Sheet s=new Sheet(store.displayName(route.file));s.action("Reconnaissance virtuelle",()->{s.dialog.dismiss();recon(route);});s.action("Comparer avec une autre tournée",()->{s.dialog.dismiss();comparePicker(route);});s.action("Programmer la collecte",()->{s.dialog.dismiss();schedule(route);});s.action("Historique des versions",()->{s.dialog.dismiss();history(route);});s.action("Préparer la recherche par rue",()->{s.dialog.dismiss();index(Collections.singletonList(route));});}
    private void pick(String title,File exclude,Done<RouteStore.Summary> done){
        job("Chargement des tournées",store::summaries,routes->{Sheet s=new Sheet(title);int count=0;for(RouteStore.Summary r:routes)if(!r.file.equals(exclude)){count++;s.action(store.displayName(r.file),()->{s.dialog.dismiss();done.accept(r);});}if(count==0)s.info("Aucune autre tournée disponible.");});
    }
    private void comparePicker(RouteStore.Summary first){pick("Comparer à "+store.displayName(first.file),first.file,second->job("Comparaison des tracés",()->RouteComparison.compare(first.points,second.points),result->{
        if(study!=null)study.close();study=new RouteStudyMap(host,store,first,second,result,streets.read(first.file),streets.read(second.file));study.show();
    }));}
    private void recon(RouteStore.Summary r){if(r.points.size()<2){note("Trace insuffisante");return;}if(study!=null)study.close();study=new RouteStudyMap(host,store,r,null,null,null,streets.read(r.file));study.show();}

    private void index(List<RouteStore.Summary> routes){
        Sheet s=new Sheet("Préparer les noms de rues");s.info("La zone des tournées sera envoyée à OpenStreetMap Overpass pour retrouver les rues. Les résultats seront conservés sur le téléphone pour les recherches hors ligne. Une rue proche du tracé peut être ambiguë : vérifie les résultats sur le plan.");
        s.action("Préparer "+routes.size()+" tournée(s)",()->{s.dialog.dismiss();job("Recherche des rues",()->{
            int ok=0;StringBuilder errors=new StringBuilder();for(RouteStore.Summary r:routes){if(Thread.currentThread().isInterrupted())throw new InterruptedException();try{streets.build(host,r);ok++;}catch(Exception e){errors.append("\n").append(store.displayName(r.file)).append(" : ").append(e.getMessage());}}
            return ok+" tournée(s) préparée(s)."+errors;
        },message->{Sheet result=new Sheet("Noms de rues");result.info(message);result.action("Ouvrir la recherche",()->{result.dialog.dismiss();search();});});});
    }
    private void search(){
        job("Chargement de l’index",()->{Map<RouteStore.Summary,List<PaperRoute.Step>> all=new LinkedHashMap<>();for(RouteStore.Summary r:store.summaries())all.put(r,streets.read(r.file));return all;},all->{
            Sheet s=new Sheet("Rechercher une rue");int missing=0;for(List<PaperRoute.Step> steps:all.values())if(steps==null)missing++;
            s.info("Recherche dans les noms de rues préparés et les repères. "+missing+" tournée(s) sans index : leurs rues ne sont pas encore recherchables.");
            EditText input=new EditText(host);input.setTextColor(Color.WHITE);input.setHintTextColor(Color.LTGRAY);input.setHint("Ex. rue du Cerf");input.setSingleLine();s.body.addView(input);
            LinearLayout results=column();s.body.addView(results);
            Runnable render=()->{results.removeAllViews();String q=StreetIndex.normalize(input.getText().toString());if(q.length()<2){results.addView(text("Saisis au moins deux caractères.",14));return;}int hits=0;
                for(Map.Entry<RouteStore.Summary,List<PaperRoute.Step>> entry:all.entrySet()){
                    RouteStore.Summary r=entry.getKey();Set<String> matches=new LinkedHashSet<>();if(entry.getValue()!=null)for(PaperRoute.Step step:entry.getValue())if(StreetIndex.normalize(step.name).contains(q))matches.add(step.name);
                    for(RouteStore.Event e:r.events)if(StreetIndex.normalize(e.label).contains(q))matches.add(e.label+" (repère)");
                    if(!matches.isEmpty()){hits++;Button b=new Button(host);b.setAllCaps(false);b.setText(store.displayName(r.file)+"\n"+android.text.TextUtils.join(" • ",matches));b.setOnClickListener(v->{s.dialog.dismiss();recon(r);});results.addView(b);}
                }if(hits==0)results.addView(text("Aucun résultat dans les données préparées.",14));};
            input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int c,int z){}public void onTextChanged(CharSequence x,int a,int b,int c){render.run();}public void afterTextChanged(android.text.Editable x){}});render.run();
            s.action("Préparer / actualiser les noms de rues",()->{s.dialog.dismiss();index(new ArrayList<>(all.keySet()));});
        });
    }
    private JSONObject scheduleFor(File f){try{return new JSONObject(prefs.getString("collection_"+f.getName(),"{}"));}catch(Exception e){return new JSONObject();}}
    private void schedule(RouteStore.Summary r){
        Sheet s=new Sheet("Collecte · "+store.displayName(r.file));JSONObject saved=scheduleFor(r.file);s.info("Récurrence hebdomadaire. La parité suit le numéro de semaine ISO (lundi à dimanche).");
        CheckBox[] days=new CheckBox[7];String[] names={"Lundi","Mardi","Mercredi","Jeudi","Vendredi","Samedi","Dimanche"};for(int i=0;i<7;i++){days[i]=new CheckBox(host);days[i].setText(names[i]);days[i].setTextColor(Color.WHITE);days[i].setChecked((saved.optInt("days")&(1<<i))!=0);s.body.addView(days[i]);}
        Spinner parity=new Spinner(host);parity.setAdapter(new ArrayAdapter<>(host,android.R.layout.simple_spinner_dropdown_item,new String[]{"Toutes les semaines","Semaines paires","Semaines impaires"}));parity.setSelection(saved.optInt("parity"));s.body.addView(parity);
        EditText waste=new EditText(host);waste.setTextColor(Color.WHITE);waste.setHintTextColor(Color.LTGRAY);waste.setHint("Type de déchets : ordures, tri, verre…");waste.setText(saved.optString("waste"));s.body.addView(waste);
        s.action("Enregistrer",()->{int mask=0;for(int i=0;i<7;i++)if(days[i].isChecked())mask|=1<<i;if(mask==0){note("Choisis au moins un jour");return;}if(waste.getText().toString().trim().isEmpty()){note("Indique le type de déchets");return;}
            try{JSONObject j=new JSONObject().put("days",mask).put("parity",parity.getSelectedItemPosition()).put("waste",waste.getText().toString().trim());if(!prefs.edit().putString("collection_"+r.file.getName(),j.toString()).commit()){note("Enregistrement impossible");return;}s.dialog.dismiss();calendar(LocalDate.now());}catch(Exception e){note("Enregistrement impossible");}});
        if(saved.has("days"))s.action("Supprimer cette programmation",()->{prefs.edit().remove("collection_"+r.file.getName()).apply();s.dialog.dismiss();calendar(LocalDate.now());});
    }
    private void calendar(LocalDate start){
        job("Chargement du calendrier",store::summaries,routes->{Sheet s=new Sheet("Calendrier de collecte");s.info("Du "+start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" · 28 jours");
            s.action("Choisir une date",()->new DatePickerDialog(host,(v,y,m,d)->{s.dialog.dismiss();calendar(LocalDate.of(y,m+1,d));},start.getYear(),start.getMonthValue()-1,start.getDayOfMonth()).show());
            s.action("Programmer une tournée",()->{s.dialog.dismiss();pick("Tournée à programmer",null,this::schedule);});
            boolean any=false;for(int day=0;day<28;day++){LocalDate date=start.plusDays(day);boolean heading=false;for(RouteStore.Summary r:routes){JSONObject j=scheduleFor(r.file);if(CollectionSchedule.occurs(date,j.optInt("days"),j.optInt("parity"))){any=true;if(!heading){s.info(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM",Locale.FRANCE)));heading=true;}s.action(store.displayName(r.file)+" · "+j.optString("waste"),()->{s.dialog.dismiss();Sheet choice=new Sheet(store.displayName(r.file));choice.action("Démarrer la tournée",()->{choice.dialog.dismiss();host.startToolGuidance(r);});choice.action("Modifier la programmation",()->{choice.dialog.dismiss();schedule(r);});});}}}
            if(!any)s.info("Aucune collecte programmée sur cette période.");s.action("28 jours suivants",()->{s.dialog.dismiss();calendar(start.plusDays(28));});
        });
    }
    private void history(RouteStore.Summary r){
        job("Lecture de l’historique",()->{
            if(store.archive.versions(r.file).isEmpty()){File original=store.originalFor(r.file);if(original!=null)store.archive.captureAs(r.file,original,store.displayName(r.file),"Fichier importé original");}
            store.archive.capture(r.file,store.displayName(r.file),"Version actuelle");return store.archive.versions(r.file);
        },versions->{Sheet s=new Sheet("Historique · "+store.displayName(r.file));s.info("Chaque restauration sauvegarde d’abord l’état actuel. Les versions antérieures à cette mise à jour ne sont disponibles que si le fichier importé original a été conservé.");
            s.action("Créer un point de sauvegarde",()->{s.dialog.dismiss();job("Sauvegarde",()->{store.archive.capture(r.file,store.displayName(r.file),"Sauvegarde manuelle");return true;},x->history(r));});
            for(RouteArchive.Version v:versions)s.action(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.FRANCE).format(new Date(v.time))+"\n"+v.name+" · "+v.reason,()->{
                Sheet confirm=new Sheet("Restaurer cette version ?");confirm.info("Le tracé et le nom seront restaurés. Le calendrier restera inchangé. L’index des rues sera recalculé à la prochaine préparation si le tracé diffère.");
                confirm.action("Restaurer",()->{confirm.dialog.dismiss();s.dialog.dismiss();job("Restauration",()->store.restore(r.file,v),ok->{if(ok){note("Version restaurée");host.refreshToolHistory();}else note("Restauration impossible : la version actuelle a été conservée.");});});
            });
        });
    }
    void pause(){if(study!=null)study.pause();}
    void resume(){if(study!=null)study.resume();}
    void destroy(){closed=true;worker.shutdownNow();if(study!=null)study.close();for(Dialog d:new ArrayList<>(dialogs))d.dismiss();}
}
