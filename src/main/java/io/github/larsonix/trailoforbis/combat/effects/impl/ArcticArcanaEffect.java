package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Water-Wind Bridge: Arctic Arcana.
 * Projectile spells gain +10% Spell Damage and +5% Freeze Chance.
 *
 * <p>The freeze chance bonus is handled by the node's stat modifiers.
 * This effect handles the +10% spell damage for projectile-type spells.
 */
public class ArcticArcanaEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float PROJECTILE_SPELL_BONUS = 0.10f;

    public ArcticArcanaEffect() {
        super("bridge_water_wind_3");
    }

    @Nonnull @Override public String getId() { return "arctic_arcana"; }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        // Only applies to spell attacks that are also projectile-based
        if (ctx.attackType() != AttackType.SPELL) return ctx.rpgDamage();

        float bonus = ctx.rpgDamage() * PROJECTILE_SPELL_BONUS;
        LOGGER.atFine().log("Arctic Arcana: projectile spell +%.1f damage (10%%)", bonus);
        return ctx.rpgDamage() + bonus;
    }
}
