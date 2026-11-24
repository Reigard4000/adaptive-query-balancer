package com.mullagaliev.aqpbalancer.service.balancer;

import com.mullagaliev.aqpbalancer.exception.BalancerException;
import com.mullagaliev.aqpbalancer.model.BackendServer;

import java.util.Comparator;
import java.util.List;

/**
 * Selects a routable server by weighted accumulated load.
 *
 * <p>Dividing the accumulated request count by the configured weight makes the
 * long-run request share proportional to server capacity instead of spreading
 * requests evenly. Servers that failed the latest health check are skipped, but
 * a server that has not been checked yet remains eligible.</p>
 */
public class ServerPicker {

    public BackendServer pick(List<BackendServer> servers) {
        if (servers == null || servers.isEmpty()) {
            throw new BalancerException("No backend servers are available");
        }

        return servers.stream()
                .filter(BackendServer::isRoutable)
                .min(Comparator.comparingDouble(this::weightedLoad)
                        .thenComparingLong(BackendServer::getTotalRequests)
                        .thenComparing(BackendServer::getId))
                .orElseThrow(() -> new BalancerException("No routable backend servers are available"));
    }

    private double weightedLoad(BackendServer server) {
        int weight = Math.max(1, server.getWeight());
        return (server.getTotalRequests() + server.loadScore()) / (double) weight;
    }
}
