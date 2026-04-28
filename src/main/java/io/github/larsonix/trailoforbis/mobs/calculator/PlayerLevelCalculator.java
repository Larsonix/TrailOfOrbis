package io.github.larsonix.trailoforbis.mobs.calculator;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig.PlayerDetectionConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates effective mob level based on nearby player levels.
 *
 * <p>The formula combines:
 * <ul>
 *   <li><b>Average level</b>: Average of all player levels within detection radius</li>
 *   <li><b>Group multiplier</b>: ×1.2 per additional player grouped together (within 20 blocks)</li>
 * </ul>
 *
 * <p>Example: 3 players (levels 10, 12, 8) grouped together:
 * <ul>
 *   <li>Average = (10 + 12 + 8) / 3 = 10</li>
 *   <li>Group multiplier = 1.2^2 = 1.44 (3 players = 2 additional)</li>
 *   <li>Effective level = 10 × 1.44 = 14</li>
 * </ul>
 */
public class PlayerLevelCalculator {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LevelingService levelingService;
    private final MobScalingConfig config;
    private final DistanceBonusCalculator distanceBonusCalculator;

    /**
     * Creates a new player level calculator.
     *
     * @param levelingService        LevelingService for getting player levels (nullable)
     * @param config                 Mob scaling configuration
     * @param distanceBonusCalculator Distance calculator for fallback level estimation
     */
    public PlayerLevelCalculator(
            @Nullable LevelingService levelingService,
            @Nonnull MobScalingConfig config,
            @Nonnull DistanceBonusCalculator distanceBonusCalculator) {
        this.levelingService = levelingService;
        this.config = config;
        this.distanceBonusCalculator = distanceBonusCalculator;
    }

    /**
     * Calculates the effective mob level based on nearby players.
     *
     * <p>Formula:
     * <ul>
     *   <li>Base = Average level of all players within 64 blocks</li>
     *   <li>Multiplier = ×1.2 for each additional player grouped within 20 blocks</li>
     * </ul>
     *
     * <p>If no players are nearby, falls back to distance-based level estimation.
     *
     * <p><b>Distance calculation uses the world's spawn point as origin</b>, not (0,0).
     * This ensures mobs near spawn are level 1, regardless of where spawn is in world coords.
     *
     * @param mobPosition World position of the mob (spawn location)
     * @param accessor    Component accessor for querying entity data
     * @return Effective mob level (minimum 1)
     */
    public int calculateEffectiveLevel(
            @Nonnull Vector3d mobPosition,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        PlayerDetectionConfig detection = config.getPlayerDetection();

        // Calculate distance from spawn point (not origin!)
        // This ensures mobs near spawn are level 1, regardless of where spawn is in world coords
        World world = accessor.getExternalData().getWorld();
        double distFromSpawn = DistanceBonusCalculator.calculateDistanceFromSpawn(
            mobPosition.x, mobPosition.z, world
        );
        int rawDistanceLevel = distanceBonusCalculator.estimateLevelFromDistance(distFromSpawn) - 1; // -1 because base is 1
        int distanceBonus = rawDistanceLevel / 2; // Halved for balance

        // If player detection is disabled, use distance-based level only
        if (!detection.isEnabled()) {
            return Math.max(1, 1 + distanceBonus);
        }

        double detectionRadiusSquared = detection.getDetectionRadiusSquared();

        List<PlayerInfo> nearbyPlayers = new ArrayList<>();

        // Gather all players within detection radius
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) continue;

            TransformComponent transform = accessor.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) continue;

            Vector3d playerPos = transform.getPosition();
            double distSquared = distanceSquared(mobPosition, playerPos);

            // Only consider players within detection range (64 blocks default)
            if (distSquared <= detectionRadiusSquared) {
                // Get player level from LevelingService, or default to 1 if not available
                int level = (levelingService != null) ? levelingService.getLevel(playerRef.getUuid()) : 1;
                nearbyPlayers.add(new PlayerInfo(playerPos, level));
            }
        }

        if (nearbyPlayers.isEmpty()) {
            // No players nearby - use distance-based level only (minimum level 1)
            return Math.max(1, 1 + distanceBonus);
        }

        // Calculate average player level
        double averageLevel = nearbyPlayers.stream()
            .mapToInt(PlayerInfo::level)
            .average()
            .orElse(1.0);

        // Count grouped players (within 20 blocks of each other)
        int groupedCount = countGroupedPlayers(nearbyPlayers);

        // Apply group multiplier: avg × 1.2^(groupedCount - 1)
        double groupMultiplier = config.getPlayerDetection().getGroupMultiplier();
        double multiplier = Math.pow(groupMultiplier, Math.max(0, groupedCount - 1));
        double playerBasedLevel = averageLevel * multiplier;

        // COMBINE: Player level + Distance bonus
        // This ensures mobs scale to BOTH player power AND how far from spawn you are
        double effectiveLevel = playerBasedLevel + distanceBonus;

        return Math.max(1, (int) Math.round(effectiveLevel));
    }

    /**
     * Calculates the effective mob level with cached player data.
     *
     * <p>Use this overload when you've already gathered nearby players for
     * performance reasons (avoiding redundant queries).
     *
     * <p><b>Distance calculation uses the world's spawn point as origin</b>, not (0,0).
     *
     * @param nearbyPlayers List of nearby player positions and levels
     * @param mobPosition   The mob's position for fallback calculation
     * @param world         The world (for spawn point lookup)
     * @return Effective mob level (minimum 1)
     */
    public int calculateEffectiveLevel(
            @Nonnull List<PlayerInfo> nearbyPlayers,
            @Nonnull Vector3d mobPosition,
            @Nonnull World world) {

        // Calculate distance from spawn point (not origin!)
        double distFromSpawn = DistanceBonusCalculator.calculateDistanceFromSpawn(
            mobPosition.x, mobPosition.z, world
        );
        return calculateEffectiveLevelInternal(nearbyPlayers, mobPosition, distFromSpawn);
    }

    /**
     * Calculates the effective mob level with cached player data using origin (0,0) as reference.
     *
     * <p>This overload uses (0,0) as the distance reference point instead of the world spawn.
     * Use this for testing or when the world spawn point is unavailable.
     *
     * @param nearbyPlayers List of nearby player positions and levels
     * @param mobPosition   The mob's position for fallback calculation
     * @return Effective mob level (minimum 1)
     */
    public int calculateEffectiveLevel(
            @Nonnull List<PlayerInfo> nearbyPlayers,
            @Nonnull Vector3d mobPosition) {

        // Calculate distance from origin (0,0) - for backward compatibility and testing
        double distFromOrigin = DistanceBonusCalculator.calculateDistanceFromOrigin(
            mobPosition.x, mobPosition.z
        );
        return calculateEffectiveLevelInternal(nearbyPlayers, mobPosition, distFromOrigin);
    }

    /**
     * Internal implementation of effective level calculation.
     *
     * @param nearbyPlayers List of nearby player positions and levels
     * @param mobPosition   The mob's position
     * @param distance      Distance from reference point (spawn or origin)
     * @return Effective mob level (minimum 1)
     */
    private int calculateEffectiveLevelInternal(
            @Nonnull List<PlayerInfo> nearbyPlayers,
            @Nonnull Vector3d mobPosition,
            double distance) {

        int rawDistanceLevel = distanceBonusCalculator.estimateLevelFromDistance(distance) - 1;
        int distanceBonus = rawDistanceLevel / 2; // Halved for balance

        // If player detection is disabled or no nearby players, use distance-based level only
        if (!config.getPlayerDetection().isEnabled() || nearbyPlayers.isEmpty()) {
            return Math.max(1, 1 + distanceBonus);
        }

        double averageLevel = nearbyPlayers.stream()
            .mapToInt(PlayerInfo::level)
            .average()
            .orElse(1.0);

        int groupedCount = countGroupedPlayers(nearbyPlayers);

        double groupMultiplier = config.getPlayerDetection().getGroupMultiplier();
        double multiplier = Math.pow(groupMultiplier, Math.max(0, groupedCount - 1));
        double playerBasedLevel = averageLevel * multiplier;

        // COMBINE: Player level + Distance bonus
        double effectiveLevel = playerBasedLevel + distanceBonus;

        return Math.max(1, (int) Math.round(effectiveLevel));
    }

    /**
     * Gets the number of players detected near a mob position.
     *
     * <p>Useful for debugging or display purposes.
     *
     * @param mobPosition The mob's position
     * @param accessor    Component accessor for querying
     * @return Number of players within detection radius
     */
    public int countNearbyPlayers(
            @Nonnull Vector3d mobPosition,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        PlayerDetectionConfig detection = config.getPlayerDetection();
        double detectionRadiusSquared = detection.getDetectionRadiusSquared();

        World world = accessor.getExternalData().getWorld();
        int count = 0;

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) continue;

            TransformComponent transform = accessor.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) continue;

            double distSquared = distanceSquared(mobPosition, transform.getPosition());
            if (distSquared <= detectionRadiusSquared) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts how many players are grouped together (within group radius).
     *
     * <p>Uses a simple algorithm: for each player, count how many others
     * are within the group radius, then return the maximum group size found.
     *
     * @param players List of nearby players
     * @return Maximum group size (1 if players are isolated)
     */
    private int countGroupedPlayers(@Nonnull List<PlayerInfo> players) {
        if (players.size() <= 1) {
            return players.size();
        }

        double groupRadiusSquared = config.getPlayerDetection().getGroupRadiusSquared();
        int maxGroupSize = 1;

        for (int i = 0; i < players.size(); i++) {
            int groupSize = 1;
            for (int j = 0; j < players.size(); j++) {
                if (i != j) {
                    double dist = distanceSquared(
                        players.get(i).position(),
                        players.get(j).position()
                    );
                    if (dist <= groupRadiusSquared) {
                        groupSize++;
                    }
                }
            }
            maxGroupSize = Math.max(maxGroupSize, groupSize);
        }

        return maxGroupSize;
    }

    /**
     * Calculates squared distance between two points.
     * Avoids sqrt for performance.
     */
    private static double distanceSquared(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Record for storing player position and level during calculation.
     */
    public record PlayerInfo(@Nonnull Vector3d position, int level) {}
}
