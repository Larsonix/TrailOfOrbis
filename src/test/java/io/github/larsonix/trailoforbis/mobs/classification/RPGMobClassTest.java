package io.github.larsonix.trailoforbis.mobs.classification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RPGMobClass} enum.
 *
 * <p>Verifies the 5-tier classification system behavior:
 * PASSIVE < MINOR < HOSTILE < ELITE < BOSS
 */
@DisplayName("RPGMobClass")
class RPGMobClassTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enum Values Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Enum Values")
    class EnumValuesTests {

        @Test
        @DisplayName("All 5 tiers are present")
        void allTiersPresent() {
            RPGMobClass[] values = RPGMobClass.values();
            assertEquals(5, values.length, "Should have 5 tiers");

            assertNotNull(RPGMobClass.PASSIVE);
            assertNotNull(RPGMobClass.MINOR);
            assertNotNull(RPGMobClass.HOSTILE);
            assertNotNull(RPGMobClass.ELITE);
            assertNotNull(RPGMobClass.BOSS);
        }

        @Test
        @DisplayName("Enum ordinals follow correct order")
        void enumOrdinalsCorrectOrder() {
            // PASSIVE < MINOR < HOSTILE < ELITE < BOSS
            assertTrue(RPGMobClass.PASSIVE.ordinal() < RPGMobClass.MINOR.ordinal());
            assertTrue(RPGMobClass.MINOR.ordinal() < RPGMobClass.HOSTILE.ordinal());
            assertTrue(RPGMobClass.HOSTILE.ordinal() < RPGMobClass.ELITE.ordinal());
            assertTrue(RPGMobClass.ELITE.ordinal() < RPGMobClass.BOSS.ordinal());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Combat Relevance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isCombatRelevant()")
    class CombatRelevanceTests {

        @Test
        @DisplayName("PASSIVE is not combat relevant")
        void passive_notCombatRelevant() {
            assertFalse(RPGMobClass.PASSIVE.isCombatRelevant(),
                "PASSIVE should not be combat relevant");
        }

        @Test
        @DisplayName("MINOR is combat relevant")
        void minor_isCombatRelevant() {
            assertTrue(RPGMobClass.MINOR.isCombatRelevant(),
                "MINOR should be combat relevant (receives stat scaling and grants XP)");
        }

        @Test
        @DisplayName("HOSTILE is combat relevant")
        void hostile_isCombatRelevant() {
            assertTrue(RPGMobClass.HOSTILE.isCombatRelevant(),
                "HOSTILE should be combat relevant");
        }

        @Test
        @DisplayName("ELITE is combat relevant")
        void elite_isCombatRelevant() {
            assertTrue(RPGMobClass.ELITE.isCombatRelevant(),
                "ELITE should be combat relevant");
        }

        @Test
        @DisplayName("BOSS is combat relevant")
        void boss_isCombatRelevant() {
            assertTrue(RPGMobClass.BOSS.isCombatRelevant(),
                "BOSS should be combat relevant");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Hostile Check Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isHostile()")
    class HostileTests {

        @Test
        @DisplayName("PASSIVE is not hostile")
        void passive_notHostile() {
            assertFalse(RPGMobClass.PASSIVE.isHostile(),
                "PASSIVE should not be hostile");
        }

        @Test
        @DisplayName("MINOR is hostile")
        void minor_isHostile() {
            assertTrue(RPGMobClass.MINOR.isHostile(),
                "MINOR should be hostile (they attack players, just give reduced XP)");
        }

        @Test
        @DisplayName("HOSTILE is hostile")
        void hostile_isHostile() {
            assertTrue(RPGMobClass.HOSTILE.isHostile(),
                "HOSTILE should be hostile");
        }

        @Test
        @DisplayName("ELITE is hostile")
        void elite_isHostile() {
            assertTrue(RPGMobClass.ELITE.isHostile(),
                "ELITE should be hostile");
        }

        @Test
        @DisplayName("BOSS is hostile")
        void boss_isHostile() {
            assertTrue(RPGMobClass.BOSS.isHostile(),
                "BOSS should be hostile");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Codec Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CODEC")
    class CodecTests {

        @Test
        @DisplayName("CODEC is available")
        void codecIsAvailable() {
            assertNotNull(RPGMobClass.CODEC, "CODEC should be available");
        }
    }
}
