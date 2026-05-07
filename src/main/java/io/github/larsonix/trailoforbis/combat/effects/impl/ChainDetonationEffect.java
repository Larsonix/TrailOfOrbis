package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.component.RpgBurnComponent;
import io.github.larsonix.trailoforbis.ailments.component.RpgPoisonComponent;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffect;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Havoc KS2: Chain Detonation — Critical hits against DoT-affected targets
 * instantly deal remaining DoT damage as burst Void damage, then clear all DoTs.
 *
 * <p>Activated when {@code detonateDotOnCrit > 0} in player stats.
 *
 * <p>Migrated from hardcoded RPGDamageSystem lines 981-1009.
 */
public class ChainDetonationEffect implements CombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AilmentTracker ailmentTracker;

    public ChainDetonationEffect(@Nonnull AilmentTracker ailmentTracker) {
        this.ailmentTracker = ailmentTracker;
    }

    @Nonnull
    @Override
    public String getId() {
        return "chain_detonation";
    }

    @Override
    public boolean isActive(@Nonnull UUID playerId, @Nonnull ComputedStats stats) {
        return stats.getDetonateDotOnCrit() > 0;
    }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (!ctx.wasCrit() || !ctx.hasAttackerStats() || ctx.defenderUuid() == null) {
            return ctx.rpgDamage();
        }

        float burstPct = ctx.attackerStats().getDetonateDotOnCrit();
        float remainingDot = ailmentTracker.getRemainingDotDamage(ctx.defenderUuid());
        if (remainingDot <= 0) {
            return ctx.rpgDamage();
        }

        float burstDamage = remainingDot * (burstPct / 100f);
        ailmentTracker.detonateAllDots(ctx.defenderUuid());

        // Sync ECS components — remove burn/poison since DoTs were detonated
        if (ctx.defenderRef() != null && ctx.defenderRef().isValid() && ctx.commandBuffer() != null) {
            if (RpgBurnComponent.TYPE != null) {
                ctx.commandBuffer().removeComponent(ctx.defenderRef(), RpgBurnComponent.TYPE);
            }
            if (RpgPoisonComponent.TYPE != null) {
                ctx.commandBuffer().removeComponent(ctx.defenderRef(), RpgPoisonComponent.TYPE);
            }
        }

        LOGGER.atFine().log("DoT detonation: %.1f remaining × %.0f%% = %.1f burst",
            remainingDot, burstPct, burstDamage);

        return ctx.rpgDamage() + burstDamage;
    }
}
