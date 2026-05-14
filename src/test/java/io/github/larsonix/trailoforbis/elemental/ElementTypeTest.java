package io.github.larsonix.trailoforbis.elemental;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ElementType} enum.
 *
 * <p>Focuses on behavioral contracts that matter for the RPG system:
 * damage type ID generation (used in config/stat lookups), short code
 * uniqueness (used in compact display), and formatting contracts.
 */
@DisplayName("ElementType")
class ElementTypeTest {

    @Test
    @DisplayName("getDamageTypeId() produces lowercase_damage format for config lookups")
    void damageTypeIdFollowsNamingConvention() {
        assertEquals("fire_damage", ElementType.FIRE.getDamageTypeId());
        assertEquals("water_damage", ElementType.WATER.getDamageTypeId());
        assertEquals("lightning_damage", ElementType.LIGHTNING.getDamageTypeId());
        assertEquals("earth_damage", ElementType.EARTH.getDamageTypeId());
        assertEquals("wind_damage", ElementType.WIND.getDamageTypeId());
        assertEquals("void_damage", ElementType.VOID.getDamageTypeId());
    }

    @Test
    @DisplayName("All damage type IDs are unique (prevents config key collisions)")
    void damageTypeIdsAreUnique() {
        Set<String> ids = Arrays.stream(ElementType.values())
            .map(ElementType::getDamageTypeId)
            .collect(Collectors.toSet());
        assertEquals(ElementType.values().length, ids.size(),
            "Each element must have a unique damage type ID");
    }

    @Test
    @DisplayName("All short codes are unique (prevents display collisions)")
    void shortCodesAreUnique() {
        Set<Character> codes = Arrays.stream(ElementType.values())
            .map(ElementType::getShortCode)
            .collect(Collectors.toSet());
        assertEquals(ElementType.values().length, codes.size(),
            "Each element must have a unique short code");
    }

    @ParameterizedTest
    @EnumSource(ElementType.class)
    @DisplayName("Colored indicator follows format: colorCode + [shortCode]")
    void coloredIndicatorFormat(ElementType type) {
        String expected = type.getColorCode() + "[" + type.getShortCode() + "]";
        assertEquals(expected, type.getColoredIndicator());
    }

    @ParameterizedTest
    @EnumSource(ElementType.class)
    @DisplayName("Colored name follows format: colorCode + displayName")
    void coloredNameFormat(ElementType type) {
        String expected = type.getColorCode() + type.getDisplayName();
        assertEquals(expected, type.getColoredName());
    }

    @ParameterizedTest
    @EnumSource(ElementType.class)
    @DisplayName("All elements have valid color codes (§ prefix)")
    void colorCodesStartWithSection(ElementType type) {
        assertTrue(type.getColorCode().startsWith("§"),
            type.name() + " color code should start with §");
        assertEquals(2, type.getColorCode().length(),
            type.name() + " color code should be exactly 2 chars (§ + code)");
    }

    @Test
    @DisplayName("valueOf with invalid name throws IllegalArgumentException")
    void invalidValueOfThrows() {
        assertThrows(IllegalArgumentException.class, () -> ElementType.valueOf("INVALID"));
    }
}
