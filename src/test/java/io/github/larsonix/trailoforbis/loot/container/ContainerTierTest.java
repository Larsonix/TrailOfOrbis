package io.github.larsonix.trailoforbis.loot.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContainerTier} enum — config keys, defaults, and lookup.
 */
class ContainerTierTest {

    // =========================================================================
    // Enum Values and Defaults
    // =========================================================================

    @Nested
    @DisplayName("Enum values")
    class EnumValues {

        @Test
        @DisplayName("BASIC has correct config key and defaults")
        void basicTierDefaults() {
            assertEquals("basic", ContainerTier.BASIC.getConfigKey());
            assertEquals(1.0, ContainerTier.BASIC.getDefaultLootMultiplier());
            assertEquals(0.0, ContainerTier.BASIC.getDefaultRarityBonus());
        }

        @Test
        @DisplayName("DUNGEON has correct config key and defaults")
        void dungeonTierDefaults() {
            assertEquals("dungeon", ContainerTier.DUNGEON.getConfigKey());
            assertEquals(1.5, ContainerTier.DUNGEON.getDefaultLootMultiplier());
            assertEquals(0.15, ContainerTier.DUNGEON.getDefaultRarityBonus());
        }

        @Test
        @DisplayName("BOSS has correct config key and defaults")
        void bossTierDefaults() {
            assertEquals("boss", ContainerTier.BOSS.getConfigKey());
            assertEquals(2.0, ContainerTier.BOSS.getDefaultLootMultiplier());
            assertEquals(0.30, ContainerTier.BOSS.getDefaultRarityBonus());
        }

        @Test
        @DisplayName("SPECIAL has correct config key and defaults")
        void specialTierDefaults() {
            assertEquals("special", ContainerTier.SPECIAL.getConfigKey());
            assertEquals(1.75, ContainerTier.SPECIAL.getDefaultLootMultiplier());
            assertEquals(0.25, ContainerTier.SPECIAL.getDefaultRarityBonus());
        }

        @Test
        @DisplayName("Exactly four tiers exist")
        void fourTiersExist() {
            assertEquals(4, ContainerTier.values().length);
        }
    }

    // =========================================================================
    // fromConfigKey
    // =========================================================================

    @Nested
    @DisplayName("fromConfigKey()")
    class FromConfigKey {

        @Test
        @DisplayName("Exact lowercase keys resolve correctly")
        void exactLowercaseKeys() {
            assertEquals(ContainerTier.BASIC, ContainerTier.fromConfigKey("basic"));
            assertEquals(ContainerTier.DUNGEON, ContainerTier.fromConfigKey("dungeon"));
            assertEquals(ContainerTier.BOSS, ContainerTier.fromConfigKey("boss"));
            assertEquals(ContainerTier.SPECIAL, ContainerTier.fromConfigKey("special"));
        }

        @Test
        @DisplayName("Case-insensitive matching works")
        void caseInsensitive() {
            assertEquals(ContainerTier.BOSS, ContainerTier.fromConfigKey("BOSS"));
            assertEquals(ContainerTier.BOSS, ContainerTier.fromConfigKey("Boss"));
            assertEquals(ContainerTier.DUNGEON, ContainerTier.fromConfigKey("DUNGEON"));
            assertEquals(ContainerTier.DUNGEON, ContainerTier.fromConfigKey("Dungeon"));
            assertEquals(ContainerTier.SPECIAL, ContainerTier.fromConfigKey("SpEcIaL"));
        }

        @Test
        @DisplayName("Unknown config key returns BASIC")
        void unknownKeyReturnsBasic() {
            assertEquals(ContainerTier.BASIC, ContainerTier.fromConfigKey("unknown"));
            assertEquals(ContainerTier.BASIC, ContainerTier.fromConfigKey(""));
            assertEquals(ContainerTier.BASIC, ContainerTier.fromConfigKey("legendary"));
        }
    }

    // =========================================================================
    // Ordering
    // =========================================================================

    @Nested
    @DisplayName("Tier ordering")
    class TierOrdering {

        @Test
        @DisplayName("Loot multiplier increases: BASIC < DUNGEON < SPECIAL < BOSS")
        void lootMultiplierOrdering() {
            assertTrue(ContainerTier.BASIC.getDefaultLootMultiplier()
                    < ContainerTier.DUNGEON.getDefaultLootMultiplier());
            assertTrue(ContainerTier.DUNGEON.getDefaultLootMultiplier()
                    < ContainerTier.SPECIAL.getDefaultLootMultiplier());
            assertTrue(ContainerTier.SPECIAL.getDefaultLootMultiplier()
                    < ContainerTier.BOSS.getDefaultLootMultiplier());
        }

        @Test
        @DisplayName("Rarity bonus increases: BASIC < DUNGEON < SPECIAL < BOSS")
        void rarityBonusOrdering() {
            assertTrue(ContainerTier.BASIC.getDefaultRarityBonus()
                    < ContainerTier.DUNGEON.getDefaultRarityBonus());
            assertTrue(ContainerTier.DUNGEON.getDefaultRarityBonus()
                    < ContainerTier.SPECIAL.getDefaultRarityBonus());
            assertTrue(ContainerTier.SPECIAL.getDefaultRarityBonus()
                    < ContainerTier.BOSS.getDefaultRarityBonus());
        }

        @Test
        @DisplayName("Enum ordinal order: BASIC, DUNGEON, BOSS, SPECIAL")
        void ordinalOrder() {
            assertEquals(0, ContainerTier.BASIC.ordinal());
            assertEquals(1, ContainerTier.DUNGEON.ordinal());
            assertEquals(2, ContainerTier.BOSS.ordinal());
            assertEquals(3, ContainerTier.SPECIAL.ordinal());
        }
    }
}
