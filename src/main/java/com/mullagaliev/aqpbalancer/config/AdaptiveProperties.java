package com.mullagaliev.aqpbalancer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Spring binding for {@link AdaptiveConfig}. Keeping a mutable JavaBean here
 * lets Spring Boot bind defaults and environment variables without coupling
 * the core algorithm to Spring.
 */
@Component
@ConfigurationProperties(prefix = "balancer.adaptive")
public class AdaptiveProperties {

    private int latencyWindowSize = 1_000;
    private int minEstimationSamples = 20;
    private long refreshIntervalMs = 1_000L;
    private int refreshSampleInterval = 100;
    private double defaultAlpha = 2.5;
    private int detectorMinSamples = 8;
    private int detectorMaxHistory = 120;
    private double cusumBaseline = 2.2;
    private double cusumDrift = 0.05;
    private double cusumThreshold = 1.0;
    private double rewardAccuracyWeight = 0.7;
    private double rewardLatencyWeight = 0.3;
    private double rewardLatencyScaleMs = 10_000.0;

    public AdaptiveConfig toConfig() {
        return new AdaptiveConfig(
                latencyWindowSize,
                minEstimationSamples,
                refreshIntervalMs,
                refreshSampleInterval,
                defaultAlpha,
                detectorMinSamples,
                detectorMaxHistory,
                cusumBaseline,
                cusumDrift,
                cusumThreshold,
                rewardAccuracyWeight,
                rewardLatencyWeight,
                rewardLatencyScaleMs
        );
    }

    public int getLatencyWindowSize() {
        return latencyWindowSize;
    }

    public void setLatencyWindowSize(int latencyWindowSize) {
        this.latencyWindowSize = latencyWindowSize;
    }

    public int getMinEstimationSamples() {
        return minEstimationSamples;
    }

    public void setMinEstimationSamples(int minEstimationSamples) {
        this.minEstimationSamples = minEstimationSamples;
    }

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public void setRefreshIntervalMs(long refreshIntervalMs) {
        this.refreshIntervalMs = refreshIntervalMs;
    }

    public int getRefreshSampleInterval() {
        return refreshSampleInterval;
    }

    public void setRefreshSampleInterval(int refreshSampleInterval) {
        this.refreshSampleInterval = refreshSampleInterval;
    }

    public double getDefaultAlpha() {
        return defaultAlpha;
    }

    public void setDefaultAlpha(double defaultAlpha) {
        this.defaultAlpha = defaultAlpha;
    }

    public int getDetectorMinSamples() {
        return detectorMinSamples;
    }

    public void setDetectorMinSamples(int detectorMinSamples) {
        this.detectorMinSamples = detectorMinSamples;
    }

    public int getDetectorMaxHistory() {
        return detectorMaxHistory;
    }

    public void setDetectorMaxHistory(int detectorMaxHistory) {
        this.detectorMaxHistory = detectorMaxHistory;
    }

    public double getCusumBaseline() {
        return cusumBaseline;
    }

    public void setCusumBaseline(double cusumBaseline) {
        this.cusumBaseline = cusumBaseline;
    }

    public double getCusumDrift() {
        return cusumDrift;
    }

    public void setCusumDrift(double cusumDrift) {
        this.cusumDrift = cusumDrift;
    }

    public double getCusumThreshold() {
        return cusumThreshold;
    }

    public void setCusumThreshold(double cusumThreshold) {
        this.cusumThreshold = cusumThreshold;
    }

    public double getRewardAccuracyWeight() {
        return rewardAccuracyWeight;
    }

    public void setRewardAccuracyWeight(double rewardAccuracyWeight) {
        this.rewardAccuracyWeight = rewardAccuracyWeight;
    }

    public double getRewardLatencyWeight() {
        return rewardLatencyWeight;
    }

    public void setRewardLatencyWeight(double rewardLatencyWeight) {
        this.rewardLatencyWeight = rewardLatencyWeight;
    }

    public double getRewardLatencyScaleMs() {
        return rewardLatencyScaleMs;
    }

    public void setRewardLatencyScaleMs(double rewardLatencyScaleMs) {
        this.rewardLatencyScaleMs = rewardLatencyScaleMs;
    }
}
