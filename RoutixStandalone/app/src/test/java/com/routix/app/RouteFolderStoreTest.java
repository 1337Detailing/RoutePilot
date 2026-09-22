package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class RouteFolderStoreTest {
    @Test public void foldersCreateRenameMoveAndDeleteWithoutTouchingGpx(){
        Context context=RuntimeEnvironment.getApplication();
        SharedPreferences prefs=context.getSharedPreferences("folders-test",0);
        prefs.edit().clear().commit();
        RouteFolderStore folders=new RouteFolderStore(prefs);
        File route=new File(context.getFilesDir(),"demo.gpx");

        assertTrue(folders.create("Bischwiller"));
        assertFalse(folders.create("bischwiller"));
        folders.move(route,"Bischwiller");
        assertEquals("Bischwiller",folders.folderOf(route));

        assertTrue(folders.rename("Bischwiller","Secteur Nord"));
        assertEquals("Secteur Nord",folders.folderOf(route));
        folders.migrateFileName("demo.gpx","demo-renomme.gpx");
        assertEquals("Secteur Nord",folders.folderOf(new File(context.getFilesDir(),"demo-renomme.gpx")));

        folders.delete("Secteur Nord");
        assertEquals(RouteFolderStore.ROOT,folders.folderOf(new File(context.getFilesDir(),"demo-renomme.gpx")));
        assertTrue(folders.folders().isEmpty());
    }
}
