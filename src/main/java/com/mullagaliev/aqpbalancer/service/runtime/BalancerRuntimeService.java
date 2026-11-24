package com.mullagaliev.aqpbalancer.service.runtime;

import com.mullagaliev.aqpbalancer.config.AdaptiveProperties;
import com.mullagaliev.aqpbalancer.config.AppConfig;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryResponse;
import com.mullagaliev.aqpbalancer.model.QueryType;
import com.mullagaliev.aqpbalancer.model.ServerExecutionResult;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.service.LoadBalancerCore;
import com.mullagaliev.aqpbalancer.service.balancer.ActiveServerPool;
import com.mullagaliev.aqpbalancer.service.balancer.AdmissionController;
import com.mullagaliev.aqpbalancer.service.balancer.ServerRegistry;
import com.mullagaliev.aqpbalancer.service.monitoring.BalancerMetrics;
import com.mullagaliev.aqpbalancer.web.SimulationMetrics;
import com.mullagaliev.aqpbalancer.web.dto.BalancerSnapshotDto;
import com.mullagaliev.aqpbalancer.web.dto.ClientQueryRequestDto;
import com.mullagaliev.aqpbalancer.web.dto.ClientQueryResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real client-facing balancer runtime.
 *
 * <p>Unlike the offline simulation endpoints, this service is long-lived:
 * clients send requests over HTTP, the active set adapts to load, overload is
 * shed explicitly, and all decisions are reflected in the monitoring stream.</p>
 */
@Service
public class BalancerRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(BalancerRuntimeService.class);

    private final ServerRegistry serverRegistry;
    private final ActiveServerPool activeServerPool;
    private final AdmissionController admissionController;
    private final BalancerMetrics metrics;
    private final LoadBalancerCore core;

    private final AtomicLong requestSequence = new AtomicLong();
    private final AtomicLong snapshotSequence = new AtomicLong();
    private volatile String lastLoggedLoadLevel = "NORMAL";

    public BalancerRuntimeService(
            ServerRegistry serverRegistry,
            ActiveServerPool activeServerPool,
            AdmissionController admissionController,
            BalancerMetrics metrics,
            AdaptiveProperties adaptiveProperties,
            @Value("${balancer.runtime.seed:42}") long seed
    ) {
        this.serverRegistry = serverRegistry;
        this.activeServerPool = activeServerPool;
        this.admissionController = admissionController;
        this.metrics = metrics;
        this.core = AppConfig.createCore(seed, adaptiveProperties.toConfig()).core();
    }

    public ClientQueryResponseDto dispatch(ClientQueryRequestDto incoming) {
        metrics.recordOffered();
        QueryRequest request = toQueryRequest(incoming);
        List<BackendServer> servers = serverRegistry.list();

        AdmissionController.Decision decision = admissionController.tryAcquire(servers, activeServerPool);
        if (!decision.accepted()) {
            metrics.recordShed(request, core.getLastPhase());
            String status = decision.capacity() == 0 ? "NO_SERVERS" : "SHED";
            return new ClientQueryResponseDto(
                    request.id(),
                    status,
                    null,
                    null,
                    null,
                    core.getLastPhase().name(),
                    null,
                    0.0,
                    null,
                    decision.loadLevel().name(),
                    round4(decision.utilization()),
                    activeServerPool.activeCount(servers),
                    decision.inFlight(),
                    decision.capacity(),
                    decision.message()
            );
        }

        metrics.recordAccepted();
        long startedNanos = System.nanoTime();
        BackendServer server = null;
        LoadBalancerCore.PreparedExecution plan = null;
        boolean completionRecorded = false;

        try {
            server = activeServerPool.pick(servers);
            server.onRequestDispatched();
            plan = core.prepare(request);
            ServerExecutionResult execution = server.execute(request, plan.phase(), plan.strategy());
            server.recordCompletion(execution);
            completionRecorded = true;

            QueryResponse response = core.complete(plan, server.getId(), execution);
            metrics.recordCompletion(response, request, execution.latencyMs());

            return new ClientQueryResponseDto(
                    request.id(),
                    response.success() ? "OK" : "FAILED",
                    server.getId(),
                    server.getDisplayName(),
                    response.strategyUsed().name(),
                    response.phaseAtTime().name(),
                    round4(response.approximateResult()),
                    round2(response.latencyMs()),
                    round4Nullable(response.relativeError()),
                    decision.loadLevel().name(),
                    round4(decision.utilization()),
                    activeServerPool.activeCount(servers),
                    decision.inFlight(),
                    decision.capacity(),
                    execution.error()
            );
        } catch (RuntimeException exception) {
            double latencyMs = (System.nanoTime() - startedNanos) / 1_000_000.0;
            String message = exception.getMessage() == null ? "Ошибка выполнения" : exception.getMessage();
            if (server != null && !completionRecorded) {
                server.recordCompletion(ServerExecutionResult.failure(latencyMs, 0, message));
            }
            metrics.recordFailure(
                    request,
                    server == null ? null : server.getId(),
                    plan == null ? core.getLastPhase() : plan.phase(),
                    plan == null ? null : plan.strategy(),
                    latencyMs
            );
            return new ClientQueryResponseDto(
                    request.id(),
                    server == null ? "NO_SERVERS" : "FAILED",
                    server == null ? null : server.getId(),
                    server == null ? null : server.getDisplayName(),
                    plan == null ? null : plan.strategy().name(),
                    plan == null ? core.getLastPhase().name() : plan.phase().name(),
                    null,
                    round2(latencyMs),
                    null,
                    decision.loadLevel().name(),
                    round4(decision.utilization()),
                    activeServerPool.activeCount(servers),
                    decision.inFlight(),
                    decision.capacity(),
                    message
            );
        } finally {
            admissionController.release();
        }
    }

    public BalancerSnapshotDto snapshot() {
        List<BackendServer> servers = serverRegistry.list();
        activeServerPool.refresh(servers);
        Set<String> activeIds = activeServerPool.activeIds();
        int inFlight = admissionController.inFlight();
        int capacity = activeServerPool.totalCapacity(servers);
        boolean anyRoutable = servers.stream().anyMatch(BackendServer::isRoutable);
        double utilization = capacity == 0 ? (servers.isEmpty() ? 0.0 : 1.0) : inFlight / (double) capacity;

        String loadLevel;
        String warning;
        if (servers.isEmpty()) {
            loadLevel = "OVERLOAD";
            warning = "Нет подключённых серверов";
        } else if (!anyRoutable) {
            loadLevel = "OVERLOAD";
            warning = "Все подключённые серверы недоступны";
        } else {
            loadLevel = admissionController.levelFor(utilization).name();
            warning = switch (loadLevel) {
                case "OVERLOAD" -> "Перегрузка кластера: " + inFlight + " из " + capacity + " слотов занято";
                case "WARNING" -> "Высокая загрузка кластера: " + Math.round(utilization * 100) + "%";
                default -> null;
            };
        }

        double[] window = core.getLatencyWindow().snapshot();
        double[] percentiles = SimulationMetrics.latencyPercentiles(window);
        double minLatency = 0.0;
        double maxLatency = 0.0;
        for (double value : window) {
            if (minLatency == 0.0 || value < minLatency) {
                minLatency = value;
            }
            maxLatency = Math.max(maxLatency, value);
        }

        long successCount = metrics.success();
        long failedCount = metrics.failed();

        return new BalancerSnapshotDto(
                snapshotSequence.incrementAndGet(),
                Math.max(0L, System.currentTimeMillis() - metrics.startedAtMs()),
                metrics.offered(),
                metrics.accepted(),
                successCount + failedCount,
                successCount,
                failedCount,
                metrics.shed(),
                inFlight,
                round2(metrics.averageLatencyMs()),
                round2(percentiles[0]),
                round2(percentiles[1]),
                round2(percentiles[2]),
                round2(minLatency),
                round2(maxLatency),
                window.length,
                round4(core.getLastAlpha()),
                round4(SimulationMetrics.finiteOrZero(core.getDetector().getSmoothedAlpha())),
                core.getLastPhase().name(),
                round4(core.getLastEws()),
                round4(core.getCusum()),
                loadLevel,
                round4(utilization),
                activeServerPool.activeCount(servers),
                servers.size(),
                capacity,
                metrics.strategyDistribution(),
                metrics.phaseDistribution(),
                metrics.queryTypeDistribution(),
                SimulationMetrics.serverStats(servers, Math.max(1L, metrics.accepted()), activeIds),
                metrics.recentRequests(),
                warning
        );
    }

    public void reset() {
        core.resetStatistics();
        metrics.reset();
        admissionController.reset();
        activeServerPool.reset();
        for (BackendServer server : serverRegistry.list()) {
            server.resetStatistics();
        }
        lastLoggedLoadLevel = "NORMAL";
    }

    public LoadBalancerCore core() {
        return core;
    }

    public BalancerMetrics metrics() {
        return metrics;
    }

    public AdmissionController admissionController() {
        return admissionController;
    }

    public ActiveServerPool activeServerPool() {
        return activeServerPool;
    }

    @Scheduled(fixedDelayString = "${balancer.active-set.tick-ms:3000}")
    public void tick() {
        List<BackendServer> servers = serverRegistry.list();
        activeServerPool.scaleInIfIdle(servers, admissionController.inFlight());
        logLoadTransition(servers);
    }

    private void logLoadTransition(List<BackendServer> servers) {
        int capacity = activeServerPool.totalCapacity(servers);
        int inFlight = admissionController.inFlight();
        double utilization = capacity == 0 ? 1.0 : inFlight / (double) capacity;
        String level;
        if (servers.isEmpty() || servers.stream().noneMatch(BackendServer::isRoutable) || capacity == 0) {
            level = "NO_SERVERS";
        } else {
            level = admissionController.levelFor(utilization).name();
        }

        if (!level.equals(lastLoggedLoadLevel)) {
            log.info("Load level: {} -> {} (in-flight={}, capacity={}, active={}/{})",
                    lastLoggedLoadLevel,
                    level,
                    inFlight,
                    capacity,
                    activeServerPool.activeCount(servers),
                    servers.size());
            lastLoggedLoadLevel = level;
        }
    }

    private QueryRequest toQueryRequest(ClientQueryRequestDto dto) {
        String requestId = dto.requestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = "req-" + requestSequence.incrementAndGet();
        }
        String sql = dto.sql();
        if (sql == null || sql.isBlank()) {
            sql = "SELECT avg(value) FROM analytics.events";
        }
        long estimatedRows = dto.estimatedRows() == null
                ? 1_000L
                : Math.max(1L, Math.min(10_000_000L, dto.estimatedRows()));
        QueryType type = parseType(dto.type());
        double skewness = dto.skewness() == null ? 0.5 : Math.max(0.0, Math.min(1.0, dto.skewness()));
        long arrivalTimeMs = dto.arrivalTimeMs() == null
                ? System.currentTimeMillis()
                : Math.max(0L, dto.arrivalTimeMs());

        return new QueryRequest(requestId, sql, estimatedRows, type, skewness, arrivalTimeMs, dto.clientId());
    }

    private QueryType parseType(String value) {
        if (value == null || value.isBlank()) {
            return QueryType.UNKNOWN;
        }
        try {
            return QueryType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return QueryType.UNKNOWN;
        }
    }

    private double round2(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private Double round4Nullable(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
