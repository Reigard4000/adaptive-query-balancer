package com.mullagaliev.aqpbalancer.repository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryQueryRepositoryTest {

    private final InMemoryQueryRepository repository = new InMemoryQueryRepository(42L);

    @Test
    void fetchSampleReturnsRequestedNumberOfRows() {
        List<Double> sample = repository.fetchSample(100);

        assertEquals(100, sample.size());
    }

    @Test
    void fetchAllReturnsNonEmptyDataSet() {
        assertFalse(repository.fetchAll().isEmpty());
    }

    @Test
    void fetchStratifiedReturnsRequestedTotalSize() {
        Map<String, List<Double>> groups = repository.fetchStratified(300);
        int total = groups.values().stream().mapToInt(List::size).sum();

        assertEquals(300, total);
        assertTrue(groups.size() > 1);
    }

    @Test
    void sampleIsReproducibleForSameSeed() {
        InMemoryQueryRepository other = new InMemoryQueryRepository(42L);

        assertEquals(repository.fetchSample(50), other.fetchSample(50));
    }
}
