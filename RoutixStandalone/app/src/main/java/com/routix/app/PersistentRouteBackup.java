package com.routix.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Mirrors GPX routes to public phone storage so they survive uninstall.
 * Android 10+ uses MediaStore.Downloads/Download/Routix without legacy storage permissions.
 * Older Android versions are left on private app storage because this app does not request the
 * broad WRITE_EXTERNAL_STORAGE permission just to persist a route backup.
 */
final class PersistentRouteBackup {
    private static final String RELATIVE = Environment.DIRECTORY_DOWNLOADS + "/Routix/";
    private final Context context;

    PersistentRouteBackup(Context context) {
        this.context = context.getApplicationContext();
    }

    void publish(File source) {
        if (Build.VERSION.SDK_INT < 29) return;
        if (source == null || !source.exists() || !source.getName().toLowerCase(Locale.ROOT).endsWith(".gpx")) return;
        try { publishMediaStore(source); } catch (Exception ignored) {}
    }

    private void publishMediaStore(File source) throws Exception {
        ContentResolver cr = context.getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri target = find(cr, collection, source.getName());
        if (target == null) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, source.getName());
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            target = cr.insert(collection, v);
            if (target == null) return;
        } else {
            ContentValues pending = new ContentValues();
            pending.put(MediaStore.MediaColumns.IS_PENDING, 1);
            try { cr.update(target, pending, null, null); } catch (Exception ignored) {}
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = cr.openOutputStream(target, "wt")) {
            if (out == null) return;
            copy(in, out);
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(target, done, null, null);
    }

    private Uri find(ContentResolver cr, Uri collection, String name) {
        String[] projection = { MediaStore.MediaColumns._ID };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        try (Cursor c = cr.query(collection, projection, selection, new String[]{name, RELATIVE}, null)) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                return Uri.withAppendedPath(collection, Long.toString(id));
            }
        } catch (Exception ignored) {}
        return null;
    }

    void restoreInto(File destination) {
        if (Build.VERSION.SDK_INT < 29 || destination == null) return;
        if (!destination.exists()) destination.mkdirs();
        try { restoreMediaStore(destination); } catch (Exception ignored) {}
    }

    private void restoreMediaStore(File destination) {
        ContentResolver cr = context.getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME };
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
        try (Cursor c = cr.query(collection, projection, selection, new String[]{RELATIVE, "%.gpx"}, MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            if (c == null) return;
            while (c.moveToNext()) {
                String name = c.getString(1);
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".gpx")) continue;
                File target = new File(destination, safeName(name));
                if (target.exists() && target.length() > 128) continue;
                Uri uri = Uri.withAppendedPath(collection, Long.toString(c.getLong(0)));
                try (InputStream in = cr.openInputStream(uri); OutputStream out = new FileOutputStream(target)) {
                    if (in != null) copy(in, out);
                } catch (Exception e) { target.delete(); }
            }
        } catch (Exception ignored) {}
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._ -]", "_");
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        out.flush();
    }
}
