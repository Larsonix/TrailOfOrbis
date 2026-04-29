package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * HolderSystem that tracks Hexcode construct and projectile entities,
 * extracting caster identity via reflection and registering it in
 * {@link HexCasterRegistry} for damage attribution.
 *
 * <p>Uses {@code Query.any()} with fast early-exit checks. The first two
 * checks ({@code isTrackingInitialized} and {@code UUIDComponent}) filter
 * out &gt;99% of entities before any Hexcode reflection is attempted.
 */
public class HexEntityTracker extends HolderSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason,
                            @Nonnull Store<EntityStore> store) {
        // Fast exit: skip if Hexcode tracking not initialized
        if (!HexcodeCompat.isTrackingInitialized()) {
            return;
        }

        // Fast exit: skip entities without UUID (particles, effects, etc.)
        UUIDComponent uuidComp = holder.getComponent(UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return;
        }

        UUID entityUuid = uuidComp.getUuid();
        Ref<EntityStore> entityRef = store.getExternalData().getRefFromUUID(entityUuid);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        // Check Hexcode components (cheapest first — most entities have none)
        if (tryTrackConstruct(holder, store, entityUuid, entityRef)) {
            return;
        }
        tryTrackProjectile(holder, store, entityUuid, entityRef);
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason,
                                @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComp = holder.getComponent(UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return;
        }
        HexCasterRegistry.unregisterConstruct(uuidComp.getUuid());
        HexCasterRegistry.unregisterProjectile(uuidComp.getUuid());
    }

    private boolean tryTrackConstruct(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull Store<EntityStore> store,
            @Nonnull UUID entityUuid,
            @Nonnull Ref<EntityStore> entityRef) {

        ComponentType<EntityStore, ?> hexEffectsType = HexcodeCompat.getHexEffectsComponentType();
        if (hexEffectsType == null || holder.getComponent(hexEffectsType) == null) {
            return false;
        }

        UUID casterUuid = extractCasterUuid(
                HexcodeCompat.extractConstructCasterRef(store, entityRef), store);
        if (casterUuid == null) {
            return false;
        }

        HexCasterRegistry.registerConstruct(entityUuid, new HexCasterRegistry.TrackedEntity(
                casterUuid, entityRef, null, System.currentTimeMillis()));
        return true;
    }

    private void tryTrackProjectile(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull Store<EntityStore> store,
            @Nonnull UUID entityUuid,
            @Nonnull Ref<EntityStore> entityRef) {

        ComponentType<EntityStore, ?> projType = HexcodeCompat.getProjectileStateComponentType();
        if (projType != null && holder.getComponent(projType) != null) {
            UUID casterUuid = extractCasterUuid(
                    HexcodeCompat.extractProjectileCasterRef(store, entityRef), store);
            if (casterUuid != null) {
                HexCasterRegistry.registerProjectile(entityUuid, new HexCasterRegistry.TrackedEntity(
                        casterUuid, entityRef, null, System.currentTimeMillis()));
                return;
            }
        }

        ComponentType<EntityStore, ?> shatterType = HexcodeCompat.getShatterStateComponentType();
        if (shatterType != null && holder.getComponent(shatterType) != null) {
            UUID casterUuid = extractCasterUuid(
                    HexcodeCompat.extractProjectileCasterRef(store, entityRef), store);
            if (casterUuid != null) {
                HexCasterRegistry.registerProjectile(entityUuid, new HexCasterRegistry.TrackedEntity(
                        casterUuid, entityRef, null, System.currentTimeMillis()));
            }
        }
    }

    @Nullable
    private UUID extractCasterUuid(@Nullable Ref<EntityStore> casterRef,
                                   @Nonnull Store<EntityStore> store) {
        if (casterRef == null || !casterRef.isValid()) {
            return null;
        }
        PlayerRef playerRef = store.getComponent(casterRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            return playerRef.getUuid();
        }
        UUIDComponent uuidComp = store.getComponent(casterRef, UUIDComponent.getComponentType());
        return uuidComp != null ? uuidComp.getUuid() : null;
    }
}
