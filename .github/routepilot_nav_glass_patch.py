from pathlib import Path

activity = Path('RoutePilotStandalone/app/src/main/java/com/routepilot/app/RoutePilotProActivity.java')
build = Path('RoutePilotStandalone/app/build.gradle')
s = activity.read_text()


def replace_between(start_marker, end_marker, replacement):
    global s
    a = s.index(start_marker)
    b = s.index(end_marker, a)
    s = s[:a] + replacement.rstrip() + '\n\n' + s[b:]

# Navigation HUD fields and path state.
s = s.replace(
    '    private TextView guideTitle,guideSubtitle,guideProgress,guideRemaining,guideNext,guideDeviation,guideEventAlert;\n',
    '    private TextView guideArrow,guideTitle,guideSubtitle,guideProgress,guideRemaining,guideNext,guideDeviation,guideEventAlert,guideSpeed;\n'
)
s = s.replace(
    '    private String lastEventAlertKey="";\n',
    '    private String lastEventAlertKey="";\n    private final List<GuidanceEngine.Point> guidancePath=new ArrayList<>();\n'
)

# Custom vehicle marker on the OSM location overlay.
s = s.replace(
    '        me=new MyLocationNewOverlay(new GpsMyLocationProvider(this),map);me.setDrawAccuracyEnabled(true);map.getOverlays().add(me);',
    '        me=new MyLocationNewOverlay(new GpsMyLocationProvider(this),map);me.setDrawAccuracyEnabled(true);applyPositionIcon();map.getOverlays().add(me);'
)

# Keep an oriented copy of the path used by GuidanceEngine.
s = s.replace(
    'guidance=new GuidanceEngine(gp,ge);guidanceHud=buildGuidanceHud();',
    'guidancePath.clear();guidancePath.addAll(gp);guidance=new GuidanceEngine(gp,ge);guidanceHud=buildGuidanceHud();'
)

# Replace the guidance HUD with a classic GPS / Waze-like layout.
replace_between('    private View buildGuidanceHud(){', '    private void updateGuidance(Location l){', r'''    private View buildGuidanceHud(){
        FrameLayout layer=new FrameLayout(this);layer.setClickable(false);

        LinearLayout navCard=new LinearLayout(this);navCard.setGravity(Gravity.CENTER_VERTICAL);navCard.setPadding(dp(14),dp(12),dp(14),dp(12));navCard.setBackground(glass(Color.argb(218,15,18,25),30));navCard.setElevation(dp(22));
        guideArrow=text("↑",46,Typeface.BOLD,Color.WHITE);guideArrow.setGravity(Gravity.CENTER);guideArrow.setBackground(glass(Color.argb(118,10,132,255),22));navCard.addView(guideArrow,new LinearLayout.LayoutParams(dp(72),dp(72)));
        LinearLayout instruction=new LinearLayout(this);instruction.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,-2,1);ilp.leftMargin=dp(14);guideTitle=text("Continue sur la trace",25,Typeface.BOLD,Color.WHITE);guideSubtitle=text("Guidage GPS actif",13,Typeface.BOLD,CYAN);instruction.addView(guideTitle);LinearLayout.LayoutParams gsp=new LinearLayout.LayoutParams(-1,-2);gsp.topMargin=dp(4);instruction.addView(guideSubtitle,gsp);navCard.addView(instruction,ilp);
        FrameLayout.LayoutParams topP=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);topP.setMargins(dp(10),safeTop()+dp(4),dp(10),0);layer.addView(navCard,topP);

        guideEventAlert=pill("",Color.argb(218,255,159,10),13);guideEventAlert.setVisibility(View.GONE);guideEventAlert.setElevation(dp(24));FrameLayout.LayoutParams alertP=new FrameLayout.LayoutParams(-1,dp(52),Gravity.TOP);alertP.setMargins(dp(18),safeTop()+dp(104),dp(18),0);layer.addView(guideEventAlert,alertP);

        guideSpeed=text("0\nkm/h",18,Typeface.BOLD,Color.WHITE);guideSpeed.setGravity(Gravity.CENTER);guideSpeed.setBackground(glass(Color.argb(215,18,21,28),99));guideSpeed.setElevation(dp(18));FrameLayout.LayoutParams speedP=new FrameLayout.LayoutParams(dp(74),dp(74),Gravity.START|Gravity.BOTTOM);speedP.setMargins(dp(14),0,0,safeBottom()+dp(132));layer.addView(guideSpeed,speedP);

        TextView locate=circle("◎",Color.argb(215,18,21,28));locate.setElevation(dp(18));locate.setOnClickListener(v->{guidanceCameraFollow=true;press(v);if(lastLocation!=null)updateGuidance(lastLocation);});FrameLayout.LayoutParams locateP=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.END|Gravity.BOTTOM);locateP.setMargins(0,0,dp(14),safeBottom()+dp(143));layer.addView(locate,locateP);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(16),dp(13),dp(16),dp(14));bottom.setBackground(glass(Color.argb(218,15,18,25),30));bottom.setElevation(dp(22));
        LinearLayout stats=new LinearLayout(this);guideRemaining=stat("—","RESTANT");guideProgress=stat("0 %","PROGRESSION");guideDeviation=stat("—","ÉCART TRACE");stats.addView(statCell(guideRemaining),new LinearLayout.LayoutParams(0,-2,1));stats.addView(statCell(guideProgress),new LinearLayout.LayoutParams(0,-2,1));stats.addView(statCell(guideDeviation),new LinearLayout.LayoutParams(0,-2,1));bottom.addView(stats);
        guideNext=text("Prochain repère : —",12,Typeface.BOLD,MUTED);LinearLayout.LayoutParams nx=new LinearLayout.LayoutParams(-1,-2);nx.topMargin=dp(9);nx.bottomMargin=dp(9);bottom.addView(guideNext,nx);
        LinearLayout controls=new LinearLayout(this);TextView overview=pill("Vue route",GLASS2,11);TextView quit=pill("Quitter",Color.argb(185,255,69,58),11);overview.setOnClickListener(v->{guidanceCameraFollow=!guidanceCameraFollow;press(v);if(guidanceCameraFollow&&lastLocation!=null)updateGuidance(lastLocation);else{map.setMapOrientation(0);if(guidingRoute!=null)fitSummary(guidingRoute);}});quit.setOnClickListener(v->confirmStopGuidance());controls.addView(overview,weighted(1.15f,5));controls.addView(quit,weighted(.85f,0));bottom.addView(controls);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);bp.setMargins(dp(10),0,dp(10),safeBottom()+dp(8));layer.addView(bottom,bp);return layer;
    }''')

replace_between('    private void updateGuidance(Location l){', '    private void confirmStopGuidance(){', r'''    private void updateGuidance(Location l){
        if(!guiding||guidance==null||!guidance.isUsable())return;
        if(guideSpeed!=null)guideSpeed.setText(Math.max(0,Math.round(l.getSpeed()*3.6f))+"\nkm/h");
        if(approachingStart&&guidingRoute!=null){
            RouteStore.Point target=guidingReverse?guidingRoute.points.get(guidingRoute.points.size()-1):guidingRoute.points.get(0);float[] dd=new float[1];Location.distanceBetween(l.getLatitude(),l.getLongitude(),target.lat,target.lon,dd);
            if(dd[0]<=45){approachingStart=false;clearApproachRoute();toast("Départ rejoint • guidage de tournée actif");}
            else{guideArrow.setText("➜");guideTitle.setText("Rejoins le départ");guideTitle.setTextColor(Color.WHITE);guideSubtitle.setText(formatDistance(approachDistanceM>0?approachDistanceM:dd[0])+" jusqu’au départ");guideProgress.setText("—");guideRemaining.setText(formatDistance(approachDistanceM>0?approachDistanceM:dd[0]));guideDeviation.setText("GPS");guideNext.setText(approachInstruction);if(guidanceCameraFollow){map.getController().animateTo(new GeoPoint(l.getLatitude(),l.getLongitude()));map.getController().setZoom(17.8);if(l.hasBearing()&&l.getSpeed()>1.1f)map.setMapOrientation(-l.getBearing());}map.invalidate();return;}
        }
        GuidanceEngine.State state=guidance.update(l.getLatitude(),l.getLongitude());if(state.nextEvent!=null&&state.distanceToNextEventM<=60)showEventAlert(state.nextEvent.type,state.nextEvent.label);else hideEventAlert();guideProgress.setText(state.progressPercent+" %");guideRemaining.setText(formatDistance(state.remainingM));guideDeviation.setText(Math.round(state.distanceToTraceM)+" m");
        if(state.finished){guideArrow.setText("✓");guideTitle.setText("Tournée terminée");guideTitle.setTextColor(GREEN);guideSubtitle.setText("Tu as atteint la fin de la trace");guideNext.setText("Arrivée atteinte");}
        else if(state.offRoute){guideArrow.setText("↺");guideTitle.setText("Rejoins la trace");guideTitle.setTextColor(RED);guideSubtitle.setText(Math.round(state.distanceToTraceM)+" m hors parcours");guideNext.setText(state.nextEvent==null?"Retrouve la ligne bleue":"Ensuite : "+state.nextEvent.label);}
        else{guideTitle.setTextColor(Color.WHITE);TurnCue cue=nextTurnCue(state);guideArrow.setText(cue.arrow);guideTitle.setText(cue.label);guideSubtitle.setText(cue.distanceM<18?"maintenant":"dans "+formatDistance(cue.distanceM));if(state.nextEvent!=null&&state.distanceToNextEventM<Float.MAX_VALUE)guideNext.setText(("REVERSE".equals(state.nextEvent.type)?"↶  ":"⇆  ")+state.nextEvent.label+" • "+formatDistance(state.distanceToNextEventM));else guideNext.setText("Suis la trace enregistrée");}
        if(guidanceCameraFollow){GuidanceEngine.Point target=guidance.targetPoint(state);map.getController().animateTo(new GeoPoint(target.lat,target.lon));map.getController().setZoom(18.25);if(l.hasBearing()&&l.getSpeed()>1.1f)map.setMapOrientation(-l.getBearing());}map.invalidate();
    }

    private static final class TurnCue{final String arrow,label;final float distanceM;TurnCue(String arrow,String label,float distanceM){this.arrow=arrow;this.label=label;this.distanceM=distanceM;}}
    private TurnCue nextTurnCue(GuidanceEngine.State state){
        if(guidancePath.size()<4)return new TurnCue("↑","Continue sur la trace",Math.min(120,state.remainingM));
        int start=Math.max(0,Math.min(guidancePath.size()-2,state.nearestIndex));float along=0;
        for(int i=start+1;i<guidancePath.size()-2;i++){
            along+=pathDistance(guidancePath.get(i-1),guidancePath.get(i));if(along>650)break;if(along<16)continue;
            int mid=indexAhead(i,18);int far=indexAhead(mid,24);if(mid<=i||far<=mid)continue;float h1=pathBearing(guidancePath.get(i),guidancePath.get(mid));float h2=pathBearing(guidancePath.get(mid),guidancePath.get(far));float delta=normalizeTurn(h2-h1);float abs=Math.abs(delta);if(abs<38)continue;
            if(abs>145)return new TurnCue("↶","Demi-tour",along);
            if(delta>0)return new TurnCue(abs>78?"↱":"↗",abs>78?"Tourne franchement à droite":"Tourne à droite",along);
            return new TurnCue(abs>78?"↰":"↖",abs>78?"Tourne franchement à gauche":"Tourne à gauche",along);
        }
        return new TurnCue("↑","Continue sur la trace",Math.min(220,state.remainingM));
    }
    private int indexAhead(int start,float meters){float d=0;for(int i=start+1;i<guidancePath.size();i++){d+=pathDistance(guidancePath.get(i-1),guidancePath.get(i));if(d>=meters)return i;}return guidancePath.size()-1;}
    private float pathDistance(GuidanceEngine.Point a,GuidanceEngine.Point b){float[] out=new float[1];Location.distanceBetween(a.lat,a.lon,b.lat,b.lon,out);return out[0];}
    private float pathBearing(GuidanceEngine.Point a,GuidanceEngine.Point b){double lat1=Math.toRadians(a.lat),lat2=Math.toRadians(b.lat),dLon=Math.toRadians(b.lon-a.lon);double y=Math.sin(dLon)*Math.cos(lat2),x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);return (float)Math.toDegrees(Math.atan2(y,x));}
    private float normalizeTurn(float a){while(a>180)a-=360;while(a<-180)a+=360;return a;}
''')

# Replace classic system confirmation with glass sheet.
s = s.replace(
    '    private void confirmStopGuidance(){new AlertDialog.Builder(this).setTitle("Quitter le guidage ?").setMessage("La tournée enregistrée ne sera pas modifiée.").setNegativeButton("Continuer",null).setPositiveButton("Quitter",(d,w)->stopGuidance()).show();}\n',
    '    private void confirmStopGuidance(){confirmSheet("Quitter le guidage ?","La tournée enregistrée ne sera pas modifiée.","Continuer","Quitter",RED,this::stopGuidance);}\n'
)
s = s.replace(
    'private void stopGuidance(){guiding=false;guidanceCameraFollow=true;approachingStart=false;clearApproachRoute();guidance=null;guidingRoute=null;guideEventAlert=null;lastEventAlertKey="";if(guidanceHud!=null){root.removeView(guidanceHud);guidanceHud=null;}clearPreview();showHistory();}',
    'private void stopGuidance(){guiding=false;guidanceCameraFollow=true;approachingStart=false;clearApproachRoute();guidance=null;guidancePath.clear();guidingRoute=null;guideEventAlert=null;lastEventAlertKey="";map.setMapOrientation(0);if(guidanceHud!=null){root.removeView(guidanceHud);guidanceHud=null;}clearPreview();showHistory();}'
)

# Hours: numeric keypad, no colon required, automatic normalization and quick current time.
replace_between('    private void showHoursEntrySheet(){', '    private EditText hoursInput(', r'''    private void showHoursEntrySheet(){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));String date=todayKey();LinearLayout panel=sheetPanel("Ajouter des heures",dayLabel(date)+" • "+date);
        EditText start=hoursInput("Début • ex. 730",prefs.getString("hours_last_start",""),InputType.TYPE_CLASS_NUMBER);EditText end=hoursInput("Fin • ex. 1530",prefs.getString("hours_last_end",""),InputType.TYPE_CLASS_NUMBER);EditText pause=hoursInput("Pause • minutes",String.valueOf(prefs.getInt("hours_last_pause",0)),InputType.TYPE_CLASS_NUMBER);
        start.setOnFocusChangeListener((v,has)->{if(!has){int m=parseClock(start.getText().toString());if(m>=0)start.setText(timeLabel(m));}});end.setOnFocusChangeListener((v,has)->{if(!has){int m=parseClock(end.getText().toString());if(m>=0)end.setText(timeLabel(m));}});
        panel.addView(start,new LinearLayout.LayoutParams(-1,dp(52)));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(8);panel.addView(end,ep);
        LinearLayout now=new LinearLayout(this);TextView startNow=pill("Début = maintenant",Color.argb(48,255,255,255),10);TextView endNow=pill("Fin = maintenant",Color.argb(48,255,255,255),10);startNow.setOnClickListener(v->{press(v);start.setText(timeLabel(nowMinutes()));});endNow.setOnClickListener(v->{press(v);end.setText(timeLabel(nowMinutes()));});LinearLayout.LayoutParams n1=new LinearLayout.LayoutParams(0,dp(38),1);n1.rightMargin=dp(4);LinearLayout.LayoutParams n2=new LinearLayout.LayoutParams(0,dp(38),1);n2.leftMargin=dp(4);now.addView(startNow,n1);now.addView(endNow,n2);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(8);panel.addView(now,np);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(52));pp.topMargin=dp(10);panel.addView(pause,pp);
        TextView hint=text("Pas besoin de taper « : » : 730, 0730 ou 7:30 deviennent 07:30 automatiquement. La date du jour est ajoutée toute seule.",10,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(9);hp.bottomMargin=dp(10);panel.addView(hint,hp);
        LinearLayout quick=new LinearLayout(this);for(int m:new int[]{0,20,30,45,60}){TextView q=pill(m+" min",Color.argb(42,255,255,255),10);q.setOnClickListener(v->{press(v);pause.setText(String.valueOf(m));});LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,dp(36),1);qp.rightMargin=dp(4);quick.addView(q,qp);}panel.addView(quick);
        TextView save=sheetAction("✓  Enregistrer la journée",accent());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.topMargin=dp(12);panel.addView(save,sp);save.setOnClickListener(v->{int a=parseClock(start.getText().toString());int b=parseClock(end.getText().toString());int breakM=parsePositive(pause.getText().toString());if(a<0||b<0){shake(start);shake(end);toast("Heure invalide • tape par exemple 730 ou 1530");return;}int gross=(b>=a?b-a:b+1440-a);if(breakM>=gross){shake(pause);toast("La pause doit être plus courte que le service");return;}hoursStore.add(date,a,b,breakM);prefs.edit().putString("hours_last_start",timeLabel(a)).putString("hours_last_end",timeLabel(b)).putInt("hours_last_pause",breakM).apply();press(v);d.dismiss();root.postDelayed(this::showHours,120);});
        presentBottomSheet(d,shell,panel);root.postDelayed(()->start.requestFocus(),180);
    }
''')

s = s.replace(
    '    private int parseClock(String raw){try{String[] p=raw.trim().replace(\'.\',\':\').split(":");if(p.length!=2)return-1;int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);return h>=0&&h<=23&&m>=0&&m<=59?h*60+m:-1;}catch(Exception e){return-1;}}',
    '''    private int parseClock(String raw){try{String t=raw.trim().replace('.',':');int h,m;if(t.contains(":")){String[] p=t.split(":");if(p.length!=2)return-1;h=Integer.parseInt(p[0]);m=Integer.parseInt(p[1]);}else{String digits=t.replaceAll("[^0-9]","");if(digits.length()==1||digits.length()==2){h=Integer.parseInt(digits);m=0;}else if(digits.length()==3){h=Integer.parseInt(digits.substring(0,1));m=Integer.parseInt(digits.substring(1));}else if(digits.length()==4){h=Integer.parseInt(digits.substring(0,2));m=Integer.parseInt(digits.substring(2));}else return-1;}return h>=0&&h<=23&&m>=0&&m<=59?h*60+m:-1;}catch(Exception e){return-1;}}'''
)
s = s.replace(
    '    private String todayKey(){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date());}',
    '    private int nowMinutes(){Calendar c=Calendar.getInstance();return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);}\n    private String todayKey(){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date());}'
)

# Add map icon preference in settings.
s = s.replace(
    'c.addView(section("APPARENCE"));c.addView(choice("Couleur d’accent",prefs.getString("accent","Bleu"),v->accentDialog()),bottom(9));c.addView(choice("Couleur de la trace",prefs.getString("trace_color","Orange"),v->traceColorDialog()),bottom(9));',
    'c.addView(section("APPARENCE"));c.addView(choice("Couleur d’accent",prefs.getString("accent","Bleu"),v->accentDialog()),bottom(9));c.addView(choice("Couleur de la trace",prefs.getString("trace_color","Orange"),v->traceColorDialog()),bottom(9));c.addView(choice("Icône sur la carte",prefs.getString("position_icon","Camion"),v->positionIconDialog()),bottom(9));'
)

# Replace system dialogs that remain visible in the product.
s = s.replace(
    '    private void requestFinishRecording(){if(!recording)return;new AlertDialog.Builder(this).setTitle("Terminer la tournée ?").setMessage("La trace et les repères seront sauvegardés localement.").setNegativeButton("Continuer",null).setPositiveButton("Terminer",(d,w)->finishRecording()).show();}\n',
    '    private void requestFinishRecording(){if(!recording)return;confirmSheet("Terminer la tournée ?","La trace GPS et les repères métier seront sauvegardés localement.","Continuer","Terminer et sauvegarder",RED,this::finishRecording);}\n'
)

replace_between('    private void eventWithNote(', '    private void undoEvent(){', r'''    private void eventWithNote(String type,String label,int color){
        if(!recording||paused){toast("Commence ou reprends la tournée");return;}Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel(label,"Ajouter une note facultative au repère");EditText input=new EditText(this);input.setHint("Ex. impasse étroite, portail…");input.setHintTextColor(Color.argb(115,255,255,255));input.setTextColor(Color.WHITE);input.setSingleLine(true);input.setPadding(dp(15),0,dp(15),0);input.setBackground(glass(Color.argb(62,255,255,255),18));panel.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));TextView save=sheetAction("✓  Enregistrer le repère",accent());save.setOnClickListener(v->{String note=input.getText().toString().trim();press(v);d.dismiss();addEvent(type,note.isEmpty()?label:label+" • "+note,color);});panel.addView(save,top(10));TextView cancel=sheetAction("Annuler",Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel,top(7));presentBottomSheet(d,shell,panel);root.postDelayed(input::requestFocus,180);
    }
''')

replace_between('    private void checkDraftRecovery(){', '    private void resumeDraft(', r'''    private void checkDraftRecovery(){
        if(recording||!store.hasDraft())return;RouteStore.Summary sum=store.draftSummary();if(sum.points.size()<2){store.clearDraft();return;}Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Tournée interrompue trouvée",formatDuration(sum.durationMs)+" • "+formatDistance(sum.distanceM)+" • "+sum.events.size()+" repères\nTu peux reprendre exactement là où RoutePilot s’est arrêté.");TextView resume=sheetAction("▶  Reprendre la tournée",accent());resume.setOnClickListener(v->{press(v);d.dismiss();resumeDraft(sum);});panel.addView(resume,bottom(8));TextView save=sheetAction("✓  Sauvegarder maintenant",Color.argb(65,255,255,255));save.setOnClickListener(v->{press(v);d.dismiss();saveRecoveredDraft(sum);});panel.addView(save,bottom(8));TextView del=sheetAction("Supprimer le brouillon",Color.argb(125,255,69,58));del.setOnClickListener(v->{press(v);d.dismiss();store.clearDraft();});panel.addView(del);presentBottomSheet(d,shell,panel);
    }
''')

replace_between('    private void rename(File f){', '    private void deleteRoute(File f){', r'''    private void rename(File f){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Renommer la tournée","Choisis un nom clair pour la retrouver rapidement");EditText e=new EditText(this);e.setText(store.displayName(f));e.setSelectAllOnFocus(true);e.setSingleLine();e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setTextColor(Color.WHITE);e.setHintTextColor(MUTED);e.setTextSize(16);e.setPadding(dp(15),0,dp(15),0);e.setBackground(glass(Color.argb(62,255,255,255),18));panel.addView(e,new LinearLayout.LayoutParams(-1,dp(54)));TextView save=sheetAction("✓  Enregistrer",accent());save.setOnClickListener(v->{press(v);store.rename(f,e.getText().toString());d.dismiss();root.postDelayed(this::showHistory,100);});panel.addView(save,top(10));TextView cancel=sheetAction("Annuler",Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel,top(7));presentBottomSheet(d,shell,panel);root.postDelayed(e::requestFocus,180);
    }
''')

s = s.replace(
    '    private void deleteRoute(File f){new AlertDialog.Builder(this).setTitle("Supprimer cette tournée ?").setMessage("Le fichier GPX local sera supprimé définitivement.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{if(store.delete(f)){toast("Tournée supprimée");showHistory();}else toast("Suppression impossible");}).show();}\n',
    '    private void deleteRoute(File f){confirmSheet("Supprimer cette tournée ?","Le fichier GPX local sera supprimé définitivement.","Annuler","Supprimer",RED,()->{if(store.delete(f)){toast("Tournée supprimée");showHistory();}else toast("Suppression impossible");});}\n'
)

# Replace offline/map source system menus with glass sheets.
replace_between('    private void offlineMenu(', '    private void downloadPack(', r'''    private void offlineMenu(FranceOfflineManager.Pack pack,boolean active){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel(pack.label,active?"Carte hors ligne actuellement active":"Carte hors ligne installée");TextView primary=sheetAction(active?"◉  Revenir à la carte en ligne":"✓  Activer cette carte",accent());primary.setOnClickListener(v->{press(v);d.dismiss();if(active)activateOnline();else activateOffline(pack,true);});panel.addView(primary,bottom(8));TextView del=sheetAction("⌫  Supprimer la carte",Color.argb(125,255,69,58));del.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(()->confirmSheet("Supprimer "+pack.label+" ?","Le fichier hors ligne sera supprimé du téléphone.","Annuler","Supprimer",RED,()->{if(active)activateOnline();if(FranceOfflineManager.delete(this,pack)){toast("Carte supprimée");showSettings();}}),120);});panel.addView(del,bottom(8));TextView cancel=sheetAction("Fermer",Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel);presentBottomSheet(d,shell,panel);
    }
''')

replace_between('    private void mapSourceMenu(){', '    private void updateMapChip(){', r'''    private void mapSourceMenu(){
        Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Source de carte","Choisis le fond utilisé par RoutePilot");TextView online=sheetAction("◎  OpenStreetMap en ligne",Color.argb(62,255,255,255));online.setOnClickListener(v->{press(v);d.dismiss();activateOnline();});panel.addView(online,bottom(8));for(FranceOfflineManager.Pack p:FranceOfflineManager.PACKS)if(FranceOfflineManager.isInstalled(this,p)){TextView row=sheetAction("▧  "+p.label+" • hors ligne",Color.argb(62,255,255,255));row.setOnClickListener(v->{press(v);d.dismiss();activateOffline(p,true);});panel.addView(row,bottom(8));}TextView cancel=sheetAction("Fermer",Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel);presentBottomSheet(d,shell,panel);
    }
''')

# Insert generic liquid confirmation and map icon picker before color picker helpers.
insert_marker = '    private void accentDialog(){'
insert = r'''    private void confirmSheet(String title,String message,String cancelLabel,String confirmLabel,int confirmColor,Runnable action){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel(title,message);TextView confirm=sheetAction(confirmLabel,confirmColor);confirm.setOnClickListener(v->{press(v);d.dismiss();root.postDelayed(action,100);});panel.addView(confirm,bottom(8));TextView cancel=sheetAction(cancelLabel,Color.argb(35,255,255,255));cancel.setOnClickListener(v->d.dismiss());panel.addView(cancel);presentBottomSheet(d,shell,panel);}

    private void positionIconDialog(){Dialog d=new Dialog(this);FrameLayout shell=new FrameLayout(this);shell.setPadding(dp(14),0,dp(14),dp(14));LinearLayout panel=sheetPanel("Icône sur la carte","Choisis ce qui représente ta position pendant les tournées");String current=prefs.getString("position_icon","Camion");for(String name:new String[]{"Bonhomme","Voiture","Camion"}){String symbol="Bonhomme".equals(name)?"●":"Voiture".equals(name)?"▰":"▣";TextView row=sheetAction((name.equals(current)?"✓  ":symbol+"  ")+name,name.equals(current)?accent():Color.argb(52,255,255,255));row.setOnClickListener(v->{press(v);prefs.edit().putString("position_icon",name).apply();applyPositionIcon();d.dismiss();root.postDelayed(this::showSettings,100);});panel.addView(row,bottom(7));}presentBottomSheet(d,shell,panel);}
    private void applyPositionIcon(){if(me==null)return;Bitmap icon=createPositionIcon(prefs==null?"Camion":prefs.getString("position_icon","Camion"));me.setPersonIcon(icon);me.setDirectionArrow(icon,icon);me.setPersonHotspot(icon.getWidth()/2f,icon.getHeight()/2f);if(map!=null)map.invalidate();}
    private Bitmap createPositionIcon(String style){int z=dp(58);Bitmap b=Bitmap.createBitmap(z,z,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShadowLayer(dp(5),0,dp(2),Color.argb(150,0,0,0));p.setColor(Color.argb(218,Color.red(accent()),Color.green(accent()),Color.blue(accent())));c.drawCircle(z/2f,z/2f,z*.43f,p);p.clearShadowLayer();p.setColor(Color.WHITE);if("Bonhomme".equals(style)){c.drawCircle(z*.5f,z*.31f,z*.09f,p);c.drawRoundRect(z*.43f,z*.40f,z*.57f,z*.67f,dp(5),dp(5),p);p.setStrokeWidth(dp(4));p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(z*.45f,z*.51f,z*.34f,z*.61f,p);c.drawLine(z*.55f,z*.51f,z*.66f,z*.61f,p);c.drawLine(z*.47f,z*.65f,z*.40f,z*.78f,p);c.drawLine(z*.53f,z*.65f,z*.60f,z*.78f,p);}else if("Voiture".equals(style)){c.drawRoundRect(z*.31f,z*.20f,z*.69f,z*.80f,dp(8),dp(8),p);p.setColor(Color.argb(210,25,32,44));c.drawRoundRect(z*.36f,z*.31f,z*.64f,z*.49f,dp(4),dp(4),p);p.setColor(Color.WHITE);c.drawRect(z*.27f,z*.30f,z*.32f,z*.45f,p);c.drawRect(z*.68f,z*.30f,z*.73f,z*.45f,p);c.drawRect(z*.27f,z*.58f,z*.32f,z*.73f,p);c.drawRect(z*.68f,z*.58f,z*.73f,z*.73f,p);}else{c.drawRoundRect(z*.29f,z*.17f,z*.71f,z*.78f,dp(7),dp(7),p);p.setColor(Color.argb(210,25,32,44));c.drawRoundRect(z*.34f,z*.22f,z*.66f,z*.38f,dp(3),dp(3),p);p.setColor(Color.argb(235,210,235,245));c.drawRoundRect(z*.34f,z*.44f,z*.66f,z*.69f,dp(4),dp(4),p);p.setColor(Color.WHITE);c.drawRect(z*.25f,z*.26f,z*.31f,z*.43f,p);c.drawRect(z*.69f,z*.26f,z*.75f,z*.43f,p);c.drawRect(z*.25f,z*.57f,z*.31f,z*.74f,p);c.drawRect(z*.69f,z*.57f,z*.75f,z*.74f,p);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.argb(220,255,255,255));c.drawCircle(z/2f,z/2f,z*.43f,p);return b;}

'''
s = s.replace(insert_marker, insert + insert_marker)

# Remove the two remaining native dialog methods if exact old lines are present.
# (Unused AlertDialog import is harmless, but all common user-facing route dialogs are now custom.)

activity.write_text(s)

b = build.read_text()
b = b.replace("versionCode 14", "versionCode 15").replace("versionName '1.0.3'", "versionName '1.0.4'")
build.write_text(b)

print('RoutePilot navigation/glass patch applied')
