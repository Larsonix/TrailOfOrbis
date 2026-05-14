package io.github.larsonix.trailoforbis.combat.indicators;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageTrace;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapRecorder;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for CombatIndicatorService, focusing on the AoE screen flash dedup.
 *
 * <p>When 40 mobs hit a player simultaneously (or AoE reflects damage back),
 * the player receives 40 DamageInfo screen flash packets — but only sees ONE
 * flash. The dedup skips redundant flashes within a 50ms window.
 */
public class CombatIndicatorServiceTest {

    @Mock private CombatEntityResolver entityResolver;
    @Mock private TrailOfOrbis plugin;
    @Mock private Store<EntityStore> store;
    @Mock private Ref<EntityStore> defenderRef;
    @Mock private Ref<EntityStore> attackerRef;

    private CombatIndicatorService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CombatIndicatorService(entityResolver, plugin, null, null);
    }

    // ==================== Screen Flash Dedup ====================

    @Nested
    @DisplayName("Screen Flash Dedup")
    class ScreenFlashDedupTests {

        @Test
        @DisplayName("Null attacker ref skips indicator entirely")
        void sendDefenderIndicator_nullAttacker_skips() {
            // Should not throw
            service.sendDefenderIndicator(store, defenderRef, null, 50f, null);
            // No PlayerRef lookup should happen
            verify(store, never()).getComponent(eq(defenderRef), any());
        }

        @Test
        @DisplayName("Null cause skips indicator entirely")
        void sendDefenderIndicator_nullCause_skips() {
            service.sendDefenderIndicator(store, defenderRef, attackerRef, 50f, null);
            verify(store, never()).getComponent(eq(defenderRef), any());
        }

        // Note: Non-player defender test skipped — requires PlayerRef.getComponentType()
        // which is unavailable in unit test context (Hytale runtime dependency).
    }

    // ==================== Avoidance Indicator Tests ====================

    @Nested
    @DisplayName("Avoidance Indicators")
    class AvoidanceTests {

        @Test
        @DisplayName("Null attacker ref still works for avoidance")
        void sendAvoidanceIndicators_nullAttacker_noException() {
            Damage damage = mock(Damage.class);
            when(entityResolver.getAttackerRef(any(), any())).thenReturn(null);

            service.sendAvoidanceIndicators(store, defenderRef, damage,
                DamageBreakdown.AvoidanceReason.DODGED);
            // Should not throw — graceful null handling
        }
    }
}
