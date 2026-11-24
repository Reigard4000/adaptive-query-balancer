package com.mullagaliev.aqpbalancer.model;

public record QueryRequest(
        String id,
        String sql,
        long estimatedRows,
        QueryType type,
        double skewness,
        long arrivalTimeMs,
        String clientId
) {

    public QueryRequest(
            String id,
            String sql,
            long estimatedRows,
            QueryType type,
            double skewness,
            long arrivalTimeMs
    ) {
        this(id, sql, estimatedRows, type, skewness, arrivalTimeMs, null);
    }
}
