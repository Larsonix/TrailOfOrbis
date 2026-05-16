package io.github.larsonix.trailoforbis.skilltree.conditional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link ConditionalEffectTracker} fires visual callbacks correctly
 * at all activation, deactivation, refresh, and cleanup hook points.
 */
@DisplayName("ConditionalVisualCallback Integration")
class ConditionalVisualCallbackTest {

    private ConditionalEffectTracker tracker;
    private MockVisualCallback callback;
    private UUID playerId;
    private long currentTime;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        tracker = new ConditionalEffectTracker(playerId);
        callback = new MockVisualCallback();
        tracker.setVisualCallback(callback);
        currentTime = System.currentTimeMillis();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTIVATION CALLBACKS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Activation Callbacks")
    class ActivationCallbacks {

        @Test
        @DisplayName("Should fire onActivate when new effect activates")
        void shouldFireOnActivateForNewEffect() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH);

            tracker.activateEffect("node1", config, currentTime);

            assertEquals(1, callback.activations.size());
            MockVisualCallback.Activation a = callback.activations.get(0);
            assertEquals(playerId, a.playerId);
            assertEquals("node1", a.nodeId);
            assertEquals(4.0f, a.duration, 0.01f);
        }

        @Test
        @DisplayName("Should fire onActivate on REFRESH stacking")
        void shouldFireOnActivateOnRefresh() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_CRIT, 3.0, StackingBehavior.REFRESH);

            tracker.activateEffect("node1", config, currentTime);
            tracker.activateEffect("node1", config, currentTime + 1000); // Refresh while active

            assertEquals(2, callback.activations.size());
            // Both should fire with the same duration (refreshed)
            assertEquals(3.0f, callback.activations.get(1).duration, 0.01f);
        }

        @Test
        @DisplayName("Should fire onActivate on STACK behavior")
        void shouldFireOnActivateOnStack() {
            ConditionalConfig config = createConfig(ConditionalTrigger.WHEN_HIT, 2.0, StackingBehavior.STACK);

            tracker.activateEffect("node1", config, currentTime);
            tracker.activateEffect("node1", config, currentTime + 500);

            assertEquals(2, callback.activations.size());
        }

        @Test
        @DisplayName("Should fire onActivate on EXTEND_DURATION with total remaining time")
        void shouldFireOnActivateOnExtend() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_BLOCK, 3.0, StackingBehavior.EXTEND_DURATION);

            tracker.activateEffect("node1", config, currentTime);
            tracker.activateEffect("node1", config, currentTime + 1000); // Extend at t+1s

            assertEquals(2, callback.activations.size());
            // Extended: original 3s had 2s remaining + 3s added = 5s remaining
            float extendedDuration = callback.activations.get(1).duration;
            assertEquals(5.0f, extendedDuration, 0.01f);
        }

        @Test
        @DisplayName("Should NOT fire onActivate on NO_REFRESH when already active")
        void shouldNotFireOnNoRefresh() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.NO_REFRESH);

            tracker.activateEffect("node1", config, currentTime);
            tracker.activateEffect("node1", config, currentTime + 500); // Blocked

            assertEquals(1, callback.activations.size()); // Only the first
        }

        @Test
        @DisplayName("Should NOT fire onActivate when on cooldown")
        void shouldNotFireWhenOnCooldown() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH);
            config.setCooldown(10.0); // 10s cooldown

            tracker.activateEffect("node1", config, currentTime);
            // Effect expires at t+4s, cooldown until t+10s
            tracker.activateEffect("node1", config, currentTime + 5000); // On cooldown

            assertEquals(1, callback.activations.size());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEACTIVATION CALLBACKS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Deactivation Callbacks")
    class DeactivationCallbacks {

        @Test
        @DisplayName("Should fire onDeactivate when effect expires in cleanup")
        void shouldFireOnDeactivateOnExpiry() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH);
            tracker.activateEffect("node1", config, currentTime);

            // Advance past expiration
            tracker.cleanup(currentTime + 5000);

            assertEquals(1, callback.deactivations.size());
            assertEquals(playerId, callback.deactivations.get(0).playerId);
            assertEquals("node1", callback.deactivations.get(0).nodeId);
        }

        @Test
        @DisplayName("Should fire onDeactivate on manual deactivation")
        void shouldFireOnDeactivateOnManualRemoval() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_CRIT, 3.0, StackingBehavior.REFRESH);
            tracker.activateEffect("node1", config, currentTime);

            tracker.deactivateEffect("node1");

            assertEquals(1, callback.deactivations.size());
        }

        @Test
        @DisplayName("Should fire onDeactivate on CONSUME_ON_HIT")
        void shouldFireOnDeactivateOnConsume() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.CONSUME_ON_HIT);
            tracker.activateEffect("node1", config, currentTime);

            tracker.consumeOnHit(currentTime + 500);

            assertEquals(1, callback.deactivations.size());
            assertEquals("node1", callback.deactivations.get(0).nodeId);
        }

        @Test
        @DisplayName("Should NOT fire onDeactivate for already-expired effects")
        void shouldNotFireForAlreadyExpired() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH);
            tracker.activateEffect("node1", config, currentTime);

            // First cleanup removes expired effect
            tracker.cleanup(currentTime + 5000);
            callback.deactivations.clear();

            // Second cleanup should find nothing
            tracker.cleanup(currentTime + 10000);
            assertEquals(0, callback.deactivations.size());
        }

        @Test
        @DisplayName("Should fire onDeactivate for multiple expired effects")
        void shouldFireForMultipleExpiredEffects() {
            ConditionalConfig config1 = createConfig(ConditionalTrigger.ON_KILL, 2.0, StackingBehavior.REFRESH);
            ConditionalConfig config2 = createConfig(ConditionalTrigger.ON_CRIT, 3.0, StackingBehavior.REFRESH);

            tracker.activateEffect("node1", config1, currentTime);
            tracker.activateEffect("node2", config2, currentTime);

            // Both expire after 4s
            tracker.cleanup(currentTime + 4000);

            assertEquals(2, callback.deactivations.size());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NULL CALLBACK SAFETY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Null Callback Safety")
    class NullCallbackSafety {

        @Test
        @DisplayName("Should not throw when callback is null")
        void shouldNotThrowWithNullCallback() {
            tracker.setVisualCallback(null);
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH);

            assertDoesNotThrow(() -> {
                tracker.activateEffect("node1", config, currentTime);
                tracker.cleanup(currentTime + 5000);
                tracker.deactivateEffect("node1");
                tracker.consumeOnHit(currentTime);
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MULTI-NODE SAME TRIGGER (bug regression test)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-Node Same Trigger")
    class MultiNodeSameTrigger {

        @Test
        @DisplayName("Two nodes with same trigger should fire onActivate for each")
        void twoNodesSameTriggerBothFireActivate() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH);

            tracker.activateEffect("nodeA", config, currentTime);
            tracker.activateEffect("nodeB", config, currentTime);

            assertEquals(2, callback.activations.size());
            assertEquals("nodeA", callback.activations.get(0).nodeId);
            assertEquals("nodeB", callback.activations.get(1).nodeId);
        }

        @Test
        @DisplayName("Deactivating one node should still fire onDeactivate (manager decides removal)")
        void deactivateOneNodeStillFiresCallback() {
            ConditionalConfig config = createConfig(ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH);

            tracker.activateEffect("nodeA", config, currentTime);
            tracker.activateEffect("nodeB", config, currentTime);

            tracker.deactivateEffect("nodeA");

            // Callback fires for nodeA deactivation — manager decides whether to remove icon
            assertEquals(1, callback.deactivations.size());
            assertEquals("nodeA", callback.deactivations.get(0).nodeId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private ConditionalConfig createConfig(ConditionalTrigger trigger, double duration, StackingBehavior stacking) {
        ConditionalConfig config = new ConditionalConfig();
        config.setTrigger(trigger);
        config.setDuration(duration);
        config.setStacking(stacking);
        config.setMaxStacks(5);
        config.setCooldown(0);

        ConditionalConfig.ConditionalEffect effect = new ConditionalConfig.ConditionalEffect();
        effect.setStat("ATTACK_SPEED_PERCENT");
        effect.setValue(10.0);
        effect.setModifierType("PERCENT");
        config.setEffects(List.of(effect));

        return config;
    }

    /**
     * Mock callback that records all activations and deactivations for assertion.
     */
    static class MockVisualCallback implements ConditionalVisualCallback {
        final List<Activation> activations = new ArrayList<>();
        final List<Deactivation> deactivations = new ArrayList<>();

        @Override
        public void onActivate(@Nonnull UUID playerId, @Nonnull String nodeId, float durationSeconds) {
            activations.add(new Activation(playerId, nodeId, durationSeconds));
        }

        @Override
        public void onDeactivate(@Nonnull UUID playerId, @Nonnull String nodeId) {
            deactivations.add(new Deactivation(playerId, nodeId));
        }

        record Activation(UUID playerId, String nodeId, float duration) {}
        record Deactivation(UUID playerId, String nodeId) {}
    }
}
