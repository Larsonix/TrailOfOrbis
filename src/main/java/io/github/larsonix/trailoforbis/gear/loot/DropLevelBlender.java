package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.LevelBlendingConfig;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Blends drop levels toward the player's level.
 *
 * <p>Formula:
 * <pre>
 * blendedLevel = sourceLevel + clamp((playerLevel - sourceLevel) × pullFactor, -maxOffset, +maxOffset)
 * finalLevel   = max(1, blendedLevel + random(±variance))
 * </pre>
 *
 * <p>When disabled, falls back to source level with ±variance only.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class DropLevelBlender {

    private final LevelBlendingConfig config;

    /**
     * Creates a DropLevelBlender.
     */
    public DropLevelBlender(@Nonnull LevelBlendingConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Calculates the blended drop level.
     *
     * @param sourceLevel mob level, realm level, etc.
     * @return always >= 1
     */
    public int calculate(int sourceLevel, int playerLevel, @Nonnull Random random) {
        int base;

        if (config.enabled()) {
            double gap = playerLevel - sourceLevel;
            double pull = gap * config.pullFactor();
            double clamped = Math.max(-config.maxOffset(), Math.min(config.maxOffset(), pull));
            base = (int) Math.round(sourceLevel + clamped);
        } else {
            base = sourceLevel;
        }

        // Apply variance
        int variance = config.variance();
        if (variance > 0) {
            int range = variance * 2 + 1; // e.g., variance=2 → range=5 (-2..+2)
            base += random.nextInt(range) - variance;
        }

        return Math.max(1, base);
    }

    /**
     * Calculates the blended drop level using ThreadLocalRandom.
     *
     * @return always >= 1
     */
    public int calculate(int sourceLevel, int playerLevel) {
        return calculate(sourceLevel, playerLevel, ThreadLocalRandom.current());
    }

    @Nonnull
    public LevelBlendingConfig getConfig() {
        return config;
    }
}
