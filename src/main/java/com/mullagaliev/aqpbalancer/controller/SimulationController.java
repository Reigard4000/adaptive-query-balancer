import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryResponse;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.service.balancer.AdaptiveLoadBalancer;
import com.mullagaliev.aqpbalancer.service.generation.QueryGenerator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Оркестратор симуляции.
 * Создаёт компоненты, прогоняет запросы через балансировщик, собирает статистику и выводит отчёт.
 */
public class SimulationController {
    
    // Параметры симуляции
    private final long totalQueries;
    private final long seed;
    private final double alpha;
    private final int serverCount;

    // Компоненты
    private main.java.com.mullagaliev.aqpbalancer.service.generation.QueryGenerator generator;
    private AdaptiveLoadBalancer balancer; // TODO: пока не существует
    private List<BackendServer> servers;

    // Статистика
    private long processedCount;
    private double totalLatency;
    private Map<StrategyType, Integer> strategyCounts;
    private Map<Phase, Integer> phaseCounts;

    public SimulationController(long totalQueries, long seed, double alpha, int serverCount) {
        this.totalQueries = totalQueries;
        this.seed = seed;
        this.alpha = alpha;
        this.serverCount = serverCount;
    }

    /** Точка входа */
    public void run() {
        setup();
        simulate();
        report();
    }

    // Создание компонентов
    private void setup() {
        generator = new QueryGenerator(totalQueries, seed);

        servers = new ArrayList<>();
        for (int i = 1; i <= serverCount; i++) {
            servers.add(new BackendServer("src-" + i, 0));
        }

        // TODO: LatencySimulator ещё нет
        // LatencySimulator simulator = new LatencySimulator(alpha);
        // balancer = new AdaptiveLoadBalancer(servers, simulator);

        strategyCounts = new EnumMap<>(StrategyType.class);
        phaseCounts = new EnumMap<>(Phase.class);
        processedCount = 0;
        totalLatency = 0.0;
    }

    // Цикл по запросам
    private void simulate() {
        while (generator.hasNext()) {
            QueryRequest request = generator.next();

            // TODO: когда AdaptiveLoadBalancer появится, раскомментировать:
            // QueryResponse response = balancer.dispatch(request);
            // record(response);

            // Заглушка, пока балансировщика нет — чтобы код компилировался
            QueryResponse response = stubResponse(request);
            record(response);
        }
    }

    // Запись метрики
    private void record(QueryResponse response) {
        processedCount++;
        totalLatency += response.latencyMs();

        strategyCounts.merge(response.strategyUsed(), 1, Integer::sum);
        phaseCounts.merge(response.phaseAtTime(), 1, Integer::sum);
    }

    // Отчёт
    private void report() {
        System.out.println("=== Отчёт об имитации ===");
        System.out.println("Обработанные запросы: " + processedCount);
        System.out.printf("Средняя задержка:   %.2f ms%n", averageLatency());
        System.out.println("Стратегия распределения:");
        strategyCounts.forEach((s, n) ->
                System.out.printf("  %-12s %d%n", s, n));
        System.out.println("Фаза распределения:");
        phaseCounts.forEach((p, n) ->
                System.out.printf("  %-12s %d%n", p, n));
    }
    private double averageLatency() {
        return processedCount == 0 ? 0.0 : totalLatency / processedCount;
    }

    // Заглушка
    private QueryResponse stubResponse(QueryRequest request) {
        // Заглушка: возвращаем фиктивный ответ, чтобы SimulationController компилировался
        return new QueryResponse(
                request.id(),
                0.0,
                0.0,
                StrategyType.EXACT,
                Phase.UNKNOWN
        );
    }
}
