package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.builtin.portals.PortalsPlugin;
import com.hypixel.hytale.builtin.portals.components.PortalDevice;
import com.hypixel.hytale.builtin.portals.components.PortalDeviceConfig;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.math.util.ChunkUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages portal blocks that connect the overworld to realm instances.
 *
 * <p>This manager handles:
 * <ul>
 *   <li>Creating portal blocks at specified positions</li>
 *   <li>Configuring portal devices with realm destinations</li>
 *   <li>Tracking portal-to-realm mappings</li>
 *   <li>Removing portals when realms close</li>
 *   <li>Looking up which realm a portal connects to</li>
 * </ul>
 *
 * <h2>Portal Creation Flow</h2>
 * <ol>
 *   <li>Place portal block at position</li>
 *   <li>Ensure block entity exists</li>
 *   <li>Attach PortalDevice component with destination world</li>
 *   <li>Track the portal-realm association</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>All world modifications are executed on the world's thread via {@code world.execute()}.
 * The portal tracking maps use concurrent collections.
 *
 * @see PortalDevice
 * @see RealmInstance
 */
public class RealmPortalManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Default portal block type key. This should be a portal-enabled block
     * defined in the game's assets.
     * Note: Hytale uses Pascal_Snake_Case for asset names (e.g., "Portal_Device", not "hytale:portal_device")
     */
    private static final String DEFAULT_PORTAL_BLOCK_TYPE = "Portal_Device";

    /**
     * Fallback portal block type if the default doesn't exist.
     * Instance_Gateway is a developer-quality portal that can teleport to configured instances.
     */
    private static final String FALLBACK_PORTAL_BLOCK_TYPE = "Instance_Gateway";

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Maps portal positions to realm IDs.
     * Key format: "worldUuid:x:y:z"
     */
    private final Map<String, UUID> portalToRealm = new ConcurrentHashMap<>();

    /**
     * Maps realm IDs to their portal positions.
     */
    private final Map<UUID, Set<PortalPosition>> realmToPortals = new ConcurrentHashMap<>();

    /**
     * Maps portal positions to the player who created them.
     * Used for personal portals that only the creator can use.
     */
    private final Map<String, UUID> portalToOwner = new ConcurrentHashMap<>();

    /**
     * Maps portal positions to their creation time.
     * Used for timeout-based cleanup.
     */
    private final Map<String, Instant> portalCreationTime = new ConcurrentHashMap<>();

    /**
     * Tracks portals that were activated (not spawned by us).
     * These are existing Portal_Device blocks that we configured with a destination.
     * When the realm closes, these should be deactivated (not removed).
     */
    private final Set<String> activatedDevices = ConcurrentHashMap.newKeySet();

    /**
     * Portal entry timeout in seconds (default: 2 minutes).
     */
    private int portalEntryTimeoutSeconds = 120;

    /**
     * The block type to use for portals.
     */
    @Nullable
    private volatile BlockType portalBlockType;

    // ═══════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new RealmPortalManager.
     */
    public RealmPortalManager() {
        // Block type is resolved lazily when first needed
    }

    /**
     * Resolves the portal block type from assets.
     *
     * @return The portal block type, or null if not found
     */
    @Nullable
    private BlockType resolvePortalBlockType() {
        if (portalBlockType != null) {
            return portalBlockType;
        }

        // Try default first
        BlockType blockType = BlockType.getAssetMap().getAsset(DEFAULT_PORTAL_BLOCK_TYPE);
        if (blockType != null && !blockType.isUnknown()) {
            portalBlockType = blockType;
            LOGGER.atInfo().log("Using portal block type: %s", DEFAULT_PORTAL_BLOCK_TYPE);
            return portalBlockType;
        }

        // Try fallback
        blockType = BlockType.getAssetMap().getAsset(FALLBACK_PORTAL_BLOCK_TYPE);
        if (blockType != null && !blockType.isUnknown()) {
            portalBlockType = blockType;
            LOGGER.atInfo().log("Using fallback portal block type: %s", FALLBACK_PORTAL_BLOCK_TYPE);
            return portalBlockType;
        }

        LOGGER.atWarning().log("No portal block type found - portal placement will fail");
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PORTAL CREATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a portal block linking to a realm instance.
     *
     * @param sourceWorld The world where the portal will be placed
     * @param position The block position for the portal
     * @param realm The realm instance to link to
     * @return A future that completes with true if the portal was created
     */
    @Nonnull
    public CompletableFuture<Boolean> createPortal(
            @Nonnull World sourceWorld,
            @Nonnull Vector3i position,
            @Nonnull RealmInstance realm) {

        Objects.requireNonNull(sourceWorld, "Source world cannot be null");
        Objects.requireNonNull(position, "Position cannot be null");
        Objects.requireNonNull(realm, "Realm cannot be null");

        World realmWorld = realm.getWorld();
        if (realmWorld == null || !realmWorld.isAlive()) {
            LOGGER.atWarning().log("Cannot create portal for realm %s - world not ready",
                realm.getRealmId());
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        sourceWorld.execute(() -> {
            try {
                boolean success = createPortalInternal(sourceWorld, position, realm, realmWorld);
                future.complete(success);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to create portal at %s for realm %s",
                    position, realm.getRealmId());
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Internal portal creation logic (must be called on world thread).
     */
    private boolean createPortalInternal(
            @Nonnull World sourceWorld,
            @Nonnull Vector3i position,
            @Nonnull RealmInstance realm,
            @Nonnull World realmWorld) {

        // Get portal block type
        BlockType blockType = resolvePortalBlockType();
        if (blockType == null) {
            LOGGER.atWarning().log("Cannot create portal - no portal block type available");
            return false;
        }

        // Ensure chunk is loaded
        WorldChunk chunk = sourceWorld.getChunkIfInMemory(
            ChunkUtil.indexChunkFromBlock(position.x, position.z));
        if (chunk == null) {
            LOGGER.atWarning().log("Cannot create portal at %s - chunk not loaded", position);
            return false;
        }

        // Place the portal block
        boolean placed = placePortalBlock(chunk, position, blockType);
        if (!placed) {
            LOGGER.atWarning().log("Failed to place portal block at %s", position);
            return false;
        }

        // Configure the portal device component
        boolean configured = configurePortalDevice(sourceWorld, position, realmWorld, blockType);
        if (!configured) {
            LOGGER.atWarning().log("Failed to configure portal device at %s", position);
            // Attempt to remove the placed block
            removePortalBlockInternal(chunk, position);
            return false;
        }

        // Track the portal
        trackPortal(sourceWorld, position, realm.getRealmId());

        LOGGER.atInfo().log("Created portal at %s in world %s linking to realm %s",
            position, sourceWorld.getName(), realm.getRealmId());

        return true;
    }

    /**
     * Creates a portal block for a specific player, with ownership tracking and timeout.
     *
     * <p>The portal will be automatically removed after the configured timeout
     * if the player doesn't enter. Use {@link #processPortalTimeouts()} to
     * trigger cleanup of expired portals.
     *
     * @param sourceWorld The world where the portal will be placed
     * @param position    The block position for the portal
     * @param realm       The realm instance to link to
     * @param ownerId     The UUID of the player who owns this portal
     * @return A future that completes with true if the portal was created
     */
    @Nonnull
    public CompletableFuture<Boolean> createPortalForPlayer(
            @Nonnull World sourceWorld,
            @Nonnull Vector3i position,
            @Nonnull RealmInstance realm,
            @Nonnull UUID ownerId) {

        Objects.requireNonNull(ownerId, "Owner ID cannot be null");

        return createPortal(sourceWorld, position, realm)
            .thenApply(success -> {
                if (success) {
                    // Track ownership and creation time
                    String key = createPortalKey(sourceWorld, position);
                    portalToOwner.put(key, ownerId);
                    portalCreationTime.put(key, Instant.now());

                    LOGGER.atInfo().log("Created personal portal at %s for player %s (timeout: %ds)",
                        position, ownerId.toString().substring(0, 8), portalEntryTimeoutSeconds);
                }
                return success;
            });
    }

    // ═══════════════════════════════════════════════════════════════════
    // PORTAL TIMEOUT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Processes expired portals and removes them.
     *
     * <p>Call this periodically (e.g., every second) from the realm manager's tick.
     *
     * @return Number of portals removed due to timeout
     */
    public int processPortalTimeouts() {
        if (portalCreationTime.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        int removedCount = 0;

        for (Map.Entry<String, Instant> entry : portalCreationTime.entrySet()) {
            String key = entry.getKey();
            Instant creationTime = entry.getValue();

            // Check if portal has expired
            long ageSeconds = now.getEpochSecond() - creationTime.getEpochSecond();
            if (ageSeconds >= portalEntryTimeoutSeconds) {
                // Parse the key to get world and position
                PortalKeyInfo keyInfo = parsePortalKey(key);
                if (keyInfo != null) {
                    World world = keyInfo.getWorld();
                    if (world != null && world.isAlive()) {
                        // Remove the portal
                        removePortal(world, keyInfo.position())
                            .thenAccept(success -> {
                                if (success) {
                                    LOGGER.atInfo().log("Removed expired portal at %s (age: %ds)",
                                        keyInfo.position(), ageSeconds);
                                }
                            });
                    }
                }

                // Clean up tracking maps (even if world removal failed)
                portalCreationTime.remove(key);
                portalToOwner.remove(key);
                removedCount++;
            }
        }

        return removedCount;
    }

    /**
     * Gets the owner of a portal.
     *
     * @param world    The world containing the portal
     * @param position The portal position
     * @return The owner's UUID, or empty if no owner or portal not found
     */
    @Nonnull
    public Optional<UUID> getPortalOwner(@Nonnull World world, @Nonnull Vector3i position) {
        String key = createPortalKey(world, position);
        return Optional.ofNullable(portalToOwner.get(key));
    }

    /**
     * Checks if a player is the owner of a portal.
     *
     * @param world    The world containing the portal
     * @param position The portal position
     * @param playerId The player ID to check
     * @return true if the player owns the portal, or if the portal has no owner
     */
    public boolean isPortalOwnerOrUnowned(@Nonnull World world, @Nonnull Vector3i position, @Nonnull UUID playerId) {
        Optional<UUID> owner = getPortalOwner(world, position);
        return owner.isEmpty() || owner.get().equals(playerId);
    }

    /**
     * Sets the portal entry timeout.
     *
     * @param timeoutSeconds Timeout in seconds
     */
    public void setPortalEntryTimeoutSeconds(int timeoutSeconds) {
        this.portalEntryTimeoutSeconds = Math.max(10, timeoutSeconds); // Minimum 10 seconds
    }

    /**
     * Gets the portal entry timeout.
     *
     * @return Timeout in seconds
     */
    public int getPortalEntryTimeoutSeconds() {
        return portalEntryTimeoutSeconds;
    }

    /**
     * Parses a portal key back into world UUID and position.
     */
    @Nullable
    private PortalKeyInfo parsePortalKey(@Nonnull String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            UUID worldUuid = UUID.fromString(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new PortalKeyInfo(worldUuid, new Vector3i(x, y, z));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Internal record for parsed portal key information.
     */
    private record PortalKeyInfo(UUID worldUuid, Vector3i position) {
        @Nullable
        World getWorld() {
            return com.hypixel.hytale.server.core.universe.Universe.get().getWorld(worldUuid);
        }
    }

    /**
     * Places a portal block at the specified position.
     */
    private boolean placePortalBlock(
            @Nonnull WorldChunk chunk,
            @Nonnull Vector3i position,
            @Nonnull BlockType blockType) {

        try {
            // Set the block type
            chunk.setBlock(position.x, position.y, position.z, blockType.getId());
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error placing portal block at %s", position);
            return false;
        }
    }

    /**
     * Configures the PortalDevice component for the portal block.
     * <p>
     * Note: Uses deprecated {@code ensureBlockEntity} as there is no
     * non-deprecated alternative for creating block entities.
     */
    @SuppressWarnings("deprecation")
    private boolean configurePortalDevice(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull World destinationWorld,
            @Nonnull BlockType blockType) {

        try {
            // Ensure block entity exists
            WorldChunk chunk = world.getChunkIfInMemory(
                ChunkUtil.indexChunkFromBlock(position.x, position.z));
            if (chunk == null) {
                return false;
            }

            Ref<ChunkStore> blockEntityRef = BlockModule.ensureBlockEntity(
                chunk, position.x, position.y, position.z);
            if (blockEntityRef == null || !blockEntityRef.isValid()) {
                LOGGER.atWarning().log("Could not ensure block entity at %s", position);
                return false;
            }

            // Get or create PortalDevice component
            Store<ChunkStore> store = world.getChunkStore().getStore();
            ComponentType<ChunkStore, PortalDevice> portalDeviceType =
                PortalsPlugin.getInstance().getPortalDeviceComponentType();

            PortalDevice existingDevice = store.getComponent(blockEntityRef, portalDeviceType);
            if (existingDevice != null) {
                // Update existing device
                existingDevice.setDestinationWorld(destinationWorld);
            } else {
                // Create new PortalDevice with default config
                PortalDeviceConfig config = createDefaultPortalConfig();
                PortalDevice portalDevice = new PortalDevice(config, blockType.getId());
                portalDevice.setDestinationWorld(destinationWorld);

                // Attach the component
                store.putComponent(blockEntityRef, portalDeviceType, portalDevice);
            }

            return true;

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error configuring portal device at %s", position);
            return false;
        }
    }

    /**
     * Creates a default portal device configuration.
     */
    @Nonnull
    private PortalDeviceConfig createDefaultPortalConfig() {
        // Use the default PortalDeviceConfig
        // The actual config structure depends on Hytale's implementation
        return new PortalDeviceConfig();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PORTAL REMOVAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Removes a portal at the specified position.
     *
     * @param world The world containing the portal
     * @param position The portal position
     * @return A future that completes with true if the portal was removed
     */
    @Nonnull
    public CompletableFuture<Boolean> removePortal(
            @Nonnull World world,
            @Nonnull Vector3i position) {

        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(position, "Position cannot be null");

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        world.execute(() -> {
            try {
                // Get chunk
                WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(position.x, position.z));
                if (chunk == null) {
                    future.complete(false);
                    return;
                }

                // Remove tracking first
                String key = createPortalKey(world, position);
                UUID realmId = portalToRealm.remove(key);
                if (realmId != null) {
                    Set<PortalPosition> portals = realmToPortals.get(realmId);
                    if (portals != null) {
                        portals.removeIf(p -> p.matches(world, position));
                        if (portals.isEmpty()) {
                            realmToPortals.remove(realmId);
                        }
                    }
                }

                // Clean up owner and creation time tracking
                portalToOwner.remove(key);
                portalCreationTime.remove(key);

                // Remove the block
                removePortalBlockInternal(chunk, position);

                LOGGER.atInfo().log("Removed portal at %s in world %s", position, world.getName());
                future.complete(true);

            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to remove portal at %s", position);
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Removes all portals associated with a realm.
     *
     * <p>This completely removes the portal blocks from the world when a realm closes.
     * Portals are removed entirely so they don't persist after the realm is gone.
     *
     * @param realm The realm whose portals should be removed
     * @return A future that completes when all portals are removed
     */
    @Nonnull
    public CompletableFuture<Integer> removeAllPortalsForRealm(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "Realm cannot be null");

        UUID realmId = realm.getRealmId();
        Set<PortalPosition> portals = realmToPortals.remove(realmId);

        if (portals == null || portals.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        // Remove all portal blocks entirely when realm closes
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (PortalPosition portal : portals) {
            String key = portal.toKey();
            portalToRealm.remove(key);
            portalToOwner.remove(key);
            portalCreationTime.remove(key);

            World world = portal.getWorld();
            if (world != null && world.isAlive()) {
                // Fully remove the portal block (replaces with air)
                futures.add(removePortal(world, portal.position()));
            }
        }

        // Wait for all removals
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v -> {
                int removed = (int) futures.stream()
                    .filter(f -> {
                        try {
                            return f.getNow(false);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .count();

                LOGGER.atInfo().log("Removed %d portals for realm %s", removed, realmId);
                return removed;
            });
    }

    /**
     * Deactivates all portals associated with a realm.
     *
     * <p>This method handles two types of portals differently:
     * <ul>
     *   <li><b>Activated devices</b> (tracked via {@link #trackPortalRealm}): The portal block
     *       is kept in place but deactivated (destination cleared, state set to Inactive).
     *       This allows the portal to be reused with a new realm map.</li>
     *   <li><b>Spawned portals</b> (created via {@link #createPortal}): The portal block is
     *       completely removed (replaced with air).</li>
     * </ul>
     *
     * @param realm The realm whose portals should be deactivated
     * @return A future that completes with the number of portals processed
     */
    @Nonnull
    public CompletableFuture<Integer> deactivateAllPortalsForRealm(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "Realm cannot be null");

        UUID realmId = realm.getRealmId();
        LOGGER.atInfo().log("Deactivating portals for realm %s (tracked portals: %d, activated devices: %d)",
            realmId.toString().substring(0, 8),
            realmToPortals.containsKey(realmId) ? realmToPortals.get(realmId).size() : 0,
            activatedDevices.size());

        Set<PortalPosition> portals = realmToPortals.remove(realmId);

        if (portals == null || portals.isEmpty()) {
            LOGGER.atInfo().log("No portals tracked for realm %s", realmId.toString().substring(0, 8));
            return CompletableFuture.completedFuture(0);
        }

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        int deactivatedCount = 0;
        int removedCount = 0;

        for (PortalPosition portal : portals) {
            String key = portal.toKey();
            LOGGER.atFine().log("Processing portal at %s (key: %s, isActivatedDevice: %b)",
                portal.position(), key, activatedDevices.contains(key));

            portalToRealm.remove(key);
            portalToOwner.remove(key);
            portalCreationTime.remove(key);

            World world = portal.getWorld();
            if (world != null && world.isAlive()) {
                // Check if this is an activated device (should deactivate) or spawned portal (should remove)
                if (activatedDevices.remove(key)) {
                    // Deactivate the portal (keep the block, clear destination)
                    LOGGER.atInfo().log("Deactivating portal device at %s", portal.position());
                    futures.add(deactivatePortal(world, portal.position()));
                    deactivatedCount++;
                } else {
                    // Remove the portal block entirely
                    LOGGER.atInfo().log("Removing spawned portal at %s", portal.position());
                    futures.add(removePortal(world, portal.position()));
                    removedCount++;
                }
            } else {
                LOGGER.atWarning().log("Cannot process portal at %s - world is null or not alive",
                    portal.position());
            }
        }

        final int finalDeactivated = deactivatedCount;
        final int finalRemoved = removedCount;

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v -> {
                LOGGER.atInfo().log("Completed portal cleanup for realm %s (%d deactivated, %d removed)",
                    realmId.toString().substring(0, 8), finalDeactivated, finalRemoved);
                return finalDeactivated + finalRemoved;
            });
    }

    /**
     * Deactivates a portal without removing the block.
     *
     * <p>This sets the block state to inactive (using the PortalDevice config's offState),
     * but keeps the Portal_Device block in place so it can be reactivated with another realm map.
     *
     * <p>Note: We intentionally do NOT call {@code setDestinationWorld(null)} because Hytale's
     * implementation dereferences the parameter without a null check, causing an NPE.
     * The stale destination UUID is harmless — {@code getDestinationWorld()} already returns
     * null for destroyed worlds.
     *
     * @param world The world containing the portal
     * @param position The portal block position
     * @return A future that completes with true if deactivation succeeded
     */
    @Nonnull
    public CompletableFuture<Boolean> deactivatePortal(
            @Nonnull World world,
            @Nonnull Vector3i position) {

        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(position, "Position cannot be null");

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        world.execute(() -> {
            try {
                // Remove tracking
                String key = createPortalKey(world, position);
                portalToRealm.remove(key);
                portalToOwner.remove(key);
                portalCreationTime.remove(key);

                // Get offState from the portal device config (default: "default")
                String offState = "default";
                PortalDevice portalDevice = getPortalDevice(world, position);
                if (portalDevice != null && portalDevice.getConfig() != null) {
                    offState = portalDevice.getConfig().getOffState();
                }

                // Set block state to inactive
                BlockType blockType = world.getBlockType(position.x, position.y, position.z);
                if (blockType != null) {
                    world.setBlockInteractionState(position, blockType, offState);
                    LOGGER.atFine().log("Set portal at %s to %s state", position, offState);
                }

                LOGGER.atInfo().log("Deactivated portal at %s (state set to %s)", position, offState);
                future.complete(true);

            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to deactivate portal at %s", position);
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Immediately deactivates entry portals for a realm, clearing tracking
     * so new maps can be used right away.
     *
     * <p>Called on victory/timeout/close to immediately free the portal for reuse.
     * The {@code realmToPortals} mapping is preserved so
     * {@link #deactivateAllPortalsForRealm} can still do full cleanup later.
     *
     * @param realm The realm whose entry portals should be deactivated
     */
    public void deactivateEntryPortalsNow(@Nonnull RealmInstance realm) {
        UUID realmId = realm.getRealmId();
        Set<PortalPosition> portals = realmToPortals.get(realmId); // Read only, don't remove

        if (portals == null || portals.isEmpty()) {
            return;
        }

        for (PortalPosition portal : portals) {
            String key = portal.toKey();

            // Clear tracking so isPortalActive() returns false immediately
            portalToRealm.remove(key);
            portalToOwner.remove(key);
            portalCreationTime.remove(key);
            activatedDevices.remove(key);

            // Schedule block state change on world thread
            World world = portal.getWorld();
            if (world != null && world.isAlive()) {
                Vector3i position = portal.position();
                world.execute(() -> {
                    try {
                        String offState = "default";
                        PortalDevice portalDevice = getPortalDevice(world, position);
                        if (portalDevice != null && portalDevice.getConfig() != null) {
                            offState = portalDevice.getConfig().getOffState();
                        }
                        BlockType blockType = world.getBlockType(position.x, position.y, position.z);
                        if (blockType != null) {
                            world.setBlockInteractionState(position, blockType, offState);
                        }
                        LOGGER.atInfo().log("Early deactivated portal at %s for realm %s",
                            position, realmId.toString().substring(0, 8));
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(
                            "Failed to early deactivate portal at %s", position);
                    }
                });
            }
        }

        LOGGER.atInfo().log("Early deactivated %d entry portals for realm %s",
            portals.size(), realmId.toString().substring(0, 8));
    }

    /**
     * Internal method to remove a portal block.
     */
    private void removePortalBlockInternal(@Nonnull WorldChunk chunk, @Nonnull Vector3i position) {
        try {
            // Replace with air
            BlockType airType = BlockType.getAssetMap().getAsset("hytale:air");
            if (airType != null) {
                chunk.setBlock(position.x, position.y, position.z, airType.getId());
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error removing portal block at %s", position);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PORTAL QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the realm associated with a portal at the given position.
     *
     * @param world The world containing the portal
     * @param position The portal position
     * @return The realm ID, or empty if no portal exists at that position
     */
    @Nonnull
    public Optional<UUID> getRealmForPortal(@Nonnull World world, @Nonnull Vector3i position) {
        String key = createPortalKey(world, position);
        return Optional.ofNullable(portalToRealm.get(key));
    }

    /**
     * Gets all portal positions for a realm.
     *
     * @param realmId The realm ID
     * @return Set of portal positions (unmodifiable)
     */
    @Nonnull
    public Set<PortalPosition> getPortalsForRealm(@Nonnull UUID realmId) {
        Set<PortalPosition> portals = realmToPortals.get(realmId);
        return portals != null ? Collections.unmodifiableSet(portals) : Collections.emptySet();
    }

    /**
     * Checks if a portal exists at the given position.
     *
     * @param world The world to check
     * @param position The position to check
     * @return true if a realm portal exists at that position
     */
    public boolean hasPortalAt(@Nonnull World world, @Nonnull Vector3i position) {
        String key = createPortalKey(world, position);
        return portalToRealm.containsKey(key);
    }

    /**
     * Checks if a portal at the given position is currently active (linked to a realm).
     *
     * <p>This is used to prevent activating a Portal_Device that's already linked
     * to another realm.
     *
     * @param world The world containing the portal
     * @param position The portal block position
     * @return true if the portal is active
     */
    public boolean isPortalActive(@Nonnull World world, @Nonnull Vector3i position) {
        return hasPortalAt(world, position);
    }

    /**
     * Gets the total number of tracked portals.
     *
     * @return Portal count
     */
    public int getPortalCount() {
        return portalToRealm.size();
    }

    /**
     * Reads the PortalDevice component at a given position.
     *
     * @param world The world
     * @param position The block position
     * @return The PortalDevice, or null if not found or chunk not loaded
     */
    @Nullable
    public PortalDevice getPortalDevice(@Nonnull World world, @Nonnull Vector3i position) {
        // Check if chunk is loaded first to avoid NPE when chunk is unloaded
        long chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z);
        if (world.getChunkIfLoaded(chunkIndex) == null) {
            LOGGER.atFine().log("Chunk not loaded for portal at %s, skipping", position);
            return null;
        }

        return BlockModule.getComponent(
            PortalsPlugin.getInstance().getPortalDeviceComponentType(),
            world,
            position.x, position.y, position.z
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Tracks a portal-realm association.
     */
    private void trackPortal(@Nonnull World world, @Nonnull Vector3i position, @Nonnull UUID realmId) {
        String key = createPortalKey(world, position);
        portalToRealm.put(key, realmId);

        PortalPosition portalPos = new PortalPosition(
            world.getWorldConfig().getUuid(),
            position
        );

        realmToPortals.computeIfAbsent(realmId, k -> ConcurrentHashMap.newKeySet())
            .add(portalPos);
    }

    /**
     * Tracks an existing Portal_Device block that has been activated for a realm.
     *
     * <p>Unlike {@link #createPortal}, this method doesn't place a new block.
     * It only tracks an existing Portal_Device that was configured to link to a realm.
     * When the realm closes, these portals are deactivated (not removed) so they can
     * be reused with another realm map.
     *
     * @param world The world containing the portal
     * @param position The portal block position
     * @param realm The realm instance the portal is linked to
     */
    public void trackPortalRealm(@Nonnull World world, @Nonnull Vector3i position, @Nonnull RealmInstance realm) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(position, "Position cannot be null");
        Objects.requireNonNull(realm, "Realm cannot be null");

        String key = createPortalKey(world, position);
        trackPortal(world, position, realm.getRealmId());

        // Mark as an activated device (not spawned by us) for proper cleanup
        activatedDevices.add(key);

        LOGGER.atInfo().log("Tracking activated portal device at %s for realm %s", position, realm.getRealmId());
    }

    /**
     * Creates a unique key for a portal position.
     */
    @Nonnull
    private String createPortalKey(@Nonnull World world, @Nonnull Vector3i position) {
        return world.getWorldConfig().getUuid() + ":" + position.x + ":" + position.y + ":" + position.z;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clears all tracked portals.
     * <p>
     * Note: This only clears the tracking data, not the actual portal blocks.
     */
    public void clearTracking() {
        portalToRealm.clear();
        realmToPortals.clear();
        portalToOwner.clear();
        portalCreationTime.clear();
        activatedDevices.clear();
        LOGGER.atInfo().log("Cleared all portal tracking data");
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED TYPES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents a portal's position in the world.
     */
    public record PortalPosition(
        @Nonnull UUID worldUuid,
        @Nonnull Vector3i position
    ) {
        public PortalPosition {
            Objects.requireNonNull(worldUuid, "World UUID cannot be null");
            Objects.requireNonNull(position, "Position cannot be null");
        }

        /**
         * Gets the world if it's still loaded.
         *
         * @return The world, or null if not loaded
         */
        @Nullable
        public World getWorld() {
            return com.hypixel.hytale.server.core.universe.Universe.get().getWorld(worldUuid);
        }

        /**
         * Creates a unique key for this position.
         */
        @Nonnull
        public String toKey() {
            return worldUuid + ":" + position.x + ":" + position.y + ":" + position.z;
        }

        /**
         * Checks if this position matches the given world and coordinates.
         */
        public boolean matches(@Nonnull World world, @Nonnull Vector3i pos) {
            return worldUuid.equals(world.getWorldConfig().getUuid()) &&
                   position.equals(pos);
        }
    }
}
