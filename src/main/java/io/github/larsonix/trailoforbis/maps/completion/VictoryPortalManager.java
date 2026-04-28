package io.github.larsonix.trailoforbis.maps.completion;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.util.TerrainUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages victory portals that spawn inside completed realms.
 *
 * <p>Victory portals:
 * <ul>
 *   <li>Spawn at the realm's center (0, ground level, 0) when the realm is completed</li>
 *   <li>Ground level is dynamically detected by scanning downward for solid blocks</li>
 *   <li>Allow players to exit back to the origin world</li>
 *   <li>Are removed when the realm closes</li>
 * </ul>
 *
 * <p>Unlike entry portals (managed by RealmPortalManager), victory portals
 * are temporary and exist only during the victory grace period.
 *
 * @see RealmInstance
 */
public class VictoryPortalManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Default fallback Y position for victory portal if ground detection fails. */
    private static final int FALLBACK_PORTAL_Y = 65;

    /**
     * Portal block type for victory portal.
     *
     * <p>Uses our custom RPG_Victory_Portal block which:
     * <ul>
     *   <li>Inherits visuals/particles from Portal_Return</li>
     *   <li>Uses RealmVictoryPortalInteraction instead of ReturnPortalInteraction</li>
     *   <li>Removes HUDs BEFORE calling exitInstance() to fix HUD persistence bug</li>
     * </ul>
     */
    private static final String PORTAL_BLOCK_TYPE = "RPG_Victory_Portal";

    /** Fallback portal block type. */
    private static final String FALLBACK_PORTAL_BLOCK_TYPE = "Portal_Device";

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Tracks victory portals by realm ID. */
    private final Map<UUID, VictoryPortalInfo> victoryPortals = new ConcurrentHashMap<>();

    /** Cached portal block type. */
    @Nullable
    private volatile BlockType portalBlockType;

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Spawns a victory portal at the realm's center.
     *
     * <p>The portal will teleport players back to the origin world where
     * they entered the realm from.
     *
     * @param realm The completed realm instance
     * @return A future that completes with true if the portal was created
     */
    @Nonnull
    public CompletableFuture<Boolean> spawnVictoryPortal(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "realm cannot be null");

        World realmWorld = realm.getWorld();
        if (realmWorld == null || !realmWorld.isAlive()) {
            LOGGER.atWarning().log("Cannot spawn victory portal - realm world not available");
            return CompletableFuture.completedFuture(false);
        }

        // Verify there's an origin world to return to (sanity check)
        // Portal_Return uses InstancesPlugin.exitInstance() which uses the return point
        // configured when the realm instance was spawned
        UUID originWorldId = getOriginWorldId(realm);
        if (originWorldId == null) {
            LOGGER.atWarning().log("Cannot spawn victory portal - no origin world found");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        realmWorld.execute(() -> {
            try {
                boolean success = spawnVictoryPortalInternal(realm, realmWorld);
                future.complete(success);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to spawn victory portal for realm %s",
                    realm.getRealmId().toString().substring(0, 8));
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Removes the victory portal for a realm.
     *
     * @param realm The realm whose victory portal should be removed
     */
    public void removeVictoryPortal(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "realm cannot be null");

        VictoryPortalInfo info = victoryPortals.remove(realm.getRealmId());
        if (info == null) {
            return;
        }

        World realmWorld = realm.getWorld();
        if (realmWorld == null || !realmWorld.isAlive()) {
            return;
        }

        realmWorld.execute(() -> {
            try {
                removeVictoryPortalInternal(realmWorld, info.position());
                LOGGER.atFine().log("Removed victory portal for realm %s",
                    realm.getRealmId().toString().substring(0, 8));
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to remove victory portal");
            }
        });
    }

    /**
     * Checks if a realm has an active victory portal.
     *
     * @param realmId The realm ID
     * @return true if a victory portal exists
     */
    public boolean hasVictoryPortal(@Nonnull UUID realmId) {
        return victoryPortals.containsKey(realmId);
    }

    /**
     * Clears all tracked victory portals.
     * Called during shutdown.
     */
    public void clearAll() {
        victoryPortals.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Internal method to spawn the victory portal (must be called on world thread).
     */
    private boolean spawnVictoryPortalInternal(
            @Nonnull RealmInstance realm,
            @Nonnull World realmWorld) {

        // Resolve portal block type
        BlockType blockType = resolvePortalBlockType();
        if (blockType == null) {
            LOGGER.atWarning().log("Cannot spawn victory portal - no portal block type available");
            return false;
        }

        // Find ground level at center of arena using biome-specific terrain materials.
        // The generic prefix-based check (Soil_*/Rock_*) matches decorative props
        // (Rock_Stone_Mossy, Soil_Leaves, crystals) that float the portal above terrain.
        // maxScanY avoids Caverns ceilings (Rock_Stone at Y=95).
        int maxScanY = (int) RealmTemplateRegistry.getBaseYForBiome(realm.getBiome()) + 30;
        Set<String> terrainMaterials = realm.getBiome().getTerrainMaterials();
        int groundY = TerrainUtils.findGroundLevel(realmWorld, 0, 0, maxScanY, terrainMaterials);
        Vector3i position = new Vector3i(0, groundY, 0);

        // Ensure chunk is loaded
        WorldChunk chunk = realmWorld.getChunkIfInMemory(
            ChunkUtil.indexChunkFromBlock(position.x, position.z));
        if (chunk == null) {
            LOGGER.atWarning().log("Cannot spawn victory portal - chunk not loaded at %s", position);
            return false;
        }

        // Place the Portal_Return block
        // Portal_Return uses CollisionEnter with ReturnPortalInteraction which:
        // 1. Checks PortalWorld.exists() - already initialized in RealmInstanceFactory
        // 2. Calls InstancesPlugin.exitInstance() - uses the return point from instance spawn
        try {
            chunk.setBlock(position.x, position.y, position.z, blockType.getId());
            LOGGER.atFine().log("Placed victory portal block: %s at %s", blockType.getId(), position);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to place victory portal block at %s", position);
            return false;
        }

        // Track the portal
        victoryPortals.put(realm.getRealmId(), new VictoryPortalInfo(
            realm.getRealmId(),
            position,
            realmWorld.getWorldConfig().getUuid()
        ));

        LOGGER.atInfo().log("Spawned victory portal for realm %s at %s",
            realm.getRealmId().toString().substring(0, 8), position);

        return true;
    }

    /**
     * Internal method to remove a victory portal block (must be called on world thread).
     */
    private void removeVictoryPortalInternal(@Nonnull World world, @Nonnull Vector3i position) {
        WorldChunk chunk = world.getChunkIfInMemory(
            ChunkUtil.indexChunkFromBlock(position.x, position.z));
        if (chunk == null) {
            return;
        }

        try {
            // Replace with air
            BlockType airType = BlockType.getAssetMap().getAsset("hytale:air");
            if (airType != null) {
                chunk.setBlock(position.x, position.y, position.z, airType.getId());
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error removing victory portal block at %s", position);
        }
    }

    /**
     * Resolves the portal block type.
     */
    @Nullable
    private BlockType resolvePortalBlockType() {
        if (portalBlockType != null) {
            LOGGER.atFine().log("Using cached portal block type: %s", portalBlockType.getId());
            return portalBlockType;
        }

        BlockType blockType = BlockType.getAssetMap().getAsset(PORTAL_BLOCK_TYPE);
        if (blockType != null && !blockType.isUnknown()) {
            portalBlockType = blockType;
            LOGGER.atFine().log("Resolved portal block type: %s", PORTAL_BLOCK_TYPE);
            return portalBlockType;
        }
        LOGGER.atWarning().log("Could not find portal block type '%s', trying fallback", PORTAL_BLOCK_TYPE);

        blockType = BlockType.getAssetMap().getAsset(FALLBACK_PORTAL_BLOCK_TYPE);
        if (blockType != null && !blockType.isUnknown()) {
            portalBlockType = blockType;
            LOGGER.atWarning().log("Using fallback portal block type: %s", FALLBACK_PORTAL_BLOCK_TYPE);
            return portalBlockType;
        }

        LOGGER.atSevere().log("Failed to resolve any portal block type!");
        return null;
    }

    /**
     * Gets the origin world ID from a realm.
     *
     * <p>The origin world is where players should be teleported back to.
     * Since the instance is spawned with a return destination configured by
     * Hytale's InstancesPlugin, we find the first non-realm world as the fallback.
     */
    @Nullable
    private UUID getOriginWorldId(@Nonnull RealmInstance realm) {
        // Find a suitable return world (any world that's not the realm itself)
        // The realm's instance was spawned with a return destination by InstancesPlugin,
        // so the portal will naturally lead back there
        for (World world : Universe.get().getWorlds().values()) {
            // Skip the realm world itself
            if (world.equals(realm.getWorld())) {
                continue;
            }

            // Skip worlds that appear to be realm instances
            String worldName = world.getName();
            if (worldName != null && worldName.toLowerCase().contains("realm")) {
                continue;
            }

            // Use this world as the return destination
            return world.getWorldConfig().getUuid();
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED TYPES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Information about a spawned victory portal.
     */
    private record VictoryPortalInfo(
        @Nonnull UUID realmId,
        @Nonnull Vector3i position,
        @Nonnull UUID worldUuid
    ) {
        VictoryPortalInfo {
            Objects.requireNonNull(realmId);
            Objects.requireNonNull(position);
            Objects.requireNonNull(worldUuid);
        }
    }
}
