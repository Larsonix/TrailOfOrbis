package io.github.larsonix.trailoforbis.maps.spawning;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a mob entry in a spawn pool with associated weight and classification.
 *
 * <p>Weights determine relative spawn probability within a pool. A mob with weight 30
 * is twice as likely to spawn as one with weight 15.
 *
 * <h2>Classification</h2>
 * <ul>
 *   <li>{@link MobClassification#NORMAL} - Standard enemies, form the bulk of spawns</li>
 *   <li>{@link MobClassification#BOSS} - Powerful unique enemies with special mechanics</li>
 * </ul>
 *
 * <h2>Elite Modifier</h2>
 * <p>Elite is NOT a classification - it's a spawn-time modifier that can apply to ANY mob.
 * Both normal and boss mobs can roll to become elite at spawn time, receiving bonus stats.
 * See {@link #ELITE_STAT_MULTIPLIER}, {@link #ELITE_HEALTH_MULTIPLIER}, {@link #ELITE_XP_MULTIPLIER}.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * WeightedMob trork = WeightedMob.normal("trork_warrior", 30);
 * WeightedMob chieftain = WeightedMob.boss("trork_chieftain", 5);
 * }</pre>
 *
 * @param mobTypeId The Hytale entity type identifier (e.g., "hytale:trork_warrior")
 * @param weight Spawn weight for probability calculation (positive integer)
 * @param classification The mob's difficulty classification
 * @param minLevel Minimum realm level for this mob to spawn (inclusive)
 * @param maxLevel Maximum realm level for this mob to spawn (inclusive, -1 for unlimited)
 *
 * @see BiomeMobPool
 * @see MobClassification
 */
public record WeightedMob(
    @Nonnull String mobTypeId,
    int weight,
    @Nonnull MobClassification classification,
    int minLevel,
    int maxLevel
) {

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents no level cap (mob spawns at any level).
     */
    public static final int NO_LEVEL_CAP = -1;

    /**
     * Default weight for standard mobs.
     */
    public static final int DEFAULT_WEIGHT = 10;

    // ═══════════════════════════════════════════════════════════════════
    // ELITE MULTIPLIERS (spawn-time modifier, applies to any mob)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Stat multiplier for elite mobs (damage, defense, etc.).
     * Elite is a spawn-time modifier that can apply to any mob (normal or boss).
     */
    public static final float ELITE_STAT_MULTIPLIER = 2.0f;

    /**
     * Health multiplier for elite mobs.
     */
    public static final float ELITE_HEALTH_MULTIPLIER = 1.5f;

    /**
     * XP reward multiplier for elite mobs.
     */
    public static final float ELITE_XP_MULTIPLIER = 3.0f;

    // ═══════════════════════════════════════════════════════════════════
    // COMPACT CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Validates and normalizes constructor arguments.
     */
    public WeightedMob {
        Objects.requireNonNull(mobTypeId, "mobTypeId cannot be null");
        Objects.requireNonNull(classification, "classification cannot be null");

        if (mobTypeId.isBlank()) {
            throw new IllegalArgumentException("mobTypeId cannot be blank");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive, got: " + weight);
        }
        if (minLevel < 0) {
            throw new IllegalArgumentException("minLevel cannot be negative, got: " + minLevel);
        }
        if (maxLevel != NO_LEVEL_CAP && maxLevel < minLevel) {
            throw new IllegalArgumentException(
                "maxLevel (" + maxLevel + ") cannot be less than minLevel (" + minLevel + ")");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a normal mob with default level range (1 to unlimited).
     *
     * @param mobTypeId The entity type identifier
     * @param weight Spawn weight
     * @return A new WeightedMob with NORMAL classification
     */
    @Nonnull
    public static WeightedMob normal(@Nonnull String mobTypeId, int weight) {
        return new WeightedMob(mobTypeId, weight, MobClassification.NORMAL, 1, NO_LEVEL_CAP);
    }

    /**
     * Creates a normal mob with specified level range.
     *
     * @param mobTypeId The entity type identifier
     * @param weight Spawn weight
     * @param minLevel Minimum level for spawning
     * @param maxLevel Maximum level for spawning (-1 for unlimited)
     * @return A new WeightedMob with NORMAL classification
     */
    @Nonnull
    public static WeightedMob normal(@Nonnull String mobTypeId, int weight, int minLevel, int maxLevel) {
        return new WeightedMob(mobTypeId, weight, MobClassification.NORMAL, minLevel, maxLevel);
    }

    /**
     * Creates a boss mob with default level range (1 to unlimited).
     *
     * @param mobTypeId The entity type identifier
     * @param weight Spawn weight
     * @return A new WeightedMob with BOSS classification
     */
    @Nonnull
    public static WeightedMob boss(@Nonnull String mobTypeId, int weight) {
        return new WeightedMob(mobTypeId, weight, MobClassification.BOSS, 1, NO_LEVEL_CAP);
    }

    /**
     * Creates a boss mob with specified level range.
     *
     * @param mobTypeId The entity type identifier
     * @param weight Spawn weight
     * @param minLevel Minimum level for spawning
     * @param maxLevel Maximum level for spawning (-1 for unlimited)
     * @return A new WeightedMob with BOSS classification
     */
    @Nonnull
    public static WeightedMob boss(@Nonnull String mobTypeId, int weight, int minLevel, int maxLevel) {
        return new WeightedMob(mobTypeId, weight, MobClassification.BOSS, minLevel, maxLevel);
    }

    /**
     * Creates a WeightedMob from a simple mob ID and weight.
     *
     * <p>Classification is inferred from the mobTypeId if it contains
     * "elite" or "boss" keywords, otherwise defaults to NORMAL.
     *
     * @param mobTypeId The entity type identifier
     * @param weight Spawn weight
     * @return A new WeightedMob with inferred classification
     */
    @Nonnull
    public static WeightedMob of(@Nonnull String mobTypeId, int weight) {
        MobClassification classification = inferClassification(mobTypeId);
        return new WeightedMob(mobTypeId, weight, classification, 1, NO_LEVEL_CAP);
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if this mob can spawn at the given realm level.
     *
     * @param level The realm level to check
     * @return true if this mob can spawn at the level
     */
    public boolean canSpawnAtLevel(int level) {
        if (level < minLevel) {
            return false;
        }
        return maxLevel == NO_LEVEL_CAP || level <= maxLevel;
    }

    /**
     * Checks if this mob has a level restriction.
     *
     * @return true if minLevel > 1 or maxLevel is set
     */
    public boolean hasLevelRestriction() {
        return minLevel > 1 || maxLevel != NO_LEVEL_CAP;
    }

    /**
     * Checks if this is a normal mob.
     *
     * @return true if classification is NORMAL
     */
    public boolean isNormal() {
        return classification == MobClassification.NORMAL;
    }

    /**
     * Checks if this is a boss mob.
     *
     * @return true if classification is BOSS
     */
    public boolean isBoss() {
        return classification == MobClassification.BOSS;
    }

    /**
     * Gets the stat multiplier for this mob's classification.
     *
     * @return Stat multiplier (1.0 for normal, higher for elite/boss)
     */
    public float getStatMultiplier() {
        return classification.getStatMultiplier();
    }

    /**
     * Gets the XP multiplier for this mob's classification.
     *
     * @return XP multiplier (1.0 for normal, higher for elite/boss)
     */
    public float getXpMultiplier() {
        return classification.getXpMultiplier();
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRANSFORMATION METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a copy with a different weight.
     *
     * @param newWeight The new weight
     * @return A new WeightedMob with updated weight
     */
    @Nonnull
    public WeightedMob withWeight(int newWeight) {
        return new WeightedMob(mobTypeId, newWeight, classification, minLevel, maxLevel);
    }

    /**
     * Creates a copy with a different level range.
     *
     * @param newMinLevel New minimum level
     * @param newMaxLevel New maximum level
     * @return A new WeightedMob with updated level range
     */
    @Nonnull
    public WeightedMob withLevelRange(int newMinLevel, int newMaxLevel) {
        return new WeightedMob(mobTypeId, weight, classification, newMinLevel, newMaxLevel);
    }

    /**
     * Creates a copy with a different classification.
     *
     * @param newClassification The new classification
     * @return A new WeightedMob with updated classification
     */
    @Nonnull
    public WeightedMob withClassification(@Nonnull MobClassification newClassification) {
        return new WeightedMob(mobTypeId, weight, newClassification, minLevel, maxLevel);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Infers classification from mob type ID based on naming conventions.
     *
     * <p>Note: Elite is no longer a classification. Names like "captain" or "elite"
     * now result in NORMAL classification - the mob can still roll to become elite
     * at spawn time based on elite chance.
     */
    private static MobClassification inferClassification(String mobTypeId) {
        String lower = mobTypeId.toLowerCase();
        if (lower.contains("boss") || lower.contains("chieftain") ||
            lower.contains("lord") || lower.contains("king") ||
            lower.contains("queen") || lower.contains("emperor")) {
            return MobClassification.BOSS;
        }
        return MobClassification.NORMAL;
    }

    @Override
    public String toString() {
        String levelRange = maxLevel == NO_LEVEL_CAP
            ? "L" + minLevel + "+"
            : "L" + minLevel + "-" + maxLevel;
        return String.format("WeightedMob[%s, w=%d, %s, %s]",
            mobTypeId, weight, classification, levelRange);
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED ENUM
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Classification of mob difficulty and reward tier.
     *
     * <p>Note: Elite is NOT a classification - it's a spawn-time modifier that can
     * apply to any mob (normal or boss). See {@link #ELITE_STAT_MULTIPLIER} etc.
     */
    public enum MobClassification {
        /**
         * Standard enemy with baseline stats.
         */
        NORMAL(1.0f, 1.0f, 1.0f),

        /**
         * Powerful unique enemy with special mechanics.
         */
        BOSS(5.0f, 3.0f, 10.0f);

        private final float statMultiplier;
        private final float healthMultiplier;
        private final float xpMultiplier;

        MobClassification(float statMultiplier, float healthMultiplier, float xpMultiplier) {
            this.statMultiplier = statMultiplier;
            this.healthMultiplier = healthMultiplier;
            this.xpMultiplier = xpMultiplier;
        }

        /**
         * Gets the general stat multiplier for damage, defense, etc.
         *
         * @return Stat multiplier (1.0 for normal)
         */
        public float getStatMultiplier() {
            return statMultiplier;
        }

        /**
         * Gets the health multiplier.
         *
         * @return Health multiplier (1.0 for normal)
         */
        public float getHealthMultiplier() {
            return healthMultiplier;
        }

        /**
         * Gets the XP reward multiplier.
         *
         * @return XP multiplier (1.0 for normal)
         */
        public float getXpMultiplier() {
            return xpMultiplier;
        }
    }
}
