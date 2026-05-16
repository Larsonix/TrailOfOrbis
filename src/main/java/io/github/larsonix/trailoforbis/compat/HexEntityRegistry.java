package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry mapping hex construct and projectile entities to their caster UUIDs.
 *
 * <p>Populated by {@link HexEntityTracker} via HolderSystem callbacks when
 * construct/projectile entities are added to a store. Queried by
 * {@link HexDamageAttributor} for proximity-based damage attribution.
 * Falls back to {@link HexCastStateStore} via executionId when entity refs go stale.
 */
public final class HexEntityRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final double MAX_CONSTRUCT_DIST_SQ = 30.0 * 30.0;
    private static final double MAX_PROJECTILE_DIST_SQ = 5.0 * 5.0;
    private static final int MAX_CONSTRUCTS = 500;
    private static final int MAX_PROJECTILES = 5_000;
    private static final long PROJECTILE_MAX_AGE_MS = 30_000L;
    private static final long CONSTRUCT_MAX_AGE_MS = 300_000L;

    /**
     * Info about a tracked hex entity (construct or projectile).
     */
    public record TrackedEntity(
            UUID casterUuid,
            Ref<EntityStore> entityRef,
            @Nullable String spellType,
            long timestamp,
            @Nullable UUID executionId
    ) {}

    private static final ConcurrentHashMap<UUID, TrackedEntity> constructs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, TrackedEntity> projectiles = new ConcurrentHashMap<>();

    private HexEntityRegistry() {}

    // ── Registration (called by HexEntityTracker) ──

    public static void registerConstruct(@Nonnull UUID entityUuid, @Nonnull TrackedEntity info) {
        if (constructs.size() >= MAX_CONSTRUCTS) evictOldest(constructs);
        constructs.put(entityUuid, info);
        LOGGER.atFine().log("[HexRegistry] Construct registered: entity=%s caster=%s exec=%s (total=%d)",
                entityUuid.toString().substring(0, 8),
                info.casterUuid().toString().substring(0, 8),
                info.executionId() != null ? info.executionId().toString().substring(0, 8) : "null",
                constructs.size());
    }

    public static void registerProjectile(@Nonnull UUID entityUuid, @Nonnull TrackedEntity info) {
        if (projectiles.size() >= MAX_PROJECTILES) evictOldest(projectiles);
        projectiles.put(entityUuid, info);
        LOGGER.atFine().log("[HexRegistry] Projectile registered: entity=%s caster=%s (total=%d)",
                entityUuid.toString().substring(0, 8),
                info.casterUuid().toString().substring(0, 8), projectiles.size());
    }

    public static void unregisterConstruct(@Nonnull UUID entityUuid) {
        constructs.remove(entityUuid);
    }

    public static void unregisterProjectile(@Nonnull UUID entityUuid) {
        projectiles.remove(entityUuid);
    }

    // ── Lookup (called by HexDamageAttributor) ──

    @Nullable
    public static UUID findConstructCaster(
            @Nonnull Ref<EntityStore> victimRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull String hexSourceType) {

        if (constructs.isEmpty()) return null;

        TransformComponent victimTc = store.getComponent(victimRef, TransformComponent.getComponentType());
        if (victimTc == null) return null;
        Vector3d victimPos = victimTc.getPosition();

        UUID bestCaster = null;
        double bestDistSq = MAX_CONSTRUCT_DIST_SQ;
        UUID bestExecutionFallback = null;

        for (Map.Entry<UUID, TrackedEntity> entry : constructs.entrySet()) {
            TrackedEntity tracked = entry.getValue();

            if (tracked.entityRef() == null || !tracked.entityRef().isValid()) {
                // Stale ref — try executionId deterministic fallback via HexCastStateStore
                if (tracked.executionId() != null && bestExecutionFallback == null) {
                    UUID execCaster = HexCastStateStore.getCasterForExecution(tracked.executionId());
                    if (execCaster != null) bestExecutionFallback = execCaster;
                }
                continue;
            }

            TransformComponent tc = store.getComponent(tracked.entityRef(), TransformComponent.getComponentType());
            if (tc == null) continue;

            Vector3d pos = tc.getPosition();
            double dx = pos.getX() - victimPos.getX();
            double dy = pos.getY() - victimPos.getY();
            double dz = pos.getZ() - victimPos.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestCaster = tracked.casterUuid();
            }
        }

        if (bestCaster != null) {
            LOGGER.atInfo().log("[HexRegistry] Construct attribution: %s → caster %s (dist=%.1f)",
                    hexSourceType, bestCaster.toString().substring(0, 8), Math.sqrt(bestDistSq));
            return bestCaster;
        }
        if (bestExecutionFallback != null) {
            LOGGER.atInfo().log("[HexRegistry] Construct attribution via executionId: %s → caster %s",
                    hexSourceType, bestExecutionFallback.toString().substring(0, 8));
        }
        return bestExecutionFallback;
    }

    @Nullable
    public static UUID findProjectileCaster(
            @Nonnull Ref<EntityStore> victimRef,
            @Nonnull Store<EntityStore> store) {

        if (projectiles.isEmpty()) return null;

        TransformComponent victimTc = store.getComponent(victimRef, TransformComponent.getComponentType());
        if (victimTc == null) return null;
        Vector3d victimPos = victimTc.getPosition();

        UUID bestCaster = null;
        double bestDistSq = MAX_PROJECTILE_DIST_SQ;
        UUID bestExecutionFallback = null;

        for (Map.Entry<UUID, TrackedEntity> entry : projectiles.entrySet()) {
            TrackedEntity tracked = entry.getValue();

            if (tracked.entityRef() == null || !tracked.entityRef().isValid()) {
                if (tracked.executionId() != null && bestExecutionFallback == null) {
                    UUID execCaster = HexCastStateStore.getCasterForExecution(tracked.executionId());
                    if (execCaster != null) bestExecutionFallback = execCaster;
                }
                continue;
            }

            TransformComponent tc = store.getComponent(tracked.entityRef(), TransformComponent.getComponentType());
            if (tc == null) continue;

            Vector3d pos = tc.getPosition();
            double dx = pos.getX() - victimPos.getX();
            double dy = pos.getY() - victimPos.getY();
            double dz = pos.getZ() - victimPos.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestCaster = tracked.casterUuid();
            }
        }

        if (bestCaster != null) {
            LOGGER.atInfo().log("[HexRegistry] Projectile attribution: caster %s (dist=%.1f)",
                    bestCaster.toString().substring(0, 8), Math.sqrt(bestDistSq));
            return bestCaster;
        }
        if (bestExecutionFallback != null) {
            LOGGER.atInfo().log("[HexRegistry] Projectile attribution via executionId: caster %s",
                    bestExecutionFallback.toString().substring(0, 8));
        }
        return bestExecutionFallback;
    }

    @Nullable
    public static Ref<EntityStore> resolveEntityRef(@Nonnull UUID casterUuid, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(casterUuid);
        return (ref != null && ref.isValid() && ref.getStore() == store) ? ref : null;
    }

    public static void clear() {
        constructs.clear();
        projectiles.clear();
    }

    public static void cleanupStale() {
        long now = System.currentTimeMillis();
        int removed = 0;

        var it = constructs.entrySet().iterator();
        while (it.hasNext()) {
            TrackedEntity t = it.next().getValue();
            if (t.entityRef() == null || !t.entityRef().isValid()
                    || (now - t.timestamp()) > CONSTRUCT_MAX_AGE_MS) {
                it.remove();
                removed++;
            }
        }

        var it2 = projectiles.entrySet().iterator();
        while (it2.hasNext()) {
            TrackedEntity t = it2.next().getValue();
            if (t.entityRef() == null || !t.entityRef().isValid()
                    || (now - t.timestamp()) > PROJECTILE_MAX_AGE_MS) {
                it2.remove();
                removed++;
            }
        }

        if (removed > 0) {
            LOGGER.atFine().log("[HexRegistry] Cleaned up %d stale entries (constructs=%d, projectiles=%d)",
                    removed, constructs.size(), projectiles.size());
        }
    }

    public static int getConstructCount() { return constructs.size(); }
    public static int getProjectileCount() { return projectiles.size(); }

    private static void evictOldest(@Nonnull ConcurrentHashMap<UUID, TrackedEntity> map) {
        UUID oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (var entry : map.entrySet()) {
            if (entry.getValue().timestamp() < oldestTime) {
                oldestTime = entry.getValue().timestamp();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) map.remove(oldestKey);
    }
}
