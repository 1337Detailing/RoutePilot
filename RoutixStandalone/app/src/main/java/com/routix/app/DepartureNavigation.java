package com.routix.app;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.net.Uri;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.Locale;

/** Road-distance preview only; navigation is delegated to the selected app. */
final class DepartureNavigation {
    static void open(Activity host,Location origin,RouteStore.Point target){
        if(origin==null){choose(host,target,"Position GPS indisponible : l’application choisie calculera le trajet.");return;}
        android.widget.Toast.makeText(host,"Calcul du trajet vers le départ…",android.widget.Toast.LENGTH_SHORT).show();
        new Thread(()->{String message="Calcul indisponible. L’application choisie recalculera le trajet.";HttpURLConnection connection=null;
            try{String url=String.format(Locale.US,"https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",origin.getLongitude(),origin.getLatitude(),target.lon,target.lat);connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(6000);connection.setRequestProperty("User-Agent","Routix/2.1");
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream()))){StringBuilder b=new StringBuilder();String l;while((l=reader.readLine())!=null&&b.length()<100000)b.append(l);JSONObject route=new JSONObject(b.toString()).getJSONArray("routes").getJSONObject(0);message=String.format(Locale.FRANCE,"Départ à %.1f km • environ %.0f min\nItinéraire voiture indicatif : vérifier les restrictions camion.",route.getDouble("distance")/1000,route.getDouble("duration")/60);}
            }catch(Exception e){DiagnosticLog.error("departure routing",e);}finally{if(connection!=null)connection.disconnect();}
            String result=message;host.runOnUiThread(()->{if(!host.isFinishing()&&!host.isDestroyed())choose(host,target,result);});
        },"departure-preview").start();
    }
    private static void choose(Activity host,RouteStore.Point point,String message){new AlertDialog.Builder(host).setTitle("Aller au départ").setMessage(message).setNegativeButton("Annuler",null).setNeutralButton("Waze",(d,w)->launch(host,point,true)).setPositiveButton("Google Maps",(d,w)->launch(host,point,false)).show();}
    private static void launch(Activity host,RouteStore.Point p,boolean waze){String coords=String.format(Locale.US,"%.7f,%.7f",p.lat,p.lon);Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(waze?"waze://?ll="+coords+"&navigate=yes":"google.navigation:q="+coords+"&mode=d"));try{host.startActivity(i);}catch(ActivityNotFoundException e){String url=waze?"https://www.waze.com/ul?ll="+coords+"&navigate=yes":"https://www.google.com/maps/dir/?api=1&destination="+coords+"&travelmode=driving";try{host.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException absent){android.widget.Toast.makeText(host,"Installe Google Maps ou Waze pour naviguer.",android.widget.Toast.LENGTH_LONG).show();}}}
}
