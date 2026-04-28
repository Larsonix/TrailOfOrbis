package io.github.larsonix.trailoforbis.maps.config;

import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Configuration for mob spawning pools in realms.
 *
 * <p>Loaded from {@code config/realm-mobs.yml}.
 *
 * <p>Each biome has a pool of mobs that can spawn, with:
 * <ul>
 *   <li>Regular mobs (standard enemies)</li>
 *   <li>Boss mobs (powerful unique enemies)</li>
 * </ul>
 *
 * <p><b>Elite Modifier:</b> Elite is NOT a pool - it's a spawn-time modifier.
 * Any mob (normal or boss) can roll to become elite at spawn time, receiving
 * bonus stats based on the elite chance settings.
 */
public class MobPoolConfig {

    // ═══════════════════════════════════════════════════════════════════
    // MOB POOLS BY BIOME
    // ═══════════════════════════════════════════════════════════════════

    private Map<RealmBiomeType, BiomeMobPool> biomePools = new EnumMap<>(RealmBiomeType.class);

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    private double baseEliteChance = 0.05; // 5% base
    private double eliteChancePerLevel = 0.0001; // +0.01% per level
    private double maxEliteChance = 0.25; // 25% max

    private double baseBossChance = 0.03; // 3% base (matches realm-mobs.yml)
    private double bossChancePerLevel = 0.0005; // +0.05% per level
    private double maxBossChance = 0.10; // 10% max

    // Visual scale multipliers for special mobs (1.0 = normal, stacks for elite boss)
    private float eliteScale = 1.15f;
    private float bossScale = 1.35f;

    private int minSpawnDistance = 20; // blocks from player
    private int maxSpawnDistance = 50;
    private int spawnBatchSize = 5;
    private int spawnIntervalTicks = 100; // 5 seconds

    // Spawn exclusion zone around player spawn point (entry location)
    // Different from minSpawnDistance which is distance from active players during gameplay
    private double spawnExclusionRadius = 20.0; // blocks from spawn point

    // Player count scaling - mobs get stronger with more players
    private float playerScalingMultiplier = 1.2f; // x1.2 per additional player
    private boolean playerScalingAffectsDamage = true; // Also scale damage, not just health

    // Global spawn count multiplier (1.0 = no change)
    private float spawnCountMultiplier = 1.0f;

    // Level fraction for AI-spawned minions (wolves summoned by orcs, etc.)
    private float minionLevelFraction = 0.75f;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    public MobPoolConfig() {
        initializeDefaults();
    }

    private void initializeDefaults() {
        // Initialize default mob pools for each biome
        for (RealmBiomeType biome : RealmBiomeType.values()) {
            biomePools.put(biome, createDefaultPool(biome));
        }
    }

    private BiomeMobPool createDefaultPool(RealmBiomeType biome) {
        BiomeMobPool pool = new BiomeMobPool();

        // Set default mobs based on biome theme
        // NPC type IDs must match Hytale's registered NPCs (case-sensitive)
        // IDs from /Assets/Server/NPC/Roles/*.json
        // Note: Elite is a spawn-time modifier, not a separate pool - former "elite" mobs
        // are now in the regular pool and can still roll to become elite at spawn time
        // Defaults mirror realm-mobs.yml — used as fallback if YAML loading fails
        switch (biome) {
            case FOREST -> {
                pool.addRegularMob("Trork_Brawler", 20);
                pool.addRegularMob("Trork_Warrior", 25);
                pool.addRegularMob("Trork_Guard", 20);
                pool.addRegularMob("Trork_Hunter", 20);
                pool.addRegularMob("Trork_Shaman", 10);
                pool.addRegularMob("Trork_Mauler", 5);
                pool.addBossMob("Trork_Chieftain", 10);
            }
            case DESERT -> {
                pool.addRegularMob("Skeleton_Burnt_Knight", 20);
                pool.addRegularMob("Skeleton_Burnt_Gunner", 15);
                pool.addRegularMob("Skeleton_Sand_Archer", 15);
                pool.addRegularMob("Skeleton_Sand_Mage", 10);
                pool.addRegularMob("Scorpion", 15);
                pool.addRegularMob("Lizard_Sand", 10);
                pool.addRegularMob("Scarak", 10);
                pool.addRegularMob("Skeleton_Burnt_Wizard", 5);
                pool.addBossMob("Skeleton_Burnt_Praetorian", 10);
            }
            case VOLCANO -> {
                pool.addRegularMob("Golem_Firesteel", 15);
                pool.addRegularMob("Skeleton_Burnt_Knight", 20);
                pool.addRegularMob("Skeleton_Burnt_Gunner", 20);
                pool.addRegularMob("Skeleton_Burnt_Wizard", 15);
                pool.addRegularMob("Emberwulf", 15);
                pool.addRegularMob("Toad_Rhino_Magma", 10);
                pool.addRegularMob("Slug_Magma", 5);
                pool.addBossMob("Dragon_Fire", 10);
            }
            case TUNDRA -> {
                pool.addRegularMob("Outlander_Marauder", 20);
                pool.addRegularMob("Outlander_Hunter", 18);
                pool.addRegularMob("Outlander_Berserker", 17);
                pool.addRegularMob("Outlander_Brute", 15);
                pool.addRegularMob("Outlander_Stalker", 12);
                pool.addRegularMob("Bear_Polar", 10);
                pool.addRegularMob("Wolf_White", 8);
                pool.addBossMob("Yeti", 10);
            }
            case SWAMP -> {
                pool.addRegularMob("Fen_Stalker", 20);
                pool.addRegularMob("Ghoul", 20);
                pool.addRegularMob("Wraith", 20);
                pool.addRegularMob("Crocodile", 15);
                pool.addRegularMob("Snake_Marsh", 15);
                pool.addRegularMob("Toad_Rhino", 10);
                pool.addBossMob("Wraith_Lantern", 10);
            }
            case MOUNTAINS -> {
                pool.addRegularMob("Goblin_Ogre", 15);
                pool.addRegularMob("Goblin_Scrapper", 25);
                pool.addRegularMob("Goblin_Lobber", 20);
                pool.addRegularMob("Goblin_Thief", 15);
                pool.addRegularMob("Goblin_Miner", 15);
                pool.addRegularMob("Bear_Grizzly", 10);
                pool.addBossMob("Goblin_Duke", 10);
            }
            case BEACH -> {
                pool.addRegularMob("Skeleton_Pirate_Captain", 15);
                pool.addRegularMob("Skeleton_Pirate_Striker", 25);
                pool.addRegularMob("Skeleton_Pirate_Gunner", 25);
                pool.addRegularMob("Crocodile", 15);
                pool.addRegularMob("Snake_Cobra", 10);
                pool.addRegularMob("Scorpion", 10);
                pool.addBossMob("Scarak_Broodmother", 10);
            }
            case JUNGLE -> {
                pool.addRegularMob("Snapdragon", 15);
                pool.addRegularMob("Kweebec_Razorleaf", 20);
                pool.addRegularMob("Bramblekin_Shaman", 15);
                pool.addRegularMob("Bramblekin", 20);
                pool.addRegularMob("Spider", 15);
                pool.addRegularMob("Snake_Cobra", 10);
                pool.addRegularMob("Raptor_Cave", 5);
                pool.addBossMob("Hedera", 10);
            }
            case CAVERNS -> {
                pool.addRegularMob("Scarak_Fighter", 25);
                pool.addRegularMob("Scarak_Defender", 20);
                pool.addRegularMob("Scarak_Seeker", 20);
                pool.addRegularMob("Scarak_Louse", 20);
                pool.addRegularMob("Scarak_Fighter_Royal_Guard", 15);
                pool.addBossMob("Scarak_Broodmother", 10);
            }
            case FROZEN_CRYPTS -> {
                pool.addRegularMob("Skeleton_Frost_Knight", 16);
                pool.addRegularMob("Skeleton_Frost_Fighter", 14);
                pool.addRegularMob("Skeleton_Frost_Soldier", 13);
                pool.addRegularMob("Skeleton_Frost_Scout", 13);
                pool.addRegularMob("Skeleton_Frost_Archer", 13);
                pool.addRegularMob("Skeleton_Frost_Ranger", 11);
                pool.addRegularMob("Skeleton_Frost_Mage", 10);
                pool.addRegularMob("Skeleton_Frost_Archmage", 10);
                pool.addBossMob("Golem_Crystal_Frost", 10);
            }
            case SAND_TOMBS -> {
                pool.addRegularMob("Skeleton_Sand_Archer", 15);
                pool.addRegularMob("Skeleton_Sand_Guard", 14);
                pool.addRegularMob("Skeleton_Sand_Assassin", 13);
                pool.addRegularMob("Skeleton_Sand_Ranger", 13);
                pool.addRegularMob("Skeleton_Sand_Scout", 12);
                pool.addRegularMob("Skeleton_Sand_Soldier", 12);
                pool.addRegularMob("Skeleton_Sand_Mage", 11);
                pool.addRegularMob("Skeleton_Sand_Archmage", 10);
                pool.addBossMob("Golem_Crystal_Sand", 10);
            }
            case VOID -> {
                pool.addRegularMob("Golem_Guardian_Void", 10);
                pool.addRegularMob("Crawler_Void", 25);
                pool.addRegularMob("Spectre_Void", 25);
                pool.addRegularMob("Eye_Void", 15);
                pool.addRegularMob("Spawn_Void", 15);
                pool.addRegularMob("Larva_Void", 10);
                pool.addBossMob("Golem_Guardian_Void", 10);
            }
            case CORRUPTED -> {
                pool.addRegularMob("Shadow_Knight", 15);
                pool.addRegularMob("Werewolf", 20);
                pool.addRegularMob("Outlander_Sorcerer", 20);
                pool.addRegularMob("Outlander_Cultist", 20);
                pool.addRegularMob("Outlander_Priest", 15);
                pool.addRegularMob("Ghoul", 10);
                pool.addBossMob("Risen_Knight", 10);
            }
        }

        return pool;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the mob pool for a biome.
     *
     * @param biome The biome type
     * @return The mob pool configuration
     */
    @Nonnull
    public BiomeMobPool getMobPool(@Nonnull RealmBiomeType biome) {
        return biomePools.getOrDefault(biome, new BiomeMobPool());
    }

    public double getBaseEliteChance() {
        return baseEliteChance;
    }

    public double getEliteChancePerLevel() {
        return eliteChancePerLevel;
    }

    public double getMaxEliteChance() {
        return maxEliteChance;
    }

    public double getBaseBossChance() {
        return baseBossChance;
    }

    public double getBossChancePerLevel() {
        return bossChancePerLevel;
    }

    public double getMaxBossChance() {
        return maxBossChance;
    }

    public float getEliteScale() {
        return eliteScale;
    }

    public float getBossScale() {
        return bossScale;
    }

    public int getMinSpawnDistance() {
        return minSpawnDistance;
    }

    public int getMaxSpawnDistance() {
        return maxSpawnDistance;
    }

    public int getSpawnBatchSize() {
        return spawnBatchSize;
    }

    public int getSpawnIntervalTicks() {
        return spawnIntervalTicks;
    }

    /**
     * Gets the spawn exclusion radius around the player spawn point.
     *
     * <p>Mobs cannot spawn within this distance of the realm entry/respawn location.
     * This is different from {@link #getMinSpawnDistance()} which controls distance
     * from active players during reinforcement waves.
     *
     * @return The exclusion radius in blocks (default 20.0)
     */
    public double getSpawnExclusionRadius() {
        return spawnExclusionRadius;
    }

    /**
     * Gets the player scaling multiplier.
     * <p>Mobs get this multiplier applied per additional player in the realm.
     * With 2 players, mobs have x1.2 stats; with 3 players, x1.44 (1.2^2), etc.
     *
     * @return The player scaling multiplier (default 1.2)
     */
    public float getPlayerScalingMultiplier() {
        return playerScalingMultiplier;
    }

    /**
     * Gets whether player scaling affects damage.
     * <p>If true, both health and damage are scaled. If false, only health is scaled.
     *
     * @return true if damage should be scaled with player count
     */
    public boolean isPlayerScalingAffectsDamage() {
        return playerScalingAffectsDamage;
    }

    /**
     * Gets the global spawn count multiplier.
     *
     * <p>This multiplier is applied to the total mob count for all realms,
     * after all other calculations (size, level, modifiers). It allows
     * server admins to globally scale realm mob density.
     *
     * @return The spawn count multiplier (default 1.0 = no change)
     */
    public float getSpawnCountMultiplier() {
        return spawnCountMultiplier;
    }

    /**
     * Gets the level fraction for AI-spawned minions.
     *
     * <p>When a realm mob's AI spawns a companion (e.g., a wolf summoned by an orc),
     * the minion's level is {@code realmLevel × minionLevelFraction}. This prevents
     * minions from being as strong as the primary realm mobs and ensures their loot
     * drops at a reduced level.
     *
     * @return The minion level fraction (default 0.75)
     */
    public float getMinionLevelFraction() {
        return minionLevelFraction;
    }

    /**
     * Calculates the elite spawn chance for a given level.
     *
     * @param level The realm level
     * @return Elite spawn chance (0.0 to maxEliteChance)
     */
    public double calculateEliteChance(int level) {
        double chance = baseEliteChance + (level * eliteChancePerLevel);
        return Math.min(chance, maxEliteChance);
    }

    /**
     * Calculates the boss spawn chance for a given level.
     *
     * @param level The realm level
     * @return Boss spawn chance (0.0 to maxBossChance)
     */
    public double calculateBossChance(int level) {
        double chance = baseBossChance + (level * bossChancePerLevel);
        return Math.min(chance, maxBossChance);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTERS
    // ═══════════════════════════════════════════════════════════════════

    public void setMobPool(@Nonnull RealmBiomeType biome, @Nonnull BiomeMobPool pool) {
        biomePools.put(biome, pool);
    }

    public void setBaseEliteChance(double baseEliteChance) {
        this.baseEliteChance = baseEliteChance;
    }

    public void setEliteChancePerLevel(double eliteChancePerLevel) {
        this.eliteChancePerLevel = eliteChancePerLevel;
    }

    public void setMaxEliteChance(double maxEliteChance) {
        this.maxEliteChance = maxEliteChance;
    }

    public void setBaseBossChance(double baseBossChance) {
        this.baseBossChance = baseBossChance;
    }

    public void setBossChancePerLevel(double bossChancePerLevel) {
        this.bossChancePerLevel = bossChancePerLevel;
    }

    public void setMaxBossChance(double maxBossChance) {
        this.maxBossChance = maxBossChance;
    }

    public void setEliteScale(float eliteScale) {
        this.eliteScale = eliteScale;
    }

    public void setBossScale(float bossScale) {
        this.bossScale = bossScale;
    }

    public void setMinSpawnDistance(int minSpawnDistance) {
        this.minSpawnDistance = minSpawnDistance;
    }

    public void setMaxSpawnDistance(int maxSpawnDistance) {
        this.maxSpawnDistance = maxSpawnDistance;
    }

    public void setSpawnBatchSize(int spawnBatchSize) {
        this.spawnBatchSize = spawnBatchSize;
    }

    public void setSpawnIntervalTicks(int spawnIntervalTicks) {
        this.spawnIntervalTicks = spawnIntervalTicks;
    }

    /**
     * Sets the spawn exclusion radius around the player spawn point.
     *
     * @param radius The exclusion radius in blocks (0 to disable)
     */
    public void setSpawnExclusionRadius(double radius) {
        this.spawnExclusionRadius = Math.max(0, radius);
    }

    public void setPlayerScalingMultiplier(float playerScalingMultiplier) {
        this.playerScalingMultiplier = playerScalingMultiplier;
    }

    public void setPlayerScalingAffectsDamage(boolean playerScalingAffectsDamage) {
        this.playerScalingAffectsDamage = playerScalingAffectsDamage;
    }

    public void setSpawnCountMultiplier(float spawnCountMultiplier) {
        this.spawnCountMultiplier = Math.max(0.1f, spawnCountMultiplier); // Min 0.1 to prevent zero spawns
    }

    public void setMinionLevelFraction(float minionLevelFraction) {
        this.minionLevelFraction = Math.max(0.1f, Math.min(1.0f, minionLevelFraction));
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED CLASS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Mob pool configuration for a single biome.
     *
     * <p>Note: Elite is NOT a pool - it's a spawn-time modifier. Any mob
     * (regular or boss) can roll to become elite at spawn time.
     */
    public static class BiomeMobPool {
        private final Map<String, Integer> regularMobs = new HashMap<>();
        private final Map<String, Integer> bossMobs = new HashMap<>();

        public void addRegularMob(@Nonnull String mobId, int weight) {
            regularMobs.put(mobId, weight);
        }

        public void addBossMob(@Nonnull String mobId, int weight) {
            bossMobs.put(mobId, weight);
        }

        @Nonnull
        public Map<String, Integer> getRegularMobs() {
            return Collections.unmodifiableMap(regularMobs);
        }

        @Nonnull
        public Map<String, Integer> getBossMobs() {
            return Collections.unmodifiableMap(bossMobs);
        }

        /**
         * Selects a random regular mob based on weights.
         *
         * @param random Random source
         * @return Selected mob ID, or null if pool is empty
         */
        public String selectRegularMob(@Nonnull Random random) {
            return selectWeighted(regularMobs, random);
        }

        /**
         * Selects a random boss mob based on weights.
         *
         * @param random Random source
         * @return Selected mob ID, or null if pool is empty
         */
        public String selectBossMob(@Nonnull Random random) {
            return selectWeighted(bossMobs, random);
        }

        private String selectWeighted(Map<String, Integer> pool, Random random) {
            if (pool.isEmpty()) {
                return null;
            }

            int totalWeight = pool.values().stream().mapToInt(Integer::intValue).sum();
            int roll = random.nextInt(totalWeight);

            int cumulative = 0;
            for (Map.Entry<String, Integer> entry : pool.entrySet()) {
                cumulative += entry.getValue();
                if (roll < cumulative) {
                    return entry.getKey();
                }
            }

            // Fallback (shouldn't happen)
            return pool.keySet().iterator().next();
        }
    }
}
