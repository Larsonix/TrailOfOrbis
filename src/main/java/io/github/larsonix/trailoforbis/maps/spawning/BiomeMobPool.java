package io.github.larsonix.trailoforbis.maps.spawning;

import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Defines the pool of mobs that can spawn in a specific biome.
 *
 * <p>Each biome has two separate pools:
 * <ul>
 *   <li><b>Normal mobs:</b> Standard enemies forming the bulk of spawns</li>
 *   <li><b>Boss mobs:</b> Powerful enemies with ~1-10% spawn chance</li>
 * </ul>
 *
 * <p><b>Elite Modifier:</b> Elite is NOT a pool - it's a spawn-time modifier.
 * Any mob (normal or boss) can roll to become elite at spawn time, receiving
 * bonus stats. See {@link WeightedMob#ELITE_STAT_MULTIPLIER}.
 *
 * <h2>Weight System</h2>
 * <p>Weights determine relative spawn probability within a classification.
 * For example, in a pool with:
 * <pre>
 * trork_warrior: 30
 * trork_archer: 20
 * wolf: 15
 * </pre>
 * The trork_warrior has 30/(30+20+15) = 46% spawn chance.
 *
 * <h2>Level Restrictions</h2>
 * <p>Mobs can have minimum/maximum level requirements. A level 50 realm
 * will only consider mobs whose level range includes 50.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is immutable after construction. Use {@link Builder} to create instances.
 *
 * @see WeightedMob
 * @see RealmBiomeType
 */
public final class BiomeMobPool {

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final RealmBiomeType biome;
    private final List<WeightedMob> normalMobs;
    private final List<WeightedMob> bossMobs;
    private final int normalTotalWeight;
    private final int bossTotalWeight;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    private BiomeMobPool(
            @Nonnull RealmBiomeType biome,
            @Nonnull List<WeightedMob> normalMobs,
            @Nonnull List<WeightedMob> bossMobs) {

        this.biome = Objects.requireNonNull(biome, "biome cannot be null");
        this.normalMobs = List.copyOf(normalMobs);
        this.bossMobs = List.copyOf(bossMobs);

        this.normalTotalWeight = calculateTotalWeight(this.normalMobs);
        this.bossTotalWeight = calculateTotalWeight(this.bossMobs);
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a builder for constructing a BiomeMobPool.
     *
     * @param biome The biome this pool is for
     * @return A new builder instance
     */
    @Nonnull
    public static Builder builder(@Nonnull RealmBiomeType biome) {
        return new Builder(biome);
    }

    /**
     * Creates an empty pool for the given biome.
     *
     * @param biome The biome type
     * @return An empty BiomeMobPool
     */
    @Nonnull
    public static BiomeMobPool empty(@Nonnull RealmBiomeType biome) {
        return new BiomeMobPool(biome, List.of(), List.of());
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the biome this pool is associated with.
     *
     * @return The biome type
     */
    @Nonnull
    public RealmBiomeType getBiome() {
        return biome;
    }

    /**
     * Gets all normal mobs in this pool.
     *
     * @return Unmodifiable list of normal mobs
     */
    @Nonnull
    public List<WeightedMob> getNormalMobs() {
        return normalMobs;
    }

    /**
     * Gets all boss mobs in this pool.
     *
     * @return Unmodifiable list of boss mobs
     */
    @Nonnull
    public List<WeightedMob> getBossMobs() {
        return bossMobs;
    }

    /**
     * Gets the total weight of all normal mobs.
     *
     * @return Sum of normal mob weights
     */
    public int getNormalTotalWeight() {
        return normalTotalWeight;
    }

    /**
     * Gets the total weight of all boss mobs.
     *
     * @return Sum of boss mob weights
     */
    public int getBossTotalWeight() {
        return bossTotalWeight;
    }

    /**
     * Gets the total number of mob entries across all classifications.
     *
     * @return Total mob count
     */
    public int getTotalMobCount() {
        return normalMobs.size() + bossMobs.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SELECTION METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Selects a random normal mob based on weights.
     *
     * @param random Random source
     * @return Selected mob, or null if pool is empty
     */
    @Nullable
    public WeightedMob selectNormalMob(@Nonnull Random random) {
        return selectWeighted(normalMobs, normalTotalWeight, random);
    }

    /**
     * Selects a random boss mob based on weights.
     *
     * @param random Random source
     * @return Selected mob, or null if pool is empty
     */
    @Nullable
    public WeightedMob selectBossMob(@Nonnull Random random) {
        return selectWeighted(bossMobs, bossTotalWeight, random);
    }

    /**
     * Selects a random normal mob that can spawn at the given level.
     *
     * @param level The realm level
     * @param random Random source
     * @return Selected mob, or null if no eligible mobs
     */
    @Nullable
    public WeightedMob selectNormalMobForLevel(int level, @Nonnull Random random) {
        return selectWeightedForLevel(normalMobs, level, random);
    }

    /**
     * Selects a random boss mob that can spawn at the given level.
     *
     * @param level The realm level
     * @param random Random source
     * @return Selected mob, or null if no eligible mobs
     */
    @Nullable
    public WeightedMob selectBossMobForLevel(int level, @Nonnull Random random) {
        return selectWeightedForLevel(bossMobs, level, random);
    }

    /**
     * Selects a mob of the specified classification.
     *
     * @param classification The mob classification to select from
     * @param random Random source
     * @return Selected mob, or null if pool is empty
     */
    @Nullable
    public WeightedMob selectMob(@Nonnull MobClassification classification, @Nonnull Random random) {
        return switch (classification) {
            case NORMAL -> selectNormalMob(random);
            case BOSS -> selectBossMob(random);
        };
    }

    /**
     * Selects a mob of the specified classification that can spawn at the level.
     *
     * @param classification The mob classification
     * @param level The realm level
     * @param random Random source
     * @return Selected mob, or null if no eligible mobs
     */
    @Nullable
    public WeightedMob selectMobForLevel(
            @Nonnull MobClassification classification,
            int level,
            @Nonnull Random random) {
        return switch (classification) {
            case NORMAL -> selectNormalMobForLevel(level, random);
            case BOSS -> selectBossMobForLevel(level, random);
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if this pool is empty (no mobs in any classification).
     *
     * @return true if all pools are empty
     */
    public boolean isEmpty() {
        return normalMobs.isEmpty() && bossMobs.isEmpty();
    }

    /**
     * Checks if this pool has any normal mobs.
     *
     * @return true if normal pool is not empty
     */
    public boolean hasNormalMobs() {
        return !normalMobs.isEmpty();
    }

    /**
     * Checks if this pool has any boss mobs.
     *
     * @return true if boss pool is not empty
     */
    public boolean hasBossMobs() {
        return !bossMobs.isEmpty();
    }

    /**
     * Gets all mob type IDs from all classifications.
     *
     * @return Set of all mob type IDs
     */
    @Nonnull
    public Set<String> getAllMobTypeIds() {
        Set<String> ids = new HashSet<>();
        normalMobs.forEach(m -> ids.add(m.mobTypeId()));
        bossMobs.forEach(m -> ids.add(m.mobTypeId()));
        return Collections.unmodifiableSet(ids);
    }

    /**
     * Gets all mobs that can spawn at the given level.
     *
     * @param level The realm level
     * @return List of eligible mobs from all classifications
     */
    @Nonnull
    public List<WeightedMob> getMobsForLevel(int level) {
        List<WeightedMob> eligible = new ArrayList<>();
        normalMobs.stream().filter(m -> m.canSpawnAtLevel(level)).forEach(eligible::add);
        bossMobs.stream().filter(m -> m.canSpawnAtLevel(level)).forEach(eligible::add);
        return Collections.unmodifiableList(eligible);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL METHODS
    // ═══════════════════════════════════════════════════════════════════

    private static int calculateTotalWeight(List<WeightedMob> mobs) {
        return mobs.stream().mapToInt(WeightedMob::weight).sum();
    }

    @Nullable
    private static WeightedMob selectWeighted(
            List<WeightedMob> mobs,
            int totalWeight,
            Random random) {

        if (mobs.isEmpty() || totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (WeightedMob mob : mobs) {
            cumulative += mob.weight();
            if (roll < cumulative) {
                return mob;
            }
        }

        // Fallback (shouldn't happen)
        return mobs.getLast();
    }

    @Nullable
    private static WeightedMob selectWeightedForLevel(
            List<WeightedMob> mobs,
            int level,
            Random random) {

        // Filter to eligible mobs
        List<WeightedMob> eligible = mobs.stream()
            .filter(m -> m.canSpawnAtLevel(level))
            .toList();

        if (eligible.isEmpty()) {
            return null;
        }

        int totalWeight = calculateTotalWeight(eligible);
        return selectWeighted(eligible, totalWeight, random);
    }

    // ═══════════════════════════════════════════════════════════════════
    // OBJECT METHODS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public String toString() {
        return String.format("BiomeMobPool[%s: %d normal, %d boss]",
            biome.name(), normalMobs.size(), bossMobs.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BiomeMobPool that)) return false;
        return biome == that.biome &&
               normalMobs.equals(that.normalMobs) &&
               bossMobs.equals(that.bossMobs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(biome, normalMobs, bossMobs);
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builder for constructing BiomeMobPool instances.
     */
    public static final class Builder {
        private final RealmBiomeType biome;
        private final List<WeightedMob> normalMobs = new ArrayList<>();
        private final List<WeightedMob> bossMobs = new ArrayList<>();

        private Builder(@Nonnull RealmBiomeType biome) {
            this.biome = Objects.requireNonNull(biome, "biome cannot be null");
        }

        /**
         * Adds a mob to the appropriate pool based on its classification.
         *
         * @param mob The mob to add
         * @return This builder for chaining
         */
        @Nonnull
        public Builder add(@Nonnull WeightedMob mob) {
            Objects.requireNonNull(mob, "mob cannot be null");
            switch (mob.classification()) {
                case NORMAL -> normalMobs.add(mob);
                case BOSS -> bossMobs.add(mob);
            }
            return this;
        }

        /**
         * Adds a normal mob with the specified ID and weight.
         *
         * @param mobTypeId The entity type identifier
         * @param weight Spawn weight
         * @return This builder for chaining
         */
        @Nonnull
        public Builder addNormal(@Nonnull String mobTypeId, int weight) {
            normalMobs.add(WeightedMob.normal(mobTypeId, weight));
            return this;
        }

        /**
         * Adds a normal mob with level restrictions.
         *
         * @param mobTypeId The entity type identifier
         * @param weight Spawn weight
         * @param minLevel Minimum spawn level
         * @param maxLevel Maximum spawn level (-1 for unlimited)
         * @return This builder for chaining
         */
        @Nonnull
        public Builder addNormal(@Nonnull String mobTypeId, int weight, int minLevel, int maxLevel) {
            normalMobs.add(WeightedMob.normal(mobTypeId, weight, minLevel, maxLevel));
            return this;
        }

        /**
         * Adds a boss mob with the specified ID and weight.
         *
         * @param mobTypeId The entity type identifier
         * @param weight Spawn weight
         * @return This builder for chaining
         */
        @Nonnull
        public Builder addBoss(@Nonnull String mobTypeId, int weight) {
            bossMobs.add(WeightedMob.boss(mobTypeId, weight));
            return this;
        }

        /**
         * Adds a boss mob with level restrictions.
         *
         * @param mobTypeId The entity type identifier
         * @param weight Spawn weight
         * @param minLevel Minimum spawn level
         * @param maxLevel Maximum spawn level (-1 for unlimited)
         * @return This builder for chaining
         */
        @Nonnull
        public Builder addBoss(@Nonnull String mobTypeId, int weight, int minLevel, int maxLevel) {
            bossMobs.add(WeightedMob.boss(mobTypeId, weight, minLevel, maxLevel));
            return this;
        }

        /**
         * Adds all mobs from another pool.
         *
         * @param other The pool to copy from
         * @return This builder for chaining
         */
        @Nonnull
        public Builder addAll(@Nonnull BiomeMobPool other) {
            Objects.requireNonNull(other, "other cannot be null");
            normalMobs.addAll(other.normalMobs);
            bossMobs.addAll(other.bossMobs);
            return this;
        }

        /**
         * Builds the BiomeMobPool.
         *
         * @return A new immutable BiomeMobPool
         */
        @Nonnull
        public BiomeMobPool build() {
            return new BiomeMobPool(biome, normalMobs, bossMobs);
        }
    }
}
