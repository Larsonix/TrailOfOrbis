package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Warlock KS1: Soul Siphon — Spell Crit Kill Sustain.
 *
 * <p>On Spell Crit Kill (a spell that crits AND kills):
 * <ul>
 *   <li>Restore 100% of the spell's Mana cost</li>
 *   <li>Heal 8% of the damage dealt</li>
 * </ul>
 *
 * <p>Both rewards share the same trigger — one event, two payoffs.
 * Mana restore is capped at full mana (no overfill).
 */
public class SoulSiphonEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Fraction of crit kill damage restored as HP. */
    private static final float CRIT_KILL_HEAL_FRACTION = 0.08f;

    public SoulSiphonEffect() {
        super("warlock_keystone_1");
    }

    @Nonnull
    @Override
    public String getId() {
        return "soul_siphon";
    }

    @Override
    public void onKill(@Nonnull UUID attackerId, @Nonnull UUID targetId,
                       float overkillDamage, @Nonnull CombatEffectContext ctx) {
        // Only triggers on spell crit kills
        if (ctx.attackType() != AttackType.SPELL) return;
        if (!ctx.wasCrit()) return;

        if (ctx.attackerRef() == null || !ctx.attackerRef().isValid()) return;

        try {
            EntityStatMap statMap = ctx.store().getComponent(ctx.attackerRef(),
                EntityStatMap.getComponentType());
            if (statMap == null) return;

            // 1. Restore mana to full
            int manaIdx = DefaultEntityStatTypes.getMana();
            var manaStat = statMap.get(manaIdx);
            if (manaStat != null) {
                float maxMana = manaStat.getMax();
                statMap.setStatValue(EntityStatMap.Predictable.SELF, manaIdx, maxMana);
                LOGGER.atFine().log("Soul Siphon: spell crit kill — mana fully restored (%.0f)", maxMana);
            }

            // 2. Heal 8% of damage dealt
            int healthIdx = DefaultEntityStatTypes.getHealth();
            var healthStat = statMap.get(healthIdx);
            if (healthStat != null) {
                float healAmount = ctx.rpgDamage() * CRIT_KILL_HEAL_FRACTION;

                // Apply Health Recovery multiplier
                if (ctx.attackerStats() != null) {
                    float recoveryPct = ctx.attackerStats().getHealthRecoveryPercent();
                    if (recoveryPct != 0) {
                        healAmount *= (1.0f + recoveryPct / 100.0f);
                    }
                }

                float curHp = healthStat.get();
                float maxHp = healthStat.getMax();
                float newHp = Math.min(curHp + healAmount, maxHp);
                statMap.setStatValue(EntityStatMap.Predictable.SELF, healthIdx, newHp);

                LOGGER.atFine().log("Soul Siphon: spell crit kill heal — %.1f (8%% of %.1f). HP: %.0f -> %.0f",
                    healAmount, ctx.rpgDamage(), curHp, newHp);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Soul Siphon: could not apply crit kill rewards — %s", e.getMessage());
        }
    }
}
