package com.mullagaliev.aqpbalancer.controller;

import com.mullagaliev.aqpbalancer.config.AppConfig;
import com.mullagaliev.aqpbalancer.config.BalancerConfig;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryResponse;
import com.mullagaliev.aqpbalancer.model.QueryType;
import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.web.SimulationMetrics;
import com.mullagaliev.aqpbalancer.web.dto.RecentRequestDto;
import com.mullagaliev.aqpbalancer.web.dto.SimulationRequestDto;
import com.mullagaliev.aqpbalancer.web.dto.SimulationResultDto;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the simulation and produces both a console report and a web DTO.
 */
public class SimulationController {

    private static final int RECENT_REQUESTS_LIMIT = 12;

    public void run() {
        System.out.println("============================================================");
        System.out.println(" ADAPTIVE QUERY BALANCER / AQP BALANCER");
        System.out.println(" Детектирование фазовых переходов и адаптивная выборка");
        System.out.println("============================================================");

        runScenario("LIGHT_TAIL (alpha=2.5)",
                new BalancerConfig(1_000, 42L, 2.5, 3), 2.5, 2.5, true,
                createDefaultServers(3, 2.5, 42L));
        runScenario("HEAVY_TAIL (alpha=1.8)",
                new BalancerConfig(1_000, 40L, 1.8, 3), 1.8, 1.8, true,
                createDefaultServers(3, 1.8, 40L));
        runScenario("TRANSITION (2.5 -> 1.8)",
                new BalancerConfig(1_500, 44L, 2.5, 3), 2.5, 1.8, true,
                createDefaultServers(3, 2.5, 44L));

        System.out.println();
        System.out.println("============================================================");
        System.out.println(" Симуляция завершена. Для веб-интерфейса: mvn spring-boot:run");
        System.out.println("============================================================");
    }

    public SimulationResultDto runForWeb(SimulationRequestDto request, List<BackendServer> servers) {
        SimulationRequestDto safe = request == null
                ? new SimulationRequestDto(1_000, 42L, 2.5)
                : request;
        BalancerConfig config = new BalancerConfig(
                safe.totalQueries(),
                safe.seed(),
                safe.alpha(),
                servers.size()
        );
        return runScenario("WEB", config, config.alpha(), config.alpha(), false, servers);
    }

    private SimulationResultDto runScenario(
            String name,
            BalancerConfig config,
            double startAlpha,
            double endAlpha,
            boolean printReport,
            List<BackendServer> servers
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

        int processed = 0;
        double totalLatency = 0.0;
        double minLatency = Double.POSITIVE_INFINITY;
        double maxLatency = 0.0;
        double[] latencies = new double[config.totalQueries()];
        Deque<RecentRequestDto> recentRequests = new ArrayDeque<>();
        long startedNanos = System.nanoTime();
        double appliedAlpha = Double.NaN;

        for (int index = 0; index < config.totalQueries(); index++) {
            double trueAlpha = alphaAt(index, config.totalQueries(), startAlpha, endAlpha);
            if (Double.compare(appliedAlpha, trueAlpha) != 0) {
                applySimulatedAlpha(servers, trueAlpha);
                appliedAlpha = trueAlpha;
            }

            QueryRequest request = components.generator().next();
            QueryResponse response = components.balancer().dispatch(request);

            strategyCounts.merge(response.strategyUsed(), 1L, Long::sum);
            phaseCounts.merge(response.phaseAtTime(), 1L, Long::sum);
            queryTypeCounts.merge(request.type(), 1L, Long::sum);
            latencies[processed] = response.latencyMs();
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
            processed++;
        }

        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        double elapsedSeconds = Math.max(0.001, elapsedMs / 1000.0);
        double averageLatency = processed == 0 ? 0.0 : totalLatency / processed;
        double[] percentiles = SimulationMetrics.latencyPercentiles(latencies);

        SimulationResultDto result = new SimulationResultDto(
                processed,
                SimulationMetrics.round2(averageLatency),
                SimulationMetrics.round2(percentiles[0]),
                SimulationMetrics.round2(percentiles[1]),
                SimulationMetrics.round2(percentiles[2]),
                SimulationMetrics.round2(processed == 0 ? 0.0 : minLatency),
                SimulationMetrics.round2(maxLatency),
                components.latencyWindow().size(),
                SimulationMetrics.round2(components.core().getLastAlpha()),
                SimulationMetrics.round2(SimulationMetrics.finiteOrZero(components.detector().getSmoothedAlpha())),
                SimulationMetrics.round2(endAlpha),
                SimulationMetrics.round2(components.core().getLastEws()),
                SimulationMetrics.round2(components.core().getCusum()),
                components.core().getLastPhase().name(),
                elapsedMs,
                SimulationMetrics.round2(processed / elapsedSeconds),
                SimulationMetrics.strategyDistribution(strategyCounts),
                SimulationMetrics.phaseDistribution(phaseCounts),
                SimulationMetrics.queryTypeDistribution(queryTypeCounts),
                SimulationMetrics.serverStats(components.balancer().getServers(), processed),
                new ArrayList<>(recentRequests)
        );

        if (printReport) {
            printReport(name, config, startAlpha, endAlpha, components, result);
        }
        return result;
    }

    private void applySimulatedAlpha(List<BackendServer> servers, double alpha) {
        for (BackendServer server : servers) {
            if (server.getType() == ServerType.SIMULATED) {
                server.setSimulatedAlpha(alpha);
            }
        }
    }

    private List<BackendServer> createDefaultServers(int count, double alpha, long seed) {
        List<BackendServer> servers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String id = "server-" + (index + 1);
            ServerSettings settings = ServerSettings.simulated(
                    "Sim-" + (index + 1),
                    ServerSettings.DEFAULT_WEIGHT,
                    ServerSettings.DEFAULT_BASE_LATENCY_MS,
                    ServerSettings.DEFAULT_FAILURE_RATE
            );
            BackendServer server = new BackendServer(id, settings);
            server.resetForRun(seed + index, alpha);
            servers.add(server);
        }
        return servers;
    }

    private double alphaAt(int index, int total, double startAlpha, double endAlpha) {
        if (Double.compare(startAlpha, endAlpha) == 0) {
            return startAlpha;
        }
        double progress = total <= 1 ? 1.0 : index / (double) (total - 1);
        return startAlpha + (endAlpha - startAlpha) * progress;
    }

    private void printReport(
            String name,
            BalancerConfig config,
            double startAlpha,
            double endAlpha,
            AppConfig.SimulationComponents components,
            SimulationResultDto result
    ) {
        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println(" Сценарий: " + name);
        System.out.println("------------------------------------------------------------");
        System.out.printf(Locale.ROOT,
                " Запросов: %d | seed: %d | серверов: %d | alpha: %.2f -> %.2f%n",
                config.totalQueries(), config.seed(), components.balancer().getServers().size(),
                startAlpha, endAlpha);
        System.out.printf(Locale.ROOT, " Обработано запросов: %d%n", result.processedCount());
        System.out.printf(Locale.ROOT,
                " Задержка: avg=%.2f ms | p50=%.2f | p95=%.2f | p99=%.2f | min=%.2f | max=%.2f%n",
                result.averageLatency(), result.latencyP50(), result.latencyP95(),
                result.latencyP99(), result.minLatency(), result.maxLatency());
        System.out.printf(Locale.ROOT, " Время: %d ms | пропускная способность: %.2f зап/с%n",
                result.elapsedMs(), result.throughput());
        System.out.printf(Locale.ROOT,
                " Оценённый alpha: %.3f | сглаженный: %.3f | EWS: %.3f | CUSUM: %.3f | фаза: %s%n",
                result.estimatedAlpha(),
                result.smoothedAlpha(),
                result.ews(),
                result.cusum(),
                result.phase());

        System.out.println(" Распределение стратегий:");
        result.strategyDistribution().forEach((strategy, count) ->
                System.out.printf(Locale.ROOT, "   %-11s %6d  %5.1f%%%n",
                        strategy, count, percent(count, result.processedCount())));

        System.out.println(" Распределение фаз:");
        result.phaseDistribution().forEach((phase, count) ->
                System.out.printf(Locale.ROOT, "   %-11s %6d  %5.1f%%%n",
                        phase, count, percent(count, result.processedCount())));

        System.out.println(" Типы запросов:");
        result.queryTypeDistribution().forEach((type, count) ->
                System.out.printf(Locale.ROOT, "   %-11s %6d  %5.1f%%%n",
                        type, count, percent(count, result.processedCount())));

        System.out.println(" Загрузка серверов:");
        components.balancer().getServers().forEach(server ->
                System.out.printf(Locale.ROOT,
                        "   %-9s %-9s weight=%-3d requests=%d ok=%d fail=%d avgLatency=%.2f ms%n",
                        server.getId(),
                        server.getType(),
                        server.getWeight(),
                        server.getTotalRequests(),
                        server.getSuccessCount(),
                        server.getFailureCount(),
                        server.getAverageLatencyMs()));
    }

    private double percent(long count, int total) {
        return total == 0 ? 0.0 : count * 100.0 / total;
    }
}
