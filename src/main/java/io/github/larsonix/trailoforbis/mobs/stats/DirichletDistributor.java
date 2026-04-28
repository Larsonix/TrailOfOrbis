package io.github.larsonix.trailoforbis.mobs.stats;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

public class DirichletDistributor {

    /** Minimum alpha value to prevent division by zero in gamma sampling */
    private static final double MIN_ALPHA = 0.001;

    public Map<MobStatType, Double> distribute(
            double totalPool,
            MobStatPoolConfig config,
            Random random
    ) {
        MobStatType[] statTypes = MobStatType.values();
        int statCount = statTypes.length;

        double[] gammaSamples = new double[statCount];
        double gammaSum = 0.0;

        for (int i = 0; i < statCount; i++) {
            MobStatType stat = statTypes[i];
            double alpha = Math.max(MIN_ALPHA, config.getAlphaWeight(stat));
            gammaSamples[i] = sampleGamma(alpha, 1.0, random);
            gammaSum += gammaSamples[i];
        }

        // Bug fix: Handle edge case where gammaSum is zero, NaN, or negative
        double[] proportions = new double[statCount];
        if (gammaSum <= 0 || Double.isNaN(gammaSum) || Double.isInfinite(gammaSum)) {
            // Fall back to uniform distribution
            double uniform = 1.0 / statCount;
            Arrays.fill(proportions, uniform);
        } else {
            for (int i = 0; i < statCount; i++) {
                proportions[i] = gammaSamples[i] / gammaSum;
            }
        }

        proportions = applyStatWeights(proportions, config, random);

        Map<MobStatType, Double> shares = new EnumMap<>(MobStatType.class);
        for (int i = 0; i < statCount; i++) {
            MobStatType stat = statTypes[i];
            shares.put(stat, totalPool * proportions[i]);
        }

        return shares;
    }

    private double[] applyStatWeights(double[] proportions, MobStatPoolConfig config, Random random) {
        String[] mobTypes = {"warrior", "ranger", "mage", "tank", "assassin"};
        String selectedType = mobTypes[random.nextInt(mobTypes.length)];
        double[] typeWeights = config.getArchetypeWeights(selectedType);

        if (typeWeights == null) {
            return proportions;
        }

        MobStatType[] statTypes = MobStatType.values();
        int statCount = statTypes.length;

        double[] weightedProportions = new double[statCount];
        double weightSum = 0.0;

        for (int i = 0; i < statCount; i++) {
            double weight = typeWeights[i];
            weightedProportions[i] = proportions[i] * weight;
            weightSum += weightedProportions[i];
        }

        if (weightSum > 0) {
            for (int i = 0; i < statCount; i++) {
                weightedProportions[i] /= weightSum;
            }
        }

        return weightedProportions;
    }

    /**
     * Samples from a Gamma distribution using Marsaglia and Tsang's method.
     *
     * @param alpha Shape parameter (must be > 0)
     * @param beta Scale parameter
     * @param random Random number generator
     * @return A sample from Gamma(alpha, beta)
     */
    public double sampleGamma(double alpha, double beta, Random random) {
        // Bug fix: Ensure alpha is positive to prevent 1/alpha = Infinity
        if (alpha <= 0) {
            alpha = MIN_ALPHA;
        }

        if (alpha > 1.0) {
            double d = alpha - 1.0 / 3.0;
            double c = 1.0 / Math.sqrt(9.0 * d);
            while (true) {
                double x = sampleNormal(random);
                double v = 1.0 + c * x;
                if (v <= 0.0) continue;
                v = v * v * v;
                double u = random.nextDouble();
                double x2 = x * x;
                if (u < 1.0 - 0.0331 * x2 * x2) return d * v * beta;
                if (Math.log(u) < 0.5 * x2 + d * (1.0 - v + Math.log(v))) return d * v * beta;
            }
        } else {
            // For alpha <= 1, use the transformation method
            double u = random.nextDouble();
            // Bug fix: Prevent u=0 which would cause pow(0, 1/alpha) issues
            u = Math.max(Double.MIN_VALUE, u);
            return sampleGamma(1.0 + alpha, beta, random) * Math.pow(u, 1.0 / alpha);
        }
    }

    /**
     * Samples from a standard normal distribution using the Box-Muller transform.
     *
     * @param random Random number generator
     * @return A sample from N(0, 1)
     */
    public double sampleNormal(Random random) {
        // Bug fix: Prevent u1=0 which would cause log(0) = -Infinity
        // nextDouble() returns [0.0, 1.0), so 0.0 is possible though rare
        double u1 = Math.max(Double.MIN_VALUE, random.nextDouble());
        double u2 = random.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }
}
