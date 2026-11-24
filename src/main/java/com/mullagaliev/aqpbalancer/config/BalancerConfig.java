package com.mullagaliev.aqpbalancer.config;

import com.mullagaliev.aqpbalancer.util.MathUtils;

/**
 * Validated simulation parameters.
 */
public record BalancerConfig(
        int totalQueries,
        long seed,
        double alpha,
        int serverCount
) {

    public static final int DEFAULT_TOTAL_QUERIES = 1_000;
    public static final long DEFAULT_SEED = 42L;
    public static final double DEFAULT_ALPHA = 2.5;
    public static final int DEFAULT_SERVER_COUNT = 3;
    public static final int MAX_TOTAL_QUERIES = 100_000;
    public static final int MAX_SERVER_COUNT = 50;

    public BalancerConfig {
        if (totalQueries <= 0) {
            totalQueries = DEFAULT_TOTAL_QUERIES;
        }
        totalQueries = Math.min(totalQueries, MAX_TOTAL_QUERIES);

        if (!Double.isFinite(alpha) || alpha <= 0.0) {
            alpha = DEFAULT_ALPHA;
        }
        alpha = MathUtils.clamp(alpha, 1.05, 10.0);

        if (serverCount <= 0) {
            serverCount = DEFAULT_SERVER_COUNT;
        }
        serverCount = Math.min(serverCount, MAX_SERVER_COUNT);
    }

    public static BalancerConfig defaults() {
        return new BalancerConfig(DEFAULT_TOTAL_QUERIES, DEFAULT_SEED, DEFAULT_ALPHA, DEFAULT_SERVER_COUNT);
    }
}
