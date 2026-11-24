package com.mullagaliev.aqpbalancer.service.learning;

import com.mullagaliev.aqpbalancer.util.MathUtils;

/**
 * Reward balances result accuracy against execution latency.
 *
 * <pre>
 * reward = accuracyWeight * (1 - error) - latencyWeight * min(1, latency / latencyScale)
 * </pre>
 *
 * <p>When the backend does not report accuracy ({@code relativeError == null}),
 * the accuracy part is not applied and the loop optimises latency only. This
 * keeps the balancer usable with third-party backends that cannot evaluate
 * their own error.</p>
 */
public class RewardCalculator {

    private static final double DEFAULT_ACCURACY_WEIGHT = 0.7;
    private static final double DEFAULT_LATENCY_WEIGHT = 0.3;
    private static final double DEFAULT_LATENCY_SCALE_MS = 10_000.0;

    private final double accuracyWeight;
    private final double latencyWeight;
    private final double latencyScaleMs;

    public RewardCalculator() {
        this(DEFAULT_ACCURACY_WEIGHT, DEFAULT_LATENCY_WEIGHT, DEFAULT_LATENCY_SCALE_MS);
    }

    public RewardCalculator(double accuracyWeight, double latencyWeight, double latencyScaleMs) {
        this.accuracyWeight = Math.max(0.0, accuracyWeight);
        this.latencyWeight = Math.max(0.0, latencyWeight);
        this.latencyScaleMs = Math.max(1.0, latencyScaleMs);
    }

    public double compute(double relativeError, double latencyMs) {
        return compute(Double.valueOf(relativeError), latencyMs);
    }

    public double compute(Double relativeError, double latencyMs) {
        double latency = Math.max(0.0, latencyMs);
        double latencyPenalty = Math.min(1.0, latency / latencyScaleMs);
        double reward;
        if (relativeError == null || !Double.isFinite(relativeError)) {
            reward = 1.0 - latencyPenalty;
        } else {
            double error = MathUtils.clamp(relativeError, 0.0, 1.0);
            reward = accuracyWeight * (1.0 - error) - latencyWeight * latencyPenalty;
        }
        return MathUtils.clamp(reward, 0.0, 1.0);
    }
}
