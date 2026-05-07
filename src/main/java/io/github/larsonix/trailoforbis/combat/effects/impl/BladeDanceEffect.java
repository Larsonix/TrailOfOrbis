package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Striker KS1: Blade Dance — Evade-to-Crit Conversion.
 *
 * <p>When you evade an attack, your next hit within 2s is a guaranteed Critical Strike
 * with +25% bonus Crit Multiplier. The charge is consumed on the next hit.
 */
public class BladeDanceEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Duration in ms after evade during which the guaranteed crit is available. */
    private static final long CHARGE_DURATION_MS = 2000L;
    /** Bonus crit multiplier applied to the guaranteed crit. */
    private static final float BONUS_CRIT_MULT = 25.0f;

    /** Per-player charge state: timestamp when the evade-crit charge was gained. */
    private final ConcurrentHashMap<UUID, Long> chargeTimestamp = new ConcurrentHashMap<>();

    public BladeDanceEffect() {
        super("striker_keystone_1");
    }

    @Nonnull
    @Override
    public String getId() {
        return "blade_dance";
    }

    @Override
    public void onAvoidance(@Nonnull UUID playerId, boolean isAttacker,
                            @Nonnull DamageBreakdown.AvoidanceReason reason,
                            @Nonnull CombatEffectContext ctx) {
        // Only activate on the DEFENDER evading (not attacker's attack being evaded)
        if (isAttacker) return;
        if (reason != DamageBreakdown.AvoidanceReason.DODGED) return;

        chargeTimestamp.put(playerId, System.currentTimeMillis());
        LOGGER.atFine().log("Blade Dance: %s evaded — guaranteed crit charged (2s window)", playerId);
    }

    @Override
    public float onPostCalculation(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerUuid() == null) return ctx.rpgDamage();

        Long chargeTime = chargeTimestamp.get(ctx.attackerUuid());
        if (chargeTime == null) return ctx.rpgDamage();

        long elapsed = System.currentTimeMillis() - chargeTime;
        if (elapsed > CHARGE_DURATION_MS) {
            chargeTimestamp.remove(ctx.attackerUuid());
            return ctx.rpgDamage();
        }

        // Consume the charge
        chargeTimestamp.remove(ctx.attackerUuid());

        // If the hit wasn't already a crit, make it one with bonus multiplier
        // If it WAS a crit, add the bonus multiplier on top
        float baseCritMult = ctx.hasAttackerStats() ? ctx.attackerStats().getCriticalMultiplier() : 150f;
        float enhancedMult = (baseCritMult + BONUS_CRIT_MULT) / 100f;

        // Apply guaranteed crit: multiply damage by crit multiplier (or bonus if already crit)
        float newDamage;
        if (ctx.wasCrit()) {
            // Already crit — add the bonus multiplier portion (+25% of base damage)
            newDamage = ctx.rpgDamage() * (1.0f + BONUS_CRIT_MULT / 100f);
        } else {
            // Not a crit — make it one (apply full crit multiplier + bonus)
            newDamage = ctx.rpgDamage() * enhancedMult;
        }

        LOGGER.atFine().log("Blade Dance: guaranteed crit consumed — damage %.1f → %.1f (mult %.0f%%+%.0f%%)",
            ctx.rpgDamage(), newDamage, baseCritMult, BONUS_CRIT_MULT);

        return newDamage;
    }

    @Override
    public void cleanup(@Nonnull UUID playerId) {
        chargeTimestamp.remove(playerId);
    }
}
