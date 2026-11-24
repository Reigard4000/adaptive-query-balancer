package com.mullagaliev.aqpbalancer.web;

import com.mullagaliev.aqpbalancer.service.runtime.BalancerRuntimeService;
import com.mullagaliev.aqpbalancer.web.dto.BalancerSnapshotDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitorApiController {

    private final BalancerRuntimeService runtime;
    private final MonitoringStreamService streamService;

    public MonitorApiController(
            BalancerRuntimeService runtime,
            MonitoringStreamService streamService
    ) {
        this.runtime = runtime;
        this.streamService = streamService;
    }

    @GetMapping("/snapshot")
    public BalancerSnapshotDto snapshot() {
        return runtime.snapshot();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "1000") int intervalMs) {
        return streamService.open(intervalMs);
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        runtime.reset();
        return Map.of("status", "RESET");
    }
}
