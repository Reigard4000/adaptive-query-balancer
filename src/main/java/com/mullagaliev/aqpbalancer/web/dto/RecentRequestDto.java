package com.mullagaliev.aqpbalancer.web.dto;

public record RecentRequestDto(
        String id,
        String serverId,
        String type,
        long estimatedRows,
        double latencyMs,
        String strategy,
        String phase,
        boolean success,
        double result,
        Double relativeError,
        String status
) {
}
