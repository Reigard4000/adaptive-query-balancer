package com.mullagaliev.aqpbalancer.web;

import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.service.balancer.ServerRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Readiness indicator: the balancer is ready only when at least one enabled
 * backend passed its latest health check.
 */
@Component("balancer")
public class BalancerHealthIndicator implements HealthIndicator {

    private final ServerRegistry serverRegistry;

    public BalancerHealthIndicator(ServerRegistry serverRegistry) {
        this.serverRegistry = serverRegistry;
    }

    @Override
    public Health health() {
        List<BackendServer> servers = serverRegistry.list();
        long routable = servers.stream().filter(BackendServer::isRoutable).count();
        if (routable == 0) {
            return Health.outOfService()
                    .withDetail("totalServers", servers.size())
                    .withDetail("routableServers", 0)
                    .withDetail("reason", "No routable backend servers")
                    .build();
        }
        return Health.up()
                .withDetail("totalServers", servers.size())
                .withDetail("routableServers", routable)
                .build();
    }
}
