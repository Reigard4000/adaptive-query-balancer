package com.mullagaliev.aqpbalancer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mullagaliev.aqpbalancer.repository.InMemoryQueryRepository;
import com.mullagaliev.aqpbalancer.service.execution.LocalQueryExecutor;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime backend server with a lightweight, thread-safe load model.
 *
 * <p>HTTP servers send a real POST request to the configured endpoint and parse
 * the analytical result returned by the backend. Simulated servers generate a
 * local result and latency, and are kept for the console demonstration mode.</p>
 */
public class BackendServer {

    private static final double MAX_LATENCY_MS = 60_000.0;
    private static final double DEFAULT_SIMULATED_ALPHA = 2.5;
    private static final int HEALTH_CHECK_TIMEOUT_MS = 2_000;
    private static final long REMOTE_STATS_TTL_MS = 10_000L;
    private static final int CIRCUIT_FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_OPEN_MS = 30_000L;
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final String id;
    private volatile ServerSettings settings;
    private volatile double simulatedAlpha = DEFAULT_SIMULATED_ALPHA;
    private volatile LocalQueryExecutor localExecutor;
    private final Map<QueryType, Double> simulatedExactResults = new ConcurrentHashMap<>();

    private final RandomGenerator random = new Well19937c();

    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong totalLatencyMicros = new AtomicLong();
    private final AtomicLong lastLatencyMicros = new AtomicLong();
    private final AtomicLong latencySamples = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long circuitOpenUntilMs = 0L;

    private volatile int lastStatusCode = 0;
    private volatile String lastError;
    private volatile long lastRequestAtMs = 0L;

    private volatile boolean healthCheckDone = false;
    private volatile boolean healthy = true;
    private volatile long lastHealthCheckAtMs = 0L;

    private volatile int remoteActive = 0;
    private volatile int remoteQueued = 0;
    private volatile int remoteCapacity = 0;
    private volatile int remoteQueueCapacity = 0;
    private volatile int remoteRejected = 0;
    private volatile double remoteUtilization = 0.0;
    private volatile long remoteStatsAtMs = 0L;

    public BackendServer(String id, ServerSettings settings) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Server id must not be blank");
        }
        this.id = id;
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public BackendServer(String id) {
        this(id, ServerSettings.simulated(
                id,
                ServerSettings.DEFAULT_WEIGHT,
                ServerSettings.DEFAULT_BASE_LATENCY_MS,
                ServerSettings.DEFAULT_FAILURE_RATE
        ));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return settings.name();
    }

    public String getDisplayName() {
        String configuredName = settings.name();
        if (configuredName != null && !configuredName.isBlank() && !"server".equalsIgnoreCase(configuredName)) {
            return configuredName;
        }
        String idValue = id == null ? "" : id;
        int dash = idValue.lastIndexOf('-');
        if (dash >= 0 && dash < idValue.length() - 1) {
            String suffix = idValue.substring(dash + 1);
            try {
                return "Сервер №" + Integer.parseInt(suffix);
            } catch (NumberFormatException ignored) {
                // Fall back to the generic id if the suffix is not numeric.
            }
        }
        return "Сервер " + idValue;
    }

    public ServerType getType() {
        return settings.type();
    }

    public ServerSettings getSettings() {
        return settings;
    }

    public String getUrl() {
        return settings.url();
    }

    public int getWeight() {
        return settings.weight();
    }

    public int getCapacity() {
        return settings.capacity();
    }

    public int getEffectiveCapacity() {
        if (isRemoteStatsFresh() && remoteCapacity > 0) {
            return remoteCapacity;
        }
        return Math.max(1, settings.capacity());
    }

    public boolean isRemoteStatsFresh() {
        return remoteStatsAtMs > 0L
                && System.currentTimeMillis() - remoteStatsAtMs <= REMOTE_STATS_TTL_MS;
    }

    public int getActiveLoad() {
        int remote = isRemoteStatsFresh() ? remoteActive : 0;
        return Math.max(activeRequests.get(), remote);
    }

    public int getQueuedLoad() {
        return isRemoteStatsFresh() ? Math.max(0, remoteQueued) : 0;
    }

    public int getRemoteQueueCapacity() {
        return isRemoteStatsFresh() ? remoteQueueCapacity : 0;
    }

    public int getRejectedRequests() {
        return isRemoteStatsFresh() ? Math.max(0, remoteRejected) : 0;
    }

    public double getReportedUtilization() {
        return isRemoteStatsFresh() ? remoteUtilization : 0.0;
    }

    public long getRemoteStatsAtMs() {
        return remoteStatsAtMs;
    }

    public double projectedUtilization() {
        return (getActiveLoad() + getQueuedLoad() + 1.0) / getEffectiveCapacity();
    }

    public void updateRemoteLoad(
            int active,
            int queued,
            int capacity,
            int queueCapacity,
            int rejected,
            double utilization
    ) {
        this.remoteActive = Math.max(0, active);
        this.remoteQueued = Math.max(0, queued);
        this.remoteCapacity = Math.max(0, capacity);
        this.remoteQueueCapacity = Math.max(0, queueCapacity);
        this.remoteRejected = Math.max(0, rejected);
        this.remoteUtilization = Double.isFinite(utilization) ? Math.max(0.0, utilization) : 0.0;
        this.remoteStatsAtMs = System.currentTimeMillis();
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public int getTimeoutMs() {
        return settings.timeoutMs();
    }

    public String getHealthMethod() {
        return settings.healthMethod();
    }

    public Map<String, String> getHeaders() {
        return settings.headers();
    }

    public String getHealthUrl() {
        return settings.resolvedHealthUrl();
    }

    public String getStatsUrl() {
        return settings.resolvedStatsUrl();
    }

    public URI getHealthUri() {
        return URI.create(settings.resolvedHealthUrl());
    }

    public URI getStatsUri() {
        return URI.create(settings.resolvedStatsUrl());
    }

    public double getBaseLatencyMs() {
        return settings.baseLatencyMs();
    }

    public double getFailureRate() {
        return settings.failureRate();
    }

    public double getSimulatedAlpha() {
        return simulatedAlpha;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public boolean isHealthCheckDone() {
        return healthCheckDone;
    }

    public long getLastHealthCheckAtMs() {
        return lastHealthCheckAtMs;
    }

    /**
     * A server is routable when it is enabled and did not fail the latest
     * health check. Servers that have not been checked yet are optimistically
     * considered routable.
     */
    public boolean isRoutable() {
        return settings.enabled() && healthy && !isCircuitOpen();
    }

    public boolean isCircuitOpen() {
        return circuitOpenUntilMs > System.currentTimeMillis();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void applySettings(ServerSettings newSettings) {
        ServerSettings previous = this.settings;
        this.settings = Objects.requireNonNull(newSettings, "newSettings");
        boolean endpointChanged = !Objects.equals(previous.url(), newSettings.url())
                || !Objects.equals(previous.healthUrl(), newSettings.healthUrl())
                || !Objects.equals(previous.statsUrl(), newSettings.statsUrl());
        if (previous.type() != newSettings.type() || endpointChanged) {
            healthCheckDone = false;
            healthy = true;
            lastHealthCheckAtMs = 0L;
            lastStatusCode = 0;
            lastError = null;
            consecutiveFailures.set(0);
            circuitOpenUntilMs = 0L;
        }
    }

    public void setSimulatedAlpha(double alpha) {
        if (Double.isFinite(alpha) && alpha > 0.0) {
            this.simulatedAlpha = Math.max(1.05, Math.min(10.0, alpha));
        }
    }

    public int getQueueSize() {
        return queueSize.get();
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getSuccessCount() {
        return successCount.get();
    }

    public long getFailureCount() {
        return failureCount.get();
    }

    public int getLastStatusCode() {
        return lastStatusCode;
    }

    public String getLastError() {
        return lastError;
    }

    public long getLastRequestAtMs() {
        return lastRequestAtMs;
    }

    public double getAverageLatencyMs() {
        long samples = latencySamples.get();
        return samples == 0 ? 0.0 : totalLatencyMicros.get() / 1000.0 / samples;
    }

    public double getLastLatencyMs() {
        return lastLatencyMicros.get() / 1000.0;
    }

    public int loadScore() {
        return queueSize.get() + activeRequests.get();
    }

    public synchronized void resetForRun(long seed, double alpha) {
        resetStatistics();
        random.setSeed(seed);
        if (settings.type() == ServerType.SIMULATED) {
            setSimulatedAlpha(alpha);
        }
    }

    public void resetStatistics() {
        queueSize.set(0);
        activeRequests.set(0);
        totalRequests.set(0);
        successCount.set(0);
        failureCount.set(0);
        totalLatencyMicros.set(0);
        lastLatencyMicros.set(0);
        latencySamples.set(0);
        lastStatusCode = 0;
        lastError = null;
        lastRequestAtMs = 0L;
        consecutiveFailures.set(0);
        circuitOpenUntilMs = 0L;
        simulatedExactResults.clear();
    }

    public void onRequestDispatched() {
        queueSize.incrementAndGet();
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void recordCompletion(ServerExecutionResult result) {
        queueSize.updateAndGet(value -> Math.max(0, value - 1));
        activeRequests.updateAndGet(value -> Math.max(0, value - 1));
        lastRequestAtMs = System.currentTimeMillis();

        if (result == null) {
            failureCount.incrementAndGet();
            if (consecutiveFailures.incrementAndGet() >= CIRCUIT_FAILURE_THRESHOLD) {
                circuitOpenUntilMs = System.currentTimeMillis() + CIRCUIT_OPEN_MS;
            }
            lastStatusCode = 0;
            lastError = "Ошибка выполнения";
            return;
        }

        if (result.success()) {
            successCount.incrementAndGet();
            consecutiveFailures.set(0);
            circuitOpenUntilMs = 0L;
        } else {
            failureCount.incrementAndGet();
            if (consecutiveFailures.incrementAndGet() >= CIRCUIT_FAILURE_THRESHOLD) {
                circuitOpenUntilMs = System.currentTimeMillis() + CIRCUIT_OPEN_MS;
            }
        }
        lastStatusCode = result.statusCode();
        lastError = result.error();

        double latencyMs = result.latencyMs();
        if (Double.isFinite(latencyMs) && latencyMs >= 0.0) {
            long latencyMicros = Math.round(latencyMs * 1000.0);
            totalLatencyMicros.addAndGet(latencyMicros);
            lastLatencyMicros.set(latencyMicros);
            latencySamples.incrementAndGet();
        }
    }

    public ServerExecutionResult execute(QueryRequest request, Phase phase, StrategyType strategy) {
        ServerSettings current = settings;
        return switch (current.type()) {
            case SIMULATED -> executeSimulated(request, phase, strategy, current);
            case HTTP -> executeHttp(request, phase, strategy, current);
        };
    }

    public ServerHealthResult checkHealth() {
        return checkHealth(HEALTH_CHECK_TIMEOUT_MS);
    }

    public ServerHealthResult checkHealth(int maxTimeoutMs) {
        ServerSettings current = settings;
        if (current.type() == ServerType.SIMULATED) {
            healthCheckDone = true;
            healthy = true;
            lastHealthCheckAtMs = System.currentTimeMillis();
            lastStatusCode = 200;
            lastError = null;
            return new ServerHealthResult(true, 200, 0.0, "Симулируемый сервер доступен");
        }

        long startedNanos = System.nanoTime();
        try {
            int timeout = Math.min(
                    Math.min(Math.max(100, current.timeoutMs()), Math.max(100, maxTimeoutMs)),
                    10_000
            );
            HttpResponse<Void> response = sendHealth(current, current.healthMethod(), timeout);
            if ("GET".equals(current.healthMethod())
                    && (response.statusCode() == 405 || response.statusCode() == 501)) {
                response = sendHealth(current, "HEAD", timeout);
            }
            double latencyMs = elapsedMs(startedNanos);
            int statusCode = response.statusCode();
            boolean reachable = statusCode >= 200 && statusCode < 300;
            lastStatusCode = statusCode;
            lastError = reachable ? null : "HTTP " + statusCode;
            healthCheckDone = true;
            healthy = reachable;
            lastHealthCheckAtMs = System.currentTimeMillis();
            return new ServerHealthResult(reachable, statusCode, latencyMs, "HTTP " + statusCode);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            lastStatusCode = 0;
            lastError = "Проверка прервана";
            healthCheckDone = true;
            healthy = false;
            lastHealthCheckAtMs = System.currentTimeMillis();
            return new ServerHealthResult(false, 0, elapsedMs(startedNanos), lastError);
        } catch (Exception exception) {
            lastStatusCode = 0;
            lastError = errorMessage(exception);
            healthCheckDone = true;
            healthy = false;
            lastHealthCheckAtMs = System.currentTimeMillis();
            return new ServerHealthResult(false, 0, elapsedMs(startedNanos), lastError);
        }
    }

    private ServerExecutionResult executeSimulated(
            QueryRequest request,
            Phase phase,
            StrategyType strategy,
            ServerSettings current
    ) {
        double randomValue;
        double failureRoll;
        double alpha;
        synchronized (random) {
            randomValue = random.nextDouble();
            failureRoll = random.nextDouble();
            alpha = Math.max(1.05, Math.min(10.0, simulatedAlpha));
        }

        double scale = current.baseLatencyMs();
        double uniform = Math.min(0.999999999, Math.max(1.0e-9, randomValue));
        double base = scale / Math.pow(1.0 - uniform, 1.0 / alpha);

        double strategyFactor = switch (strategy) {
            case EXACT -> 1.0;
            case UNIFORM -> 0.22;
            case STRATIFIED -> 0.30;
        };
        double phaseFactor = switch (phase) {
            case LIGHT_TAIL -> 1.0;
            case WARNING -> 1.15;
            case HEAVY_TAIL -> 1.35;
            case UNKNOWN -> 1.0;
        };
        double rowFactor = 0.8 + 0.2 * Math.min(
                1.2,
                Math.log10(Math.max(10L, request.estimatedRows())) / 5.0
        );

        double latencyMs = clamp(base * strategyFactor * phaseFactor * rowFactor);
        boolean success = failureRoll >= current.failureRate();

        if (!success) {
            return ServerExecutionResult.failure(latencyMs, 503, "Смоделированный сбой");
        }

        try {
            LocalQueryExecutor executor = ensureLocalExecutor();
            LocalQueryExecutor.Outcome outcome = executor.execute(request, strategy);
            double exactResult = simulatedExactResults.computeIfAbsent(
                    request.type(),
                    type -> executor.exactResult(request)
            );
            double relativeError = relativeError(outcome.result(), exactResult);
            return ServerExecutionResult.success(
                    latencyMs,
                    200,
                    outcome.result(),
                    outcome.sampleSize(),
                    outcome.totalRows(),
                    outcome.approximate(),
                    exactResult,
                    relativeError
            );
        } catch (RuntimeException exception) {
            return ServerExecutionResult.failure(latencyMs, 500, errorMessage(exception));
        }
    }

    private ServerExecutionResult executeHttp(
            QueryRequest request,
            Phase phase,
            StrategyType strategy,
            ServerSettings current
    ) {
        long startedNanos = System.nanoTime();
        try {
            String body = buildRequestBody(request, phase, strategy, current);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(current.url()))
                    .timeout(Duration.ofMillis(current.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            applyHeaders(requestBuilder, current.headers());
            HttpRequest httpRequest = requestBuilder.build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            double latencyMs = elapsedMs(startedNanos);
            int statusCode = response.statusCode();
            RemoteExecutionResponse parsed = parseResponse(response.body());

            if (statusCode >= 200 && statusCode < 300) {
                if (parsed != null && "OK".equalsIgnoreCase(parsed.status()) && parsed.result() != null) {
                    return ServerExecutionResult.success(
                            latencyMs,
                            statusCode,
                            parsed.result(),
                            parsed.sampleSize() == null ? 0 : parsed.sampleSize(),
                            parsed.totalRows() == null ? 0L : parsed.totalRows(),
                            Boolean.TRUE.equals(parsed.approximate()),
                            parsed.exactResult(),
                            parsed.relativeError()
                    );
                }
                String error = parsed != null && parsed.error() != null
                        ? parsed.error()
                        : "Бекенд не вернул результат";
                return ServerExecutionResult.failure(latencyMs, statusCode, error);
            }

            String error = parsed != null && parsed.error() != null
                    ? parsed.error()
                    : "HTTP " + statusCode;
            return ServerExecutionResult.failure(latencyMs, statusCode, error);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ServerExecutionResult.failure(elapsedMs(startedNanos), 0, "Запрос прерван");
        } catch (Exception exception) {
            return ServerExecutionResult.failure(elapsedMs(startedNanos), 0, errorMessage(exception));
        }
    }

    private LocalQueryExecutor ensureLocalExecutor() {
        LocalQueryExecutor executor = localExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = localExecutor;
                if (executor == null) {
                    executor = new LocalQueryExecutor(new InMemoryQueryRepository());
                    localExecutor = executor;
                }
            }
        }
        return executor;
    }

    private HttpResponse<Void> sendHealth(ServerSettings current, String method, int timeout)
            throws java.io.IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(current.resolvedHealthUrl()))
                .timeout(Duration.ofMillis(timeout))
                .method(method, HttpRequest.BodyPublishers.noBody());
        applyHeaders(builder, current.headers());
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null) {
                return;
            }
            String resolved = resolveHeaderValue(value);
            if (!resolved.isBlank()) {
                builder.setHeader(name, resolved);
            }
        });
    }

    private String resolveHeaderValue(String value) {
        if (value.startsWith("env:")) {
            String envName = value.substring(4).trim();
            String envValue = envName.isEmpty() ? null : System.getenv(envName);
            return envValue == null ? "" : envValue;
        }
        return value;
    }

    private double relativeError(double approximate, double exact) {
        double denominator = Math.max(1.0, Math.abs(exact));
        double error = Math.abs(approximate - exact) / denominator;
        return Math.max(0.0, Math.min(1.0, Double.isFinite(error) ? error : 1.0));
    }

    private RemoteExecutionResponse parseResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(body, RemoteExecutionResponse.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String buildRequestBody(
            QueryRequest request,
            Phase phase,
            StrategyType strategy,
            ServerSettings current
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", request.id());
        payload.put("sql", request.sql());
        payload.put("estimatedRows", request.estimatedRows());
        payload.put("type", request.type().name());
        payload.put("skewness", request.skewness());
        payload.put("arrivalTimeMs", request.arrivalTimeMs());
        payload.put("strategy", strategy.name());
        payload.put("phase", phase.name());
        payload.put("serverId", id);
        payload.put("timeoutMs", current.timeoutMs());
        payload.put("clientId", request.clientId() == null || request.clientId().isBlank()
                ? "aqp-balancer"
                : request.clientId());

        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось сериализовать запрос", exception);
        }
    }

    private double clamp(double latencyMs) {
        return Math.max(1.0, Math.min(MAX_LATENCY_MS, latencyMs));
    }

    private double elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000.0;
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteExecutionResponse(
            String queryId,
            String serverId,
            String serverName,
            String status,
            Double result,
            Integer sampleSize,
            Long totalRows,
            String strategy,
            Double executionTimeMs,
            Boolean approximate,
            Double exactResult,
            Double relativeError,
            String error
    ) {
    }
}
