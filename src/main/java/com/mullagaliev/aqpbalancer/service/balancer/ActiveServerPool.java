package com.mullagaliev.aqpbalancer.service.balancer;

import com.mullagaliev.aqpbalancer.exception.BalancerException;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps only the number of servers that is currently needed in rotation.
 *
 * <p>At low load one server is enough. When its utilization approaches the
 * scale-out threshold, another healthy node is activated. When utilization
 * stays low for a cooldown period, surplus nodes are returned to standby.</p>
 */
@Component
public class ActiveServerPool {

    private static final Logger log = LoggerFactory.getLogger(ActiveServerPool.class);

    private final int minActiveServers;
    private final int maxActiveServers;
    private final double scaleOutUtilization;
    private final double scaleInUtilization;
    private final long scaleInCooldownMs;

    private final Set<String> activeIds = new LinkedHashSet<>();
    private long lowUtilizationSinceMs = 0L;

    public ActiveServerPool(int minActiveServers, double scaleOutUtilization,
                            double scaleInUtilization, long scaleInCooldownMs) {
        this(minActiveServers, 100, scaleOutUtilization, scaleInUtilization, scaleInCooldownMs);
    }

    @Autowired
    public ActiveServerPool(
            @Value("${balancer.active-set.min-size:1}") int minActiveServers,
            @Value("${balancer.active-set.max-size:100}") int maxActiveServers,
            @Value("${balancer.active-set.scale-out-utilization:0.70}") double scaleOutUtilization,
            @Value("${balancer.active-set.scale-in-utilization:0.25}") double scaleInUtilization,
            @Value("${balancer.active-set.scale-in-cooldown-ms:15000}") long scaleInCooldownMs
    ) {
        this.maxActiveServers = Math.max(1, maxActiveServers);
        this.minActiveServers = Math.max(0, Math.min(this.maxActiveServers, minActiveServers));
        this.scaleOutUtilization = Math.max(0.1, Math.min(0.99, scaleOutUtilization));
        this.scaleInUtilization = Math.max(0.01, Math.min(this.scaleOutUtilization - 0.01, scaleInUtilization));
        this.scaleInCooldownMs = Math.max(1_000L, scaleInCooldownMs);
    }

    public synchronized void reset() {
        activeIds.clear();
        lowUtilizationSinceMs = 0L;
    }

    public synchronized Set<String> activeIds() {
        return Set.copyOf(activeIds);
    }

    public synchronized void refresh(List<BackendServer> allServers) {
        sync(allServers);
    }

    public synchronized boolean isActive(String serverId) {
        return activeIds.contains(serverId);
    }

    public synchronized List<BackendServer> activeServers(List<BackendServer> allServers) {
        List<BackendServer> result = new ArrayList<>();
        for (BackendServer server : allServers) {
            if (activeIds.contains(server.getId()) && server.isRoutable()) {
                result.add(server);
            }
        }
        return result;
    }

    public synchronized int activeCount(List<BackendServer> allServers) {
        return activeServers(allServers).size();
    }

    public synchronized int routableCount(List<BackendServer> allServers) {
        return (int) allServers.stream().filter(BackendServer::isRoutable).count();
    }

    public synchronized int totalCapacity(List<BackendServer> allServers) {
        int capacity = 0;
        for (BackendServer server : activeServers(allServers)) {
            capacity += server.getEffectiveCapacity();
        }
        return capacity;
    }

    /**
     * Activates enough nodes to satisfy the minimum and, when utilization is
     * already high, adds one more node with the most free capacity.
     */
    public synchronized void scaleOutIfNeeded(List<BackendServer> allServers, int inFlight) {
        sync(allServers);
        if (activeIds.isEmpty()) {
            addBestStandby(allServers);
            return;
        }
        if (activeIds.size() >= maxActiveServers) {
            return;
        }

        int capacity = totalCapacity(allServers);
        double aggregateUtilization = capacity == 0
                ? 1.0
                : Math.max(0.0, inFlight) / (double) capacity;
        double hottestServer = activeServers(allServers).stream()
                .mapToDouble(BackendServer::projectedUtilization)
                .max()
                .orElse(0.0);

        if (aggregateUtilization > scaleOutUtilization || hottestServer > scaleOutUtilization) {
            addBestStandby(allServers);
        }
    }

    /**
     * Scales in only when utilization has been low for the whole cooldown
     * period and the least valuable active node has no traffic.
     */
    public synchronized void scaleInIfIdle(List<BackendServer> allServers, int inFlight) {
        sync(allServers);
        int activeCount = activeServers(allServers).size();
        if (activeCount <= minActiveServers) {
            lowUtilizationSinceMs = 0L;
            return;
        }

        int capacity = totalCapacity(allServers);
        double utilization = capacity == 0 ? 1.0 : Math.max(0.0, inFlight) / (double) capacity;
        long now = System.currentTimeMillis();

        if (utilization >= scaleInUtilization) {
            lowUtilizationSinceMs = 0L;
            return;
        }

        if (lowUtilizationSinceMs == 0L) {
            lowUtilizationSinceMs = now;
            return;
        }

        if (now - lowUtilizationSinceMs < scaleInCooldownMs) {
            return;
        }

        Optional<BackendServer> removable = activeServers(allServers).stream()
                .filter(server -> server.getActiveLoad() == 0 && server.getQueuedLoad() == 0)
                .min(Comparator.comparingInt(BackendServer::getEffectiveCapacity)
                        .thenComparing(BackendServer::getId));
        if (removable.isEmpty()) {
            return;
        }

        BackendServer candidate = removable.get();
        int remainingCapacity = capacity - candidate.getEffectiveCapacity();
        double projectedUtilization = remainingCapacity <= 0
                ? 1.0
                : Math.max(0.0, inFlight) / (double) remainingCapacity;
        if (projectedUtilization >= scaleOutUtilization) {
            return;
        }

        activeIds.remove(candidate.getId());
        lowUtilizationSinceMs = 0L;
        log.info("Active set: - {} (standby, active={}, utilization={})",
                candidate.getDisplayName(), activeServers(allServers).size(), utilization);
    }

    public synchronized BackendServer pick(List<BackendServer> allServers) {
        sync(allServers);
        if (activeIds.isEmpty()) {
            addBestStandby(allServers);
        }

        return activeServers(allServers).stream()
                .min(Comparator
                        .comparingDouble(BackendServer::projectedUtilization)
                        .thenComparing(Comparator.comparingInt(BackendServer::getEffectiveCapacity).reversed())
                        .thenComparing(Comparator.comparingInt(BackendServer::getWeight).reversed())
                        .thenComparing(BackendServer::getId))
                .orElseThrow(() -> new BalancerException("No routable backend servers are available"));
    }

    private void sync(List<BackendServer> allServers) {
        Set<String> routableIds = new LinkedHashSet<>();
        for (BackendServer server : allServers) {
            if (server.isRoutable()) {
                routableIds.add(server.getId());
            }
        }
        activeIds.removeIf(id -> !routableIds.contains(id));

        int targetMinimum = Math.min(Math.min(minActiveServers, routableIds.size()), maxActiveServers);
        while (activeIds.size() < targetMinimum) {
            if (addBestStandby(allServers) == null) {
                break;
            }
        }
    }

    private BackendServer addBestStandby(List<BackendServer> allServers) {
        if (activeIds.size() >= maxActiveServers) {
            return null;
        }
        Optional<BackendServer> standby = allServers.stream()
                .filter(BackendServer::isRoutable)
                .filter(server -> !activeIds.contains(server.getId()))
                .min(Comparator
                        .comparingDouble(BackendServer::projectedUtilization)
                        .thenComparing(Comparator.comparingInt(BackendServer::getEffectiveCapacity).reversed())
                        .thenComparing(Comparator.comparingInt(BackendServer::getWeight).reversed())
                        .thenComparing(BackendServer::getId));
        if (standby.isEmpty()) {
            return null;
        }

        BackendServer server = standby.get();
        activeIds.add(server.getId());
        log.info("Active set: + {} (capacity={}, active={})",
                server.getDisplayName(), server.getEffectiveCapacity(), activeIds.size());
        return server;
    }
}
