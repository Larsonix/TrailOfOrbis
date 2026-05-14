package io.github.larsonix.trailoforbis.compat;

import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HexcodeSpellConfig}.
 *
 * <p>Covers spell→damage type mapping, element resolution, display names,
 * construct source classification, construct physics config, and validation.
 */
class HexcodeSpellConfigTest {

    private HexcodeSpellConfig config;

    @BeforeEach
    void setUp() {
        config = new HexcodeSpellConfig();
    }

    // =========================================================================
    // SPELL SOURCE DETECTION
    // =========================================================================

    @Nested
    @DisplayName("Hex Spell Source Detection")
    class SpellSourceDetection {

        @Test
        @DisplayName("hex_ prefix is detected as hex spell")
        void hexPrefix_isDetected() {
            assertTrue(HexcodeSpellConfig.isHexSpellSource("hex_bolt"));
            assertTrue(HexcodeSpellConfig.isHexSpellSource("hex_glaciate"));
            assertTrue(HexcodeSpellConfig.isHexSpellSource("hex_unknown"));
        }

        @Test
        @DisplayName("Non-hex sources are not detected")
        void nonHex_notDetected() {
            assertFalse(HexcodeSpellConfig.isHexSpellSource("fire"));
            assertFalse(HexcodeSpellConfig.isHexSpellSource("environment"));
            assertFalse(HexcodeSpellConfig.isHexSpellSource(""));
        }

        @Test
        @DisplayName("null source returns false")
        void nullSource_returnsFalse() {
            assertFalse(HexcodeSpellConfig.isHexSpellSource(null));
        }
    }

    // =========================================================================
    // DAMAGE TYPE MAPPING
    // =========================================================================

    @Nested
    @DisplayName("Damage Type Mapping")
    class DamageTypeMapping {

        @Test
        @DisplayName("All default spell types map correctly")
        void allDefaults_mapCorrectly() {
            assertEquals(DamageType.MAGIC, config.getDamageType("hex_bolt"));
            assertEquals(DamageType.FIRE, config.getDamageType("hex_combust"));
            assertEquals(DamageType.WIND, config.getDamageType("hex_gust"));
            assertEquals(DamageType.WATER, config.getDamageType("hex_glaciate"));
            assertEquals(DamageType.EARTH, config.getDamageType("hex_ensnare"));
            assertEquals(DamageType.MAGIC, config.getDamageType("hex_phase"));
        }

        @Test
        @DisplayName("Unknown spell falls back to default (MAGIC)")
        void unknownSpell_fallsToDefault() {
            assertEquals(DamageType.MAGIC, config.getDamageType("hex_custom_unknown"));
        }
    }

    // =========================================================================
    // ELEMENT MAPPING
    // =========================================================================

    @Nested
    @DisplayName("Element Type Mapping")
    class ElementMapping {

        @Test
        @DisplayName("All default spell elements map correctly")
        void allDefaults_mapCorrectly() {
            assertEquals(ElementType.LIGHTNING, config.getElement("hex_bolt"));
            assertEquals(ElementType.FIRE, config.getElement("hex_combust"));
            assertEquals(ElementType.WIND, config.getElement("hex_gust"));
            assertEquals(ElementType.WATER, config.getElement("hex_glaciate"));
            assertEquals(ElementType.EARTH, config.getElement("hex_ensnare"));
            assertEquals(ElementType.VOID, config.getElement("hex_phase"));
        }

        @Test
        @DisplayName("Default fallback has no element")
        void defaultFallback_noElement() {
            assertNull(config.getElement("hex_unknown_glyph"));
        }
    }

    // =========================================================================
    // DISPLAY NAMES
    // =========================================================================

    @Nested
    @DisplayName("Spell Display Names")
    class DisplayNames {

        @Test
        @DisplayName("Known spells return display names")
        void knownSpells_returnNames() {
            assertEquals("Lightning Bolt", config.getDisplayName("hex_bolt"));
            assertEquals("Glaciate", config.getDisplayName("hex_glaciate"));
            assertEquals("Phase Crush", config.getDisplayName("hex_phase"));
        }

        @Test
        @DisplayName("Unknown spell returns 'Hex Spell'")
        void unknownSpell_returnsDefault() {
            assertEquals("Hex Spell", config.getDisplayName("hex_unknown"));
        }
    }

    // =========================================================================
    // CONSTRUCT SOURCES
    // =========================================================================

    @Nested
    @DisplayName("Construct Sources")
    class ConstructSources {

        @Test
        @DisplayName("Default construct sources include glaciate, ensnare, phase")
        void defaultConstructSources() {
            Set<String> sources = config.getConstructSources();
            assertTrue(sources.contains("hex_glaciate"));
            assertTrue(sources.contains("hex_ensnare"));
            assertTrue(sources.contains("hex_phase"));
            assertEquals(3, sources.size());
        }

        @Test
        @DisplayName("Bolt is NOT a construct source")
        void bolt_notConstruct() {
            assertFalse(config.getConstructSources().contains("hex_bolt"));
        }

        @Test
        @DisplayName("Construct sources set is unmodifiable")
        void constructSources_unmodifiable() {
            Set<String> sources = config.getConstructSources();
            assertThrows(UnsupportedOperationException.class, () -> sources.add("hex_test"));
        }
    }

    // =========================================================================
    // CONSTRUCT PHYSICS
    // =========================================================================

    @Nested
    @DisplayName("Construct Physics Config")
    class ConstructPhysicsTests {

        @Test
        @DisplayName("Glaciate has gravity=20.0 by default")
        void glaciate_hasGravity() {
            assertEquals(20.0f, config.getConstructGravity("hex_glaciate"), 0.001f);
        }

        @Test
        @DisplayName("Ensnare has gravity=0 (direct damage, not velocity-based)")
        void ensnare_noGravity() {
            assertEquals(0f, config.getConstructGravity("hex_ensnare"), 0.001f);
        }

        @Test
        @DisplayName("Phase has gravity=0 (direct damage, not velocity-based)")
        void phase_noGravity() {
            assertEquals(0f, config.getConstructGravity("hex_phase"), 0.001f);
        }

        @Test
        @DisplayName("Unknown source returns gravity=0")
        void unknownSource_noGravity() {
            assertEquals(0f, config.getConstructGravity("hex_bolt"), 0.001f);
            assertEquals(0f, config.getConstructGravity("hex_unknown"), 0.001f);
        }

        @Test
        @DisplayName("null source returns gravity=0")
        void nullSource_noGravity() {
            assertEquals(0f, config.getConstructGravity(null), 0.001f);
        }

        @Test
        @DisplayName("Max construct multiplier defaults to 5.0")
        void maxConstructMultiplier_default() {
            assertEquals(5.0f, config.getMaxConstructMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("ConstructPhysics inner class roundtrips through getters/setters")
        void constructPhysics_roundtrip() {
            HexcodeSpellConfig.ConstructPhysics physics = new HexcodeSpellConfig.ConstructPhysics();
            assertEquals(0f, physics.getGravity(), 0.001f);

            physics.setGravity(25.0f);
            assertEquals(25.0f, physics.getGravity(), 0.001f);
        }

        @Test
        @DisplayName("ConstructPhysics constructor sets gravity")
        void constructPhysics_constructor() {
            HexcodeSpellConfig.ConstructPhysics physics = new HexcodeSpellConfig.ConstructPhysics(30.0f);
            assertEquals(30.0f, physics.getGravity(), 0.001f);
        }

        @Test
        @DisplayName("Custom construct physics via YAML setters")
        void customConstructPhysics_viaSetters() {
            Map<String, HexcodeSpellConfig.ConstructPhysics> custom = new LinkedHashMap<>();
            custom.put("hex_custom", new HexcodeSpellConfig.ConstructPhysics(15.0f));
            config.setConstruct_physics(custom);

            assertEquals(15.0f, config.getConstructGravity("hex_custom"), 0.001f);
            // Old defaults are replaced
            assertEquals(0f, config.getConstructGravity("hex_glaciate"), 0.001f);
        }

        @Test
        @DisplayName("Max construct multiplier via YAML setters")
        void maxConstructMultiplier_viaSetters() {
            config.setMax_construct_multiplier(10.0f);
            assertEquals(10.0f, config.getMaxConstructMultiplier(), 0.001f);
        }
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Config Validation")
    class Validation {

        @Test
        @DisplayName("Default config passes validation")
        void defaultConfig_passes() {
            assertDoesNotThrow(() -> config.validate());
        }

        @Test
        @DisplayName("Negative resistance cap is clamped to 0")
        void negativeResistanceCap_clamped() {
            config.setMax_resistance_cap(-10f);
            config.validate();
            assertEquals(0f, config.getMax_resistance_cap(), 0.001f);
        }

        @Test
        @DisplayName("Resistance cap above 100 is clamped to 100")
        void highResistanceCap_clamped() {
            config.setMax_resistance_cap(150f);
            config.validate();
            assertEquals(100f, config.getMax_resistance_cap(), 0.001f);
        }

        @Test
        @DisplayName("Damage amplification below 1.0 is clamped")
        void lowAmplification_clamped() {
            config.setMax_damage_amplification(0.5f);
            config.validate();
            assertEquals(1.0f, config.getMax_damage_amplification(), 0.001f);
        }

        @Test
        @DisplayName("Negative mana cost reduction is clamped to 0")
        void negativeManaReduction_clamped() {
            config.setMax_mana_cost_reduction(-5f);
            config.validate();
            assertEquals(0f, config.getMax_mana_cost_reduction(), 0.001f);
        }

        @Test
        @DisplayName("Negative echo cooldown is clamped to 0")
        void negativeEchoCooldown_clamped() {
            config.setEcho_cooldown_ms(-100L);
            config.validate();
            assertEquals(0L, config.getEcho_cooldown_ms());
        }
    }

    // =========================================================================
    // VOLATILITY RATIO
    // =========================================================================

    @Nested
    @DisplayName("Volatility Ratio Config")
    class VolatilityRatio {

        @Test
        @DisplayName("Default exponent is 1.5")
        void defaultExponent() {
            assertEquals(1.5f, config.getVolatilityRatioExponent(), 0.001f);
        }

        @Test
        @DisplayName("Exponent roundtrips through YAML setter")
        void exponent_roundtrip() {
            config.setVolatility_ratio_exponent(2.0f);
            assertEquals(2.0f, config.getVolatilityRatioExponent(), 0.001f);
        }
    }
}
