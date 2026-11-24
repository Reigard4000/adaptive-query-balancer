package com.mullagaliev.aqpbalancer.service.balancer;

import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveServerPoolTest {

    @Test
    void initiallyActivatesTheStrongestFreeServer() {
        ActiveServerPool pool = new ActiveServerPool(1, 0.7, 0.25, 1_000);
        BackendServer small = server("server-1", 16);
        BackendServer large = server("server-2", 48);

        BackendServer picked = pool.pick(List.of(small, large));

        assertEquals("server-2", picked.getId());
        assertEquals(1, pool.activeCount(List.of(small, large)));
    }

    @Test
    void scalesOutWhenUtilizationIsHigh() {
        ActiveServerPool pool = new ActiveServerPool(1, 0.7, 0.25, 1_000);
        BackendServer first = server("server-1", 48);
        BackendServer second = server("server-2", 32);
        BackendServer third = server("server-3", 24);
        List<BackendServer> servers = List.of(first, second, third);

        pool.pick(servers);
        pool.scaleOutIfNeeded(servers, 40);

        assertEquals(2, pool.activeCount(servers));
        assertTrue(pool.activeIds().contains("server-1"));
    }

    @Test
    void scalesInUnderLowTrafficEvenWhenSomeRequestsAreInFlight() throws InterruptedException {
        ActiveServerPool pool = new ActiveServerPool(1, 0.7, 0.25, 1_000);
        BackendServer first = server("server-1", 48);
        BackendServer second = server("server-2", 32);
        List<BackendServer> servers = List.of(first, second);

        pool.pick(servers);
        pool.scaleOutIfNeeded(servers, 40);
        assertEquals(2, pool.activeCount(servers));

        pool.scaleInIfIdle(servers, 3);
        assertEquals(2, pool.activeCount(servers));

        Thread.sleep(1_100L);
        pool.scaleInIfIdle(servers, 3);

        assertEquals(1, pool.activeCount(servers));
    }

    @Test
    void skipsDisabledServers() {
        ActiveServerPool pool = new ActiveServerPool(1, 0.7, 0.25, 1_000);
        BackendServer disabled = server("server-1", 48, false);
        BackendServer enabled = server("server-2", 32, true);

        BackendServer picked = pool.pick(List.of(disabled, enabled));

        assertEquals("server-2", picked.getId());
    }

    private BackendServer server(String id, int capacity) {
        return server(id, capacity, true);
    }

    private BackendServer server(String id, int capacity, boolean enabled) {
        ServerSettings settings = new ServerSettings(
                id,
                ServerType.HTTP,
                "http://127.0.0.1:9001/execute",
                10,
                capacity,
                enabled,
                1_000,
                40.0,
                0.0
        );
        BackendServer server = new BackendServer(id, settings);
        server.updateRemoteLoad(0, 0, capacity, capacity * 2, 0, 0.0);
        return server;
    }
}
