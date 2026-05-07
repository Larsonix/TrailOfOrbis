package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Water-Lightning Bridge: Storm Shatter.
 * Spell crits against Frozen targets deal +20% bonus Lightning Damage.
 */
public class StormShatterEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float LIGHTNING_VS_FROZEN_BONUS = 0.20f;
    private final AilmentTracker ailmentTracker;

    public StormShatterEffect(@Nonnull AilmentTracker ailmentTracker) {
        super("bridge_lightning_water_3");
        this.ailmentTracker = ailmentTracker;
    }

    @Nonnull @Override public String getId() { return "storm_shatter"; }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (ctx.defenderUuid() == null || !ctx.wasCrit()) return ctx.rpgDamage();
        if (ctx.attackType() != AttackType.SPELL) return ctx.rpgDamage();
        if (!ailmentTracker.hasAilment(ctx.defenderUuid(), AilmentType.FREEZE)) return ctx.rpgDamage();

        float bonus = ctx.rpgDamage() * LIGHTNING_VS_FROZEN_BONUS;
        LOGGER.atFine().log("Storm Shatter: spell crit vs frozen +%.1f lightning (20%%)", bonus);
        return ctx.rpgDamage() + bonus;
    }
}
