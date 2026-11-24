package com.mullagaliev.aqpbalancer.service.learning;

import com.mullagaliev.aqpbalancer.model.ExecutionSample;
import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import com.mullagaliev.aqpbalancer.service.decision.ThompsonSampler;

import java.util.Objects;

/**
 * Closes the learning loop: converts an execution sample into a reward and
 * updates the corresponding Thompson Sampling arm.
 */
public class BanditUpdater {

    private final ThompsonSampler sampler;
    private final RewardCalculator rewardCalculator;

    public BanditUpdater(ThompsonSampler sampler, RewardCalculator rewardCalculator) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.rewardCalculator = Objects.requireNonNull(rewardCalculator, "rewardCalculator");
    }

    /**
     * @return the reward that was applied to the arm (0 for failed executions)
     */
    public double onExecutionFinished(QueryContext context, StrategyType strategy, ExecutionSample sample) {
        if (sample == null || !sample.success()) {
            return 0.0;
        }
        double reward = rewardCalculator.compute(sample.relativeError(), sample.latencyMs());
        sampler.update(context, strategy, reward);
        return reward;
    }
}
