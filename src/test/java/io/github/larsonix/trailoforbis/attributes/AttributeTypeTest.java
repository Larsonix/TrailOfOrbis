package io.github.larsonix.trailoforbis.attributes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AttributeType enum - Elemental Attribute System.
 *
 * <p>The 6 elements each represent a distinct playstyle archetype:
 * <ul>
 *   <li>FIRE - Glass cannon</li>
 *   <li>WATER - Glacier tank</li>
 *   <li>LIGHTNING - Storm speed</li>
 *   <li>EARTH - Mountain fortress</li>
 *   <li>WIND - Ghost evasion</li>
 *   <li>VOID - Dark bargain</li>
 * </ul>
 */
public class AttributeTypeTest {

    @Test
    @DisplayName("fromString returns FIRE for various inputs")
    void fromStringFire() {
        assertEquals(AttributeType.FIRE, AttributeType.fromString("FIRE"));
        assertEquals(AttributeType.FIRE, AttributeType.fromString("Fire"));
        assertEquals(AttributeType.FIRE, AttributeType.fromString("fire"));
        assertEquals(AttributeType.FIRE, AttributeType.fromString("  FIRE  ")); // with whitespace
    }

    @Test
    @DisplayName("fromString returns WATER for various inputs")
    void fromStringWater() {
        assertEquals(AttributeType.WATER, AttributeType.fromString("WATER"));
        assertEquals(AttributeType.WATER, AttributeType.fromString("Water"));
        assertEquals(AttributeType.WATER, AttributeType.fromString("water"));
    }

    @Test
    @DisplayName("fromString returns LIGHTNING for various inputs")
    void fromStringLightning() {
        assertEquals(AttributeType.LIGHTNING, AttributeType.fromString("LIGHTNING"));
        assertEquals(AttributeType.LIGHTNING, AttributeType.fromString("Lightning"));
        assertEquals(AttributeType.LIGHTNING, AttributeType.fromString("lightning"));
    }

    @Test
    @DisplayName("fromString returns EARTH for various inputs")
    void fromStringEarth() {
        assertEquals(AttributeType.EARTH, AttributeType.fromString("EARTH"));
        assertEquals(AttributeType.EARTH, AttributeType.fromString("Earth"));
        assertEquals(AttributeType.EARTH, AttributeType.fromString("earth"));
    }

    @Test
    @DisplayName("fromString returns WIND for various inputs")
    void fromStringWind() {
        assertEquals(AttributeType.WIND, AttributeType.fromString("WIND"));
        assertEquals(AttributeType.WIND, AttributeType.fromString("Wind"));
        assertEquals(AttributeType.WIND, AttributeType.fromString("wind"));
    }

    @Test
    @DisplayName("fromString returns VOID for various inputs")
    void fromStringVoid() {
        assertEquals(AttributeType.VOID, AttributeType.fromString("VOID"));
        assertEquals(AttributeType.VOID, AttributeType.fromString("Void"));
        assertEquals(AttributeType.VOID, AttributeType.fromString("void"));
    }

    @Test
    @DisplayName("fromString returns null for invalid inputs")
    void fromStringInvalid() {
        assertNull(AttributeType.fromString("INVALID"));
        assertNull(AttributeType.fromString("invalid"));
        assertNull(AttributeType.fromString("FOO"));
        assertNull(AttributeType.fromString(""));
        assertNull(AttributeType.fromString("   "));
        assertNull(AttributeType.fromString(null));
        // Old attribute names should no longer work
        assertNull(AttributeType.fromString("STRENGTH"));
        assertNull(AttributeType.fromString("DEXTERITY"));
        assertNull(AttributeType.fromString("INTELLIGENCE"));
        assertNull(AttributeType.fromString("VITALITY"));
        assertNull(AttributeType.fromString("LUCK"));
    }

    @Test
    @DisplayName("Display name and hex color are correct")
    void displayNameAndHexColor() {
        assertEquals("Fire", AttributeType.FIRE.getDisplayName());
        assertEquals("#FF7755", AttributeType.FIRE.getHexColor());
        assertEquals("Physical damage, charged attack damage, crit multiplier, burn damage, ignite chance", AttributeType.FIRE.getDescription());

        assertEquals("Water", AttributeType.WATER.getDisplayName());
        assertEquals("#55CCEE", AttributeType.WATER.getHexColor());

        assertEquals("Lightning", AttributeType.LIGHTNING.getDisplayName());
        assertEquals("#FFEE55", AttributeType.LIGHTNING.getHexColor());

        assertEquals("Earth", AttributeType.EARTH.getDisplayName());
        assertEquals("#DDAA55", AttributeType.EARTH.getHexColor());

        assertEquals("Wind", AttributeType.WIND.getDisplayName());
        assertEquals("#77DD77", AttributeType.WIND.getHexColor());

        assertEquals("Void", AttributeType.VOID.getDisplayName());
        assertEquals("#BB77DD", AttributeType.VOID.getHexColor());
    }

    @Test
    @DisplayName("All attribute types have unique properties")
    void allAttributesUnique() {
        AttributeType[] types = AttributeType.values();
        assertEquals(6, types.length, "Should have exactly 6 attribute types (elements)");

        // Verify all have non-null properties
        for (AttributeType type : types) {
            assertNotNull(type.getDisplayName());
            assertNotNull(type.getHexColor());
            assertNotNull(type.getDescription());
            assertFalse(type.getDisplayName().isBlank());
            assertFalse(type.getHexColor().isBlank());
            assertFalse(type.getDescription().isBlank());
            assertTrue(type.getHexColor().startsWith("#"), "Hex color should start with #");
        }
    }
}
