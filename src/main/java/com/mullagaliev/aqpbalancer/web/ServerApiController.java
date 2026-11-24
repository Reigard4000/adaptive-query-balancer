package com.mullagaliev.aqpbalancer.web;

import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.model.ServerHealthResult;
import com.mullagaliev.aqpbalancer.model.ServerSettings;
import com.mullagaliev.aqpbalancer.model.ServerType;
import com.mullagaliev.aqpbalancer.service.discovery.ServerDiscoveryService;
import com.mullagaliev.aqpbalancer.service.runtime.BalancerRuntimeService;
import com.mullagaliev.aqpbalancer.service.balancer.ServerRegistry;
import com.mullagaliev.aqpbalancer.web.dto.DiscoveryResultDto;
import com.mullagaliev.aqpbalancer.web.dto.ServerRequestDto;
import com.mullagaliev.aqpbalancer.web.dto.ServerStatDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/servers")
public class ServerApiController {

    private final ServerRegistry serverRegistry;
    private final ServerDiscoveryService discoveryService;
    private final BalancerRuntimeService runtime;
    private final boolean demoEnabled;

    public ServerApiController(
            ServerRegistry serverRegistry,
            ServerDiscoveryService discoveryService,
            BalancerRuntimeService runtime,
            @Value("${balancer.demo.enabled:false}") boolean demoEnabled
    ) {
        this.serverRegistry = serverRegistry;
        this.discoveryService = discoveryService;
        this.runtime = runtime;
        this.demoEnabled = demoEnabled;
    }

    @GetMapping
    public List<ServerStatDto> list() {
        List<BackendServer> servers = serverRegistry.list();
        runtime.activeServerPool().refresh(servers);
        return SimulationMetrics.serverStats(
                servers,
                totalRequests(servers),
                runtime.activeServerPool().activeIds()
        );
    }

    @PostMapping("/discover")
    public DiscoveryResultDto discover() {
        return discoveryService.discover();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServerStatDto add(@RequestBody ServerRequestDto request) {
        ServerSettings settings = request.toSettings();
        requireSupportedType(settings);
        BackendServer server = serverRegistry.add(settings);
        server.checkHealth();
        runtime.activeServerPool().refresh(serverRegistry.list());
        return SimulationMetrics.serverStat(
                server,
                totalRequests(serverRegistry.list()),
                runtime.activeServerPool().isActive(server.getId())
        );
    }

    @PutMapping("/{id}")
    public ServerStatDto update(@PathVariable String id, @RequestBody ServerRequestDto request) {
        BackendServer existing = serverRegistry.get(id);
        ServerSettings settings = request.toSettings(existing.getSettings());
        requireSupportedType(settings);
        BackendServer server = serverRegistry.update(id, settings);
        server.checkHealth();
        runtime.activeServerPool().refresh(serverRegistry.list());
        return SimulationMetrics.serverStat(
                server,
                totalRequests(serverRegistry.list()),
                runtime.activeServerPool().isActive(server.getId())
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String id) {
        serverRegistry.remove(id);
    }

    @PostMapping("/{id}/check")
    public ServerHealthResult check(@PathVariable String id) {
        return serverRegistry.get(id).checkHealth();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(NoSuchElementException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage() == null ? "Invalid server settings" : exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleState(IllegalStateException exception) {
        return Map.of("error", exception.getMessage() == null ? "Operation is not available" : exception.getMessage());
    }

    private void requireSupportedType(ServerSettings settings) {
        if (!demoEnabled && settings.type() == ServerType.SIMULATED) {
            throw new IllegalArgumentException("SIMULATED servers are available only with balancer.demo.enabled=true");
        }
    }

    private long totalRequests(List<BackendServer> servers) {
        return servers.stream().mapToLong(BackendServer::getTotalRequests).sum();
    }
}
