package com.mullagaliev.aqpbalancer.web;

import com.mullagaliev.aqpbalancer.service.balancer.ActiveServerPool;
import com.mullagaliev.aqpbalancer.service.balancer.AdmissionController;
import com.mullagaliev.aqpbalancer.service.balancer.ServerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Binds live balancer state to Micrometer gauges.
 */
@Component
public class BalancerMetricsBinder {

    public BalancerMetricsBinder(
            MeterRegistry meterRegistry,
            ServerRegistry serverRegistry,
            ActiveServerPool activeServerPool,
            AdmissionController admissionController
    ) {
        Gauge.builder("balancer.inflight", admissionController, AdmissionController::inFlight)
                .description("Requests currently holding an admission slot")
                .register(meterRegistry);
        Gauge.builder("balancer.servers.active", activeServerPool,
                        pool -> pool.activeCount(serverRegistry.list()))
                .description("Servers in the active set")
                .register(meterRegistry);
        Gauge.builder("balancer.servers.routable", serverRegistry,
                        registry -> registry.list().stream().filter(server -> server.isRoutable()).count())
                .description("Servers that are enabled and healthy")
                .register(meterRegistry);
    }
}
