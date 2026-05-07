package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ExponentialScalingConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ImplicitDamageConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.WeaponBaseRange;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ImplicitDamageCalculator - The weapon base damage system.
 *
 * <p>All weapons share a unified base damage range. Growth is driven by
 * LevelScaling with a configurable scale factor. Two-handed weapons get
 * a flat multiplier. Thrown consumables are excluded entirely.
 *
 * <p><b>Formula:</b>
 * <pre>
 * bonus = LevelScaling.getBonusPercent(level) / 100 × scaleFactor
 * scaledMin = baseMin + bonus
 * scaledMax = baseMax + bonus
 * if twoHanded: scaledMin/Max *= twoHandedMultiplier
 * rolledValue = random(scaledMin, scaledMax)
 * </pre>
 */
class ImplicitDamageCalculatorTest {

    private ImplicitDamageCalculator calculator;
    private GearBalanceConfig mockConfig;

    // Test config values
    private static final double BASE_MIN = 1.0;
    private static final double BASE_MAX = 5.0;
    private static final double SCALE_FACTOR = 55.0;
    private static final double TWO_HANDED_MULTIPLIER = 2.0;

    @BeforeEach
    void setUp() {
        ImplicitDamageConfig implicitConfig = new ImplicitDamageConfig(
                true, BASE_MIN, BASE_MAX, SCALE_FACTOR, TWO_HANDED_MULTIPLIER,
                0.1, 0.3, 3.0,  // spellbook: mana_regen
                70.0, 5.0        // element weights: physical=70, elemental_each=5
        );

        // ExponentialScaling is no longer used by ImplicitDamageCalculator,
        // but GearBalanceConfig still requires it
        ExponentialScalingConfig expScaling = new ExponentialScalingConfig(
            true, 2.0, 100, 50.0, 1.0, 100, 2.0
        );

        mockConfig = mock(GearBalanceConfig.class);
        when(mockConfig.implicitDamage()).thenReturn(implicitConfig);
        when(mockConfig.exponentialScaling()).thenReturn(expScaling);

        calculator = new ImplicitDamageCalculator(mockConfig);
    }

    // =========================================================================
    // BASIC CALCULATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Basic Calculation")
    class BasicCalculationTests {

        @Test
        @DisplayName("calculate() produces valid implicit with correct damage type")
        void calculate_ProducesValidImplicit() {
            Random random = new Random(42);

            WeaponImplicit implicit = calculator.calculate(WeaponType.SWORD, 1, random);

            assertNotNull(implicit);
            assertEquals(ImplicitDamageCalculator.PHYSICAL_DAMAGE, implicit.damageType());
            assertTrue(implicit.rolledValue() >= implicit.minValue());
            assertTrue(implicit.rolledValue() <= implicit.maxValue());
        }

        @Test
        @DisplayName("Level 1 implicit uses base range (no LevelScaling bonus)")
        void calculate_Level1_UsesBaseRange() {
            Random random = new Random(42);

            WeaponImplicit implicit = calculator.calculate(WeaponType.SWORD, 1, random);

            // At level 1, LevelScaling.getBonusPercent(1) = 0%, so range = base
            assertEquals(BASE_MIN, implicit.minValue(), 0.01,
                "Min at level 1 should be base_min");
            assertEquals(BASE_MAX, implicit.maxValue(), 0.01,
                "Max at level 1 should be base_max");
        }

        @Test
        @DisplayName("Rolled value is always within [min, max] range")
        void calculate_RolledValueInRange() {
            Random random = new Random();

            for (int i = 0; i < 100; i++) {
                WeaponImplicit implicit = calculator.calculate(WeaponType.SWORD, 50, random);

                assertTrue(implicit.rolledValue() >= implicit.minValue(),
                    "Rolled value " + implicit.rolledValue() + " should be >= min " + implicit.minValue());
                assertTrue(implicit.rolledValue() <= implicit.maxValue(),
                    "Rolled value " + implicit.rolledValue() + " should be <= max " + implicit.maxValue());
            }
        }
    }

    // =========================================================================
    // UNIFIED BASE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Unified Base Damage")
    class UnifiedBaseTests {

        @Test
        @DisplayName("All one-handed weapons share the same range at any level")
        void allOneHandedWeapons_SameRange() {
            int level = 50;
            Random random = new Random(42);

            // All 1H weapons should produce identical min/max
            // Spellbooks excluded — they use a separate mana_regen implicit
            WeaponType[] oneHandedTypes = {
                WeaponType.SWORD, WeaponType.DAGGER, WeaponType.AXE,
                WeaponType.MACE, WeaponType.CLAWS, WeaponType.CLUB,
                WeaponType.WAND
            };

            WeaponImplicit reference = calculator.calculate(WeaponType.SWORD, level, random);

            for (WeaponType type : oneHandedTypes) {
                WeaponImplicit implicit = calculator.calculate(type, level, new Random(99));
                assertEquals(reference.minValue(), implicit.minValue(), 0.01,
                    type + " should have same min as SWORD at level " + level);
                assertEquals(reference.maxValue(), implicit.maxValue(), 0.01,
                    type + " should have same max as SWORD at level " + level);
            }
        }

        @Test
        @DisplayName("Two-handed weapons have exactly twoHandedMultiplier× the one-handed range")
        void twoHandedWeapons_MultiplierApplied() {
            int level = 50;
            Random random = new Random(42);

            WeaponImplicit oneHanded = calculator.calculate(WeaponType.SWORD, level, random);
            WeaponImplicit twoHanded = calculator.calculate(WeaponType.LONGSWORD, level, new Random(42));

            assertEquals(oneHanded.minValue() * TWO_HANDED_MULTIPLIER, twoHanded.minValue(), 0.01,
                "2H min should be exactly " + TWO_HANDED_MULTIPLIER + "× 1H min");
            assertEquals(oneHanded.maxValue() * TWO_HANDED_MULTIPLIER, twoHanded.maxValue(), 0.01,
                "2H max should be exactly " + TWO_HANDED_MULTIPLIER + "× 1H max");
        }

        @Test
        @DisplayName("All two-handed weapons share the same range")
        void allTwoHandedWeapons_SameRange() {
            int level = 50;
            Random random = new Random(42);

            WeaponType[] twoHandedTypes = {
                WeaponType.LONGSWORD, WeaponType.BATTLEAXE, WeaponType.SPEAR,
                WeaponType.SHORTBOW, WeaponType.CROSSBOW, WeaponType.BLOWGUN,
                WeaponType.STAFF
            };

            WeaponImplicit reference = calculator.calculate(WeaponType.LONGSWORD, level, random);

            for (WeaponType type : twoHandedTypes) {
                WeaponImplicit implicit = calculator.calculate(type, level, new Random(99));
                assertEquals(reference.minValue(), implicit.minValue(), 0.01,
                    type + " should have same min as LONGSWORD at level " + level);
                assertEquals(reference.maxValue(), implicit.maxValue(), 0.01,
                    type + " should have same max as LONGSWORD at level " + level);
            }
        }
    }

    // =========================================================================
    // LEVEL SCALING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Level Scaling")
    class LevelScalingTests {

        @Test
        @DisplayName("Higher level weapons have higher implicit damage")
        void calculate_HigherLevelHigherDamage() {
            Random random = new Random(42);

            WeaponImplicit level10 = calculator.calculate(WeaponType.SWORD, 10, random);
            WeaponImplicit level50 = calculator.calculate(WeaponType.SWORD, 50, new Random(42));
            WeaponImplicit level100 = calculator.calculate(WeaponType.SWORD, 100, new Random(42));

            assertTrue(level10.maxValue() < level50.maxValue(),
                "Level 50 should have higher max than level 10");
            assertTrue(level50.maxValue() < level100.maxValue(),
                "Level 100 should have higher max than level 50");
        }

        @Test
        @DisplayName("Formula: implicit = base + LevelScaling bonus × scaleFactor")
        void formula_MatchesLevelScaling() {
            Random random = new Random(42);
            int level = 100;

            WeaponImplicit implicit = calculator.calculate(WeaponType.SWORD, level, random);

            double bonus = LevelScaling.getBonusPercent(level) / 100.0 * SCALE_FACTOR;
            double expectedMin = BASE_MIN + bonus;
            double expectedMax = BASE_MAX + bonus;

            assertEquals(expectedMin, implicit.minValue(), 0.01,
                "Min should be base_min + LevelScaling bonus × scaleFactor");
            assertEquals(expectedMax, implicit.maxValue(), 0.01,
                "Max should be base_max + LevelScaling bonus × scaleFactor");
        }

        @Test
        @DisplayName("Level 1 vs Level 100 vs Level 1000 scaling")
        void calculate_ScalingAcrossLevels() {
            Random random = new Random(42);

            WeaponImplicit level1 = calculator.calculate(WeaponType.SWORD, 1, random);
            WeaponImplicit level100 = calculator.calculate(WeaponType.SWORD, 100, new Random(42));
            WeaponImplicit level1000 = calculator.calculate(WeaponType.SWORD, 1000, new Random(42));

            assertTrue(level100.maxValue() > level1.maxValue(),
                "Level 100 should be higher than level 1");
            assertTrue(level1000.maxValue() > level100.maxValue(),
                "Level 1000 should be higher than level 100");
        }
    }

    // =========================================================================
    // WEAPON TYPE MAPPING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Weapon Type Mapping")
    class WeaponTypeMappingTests {

        @Test
        @DisplayName("Physical weapons get 'physical_damage' type")
        void physicalWeapons_GetPhysicalDamageType() {
            Random random = new Random(42);

            WeaponImplicit sword = calculator.calculate(WeaponType.SWORD, 10, random);
            WeaponImplicit dagger = calculator.calculate(WeaponType.DAGGER, 10, new Random(42));
            WeaponImplicit longsword = calculator.calculate(WeaponType.LONGSWORD, 10, new Random(42));

            assertEquals(ImplicitDamageCalculator.PHYSICAL_DAMAGE, sword.damageType());
            assertEquals(ImplicitDamageCalculator.PHYSICAL_DAMAGE, dagger.damageType());
            assertEquals(ImplicitDamageCalculator.PHYSICAL_DAMAGE, longsword.damageType());
        }

        @Test
        @DisplayName("Magic weapons get a random elemental damage type")
        void magicWeapons_GetElementalDamageType() {
            Random random = new Random(42);

            WeaponImplicit staff = calculator.calculate(WeaponType.STAFF, 10, random);
            WeaponImplicit wand = calculator.calculate(WeaponType.WAND, 10, new Random(43));
            WeaponImplicit spellbook = calculator.calculate(WeaponType.SPELLBOOK, 10, new Random(42));

            // Staff and wand get a fixed element (one of the 6)
            assertNotNull(staff.getSpellElement(), "Staff should have a fixed element");
            assertNotNull(wand.getSpellElement(), "Wand should have a fixed element");
            assertTrue(staff.damageType().endsWith("_damage"), "Staff damageType should end with _damage");
            assertTrue(wand.damageType().endsWith("_damage"), "Wand damageType should end with _damage");
            assertNotEquals(ImplicitDamageCalculator.PHYSICAL_DAMAGE, staff.damageType());
            assertNotEquals(ImplicitDamageCalculator.PHYSICAL_DAMAGE, wand.damageType());
            // Spellbook still gets mana_regen (support item)
            assertEquals(ImplicitDamageCalculator.MANA_REGEN, spellbook.damageType());
        }

        @Test
        @DisplayName("All stat-eligible weapon types produce valid implicits")
        void allStatEligibleTypes_MappedCorrectly() {
            Random random = new Random(42);

            for (WeaponType type : WeaponType.values()) {
                if (!type.isStatEligible()) continue;

                WeaponImplicit implicit = calculator.calculate(type, 10, random);

                assertNotNull(implicit, "Implicit should be created for " + type);
                assertFalse(implicit.damageType().isEmpty(),
                    "Damage type should not be empty for " + type);
            }
        }
    }

    // =========================================================================
    // THROWN & SHIELD HANDLING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Ineligible Weapons")
    class IneligibleWeaponTests {

        @Test
        @DisplayName("Shields should not have implicits")
        void shouldHaveImplicit_ShieldReturnsFalse() {
            assertFalse(calculator.shouldHaveImplicit(WeaponType.SHIELD),
                "Shields should not have implicit damage");
        }

        @Test
        @DisplayName("Thrown weapons should not have implicits")
        void shouldHaveImplicit_ThrownReturnsFalse() {
            assertFalse(calculator.shouldHaveImplicit(WeaponType.BOMB),
                "Bombs should not have implicit damage");
            assertFalse(calculator.shouldHaveImplicit(WeaponType.DART),
                "Darts should not have implicit damage");
            assertFalse(calculator.shouldHaveImplicit(WeaponType.KUNAI),
                "Kunai should not have implicit damage");
        }

        @Test
        @DisplayName("Non-thrown, non-shield weapons should have implicits")
        void shouldHaveImplicit_EligibleReturnsTrue() {
            assertTrue(calculator.shouldHaveImplicit(WeaponType.SWORD));
            assertTrue(calculator.shouldHaveImplicit(WeaponType.STAFF));
            assertTrue(calculator.shouldHaveImplicit(WeaponType.DAGGER));
            assertTrue(calculator.shouldHaveImplicit(WeaponType.LONGSWORD));
            assertTrue(calculator.shouldHaveImplicit(WeaponType.SHORTBOW));
            assertTrue(calculator.shouldHaveImplicit(WeaponType.UNKNOWN));
        }
    }

    // =========================================================================
    // REROLL TESTS
    // =========================================================================

    @Nested
    @DisplayName("Reroll Functionality")
    class RerollTests {

        @Test
        @DisplayName("Reroll stays within original range")
        void reroll_StaysWithinOriginalRange() {
            Random random = new Random(42);
            WeaponImplicit original = calculator.calculate(WeaponType.SWORD, 50, random);

            for (int i = 0; i < 100; i++) {
                WeaponImplicit rerolled = calculator.reroll(original, new Random());

                assertEquals(original.minValue(), rerolled.minValue(),
                    "Min value should not change on reroll");
                assertEquals(original.maxValue(), rerolled.maxValue(),
                    "Max value should not change on reroll");
                assertEquals(original.damageType(), rerolled.damageType(),
                    "Damage type should not change on reroll");

                assertTrue(rerolled.rolledValue() >= rerolled.minValue(),
                    "Rerolled value should be >= min");
                assertTrue(rerolled.rolledValue() <= rerolled.maxValue(),
                    "Rerolled value should be <= max");
            }
        }

        @Test
        @DisplayName("Reroll can produce different values")
        void reroll_CanProduceDifferentValues() {
            Random random = new Random(42);
            WeaponImplicit original = calculator.calculate(WeaponType.SWORD, 50, random);

            int differentCount = 0;
            double lastValue = original.rolledValue();

            for (int i = 0; i < 50; i++) {
                WeaponImplicit rerolled = calculator.reroll(original, new Random());
                if (Math.abs(rerolled.rolledValue() - lastValue) > 0.01) {
                    differentCount++;
                }
                lastValue = rerolled.rolledValue();
            }

            assertTrue(differentCount > 30,
                "Reroll should produce different values (got " + differentCount + " different out of 50)");
        }
    }

    // =========================================================================
    // RESCALE FOR LEVEL TESTS
    // =========================================================================

    @Nested
    @DisplayName("Rescale For Level (Proportionality Preservation)")
    class RescaleForLevelTests {

        @Test
        @DisplayName("Percentile is preserved when level increases")
        void rescale_PreservesPercentile_LevelUp() {
            // Create an implicit at level 10 with a known percentile
            WeaponImplicit original = WeaponImplicit.of(
                    ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                    10.0, 20.0, 18.0  // 80th percentile
            );
            double originalPercentile = original.rollPercentile();
            assertEquals(0.8, originalPercentile, 0.001);

            // Rescale to level 50
            WeaponImplicit rescaled = calculator.rescaleForLevel(original, WeaponType.SWORD, 50);

            // New range should be different (higher)
            assertTrue(rescaled.minValue() > original.minValue(),
                "Min should increase with level");
            assertTrue(rescaled.maxValue() > original.maxValue(),
                "Max should increase with level");

            // Percentile must be preserved
            assertEquals(originalPercentile, rescaled.rollPercentile(), 0.001,
                "Percentile should be preserved across level change");
        }

        @Test
        @DisplayName("Percentile is preserved when level decreases")
        void rescale_PreservesPercentile_LevelDown() {
            WeaponImplicit original = WeaponImplicit.of(
                    ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                    30.0, 50.0, 35.0  // 25th percentile
            );
            double originalPercentile = original.rollPercentile();

            WeaponImplicit rescaled = calculator.rescaleForLevel(original, WeaponType.SWORD, 1);

            assertEquals(originalPercentile, rescaled.rollPercentile(), 0.001,
                "Percentile should be preserved when level decreases");
        }

        @Test
        @DisplayName("Minimum roll (0th percentile) stays at minimum")
        void rescale_MinRoll_StaysAtMin() {
            WeaponImplicit original = WeaponImplicit.of(
                    ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                    10.0, 20.0, 10.0  // 0th percentile (worst roll)
            );

            WeaponImplicit rescaled = calculator.rescaleForLevel(original, WeaponType.SWORD, 50);

            assertEquals(rescaled.minValue(), rescaled.rolledValue(), 0.001,
                "0th percentile roll should land at new min");
            assertEquals(0.0, rescaled.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("Maximum roll (100th percentile) stays at maximum")
        void rescale_MaxRoll_StaysAtMax() {
            WeaponImplicit original = WeaponImplicit.of(
                    ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                    10.0, 20.0, 20.0  // 100th percentile (perfect roll)
            );

            WeaponImplicit rescaled = calculator.rescaleForLevel(original, WeaponType.SWORD, 50);

            assertEquals(rescaled.maxValue(), rescaled.rolledValue(), 0.001,
                "100th percentile roll should land at new max");
            assertEquals(1.0, rescaled.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("Two-handed weapons use 2H range when rescaling")
        void rescale_TwoHanded_Uses2HRange() {
            Random random = new Random(42);
            // Generate a 1H and 2H implicit at level 10 with same percentile
            WeaponImplicit oneHanded = WeaponImplicit.of(
                    ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                    10.0, 20.0, 15.0  // 50th percentile
            );
            WeaponImplicit twoHanded = WeaponImplicit.of(
                    ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                    20.0, 40.0, 30.0  // 50th percentile
            );

            WeaponImplicit rescaled1H = calculator.rescaleForLevel(oneHanded, WeaponType.SWORD, 50);
            WeaponImplicit rescaled2H = calculator.rescaleForLevel(twoHanded, WeaponType.LONGSWORD, 50);

            // 2H should have exactly 2× the 1H range
            assertEquals(rescaled1H.minValue() * TWO_HANDED_MULTIPLIER, rescaled2H.minValue(), 0.01);
            assertEquals(rescaled1H.maxValue() * TWO_HANDED_MULTIPLIER, rescaled2H.maxValue(), 0.01);

            // Both should preserve 50th percentile
            assertEquals(0.5, rescaled1H.rollPercentile(), 0.001);
            assertEquals(0.5, rescaled2H.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("Spellbook implicit uses spellbook range when rescaling")
        void rescale_Spellbook_UsesSpellbookRange() {
            WeaponImplicit original = WeaponImplicit.of(
                    ImplicitDamageCalculator.MANA_REGEN,
                    0.1, 0.3, 0.2  // 50th percentile
            );

            WeaponImplicit rescaled = calculator.rescaleForLevel(original, WeaponType.SPELLBOOK, 50);

            assertEquals(ImplicitDamageCalculator.MANA_REGEN, rescaled.damageType(),
                "Spellbook should keep mana_regen type");
            assertEquals(0.5, rescaled.rollPercentile(), 0.001,
                "Spellbook percentile should be preserved");
        }

        @Test
        @DisplayName("Damage type is preserved across rescale")
        void rescale_PreservesDamageType() {
            WeaponImplicit physical = WeaponImplicit.of(
                    ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                    10.0, 20.0, 15.0
            );
            WeaponImplicit spell = WeaponImplicit.of(
                    ImplicitDamageCalculator.SPELL_DAMAGE,
                    10.0, 20.0, 15.0
            );

            assertEquals(ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                calculator.rescaleForLevel(physical, WeaponType.SWORD, 50).damageType());
            assertEquals(ImplicitDamageCalculator.SPELL_DAMAGE,
                calculator.rescaleForLevel(spell, WeaponType.STAFF, 50).damageType());
        }

        @Test
        @DisplayName("Rescale is deterministic (no randomness)")
        void rescale_IsDeterministic() {
            WeaponImplicit original = WeaponImplicit.of(
                    ImplicitDamageCalculator.PHYSICAL_DAMAGE,
                    10.0, 20.0, 17.5
            );

            WeaponImplicit first = calculator.rescaleForLevel(original, WeaponType.SWORD, 50);
            WeaponImplicit second = calculator.rescaleForLevel(original, WeaponType.SWORD, 50);

            assertEquals(first.rolledValue(), second.rolledValue(), 0.0,
                "Rescale must produce identical results every time (no randomness)");
            assertEquals(first.minValue(), second.minValue(), 0.0);
            assertEquals(first.maxValue(), second.maxValue(), 0.0);
        }

        @Test
        @DisplayName("Rolled value is always within new range bounds")
        void rescale_ValueAlwaysInRange() {
            Random random = new Random();

            for (int i = 0; i < 100; i++) {
                double min = 10.0;
                double max = 20.0;
                double value = min + random.nextDouble() * (max - min);
                WeaponImplicit original = WeaponImplicit.of(
                        ImplicitDamageCalculator.PHYSICAL_DAMAGE, min, max, value);

                int newLevel = 1 + random.nextInt(150);
                WeaponImplicit rescaled = calculator.rescaleForLevel(original, WeaponType.SWORD, newLevel);

                assertTrue(rescaled.rolledValue() >= rescaled.minValue() - 0.0001,
                    "Value " + rescaled.rolledValue() + " should be >= min " + rescaled.minValue());
                assertTrue(rescaled.rolledValue() <= rescaled.maxValue() + 0.0001,
                    "Value " + rescaled.rolledValue() + " should be <= max " + rescaled.maxValue());
            }
        }
    }

    // =========================================================================
    // CONFIG DISABLED TESTS
    // =========================================================================

    @Nested
    @DisplayName("Config Disabled")
    class ConfigDisabledTests {

        @Test
        @DisplayName("isEnabled() returns config value")
        void isEnabled_ReturnsConfigValue() {
            assertTrue(calculator.isEnabled(), "Should be enabled with enabled config");

            ImplicitDamageConfig disabledConfig = ImplicitDamageConfig.DISABLED;
            when(mockConfig.implicitDamage()).thenReturn(disabledConfig);
            calculator = new ImplicitDamageCalculator(mockConfig);

            assertFalse(calculator.isEnabled(), "Should be disabled with disabled config");
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Level 0 returns base values (no scaling)")
        void calculate_Level0_BaseValues() {
            Random random = new Random(42);

            WeaponImplicit implicit = calculator.calculate(WeaponType.SWORD, 0, random);

            assertNotNull(implicit);
            // At level 0, LevelScaling.getBonusPercent(0) = 0%, so range = base
            assertEquals(BASE_MIN, implicit.minValue(), 0.01);
            assertEquals(BASE_MAX, implicit.maxValue(), 0.01);
        }

        @Test
        @DisplayName("Negative level handled gracefully")
        void calculate_NegativeLevel_HandledGracefully() {
            Random random = new Random(42);

            WeaponImplicit implicit = calculator.calculate(WeaponType.SWORD, -5, random);

            assertNotNull(implicit);
            assertTrue(implicit.rolledValue() >= 0, "Damage should not be negative");
        }

        @Test
        @DisplayName("Very high level (1000+) handled with diminishing returns")
        void calculate_VeryHighLevel_DiminishingReturns() {
            Random random = new Random(42);

            WeaponImplicit level100 = calculator.calculate(WeaponType.SWORD, 100, random);
            WeaponImplicit level1000 = calculator.calculate(WeaponType.SWORD, 1000, new Random(42));

            // Level 1000 should be higher but not explosively so
            assertTrue(level1000.maxValue() > level100.maxValue(),
                "Level 1000 should produce higher damage than level 100");
            assertTrue(Double.isFinite(level1000.maxValue()),
                "Very high level should produce finite damage values");

            // Ratio should be bounded (LevelScaling has asymptotic ceiling)
            double ratio = level1000.maxValue() / level100.maxValue();
            assertTrue(ratio < 10,
                "Scaling ratio 1000/100 should be bounded, got: " + ratio);
        }

        @Test
        @DisplayName("Null weapon type throws exception")
        void calculate_NullWeaponType_ThrowsException() {
            Random random = new Random(42);

            assertThrows(NullPointerException.class,
                () -> calculator.calculate(null, 10, random));
        }

        @Test
        @DisplayName("Null random throws exception")
        void calculate_NullRandom_ThrowsException() {
            assertThrows(NullPointerException.class,
                () -> calculator.calculate(WeaponType.SWORD, 10, null));
        }

        @Test
        @DisplayName("Null config throws exception on construction")
        void constructor_NullConfig_ThrowsException() {
            assertThrows(NullPointerException.class,
                () -> new ImplicitDamageCalculator(null));
        }
    }

    // =========================================================================
    // FORMULA VERIFICATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Formula Verification")
    class FormulaVerificationTests {

        @Test
        @DisplayName("Formula: scaledMin = baseMin + LevelScaling bonus × scaleFactor")
        void formula_ScaledMinCorrect() {
            Random random = new Random(42);

            WeaponImplicit implicit = calculator.calculate(WeaponType.SWORD, 100, random);

            double bonus = LevelScaling.getBonusPercent(100) / 100.0 * SCALE_FACTOR;
            double expectedMin = BASE_MIN + bonus;
            assertEquals(expectedMin, implicit.minValue(), 0.01,
                "scaledMin should use additive LevelScaling formula");
        }

        @Test
        @DisplayName("Formula: scaledMax = baseMax + LevelScaling bonus × scaleFactor")
        void formula_ScaledMaxCorrect() {
            Random random = new Random(42);

            WeaponImplicit implicit = calculator.calculate(WeaponType.SWORD, 100, random);

            double bonus = LevelScaling.getBonusPercent(100) / 100.0 * SCALE_FACTOR;
            double expectedMax = BASE_MAX + bonus;
            assertEquals(expectedMax, implicit.maxValue(), 0.01,
                "scaledMax should use additive LevelScaling formula");
        }

        @Test
        @DisplayName("2H formula: range = (base + bonus) × twoHandedMultiplier")
        void formula_TwoHandedCorrect() {
            Random random = new Random(42);
            int level = 100;

            WeaponImplicit implicit = calculator.calculate(WeaponType.LONGSWORD, level, random);

            double bonus = LevelScaling.getBonusPercent(level) / 100.0 * SCALE_FACTOR;
            double expectedMin = (BASE_MIN + bonus) * TWO_HANDED_MULTIPLIER;
            double expectedMax = (BASE_MAX + bonus) * TWO_HANDED_MULTIPLIER;

            assertEquals(expectedMin, implicit.minValue(), 0.01,
                "2H min should be (base_min + bonus) × 2H multiplier");
            assertEquals(expectedMax, implicit.maxValue(), 0.01,
                "2H max should be (base_max + bonus) × 2H multiplier");
        }
    }
}
