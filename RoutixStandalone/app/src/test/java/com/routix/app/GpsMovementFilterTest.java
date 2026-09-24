package com.routix.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class GpsMovementFilterTest {
    @Test public void normalUrbanMotionIsAccepted(){
        assertTrue(GpsMovementFilter.plausible(24, 2000, 5, 5));
    }

    @Test public void highwayMotionIsAccepted(){
        assertTrue(GpsMovementFilter.plausible(100, 3000, 5, 5));
    }

    @Test public void oneSecondTeleportIsRejected(){
        assertFalse(GpsMovementFilter.plausible(180, 1000, 5, 5));
    }

    @Test public void accuracyUncertaintyGetsReasonableSlack(){
        assertTrue(GpsMovementFilter.plausible(85, 1000, 20, 20));
    }

    @Test public void staleGapCannotInflateDistance(){
        assertFalse(GpsMovementFilter.plausible(50, 31000, 5, 5));
    }

    @Test public void invalidNumbersAreRejected(){
        assertFalse(GpsMovementFilter.plausible(Double.NaN, 1000, 5, 5));
        assertFalse(GpsMovementFilter.plausible(10, 0, 5, 5));
        assertFalse(GpsMovementFilter.plausible(10, 1000, Float.NaN, 5));
    }
}
