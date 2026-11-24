package com.mullagaliev.aqpbalancer.web;

import com.mullagaliev.aqpbalancer.config.BalancerConfig;
import com.mullagaliev.aqpbalancer.controller.SimulationController;
import com.mullagaliev.aqpbalancer.exception.BalancerException;
import com.mullagaliev.aqpbalancer.model.BackendServer;
import com.mullagaliev.aqpbalancer.service.balancer.ServerRegistry;
import com.mullagaliev.aqpbalancer.web.dto.SimulationRequestDto;
import com.mullagaliev.aqpbalancer.web.dto.SimulationResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "balancer.demo.enabled", havingValue = "true")
public class SimulationApiController {

    private final SimulationController simulationController = new SimulationController();
    private final StreamingSimulationService streamingSimulationService;
    private final ServerRegistry serverRegistry;

    public SimulationApiController(
            StreamingSimulationService streamingSimulationService,
            ServerRegistry serverRegistry
    ) {
        this.streamingSimulationService = streamingSimulationService;
        this.serverRegistry = serverRegistry;
    }

    @PostMapping("/simulate")
    public SimulationResultDto simulate(@RequestBody(required = false) SimulationRequestDto request) {
        SimulationRequestDto safe = request == null
                ? new SimulationRequestDto(1_000, 42L, 2.5)
                : request;
        BalancerConfig config = new BalancerConfig(
                safe.totalQueries(),
                safe.seed(),
                safe.alpha(),
                1
        );
        List<BackendServer> servers = serverRegistry.beginRun(config.seed(), config.alpha());
        return simulationController.runForWeb(safe, servers);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(defaultValue = "42") long seed,
            @RequestParam(defaultValue = "2.5") double alpha,
            @RequestParam(defaultValue = "25") int batchSize,
            @RequestParam(defaultValue = "500") int intervalMs
    ) {
        return streamingSimulationService.open(seed, alpha, batchSize, intervalMs);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage() == null ? "Invalid request" : exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalState(IllegalStateException exception) {
        return Map.of("error", exception.getMessage() == null ? "No servers available" : exception.getMessage());
    }

    @ExceptionHandler(BalancerException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBalancer(BalancerException exception) {
        return Map.of("error", exception.getMessage() == null ? "Balancer error" : exception.getMessage());
    }
}
