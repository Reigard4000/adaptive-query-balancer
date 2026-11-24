package com.mullagaliev.aqpbalancer.service.monitoring;

import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryResponse;
import com.mullagaliev.aqpbalancer.model.QueryType;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.web.dto.RecentRequestDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Global counters and recent request history for real client traffic.
 *
 * <p>In-memory counters power the UI and can be reset between experiments.
 * Micrometer counters are cumulative and are exposed through Actuator.</p>
 */
@Component
public class BalancerMetrics {

    private static final int RECENT_LIMIT = 20;

    private final LongAdder offered = new LongAdder();
    private final LongAdder accepted = new LongAdder();
    private final LongAdder shed = new LongAdder();
    private final LongAdder success = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final AtomicLong totalLatencyMicros = new AtomicLong();

    private final Counter offeredCounter;
    private final Counter acceptedCounter;
    private final Counter shedCounter;
    private final Counter successCounter;
    private final Counter failedCounter;

    private final Map<StrategyType, LongAdder> strategyCounts = new EnumMap<>(StrategyType.class);
    private final Map<Phase, LongAdder> phaseCounts = new EnumMap<>(Phase.class);
    private final Map<QueryType, LongAdder> queryTypeCounts = new EnumMap<>(QueryType.class);
    private final ConcurrentLinkedDeque<RecentRequestDto> recentRequests = new ConcurrentLinkedDeque<>();

    private volatile long startedAtMs = System.currentTimeMillis();

    public BalancerMetrics() {
        this(new SimpleMeterRegistry());
    }

    @Autowired
    public BalancerMetrics(MeterRegistry meterRegistry) {
        this.offeredCounter = Counter.builder("balancer.requests")
                .tag("outcome", "offered")
                .register(meterRegistry);
        this.acceptedCounter = Counter.builder("balancer.requests")
                .tag("outcome", "accepted")
                .register(meterRegistry);
        this.shedCounter = Counter.builder("balancer.requests")
                .tag("outcome", "shed")
                .register(meterRegistry);
        this.successCounter = Counter.builder("balancer.requests")
                .tag("outcome", "success")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("balancer.requests")
                .tag("outcome", "failed")
                .register(meterRegistry);

        for (StrategyType value : StrategyType.values()) {
            strategyCounts.put(value, new LongAdder());
        }
        for (Phase value : Phase.values()) {
            phaseCounts.put(value, new LongAdder());
        }
        for (QueryType value : QueryType.values()) {
            queryTypeCounts.put(value, new LongAdder());
        }
    }

    public void recordOffered() {
        offered.increment();
        offeredCounter.increment();
    }

    public void recordAccepted() {
        accepted.increment();
        acceptedCounter.increment();
    }

    public void recordShed(QueryRequest request, Phase phase) {
        shed.increment();
        shedCounter.increment();
        addRecent(new RecentRequestDto(
                request.id(),
                "",
                request.type().name(),
                request.estimatedRows(),
                0.0,
                "—",
                phase == null ? Phase.UNKNOWN.name() : phase.name(),
                false,
                0.0,
                0.0,
                "SHED"
        ));
    }

    public void recordCompletion(QueryResponse response, QueryRequest request, double latencyMs) {
        if (response.success()) {
            success.increment();
            successCounter.increment();
        } else {
            failed.increment();
            failedCounter.increment();
        }
        if (Double.isFinite(latencyMs) && latencyMs > 0.0) {
            totalLatencyMicros.addAndGet(Math.round(latencyMs * 1_000.0));
        }

        strategyCounts.get(response.strategyUsed()).increment();
        phaseCounts.get(response.phaseAtTime()).increment();
        queryTypeCounts.get(request.type()).increment();

        addRecent(new RecentRequestDto(
                request.id(),
                response.serverId(),
                request.type().name(),
                request.estimatedRows(),
                round2(latencyMs),
                response.strategyUsed().name(),
                response.phaseAtTime().name(),
                response.success(),
                round2(response.approximateResult()),
                response.relativeError() == null ? null : round2(response.relativeError()),
                response.success() ? "OK" : "FAILED"
        ));
    }

    public void recordFailure(
            QueryRequest request,
            String serverId,
            Phase phase,
            StrategyType strategy,
            double latencyMs
    ) {
        failed.increment();
        failedCounter.increment();
        if (Double.isFinite(latencyMs) && latencyMs > 0.0) {
            totalLatencyMicros.addAndGet(Math.round(latencyMs * 1_000.0));
        }
        if (strategy != null) {
            strategyCounts.get(strategy).increment();
        }
        if (phase != null) {
            phaseCounts.get(phase).increment();
        }
        queryTypeCounts.get(request.type()).increment();
        addRecent(new RecentRequestDto(
                request.id(),
                serverId == null ? "" : serverId,
                request.type().name(),
                request.estimatedRows(),
                round2(latencyMs),
                strategy == null ? "—" : strategy.name(),
                phase == null ? Phase.UNKNOWN.name() : phase.name(),
                false,
                0.0,
                0.0,
                "FAILED"
        ));
    }

    public List<RecentRequestDto> recentRequests() {
        return new ArrayList<>(recentRequests);
    }

    public Map<String, Long> strategyDistribution() {
        return distribution(strategyCounts);
    }

    public Map<String, Long> phaseDistribution() {
        return distribution(phaseCounts);
    }

    public Map<String, Long> queryTypeDistribution() {
        return distribution(queryTypeCounts);
    }

    public long offered() {
        return offered.sum();
    }

    public long accepted() {
        return accepted.sum();
    }

    public long shed() {
        return shed.sum();
    }

    public long success() {
        return success.sum();
    }

    public long failed() {
        return failed.sum();
    }

    public double averageLatencyMs() {
        long completed = success() + failed();
        return completed == 0 ? 0.0 : totalLatencyMicros.get() / 1_000.0 / completed;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public void reset() {
        offered.reset();
        accepted.reset();
        shed.reset();
        success.reset();
        failed.reset();
        totalLatencyMicros.set(0L);
        strategyCounts.values().forEach(LongAdder::reset);
        phaseCounts.values().forEach(LongAdder::reset);
        queryTypeCounts.values().forEach(LongAdder::reset);
        recentRequests.clear();
        startedAtMs = System.currentTimeMillis();
    }

    private void addRecent(RecentRequestDto dto) {
        recentRequests.addFirst(dto);
        while (recentRequests.size() > RECENT_LIMIT) {
            recentRequests.removeLast();
        }
    }

    private <E extends Enum<E>> Map<String, Long> distribution(Map<E, LongAdder> counts) {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        counts.forEach((key, value) -> result.put(key.name(), value.sum()));
        return result;
    }

    private double round2(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.round(value * 100.0) / 100.0;
    }
}
