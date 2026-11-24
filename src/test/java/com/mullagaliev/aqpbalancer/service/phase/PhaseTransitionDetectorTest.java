package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.model.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhaseTransitionDetectorTest {

    @Test
    void lightTailSeriesIsClassifiedAsLightTail() {
        PhaseTransitionDetector detector = new PhaseTransitionDetector();
        Phase phase = Phase.UNKNOWN;
        for (int i = 0; i < 50; i++) {
            phase = detector.detect(2.5);
        }
        assertEquals(Phase.LIGHT_TAIL, phase);
    }

    @Test
    void heavyTailSeriesIsClassifiedAsHeavyTail() {
        PhaseTransitionDetector detector = new PhaseTransitionDetector();
        Phase phase = Phase.UNKNOWN;
        for (int i = 0; i < 50; i++) {
            phase = detector.detect(1.8);
        }
        assertEquals(Phase.HEAVY_TAIL, phase);
    }

    @Test
    void valuesNearCriticalBoundaryAreWarning() {
        PhaseTransitionDetector detector = new PhaseTransitionDetector();
        Phase phase = Phase.UNKNOWN;
        for (int i = 0; i < 50; i++) {
            phase = detector.detect(2.1);
        }
        assertEquals(Phase.WARNING, phase);
    }

    @Test
    void tooFewSamplesProduceUnknown() {
        PhaseTransitionDetector detector = new PhaseTransitionDetector(10);
        for (int i = 0; i < 5; i++) {
            detector.detect(2.5);
        }
        assertEquals(Phase.UNKNOWN, detector.getPhase());
    }
}
