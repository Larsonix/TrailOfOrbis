package io.github.larsonix.trailoforbis.mobs.spawn.weight;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.spawn.config.MobSpawnConfig;
import io.github.larsonix.trailoforbis.mobs.spawn.config.MobSpawnConfig.LevelScalingConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Spawn weight modifier based on nearby player levels.
 *
 * <p>Higher level players attract more hostile spawns. Two formulas are supported:
 *
 * <h3>Logarithmic Formula (default, recommended)</h3>
 * <pre>
 * bonus = 0.0294 * log10(level)^2.75
 * multiplier = 1.0 + bonus
 * </pre>
 *
 * <p>Logarithmic scaling is gentler and prevents server overload at extreme levels:
 * <ul>
 *   <li>Level 1: 1.0×</li>
 *   <li>Level 100: ~1.2×</li>
 *   <li>Level 1,000: ~1.6×</li>
 *   <li>Level 10,000: ~2.2×</li>
 *   <li>Level 100,000: ~3.3×</li>
 *   <li>Level 1,000,000: 5.0×</li>
 * </ul>
 *
 * <h3>Linear Formula (legacy)</h3>
 * <pre>
 * increments = floor(highestLevel / levelPerIncrement)
 * multiplier = 1.0 + (increments * incrementBonus)
 * final = min(multiplier, maxLevelMultiplier)
 * </pre>
 *
 * <p>Example with legacy defaults (levelPerIncrement=50, incrementBonus=0.2):
 * <ul>
 *   <li>Level 1-49: 1.0× (no bonus)</li>
 *   <li>Level 50-99: 1.2× (+20%)</li>
 *   <li>Level 100-149: 1.4× (+40%)</li>
 * </ul>
 *
 * <p><b>Warning:</b> Linear formula with high level caps can cause server crashes
 * due to excessive spawn bursts at very high player levels.
 */
public class LevelBasedWeightModifier implements SpawnWeightModifier {

    private final MobSpawnConfig config;
    private final LevelingService levelingService;

    /**
     * Creates a new level-based weight modifier.
     *
     * @param config          The mob spawn configuration
     * @param levelingService Service for getting player levels (nullable - assumes level 1)
     */
    public LevelBasedWeightModifier(
            @Nonnull MobSpawnConfig config,
            @Nullable LevelingService levelingService) {
        this.config = config;
        this.levelingService = levelingService;
    }

    @Override
    public double getWeightMultiplier(
            int roleIndex,
            @Nonnull RPGMobClass mobClass,
            @Nonnull Vector3d position,
            @Nonnull Store<EntityStore> store) {

        LevelScalingConfig scalingConfig = config.getLevelScaling();

        // Check if level scaling is enabled
        if (!scalingConfig.isEnabled()) {
            return 1.0;
        }

        // Check if hostile only and this mob is not hostile
        if (scalingConfig.isHostileOnly() && !isHostileClass(mobClass)) {
            return 1.0;
        }

        // Find highest player level within detection radius
        int highestLevel = findHighestPlayerLevel(position, store, scalingConfig);

        if (highestLevel <= 0) {
            return 1.0;
        }

        // Calculate multiplier using selected formula
        double multiplier;
        if (scalingConfig.isLogarithmicFormula()) {
            multiplier = calculateLogarithmicMultiplier(highestLevel);
        } else {
            // Legacy linear formula
            int increments = highestLevel / scalingConfig.getLevelPerIncrement();
            multiplier = 1.0 + (increments * scalingConfig.getIncrementBonus());
        }

        // Apply cap
        return Math.min(multiplier, scalingConfig.getMaxLevelMultiplier());
    }

    /**
     * Calculates spawn multiplier using logarithmic scaling.
     *
     * <p>This formula scales gently with level to prevent server overload:
     * <pre>
     * bonus = 0.0294 * log10(level)^2.75
     * multiplier = 1.0 + bonus
     * </pre>
     *
     * @param level The highest player level nearby
     * @return Spawn multiplier (1.0 at level 1, ~5.0 at level 1,000,000)
     */
    private double calculateLogarithmicMultiplier(int level) {
        if (level <= 1) {
            return 1.0;
        }

        // Power-of-log formula: grows slowly at first, then faster
        // Tuned to reach ~5.0x at level 1,000,000
        double logLevel = Math.log10(level);
        double bonus = 0.0294 * Math.pow(logLevel, 2.75);

        return 1.0 + bonus;
    }

    /**
     * Checks if a mob class is considered hostile.
     */
    private boolean isHostileClass(RPGMobClass mobClass) {
        return mobClass.isHostile();
    }

    /**
     * Finds the highest level among all players within detection radius.
     */
    private int findHighestPlayerLevel(
            @Nonnull Vector3d position,
            @Nonnull Store<EntityStore> store,
            @Nonnull LevelScalingConfig scalingConfig) {

        World world = store.getExternalData().getWorld();
        if (world == null) {
            return 0;
        }

        double radiusSquared = scalingConfig.getDetectionRadiusSquared();
        int highestLevel = 0;

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }

            // Get player position
            TransformComponent transform = store.getComponent(
                entityRef, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }

            // Check if within detection radius
            Vector3d playerPos = transform.getPosition();
            double distSquared = distanceSquared(position, playerPos);

            if (distSquared <= radiusSquared) {
                // Get player level
                int level = (levelingService != null)
                    ? levelingService.getLevel(playerRef.getUuid())
                    : 1;

                if (level > highestLevel) {
                    highestLevel = level;
                }
            }
        }

        return highestLevel;
    }

    /**
     * Calculates squared distance between two points.
     */
    private static double distanceSquared(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
