package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.exception.EstimationException;

public interface TailIndexEstimator {

    /**
     * Estimates the tail index alpha of the supplied sample.
     *
     * @param values latency sample (unsorted)
     * @return estimated alpha
     * @throws EstimationException when there is not enough data or the estimate is not finite
     */
    double estimate(double[] values);
}
