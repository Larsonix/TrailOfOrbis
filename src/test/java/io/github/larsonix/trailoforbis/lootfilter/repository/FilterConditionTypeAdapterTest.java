package io.github.larsonix.trailoforbis.lootfilter.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.lootfilter.model.ConditionType;
import io.github.larsonix.trailoforbis.lootfilter.model.CorruptionFilter;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FilterConditionTypeAdapter} — round-trip serialization of all 12 condition types.
 */
class FilterConditionTypeAdapterTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
                .registerTypeAdapter(FilterCondition.class, new FilterConditionTypeAdapter())
                .create();
    }

    private FilterCondition roundTrip(FilterCondition original) {
        String json = gson.toJson(original, FilterCondition.class);
        return gson.fromJson(json, FilterCondition.class);
    }

    @Test
    @DisplayName("MinRarity round-trips correctly")
    void minRarityRoundTrip() {
        var original = new FilterCondition.MinRarity(GearRarity.EPIC);
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.MinRarity.class, result);
        assertEquals(GearRarity.EPIC, ((FilterCondition.MinRarity) result).threshold());
    }

    @Test
    @DisplayName("MaxRarity round-trips correctly")
    void maxRarityRoundTrip() {
        var original = new FilterCondition.MaxRarity(GearRarity.UNCOMMON);
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.MaxRarity.class, result);
        assertEquals(GearRarity.UNCOMMON, ((FilterCondition.MaxRarity) result).threshold());
    }

    @Test
    @DisplayName("EquipmentSlotCondition round-trips correctly")
    void equipmentSlotRoundTrip() {
        var original = new FilterCondition.EquipmentSlotCondition(Set.of("weapon", "head"));
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.EquipmentSlotCondition.class, result);
        assertEquals(Set.of("weapon", "head"), ((FilterCondition.EquipmentSlotCondition) result).slots());
    }

    @Test
    @DisplayName("WeaponTypeCondition round-trips correctly")
    void weaponTypeRoundTrip() {
        var original = new FilterCondition.WeaponTypeCondition(Set.of(WeaponType.SWORD, WeaponType.STAFF));
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.WeaponTypeCondition.class, result);
        assertEquals(Set.of(WeaponType.SWORD, WeaponType.STAFF),
                ((FilterCondition.WeaponTypeCondition) result).types());
    }

    @Test
    @DisplayName("ArmorMaterialCondition round-trips correctly")
    void armorMaterialRoundTrip() {
        var original = new FilterCondition.ArmorMaterialCondition(Set.of(ArmorMaterial.LEATHER, ArmorMaterial.PLATE));
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.ArmorMaterialCondition.class, result);
        assertEquals(Set.of(ArmorMaterial.LEATHER, ArmorMaterial.PLATE),
                ((FilterCondition.ArmorMaterialCondition) result).materials());
    }

    @Test
    @DisplayName("ItemLevelRange round-trips correctly")
    void itemLevelRangeRoundTrip() {
        var original = new FilterCondition.ItemLevelRange(10, 50);
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.ItemLevelRange.class, result);
        var range = (FilterCondition.ItemLevelRange) result;
        assertEquals(10, range.min());
        assertEquals(50, range.max());
    }

    @Test
    @DisplayName("QualityRange round-trips correctly")
    void qualityRangeRoundTrip() {
        var original = new FilterCondition.QualityRange(60, 100);
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.QualityRange.class, result);
        var range = (FilterCondition.QualityRange) result;
        assertEquals(60, range.min());
        assertEquals(100, range.max());
    }

    @Test
    @DisplayName("RequiredModifiers round-trips correctly")
    void requiredModifiersRoundTrip() {
        var original = new FilterCondition.RequiredModifiers(Set.of("sharp", "sturdy"), 2);
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.RequiredModifiers.class, result);
        var rm = (FilterCondition.RequiredModifiers) result;
        assertEquals(Set.of("sharp", "sturdy"), rm.modifierIds());
        assertEquals(2, rm.minCount());
    }

    @Test
    @DisplayName("ModifierValueRange round-trips correctly")
    void modifierValueRangeRoundTrip() {
        var original = new FilterCondition.ModifierValueRange("sharp", 5.5, 20.0);
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.ModifierValueRange.class, result);
        var mvr = (FilterCondition.ModifierValueRange) result;
        assertEquals("sharp", mvr.modifierId());
        assertEquals(5.5, mvr.minValue(), 0.001);
        assertEquals(20.0, mvr.maxValue(), 0.001);
    }

    @Test
    @DisplayName("ImplicitCondition round-trips correctly")
    void implicitConditionRoundTrip() {
        var original = new FilterCondition.ImplicitCondition(0.75, Set.of("physical_damage"));
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.ImplicitCondition.class, result);
        var ic = (FilterCondition.ImplicitCondition) result;
        assertEquals(0.75, ic.minPercentile(), 0.001);
        assertEquals(Set.of("physical_damage"), ic.damageTypes());
    }

    @Test
    @DisplayName("MinModifierCount round-trips correctly")
    void minModifierCountRoundTrip() {
        var original = new FilterCondition.MinModifierCount(4);
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.MinModifierCount.class, result);
        assertEquals(4, ((FilterCondition.MinModifierCount) result).count());
    }

    @ParameterizedTest
    @EnumSource(CorruptionFilter.class)
    @DisplayName("CorruptionStateCondition round-trips for all CorruptionFilter values")
    void corruptionStateRoundTrip(CorruptionFilter filter) {
        var original = new FilterCondition.CorruptionStateCondition(filter);
        var result = roundTrip(original);
        assertInstanceOf(FilterCondition.CorruptionStateCondition.class, result);
        assertEquals(filter, ((FilterCondition.CorruptionStateCondition) result).filter());
    }

    @Test
    @DisplayName("JSON contains type discriminator field")
    void jsonContainsTypeField() {
        var condition = new FilterCondition.MinRarity(GearRarity.RARE);
        String json = gson.toJson(condition, FilterCondition.class);
        assertTrue(json.contains("\"type\":\"MIN_RARITY\""));
    }

    @Test
    @DisplayName("All 12 condition types can be serialized")
    void allTypesSerializable() {
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
                new FilterCondition.CorruptionStateCondition(CorruptionFilter.EITHER)
        );

        assertEquals(ConditionType.values().length, conditions.size(),
                "Test should cover all ConditionType values");

        for (FilterCondition c : conditions) {
            var result = roundTrip(c);
            assertEquals(c.type(), result.type(),
                    "Round-trip should preserve type for " + c.type());
        }
    }
}
