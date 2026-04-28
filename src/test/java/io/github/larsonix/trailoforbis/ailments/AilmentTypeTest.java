package io.github.larsonix.trailoforbis.ailments;

import io.github.larsonix.trailoforbis.elemental.ElementType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AilmentType} enum.
 */
@DisplayName("AilmentType")
class AilmentTypeTest {

    @Test
    @DisplayName("BURN maps from FIRE element")
    void burnMapsFromFire() {
        assertEquals(AilmentType.BURN, AilmentType.forElement(ElementType.FIRE));
    }

    @Test
    @DisplayName("FREEZE maps from WATER element")
    void freezeMapsFromWater() {
        assertEquals(AilmentType.FREEZE, AilmentType.forElement(ElementType.WATER));
    }

    @Test
    @DisplayName("SHOCK maps from LIGHTNING element")
    void shockMapsFromLightning() {
        assertEquals(AilmentType.SHOCK, AilmentType.forElement(ElementType.LIGHTNING));
    }

    @Test
    @DisplayName("POISON maps from VOID element")
    void poisonMapsFromVoid() {
        assertEquals(AilmentType.POISON, AilmentType.forElement(ElementType.VOID));
    }

    @Test
    @DisplayName("null element returns null")
    void nullElementReturnsNull() {
        assertNull(AilmentType.forElement(null));
    }

    @ParameterizedTest
    @EnumSource(AilmentType.class)
    @DisplayName("All ailment types have associated element")
    void allTypesHaveElement(AilmentType type) {
        assertNotNull(type.getElement(), type.name() + " should have an element");
    }

    @ParameterizedTest
    @EnumSource(AilmentType.class)
    @DisplayName("All ailment types have display name")
    void allTypesHaveDisplayName(AilmentType type) {
        assertNotNull(type.getDisplayName(), type.name() + " should have a display name");
        assertFalse(type.getDisplayName().isEmpty(), type.name() + " display name should not be empty");
    }

    @ParameterizedTest
    @EnumSource(AilmentType.class)
    @DisplayName("All ailment types have positive default duration")
    void allTypesHavePositiveDuration(AilmentType type) {
        assertTrue(type.getDefaultDuration() > 0, type.name() + " should have positive duration");
    }

    @ParameterizedTest
    @EnumSource(AilmentType.class)
    @DisplayName("All ailment types have positive max stacks")
    void allTypesHavePositiveMaxStacks(AilmentType type) {
        assertTrue(type.getMaxStacks() >= 1, type.name() + " should have at least 1 stack");
    }

    @ParameterizedTest
    @EnumSource(AilmentType.class)
    @DisplayName("All ailment types have non-negative damage ratio")
    void allTypesHaveNonNegativeDamageRatio(AilmentType type) {
        assertTrue(type.getBaseDamageRatio() >= 0, type.name() + " damage ratio should be >= 0");
    }

    @ParameterizedTest
    @EnumSource(AilmentType.class)
    @DisplayName("All ailment types have non-negative base chance")
    void allTypesHaveNonNegativeBaseChance(AilmentType type) {
        assertTrue(type.getBaseChance() >= 0, type.name() + " base chance should be >= 0");
    }

    @Test
    @DisplayName("BURN deals damage (DoT type)")
    void burnDealsDoT() {
        assertTrue(AilmentType.BURN.dealsDamage());
        assertFalse(AilmentType.BURN.isDebuff());
        assertFalse(AilmentType.BURN.stacks());
    }

    @Test
    @DisplayName("FREEZE is debuff type (no damage)")
    void freezeIsDebuff() {
        assertFalse(AilmentType.FREEZE.dealsDamage());
        assertTrue(AilmentType.FREEZE.isDebuff());
        assertFalse(AilmentType.FREEZE.stacks());
    }

    @Test
    @DisplayName("SHOCK is debuff type (no damage)")
    void shockIsDebuff() {
        assertFalse(AilmentType.SHOCK.dealsDamage());
        assertTrue(AilmentType.SHOCK.isDebuff());
        assertFalse(AilmentType.SHOCK.stacks());
    }

    @Test
    @DisplayName("POISON deals stacking damage")
    void poisonIsStackingDoT() {
        assertTrue(AilmentType.POISON.dealsDamage());
        assertFalse(AilmentType.POISON.isDebuff());
        assertTrue(AilmentType.POISON.stacks());
    }

    @Test
    @DisplayName("POISON has max stacks > 1")
    void poisonHasMultipleMaxStacks() {
        assertTrue(AilmentType.POISON.getMaxStacks() > 1, "Poison should allow multiple stacks");
    }

    @Test
    @DisplayName("Non-stacking ailments have max stacks of 1")
    void nonStackingHaveSingleStack() {
        assertEquals(1, AilmentType.BURN.getMaxStacks());
        assertEquals(1, AilmentType.FREEZE.getMaxStacks());
        assertEquals(1, AilmentType.SHOCK.getMaxStacks());
    }

    @Test
    @DisplayName("fromName finds ailments case-insensitively")
    void fromNameFindsAilments() {
        assertEquals(AilmentType.BURN, AilmentType.fromName("BURN"));
        assertEquals(AilmentType.BURN, AilmentType.fromName("burn"));
        assertEquals(AilmentType.BURN, AilmentType.fromName("Burning"));
        assertEquals(AilmentType.FREEZE, AilmentType.fromName("Chilled"));
        assertNull(AilmentType.fromName("InvalidName"));
        assertNull(AilmentType.fromName(null));
        assertNull(AilmentType.fromName(""));
    }

    @Test
    @DisplayName("getColoredName returns name with color code")
    void getColoredNameIncludesColor() {
        String coloredBurn = AilmentType.BURN.getColoredName();
        assertTrue(coloredBurn.contains("§c"));
        assertTrue(coloredBurn.contains("Burning"));
    }
}
