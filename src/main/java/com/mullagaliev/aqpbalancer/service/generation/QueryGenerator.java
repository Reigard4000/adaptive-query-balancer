package com.mullagaliev.aqpbalancer.service.generation;

import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryType;

import java.util.Random;

/**
 * Генератор потока запросов для симуляции.
 * Создаёт QueryRequest.
 */
public class QueryGenerator {
    private final Random random;
    private final long totalCount;
    private long counter;

    public QueryGenerator(long totalCount, long seed) {
        this.totalCount = totalCount;
        this.random = new Random(seed);
        this.counter = 0;
    }

    public boolean hasNext() {
        return counter < totalCount;
    }

    /**
     * @return следующий QueryRequest
     * @throws IllegalStateException если запросы закончились
     */
    public QueryRequest next() {
        if (!hasNext()) {
            throw new IllegalStateException("No more queries to generate");
        }

        counter++;
        long id = counter;
        QueryType type = pickType();
        String sql = sqlTemplateFor(type);
        long estimatedRows = pickEstimatedRows();
        double skewness = pickSkewness();
        long arrivalTimeMs = System.currentTimeMillis();

        return new QueryRequest(id, sql, estimatedRows, type, skewness, arrivalTimeMs);
    }

    /**
     * Распределение типов запросов:
     *   SCAN      - 50%
     *   JOIN      - 20%
     *   AGGREGATE - 20%
     *   TOP_K     - 10%
     */
    private QueryType pickType() {
        double r = random.nextDouble();
        if (r < 0.50) return QueryType.SCAN;
        if (r < 0.70) return QueryType.JOIN;
        if (r < 0.90) return QueryType.AGGREGATE;
        return QueryType.TOP_K;
    }

    /**
     * Логарифмическое распределение от 10^3 до 10^7.
     */
    private long pickEstimatedRows() {
        double exponent = 3.0 + random.nextDouble() * 4.0;   // 3..7
        return (long) Math.pow(10, exponent);
    }

    /** Скошенность от -1.0 до 1.0. */
    private double pickSkewness() {
        return -1.0 + random.nextDouble() * 2.0;
    }

    private String sqlTemplateFor(QueryType type) {
        return switch (type) {
            case SCAN      -> "SELECT * FROM t WHERE x > ?";
            case JOIN      -> "SELECT * FROM a JOIN b ON a.id = b.id";
            case AGGREGATE -> "SELECT SUM(x) FROM t GROUP BY y";
            case TOP_K     -> "SELECT * FROM t ORDER BY x DESC LIMIT 100";
            case UNKNOWN   -> "SELECT * FROM t";
        };
    }
}