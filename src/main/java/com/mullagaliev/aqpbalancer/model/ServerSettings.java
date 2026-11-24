package com.mullagaliev.aqpbalancer.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editable server configuration shared by simulated and real HTTP backends.
 *
 * <p>HTTP servers may override the health and stats endpoints and add custom
 * request headers. Header values prefixed with {@code env:} are resolved from
 * the environment at request time, so secrets do not have to be persisted in
 * the JSON state file.</p>
 */
public record ServerSettings(
        String name,
        ServerType type,
        String url,
        int weight,
        int capacity,
        boolean enabled,
        int timeoutMs,
        double baseLatencyMs,
        double failureRate,
        String healthUrl,
        String statsUrl,
        String healthMethod,
        Map<String, String> headers
) {

    public static final int DEFAULT_WEIGHT = 10;
    public static final int DEFAULT_CAPACITY = 32;
    public static final int DEFAULT_TIMEOUT_MS = 3_000;
    public static final double DEFAULT_BASE_LATENCY_MS = 40.0;
    public static final double DEFAULT_FAILURE_RATE = 0.0;
    public static final String DEFAULT_HEALTH_METHOD = "GET";

    public ServerSettings {
        name = (name == null || name.isBlank()) ? "server" : name.trim();
        type = type == null ? ServerType.HTTP : type;
        url = url == null ? "" : url.trim();
        healthUrl = healthUrl == null ? "" : healthUrl.trim();
        statsUrl = statsUrl == null ? "" : statsUrl.trim();
        healthMethod = normalizeMethod(healthMethod);

        if (weight <= 0) {
            weight = DEFAULT_WEIGHT;
        }
        weight = Math.min(100, weight);

        if (capacity <= 0) {
            capacity = DEFAULT_CAPACITY;
        }
        capacity = Math.max(1, Math.min(10_000, capacity));

        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
        timeoutMs = Math.max(100, Math.min(60_000, timeoutMs));

        if (!Double.isFinite(baseLatencyMs) || baseLatencyMs <= 0.0) {
            baseLatencyMs = DEFAULT_BASE_LATENCY_MS;
        }
        baseLatencyMs = Math.max(1.0, Math.min(60_000.0, baseLatencyMs));

        if (!Double.isFinite(failureRate)) {
            failureRate = DEFAULT_FAILURE_RATE;
        }
        failureRate = Math.max(0.0, Math.min(1.0, failureRate));

        if (headers == null) {
            headers = Map.of();
        } else {
            Map<String, String> copy = new LinkedHashMap<>();
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    copy.put(key.trim(), value.trim());
                }
            });
            headers = Map.copyOf(copy);
        }

        if (type == ServerType.HTTP) {
            validateUrl(url);
            if (!healthUrl.isBlank()) {
                validateUrl(healthUrl);
            }
            if (!statsUrl.isBlank()) {
                validateUrl(statsUrl);
            }
        }
    }

    /**
     * Backward-compatible constructor for callers that do not override health
     * and stats endpoints.
     */
    public ServerSettings(
            String name,
            ServerType type,
            String url,
            int weight,
            int capacity,
            boolean enabled,
            int timeoutMs,
            double baseLatencyMs,
            double failureRate
    ) {
        this(name, type, url, weight, capacity, enabled, timeoutMs, baseLatencyMs, failureRate,
                "", "", DEFAULT_HEALTH_METHOD, Map.of());
    }

    public static ServerSettings simulated(
            String name,
            int weight,
            double baseLatencyMs,
            double failureRate
    ) {
        return new ServerSettings(
                name,
                ServerType.SIMULATED,
                "",
                weight,
                DEFAULT_CAPACITY,
                true,
                DEFAULT_TIMEOUT_MS,
                baseLatencyMs,
                failureRate
        );
    }

    public static ServerSettings http(String name, String url, int weight, int timeoutMs) {
        return new ServerSettings(
                name,
                ServerType.HTTP,
                url,
                weight,
                DEFAULT_CAPACITY,
                true,
                timeoutMs,
                DEFAULT_BASE_LATENCY_MS,
                DEFAULT_FAILURE_RATE
        );
    }

    public String resolvedHealthUrl() {
        return healthUrl.isBlank() ? deriveEndpoint(url, "/health") : healthUrl;
    }

    public String resolvedStatsUrl() {
        return statsUrl.isBlank() ? deriveEndpoint(url, "/stats") : statsUrl;
    }

    public static String deriveEndpoint(String executeUrl, String rootPath) {
        try {
            URI uri = URI.create(executeUrl);
            String authority = uri.getRawAuthority();
            if (authority == null || authority.isBlank()) {
                return executeUrl;
            }
            return uri.getScheme() + "://" + authority + rootPath;
        } catch (IllegalArgumentException exception) {
            return executeUrl;
        }
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return DEFAULT_HEALTH_METHOD;
        }
        String normalized = method.trim().toUpperCase();
        return "HEAD".equals(normalized) ? "HEAD" : "GET";
    }

    private static void validateUrl(String url) {
        if (url.isBlank()) {
            throw new IllegalArgumentException("Для HTTP-сервера укажите URL");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("URL должен начинаться с http:// или https://");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Некорректный URL сервера: " + url);
        }
    }
}
