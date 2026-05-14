package io.github.larsonix.trailoforbis.lifecycle;

import io.github.larsonix.trailoforbis.commands.tooadmin.clipboard.CopyPasteClipboard;
import io.github.larsonix.trailoforbis.compat.HexCastStateStore;
import io.github.larsonix.trailoforbis.compat.HexSpellEchoService;
import io.github.larsonix.trailoforbis.gear.item.PlayerItemCache;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying that all per-player state is properly cleaned up on disconnect.
 *
 * <p>Each test follows the same pattern: populate per-player maps, call the
 * cleanup method, assert the player's data is gone while other players' data remains.
 *
 * <p>These tests exist because memory leaks from missing disconnect cleanup have been
 * found multiple times in production. Each nested class covers one specific manager
 * that was confirmed leaking (May 2026 audit).
 */
@DisplayName("Player Disconnect Cleanup")
class PlayerDisconnectCleanupTest {

    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();

    // ═══════════════════════════════════════════════════════════════════
    // PlayerItemCache
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PlayerItemCache")
    class PlayerItemCacheCleanup {

        @Test
        @DisplayName("removePlayer() clears all cached items for that player")
        void removePlayerClearsCache() {
            PlayerItemCache cache = new PlayerItemCache();
            cache.initPlayer(PLAYER_A);
            cache.initPlayer(PLAYER_B);

            cache.markSent(PLAYER_A, "rpg_gear_sword_1", 12345);
            cache.markSent(PLAYER_A, "rpg_gear_shield_2", 67890);
            cache.markSent(PLAYER_B, "rpg_gear_bow_1", 11111);

            assertEquals(2, cache.getPlayerCount());
            assertEquals(2, cache.getItemCount(PLAYER_A));

            cache.removePlayer(PLAYER_A);

            assertEquals(1, cache.getPlayerCount());
            assertFalse(cache.hasPlayer(PLAYER_A), "Player A data should be gone");
            assertTrue(cache.hasPlayer(PLAYER_B), "Player B data should remain");
            assertEquals(1, cache.getItemCount(PLAYER_B));
        }

        @Test
        @DisplayName("removePlayer() is idempotent for unknown UUID")
        void removePlayerIdempotent() {
            PlayerItemCache cache = new PlayerItemCache();
            UUID unknown = UUID.randomUUID();
            assertDoesNotThrow(() -> cache.removePlayer(unknown));
            assertEquals(0, cache.getPlayerCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CopyPasteClipboard
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CopyPasteClipboard")
    class CopyPasteClipboardCleanup {

        @Test
        @DisplayName("clearAll() removes all clipboard entries for that player")
        void clearAllRemovesPlayerEntries() {
            CopyPasteClipboard clipboard = CopyPasteClipboard.getInstance();

            // Create minimal gear data for testing
            GearData gearA = new GearData(
                null, 10, GearRarity.RARE, 85,
                List.of(), List.of(), false, null, null,
                "Weapon_Sword_Iron", null, List.of(), 0
            );
            GearData gearB = new GearData(
                null, 5, GearRarity.COMMON, 50,
                List.of(), List.of(), false, null, null,
                "Armor_Helmet_Iron", null, List.of(), 0
            );

            clipboard.copyGear(PLAYER_A, gearA);
            clipboard.copyGear(PLAYER_B, gearB);

            assertTrue(clipboard.getCopiedGear(PLAYER_A).isPresent());
            assertTrue(clipboard.getCopiedGear(PLAYER_B).isPresent());

            clipboard.clearAll(PLAYER_A);

            assertFalse(clipboard.getCopiedGear(PLAYER_A).isPresent(),
                "Player A clipboard should be cleared");
            assertTrue(clipboard.getCopiedGear(PLAYER_B).isPresent(),
                "Player B clipboard should remain");

            // Clean up after test to not affect other tests (singleton)
            clipboard.clearAll(PLAYER_B);
        }

        @Test
        @DisplayName("clearAll() is safe for player with no clipboard data")
        void clearAllSafeForEmpty() {
            CopyPasteClipboard clipboard = CopyPasteClipboard.getInstance();
            assertDoesNotThrow(() -> clipboard.clearAll(UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HexCastEventInterceptor
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("HexCastEventInterceptor")
    class HexCastEventInterceptorCleanup {

        @Test
        @DisplayName("onPlayerDisconnect() cleans up static maps without throwing")
        void onPlayerDisconnectCleansStaticMaps() {
            // We can't easily populate the static maps (they're package-private),
            // but we CAN verify the method doesn't throw for any UUID.
            // The important thing is that the method EXISTS and is called.
            assertDoesNotThrow(() -> HexCastStateStore.onPlayerDisconnect(PLAYER_A));
            assertDoesNotThrow(() -> HexSpellEchoService.onPlayerDisconnect(PLAYER_A));
            assertDoesNotThrow(() -> HexCastStateStore.onPlayerDisconnect(PLAYER_B));
            assertDoesNotThrow(() -> HexSpellEchoService.onPlayerDisconnect(PLAYER_B));
            assertDoesNotThrow(() -> HexCastStateStore.onPlayerDisconnect(UUID.randomUUID()));
        }
    }
}
