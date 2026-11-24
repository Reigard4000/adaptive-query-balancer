package com.mullagaliev.aqpbalancer.service.quality;

import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.ServerExecutionResult;

import java.util.OptionalDouble;

/**
 * Uses the accuracy information returned by the backend.
 *
 * <p>Priority:</p>
 * <ol>
 *     <li>{@code relativeError} explicitly reported by the backend;</li>
 *     <li>{@code exactResult} + approximate result, from which the relative
 *     error is calculated;</li>
 *     <li>an EXACT (non-approximate) result is considered fully accurate.</li>
 * </ol>
 */
public class BackendAccuracyProvider implements AccuracyProvider {

    @Override
    public OptionalDouble relativeError(QueryRequest request, ServerExecutionResult execution) {
        if (execution == null || !execution.success() || !execution.hasResult()) {
            return OptionalDouble.empty();
        }
        if (execution.relativeError() != null && Double.isFinite(execution.relativeError())) {
            return OptionalDouble.of(clamp(execution.relativeError()));
        }
        if (execution.exactResult() != null && Double.isFinite(execution.exactResult())) {
            return OptionalDouble.of(relativeError(execution.result(), execution.exactResult()));
        }
        if (!execution.approximate()) {
            return OptionalDouble.of(0.0);
        }
        return OptionalDouble.empty();
    }

    private double relativeError(double approximate, double exact) {
        double denominator = Math.max(1.0, Math.abs(exact));
        double error = Math.abs(approximate - exact) / denominator;
        return clamp(Double.isFinite(error) ? error : 1.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
