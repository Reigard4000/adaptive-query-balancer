package com.mullagaliev.aqpbalancer.model;

/**
 * Стратегия выборки данных.
 */
public enum StrategyType {
    EXACT,
    UNIFORM,
    STRATIFIED;

    public static StrategyType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return StrategyType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}