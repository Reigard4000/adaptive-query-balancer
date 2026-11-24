package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.exception.EstimationException;

/**
 * Dekkers-Eenmaas moment estimator (1989).
 */
public class MomentsEstimator implements TailIndexEstimator {

    private static final int MIN_SAMPLES = 20;
    private static final int MIN_K = 5;
    private static final double TAIL_FRACTION = 0.25;
    private static final double MIN_VALID_ALPHA = 1.01;
    private static final double MAX_VALID_ALPHA = 12.0;

    @Override
    public double estimate(double[] values) {
        double[] sorted = positiveSortedDescending(values);
        int n = sorted.length;
        if (n < MIN_SAMPLES) {
            throw new EstimationException("Moments estimator needs at least " + MIN_SAMPLES + " positive values");
        }

        int k = Math.max(MIN_K, (int) Math.round(n * TAIL_FRACTION));
        k = Math.min(k, n - 1);
        double threshold = sorted[k];
        if (threshold <= 0.0) {
            throw new EstimationException("Moments threshold must be positive");
        }

        double m1 = 0.0;
        double m2 = 0.0;
        int count = 0;
        for (int i = 0; i < k; i++) {
            if (sorted[i] > threshold) {
                double logRatio = Math.log(sorted[i] / threshold);
                m1 += logRatio;
                m2 += logRatio * logRatio;
                count++;
            }
        }
        if (count < 2) {
            throw new EstimationException("Moments estimator needs at least two observations above the threshold");
        }

        m1 /= count;
        m2 /= count;
        if (m2 <= 0.0) {
            throw new EstimationException("Moments estimator is undefined for a degenerate sample");
        }

        double ratio = m1 * m1 / m2;
        ratio = Math.min(ratio, 0.999999);
        double gamma = m1 + 1.0 - 0.5 / (1.0 - ratio);
        if (!Double.isFinite(gamma) || gamma <= 0.0) {
            throw new EstimationException("Moments estimator produced an invalid shape: " + gamma);
        }

        double estimate = 1.0 / gamma;
        if (!Double.isFinite(estimate) || estimate < MIN_VALID_ALPHA || estimate > MAX_VALID_ALPHA) {
            throw new EstimationException("Moments estimator produced an invalid value: " + estimate);
        }
        return estimate;
    }

    private static double[] positiveSortedDescending(double[] values) {
        if (values == null) {
            return new double[0];
        }
        return java.util.Arrays.stream(values)
                .filter(value -> Double.isFinite(value) && value > 0.0)
                .boxed()
                .sorted(java.util.Comparator.reverseOrder())
                .mapToDouble(Double::doubleValue)
                .toArray();
    }
}
