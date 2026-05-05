package io.github.larsonix.trailoforbis.gear.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WeaponImplicit} and {@link ArmorImplicit} record methods.
 *
 * <p>Focuses on {@code withPreservedPercentile()} — the mathematical core of
 * the Threshold Stone proportionality fix.
 */
class WeaponImplicitTest {

    // =========================================================================
    // WeaponImplicit.withPreservedPercentile
    // =========================================================================

    @Nested
    @DisplayName("WeaponImplicit.withPreservedPercentile")
    class WeaponPreservedPercentileTests {

        @Test
        @DisplayName("50th percentile maps to midpoint of new range")
        void midpoint_MapsToMidpoint() {
            WeaponImplicit original = WeaponImplicit.of("physical_damage", 10.0, 20.0, 15.0);
            assertEquals(0.5, original.rollPercentile(), 0.001);

            WeaponImplicit rescaled = original.withPreservedPercentile(100.0, 200.0);

            assertEquals(150.0, rescaled.rolledValue(), 0.001);
            assertEquals(0.5, rescaled.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("0th percentile maps to new minimum")
        void minimum_MapsToNewMin() {
            WeaponImplicit original = WeaponImplicit.of("physical_damage", 10.0, 20.0, 10.0);

            WeaponImplicit rescaled = original.withPreservedPercentile(100.0, 200.0);

            assertEquals(100.0, rescaled.rolledValue(), 0.001);
        }

        @Test
        @DisplayName("100th percentile maps to new maximum")
        void maximum_MapsToNewMax() {
            WeaponImplicit original = WeaponImplicit.of("physical_damage", 10.0, 20.0, 20.0);

            WeaponImplicit rescaled = original.withPreservedPercentile(100.0, 200.0);

            assertEquals(200.0, rescaled.rolledValue(), 0.001);
        }

        @Test
        @DisplayName("Arbitrary percentile preserved exactly")
        void arbitraryPercentile_PreservedExactly() {
            // 73rd percentile: (17.3 - 10) / (20 - 10) = 0.73
            WeaponImplicit original = WeaponImplicit.of("physical_damage", 10.0, 20.0, 17.3);

            WeaponImplicit rescaled = original.withPreservedPercentile(50.0, 150.0);

            // Expected: 50 + 0.73 * (150 - 50) = 50 + 73 = 123
            assertEquals(123.0, rescaled.rolledValue(), 0.01);
            assertEquals(original.rollPercentile(), rescaled.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("Flat range (min == max) returns min regardless of percentile")
        void flatRange_OriginalFlatRange() {
            WeaponImplicit original = WeaponImplicit.of("physical_damage", 15.0, 15.0, 15.0);
            // rollPercentile() returns 1.0 for flat ranges

            WeaponImplicit rescaled = original.withPreservedPercentile(100.0, 200.0);

            // percentile=1.0 → maps to max of new range
            assertEquals(200.0, rescaled.rolledValue(), 0.001);
        }

        @Test
        @DisplayName("New flat range (min == max) always returns min")
        void flatRange_NewFlatRange() {
            WeaponImplicit original = WeaponImplicit.of("physical_damage", 10.0, 20.0, 15.0);

            WeaponImplicit rescaled = original.withPreservedPercentile(42.0, 42.0);

            assertEquals(42.0, rescaled.rolledValue(), 0.001);
        }

        @Test
        @DisplayName("Damage type is preserved")
        void damageType_Preserved() {
            WeaponImplicit original = WeaponImplicit.of("spell_damage", 10.0, 20.0, 15.0);

            WeaponImplicit rescaled = original.withPreservedPercentile(100.0, 200.0);

            assertEquals("spell_damage", rescaled.damageType());
        }

        @Test
        @DisplayName("Range shrinks correctly (level decrease scenario)")
        void rangeShrinks_PercentilePreserved() {
            WeaponImplicit original = WeaponImplicit.of("physical_damage", 100.0, 200.0, 175.0);
            // 75th percentile

            WeaponImplicit rescaled = original.withPreservedPercentile(10.0, 20.0);

            assertEquals(17.5, rescaled.rolledValue(), 0.001);
            assertEquals(0.75, rescaled.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("Fuzz: percentile always preserved for random inputs")
        void fuzz_PercentileAlwaysPreserved() {
            Random rng = new Random(12345);

            for (int i = 0; i < 1000; i++) {
                double min = rng.nextDouble() * 100;
                double max = min + rng.nextDouble() * 100 + 0.01; // ensure max > min
                double value = min + rng.nextDouble() * (max - min);

                WeaponImplicit original = WeaponImplicit.of("physical_damage", min, max, value);
                double originalPercentile = original.rollPercentile();

                double newMin = rng.nextDouble() * 500;
                double newMax = newMin + rng.nextDouble() * 500 + 0.01;

                WeaponImplicit rescaled = original.withPreservedPercentile(newMin, newMax);

                assertEquals(originalPercentile, rescaled.rollPercentile(), 0.0001,
                    String.format("Iteration %d: percentile mismatch (original=%.4f, rescaled=%.4f)",
                        i, originalPercentile, rescaled.rollPercentile()));
                assertTrue(rescaled.rolledValue() >= rescaled.minValue() - 0.0001);
                assertTrue(rescaled.rolledValue() <= rescaled.maxValue() + 0.0001);
            }
        }
    }

    // =========================================================================
    // ArmorImplicit.withPreservedPercentile
    // =========================================================================

    @Nested
    @DisplayName("ArmorImplicit.withPreservedPercentile")
    class ArmorPreservedPercentileTests {

        @Test
        @DisplayName("50th percentile maps to midpoint of new range")
        void midpoint_MapsToMidpoint() {
            ArmorImplicit original = ArmorImplicit.of("armor", 10.0, 20.0, 15.0);

            ArmorImplicit rescaled = original.withPreservedPercentile(100.0, 200.0);

            assertEquals(150.0, rescaled.rolledValue(), 0.001);
            assertEquals(0.5, rescaled.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("Defense type is preserved")
        void defenseType_Preserved() {
            ArmorImplicit original = ArmorImplicit.of("evasion", 10.0, 20.0, 15.0);

            ArmorImplicit rescaled = original.withPreservedPercentile(100.0, 200.0);

            assertEquals("evasion", rescaled.defenseType());
        }

        @Test
        @DisplayName("Fuzz: percentile always preserved for random inputs")
        void fuzz_PercentileAlwaysPreserved() {
            Random rng = new Random(54321);

            for (int i = 0; i < 1000; i++) {
                double min = rng.nextDouble() * 100;
                double max = min + rng.nextDouble() * 100 + 0.01;
                double value = min + rng.nextDouble() * (max - min);

                ArmorImplicit original = ArmorImplicit.of("armor", min, max, value);
                double originalPercentile = original.rollPercentile();

                double newMin = rng.nextDouble() * 500;
                double newMax = newMin + rng.nextDouble() * 500 + 0.01;

                ArmorImplicit rescaled = original.withPreservedPercentile(newMin, newMax);

                assertEquals(originalPercentile, rescaled.rollPercentile(), 0.0001,
                    String.format("Iteration %d: percentile mismatch", i));
            }
        }
    }

    // =========================================================================
    // rollPercentile edge cases
    // =========================================================================

    @Nested
    @DisplayName("rollPercentile Edge Cases")
    class RollPercentileTests {

        @Test
        @DisplayName("Flat range returns 1.0")
        void flatRange_Returns1() {
            WeaponImplicit flat = WeaponImplicit.of("physical_damage", 42.0, 42.0, 42.0);
            assertEquals(1.0, flat.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("Min roll returns 0.0")
        void minRoll_Returns0() {
            WeaponImplicit min = WeaponImplicit.of("physical_damage", 10.0, 20.0, 10.0);
            assertEquals(0.0, min.rollPercentile(), 0.001);
        }

        @Test
        @DisplayName("Max roll returns 1.0")
        void maxRoll_Returns1() {
            WeaponImplicit max = WeaponImplicit.of("physical_damage", 10.0, 20.0, 20.0);
            assertEquals(1.0, max.rollPercentile(), 0.001);
        }
    }
}
