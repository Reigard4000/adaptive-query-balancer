package com.mullagaliev.aqpbalancer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mullagaliev.aqpbalancer.config.AppConfig;
import com.mullagaliev.aqpbalancer.config.BalancerConfig;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryResponse;
import com.mullagaliev.aqpbalancer.model.QueryType;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.service.balancer.ServerRegistry;
import com.mullagaliev.aqpbalancer.web.dto.RecentRequestDto;
import com.mullagaliev.aqpbalancer.web.dto.StreamSnapshotDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs an endless request stream for a single SSE client and periodically
 * publishes a full monitoring snapshot.
 */
@Service
@ConditionalOnProperty(name = "balancer.demo.enabled", havingValue = "true")
public class StreamingSimulationService {

    private static final int MAX_BATCH_SIZE = 200;
    private static final int MIN_INTERVAL_MS = 50;
    private static final int MAX_INTERVAL_MS = 5_000;
    private static final int RECENT_REQUESTS_LIMIT = 12;

    private final ObjectMapper objectMapper;
    private final ServerRegistry serverRegistry;

    public StreamingSimulationService(ObjectMapper objectMapper, ServerRegistry serverRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.serverRegistry = Objects.requireNonNull(serverRegistry, "serverRegistry");
    }

    public SseEmitter open(
            long seed,
            double alpha,
            int batchSize,
            int intervalMs
    ) {
        BalancerConfig config = new BalancerConfig(1, seed, alpha, 1);
        int safeBatchSize = clamp(batchSize, 1, MAX_BATCH_SIZE);
        int safeIntervalMs = clamp(intervalMs, MIN_INTERVAL_MS, MAX_INTERVAL_MS);
        List<BackendServer> servers = serverRegistry.beginRun(config.seed(), config.alpha());

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean running = new AtomicBoolean(true);
        emitter.onCompletion(() -> running.set(false));
        emitter.onTimeout(() -> {
            running.set(false);
            emitter.complete();
        });
        emitter.onError(exception -> running.set(false));

        Thread worker = new Thread(
                () -> runStream(emitter, running, config, servers, safeBatchSize, safeIntervalMs),
                "aqp-stream-" + seed
        );
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    private void runStream(
            SseEmitter emitter,
            AtomicBoolean running,
            BalancerConfig config,
            List<BackendServer> servers,
            int batchSize,
            int intervalMs
    ) {
        AppConfig.SimulationComponents components = AppConfig.create(config, servers);

        Map<StrategyType, Long> strategyCounts = new EnumMap<>(StrategyType.class);
        Map<Phase, Long> phaseCounts = new EnumMap<>(Phase.class);
        Map<QueryType, Long> queryTypeCounts = new EnumMap<>(QueryType.class);
        for (StrategyType strategy : StrategyType.values()) {
            strategyCounts.put(strategy, 0L);
        }
        for (Phase phase : Phase.values()) {
            phaseCounts.put(phase, 0L);
        }
        for (QueryType type : QueryType.values()) {
            queryTypeCounts.put(type, 0L);
        }

        Deque<RecentRequestDto> recentRequests = new ArrayDeque<>();
        long processed = 0L;
        long snapshotNumber = 0L;
        long lastEmitNanos = System.nanoTime();
        long processedAtLastEmit = 0L;
        long startedNanos = lastEmitNanos;
        double totalLatency = 0.0;
        double minLatency = Double.POSITIVE_INFINITY;
        double maxLatency = 0.0;

        try {
            while (running.get()) {
                long loopStartNanos = System.nanoTime();

                for (int index = 0; index < batchSize && running.get(); index++) {
                    QueryRequest request = components.generator().next();
                    QueryResponse response = components.balancer().dispatch(request);

                    strategyCounts.merge(response.strategyUsed(), 1L, Long::sum);
                    phaseCounts.merge(response.phaseAtTime(), 1L, Long::sum);
                    queryTypeCounts.merge(request.type(), 1L, Long::sum);

                    processed++;
                    totalLatency += response.latencyMs();
                    minLatency = Math.min(minLatency, response.latencyMs());
                    maxLatency = Math.max(maxLatency, response.latencyMs());

                    recentRequests.addFirst(new RecentRequestDto(
                            request.id(),
                            response.serverId(),
                            request.type().name(),
                            request.estimatedRows(),
                            SimulationMetrics.round2(response.latencyMs()),
                            response.strategyUsed().name(),
                            response.phaseAtTime().name(),
                            response.success(),
                            SimulationMetrics.round2(response.approximateResult()),
                            response.relativeError() == null ? null : SimulationMetrics.round2(response.relativeError()),
                            response.success() ? "OK" : "FAILED"
                    ));
                    while (recentRequests.size() > RECENT_REQUESTS_LIMIT) {
                        recentRequests.removeLast();
                    }
                }

                long nowNanos = System.nanoTime();
                long elapsedMs = (nowNanos - startedNanos) / 1_000_000L;
                long intervalRequests = processed - processedAtLastEmit;
                double intervalSeconds = Math.max(0.001, (nowNanos - lastEmitNanos) / 1_000_000_000.0);
                double throughput = intervalRequests / intervalSeconds;

                double[] window = components.latencyWindow().snapshot();
                double[] percentiles = SimulationMetrics.latencyPercentiles(window);
                double averageLatency = processed == 0 ? 0.0 : totalLatency / processed;

                StreamSnapshotDto snapshot = new StreamSnapshotDto(
                        ++snapshotNumber,
                        elapsedMs,
                        processed,
                        SimulationMetrics.round2(throughput),
                        SimulationMetrics.round2(averageLatency),
                        SimulationMetrics.round2(percentiles[0]),
                        SimulationMetrics.round2(percentiles[1]),
                        SimulationMetrics.round2(percentiles[2]),
                        SimulationMetrics.round2(processed == 0 ? 0.0 : minLatency),
                        SimulationMetrics.round2(maxLatency),
                        window.length,
                        SimulationMetrics.round2(config.alpha()),
                        SimulationMetrics.round2(components.core().getLastAlpha()),
                        SimulationMetrics.round2(SimulationMetrics.finiteOrZero(
                                components.detector().getSmoothedAlpha())),
                        components.core().getLastPhase().name(),
                        SimulationMetrics.round2(components.core().getLastEws()),
                        SimulationMetrics.round2(components.core().getCusum()),
                        SimulationMetrics.strategyDistribution(strategyCounts),
                        SimulationMetrics.phaseDistribution(phaseCounts),
                        SimulationMetrics.queryTypeDistribution(queryTypeCounts),
                        SimulationMetrics.serverStats(components.balancer().getServers(), processed),
                        new ArrayList<>(recentRequests)
                );

                emitter.send(SseEmitter.event()
                        .name("snapshot")
                        .data(objectMapper.writeValueAsString(snapshot)));

                lastEmitNanos = nowNanos;
                processedAtLastEmit = processed;

                long loopElapsedMs = (System.nanoTime() - loopStartNanos) / 1_000_000L;
                long sleepMs = intervalMs - loopElapsedMs;
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalStateException exception) {
            running.set(false);
        } catch (RuntimeException exception) {
            running.set(false);
        } finally {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // The client may have disconnected before the stream reached this point.
            }
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
