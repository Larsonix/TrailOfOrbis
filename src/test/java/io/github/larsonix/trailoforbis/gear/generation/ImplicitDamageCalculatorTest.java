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
                true, BASE_MIN, BASE_MAX, SCALE_FACTOR, TWO_HANDED_MULTIPLIER
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
            WeaponType[] oneHandedTypes = {
                WeaponType.SWORD, WeaponType.DAGGER, WeaponType.AXE,
                WeaponType.MACE, WeaponType.CLAWS, WeaponType.CLUB,
                WeaponType.WAND, WeaponType.SPELLBOOK
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
        @DisplayName("Magic weapons get 'spell_damage' type")
        void magicWeapons_GetSpellDamageType() {
            Random random = new Random(42);

            WeaponImplicit staff = calculator.calculate(WeaponType.STAFF, 10, random);
            WeaponImplicit wand = calculator.calculate(WeaponType.WAND, 10, new Random(42));
            WeaponImplicit spellbook = calculator.calculate(WeaponType.SPELLBOOK, 10, new Random(42));

            assertEquals(ImplicitDamageCalculator.SPELL_DAMAGE, staff.damageType());
            assertEquals(ImplicitDamageCalculator.SPELL_DAMAGE, wand.damageType());
            assertEquals(ImplicitDamageCalculator.SPELL_DAMAGE, spellbook.damageType());
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
