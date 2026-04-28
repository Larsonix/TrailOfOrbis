package io.github.larsonix.trailoforbis.attributes;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownCalculator;
import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.armor.EquipmentArmorReader;
import io.github.larsonix.trailoforbis.skilltree.calculation.AggregatedModifiers;
import io.github.larsonix.trailoforbis.skilltree.calculation.SkillTreeStatAggregator;
import io.github.larsonix.trailoforbis.skilltree.calculation.StatsCombiner;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalTriggerSystem;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.systems.StatProvider;
import io.github.larsonix.trailoforbis.gear.stats.GearBonusProvider;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manager for player attributes and stat calculations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Attribute allocation with validation</li>
 *   <li>Stats recalculation orchestration</li>
 *   <li>ECS component application (health, mana, movement)</li>
 * </ul>
 *
 * <p><b>CRITICAL:</b> ComputedStats are TRANSIENT - never persisted to database.
 * They are recalculated from attributes + config whenever needed.
 *
 * <p>ECS methods MUST be called from the ECS world thread (event handler or world.execute()).
 *
 * <p>Example usage:
 * <pre>
 * // In an event handler (already on world thread)
 * attributeManager.allocateAttribute(store, commandBuffer, playerRef, AttributeType.STRENGTH);
 *
 * // On player join
 * attributeManager.recalculateStats(store, commandBuffer, playerRef);
 * </pre>
 */
public class AttributeManager implements AttributeService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final PlayerDataRepository playerDataRepository;
    private final AttributeCalculator calculator;
    private final ConfigManager configManager;
    private final StatProvider statProvider;
    private final ConcurrentHashMap<UUID, ReentrantLock> statsLocks = new ConcurrentHashMap<>();

    /**
     * Per-player stats version counter.
     *
     * <p>Incremented every time stats are recalculated. Used by the gear tooltip
     * system to detect when requirement display colors need updating.
     */
    private final Map<UUID, AtomicLong> statsVersions = new ConcurrentHashMap<>();

    // Gear bonus provider for applying equipment stat bonuses
    private volatile GearBonusProvider gearBonusProvider;

    // Conditional trigger system for ON_KILL, ON_CRIT, etc. effects
    private volatile ConditionalTriggerSystem conditionalTriggerSystem;

    // Lazy-initialized breakdown calculator for on-demand stat source analysis
    private volatile StatBreakdownCalculator breakdownCalculator;

    // Callback for applying stats to ECS after recalculation
    private volatile StatsApplicationCallback statsApplicationCallback;

    // Callback for refreshing gear tooltips after stat changes
    private volatile TooltipRefreshCallback tooltipRefreshCallback;

    /**
     * Creates a new AttributeManager.
     *
     * @param dataManager The data manager for database connections
     * @param configManager The config manager for RPG settings
     * @param statProvider Provider for base stats from the game
     */
    public AttributeManager(@Nonnull DataManager dataManager, @Nonnull ConfigManager configManager, @Nonnull StatProvider statProvider) {
        Objects.requireNonNull(dataManager, "dataManager cannot be null");
        Objects.requireNonNull(configManager, "configManager cannot be null");
        Objects.requireNonNull(statProvider, "statProvider cannot be null");

        this.configManager = configManager;
        this.playerDataRepository = new PlayerDataRepository(dataManager);
        this.calculator = new AttributeCalculator(configManager.getRPGConfig());
        this.statProvider = statProvider;
    }

    /**
     * Creates a new AttributeManager with explicit dependencies (for testing).
     *
     * @param playerDataRepository The player data repository
     * @param calculator The attribute calculator
     * @param configManager The config manager
     * @param statProvider The stat provider
     */
    public AttributeManager(
        @Nonnull PlayerDataRepository playerDataRepository,
        @Nonnull AttributeCalculator calculator,
        @Nonnull ConfigManager configManager,
        @Nonnull StatProvider statProvider
    ) {
        this.playerDataRepository = Objects.requireNonNull(playerDataRepository);
        this.calculator = Objects.requireNonNull(calculator);
        this.configManager = Objects.requireNonNull(configManager);
        this.statProvider = Objects.requireNonNull(statProvider);
    }

    /** Gets or creates a lock for a specific player. */
    private ReentrantLock getStatsLock(UUID playerId) {
        return statsLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    }

    /**
     * Gets the current stats version for a player.
     *
     * <p>The stats version is a monotonically increasing counter that gets
     * incremented every time the player's stats are recalculated. This is used
     * by the gear tooltip system to detect when requirement display colors
     * need updating (e.g., when a player allocates attribute points).
     *
     * @param playerId The player's UUID
     * @return The current stats version (0 if player not tracked)
     */
    public long getStatsVersion(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        AtomicLong version = statsVersions.get(playerId);
        return version != null ? version.get() : 0L;
    }

    /** Called internally after stats recalculation completes. */
    private void incrementStatsVersion(@Nonnull UUID playerId) {
        statsVersions.computeIfAbsent(playerId, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Allocates 1 point to an attribute.
     *
     * <p>VALIDATION:
     * <ul>
     *   <li>Player must exist in database</li>
     *   <li>Player must have unallocated points > 0</li>
     * </ul>
     *
     * <p>SIDE EFFECTS:
     * <ul>
     *   <li>Updates attribute (+1)</li>
     *   <li>Decrements unallocated points (-1)</li>
     *   <li>Recalculates all stats</li>
     *   <li>Updates cache with new ComputedStats</li>
     *   <li>Saves to database</li>
     *   <li>Logs success message</li>
     * </ul>
     *
     * <p>Note: This overload does NOT apply ECS changes. Use the overload with
     * ECS parameters to apply health/mana/movement speed to the player entity.
     *
     * @param playerId The player's UUID
     * @param type The attribute type to allocate to
     * @return true if allocation succeeded, false if validation failed
     */
    @Override
    public boolean allocateAttribute(@Nonnull UUID playerId, @Nonnull AttributeType type) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        // 1. Validate player exists
        Optional<PlayerData> existing = playerDataRepository.get(playerId);
        if (existing.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Cannot allocate - player data not found: %s", playerId);
            return false;
        }

        PlayerData data = existing.get();

        // 2. Validate has points
        if (data.getUnallocatedPoints() <= 0) {
            LOGGER.at(Level.INFO).log("Player %s has no unallocated points", playerId);
            return false;
        }

        // 3. Update attribute (+1) and points (-1)
        final PlayerData updated = incrementAttribute(data, type)
            .withUnallocatedPoints(data.getUnallocatedPoints() - 1);

        // 4. Save to database (attributes only - ComputedStats is transient)
        playerDataRepository.save(updated);

        // 5. Recalculate all stats (includes skill tree modifiers)
        recalculateStats(playerId);

        // 6. Log success
        LOGGER.at(Level.INFO).log("Player %s allocated 1 point to %s (now %d)", playerId, type.getDisplayName(), getAttributeValue(updated, type));

        return true;
    }

    /**
     * Recalculates and caches all stats for a player.
     *
     * <p>This method acquires a per-player lock to ensure thread-safe stat calculation.
     * Multiple threads can recalculate stats for different players concurrently,
     * but only one thread can recalculate stats for the same player at a time.
     *
     * <p>This method:
     * <ul>
     *   <li>Gets PlayerData from cache/database</li>
     *   <li>Uses calculator to compute base stats from attributes</li>
     *   <li>Applies skill tree modifiers (if available)</li>
     *   <li>Attaches ComputedStats to PlayerData (transient, in-memory only)</li>
     *   <li>Updates cache (does NOT persist to database)</li>
     * </ul>
     *
     * <p>Calculation Pipeline:
     * <ol>
     *   <li>Base stats from AttributeCalculator (attributes + vanilla base)</li>
     *   <li>Equipment armor included in base calculation</li>
     *   <li>Skill tree modifiers applied via PoE formula</li>
     *   <li>Gear stat bonuses from equipped items (prefixes/suffixes)</li>
     *   <li>(Future: Buffs, temporary effects, etc.)</li>
     * </ol>
     *
     * <p>Note: This overload does NOT apply ECS changes. Use the overload with
     * ECS parameters to apply health/mana/movement speed to the player entity.
     *
     * @param playerId The player's UUID
     * @return The calculated ComputedStats, or null if player not found
     */
    @Override
    @Nullable
    public ComputedStats recalculateStats(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        ReentrantLock lock = getStatsLock(playerId);
        lock.lock();
        try {
            return recalculateStatsInternal(playerId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Internal method to recalculate stats without locking.
     * Must be called with the player's lock already acquired.
     *
     * @param playerId The player's UUID
     * @return The calculated ComputedStats, or null if player not found
     */
    private ComputedStats recalculateStatsInternal(@Nonnull UUID playerId) {

        // 1. Get player data
        Optional<PlayerData> existing = playerDataRepository.get(playerId);
        if (existing.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Cannot recalculate - player data not found: %s", playerId);
            return null;
        }

        PlayerData data = existing.get();

        // 2. Get vanilla base stats (dynamic from entity)
        BaseStats vanillaStats = statProvider.getBaseStats(playerId);

        // 2.5. Get equipment armor from player's equipped items (respecting config)
        float equipmentArmor = 0f;
        RPGConfig.ArmorConfig armorConfig = configManager.getRPGConfig().getArmor();
        if (armorConfig.isIncludeEquipmentArmor()) {
            equipmentArmor = EquipmentArmorReader.getTotalEquipmentArmorByUUID(
                playerId,
                armorConfig.getEquipmentArmorMultiplier()
            );
        }

        // 2.6. Get player level for magic charges calculation
        int playerLevel = 1;
        Optional<io.github.larsonix.trailoforbis.leveling.api.LevelingService> levelingOpt =
                ServiceRegistry.get(io.github.larsonix.trailoforbis.leveling.api.LevelingService.class);
        if (levelingOpt.isPresent()) {
            playerLevel = levelingOpt.get().getLevel(playerId);
        }

        // 3. Calculate base stats from attributes + vanilla base + equipment armor + level
        ComputedStats baseStats = calculator.calculateStats(data, vanillaStats, equipmentArmor, playerLevel);
        LOGGER.at(Level.FINE).log("Base stats from attributes: HP=%s, Mana=%s, Armor=%s (equipment: %s)",
            baseStats.getMaxHealth(), baseStats.getMaxMana(), baseStats.getArmor(), equipmentArmor);

        // 4. Apply skill tree modifiers (if skill tree system is enabled)
        ComputedStats finalStats = baseStats;
        Optional<SkillTreeService> skillServiceOpt = ServiceRegistry.get(SkillTreeService.class);
        if (skillServiceOpt.isPresent()) {
            SkillTreeService skillService = skillServiceOpt.get();
            SkillTreeData treeData = skillService.getSkillTreeData(playerId);

            // Only apply if player has allocated nodes
            if (!treeData.getAllocatedNodes().isEmpty()) {
                // Get the manager to access the tree config
                if (skillService instanceof SkillTreeManager manager && manager.getTreeConfig() != null) {
                    SkillTreeStatAggregator aggregator = new SkillTreeStatAggregator(manager.getTreeConfig());
                    StatsCombiner combiner = new StatsCombiner();

                    AggregatedModifiers modifiers = aggregator.aggregate(treeData);

                    // Log aggregated modifiers for all stats (FINER level due to loop verbosity)
                    LOGGER.at(Level.FINER).log("Skill tree modifiers for %s:", playerId);
                    for (io.github.larsonix.trailoforbis.skilltree.model.StatType statType :
                            io.github.larsonix.trailoforbis.skilltree.model.StatType.values()) {
                        float flat = modifiers.getFlatSum(statType);
                        float percent = modifiers.getPercentSum(statType);
                        var mults = modifiers.getMultipliers(statType);
                        if (flat != 0 || percent != 0 || !mults.isEmpty()) {
                            LOGGER.at(Level.FINER).log("  %s: flat=%s, percent=%s%%, multipliers=%s", statType, flat, percent, mults);
                        }
                    }

                    finalStats = combiner.combine(baseStats, modifiers);

                    LOGGER.at(Level.FINE).log("Applied skill tree modifiers for %s (%d nodes): HP=%s",
                        playerId, treeData.getAllocatedNodes().size(), finalStats.getMaxHealth());
                }
            }
        }

        // 4.5. Fold percent resource modifiers (maxHealthPercent, maxManaPercent, etc.) into
        // actual resource values. These are stored separately by AttributeCalculator and
        // StatsCombiner but must be applied BEFORE gear bonuses (which layer on top).
        finalStats.consolidateResourcePercents();

        // 5. Apply gear bonuses from equipped items (if gear system is enabled)
        GearBonusProvider gearProvider = this.gearBonusProvider;
        if (gearProvider != null) {
            boolean applied = gearProvider.applyGearBonuses(playerId, finalStats);
            if (applied) {
                LOGGER.at(Level.FINE).log("Applied gear bonuses for %s: HP=%s, PhysDmg=%s",
                    playerId, finalStats.getMaxHealth(), finalStats.getPhysicalDamage());
            }
        }

        // 6. Apply conditional modifiers (ON_KILL, ON_CRIT, threshold bonuses) if enabled
        ConditionalTriggerSystem condSystem = this.conditionalTriggerSystem;
        if (condSystem != null) {
            List<StatModifier> conditionalMods = condSystem.getActiveModifiers(playerId);
            if (!conditionalMods.isEmpty()) {
                // Build AggregatedModifiers from active conditional effects
                AggregatedModifiers.Builder conditionalBuilder = AggregatedModifiers.builder();
                for (StatModifier mod : conditionalMods) {
                    conditionalBuilder.addModifier(mod);
                }
                AggregatedModifiers conditionalAggregated = conditionalBuilder.build();

                // Apply using StatsCombiner
                StatsCombiner combiner = new StatsCombiner();
                finalStats = combiner.combine(finalStats, conditionalAggregated);

                LOGGER.at(Level.FINE).log("Applied %d conditional modifiers for %s",
                    conditionalMods.size(), playerId);
            }
        }

        // 7. Attach to PlayerData (transient, in-memory only)
        // Check if stats actually changed before incrementing version —
        // multiple systems call recalculateStats() per frame, and unconditional
        // version bumps cause infinite item re-syncs (each bump invalidates
        // every item's definition hash, flooding the client with packets).
        ComputedStats previousStats = data.getComputedStats();
        boolean statsChanged = previousStats == null || !previousStats.equals(finalStats);

        PlayerData withStats = data.withComputedStats(finalStats);
        playerDataRepository.updateCache(playerId, withStats);

        // DIAGNOSTIC: Log whether stats actually changed (INFO level so it's visible)
        if (statsChanged) {
            LOGGER.atInfo().log("[DIAG] Stats CHANGED for %s — will apply + bump version (HP=%.1f)",
                playerId.toString().substring(0, 8), finalStats.getMaxHealth());
        } else {
            LOGGER.atInfo().log("[DIAG] Stats UNCHANGED for %s — skipping ECS apply + version bump",
                playerId.toString().substring(0, 8));
        }

        // 8. Increment stats version ONLY when stats actually changed
        if (statsChanged) {
            incrementStatsVersion(playerId);
        }

        // Apply to ECS and refresh tooltips ONLY when stats actually changed.
        // When stats are unchanged (e.g., redundant recalculation from multiple
        // systems firing on the same event), skip all side effects to prevent
        // feedback loops and redundant packet floods.
        if (statsChanged) {
            StatsApplicationCallback callback = this.statsApplicationCallback;
            if (callback != null) {
                LOGGER.at(Level.FINE).log("[STATS] Invoking ECS callback for player %s (HP=%.1f, MovSpeed=%.1f%%)",
                    playerId, finalStats.getMaxHealth(), finalStats.getMovementSpeedPercent());
                try {
                    callback.applyStatsToEntity(playerId);
                } catch (Exception e) {
                    LOGGER.at(Level.SEVERE).withCause(e).log("[STATS] ECS callback FAILED for %s", playerId);
                }
            }

            // 9. Refresh gear tooltips if callback is registered
            TooltipRefreshCallback tooltipCallback = this.tooltipRefreshCallback;
            if (tooltipCallback != null) {
                try {
                    tooltipCallback.refreshTooltips(playerId);
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log("Failed to refresh tooltips for %s", playerId);
                }
            }
        }

        return finalStats;
    }

    /**
     * Gets the current ComputedStats for a player.
     *
     * <p>Uses double-checked locking pattern for defensive recalculation:
     * <ol>
     *   <li>First check: Read from cache without lock (fast path)</li>
     *   <li>If null: Acquire lock</li>
     *   <li>Second check: Read from cache again (another thread may have calculated)</li>
     *   <li>If still null: Recalculate with lock held</li>
     * </ol>
     *
     * <p>This ensures stats are always available while minimizing lock contention.
     * Defensive recalculation should rarely trigger after Phase 1 fixes.
     *
     * @param playerId The player's UUID
     * @return The ComputedStats, or null if player not found
     */
    /**
     * Returns all cached (online) player UUIDs.
     */
    @Nonnull
    public Set<UUID> getOnlinePlayerIds() {
        return playerDataRepository.getCachedUuids();
    }

    @Override
    @Nullable
    public ComputedStats getStats(@Nonnull UUID playerId) {
        // First check: Read from cache without lock (fast path)
        Optional<PlayerData> data = playerDataRepository.get(playerId);
        if (data.isEmpty()) {
            LOGGER.at(Level.FINE).log("getStats() - Player not found in repository: %s", playerId);
            return null;
        }

        ComputedStats stats = data.get().getComputedStats();

        // Fast path: Stats exist, return immediately
        if (stats != null) {
            return stats;
        }

        // Slow path: Stats are null, use double-checked locking
        ReentrantLock lock = getStatsLock(playerId);
        lock.lock();
        try {
            // Second check: Another thread may have calculated while we waited for lock
            data = playerDataRepository.get(playerId);
            if (data.isEmpty()) {
                LOGGER.at(Level.SEVERE).log("ERROR: getStats() - Player disappeared during lock acquisition: %s", playerId);
                return null;
            }

            stats = data.get().getComputedStats();
            if (stats != null) {
                // Another thread calculated it - return their result
                return stats;
            }

            // Still null - perform defensive recalculation
            LOGGER.at(Level.WARNING).log("getStats() - Defensive recalculation triggered for %s (possible race condition)", playerId);

            stats = recalculateStatsInternal(playerId);
            if (stats == null) {
                LOGGER.at(Level.SEVERE).log("CRITICAL: getStats() - recalculateStatsInternal() also returned null for %s", playerId);
            }
            return stats;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the stat breakdown showing per-source contributions for each stat.
     *
     * <p>Uses lazy-initialized {@link StatBreakdownCalculator} with double-checked
     * locking. Safe to call after all providers are wired (gearBonusProvider, etc.).
     *
     * @param playerId The player's UUID
     * @return The breakdown, or null if player data unavailable
     */
    @Override
    @Nullable
    public StatBreakdownResult getStatBreakdown(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        // Lazy-init with double-checked locking
        StatBreakdownCalculator calc = this.breakdownCalculator;
        if (calc == null) {
            synchronized (this) {
                calc = this.breakdownCalculator;
                if (calc == null) {
                    calc = new StatBreakdownCalculator(
                        calculator, configManager, statProvider,
                        playerDataRepository, gearBonusProvider, conditionalTriggerSystem
                    );
                    this.breakdownCalculator = calc;
                }
            }
        }

        return calc.calculate(playerId);
    }

    /**
     * Gets all attribute values for a player.
     *
     * <p>Returns a map of attribute type to current value.
     * Used by the equipment validation system to check gear requirements.
     *
     * @param playerId The player's UUID
     * @return Map of attribute type to value, empty map if player not found
     */
    @Override
    @Nonnull
    public Map<AttributeType, Integer> getPlayerAttributes(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Optional<PlayerData> dataOpt = playerDataRepository.get(playerId);
        if (dataOpt.isEmpty()) {
            return Map.of();
        }

        PlayerData data = dataOpt.get();
        return Map.of(
            AttributeType.FIRE, data.getFire(),
            AttributeType.WATER, data.getWater(),
            AttributeType.LIGHTNING, data.getLightning(),
            AttributeType.EARTH, data.getEarth(),
            AttributeType.WIND, data.getWind(),
            AttributeType.VOID, data.getVoidAttr()
        );
    }

    /** Increments the specified attribute by 1. */
    private PlayerData incrementAttribute(PlayerData data, AttributeType type) {
        return type.increment(data);
    }

    /** Gets the value of a specific attribute from player data. */
    private int getAttributeValue(PlayerData data, AttributeType type) {
        return type.getValue(data);
    }

    /** Sets a specific attribute value in player data. */
    private PlayerData setAttributeValue(PlayerData data, AttributeType type, int value) {
        return type.withValue(data, value);
    }

    // ==================== Admin Methods ====================

    /**
     * Sets a player's unallocated points to an absolute value.
     *
     * @param playerId The player's UUID
     * @param points The new point total (must be >= 0)
     * @return true if operation succeeded, false if player not found or invalid value
     */
    @Override
    public boolean setUnallocatedPoints(@Nonnull UUID playerId, int points) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        if (points < 0) {
            LOGGER.at(Level.SEVERE).log("Cannot set unallocated points to negative value: %d", points);
            return false;
        }

        Optional<PlayerData> existing = playerDataRepository.get(playerId);
        if (existing.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Cannot set points - player data not found: %s", playerId);
            return false;
        }

        PlayerData data = existing.get();
        PlayerData updated = data.withUnallocatedPoints(points);

        playerDataRepository.save(updated);

        // Recalculate all stats (includes skill tree modifiers)
        recalculateStats(playerId);

        LOGGER.at(Level.INFO).log("Set unallocated points for %s to %d", playerId, points);
        return true;
    }

    /**
     * Modifies a player's unallocated points by a delta value.
     *
     * @param playerId The player's UUID
     * @param delta The amount to add (positive) or remove (negative)
     * @return true if operation succeeded, false if player not found or would go negative
     */
    @Override
    public boolean modifyUnallocatedPoints(@Nonnull UUID playerId, int delta) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Optional<PlayerData> existing = playerDataRepository.get(playerId);
        if (existing.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Cannot modify points - player data not found: %s", playerId);
            return false;
        }

        PlayerData data = existing.get();
        int newPoints = data.getUnallocatedPoints() + delta;

        if (newPoints < 0) {
            LOGGER.at(Level.SEVERE).log("Cannot modify points - would result in negative value: %d + %d = %d", data.getUnallocatedPoints(), delta, newPoints);
            return false;
        }

        PlayerData updated = data.withUnallocatedPoints(newPoints);
        playerDataRepository.save(updated);

        // Recalculate all stats (includes skill tree modifiers)
        recalculateStats(playerId);

        LOGGER.at(Level.INFO).log("Modified unallocated points for %s by %d (now %d)", playerId, delta, newPoints);
        return true;
    }

    /**
     * Sets a player's attribute to an absolute value.
     *
     * @param playerId The player's UUID
     * @param type The attribute type to set
     * @param value The new value (must be >= 0)
     * @return true if operation succeeded, false if player not found or invalid value
     */
    @Override
    public boolean setAttribute(@Nonnull UUID playerId, @Nonnull AttributeType type, int value) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (value < 0) {
            LOGGER.at(Level.SEVERE).log("Cannot set attribute to negative value: %d", value);
            return false;
        }

        Optional<PlayerData> existing = playerDataRepository.get(playerId);
        if (existing.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Cannot set attribute - player data not found: %s", playerId);
            return false;
        }

        PlayerData data = existing.get();
        PlayerData updated = setAttributeValue(data, type, value);

        playerDataRepository.save(updated);

        // Recalculate all stats (includes skill tree modifiers)
        recalculateStats(playerId);

        LOGGER.at(Level.INFO).log("Set %s for %s to %d", type.getDisplayName(), playerId, value);
        return true;
    }

    /**
     * Modifies a player's attribute by a delta value.
     *
     * @param playerId The player's UUID
     * @param type The attribute type to modify
     * @param delta The amount to add (positive) or remove (negative)
     * @return true if operation succeeded, false if player not found or would go negative
     */
    @Override
    public boolean modifyAttribute(@Nonnull UUID playerId, @Nonnull AttributeType type, int delta) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        Optional<PlayerData> existing = playerDataRepository.get(playerId);
        if (existing.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Cannot modify attribute - player data not found: %s", playerId);
            return false;
        }

        PlayerData data = existing.get();
        int currentValue = getAttributeValue(data, type);
        int newValue = currentValue + delta;

        if (newValue < 0) {
            LOGGER.at(Level.SEVERE).log("Cannot modify attribute - would result in negative value: %d + %d = %d", currentValue, delta, newValue);
            return false;
        }

        PlayerData updated = setAttributeValue(data, type, newValue);
        playerDataRepository.save(updated);

        // Recalculate all stats (includes skill tree modifiers)
        recalculateStats(playerId);

        LOGGER.at(Level.INFO).log("Modified %s for %s by %d (now %d)", type.getDisplayName(), playerId, delta, newValue);
        return true;
    }

    /**
     * Resets all attributes to 0 and refunds all allocated points.
     * Costs 50% of the total allocated points in attribute refund points
     * (half what individual deallocation would cost one-by-one).
     *
     * @param playerId The player's UUID
     * @return The number of refunded points, -1 if player not found, -2 if not enough refund points
     */
    @Override
    public int resetAllAttributes(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Optional<PlayerData> existing = playerDataRepository.get(playerId);
        if (existing.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Cannot reset attributes - player data not found: %s", playerId);
            return -1;
        }

        PlayerData data = existing.get();
        int totalAllocated = data.getTotalAllocatedPoints();
        if (totalAllocated == 0) {
            return 0;
        }

        // Full respec costs 50% of what one-by-one deallocation would cost
        // (one-by-one = 1 refund point per attribute point = totalAllocated refund points)
        int respecCost = (int) Math.ceil(totalAllocated * 0.5);
        if (data.getAttributeRefundPoints() < respecCost) {
            LOGGER.at(Level.INFO).log("Cannot reset attributes for %s - not enough refund points (has %d, needs %d)",
                playerId, data.getAttributeRefundPoints(), respecCost);
            return -2;
        }

        PlayerData updated = data.toBuilder()
            .fire(0)
            .water(0)
            .lightning(0)
            .earth(0)
            .wind(0)
            .voidAttr(0)
            .unallocatedPoints(data.getUnallocatedPoints() + totalAllocated)
            .attributeRefundPoints(data.getAttributeRefundPoints() - respecCost)
            .attributeRespecs(data.getAttributeRespecs() + 1)
            .build();

        playerDataRepository.save(updated);

        // Recalculate all stats (includes skill tree modifiers)
        recalculateStats(playerId);

        LOGGER.at(Level.INFO).log("Reset all attributes for %s - refunded %d points, cost %d refund points (had %d)",
            playerId, totalAllocated, respecCost, data.getAttributeRefundPoints());
        return totalAllocated;
    }

    /**
     * Admin override: resets all attributes without costing refund points.
     */
    @Override
    public int resetAllAttributesAdmin(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Optional<PlayerData> existing = playerDataRepository.get(playerId);
        if (existing.isEmpty()) {
            LOGGER.at(Level.SEVERE).log("Cannot reset attributes - player data not found: %s", playerId);
            return -1;
        }

        PlayerData data = existing.get();
        int refundedPoints = data.getTotalAllocatedPoints();
        if (refundedPoints == 0) {
            return 0;
        }

        PlayerData updated = data.toBuilder()
            .fire(0)
            .water(0)
            .lightning(0)
            .earth(0)
            .wind(0)
            .voidAttr(0)
            .unallocatedPoints(data.getUnallocatedPoints() + refundedPoints)
            .attributeRespecs(data.getAttributeRespecs() + 1)
            .build();

        playerDataRepository.save(updated);
        recalculateStats(playerId);

        LOGGER.at(Level.INFO).log("Admin reset all attributes for %s - refunded %d points (no cost)", playerId, refundedPoints);
        return refundedPoints;
    }

    // ==================== ECS Helper Methods ====================
    // These calculate values needed for ECS modifications.
    // The actual ECS application (CommandBuffer, EntityStatMap) is handled
    // by the main plugin class when it has access to the ECS world context.

    /**
     * Calculates the movement speed multiplier from percentage.
     *
     * <p>Formula: 1.0 + (moveSpeedPercent / 100.0)
     *
     * @param moveSpeedPercent The movement speed percentage (e.g., 50 = +50%)
     * @return The multiplier to apply (e.g., 1.5 for +50%)
     */
    public float calculateSpeedMultiplier(float moveSpeedPercent) {
        return 1.0f + (moveSpeedPercent / 100.0f);
    }

    // ==================== Getters ====================

    /** Gets the player data repository. */
    @Override
    @Nonnull
    public PlayerDataRepository getPlayerDataRepository() {
        return playerDataRepository;
    }

    /** Gets the attribute calculator. */
    @Nonnull
    public AttributeCalculator getCalculator() {
        return calculator;
    }

    /** Gets the config manager. */
    @Nonnull
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Sets the gear bonus provider for equipment stat integration.
     *
     * <p>This must be called after gear system initialization to enable
     * gear stat bonuses in the calculation pipeline. If not set, gear
     * bonuses are skipped.
     *
     * @param provider The gear bonus provider
     */
    public void setGearBonusProvider(@Nullable GearBonusProvider provider) {
        this.gearBonusProvider = provider;
        if (provider != null) {
            LOGGER.at(Level.INFO).log("Gear bonus provider set - equipment bonuses enabled");
        }
    }

    /**
     * Sets the conditional trigger system for ON_KILL, ON_CRIT, etc. effects.
     *
     * <p>When set, active conditional modifiers are applied during stat recalculation.
     * This enables skill tree effects like "On Kill: +20% attack speed for 4s".
     *
     * @param system The conditional trigger system, or null to disable
     */
    public void setConditionalTriggerSystem(@Nullable ConditionalTriggerSystem system) {
        this.conditionalTriggerSystem = system;
        if (system != null) {
            LOGGER.at(Level.INFO).log("Conditional trigger system set - ON_KILL/ON_CRIT effects enabled");
        }
    }

    /** @return The conditional trigger system, or null if not configured */
    @Nullable
    public ConditionalTriggerSystem getConditionalTriggerSystem() {
        return conditionalTriggerSystem;
    }

    /** @return The gear bonus provider, or null if not configured */
    @Nullable
    public GearBonusProvider getGearBonusProvider() {
        return gearBonusProvider;
    }

    /**
     * Sets the stats application callback for automatic ECS sync.
     *
     * <p>When set, the callback is invoked after every stat recalculation,
     * ensuring that computed stats are automatically applied to the player's
     * entity in Hytale's ECS.
     *
     * <p>This is the key bridge between the data layer (AttributeManager) and
     * the ECS layer (StatsApplicationSystem). Without this callback, stats
     * changes from commands, skill tree allocations, and other sources would
     * not be reflected in the game.
     *
     * @param callback The callback to invoke after stat recalculation, or null to disable
     */
    public void setStatsApplicationCallback(@Nullable StatsApplicationCallback callback) {
        this.statsApplicationCallback = callback;
        if (callback != null) {
            LOGGER.at(Level.INFO).log("Stats application callback registered - ECS sync enabled");
        }
    }

    /**
     * Callback interface for refreshing gear tooltips when stats change.
     *
     * <p>Implement this to update requirement display colors in item tooltips
     * when a player's stats change (e.g., after allocating attribute points).
     */
    public interface TooltipRefreshCallback {
        /**
         * Called after stats recalculation to refresh gear tooltips.
         *
         * @param playerId The player whose tooltips need refreshing
         */
        void refreshTooltips(@Nonnull UUID playerId);
    }

    /**
     * Sets the tooltip refresh callback for automatic tooltip updates.
     *
     * <p>When set, the callback is invoked after every stat recalculation,
     * ensuring that gear tooltip requirement colors are updated in real-time.
     *
     * <p>This is the key bridge between attribute changes and the gear tooltip
     * system. Without this callback, requirement display colors (green/red)
     * would not update when players allocate points, level up, or equip gear.
     *
     * @param callback The callback to invoke after stat recalculation, or null to disable
     */
    public void setTooltipRefreshCallback(@Nullable TooltipRefreshCallback callback) {
        this.tooltipRefreshCallback = callback;
        if (callback != null) {
            LOGGER.at(Level.INFO).log("Tooltip refresh callback registered - gear tooltip updates enabled");
        }
    }

    /**
     * The modifier ID used for RPG attribute bonuses in ECS.
     *
     * <p>Use this constant when applying modifiers to EntityStatMap.
     */
    public static final String RPG_MODIFIER_ID = "rpg_attribute_bonus";

    /**
     * The base sprint speed in Hytale (default: 1.65).
     *
     * <p>Movement speed bonuses are applied as multipliers to this value.
     */
    public static final float BASE_SPRINT_SPEED = 1.65f;

    // ==================== Cleanup ====================

    /**
     * Cleans up resources for a player when they disconnect.
     *
     * <p>This removes the per-player lock and stats version from their respective
     * maps to prevent memory leaks over time as players join and leave.
     *
     * <p>Should be called from the PlayerDisconnect event handler.
     *
     * @param playerId The player's UUID to clean up
     */
    public void cleanupPlayer(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        statsLocks.remove(playerId);
        statsVersions.remove(playerId);
    }

    // =========================================================================
    // REFUND POINTS
    // =========================================================================

    @Override
    public int getAttributeRefundPoints(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        return playerDataRepository.get(playerId)
            .map(PlayerData::getAttributeRefundPoints)
            .orElse(0);
    }

    @Override
    public boolean modifyAttributeRefundPoints(@Nonnull UUID playerId, int delta) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Optional<PlayerData> dataOpt = playerDataRepository.get(playerId);
        if (dataOpt.isEmpty()) return false;

        PlayerData data = dataOpt.get();
        int newValue = data.getAttributeRefundPoints() + delta;
        if (newValue < 0) {
            LOGGER.at(Level.WARNING).log("Cannot modify attribute refund points for %s: would go negative (%d + %d)",
                playerId, data.getAttributeRefundPoints(), delta);
            return false;
        }

        PlayerData updated = data.withAttributeRefundPoints(newValue);
        playerDataRepository.save(updated);
        return true;
    }
}
