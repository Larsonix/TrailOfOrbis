package io.github.larsonix.trailoforbis.mobs;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Service interface for accessing mob scaling data.
 *
 * <p>This service is used by other systems (primarily {@code RPGDamageSystem})
 * to retrieve mob stats for combat calculations.
 *
 * <p>Example usage in RPGDamageSystem:
 * <pre>
 * MobScalingService mobService = ServiceRegistry.require(MobScalingService.class);
 * MobStats stats = mobService.getMobStats(attackerRef, accessor);
 * if (stats != null) {
 *     ComputedStats attackerStats = stats.toComputedStats();
 *     // Use for damage calculation...
 * }
 * </pre>
 *
 * @see MobStats
 * @see MobScalingComponent
 */
public interface MobScalingService {

    /**
     * Gets the computed stats for a mob.
     *
     * <p>Reads the {@link MobScalingComponent} attached to the entity and
     * returns its stored {@link MobStats}.
     *
     * @param mobRef   Entity reference
     * @param accessor Component accessor for reading data
     * @return Mob stats, or null if not a scaled mob
     */
    @Nullable
    MobStats getMobStats(@Nonnull Ref<EntityStore> mobRef, @Nonnull ComponentAccessor<EntityStore> accessor);

    /**
     * Gets the computed stats as a ComputedStats object.
     *
     * <p>This is a convenience method for damage system integration.
     * Equivalent to {@code getMobStats(ref, accessor).toComputedStats()}.
     *
     * @param mobRef   Entity reference
     * @param accessor Component accessor
     * @return ComputedStats for combat calculations, or null if not scaled
     */
    @Nullable
    ComputedStats getMobComputedStats(@Nonnull Ref<EntityStore> mobRef, @Nonnull ComponentAccessor<EntityStore> accessor);

    /**
     * Checks if an entity is a hostile mob that has scaling applied.
     *
     * @param entityRef Entity reference
     * @param accessor  Component accessor
     * @return true if the entity has a MobScalingComponent attached
     */
    boolean isScaledMob(@Nonnull Ref<EntityStore> entityRef, @Nonnull ComponentAccessor<EntityStore> accessor);

    /**
     * Checks if an entity should receive difficulty scaling.
     *
     * <p>This checks the hostile attitude, not whether scaling has been applied.
     * Use this to determine if a mob SHOULD be scaled.
     *
     * @param entityRef Entity reference
     * @param accessor  Component accessor
     * @return true if entity is a hostile mob that should be scaled
     */
    boolean isScalableMob(@Nonnull Ref<EntityStore> entityRef, @Nonnull ComponentAccessor<EntityStore> accessor);

    /**
     * Gets the scaling component for a mob, if present.
     *
     * @param mobRef   Entity reference
     * @param accessor Component accessor
     * @return The MobScalingComponent, or null if not attached
     */
    @Nullable
    MobScalingComponent getScalingComponent(@Nonnull Ref<EntityStore> mobRef, @Nonnull ComponentAccessor<EntityStore> accessor);

    /**
     * Gets the effective level of a scaled mob.
     *
     * @param mobRef   Entity reference
     * @param accessor Component accessor
     * @return Mob level, or 1 if not scaled
     */
    int getMobLevel(@Nonnull Ref<EntityStore> mobRef, @Nonnull ComponentAccessor<EntityStore> accessor);

    /**
     * Checks if the mob scaling system is enabled.
     *
     * @return true if mob scaling is active
     */
    boolean isEnabled();
}
