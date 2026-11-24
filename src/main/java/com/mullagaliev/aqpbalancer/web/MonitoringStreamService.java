package com.mullagaliev.aqpbalancer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mullagaliev.aqpbalancer.service.runtime.BalancerRuntimeService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Publishes live balancer snapshots without generating any traffic.
 *
 * <p>All subscribers share a small scheduler; the number of monitoring threads
 * does not grow with the number of connected browsers.</p>
 */
@Service
public class MonitoringStreamService {

    private static final int MIN_INTERVAL_MS = 200;
    private static final int MAX_INTERVAL_MS = 5_000;

    private final ObjectMapper objectMapper;
    private final BalancerRuntimeService runtime;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            2,
            runnable -> {
                Thread thread = new Thread(runnable, "aqp-monitor-stream");
                thread.setDaemon(true);
                return thread;
            }
    );

    public MonitoringStreamService(ObjectMapper objectMapper, BalancerRuntimeService runtime) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public SseEmitter open(int intervalMs) {
        int safeIntervalMs = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, intervalMs));
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        emitter.onCompletion(() -> {
            running.set(false);
            cancel(futureRef);
        });
        emitter.onTimeout(() -> {
            running.set(false);
            cancel(futureRef);
            emitter.complete();
        });
        emitter.onError(exception -> {
            running.set(false);
            cancel(futureRef);
        });

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> sendSnapshot(emitter, running, futureRef),
                0L,
                safeIntervalMs,
                TimeUnit.MILLISECONDS
        );
        futureRef.set(future);
        return emitter;
    }

    private void sendSnapshot(
            SseEmitter emitter,
            AtomicBoolean running,
            AtomicReference<ScheduledFuture<?>> futureRef
    ) {
        if (!running.get()) {
            cancel(futureRef);
            complete(emitter);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(objectMapper.writeValueAsString(runtime.snapshot())));
        } catch (IOException | IllegalStateException exception) {
            running.set(false);
            cancel(futureRef);
            complete(emitter);
        } catch (RuntimeException exception) {
            running.set(false);
            cancel(futureRef);
            complete(emitter);
        }
    }

    private void cancel(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // Client already disconnected.
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
