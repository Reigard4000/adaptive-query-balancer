package com.mullagaliev.aqpbalancer.service.learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardCalculatorTest {

    private final RewardCalculator calculator = new RewardCalculator();

    @Test
    void exactAndFastBeatsInexactAndSlow() {
        double exactFast = calculator.compute(0.0, 100.0);
        double inexactSlow = calculator.compute(0.8, 9_000.0);

        assertTrue(exactFast > inexactSlow,
                "exactFast=" + exactFast + " should exceed inexactSlow=" + inexactSlow);
    }

    @Test
    void exactZeroLatencyProducedMaximumReward() {
        assertEquals(0.7, calculator.compute(0.0, 0.0), 1.0e-9);
    }

    @Test
    void rewardIsAlwaysClampedToUnitInterval() {
        double low = calculator.compute(5.0, 60_000.0);
        double high = calculator.compute(-5.0, -100.0);

        assertTrue(low >= 0.0 && low <= 1.0);
        assertTrue(high >= 0.0 && high <= 1.0);
    }
}
