package io.github.larsonix.trailoforbis.combat.blocking;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlockingStats}.
 */
class BlockingStatsTest {

    @Test
    @DisplayName("from() extracts correct stats from ComputedStats")
    void testFromComputedStats() {
        ComputedStats stats = ComputedStats.builder()
            .blockChance(75f)
            .blockDamageReduction(50f)
            .staminaDrainReduction(30f)
            .build();

        BlockingStats blockingStats = BlockingStats.from(stats);

        assertEquals(75f, blockingStats.blockChance(), 0.001f);
        assertEquals(50f, blockingStats.damageReduction(), 0.001f);
        assertEquals(30f, blockingStats.staminaDrainReduction(), 0.001f);
    }

    @Test
    @DisplayName("getDamageMultiplier() returns correct damage multiplier")
    void testGetDamageMultiplier() {
        // 70% damage reduction means 30% damage taken (0.3 multiplier)
        BlockingStats stats70 = new BlockingStats(100f, 70f, 0f);
        assertEquals(0.3f, stats70.getDamageMultiplier(), 0.001f);

        // 0% reduction means 100% damage taken
        BlockingStats stats0 = new BlockingStats(100f, 0f, 0f);
        assertEquals(1.0f, stats0.getDamageMultiplier(), 0.001f);

        // 100% reduction means 0% damage taken (full block)
        BlockingStats stats100 = new BlockingStats(100f, 100f, 0f);
        assertEquals(0.0f, stats100.getDamageMultiplier(), 0.001f);

        // Values over 100% are capped
        BlockingStats statsOver = new BlockingStats(100f, 150f, 0f);
        assertEquals(0.0f, statsOver.getDamageMultiplier(), 0.001f);
    }

    @Test
    @DisplayName("getEffectiveStaminaReduction() caps at 75%")
    void testGetEffectiveStaminaReduction_capped() {
        // Normal value
        BlockingStats stats30 = new BlockingStats(100f, 50f, 30f);
        assertEquals(0.3f, stats30.getEffectiveStaminaReduction(), 0.001f);

        // At cap
        BlockingStats stats75 = new BlockingStats(100f, 50f, 75f);
        assertEquals(0.75f, stats75.getEffectiveStaminaReduction(), 0.001f);

        // Over cap - should be capped to 75%
        BlockingStats statsOver = new BlockingStats(100f, 50f, 100f);
        assertEquals(0.75f, statsOver.getEffectiveStaminaReduction(), 0.001f);

        // Zero reduction
        BlockingStats stats0 = new BlockingStats(100f, 50f, 0f);
        assertEquals(0.0f, stats0.getEffectiveStaminaReduction(), 0.001f);
    }

    @Test
    @DisplayName("rollBlock() succeeds deterministically at 100%")
    void testRollBlock_deterministicAt100Percent() {
        BlockingStats stats100 = new BlockingStats(100f, 50f, 0f);

        // Should always succeed at 100% chance
        for (int i = 0; i < 100; i++) {
            assertTrue(stats100.rollBlock(), "Block should always succeed at 100% chance");
        }
    }

    @Test
    @DisplayName("rollBlock() fails deterministically at 0%")
    void testRollBlock_deterministicAt0Percent() {
        BlockingStats stats0 = new BlockingStats(0f, 50f, 0f);

        // Should always fail at 0% chance
        for (int i = 0; i < 100; i++) {
            assertFalse(stats0.rollBlock(), "Block should always fail at 0% chance");
        }
    }

    @Test
    @DisplayName("rollBlock() follows probability at intermediate values")
    void testRollBlock_probabilisticAtIntermediateValues() {
        BlockingStats stats50 = new BlockingStats(50f, 50f, 0f);

        // Run many trials and check the success rate is approximately 50%
        int successes = 0;
        int trials = 10000;
        for (int i = 0; i < trials; i++) {
            if (stats50.rollBlock()) {
                successes++;
            }
        }

        // With 10,000 trials at 50%, we expect ~5000 successes
        // Allow 5% deviation (4500-5500)
        double successRate = (double) successes / trials;
        assertTrue(successRate >= 0.45 && successRate <= 0.55,
            "Success rate " + successRate + " should be approximately 50%");
    }
}
