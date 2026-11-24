package com.mullagaliev.aqpbalancer.web.dto;

import java.util.List;
import java.util.Map;

public record StreamSnapshotDto(
        long snapshot,
        long elapsedMs,
        long processedCount,
        double throughput,
        double averageLatency,
        double windowP50,
        double windowP95,
        double windowP99,
        double minLatency,
        double maxLatency,
        int windowSize,
        double trueAlpha,
        double estimatedAlpha,
        double smoothedAlpha,
        String phase,
        double ews,
        double cusum,
        Map<String, Long> strategyDistribution,
        Map<String, Long> phaseDistribution,
        Map<String, Long> queryTypeDistribution,
        List<ServerStatDto> servers,
        List<RecentRequestDto> recentRequests
) {
}
