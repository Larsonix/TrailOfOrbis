package io.github.larsonix.trailoforbis.skilltree.conditional;

import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConditionalEffectTracker}.
 */
@DisplayName("ConditionalEffectTracker")
class ConditionalEffectTrackerTest {

    private ConditionalEffectTracker tracker;
    private UUID playerId;
    private long currentTime;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        tracker = new ConditionalEffectTracker(playerId);
        currentTime = System.currentTimeMillis();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private ConditionalConfig createTimedConfig(
        ConditionalTrigger trigger,
        double duration,
        StackingBehavior stacking,
        String stat,
        double value
    ) {
        ConditionalConfig config = new ConditionalConfig();
        config.setTrigger(trigger);
        config.setDuration(duration);
        config.setStacking(stacking);
        config.setMaxStacks(5);
        config.setCooldown(0);

        ConditionalConfig.ConditionalEffect effect = new ConditionalConfig.ConditionalEffect();
        effect.setStat(stat);
        effect.setValue(value);
        effect.setModifierType("PERCENT");
        config.setEffects(List.of(effect));

        return config;
    }

    private ConditionalConfig createConfigWithCooldown(double duration, double cooldown) {
        ConditionalConfig config = createTimedConfig(
            ConditionalTrigger.ON_KILL, duration, StackingBehavior.REFRESH,
            "ATTACK_SPEED_PERCENT", 20.0
        );
        config.setCooldown(cooldown);
        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTIVATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Effect Activation")
    class ActivationTests {

        @Test
        @DisplayName("Should activate new effect")
        void shouldActivateNewEffect() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );

            boolean activated = tracker.activateEffect("node1", config, currentTime);

            assertTrue(activated);
            assertTrue(tracker.isEffectActive("node1", currentTime));
            assertEquals(1, tracker.getStackCount("node1", currentTime));
        }

        @Test
        @DisplayName("Should provide active modifiers")
        void shouldProvideActiveModifiers() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );

            tracker.activateEffect("node1", config, currentTime);

            List<StatModifier> modifiers = tracker.getActiveModifiers(currentTime);

            assertEquals(1, modifiers.size());
            StatModifier modifier = modifiers.get(0);
            assertEquals(StatType.ATTACK_SPEED_PERCENT, modifier.getStat());
            assertEquals(20.0f, modifier.getValue(), 0.001f);
            assertEquals(ModifierType.PERCENT, modifier.getType());
        }

        @Test
        @DisplayName("Should expire effect after duration")
        void shouldExpireEffectAfterDuration() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );

            tracker.activateEffect("node1", config, currentTime);

            // Still active before expiration
            assertTrue(tracker.isEffectActive("node1", currentTime + 3000));

            // Expired after duration
            assertFalse(tracker.isEffectActive("node1", currentTime + 5000));
        }

        @Test
        @DisplayName("Should calculate remaining duration")
        void shouldCalculateRemainingDuration() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );

            tracker.activateEffect("node1", config, currentTime);

            // Check at 1 second
            double remaining = tracker.getRemainingDuration("node1", currentTime + 1000);
            assertEquals(3.0, remaining, 0.1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STACKING BEHAVIOR TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stacking Behavior")
    class StackingTests {

        @Test
        @DisplayName("REFRESH should reset duration")
        void refreshShouldResetDuration() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );

            tracker.activateEffect("node1", config, currentTime);

            // Trigger again at 3 seconds
            boolean refreshed = tracker.activateEffect("node1", config, currentTime + 3000);

            assertTrue(refreshed);
            assertEquals(1, tracker.getStackCount("node1", currentTime + 3000));

            // Should now expire 4 seconds after refresh, not original activation
            assertTrue(tracker.isEffectActive("node1", currentTime + 6000));
            assertFalse(tracker.isEffectActive("node1", currentTime + 8000));
        }

        @Test
        @DisplayName("STACK should add stacks up to max")
        void stackShouldAddStacks() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.STACK,
                "PHYSICAL_DAMAGE_PERCENT", 5.0
            );
            config.setMaxStacks(3);

            // Activate 4 times
            tracker.activateEffect("node1", config, currentTime);
            tracker.activateEffect("node1", config, currentTime + 100);
            tracker.activateEffect("node1", config, currentTime + 200);
            tracker.activateEffect("node1", config, currentTime + 300);

            // Should be capped at 3 stacks
            assertEquals(3, tracker.getStackCount("node1", currentTime + 300));

            // Modifier value should be multiplied by stacks
            List<StatModifier> modifiers = tracker.getActiveModifiers(currentTime + 300);
            assertEquals(15.0f, modifiers.get(0).getValue(), 0.001f); // 5 * 3 = 15
        }

        @Test
        @DisplayName("NO_REFRESH should block re-activation")
        void noRefreshShouldBlockReactivation() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.NO_REFRESH,
                "ATTACK_SPEED_PERCENT", 50.0
            );

            assertTrue(tracker.activateEffect("node1", config, currentTime));

            // Try to trigger again while active
            assertFalse(tracker.activateEffect("node1", config, currentTime + 1000));

            // Should be able to trigger after expiration
            assertTrue(tracker.activateEffect("node1", config, currentTime + 5000));
        }

        @Test
        @DisplayName("EXTEND_DURATION should add time")
        void extendDurationShouldAddTime() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 2.0, StackingBehavior.EXTEND_DURATION,
                "MOVEMENT_SPEED_PERCENT", 10.0
            );

            tracker.activateEffect("node1", config, currentTime);

            // Trigger again at 1 second (extending by another 2 seconds)
            tracker.activateEffect("node1", config, currentTime + 1000);

            // Original would expire at 2s, now should last until ~4s from original start
            assertTrue(tracker.isEffectActive("node1", currentTime + 3500));
            assertFalse(tracker.isEffectActive("node1", currentTime + 4500));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COOLDOWN TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cooldown")
    class CooldownTests {

        @Test
        @DisplayName("Should block activation during cooldown")
        void shouldBlockDuringCooldown() {
            ConditionalConfig config = createConfigWithCooldown(4.0, 10.0);

            assertTrue(tracker.activateEffect("node1", config, currentTime));

            // Effect expires at 4s, but cooldown until 10s
            // Try to activate at 6s (after expiry but during cooldown)
            assertFalse(tracker.activateEffect("node1", config, currentTime + 6000));

            // Should work after cooldown
            assertTrue(tracker.activateEffect("node1", config, currentTime + 11000));
        }

        @Test
        @DisplayName("Should track cooldown status")
        void shouldTrackCooldownStatus() {
            ConditionalConfig config = createConfigWithCooldown(2.0, 5.0);

            tracker.activateEffect("node1", config, currentTime);

            assertTrue(tracker.isOnCooldown("node1", currentTime + 3000));
            assertFalse(tracker.isOnCooldown("node1", currentTime + 6000));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSUME TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Consume Behavior")
    class ConsumeTests {

        @Test
        @DisplayName("CONSUME_ON_HIT should remove effect when consumed")
        void consumeOnHitShouldRemoveEffect() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_CRIT, 10.0, StackingBehavior.CONSUME_ON_HIT,
                "PHYSICAL_DAMAGE_PERCENT", 50.0
            );

            tracker.activateEffect("node1", config, currentTime);
            assertTrue(tracker.isEffectActive("node1", currentTime));

            List<ConditionalConfig> consumed = tracker.consumeOnHit(currentTime + 1000);

            assertEquals(1, consumed.size());
            assertFalse(tracker.isEffectActive("node1", currentTime + 1000));
        }

        @Test
        @DisplayName("CONSUME_ON_SKILL should remove effect when consumed")
        void consumeOnSkillShouldRemoveEffect() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 10.0, StackingBehavior.CONSUME_ON_SKILL,
                "MANA_COST_REDUCTION", 100.0
            );

            tracker.activateEffect("node1", config, currentTime);

            List<ConditionalConfig> consumed = tracker.consumeOnSkill(currentTime + 1000);

            assertEquals(1, consumed.size());
            assertFalse(tracker.isEffectActive("node1", currentTime + 1000));
        }

        @Test
        @DisplayName("Should only consume matching behavior")
        void shouldOnlyConsumeMatchingBehavior() {
            ConditionalConfig consumeHit = createTimedConfig(
                ConditionalTrigger.ON_CRIT, 10.0, StackingBehavior.CONSUME_ON_HIT,
                "PHYSICAL_DAMAGE_PERCENT", 50.0
            );
            ConditionalConfig refresh = createTimedConfig(
                ConditionalTrigger.ON_KILL, 10.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );

            tracker.activateEffect("consume_node", consumeHit, currentTime);
            tracker.activateEffect("refresh_node", refresh, currentTime);

            List<ConditionalConfig> consumed = tracker.consumeOnHit(currentTime + 1000);

            assertEquals(1, consumed.size());
            assertFalse(tracker.isEffectActive("consume_node", currentTime + 1000));
            assertTrue(tracker.isEffectActive("refresh_node", currentTime + 1000));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("Should remove expired effects")
        void shouldRemoveExpiredEffects() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 2.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );

            tracker.activateEffect("node1", config, currentTime);
            tracker.activateEffect("node2", config, currentTime + 1000);

            // At 3s: node1 expired, node2 still active
            int removed = tracker.cleanup(currentTime + 3000);

            assertEquals(1, removed);
            assertEquals(1, tracker.getActiveEffectCount(currentTime + 3000));
        }

        @Test
        @DisplayName("clearAll should remove everything")
        void clearAllShouldRemoveEverything() {
            ConditionalConfig config = createTimedConfig(
                ConditionalTrigger.ON_KILL, 10.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );

            tracker.activateEffect("node1", config, currentTime);
            tracker.activateEffect("node2", config, currentTime);

            tracker.clearAll();

            assertEquals(0, tracker.getActiveEffectCount(currentTime));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle multiple effects simultaneously")
        void shouldHandleMultipleEffects() {
            ConditionalConfig config1 = createTimedConfig(
                ConditionalTrigger.ON_KILL, 4.0, StackingBehavior.REFRESH,
                "ATTACK_SPEED_PERCENT", 20.0
            );
            ConditionalConfig config2 = createTimedConfig(
                ConditionalTrigger.ON_CRIT, 4.0, StackingBehavior.REFRESH,
                "CRITICAL_MULTIPLIER", 50.0
            );

            tracker.activateEffect("node1", config1, currentTime);
            tracker.activateEffect("node2", config2, currentTime);

            assertEquals(2, tracker.getActiveEffectCount(currentTime));

            List<StatModifier> modifiers = tracker.getActiveModifiers(currentTime);
            assertEquals(2, modifiers.size());
        }

        @Test
        @DisplayName("Should return empty modifiers when no effects active")
        void shouldReturnEmptyWhenNoEffects() {
            List<StatModifier> modifiers = tracker.getActiveModifiers(currentTime);
            assertTrue(modifiers.isEmpty());
        }

        @Test
        @DisplayName("Should handle deactivate for non-existent effect")
        void shouldHandleDeactivateNonExistent() {
            assertFalse(tracker.deactivateEffect("nonexistent"));
        }
    }
}
