package com.mullagaliev.aqpbalancer.service.balancer;

import com.mullagaliev.aqpbalancer.model.BackendServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prevents the cluster from being overwhelmed.
 *
 * <p>Every accepted request reserves one in-flight slot. When all active
 * servers are already at their planned concurrency (including the configured
 * overload factor), new requests are shed with an explicit decision instead of
 * being sent to a collapsing backend.</p>
 */
@Component
public class AdmissionController {

    private final double warningUtilization;
    private final double criticalUtilization;
    private final double overloadFactor;

    private final AtomicInteger inFlight = new AtomicInteger();

    public AdmissionController(
            @Value("${balancer.admission.warning-utilization:0.70}") double warningUtilization,
            @Value("${balancer.admission.critical-utilization:0.90}") double criticalUtilization,
            @Value("${balancer.admission.overload-factor:1.20}") double overloadFactor
    ) {
        this.warningUtilization = Math.max(0.1, Math.min(0.99, warningUtilization));
        this.criticalUtilization = Math.max(this.warningUtilization, Math.min(1.5, criticalUtilization));
        this.overloadFactor = Math.max(1.0, Math.min(5.0, overloadFactor));
    }

    public Decision tryAcquire(List<BackendServer> servers, ActiveServerPool activePool) {
        boolean anyRoutable = servers.stream().anyMatch(BackendServer::isRoutable);
        if (!anyRoutable) {
            return Decision.noServers();
        }

        activePool.scaleOutIfNeeded(servers, inFlight.get() + 1);
        int capacity = activePool.totalCapacity(servers);
        if (capacity <= 0) {
            return Decision.noServers();
        }

        int current = inFlight.incrementAndGet();
        double utilization = current / (double) capacity;
        int maxAllowed = (int) Math.ceil(capacity * overloadFactor);

        if (current > maxAllowed) {
            inFlight.decrementAndGet();
            return new Decision(
                    false,
                    LoadLevel.OVERLOAD,
                    utilization,
                    capacity,
                    inFlight.get(),
                    "Все серверы перегружены: " + current + " из " + maxAllowed + " слотов",
                    activePool.activeIds()
            );
        }

        return new Decision(
                true,
                levelFor(utilization),
                utilization,
                capacity,
                current,
                null,
                activePool.activeIds()
        );
    }

    public void release() {
        inFlight.updateAndGet(value -> Math.max(0, value - 1));
    }

    public int inFlight() {
        return inFlight.get();
    }

    public void reset() {
        inFlight.set(0);
    }

    public double warningUtilization() {
        return warningUtilization;
    }

    public double criticalUtilization() {
        return criticalUtilization;
    }

    public double overloadFactor() {
        return overloadFactor;
    }

    public LoadLevel levelFor(double utilization) {
        if (utilization >= criticalUtilization) {
            return LoadLevel.OVERLOAD;
        }
        if (utilization >= warningUtilization) {
            return LoadLevel.WARNING;
        }
        return LoadLevel.NORMAL;
    }

    public enum LoadLevel {
        NORMAL,
        WARNING,
        OVERLOAD
    }

    public record Decision(
            boolean accepted,
            LoadLevel loadLevel,
            double utilization,
            int capacity,
            int inFlight,
            String message,
            Set<String> activeServerIds
    ) {

        public static Decision noServers() {
            return new Decision(
                    false,
                    LoadLevel.OVERLOAD,
                    1.0,
                    0,
                    0,
                    "Нет доступных серверов",
                    Set.of()
            );
        }
    }
}
