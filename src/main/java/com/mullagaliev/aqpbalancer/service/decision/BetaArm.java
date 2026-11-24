package com.mullagaliev.aqpbalancer.service.decision;

import com.mullagaliev.aqpbalancer.util.MathUtils;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.util.Objects;

/**
 * Bernoulli arm modelled by a Beta(a, b) distribution.
 *
 * <p>The sample is obtained through the inverse CDF of the Commons Math
 * {@link BetaDistribution}, driven by an explicit seeded random generator, so
 * the whole simulation remains reproducible.</p>
 */
public class BetaArm {

    private double alpha;
    private double beta;
    private final RandomGenerator random;

    public BetaArm() {
        this(1.0, 1.0, new Well19937c());
    }

    public BetaArm(long seed) {
        this(1.0, 1.0, new Well19937c(seed));
    }

    public BetaArm(RandomGenerator random) {
        this(1.0, 1.0, random);
    }

    public BetaArm(double alpha, double beta, RandomGenerator random) {
        if (!Double.isFinite(alpha) || alpha <= 0.0 || !Double.isFinite(beta) || beta <= 0.0) {
            throw new IllegalArgumentException("Beta parameters must be positive and finite");
        }
        this.alpha = alpha;
        this.beta = beta;
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized double sample() {
        double u = random.nextDouble();
        BetaDistribution distribution = new BetaDistribution(alpha, beta);
        return MathUtils.clamp(distribution.inverseCumulativeProbability(u), 0.0, 1.0);
    }

    public synchronized void update(double reward) {
        double clamped = MathUtils.clamp(reward, 0.0, 1.0);
        alpha += clamped;
        beta += 1.0 - clamped;
    }

    public synchronized double mean() {
        return alpha / (alpha + beta);
    }

    public synchronized double getAlpha() {
        return alpha;
    }

    public synchronized double getBeta() {
        return beta;
    }

    public synchronized double getObservations() {
        return alpha + beta - 2.0;
    }
}
