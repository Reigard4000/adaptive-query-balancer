package com.mullagaliev.aqpbalancer.service.execution;

import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.SampleResult;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.repository.QueryRepository;
import com.mullagaliev.aqpbalancer.service.aggregation.Aggregator;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Executes a sampling strategy in-process.
 *
 * <p>Used only by built-in simulated servers (console demonstration mode and
 * backward compatibility). Real HTTP backends execute queries themselves and
 * return the result in the response body.</p>
 */
public class LocalQueryExecutor {

    private final Map<StrategyType, SamplingStrategy> strategies;
    private final Aggregator aggregator;

    public LocalQueryExecutor(QueryRepository repository) {
        Objects.requireNonNull(repository, "repository");
        this.aggregator = new Aggregator();
        this.strategies = new EnumMap<>(StrategyType.class);
        this.strategies.put(StrategyType.EXACT, new ExactStrategy(repository));
        this.strategies.put(StrategyType.UNIFORM, new UniformStrategy(repository));
        this.strategies.put(StrategyType.STRATIFIED, new StratifiedStrategy(repository));
    }

    public synchronized Outcome execute(QueryRequest request, StrategyType strategy) {
        SamplingStrategy executor = strategies.get(strategy);
        if (executor == null) {
            throw new IllegalStateException("Strategy " + strategy + " is not configured");
        }
        QueryContext context = new QueryContext(
                "local",
                request.type(),
                request.skewness(),
                0,
                0.0
        );
        SampleResult sample = executor.execute(request, context);
        double result = aggregator.aggregate(sample, request.type());
        return new Outcome(
                result,
                sample.sampleSize(),
                sample.totalRows(),
                strategy != StrategyType.EXACT
        );
    }

    public synchronized double exactResult(QueryRequest request) {
        return execute(request, StrategyType.EXACT).result();
    }

    public record Outcome(
            double result,
            int sampleSize,
            long totalRows,
            boolean approximate
    ) {
    }
}
