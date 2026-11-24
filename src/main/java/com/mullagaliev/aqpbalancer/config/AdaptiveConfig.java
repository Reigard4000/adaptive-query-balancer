package com.mullagaliev.aqpbalancer.config;

import com.mullagaliev.aqpbalancer.util.MathUtils;

/**
 * Tunable parameters of the adaptive control loop.
 *
 * <p>The defaults match the original demo behaviour, but the values are
 * externalised so a real deployment can tune the window, detector and reward
 * without rebuilding the application.</p>
 */
public record AdaptiveConfig(
        int latencyWindowSize,
        int minEstimationSamples,
        long refreshIntervalMs,
        int refreshSampleInterval,
        double defaultAlpha,
        int detectorMinSamples,
        int detectorMaxHistory,
        double cusumBaseline,
        double cusumDrift,
        double cusumThreshold,
        double rewardAccuracyWeight,
        double rewardLatencyWeight,
        double rewardLatencyScaleMs
) {

    public static AdaptiveConfig defaults() {
        return new AdaptiveConfig(
                1_000,
                20,
                1_000L,
                50,
                2.5,
                8,
                120,
                2.2,
                0.05,
                1.0,
                0.7,
                0.3,
                10_000.0
        );
    }

    public AdaptiveConfig {
        latencyWindowSize = Math.max(20, Math.min(100_000, latencyWindowSize));
        minEstimationSamples = Math.max(5, Math.min(latencyWindowSize, minEstimationSamples));
        refreshIntervalMs = Math.max(0L, Math.min(60_000L, refreshIntervalMs));
        refreshSampleInterval = Math.max(1, Math.min(100_000, refreshSampleInterval));
        if (!Double.isFinite(defaultAlpha) || defaultAlpha <= 0.0) {
            defaultAlpha = 2.5;
        }
        defaultAlpha = MathUtils.clamp(defaultAlpha, 1.05, 12.0);
        detectorMinSamples = Math.max(2, Math.min(10_000, detectorMinSamples));
        detectorMaxHistory = Math.max(detectorMinSamples, Math.min(100_000, detectorMaxHistory));
        cusumBaseline = finitePositive(cusumBaseline, 2.2);
        cusumDrift = finiteNonNegative(cusumDrift, 0.05);
        cusumThreshold = finiteNonNegative(cusumThreshold, 1.0);
        rewardAccuracyWeight = Math.max(0.0, Math.min(1.0,
                finiteNonNegative(rewardAccuracyWeight, 0.7)));
        rewardLatencyWeight = Math.max(0.0, Math.min(1.0,
                finiteNonNegative(rewardLatencyWeight, 0.3)));
        rewardLatencyScaleMs = finitePositive(rewardLatencyScaleMs, 10_000.0);
    }

    private static double finitePositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? value : fallback;
    }
}
