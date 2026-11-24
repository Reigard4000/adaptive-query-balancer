package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.exception.EstimationException;

/**
 * Pickands (1975) tail index estimator.
 *
 * <pre>
 * alpha = ln(2) / ln( (x_k - x_2k) / (x_2k - x_4k) )
 * </pre>
 */
public class PickandsEstimator implements TailIndexEstimator {

    private static final int MIN_SAMPLES = 20;
    private static final double TAIL_FRACTION = 0.25;
    private static final double MIN_VALID_ALPHA = 1.01;
    private static final double MAX_VALID_ALPHA = 12.0;

    @Override
    public double estimate(double[] values) {
        double[] sorted = positiveSortedDescending(values);
        int n = sorted.length;
        if (n < MIN_SAMPLES) {
            throw new EstimationException("Pickands estimator needs at least " + MIN_SAMPLES + " positive values");
        }

        int k = Math.max(1, (int) Math.round(n * TAIL_FRACTION));
        if (4 * k > n) {
            k = Math.max(1, n / 4);
        }

        double xk = sorted[k - 1];
        double x2k = sorted[2 * k - 1];
        double x4k = sorted[4 * k - 1];

        double numerator = xk - x2k;
        double denominator = x2k - x4k;
        if (numerator <= 0.0 || denominator <= 0.0) {
            throw new EstimationException("Pickands estimator requires strictly separated order statistics");
        }

        double ratio = numerator / denominator;
        if (!Double.isFinite(ratio) || ratio <= 1.0) {
            throw new EstimationException("Pickands ratio is outside the valid range: " + ratio);
        }

        double estimate = Math.log(2.0) / Math.log(ratio);
        if (!Double.isFinite(estimate) || estimate < MIN_VALID_ALPHA || estimate > MAX_VALID_ALPHA) {
            throw new EstimationException("Pickands estimator produced an invalid value: " + estimate);
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
