package com.mullagaliev.aqpbalancer.service.balancer;

import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdmissionControllerTest {

    @Test
    void shedsWhenAllCapacityIsReserved() {
        AdmissionController admission = new AdmissionController(0.7, 0.9, 1.2);
        ActiveServerPool pool = new ActiveServerPool(1, 0.7, 0.25, 1_000);
        BackendServer server = server("server-1", 10);
        List<BackendServer> servers = List.of(server);

        int accepted = 0;
        for (int index = 0; index < 12; index++) {
            AdmissionController.Decision decision = admission.tryAcquire(servers, pool);
            if (decision.accepted()) {
                accepted++;
            }
        }

        assertEquals(12, accepted);
        AdmissionController.Decision shed = admission.tryAcquire(servers, pool);
        assertFalse(shed.accepted());
        assertEquals(AdmissionController.LoadLevel.OVERLOAD, shed.loadLevel());
    }

    @Test
    void scalesOutBeforeShedding() {
        AdmissionController admission = new AdmissionController(0.7, 0.9, 1.2);
        ActiveServerPool pool = new ActiveServerPool(1, 0.7, 0.25, 1_000);
        BackendServer first = server("server-1", 10);
        BackendServer second = server("server-2", 10);
        List<BackendServer> servers = List.of(first, second);

        for (int index = 0; index < 9; index++) {
            assertTrue(admission.tryAcquire(servers, pool).accepted());
        }
        AdmissionController.Decision decision = admission.tryAcquire(servers, pool);

        assertTrue(decision.accepted());
        assertTrue(pool.activeCount(servers) >= 2);
        assertTrue(decision.capacity() >= 20);
    }

    @Test
    void reportsNoServersWithoutRoutableBackends() {
        AdmissionController admission = new AdmissionController(0.7, 0.9, 1.2);
        ActiveServerPool pool = new ActiveServerPool(1, 0.7, 0.25, 1_000);

        AdmissionController.Decision decision = admission.tryAcquire(List.of(), pool);

        assertFalse(decision.accepted());
        assertEquals(0, decision.capacity());
    }

    private BackendServer server(String id, int capacity) {
        ServerSettings settings = new ServerSettings(
                id,
                ServerType.HTTP,
                "http://127.0.0.1:9001/execute",
                10,
                capacity,
                true,
                1_000,
                40.0,
                0.0
        );
        BackendServer server = new BackendServer(id, settings);
        server.updateRemoteLoad(0, 0, capacity, capacity * 2, 0, 0.0);
        return server;
    }
}
