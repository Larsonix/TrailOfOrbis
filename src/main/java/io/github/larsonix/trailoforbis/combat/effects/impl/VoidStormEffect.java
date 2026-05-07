package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Lightning-Void Bridge: Void Storm.
 * Shock ticks deal +5% bonus Void Damage. +5% Status Duration (stat modifier).
 */
public class VoidStormEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float SHOCK_VOID_BONUS = 0.05f;

    private final AilmentTracker ailmentTracker;

    public VoidStormEffect(@Nonnull AilmentTracker ailmentTracker) {
        super("bridge_lightning_void_3");
        this.ailmentTracker = ailmentTracker;
    }

    @Nonnull @Override public String getId() { return "void_storm"; }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (ctx.defenderUuid() == null) return ctx.rpgDamage();
        // Bonus damage vs shocked targets (applies to all damage, not just DoT ticks)
        if (!ailmentTracker.hasAilment(ctx.defenderUuid(), AilmentType.SHOCK)) return ctx.rpgDamage();

        float bonus = ctx.rpgDamage() * SHOCK_VOID_BONUS;
        LOGGER.atFine().log("Void Storm: +%.1f void damage vs shocked (5%%)", bonus);
        return ctx.rpgDamage() + bonus;
    }
}
