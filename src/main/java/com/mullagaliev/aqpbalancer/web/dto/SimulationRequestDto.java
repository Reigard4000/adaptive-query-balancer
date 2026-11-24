package com.mullagaliev.aqpbalancer.web.dto;

public record SimulationRequestDto(
        int totalQueries,
        long seed,
        double alpha
) {
}
