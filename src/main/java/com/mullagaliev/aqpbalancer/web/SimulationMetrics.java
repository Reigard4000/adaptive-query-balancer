package com.mullagaliev.aqpbalancer.web;

import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.QueryType;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.web.dto.ServerStatDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared helpers for building web-facing statistics.
 */
public final class SimulationMetrics {

    private SimulationMetrics() {
    }

    public static Map<String, Long> strategyDistribution(Map<StrategyType, Long> counts) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (StrategyType strategy : StrategyType.values()) {
            distribution.put(strategy.name(), counts.getOrDefault(strategy, 0L));
        }
        return distribution;
    }

    public static Map<String, Long> phaseDistribution(Map<Phase, Long> counts) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Phase phase : Phase.values()) {
            distribution.put(phase.name(), counts.getOrDefault(phase, 0L));
        }
        return distribution;
    }

    public static Map<String, Long> queryTypeDistribution(Map<QueryType, Long> counts) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (QueryType type : QueryType.values()) {
            distribution.put(type.name(), counts.getOrDefault(type, 0L));
        }
        return distribution;
    }

    public static List<ServerStatDto> serverStats(List<BackendServer> servers, long processedCount) {
        return serverStats(servers, processedCount, Set.of());
    }

    public static List<ServerStatDto> serverStats(
            List<BackendServer> servers,
            long processedCount,
            Set<String> activeServerIds
    ) {
        List<ServerStatDto> stats = new ArrayList<>(servers.size());
        for (BackendServer server : servers) {
            stats.add(serverStat(server, processedCount, activeServerIds.contains(server.getId())));
        }
        return stats;
    }

    public static ServerStatDto serverStat(BackendServer server, long processedCount) {
        return serverStat(server, processedCount, server.isEnabled());
    }

    public static ServerStatDto serverStat(BackendServer server, long processedCount, boolean active) {
        long requests = server.getTotalRequests();
        double share = processedCount == 0 ? 0.0 : requests * 100.0 / processedCount;
        return new ServerStatDto(
                server.getId(),
                server.getDisplayName(),
                server.getType().name(),
                server.getUrl(),
                server.getWeight(),
                server.isEnabled(),
                server.getTimeoutMs(),
                round2(server.getBaseLatencyMs()),
                round2(server.getFailureRate()),
                round2(server.getSimulatedAlpha()),
                requests,
                server.getSuccessCount(),
                server.getFailureCount(),
                round2(share),
                round2(server.getAverageLatencyMs()),
                round2(server.getLastLatencyMs()),
                server.getQueueSize(),
                server.getActiveRequests(),
                server.loadScore(),
                server.getLastStatusCode(),
                server.getLastError(),
                server.isHealthy(),
                server.isHealthCheckDone(),
                server.getLastHealthCheckAtMs(),
                server.getEffectiveCapacity(),
                server.getRemoteQueueCapacity(),
                server.getQueuedLoad(),
                server.getRejectedRequests(),
                round2(server.projectedUtilization()),
                active,
                active ? "ACTIVE" : "STANDBY"
        );
    }

    /**
     * @return p50, p95 and p99 of the supplied values
     */
    public static double[] latencyPercentiles(double[] values) {
        if (values == null || values.length == 0) {
            return new double[]{0.0, 0.0, 0.0};
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return new double[]{
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99)
        };
    }

    public static double round2(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    public static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double percentile(double[] sorted, double percentile) {
        int rank = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank))];
    }
}
