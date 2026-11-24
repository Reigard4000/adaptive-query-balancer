package com.mullagaliev.aqpbalancer.model;

public record SampleResult(
        double[] sampledValues,
        int sampleSize,
        long totalRows
) {
}
