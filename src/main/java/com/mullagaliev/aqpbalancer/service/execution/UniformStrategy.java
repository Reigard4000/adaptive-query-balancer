package com.mullagaliev.aqpbalancer.service.execution;

import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.SampleResult;
import com.mullagaliev.aqpbalancer.repository.QueryRepository;

import java.util.List;

/**
 * Simple random sample, capped at {@value #MAX_SAMPLE_SIZE} rows.
 */
public class UniformStrategy implements SamplingStrategy {

    public static final int MAX_SAMPLE_SIZE = 10_000;

    private final QueryRepository repository;

    public UniformStrategy(QueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public SampleResult execute(QueryRequest request, QueryContext context) {
        int requested = sampleSize(request);
        List<Double> values = repository.fetchSample(requested);
        double[] sample = values.stream().mapToDouble(Double::doubleValue).toArray();
        long totalRows = Math.max(request.estimatedRows(), sample.length);
        return new SampleResult(sample, sample.length, totalRows);
    }

    static int sampleSize(QueryRequest request) {
        long rows = Math.max(1L, request.estimatedRows());
        return (int) Math.min(MAX_SAMPLE_SIZE, rows);
    }
}
