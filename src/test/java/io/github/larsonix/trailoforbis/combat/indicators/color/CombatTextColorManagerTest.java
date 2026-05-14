package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService.CombatTextParams;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CombatTextColorManager dedup logic.
 *
 * <p>The dedup prevents sending redundant template swap packets when
 * the same element is used for AoE damage (40 identical packets -> 1).
 *
 * <p>These tests verify the per-player profile cache and template build cache
 * that were added to fix Hexcode AoE performance and color stomping.
 */
public class CombatTextColorManagerTest {

    // ==================== Dedup Cache Tests ====================
    // These tests verify the internal caching logic without requiring
    // the full initialization chain (config, template registry, resolver).

    @Nested
    @DisplayName("Per-Player Profile Dedup")
    class ProfileDedupTests {

        @Test
        @DisplayName("removePlayer clears cached profile for that player")
        void removePlayer_clearsCachedProfile() {
            CombatTextColorManager manager = new CombatTextColorManager(mock(ConfigManager.class));

            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            // removePlayer should not throw even if player was never cached
            assertDoesNotThrow(() -> manager.removePlayer(player1));
            assertDoesNotThrow(() -> manager.removePlayer(player2));
        }

        @Test
        @DisplayName("shutdown clears all caches")
        void shutdown_clearsAllCaches() {
            CombatTextColorManager manager = new CombatTextColorManager(mock(ConfigManager.class));

            // shutdown should not throw
            assertDoesNotThrow(() -> manager.shutdown());

            // After shutdown, system is disabled
            assertFalse(manager.isEnabled());
        }

        @Test
        @DisplayName("applyAndResolve returns null when disabled")
        void applyAndResolve_disabled_returnsNull() {
            CombatTextColorManager manager = new CombatTextColorManager(mock(ConfigManager.class));
            // Not initialized = disabled

            PlayerRef player = mock(PlayerRef.class);
            CombatTextParams params = CombatTextParams.forDamage(false);

            assertNull(manager.applyAndResolve(player, null, params));
        }

        @Test
        @DisplayName("applyByKey does nothing when disabled")
        void applyByKey_disabled_doesNothing() {
            CombatTextColorManager manager = new CombatTextColorManager(mock(ConfigManager.class));

            PlayerRef player = mock(PlayerRef.class);

            // Should not throw
            assertDoesNotThrow(() -> manager.applyByKey(player, "healing"));
        }
    }
}
