package com.mullagaliev.aqpbalancer.service;

import com.mullagaliev.aqpbalancer.config.AdaptiveConfig;
import com.mullagaliev.aqpbalancer.exception.EstimationException;
import com.mullagaliev.aqpbalancer.model.ExecutionSample;
import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryResponse;
import com.mullagaliev.aqpbalancer.model.ServerExecutionResult;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.service.decision.StrategyPolicy;
import com.mullagaliev.aqpbalancer.service.decision.ThompsonSampler;
import com.mullagaliev.aqpbalancer.service.learning.BanditUpdater;
import com.mullagaliev.aqpbalancer.service.phase.EnsembleTailIndexEstimator;
import com.mullagaliev.aqpbalancer.service.phase.LatencyWindow;
import com.mullagaliev.aqpbalancer.service.phase.PhaseTransitionDetector;
import com.mullagaliev.aqpbalancer.service.quality.AccuracyProvider;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Orchestrates the closed adaptation loop:
 * phase estimation -> strategy decision -> execution -> learning.
 *
 * <p>The core never reads a local reference dataset. Accuracy is supplied by an
 * {@link AccuracyProvider}; when a backend does not report accuracy the
 * learning loop optimises latency only.</p>
 */
public class LoadBalancerCore {

    public static final double DEFAULT_ALPHA = 2.5;

    private final LatencyWindow latencyWindow;
    private final EnsembleTailIndexEstimator estimator;
    private final PhaseTransitionDetector detector;
    private final StrategyPolicy policy;
    private final ThompsonSampler sampler;
    private final BanditUpdater banditUpdater;
    private final AccuracyProvider accuracyProvider;
    private final AdaptiveConfig config;

    private volatile double lastAlpha;
    private volatile Phase lastPhase = Phase.UNKNOWN;
    private long processedCount = 0L;
    private double totalLatencyMs = 0.0;
    private long lastRefreshAtMs = 0L;
    private int samplesSinceRefresh = 0;

    public LoadBalancerCore(
            LatencyWindow latencyWindow,
            EnsembleTailIndexEstimator estimator,
            PhaseTransitionDetector detector,
            StrategyPolicy policy,
            ThompsonSampler sampler,
            BanditUpdater banditUpdater,
            AccuracyProvider accuracyProvider,
            AdaptiveConfig config
    ) {
        this.latencyWindow = Objects.requireNonNull(latencyWindow, "latencyWindow");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.banditUpdater = Objects.requireNonNull(banditUpdater, "banditUpdater");
        this.accuracyProvider = Objects.requireNonNull(accuracyProvider, "accuracyProvider");
        this.config = Objects.requireNonNull(config, "config");
        this.lastAlpha = config.defaultAlpha();
    }

    public synchronized PreparedExecution prepare(QueryRequest request) {
        Objects.requireNonNull(request, "request");

        refreshPhaseIfDue();
        QueryContext context = buildContext(request);
        Set<StrategyType> allowed = policy.allowedFor(lastPhase, lastAlpha);
        StrategyType strategy = sampler.select(context, allowed);

        return new PreparedExecution(request, context, strategy, lastPhase);
    }

    public synchronized QueryResponse complete(
            PreparedExecution plan,
            String serverId,
            ServerExecutionResult execution
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(execution, "execution");

        double latencyMs = Double.isFinite(execution.latencyMs())
                ? Math.max(0.0, execution.latencyMs())
                : 0.0;

        double approximateResult = execution.hasResult() ? execution.result() : 0.0;
        OptionalDouble accuracy = accuracyProvider.relativeError(plan.request(), execution);
        Double relativeError = accuracy.isPresent() ? accuracy.getAsDouble() : null;

        ExecutionSample executionSample = new ExecutionSample(
                plan.request().id(),
                latencyMs,
                execution.success(),
                relativeError
        );
        banditUpdater.onExecutionFinished(plan.context(), plan.strategy(), executionSample);

        if (execution.success()) {
            latencyWindow.add(latencyMs);
            samplesSinceRefresh++;
        }
        processedCount++;
        totalLatencyMs += latencyMs;

        return new QueryResponse(
                plan.request().id(),
                approximateResult,
                latencyMs,
                plan.strategy(),
                plan.phase(),
                serverId,
                execution.success(),
                relativeError
        );
    }

    private void refreshPhaseIfDue() {
        if (latencyWindow.size() < config.minEstimationSamples()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean timeElapsed = now - lastRefreshAtMs >= config.refreshIntervalMs();
        boolean enoughSamples = samplesSinceRefresh >= config.refreshSampleInterval();
        if (!timeElapsed && !enoughSamples) {
            return;
        }
        refreshPhase();
        lastRefreshAtMs = now;
        samplesSinceRefresh = 0;
    }

    private void refreshPhase() {
        try {
            double alpha = estimator.estimate(latencyWindow.snapshot());
            lastAlpha = alpha;
            lastPhase = detector.detect(alpha);
        } catch (EstimationException ignored) {
            // Keep the last known estimate until the sample becomes usable again.
        }
    }

    private QueryContext buildContext(QueryRequest request) {
        int hourOfDay = (int) ((request.arrivalTimeMs() / 3_600_000L) % 24L);
        double platformLoad = Math.min(1.0, latencyWindow.size() / (double) latencyWindow.getCapacity());
        return new QueryContext(
                alphaBucket(lastAlpha),
                request.type(),
                request.skewness(),
                hourOfDay,
                platformLoad
        );
    }

    private String alphaBucket(double alpha) {
        if (alpha < 1.8) {
            return "<1.8";
        }
        if (alpha < 2.0) {
            return "1.8-2.0";
        }
        if (alpha < 2.2) {
            return "2.0-2.2";
        }
        if (alpha < 2.5) {
            return "2.2-2.5";
        }
        return ">=2.5";
    }

    public double getLastAlpha() {
        return lastAlpha;
    }

    public Phase getLastPhase() {
        return lastPhase;
    }

    public double getLastEws() {
        return detector.getLastEws();
    }

    public double getCusum() {
        return detector.getCusum();
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public double getAverageLatencyMs() {
        return processedCount == 0 ? 0.0 : totalLatencyMs / processedCount;
    }

    public LatencyWindow getLatencyWindow() {
        return latencyWindow;
    }

    public ThompsonSampler getSampler() {
        return sampler;
    }

    public PhaseTransitionDetector getDetector() {
        return detector;
    }

    public AdaptiveConfig getConfig() {
        return config;
    }

    public void resetStatistics() {
        latencyWindow.clear();
        detector.reset();
        processedCount = 0L;
        totalLatencyMs = 0.0;
        lastAlpha = config.defaultAlpha();
        lastPhase = Phase.UNKNOWN;
        lastRefreshAtMs = 0L;
        samplesSinceRefresh = 0;
    }

    public record PreparedExecution(
            QueryRequest request,
            QueryContext context,
            StrategyType strategy,
            Phase phase
    ) {
    }
}
