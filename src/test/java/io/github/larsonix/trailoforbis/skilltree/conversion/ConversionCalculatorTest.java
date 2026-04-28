package io.github.larsonix.trailoforbis.skilltree.conversion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConversionCalculator - PoE-style damage conversion system.
 *
 * <p>Conversion follows PoE-style rules:
 * <ol>
 *   <li>Sort conversions by source element priority (Physical → Elemental → Chaos)</li>
 *   <li>Apply each conversion in order</li>
 *   <li>Converted damage can be converted again by later conversions</li>
 *   <li>Total conversion from a source cannot exceed 100%</li>
 * </ol>
 *
 * <p>Example flow for Physical with 50% → Fire and 30% → Cold:
 * <pre>
 * Input: 100 Physical
 * Step 1: 50 Physical → 50 Fire, 50 Physical remains
 * Step 2: 15 Physical → 15 Cold, 35 Physical remains
 * Output: 35 Physical, 50 Fire, 15 Cold
 * </pre>
 *
 * <p>Wait, let me re-read the code... The implementation actually converts from
 * the remaining physical proportionally:
 * <pre>
 * Input: 100 Physical with 50% Fire + 30% Cold = 80% total
 * Fire: 100 × 50% = 50
 * Cold: 100 × 30% = 30
 * Remaining: 100 - 80 = 20 Physical
 * </pre>
 */
class ConversionCalculatorTest {

    private ConversionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ConversionCalculator();
    }

    // =========================================================================
    // EMPTY CONVERSIONS TESTS
    // =========================================================================

    @Nested
    @DisplayName("Empty Conversions")
    class EmptyConversionsTests {

        @Test
        @DisplayName("Empty conversions returns copy of input")
        void applyConversions_EmptyList_ReturnsCopy() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            Map<DamageElement, Float> result = calculator.applyConversions(input, List.of());

            assertEquals(100f, result.get(DamageElement.PHYSICAL), 0.01f);
            assertNotSame(input, result, "Should return a copy, not the same instance");
        }

        @Test
        @DisplayName("Empty input returns empty output")
        void applyConversions_EmptyInput_ReturnsEmpty() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 50f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            assertTrue(result.isEmpty() || result.values().stream().allMatch(v -> v <= 0),
                "Empty input should produce empty output");
        }
    }

    // =========================================================================
    // SINGLE CONVERSION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Single Conversion")
    class SingleConversionTests {

        @Test
        @DisplayName("50% physical to fire: 100 phys → 50 phys + 50 fire")
        void singleConversion_50PercentPhysToFire() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 50f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            assertEquals(50f, result.getOrDefault(DamageElement.PHYSICAL, 0f), 0.01f,
                "50% should remain as physical");
            assertEquals(50f, result.getOrDefault(DamageElement.FIRE, 0f), 0.01f,
                "50% should convert to fire");
            assertEquals(100f, calculator.totalDamage(result), 0.01f,
                "Total damage should be preserved");
        }

        @Test
        @DisplayName("100% conversion removes all source damage")
        void singleConversion_100Percent() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 100f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            // Physical should be removed (near zero due to floating point)
            assertTrue(result.getOrDefault(DamageElement.PHYSICAL, 0f) < 0.01f,
                "Physical should be fully converted");
            assertEquals(100f, result.getOrDefault(DamageElement.FIRE, 0f), 0.01f,
                "All damage should be fire");
        }

        @Test
        @DisplayName("0% conversion has no effect")
        void singleConversion_0Percent_NoEffect() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            // Note: 0% is invalid per ConversionEffect.isValid(), but let's test
            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 0f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            assertEquals(100f, result.getOrDefault(DamageElement.PHYSICAL, 0f), 0.01f,
                "0% conversion should leave physical unchanged");
            assertEquals(0f, result.getOrDefault(DamageElement.FIRE, 0f), 0.01f,
                "No fire damage should be added");
        }
    }

    // =========================================================================
    // MULTIPLE CONVERSIONS SAME SOURCE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Multiple Conversions Same Source")
    class MultipleConversionsSameSourceTests {

        @Test
        @DisplayName("50% fire + 30% cold from physical")
        void multipleConversions_SameSource() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 50f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 30f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            // 80% total conversion, 20% remains physical
            assertEquals(20f, result.getOrDefault(DamageElement.PHYSICAL, 0f), 0.01f,
                "20% should remain as physical");
            assertEquals(50f, result.getOrDefault(DamageElement.FIRE, 0f), 0.01f,
                "50% should convert to fire");
            assertEquals(30f, result.getOrDefault(DamageElement.WATER, 0f), 0.01f,
                "30% should convert to cold");
            assertEquals(100f, calculator.totalDamage(result), 0.01f,
                "Total damage should be preserved");
        }

        @Test
        @DisplayName("Conversions to all elements from physical")
        void multipleConversions_AllElements() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 25f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 25f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.LIGHTNING, 25f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.VOID, 25f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            // 100% total, 0 remains physical
            assertTrue(result.getOrDefault(DamageElement.PHYSICAL, 0f) < 0.01f,
                "Physical should be fully converted");
            assertEquals(25f, result.getOrDefault(DamageElement.FIRE, 0f), 0.01f);
            assertEquals(25f, result.getOrDefault(DamageElement.WATER, 0f), 0.01f);
            assertEquals(25f, result.getOrDefault(DamageElement.LIGHTNING, 0f), 0.01f);
            assertEquals(25f, result.getOrDefault(DamageElement.VOID, 0f), 0.01f);
        }
    }

    // =========================================================================
    // 100% CAP WITH PROPORTIONAL SCALING TESTS
    // =========================================================================

    @Nested
    @DisplayName("100% Conversion Cap")
    class ConversionCapTests {

        @Test
        @DisplayName("150% total scales down proportionally to 100%")
        void conversionCap_150Percent_ScalesTo100() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            // 75% + 75% = 150% total
            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 75f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 75f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            // Should scale to 50% each (75/150 × 100 = 50)
            assertTrue(result.getOrDefault(DamageElement.PHYSICAL, 0f) < 0.01f,
                "Physical should be fully converted after 100%+ scaling");
            assertEquals(50f, result.getOrDefault(DamageElement.FIRE, 0f), 0.5f,
                "Fire should be 50% after proportional scaling");
            assertEquals(50f, result.getOrDefault(DamageElement.WATER, 0f), 0.5f,
                "Cold should be 50% after proportional scaling");
            assertEquals(100f, calculator.totalDamage(result), 0.01f,
                "Total damage should be preserved even with overcapped conversion");
        }

        @Test
        @DisplayName("300% total scales down proportionally")
        void conversionCap_300Percent_ScalesDown() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            // 100% + 100% + 100% = 300% total
            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 100f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 100f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.LIGHTNING, 100f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            // Each should get 100/300 × 100 = 33.33%
            float fire = result.getOrDefault(DamageElement.FIRE, 0f);
            float cold = result.getOrDefault(DamageElement.WATER, 0f);
            float lightning = result.getOrDefault(DamageElement.LIGHTNING, 0f);

            assertEquals(33.33f, fire, 0.5f, "Fire should be ~33.3%");
            assertEquals(33.33f, cold, 0.5f, "Cold should be ~33.3%");
            assertEquals(33.33f, lightning, 0.5f, "Lightning should be ~33.3%");
            assertEquals(100f, calculator.totalDamage(result), 1f,
                "Total damage should be preserved");
        }

        @Test
        @DisplayName("Exactly 100% cap - no scaling needed")
        void conversionCap_Exactly100_NoScaling() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 60f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 40f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            assertEquals(60f, result.getOrDefault(DamageElement.FIRE, 0f), 0.01f);
            assertEquals(40f, result.getOrDefault(DamageElement.WATER, 0f), 0.01f);
            assertTrue(result.getOrDefault(DamageElement.PHYSICAL, 0f) < 0.01f);
        }
    }

    // =========================================================================
    // CONVERSION PRIORITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Conversion Priority")
    class ConversionPriorityTests {

        @Test
        @DisplayName("Physical converts before Fire")
        void priority_PhysicalBeforeFire() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);
            input.put(DamageElement.FIRE, 50f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 50f),
                new ConversionEffect(DamageElement.FIRE, DamageElement.VOID, 50f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            // Physical: 100 → 50 phys + 50 cold
            // Fire: 50 → 25 fire + 25 chaos
            assertEquals(50f, result.getOrDefault(DamageElement.PHYSICAL, 0f), 0.01f);
            assertEquals(50f, result.getOrDefault(DamageElement.WATER, 0f), 0.01f);
            assertEquals(25f, result.getOrDefault(DamageElement.FIRE, 0f), 0.01f);
            assertEquals(25f, result.getOrDefault(DamageElement.VOID, 0f), 0.01f);
        }

        @Test
        @DisplayName("Conversion priority order: Physical < Fire < Cold < Lightning < Chaos")
        void priority_OrderIsCorrect() {
            // Verify the priority values
            assertTrue(DamageElement.PHYSICAL.getPriority() < DamageElement.FIRE.getPriority());
            assertTrue(DamageElement.FIRE.getPriority() < DamageElement.WATER.getPriority());
            assertTrue(DamageElement.WATER.getPriority() < DamageElement.LIGHTNING.getPriority());
            assertTrue(DamageElement.LIGHTNING.getPriority() < DamageElement.VOID.getPriority());
        }
    }

    // =========================================================================
    // TOTAL DAMAGE PRESERVATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Total Damage Preservation")
    class TotalDamagePreservationTests {

        @Test
        @DisplayName("Total damage preserved with single conversion")
        void totalDamage_PreservedSingleConversion() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 123.45f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 67f)
            );

            float inputTotal = calculator.totalDamage(input);
            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);
            float outputTotal = calculator.totalDamage(result);

            assertEquals(inputTotal, outputTotal, 0.01f,
                "Total damage must be preserved after conversion");
        }

        @Test
        @DisplayName("Total damage preserved with multiple conversions")
        void totalDamage_PreservedMultipleConversions() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 200f);
            input.put(DamageElement.FIRE, 50f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 25f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 25f),
                new ConversionEffect(DamageElement.FIRE, DamageElement.VOID, 50f)
            );

            float inputTotal = calculator.totalDamage(input);
            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);
            float outputTotal = calculator.totalDamage(result);

            assertEquals(inputTotal, outputTotal, 0.01f,
                "Total damage must be preserved");
        }

        @Test
        @DisplayName("Total damage preserved with overcapped conversions")
        void totalDamage_PreservedOvercapped() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 500f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 80f),
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 80f)
            );

            float inputTotal = calculator.totalDamage(input);
            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);
            float outputTotal = calculator.totalDamage(result);

            assertEquals(inputTotal, outputTotal, 0.01f,
                "Total damage must be preserved even with overcapped conversions");
        }
    }

    // =========================================================================
    // ZERO/EDGE CASES TESTS
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Zero damage input")
        void zeroDamageInput() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 0f);

            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 50f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            assertEquals(0f, calculator.totalDamage(result), 0.01f,
                "Zero input should produce zero output");
        }

        @Test
        @DisplayName("Invalid conversion (same source and target) is filtered")
        void invalidConversion_SameSourceTarget_Filtered() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            // Physical to Physical is invalid (canConvertTo returns false)
            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.PHYSICAL, 50f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            // Invalid conversion should be filtered, so no change
            assertEquals(100f, result.getOrDefault(DamageElement.PHYSICAL, 0f), 0.01f,
                "Invalid conversion should be ignored");
        }

        @Test
        @DisplayName("Invalid conversion (backwards) is filtered")
        void invalidConversion_Backwards_Filtered() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.FIRE, 100f);

            // Fire to Physical is invalid (higher to lower priority)
            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.FIRE, DamageElement.PHYSICAL, 50f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            // Invalid conversion should be filtered
            assertEquals(100f, result.getOrDefault(DamageElement.FIRE, 0f), 0.01f,
                "Backwards conversion should be ignored");
            assertEquals(0f, result.getOrDefault(DamageElement.PHYSICAL, 0f), 0.01f);
        }

        @Test
        @DisplayName("Negative conversion percent is filtered as invalid")
        void negativeConversionPercent_Filtered() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 100f);

            // Negative percent should be invalid
            List<ConversionEffect> conversions = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, -50f)
            );

            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);

            assertEquals(100f, result.getOrDefault(DamageElement.PHYSICAL, 0f), 0.01f,
                "Negative conversion should be ignored");
        }

        @Test
        @DisplayName("Large conversion list performance (100 conversions)")
        void largeConversionList_Performance() {
            Map<DamageElement, Float> input = new EnumMap<>(DamageElement.class);
            input.put(DamageElement.PHYSICAL, 10000f);

            // Create 100 small conversions
            List<ConversionEffect> conversions = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                conversions.add(new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 1f));
            }

            long startTime = System.nanoTime();
            Map<DamageElement, Float> result = calculator.applyConversions(input, conversions);
            long endTime = System.nanoTime();

            // Should complete in reasonable time (< 100ms)
            long durationMs = (endTime - startTime) / 1_000_000;
            assertTrue(durationMs < 100, "Processing should be fast, took: " + durationMs + "ms");

            // Total should be capped at 100% conversion
            assertEquals(10000f, calculator.totalDamage(result), 1f,
                "Total damage should be preserved");
        }
    }

    // =========================================================================
    // AGGREGATE HELPER TESTS
    // =========================================================================

    @Nested
    @DisplayName("Aggregate Helper")
    class AggregateHelperTests {

        @Test
        @DisplayName("aggregate() combines multiple lists")
        void aggregate_CombinesLists() {
            List<ConversionEffect> list1 = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 25f)
            );
            List<ConversionEffect> list2 = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.WATER, 25f)
            );

            List<ConversionEffect> combined = calculator.aggregate(list1, list2);

            assertEquals(2, combined.size());
        }

        @Test
        @DisplayName("aggregate() handles null lists")
        void aggregate_HandlesNullLists() {
            List<ConversionEffect> list1 = List.of(
                new ConversionEffect(DamageElement.PHYSICAL, DamageElement.FIRE, 25f)
            );

            List<ConversionEffect> combined = calculator.aggregate(list1, null);

            assertEquals(1, combined.size());
        }

        @Test
        @DisplayName("aggregate() with all nulls returns empty")
        void aggregate_AllNulls_ReturnsEmpty() {
            List<ConversionEffect> combined = calculator.aggregate(null, null, null);

            assertTrue(combined.isEmpty());
        }
    }

    // =========================================================================
    // TOTAL DAMAGE HELPER TESTS
    // =========================================================================

    @Nested
    @DisplayName("Total Damage Helper")
    class TotalDamageHelperTests {

        @Test
        @DisplayName("totalDamage() sums all elements")
        void totalDamage_SumsAllElements() {
            Map<DamageElement, Float> damage = new EnumMap<>(DamageElement.class);
            damage.put(DamageElement.PHYSICAL, 100f);
            damage.put(DamageElement.FIRE, 50f);
            damage.put(DamageElement.WATER, 25f);

            assertEquals(175f, calculator.totalDamage(damage), 0.01f);
        }

        @Test
        @DisplayName("totalDamage() handles empty map")
        void totalDamage_EmptyMap() {
            Map<DamageElement, Float> damage = new EnumMap<>(DamageElement.class);

            assertEquals(0f, calculator.totalDamage(damage), 0.01f);
        }
    }
}
