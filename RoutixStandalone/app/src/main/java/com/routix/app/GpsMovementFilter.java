package com.routix.app;

/** Shared physical plausibility policy for recorded and guidance travel distance. */
final class GpsMovementFilter {
    private static final double MAX_SPEED_MPS = 55.0; // 198 km/h: generous for road use, rejects GPS teleports.
    private static final double BASE_JUMP_ALLOWANCE_M = 25.0;
    private static final long MAX_GAP_MS = 30_000L;

    private GpsMovementFilter() {}

    static boolean plausible(double distanceM, long elapsedMs, float previousAccuracyM, float accuracyM) {
        if (!Double.isFinite(distanceM) || distanceM < 0 || elapsedMs <= 0 || elapsedMs > MAX_GAP_MS) return false;
        if (!Float.isFinite(previousAccuracyM) || !Float.isFinite(accuracyM) || previousAccuracyM < 0 || accuracyM < 0) return false;
        double uncertainty = Math.min(80.0, previousAccuracyM + accuracyM);
        double allowed = BASE_JUMP_ALLOWANCE_M + uncertainty + MAX_SPEED_MPS * (elapsedMs / 1000.0);
        return distanceM <= allowed;
    }
}
