package com.routepilot.app;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.util.Arrays;
import java.util.List;

final class FranceOfflineManager {
    static final class Pack {
        final String id, label, fileName, url;
        final int sizeMb;
        Pack(String id, String label, String fileName, int sizeMb) {
            this.id = id; this.label = label; this.fileName = fileName; this.sizeMb = sizeMb;
            this.url = "https://download.mapsforge.org/maps/v5/europe/france/" + fileName;
        }
    }

    static final List<Pack> PACKS = Arrays.asList(
            new Pack("alsace", "Alsace", "alsace.map", 85),
            new Pack("lorraine", "Lorraine", "lorraine.map", 121),
            new Pack("champagne", "Champagne-Ardenne", "champagne-ardenne.map", 79),
            new Pack("franche_comte", "Franche-Comté", "franche-comte.map", 87),
            new Pack("bourgogne", "Bourgogne", "bourgogne.map", 150),
            new Pack("ile_de_france", "Île-de-France", "ile-de-france.map", 197),
            new Pack("centre", "Centre", "centre.map", 172),
            new Pack("auvergne", "Auvergne", "auvergne.map", 109),
            new Pack("rhone_alpes", "Rhône-Alpes", "rhone-alpes.map", 335),
            new Pack("paca", "Provence-Alpes-Côte d’Azur", "provence-alpes-cote-d-azur.map", 229),
            new Pack("aquitaine", "Aquitaine", "aquitaine.map", 209),
            new Pack("bretagne", "Bretagne", "bretagne.map", 206),
            new Pack("corse", "Corse", "corse.map", 25)
    );

    private FranceOfflineManager() {}

    static File mapsDir(Context c) {
        File d = new File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "maps");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File file(Context c, Pack p) { return new File(mapsDir(c), p.fileName); }
    static boolean isInstalled(Context c, Pack p) { File f = file(c, p); return f.exists() && f.length() > 1024 * 1024; }

    static long download(Context c, Pack p) {
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(p.url));
        r.setTitle("RoutePilot • " + p.label);
        r.setDescription("Carte vectorielle hors ligne");
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setAllowedOverMetered(true);
        r.setAllowedOverRoaming(false);
        r.setDestinationUri(Uri.fromFile(file(c, p)));
        return dm.enqueue(r);
    }

    static long installedBytes(Context c) {
        long total = 0;
        for (Pack p : PACKS) if (isInstalled(c, p)) total += file(c, p).length();
        return total;
    }
}
