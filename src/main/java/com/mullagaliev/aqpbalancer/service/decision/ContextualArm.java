package com.mullagaliev.aqpbalancer.service.decision;

import com.mullagaliev.aqpbalancer.model.StrategyType;

import java.util.Objects;

/**
 * One strategy arm together with its Beta posterior.
 */
public class ContextualArm {

    private final StrategyType strategyType;
    private final BetaArm betaArm;

    public ContextualArm(StrategyType strategyType, BetaArm betaArm) {
        this.strategyType = Objects.requireNonNull(strategyType, "strategyType");
        this.betaArm = Objects.requireNonNull(betaArm, "betaArm");
    }

    public StrategyType getStrategyType() {
        return strategyType;
    }

    public double sample() {
        return betaArm.sample();
    }

    public void update(double reward) {
        betaArm.update(reward);
    }

    public BetaArm getBetaArm() {
        return betaArm;
    }
}
