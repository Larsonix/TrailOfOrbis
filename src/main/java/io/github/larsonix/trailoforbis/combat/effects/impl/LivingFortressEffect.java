package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalEffectTracker;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalTriggerSystem;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Earth KS1: Living Fortress — Immovable Object.
 *
 * <p>While standing still (not moving for 1s+), gain Fortified state:
 * +50% Armor, +15% Block Chance (handled by WHILE_STATIONARY conditional),
 * and blocked attacks heal 5% of blocked damage (handled here).
 *
 * <p>The block heal only fires while the Fortified (stationary) conditional is active.
 */
public class LivingFortressEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String KEYSTONE_NODE = "earth_keystone_1";
    /** Block heal as fraction of blocked damage. */
    private static final float BLOCK_HEAL_FRACTION = 0.05f;

    public LivingFortressEffect() {
        super(KEYSTONE_NODE);
    }

    @Nonnull
    @Override
    public String getId() {
        return "living_fortress";
    }

    @Override
    public void onBlock(@Nonnull UUID defenderId, float blockedDamage, float postBlockDamage,
                        @Nonnull CombatEffectContext ctx) {
        // Only heal while Fortified (stationary conditional active)
        if (!isFortified(defenderId)) return;

        float healAmount = blockedDamage * BLOCK_HEAL_FRACTION;
        if (healAmount <= 0) return;

        // Apply Health Recovery multiplier
        if (ctx.defenderStats() != null) {
            float recoveryPct = ctx.defenderStats().getHealthRecoveryPercent();
            if (recoveryPct != 0) {
                healAmount *= (1.0f + recoveryPct / 100.0f);
            }
        }

        if (ctx.defenderRef() != null && ctx.defenderRef().isValid()) {
            try {
                EntityStatMap statMap = ctx.store().getComponent(ctx.defenderRef(),
                    EntityStatMap.getComponentType());
                if (statMap != null) {
                    int healthIdx = DefaultEntityStatTypes.getHealth();
                    var healthStat = statMap.get(healthIdx);
                    if (healthStat != null) {
                        float curHp = healthStat.get();
                        float maxHp = healthStat.getMax();
                        float newHp = Math.min(curHp + healAmount, maxHp);
                        statMap.setStatValue(EntityStatMap.Predictable.SELF, healthIdx, newHp);
                        LOGGER.atFine().log("Living Fortress: block heal %.1f (5%% of %.1f blocked). HP: %.0f -> %.0f",
                            healAmount, blockedDamage, curHp, newHp);
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("Living Fortress: could not apply block heal — %s", e.getMessage());
            }
        }
    }

    /**
     * Checks if the player is in Fortified state (WHILE_STATIONARY conditional active).
     */
    private boolean isFortified(@Nonnull UUID playerId) {
        TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
        if (plugin == null) return false;

        ConditionalTriggerSystem triggerSystem = plugin.getConditionalTriggerSystem();
        if (triggerSystem == null) return false;

        ConditionalEffectTracker tracker = triggerSystem.getTracker(playerId);
        if (tracker == null) return false;

        return tracker.isEffectActive(KEYSTONE_NODE, System.currentTimeMillis());
    }
}
