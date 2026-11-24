package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.exception.EstimationException;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HillEstimatorTest {

    @Test
    void estimatesParetoTailIndex() {
        double trueAlpha = 1.8;
        Random random = new Random(123L);
        double[] values = new double[5_000];
        for (int i = 0; i < values.length; i++) {
            values[i] = Math.pow(1.0 - random.nextDouble(), -1.0 / trueAlpha);
        }

        double estimate = new HillEstimator().estimate(values);

        assertEquals(trueAlpha, estimate, 0.30, "Hill estimate should be close to the true alpha");
    }

    @Test
    void rejectsTooSmallSample() {
        assertThrows(EstimationException.class, () -> new HillEstimator().estimate(new double[]{1.0, 2.0, 3.0}));
    }

    @Test
    void ensembleReturnsFinitePositiveValue() {
        Random random = new Random(7L);
        double[] values = new double[1_000];
        for (int i = 0; i < values.length; i++) {
            values[i] = 50.0 * Math.pow(1.0 - random.nextDouble(), -1.0 / 2.2);
        }

        double estimate = new EnsembleTailIndexEstimator().estimate(values);

        assertTrue(Double.isFinite(estimate));
        assertTrue(estimate > 1.0);
    }
}
