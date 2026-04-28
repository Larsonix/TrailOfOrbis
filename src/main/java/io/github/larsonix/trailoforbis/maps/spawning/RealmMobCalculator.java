package io.github.larsonix.trailoforbis.maps.spawning;

import io.github.larsonix.trailoforbis.maps.config.MobPoolConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Calculates mob spawning parameters for realm instances.
 *
 * <p>This calculator determines:
 * <ul>
 *   <li>Total monster count based on size, level, and modifiers</li>
 *   <li>Distribution between normal and boss mobs</li>
 *   <li>Spawn wave sizing and timing</li>
 *   <li>Level scaling for mob stats</li>
 *   <li>Elite chance for spawn-time modifier application</li>
 * </ul>
 *
 * <h2>Calculation Formulas</h2>
 *
 * <h3>Total Mob Count</h3>
 * <pre>
 * baseCount = size.baseMonsterCount
 * sizeMultiplier = size.monsterMultiplier
 * levelMultiplier = 1.0 + (level * 0.02)
 * modifierMultiplier = 1.0 + (extraMonsterPercent / 100)
 *
 * totalCount = baseCount * sizeMultiplier * levelMultiplier * modifierMultiplier
 * </pre>
 *
 * <h3>Boss Chance</h3>
 * <pre>
 * bossChance = baseBossChance + (level * bossChancePerLevel)
 * normalCount = totalCount - bossCount
 * </pre>
 *
 * <h3>Elite Modifier (per-mob roll at spawn time)</h3>
 * <pre>
 * eliteChance = baseEliteChance + (level * eliteChancePerLevel)
 * // Applied independently to each mob (normal or boss)
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * RealmMobCalculator calc = new RealmMobCalculator(mobPoolConfig);
 * SpawnDistribution dist = calc.calculate(mapData);
 *
 * int normalCount = dist.normalCount();
 * int bossCount = dist.bossCount();
 * double eliteChance = dist.eliteChance(); // For per-mob roll
 * }</pre>
 *
 * @see MobPoolConfig
 * @see RealmMapData
 */
public class RealmMobCalculator {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Minimum monsters in any realm, regardless of calculations.
     */
    public static final int MINIMUM_MONSTERS = 5;

    /**
     * Maximum monsters to prevent performance issues.
     */
    public static final int MAXIMUM_MONSTERS = 500;

    /**
     * Level factor for monster count scaling.
     */
    private static final float LEVEL_MULTIPLIER_FACTOR = 0.02f;

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final MobPoolConfig config;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new calculator with the specified configuration.
     *
     * @param config The mob pool configuration
     */
    public RealmMobCalculator(@Nonnull MobPoolConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Creates a calculator with default configuration.
     *
     * @return A new RealmMobCalculator with defaults
     */
    @Nonnull
    public static RealmMobCalculator withDefaults() {
        return new RealmMobCalculator(new MobPoolConfig());
    }

    // ═══════════════════════════════════════════════════════════════════
    // CALCULATION METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the complete spawn distribution for a realm.
     *
     * <p>Note: Elite is now a spawn-time modifier, not a spawn count. The
     * eliteChance is stored in the distribution for use during per-mob spawn
     * rolls, but there is no eliteCount in the distribution.
     *
     * @param mapData The realm map data
     * @return The calculated spawn distribution
     */
    @Nonnull
    public SpawnDistribution calculate(@Nonnull RealmMapData mapData) {
        Objects.requireNonNull(mapData, "mapData cannot be null");

        int totalCount = calculateTotalMobCount(mapData);
        int guaranteedBosses = mapData.size().getGuaranteedBosses();

        // Calculate chances for this level
        int level = mapData.level();
        double eliteChance = config.calculateEliteChance(level);
        double bossChance = config.calculateBossChance(level);

        // Apply ELITE_CHANCE modifier bonus
        int eliteBonus = mapData.getEliteChanceBonus();
        if (eliteBonus > 0) {
            eliteChance += eliteBonus / 100.0;
            // Cap at max elite chance
            eliteChance = Math.min(eliteChance, config.getMaxEliteChance());
        }

        // Calculate distribution (elite is now per-mob roll, not a count)
        // Guaranteed bosses are separate from chance-based bosses
        int randomBosses = (int) Math.floor((totalCount - guaranteedBosses) * bossChance);
        int bossCount = guaranteedBosses + randomBosses;
        int normalCount = totalCount - bossCount;

        // Ensure minimums
        if (normalCount < 1 && totalCount > 0) {
            normalCount = 1;
            if (bossCount > guaranteedBosses) bossCount--;
        }

        return new SpawnDistribution(
            totalCount,
            normalCount,
            bossCount,
            guaranteedBosses,
            eliteChance,
            bossChance
        );
    }

    /**
     * Calculates just the total mob count for a realm.
     *
     * @param mapData The realm map data
     * @return Total mob count
     */
    public int calculateTotalMobCount(@Nonnull RealmMapData mapData) {
        Objects.requireNonNull(mapData, "mapData cannot be null");

        RealmLayoutSize size = mapData.size();
        int level = mapData.level();
        int extraMonsterPercent = mapData.getExtraMonsterPercent();

        return calculateTotalMobCount(size, level, extraMonsterPercent);
    }

    /**
     * Calculates total mob count from individual parameters.
     *
     * @param size Arena size
     * @param level Realm level
     * @param extraMonsterPercent Additional monster percentage from modifiers
     * @return Total mob count
     */
    public int calculateTotalMobCount(
            @Nonnull RealmLayoutSize size,
            int level,
            int extraMonsterPercent) {

        Objects.requireNonNull(size, "size cannot be null");

        int baseCount = size.getBaseMonsterCount();
        float sizeMultiplier = size.getMonsterMultiplier();
        float levelMultiplier = 1.0f + (level * LEVEL_MULTIPLIER_FACTOR);
        float modifierMultiplier = 1.0f + (extraMonsterPercent / 100.0f);

        // Apply global spawn count multiplier from config
        float globalMultiplier = config.getSpawnCountMultiplier();

        int totalCount = Math.round(baseCount * sizeMultiplier * levelMultiplier
                                    * modifierMultiplier * globalMultiplier);

        // Clamp to valid range
        return Math.max(MINIMUM_MONSTERS, Math.min(totalCount, MAXIMUM_MONSTERS));
    }

    /**
     * Calculates the elite spawn chance for a given level.
     *
     * @param level The realm level
     * @return Elite spawn chance (0.0 to maxEliteChance)
     */
    public double calculateEliteChance(int level) {
        return config.calculateEliteChance(level);
    }

    /**
     * Calculates the boss spawn chance for a given level.
     *
     * @param level The realm level
     * @return Boss spawn chance (0.0 to maxBossChance)
     */
    public double calculateBossChance(int level) {
        return config.calculateBossChance(level);
    }

    // ═══════════════════════════════════════════════════════════════════
    // WAVE CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates spawn wave parameters.
     *
     * @param distribution The spawn distribution
     * @return Wave parameters
     */
    @Nonnull
    public WaveParameters calculateWaves(@Nonnull SpawnDistribution distribution) {
        Objects.requireNonNull(distribution, "distribution cannot be null");

        int batchSize = config.getSpawnBatchSize();
        int intervalTicks = config.getSpawnIntervalTicks();

        // Initial wave spawns more mobs to populate the arena quickly
        int initialWaveSize = Math.min(distribution.totalCount() / 4, batchSize * 3);
        initialWaveSize = Math.max(initialWaveSize, batchSize);

        // Calculate number of waves needed
        int remainingAfterInitial = distribution.totalCount() - initialWaveSize;
        int subsequentWaves = (int) Math.ceil((double) remainingAfterInitial / batchSize);

        return new WaveParameters(
            initialWaveSize,
            batchSize,
            subsequentWaves,
            intervalTicks
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // STAT SCALING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the health multiplier for mobs at the given level.
     *
     * <p>Formula: baseMultiplier * eliteMultiplier * (1 + level * 0.05)
     *
     * @param level The realm level
     * @param classification The mob classification
     * @param isElite Whether this mob is elite (spawn-time modifier)
     * @param mapData The realm map data (for modifiers)
     * @return Health multiplier
     */
    public float calculateHealthMultiplier(
            int level,
            @Nonnull MobClassification classification,
            boolean isElite,
            @Nonnull RealmMapData mapData) {

        float baseMultiplier = classification.getHealthMultiplier();
        float eliteMultiplier = isElite ? WeightedMob.ELITE_HEALTH_MULTIPLIER : 1.0f;
        float levelScaling = 1.0f + (level * 0.05f);
        float modifierMultiplier = mapData.getMonsterHealthMultiplier();

        return baseMultiplier * eliteMultiplier * levelScaling * modifierMultiplier;
    }

    /**
     * Calculates the damage multiplier for mobs at the given level.
     *
     * <p>Formula: baseMultiplier * eliteMultiplier * (1 + level * 0.03)
     *
     * @param level The realm level
     * @param classification The mob classification
     * @param isElite Whether this mob is elite (spawn-time modifier)
     * @param mapData The realm map data (for modifiers)
     * @return Damage multiplier
     */
    public float calculateDamageMultiplier(
            int level,
            @Nonnull MobClassification classification,
            boolean isElite,
            @Nonnull RealmMapData mapData) {

        float baseMultiplier = classification.getStatMultiplier();
        float eliteMultiplier = isElite ? WeightedMob.ELITE_STAT_MULTIPLIER : 1.0f;
        float levelScaling = 1.0f + (level * 0.03f);
        float modifierMultiplier = mapData.getMonsterDamageMultiplier();

        return baseMultiplier * eliteMultiplier * levelScaling * modifierMultiplier;
    }

    /**
     * Calculates base XP reward for killing a mob.
     *
     * <p>Formula: baseXp * classification.xpMultiplier * eliteMultiplier * (1 + level * 0.02)
     *
     * @param level The realm level
     * @param classification The mob classification
     * @param isElite Whether this mob is elite (spawn-time modifier)
     * @param baseXp Base XP value for the mob type
     * @return Calculated XP reward
     */
    public int calculateXpReward(
            int level,
            @Nonnull MobClassification classification,
            boolean isElite,
            int baseXp) {
        float classMultiplier = classification.getXpMultiplier();
        float eliteMultiplier = isElite ? WeightedMob.ELITE_XP_MULTIPLIER : 1.0f;
        float levelScaling = 1.0f + (level * 0.02f);
        return Math.round(baseXp * classMultiplier * eliteMultiplier * levelScaling);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the underlying configuration.
     *
     * @return The mob pool configuration
     */
    @Nonnull
    public MobPoolConfig getConfig() {
        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED RECORDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents the calculated spawn distribution for a realm.
     *
     * <p>Note: Elite is a spawn-time modifier, not a spawn count. The eliteChance
     * is stored here for use during per-mob spawn rolls, but there is no eliteCount.
     * Any mob (normal or boss) can roll to become elite at spawn time.
     *
     * @param totalCount Total number of mobs to spawn
     * @param normalCount Number of normal mobs
     * @param bossCount Number of boss mobs (includes guaranteed)
     * @param guaranteedBosses Number of guaranteed boss spawns
     * @param eliteChance The elite chance for per-mob rolls
     * @param bossChance The boss spawn chance used
     */
    public record SpawnDistribution(
        int totalCount,
        int normalCount,
        int bossCount,
        int guaranteedBosses,
        double eliteChance,
        double bossChance
    ) {
        /**
         * Creates a validated spawn distribution.
         */
        public SpawnDistribution {
            if (totalCount < 0) {
                throw new IllegalArgumentException("totalCount cannot be negative");
            }
            if (normalCount < 0 || bossCount < 0) {
                throw new IllegalArgumentException("Counts cannot be negative");
            }
            if (normalCount + bossCount != totalCount) {
                throw new IllegalArgumentException(
                    "Counts must sum to totalCount: " +
                    normalCount + " + " + bossCount +
                    " != " + totalCount);
            }
        }

        /**
         * Gets the percentage of normal mobs.
         *
         * @return Normal percentage (0.0 to 1.0)
         */
        public double normalPercentage() {
            return totalCount > 0 ? (double) normalCount / totalCount : 0.0;
        }

        /**
         * Gets the percentage of boss mobs.
         *
         * @return Boss percentage (0.0 to 1.0)
         */
        public double bossPercentage() {
            return totalCount > 0 ? (double) bossCount / totalCount : 0.0;
        }

        /**
         * Gets the number of chance-based bosses (not guaranteed).
         *
         * @return Random boss count
         */
        public int randomBossCount() {
            return bossCount - guaranteedBosses;
        }

        @Override
        public String toString() {
            return String.format(
                "SpawnDistribution[total=%d, normal=%d (%.1f%%), boss=%d (%.1f%%, %d guaranteed), eliteChance=%.1f%%]",
                totalCount,
                normalCount, normalPercentage() * 100,
                bossCount, bossPercentage() * 100, guaranteedBosses,
                eliteChance * 100
            );
        }
    }

    /**
     * Parameters for spawn wave timing.
     *
     * @param initialWaveSize Number of mobs in the first wave
     * @param subsequentWaveSize Number of mobs in subsequent waves
     * @param waveCount Total number of waves after initial
     * @param waveIntervalTicks Ticks between waves
     */
    public record WaveParameters(
        int initialWaveSize,
        int subsequentWaveSize,
        int waveCount,
        int waveIntervalTicks
    ) {
        /**
         * Creates validated wave parameters.
         */
        public WaveParameters {
            if (initialWaveSize < 1) {
                throw new IllegalArgumentException("initialWaveSize must be positive");
            }
            if (subsequentWaveSize < 1) {
                throw new IllegalArgumentException("subsequentWaveSize must be positive");
            }
            if (waveCount < 0) {
                throw new IllegalArgumentException("waveCount cannot be negative");
            }
            if (waveIntervalTicks < 1) {
                throw new IllegalArgumentException("waveIntervalTicks must be positive");
            }
        }

        /**
         * Gets the wave interval in seconds.
         *
         * @return Wave interval in seconds (assuming 20 ticks/second)
         */
        public float waveIntervalSeconds() {
            return waveIntervalTicks / 20.0f;
        }

        /**
         * Gets the total spawn duration in ticks.
         *
         * @return Total duration in ticks
         */
        public int totalDurationTicks() {
            return waveCount * waveIntervalTicks;
        }

        /**
         * Gets the total spawn duration in seconds.
         *
         * @return Total duration in seconds
         */
        public float totalDurationSeconds() {
            return totalDurationTicks() / 20.0f;
        }

        @Override
        public String toString() {
            return String.format(
                "WaveParameters[initial=%d, subsequent=%d, waves=%d, interval=%.1fs]",
                initialWaveSize, subsequentWaveSize, waveCount, waveIntervalSeconds()
            );
        }
    }
}
