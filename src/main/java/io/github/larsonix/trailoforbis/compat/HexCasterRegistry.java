package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
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
 * {@link HexDamageAttributionSystem} when construct or projectile damage
 * needs attribution.
 */
public final class HexCasterRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Maximum distance (squared) between a construct and a victim for attribution. */
    private static final double MAX_CONSTRUCT_DIST_SQ = 30.0 * 30.0;

    /** Maximum distance (squared) between a projectile and a victim for attribution. */
    private static final double MAX_PROJECTILE_DIST_SQ = 5.0 * 5.0;

    /**
     * Info about a tracked hex entity (construct or projectile).
     *
     * @param casterUuid The UUID of the player who cast the spell
     * @param entityRef  The entity reference (may become invalid if entity despawns)
     * @param spellType  The source type (e.g., "hex_ensnare", "hex_bolt")
     * @param timestamp  When the entity was registered (for staleness checks)
     */
    public record TrackedEntity(
            UUID casterUuid,
            Ref<EntityStore> entityRef,
            @Nullable String spellType,
            long timestamp
    ) {}

    /** Construct entities: constructEntityUUID → caster info. */
    private static final ConcurrentHashMap<UUID, TrackedEntity> constructs = new ConcurrentHashMap<>();

    /** Projectile entities: projectileEntityUUID → caster info. */
    private static final ConcurrentHashMap<UUID, TrackedEntity> projectiles = new ConcurrentHashMap<>();

    private HexCasterRegistry() {}

    // =========================================================================
    // REGISTRATION (called by HexEntityTracker)
    // =========================================================================

    public static void registerConstruct(@Nonnull UUID entityUuid, @Nonnull TrackedEntity info) {
        constructs.put(entityUuid, info);
        LOGGER.atFine().log("[HexRegistry] Construct registered: entity=%s caster=%s type=%s",
                entityUuid.toString().substring(0, 8),
                info.casterUuid().toString().substring(0, 8),
                info.spellType());
    }

    public static void registerProjectile(@Nonnull UUID entityUuid, @Nonnull TrackedEntity info) {
        projectiles.put(entityUuid, info);
        LOGGER.atFine().log("[HexRegistry] Projectile registered: entity=%s caster=%s",
                entityUuid.toString().substring(0, 8),
                info.casterUuid().toString().substring(0, 8));
    }

    public static void unregisterConstruct(@Nonnull UUID entityUuid) {
        TrackedEntity removed = constructs.remove(entityUuid);
        if (removed != null) {
            LOGGER.atFine().log("[HexRegistry] Construct unregistered: entity=%s",
                    entityUuid.toString().substring(0, 8));
        }
    }

    public static void unregisterProjectile(@Nonnull UUID entityUuid) {
        TrackedEntity removed = projectiles.remove(entityUuid);
        if (removed != null) {
            LOGGER.atFine().log("[HexRegistry] Projectile unregistered: entity=%s",
                    entityUuid.toString().substring(0, 8));
        }
    }

    // =========================================================================
    // LOOKUP (called by HexDamageAttributionSystem)
    // =========================================================================

    /**
     * Finds the caster UUID for a construct-type spell by looking up the nearest
     * construct entity of the matching type to the victim.
     *
     * @param victimRef    The entity that took damage
     * @param store        The entity store
     * @param hexSourceType The hex source type (e.g., "hex_ensnare")
     * @return The caster's UUID, or null if no matching construct found
     */
    @Nullable
    public static UUID findConstructCaster(
            @Nonnull Ref<EntityStore> victimRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull String hexSourceType) {

        if (constructs.isEmpty()) {
            return null;
        }

        TransformComponent victimTc = store.getComponent(victimRef, TransformComponent.getComponentType());
        if (victimTc == null) {
            return null;
        }
        Vector3d victimPos = victimTc.getPosition();

        UUID bestCaster = null;
        double bestDistSq = MAX_CONSTRUCT_DIST_SQ;

        for (Map.Entry<UUID, TrackedEntity> entry : constructs.entrySet()) {
            TrackedEntity tracked = entry.getValue();

            // Entity ref must still be valid
            if (tracked.entityRef() == null || !tracked.entityRef().isValid()) {
                continue;
            }

            TransformComponent tc = store.getComponent(tracked.entityRef(), TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }

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
        }

        return bestCaster;
    }

    /**
     * Finds the caster UUID for a projectile-delivered spell by looking up the nearest
     * projectile entity to the victim.
     *
     * @param victimRef The entity that took damage
     * @param store     The entity store
     * @return The caster's UUID, or null if no matching projectile found
     */
    @Nullable
    public static UUID findProjectileCaster(
            @Nonnull Ref<EntityStore> victimRef,
            @Nonnull Store<EntityStore> store) {

        if (projectiles.isEmpty()) {
            return null;
        }

        TransformComponent victimTc = store.getComponent(victimRef, TransformComponent.getComponentType());
        if (victimTc == null) {
            return null;
        }
        Vector3d victimPos = victimTc.getPosition();

        UUID bestCaster = null;
        double bestDistSq = MAX_PROJECTILE_DIST_SQ;

        for (Map.Entry<UUID, TrackedEntity> entry : projectiles.entrySet()) {
            TrackedEntity tracked = entry.getValue();

            if (tracked.entityRef() == null || !tracked.entityRef().isValid()) {
                continue;
            }

            TransformComponent tc = store.getComponent(tracked.entityRef(), TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }

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
        }

        return bestCaster;
    }

    /**
     * Resolves a caster UUID to an entity ref in the given store.
     *
     * @param casterUuid The caster's UUID
     * @param store      The entity store
     * @return The caster's entity ref, or null if not in this store/world
     */
    @Nullable
    public static Ref<EntityStore> resolveEntityRef(
            @Nonnull UUID casterUuid,
            @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(casterUuid);
        return (ref != null && ref.isValid()) ? ref : null;
    }

    /**
     * Clears all tracked entities. Called during shutdown.
     */
    public static void clear() {
        constructs.clear();
        projectiles.clear();
    }

    /**
     * Removes stale entries where entity refs are no longer valid.
     * Call periodically (e.g., every 30 seconds) as a safety net.
     */
    public static void cleanupStale() {
        int removed = 0;
        var it = constructs.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().entityRef() == null || !entry.getValue().entityRef().isValid()) {
                it.remove();
                removed++;
            }
        }
        var it2 = projectiles.entrySet().iterator();
        while (it2.hasNext()) {
            var entry = it2.next();
            if (entry.getValue().entityRef() == null || !entry.getValue().entityRef().isValid()) {
                it2.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.atFine().log("[HexRegistry] Cleaned up %d stale entries (constructs=%d, projectiles=%d)",
                    removed, constructs.size(), projectiles.size());
        }
    }

    /** Returns the number of tracked constructs (for diagnostics). */
    public static int getConstructCount() {
        return constructs.size();
    }

    /** Returns the number of tracked projectiles (for diagnostics). */
    public static int getProjectileCount() {
        return projectiles.size();
    }
}
