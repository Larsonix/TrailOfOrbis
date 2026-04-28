package io.github.larsonix.trailoforbis.skilltree.calculation;

import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AggregatedModifiers - collects and organizes modifiers by stat and type.
 */
@ExtendWith(MockitoExtension.class)
class AggregatedModifiersTest {

    // ═══════════════════════════════════════════════════════════════════
    // FLAT SUM TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getFlatSum")
    class FlatSumTests {

        @Test
        @DisplayName("Should return 0 for stat with no modifiers")
        void shouldReturnZeroForNoModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder().build();

            assertEquals(0, modifiers.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
        }

        @Test
        @DisplayName("Should sum flat modifiers")
        void shouldSumFlatModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 3, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 10, ModifierType.FLAT))
                .build();

            assertEquals(8, modifiers.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
            assertEquals(10, modifiers.getFlatSum(StatType.MAX_HEALTH), 0.001f);
        }

        @Test
        @DisplayName("Should handle negative flat modifiers")
        void shouldHandleNegativeFlatModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 10, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, -3, ModifierType.FLAT))
                .build();

            assertEquals(7, modifiers.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERCENT SUM TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getPercentSum")
    class PercentSumTests {

        @Test
        @DisplayName("Should return 0 for stat with no percent modifiers")
        void shouldReturnZeroForNoPercentModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder().build();

            assertEquals(0, modifiers.getPercentSum(StatType.PHYSICAL_DAMAGE_PERCENT), 0.001f);
        }

        @Test
        @DisplayName("Should sum percent modifiers additively")
        void shouldSumPercentModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE_PERCENT, 10, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE_PERCENT, 5, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 20, ModifierType.FLAT)) // Not PERCENT
                .build();

            assertEquals(15, modifiers.getPercentSum(StatType.PHYSICAL_DAMAGE_PERCENT), 0.001f);
        }

        @Test
        @DisplayName("Should not mix FLAT and PERCENT modifiers")
        void shouldNotMixFlatAndPercentModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 10, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.PERCENT))
                .build();

            // Only FLAT modifiers should be summed in getFlatSum
            assertEquals(10, modifiers.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
            // PERCENT modifiers are separate
            assertEquals(5, modifiers.getPercentSum(StatType.PHYSICAL_DAMAGE), 0.001f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MULTIPLIERS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getMultipliers")
    class MultipliersTests {

        @Test
        @DisplayName("Should return empty list for stat with no multipliers")
        void shouldReturnEmptyListForNoMultipliers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder().build();

            assertTrue(modifiers.getMultipliers(StatType.PHYSICAL_DAMAGE).isEmpty());
        }

        @Test
        @DisplayName("Should return list of multipliers")
        void shouldReturnListOfMultipliers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20, ModifierType.MULTIPLIER))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 50, ModifierType.MULTIPLIER))
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 10, ModifierType.MULTIPLIER))
                .build();

            List<Float> multipliers = modifiers.getMultipliers(StatType.PHYSICAL_DAMAGE);
            assertEquals(2, multipliers.size());
            assertTrue(multipliers.contains(20f));
            assertTrue(multipliers.contains(50f));
        }

        @Test
        @DisplayName("Should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20, ModifierType.MULTIPLIER))
                .build();

            List<Float> multipliers = modifiers.getMultipliers(StatType.PHYSICAL_DAMAGE);

            assertThrows(UnsupportedOperationException.class, () ->
                multipliers.add(10f));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HAS MODIFIERS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("hasModifiers")
    class HasModifiersTests {

        @Test
        @DisplayName("Should return false for empty modifiers")
        void shouldReturnFalseForEmptyModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder().build();

            assertFalse(modifiers.hasModifiers(StatType.PHYSICAL_DAMAGE));
        }

        @Test
        @DisplayName("Should return true for stat with modifiers")
        void shouldReturnTrueForStatWithModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 10, ModifierType.FLAT))
                .build();

            assertTrue(modifiers.hasModifiers(StatType.PHYSICAL_DAMAGE));
            assertFalse(modifiers.hasModifiers(StatType.MAX_HEALTH));
        }

        @Test
        @DisplayName("Should return true for any modifier type")
        void shouldReturnTrueForAnyModifierType() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.CRITICAL_CHANCE, 1, ModifierType.FLAT))
                .build();

            assertTrue(modifiers.hasModifiers(StatType.CRITICAL_CHANCE));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Should build with single modifier")
        void shouldBuildWithSingleModifier() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.FLAT))
                .build();

            assertEquals(5, modifiers.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
        }

        @Test
        @DisplayName("Should add multiple modifiers with addAllModifiers")
        void shouldAddMultipleModifiers() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addAllModifiers(List.of(
                    new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.FLAT),
                    new StatModifier(StatType.CRITICAL_CHANCE, 1, ModifierType.FLAT),
                    new StatModifier(StatType.MAX_HEALTH, 10, ModifierType.FLAT)
                ))
                .build();

            assertEquals(5, modifiers.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
            assertEquals(1, modifiers.getFlatSum(StatType.CRITICAL_CHANCE), 0.001f);
            assertEquals(10, modifiers.getFlatSum(StatType.MAX_HEALTH), 0.001f);
        }

        @Test
        @DisplayName("Should allow chaining")
        void shouldAllowChaining() {
            AggregatedModifiers modifiers = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.CRITICAL_CHANCE, 1, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 10, ModifierType.FLAT))
                .build();

            assertNotNull(modifiers);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLEX SCENARIO TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should handle complex modifier scenario")
    void shouldHandleComplexModifierScenario() {
        // Simulate aggregating from multiple skill nodes
        AggregatedModifiers modifiers = AggregatedModifiers.builder()
            // Node 1: +5 flat phys, +10% phys
            .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.FLAT))
            .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE_PERCENT, 10, ModifierType.PERCENT))
            // Node 2: +3 flat phys, +20% more (multiplier)
            .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 3, ModifierType.FLAT))
            .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20, ModifierType.MULTIPLIER))
            // Node 3: +2% crit chance (flat)
            .addModifier(new StatModifier(StatType.CRITICAL_CHANCE, 2, ModifierType.FLAT))
            .build();

        // Verify flat sums
        assertEquals(8, modifiers.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
        assertEquals(2, modifiers.getFlatSum(StatType.CRITICAL_CHANCE), 0.001f);

        // Verify percent sums
        assertEquals(10, modifiers.getPercentSum(StatType.PHYSICAL_DAMAGE_PERCENT), 0.001f);

        // Verify multipliers are separate
        List<Float> multipliers = modifiers.getMultipliers(StatType.PHYSICAL_DAMAGE);
        assertEquals(1, multipliers.size());
        assertEquals(20, multipliers.get(0), 0.001f);

        // Verify hasModifiers
        assertTrue(modifiers.hasModifiers(StatType.PHYSICAL_DAMAGE));
        assertTrue(modifiers.hasModifiers(StatType.CRITICAL_CHANCE));
        assertFalse(modifiers.hasModifiers(StatType.MAX_HEALTH));
    }

    @Test
    @DisplayName("Should handle large number of modifiers")
    void shouldHandleLargeNumberOfModifiers() {
        AggregatedModifiers.Builder builder = AggregatedModifiers.builder();

        // Add 100 modifiers
        for (int i = 0; i < 100; i++) {
            builder.addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 1, ModifierType.FLAT));
        }

        AggregatedModifiers modifiers = builder.build();

        assertEquals(100, modifiers.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
    }
}
