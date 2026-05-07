package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;

/**
 * Fire KS2: Berserker's Rage — Pain Fuels Power.
 *
 * <p>Mechanics:
 * <ul>
 *   <li>Damage increases by 1% for every 2% of HP missing (at 50% HP = +25% damage)</li>
 *   <li>Attacks cost 3% of current HP (self-damage, non-lethal — floor at 1 HP)</li>
 *   <li>Life steal/recovery cannot heal above 50% HP</li>
 * </ul>
 */
public class BerserkersRageEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Damage bonus: +1% per this many % of missing HP. Value 2.0 means +1% per 2% missing. */
    private static final float MISSING_HP_PER_BONUS_PERCENT = 2.0f;
    /** Attacks cost this fraction of current HP. */
    private static final float SELF_DAMAGE_FRACTION = 0.03f;
    /** Life steal cannot heal above this fraction of max HP. */
    private static final float LIFE_STEAL_HP_CAP = 0.50f;

    public BerserkersRageEffect() {
        super("fire_keystone_2");
    }

    @Nonnull
    @Override
    public String getId() {
        return "berserkers_rage";
    }

    @Override
    public float onPostCalculation(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerUuid() == null || !ctx.hasAttackerStats()) {
            return ctx.rpgDamage();
        }

        // Calculate missing HP damage bonus
        // At 100% HP: 0% bonus. At 50% HP: 25% bonus. At 10% HP: 45% bonus.
        float missingHpPercent = ctx.attackerMissingHpPercent() * 100f;

        float bonusDamagePercent = missingHpPercent / MISSING_HP_PER_BONUS_PERCENT;
        float multiplier = 1.0f + (bonusDamagePercent / 100f);

        if (multiplier > 1.0f) {
            LOGGER.atFine().log("Berserker's Rage: %.0f%% missing HP → +%.1f%% damage (×%.2f)",
                missingHpPercent, bonusDamagePercent, multiplier);
        }

        // Self-damage: 3% of current HP per attack (non-lethal, floor at 1 HP)
        applySelfDamage(ctx);

        return ctx.rpgDamage() * multiplier;
    }

    // Life steal cap at 50% HP is enforced in RPGDamageSystem.getBerserkersRageLeechCap()
    // which clamps all leech/steal amounts when this effect is active.

    private void applySelfDamage(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerRef() == null || !ctx.attackerRef().isValid()) return;

        try {
            EntityStatMap statMap = ctx.store().getComponent(ctx.attackerRef(), EntityStatMap.getComponentType());
            if (statMap == null) return;

            int hpIdx = DefaultEntityStatTypes.getHealth();
            EntityStatValue hpStat = statMap.get(hpIdx);
            if (hpStat == null) return;

            float currentHp = hpStat.get();
            float selfDmg = currentHp * SELF_DAMAGE_FRACTION;

            // Non-lethal: floor at 1 HP
            float newHp = Math.max(1.0f, currentHp - selfDmg);
            if (newHp < currentHp) {
                statMap.setStatValue(EntityStatMap.Predictable.SELF, hpIdx, newHp);
                LOGGER.atFine().log("Berserker's Rage: self-damage %.1f (3%% of %.0f HP). HP: %.0f → %.0f",
                    selfDmg, currentHp, currentHp, newHp);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Berserker's Rage: could not apply self-damage — %s", e.getMessage());
        }
    }
}
