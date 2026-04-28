package io.github.larsonix.trailoforbis.gear.conversion;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;

import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Provides distance-based level estimation for chest loot and other systems.
 *
 * <p>The item level is determined by distance from spawn using the formula:
 * {@code level = 1 + (distance - 100) / 75}
 *
 * <p><b>Note:</b> Container loot replacement is now handled by
 * {@code ContainerLootInterceptor} (ECS system). This class remains for
 * distance-based level calculations used by other systems.
 */
public final class ChestLootConversionListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final VanillaItemConverter converter;
    private final DistanceBonusCalculator distanceCalculator;

    /**
     * Creates a new ChestLootConversionListener.
     */
    public ChestLootConversionListener(
            @Nonnull VanillaItemConverter converter,
            @Nonnull MobScalingConfig mobScalingConfig) {
        this.converter = Objects.requireNonNull(converter, "converter cannot be null");
        Objects.requireNonNull(mobScalingConfig, "mobScalingConfig cannot be null");
        this.distanceCalculator = new DistanceBonusCalculator(mobScalingConfig);
    }

    /**
     * Registers this listener with the event registry.
     *
     * <p>Currently only logs initialization status. Container loot handling
     * is done by {@code ContainerLootInterceptor} via UseBlockEvent.Pre.
     *
     */
    public void register(@Nonnull EventRegistry eventRegistry) {
        Objects.requireNonNull(eventRegistry, "eventRegistry cannot be null");

        if (!converter.isSourceEnabled(VanillaItemConverter.AcquisitionSource.CHEST_LOOT)) {
            LOGGER.atInfo().log("Chest loot conversion is disabled, not registering listener");
            return;
        }

        LOGGER.atInfo().log("ChestLootConversionListener initialized (distance calculator ready)");
    }

    /**
     * Gets the distance calculator for use by other systems.
     *
     * <p>Used by:
     * <ul>
     *   <li>Mob scaling system for difficulty calculations</li>
     *   <li>Loot scaling based on distance from spawn</li>
     *   <li>Container loot system for level estimation</li>
     * </ul>
     *
     */
    @Nonnull
    public DistanceBonusCalculator getDistanceCalculator() {
        return distanceCalculator;
    }

    /**
     * Estimates item level from player position.
     *
     * <p>Uses distance from world origin (spawn) to determine appropriate
     * item level. Formula: {@code level = 1 + (distance - 100) / 75}
     *
     * @param x X coordinate
     * @param z Z coordinate
     * @return The estimated item level (minimum 1)
     */
    public int estimateLevelFromPosition(double x, double z) {
        double distance = DistanceBonusCalculator.calculateDistanceFromOrigin(x, z);
        return distanceCalculator.estimateLevelFromDistance(distance);
    }

    /**
     * Estimates item level from a position vector.
     *
     * @return minimum 1
     */
    public int estimateLevelFromPosition(@Nonnull Vector3d pos) {
        return estimateLevelFromPosition(pos.getX(), pos.getZ());
    }
}
