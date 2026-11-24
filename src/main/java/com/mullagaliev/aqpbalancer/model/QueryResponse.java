package com.mullagaliev.aqpbalancer.model;

public record QueryResponse(
        String requestId,
        double approximateResult,
        double latencyMs,
        StrategyType strategyUsed,
        Phase phaseAtTime,
        String serverId,
        boolean success,
        Double relativeError
) {
}
