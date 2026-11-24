package com.mullagaliev.aqpbalancer.service.balancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import com.mullagaliev.aqpbalancer.service.persistence.ServerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRegistryPersistenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void restoresServersAfterRestart() {
        ServerStore store = new ServerStore(objectMapper, tempDir.resolve("servers.json").toString());
        ServerRegistry first = new ServerRegistry(store);
        ServerSettings settings = new ServerSettings(
                "atlas",
                ServerType.HTTP,
                "http://127.0.0.1:9001/execute",
                12,
                32,
                true,
                3000,
                40.0,
                0.0
        );

        BackendServer added = first.add(settings);
        first.update(added.getId(), new ServerSettings(
                "atlas-renamed",
                ServerType.HTTP,
                "http://127.0.0.1:9001/execute",
                25,
                32,
                false,
                3000,
                40.0,
                0.0
        ));

        ServerRegistry restored = new ServerRegistry(store);
        assertEquals(1, restored.list().size());
        BackendServer restoredServer = restored.get(added.getId());
        assertEquals("atlas-renamed", restoredServer.getName());
        assertEquals(25, restoredServer.getWeight());
        assertTrue(!restoredServer.isEnabled());
    }

    @Test
    void removalIsPersisted() {
        ServerStore store = new ServerStore(objectMapper, tempDir.resolve("servers.json").toString());
        ServerRegistry first = new ServerRegistry(store);
        BackendServer added = first.add(ServerSettings.http(
                "borealis",
                "http://127.0.0.1:9002/execute",
                10,
                3000
        ));

        first.remove(added.getId());

        ServerRegistry restored = new ServerRegistry(store);
        assertTrue(restored.list().isEmpty());
    }
}
