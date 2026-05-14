package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.AddReason;
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

// Direct Hexcode imports — this class is only registered when Hexcode is present
// (gated behind HexcodeCompat.isLoaded() in TrailOfOrbis.registerEcsSystems())
import com.riprod.hexcode.core.common.construct.component.HexEffectsComponent;
import com.riprod.hexcode.core.common.construct.component.HexStatus;
import com.riprod.hexcode.core.state.execution.component.HexContext;
import com.riprod.hexcode.builtin.glyphs.projectile.component.ProjectileState;
import com.riprod.hexcode.builtin.glyphs.shatter.component.ShatterState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * HolderSystem that tracks Hexcode construct and projectile entities,
 * extracting caster identity via direct API and registering it in
 * {@link HexEntityRegistry} for damage attribution.
 *
 * <p>Uses {@code Query.any()} with fast early-exit checks. The first two
 * checks ({@code isTrackingInitialized} and {@code UUIDComponent}) filter
 * out &gt;99% of entities before any Hexcode component access is attempted.
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

        // Check Hexcode components via direct API (cheapest first — most entities have none)
        if (tryTrackConstruct(store, entityUuid, entityRef)) {
            return;
        }
        tryTrackProjectile(store, entityUuid, entityRef);
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason,
                                @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComp = holder.getComponent(UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return;
        }
        HexEntityRegistry.unregisterConstruct(uuidComp.getUuid());
        HexEntityRegistry.unregisterProjectile(uuidComp.getUuid());
    }

    @SuppressWarnings("unchecked")
    private boolean tryTrackConstruct(
            @Nonnull Store<EntityStore> store,
            @Nonnull UUID entityUuid,
            @Nonnull Ref<EntityStore> entityRef) {

        // Direct API: no reflection
        HexEffectsComponent hexEffects = store.getComponent(entityRef, HexEffectsComponent.getComponentType());
        if (hexEffects == null) {
            return false;
        }

        // Extract caster and executionId from the first valid effect
        UUID casterUuid = null;
        UUID executionId = null;
        Map<?, HexStatus<?>> effects = hexEffects.getEffects();
        if (effects != null) {
            for (HexStatus<?> status : effects.values()) {
                HexContext ctx = status.getHexContext();
                if (ctx == null) continue;
                Ref<EntityStore> casterRef = ctx.getCasterRef();
                casterUuid = extractCasterUuid(casterRef, store);
                if (casterUuid != null) {
                    executionId = ctx.getExecutionId();
                    break;
                }
            }
        }

        if (casterUuid == null) {
            return false;
        }

        HexEntityRegistry.registerConstruct(entityUuid, new HexEntityRegistry.TrackedEntity(
                casterUuid, entityRef, null, System.currentTimeMillis(), executionId));
        return true;
    }

    private void tryTrackProjectile(
            @Nonnull Store<EntityStore> store,
            @Nonnull UUID entityUuid,
            @Nonnull Ref<EntityStore> entityRef) {

        // Try ProjectileState first (direct API)
        ProjectileState projState = store.getComponent(entityRef, ProjectileState.getComponentType());
        if (projState != null) {
            HexContext projCtx = projState.getHexContext();
            UUID casterUuid = extractCasterFromContext(projCtx, store);
            if (casterUuid != null) {
                UUID executionId = projCtx != null ? projCtx.getExecutionId() : null;
                HexEntityRegistry.registerProjectile(entityUuid, new HexEntityRegistry.TrackedEntity(
                        casterUuid, entityRef, null, System.currentTimeMillis(), executionId));
                return;
            }
        }

        // Try ShatterState (direct API)
        ShatterState shatterState = store.getComponent(entityRef, ShatterState.getComponentType());
        if (shatterState != null) {
            HexContext shatterCtx = shatterState.getHexContext();
            UUID casterUuid = extractCasterFromContext(shatterCtx, store);
            if (casterUuid != null) {
                UUID executionId = shatterCtx != null ? shatterCtx.getExecutionId() : null;
                HexEntityRegistry.registerProjectile(entityUuid, new HexEntityRegistry.TrackedEntity(
                        casterUuid, entityRef, null, System.currentTimeMillis(), executionId));
            }
        }
    }

    @Nullable
    private UUID extractCasterFromContext(@Nullable HexContext ctx,
                                          @Nonnull Store<EntityStore> store) {
        if (ctx == null) return null;
        Ref<EntityStore> casterRef = ctx.getCasterRef();
        return extractCasterUuid(casterRef, store);
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
