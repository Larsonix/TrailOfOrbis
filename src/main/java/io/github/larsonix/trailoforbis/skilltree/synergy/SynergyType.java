package io.github.larsonix.trailoforbis.skilltree.synergy;

/**
 * Types of synergy calculations for scaling node bonuses.
 */
public enum SynergyType {
    /**
     * Bonus scales with number of nodes allocated in the same element/arm.
     * Example: "Per 3 Fire nodes, +3% Fire Damage"
     */
    ELEMENTAL_COUNT,

    /**
     * Bonus scales with number of nodes granting a specific stat type.
     * Example: "Per 5 nodes granting physical damage, +2% critical chance"
     */
    STAT_COUNT,

    /**
     * Bonus scales with number of nodes in the same branch/region.
     * Example: "Per 3 Havoc nodes, +2% Crit Damage"
     */
    BRANCH_COUNT,

    /**
     * Bonus scales with number of notable/keystone nodes allocated.
     * Example: "Per notable allocated, +2% spell damage"
     */
    TIER_COUNT,

    /**
     * Bonus scales with total allocated nodes (any type).
     * Example: "Per 10 total nodes, +50 max health"
     */
    TOTAL_COUNT,

    /**
     * Bonus scales with the sum of attribute points in the octant's 3 parent elements.
     * Used by octant nexus hubs to reward multi-element attribute investment.
     * Example: "Per 1 point in (Fire+Void+Lightning), +0.1% Crit Multiplier"
     * The region field on SynergyConfig determines which octant (and thus which 3 elements).
     */
    ATTRIBUTE_SUM_SCALING
}
