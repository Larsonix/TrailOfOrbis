package io.github.larsonix.trailoforbis.mobs.calculator;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig.DistanceScalingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig.SafeZoneConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates distance-based bonus stat pool for mob scaling.
 *
 * <p>Uses LINEAR scaling for predictable difficulty progression:
 * <ul>
 *   <li><b>Safe Zone</b> (0-50 blocks): No scaling</li>
 *   <li><b>Transition</b> (50-100 blocks): No bonus yet</li>
 *   <li><b>Scaling Zone</b> (100+ blocks): Linear growth based on distance</li>
 * </ul>
 *
 * <p>Formula: {@code bonusPool = (distance - scalingStart) × poolPerBlock}
 *
 * <p>With default settings (0.3 pool/block):
 * <ul>
 *   <li>1000 blocks: ~270 pool</li>
 *   <li>3000 blocks: ~870 pool</li>
 *   <li>5000 blocks: ~1470 pool</li>
 *   <li>10000 blocks: ~2970 pool</li>
 * </ul>
 *
 * <p>This is a pure function calculator with no side effects.
 */
public class DistanceBonusCalculator {

    private final MobScalingConfig config;

    /**
     * Cache of spawn origin points per world.
     * Key is the world instance, value is the XZ spawn origin (Y is ignored for distance).
     * This is populated lazily on first access per world.
     */
    private static final Map<World, Vector3d> spawnOriginCache = new ConcurrentHashMap<>();

    /**
     * Constant UUID used for spawn point lookups.
     * Using a zero UUID ensures consistent spawn point selection across calls.
     */
    private static final UUID SPAWN_LOOKUP_UUID = new UUID(0L, 0L);

    /**
     * Creates a new distance bonus calculator.
     *
     * @param config The mob scaling configuration
     */
    public DistanceBonusCalculator(@Nonnull MobScalingConfig config) {
        this.config = config;
    }

    /**
     * Calculates the bonus stat pool based on distance from origin (0,0).
     *
     * <p>Uses simple LINEAR scaling:
     * <ul>
     *   <li>0-50 blocks: SAFE ZONE (no scaling)</li>
     *   <li>50-100 blocks: Transition (no bonus yet)</li>
     *   <li>100+ blocks: Linear growth at {@code poolPerBlock} rate</li>
     * </ul>
     *
     * @param distanceFromOrigin Distance from world origin (0,0) in XZ plane
     * @return Total bonus stat points to distribute randomly across stats
     */
    public double calculateBonusPool(double distanceFromOrigin) {
        SafeZoneConfig safeZone = config.getSafeZone();
        DistanceScalingConfig scaling = config.getDistanceScaling();

        // Safe zone - no scaling at all
        if (distanceFromOrigin <= safeZone.getRadius()) {
            return 0.0;
        }

        // Transition zone - no bonus yet, but not safe zone
        double scalingStart = scaling.getScalingStart();
        if (distanceFromOrigin < scalingStart) {
            return 0.0;
        }

        // Linear scaling: (distance - start) × poolPerBlock
        double effectiveDistance = distanceFromOrigin - scalingStart;
        double bonusPool = effectiveDistance * scaling.getPoolPerBlock();

        // Apply optional cap (0 = no cap)
        double maxPool = scaling.getMaxBonusPool();
        if (maxPool > 0) {
            bonusPool = Math.min(bonusPool, maxPool);
        }

        return bonusPool;
    }

    /**
     * Estimates a "distance-based level" when no players are nearby.
     *
     * <p>Used for mobs that spawn before any player approaches.
     * The estimated level increases roughly every 75 blocks.
     *
     * @param distanceFromOrigin Distance from world origin in XZ plane
     * @return Estimated mob level based on distance alone
     */
    public int estimateLevelFromDistance(double distanceFromOrigin) {
        double scalingStart = config.getDistanceScaling().getScalingStart();

        if (distanceFromOrigin <= scalingStart) {
            return 1; // Minimum level in safe/transition zones
        }

        // Rough conversion: every 75 blocks ≈ 1 level
        double effectiveDistance = distanceFromOrigin - scalingStart;
        int estimatedLevel = 1 + (int) (effectiveDistance / 75);

        return estimatedLevel;
    }

    /**
     * Checks if a position is within the safe zone (no scaling).
     *
     * @param distanceFromOrigin Distance from world origin
     * @return true if within safe zone
     */
    public boolean isInSafeZone(double distanceFromOrigin) {
        return distanceFromOrigin <= config.getSafeZone().getRadius();
    }

    /**
     * Checks if a position is within the transition zone (scaling starts ramping).
     *
     * @param distanceFromOrigin Distance from world origin
     * @return true if within transition zone
     */
    public boolean isInTransitionZone(double distanceFromOrigin) {
        SafeZoneConfig safeZone = config.getSafeZone();
        return distanceFromOrigin > safeZone.getRadius()
            && distanceFromOrigin < safeZone.getTransitionEnd();
    }

    /**
     * Gets the scaling zone name for debugging/display.
     *
     * @param distanceFromOrigin Distance from world origin
     * @return Zone name: "Safe", "Transition", or "Scaling"
     */
    @Nonnull
    public String getZoneName(double distanceFromOrigin) {
        SafeZoneConfig safeZone = config.getSafeZone();
        DistanceScalingConfig scaling = config.getDistanceScaling();

        if (distanceFromOrigin <= safeZone.getRadius()) {
            return "Safe";
        } else if (distanceFromOrigin < scaling.getScalingStart()) {
            return "Transition";
        } else {
            return "Scaling";
        }
    }

    /**
     * Calculates distance from origin using only X and Z coordinates.
     *
     * <p>Y (height) is intentionally ignored - a mob at (1000, 64, 0) should
     * scale the same as one at (1000, 128, 0).
     *
     * @param x X coordinate (world space)
     * @param z Z coordinate (world space)
     * @return Distance from origin (0, 0) in XZ plane
     */
    public static double calculateDistanceFromOrigin(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    /**
     * Calculates squared distance from origin (avoids sqrt for comparisons).
     *
     * @param x X coordinate
     * @param z Z coordinate
     * @return Squared distance from origin
     */
    public static double calculateDistanceSquaredFromOrigin(double x, double z) {
        return x * x + z * z;
    }

    /**
     * Calculates distance from the world's spawn point using only X and Z coordinates.
     *
     * <p>This is the preferred method for mob level scaling, as it ensures mobs
     * near the spawn point are level 1, regardless of where the spawn is located
     * in world coordinates.
     *
     * @param x     X coordinate (world space)
     * @param z     Z coordinate (world space)
     * @param world The world to get spawn point from
     * @return Distance from spawn in XZ plane
     */
    public static double calculateDistanceFromSpawn(double x, double z, @Nonnull World world) {
        Vector3d spawnOrigin = getSpawnOrigin(world);
        double dx = x - spawnOrigin.x;
        double dz = z - spawnOrigin.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Calculates distance from a custom spawn origin using only X and Z coordinates.
     *
     * <p>Use this overload when you've already retrieved the spawn origin.
     *
     * @param x           X coordinate (world space)
     * @param z           Z coordinate (world space)
     * @param spawnOrigin The spawn origin point
     * @return Distance from spawn in XZ plane
     */
    public static double calculateDistanceFromSpawn(double x, double z, @Nonnull Vector3d spawnOrigin) {
        double dx = x - spawnOrigin.x;
        double dz = z - spawnOrigin.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Gets the spawn origin for a world, caching the result.
     *
     * <p>The spawn origin is used as the center point for distance-based mob scaling.
     * It's typically the first spawn point defined by the world's spawn provider.
     *
     * @param world The world to get spawn origin for
     * @return The spawn origin position (Y may be 0 as it's ignored for distance)
     */
    @Nonnull
    public static Vector3d getSpawnOrigin(@Nonnull World world) {
        return spawnOriginCache.computeIfAbsent(world, DistanceBonusCalculator::computeSpawnOrigin);
    }

    /**
     * Computes the spawn origin for a world by querying its spawn provider.
     *
     * <p>Uses {@code getSpawnPoint(World, UUID)} instead of the deprecated
     * {@code getSpawnPoints()} array method.
     *
     * @param world The world to compute spawn origin for
     * @return The spawn origin, or (0, 0, 0) if spawn provider is unavailable
     */
    @Nonnull
    private static Vector3d computeSpawnOrigin(@Nonnull World world) {
        try {
            ISpawnProvider spawnProvider = world.getWorldConfig().getSpawnProvider();
            if (spawnProvider == null) {
                return Vector3d.ZERO;
            }

            // Use getSpawnPoint(World, UUID) instead of deprecated getSpawnPoints()
            Transform spawn = spawnProvider.getSpawnPoint(world, SPAWN_LOOKUP_UUID);
            if (spawn != null) {
                Vector3d position = spawn.getPosition();
                if (position != null) {
                    return position;
                }
            }
        } catch (Exception e) {
            // Fall through to return ZERO
        }

        return Vector3d.ZERO;
    }

    /**
     * Clears the spawn origin cache for a specific world.
     *
     * <p>Call this when a world is unloaded to prevent memory leaks.
     *
     * @param world The world to clear from cache
     */
    public static void clearCacheForWorld(@Nullable World world) {
        if (world != null) {
            spawnOriginCache.remove(world);
        }
    }

    /**
     * Clears the entire spawn origin cache.
     *
     * <p>Call this during plugin shutdown.
     */
    public static void clearCache() {
        spawnOriginCache.clear();
    }
}
