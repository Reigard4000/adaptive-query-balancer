package com.mullagaliev.aqpbalancer.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsTest {

    @Test
    void derivesHealthAndStatsEndpointsFromExecuteUrl() {
        ServerSettings settings = ServerSettings.http(
                "atlas", "http://127.0.0.1:9001/execute", 10, 3_000
        );

        assertEquals("http://127.0.0.1:9001/health", settings.resolvedHealthUrl());
        assertEquals("http://127.0.0.1:9001/stats", settings.resolvedStatsUrl());
        assertEquals("GET", settings.healthMethod());
    }

    @Test
    void keepsConfiguredEndpointsAndHeaders() {
        ServerSettings settings = new ServerSettings(
                "atlas",
                ServerType.HTTP,
                "https://analytics.example.com/api/execute",
                10,
                32,
                true,
                5_000,
                40.0,
                0.0,
                "https://analytics.example.com/api/health",
                "https://analytics.example.com/api/stats",
                "head",
                Map.of("Authorization", "Bearer env:ATLAS_TOKEN")
        );

        assertEquals("https://analytics.example.com/api/health", settings.resolvedHealthUrl());
        assertEquals("https://analytics.example.com/api/stats", settings.resolvedStatsUrl());
        assertEquals("HEAD", settings.healthMethod());
        assertEquals("Bearer env:ATLAS_TOKEN", settings.headers().get("Authorization"));
    }

    @Test
    void oldConstructorProducesSafeDefaults() {
        ServerSettings settings = ServerSettings.simulated("sim", 10, 40.0, 0.0);

        assertTrue(settings.headers().isEmpty());
        assertEquals("GET", settings.healthMethod());
        assertEquals("", settings.healthUrl());
        assertEquals("", settings.statsUrl());
    }
}
