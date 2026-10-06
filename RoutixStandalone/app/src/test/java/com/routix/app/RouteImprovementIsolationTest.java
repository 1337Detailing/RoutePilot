package com.routix.app;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.nio.file.*;import java.util.*;
public class RouteImprovementIsolationTest{
 @Test public void optimizerIsSeparateFromRecordingPath()throws Exception{
  String store=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/RouteStore.java")));
  String tracking=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/TrackingService.java")));
  assertFalse(tracking.contains("RouteImprovementEngine"));
  assertTrue(store.contains("createImprovedRoute"));
  assertFalse(store.substring(store.indexOf("File createRoute("),store.indexOf("boolean writeGpx")).contains("RouteImprovementEngine"));
 }
 @Test public void optimizedSaveHasDistinctNameAndSourceMetadata()throws Exception{
  String store=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/RouteStore.java")));
  assertTrue(store.contains(" – optimisée"));assertTrue(store.contains("optimized_from_"));
 }
 @Test public void engineRequiresOsmDirectionAndMajorRoadRules()throws Exception{
  String e=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/RouteImprovementEngine.java")));
  assertTrue(e.contains("oneway"));assertTrue(e.contains("trunk"));assertTrue(e.contains("primary"));assertTrue(e.contains("secondary"));assertTrue(e.contains("router.project-osrm.org"));
 }
}