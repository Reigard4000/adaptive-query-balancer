package com.mullagaliev.aqpbalancer.service.phase;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe sliding window of recent latency observations.
 */
public class LatencyWindow {

    public static final int DEFAULT_CAPACITY = 200;

    private final int capacity;
    private final Deque<Double> values = new ArrayDeque<>();

    public LatencyWindow() {
        this(DEFAULT_CAPACITY);
    }

    public LatencyWindow(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Window capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized void add(double latencyMs) {
        if (!Double.isFinite(latencyMs) || latencyMs < 0.0) {
            return;
        }
        if (values.size() == capacity) {
            values.removeFirst();
        }
        values.addLast(latencyMs);
    }

    public synchronized int size() {
        return values.size();
    }

    public synchronized boolean isReady(int minimumSamples) {
        return values.size() >= minimumSamples;
    }

    public synchronized double[] snapshot() {
        double[] result = new double[values.size()];
        int index = 0;
        for (double value : values) {
            result[index++] = value;
        }
        return result;
    }

    public synchronized double mean() {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    public synchronized void clear() {
        values.clear();
    }

    public int getCapacity() {
        return capacity;
    }
}
