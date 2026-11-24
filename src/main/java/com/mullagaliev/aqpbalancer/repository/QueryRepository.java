package com.mullagaliev.aqpbalancer.repository;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over the demo query data source. It is used only by simulated
 * backends and the console demo; the runtime balancer does not read local data.
 */
public interface QueryRepository {

    List<Double> fetchAll();

    /**
     * Returns exactly {@code sampleSize} values. When the requested size is
     * larger than the data set, values are sampled with replacement.
     */
    List<Double> fetchSample(int sampleSize);

    /**
     * Returns a proportional stratified sample grouped by stratum name.
     * The total number of values is {@code sampleSize} (unless the data set is
     * smaller than one value per stratum).
     */
    Map<String, List<Double>> fetchStratified(int sampleSize);
}
