package com.mullagaliev.aqpbalancer.web.dto;

public record ClientQueryRequestDto(
        String clientId,
        String requestId,
        String sql,
        Long estimatedRows,
        String type,
        Double skewness,
        Long arrivalTimeMs
) {
}
