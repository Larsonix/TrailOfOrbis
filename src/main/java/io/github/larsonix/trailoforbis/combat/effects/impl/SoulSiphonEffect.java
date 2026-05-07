package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warlock KS1: Soul Siphon — Spell Vampire.
 *
 * <p>Mechanics:
 * <ul>
 *   <li>Spell kills fully restore mana</li>
 *   <li>Spell critical hits restore 8% of damage dealt as HP</li>
 *   <li>Overkill damage on spell kills charges next spell (+50% of overkill as bonus damage)</li>
 * </ul>
 */
public class SoulSiphonEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Fraction of crit damage restored as HP. */
    private static final float CRIT_HEAL_FRACTION = 0.08f;
    /** Fraction of overkill damage stored as bonus for next spell. */
    private static final float OVERKILL_CARRY_FRACTION = 0.50f;

    /** Per-player stored overkill charge for next spell. */
    private final ConcurrentHashMap<UUID, Float> overkillCharge = new ConcurrentHashMap<>();

    public SoulSiphonEffect() {
        super("warlock_keystone_1");
    }

    @Nonnull
    @Override
    public String getId() {
        return "soul_siphon";
    }

    @Override
    public float onPostCalculation(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerUuid() == null || ctx.attackType() != AttackType.SPELL) return ctx.rpgDamage();

        // Apply stored overkill charge to this spell
        Float charge = overkillCharge.remove(ctx.attackerUuid());
        if (charge != null && charge > 0) {
            LOGGER.atFine().log("Soul Siphon: overkill charge consumed — +%.1f bonus damage", charge);
            return ctx.rpgDamage() + charge;
        }
        return ctx.rpgDamage();
    }

    @Override
    public float onRecovery(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerUuid() == null || !ctx.hasAttackerStats()) return 0f;
        if (ctx.attackType() != AttackType.SPELL) return 0f;

        // Spell crit heal: 8% of damage dealt as HP recovery
        if (ctx.wasCrit()) {
            float healAmount = ctx.rpgDamage() * CRIT_HEAL_FRACTION;
            LOGGER.atFine().log("Soul Siphon: spell crit heal — %.1f (8%% of %.1f)", healAmount, ctx.rpgDamage());
            return healAmount;
        }

        return 0f;
    }

    @Override
    public void onKill(@Nonnull UUID attackerId, @Nonnull UUID targetId,
                       float overkillDamage, @Nonnull CombatEffectContext ctx) {
        if (ctx.attackType() != AttackType.SPELL) return;

        // Full mana restore on spell kill
        if (ctx.attackerRef() != null && ctx.attackerRef().isValid()) {
            try {
                EntityStatMap atkStatMap = ctx.store().getComponent(ctx.attackerRef(), EntityStatMap.getComponentType());
                if (atkStatMap != null) {
                    int manaIdx = DefaultEntityStatTypes.getMana();
                    var manaStat = atkStatMap.get(manaIdx);
                    if (manaStat != null) {
                        float maxMana = manaStat.getMax();
                        atkStatMap.setStatValue(EntityStatMap.Predictable.SELF, manaIdx, maxMana);
                        LOGGER.atFine().log("Soul Siphon: spell kill — mana fully restored (%.0f)", maxMana);
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Soul Siphon: could not restore mana — %s", e.getMessage());
            }
        }

        // Store overkill charge for next spell (50% of overkill damage)
        if (overkillDamage > 0) {
            float charge = overkillDamage * OVERKILL_CARRY_FRACTION;
            overkillCharge.put(attackerId, charge);
            LOGGER.atFine().log("Soul Siphon: overkill %.1f → stored %.1f charge for next spell", overkillDamage, charge);
        }
    }

    @Override
    public void cleanup(@Nonnull UUID playerId) {
        overkillCharge.remove(playerId);
    }
}
