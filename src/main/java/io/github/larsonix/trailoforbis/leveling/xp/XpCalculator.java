package io.github.larsonix.trailoforbis.leveling.xp;

import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;

/**
 * Interface for calculating XP rewards.
 *
 * <p>Implementations define how XP is calculated from different sources.
 * The primary implementation is {@link MobStatsXpCalculator} for mob kills.
 */
public interface XpCalculator {

    /**
     * Calculates XP reward for killing a mob.
     *
     * <p>The calculation considers:
     * <ul>
     *   <li>Mob level</li>
     *   <li>Mob stat pool (total stats)</li>
     *   <li>Mob tier (normal, elite, boss)</li>
     * </ul>
     *
     * @param mobScaling The mob's scaling component with stats
     * @return The calculated XP reward (always >= 1)
     */
    long calculateMobKillXp(@Nonnull MobScalingComponent mobScaling);
}
