package io.github.larsonix.trailoforbis.elemental;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ElementType} enum.
 *
 * <p>Validates element properties, color codes, and display formatting.
 */
@DisplayName("ElementType")
class ElementTypeTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enum Values Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Enum Values")
    class EnumValuesTests {

        @Test
        @DisplayName("Has exactly 6 elements (Gaia's 5 + Varyn's 1)")
        void hasExactly6Elements() {
            assertEquals(6, ElementType.values().length);
        }

        @Test
        @DisplayName("Contains FIRE element")
        void containsFire() {
            assertNotNull(ElementType.FIRE);
        }

        @Test
        @DisplayName("Contains WATER element")
        void containsWater() {
            assertNotNull(ElementType.WATER);
        }

        @Test
        @DisplayName("Contains LIGHTNING element")
        void containsLightning() {
            assertNotNull(ElementType.LIGHTNING);
        }

        @Test
        @DisplayName("Contains EARTH element")
        void containsEarth() {
            assertNotNull(ElementType.EARTH);
        }

        @Test
        @DisplayName("Contains WIND element")
        void containsWind() {
            assertNotNull(ElementType.WIND);
        }

        @Test
        @DisplayName("Contains VOID element")
        void containsVoid() {
            assertNotNull(ElementType.VOID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Display Name Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Display Names")
    class DisplayNameTests {

        @Test
        @DisplayName("FIRE has display name 'Fire'")
        void fire_hasCorrectDisplayName() {
            assertEquals("Fire", ElementType.FIRE.getDisplayName());
        }

        @Test
        @DisplayName("WATER has display name 'Water'")
        void water_hasCorrectDisplayName() {
            assertEquals("Water", ElementType.WATER.getDisplayName());
        }

        @Test
        @DisplayName("LIGHTNING has display name 'Lightning'")
        void lightning_hasCorrectDisplayName() {
            assertEquals("Lightning", ElementType.LIGHTNING.getDisplayName());
        }

        @Test
        @DisplayName("EARTH has display name 'Earth'")
        void earth_hasCorrectDisplayName() {
            assertEquals("Earth", ElementType.EARTH.getDisplayName());
        }

        @Test
        @DisplayName("WIND has display name 'Wind'")
        void wind_hasCorrectDisplayName() {
            assertEquals("Wind", ElementType.WIND.getDisplayName());
        }

        @Test
        @DisplayName("VOID has display name 'Void'")
        void void_hasCorrectDisplayName() {
            assertEquals("Void", ElementType.VOID.getDisplayName());
        }

        @ParameterizedTest
        @EnumSource(ElementType.class)
        @DisplayName("All elements have non-empty display names")
        void allElements_haveNonEmptyDisplayNames(ElementType type) {
            assertNotNull(type.getDisplayName());
            assertFalse(type.getDisplayName().isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Color Code Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Color Codes")
    class ColorCodeTests {

        @Test
        @DisplayName("FIRE has red color code (§c)")
        void fire_hasRedColorCode() {
            assertEquals("§c", ElementType.FIRE.getColorCode());
        }

        @Test
        @DisplayName("WATER has aqua color code (§b)")
        void water_hasAquaColorCode() {
            assertEquals("§b", ElementType.WATER.getColorCode());
        }

        @Test
        @DisplayName("LIGHTNING has yellow color code (§e)")
        void lightning_hasYellowColorCode() {
            assertEquals("§e", ElementType.LIGHTNING.getColorCode());
        }

        @Test
        @DisplayName("EARTH has brown/gold color code (§6)")
        void earth_hasBrownColorCode() {
            assertEquals("§6", ElementType.EARTH.getColorCode());
        }

        @Test
        @DisplayName("WIND has light green color code (§a)")
        void wind_hasLightGreenColorCode() {
            assertEquals("§a", ElementType.WIND.getColorCode());
        }

        @Test
        @DisplayName("VOID has purple color code (§5)")
        void void_hasPurpleColorCode() {
            assertEquals("§5", ElementType.VOID.getColorCode());
        }

        @ParameterizedTest
        @EnumSource(ElementType.class)
        @DisplayName("All elements have color codes starting with §")
        void allElements_haveColorCodesStartingWithSection(ElementType type) {
            assertTrue(type.getColorCode().startsWith("§"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Short Code Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Short Codes")
    class ShortCodeTests {

        @Test
        @DisplayName("FIRE has short code 'F'")
        void fire_hasShortCodeF() {
            assertEquals('F', ElementType.FIRE.getShortCode());
        }

        @Test
        @DisplayName("WATER has short code 'W'")
        void water_hasShortCodeC() {
            assertEquals('W', ElementType.WATER.getShortCode());
        }

        @Test
        @DisplayName("LIGHTNING has short code 'L'")
        void lightning_hasShortCodeL() {
            assertEquals('L', ElementType.LIGHTNING.getShortCode());
        }

        @Test
        @DisplayName("EARTH has short code 'E'")
        void earth_hasShortCodeE() {
            assertEquals('E', ElementType.EARTH.getShortCode());
        }

        @Test
        @DisplayName("WIND has short code 'A' (for Air)")
        void wind_hasShortCodeA() {
            assertEquals('A', ElementType.WIND.getShortCode());
        }

        @Test
        @DisplayName("VOID has short code 'V'")
        void void_hasShortCodeV() {
            assertEquals('V', ElementType.VOID.getShortCode());
        }

        @Test
        @DisplayName("All short codes are unique")
        void allShortCodes_areUnique() {
            ElementType[] types = ElementType.values();
            for (int i = 0; i < types.length; i++) {
                for (int j = i + 1; j < types.length; j++) {
                    assertNotEquals(
                        types[i].getShortCode(),
                        types[j].getShortCode(),
                        "Short codes should be unique: " + types[i] + " and " + types[j]
                    );
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Formatted Output Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Formatted Output")
    class FormattedOutputTests {

        @Test
        @DisplayName("getColoredIndicator returns formatted indicator for FIRE")
        void getColoredIndicator_fire_returnsFormattedIndicator() {
            assertEquals("§c[F]", ElementType.FIRE.getColoredIndicator());
        }

        @Test
        @DisplayName("getColoredIndicator returns formatted indicator for WATER")
        void getColoredIndicator_water_returnsFormattedIndicator() {
            assertEquals("§b[W]", ElementType.WATER.getColoredIndicator());
        }

        @Test
        @DisplayName("getColoredIndicator returns formatted indicator for LIGHTNING")
        void getColoredIndicator_lightning_returnsFormattedIndicator() {
            assertEquals("§e[L]", ElementType.LIGHTNING.getColoredIndicator());
        }

        @Test
        @DisplayName("getColoredIndicator returns formatted indicator for EARTH")
        void getColoredIndicator_earth_returnsFormattedIndicator() {
            assertEquals("§6[E]", ElementType.EARTH.getColoredIndicator());
        }

        @Test
        @DisplayName("getColoredIndicator returns formatted indicator for WIND")
        void getColoredIndicator_wind_returnsFormattedIndicator() {
            assertEquals("§a[A]", ElementType.WIND.getColoredIndicator());
        }

        @Test
        @DisplayName("getColoredIndicator returns formatted indicator for VOID")
        void getColoredIndicator_void_returnsFormattedIndicator() {
            assertEquals("§5[V]", ElementType.VOID.getColoredIndicator());
        }

        @Test
        @DisplayName("getColoredName returns formatted name for FIRE")
        void getColoredName_fire_returnsFormattedName() {
            assertEquals("§cFire", ElementType.FIRE.getColoredName());
        }

        @Test
        @DisplayName("getColoredName returns formatted name for WATER")
        void getColoredName_water_returnsFormattedName() {
            assertEquals("§bWater", ElementType.WATER.getColoredName());
        }

        @Test
        @DisplayName("getColoredName returns formatted name for LIGHTNING")
        void getColoredName_lightning_returnsFormattedName() {
            assertEquals("§eLightning", ElementType.LIGHTNING.getColoredName());
        }

        @Test
        @DisplayName("getColoredName returns formatted name for EARTH")
        void getColoredName_earth_returnsFormattedName() {
            assertEquals("§6Earth", ElementType.EARTH.getColoredName());
        }

        @Test
        @DisplayName("getColoredName returns formatted name for WIND")
        void getColoredName_wind_returnsFormattedName() {
            assertEquals("§aWind", ElementType.WIND.getColoredName());
        }

        @Test
        @DisplayName("getColoredName returns formatted name for VOID")
        void getColoredName_void_returnsFormattedName() {
            assertEquals("§5Void", ElementType.VOID.getColoredName());
        }

        @ParameterizedTest
        @EnumSource(ElementType.class)
        @DisplayName("All colored indicators follow format: colorCode + [shortCode]")
        void allColoredIndicators_followFormat(ElementType type) {
            String expected = type.getColorCode() + "[" + type.getShortCode() + "]";
            assertEquals(expected, type.getColoredIndicator());
        }

        @ParameterizedTest
        @EnumSource(ElementType.class)
        @DisplayName("All colored names follow format: colorCode + displayName")
        void allColoredNames_followFormat(ElementType type) {
            String expected = type.getColorCode() + type.getDisplayName();
            assertEquals(expected, type.getColoredName());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Enum valueOf Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Enum valueOf")
    class EnumValueOfTests {

        @Test
        @DisplayName("valueOf('FIRE') returns FIRE")
        void valueOf_fire_returnsFire() {
            assertEquals(ElementType.FIRE, ElementType.valueOf("FIRE"));
        }

        @Test
        @DisplayName("valueOf('WATER') returns WATER")
        void valueOf_water_returnsWater() {
            assertEquals(ElementType.WATER, ElementType.valueOf("WATER"));
        }

        @Test
        @DisplayName("valueOf('LIGHTNING') returns LIGHTNING")
        void valueOf_lightning_returnsLightning() {
            assertEquals(ElementType.LIGHTNING, ElementType.valueOf("LIGHTNING"));
        }

        @Test
        @DisplayName("valueOf('EARTH') returns EARTH")
        void valueOf_earth_returnsEarth() {
            assertEquals(ElementType.EARTH, ElementType.valueOf("EARTH"));
        }

        @Test
        @DisplayName("valueOf('WIND') returns WIND")
        void valueOf_wind_returnsWind() {
            assertEquals(ElementType.WIND, ElementType.valueOf("WIND"));
        }

        @Test
        @DisplayName("valueOf('VOID') returns VOID")
        void valueOf_void_returnsVoid() {
            assertEquals(ElementType.VOID, ElementType.valueOf("VOID"));
        }

        @Test
        @DisplayName("valueOf with invalid name throws IllegalArgumentException")
        void valueOf_invalidName_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> ElementType.valueOf("INVALID"));
        }
    }
}
