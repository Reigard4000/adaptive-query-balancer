package com.mullagaliev.aqpbalancer.service.aggregation;

import com.mullagaliev.aqpbalancer.model.QueryType;
import com.mullagaliev.aqpbalancer.model.SampleResult;

import java.util.Arrays;

/**
 * Turns sampled values into a single approximate query result.
 */
public class Aggregator {

    public double aggregate(SampleResult result, QueryType type) {
        if (result == null || result.sampledValues() == null || result.sampledValues().length == 0) {
            return 0.0;
        }
        double[] values = result.sampledValues();
        if (type == QueryType.TOP_K) {
            return Arrays.stream(values).max().orElse(0.0);
        }
        return Arrays.stream(values).average().orElse(0.0);
    }
}
