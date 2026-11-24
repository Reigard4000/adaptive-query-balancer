package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.exception.EstimationException;

import java.util.Arrays;

/**
 * Hill (1975) tail index estimator.
 *
 * <pre>
 * alpha = 1 / mean( ln(x_i / x_k) ), i = 1..k
 * </pre>
 */
public class HillEstimator implements TailIndexEstimator {

    private static final int MIN_SAMPLES = 20;
    private static final int MIN_K = 5;
    private static final double TAIL_FRACTION = 0.20;
    private static final double MIN_VALID_ALPHA = 1.01;
    private static final double MAX_VALID_ALPHA = 12.0;

    @Override
    public double estimate(double[] values) {
        double[] sorted = positiveSortedDescending(values);
        int n = sorted.length;
        if (n < MIN_SAMPLES) {
            throw new EstimationException("Hill estimator needs at least " + MIN_SAMPLES + " positive values");
        }

        int k = Math.max(MIN_K, (int) Math.round(n * TAIL_FRACTION));
        k = Math.min(k, n - 1);
        double threshold = sorted[k];
        if (threshold <= 0.0) {
            throw new EstimationException("Hill threshold must be positive");
        }

        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < k; i++) {
            if (sorted[i] > threshold) {
                sum += Math.log(sorted[i] / threshold);
                count++;
            }
        }
        if (count == 0 || sum <= 0.0) {
            throw new EstimationException("Hill estimator is undefined for a degenerate sample");
        }

        double estimate = count / sum;
        if (!Double.isFinite(estimate) || estimate < MIN_VALID_ALPHA || estimate > MAX_VALID_ALPHA) {
            throw new EstimationException("Hill estimator produced an invalid value: " + estimate);
        }
        return estimate;
    }

    private static double[] positiveSortedDescending(double[] values) {
        if (values == null) {
            return new double[0];
        }
        double[] sorted = Arrays.stream(values)
                .filter(value -> Double.isFinite(value) && value > 0.0)
                .sorted()
                .toArray();
        for (int left = 0, right = sorted.length - 1; left < right; left++, right--) {
            double tmp = sorted[left];
            sorted[left] = sorted[right];
            sorted[right] = tmp;
        }
        return sorted;
    }
}
