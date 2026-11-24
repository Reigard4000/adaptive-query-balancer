package com.mullagaliev.aqpbalancer.model;

/**
 * Result of one backend invocation.
 *
 * <p>{@code latencyMs} is measured by the balancer end-to-end, while
 * {@code result}, {@code sampleSize}, {@code totalRows} and {@code approximate}
 * come from the backend response body. {@code exactResult} and
 * {@code relativeError} are optional: a backend may report either of them when
 * it can evaluate the accuracy of its own approximate result.</p>
 */
public record ServerExecutionResult(
        double latencyMs,
        boolean success,
        int statusCode,
        String error,
        Double result,
        int sampleSize,
        long totalRows,
        boolean approximate,
        Double exactResult,
        Double relativeError
) {

    public ServerExecutionResult {
        if (!Double.isFinite(latencyMs) || latencyMs < 0.0) {
            latencyMs = 0.0;
        }
        if (error != null && error.isBlank()) {
            error = null;
        }
        if (exactResult != null && !Double.isFinite(exactResult)) {
            exactResult = null;
        }
        if (relativeError != null) {
            if (!Double.isFinite(relativeError)) {
                relativeError = null;
            } else {
                relativeError = Math.max(0.0, Math.min(1.0, relativeError));
            }
        }
    }

    public static ServerExecutionResult success(double latencyMs, int statusCode) {
        return new ServerExecutionResult(latencyMs, true, statusCode, null, null, 0, 0, false, null, null);
    }

    public static ServerExecutionResult success(
            double latencyMs,
            int statusCode,
            double result,
            int sampleSize,
            long totalRows,
            boolean approximate
    ) {
        return success(latencyMs, statusCode, result, sampleSize, totalRows, approximate, null, null);
    }

    public static ServerExecutionResult success(
            double latencyMs,
            int statusCode,
            double result,
            int sampleSize,
            long totalRows,
            boolean approximate,
            Double exactResult,
            Double relativeError
    ) {
        return new ServerExecutionResult(
                latencyMs,
                true,
                statusCode,
                null,
                result,
                Math.max(0, sampleSize),
                Math.max(0L, totalRows),
                approximate,
                exactResult,
                relativeError
        );
    }

    public static ServerExecutionResult failure(double latencyMs, int statusCode, String error) {
        return new ServerExecutionResult(latencyMs, false, statusCode, error, null, 0, 0, false, null, null);
    }

    public boolean hasResult() {
        return result != null && Double.isFinite(result);
    }
}
