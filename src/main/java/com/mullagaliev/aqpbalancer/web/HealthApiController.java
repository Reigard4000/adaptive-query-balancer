package com.mullagaliev.aqpbalancer.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight liveness endpoint kept for backwards compatibility.
 *
 * <p>Readiness and dependency-aware health are exposed by Spring Boot Actuator
 * at {@code /actuator/health}.</p>
 */
@RestController
@RequestMapping("/api")
public class HealthApiController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "aqp-balancer");
    }
}
