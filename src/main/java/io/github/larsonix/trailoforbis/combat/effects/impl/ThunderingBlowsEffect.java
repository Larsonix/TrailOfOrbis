package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Fire-Lightning Bridge: Thundering Blows.
 * Crits have +10% chance to apply Shock. Shocked targets take +8% Fire Damage.
 *
 * <p>The shock application on crit is handled by the ailment system via the CRIT_SHOCK_CHANCE stat.
 * This effect handles the +8% Fire Damage vs Shocked conditional.
 */
public class ThunderingBlowsEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float FIRE_VS_SHOCKED_BONUS = 0.08f;

    private final AilmentTracker ailmentTracker;

    public ThunderingBlowsEffect(@Nonnull AilmentTracker ailmentTracker) {
        super("bridge_fire_lightning_3");
        this.ailmentTracker = ailmentTracker;
    }

    @Nonnull @Override public String getId() { return "thundering_blows"; }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (ctx.defenderUuid() == null) return ctx.rpgDamage();
        if (!ailmentTracker.hasAilment(ctx.defenderUuid(), AilmentType.SHOCK)) return ctx.rpgDamage();

        float bonus = ctx.rpgDamage() * FIRE_VS_SHOCKED_BONUS;
        LOGGER.atFine().log("Thundering Blows: +%.1f fire damage vs shocked (8%%)", bonus);
        return ctx.rpgDamage() + bonus;
    }
}
