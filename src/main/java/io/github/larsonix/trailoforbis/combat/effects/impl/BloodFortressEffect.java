package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Juggernaut KS1: Blood Fortress — Vampiric Aegis.
 *
 * <p>When you Block an attack, your next 3 attacks within 4s gain Life Steal equal to
 * 15% of the blocked damage. Successful blocks also grant a Blood Shield (10% of blocked
 * damage as temporary HP lasting 5s, stacks up to 30% Max HP).
 *
 * <p>Implementation: Tracks block charges per player. When a block occurs, charges
 * are created. When the player deals damage, charges are consumed for bonus life steal.
 */
public class BloodFortressEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int CHARGES_PER_BLOCK = 3;
    private static final long CHARGE_DURATION_MS = 4000L;
    private static final float BLOCK_LIFE_STEAL_FRACTION = 0.15f;

    private final ConcurrentHashMap<UUID, ChargeState> charges = new ConcurrentHashMap<>();

    public BloodFortressEffect() {
        super("juggernaut_keystone_1");
    }

    @Nonnull
    @Override
    public String getId() {
        return "blood_fortress";
    }

    @Override
    public void onBlock(@Nonnull UUID defenderId, float blockedDamage, float postBlockDamage,
                        @Nonnull CombatEffectContext ctx) {
        float lifeStealPerCharge = blockedDamage * BLOCK_LIFE_STEAL_FRACTION;
        charges.put(defenderId, new ChargeState(CHARGES_PER_BLOCK, lifeStealPerCharge, System.currentTimeMillis()));

        LOGGER.atFine().log("Blood Fortress: %s blocked %.1f — %d charges loaded (%.1f life steal each)",
            defenderId, blockedDamage, CHARGES_PER_BLOCK, lifeStealPerCharge);
    }

    @Override
    public float onRecovery(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerUuid() == null) return 0f;

        ChargeState state = charges.get(ctx.attackerUuid());
        if (state == null || state.chargesRemaining <= 0) return 0f;

        long elapsed = System.currentTimeMillis() - state.chargedAt;
        if (elapsed > CHARGE_DURATION_MS) {
            charges.remove(ctx.attackerUuid());
            return 0f;
        }

        // Consume a charge and return the life steal amount
        charges.compute(ctx.attackerUuid(), (uuid, s) -> {
            if (s == null || s.chargesRemaining <= 1) return null; // Remove when last charge used
            return new ChargeState(s.chargesRemaining - 1, s.lifeStealAmount, s.chargedAt);
        });

        LOGGER.atFine().log("Blood Fortress: charge consumed — +%.1f HP (from block). %d remaining.",
            state.lifeStealAmount, state.chargesRemaining - 1);

        return state.lifeStealAmount;
    }

    @Override
    public void cleanup(@Nonnull UUID playerId) {
        charges.remove(playerId);
    }

    private record ChargeState(int chargesRemaining, float lifeStealAmount, long chargedAt) {}
}
