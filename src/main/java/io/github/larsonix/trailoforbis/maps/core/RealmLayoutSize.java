package io.github.larsonix.trailoforbis.maps.core;

import com.hypixel.hytale.codec.codecs.EnumCodec;

import javax.annotation.Nonnull;

/**
 * Defines the size variants for Realm instances.
 *
 * <p>Size affects:
 * <ul>
 *   <li>Arena dimensions (radius in blocks)</li>
 *   <li>Base monster count before multipliers</li>
 *   <li>Guaranteed boss spawns</li>
 *   <li>Completion time expectations</li>
 *   <li>Reward scaling</li>
 * </ul>
 *
 * <p>Larger realms offer more rewards but require more time and skill.
 */
public enum RealmLayoutSize {

    /**
     * Small arena for quick runs.
     * 25 block radius (50x50 arena), ~5 minutes completion time.
     */
    SMALL("Small", 25, 15, 1.0f, 0, 300, 20, 50),

    /**
     * Medium arena for standard gameplay.
     * 49 block radius (98x98 arena). Radius scales dynamically with level.
     */
    MEDIUM("Medium", 49, 25, 1.0f, 1, 600, 35, 80),

    /**
     * Large arena for extended sessions.
     * 100 block radius (200x200 arena). Radius scales dynamically with level.
     */
    LARGE("Large", 100, 40, 1.0f, 1, 900, 60, 150),

    /**
     * Massive arena for group content.
     * 200 block radius (400x400 arena). Radius scales dynamically with level.
     */
    MASSIVE("Massive", 200, 70, 1.0f, 2, 1200, 100, 250);

    private final String displayName;
    private final int arenaRadius;
    private final int baseMonsterCount;
    private final float monsterMultiplier;
    private final int guaranteedBosses;
    private final int baseTimeoutSeconds;
    private final int minRadius;
    private final int maxRadius;

    RealmLayoutSize(
            @Nonnull String displayName,
            int arenaRadius,
            int baseMonsterCount,
            float monsterMultiplier,
            int guaranteedBosses,
            int baseTimeoutSeconds,
            int minRadius,
            int maxRadius) {
        this.displayName = displayName;
        this.arenaRadius = arenaRadius;
        this.baseMonsterCount = baseMonsterCount;
        this.monsterMultiplier = monsterMultiplier;
        this.guaranteedBosses = guaranteedBosses;
        this.baseTimeoutSeconds = baseTimeoutSeconds;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return The display name (e.g., "Small", "Massive")
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the arena radius in blocks.
     *
     * <p>The playable area is a circle with this radius from center.
     *
     * @return Arena radius in blocks (50-400)
     */
    public int getArenaRadius() {
        return arenaRadius;
    }

    /**
     * Gets the arena diameter in blocks.
     *
     * @return Arena diameter (radius * 2)
     */
    public int getArenaDiameter() {
        return arenaRadius * 2;
    }

    /**
     * Gets the minimum allowed radius for dynamic arena scaling.
     * The computed radius will never go below this value.
     */
    public int getMinRadius() {
        return minRadius;
    }

    /**
     * Gets the maximum allowed radius for dynamic arena scaling.
     * The computed radius will never exceed this value.
     */
    public int getMaxRadius() {
        return maxRadius;
    }

    /**
     * Gets the base monster count before any multipliers.
     *
     * <p>Final count is: baseMonsterCount * monsterMultiplier * levelMultiplier * modifierMultiplier
     *
     * @return Base monster count (15-70)
     */
    public int getBaseMonsterCount() {
        return baseMonsterCount;
    }

    /**
     * Gets the monster count multiplier for this size.
     *
     * <p>Applied to base count during spawn calculations.
     *
     * @return Monster multiplier (1.0 for all sizes)
     */
    public float getMonsterMultiplier() {
        return monsterMultiplier;
    }

    /**
     * Gets the number of guaranteed boss spawns.
     *
     * <p>These bosses spawn in addition to the random elite/boss chances.
     *
     * @return Guaranteed boss count (0-3)
     */
    public int getGuaranteedBosses() {
        return guaranteedBosses;
    }

    /**
     * Gets the base timeout in seconds.
     *
     * <p>Realm closes if not completed within this time.
     * May be modified by realm modifiers.
     *
     * @return Base timeout in seconds (300-1200)
     */
    public int getBaseTimeoutSeconds() {
        return baseTimeoutSeconds;
    }

    /**
     * Calculates the expected monster count for a given level.
     *
     * <p>Formula: baseMonsterCount * monsterMultiplier * (1 + level * 0.02)
     *
     * @param level The realm level (1-10000)
     * @return Expected monster count
     */
    public int calculateMonsterCount(int level) {
        float levelMultiplier = 1.0f + (level * 0.02f);
        return Math.round(baseMonsterCount * monsterMultiplier * levelMultiplier);
    }

    /**
     * Calculates the reward multiplier for this size.
     *
     * <p>Larger realms give proportionally more rewards.
     *
     * @return Reward multiplier (1.0 for SMALL, scales up)
     */
    public float getRewardMultiplier() {
        return switch (this) {
            case SMALL -> 1.0f;
            case MEDIUM -> 1.5f;
            case LARGE -> 2.5f;
            case MASSIVE -> 4.0f;
        };
    }

    /**
     * Gets the approximate area in square blocks.
     *
     * @return Approximate arena area (PI * radius^2)
     */
    public int getApproximateArea() {
        return (int) (Math.PI * arenaRadius * arenaRadius);
    }

    /**
     * Gets the next larger size, or empty if already at maximum.
     *
     * @return Next size tier, or empty if MASSIVE
     */
    @Nonnull
    public java.util.Optional<RealmLayoutSize> getNextSize() {
        int nextOrdinal = this.ordinal() + 1;
        RealmLayoutSize[] values = values();
        if (nextOrdinal < values.length) {
            return java.util.Optional.of(values[nextOrdinal]);
        }
        return java.util.Optional.empty();
    }

    /**
     * Gets the next smaller size, or empty if already at minimum.
     *
     * @return Previous size tier, or empty if SMALL
     */
    @Nonnull
    public java.util.Optional<RealmLayoutSize> getPreviousSize() {
        int prevOrdinal = this.ordinal() - 1;
        if (prevOrdinal >= 0) {
            return java.util.Optional.of(values()[prevOrdinal]);
        }
        return java.util.Optional.empty();
    }

    /**
     * Gets a random size with weighted probability.
     *
     * <p>Weights: SMALL=40%, MEDIUM=35%, LARGE=20%, MASSIVE=5%
     *
     * @param random The random source
     * @return A weighted random size
     */
    @Nonnull
    public static RealmLayoutSize randomWeighted(@Nonnull java.util.Random random) {
        int roll = random.nextInt(100);
        if (roll < 40) return SMALL;
        if (roll < 75) return MEDIUM;
        if (roll < 95) return LARGE;
        return MASSIVE;
    }

    /**
     * Parse size from string (case-insensitive).
     *
     * @param name Size name
     * @return The corresponding RealmLayoutSize
     * @throws IllegalArgumentException if name is not recognized
     */
    @Nonnull
    public static RealmLayoutSize fromString(@Nonnull String name) {
        if (name == null) {
            throw new IllegalArgumentException("Size name cannot be null");
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown layout size: " + name);
        }
    }

    /**
     * Codec for serialization/deserialization.
     */
    public static final EnumCodec<RealmLayoutSize> CODEC = new EnumCodec<>(RealmLayoutSize.class);
}
