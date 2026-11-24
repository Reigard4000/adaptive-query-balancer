package com.mullagaliev.aqpbalancer.service.monitoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.ServerType;
import com.mullagaliev.aqpbalancer.service.balancer.ServerRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polls live load counters from backend {@code /stats} endpoints.
 *
 * <p>Polling is parallel and bounded. When a backend stops exposing fresh
 * stats, {@link BackendServer} falls back to local counters instead of reusing
 * stale values forever.</p>
 */
@Service
public class ServerLoadMonitor {

    private static final Logger log = LoggerFactory.getLogger(ServerLoadMonitor.class);
    private static final int REQUEST_TIMEOUT_MS = 700;

    private final ServerRegistry serverRegistry;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
            .build();

    public ServerLoadMonitor(ServerRegistry serverRegistry, ObjectMapper objectMapper) {
        this(serverRegistry, objectMapper, 4);
    }

    @Autowired
    public ServerLoadMonitor(
            ServerRegistry serverRegistry,
            ObjectMapper objectMapper,
            @Value("${balancer.load-poll.threads:4}") int threads
    ) {
        this.serverRegistry = serverRegistry;
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads), threadFactory("aqp-load-poll"));
    }

    @Scheduled(
            fixedDelayString = "${balancer.load-poll.interval-ms:1000}",
            initialDelayString = "${balancer.load-poll.initial-delay-ms:1000}"
    )
    public void pollAll() {
        List<BackendServer> servers = serverRegistry.list().stream()
                .filter(server -> server.getType() == ServerType.HTTP)
                .filter(BackendServer::isEnabled)
                .toList();
        CompletableFuture<?>[] tasks = servers.stream()
                .map(server -> CompletableFuture.runAsync(() -> poll(server), executor))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(tasks).join();
        } catch (RuntimeException exception) {
            log.debug("Load-poll cycle failed: {}", exception.getMessage());
        }
    }

    private void poll(BackendServer server) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(server.getStatsUri())
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                    .header("Accept", "application/json")
                    .GET();
            applyHeaders(builder, server.getHeaders());
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return;
            }

            RemoteStats stats = objectMapper.readValue(response.body(), RemoteStats.class);
            server.updateRemoteLoad(
                    valueOrZero(stats.active()),
                    valueOrZero(stats.queued()),
                    valueOrZero(stats.capacity()),
                    valueOrZero(stats.queueCapacity()),
                    valueOrZero(stats.rejectedRequests()),
                    stats.utilization() == null ? 0.0 : stats.utilization()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.debug("Stats poll failed for {}: {}", server.getId(), exception.getMessage());
        }
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null) {
                return;
            }
            String resolved = value.startsWith("env:")
                    ? System.getenv(value.substring(4).trim())
                    : value;
            if (resolved != null && !resolved.isBlank()) {
                builder.setHeader(name, resolved);
            }
        });
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteStats(
            Integer active,
            Integer queued,
            Integer capacity,
            Integer queueCapacity,
            Integer rejectedRequests,
            Double utilization
    ) {
    }
}
