package com.mullagaliev.aqpbalancer.model;

/**
 * Тип аналитического запроса.
 */
public enum QueryType {
    SCAN,
    JOIN,
    AGGREGATE,
    TOP_K,
    UNKNOWN;

    public static QueryType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return QueryType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}