package com.mullagaliev.aqpbalancer.service.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mullagaliev.aqpbalancer.model.PersistedServer;
import com.mullagaliev.aqpbalancer.model.PersistedServerState;
import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsServers() {
        Path file = tempDir.resolve("connected-servers.json");
        ServerStore store = new ServerStore(objectMapper, file.toString());
        ServerSettings settings = new ServerSettings(
                "atlas",
                ServerType.HTTP,
                "http://127.0.0.1:9001/execute",
                7,
                32,
                true,
                2500,
                40.0,
                0.0
        );

        store.save(new PersistedServerState(
                PersistedServerState.CURRENT_VERSION,
                3L,
                List.of(new PersistedServer("server-3", settings))
        ));

        PersistedServerState loaded = store.load();

        assertEquals(3L, loaded.nextSequence());
        assertEquals(1, loaded.servers().size());
        assertEquals("server-3", loaded.servers().get(0).id());
        assertEquals(settings, loaded.servers().get(0).settings());
    }

    @Test
    void missingFileProducesEmptyState() {
        ServerStore store = new ServerStore(objectMapper, tempDir.resolve("absent.json").toString());

        PersistedServerState loaded = store.load();

        assertTrue(loaded.servers().isEmpty());
        assertEquals(0L, loaded.nextSequence());
    }

    @Test
    void corruptFileDoesNotBreakStartup() throws Exception {
        Path file = tempDir.resolve("broken.json");
        Files.writeString(file, "{not-json");
        ServerStore store = new ServerStore(objectMapper, file.toString());

        PersistedServerState loaded = store.load();

        assertTrue(loaded.servers().isEmpty());
    }

    @Test
    void loadsOldFormatWithoutEndpointFields() throws Exception {
        Path file = tempDir.resolve("old.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "nextSequence": 1,
                  "servers": [{
                    "id": "server-1",
                    "settings": {
                      "name": "atlas",
                      "type": "HTTP",
                      "url": "http://127.0.0.1:9001/execute",
                      "weight": 10,
                      "capacity": 32,
                      "enabled": true,
                      "timeoutMs": 3000,
                      "baseLatencyMs": 40.0,
                      "failureRate": 0.0
                    }
                  }]
                }
                """);
        ServerStore store = new ServerStore(objectMapper, file.toString());

        PersistedServerState loaded = store.load();

        assertEquals(1, loaded.servers().size());
        ServerSettings settings = loaded.servers().get(0).settings();
        assertEquals("", settings.healthUrl());
        assertEquals("GET", settings.healthMethod());
        assertTrue(settings.headers().isEmpty());
    }
}
