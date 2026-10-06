package com.routix.app;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.nio.file.*;
public class RoadRestrictionFeatureTest{
 @Test public void speedAndHeightUseOsmWithoutInventedFallbacks()throws Exception{String s=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/RoadRestrictionProvider.java")));assertTrue(s.contains("maxspeed:hgv"));assertTrue(s.contains("maxspeed"));assertTrue(s.contains("maxheight:physical"));assertTrue(s.contains("maxheight"));assertTrue(s.contains("height_restrictor"));assertFalse(s.contains("highway=\"residential\"?50"));}
 @Test public void restrictionProviderDoesNotTouchRecorder()throws Exception{String t=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/TrackingService.java")));assertFalse(t.contains("RoadRestrictionProvider"));assertFalse(t.contains("RouteImprovementEngine"));}
}