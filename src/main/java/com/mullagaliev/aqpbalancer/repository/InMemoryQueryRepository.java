package com.mullagaliev.aqpbalancer.repository;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Demo-only in-memory dataset.
 *
 * <p>When {@code data/sample_queries.csv} is present on the classpath it is
 * loaded; otherwise a deterministic synthetic dataset is generated. The real
 * balancer runtime never uses this repository.</p>
 */
public class InMemoryQueryRepository implements QueryRepository {

    private static final String SAMPLE_RESOURCE = "/data/sample_queries.csv";

    private final List<Double> values = new ArrayList<>();
    private final Map<String, List<Double>> valuesByStratum = new TreeMap<>();
    private final Random random;

    public InMemoryQueryRepository() {
        this(42L);
    }

    public InMemoryQueryRepository(long seed) {
        this.random = new Random(seed);
        load();
        if (values.isEmpty()) {
            loadFallbackData();
        }
    }

    @Override
    public List<Double> fetchAll() {
        return List.copyOf(values);
    }

    @Override
    public List<Double> fetchSample(int sampleSize) {
        return sample(values, sampleSize);
    }

    @Override
    public Map<String, List<Double>> fetchStratified(int sampleSize) {
        if (sampleSize <= 0 || values.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> allocations = allocate(sampleSize);
        Map<String, List<Double>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : allocations.entrySet()) {
            int count = entry.getValue();
            if (count <= 0) {
                continue;
            }
            List<Double> stratumValues = valuesByStratum.get(entry.getKey());
            if (stratumValues != null && !stratumValues.isEmpty()) {
                result.put(entry.getKey(), sample(stratumValues, count));
            }
        }
        return result;
    }

    private List<Double> sample(List<Double> source, int sampleSize) {
        if (sampleSize <= 0 || source.isEmpty()) {
            return List.of();
        }
        if (sampleSize <= source.size()) {
            List<Double> copy = new ArrayList<>(source);
            Collections.shuffle(copy, random);
            return List.copyOf(copy.subList(0, sampleSize));
        }

        List<Double> result = new ArrayList<>(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            result.add(source.get(random.nextInt(source.size())));
        }
        return List.copyOf(result);
    }

    private Map<String, Integer> allocate(int sampleSize) {
        int total = values.size();
        Map<String, Integer> allocations = new LinkedHashMap<>();
        Map<String, Double> remainders = new LinkedHashMap<>();
        int assigned = 0;

        for (Map.Entry<String, List<Double>> entry : valuesByStratum.entrySet()) {
            double exact = sampleSize * (double) entry.getValue().size() / total;
            int base = (int) Math.floor(exact);
            allocations.put(entry.getKey(), base);
            remainders.put(entry.getKey(), exact - base);
            assigned += base;
        }

        List<String> byRemainder = new ArrayList<>(remainders.keySet());
        byRemainder.sort(Comparator.comparingDouble((String key) -> remainders.get(key)).reversed());

        int index = 0;
        while (assigned < sampleSize && !byRemainder.isEmpty()) {
            String key = byRemainder.get(index % byRemainder.size());
            allocations.merge(key, 1, Integer::sum);
            assigned++;
            index++;
        }
        return allocations;
    }

    private void load() {
        try (InputStream input = getClass().getResourceAsStream(SAMPLE_RESOURCE)) {
            if (input == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                 CSVParser parser = CSVFormat.DEFAULT.builder()
                         .setHeader()
                         .setSkipHeaderRecord(true)
                         .setTrim(true)
                         .build()
                         .parse(reader)) {
                for (CSVRecord record : parser) {
                    String rawValue = record.isMapped("value") ? record.get("value") : record.get(1);
                    if (rawValue == null || rawValue.isBlank()) {
                        continue;
                    }
                    double value;
                    try {
                        value = Double.parseDouble(rawValue);
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (!Double.isFinite(value) || value <= 0.0) {
                        continue;
                    }
                    String stratum = record.isMapped("stratum") ? record.get("stratum") : "default";
                    if (stratum == null || stratum.isBlank()) {
                        stratum = "default";
                    }
                    values.add(value);
                    valuesByStratum.computeIfAbsent(stratum, key -> new ArrayList<>()).add(value);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + SAMPLE_RESOURCE, ex);
        }
    }

    private void loadFallbackData() {
        Random fallbackRandom = new Random(2026L);
        String[] strata = {"low", "mid", "high"};
        for (int i = 0; i < 5_000; i++) {
            double value = 10.0 * Math.pow(1.0 - fallbackRandom.nextDouble(), -1.0 / 1.08);
            double capped = Math.min(value, 1_000_000.0);
            values.add(capped);
            String stratum = capped < 20.0 ? strata[0] : (capped < 100.0 ? strata[1] : strata[2]);
            valuesByStratum.computeIfAbsent(stratum, key -> new ArrayList<>()).add(capped);
        }
    }
}
