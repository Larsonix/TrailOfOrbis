package io.github.larsonix.trailoforbis.ailments;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.config.AilmentConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * ECS tick system that manages non-damage ailment durations (Freeze, Shock).
 *
 * <p>DOT damage (Burn, Poison) is handled by dedicated ECS tick systems:
 * <ul>
 *   <li>{@link io.github.larsonix.trailoforbis.ailments.component.RpgBurnTickSystem} — burn DOT</li>
 *   <li>{@link io.github.larsonix.trailoforbis.ailments.component.RpgPoisonTickSystem} — poison DOT</li>
 * </ul>
 *
 * <p>This system handles:
 * <ul>
 *   <li>Freeze duration countdown and expiry (slow effect read by MobSpeedEffectManager)</li>
 *   <li>Shock duration countdown and expiry (damage amp read by DamageModifierProcessor)</li>
 * </ul>
 *
 * <p><b>Query:</b> All entities with {@link EntityStatMap}. Entities without
 * active Freeze/Shock ailments exit immediately via O(1) tracker lookup.
 *
 * @see AilmentTracker
 */
public class AilmentTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, EntityStatMap> statMapType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, UUIDComponent> uuidType;

    private final AilmentTracker tracker;
    private final AilmentConfig config;

    public AilmentTickSystem(@Nonnull AilmentTracker tracker, @Nonnull AilmentConfig config) {
        this.tracker = tracker;
        this.config = config;
        this.statMapType = EntityStatMap.getComponentType();
        this.playerRefType = PlayerRef.getComponentType();
        this.uuidType = UUIDComponent.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(statMapType);
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        if (!config.isEnabled()) return;

        UUID entityUuid = resolveEntityUuid(index, archetypeChunk, store);
        if (entityUuid == null) return;

        EntityAilmentState ailmentState = tracker.get(entityUuid);
        if (ailmentState == null) return;

        // Only process non-DOT ailments here — DOT is handled by RpgBurnTickSystem / RpgPoisonTickSystem
        boolean hasFreeze = ailmentState.hasAilment(AilmentType.FREEZE);
        boolean hasShock = ailmentState.hasAilment(AilmentType.SHOCK);
        if (!hasFreeze && !hasShock) return;

        // Tick Freeze duration
        if (hasFreeze) {
            AilmentState freeze = ailmentState.getFreeze();
            if (freeze != null) {
                AilmentState updated = freeze.afterTick(dt);
                if (updated.isExpired()) {
                    ailmentState.removeAilment(AilmentType.FREEZE);
                } else {
                    ailmentState.applyAilment(updated);
                }
            }
        }

        // Tick Shock duration
        if (hasShock) {
            AilmentState shock = ailmentState.getShock();
            if (shock != null) {
                AilmentState updated = shock.afterTick(dt);
                if (updated.isExpired()) {
                    ailmentState.removeAilment(AilmentType.SHOCK);
                } else {
                    ailmentState.applyAilment(updated);
                }
            }
        }

        // Clean up empty state
        if (!ailmentState.hasAnyAilment()) {
            tracker.cleanup(entityUuid);
        }
    }

    @Nullable
    private UUID resolveEntityUuid(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store) {

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return null;

        PlayerRef playerRef = store.getComponent(ref, playerRefType);
        if (playerRef != null) return playerRef.getUuid();

        UUIDComponent uuidComponent = store.getComponent(ref, uuidType);
        if (uuidComponent != null) return uuidComponent.getUuid();

        return null;
    }

    @Nonnull
    public AilmentTracker getTracker() {
        return tracker;
    }

    @Nonnull
    public AilmentConfig getConfig() {
        return config;
    }
}
