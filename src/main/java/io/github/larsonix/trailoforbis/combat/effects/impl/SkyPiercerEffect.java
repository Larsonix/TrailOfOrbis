package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wind KS2: Sky Piercer — Marksman's Focus.
 *
 * <p>After not attacking for 1.5s, your next projectile attack becomes a Focused Shot:
 * deals 80% bonus damage and ignores 20% of enemy defenses (modeled as bonus damage
 * proportional to what defenses reduced, since the calculator already ran).
 * Only one Focused Shot per charge cycle.
 */
public class SkyPiercerEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Time in ms without attacking required to charge a Focused Shot. */
    private static final long CHARGE_TIME_MS = 1500L;
    /** Bonus damage multiplier for Focused Shot. */
    private static final float FOCUSED_DAMAGE_BONUS = 0.80f;
    /**
     * Defense bypass fraction. Since resistance already reduced the damage in the calculator,
     * we model this as: bonus = rpgDamage * (bypass / (1 - avgDefenseReduction)).
     * Simplified: we use the ratio of base damage to post-defense damage to estimate
     * what 20% penetration would recover. Approximated as 20% of damage.
     */
    private static final float DEFENSE_BYPASS_BONUS = 0.20f;

    /** Tracks last attack time per player. */
    private final ConcurrentHashMap<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();

    public SkyPiercerEffect() {
        super("wind_keystone_2");
    }

    @Nonnull
    @Override
    public String getId() {
        return "sky_piercer";
    }

    @Override
    public float onPostCalculation(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerUuid() == null) return ctx.rpgDamage();

        long now = System.currentTimeMillis();
        Long lastAttack = lastAttackTime.get(ctx.attackerUuid());

        // Record this attack
        lastAttackTime.put(ctx.attackerUuid(), now);

        // Check if Focused Shot is charged
        if (lastAttack != null && (now - lastAttack) < CHARGE_TIME_MS) {
            return ctx.rpgDamage(); // Not charged
        }

        // Only applies to projectile attacks
        if (ctx.attackType() != AttackType.PROJECTILE) {
            return ctx.rpgDamage();
        }

        // Apply Focused Shot: +80% damage + 20% defense bypass
        // Defense bypass is modeled as additional damage proportional to what was lost to defenses.
        // rpgBaseDamage = pre-defense damage, rpgDamage = post-defense damage.
        // Defense reduction ratio = 1 - (rpgDamage / rpgBaseDamage)
        float bonusDamage = ctx.rpgDamage() * FOCUSED_DAMAGE_BONUS;
        float defenseBypassDamage = 0f;
        if (ctx.rpgBaseDamage() > 0 && ctx.rpgDamage() < ctx.rpgBaseDamage()) {
            float defenseLost = ctx.rpgBaseDamage() - ctx.rpgDamage();
            defenseBypassDamage = defenseLost * DEFENSE_BYPASS_BONUS;
        }

        float totalBonus = bonusDamage + defenseBypassDamage;
        LOGGER.atFine().log("Sky Piercer: Focused Shot! +%.0f%% damage (%.1f) + %.0f%% defense bypass (%.1f) = +%.1f total",
            FOCUSED_DAMAGE_BONUS * 100, bonusDamage, DEFENSE_BYPASS_BONUS * 100, defenseBypassDamage, totalBonus);

        return ctx.rpgDamage() + totalBonus;
    }

    @Override
    public void cleanup(@Nonnull UUID playerId) {
        lastAttackTime.remove(playerId);
    }
}
