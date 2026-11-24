package com.mullagaliev.aqpbalancer.service.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mullagaliev.aqpbalancer.model.PersistedServer;
import com.mullagaliev.aqpbalancer.model.PersistedServerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable storage for connected server settings.
 *
 * <p>The registry writes {@code connected-servers.json} after every change and
 * reads it on startup, so a balancer restart does not require re-adding the
 * backends. Statistics are intentionally not persisted: they belong to a
 * concrete run, while connection settings belong to the deployment.</p>
 */
@Service
public class ServerStore {

    private static final Logger log = LoggerFactory.getLogger(ServerStore.class);

    private final ObjectMapper objectMapper;
    private final Path file;

    public ServerStore(
            ObjectMapper objectMapper,
            @Value("${balancer.servers-file:./data/connected-servers.json}") String filePath
    ) {
        this.objectMapper = objectMapper;
        this.file = Paths.get(filePath).toAbsolutePath().normalize();
    }

    public synchronized PersistedServerState load() {
        if (!Files.exists(file)) {
            return PersistedServerState.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            long nextSequence = root.path("nextSequence").asLong(0L);
            int version = root.path("version").asInt(PersistedServerState.CURRENT_VERSION);
            if (version > PersistedServerState.CURRENT_VERSION) {
                log.warn("Servers file {} has a newer format version {}; known version is {}",
                        file, version, PersistedServerState.CURRENT_VERSION);
            }
            List<PersistedServer> servers = new ArrayList<>();

            for (JsonNode node : root.path("servers")) {
                try {
                    PersistedServer server = objectMapper.treeToValue(node, PersistedServer.class);
                    if (server.id() != null && !server.id().isBlank() && server.settings() != null) {
                        servers.add(server);
                    }
                } catch (Exception exception) {
                    log.warn("Skipping invalid server entry in {}: {}", file, exception.getMessage());
                }
            }

            log.info("Loaded {} server(s) from {}", servers.size(), file);
            return new PersistedServerState(PersistedServerState.CURRENT_VERSION, nextSequence, servers);
        } catch (Exception exception) {
            log.error("Failed to read {} ({}). Starting with an empty server registry.",
                    file, exception.getMessage());
            return PersistedServerState.empty();
        }
    }

    public synchronized void save(PersistedServerState state) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temporary)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, state);
            }

            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            log.error("Failed to save {}: {}", file, exception.getMessage());
        }
    }

    public Path getFile() {
        return file;
    }
}
