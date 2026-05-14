package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Earth↔Wind Bridge Payoff: Life Stream — Evading restores 2% Max HP.
 * The +8 Evasion bonus is handled by the node's regular stat modifiers.
 */
public class LifeStreamEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float EVADE_HEAL_FRACTION = 0.02f;

    public LifeStreamEffect() {
        super("bridge_earth_wind_3");
    }

    @Nonnull
    @Override
    public String getId() {
        return "life_stream";
    }

    @Override
    public void onAvoidance(@Nonnull UUID playerId, boolean isAttacker,
                            @Nonnull DamageBreakdown.AvoidanceReason reason,
                            @Nonnull CombatEffectContext ctx) {
        if (isAttacker) return; // Only defender
        if (reason != DamageBreakdown.AvoidanceReason.DODGED) return;

        if (ctx.defenderRef() == null || !ctx.defenderRef().isValid()) return;

        try {
            var statMap = ctx.store().getComponent(ctx.defenderRef(),
                com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
            if (statMap != null) {
                int hpIdx = com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth();
                var hpStat = statMap.get(hpIdx);
                if (hpStat != null) {
                    float heal = hpStat.getMax() * EVADE_HEAL_FRACTION;
                    // Apply Health Recovery multiplier
                    if (ctx.defenderStats() != null) {
                        float recoveryPct = ctx.defenderStats().getHealthRecoveryPercent();
                        if (recoveryPct != 0) {
                            heal *= (1.0f + recoveryPct / 100.0f);
                        }
                    }
                    float newHp = Math.min(hpStat.get() + heal, hpStat.getMax());
                    statMap.setStatValue(
                        com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.Predictable.SELF,
                        hpIdx, newHp);
                    LOGGER.atFine().log("Life Stream: evade heal %.1f (2%% of %.0f max HP)", heal, hpStat.getMax());
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Life Stream: could not apply evade heal — %s", e.getMessage());
        }
    }
}
