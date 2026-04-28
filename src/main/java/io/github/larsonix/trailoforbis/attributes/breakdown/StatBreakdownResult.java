package io.github.larsonix.trailoforbis.attributes.breakdown;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;

/**
 * Immutable snapshot of the stat calculation pipeline at each stage.
 *
 * <p>Deltas between consecutive snapshots give per-source contributions:
 * <ul>
 *   <li>{@code afterAttributes - base} = attribute point grants</li>
 *   <li>{@code afterSkillTree - afterAttributes} = skill tree modifiers</li>
 *   <li>{@code afterGear - afterSkillTree} = gear flat/percent bonuses</li>
 *   <li>{@code afterConditionals - afterGear} = ON_KILL/ON_CRIT temporary effects</li>
 * </ul>
 *
 * <p>Computed on demand when StatsPage opens. Never persisted, never cached.
 *
 * @param base              Vanilla stats + equipment armor, zero attribute points
 * @param afterAttributes   Attribute point grants applied
 * @param afterSkillTree    Skill tree PoE modifiers applied
 * @param afterGear         Gear flat/percent bonuses applied
 * @param afterConditionals ON_KILL/ON_CRIT temporary effects applied
 */
public record StatBreakdownResult(
    @Nonnull ComputedStats base,
    @Nonnull ComputedStats afterAttributes,
    @Nonnull ComputedStats afterSkillTree,
    @Nonnull ComputedStats afterGear,
    @Nonnull ComputedStats afterConditionals
) {}
