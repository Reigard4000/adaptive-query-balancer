package com.mullagaliev.aqpbalancer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendServerCircuitBreakerTest {

    @Test
    void opensAfterRepeatedFailuresAndRecoversAfterSuccess() {
        BackendServer server = new BackendServer("server-1", new ServerSettings(
                "atlas",
                ServerType.HTTP,
                "http://127.0.0.1:9001/execute",
                10,
                32,
                true,
                1_000,
                40.0,
                0.0
        ));

        for (int index = 0; index < 5; index++) {
            server.onRequestDispatched();
            server.recordCompletion(ServerExecutionResult.failure(10.0, 503, "boom"));
        }

        assertTrue(server.isCircuitOpen());
        assertFalse(server.isRoutable());

        server.onRequestDispatched();
        server.recordCompletion(ServerExecutionResult.success(10.0, 200, 1.0, 100, 1_000, false));

        assertFalse(server.isCircuitOpen());
        assertTrue(server.isRoutable());
    }
}
