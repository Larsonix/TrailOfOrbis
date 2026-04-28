package io.github.larsonix.trailoforbis.skilltree.conditional;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active conditional effects for a single player.
 *
 * <p>Conditional effects are temporary stat bonuses that activate when certain
 * conditions are met (on-kill, on-crit, low life, etc.). This class manages:
 * <ul>
 *   <li>Active effect tracking with duration timers</li>
 *   <li>Stack management (refresh, stack, consume)</li>
 *   <li>Cooldown enforcement</li>
 *   <li>Aggregating active bonuses for stat calculation</li>
 * </ul>
 *
 * <p>Thread-safe for concurrent access from combat events.
 *
 * @see ConditionalConfig
 * @see ConditionalTrigger
 * @see StackingBehavior
 */
public class ConditionalEffectTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final UUID playerId;

    /**
     * Active effects keyed by node ID.
     * Each node can have at most one active effect.
     */
    private final Map<String, ActiveEffect> activeEffects = new ConcurrentHashMap<>();

    /**
     * Cooldowns keyed by node ID.
     * Tracks when each effect can be triggered again.
     */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * Creates a new tracker for the given player.
     *
     * @param playerId The player's UUID
     */
    public ConditionalEffectTracker(@Nonnull UUID playerId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFECT ACTIVATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Attempts to activate a conditional effect.
     *
     * @param nodeId     The skill node ID
     * @param config     The conditional configuration
     * @param currentTime Current time in milliseconds
     * @return true if the effect was activated (or refreshed/stacked)
     */
    public boolean activateEffect(@Nonnull String nodeId, @Nonnull ConditionalConfig config, long currentTime) {
        // Check cooldown
        if (isOnCooldown(nodeId, currentTime)) {
            LOGGER.atFine().log("Effect %s on cooldown for player %s", nodeId, playerId);
            return false;
        }

        ActiveEffect existing = activeEffects.get(nodeId);

        if (existing != null && existing.isActive(currentTime)) {
            // Effect already active - apply stacking behavior
            return handleStackingBehavior(nodeId, existing, config, currentTime);
        }

        // Create new effect
        ActiveEffect newEffect = createEffect(nodeId, config, currentTime);
        activeEffects.put(nodeId, newEffect);

        // Set cooldown if configured
        if (config.hasCooldown()) {
            long cooldownEnd = currentTime + (long) (config.getCooldown() * 1000);
            cooldowns.put(nodeId, cooldownEnd);
        }

        LOGGER.atFine().log("Activated effect %s for player %s (stacks: %d)",
            nodeId, playerId, newEffect.stacks);

        return true;
    }

    /**
     * Handles stacking behavior when effect is already active.
     */
    private boolean handleStackingBehavior(
        @Nonnull String nodeId,
        @Nonnull ActiveEffect existing,
        @Nonnull ConditionalConfig config,
        long currentTime
    ) {
        return switch (config.getStacking()) {
            case REFRESH -> {
                // Refresh duration to full
                existing.expirationTime = currentTime + (long) (config.getDuration() * 1000);
                LOGGER.atFine().log("Refreshed effect %s for player %s", nodeId, playerId);
                yield true;
            }
            case STACK -> {
                // Add a stack (up to max)
                if (existing.stacks < config.getMaxStacks()) {
                    existing.stacks++;
                    existing.expirationTime = currentTime + (long) (config.getDuration() * 1000);
                    LOGGER.atFine().log("Stacked effect %s for player %s (now %d stacks)",
                        nodeId, playerId, existing.stacks);
                    yield true;
                } else {
                    // At max stacks, just refresh duration
                    existing.expirationTime = currentTime + (long) (config.getDuration() * 1000);
                    yield true;
                }
            }
            case NO_REFRESH -> {
                // Cannot trigger again while active
                LOGGER.atFine().log("Effect %s blocked (no refresh) for player %s", nodeId, playerId);
                yield false;
            }
            case EXTEND_DURATION -> {
                // Add time instead of refreshing
                existing.expirationTime += (long) (config.getDuration() * 1000);
                LOGGER.atFine().log("Extended effect %s for player %s", nodeId, playerId);
                yield true;
            }
            case CONSUME_ON_HIT, CONSUME_ON_SKILL -> {
                // These are consumed behaviors, not triggered - just refresh
                existing.expirationTime = currentTime + (long) (config.getDuration() * 1000);
                yield true;
            }
        };
    }

    /**
     * Creates a new active effect from config.
     */
    @Nonnull
    private ActiveEffect createEffect(@Nonnull String nodeId, @Nonnull ConditionalConfig config, long currentTime) {
        ActiveEffect effect = new ActiveEffect();
        effect.nodeId = nodeId;
        effect.config = config;
        effect.startTime = currentTime;
        effect.stacks = 1;

        if (config.isTimedEffect()) {
            effect.expirationTime = currentTime + (long) (config.getDuration() * 1000);
        } else {
            // Persistent effect (threshold-based)
            effect.expirationTime = Long.MAX_VALUE;
        }

        return effect;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFECT DEACTIVATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Deactivates an effect immediately (e.g., for CONSUME behaviors).
     *
     * @param nodeId The node ID
     * @return true if the effect was active and deactivated
     */
    public boolean deactivateEffect(@Nonnull String nodeId) {
        ActiveEffect removed = activeEffects.remove(nodeId);
        if (removed != null) {
            LOGGER.atFine().log("Deactivated effect %s for player %s", nodeId, playerId);
            return true;
        }
        return false;
    }

    /**
     * Deactivates a persistent effect when threshold condition is no longer met.
     *
     * @param nodeId The node ID
     */
    public void deactivatePersistentEffect(@Nonnull String nodeId) {
        ActiveEffect effect = activeEffects.get(nodeId);
        if (effect != null && !effect.config.isTimedEffect()) {
            activeEffects.remove(nodeId);
            LOGGER.atFine().log("Deactivated persistent effect %s for player %s", nodeId, playerId);
        }
    }

    /**
     * Consumes an active effect with CONSUME_ON_HIT behavior.
     *
     * @param currentTime Current time in milliseconds
     * @return List of consumed effect configs
     */
    @Nonnull
    public List<ConditionalConfig> consumeOnHit(long currentTime) {
        return consumeEffectsWithBehavior(StackingBehavior.CONSUME_ON_HIT, currentTime);
    }

    /**
     * Consumes an active effect with CONSUME_ON_SKILL behavior.
     *
     * @param currentTime Current time in milliseconds
     * @return List of consumed effect configs
     */
    @Nonnull
    public List<ConditionalConfig> consumeOnSkill(long currentTime) {
        return consumeEffectsWithBehavior(StackingBehavior.CONSUME_ON_SKILL, currentTime);
    }

    /**
     * Consumes effects with a specific stacking behavior.
     */
    @Nonnull
    private List<ConditionalConfig> consumeEffectsWithBehavior(StackingBehavior behavior, long currentTime) {
        List<ConditionalConfig> consumed = new ArrayList<>();

        Iterator<Map.Entry<String, ActiveEffect>> iter = activeEffects.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, ActiveEffect> entry = iter.next();
            ActiveEffect effect = entry.getValue();

            if (effect.isActive(currentTime) && effect.config.getStacking() == behavior) {
                consumed.add(effect.config);
                iter.remove();
                LOGGER.atFine().log("Consumed effect %s for player %s", entry.getKey(), playerId);
            }
        }

        return consumed;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Removes expired effects.
     *
     * @param currentTime Current time in milliseconds
     * @return Number of effects removed
     */
    public int cleanup(long currentTime) {
        int removed = 0;

        Iterator<Map.Entry<String, ActiveEffect>> iter = activeEffects.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, ActiveEffect> entry = iter.next();
            if (!entry.getValue().isActive(currentTime)) {
                iter.remove();
                removed++;
                LOGGER.atFine().log("Expired effect %s for player %s", entry.getKey(), playerId);
            }
        }

        // Clean up old cooldowns
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);

        return removed;
    }

    /**
     * Clears all active effects (e.g., on player death or logout).
     */
    public void clearAll() {
        activeEffects.clear();
        cooldowns.clear();
        LOGGER.atFine().log("Cleared all effects for player %s", playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERYING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if an effect is on cooldown.
     */
    public boolean isOnCooldown(@Nonnull String nodeId, long currentTime) {
        Long cooldownEnd = cooldowns.get(nodeId);
        return cooldownEnd != null && currentTime < cooldownEnd;
    }

    /**
     * Checks if an effect is currently active.
     */
    public boolean isEffectActive(@Nonnull String nodeId, long currentTime) {
        ActiveEffect effect = activeEffects.get(nodeId);
        return effect != null && effect.isActive(currentTime);
    }

    /**
     * Gets the current stack count for an effect.
     *
     * @return Stack count, or 0 if not active
     */
    public int getStackCount(@Nonnull String nodeId, long currentTime) {
        ActiveEffect effect = activeEffects.get(nodeId);
        if (effect != null && effect.isActive(currentTime)) {
            return effect.stacks;
        }
        return 0;
    }

    /**
     * Gets the remaining duration for an effect.
     *
     * @return Remaining duration in seconds, or 0 if not active or persistent
     */
    public double getRemainingDuration(@Nonnull String nodeId, long currentTime) {
        ActiveEffect effect = activeEffects.get(nodeId);
        if (effect != null && effect.isActive(currentTime)) {
            if (effect.expirationTime == Long.MAX_VALUE) {
                return Double.MAX_VALUE; // Persistent
            }
            return (effect.expirationTime - currentTime) / 1000.0;
        }
        return 0;
    }

    /**
     * Gets all active effects as stat modifiers.
     *
     * @param currentTime Current time in milliseconds
     * @return List of active stat modifiers
     */
    @Nonnull
    public List<StatModifier> getActiveModifiers(long currentTime) {
        List<StatModifier> modifiers = new ArrayList<>();

        for (ActiveEffect effect : activeEffects.values()) {
            if (effect.isActive(currentTime)) {
                modifiers.addAll(effect.getModifiers());
            }
        }

        return modifiers;
    }

    /**
     * Gets the number of currently active effects.
     */
    public int getActiveEffectCount(long currentTime) {
        int count = 0;
        for (ActiveEffect effect : activeEffects.values()) {
            if (effect.isActive(currentTime)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the player ID.
     */
    @Nonnull
    public UUID getPlayerId() {
        return playerId;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents an active conditional effect.
     */
    private static class ActiveEffect {
        String nodeId;
        ConditionalConfig config;
        long startTime;
        long expirationTime;
        int stacks = 1;

        /**
         * Checks if this effect is still active.
         * Effect is active up to and including its expiration time.
         */
        boolean isActive(long currentTime) {
            return currentTime <= expirationTime;
        }

        /**
         * Gets the stat modifiers for this effect, accounting for stacks.
         */
        @Nonnull
        List<StatModifier> getModifiers() {
            List<StatModifier> modifiers = new ArrayList<>();

            for (ConditionalConfig.ConditionalEffect effect : config.getEffects()) {
                StatType statType = parseStatType(effect.getStat());
                if (statType == null) continue;

                ModifierType modifierType = parseModifierType(effect.getModifierType());
                float value = (float) (effect.getValue() * stacks);

                modifiers.add(new StatModifier(statType, value, modifierType));
            }

            return modifiers;
        }

        @Nullable
        private StatType parseStatType(@Nonnull String statName) {
            if (statName.isBlank()) return null;
            try {
                return StatType.valueOf(statName.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Nonnull
        private ModifierType parseModifierType(@Nonnull String modifierTypeName) {
            if (modifierTypeName.isBlank()) return ModifierType.PERCENT;
            try {
                return ModifierType.valueOf(modifierTypeName.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return ModifierType.PERCENT;
            }
        }
    }
}
