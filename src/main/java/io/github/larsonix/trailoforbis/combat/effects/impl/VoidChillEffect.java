package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Water-Void Bridge: Void Chill.
 * +5% Freeze Chance (stat modifier). Frozen enemies take +8% Void Damage.
 */
public class VoidChillEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float VOID_VS_FROZEN_BONUS = 0.08f;

    private final AilmentTracker ailmentTracker;

    public VoidChillEffect(@Nonnull AilmentTracker ailmentTracker) {
        super("bridge_water_void_3");
        this.ailmentTracker = ailmentTracker;
    }

    @Nonnull @Override public String getId() { return "void_chill"; }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (ctx.defenderUuid() == null) return ctx.rpgDamage();
        if (!ailmentTracker.hasAilment(ctx.defenderUuid(), AilmentType.FREEZE)) return ctx.rpgDamage();

        float bonus = ctx.rpgDamage() * VOID_VS_FROZEN_BONUS;
        LOGGER.atFine().log("Void Chill: +%.1f void damage vs frozen (8%%)", bonus);
        return ctx.rpgDamage() + bonus;
    }
}
