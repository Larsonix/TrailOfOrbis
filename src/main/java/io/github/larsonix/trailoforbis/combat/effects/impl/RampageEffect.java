package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Havoc KS1: Rampage — Kill Chain Escalation.
 *
 * <p>On Kill: gain a Rampage stack (max 5, lasts 6s, refreshes per kill).
 * Each stack grants +8% Attack Speed, +4% Crit Multiplier, +2% DoT Damage.
 *
 * <p>Note: The stat buffs from Rampage stacks are applied via the existing
 * ConditionalTriggerSystem (ON_KILL conditional on the keystone node).
 * This CombatEffect handles the KILL TRACKING for the stack system,
 * providing the multiplicative damage bonus per stack via onPostCalculation.
 */
public class RampageEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int MAX_STACKS = 5;
    private static final long STACK_DURATION_MS = 6000L;
    private static final float DAMAGE_PER_STACK_PERCENT = 2.0f; // Additional flat % per stack on top of conditional buffs

    private final ConcurrentHashMap<UUID, StackState> stacks = new ConcurrentHashMap<>();

    public RampageEffect() {
        super("havoc_keystone_1");
    }

    @Nonnull
    @Override
    public String getId() {
        return "rampage";
    }

    @Override
    public void onKill(@Nonnull UUID attackerId, @Nonnull UUID targetId,
                       float overkillDamage, @Nonnull CombatEffectContext ctx) {
        long now = System.currentTimeMillis();
        stacks.compute(attackerId, (uuid, existing) -> {
            if (existing == null || (now - existing.lastKillTime) > STACK_DURATION_MS) {
                // Expired or no stacks — start fresh
                return new StackState(1, now);
            }
            // Refresh timer and add stack (capped)
            int newCount = Math.min(existing.count + 1, MAX_STACKS);
            return new StackState(newCount, now);
        });

        StackState state = stacks.get(attackerId);
        LOGGER.atFine().log("Rampage: %s kills — %d/%d stacks", attackerId, state.count, MAX_STACKS);
    }

    @Override
    public float onPostCalculation(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerUuid() == null) return ctx.rpgDamage();

        StackState state = stacks.get(ctx.attackerUuid());
        if (state == null) return ctx.rpgDamage();

        long elapsed = System.currentTimeMillis() - state.lastKillTime;
        if (elapsed > STACK_DURATION_MS) {
            stacks.remove(ctx.attackerUuid());
            return ctx.rpgDamage();
        }

        // Apply per-stack damage bonus
        float bonus = state.count * DAMAGE_PER_STACK_PERCENT / 100f;
        return ctx.rpgDamage() * (1.0f + bonus);
    }

    @Override
    public void cleanup(@Nonnull UUID playerId) {
        stacks.remove(playerId);
    }

    private record StackState(int count, long lastKillTime) {}
}
