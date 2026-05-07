package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water KS1: Glacial Mastery — Shatter Specialist.
 *
 * <p>Hitting a Frozen target SHATTERS the freeze, dealing burst Water damage equal to
 * 200% of the hit damage. After shattering, the target cannot be re-frozen for 4 seconds.
 * Non-frozen targets take 20% less damage from this player.
 */
public class GlacialMasteryEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Shatter burst damage as % of the hit that triggered it. */
    private static final float SHATTER_DAMAGE_MULTIPLIER = 2.0f;
    /** Cooldown in milliseconds before the target can be re-frozen. */
    private static final long SHATTER_COOLDOWN_MS = 4000L;
    /** Damage penalty against non-frozen targets. */
    private static final float NON_FROZEN_PENALTY = 0.20f;

    private final AilmentTracker ailmentTracker;
    /** Per-defender shatter cooldown tracking. */
    private final ConcurrentHashMap<UUID, Long> lastShatterTime = new ConcurrentHashMap<>();

    public GlacialMasteryEffect(@Nonnull AilmentTracker ailmentTracker) {
        super("water_keystone_1");
        this.ailmentTracker = ailmentTracker;
    }

    @Nonnull
    @Override
    public String getId() {
        return "glacial_mastery";
    }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (ctx.defenderUuid() == null || !ctx.hasAttackerStats()) {
            return ctx.rpgDamage();
        }

        // Check if target is frozen
        boolean targetFrozen = ailmentTracker.hasAilment(ctx.defenderUuid(), AilmentType.FREEZE);

        if (targetFrozen) {
            // Check cooldown
            long now = System.currentTimeMillis();
            Long lastShatter = lastShatterTime.get(ctx.defenderUuid());
            if (lastShatter != null && (now - lastShatter) < SHATTER_COOLDOWN_MS) {
                // Cooldown active — treat as non-frozen (apply penalty)
                return ctx.rpgDamage() * (1.0f - NON_FROZEN_PENALTY);
            }

            // SHATTER: burst damage + clear freeze + start cooldown
            float burstDamage = ctx.rpgDamage() * SHATTER_DAMAGE_MULTIPLIER;
            ailmentTracker.removeAilment(ctx.defenderUuid(), AilmentType.FREEZE);
            lastShatterTime.put(ctx.defenderUuid(), now);

            LOGGER.atFine().log("Glacial Shatter: %.1f base × %.0f%% = +%.1f burst. 4s cooldown started.",
                ctx.rpgDamage(), SHATTER_DAMAGE_MULTIPLIER * 100, burstDamage);

            return ctx.rpgDamage() + burstDamage;
        } else {
            // Non-frozen penalty
            return ctx.rpgDamage() * (1.0f - NON_FROZEN_PENALTY);
        }
    }

    @Override
    public void cleanup(@Nonnull UUID playerId) {
        // Remove cooldowns where this player was the target (keyed by defender UUID).
        // Short-lived entries (4s TTL) for other targets will expire naturally.
        lastShatterTime.remove(playerId);
    }
}
