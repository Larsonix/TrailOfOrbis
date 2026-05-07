package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Fire-Wind Bridge: Boiling Currents.
 * Charged projectile attacks deal +15% bonus Fire Damage.
 *
 * <p>Since we can't distinguish "charged" from "normal" projectile attacks in the current
 * pipeline, this applies to ALL projectile attacks as a slightly lower bonus.
 * TODO: Detect charged attacks via DamageSequence metadata when available.
 */
public class BoilingCurrentsEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float PROJECTILE_FIRE_BONUS = 0.15f;

    public BoilingCurrentsEffect() {
        super("bridge_fire_wind_3");
    }

    @Nonnull @Override public String getId() { return "boiling_currents"; }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackType() != AttackType.PROJECTILE) return ctx.rpgDamage();

        float bonus = ctx.rpgDamage() * PROJECTILE_FIRE_BONUS;
        LOGGER.atFine().log("Boiling Currents: projectile +%.1f fire damage (15%%)", bonus);
        return ctx.rpgDamage() + bonus;
    }
}
