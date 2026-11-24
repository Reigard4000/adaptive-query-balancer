package com.mullagaliev.aqpbalancer.web;

import com.mullagaliev.aqpbalancer.service.runtime.BalancerRuntimeService;
import com.mullagaliev.aqpbalancer.web.dto.ClientQueryRequestDto;
import com.mullagaliev.aqpbalancer.web.dto.ClientQueryResponseDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client-facing query endpoint.
 *
 * <p>External load clients send analytical requests here. The balancer decides
 * which server to use, which sampling strategy to apply, and whether the
 * request should be admitted or shed.</p>
 */
@RestController
@RequestMapping("/api")
public class ClientApiController {

    private final BalancerRuntimeService runtime;

    public ClientApiController(BalancerRuntimeService runtime) {
        this.runtime = runtime;
    }

    @PostMapping("/execute")
    public ResponseEntity<ClientQueryResponseDto> execute(
            @RequestBody(required = false) ClientQueryRequestDto request
    ) {
        ClientQueryRequestDto safe = request == null
                ? new ClientQueryRequestDto(null, null, null, 1_000L, "SCAN", 0.5, null)
                : request;

        ClientQueryResponseDto response = runtime.dispatch(safe);
        return switch (response.status()) {
            case "OK" -> ResponseEntity.ok(response);
            case "SHED" -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, "1")
                    .body(response);
            case "NO_SERVERS" -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            case "FAILED" -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        };
    }
}
