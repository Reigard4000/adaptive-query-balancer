package com.mullagaliev.aqpbalancer.service.health;

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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodically probes connected HTTP backends.
 *
 * <p>Checks run in a small bounded pool so one slow node cannot delay the rest
 * of the cluster. The result is used by {@code ActiveServerPool}: a node that
 * stops responding is automatically excluded from routing and returns to
 * rotation as soon as its health endpoint recovers.</p>
 */
@Service
public class ServerHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ServerHealthMonitor.class);

    private final ServerRegistry serverRegistry;
    private final int timeoutMs;
    private final ExecutorService executor;

    public ServerHealthMonitor(ServerRegistry serverRegistry) {
        this(serverRegistry, 2_000, 4);
    }

    @Autowired
    public ServerHealthMonitor(
            ServerRegistry serverRegistry,
            @Value("${balancer.health-check.timeout-ms:2000}") int timeoutMs,
            @Value("${balancer.health-check.threads:4}") int threads
    ) {
        this.serverRegistry = serverRegistry;
        this.timeoutMs = Math.max(100, timeoutMs);
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads), threadFactory("aqp-health"));
    }

    @Scheduled(
            fixedDelayString = "${balancer.health-check.interval-ms:15000}",
            initialDelayString = "${balancer.health-check.initial-delay-ms:3000}"
    )
    public void checkAll() {
        List<BackendServer> servers = serverRegistry.list().stream()
                .filter(server -> server.getType() == ServerType.HTTP)
                .filter(BackendServer::isEnabled)
                .toList();
        CompletableFuture<?>[] tasks = servers.stream()
                .map(server -> CompletableFuture.runAsync(() -> check(server), executor))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(tasks).join();
        } catch (RuntimeException exception) {
            log.warn("Health-check cycle failed: {}", exception.getMessage());
        }
    }

    private void check(BackendServer server) {
        boolean before = server.isHealthy();
        try {
            server.checkHealth(timeoutMs);
        } catch (RuntimeException exception) {
            log.warn("Health check failed for {}: {}", server.getId(), exception.getMessage());
        }
        if (before != server.isHealthy()) {
            log.info("Server {} ({}): availability changed to {}",
                    server.getId(), server.getUrl(), server.isHealthy() ? "UP" : "DOWN");
        }
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
}
