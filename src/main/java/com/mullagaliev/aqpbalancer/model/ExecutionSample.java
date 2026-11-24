package com.mullagaliev.aqpbalancer.model;

public record ExecutionSample(
        String requestId,
        double latencyMs,
        boolean success,
        Double relativeError
) {
}
