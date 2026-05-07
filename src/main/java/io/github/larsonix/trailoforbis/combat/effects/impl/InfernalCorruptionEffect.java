package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Fire-Void Bridge: Infernal Corruption.
 * Burn DoT ticks restore 3% of tick damage as HP to the attacker.
 */
public class InfernalCorruptionEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float BURN_LEECH_FRACTION = 0.03f;

    public InfernalCorruptionEffect() { super("bridge_fire_void_3"); }

    @Nonnull @Override public String getId() { return "infernal_corruption"; }

    @Override
    public float onRecovery(@Nonnull CombatEffectContext ctx) {
        if (ctx.rpgDamage() <= 0) return 0f;
        // Only heal from DoT ticks (AttackType.UNKNOWN = DoT recovery context)
        if (ctx.attackType() != io.github.larsonix.trailoforbis.combat.AttackType.UNKNOWN) return 0f;

        float heal = ctx.rpgDamage() * BURN_LEECH_FRACTION;
        if (heal > 0) {
            LOGGER.atFine().log("Infernal Corruption: DoT heal %.1f (3%% of %.1f)", heal, ctx.rpgDamage());
        }
        return heal;
    }
}
