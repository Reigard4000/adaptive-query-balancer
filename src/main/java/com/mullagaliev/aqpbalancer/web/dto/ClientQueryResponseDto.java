package com.mullagaliev.aqpbalancer.web.dto;

public record ClientQueryResponseDto(
        String requestId,
        String status,
        String serverId,
        String serverLabel,
        String strategy,
        String phase,
        Double result,
        Double latencyMs,
        Double relativeError,
        String loadLevel,
        Double utilization,
        Integer activeServers,
        Integer inFlight,
        Integer capacity,
        String message
) {
}
