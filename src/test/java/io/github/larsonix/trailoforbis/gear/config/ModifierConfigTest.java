package io.github.larsonix.trailoforbis.gear.config;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModifierConfig and its nested types.
 */
class ModifierConfigTest {

    // =========================================================================
    // MODIFIER DEFINITION TESTS
    // =========================================================================

    @Nested
    @DisplayName("ModifierDefinition")
    class ModifierDefinitionTests {

        @Test
        @DisplayName("Valid definition creates successfully")
        void validDefinition_createsSuccessfully() {
            ModifierDefinition def = new ModifierDefinition(
                "sharp",
                "Sharp",
                "physical_damage",
                StatType.FLAT,
                1.0,
                3.0,
                0.2,
                100,
                null,
                null
            );

            assertEquals("sharp", def.id());
            assertEquals("Sharp", def.displayName());
            assertEquals("physical_damage", def.stat());
            assertEquals(StatType.FLAT, def.statType());
            assertEquals(1.0, def.baseMin());
            assertEquals(3.0, def.baseMax());
            assertEquals(0.2, def.scalePerLevel());
            assertEquals(100, def.weight());
            assertNull(def.requiredAttribute());
            assertNull(def.allowedSlots());
        }

        @Test
        @DisplayName("Definition with required attribute")
        void definitionWithRequiredAttribute_createsSuccessfully() {
            ModifierDefinition def = new ModifierDefinition(
                "blazing",
                "Blazing",
                "fire_damage",
                StatType.FLAT,
                1.0,
                2.5,
                0.15,
                50,
                AttributeType.FIRE,
                null
            );

            assertEquals(AttributeType.FIRE, def.requiredAttribute());
        }

        @Test
        @DisplayName("Definition with allowed slots")
        void definitionWithAllowedSlots_createsSuccessfully() {
            ModifierDefinition def = new ModifierDefinition(
                "swift",
                "Swift",
                "attack_speed",
                StatType.PERCENT,
                1.0,
                3.0,
                0.1,
                25,
                AttributeType.LIGHTNING,
                Set.of("weapon", "hands")
            );

            assertNotNull(def.allowedSlots());
            assertTrue(def.allowedSlots().contains("weapon"));
            assertTrue(def.allowedSlots().contains("hands"));
        }

        @Test
        @DisplayName("Empty ID throws exception")
        void emptyId_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new ModifierDefinition(
                    "",
                    "Display",
                    "stat",
                    StatType.FLAT,
                    1,
                    2,
                    0.1,
                    10,
                    null,
                    null
                )
            );
            assertTrue(ex.getMessage().contains("id cannot be blank"));
        }

        @Test
        @DisplayName("Blank display name throws exception")
        void blankDisplayName_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new ModifierDefinition(
                    "id",
                    "   ",
                    "stat",
                    StatType.FLAT,
                    1,
                    2,
                    0.1,
                    10,
                    null,
                    null
                )
            );
            assertTrue(ex.getMessage().contains("displayName cannot be blank"));
        }

        @Test
        @DisplayName("Null stat throws exception")
        void nullStat_throwsException() {
            assertThrows(
                NullPointerException.class,
                () -> new ModifierDefinition(
                    "id",
                    "Display",
                    null,
                    StatType.FLAT,
                    1,
                    2,
                    0.1,
                    10,
                    null,
                    null
                )
            );
        }

        @Test
        @DisplayName("Negative base min creates successfully for reduction modifiers")
        void negativeBaseMin_createsSuccessfully() {
            // Negative base_min is valid for reduction modifiers (e.g., -5% gravity reduction)
            ModifierDefinition def = new ModifierDefinition(
                "trajectory",
                "Trajectory",
                "projectile_gravity_percent",
                StatType.FLAT,
                -5,
                2,
                0.1,
                10,
                null,
                null
            );

            assertEquals(-5.0, def.baseMin());
            assertEquals(2.0, def.baseMax());
        }

        @Test
        @DisplayName("Negative range creates successfully for reduction modifiers")
        void negativeRange_createsSuccessfully() {
            // For reduction modifiers: -5% (min) to -15% (max) is semantically valid
            // even though -5 > -15 numerically
            ModifierDefinition def = new ModifierDefinition(
                "trajectory",
                "Trajectory",
                "projectile_gravity_percent",
                StatType.FLAT,
                -5,  // small reduction
                -15, // large reduction
                -0.5,
                10,
                null,
                null
            );

            assertEquals(-5.0, def.baseMin());
            assertEquals(-15.0, def.baseMax());
        }

        @Test
        @DisplayName("Negative scale per level creates successfully for reduction modifiers")
        void negativeScalePerLevel_createsSuccessfully() {
            // Negative scaling is valid (reduction grows stronger with level)
            ModifierDefinition def = new ModifierDefinition(
                "trajectory",
                "Trajectory",
                "projectile_gravity_percent",
                StatType.FLAT,
                1,
                2,
                -0.1,
                10,
                null,
                null
            );

            assertEquals(-0.1, def.scalePerLevel());
        }

        @Test
        @DisplayName("Negative weight throws exception")
        void negativeWeight_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new ModifierDefinition(
                    "id",
                    "Display",
                    "stat",
                    StatType.FLAT,
                    1,
                    2,
                    0.1,
                    -10,
                    null,
                    null
                )
            );
            assertTrue(ex.getMessage().contains("weight cannot be negative"));
        }
    }

    // =========================================================================
    // SLOT FILTERING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Slot Filtering")
    class SlotFilteringTests {

        @Test
        @DisplayName("Null allowed slots allows all slots")
        void nullAllowedSlots_allowsAll() {
            ModifierDefinition def = new ModifierDefinition(
                "sharp",
                "Sharp",
                "physical_damage",
                StatType.FLAT,
                1,
                3,
                0.2,
                100,
                null,
                null
            );

            assertTrue(def.isAllowedOnSlot("weapon"));
            assertTrue(def.isAllowedOnSlot("chest"));
            assertTrue(def.isAllowedOnSlot("anything"));
        }

        @Test
        @DisplayName("Defined allowed slots restricts correctly")
        void definedAllowedSlots_restrictsCorrectly() {
            ModifierDefinition def = new ModifierDefinition(
                "swift",
                "Swift",
                "attack_speed",
                StatType.PERCENT,
                1,
                3,
                0.1,
                25,
                null,
                Set.of("weapon", "hands")
            );

            assertTrue(def.isAllowedOnSlot("weapon"));
            assertTrue(def.isAllowedOnSlot("hands"));
            assertFalse(def.isAllowedOnSlot("chest"));
            assertFalse(def.isAllowedOnSlot("feet"));
        }

        @Test
        @DisplayName("Slot check is case insensitive")
        void slotCheck_caseInsensitive() {
            ModifierDefinition def = new ModifierDefinition(
                "swift",
                "Swift",
                "attack_speed",
                StatType.PERCENT,
                1,
                3,
                0.1,
                25,
                null,
                Set.of("weapon", "hands")
            );

            assertTrue(def.isAllowedOnSlot("WEAPON"));
            assertTrue(def.isAllowedOnSlot("Weapon"));
            assertTrue(def.isAllowedOnSlot("weapon"));
        }
    }

    // =========================================================================
    // VALUE CALCULATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Value Calculation")
    class ValueCalculationTests {

        @Test
        @DisplayName("Calculate range at level 1 returns base range")
        void calculateRange_level1_returnsBaseRange() {
            ModifierDefinition def = new ModifierDefinition(
                "test",
                "Test",
                "stat",
                StatType.FLAT,
                5.0,
                10.0,
                0.5,
                100,
                null,
                null
            );

            ValueRange range = def.calculateRange(1);

            // At level 1, multiplier=1.0 so effectiveLevel=0, no bonus
            assertEquals(5.0, range.min(), 0.01);
            assertEquals(10.0, range.max(), 0.01);
        }

        @Test
        @DisplayName("Calculate range at level 50 scales correctly")
        void calculateRange_level50_scalesCorrectly() {
            ModifierDefinition def = new ModifierDefinition(
                "test",
                "Test",
                "stat",
                StatType.FLAT,
                5.0,
                10.0,
                0.5,
                100,
                null,
                null
            );

            ValueRange range = def.calculateRange(50);

            // At level 50, effectiveLevel ≈ 31.6, bonus ≈ 15.8
            assertEquals(20.8, range.min(), 1.0);
            assertEquals(25.8, range.max(), 1.0);
        }

        @Test
        @DisplayName("Calculate value with roll factor 0 returns min")
        void calculateValue_rollFactor0_returnsMin() {
            ModifierDefinition def = new ModifierDefinition(
                "test",
                "Test",
                "stat",
                StatType.FLAT,
                10.0,
                20.0,
                0.0,
                100,
                null,
                null
            );

            double value = def.calculateValue(1, 0.0);

            assertEquals(10.0, value);
        }

        @Test
        @DisplayName("Calculate value with roll factor 1 returns max")
        void calculateValue_rollFactor1_returnsMax() {
            ModifierDefinition def = new ModifierDefinition(
                "test",
                "Test",
                "stat",
                StatType.FLAT,
                10.0,
                20.0,
                0.0,
                100,
                null,
                null
            );

            double value = def.calculateValue(1, 1.0);

            assertEquals(20.0, value);
        }

        @Test
        @DisplayName("Calculate value with roll factor 0.5 returns middle")
        void calculateValue_rollFactor05_returnsMiddle() {
            ModifierDefinition def = new ModifierDefinition(
                "test",
                "Test",
                "stat",
                StatType.FLAT,
                10.0,
                20.0,
                0.0,
                100,
                null,
                null
            );

            double value = def.calculateValue(1, 0.5);

            assertEquals(15.0, value);
        }
    }

    // =========================================================================
    // VALUE RANGE TESTS
    // =========================================================================

    @Nested
    @DisplayName("ValueRange")
    class ValueRangeTests {

        @Test
        @DisplayName("Valid range creates successfully")
        void validRange_createsSuccessfully() {
            ValueRange range = new ValueRange(10, 20);

            assertEquals(10, range.min());
            assertEquals(20, range.max());
        }

        @Test
        @DisplayName("Negative range creates successfully for reduction modifiers")
        void negativeRange_createsSuccessfully() {
            // For reduction modifiers, min=-5 (small reduction), max=-15 (large reduction)
            // is semantically valid even though -5 > -15 numerically
            ValueRange range = new ValueRange(-5, -15);

            assertEquals(-5, range.min());
            assertEquals(-15, range.max());
        }

        @Test
        @DisplayName("Interpolate clamps input below 0")
        void interpolate_clampsInputBelowZero() {
            ValueRange range = new ValueRange(10, 20);

            assertEquals(10.0, range.interpolate(-0.5)); // Clamped to 0
        }

        @Test
        @DisplayName("Interpolate clamps input above 1")
        void interpolate_clampsInputAbove1() {
            ValueRange range = new ValueRange(10, 20);

            assertEquals(20.0, range.interpolate(1.5)); // Clamped to 1
        }

        @Test
        @DisplayName("Interpolate at 0.5 returns middle")
        void interpolate_at05_returnsMiddle() {
            ValueRange range = new ValueRange(10, 20);

            assertEquals(15.0, range.interpolate(0.5));
        }
    }

    // =========================================================================
    // STAT TYPE TESTS
    // =========================================================================

    @Nested
    @DisplayName("StatType")
    class StatTypeTests {

        @Test
        @DisplayName("fromString 'flat' returns FLAT")
        void fromString_flat_returnsFLAT() {
            assertEquals(StatType.FLAT, StatType.fromString("flat"));
        }

        @Test
        @DisplayName("fromString 'percent' returns PERCENT")
        void fromString_percent_returnsPERCENT() {
            assertEquals(StatType.PERCENT, StatType.fromString("percent"));
        }

        @Test
        @DisplayName("fromString '%' returns PERCENT")
        void fromString_percentSymbol_returnsPERCENT() {
            assertEquals(StatType.PERCENT, StatType.fromString("%"));
        }

        @Test
        @DisplayName("fromString is case insensitive")
        void fromString_caseInsensitive() {
            assertEquals(StatType.FLAT, StatType.fromString("FLAT"));
            assertEquals(StatType.FLAT, StatType.fromString("Flat"));
            assertEquals(StatType.PERCENT, StatType.fromString("PERCENT"));
        }

        @Test
        @DisplayName("fromString invalid throws exception")
        void fromString_invalid_throwsException() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> StatType.fromString("invalid")
            );
            assertTrue(ex.getMessage().contains("Invalid stat type"));
        }
    }

    // =========================================================================
    // MODIFIER CONFIG TESTS
    // =========================================================================

    @Nested
    @DisplayName("ModifierConfig")
    class ModifierConfigTests {

        private ModifierConfig createTestConfig() {
            ModifierDefinition sharp = new ModifierDefinition(
                "sharp", "Sharp", "physical_damage", StatType.FLAT,
                1, 3, 0.2, 100, null, null
            );
            ModifierDefinition swift = new ModifierDefinition(
                "swift", "Swift", "attack_speed", StatType.PERCENT,
                1, 3, 0.1, 25, AttributeType.LIGHTNING, Set.of("weapon", "hands")
            );
            ModifierDefinition ofTheWhale = new ModifierDefinition(
                "of_the_whale", "of the Whale", "max_health", StatType.FLAT,
                5, 15, 1.0, 100, AttributeType.WATER, null
            );
            ModifierDefinition ofSpeed = new ModifierDefinition(
                "of_speed", "of Speed", "movement_speed_percent", StatType.PERCENT,
                2, 5, 0.1, 25, AttributeType.LIGHTNING, Set.of("feet")
            );

            return new ModifierConfig(
                Map.of("sharp", sharp, "swift", swift),
                Map.of("of_the_whale", ofTheWhale, "of_speed", ofSpeed)
            );
        }

        @Test
        @DisplayName("Prefixes present")
        void prefixes_present() {
            ModifierConfig config = createTestConfig();

            assertFalse(config.prefixes().isEmpty());
            assertTrue(config.getPrefix("sharp").isPresent());
        }

        @Test
        @DisplayName("Suffixes present")
        void suffixes_present() {
            ModifierConfig config = createTestConfig();

            assertFalse(config.suffixes().isEmpty());
            assertTrue(config.getSuffix("of_the_whale").isPresent());
        }

        @Test
        @DisplayName("Get prefix is case insensitive")
        void getPrefix_caseInsensitive() {
            ModifierConfig config = createTestConfig();

            assertTrue(config.getPrefix("SHARP").isPresent());
            assertTrue(config.getPrefix("Sharp").isPresent());
            assertTrue(config.getPrefix("sharp").isPresent());
        }

        @Test
        @DisplayName("Get suffix is case insensitive")
        void getSuffix_caseInsensitive() {
            ModifierConfig config = createTestConfig();

            assertTrue(config.getSuffix("OF_THE_WHALE").isPresent());
        }

        @Test
        @DisplayName("Prefix list matches map")
        void prefixList_matchesMap() {
            ModifierConfig config = createTestConfig();

            assertEquals(config.prefixes().size(), config.prefixList().size());
        }

        @Test
        @DisplayName("Suffix list matches map")
        void suffixList_matchesMap() {
            ModifierConfig config = createTestConfig();

            assertEquals(config.suffixes().size(), config.suffixList().size());
        }

        @Test
        @DisplayName("Total prefix weight is sum of all weights")
        void totalPrefixWeight_isSumOfWeights() {
            ModifierConfig config = createTestConfig();

            // sharp=100, swift=25
            assertEquals(125, config.totalPrefixWeight());
        }

        @Test
        @DisplayName("Total suffix weight is sum of all weights")
        void totalSuffixWeight_isSumOfWeights() {
            ModifierConfig config = createTestConfig();

            // of_the_whale=100, of_speed=25
            assertEquals(125, config.totalSuffixWeight());
        }

        @Test
        @DisplayName("Prefixes for slot weapon includes swift")
        void prefixesForSlot_weapon_includesSwift() {
            ModifierConfig config = createTestConfig();
            List<ModifierDefinition> weaponPrefixes = config.prefixesForSlot("weapon");

            assertTrue(weaponPrefixes.stream()
                .anyMatch(m -> m.id().equals("swift")));
        }

        @Test
        @DisplayName("Prefixes for slot chest excludes swift")
        void prefixesForSlot_chest_excludesSwift() {
            ModifierConfig config = createTestConfig();
            List<ModifierDefinition> chestPrefixes = config.prefixesForSlot("chest");

            assertFalse(chestPrefixes.stream()
                .anyMatch(m -> m.id().equals("swift")));
        }

        @Test
        @DisplayName("Suffixes for slot feet includes of_speed")
        void suffixesForSlot_feet_includesOfSpeed() {
            ModifierConfig config = createTestConfig();
            List<ModifierDefinition> feetSuffixes = config.suffixesForSlot("feet");

            assertTrue(feetSuffixes.stream()
                .anyMatch(m -> m.id().equals("of_speed")));
        }

        @Test
        @DisplayName("Suffixes for slot chest excludes of_speed")
        void suffixesForSlot_chest_excludesOfSpeed() {
            ModifierConfig config = createTestConfig();
            List<ModifierDefinition> chestSuffixes = config.suffixesForSlot("chest");

            assertFalse(chestSuffixes.stream()
                .anyMatch(m -> m.id().equals("of_speed")));
        }

        @Test
        @DisplayName("Slot filtering is case insensitive")
        void slotFiltering_caseInsensitive() {
            ModifierConfig config = createTestConfig();

            List<ModifierDefinition> lower = config.prefixesForSlot("weapon");
            List<ModifierDefinition> upper = config.prefixesForSlot("WEAPON");
            List<ModifierDefinition> mixed = config.prefixesForSlot("Weapon");

            assertEquals(lower.size(), upper.size());
            assertEquals(lower.size(), mixed.size());
        }

        @Test
        @DisplayName("Total weight for slot is less than or equal to total")
        void totalWeightForSlot_lessThanOrEqualToTotal() {
            ModifierConfig config = createTestConfig();

            int weaponWeight = config.totalPrefixWeightForSlot("weapon");
            int totalWeight = config.totalPrefixWeight();

            assertTrue(weaponWeight <= totalWeight);
        }

        @Test
        @DisplayName("All stat IDs collected")
        void allStatIds_collected() {
            ModifierConfig config = createTestConfig();

            Set<String> stats = config.allStatIds();
            assertTrue(stats.contains("physical_damage"));
            assertTrue(stats.contains("max_health"));
            assertTrue(stats.contains("attack_speed"));
        }

        @Test
        @DisplayName("Prefixes map is immutable")
        void prefixes_isImmutable() {
            ModifierConfig config = createTestConfig();

            assertThrows(
                UnsupportedOperationException.class,
                () -> config.prefixes().put("test", null)
            );
        }

        @Test
        @DisplayName("Suffixes map is immutable")
        void suffixes_isImmutable() {
            ModifierConfig config = createTestConfig();

            assertThrows(
                UnsupportedOperationException.class,
                () -> config.suffixes().put("test", null)
            );
        }
    }
}
