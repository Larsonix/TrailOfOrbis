package io.github.larsonix.trailoforbis.maps.reward;

import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for victory rewards granted when a realm is completed.
 *
 * <p>Rewards scale by realm size and are affected by IIQ/IIR bonuses:
 * <ul>
 *   <li><b>SMALL:</b> 2 random items</li>
 *   <li><b>MEDIUM:</b> 3 random items</li>
 *   <li><b>LARGE:</b> 3 random items + 25% bonus IIR</li>
 *   <li><b>MASSIVE:</b> 3 random items + 50% bonus IIR + 10% bonus IIQ</li>
 * </ul>
 *
 * <p>Each item is randomly chosen from: map, stone, or gear (equal weight).
 *
 * <h2>IIQ Bonus System</h2>
 * <p>Base amounts from size are guaranteed. IIQ provides a % chance for +1 bonus
 * random item.
 *
 * <h2>Level Ranges</h2>
 * <ul>
 *   <li><b>Maps:</b> Completed map level ±1</li>
 *   <li><b>Gear:</b> Completed map level -3 to +0</li>
 *   <li><b>Stones:</b> Rarity only (no level)</li>
 * </ul>
 *
 * @see VictoryRewardGenerator
 */
public class VictoryRewardConfig {

    // ═══════════════════════════════════════════════════════════════════
    // SIZE REWARD SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reward quantities per realm size.
     *
     * <p>Each item is randomly chosen from: map, stone, or gear (equal weight).
     */
    public static class SizeRewards {
        private int totalItems = 0;
        private double bonusIir = 0.0;
        private double bonusIiq = 0.0;

        /**
         * Gets the total number of random items granted for this size tier.
         *
         * @return Total item count (each randomly assigned as map, stone, or gear)
         */
        public int getTotalItems() {
            return totalItems;
        }

        public void setTotalItems(int totalItems) {
            this.totalItems = Math.max(0, totalItems);
        }

        /**
         * Gets the bonus IIR applied to all drops for this size tier.
         *
         * <p>Used for LARGE/MASSIVE tiers to improve rarity on top of player's existing bonus.
         *
         * @return Bonus IIR as percentage (50 = +50%)
         */
        public double getBonusIir() {
            return bonusIir;
        }

        public void setBonusIir(double bonusIir) {
            this.bonusIir = Math.max(0, bonusIir);
        }

        /**
         * Gets the bonus IIQ applied to bonus roll chances for this size tier.
         *
         * <p>Used for MASSIVE tier to give extra bonus item roll chances.
         *
         * @return Bonus IIQ as percentage (10 = +10% chance per bonus roll)
         */
        public double getBonusIiq() {
            return bonusIiq;
        }

        public void setBonusIiq(double bonusIiq) {
            this.bonusIiq = Math.max(0, bonusIiq);
        }

        /**
         * Checks if this size tier grants any rewards.
         */
        public boolean hasRewards() {
            return totalItems > 0;
        }

        @Override
        public String toString() {
            return String.format("SizeRewards{totalItems=%d, bonusIir=%.0f%%, bonusIiq=%.0f%%}",
                totalItems, bonusIir, bonusIiq);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEFAULTS
    // ═══════════════════════════════════════════════════════════════════

    private static final SizeRewards DEFAULT_SMALL = createDefaults(2, 0, 0);
    private static final SizeRewards DEFAULT_MEDIUM = createDefaults(3, 0, 0);
    private static final SizeRewards DEFAULT_LARGE = createDefaults(3, 25, 0);
    private static final SizeRewards DEFAULT_MASSIVE = createDefaults(3, 50, 10);

    private static SizeRewards createDefaults(int totalItems, double bonusIir, double bonusIiq) {
        SizeRewards rewards = new SizeRewards();
        rewards.setTotalItems(totalItems);
        rewards.setBonusIir(bonusIir);
        rewards.setBonusIiq(bonusIiq);
        return rewards;
    }

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    /** Rewards per size tier */
    private final Map<RealmLayoutSize, SizeRewards> sizeRewards = new EnumMap<>(RealmLayoutSize.class);

    /** Level variance for map drops (±this value from completed level) */
    private int mapLevelVariance = 1;

    /** Minimum level offset for gear drops (negative = lower than map level) */
    private int gearLevelMinOffset = -3;

    /** Maximum level offset for gear drops */
    private int gearLevelMaxOffset = 0;

    /** Maximum bonus items per type from IIQ */
    private int maxBonusPerType = 2;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a VictoryRewardConfig with default settings.
     */
    public VictoryRewardConfig() {
        // Initialize with defaults
        sizeRewards.put(RealmLayoutSize.SMALL, DEFAULT_SMALL);
        sizeRewards.put(RealmLayoutSize.MEDIUM, DEFAULT_MEDIUM);
        sizeRewards.put(RealmLayoutSize.LARGE, DEFAULT_LARGE);
        sizeRewards.put(RealmLayoutSize.MASSIVE, DEFAULT_MASSIVE);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the reward configuration for a specific realm size.
     *
     * @param size The realm size
     * @return The reward configuration for that size (never null)
     */
    @Nonnull
    public SizeRewards getSizeRewards(@Nonnull RealmLayoutSize size) {
        Objects.requireNonNull(size, "size cannot be null");
        return sizeRewards.getOrDefault(size, DEFAULT_SMALL);
    }

    /**
     * Gets the level variance for map drops.
     *
     * <p>Maps drop at completed level ±this value.
     * e.g., variance=1 with level 50 → maps drop at 49-51
     *
     * @return Level variance (default 1)
     */
    public int getMapLevelVariance() {
        return mapLevelVariance;
    }

    /**
     * Gets the minimum level offset for gear drops.
     *
     * <p>Gear drops at completed level + offset (negative means lower).
     * e.g., minOffset=-3, maxOffset=0 with level 50 → gear drops at 47-50
     *
     * @return Minimum level offset (default -3)
     */
    public int getGearLevelMinOffset() {
        return gearLevelMinOffset;
    }

    /**
     * Gets the maximum level offset for gear drops.
     *
     * @return Maximum level offset (default 0)
     */
    public int getGearLevelMaxOffset() {
        return gearLevelMaxOffset;
    }

    /**
     * Gets the maximum bonus items per type from IIQ.
     *
     * <p>Limits how many bonus items can be granted from IIQ rolls.
     * Each item type (maps, stones, gear) is capped independently.
     *
     * @return Maximum bonus per type (default 2)
     */
    public int getMaxBonusPerType() {
        return maxBonusPerType;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the reward configuration for a specific realm size.
     *
     * @param size The realm size
     * @param rewards The reward configuration
     */
    public void setSizeRewards(@Nonnull RealmLayoutSize size, @Nonnull SizeRewards rewards) {
        Objects.requireNonNull(size, "size cannot be null");
        Objects.requireNonNull(rewards, "rewards cannot be null");
        sizeRewards.put(size, rewards);
    }

    public void setMapLevelVariance(int mapLevelVariance) {
        this.mapLevelVariance = Math.max(0, mapLevelVariance);
    }

    public void setGearLevelMinOffset(int gearLevelMinOffset) {
        this.gearLevelMinOffset = gearLevelMinOffset;
    }

    public void setGearLevelMaxOffset(int gearLevelMaxOffset) {
        this.gearLevelMaxOffset = gearLevelMaxOffset;
    }

    public void setMaxBonusPerType(int maxBonusPerType) {
        this.maxBonusPerType = Math.max(0, maxBonusPerType);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates a random map level based on completed level and variance.
     *
     * @param completedLevel The level of the completed realm
     * @param random The random source
     * @return A level within ±variance of completedLevel (minimum 1)
     */
    public int calculateMapLevel(int completedLevel, java.util.Random random) {
        if (mapLevelVariance == 0) {
            return Math.max(1, completedLevel);
        }
        int min = Math.max(1, completedLevel - mapLevelVariance);
        int max = completedLevel + mapLevelVariance;
        return random.nextInt(max - min + 1) + min;
    }

    /**
     * Calculates a random gear level based on completed level and offsets.
     *
     * @param completedLevel The level of the completed realm
     * @param random The random source
     * @return A level within offset range of completedLevel (minimum 1)
     */
    public int calculateGearLevel(int completedLevel, java.util.Random random) {
        int min = Math.max(1, completedLevel + gearLevelMinOffset);
        int max = Math.max(min, completedLevel + gearLevelMaxOffset);
        return random.nextInt(max - min + 1) + min;
    }

    @Override
    public String toString() {
        return String.format(
            "VictoryRewardConfig{mapVariance=%d, gearOffset=[%d,%d], maxBonus=%d, sizes=%s}",
            mapLevelVariance, gearLevelMinOffset, gearLevelMaxOffset, maxBonusPerType, sizeRewards);
    }
}
