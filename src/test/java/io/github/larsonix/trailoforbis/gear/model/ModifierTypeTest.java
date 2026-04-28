package io.github.larsonix.trailoforbis.gear.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModifierType enum.
 */
class ModifierTypeTest {

    @Test
    @DisplayName("PREFIX has correct config key")
    void prefix_hasCorrectConfigKey() {
        assertEquals("prefix", ModifierType.PREFIX.getConfigKey());
    }

    @Test
    @DisplayName("SUFFIX has correct config key")
    void suffix_hasCorrectConfigKey() {
        assertEquals("suffix", ModifierType.SUFFIX.getConfigKey());
    }

    @Test
    @DisplayName("fromConfigKey returns PREFIX for 'prefix'")
    void fromConfigKey_prefix_returnsPrefix() {
        assertEquals(ModifierType.PREFIX, ModifierType.fromConfigKey("prefix"));
    }

    @Test
    @DisplayName("fromConfigKey returns SUFFIX for 'suffix'")
    void fromConfigKey_suffix_returnsSuffix() {
        assertEquals(ModifierType.SUFFIX, ModifierType.fromConfigKey("suffix"));
    }

    @Test
    @DisplayName("fromConfigKey works with uppercase input")
    void fromConfigKey_uppercase_works() {
        assertEquals(ModifierType.PREFIX, ModifierType.fromConfigKey("PREFIX"));
        assertEquals(ModifierType.SUFFIX, ModifierType.fromConfigKey("SUFFIX"));
    }

    @Test
    @DisplayName("fromConfigKey works with mixed case input")
    void fromConfigKey_mixedCase_works() {
        assertEquals(ModifierType.PREFIX, ModifierType.fromConfigKey("Prefix"));
        assertEquals(ModifierType.SUFFIX, ModifierType.fromConfigKey("SuFfIx"));
    }

    @Test
    @DisplayName("fromConfigKey throws IllegalArgumentException for unknown key")
    void fromConfigKey_unknown_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ModifierType.fromConfigKey("unknown")
        );
        assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    @DisplayName("fromConfigKey throws NullPointerException for null key")
    void fromConfigKey_null_throwsException() {
        assertThrows(
            NullPointerException.class,
            () -> ModifierType.fromConfigKey(null)
        );
    }
}
