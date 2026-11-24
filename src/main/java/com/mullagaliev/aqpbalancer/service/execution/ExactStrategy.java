package com.mullagaliev.aqpbalancer.service.execution;

import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.SampleResult;
import com.mullagaliev.aqpbalancer.repository.QueryRepository;

import java.util.List;

/**
 * Full scan: every available row participates in the result.
 */
public class ExactStrategy implements SamplingStrategy {

    private final QueryRepository repository;

    public ExactStrategy(QueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public SampleResult execute(QueryRequest request, QueryContext context) {
        List<Double> values = repository.fetchAll();
        double[] sample = values.stream().mapToDouble(Double::doubleValue).toArray();
        long totalRows = Math.max(request.estimatedRows(), sample.length);
        return new SampleResult(sample, sample.length, totalRows);
    }
}
