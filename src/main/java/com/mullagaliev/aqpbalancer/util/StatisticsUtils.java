package com.mullagaliev.aqpbalancer.util;

public final class StatisticsUtils {

    private StatisticsUtils() {
    }

    public static double variance(double[] values) {
        if (values == null || values.length < 2) {
            return 0.0;
        }
        double mean = MathUtils.mean(values);
        double sum = 0.0;
        for (double value : values) {
            double delta = value - mean;
            sum += delta * delta;
        }
        return sum / values.length;
    }

    public static double lag1Autocorrelation(double[] values) {
        if (values == null || values.length < 3) {
            return 0.0;
        }
        double mean = MathUtils.mean(values);
        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 1; i < values.length; i++) {
            numerator += (values[i] - mean) * (values[i - 1] - mean);
        }
        for (double value : values) {
            denominator += (value - mean) * (value - mean);
        }
        if (denominator <= 1.0e-12) {
            return 0.0;
        }
        return MathUtils.clamp(numerator / denominator, -1.0, 1.0);
    }

    /**
     * Least-squares slope of the series (change per one observation).
     */
    public static double trend(double[] values) {
        if (values == null || values.length < 2) {
            return 0.0;
        }
        int n = values.length;
        double meanIndex = (n - 1) / 2.0;
        double meanValue = MathUtils.mean(values);
        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < n; i++) {
            double deltaIndex = i - meanIndex;
            numerator += deltaIndex * (values[i] - meanValue);
            denominator += deltaIndex * deltaIndex;
        }
        return denominator <= 1.0e-12 ? 0.0 : numerator / denominator;
    }

    /**
     * Fraction of adjacent pairs that cross the given level.
     */
    public static double crossingFrequency(double[] values, double level) {
        if (values == null || values.length < 2) {
            return 0.0;
        }
        int crossings = 0;
        for (int i = 1; i < values.length; i++) {
            if ((values[i - 1] - level) * (values[i] - level) < 0.0) {
                crossings++;
            }
        }
        return crossings / (double) (values.length - 1);
    }
}
