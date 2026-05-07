package io.github.larsonix.trailoforbis.combat.effects;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry and dispatcher for {@link CombatEffect} instances.
 *
 * <p>Effects are registered once at server startup. The registry maintains a per-player
 * cache of active effects (determined by {@link CombatEffect#isActive}), refreshed
 * whenever stats are recalculated.
 *
 * <p>During combat, RPGDamageSystem calls the runner methods (e.g., {@link #runPostCalculation})
 * which dispatch to all active effects for the relevant player.
 *
 * <p>Thread safety:
 * <ul>
 *   <li>{@code effects} list: CopyOnWriteArrayList (registered once, read many)</li>
 *   <li>{@code activeEffects} cache: ConcurrentHashMap (updated on stat recalc, read on damage)</li>
 *   <li>Per-effect state: each CombatEffect manages its own thread safety</li>
 * </ul>
 *
 * @see CombatEffect
 * @see CombatEffectContext
 */
public class CombatEffectRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** All registered effects. Populated once at startup, never modified after. */
    private final List<CombatEffect> effects = new CopyOnWriteArrayList<>();

    /** Per-player cache of currently active effects. Rebuilt on stat recalculation. */
    private final ConcurrentHashMap<UUID, List<CombatEffect>> activeEffects = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers a combat effect. Called once during server startup.
     */
    public void register(@Nonnull CombatEffect effect) {
        effects.add(effect);
        LOGGER.atInfo().log("Registered combat effect: %s", effect.getId());
    }

    /**
     * Returns the number of registered effects.
     */
    public int getRegisteredCount() {
        return effects.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTIVE EFFECT MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Refreshes the active effects cache for a player based on their current stats.
     *
     * <p>Called by AttributeManager after every stat recalculation. Determines which
     * effects are active by calling {@link CombatEffect#isActive} on each registered effect.
     *
     * <p>Also notifies newly activated effects via {@link CombatEffect#onStatsRecalculated}
     * and cleans up newly deactivated effects via {@link CombatEffect#cleanup}.
     */
    public void refreshActiveEffects(@Nonnull UUID playerId, @Nonnull ComputedStats stats) {
        List<CombatEffect> previousActive = activeEffects.getOrDefault(playerId, List.of());
        Set<String> previousIds = new HashSet<>();
        for (CombatEffect e : previousActive) {
            previousIds.add(e.getId());
        }

        List<CombatEffect> newActive = new ArrayList<>();
        for (CombatEffect effect : effects) {
            try {
                if (effect.isActive(playerId, stats)) {
                    newActive.add(effect);

                    // Notify all active effects of stat change
                    effect.onStatsRecalculated(playerId, stats);

                    // Log newly activated effects
                    if (!previousIds.contains(effect.getId())) {
                        LOGGER.atFine().log("Combat effect activated for %s: %s", playerId, effect.getId());
                    }
                } else if (previousIds.contains(effect.getId())) {
                    // Effect was active but is no longer — cleanup
                    effect.cleanup(playerId);
                    LOGGER.atFine().log("Combat effect deactivated for %s: %s", playerId, effect.getId());
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error checking isActive for effect %s on player %s",
                    effect.getId(), playerId);
            }
        }

        if (newActive.isEmpty()) {
            activeEffects.remove(playerId);
        } else {
            activeEffects.put(playerId, List.copyOf(newActive));
        }
    }

    /**
     * Gets the currently active effects for a player.
     *
     * @return Immutable list of active effects (empty if none)
     */
    @Nonnull
    public List<CombatEffect> getActiveEffects(@Nonnull UUID playerId) {
        return activeEffects.getOrDefault(playerId, List.of());
    }

    /**
     * Cleans up all state for a disconnecting player.
     */
    public void cleanup(@Nonnull UUID playerId) {
        List<CombatEffect> active = activeEffects.remove(playerId);
        if (active != null) {
            for (CombatEffect effect : active) {
                try {
                    effect.cleanup(playerId);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error cleaning up effect %s for player %s",
                        effect.getId(), playerId);
                }
            }
        }
    }

    /**
     * Shuts down the registry, cleaning up all players.
     */
    public void shutdown() {
        for (UUID playerId : activeEffects.keySet()) {
            cleanup(playerId);
        }
        activeEffects.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMBAT PIPELINE RUNNERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Runs onPostCalculation for all active attacker effects.
     *
     * @return Modified RPG damage value
     */
    public float runPostCalculation(@Nullable UUID attackerUuid, @Nonnull CombatEffectContext ctx) {
        if (attackerUuid == null) return ctx.rpgDamage();
        float damage = ctx.rpgDamage();
        for (CombatEffect effect : getActiveEffects(attackerUuid)) {
            try {
                float newDamage = effect.onPostCalculation(ctx.withDamage(damage));
                if (newDamage != damage) {
                    LOGGER.atFine().log("Effect %s modified post-calc damage: %.1f -> %.1f",
                        effect.getId(), damage, newDamage);
                    damage = newDamage;
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in onPostCalculation for effect %s", effect.getId());
            }
        }
        return damage;
    }

    /**
     * Runs onPostModifications for all active attacker effects.
     *
     * @return Modified RPG damage value
     */
    public float runPostModifications(@Nullable UUID attackerUuid, @Nonnull CombatEffectContext ctx) {
        if (attackerUuid == null) return ctx.rpgDamage();
        float damage = ctx.rpgDamage();
        for (CombatEffect effect : getActiveEffects(attackerUuid)) {
            try {
                float newDamage = effect.onPostModifications(ctx.withDamage(damage));
                if (newDamage != damage) {
                    LOGGER.atFine().log("Effect %s modified post-mods damage: %.1f -> %.1f",
                        effect.getId(), damage, newDamage);
                    damage = newDamage;
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in onPostModifications for effect %s", effect.getId());
            }
        }
        return damage;
    }

    /**
     * Runs onAvoidance for all active effects on the relevant player.
     */
    public void runOnAvoidance(@Nullable UUID attackerUuid, @Nonnull UUID defenderUuid,
                               @Nonnull DamageBreakdown.AvoidanceReason reason,
                               @Nonnull CombatEffectContext ctx) {
        // Notify defender's effects (they dodged/blocked)
        for (CombatEffect effect : getActiveEffects(defenderUuid)) {
            try {
                effect.onAvoidance(defenderUuid, false, reason, ctx);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in onAvoidance (defender) for effect %s", effect.getId());
            }
        }
        // Notify attacker's effects (their attack was avoided)
        if (attackerUuid != null) {
            for (CombatEffect effect : getActiveEffects(attackerUuid)) {
                try {
                    effect.onAvoidance(attackerUuid, true, reason, ctx);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in onAvoidance (attacker) for effect %s", effect.getId());
                }
            }
        }
    }

    /**
     * Runs onBlock for all active defender effects.
     */
    public void runOnBlock(@Nonnull UUID defenderId, float blockedDamage, float postBlockDamage,
                           @Nonnull CombatEffectContext ctx) {
        for (CombatEffect effect : getActiveEffects(defenderId)) {
            try {
                effect.onBlock(defenderId, blockedDamage, postBlockDamage, ctx);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in onBlock for effect %s", effect.getId());
            }
        }
    }

    /**
     * Runs onKill for all active attacker effects.
     */
    public void runOnKill(@Nonnull UUID attackerId, @Nonnull UUID targetId,
                          float overkillDamage, @Nonnull CombatEffectContext ctx) {
        for (CombatEffect effect : getActiveEffects(attackerId)) {
            try {
                effect.onKill(attackerId, targetId, overkillDamage, ctx);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in onKill for effect %s", effect.getId());
            }
        }
    }

    /**
     * Runs onRecovery for all active attacker effects.
     *
     * @return Total additional HP recovery from all effects
     */
    public float runOnRecovery(@Nullable UUID attackerUuid, @Nonnull CombatEffectContext ctx) {
        if (attackerUuid == null) return 0f;
        float totalRecovery = 0f;
        for (CombatEffect effect : getActiveEffects(attackerUuid)) {
            try {
                float extra = effect.onRecovery(ctx);
                totalRecovery += extra;
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in onRecovery for effect %s", effect.getId());
            }
        }
        return totalRecovery;
    }
}
