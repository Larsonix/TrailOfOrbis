package io.github.larsonix.trailoforbis.gear.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GearRarity enum.
 */
class GearRarityTest {

    // Basic enum values

    @Test
    @DisplayName("All rarities have positive max modifiers")
    void allRarities_havePositiveMaxModifiers() {
        for (GearRarity rarity : GearRarity.values()) {
            assertTrue(rarity.getMaxModifiers() > 0,
                rarity + " should have positive max modifiers");
        }
    }

    @Test
    @DisplayName("All rarities have positive stat multiplier")
    void allRarities_havePositiveStatMultiplier() {
        for (GearRarity rarity : GearRarity.values()) {
            assertTrue(rarity.getStatMultiplier() > 0,
                rarity + " should have positive stat multiplier");
        }
    }

    @Test
    @DisplayName("All rarities have positive durability multiplier")
    void allRarities_havePositiveDurabilityMultiplier() {
        for (GearRarity rarity : GearRarity.values()) {
            assertTrue(rarity.getDurabilityMultiplier() > 0,
                rarity + " should have positive durability multiplier");
        }
    }

    @Test
    @DisplayName("All rarities have positive drop weight")
    void allRarities_havePositiveDropWeight() {
        for (GearRarity rarity : GearRarity.values()) {
            assertTrue(rarity.getDropWeight() > 0,
                rarity + " should have positive drop weight");
        }
    }

    @Test
    @DisplayName("All rarities have non-empty hex color")
    void allRarities_haveNonEmptyHexColor() {
        for (GearRarity rarity : GearRarity.values()) {
            assertNotNull(rarity.getHexColor());
            assertFalse(rarity.getHexColor().isBlank(),
                rarity + " should have non-blank hex color");
            assertTrue(rarity.getHexColor().startsWith("#"),
                rarity + " hex color should start with #");
        }
    }

    @Test
    @DisplayName("All rarities have non-empty Hytale quality ID")
    void allRarities_haveNonEmptyHytaleQualityId() {
        for (GearRarity rarity : GearRarity.values()) {
            assertNotNull(rarity.getHytaleQualityId());
            assertFalse(rarity.getHytaleQualityId().isBlank(),
                rarity + " should have non-blank Hytale quality ID");
        }
    }

    // Ordering (critical for tier comparison)

    @Test
    @DisplayName("Rarity order is COMMON to UNIQUE")
    void rarityOrder_isCommonToUnique() {
        GearRarity[] expected = {
            GearRarity.COMMON,
            GearRarity.UNCOMMON,
            GearRarity.RARE,
            GearRarity.EPIC,
            GearRarity.LEGENDARY,
            GearRarity.MYTHIC,
            GearRarity.UNIQUE
        };
        assertArrayEquals(expected, GearRarity.values());
    }

    @Test
    @DisplayName("COMMON is the lowest ordinal")
    void common_isLowestOrdinal() {
        assertEquals(0, GearRarity.COMMON.ordinal());
    }

    @Test
    @DisplayName("UNIQUE is the highest ordinal")
    void unique_isHighestOrdinal() {
        assertEquals(GearRarity.values().length - 1, GearRarity.UNIQUE.ordinal());
    }

    @Test
    @DisplayName("MYTHIC is second highest ordinal")
    void mythic_isSecondHighestOrdinal() {
        assertEquals(GearRarity.values().length - 2, GearRarity.MYTHIC.ordinal());
    }

    // Stat multiplier ordering

    @Test
    @DisplayName("Stat multiplier increases with rarity")
    void statMultiplier_increasesWithRarity() {
        GearRarity[] rarities = GearRarity.values();
        for (int i = 1; i < rarities.length; i++) {
            assertTrue(rarities[i].getStatMultiplier() >= rarities[i - 1].getStatMultiplier(),
                rarities[i] + " should have stat multiplier >= " + rarities[i - 1]);
        }
    }

    // Durability multiplier ordering

    @Test
    @DisplayName("Durability multiplier increases with rarity")
    void durabilityMultiplier_increasesWithRarity() {
        GearRarity[] rarities = GearRarity.values();
        for (int i = 1; i < rarities.length; i++) {
            assertTrue(rarities[i].getDurabilityMultiplier() >= rarities[i - 1].getDurabilityMultiplier(),
                rarities[i] + " should have durability multiplier >= " + rarities[i - 1]);
        }
    }

    // Drop weight ordering (inverted - rarer = lower weight)

    @Test
    @DisplayName("Drop weight decreases with rarity")
    void dropWeight_decreasesWithRarity() {
        GearRarity[] rarities = GearRarity.values();
        for (int i = 1; i < rarities.length; i++) {
            assertTrue(rarities[i].getDropWeight() <= rarities[i - 1].getDropWeight(),
                rarities[i] + " should have drop weight <= " + rarities[i - 1]);
        }
    }

    // Max modifiers

    @Test
    @DisplayName("COMMON has 1 max modifier")
    void common_hasOneMaxModifier() {
        assertEquals(1, GearRarity.COMMON.getMaxModifiers());
    }

    @Test
    @DisplayName("UNCOMMON has 2 max modifiers")
    void uncommon_hasTwoMaxModifiers() {
        assertEquals(2, GearRarity.UNCOMMON.getMaxModifiers());
    }

    @Test
    @DisplayName("RARE has 3 max modifiers")
    void rare_hasThreeMaxModifiers() {
        assertEquals(3, GearRarity.RARE.getMaxModifiers());
    }

    @Test
    @DisplayName("EPIC has 4 max modifiers")
    void epic_hasFourMaxModifiers() {
        assertEquals(4, GearRarity.EPIC.getMaxModifiers());
    }

    @Test
    @DisplayName("LEGENDARY has 5 max modifiers")
    void legendary_hasFiveMaxModifiers() {
        assertEquals(5, GearRarity.LEGENDARY.getMaxModifiers());
    }

    @Test
    @DisplayName("MYTHIC has 6 max modifiers")
    void mythic_hasSixMaxModifiers() {
        assertEquals(6, GearRarity.MYTHIC.getMaxModifiers());
    }

    // Tier navigation

    @Test
    @DisplayName("COMMON.getNextTier() returns UNCOMMON")
    void common_getNextTier_returnsUncommon() {
        assertEquals(Optional.of(GearRarity.UNCOMMON), GearRarity.COMMON.getNextTier());
    }

    @Test
    @DisplayName("LEGENDARY.getNextTier() returns MYTHIC")
    void legendary_getNextTier_returnsMythic() {
        assertEquals(Optional.of(GearRarity.MYTHIC), GearRarity.LEGENDARY.getNextTier());
    }

    @Test
    @DisplayName("MYTHIC.getNextTier() returns empty")
    void mythic_getNextTier_returnsEmpty() {
        assertEquals(Optional.empty(), GearRarity.MYTHIC.getNextTier());
    }

    @Test
    @DisplayName("MYTHIC.getPreviousTier() returns LEGENDARY")
    void mythic_getPreviousTier_returnsLegendary() {
        assertEquals(Optional.of(GearRarity.LEGENDARY), GearRarity.MYTHIC.getPreviousTier());
    }

    @Test
    @DisplayName("UNCOMMON.getPreviousTier() returns COMMON")
    void uncommon_getPreviousTier_returnsCommon() {
        assertEquals(Optional.of(GearRarity.COMMON), GearRarity.UNCOMMON.getPreviousTier());
    }

    @Test
    @DisplayName("COMMON.getPreviousTier() returns empty")
    void common_getPreviousTier_returnsEmpty() {
        assertEquals(Optional.empty(), GearRarity.COMMON.getPreviousTier());
    }

    // isAtLeast

    @Test
    @DisplayName("EPIC.isAtLeast(RARE) returns true")
    void epic_isAtLeast_rare_returnsTrue() {
        assertTrue(GearRarity.EPIC.isAtLeast(GearRarity.RARE));
    }

    @Test
    @DisplayName("EPIC.isAtLeast(EPIC) returns true")
    void epic_isAtLeast_epic_returnsTrue() {
        assertTrue(GearRarity.EPIC.isAtLeast(GearRarity.EPIC));
    }

    @Test
    @DisplayName("EPIC.isAtLeast(LEGENDARY) returns false")
    void epic_isAtLeast_legendary_returnsFalse() {
        assertFalse(GearRarity.EPIC.isAtLeast(GearRarity.LEGENDARY));
    }

    @Test
    @DisplayName("COMMON.isAtLeast(COMMON) returns true")
    void common_isAtLeast_common_returnsTrue() {
        assertTrue(GearRarity.COMMON.isAtLeast(GearRarity.COMMON));
    }

    // fromString

    @Test
    @DisplayName("fromString works with uppercase")
    void fromString_uppercase_works() {
        assertEquals(GearRarity.RARE, GearRarity.fromString("RARE"));
    }

    @Test
    @DisplayName("fromString works with lowercase")
    void fromString_lowercase_works() {
        assertEquals(GearRarity.RARE, GearRarity.fromString("rare"));
    }

    @Test
    @DisplayName("fromString works with mixed case")
    void fromString_mixedCase_works() {
        assertEquals(GearRarity.RARE, GearRarity.fromString("Rare"));
        assertEquals(GearRarity.LEGENDARY, GearRarity.fromString("LeGeNdArY"));
    }

    @Test
    @DisplayName("fromString throws IllegalArgumentException for null")
    void fromString_null_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> GearRarity.fromString(null)
        );
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    @DisplayName("fromString throws IllegalArgumentException for unknown name")
    void fromString_unknown_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> GearRarity.fromString("SUPER_RARE")
        );
        assertTrue(ex.getMessage().contains("Unknown rarity"));
    }

    @Test
    @DisplayName("fromString throws IllegalArgumentException for empty string")
    void fromString_emptyString_throwsException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> GearRarity.fromString("")
        );
    }
}
