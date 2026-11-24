package com.mullagaliev.aqpbalancer.service.generation;

import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryType;

import java.util.Locale;
import java.util.Random;

/**
 * Deterministic synthetic stream of analytical queries.
 */
public class QueryGenerator {

    private static final long MIN_ROWS = 1_000L;
    private static final long MAX_ROWS = 1_000_000L;

    private final Random random;
    private long sequence = 0L;

    public QueryGenerator(long seed) {
        this.random = new Random(seed);
    }

    public QueryRequest next() {
        sequence++;
        QueryType type = nextType();
        long estimatedRows = nextEstimatedRows();
        double skewness = random.nextDouble();
        long arrivalTimeMs = sequence * 10L + random.nextInt(5);
        String sql = "SELECT " + projection(type)
                + " FROM analytics.events WHERE ds >= current_date - 7 AND " + predicate(type)
                + " GROUP BY " + groupBy(type);

        return new QueryRequest(
                "q-" + sequence,
                sql,
                estimatedRows,
                type,
                skewness,
                arrivalTimeMs
        );
    }

    private QueryType nextType() {
        int roll = random.nextInt(100);
        if (roll < 40) {
            return QueryType.SCAN;
        }
        if (roll < 65) {
            return QueryType.JOIN;
        }
        if (roll < 90) {
            return QueryType.AGGREGATE;
        }
        if (roll < 95) {
            return QueryType.TOP_K;
        }
        return QueryType.UNKNOWN;
    }

    private long nextEstimatedRows() {
        double logRows = 10.5 + random.nextGaussian() * 0.9;
        long rows = (long) Math.exp(logRows);
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
    }

    private String projection(QueryType type) {
        return switch (type) {
            case SCAN -> "user_id, event_type, payload";
            case JOIN -> "u.user_id, e.event_type, count(*)";
            case AGGREGATE -> "event_type, avg(duration_ms), count(*)";
            case TOP_K -> "user_id, sum(amount) AS total";
            case UNKNOWN -> "*";
        };
    }

    private String predicate(QueryType type) {
        return switch (type) {
            case SCAN -> "event_type = 'click'";
            case JOIN -> "u.country = 'RU' AND e.user_id = u.user_id";
            case AGGREGATE -> "duration_ms > 100";
            case TOP_K -> "amount > 10";
            case UNKNOWN -> "1 = 1";
        };
    }

    private String groupBy(QueryType type) {
        return switch (type) {
            case SCAN -> "user_id, event_type, payload";
            case JOIN -> "u.user_id, e.event_type";
            case AGGREGATE -> "event_type";
            case TOP_K -> "user_id";
            case UNKNOWN -> "1";
        };
    }

    public String describe() {
        return String.format(Locale.ROOT, "QueryGenerator[generated=%d]", sequence);
    }
}
