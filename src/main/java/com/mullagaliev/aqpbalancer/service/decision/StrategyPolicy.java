package com.mullagaliev.aqpbalancer.service.decision;

import com.mullagaliev.aqpbalancer.model.Phase;
import com.mullagaliev.aqpbalancer.model.StrategyType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Maps the detected phase and alpha estimate to a safe set of strategies.
 */
public class StrategyPolicy {

    private static final double LIGHT_TAIL_ALPHA = 2.2;
    private static final double WARNING_ALPHA = 2.0;

    public Set<StrategyType> allowedFor(Phase phase, double alpha) {
        EnumSet<StrategyType> allowed = EnumSet.noneOf(StrategyType.class);

        if (phase == null || phase == Phase.UNKNOWN) {
            allowed.add(StrategyType.EXACT);
            allowed.add(StrategyType.UNIFORM);
            allowed.add(StrategyType.STRATIFIED);
            return Collections.unmodifiableSet(allowed);
        }

        if (phase == Phase.HEAVY_TAIL || alpha <= WARNING_ALPHA) {
            allowed.add(StrategyType.UNIFORM);
            allowed.add(StrategyType.STRATIFIED);
            return Collections.unmodifiableSet(allowed);
        }

        if (phase == Phase.WARNING || alpha <= LIGHT_TAIL_ALPHA) {
            allowed.add(StrategyType.UNIFORM);
            allowed.add(StrategyType.STRATIFIED);
            return Collections.unmodifiableSet(allowed);
        }

        allowed.add(StrategyType.EXACT);
        allowed.add(StrategyType.UNIFORM);
        return Collections.unmodifiableSet(allowed);
    }
}
