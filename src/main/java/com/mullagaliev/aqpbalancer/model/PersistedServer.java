package com.mullagaliev.aqpbalancer.model;

public record PersistedServer(
        String id,
        ServerSettings settings
) {
}
