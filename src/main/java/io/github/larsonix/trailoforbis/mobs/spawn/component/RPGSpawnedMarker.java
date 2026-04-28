package io.github.larsonix.trailoforbis.mobs.spawn.component;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;

/**
 * Marker component for mobs spawned by the RPG spawn system.
 *
 * <p>This is an empty marker component used to identify mobs that were
 * spawned as "additional spawns" by the RPG spawn multiplier system.
 * It prevents infinite spawn loops by allowing the spawn system to skip
 * multiplier calculations for mobs that already have this marker.
 *
 * <p>This replaces the previous position-based loop prevention which was
 * fragile and race-prone. Component markers are:
 * <ul>
 *   <li>Automatically cleaned up when the entity is removed</li>
 *   <li>Thread-safe (managed by the ECS)</li>
 *   <li>Reliable (no TTL timing issues)</li>
 * </ul>
 */
public class RPGSpawnedMarker implements Component<EntityStore> {

    /**
     * Codec for ECS persistence. Empty marker — no fields to serialize.
     */
    public static final BuilderCodec<RPGSpawnedMarker> CODEC = BuilderCodec.builder(
            RPGSpawnedMarker.class,
            RPGSpawnedMarker::new
        )
        .build();

    /**
     * Default constructor - creates an empty marker.
     */
    public RPGSpawnedMarker() {
    }

    /**
     * Copy constructor - required for {@link #clone()}.
     */
    private RPGSpawnedMarker(@Nonnull RPGSpawnedMarker other) {
        // Empty marker has no state to copy
    }

    /**
     * Gets the component type from the plugin registry.
     *
     * @return The registered component type for RPGSpawnedMarker
     */
    @Nonnull
    public static ComponentType<EntityStore, RPGSpawnedMarker> getComponentType() {
        return TrailOfOrbis.getInstance().getRPGSpawnedMarkerType();
    }

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new RPGSpawnedMarker(this);
    }

    @Override
    public String toString() {
        return "RPGSpawnedMarker{}";
    }
}
