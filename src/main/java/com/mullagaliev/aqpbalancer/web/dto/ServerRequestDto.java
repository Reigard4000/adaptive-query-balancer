package com.mullagaliev.aqpbalancer.web.dto;

import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;

import java.util.Map;

public record ServerRequestDto(
        String name,
        String type,
        String url,
        Integer weight,
        Integer capacity,
        Boolean enabled,
        Integer timeoutMs,
        Double baseLatencyMs,
        Double failureRate,
        String healthUrl,
        String statsUrl,
        String healthMethod,
        Map<String, String> headers
) {

    public ServerRequestDto(
            String name,
            String type,
            String url,
            Integer weight,
            Integer capacity,
            Boolean enabled,
            Integer timeoutMs,
            Double baseLatencyMs,
            Double failureRate
    ) {
        this(name, type, url, weight, capacity, enabled, timeoutMs, baseLatencyMs, failureRate,
                null, null, null, null);
    }

    public ServerSettings toSettings() {
        return toSettings(null);
    }

    /**
     * Builds settings for an update. Fields that are absent from the request
     * keep their current values instead of being silently reset to defaults.
     */
    public ServerSettings toSettings(ServerSettings current) {
        ServerType serverType = type == null || type.isBlank()
                ? (current == null ? ServerType.HTTP : current.type())
                : parseType(type);
        String resolvedName = name == null || name.isBlank()
                ? (current == null ? null : current.name())
                : name;
        String resolvedUrl = url == null
                ? (current == null ? null : current.url())
                : url;
        int safeWeight = weight == null
                ? (current == null ? ServerSettings.DEFAULT_WEIGHT : current.weight())
                : weight;
        int safeCapacity = capacity == null
                ? (current == null ? ServerSettings.DEFAULT_CAPACITY : current.capacity())
                : capacity;
        boolean safeEnabled = enabled == null
                ? (current == null || current.enabled())
                : enabled;
        int safeTimeout = timeoutMs == null
                ? (current == null ? ServerSettings.DEFAULT_TIMEOUT_MS : current.timeoutMs())
                : timeoutMs;
        double safeBaseLatency = baseLatencyMs == null
                ? (current == null ? ServerSettings.DEFAULT_BASE_LATENCY_MS : current.baseLatencyMs())
                : baseLatencyMs;
        double safeFailureRate = failureRate == null
                ? (current == null ? 0.0 : current.failureRate())
                : failureRate;
        String safeHealthUrl = healthUrl == null
                ? (current == null ? "" : current.healthUrl())
                : healthUrl;
        String safeStatsUrl = statsUrl == null
                ? (current == null ? "" : current.statsUrl())
                : statsUrl;
        String safeHealthMethod = healthMethod == null
                ? (current == null ? ServerSettings.DEFAULT_HEALTH_METHOD : current.healthMethod())
                : healthMethod;
        Map<String, String> safeHeaders = headers == null
                ? (current == null ? Map.of() : current.headers())
                : headers;

        return new ServerSettings(
                resolvedName,
                serverType,
                resolvedUrl,
                safeWeight,
                safeCapacity,
                safeEnabled,
                safeTimeout,
                safeBaseLatency,
                safeFailureRate,
                safeHealthUrl,
                safeStatsUrl,
                safeHealthMethod,
                safeHeaders
        );
    }

    private ServerType parseType(String value) {
        String normalized = value.trim().toUpperCase();
        if ("SIMULATED".equals(normalized) || "SIM".equals(normalized)) {
            return ServerType.SIMULATED;
        }
        return ServerType.HTTP;
    }
}
