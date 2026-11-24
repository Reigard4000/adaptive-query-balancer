package com.mullagaliev.aqpbalancer.web.dto;

import java.util.List;

public record DiscoveryResultDto(
        List<DiscoveredBackendDto> added,
        List<DiscoveredBackendDto> existing,
        List<UnreachableBackendDto> unreachable
) {

    public record DiscoveredBackendDto(
            String id,
            String name,
            String url,
            String serverId,
            String profile
    ) {
    }

    public record UnreachableBackendDto(
            String url,
            String reason
    ) {
    }
}
