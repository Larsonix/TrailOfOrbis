package io.github.larsonix.trailoforbis.maps.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import com.hypixel.hytale.server.npc.systems.SteppableTickingSystem;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.Set;

/**
 * Injects realm speed multipliers into NPC speed cache before movement.
 *
 * <p>Runs AFTER {@link RoleSystems.PreBehaviourSupportTickSystem} (cache invalidated)
 * and BEFORE {@link SteeringSystem} (movement). This ensures animations sync properly
 * because the motion controller uses our injected speed value.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>{@code PreBehaviourSupportTickSystem} invalidates speed cache (set to MAX_VALUE)</li>
 *   <li>This system sets cache to our {@code speedMultiplier}</li>
 *   <li>{@code SteeringSystem} reads cache and uses our value for movement</li>
 *   <li>Animations sync because motion controller "thinks" mob is actually that fast</li>
 * </ol>
 *
 * <p><b>Performance:</b>
 * <ul>
 *   <li>Only iterates realm mobs (query filters by {@link RealmMobComponent})</li>
 *   <li>Early exit for mobs with {@code speedMultiplier <= 1.0}</li>
 *   <li>Field lookup cached at startup, single {@code setFloat()} per mob per tick</li>
 *   <li>No external data structures (WeakHashMap, etc.)</li>
 * </ul>
 *
 * @see RealmMobComponent#getSpeedMultiplier()
 */
public class RealmMobSpeedCacheSystem extends SteppableTickingSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Component types
    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, RealmMobComponent> realmMobType;

    // System dependencies
    private final Set<Dependency<EntityStore>> dependencies;

    // Query for iteration
    private final Query<EntityStore> query;

    // Cached reflection field for performance
    private final Field speedCacheField;

    /**
     * Creates a new realm mob speed cache system.
     *
     * @param plugin The TrailOfOrbis plugin instance
     * @throws RuntimeException if the reflection field cannot be found (API changed)
     */
    public RealmMobSpeedCacheSystem(@Nonnull TrailOfOrbis plugin) {
        this.npcType = NPCEntity.getComponentType();
        this.realmMobType = RealmMobComponent.getComponentType();

        // Run AFTER cache invalidation, BEFORE movement
        this.dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, RoleSystems.PreBehaviourSupportTickSystem.class),
            new SystemDependency<>(Order.BEFORE, SteeringSystem.class)
        );

        // Query for entities that have both RealmMobComponent and NPCEntity
        this.query = Query.and(realmMobType, npcType);

        // Cache the reflection field at startup
        try {
            this.speedCacheField = NPCEntity.class.getDeclaredField("cachedEntityHorizontalSpeedMultiplier");
            this.speedCacheField.setAccessible(true);
            LOGGER.atInfo().log("RealmMobSpeedCacheSystem: Field access initialized");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(
                "Failed to find cachedEntityHorizontalSpeedMultiplier field - Hytale API changed?", e);
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // Reflection is not thread-safe
        return false;
    }

    @Override
    public void steppedTick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Get RealmMobComponent to check speed multiplier
        RealmMobComponent realmMob = archetypeChunk.getComponent(index, realmMobType);
        if (realmMob == null) {
            return;
        }

        float speedMultiplier = realmMob.getSpeedMultiplier();

        // Skip if no speed boost needed (multiplier is 1.0 or less)
        if (speedMultiplier <= 1.0f) {
            return;
        }

        // Get the NPC component to inject our speed
        NPCEntity npc = archetypeChunk.getComponent(index, npcType);
        if (npc == null) {
            return;
        }

        // Inject our speed multiplier into the cache
        // This replaces the MAX_VALUE from cache invalidation
        try {
            speedCacheField.setFloat(npc, speedMultiplier);
        } catch (IllegalAccessException e) {
            // Should never happen after setAccessible(true)
            LOGGER.atWarning().withCause(e).log("Failed to inject speed multiplier");
        }
    }
}
