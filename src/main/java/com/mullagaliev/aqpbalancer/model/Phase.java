package com.mullagaliev.aqpbalancer.model;

/**
 * Фаза системы относительно индекса хвоста a.
 */
public enum Phase {
    LIGHT_TAIL,
    WARNING,
    HEAVY_TAIL,
    UNKNOWN;

    public static Phase fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return Phase.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /**
     * true для тревожных фаз - WARNING и HEAVY_TAIL.
     */
    public boolean isCritical() {
        return this == WARNING || this == HEAVY_TAIL;
    }
}
