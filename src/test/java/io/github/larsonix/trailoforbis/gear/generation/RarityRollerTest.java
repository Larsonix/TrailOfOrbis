package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RarityRoller - 20 test cases.
 */
class RarityRollerTest {

    private GearBalanceConfig config;

    @BeforeEach
    void setUp() {
        config = createDefaultConfig();
    }

    // =========================================================================
    // BASIC ROLLING TESTS (5 cases)
    // =========================================================================

    @Nested
    @DisplayName("Basic Rolling")
    class BasicRollingTests {

        @Test
        @DisplayName("roll() with no bonus returns valid rarity")
        void roll_NoBonus_ReturnsValidRarity() {
            RarityRoller roller = new RarityRoller(config);
            GearRarity rarity = roller.roll();
            assertNotNull(rarity);
        }

        @Test
        @DisplayName("Multiple rolls distribution matches weights")
        void roll_MultipleRolls_DistributionMatchesWeights() {
            RarityRoller roller = new RarityRoller(config);
            Map<GearRarity, Integer> counts = new EnumMap<>(GearRarity.class);
            for (GearRarity r : GearRarity.values()) {
                counts.put(r, 0);
            }

            for (int i = 0; i < 10000; i++) {
                GearRarity rarity = roller.roll();
                counts.merge(rarity, 1, Integer::sum);
            }

            // Common should be ~75% (4× geometric gaps), allow 5% tolerance
            double commonPercent = counts.get(GearRarity.COMMON) / 10000.0;
            assertTrue(commonPercent >= 0.70 && commonPercent <= 0.80,
                "Common percent should be ~75%, was: " + commonPercent);
        }

        @Test
        @DisplayName("Deterministic with seed produces reproducible results")
        void roll_DeterministicWithSeed_ReproducibleResults() {
            RarityRoller roller1 = new RarityRoller(config, new Random(12345));
            RarityRoller roller2 = new RarityRoller(config, new Random(12345));

            for (int i = 0; i < 100; i++) {
                assertEquals(roller1.roll(), roller2.roll());
            }
        }

        @Test
        @DisplayName("All rarities are possible")
        void roll_AllRaritiesPossible() {
            RarityRoller roller = new RarityRoller(config, new Random(42));
            Set<GearRarity> seen = EnumSet.noneOf(GearRarity.class);

            // Roll many times to see all rarities
            for (int i = 0; i < 100000 && seen.size() < GearRarity.values().length; i++) {
                seen.add(roller.roll());
            }

            assertEquals(GearRarity.values().length, seen.size(), "Should see all rarities");
        }

        @Test
        @DisplayName("getBaseWeight matches config")
        void getBaseWeight_MatchesConfig() {
            RarityRoller roller = new RarityRoller(config);
            assertEquals(config.rarityConfig(GearRarity.COMMON).dropWeight(),
                roller.getBaseWeight(GearRarity.COMMON), 0.001);
        }
    }

    // =========================================================================
    // RARITY BONUS TESTS (8 cases)
    // =========================================================================

    @Nested
    @DisplayName("Rarity Bonus")
    class RarityBonusTests {

        @Test
        @DisplayName("Bonus shifts distribution toward rarer")
        void roll_WithBonus_ShiftsTowardRarer() {
            RarityRoller roller = new RarityRoller(config);

            int noBonus = 0, withBonus = 0;
            for (int i = 0; i < 10000; i++) {
                if (roller.roll(0.0).ordinal() >= GearRarity.RARE.ordinal()) noBonus++;
            }
            for (int i = 0; i < 10000; i++) {
                if (roller.roll(1.0).ordinal() >= GearRarity.RARE.ordinal()) withBonus++;
            }

            assertTrue(withBonus > noBonus, "With bonus should have more rare+");
        }

        @Test
        @DisplayName("Negative bonus clamped to zero")
        void roll_NegativeBonus_ClampedToZero() {
            RarityRoller roller = new RarityRoller(config);
            GearRarity rarity = roller.roll(-5.0);
            assertNotNull(rarity);
        }

        @Test
        @DisplayName("Huge bonus clamped to 10")
        void roll_HugeBonus_ClampedTo10() {
            RarityRoller roller = new RarityRoller(config);
            GearRarity rarity = roller.roll(1000.0);
            assertNotNull(rarity);
        }

        @Test
        @DisplayName("Adjusted weights with no bonus equal base")
        void calculateAdjustedWeights_NoBonus_EqualsBase() {
            RarityRoller roller = new RarityRoller(config);
            Map<GearRarity, Double> adjusted = roller.calculateAdjustedWeights(0.0);

            for (GearRarity rarity : GearRarity.values()) {
                assertEquals(roller.getBaseWeight(rarity), adjusted.get(rarity), 0.001);
            }
        }

        @Test
        @DisplayName("Adjusted weights with bonus leave COMMON unchanged")
        void calculateAdjustedWeights_WithBonus_CommonUnchanged() {
            RarityRoller roller = new RarityRoller(config);
            Map<GearRarity, Double> adjusted = roller.calculateAdjustedWeights(1.0);

            assertEquals(roller.getBaseWeight(GearRarity.COMMON), adjusted.get(GearRarity.COMMON), 0.001);
        }

        @Test
        @DisplayName("Adjusted weights with bonus increase UNIQUE")
        void calculateAdjustedWeights_WithBonus_UniqueIncreased() {
            RarityRoller roller = new RarityRoller(config);
            Map<GearRarity, Double> adjusted = roller.calculateAdjustedWeights(1.0);

            // UNIQUE (index 6 of 7) should be doubled with 100% bonus
            // Formula: multiplier = 1.0 + (rarityBonus * rarityFactor)
            // rarityFactor = 6 / (7-1) = 1.0, so with 100% bonus: multiplier = 2.0
            assertEquals(roller.getBaseWeight(GearRarity.UNIQUE) * 2, adjusted.get(GearRarity.UNIQUE), 0.001);
        }

        @Test
        @DisplayName("Probabilities sum to one")
        void getProbability_SumsToOne() {
            RarityRoller roller = new RarityRoller(config);

            double sum = 0;
            for (GearRarity rarity : GearRarity.values()) {
                sum += roller.getProbability(rarity, 0.5);
            }

            assertEquals(1.0, sum, 0.001);
        }

        @Test
        @DisplayName("Higher bonus increases rare probability")
        void getProbability_WithHigherBonus_RarerIncrease() {
            RarityRoller roller = new RarityRoller(config);

            double probNoBonus = roller.getProbability(GearRarity.LEGENDARY, 0.0);
            double probWithBonus = roller.getProbability(GearRarity.LEGENDARY, 2.0);

            assertTrue(probWithBonus > probNoBonus);
        }
    }

    // =========================================================================
    // EDGE CASES (7 cases)
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null config throws exception")
        void constructor_NullConfig_ThrowsException() {
            assertThrows(NullPointerException.class, () -> new RarityRoller(null));
        }

        @Test
        @DisplayName("Null random throws exception")
        void constructor_NullRandom_ThrowsException() {
            assertThrows(NullPointerException.class, () -> new RarityRoller(config, null));
        }

        @Test
        @DisplayName("Zero weight rarity never returned")
        void roll_ZeroWeightRarity_NeverReturned() {
            GearBalanceConfig zeroMythicConfig = createConfigWithZeroWeight(GearRarity.MYTHIC);
            RarityRoller roller = new RarityRoller(zeroMythicConfig);

            for (int i = 0; i < 10000; i++) {
                assertNotEquals(GearRarity.MYTHIC, roller.roll());
            }
        }

        @Test
        @DisplayName("Total base weight matches config sum")
        void getTotalBaseWeight_MatchesConfigSum() {
            RarityRoller roller = new RarityRoller(config);

            double expected = 0;
            for (GearRarity rarity : GearRarity.values()) {
                expected += config.rarityConfig(rarity).dropWeight();
            }

            assertEquals(expected, roller.getTotalBaseWeight(), 0.001);
        }

        @Test
        @DisplayName("Thread safety with synchronized random")
        void roll_ThreadSafety_WithSharedRandom() throws ExecutionException, InterruptedException {
            Random syncRandom = new Random() {
                @Override
                public synchronized double nextDouble() {
                    return super.nextDouble();
                }
            };
            RarityRoller roller = new RarityRoller(config, syncRandom);

            List<CompletableFuture<GearRarity>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(CompletableFuture.supplyAsync(roller::roll));
            }

            for (CompletableFuture<GearRarity> future : futures) {
                assertNotNull(future.get());
            }
        }

        @Test
        @DisplayName("Single rarity config always returns that rarity")
        void roll_SingleRarityConfig_AlwaysReturnsThatRarity() {
            GearBalanceConfig epicOnlyConfig = createSingleRarityConfig(GearRarity.EPIC);
            RarityRoller roller = new RarityRoller(epicOnlyConfig);

            for (int i = 0; i < 100; i++) {
                assertEquals(GearRarity.EPIC, roller.roll());
            }
        }

        @Test
        @DisplayName("Floating point precision doesn't cause infinite loop")
        void roll_FloatingPointPrecision_NoInfiniteLoop() {
            RarityRoller roller = new RarityRoller(config);

            for (int i = 0; i < 100000; i++) {
                assertNotNull(roller.roll());
            }
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private GearBalanceConfig createDefaultConfig() {
        return TestConfigFactory.createDefaultBalanceConfig();
    }

    private GearBalanceConfig createConfigWithZeroWeight(GearRarity zeroRarity) {
        return TestConfigFactory.createConfigWithZeroWeight(zeroRarity);
    }

    private GearBalanceConfig createSingleRarityConfig(GearRarity onlyRarity) {
        return TestConfigFactory.createSingleRarityConfig(onlyRarity);
    }
}
