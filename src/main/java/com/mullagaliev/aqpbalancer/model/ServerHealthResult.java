package com.mullagaliev.aqpbalancer.model;

public record ServerHealthResult(
        boolean reachable,
        int statusCode,
        double latencyMs,
        String message
) {
}
