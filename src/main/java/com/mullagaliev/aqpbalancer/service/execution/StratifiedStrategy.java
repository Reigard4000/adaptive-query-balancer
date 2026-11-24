package com.mullagaliev.aqpbalancer.service.execution;

import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.SampleResult;
import com.mullagaliev.aqpbalancer.repository.QueryRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Proportional stratified sample across the strata exposed by the repository.
 */
public class StratifiedStrategy implements SamplingStrategy {

    private final QueryRepository repository;

    public StratifiedStrategy(QueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public SampleResult execute(QueryRequest request, QueryContext context) {
        int requested = UniformStrategy.sampleSize(request);
        Map<String, List<Double>> groups = repository.fetchStratified(requested);

        List<Double> values = new ArrayList<>(requested);
        for (List<Double> group : groups.values()) {
            values.addAll(group);
        }
        if (values.isEmpty()) {
            values = repository.fetchSample(requested);
        }

        double[] sample = values.stream().mapToDouble(Double::doubleValue).toArray();
        long totalRows = Math.max(request.estimatedRows(), sample.length);
        return new SampleResult(sample, sample.length, totalRows);
    }
}
