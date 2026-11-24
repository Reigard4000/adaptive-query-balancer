package com.mullagaliev.aqpbalancer.util;

import java.util.Arrays;

public final class MathUtils {

    private MathUtils() {
    }

    public static double median(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Cannot compute median of an empty array");
        }
        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        int middle = copy.length / 2;
        if (copy.length % 2 == 1) {
            return copy[middle];
        }
        return (copy[middle - 1] + copy[middle]) / 2.0;
    }

    public static double min(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Cannot compute min of an empty array");
        }
        double result = Double.POSITIVE_INFINITY;
        for (double value : values) {
            result = Math.min(result, value);
        }
        return result;
    }

    public static double max(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Cannot compute max of an empty array");
        }
        double result = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    public static double clamp(double value, double from, double to) {
        if (from > to) {
            throw new IllegalArgumentException("'from' must be <= 'to'");
        }
        if (Double.isNaN(value)) {
            return from;
        }
        return Math.max(from, Math.min(to, value));
    }

    public static double mean(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
