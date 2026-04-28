package io.github.larsonix.trailoforbis.mobs.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class DirichletDistributorTest {
    private DirichletDistributor distributor;
    private MobStatPoolConfig config;
    private Random random;

    @BeforeEach
    void setUp() {
        distributor = new DirichletDistributor();
        config = MobStatPoolConfig.createDefaults();
        random = new Random(12345L);
    }

    @Test
    void testDistributeReturnsCorrectNumberOfStats() {
        double totalPool = 100.0;
        Map<MobStatType, Double> shares = distributor.distribute(totalPool, config, random);

        assertEquals(MobStatType.values().length, shares.size());
    }

    @Test
    void testDistributeSharesSumToTotalPool() {
        double totalPool = 250.0;
        Map<MobStatType, Double> shares = distributor.distribute(totalPool, config, random);

        double sum = shares.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(totalPool, sum, 0.001);
    }

    @Test
    void testAllSharesAreNonNegative() {
        double totalPool = 100.0;
        Map<MobStatType, Double> shares = distributor.distribute(totalPool, config, random);

        for (Double share : shares.values()) {
            assertTrue(share >= 0.0, "Share should be non-negative");
        }
    }

    @Test
    void testSampleGammaReturnsPositiveValues() {
        double alpha = 2.0;
        double beta = 1.0;

        for (int i = 0; i < 100; i++) {
            double sample = distributor.sampleGamma(alpha, beta, random);
            assertTrue(sample > 0.0, "Gamma sample should be positive");
        }
    }

    @Test
    void testSampleGammaForAlphaGreaterThanOne() {
        double alpha = 5.0;
        double beta = 2.0;

        double sum = 0.0;
        int count = 1000;
        for (int i = 0; i < count; i++) {
            double sample = distributor.sampleGamma(alpha, beta, random);
            assertTrue(sample > 0.0);
            sum += sample;
        }
        double mean = sum / count;
        assertTrue(mean > 0.0);
    }

    @Test
    void testSampleGammaForAlphaLessThanOne() {
        double alpha = 0.5;
        double beta = 1.0;

        double sum = 0.0;
        int count = 1000;
        for (int i = 0; i < count; i++) {
            double sample = distributor.sampleGamma(alpha, beta, random);
            assertTrue(sample > 0.0);
            sum += sample;
        }
        double mean = sum / count;
        assertTrue(mean > 0.0);
    }

    @Test
    void testSampleNormalReturnsValidValues() {
        double sum = 0.0;
        int count = 10000;
        for (int i = 0; i < count; i++) {
            double sample = distributor.sampleNormal(random);
            sum += sample;
        }
        double mean = sum / count;
        assertTrue(Math.abs(mean) < 0.1, "Mean of normal samples should be close to 0");
    }

    @Test
    void testSameSeedProducesSameDistribution() {
        Random random1 = new Random(99999L);
        Random random2 = new Random(99999L);

        Map<MobStatType, Double> shares1 = distributor.distribute(100.0, config, random1);
        Map<MobStatType, Double> shares2 = distributor.distribute(100.0, config, random2);

        for (MobStatType stat : MobStatType.values()) {
            assertEquals(shares1.get(stat), shares2.get(stat), 0.001,
                "Same seed should produce same shares for " + stat);
        }
    }

    @Test
    void testZeroPoolReturnsZeroShares() {
        double totalPool = 0.0;
        Map<MobStatType, Double> shares = distributor.distribute(totalPool, config, random);

        for (Double share : shares.values()) {
            assertEquals(0.0, share, 0.001);
        }
    }

    @Test
    void testVeryLargePool() {
        double totalPool = 1000000.0;
        Map<MobStatType, Double> shares = distributor.distribute(totalPool, config, random);

        double sum = shares.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(totalPool, sum, 0.001);
    }

    @Test
    void testVerySmallPool() {
        double totalPool = 0.001;
        Map<MobStatType, Double> shares = distributor.distribute(totalPool, config, random);

        double sum = shares.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(totalPool, sum, 0.0001);
    }

    @Test
    void testAllStatTypesHaveEntries() {
        double totalPool = 500.0;
        Map<MobStatType, Double> shares = distributor.distribute(totalPool, config, random);

        for (MobStatType stat : MobStatType.values()) {
            assertTrue(shares.containsKey(stat), "Shares should contain " + stat);
            Double share = shares.get(stat);
            assertNotNull(share, "Share for " + stat + " should not be null");
        }
    }
}
