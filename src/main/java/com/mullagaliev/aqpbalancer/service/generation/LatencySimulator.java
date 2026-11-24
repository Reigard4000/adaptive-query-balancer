package com.mullagaliev.aqpbalancer.service.generation;

import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.util.MathUtils;
import org.apache.commons.math3.distribution.ParetoDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

/**
 * Simulates request latency with a Pareto distribution whose shape equals the
 * true tail index alpha. The estimator never receives this value directly: it
 * has to recover it from observed latencies.
 */
public class LatencySimulator {

    private static final double SCALE_MS = 40.0;
    private static final double MAX_LATENCY_MS = 60_000.0;
    private static final double MIN_ALPHA = 1.05;
    private static final double MAX_ALPHA = 10.0;

    private final RandomGenerator random;
    private double trueAlpha = 2.5;
    private ParetoDistribution cachedDistribution;
    private double cachedAlpha = Double.NaN;

    public LatencySimulator() {
        this(new Well19937c());
    }

    public LatencySimulator(long seed) {
        this(new Well19937c(seed));
    }

    public LatencySimulator(RandomGenerator random) {
        this.random = random;
    }

    public double simulate(QueryRequest request, Phase phase, StrategyType strategy) {
        double strategyFactor = switch (strategy) {
            case EXACT -> 1.0;
            case UNIFORM -> 0.22;
            case STRATIFIED -> 0.30;
        };
        double phaseFactor = switch (phase) {
            case LIGHT_TAIL -> 1.0;
            case WARNING -> 1.15;
            case HEAVY_TAIL -> 1.35;
            case UNKNOWN -> 1.0;
        };
        double rowFactor = 0.8 + 0.2 * Math.min(1.2, Math.log10(Math.max(10L, request.estimatedRows())) / 5.0);

        double base = nextParetoSample();
        double latency = base * strategyFactor * phaseFactor * rowFactor;
        return MathUtils.clamp(latency, 1.0, MAX_LATENCY_MS);
    }

    private double nextParetoSample() {
        double alpha = MathUtils.clamp(trueAlpha, MIN_ALPHA, MAX_ALPHA);
        if (cachedDistribution == null || Double.compare(cachedAlpha, alpha) != 0) {
            cachedDistribution = new ParetoDistribution(SCALE_MS, alpha);
            cachedAlpha = alpha;
        }
        double u = random.nextDouble();
        return cachedDistribution.inverseCumulativeProbability(u);
    }

    public void setAlpha(double alpha) {
        if (Double.isFinite(alpha) && alpha > 0.0) {
            this.trueAlpha = alpha;
        }
    }

    public double getAlpha() {
        return trueAlpha;
    }
}
