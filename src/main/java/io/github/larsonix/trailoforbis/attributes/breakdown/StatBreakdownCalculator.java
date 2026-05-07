package io.github.larsonix.trailoforbis.attributes.breakdown;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.armor.EquipmentArmorReader;
import io.github.larsonix.trailoforbis.attributes.AttributeCalculator;
import io.github.larsonix.trailoforbis.attributes.BaseStats;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.gear.stats.GearBonusProvider;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.calculation.AggregatedModifiers;
import io.github.larsonix.trailoforbis.skilltree.calculation.SkillTreeStatAggregator;
import io.github.larsonix.trailoforbis.skilltree.calculation.StatsCombiner;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalTriggerSystem;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.systems.StatProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Replays the stat calculation pipeline, capturing {@link ComputedStats} snapshots
 * at each stage boundary. The deltas between consecutive snapshots reveal each
 * source's contribution to every stat.
 *
 * <p>This mirrors the pipeline in {@code AttributeManager.recalculateStatsInternal()}
 * but instead of producing a single final result, it captures 5 intermediate snapshots.
 *
 * <p>Pipeline stages:
 * <ol>
 *   <li><b>Base</b> — vanilla stats + equipment armor, zero attribute points</li>
 *   <li><b>After Attributes</b> — real attribute point grants applied</li>
 *   <li><b>After Skill Tree</b> — skill tree PoE modifiers applied</li>
 *   <li><b>After Gear</b> — gear flat/percent bonuses applied</li>
 *   <li><b>After Conditionals</b> — ON_KILL/ON_CRIT temporary effects</li>
 * </ol>
 */
public class StatBreakdownCalculator {

    private final AttributeCalculator calculator;
    private final ConfigManager configManager;
    private final StatProvider statProvider;
    private final PlayerDataRepository playerDataRepo;
    private final GearBonusProvider gearBonusProvider;
    private final ConditionalTriggerSystem condSystem;

    public StatBreakdownCalculator(
        @Nonnull AttributeCalculator calculator,
        @Nonnull ConfigManager configManager,
        @Nonnull StatProvider statProvider,
        @Nonnull PlayerDataRepository playerDataRepo,
        @Nullable GearBonusProvider gearBonusProvider,
        @Nullable ConditionalTriggerSystem condSystem
    ) {
        this.calculator = Objects.requireNonNull(calculator);
        this.configManager = Objects.requireNonNull(configManager);
        this.statProvider = Objects.requireNonNull(statProvider);
        this.playerDataRepo = Objects.requireNonNull(playerDataRepo);
        this.gearBonusProvider = gearBonusProvider;
        this.condSystem = condSystem;
    }

    /**
     * Calculates the stat breakdown for a player by replaying the full pipeline.
     *
     * @param playerId The player's UUID
     * @return The breakdown result with 5 snapshots, or null if player data unavailable
     */
    @Nullable
    public StatBreakdownResult calculate(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        // Get player data
        PlayerData realData = playerDataRepo.get(playerId).orElse(null);
        if (realData == null) return null;

        // Get vanilla base stats and equipment armor
        BaseStats vanillaStats = statProvider.getBaseStats(playerId);
        float equipmentArmor = getEquipmentArmor(playerId);

        // Get player level (matches AttributeManager.recalculateStatsInternal)
        int playerLevel = 1;
        Optional<LevelingService> levelingOpt = ServiceRegistry.get(LevelingService.class);
        if (levelingOpt.isPresent()) {
            playerLevel = levelingOpt.get().getLevel(playerId);
        }

        // Create zero-attribute clone for base snapshot
        PlayerData zeroData = realData.toBuilder()
            .fire(0).water(0).lightning(0).earth(0).wind(0).voidAttr(0)
            .build();

        // === STEP 1: BASE SNAPSHOT ===
        // Vanilla stats + equipment armor, zero attribute points
        ComputedStats base = calculator.calculateStats(zeroData, vanillaStats, equipmentArmor, playerLevel);

        // === STEP 2: AFTER ATTRIBUTES SNAPSHOT ===
        // Real attribute point grants applied
        ComputedStats afterAttributes = calculator.calculateStats(realData, vanillaStats, equipmentArmor, playerLevel);

        // === STEP 3: AFTER SKILL TREE SNAPSHOT ===
        ComputedStats afterSkillTree = afterAttributes;
        Optional<SkillTreeService> skillOpt = ServiceRegistry.get(SkillTreeService.class);
        if (skillOpt.isPresent()) {
            SkillTreeService skillService = skillOpt.get();
            SkillTreeData treeData = skillService.getSkillTreeData(playerId);

            if (!treeData.getAllocatedNodes().isEmpty()) {
                if (skillService instanceof SkillTreeManager manager && manager.getTreeConfig() != null) {
                    SkillTreeStatAggregator aggregator = new SkillTreeStatAggregator(manager.getTreeConfig());
                    aggregator.setPlayerData(realData); // For ATTRIBUTE_SUM_SCALING nexus hubs
                    StatsCombiner combiner = new StatsCombiner();
                    AggregatedModifiers treeMods = aggregator.aggregate(treeData);
                    afterSkillTree = combiner.combine(afterAttributes, treeMods);
                }
            }
        }

        // === STEP 4: AFTER GEAR SNAPSHOT ===
        // CRITICAL: deep copy before gear mutation (gear applies in-place)
        ComputedStats afterGear = afterSkillTree.copy();
        if (gearBonusProvider != null) {
            gearBonusProvider.applyGearBonuses(playerId, afterGear);
        }

        // === STEP 5: AFTER CONDITIONALS SNAPSHOT ===
        ComputedStats afterConditionals = afterGear;
        if (condSystem != null) {
            List<StatModifier> condMods = condSystem.getActiveModifiers(playerId);
            if (!condMods.isEmpty()) {
                AggregatedModifiers.Builder condBuilder = AggregatedModifiers.builder();
                for (StatModifier mod : condMods) {
                    condBuilder.addModifier(mod);
                }
                StatsCombiner combiner = new StatsCombiner();
                afterConditionals = combiner.combine(afterGear, condBuilder.build());
            }
        }

        // === STEP 6: RETURN UNCONSOLIDATED SNAPSHOTS ===
        // Snapshots are stored WITHOUT consolidation so the consumer can decompose
        // each source's contribution into flat, percent, and multiplier layers.
        //
        // For resource stats (HP, mana, etc.) the ARPG formula is:
        //   Final = (base + flat) × (1 + percent/100) × multiplierProduct
        //
        // The consumer extracts:
        //   - Flat per source:  delta on the raw field (e.g., getMaxHealth())
        //   - Percent per source: delta on the accumulator (e.g., getMaxHealthPercent())
        //   - Multiplier per source: ratio of multiplierProduct between snapshots
        //   - Final value: from the real ComputedStats (already consolidated by main pipeline)
        //
        // For non-resource stats (armor, crit, etc.), the raw field IS the final value
        // since these stats don't go through consolidateResourcePercents().

        return new StatBreakdownResult(base, afterAttributes, afterSkillTree, afterGear, afterConditionals);
    }

    /**
     * Gets equipment armor value for a player, respecting config.
     */
    private float getEquipmentArmor(@Nonnull UUID playerId) {
        RPGConfig.ArmorConfig armorConfig = configManager.getRPGConfig().getArmor();
        if (armorConfig.isIncludeEquipmentArmor()) {
            return EquipmentArmorReader.getTotalEquipmentArmorByUUID(
                playerId, armorConfig.getEquipmentArmorMultiplier());
        }
        return 0f;
    }
}
