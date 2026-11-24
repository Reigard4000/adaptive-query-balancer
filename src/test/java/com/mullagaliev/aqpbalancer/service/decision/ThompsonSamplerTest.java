package com.mullagaliev.aqpbalancer.service.decision;

import com.mullagaliev.aqpbalancer.model.QueryContext;
import com.mullagaliev.aqpbalancer.model.QueryType;
import com.mullagaliev.aqpbalancer.model.StrategyType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThompsonSamplerTest {

    @Test
    void convergesToBetterArm() {
        QueryContext context = new QueryContext(">=2.5", QueryType.SCAN, 0.5, 12, 0.0);
        Set<StrategyType> allowed = EnumSet.of(StrategyType.EXACT, StrategyType.UNIFORM);
        ThompsonSampler sampler = new ThompsonSampler(7L);

        int exactSelections = 0;
        for (int i = 0; i < 2_000; i++) {
            StrategyType selected = sampler.select(context, allowed);
            if (selected == StrategyType.EXACT) {
                exactSelections++;
            }
            sampler.update(context, selected, selected == StrategyType.EXACT ? 0.9 : 0.1);
        }

        assertTrue(exactSelections > 1_600,
                "Thompson Sampling should prefer the better arm, exactSelections=" + exactSelections);
    }
}
