package com.mullagaliev.aqpbalancer.model;

import java.util.List;

public record PersistedServerState(
        int version,
        long nextSequence,
        List<PersistedServer> servers
) {

    public static final int CURRENT_VERSION = 1;

    public PersistedServerState {
        if (servers == null) {
            servers = List.of();
        } else {
            servers = List.copyOf(servers);
        }
    }

    public static PersistedServerState empty() {
        return new PersistedServerState(CURRENT_VERSION, 0L, List.of());
    }
}
