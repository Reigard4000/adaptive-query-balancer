package com.mullagaliev.aqpbalancer.web.dto;

import java.util.List;
import java.util.Map;

public record BalancerSnapshotDto(
        long snapshot,
        long elapsedMs,
        long offeredCount,
        long acceptedCount,
        long completedCount,
        long successCount,
        long failedCount,
        long shedCount,
        int inFlight,
        double averageLatency,
        double latencyP50,
        double latencyP95,
        double latencyP99,
        double minLatency,
        double maxLatency,
        int windowSize,
        double estimatedAlpha,
        double smoothedAlpha,
        String phase,
        double ews,
        double cusum,
        String loadLevel,
        double utilization,
        int activeServers,
        int totalServers,
        int totalCapacity,
        Map<String, Long> strategyDistribution,
        Map<String, Long> phaseDistribution,
        Map<String, Long> queryTypeDistribution,
        List<ServerStatDto> servers,
        List<RecentRequestDto> recentRequests,
        String warning
) {
}
