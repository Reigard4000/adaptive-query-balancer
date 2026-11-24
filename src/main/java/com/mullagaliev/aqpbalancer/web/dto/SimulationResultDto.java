package com.mullagaliev.aqpbalancer.web.dto;

import java.util.List;
import java.util.Map;

public record SimulationResultDto(
        int processedCount,
        double averageLatency,
        double latencyP50,
        double latencyP95,
        double latencyP99,
        double minLatency,
        double maxLatency,
        int windowSize,
        double estimatedAlpha,
        double smoothedAlpha,
        double trueAlpha,
        double ews,
        double cusum,
        String phase,
        long elapsedMs,
        double throughput,
        Map<String, Long> strategyDistribution,
        Map<String, Long> phaseDistribution,
        Map<String, Long> queryTypeDistribution,
        List<ServerStatDto> servers,
        List<RecentRequestDto> recentRequests
) {
}
