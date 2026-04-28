package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.math.vector.Vector3d;

import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.LevelBlendingConfig;
import io.github.larsonix.trailoforbis.gear.loot.LootCalculator.LootRoll;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobBonus;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LootCalculator - 25 test cases.
 *
 * <p>Note: WIND replaces LUCK for loot rarity bonuses in the elemental system.
 * WIND represents the "ghost/fortune" archetype and provides loot rarity bonus.
 */
@ExtendWith(MockitoExtension.class)
class LootCalculatorTest {

    @Mock
    private AttributeManager attributeManager;

    private LootSettings settings;
    private RarityBonusCalculator rarityBonusCalculator;
    private Random fixedRandom;

    @BeforeEach
    void setUp() {
        // Create settings with known values
        settings = new LootSettings(
            0.05,  // 5% base drop chance
            0.5,   // +0.5% per WIND (replaces LUCK)
            true,  // distance scaling enabled
            100,   // 100 blocks per 1%
            50.0,  // max 50% distance bonus
            Map.of(
                MobType.NORMAL, MobBonus.NONE,
                MobType.ELITE, new MobBonus(0.5, 0.25),  // 1.5x drops, +25% rarity
                MobType.BOSS, new MobBonus(1.0, 0.5)     // 2x drops, +50% rarity
            )
        );

        // Shared calculator wrapping mock AttributeManager
        rarityBonusCalculator = new RarityBonusCalculator(attributeManager, settings.getLuckToRarityPercent());
    }

    // Use DISABLED blending to preserve original behavior in existing tests
    private final DropLevelBlender blender = new DropLevelBlender(LevelBlendingConfig.DISABLED);

    private LootCalculator createCalculator(long seed) {
        fixedRandom = new Random(seed);
        return new LootCalculator(settings, rarityBonusCalculator, blender, fixedRandom);
    }

    // =========================================================================
    // DROP CHANCE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Drop Chance Tests")
    class DropChanceTests {

        @Test
        @DisplayName("shouldDropGear for NORMAL mob with low roll returns true")
        void shouldDropGear_NormalMob_LowRoll_Drops() {
            // Create calculator with seeded random that will return < 0.05
            LootCalculator calculator = createCalculatorWithControlledRandom(0.04);

            boolean drops = calculator.shouldDropGear(MobType.NORMAL);

            assertTrue(drops);
        }

        @Test
        @DisplayName("shouldDropGear for NORMAL mob with high roll returns false")
        void shouldDropGear_NormalMob_HighRoll_NoDrops() {
            // Create calculator with seeded random that will return > 0.05
            LootCalculator calculator = createCalculatorWithControlledRandom(0.10);

            boolean drops = calculator.shouldDropGear(MobType.NORMAL);

            assertFalse(drops);
        }

        @Test
        @DisplayName("shouldDropGear for ELITE has higher chance")
        void shouldDropGear_Elite_HigherChance() {
            // Elite has 1.5x multiplier, so 7.5% chance
            // Roll of 0.06 should succeed for elite but fail for normal
            LootCalculator calculator = createCalculatorWithControlledRandom(0.06);

            boolean eliteDrops = calculator.shouldDropGear(MobType.ELITE);

            assertTrue(eliteDrops, "Elite should drop at 6% roll (7.5% chance)");
        }

        @Test
        @DisplayName("shouldDropGear for BOSS has double chance")
        void shouldDropGear_Boss_DoubleChance() {
            // Boss has 2x multiplier, so 10% chance
            LootCalculator calculator = createCalculatorWithControlledRandom(0.08);

            boolean bossDrops = calculator.shouldDropGear(MobType.BOSS);

            assertTrue(bossDrops, "Boss should drop at 8% roll (10% chance)");
        }

        private LootCalculator createCalculatorWithControlledRandom(double returnValue) {
            Random mockRandom = mock(Random.class);
            when(mockRandom.nextDouble()).thenReturn(returnValue);
            return new LootCalculator(settings, rarityBonusCalculator, blender, mockRandom);
        }
    }

    // =========================================================================
    // DROP COUNT TESTS
    // =========================================================================

    @Nested
    @DisplayName("Drop Count Tests")
    class DropCountTests {

        @Test
        @DisplayName("calculateDropCount for NORMAL returns 1")
        void calculateDropCount_Normal_Returns1() {
            LootCalculator calculator = createCalculator(12345);

            int count = calculator.calculateDropCount(MobType.NORMAL);

            assertEquals(1, count);
        }

        @Test
        @DisplayName("calculateDropCount for BOSS can return multiple")
        void calculateDropCount_Boss_CanBeMultiple() {
            // Boss has 2x multiplier, so base 1 + 1 guaranteed = 2
            // Run multiple times to ensure we get 2+
            Random mockRandom = mock(Random.class);
            when(mockRandom.nextDouble()).thenReturn(0.99); // High roll for extra drop chance
            LootCalculator calculator = new LootCalculator(settings, rarityBonusCalculator, blender, mockRandom);

            int count = calculator.calculateDropCount(MobType.BOSS);

            assertTrue(count >= 2, "Boss should drop at least 2 items");
        }

        @Test
        @DisplayName("calculateDropCount for ELITE returns 1 or 2")
        void calculateDropCount_Elite_Returns1Or2() {
            // Elite has 1.5x multiplier, so base 1 + 0.5 fractional chance for extra
            LootCalculator calculator = createCalculator(12345);

            // Run multiple times
            Set<Integer> counts = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                calculator = createCalculator(i);
                counts.add(calculator.calculateDropCount(MobType.ELITE));
            }

            assertTrue(counts.contains(1) || counts.contains(2),
                "Elite should return 1 or 2 drops");
        }
    }

    // =========================================================================
    // RARITY BONUS TESTS
    // =========================================================================

    @Nested
    @DisplayName("Rarity Bonus Tests")
    class RarityBonusTests {

        @Test
        @DisplayName("calculateRarityBonus includes WIND (replaces LUCK)")
        void calculateRarityBonus_IncludesWind() {
            UUID playerId = UUID.randomUUID();
            // WIND replaces LUCK for loot rarity in the elemental system
            when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of(AttributeType.WIND, 20));

            LootCalculator calculator = createCalculator(12345);
            double bonus = calculator.calculateRarityBonus(
                playerId, MobType.NORMAL, new Vector3d(0, 64, 0));

            // 20 WIND * 0.5% = 10%
            assertTrue(bonus >= 10.0, "Bonus should include 10% from WIND");
        }

        @Test
        @DisplayName("calculateRarityBonus includes distance")
        void calculateRarityBonus_IncludesDistance() {
            UUID playerId = UUID.randomUUID();
            when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of(AttributeType.WIND, 0));

            LootCalculator calculator = createCalculator(12345);
            calculator.setWorldSpawn(new Vector3d(0, 64, 0));

            // 500 blocks away = 5% bonus
            double bonus = calculator.calculateRarityBonus(
                playerId, MobType.NORMAL, new Vector3d(500, 64, 0));

            assertTrue(bonus >= 5.0, "Bonus should include ~5% from distance");
        }

        @Test
        @DisplayName("calculateRarityBonus distance is capped")
        void calculateRarityBonus_DistanceCapped() {
            UUID playerId = UUID.randomUUID();
            when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of(AttributeType.WIND, 0));

            LootCalculator calculator = createCalculator(12345);
            calculator.setWorldSpawn(new Vector3d(0, 64, 0));

            // 10000 blocks away would be 100%, but capped at 50%
            double bonus = calculator.calculateRarityBonus(
                playerId, MobType.NORMAL, new Vector3d(10000, 64, 0));

            assertEquals(50.0, bonus, 0.1, "Distance bonus should be capped at 50%");
        }

        @Test
        @DisplayName("calculateRarityBonus includes mob type bonus")
        void calculateRarityBonus_IncludesMobType() {
            UUID playerId = UUID.randomUUID();
            when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of(AttributeType.WIND, 0));

            LootCalculator calculator = createCalculator(12345);
            calculator.setWorldSpawn(new Vector3d(0, 64, 0));

            double normalBonus = calculator.calculateRarityBonus(
                playerId, MobType.NORMAL, new Vector3d(0, 64, 0));
            double bossBonus = calculator.calculateRarityBonus(
                playerId, MobType.BOSS, new Vector3d(0, 64, 0));

            assertTrue(bossBonus > normalBonus, "Boss bonus should be higher than normal");
            assertEquals(50.0, bossBonus, 0.1, "Boss should add 50% rarity bonus");
        }

        @Test
        @DisplayName("calculateRarityBonus sums all factors")
        void calculateRarityBonus_SumsAllFactors() {
            UUID playerId = UUID.randomUUID();
            // WIND replaces LUCK for loot rarity
            when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of(AttributeType.WIND, 20));

            LootCalculator calculator = createCalculator(12345);
            calculator.setWorldSpawn(new Vector3d(0, 64, 0));

            // WIND: 20 * 0.5 = 10%
            // Distance: 500 / 100 = 5%
            // Boss: 50%
            // Total: 65%
            double bonus = calculator.calculateRarityBonus(
                playerId, MobType.BOSS, new Vector3d(500, 64, 0));

            assertEquals(65.0, bonus, 1.0, "Total bonus should be ~65%");
        }
    }

    // =========================================================================
    // ITEM LEVEL TESTS
    // =========================================================================

    @Nested
    @DisplayName("Item Level Tests")
    class ItemLevelTests {

        @Test
        @DisplayName("calculateItemLevel averages around mob level")
        void calculateItemLevel_AveragesAroundMobLevel() {
            // Run many times and check average
            int mobLevel = 50;
            int total = 0;
            for (int i = 0; i < 100; i++) {
                LootCalculator calculator = createCalculator(i);
                total += calculator.calculateItemLevel(mobLevel, mobLevel);
            }
            double avg = total / 100.0;

            assertTrue(avg >= 48 && avg <= 52,
                "Average item level should be close to mob level");
        }

        @Test
        @DisplayName("calculateItemLevel has variance")
        void calculateItemLevel_HasVariance() {
            int mobLevel = 50;
            Set<Integer> levels = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                LootCalculator calculator = createCalculator(i);
                levels.add(calculator.calculateItemLevel(mobLevel, mobLevel));
            }

            assertTrue(levels.size() > 1, "Should have some variance in item level");
        }

        @Test
        @DisplayName("calculateItemLevel never below 1")
        void calculateItemLevel_NeverBelowOne() {
            // Even with mob level 1 and negative variance, should never go below 1
            for (int i = 0; i < 100; i++) {
                LootCalculator calculator = createCalculator(i);
                int level = calculator.calculateItemLevel(1, 1);
                assertTrue(level >= 1, "Item level should never be below 1");
            }
        }
    }

    // =========================================================================
    // COMPLETE LOOT CALCULATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Complete Loot Calculation Tests")
    class CompleteLootTests {

        @Test
        @DisplayName("calculateLoot with no drop returns NO_DROP")
        void calculateLoot_NoDrop_ReturnsEmpty() {
            // High random roll = no drop
            Random mockRandom = mock(Random.class);
            when(mockRandom.nextDouble()).thenReturn(0.99);
            LootCalculator calculator = new LootCalculator(settings, rarityBonusCalculator, blender, mockRandom);

            LootRoll result = calculator.calculateLoot(
                UUID.randomUUID(), MobType.NORMAL, 50, 50, new Vector3d(0, 64, 0));

            assertFalse(result.shouldDrop());
            assertEquals(LootRoll.NO_DROP, result);
        }

        @Test
        @DisplayName("calculateLoot with drop success has all fields")
        void calculateLoot_DropSuccess_HasAllFields() {
            UUID playerId = UUID.randomUUID();
            // WIND replaces LUCK for loot rarity
            when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of(AttributeType.WIND, 10));

            // Low random roll = drop succeeds
            Random mockRandom = mock(Random.class);
            when(mockRandom.nextDouble()).thenReturn(0.01);
            when(mockRandom.nextInt(anyInt())).thenReturn(0);
            LootCalculator calculator = new LootCalculator(settings, rarityBonusCalculator, blender, mockRandom);

            LootRoll result = calculator.calculateLoot(
                playerId, MobType.ELITE, 50, 50, new Vector3d(500, 64, 0));

            assertTrue(result.shouldDrop());
            assertTrue(result.dropCount() >= 1);
            assertTrue(result.rarityBonus() >= 0);
            assertTrue(result.itemLevel() >= 1);
        }
    }

    // =========================================================================
    // STATISTICAL TESTS
    // =========================================================================

    @Nested
    @DisplayName("Statistical Tests")
    class StatisticalTests {

        @Test
        @DisplayName("drop rate is influenced by mob type")
        void dropRate_InfluencedByMobType() {
            // Use a single random instance to get true randomness
            Random random = new Random(12345);
            LootCalculator calculator = new LootCalculator(settings, rarityBonusCalculator, blender, random);

            int normalDrops = 0;
            int bossDrops = 0;

            for (int i = 0; i < 1000; i++) {
                if (calculator.shouldDropGear(MobType.NORMAL)) {
                    normalDrops++;
                }
                if (calculator.shouldDropGear(MobType.BOSS)) {
                    bossDrops++;
                }
            }

            // Boss should have more drops than normal due to 2x multiplier
            assertTrue(bossDrops > normalDrops,
                "Boss should have higher drop rate than normal");
        }
    }
}
