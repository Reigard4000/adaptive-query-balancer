package com.mullagaliev.aqpbalancer.service.balancer;

import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.PersistedServer;
import com.mullagaliev.aqpbalancer.model.PersistedServerState;
import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import com.mullagaliev.aqpbalancer.service.persistence.ServerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of manually connected backend servers.
 *
 * <p>Unlike the previous in-memory-only version, the registry restores its
 * contents from disk on startup and persists every add/update/remove.</p>
 */
@Service
public class ServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServerRegistry.class);

    private final Map<String, BackendServer> servers = new LinkedHashMap<>();
    private final ServerStore store;
    private long sequence = 0L;

    public ServerRegistry(ServerStore store) {
        this(store, true);
    }

    @Autowired
    public ServerRegistry(ServerStore store, @Value("${balancer.demo.enabled:false}") boolean demoEnabled) {
        this.store = Objects.requireNonNull(store, "store");
        PersistedServerState state = store.load();
        this.sequence = Math.max(0L, state.nextSequence());
        for (PersistedServer persisted : state.servers()) {
            if (!demoEnabled && persisted.settings().type() == ServerType.SIMULATED) {
                log.warn("Skipping simulated server {} because demo mode is disabled", persisted.id());
                continue;
            }
            BackendServer server = new BackendServer(persisted.id(), persisted.settings());
            servers.put(persisted.id(), server);
            sequence = Math.max(sequence, numericSuffix(persisted.id()));
        }
    }

    public synchronized List<BackendServer> list() {
        return new ArrayList<>(servers.values());
    }

    public synchronized List<BackendServer> beginRun(long seed, double alpha) {
        if (servers.values().stream().noneMatch(BackendServer::isEnabled)) {
            throw new IllegalStateException(
                    "Нет включённых серверов. Подключите хотя бы один бекенд на вкладке «Серверы»."
            );
        }
        if (servers.values().stream().noneMatch(BackendServer::isRoutable)) {
            throw new IllegalStateException(
                    "Нет доступных серверов: все отключены или не прошли проверку доступности."
            );
        }

        long serverIndex = 0L;
        for (BackendServer server : servers.values()) {
            server.resetForRun(seed + serverIndex, alpha);
            serverIndex++;
        }
        return list();
    }

    public synchronized BackendServer add(ServerSettings settings) {
        ensureUniqueUrl(settings, null);
        String id = "server-" + (++sequence);
        BackendServer server = new BackendServer(id, settings);
        servers.put(id, server);
        persist();
        return server;
    }

    public synchronized BackendServer update(String id, ServerSettings settings) {
        BackendServer server = get(id);
        ensureUniqueUrl(settings, id);
        boolean typeChanged = server.getType() != settings.type();
        server.applySettings(settings);
        if (typeChanged) {
            server.resetStatistics();
        }
        persist();
        return server;
    }

    public synchronized void remove(String id) {
        if (servers.remove(id) == null) {
            throw new NoSuchElementException("Сервер " + id + " не найден");
        }
        persist();
    }

    public synchronized BackendServer get(String id) {
        BackendServer server = servers.get(id);
        if (server == null) {
            throw new NoSuchElementException("Сервер " + id + " не найден");
        }
        return server;
    }

    public synchronized boolean containsUrl(String url) {
        return findByUrl(url).isPresent();
    }

    public synchronized Optional<BackendServer> findByUrl(String url) {
        return servers.values().stream()
                .filter(server -> server.getType() == ServerType.HTTP)
                .filter(server -> sameUrl(server.getUrl(), url))
                .findFirst();
    }

    public ServerStore getStore() {
        return store;
    }

    private void ensureUniqueUrl(ServerSettings settings, String allowedId) {
        if (settings.type() != ServerType.HTTP) {
            return;
        }
        for (Map.Entry<String, BackendServer> entry : servers.entrySet()) {
            if (entry.getKey().equals(allowedId)) {
                continue;
            }
            BackendServer existing = entry.getValue();
            if (existing.getType() == ServerType.HTTP && sameUrl(existing.getUrl(), settings.url())) {
                throw new IllegalArgumentException(
                        "Сервер с URL " + settings.url() + " уже подключён как "
                                + existing.getDisplayName()
                );
            }
        }
    }

    private boolean sameUrl(String left, String right) {
        return normalizeUrl(left).equals(normalizeUrl(right));
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private long numericSuffix(String id) {
        if (id == null) {
            return 0L;
        }
        int dash = id.lastIndexOf('-');
        if (dash < 0 || dash == id.length() - 1) {
            return 0L;
        }
        try {
            return Long.parseLong(id.substring(dash + 1));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private void persist() {
        List<PersistedServer> snapshot = servers.values().stream()
                .map(server -> new PersistedServer(server.getId(), server.getSettings()))
                .toList();
        store.save(new PersistedServerState(PersistedServerState.CURRENT_VERSION, sequence, snapshot));
    }
}
