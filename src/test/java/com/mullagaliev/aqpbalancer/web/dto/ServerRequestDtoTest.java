package com.mullagaliev.aqpbalancer.web.dto;

import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRequestDtoTest {

    @Test
    void updateKeepsFieldsThatAreAbsentFromTheRequest() {
        ServerSettings current = new ServerSettings(
                "atlas",
                ServerType.SIMULATED,
                "",
                25,
                64,
                true,
                4_000,
                12.5,
                0.25
        );
        ServerRequestDto request = new ServerRequestDto(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        ServerSettings updated = request.toSettings(current);

        assertEquals("atlas", updated.name());
        assertEquals(ServerType.SIMULATED, updated.type());
        assertEquals("", updated.url());
        assertEquals(25, updated.weight());
        assertEquals(64, updated.capacity());
        assertTrue(updated.enabled());
        assertEquals(4_000, updated.timeoutMs());
        assertEquals(12.5, updated.baseLatencyMs(), 1.0e-9);
        assertEquals(0.25, updated.failureRate(), 1.0e-9);
    }

    @Test
    void updateAppliesProvidedFields() {
        ServerSettings current = new ServerSettings(
                "atlas",
                ServerType.HTTP,
                "http://127.0.0.1:9001/execute",
                10,
                32,
                true,
                3_000,
                40.0,
                0.0
        );
        ServerRequestDto request = new ServerRequestDto(
                null,
                "HTTP",
                "http://127.0.0.1:9002/execute",
                null,
                48,
                false,
                null,
                null,
                null
        );

        ServerSettings updated = request.toSettings(current);

        assertEquals("atlas", updated.name());
        assertEquals("http://127.0.0.1:9002/execute", updated.url());
        assertEquals(10, updated.weight());
        assertEquals(48, updated.capacity());
        assertTrue(!updated.enabled());
        assertEquals(3_000, updated.timeoutMs());
    }

    @Test
    void failureRateIsStoredAsAFraction() {
        ServerRequestDto request = new ServerRequestDto(
                "sim",
                "SIMULATED",
                "",
                10,
                32,
                true,
                3_000,
                40.0,
                0.25
        );

        ServerSettings settings = request.toSettings();

        assertEquals(0.25, settings.failureRate(), 1.0e-9);
    }
}
