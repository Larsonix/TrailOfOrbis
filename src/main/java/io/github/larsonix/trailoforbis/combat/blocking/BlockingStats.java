package io.github.larsonix.trailoforbis.combat.blocking;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Container for a player's active blocking stats.
 *
 * <p>This record encapsulates all stats relevant to active shield blocking, mapping
 * to existing ComputedStats fields for consistency:
 * <ul>
 *   <li>{@code blockChance} - NEW stat: % chance for active block to succeed (0-100)</li>
 *   <li>{@code damageReduction} - Existing: BLOCK_DAMAGE_REDUCTION, % damage reduced when blocking</li>
 *   <li>{@code staminaDrainReduction} - Existing: STAMINA_DRAIN_REDUCTION, % stamina cost reduction</li>
 * </ul>
 *
 * <p>Design Decision: Rather than creating redundant stats, we reuse existing stats
 * with clear semantics. The only new stat is {@code blockChance} which determines
 * whether the active block succeeds at all.
 *
 * @param blockChance % chance for active block to succeed (0-100)
 * @param damageReduction % damage reduced when blocking (0-100)
 * @param staminaDrainReduction % reduction in stamina cost when blocking (0-75 cap)
 */
public record BlockingStats(
    float blockChance,
    float damageReduction,
    float staminaDrainReduction
) {
    /** Cap for stamina drain reduction to prevent free blocking. */
    private static final float MAX_STAMINA_REDUCTION = 75f;

    /**
     * Rolls a block chance check using the configured block chance.
     *
     * @return true if the roll succeeds (attack is blocked)
     */
    public boolean rollBlock() {
        return ThreadLocalRandom.current().nextFloat() * 100f < blockChance;
    }

    /**
     * Gets the damage multiplier after block reduction.
     *
     * <p>A 70% damage reduction means the player takes 30% of the original damage,
     * so this returns 0.3.
     *
     * @return The damage multiplier (0.0-1.0, where 0.0 = full block)
     */
    public float getDamageMultiplier() {
        return 1.0f - Math.min(damageReduction, 100f) / 100f;
    }

    /**
     * Gets the effective stamina reduction as a decimal.
     *
     * <p>Capped at {@link #MAX_STAMINA_REDUCTION} (75%) to prevent free blocking.
     *
     * @return The stamina reduction as a decimal (0.0-0.75)
     */
    public float getEffectiveStaminaReduction() {
        return Math.min(staminaDrainReduction, MAX_STAMINA_REDUCTION) / 100f;
    }

    /**
     * Creates BlockingStats from ComputedStats using existing stat mappings.
     *
     * <p>Field mapping:
     * <ul>
     *   <li>{@code blockChance} → {@code stats.getBlockChance()} (NEW stat)</li>
     *   <li>{@code damageReduction} → {@code stats.getBlockDamageReduction()} (existing)</li>
     *   <li>{@code staminaDrainReduction} → {@code stats.getStaminaDrainReduction()} (existing)</li>
     * </ul>
     *
     * @param stats The player's computed stats
     * @return A BlockingStats instance with values from the player's stats
     */
    @Nonnull
    public static BlockingStats from(@Nonnull ComputedStats stats) {
        return new BlockingStats(
            stats.getBlockChance(),
            stats.getBlockDamageReduction(),
            stats.getStaminaDrainReduction()
        );
    }
}
