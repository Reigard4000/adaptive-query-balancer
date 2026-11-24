package com.mullagaliev.aqpbalancer.service.quality;

import com.mullagaliev.aqpbalancer.model.QueryRequest;
import com.mullagaliev.aqpbalancer.model.ServerExecutionResult;

import java.util.OptionalDouble;

/**
 * Supplies an accuracy signal for the learning loop.
 *
 * <p>The balancer must work with real backends that do not know anything about
 * the demo dataset. An implementation therefore returns an empty result when
 * the backend does not report enough information to evaluate accuracy.</p>
 */
public interface AccuracyProvider {

    OptionalDouble relativeError(QueryRequest request, ServerExecutionResult execution);
}
