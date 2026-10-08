package com.mullagaliev.aqpbalancer.model;

/**
 * Ответ клиенту от балансировщика.
 * Содержит приближённый результат и метаданные о том, как запрос был обработан.
 */
public record QueryResponse(
    /** Идентификатор исходного запроса */
    long requestId,

    /** Приближённый результат выполнения */
    double approximateResult,

    /** Время выполнения запроса в миллисекундах */
    double latencyMs,

    /** Стратегия выборки, которая была использована */
    StrategyType strategyUsed,

    /** Фаза системы в момент выполнения запроса */
    Phase phaseAtTime
) {
    @Override
    public String toString() {
        return String.format("QueryResponse[id=%d, result=%.2f, latency=%.1fms, strategy=%s, phase=%s]",
            requestId, approximateResult, latencyMs, strategyUsed, phaseAtTime
        );
    }
}