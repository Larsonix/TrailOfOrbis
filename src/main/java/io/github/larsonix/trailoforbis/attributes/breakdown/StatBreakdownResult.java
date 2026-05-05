package io.github.larsonix.trailoforbis.attributes.breakdown;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;

/**
 * Unconsolidated snapshots of the stat calculation pipeline at each stage.
 *
 * <p><b>UNCONSOLIDATED:</b> These snapshots have NOT been through
 * {@code consolidateResourcePercents()}. For resource stats (HP, mana, etc.),
 * the raw field holds ONLY flat contributions and the paired percent field
 * holds ONLY percent contributions. The consumer applies the ARPG formula:
 * {@code final = flat × (1 + percent/100) × multiplierProduct}.
 *
 * <p>For non-resource stats (armor, crit, etc.), the raw field IS the final
 * value since these stats don't go through consolidation.
 *
 * <p>Per-source contributions are computed as deltas between consecutive snapshots:
 * <ul>
 *   <li><b>Flat per source:</b> delta on raw field (e.g., {@code getMaxHealth()})</li>
 *   <li><b>Percent per source:</b> delta on accumulator (e.g., {@code getMaxHealthPercent()})</li>
 *   <li><b>Multiplier per source:</b> ratio of {@code getMultiplierProduct(field)}</li>
 * </ul>
 *
 * <p>Computed on demand when StatsPage opens. Never persisted, never cached.
 *
 * @param base              Vanilla stats + equipment armor, zero attribute points
 * @param afterAttributes   Attribute point grants applied (unconsolidated)
 * @param afterSkillTree    Skill tree PoE modifiers applied (unconsolidated)
 * @param afterGear         Gear flat/percent bonuses applied (unconsolidated)
 * @param afterConditionals ON_KILL/ON_CRIT temporary effects applied (unconsolidated)
 */
public record StatBreakdownResult(
    @Nonnull ComputedStats base,
    @Nonnull ComputedStats afterAttributes,
    @Nonnull ComputedStats afterSkillTree,
    @Nonnull ComputedStats afterGear,
    @Nonnull ComputedStats afterConditionals
) {}
