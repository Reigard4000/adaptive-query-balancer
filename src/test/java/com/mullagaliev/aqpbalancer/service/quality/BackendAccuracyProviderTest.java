package com.mullagaliev.aqpbalancer.service.quality;

import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.QueryType;
import com.mullagaliev.aqpbalancer.model.ServerExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendAccuracyProviderTest {

    private final BackendAccuracyProvider provider = new BackendAccuracyProvider();
    private final QueryRequest request = new QueryRequest(
            "q-1", "SELECT avg(v) FROM t", 1_000, QueryType.AGGREGATE, 0.5, 0L
    );

    @Test
    void usesExplicitRelativeError() {
        ServerExecutionResult result = ServerExecutionResult.success(
                10.0, 200, 42.0, 100, 1_000, true, null, 0.25
        );

        OptionalDouble accuracy = provider.relativeError(request, result);

        assertTrue(accuracy.isPresent());
        assertEquals(0.25, accuracy.getAsDouble(), 1.0e-9);
    }

    @Test
    void calculatesErrorFromExactResult() {
        ServerExecutionResult result = ServerExecutionResult.success(
                10.0, 200, 80.0, 100, 1_000, true, 100.0, null
        );

        OptionalDouble accuracy = provider.relativeError(request, result);

        assertTrue(accuracy.isPresent());
        assertEquals(0.2, accuracy.getAsDouble(), 1.0e-9);
    }

    @Test
    void exactResultIsFullyAccurate() {
        ServerExecutionResult result = ServerExecutionResult.success(
                10.0, 200, 42.0, 1_000, 1_000, false
        );

        OptionalDouble accuracy = provider.relativeError(request, result);

        assertTrue(accuracy.isPresent());
        assertEquals(0.0, accuracy.getAsDouble(), 1.0e-9);
    }

    @Test
    void approximateResultWithoutSignalHasNoAccuracy() {
        ServerExecutionResult result = ServerExecutionResult.success(
                10.0, 200, 42.0, 100, 1_000, true
        );

        assertTrue(provider.relativeError(request, result).isEmpty());
    }

    @Test
    void failureHasNoAccuracy() {
        ServerExecutionResult result = ServerExecutionResult.failure(10.0, 503, "boom");

        assertTrue(provider.relativeError(request, result).isEmpty());
    }
}
