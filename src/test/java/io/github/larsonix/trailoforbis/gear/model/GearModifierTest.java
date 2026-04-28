package io.github.larsonix.trailoforbis.gear.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GearModifier record.
 */
class GearModifierTest {

    // Construction - valid

    @Test
    @DisplayName("Constructor succeeds with valid flat prefix")
    void constructor_validFlatPrefix_succeeds() {
        GearModifier mod = GearModifier.of(
            "sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0
        );
        assertEquals("sharp", mod.id());
        assertEquals("Sharp", mod.displayName());
        assertEquals(ModifierType.PREFIX, mod.type());
        assertEquals("physical_damage", mod.statId());
        assertEquals(GearModifier.STAT_TYPE_FLAT, mod.statType());
        assertEquals(10.0, mod.value());
    }

    @Test
    @DisplayName("Constructor succeeds with valid percent suffix")
    void constructor_validPercentSuffix_succeeds() {
        GearModifier mod = GearModifier.of(
            "of_the_whale", "of the Whale", ModifierType.SUFFIX,
            "max_health", GearModifier.STAT_TYPE_PERCENT, 15.5
        );
        assertEquals("of_the_whale", mod.id());
        assertEquals("of the Whale", mod.displayName());
        assertEquals(ModifierType.SUFFIX, mod.type());
        assertEquals("max_health", mod.statId());
        assertEquals(GearModifier.STAT_TYPE_PERCENT, mod.statType());
        assertEquals(15.5, mod.value());
    }

    @Test
    @DisplayName("Constructor succeeds with negative value (penalties are valid)")
    void constructor_negativeValue_succeeds() {
        GearModifier mod = GearModifier.of(
            "cursed", "Cursed", ModifierType.PREFIX,
            "luck", GearModifier.STAT_TYPE_FLAT, -5.0
        );
        assertEquals(-5.0, mod.value());
    }

    @Test
    @DisplayName("Constructor succeeds with zero value (edge case but valid)")
    void constructor_zeroValue_succeeds() {
        GearModifier mod = GearModifier.of(
            "neutral", "Neutral", ModifierType.PREFIX,
            "armor", GearModifier.STAT_TYPE_FLAT, 0.0
        );
        assertEquals(0.0, mod.value());
    }

    // Construction - invalid

    @Test
    @DisplayName("Constructor throws NullPointerException for null id")
    void constructor_nullId_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            GearModifier.of(null, "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)
        );
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException for blank id")
    void constructor_blankId_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () ->
            GearModifier.of("  ", "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)
        );
    }

    @Test
    @DisplayName("Constructor throws NullPointerException for null displayName")
    void constructor_nullDisplayName_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            GearModifier.of("sharp", null, ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)
        );
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException for blank displayName")
    void constructor_blankDisplayName_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () ->
            GearModifier.of("sharp", "", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)
        );
    }

    @Test
    @DisplayName("Constructor throws NullPointerException for null type")
    void constructor_nullType_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            GearModifier.of("sharp", "Sharp", null,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0)
        );
    }

    @Test
    @DisplayName("Constructor throws NullPointerException for null statId")
    void constructor_nullStatId_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                null, GearModifier.STAT_TYPE_FLAT, 10.0)
        );
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException for blank statId")
    void constructor_blankStatId_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () ->
            GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                "   ", GearModifier.STAT_TYPE_FLAT, 10.0)
        );
    }

    @Test
    @DisplayName("Constructor throws NullPointerException for null statType")
    void constructor_nullStatType_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", null, 10.0)
        );
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException for invalid statType")
    void constructor_invalidStatType_throwsIAE() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", "invalid", 10.0)
        );
        assertTrue(ex.getMessage().contains("flat") || ex.getMessage().contains("percent"));
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException for NaN value")
    void constructor_nanValue_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () ->
            GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, Double.NaN)
        );
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException for infinite value")
    void constructor_infiniteValue_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () ->
            GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, Double.POSITIVE_INFINITY)
        );
    }

    // Type checks

    @Test
    @DisplayName("isFlat returns true for flat stat type")
    void isFlat_flatType_returnsTrue() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        assertTrue(mod.isFlat());
    }

    @Test
    @DisplayName("isFlat returns false for percent stat type")
    void isFlat_percentType_returnsFalse() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_PERCENT, 10.0);
        assertFalse(mod.isFlat());
    }

    @Test
    @DisplayName("isPercent returns true for percent stat type")
    void isPercent_percentType_returnsTrue() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_PERCENT, 10.0);
        assertTrue(mod.isPercent());
    }

    @Test
    @DisplayName("isPercent returns false for flat stat type")
    void isPercent_flatType_returnsFalse() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        assertFalse(mod.isPercent());
    }

    @Test
    @DisplayName("isPrefix returns true for PREFIX type")
    void isPrefix_prefixType_returnsTrue() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        assertTrue(mod.isPrefix());
    }

    @Test
    @DisplayName("isPrefix returns false for SUFFIX type")
    void isPrefix_suffixType_returnsFalse() {
        GearModifier mod = GearModifier.of("of_the_whale", "of the Whale", ModifierType.SUFFIX,
            "max_health", GearModifier.STAT_TYPE_FLAT, 10.0);
        assertFalse(mod.isPrefix());
    }

    @Test
    @DisplayName("isSuffix returns true for SUFFIX type")
    void isSuffix_suffixType_returnsTrue() {
        GearModifier mod = GearModifier.of("of_the_whale", "of the Whale", ModifierType.SUFFIX,
            "max_health", GearModifier.STAT_TYPE_FLAT, 10.0);
        assertTrue(mod.isSuffix());
    }

    @Test
    @DisplayName("isSuffix returns false for PREFIX type")
    void isSuffix_prefixType_returnsFalse() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        assertFalse(mod.isSuffix());
    }

    // formatForTooltip

    @Test
    @DisplayName("formatForTooltip shows plus sign for positive flat value")
    void formatForTooltip_positiveFlat_hasPlus() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        String result = mod.formatForTooltip("Physical Damage");
        assertTrue(result.startsWith("+"));
        assertTrue(result.contains("Physical Damage"));
    }

    @Test
    @DisplayName("formatForTooltip shows minus sign for negative flat value")
    void formatForTooltip_negativeFlat_hasMinus() {
        GearModifier mod = GearModifier.of("cursed", "Cursed", ModifierType.PREFIX,
            "luck", GearModifier.STAT_TYPE_FLAT, -5.0);
        String result = mod.formatForTooltip("Luck");
        assertTrue(result.startsWith("-"));
    }

    @Test
    @DisplayName("formatForTooltip shows percent sign for percent value")
    void formatForTooltip_positivePercent_hasPercentSign() {
        GearModifier mod = GearModifier.of("of_the_whale", "of the Whale", ModifierType.SUFFIX,
            "max_health", GearModifier.STAT_TYPE_PERCENT, 10.0);
        String result = mod.formatForTooltip("Max Health");
        assertTrue(result.contains("%"));
    }

    @Test
    @DisplayName("formatForTooltip shows no decimal for whole number")
    void formatForTooltip_wholeNumber_noDecimal() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        String result = mod.formatForTooltip("Physical Damage");
        assertEquals("+10 Physical Damage", result);
    }

    @Test
    @DisplayName("formatForTooltip shows one decimal place for decimal value")
    void formatForTooltip_decimal_oneDecimalPlace() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.5);
        String result = mod.formatForTooltip("Physical Damage");
        assertEquals("+10.5 Physical Damage", result);
    }

    // withNewValue (type-safe version)

    @Test
    @DisplayName("withNewValue creates new instance")
    void withNewValue_createsNewInstance() {
        GearModifier original = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        GearModifier modified = original.withNewValue(20.0);
        assertNotSame(original, modified);
    }

    @Test
    @DisplayName("withNewValue preserves other fields")
    void withNewValue_preservesOtherFields() {
        GearModifier original = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        GearModifier modified = original.withNewValue(20.0);

        assertEquals(original.id(), modified.id());
        assertEquals(original.displayName(), modified.displayName());
        assertEquals(original.type(), modified.type());
        assertEquals(original.statId(), modified.statId());
        assertEquals(original.statType(), modified.statType());
        assertEquals(20.0, modified.value());
    }

    @Test
    @DisplayName("withNewValue leaves original unchanged (immutability)")
    void withNewValue_originalUnchanged() {
        GearModifier original = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        original.withNewValue(20.0);
        assertEquals(10.0, original.value());
    }

    // toString

    @Test
    @DisplayName("toString contains all relevant info")
    void toString_containsAllRelevantInfo() {
        GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        String str = mod.toString();
        assertTrue(str.contains("PREFIX"));
        assertTrue(str.contains("Sharp"));
        assertTrue(str.contains("physical_damage"));
    }

    // equals/hashCode

    @Test
    @DisplayName("equals returns true for same values")
    void equals_sameValues_returnsTrue() {
        GearModifier mod1 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        GearModifier mod2 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        assertEquals(mod1, mod2);
    }

    @Test
    @DisplayName("equals returns false for different value")
    void equals_differentValue_returnsFalse() {
        GearModifier mod1 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        GearModifier mod2 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 20.0);
        assertNotEquals(mod1, mod2);
    }

    @Test
    @DisplayName("hashCode is same for equal objects")
    void hashCode_sameValues_sameHash() {
        GearModifier mod1 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        GearModifier mod2 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX,
            "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);
        assertEquals(mod1.hashCode(), mod2.hashCode());
    }
}
