package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.exception.EstimationException;
import com.mullagaliev.aqpbalancer.util.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Robust ensemble: the median of Hill, Pickands and Moments estimates.
 */
public class EnsembleTailIndexEstimator implements TailIndexEstimator {

    private final TailIndexEstimator[] estimators;

    public EnsembleTailIndexEstimator() {
        this(new HillEstimator(), new PickandsEstimator(), new MomentsEstimator());
    }

    public EnsembleTailIndexEstimator(TailIndexEstimator... estimators) {
        if (estimators == null || estimators.length == 0) {
            throw new IllegalArgumentException("At least one estimator is required");
        }
        this.estimators = estimators.clone();
    }

    @Override
    public double estimate(double[] values) {
        List<Double> valid = new ArrayList<>(estimators.length);
        for (TailIndexEstimator estimator : estimators) {
            try {
                double value = estimator.estimate(values);
                if (Double.isFinite(value) && value > 0.0) {
                    valid.add(value);
                }
            } catch (EstimationException ignored) {
                // A single estimator may be undefined for a given sample; the ensemble stays robust.
            }
        }
        if (valid.isEmpty()) {
            throw new EstimationException("All tail index estimators failed for the current sample");
        }
        if (valid.size() < estimators.length) {
            return valid.get(0);
        }
        double[] estimates = valid.stream().mapToDouble(Double::doubleValue).toArray();
        return MathUtils.median(estimates);
    }
}
