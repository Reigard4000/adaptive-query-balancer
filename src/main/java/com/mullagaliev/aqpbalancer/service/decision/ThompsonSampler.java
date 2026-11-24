package com.mullagaliev.aqpbalancer.service.decision;

import com.mullagaliev.aqpbalancer.exception.BalancerException;
import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contextual Thompson Sampling over a set of allowed sampling strategies.
 */
public class ThompsonSampler {

    private final Map<String, Map<StrategyType, ContextualArm>> armsByContext = new HashMap<>();
    private final RandomGenerator random;

    public ThompsonSampler() {
        this(new Well19937c());
    }

    public ThompsonSampler(long seed) {
        this(new Well19937c(seed));
    }

    public ThompsonSampler(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized StrategyType select(QueryContext context, Set<StrategyType> allowedStrategies) {
        Objects.requireNonNull(context, "context");
        if (allowedStrategies == null || allowedStrategies.isEmpty()) {
            throw new BalancerException("No strategies are allowed for context " + context.key());
        }

        Map<StrategyType, ContextualArm> arms = armsFor(context);
        StrategyType bestStrategy = null;
        double bestSample = Double.NEGATIVE_INFINITY;

        // Iterate over the enum declaration order for deterministic tie-breaking.
        for (StrategyType strategy : StrategyType.values()) {
            if (!allowedStrategies.contains(strategy)) {
                continue;
            }
            ContextualArm arm = arms.computeIfAbsent(strategy, type -> new ContextualArm(type, new BetaArm(random)));
            double sample = arm.sample();
            if (sample > bestSample) {
                bestSample = sample;
                bestStrategy = strategy;
            }
        }

        if (bestStrategy == null) {
            throw new BalancerException("No strategy could be selected for context " + context.key());
        }
        return bestStrategy;
    }

    public synchronized void update(QueryContext context, StrategyType strategy, double reward) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(strategy, "strategy");
        ContextualArm arm = armsFor(context)
                .computeIfAbsent(strategy, type -> new ContextualArm(type, new BetaArm(random)));
        arm.update(reward);
    }

    public synchronized ContextualArm getArm(QueryContext context, StrategyType strategy) {
        return armsFor(context).get(strategy);
    }

    public synchronized Map<String, Map<StrategyType, ContextualArm>> snapshot() {
        Map<String, Map<StrategyType, ContextualArm>> copy = new HashMap<>();
        armsByContext.forEach((key, value) -> copy.put(key, new EnumMap<>(value)));
        return copy;
    }

    public synchronized void reset() {
        armsByContext.clear();
    }

    private Map<StrategyType, ContextualArm> armsFor(QueryContext context) {
        return armsByContext.computeIfAbsent(context.key(), key -> new EnumMap<>(StrategyType.class));
    }
}
