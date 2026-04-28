package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FilterCondition} sealed interface and all 12 record implementations.
 */
class FilterConditionTest {

    // ═══════════════════════════════════════════════════════════════════
    // TEST FIXTURES
    // ═══════════════════════════════════════════════════════════════════

    private static GearData gearWithRarity(GearRarity rarity) {
        return GearData.builder().level(10).rarity(rarity).quality(50).build();
    }

    private static GearData gearWithLevel(int level) {
        return GearData.builder().level(level).rarity(GearRarity.RARE).quality(50).build();
    }

    private static GearData gearWithQuality(int quality) {
        return GearData.builder().level(10).rarity(GearRarity.RARE).quality(quality).build();
    }

    private static GearData gearWithModifiers(GearModifier... mods) {
        var builder = GearData.builder().level(10).rarity(GearRarity.MYTHIC).quality(50);
        for (GearModifier mod : mods) {
            if (mod.isPrefix()) builder.addPrefix(mod);
            else builder.addSuffix(mod);
        }
        return builder.build();
    }

    private static GearModifier prefix(String id, double value) {
        return GearModifier.of(id, id, ModifierType.PREFIX, "physical_damage", "flat", value);
    }

    private static GearModifier suffix(String id, double value) {
        return GearModifier.of(id, id, ModifierType.SUFFIX, "max_health", "flat", value);
    }

    private static GearData gearWithImplicit(String damageType, double percentile) {
        double min = 100.0;
        double max = 200.0;
        double rolled = min + (max - min) * percentile;
        return GearData.builder()
                .level(10).rarity(GearRarity.RARE).quality(50)
                .implicit(WeaponImplicit.of(damageType, min, max, rolled))
                .build();
    }

    private static GearData gearCorrupted(boolean corrupted) {
        return GearData.builder().level(10).rarity(GearRarity.RARE).quality(50)
                .corrupted(corrupted).build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // MinRarity
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MinRarity")
    class MinRarityTests {

        @Test
        @DisplayName("matches items at or above threshold")
        void matchesAtOrAbove() {
            var condition = new FilterCondition.MinRarity(GearRarity.RARE);
            assertTrue(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithRarity(GearRarity.EPIC), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithRarity(GearRarity.MYTHIC), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects items below threshold")
        void rejectsBelowThreshold() {
            var condition = new FilterCondition.MinRarity(GearRarity.RARE);
            assertFalse(condition.matches(gearWithRarity(GearRarity.COMMON), EquipmentType.SWORD));
            assertFalse(condition.matches(gearWithRarity(GearRarity.UNCOMMON), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("type is MIN_RARITY")
        void hasCorrectType() {
            assertEquals(ConditionType.MIN_RARITY, new FilterCondition.MinRarity(GearRarity.RARE).type());
        }

        @Test
        @DisplayName("describe includes threshold name")
        void describeIncludesThreshold() {
            assertTrue(new FilterCondition.MinRarity(GearRarity.EPIC).describe().contains("EPIC"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MaxRarity
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MaxRarity")
    class MaxRarityTests {

        @Test
        @DisplayName("matches items at or below threshold")
        void matchesAtOrBelow() {
            var condition = new FilterCondition.MaxRarity(GearRarity.RARE);
            assertTrue(condition.matches(gearWithRarity(GearRarity.COMMON), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithRarity(GearRarity.UNCOMMON), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects items above threshold")
        void rejectsAboveThreshold() {
            var condition = new FilterCondition.MaxRarity(GearRarity.RARE);
            assertFalse(condition.matches(gearWithRarity(GearRarity.EPIC), EquipmentType.SWORD));
            assertFalse(condition.matches(gearWithRarity(GearRarity.LEGENDARY), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("type is MAX_RARITY")
        void hasCorrectType() {
            assertEquals(ConditionType.MAX_RARITY, new FilterCondition.MaxRarity(GearRarity.RARE).type());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EquipmentSlotCondition
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EquipmentSlotCondition")
    class EquipmentSlotTests {

        @Test
        @DisplayName("matches equipment in specified slots")
        void matchesSpecifiedSlots() {
            var condition = new FilterCondition.EquipmentSlotCondition(Set.of("weapon", "shield"));
            assertTrue(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.SHIELD));
        }

        @Test
        @DisplayName("rejects equipment not in specified slots")
        void rejectsOtherSlots() {
            var condition = new FilterCondition.EquipmentSlotCondition(Set.of("weapon"));
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.PLATE_CHEST));
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.LEATHER_HEAD));
        }

        @Test
        @DisplayName("armor slots resolve correctly")
        void armorSlotsResolve() {
            var headOnly = new FilterCondition.EquipmentSlotCondition(Set.of("head"));
            assertTrue(headOnly.matches(gearWithRarity(GearRarity.RARE), EquipmentType.CLOTH_HEAD));
            assertTrue(headOnly.matches(gearWithRarity(GearRarity.RARE), EquipmentType.PLATE_HEAD));
            assertFalse(headOnly.matches(gearWithRarity(GearRarity.RARE), EquipmentType.PLATE_CHEST));
        }

        @Test
        @DisplayName("slots are immutable")
        void slotsAreImmutable() {
            var mutableSet = new java.util.HashSet<>(Set.of("weapon"));
            var condition = new FilterCondition.EquipmentSlotCondition(mutableSet);
            mutableSet.add("head");
            // Condition should not be affected by external mutation
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.PLATE_HEAD));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WeaponTypeCondition
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WeaponTypeCondition")
    class WeaponTypeTests {

        @Test
        @DisplayName("matches specified weapon types")
        void matchesSpecifiedTypes() {
            var condition = new FilterCondition.WeaponTypeCondition(Set.of(WeaponType.SWORD, WeaponType.AXE));
            assertTrue(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.AXE));
        }

        @Test
        @DisplayName("rejects non-matching weapon types")
        void rejectsOtherTypes() {
            var condition = new FilterCondition.WeaponTypeCondition(Set.of(WeaponType.SWORD));
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.STAFF));
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.DAGGER));
        }

        @Test
        @DisplayName("rejects non-weapon equipment")
        void rejectsNonWeapons() {
            var condition = new FilterCondition.WeaponTypeCondition(Set.of(WeaponType.SWORD));
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.PLATE_CHEST));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ArmorMaterialCondition
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ArmorMaterialCondition")
    class ArmorMaterialTests {

        @Test
        @DisplayName("matches specified materials")
        void matchesMaterials() {
            var condition = new FilterCondition.ArmorMaterialCondition(Set.of(ArmorMaterial.LEATHER));
            assertTrue(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.LEATHER_CHEST));
            assertTrue(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.LEATHER_HEAD));
        }

        @Test
        @DisplayName("rejects non-matching materials")
        void rejectsOtherMaterials() {
            var condition = new FilterCondition.ArmorMaterialCondition(Set.of(ArmorMaterial.LEATHER));
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.PLATE_CHEST));
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.CLOTH_HEAD));
        }

        @Test
        @DisplayName("rejects non-armor equipment")
        void rejectsNonArmor() {
            var condition = new FilterCondition.ArmorMaterialCondition(Set.of(ArmorMaterial.PLATE));
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.SWORD));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ItemLevelRange
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ItemLevelRange")
    class ItemLevelRangeTests {

        @Test
        @DisplayName("matches items within range")
        void matchesWithinRange() {
            var condition = new FilterCondition.ItemLevelRange(10, 20);
            assertTrue(condition.matches(gearWithLevel(10), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithLevel(15), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithLevel(20), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects items outside range")
        void rejectsOutsideRange() {
            var condition = new FilterCondition.ItemLevelRange(10, 20);
            assertFalse(condition.matches(gearWithLevel(9), EquipmentType.SWORD));
            assertFalse(condition.matches(gearWithLevel(21), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("auto-swaps min/max if reversed")
        void autoSwapsReversed() {
            var condition = new FilterCondition.ItemLevelRange(20, 10);
            assertEquals(10, condition.min());
            assertEquals(20, condition.max());
        }

        @Test
        @DisplayName("clamps min to 1")
        void clampsMinToOne() {
            var condition = new FilterCondition.ItemLevelRange(-5, 10);
            assertEquals(1, condition.min());
        }

        @Test
        @DisplayName("single level range works")
        void singleLevelRange() {
            var condition = new FilterCondition.ItemLevelRange(15, 15);
            assertTrue(condition.matches(gearWithLevel(15), EquipmentType.SWORD));
            assertFalse(condition.matches(gearWithLevel(14), EquipmentType.SWORD));
            assertEquals("Level 15", condition.describe());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QualityRange
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("QualityRange")
    class QualityRangeTests {

        @Test
        @DisplayName("matches items within quality range")
        void matchesWithinRange() {
            var condition = new FilterCondition.QualityRange(60, 90);
            assertTrue(condition.matches(gearWithQuality(60), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithQuality(75), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithQuality(90), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects items outside quality range")
        void rejectsOutsideRange() {
            var condition = new FilterCondition.QualityRange(60, 90);
            assertFalse(condition.matches(gearWithQuality(59), EquipmentType.SWORD));
            assertFalse(condition.matches(gearWithQuality(91), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("open-ended high range described correctly")
        void openEndedHighRange() {
            var condition = new FilterCondition.QualityRange(80, 101);
            assertEquals("Quality 80+", condition.describe());
        }

        @Test
        @DisplayName("open-ended low range described correctly")
        void openEndedLowRange() {
            var condition = new FilterCondition.QualityRange(1, 30);
            assertEquals("Quality ≤30", condition.describe());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RequiredModifiers
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RequiredModifiers")
    class RequiredModifiersTests {

        @Test
        @DisplayName("matches when all required modifiers present")
        void matchesAllPresent() {
            var condition = new FilterCondition.RequiredModifiers(Set.of("sharp", "sturdy"), 2);
            var gear = gearWithModifiers(prefix("sharp", 10), suffix("sturdy", 5));
            assertTrue(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("matches when minCount met from larger set")
        void matchesMinCountMet() {
            var condition = new FilterCondition.RequiredModifiers(Set.of("sharp", "sturdy", "swift"), 2);
            var gear = gearWithModifiers(prefix("sharp", 10), suffix("sturdy", 5));
            assertTrue(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects when insufficient matching modifiers")
        void rejectsInsufficientMatches() {
            var condition = new FilterCondition.RequiredModifiers(Set.of("sharp", "sturdy"), 2);
            var gear = gearWithModifiers(prefix("sharp", 10), suffix("other", 5));
            assertFalse(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("minCount clamped to at least 1")
        void minCountClampedToOne() {
            var condition = new FilterCondition.RequiredModifiers(Set.of("sharp"), 0);
            assertEquals(1, condition.minCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ModifierValueRange
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ModifierValueRange")
    class ModifierValueRangeTests {

        @Test
        @DisplayName("matches modifier within value range")
        void matchesInRange() {
            var condition = new FilterCondition.ModifierValueRange("sharp", 5.0, 15.0);
            var gear = gearWithModifiers(prefix("sharp", 10.0));
            assertTrue(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects modifier outside value range")
        void rejectsOutOfRange() {
            var condition = new FilterCondition.ModifierValueRange("sharp", 5.0, 15.0);
            var gear = gearWithModifiers(prefix("sharp", 20.0));
            assertFalse(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects if modifier not present")
        void rejectsIfAbsent() {
            var condition = new FilterCondition.ModifierValueRange("sharp", 5.0, 15.0);
            var gear = gearWithModifiers(prefix("other", 10.0));
            assertFalse(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("auto-swaps min/max if reversed")
        void autoSwapsReversed() {
            var condition = new FilterCondition.ModifierValueRange("sharp", 15.0, 5.0);
            assertEquals(5.0, condition.minValue());
            assertEquals(15.0, condition.maxValue());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ImplicitCondition
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ImplicitCondition")
    class ImplicitConditionTests {

        @Test
        @DisplayName("matches weapon with matching damage type and percentile")
        void matchesDamageTypeAndPercentile() {
            var condition = new FilterCondition.ImplicitCondition(0.5, Set.of("physical_damage"));
            var gear = gearWithImplicit("physical_damage", 0.8);
            assertTrue(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects wrong damage type")
        void rejectsWrongDamageType() {
            var condition = new FilterCondition.ImplicitCondition(0.0, Set.of("spell_damage"));
            var gear = gearWithImplicit("physical_damage", 0.8);
            assertFalse(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects low percentile")
        void rejectsLowPercentile() {
            var condition = new FilterCondition.ImplicitCondition(0.8, Set.of());
            var gear = gearWithImplicit("physical_damage", 0.3);
            assertFalse(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects gear without implicit")
        void rejectsNoImplicit() {
            var condition = new FilterCondition.ImplicitCondition(0.0, Set.of());
            assertFalse(condition.matches(gearWithRarity(GearRarity.RARE), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("empty damage types matches any damage type")
        void emptyDamageTypesMatchesAny() {
            var condition = new FilterCondition.ImplicitCondition(0.0, Set.of());
            assertTrue(condition.matches(gearWithImplicit("physical_damage", 0.5), EquipmentType.SWORD));
            assertTrue(condition.matches(gearWithImplicit("spell_damage", 0.5), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("percentile is clamped to 0-1")
        void percentileClamped() {
            var low = new FilterCondition.ImplicitCondition(-0.5, Set.of());
            assertEquals(0.0, low.minPercentile());
            var high = new FilterCondition.ImplicitCondition(2.0, Set.of());
            assertEquals(1.0, high.minPercentile());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MinModifierCount
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MinModifierCount")
    class MinModifierCountTests {

        @Test
        @DisplayName("matches items with enough modifiers")
        void matchesEnoughModifiers() {
            var condition = new FilterCondition.MinModifierCount(2);
            var gear = gearWithModifiers(prefix("sharp", 10), suffix("sturdy", 5));
            assertTrue(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("matches items with more than minimum")
        void matchesMoreThanMinimum() {
            var condition = new FilterCondition.MinModifierCount(1);
            var gear = gearWithModifiers(prefix("sharp", 10), suffix("sturdy", 5));
            assertTrue(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("rejects items with too few modifiers")
        void rejectsTooFew() {
            var condition = new FilterCondition.MinModifierCount(3);
            var gear = gearWithModifiers(prefix("sharp", 10), suffix("sturdy", 5));
            assertFalse(condition.matches(gear, EquipmentType.SWORD));
        }

        @Test
        @DisplayName("count is clamped to 0-6")
        void countClamped() {
            assertEquals(0, new FilterCondition.MinModifierCount(-1).count());
            assertEquals(6, new FilterCondition.MinModifierCount(10).count());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CorruptionStateCondition
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CorruptionStateCondition")
    class CorruptionStateTests {

        @Test
        @DisplayName("CORRUPTED_ONLY matches corrupted items")
        void corruptedOnlyMatchesCorrupted() {
            var condition = new FilterCondition.CorruptionStateCondition(CorruptionFilter.CORRUPTED_ONLY);
            assertTrue(condition.matches(gearCorrupted(true), EquipmentType.SWORD));
            assertFalse(condition.matches(gearCorrupted(false), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("NOT_CORRUPTED matches uncorrupted items")
        void notCorruptedMatchesUncorrupted() {
            var condition = new FilterCondition.CorruptionStateCondition(CorruptionFilter.NOT_CORRUPTED);
            assertTrue(condition.matches(gearCorrupted(false), EquipmentType.SWORD));
            assertFalse(condition.matches(gearCorrupted(true), EquipmentType.SWORD));
        }

        @Test
        @DisplayName("EITHER matches both states")
        void eitherMatchesBoth() {
            var condition = new FilterCondition.CorruptionStateCondition(CorruptionFilter.EITHER);
            assertTrue(condition.matches(gearCorrupted(true), EquipmentType.SWORD));
            assertTrue(condition.matches(gearCorrupted(false), EquipmentType.SWORD));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Type consistency
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Each condition implementation returns its correct ConditionType")
    void allConditionsReturnCorrectType() {
        assertEquals(ConditionType.MIN_RARITY, new FilterCondition.MinRarity(GearRarity.COMMON).type());
        assertEquals(ConditionType.MAX_RARITY, new FilterCondition.MaxRarity(GearRarity.COMMON).type());
        assertEquals(ConditionType.EQUIPMENT_SLOT, new FilterCondition.EquipmentSlotCondition(Set.of("weapon")).type());
        assertEquals(ConditionType.WEAPON_TYPE, new FilterCondition.WeaponTypeCondition(Set.of(WeaponType.SWORD)).type());
        assertEquals(ConditionType.ARMOR_MATERIAL, new FilterCondition.ArmorMaterialCondition(Set.of(ArmorMaterial.PLATE)).type());
        assertEquals(ConditionType.ITEM_LEVEL_RANGE, new FilterCondition.ItemLevelRange(1, 10).type());
        assertEquals(ConditionType.QUALITY_RANGE, new FilterCondition.QualityRange(1, 100).type());
        assertEquals(ConditionType.REQUIRED_MODIFIERS, new FilterCondition.RequiredModifiers(Set.of("x"), 1).type());
        assertEquals(ConditionType.MODIFIER_VALUE_RANGE, new FilterCondition.ModifierValueRange("x", 0, 10).type());
        assertEquals(ConditionType.IMPLICIT_CONDITION, new FilterCondition.ImplicitCondition(0.5, Set.of()).type());
        assertEquals(ConditionType.MIN_MODIFIER_COUNT, new FilterCondition.MinModifierCount(1).type());
        assertEquals(ConditionType.CORRUPTION_STATE, new FilterCondition.CorruptionStateCondition(CorruptionFilter.EITHER).type());
    }

    @Test
    @DisplayName("All conditions produce non-empty describe()")
    void allConditionsDescribeNonEmpty() {
        List<FilterCondition> conditions = List.of(
                new FilterCondition.MinRarity(GearRarity.RARE),
                new FilterCondition.MaxRarity(GearRarity.EPIC),
                new FilterCondition.EquipmentSlotCondition(Set.of("weapon")),
                new FilterCondition.WeaponTypeCondition(Set.of(WeaponType.SWORD)),
                new FilterCondition.ArmorMaterialCondition(Set.of(ArmorMaterial.PLATE)),
                new FilterCondition.ItemLevelRange(1, 50),
                new FilterCondition.QualityRange(50, 100),
                new FilterCondition.RequiredModifiers(Set.of("sharp"), 1),
                new FilterCondition.ModifierValueRange("sharp", 0, 10),
                new FilterCondition.ImplicitCondition(0.5, Set.of("physical_damage")),
                new FilterCondition.MinModifierCount(3),
                new FilterCondition.CorruptionStateCondition(CorruptionFilter.CORRUPTED_ONLY)
        );

        for (FilterCondition c : conditions) {
            assertNotNull(c.describe(), c.type() + " describe() should not be null");
            assertFalse(c.describe().isBlank(), c.type() + " describe() should not be blank");
        }
    }
}
