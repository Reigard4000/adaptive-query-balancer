package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.model.Phase;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Combines a CUSUM statistic for downward alpha shifts with the EWS score and
 * classifies the current phase.
 */
public class PhaseTransitionDetector {

    public static final int DEFAULT_MIN_ALPHA_SAMPLES = 8;
    public static final int DEFAULT_MAX_HISTORY = 120;

    private static final double LIGHT_TAIL_THRESHOLD = 2.2;
    private static final double HEAVY_TAIL_THRESHOLD = 2.0;
    private static final double EWS_WARNING_THRESHOLD = 0.6;
    private static final double EARLY_WARNING_ALPHA = 2.25;
    private static final double ALPHA_SMOOTHING = 0.30;

    private final int minAlphaSamples;
    private final int maxHistory;
    private final double cusumBaseline;
    private final double cusumDrift;
    private final double cusumThreshold;

    private final EwsCalculator ewsCalculator = new EwsCalculator();
    private final Deque<Double> alphaHistory = new ArrayDeque<>();

    private double cusum = 0.0;
    private double lastEws = 0.0;
    private double smoothedAlpha = Double.NaN;
    private Phase phase = Phase.UNKNOWN;

    public PhaseTransitionDetector() {
        this(DEFAULT_MIN_ALPHA_SAMPLES);
    }

    public PhaseTransitionDetector(int minAlphaSamples) {
        this(minAlphaSamples, DEFAULT_MAX_HISTORY, 2.2, 0.05, 1.0);
    }

    public PhaseTransitionDetector(
            int minAlphaSamples,
            int maxHistory,
            double cusumBaseline,
            double cusumDrift,
            double cusumThreshold
    ) {
        if (minAlphaSamples < 1 || maxHistory < minAlphaSamples) {
            throw new IllegalArgumentException("Invalid detector window configuration");
        }
        this.minAlphaSamples = minAlphaSamples;
        this.maxHistory = maxHistory;
        this.cusumBaseline = cusumBaseline;
        this.cusumDrift = cusumDrift;
        this.cusumThreshold = cusumThreshold;
    }

    /**
     * Feeds a new alpha estimate into the detector and returns the updated phase.
     */
    public synchronized Phase detect(double alpha) {
        if (!Double.isFinite(alpha) || alpha <= 0.0) {
            return phase;
        }

        smoothedAlpha = Double.isNaN(smoothedAlpha)
                ? alpha
                : (1.0 - ALPHA_SMOOTHING) * smoothedAlpha + ALPHA_SMOOTHING * alpha;

        alphaHistory.addLast(smoothedAlpha);
        while (alphaHistory.size() > maxHistory) {
            alphaHistory.removeFirst();
        }

        cusum = Math.max(0.0, cusum + (cusumBaseline - smoothedAlpha) - cusumDrift);

        if (alphaHistory.size() < minAlphaSamples) {
            phase = Phase.UNKNOWN;
            return phase;
        }

        double[] history = alphaHistory.stream().mapToDouble(Double::doubleValue).toArray();
        lastEws = ewsCalculator.calculate(history);
        phase = classify(smoothedAlpha, lastEws, cusum);
        return phase;
    }

    private Phase classify(double alpha, double ews, double cusumValue) {
        if (alpha <= HEAVY_TAIL_THRESHOLD) {
            return Phase.HEAVY_TAIL;
        }
        if (alpha <= LIGHT_TAIL_THRESHOLD) {
            return Phase.WARNING;
        }
        boolean earlyWarning = alpha <= EARLY_WARNING_ALPHA
                && (ews > EWS_WARNING_THRESHOLD || cusumValue > cusumThreshold);
        return earlyWarning ? Phase.WARNING : Phase.LIGHT_TAIL;
    }

    public synchronized Phase getPhase() {
        return phase;
    }

    public synchronized double getLastEws() {
        return lastEws;
    }

    public synchronized double getCusum() {
        return cusum;
    }

    public synchronized int getAlphaHistorySize() {
        return alphaHistory.size();
    }

    public synchronized double getSmoothedAlpha() {
        return smoothedAlpha;
    }

    public synchronized void reset() {
        alphaHistory.clear();
        cusum = 0.0;
        lastEws = 0.0;
        smoothedAlpha = Double.NaN;
        phase = Phase.UNKNOWN;
    }
}
