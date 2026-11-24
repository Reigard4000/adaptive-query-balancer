package com.mullagaliev.aqpbalancer.service.phase;

import com.mullagaliev.aqpbalancer.util.MathUtils;
import com.mullagaliev.aqpbalancer.util.StatisticsUtils;

import java.util.Arrays;

/**
 * Early Warning Score (EWS) for a series of alpha estimates.
 *
 * <pre>
 * EWS = 0.4 * trend + 0.3 * rho1 + 0.2 * varianceRatio + 0.1 * crossingFrequency
 * </pre>
 */
public class EwsCalculator {

    private static final double CRITICAL_ALPHA = 2.0;
    private static final double TREND_SCALE = 1.0;
    private static final double VARIANCE_SCALE = 2.0;

    public double calculate(double[] alphaSeries) {
        return components(alphaSeries).score();
    }

    public EwsComponents components(double[] alphaSeries) {
        if (alphaSeries == null || alphaSeries.length < 4) {
            return new EwsComponents(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        int n = alphaSeries.length;
        double slope = StatisticsUtils.trend(alphaSeries);
        double trendScore = MathUtils.clamp(-slope * (n - 1) / TREND_SCALE, 0.0, 1.0);

        double rho1 = MathUtils.clamp(StatisticsUtils.lag1Autocorrelation(alphaSeries), 0.0, 1.0);

        int shortSize = Math.max(2, n / 4);
        double[] shortWindow = Arrays.copyOfRange(alphaSeries, n - shortSize, n);
        double longVariance = StatisticsUtils.variance(alphaSeries);
        double shortVariance = StatisticsUtils.variance(shortWindow);
        double varianceRatio = longVariance <= 1.0e-12 ? 0.0 : shortVariance / longVariance;
        double varianceScore = MathUtils.clamp((varianceRatio - 1.0) / VARIANCE_SCALE, 0.0, 1.0);

        double crossingFrequency = StatisticsUtils.crossingFrequency(alphaSeries, CRITICAL_ALPHA);

        double score = 0.4 * trendScore
                + 0.3 * rho1
                + 0.2 * varianceScore
                + 0.1 * crossingFrequency;

        return new EwsComponents(trendScore, rho1, varianceRatio, crossingFrequency, MathUtils.clamp(score, 0.0, 1.0));
    }

    public record EwsComponents(
            double trend,
            double rho1,
            double varianceRatio,
            double crossingFrequency,
            double score
    ) {
    }
}
