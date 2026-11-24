package com.mullagaliev.aqpbalancer.service.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import com.mullagaliev.aqpbalancer.service.balancer.ServerRegistry;
import com.mullagaliev.aqpbalancer.web.dto.DiscoveryResultDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Discovers backend nodes by probing configurable addresses.
 *
 * <p>The default deployment scans a local port range, but the list of hosts and
 * ports is fully configuration-driven. Manual URL registration remains
 * available through the server API.</p>
 */
@Service
public class ServerDiscoveryService {

    private static final int DISCOVERY_TIMEOUT_MS = 1_500;
    private static final int MAX_SCAN_PORTS = 500;
    private static final int DEFAULT_WEIGHT = 10;
    private static final int DEFAULT_CAPACITY = 32;
    private static final int DEFAULT_TIMEOUT_MS = 5_000;

    private final ServerRegistry serverRegistry;
    private final ObjectMapper objectMapper;
    private final List<String> candidates;
    private final boolean enabled;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(DISCOVERY_TIMEOUT_MS))
            .build();

    public ServerDiscoveryService(
            ServerRegistry serverRegistry,
            ObjectMapper objectMapper,
            @Value("${balancer.discovery.enabled:false}") boolean enabled,
            @Value("${balancer.discovery.urls:}") String discoveryUrls,
            @Value("${balancer.discovery.hosts:}") String discoveryHosts,
            @Value("${balancer.discovery.port-start:9001}") int portStart,
            @Value("${balancer.discovery.port-end:9010}") int portEnd
    ) {
        this.serverRegistry = Objects.requireNonNull(serverRegistry, "serverRegistry");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.enabled = enabled;
        this.candidates = enabled ? buildCandidates(discoveryUrls, discoveryHosts, portStart, portEnd) : List.of();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public DiscoveryResultDto discover() {
        if (!enabled) {
            throw new IllegalStateException(
                    "Discovery is disabled. Set balancer.discovery.enabled=true and configure hosts/urls."
            );
        }
        List<DiscoveryResultDto.DiscoveredBackendDto> added = new ArrayList<>();
        List<DiscoveryResultDto.DiscoveredBackendDto> existing = new ArrayList<>();
        List<DiscoveryResultDto.UnreachableBackendDto> unreachable = new ArrayList<>();

        List<ProbeResult> probes = candidates.parallelStream()
                .map(this::probe)
                .toList();

        for (ProbeResult probe : probes) {
            String executeUrl = probe.baseUrl() + "/execute";
            Optional<BackendServer> existingServer = serverRegistry.findByUrl(executeUrl);
            if (existingServer.isPresent()) {
                BackendServer server = existingServer.get();
                existing.add(new DiscoveryResultDto.DiscoveredBackendDto(
                        server.getId(),
                        server.getDisplayName(),
                        executeUrl,
                        null,
                        null
                ));
                continue;
            }

            if (probe.info() == null) {
                unreachable.add(new DiscoveryResultDto.UnreachableBackendDto(
                        probe.baseUrl(),
                        probe.error() == null ? "недоступен" : probe.error()
                ));
                continue;
            }

            BackendInfo info = probe.info();
            String serverName = info.name() == null || info.name().isBlank() ? "server" : info.name();
            ServerSettings settings = new ServerSettings(
                    serverName,
                    ServerType.HTTP,
                    executeUrl,
                    DEFAULT_WEIGHT,
                    info.capacity() == null ? DEFAULT_CAPACITY : info.capacity(),
                    true,
                    DEFAULT_TIMEOUT_MS,
                    ServerSettings.DEFAULT_BASE_LATENCY_MS,
                    0.0
            );

            BackendServer server = serverRegistry.add(settings);
            server.checkHealth();
            added.add(new DiscoveryResultDto.DiscoveredBackendDto(
                    server.getId(),
                    server.getDisplayName(),
                    executeUrl,
                    info.serverId(),
                    info.profile()
            ));
        }

        return new DiscoveryResultDto(added, existing, unreachable);
    }

    public List<String> getCandidates() {
        return candidates;
    }

    private ProbeResult probe(String baseUrl) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/info"))
                            .timeout(Duration.ofMillis(DISCOVERY_TIMEOUT_MS))
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ProbeResult(baseUrl, null, "HTTP " + response.statusCode());
            }

            BackendInfo info = objectMapper.readValue(response.body(), BackendInfo.class);
            if (info.serverId() == null || info.serverId().isBlank()) {
                return new ProbeResult(baseUrl, null, "в ответе /info нет serverId");
            }
            return new ProbeResult(baseUrl, info, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProbeResult(baseUrl, null, "прервано");
        } catch (Exception exception) {
            String message = exception.getMessage();
            return new ProbeResult(
                    baseUrl,
                    null,
                    exception.getClass().getSimpleName() + (message == null ? "" : ": " + message)
            );
        }
    }

    private List<String> buildCandidates(
            String discoveryUrls,
            String discoveryHosts,
            int portStart,
            int portEnd
    ) {
        Set<String> result = new LinkedHashSet<>();

        if (discoveryUrls != null && !discoveryUrls.isBlank()) {
            Arrays.stream(discoveryUrls.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(this::normalizeBaseUrl)
                    .forEach(result::add);
        }

        if (discoveryHosts != null && !discoveryHosts.isBlank()) {
            int start = Math.max(1, portStart);
            int end = Math.min(65_535, portEnd);
            int portCount = end - start + 1;
            if (portCount > 0 && portCount <= MAX_SCAN_PORTS) {
                for (String host : discoveryHosts.split(",")) {
                    String normalizedHost = host.trim();
                    if (normalizedHost.isEmpty()) {
                        continue;
                    }
                    for (int port = start; port <= end; port++) {
                        result.add("http://" + normalizedHost + ":" + port);
                    }
                }
            }
        }

        return List.copyOf(result);
    }

    private String normalizeBaseUrl(String url) {
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BackendInfo(
            String serverId,
            String name,
            String profile,
            String version,
            Integer capacity,
            Integer queueCapacity
    ) {
    }

    private record ProbeResult(
            String baseUrl,
            BackendInfo info,
            String error
    ) {
    }
}
