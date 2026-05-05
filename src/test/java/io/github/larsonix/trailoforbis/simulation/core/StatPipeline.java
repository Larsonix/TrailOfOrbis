package io.github.larsonix.trailoforbis.simulation.core;

import io.github.larsonix.trailoforbis.attributes.AttributeCalculator;
import io.github.larsonix.trailoforbis.attributes.BaseStats;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;
import io.github.larsonix.trailoforbis.skilltree.calculation.AggregatedModifiers;
import io.github.larsonix.trailoforbis.skilltree.calculation.SkillTreeStatAggregator;
import io.github.larsonix.trailoforbis.skilltree.calculation.StatsCombiner;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Replicates the full stat computation pipeline from AttributeManager.recalculateStatsInternal()
 * using only pure calculation classes — no Hytale server required.
 *
 * <p>Pipeline order (matches production exactly):
 * <ol>
 *   <li>AttributeCalculator: element points → base ComputedStats</li>
 *   <li>SkillTreeStatAggregator + StatsCombiner: allocated nodes → modified stats</li>
 *   <li>consolidateResourcePercents: fold percent resource mods into base values</li>
 *   <li>GearStatApplier: gear bonuses → final stats</li>
 * </ol>
 */
public final class StatPipeline {

    private final AttributeCalculator attributeCalculator;
    private final SkillTreeStatAggregator skillTreeAggregator;
    private final StatsCombiner statsCombiner;
    private final GearStatApplier gearApplier;

    public StatPipeline(@Nonnull RPGConfig config, @Nonnull SkillTreeConfig skillTreeConfig) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(skillTreeConfig, "skillTreeConfig");

        this.attributeCalculator = new AttributeCalculator(config);
        this.skillTreeAggregator = new SkillTreeStatAggregator(skillTreeConfig);
        this.statsCombiner = new StatsCombiner();
        this.gearApplier = new GearStatApplier();
    }

    /**
     * Computes final ComputedStats from player build components.
     *
     * <p>Pipeline order (matches production AttributeManager exactly):
     * <ol>
     *   <li>AttributeCalculator: element points → base stats (flat + percent accumulators)</li>
     *   <li>StatsCombiner: skill tree nodes → flat added, percent accumulated</li>
     *   <li>GearStatApplier: gear bonuses → flat added, percent accumulated</li>
     *   <li>consolidateResourcePercents: ALL percent sources summed, applied ONCE</li>
     * </ol>
     *
     * @param playerData    Element attribute allocations
     * @param baseStats     Vanilla base resource values (use BaseStats.defaults())
     * @param skillTreeData Allocated skill tree nodes
     * @param gearBonuses   Summed gear bonuses (flat + percent maps)
     * @return Final ComputedStats ready for combat simulation
     */
    @Nonnull
    public ComputedStats compute(
            @Nonnull PlayerData playerData,
            @Nonnull BaseStats baseStats,
            @Nonnull SkillTreeData skillTreeData,
            @Nonnull GearBonuses gearBonuses) {

        // Step 1: Element attributes → base stats
        ComputedStats stats = attributeCalculator.calculateStats(playerData, baseStats);

        // Step 2: Skill tree modifiers (PoE formula: base+flat × %inc × %more)
        AggregatedModifiers modifiers = skillTreeAggregator.aggregate(skillTreeData);
        stats = statsCombiner.combine(stats, modifiers);

        // Step 3: Apply gear bonuses (flat to base, percent to accumulators)
        gearApplier.apply(stats, gearBonuses);

        // Step 4: Consolidate ALL resource percent accumulators — PoE "increased" formula
        // All sources (attributes, skill tree, gear) have deposited into percent fields.
        // Apply once: base × (1 + totalPercent/100)
        stats.consolidateResourcePercents();

        return stats;
    }

    /**
     * Computes stats without gear (for isolating attribute + skill tree contribution).
     */
    @Nonnull
    public ComputedStats computeWithoutGear(
            @Nonnull PlayerData playerData,
            @Nonnull BaseStats baseStats,
            @Nonnull SkillTreeData skillTreeData) {
        return compute(playerData, baseStats, skillTreeData, GearBonuses.EMPTY);
    }
}
