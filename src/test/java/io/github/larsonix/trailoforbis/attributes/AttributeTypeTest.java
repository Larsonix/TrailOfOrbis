package io.github.larsonix.trailoforbis.attributes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AttributeType enum — the 6 elemental archetypes.
 *
 * <p>Focuses on fromString parsing (used in config loading and commands),
 * hex color format validation (used in UI rendering), and uniqueness
 * constraints that prevent runtime collisions.
 */
public class AttributeTypeTest {

    @Test
    @DisplayName("fromString is case-insensitive and trims whitespace")
    void fromStringCaseInsensitive() {
        assertEquals(AttributeType.FIRE, AttributeType.fromString("FIRE"));
        assertEquals(AttributeType.FIRE, AttributeType.fromString("Fire"));
        assertEquals(AttributeType.FIRE, AttributeType.fromString("fire"));
        assertEquals(AttributeType.FIRE, AttributeType.fromString("  FIRE  "));
    }

    @ParameterizedTest
    @EnumSource(AttributeType.class)
    @DisplayName("Every attribute round-trips through fromString")
    void fromStringRoundTrip(AttributeType type) {
        assertEquals(type, AttributeType.fromString(type.name()));
        assertEquals(type, AttributeType.fromString(type.name().toLowerCase()));
    }

    @Test
    @DisplayName("fromString returns null for invalid, empty, null, and legacy names")
    void fromStringInvalid() {
        assertNull(AttributeType.fromString("INVALID"));
        assertNull(AttributeType.fromString(""));
        assertNull(AttributeType.fromString("   "));
        assertNull(AttributeType.fromString(null));
        // Legacy attribute names must NOT resolve (removed in elemental rework)
        assertNull(AttributeType.fromString("STRENGTH"));
        assertNull(AttributeType.fromString("DEXTERITY"));
        assertNull(AttributeType.fromString("INTELLIGENCE"));
        assertNull(AttributeType.fromString("VITALITY"));
        assertNull(AttributeType.fromString("LUCK"));
    }

    @ParameterizedTest
    @EnumSource(AttributeType.class)
    @DisplayName("All hex colors are valid 7-char format (#RRGGBB)")
    void hexColorFormat(AttributeType type) {
        String hex = type.getHexColor();
        assertTrue(hex.startsWith("#"), type.name() + " hex should start with #");
        assertEquals(7, hex.length(), type.name() + " hex should be #RRGGBB (7 chars)");
        assertTrue(hex.substring(1).matches("[0-9A-Fa-f]{6}"),
            type.name() + " hex should contain valid hex digits");
    }

    @Test
    @DisplayName("All hex colors are unique (prevents UI color collisions)")
    void hexColorsAreUnique() {
        Set<String> colors = Arrays.stream(AttributeType.values())
            .map(AttributeType::getHexColor)
            .collect(Collectors.toSet());
        assertEquals(AttributeType.values().length, colors.size());
    }

    @Test
    @DisplayName("All display names are unique")
    void displayNamesAreUnique() {
        Set<String> names = Arrays.stream(AttributeType.values())
            .map(AttributeType::getDisplayName)
            .collect(Collectors.toSet());
        assertEquals(AttributeType.values().length, names.size());
    }
}
