package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Void KS2: Feast or Famine — Kill Momentum Sustain.
 *
 * <p>On Kill: heal 8% of attacker's Max HP. The damage buff (+10% All Damage for 5s)
 * is handled by the ON_KILL ConditionalConfig on the keystone node in skill-tree.yml.
 *
 * <p>This CombatEffect only handles the HP restoration, which ConditionalConfig cannot do.
 */
public class FeastOrFamineEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float HEAL_FRACTION = 0.08f;

    public FeastOrFamineEffect() {
        super("void_keystone_2");
    }

    @Nonnull
    @Override
    public String getId() {
        return "feast_or_famine";
    }

    @Override
    public void onKill(@Nonnull UUID attackerId, @Nonnull UUID targetId,
                       float overkillDamage, @Nonnull CombatEffectContext ctx) {
        if (ctx.attackerRef() == null || !ctx.attackerRef().isValid()) return;

        try {
            EntityStatMap statMap = ctx.store().getComponent(ctx.attackerRef(),
                EntityStatMap.getComponentType());
            if (statMap == null) return;

            int healthIdx = DefaultEntityStatTypes.getHealth();
            var healthStat = statMap.get(healthIdx);
            if (healthStat == null) return;

            float curHp = healthStat.get();
            float maxHp = healthStat.getMax();
            float healAmount = maxHp * HEAL_FRACTION;

            // Apply Health Recovery multiplier if available
            if (ctx.attackerStats() != null) {
                float recoveryPct = ctx.attackerStats().getHealthRecoveryPercent();
                if (recoveryPct != 0) {
                    healAmount *= (1.0f + recoveryPct / 100.0f);
                }
            }

            float newHp = Math.min(curHp + healAmount, maxHp);
            statMap.setStatValue(EntityStatMap.Predictable.SELF, healthIdx, newHp);

            LOGGER.atFine().log("Feast or Famine: kill heal %.1f (8%% of %.0f max HP). HP: %.0f -> %.0f",
                healAmount, maxHp, curHp, newHp);
        } catch (Exception e) {
            LOGGER.atFine().log("Feast or Famine: could not apply kill heal — %s", e.getMessage());
        }
    }
}
