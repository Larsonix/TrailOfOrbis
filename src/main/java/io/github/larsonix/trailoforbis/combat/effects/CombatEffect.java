package io.github.larsonix.trailoforbis.combat.effects;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * A behavioral combat effect that hooks into the damage pipeline.
 *
 * <p>CombatEffects handle mechanics that go beyond stat modifiers — things like
 * "attacks cost HP", "shock chains to nearby enemies", or "blocking charges
 * your next attacks with life steal." They activate based on stat values that
 * keystones (or skills, gear uniques, etc.) deposit into ComputedStats.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Registered once at server startup via {@link CombatEffectRegistry#register}</li>
 *   <li>Activated per-player when {@link #isActive} returns true (checked on stat recalculation)</li>
 *   <li>Hook methods called during combat for active players only</li>
 *   <li>Deactivated when stat disappears (node deallocated, gear unequipped)</li>
 *   <li>{@link #cleanup} called on disconnect</li>
 * </ol>
 *
 * <p>All hook methods have default no-op implementations. Override only the hooks
 * your effect needs.
 *
 * @see CombatEffectRegistry
 * @see CombatEffectContext
 */
public interface CombatEffect {

    /**
     * Unique identifier for this effect (e.g., "glacial_mastery", "berserkers_rage").
     */
    @Nonnull
    String getId();

    /**
     * Returns true if this effect should be active for the given player.
     *
     * <p>Typically checks a stat value in ComputedStats that the associated keystone deposits.
     * Example: {@code return stats.getFreezeShatterMultiplier() > 0;}
     *
     * <p>Called during stat recalculation (not on every damage event). Result is cached
     * by the registry until the next stat recalc.
     */
    boolean isActive(@Nonnull UUID playerId, @Nonnull ComputedStats stats);

    // ═══════════════════════════════════════════════════════════════════
    // COMBAT HOOKS (all default no-op)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called after the 10-step damage calculator but before post-calculation modifications
     * (parry, shield, shock amp, realm modifiers). Can modify the RPG damage value.
     *
     * @param ctx Combat context with all calculation data
     * @return Modified RPG damage value (return ctx.rpgDamage() for no change)
     */
    default float onPostCalculation(@Nonnull CombatEffectContext ctx) {
        return ctx.rpgDamage();
    }

    /**
     * Called after all post-calculation modifications. This is where burst damage effects
     * (DoT detonation, spell echo) add their bonus damage.
     *
     * @param ctx Combat context with final damage data
     * @return Modified RPG damage value (return ctx.rpgDamage() for no change)
     */
    default float onPostModifications(@Nonnull CombatEffectContext ctx) {
        return ctx.rpgDamage();
    }

    /**
     * Called when the attacker's attack is avoided (dodge, block, parry).
     * Used for on-evade and on-block triggered effects.
     *
     * @param playerId The player this effect is active on
     * @param isAttacker True if the player is the attacker (their attack was avoided),
     *                   false if the player is the defender (they avoided the attack)
     * @param reason The avoidance reason (DODGED, BLOCKED, PARRIED)
     * @param ctx Combat context
     */
    default void onAvoidance(@Nonnull UUID playerId, boolean isAttacker,
                             @Nonnull DamageBreakdown.AvoidanceReason reason,
                             @Nonnull CombatEffectContext ctx) {}

    /**
     * Called when active blocking reduces damage (player holding shield).
     *
     * @param defenderId The blocking player
     * @param blockedDamage Amount of damage that was blocked
     * @param postBlockDamage Damage remaining after block reduction
     * @param ctx Combat context
     */
    default void onBlock(@Nonnull UUID defenderId, float blockedDamage, float postBlockDamage,
                         @Nonnull CombatEffectContext ctx) {}

    /**
     * Called after lethal damage is confirmed — the target is dead.
     *
     * @param attackerId The killer
     * @param targetId The killed entity's UUID (may not be a player)
     * @param overkillDamage Damage dealt beyond what was needed to kill
     * @param ctx Combat context
     */
    default void onKill(@Nonnull UUID attackerId, @Nonnull UUID targetId,
                        float overkillDamage, @Nonnull CombatEffectContext ctx) {}

    /**
     * Called during the recovery phase (leech, thorns). Can add extra recovery.
     *
     * @param ctx Combat context with final damage values
     * @return Additional HP to recover (0 for no extra recovery)
     */
    default float onRecovery(@Nonnull CombatEffectContext ctx) {
        return 0f;
    }

    /**
     * Called when the player's stats are recalculated (allocation, gear change, level up).
     * Used by effects that need to update internal state based on new stat values.
     */
    default void onStatsRecalculated(@Nonnull UUID playerId, @Nonnull ComputedStats newStats) {}

    /**
     * Called when the effect is deactivated for a player (disconnect, stat removed).
     * Clean up any per-player state.
     */
    default void cleanup(@Nonnull UUID playerId) {}
}
