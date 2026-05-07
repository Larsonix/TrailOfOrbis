package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Earth-Void Bridge: Soul Garden.
 * +0.5 HP Regen/s (stat modifier). DoTs you inflict heal you for 2% of tick damage.
 */
public class SoulGardenEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float DOT_HEAL_FRACTION = 0.02f;

    public SoulGardenEffect() { super("bridge_earth_void_3"); }

    @Nonnull @Override public String getId() { return "soul_garden"; }

    @Override
    public float onRecovery(@Nonnull CombatEffectContext ctx) {
        if (ctx.rpgDamage() <= 0) return 0f;
        // Only heal from DoT ticks (AttackType.UNKNOWN = DoT recovery context)
        if (ctx.attackType() != io.github.larsonix.trailoforbis.combat.AttackType.UNKNOWN) return 0f;

        float heal = ctx.rpgDamage() * DOT_HEAL_FRACTION;
        if (heal > 0) {
            LOGGER.atFine().log("Soul Garden: DoT heal %.1f (2%% of %.1f)", heal, ctx.rpgDamage());
        }
        return heal;
    }
}
