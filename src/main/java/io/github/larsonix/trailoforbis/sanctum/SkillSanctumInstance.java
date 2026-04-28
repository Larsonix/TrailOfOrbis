package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.WorldEmptyCondition;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.protocol.InteractableUpdate;
import com.hypixel.hytale.protocol.ItemUpdate;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.math.util.ChunkUtil;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.util.ChunkLoadHelper;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeComponent;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeSubtitleComponent;
import io.github.larsonix.trailoforbis.skilltree.NodeLabelFormatter;
import io.github.larsonix.trailoforbis.skilltree.NodeState;
import io.github.larsonix.trailoforbis.sanctum.interactions.SkillNodeInteraction;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single player's Skill Sanctum instance.
 *
 * <p>Each player has their own sanctum with their own set of node orbs.
 * The sanctum is a dedicated realm (world/instance) containing:
 * <ul>
 *   <li>121 floating orb entities representing skill nodes</li>
 *   <li>Particle beams connecting adjacent nodes</li>
 *   <li>A void environment with minimal geometry</li>
 * </ul>
 *
 * <h3>Entity Tracking:</h3>
 * <p>All spawned orb entities are tracked by their entity Ref and node ID.
 * This allows efficient lookup when:
 * <ul>
 *   <li>Updating visual state after allocation</li>
 *   <li>Destroying all entities on close</li>
 *   <li>Finding adjacent nodes for beam rendering</li>
 * </ul>
 *
 * @see SkillSanctumManager
 * @see SkillSanctumNodeSpawner
 */
public class SkillSanctumInstance {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Distance in blocks within which nameplates are visible.
     */
    private static final double NAMEPLATE_VISIBLE_DISTANCE = 25.0;
    private static final double NAMEPLATE_VISIBLE_DISTANCE_SQ = NAMEPLATE_VISIBLE_DISTANCE * NAMEPLATE_VISIBLE_DISTANCE;

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final SkillSanctumManager manager;
    private final UUID playerId;

    /**
     * The underlying realm instance.
     */
    @Nullable
    private final RealmInstance realmInstance;

    /**
     * Map of node ID → entity Ref for all spawned orbs.
     */
    private final Map<String, Ref<EntityStore>> nodeEntities;

    /**
     * Map of node ID → entity Ref for subtitle entities (description/hint text).
     */
    private final Map<String, Ref<EntityStore>> subtitleEntities;

    /**
     * Set of node IDs that are currently allocated.
     * Cached from SkillTreeManager for quick lookup.
     */
    private final Set<String> allocatedNodes;

    /**
     * Whether this instance is active and usable.
     */
    private boolean active;

    /**
     * Timestamp when this instance was created.
     */
    private final long createdAt;

    /**
     * Whether nodes have been spawned.
     */
    private boolean nodesSpawned;

    /**
     * Set of node IDs that currently have visible nameplates.
     * Used to avoid unnecessary updates when visibility hasn't changed.
     */
    private final Set<String> visibleNameplates;

    /**
     * Per-instance connection renderer for proximity-based laser beams.
     * Each instance owns its renderer (not shared) to support concurrent sanctums.
     */
    @Nullable
    private SkillSanctumConnectionRenderer connectionRenderer;

    /**
     * Whether interaction hints have been sent to the player at least once.
     * Used to ensure immediate hint sending after spawn completes.
     */
    private boolean initialHintsSent;

    /**
     * Original canFly state before entering sanctum.
     * Used to restore on exit.
     */
    private boolean originalCanFly = false;

    /**
     * Whether flight has been enabled for the player.
     * Used by the tick system to retry until successful.
     */
    private boolean flightEnabled = false;

    /**
     * Whether player interactions have been set up.
     * Used by the tick system to retry until the player is in the sanctum.
     */
    private boolean interactionsSetUp = false;

    /**
     * Whether the immediate visual component resync has been performed.
     * Set after all nodes + ropes are spawned and components are re-flagged.
     */
    private boolean componentResyncDone = false;

    /**
     * Timestamp (ms) when a deferred visual refresh should execute, or 0 if none pending.
     * Set by inventory change events to re-send visual components after UpdateItems packets
     * reset client-side rendering of node entities.
     */
    private long visualRefreshAt;

    /**
     * Whether the delayed (safety-net) component resync has been performed.
     * Set ~2.5s after the immediate resync as a second pass.
     */
    private boolean delayedComponentResyncDone = false;

    /**
     * Timestamp when the immediate component resync was performed.
     * Used to schedule the delayed resync.
     */
    private long componentResyncTimestamp = 0;

    /**
     * Whether chunks in the sanctum world are loaded and ready for entity spawning.
     * Set asynchronously by {@link #startChunkLoadAwait(Runnable)} via {@link ChunkLoadHelper}.
     */
    private volatile boolean chunksReady = false;

    /**
     * The return point Transform for when the player exits the sanctum.
     * This is the player's position before entering.
     */
    @Nonnull
    private final Transform returnPoint;

    /**
     * The ultimate return world UUID - a permanent world that always exists.
     *
     * <p>This is found by following the return point chain until we find a non-instance world.
     * If the immediate return world (e.g., Forgotten Temple) is destroyed while the player
     * is in the sanctum, we fall back to this world instead.
     */
    @Nullable
    private final UUID ultimateReturnWorldId;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new sanctum instance for a player.
     *
     * @param manager               The sanctum manager
     * @param playerId              The owning player's UUID
     * @param realmInstance         The underlying realm instance
     * @param returnPoint           The return point for when the player exits
     * @param ultimateReturnWorldId The ultimate fallback world UUID (non-instance, permanent)
     */
    public SkillSanctumInstance(
            @Nonnull SkillSanctumManager manager,
            @Nonnull UUID playerId,
            @Nonnull RealmInstance realmInstance,
            @Nonnull Transform returnPoint,
            @Nullable UUID ultimateReturnWorldId) {
        this.manager = manager;
        this.playerId = playerId;
        this.realmInstance = realmInstance;
        this.returnPoint = returnPoint;
        this.ultimateReturnWorldId = ultimateReturnWorldId;
        this.nodeEntities = new ConcurrentHashMap<>();
        this.subtitleEntities = new ConcurrentHashMap<>();
        this.allocatedNodes = ConcurrentHashMap.newKeySet();
        this.visibleNameplates = ConcurrentHashMap.newKeySet();
        this.active = true;
        this.createdAt = System.currentTimeMillis();
        this.nodesSpawned = false;
        this.initialHintsSent = false;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHUNK LOADING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Starts chunk loading with a callback that fires synchronously when chunks are ready.
     *
     * <p>Uses {@code thenRun} (not {@code thenRunAsync}) so the callback executes inline
     * on the world thread in the same tick the chunks finish loading. This eliminates the
     * 1-2 tick gap that caused "chunk not loaded" warnings when entities spawned.
     *
     * <p>If a timeout occurs (chunks don't load within the deadline), the callback is
     * dispatched to the world thread via {@code world.execute()} as a fallback.
     *
     * @param onChunksReady Optional callback to run when chunks are loaded (on world thread)
     */
    public void startChunkLoadAwait(@Nullable Runnable onChunksReady) {
        World world = getSanctumWorld();
        if (world == null) {
            chunksReady = true; // Fallback — proceed without waiting
            if (onChunksReady != null) onChunksReady.run();
            return;
        }
        // Sanctum nodes span up to OUTER_RADIUS (80 blocks) from center
        int radiusInChunks = (int) Math.ceil(GalaxySpiralLayoutMapper.OUTER_RADIUS / ChunkUtil.SIZE) + 1;
        world.execute(() -> {
            ChunkLoadHelper.awaitAreaLoaded(world, 0, 0, radiusInChunks)
                .thenRun(() -> {
                    // SUCCESS: runs on world thread, same tick as chunk completion — zero gap
                    chunksReady = true;
                    LOGGER.atFine().log("Sanctum chunks ready for %s", playerId);
                    if (onChunksReady != null) onChunksReady.run();
                })
                .exceptionally(ex -> {
                    // TIMEOUT: runs on JVM scheduler thread, dispatch to world thread
                    LOGGER.atWarning().log("Chunk load timeout for sanctum %s, spawning with delay",
                        playerId);
                    chunksReady = true;
                    if (onChunksReady != null) world.execute(onChunksReady);
                    return null;
                });
        });
    }

    /**
     * Returns whether chunks are loaded and entity spawning is allowed.
     */
    public boolean areChunksReady() {
        return chunksReady;
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initializes the sanctum and spawns all node orbs.
     *
     * <p>This:
     * <ol>
     *   <li>Loads the player's allocated nodes from SkillTreeManager</li>
     *   <li>Queues all 121 node orbs for spawning</li>
     *   <li>Returns when spawning is queued (actual spawning happens in ticks)</li>
     * </ol>
     *
     * @param spawner The node spawner to use
     * @return CompletableFuture that completes when spawning is queued
     */
    @Nonnull
    public CompletableFuture<Boolean> initializeAndSpawnNodes(@Nullable SkillSanctumNodeSpawner spawner) {
        if (spawner == null) {
            LOGGER.atWarning().log("Node spawner is null for sanctum %s", playerId);
            return CompletableFuture.completedFuture(false);
        }

        if (realmInstance == null) {
            LOGGER.atWarning().log("Realm instance is null for sanctum %s", playerId);
            return CompletableFuture.completedFuture(false);
        }

        LOGGER.atInfo().log("Initializing sanctum for %s", playerId);

        // Configure world settings (frozen midnight + void weather)
        configureSanctumWorldSettings();

        // Load allocated nodes from skill tree
        SkillTreeManager skillTreeManager = manager.getSkillTreeManager();
        Set<String> playerAllocated = skillTreeManager.getAllocatedNodes(playerId);
        allocatedNodes.addAll(playerAllocated);

        LOGGER.atInfo().log("Player %s has %d allocated nodes", playerId, allocatedNodes.size());

        // Queue all node spawns - nodesSpawned will be set by markSpawningComplete()
        // when the spawner finishes creating all entities on the world thread
        return spawner.queueAllNodes(this, allocatedNodes)
            .thenApply(result -> {
                LOGGER.atInfo().log("Queued %d node spawns for %s", result.queued(), playerId);

                // Create per-instance connection renderer and apply config
                connectionRenderer = new SkillSanctumConnectionRenderer(
                    manager.getSkillTreeManager(), playerId);
                var connConfig = manager.getConfig().getConnections();
                connectionRenderer.setBeamDurationMs(connConfig.getBeamDurationMs());
                connectionRenderer.setInitialFillRate(connConfig.getInitialFillRate());
                connectionRenderer.setRefreshMarginMs(connConfig.getRefreshMarginMs());
                connectionRenderer.computeConnectionVisuals(allocatedNodes);
                LOGGER.atInfo().log("Initialized connection renderer for sanctum %s", playerId);

                return true;
            })
            .exceptionally(ex -> {
                LOGGER.atWarning().withCause(ex).log("Failed to queue node spawns for %s", playerId);
                return false;
            });
    }

    /**
     * Closes this sanctum instance.
     *
     * <p>This:
     * <ol>
     *   <li>Marks instance as inactive</li>
     *   <li>Clears entity tracking</li>
     *   <li>Closes the underlying realm instance</li>
     *   <li>Notifies manager</li>
     * </ol>
     */
    public void close() {
        if (!active) {
            return;
        }

        LOGGER.atInfo().log("Closing sanctum instance for %s", playerId);

        // Mark as inactive first to prevent new operations
        active = false;

        // Clear pending spawns for this instance
        SkillSanctumNodeSpawner spawner = manager.getNodeSpawner();
        if (spawner != null) {
            spawner.clearPendingSpawns(playerId);
        }

        // Clean up connection renderer (beams auto-expire, just clear state)
        if (connectionRenderer != null) {
            connectionRenderer.cleanup();
            connectionRenderer = null;
        }

        // TODO: Phase 4 - Destroy all orb entities
        // For now, entities will be cleaned up when the realm closes

        // Close the underlying realm and remove the instance world.
        // forceClose() only transitions the state to CLOSING — it does NOT delete the
        // backing world.  Without safeRemoveInstance(), Hytale persists the world to disk
        // and reloads it on every server restart, causing orphaned instances to accumulate.
        if (realmInstance != null) {
            World world = realmInstance.getWorld();
            try {
                realmInstance.forceClose(RealmInstance.CompletionReason.ABANDONED);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error closing realm for %s", playerId);
            }
            // Actually delete the instance world so Hytale doesn't persist it
            if (world != null && world.isAlive()) {
                try {
                    InstancesPlugin.safeRemoveInstance(world);
                    LOGGER.atInfo().log("Removed instance world for sanctum %s", playerId);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Failed to remove instance world for %s", playerId);
                }
            }
        }

        nodeEntities.clear();
        subtitleEntities.clear();
        allocatedNodes.clear();

        manager.onInstanceClosed(playerId);
        LOGGER.atInfo().log("Sanctum instance closed for %s", playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENVIRONMENT CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Configures the sanctum world environment settings.
     * Sets time to permanent midnight with frozen progression.
     * Should be called once after the world is created.
     */
    private void configureSanctumWorldSettings() {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot configure sanctum world settings - world not available");
            return;
        }

        // Mark sanctum worlds as ephemeral — they should never persist across restarts.
        // setDeleteOnRemove:        delete files when safeRemoveInstance() is called
        // setDeleteOnUniverseStart: delete files on server boot (crash recovery safety net)
        // Standard pattern for ephemeral instances.
        world.getWorldConfig().setDeleteOnRemove(true);
        world.getWorldConfig().setDeleteOnUniverseStart(true);

        // Set removal condition: auto-remove when no players are present.
        // This is a runtime safety net — if close() somehow doesn't fire (e.g., player
        // disconnects during teleport), RemovalSystem will clean up automatically.
        InstanceWorldConfig instanceConfig = InstanceWorldConfig.get(world.getWorldConfig());
        if (instanceConfig != null) {
            instanceConfig.setRemovalConditions(WorldEmptyCondition.REMOVE_WHEN_EMPTY);
            LOGGER.atInfo().log("Sanctum %s configured: ephemeral + REMOVE_WHEN_EMPTY", playerId);
        }

        // Freeze time progression
        world.getWorldConfig().setGameTimePaused(true);
        LOGGER.atInfo().log("Froze game time for sanctum %s", playerId);

        // Set to midnight (0.0 = start of day) on world thread
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());

            if (timeResource != null) {
                timeResource.setDayTime(0.0, world, store);
                LOGGER.atInfo().log("Set sanctum time to midnight for %s", playerId);
            } else {
                LOGGER.atWarning().log("WorldTimeResource not available for sanctum %s", playerId);
            }
        });
    }

    /**
     * Enables flight for the player while in the sanctum.
     * Captures original flight state for restoration on exit.
     *
     * <p>This method must be called on the sanctum world thread after verifying
     * the player is in the sanctum world.
     *
     * <p>In addition to enabling flight permission (canFly), this method also:
     * <ul>
     *   <li>Sets the player's movement state to flying</li>
     *   <li>Sends a SetMovementStates packet to force the client into flight mode</li>
     * </ul>
     *
     * @param playerRef The player to enable flight for
     * @return true if flight was enabled successfully
     */
    private boolean enableFlightForPlayer(@Nonnull PlayerRef playerRef) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("enableFlightForPlayer: world not available");
            return false;
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            LOGGER.atWarning().log("enableFlightForPlayer: entity ref invalid for %s", playerRef.getUuid());
            return false;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        MovementManager movementManager = store.getComponent(entityRef, MovementManager.getComponentType());

        if (movementManager == null) {
            LOGGER.atWarning().log("enableFlightForPlayer: MovementManager not found for %s", playerRef.getUuid());
            return false;
        }

        // Save original state
        originalCanFly = movementManager.getSettings().canFly;
        // Enable flight permission
        movementManager.getSettings().canFly = true;
        // Sync to client
        movementManager.update(playerRef.getPacketHandler());

        // Set flying state on the MovementStatesComponent (forces player into flying mode)
        MovementStatesComponent movementStatesComp = store.getComponent(entityRef,
            MovementStatesComponent.getComponentType());
        if (movementStatesComp != null) {
            MovementStates states = movementStatesComp.getMovementStates();
            states.flying = true;
            states.onGround = false;
            states.falling = false;
            LOGGER.atFine().log("Set flying state on MovementStatesComponent for %s", playerRef.getUuid());
        }

        // Send SetMovementStates packet to force client into flying state
        SavedMovementStates savedStates = new SavedMovementStates(true); // flying = true
        playerRef.getPacketHandler().write(new SetMovementStates(savedStates));

        LOGGER.atInfo().log("Enabled forced flight for player %s in sanctum (was canFly: %b)",
            playerRef.getUuid(), originalCanFly);
        return true;
    }

    /**
     * Ticks flight enabling.
     *
     * <p>Called periodically to enable flight once the player is in the sanctum.
     * The teleport is async, so we need to retry until the player entity is
     * actually in the sanctum world.
     *
     * @implNote Must be called from the world thread.
     */
    public void tickFlightEnable() {
        if (!active || flightEnabled) {
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) {
            LOGGER.atFine().log("tickFlightEnable: player not found or invalid for %s", playerId);
            return;
        }

        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atFine().log("tickFlightEnable: sanctum world not available for %s", playerId);
            return;
        }

        // Check player's current world
        UUID playerWorldUuid = playerRef.getWorldUuid();
        UUID sanctumWorldUuid = world.getWorldConfig().getUuid();
        if (playerWorldUuid == null || !playerWorldUuid.equals(sanctumWorldUuid)) {
            LOGGER.atFine().log("tickFlightEnable: player %s not in sanctum yet (player world: %s, sanctum: %s)",
                playerId, playerWorldUuid, sanctumWorldUuid);
            return;
        }

        if (enableFlightForPlayer(playerRef)) {
            flightEnabled = true;

            // Show the skill point HUD now that the player is in the sanctum
            Store<EntityStore> store = world.getEntityStore().getStore();
            manager.getSkillPointHudManager().showHud(playerId, playerRef, store);
        }
    }

    /**
     * Checks if flight has been enabled for the player.
     *
     * @return true if flight has been enabled
     */
    public boolean isFlightEnabled() {
        return flightEnabled;
    }

    /**
     * Ticks interaction setup.
     *
     * <p>Called periodically to set up interactions once the player is in the sanctum.
     * The teleport is async, so we need to wait until the player's entity is valid
     * in the sanctum world before we can add the Interactions component.
     *
     * @implNote Must be called from the world thread.
     */
    public void tickInteractionSetup() {
        if (!active || interactionsSetUp) {
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) {
            LOGGER.atFine().log("tickInteractionSetup: player not found or invalid for %s", playerId);
            return;
        }

        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atFine().log("tickInteractionSetup: sanctum world not available for %s", playerId);
            return;
        }

        // Check player's current world matches sanctum
        UUID playerWorldUuid = playerRef.getWorldUuid();
        UUID sanctumWorldUuid = world.getWorldConfig().getUuid();
        if (playerWorldUuid == null || !playerWorldUuid.equals(sanctumWorldUuid)) {
            LOGGER.atFine().log("tickInteractionSetup: player %s not in sanctum yet (player world: %s, sanctum: %s)",
                playerId, playerWorldUuid, sanctumWorldUuid);
            return; // Player not in sanctum yet
        }

        try {
            setupPlayerInteractions(playerRef);
            interactionsSetUp = true;
            LOGGER.atInfo().log("Deferred interaction setup complete for player %s", playerId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to setup interactions for %s", playerId);
        }
    }

    /**
     * Checks if player interactions have been set up.
     *
     * @return true if interactions have been set up
     */
    public boolean areInteractionsSetUp() {
        return interactionsSetUp;
    }

    /**
     * Ticks to ensure player remains flying (prevents toggle off).
     *
     * <p>Called periodically after flight is enabled. If the player somehow
     * stops flying (e.g., pressing the fly toggle key), this method re-forces
     * them back into flying state.
     *
     * <p>This ensures the sanctum is a forced-fly environment where players
     * cannot land or walk - they must fly through the skill tree void.
     *
     * @implNote Must be called from the world thread.
     */
    public void tickForceFlight() {
        if (!active || !flightEnabled) {
            return;
        }

        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        // Check player's current world
        UUID playerWorldUuid = playerRef.getWorldUuid();
        UUID sanctumWorldUuid = world.getWorldConfig().getUuid();
        if (playerWorldUuid == null || !playerWorldUuid.equals(sanctumWorldUuid)) {
            return;
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        MovementStatesComponent comp = store.getComponent(entityRef,
            MovementStatesComponent.getComponentType());

        if (comp != null) {
            MovementStates states = comp.getMovementStates();
            // Re-force flying if player somehow toggled it off
            if (!states.flying || states.onGround) {
                states.flying = true;
                states.onGround = false;
                states.falling = false;

                // Re-send packet to sync client
                SavedMovementStates savedStates = new SavedMovementStates(true);
                playerRef.getPacketHandler().write(new SetMovementStates(savedStates));

                LOGGER.atFine().log("Re-forced flying state for player %s in sanctum", playerId);
            }
        }
    }

    /**
     * Restores the player's original flight state when leaving sanctum.
     *
     * @param playerRef The player to restore flight for
     */
    public void restoreFlightForPlayer(@Nonnull PlayerRef playerRef) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) return;

        world.execute(() -> {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) return;

            Store<EntityStore> store = world.getEntityStore().getStore();
            MovementManager movementManager = store.getComponent(entityRef, MovementManager.getComponentType());

            if (movementManager != null) {
                movementManager.getSettings().canFly = originalCanFly;
                movementManager.update(playerRef.getPacketHandler());

                LOGGER.atInfo().log("Restored flight state for player %s (canFly=%b)",
                    playerRef.getUuid(), originalCanFly);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Teleports a player to the sanctum spawn point.
     *
     * <p>This also adds the {@link Interactions} component to the player with
     * {@code InteractionType.Use → *AllocateSkillNode} so that pressing F on
     * skill node entities triggers the skill allocation interaction.
     *
     * @param playerRef The player to teleport
     * @return true if teleport was initiated (actual teleport is async)
     */
    public boolean teleportPlayerToSpawn(@Nonnull PlayerRef playerRef) {
        if (!active) {
            LOGGER.atWarning().log("Cannot teleport %s - sanctum not active", playerRef.getUuid());
            return false;
        }

        World sanctumWorld = getSanctumWorld();
        if (sanctumWorld == null || !sanctumWorld.isAlive()) {
            LOGGER.atWarning().log("Cannot teleport %s - no sanctum world", playerRef.getUuid());
            return false;
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (!entityRef.isValid()) {
            LOGGER.atWarning().log("Cannot teleport %s - entity ref not valid", playerRef.getUuid());
            return false;
        }

        // Get player's current world
        UUID currentWorldUuid = playerRef.getWorldUuid();
        World currentWorld = currentWorldUuid != null ? Universe.get().getWorld(currentWorldUuid) : null;
        if (currentWorld == null) {
            LOGGER.atWarning().log("Cannot teleport %s - not in any world", playerRef.getUuid());
            return false;
        }

        Vector3d spawnPos = GalaxySpiralLayoutMapper.getPlayerSpawnPosition();

        // Execute teleport on the current world's thread
        currentWorld.execute(() -> {
            try {
                Store<EntityStore> store = currentWorld.getEntityStore().getStore();

                // Teleport to sanctum world with return point for exit
                InstancesPlugin.teleportPlayerToInstance(entityRef, store, sanctumWorld, returnPoint);

                LOGGER.atInfo().log("Teleported %s to sanctum at (%.1f, %.1f, %.1f), return at (%.1f, %.1f, %.1f)",
                    playerRef.getUuid(), spawnPos.x, spawnPos.y, spawnPos.z,
                    returnPoint.getPosition().x, returnPoint.getPosition().y, returnPoint.getPosition().z);

            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to teleport %s to sanctum", playerRef.getUuid());
            }
        });

        // Note: Interactions and flight are set up by tickInteractionSetup() and tickFlightEnable()
        // once the player is fully in the sanctum world. The teleport is async, so we can't
        // set them up immediately here.

        return true;
    }

    /**
     * Sets up the Interactions component on the player for skill node allocation.
     *
     * <p>When a player is in the sanctum, they need an Interactions component with
     * {@code InteractionType.Use → *AllocateSkillNode}. Without this, the server
     * cannot route F-key interactions on entities to our skill node handler.
     *
     * <p>This is needed because:
     * <ol>
     *   <li>The client sends entityId (NetworkId) when pressing F on an entity</li>
     *   <li>The server creates InteractionContext with the PLAYER as runningForEntity</li>
     *   <li>getRootInteractionId() checks the player's Interactions component first</li>
     *   <li>Without this, it falls back to held item interactions (which don't handle skill nodes)</li>
     * </ol>
     *
     * @param playerRef The player to setup
     */
    private void setupPlayerInteractions(@Nonnull PlayerRef playerRef) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            LOGGER.atWarning().log("Cannot setup interactions - invalid entity ref for %s", playerRef.getUuid());
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        // Create Interactions component with Use → *AllocateSkillNode
        Interactions interactions = new Interactions();
        interactions.setInteractionId(InteractionType.Use, SkillNodeInteraction.DEFAULT_ID);
        interactions.setInteractionHint("Press F to (un)allocate | Click to see details");

        // Add component to player - putComponent() adds or replaces
        store.putComponent(entityRef, Interactions.getComponentType(), interactions);

        LOGGER.atInfo().log("Added Interactions component to player %s for skill node allocation",
            playerRef.getUuid());

        // Note: Interaction hints are sent by the periodic tickInteractionHints() call
        // from the manager's tick loop. This ensures hints are sent after entity visibility
        // is established and continues to refresh as the player moves.
    }

    /**
     * Sends interaction hints to a player for all skill nodes they know about.
     *
     * <p>This is necessary because item entities don't automatically participate
     * in the TrackerTickSystem's Interactions sync. We manually send ComponentUpdate
     * packets with the interaction hint, similar to how NPCs use SetInteractable.
     *
     * <p>Note: We use {@code viewerComponent.sent} instead of {@code visible} because
     * {@code visible} is transient (cleared/rebuilt each tick) while {@code sent}
     * tracks entities the client knows about. We directly add to {@code updates}
     * instead of using {@code queueUpdate()} which validates against {@code visible}.
     *
     * @param playerRef The player to send hints to
     */
    private void sendInteractionHintsToPlayer(@Nonnull PlayerRef playerRef) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        // Validate player is in sanctum world (prevents cross-store AIOOBE)
        Ref<EntityStore> playerEntityRef = getPlayerRefInSanctumWorld(playerRef, store);
        if (playerEntityRef == null) {
            return;
        }

        // Get player's EntityViewer component (handles what entities they can see)
        EntityTrackerSystems.EntityViewer viewerComponent;
        try {
            viewerComponent = store.getComponent(playerEntityRef, EntityTrackerSystems.EntityViewer.getComponentType());
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.atWarning().log("Player %s entity ref stale in sanctum (AIOOBE)", playerRef.getUuid());
            return;
        }

        if (viewerComponent == null) {
            LOGGER.atWarning().log("Player %s has no EntityViewer component", playerRef.getUuid());
            return;
        }

        int hintsSent = 0;
        for (Map.Entry<String, Ref<EntityStore>> entry : nodeEntities.entrySet()) {
            Ref<EntityStore> nodeRef = entry.getValue();
            if (nodeRef == null || !nodeRef.isValid()) {
                continue;
            }

            // Only send hint if client knows about this entity (it's been sent to them)
            // Using 'sent' instead of 'visible' because visible is transient
            if (viewerComponent.sent.containsKey(nodeRef)) {
                // Create Interactable component update with hint
                InteractableUpdate update = new InteractableUpdate("Press F to (un)allocate | Click to see details");

                // Directly add to updates map - bypasses visible check in queueUpdate()
                // SendPackets system will handle entities in updates that aren't in sent
                viewerComponent.updates
                    .computeIfAbsent(nodeRef, k -> new EntityTrackerSystems.EntityUpdate())
                    .queueUpdate(update);
                hintsSent++;
            }
        }

        if (hintsSent > 0) {
            initialHintsSent = true;
        }
        LOGGER.atInfo().log("Sent %d interaction hints to player %s", hintsSent, playerRef.getUuid());
    }

    /**
     * Ticks interaction hint visibility.
     *
     * <p>Called periodically to send hints for newly visible nodes.
     * This ensures that as players move through the sanctum, nodes that
     * come into view will show their interaction prompts.
     *
     * @implNote Must be called from the world thread.
     */
    public void tickInteractionHints() {
        if (!active || !nodesSpawned) {
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        sendInteractionHintsToPlayer(playerRef);
    }

    /**
     * Checks if initial interaction hints have been sent to the player.
     *
     * @return true if hints have been sent at least once
     */
    public boolean areInitialHintsSent() {
        return initialHintsSent;
    }

    /**
     * Removes the Interactions component from the player.
     * Called when the player leaves the sanctum.
     *
     * @param playerRef The player to cleanup
     */
    public void removePlayerInteractions(@Nonnull PlayerRef playerRef) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        world.execute(() -> {
            try {
                Ref<EntityStore> entityRef = playerRef.getReference();
                if (!entityRef.isValid()) {
                    return;
                }

                Store<EntityStore> store = world.getEntityStore().getStore();

                // Remove the Interactions component
                store.removeComponent(entityRef, Interactions.getComponentType());

                LOGGER.atInfo().log("Removed Interactions component from player %s", playerRef.getUuid());
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to remove interactions for %s", playerRef.getUuid());
            }
        });
    }

    /**
     * Exits the player from the sanctum instance.
     *
     * <p>This method implements a three-tier fallback strategy for teleporting the player:
     * <ol>
     *   <li>Try {@link InstancesPlugin#exitInstance(Ref, ComponentAccessor)} (stored return point)</li>
     *   <li>Fallback to {@code ultimateReturnWorldId} (the permanent world in the chain)</li>
     *   <li>Last resort: teleport to default world</li>
     * </ol>
     *
     * <p>This fallback is necessary because if the player entered the sanctum from a
     * temporary instance (like Forgotten Temple), that instance might be destroyed
     * while the player is in the sanctum, leaving the stored return point invalid.
     *
     * @param playerRef The player to exit
     * @return true if the exit was initiated successfully
     */
    public boolean exitPlayer(@Nonnull PlayerRef playerRef) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot exit player %s - sanctum world not available", playerRef.getUuid());
            return false;
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            LOGGER.atWarning().log("Cannot exit player %s - entity ref invalid", playerRef.getUuid());
            return false;
        }

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();

                // Tier 1: Try normal exit via InstancesPlugin (uses stored return point)
                if (tryExitViaInstancesPlugin(entityRef, store)) {
                    LOGGER.atInfo().log("Exited player %s via normal return point", playerRef.getUuid());
                    return;
                }

                // Tier 2: Fallback to ultimate return world
                if (ultimateReturnWorldId != null && tryExitToWorld(entityRef, store, ultimateReturnWorldId)) {
                    LOGGER.atInfo().log("Exited player %s via ultimate return world", playerRef.getUuid());
                    return;
                }

                // Tier 3: Last resort - default world
                World defaultWorld = Universe.get().getDefaultWorld();
                if (defaultWorld != null && tryExitToWorld(entityRef, store, defaultWorld.getWorldConfig().getUuid())) {
                    LOGGER.atInfo().log("Exited player %s to default world (fallback)", playerRef.getUuid());
                    return;
                }

                LOGGER.atSevere().log("FAILED to exit player %s from sanctum - no valid world found!", playerRef.getUuid());
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to exit player %s from sanctum", playerRef.getUuid());
            }
        });

        return true;
    }

    /**
     * Attempts to exit via InstancesPlugin. Returns false if return world is missing.
     *
     * @param entityRef The player entity reference
     * @param store     The entity store
     * @return true if exit succeeded, false if return world was missing
     */
    private boolean tryExitViaInstancesPlugin(@Nonnull Ref<EntityStore> entityRef, @Nonnull Store<EntityStore> store) {
        try {
            InstancesPlugin.exitInstance(entityRef, store);
            return true;
        } catch (IllegalArgumentException e) {
            // "Missing return world" - expected when origin instance was destroyed
            LOGGER.atFine().log("Normal exit failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Manually teleports player to a specific world using the stored return point position.
     *
     * @param entityRef     The player entity reference
     * @param store         The entity store
     * @param targetWorldId The target world UUID
     * @return true if teleport was initiated, false if world doesn't exist
     */
    private boolean tryExitToWorld(
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull UUID targetWorldId) {
        World targetWorld = Universe.get().getWorld(targetWorldId);
        if (targetWorld == null || !targetWorld.isAlive()) {
            LOGGER.atFine().log("Target world %s not available for fallback exit", targetWorldId);
            return false;
        }

        // Create teleport component to move player
        Teleport teleport = Teleport.createForPlayer(targetWorld, returnPoint);
        store.addComponent(entityRef, Teleport.getComponentType(), teleport);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // NODE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when a node is allocated via F-key interaction.
     *
     * <p>Updates:
     * <ul>
     *   <li>Local cache of allocated nodes</li>
     *   <li>Visual state of the allocated orb (→ ALLOCATED)</li>
     *   <li>Visual state of adjacent orbs (LOCKED → AVAILABLE if now reachable)</li>
     *   <li>Particle beams to adjacent nodes</li>
     * </ul>
     *
     * @param nodeId The ID of the allocated node
     */
    public void onNodeAllocated(@Nonnull String nodeId) {
        allocatedNodes.add(nodeId);

        LOGGER.atInfo().log("=== Node Allocated: %s (sanctum for %s) ===", nodeId, playerId);
        LOGGER.atInfo().log("nodeEntities map size: %d, contains %s: %b",
            nodeEntities.size(), nodeId, nodeEntities.containsKey(nodeId));

        // Get the skill tree manager to find adjacent nodes
        SkillTreeManager skillTreeManager = manager.getSkillTreeManager();
        Optional<SkillNode> nodeOpt = skillTreeManager.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            LOGGER.atWarning().log("Could not find node %s in skill tree config!", nodeId);
            return;
        }

        SkillNode node = nodeOpt.get();
        LOGGER.atInfo().log("Found node config: %s (connections: %s)", nodeId, node.getConnections());

        // Update the allocated node's item to the ALLOCATED variant (full glow)
        updateNodeItemState(nodeId, node, NodeState.ALLOCATED);

        // Update adjacent nodes: LOCKED → AVAILABLE (also updates their lights)
        updateAdjacentNodeStates(node);

        // Spawn allocation burst particles and clear+burst all connection beams.
        // Clear+burst: wipes stale shapes (ghost beams at old Y offsets) and
        // re-sends all ~260 connections in one tick. Research-proven approach.
        if (connectionRenderer != null) {
            connectionRenderer.spawnAllocationBurst(this, nodeId);
            World beamWorld = getSanctumWorld();
            if (beamWorld != null && beamWorld.isAlive()) {
                connectionRenderer.recomputeAndBurst(beamWorld, allocatedNodes);
            }
        }

        LOGGER.atInfo().log("=== Node allocation processing complete for %s ===", nodeId);
    }

    /**
     * Called when a node is deallocated via command or API.
     *
     * <p>Updates:
     * <ul>
     *   <li>Local cache of allocated nodes</li>
     *   <li>SkillNodeComponent state on the entity</li>
     *   <li>Visual state of the deallocated orb (→ AVAILABLE or LOCKED)</li>
     *   <li>Visual state of adjacent orbs that may become LOCKED</li>
     * </ul>
     *
     * @param nodeId The ID of the deallocated node
     */
    public void onNodeDeallocated(@Nonnull String nodeId) {
        allocatedNodes.remove(nodeId);

        LOGGER.atInfo().log("=== Node Deallocated: %s (sanctum for %s) ===", nodeId, playerId);

        SkillTreeManager skillTreeManager = manager.getSkillTreeManager();
        Optional<SkillNode> nodeOpt = skillTreeManager.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            LOGGER.atWarning().log("Could not find node %s in skill tree config!", nodeId);
            return;
        }

        SkillNode node = nodeOpt.get();

        // Determine new state: AVAILABLE if connected to an allocated node, else LOCKED
        NodeState newState = NodeState.LOCKED;
        for (String connectedId : node.getConnections()) {
            if (allocatedNodes.contains(connectedId)) {
                newState = NodeState.AVAILABLE;
                break;
            }
        }

        // Also check if it's a start node - start nodes are always AVAILABLE
        if (node.isStartNode()) {
            newState = NodeState.AVAILABLE;
        }

        // Update both the component state AND the visual
        updateNodeComponentAndVisual(nodeId, node, newState);

        // Check adjacent nodes - they may become LOCKED if no longer connected
        updateAdjacentNodesAfterDeallocation(node);

        // Clear+burst all connection beams (eliminates ghost beams at old Y offsets)
        if (connectionRenderer != null) {
            World beamWorld = getSanctumWorld();
            if (beamWorld != null && beamWorld.isAlive()) {
                connectionRenderer.recomputeAndBurst(beamWorld, allocatedNodes);
            }
        }

        LOGGER.atInfo().log("=== Node deallocation processing complete for %s ===", nodeId);
    }

    /**
     * Updates both the SkillNodeComponent state and the visual item for a node.
     *
     * <p>This ensures the node is properly interactable after state changes
     * (the interaction system checks the component state, not just the visual).
     *
     * @param nodeId The node ID
     * @param node   The skill node config
     * @param state  The new state
     */
    private void updateNodeComponentAndVisual(@Nonnull String nodeId, @Nonnull SkillNode node, @Nonnull NodeState state) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot update node %s - world is null or not alive", nodeId);
            return;
        }

        TrailOfOrbis plugin = manager.getPlugin();
        ComponentType<EntityStore, SkillNodeComponent> componentType = plugin.getSkillNodeComponentType();

        world.execute(() -> {
            Ref<EntityStore> entityRef = findNodeEntityByNodeId(world, nodeId);
            if (entityRef == null) {
                LOGGER.atWarning().log("Cannot update node %s - entity not found", nodeId);
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();

            // Update the SkillNodeComponent state (this is what the interaction checks!)
            if (componentType != null) {
                SkillNodeComponent nodeComponent = store.getComponent(entityRef, componentType);
                if (nodeComponent != null) {
                    nodeComponent.setState(state);
                }
            }

            // Update the visual item + subtitle position
            updateNodeVisual(world, store, entityRef, node, state);

            LOGGER.atFine().log("Updated node %s: state=%s", nodeId, state);
        });
    }

    /**
     * Called when a full respec occurs via command or API.
     *
     * <p>Updates all node visual states to reflect the reset state:
     * <ul>
     *   <li>Clears local cache of allocated nodes</li>
     *   <li>Updates SkillNodeComponent state on each entity</li>
     *   <li>All non-start nodes become LOCKED</li>
     *   <li>Start nodes become AVAILABLE</li>
     * </ul>
     *
     * @param clearedNodeIds The set of node IDs that were cleared
     */
    public void onRespec(@Nonnull Set<String> clearedNodeIds) {
        LOGGER.atInfo().log("=== Respec: clearing %d nodes (sanctum for %s) ===",
            clearedNodeIds.size(), playerId);

        // Reset to origin-only state (origin is always allocated — it's the graph root)
        allocatedNodes.clear();
        allocatedNodes.add(SkillTreeManager.ORIGIN_NODE_ID);

        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot process respec - world is null or not alive");
            return;
        }

        TrailOfOrbis plugin = manager.getPlugin();
        ComponentType<EntityStore, SkillNodeComponent> componentType = plugin.getSkillNodeComponentType();
        if (componentType == null) {
            LOGGER.atWarning().log("Cannot process respec - SkillNodeComponent type is null");
            return;
        }

        SkillTreeManager skillTreeManager = manager.getSkillTreeManager();

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int updatedCount = 0;

            // Update all nodes that were cleared
            for (String nodeId : clearedNodeIds) {
                // Skip origin — it stays ALLOCATED (preserved by withRespec())
                if (SkillTreeManager.ORIGIN_NODE_ID.equals(nodeId)) {
                    continue;
                }

                Optional<SkillNode> nodeOpt = skillTreeManager.getNode(nodeId);
                if (nodeOpt.isEmpty()) continue;

                SkillNode node = nodeOpt.get();

                // Nodes adjacent to origin become AVAILABLE, everything else LOCKED
                NodeState newState = NodeState.LOCKED;
                for (String conn : node.getConnections()) {
                    if (SkillTreeManager.ORIGIN_NODE_ID.equals(conn)) {
                        newState = NodeState.AVAILABLE;
                        break;
                    }
                }

                // Find the entity for this node
                Ref<EntityStore> entityRef = findNodeEntityByNodeId(world, nodeId);
                if (entityRef == null) {
                    LOGGER.atFine().log("Skipping %s - entity not found", nodeId);
                    continue;
                }

                // Update the SkillNodeComponent state (this is what the interaction checks!)
                SkillNodeComponent nodeComponent = store.getComponent(entityRef, componentType);
                if (nodeComponent != null) {
                    nodeComponent.setState(newState);
                }

                // Update the visual item + subtitle position
                updateNodeVisual(world, store, entityRef, node, newState);

                updatedCount++;
                LOGGER.atFine().log("Respec: node %s updated to %s", nodeId, newState);
            }

            LOGGER.atInfo().log("Respec complete: %d nodes updated", updatedCount);
        });

        // Clear+burst all connection beams on the world thread
        if (connectionRenderer != null) {
            World beamWorld = getSanctumWorld();
            if (beamWorld != null && beamWorld.isAlive()) {
                // Capture current state for the world thread callback
                Set<String> allocatedSnapshot = Set.copyOf(allocatedNodes);
                beamWorld.execute(() -> {
                    connectionRenderer.recomputeAndBurst(beamWorld, allocatedSnapshot);
                });
            }
        }

        LOGGER.atInfo().log("=== Respec processing complete for %s ===", playerId);
    }

    /**
     * Updates adjacent nodes after a deallocation.
     * Nodes that were AVAILABLE may become LOCKED if no longer connected to any allocated node.
     */
    private void updateAdjacentNodesAfterDeallocation(@Nonnull SkillNode deallocatedNode) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        TrailOfOrbis plugin = manager.getPlugin();
        ComponentType<EntityStore, SkillNodeComponent> componentType = plugin.getSkillNodeComponentType();
        if (componentType == null) {
            return;
        }

        SkillTreeManager skillTreeManager = manager.getSkillTreeManager();

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();

            for (String connectedId : deallocatedNode.getConnections()) {
                // Skip allocated nodes
                if (allocatedNodes.contains(connectedId)) continue;

                // Check if this node is still connected to any allocated node
                Optional<SkillNode> connectedNodeOpt = skillTreeManager.getNode(connectedId);
                if (connectedNodeOpt.isEmpty()) continue;

                SkillNode connectedNode = connectedNodeOpt.get();
                boolean stillConnected = connectedNode.isStartNode();
                if (!stillConnected) {
                    for (String neighbor : connectedNode.getConnections()) {
                        if (allocatedNodes.contains(neighbor)) {
                            stillConnected = true;
                            break;
                        }
                    }
                }

                // If no longer connected, update to LOCKED
                if (!stillConnected) {
                    Ref<EntityStore> entityRef = findNodeEntityByNodeId(world, connectedId);
                    if (entityRef == null) continue;

                    SkillNodeComponent nodeComponent = store.getComponent(entityRef, componentType);
                    if (nodeComponent != null && nodeComponent.getState() == NodeState.AVAILABLE) {
                        nodeComponent.setState(NodeState.LOCKED);

                        // Update the visual item + subtitle position
                        updateNodeVisual(world, store, entityRef, connectedNode, NodeState.LOCKED);

                        LOGGER.atFine().log("Adjacent node %s updated: AVAILABLE → LOCKED", connectedId);
                    }
                }
            }
        });
    }

    /**
     * Updates the visual state of adjacent nodes.
     * Nodes that were LOCKED become AVAILABLE if now connected to an allocated node.
     */
    private void updateAdjacentNodeStates(@Nonnull SkillNode allocatedNode) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot update adjacent nodes - world is null or not alive");
            return;
        }

        TrailOfOrbis plugin = manager.getPlugin();
        com.hypixel.hytale.component.ComponentType<EntityStore, SkillNodeComponent> componentType =
            plugin.getSkillNodeComponentType();
        if (componentType == null) {
            LOGGER.atWarning().log("Cannot update adjacent nodes - SkillNodeComponent type is null");
            return;
        }

        LOGGER.atInfo().log("Updating adjacent nodes for %s: %s",
            allocatedNode.getId(), allocatedNode.getConnections());

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int updatedCount = 0;

            // Update each connected node
            for (String connectedId : allocatedNode.getConnections()) {
                // Skip already allocated nodes
                if (allocatedNodes.contains(connectedId)) {
                    LOGGER.atFine().log("Skipping %s - already allocated", connectedId);
                    continue;
                }

                // Look up entity dynamically (stored refs can become invalid)
                Ref<EntityStore> entityRef = findNodeEntityByNodeId(world, connectedId);
                if (entityRef == null) {
                    LOGGER.atFine().log("Skipping %s - entity not found via ECS lookup", connectedId);
                    continue;
                }

                // Get the SkillNodeComponent
                SkillNodeComponent nodeComponent = store.getComponent(entityRef, componentType);
                if (nodeComponent == null) {
                    LOGGER.atFine().log("Skipping %s - no SkillNodeComponent", connectedId);
                    continue;
                }

                // Update state from LOCKED to AVAILABLE
                if (nodeComponent.getState() == NodeState.LOCKED) {
                    nodeComponent.setState(NodeState.AVAILABLE);

                    // Update the node's item to the AVAILABLE variant (medium glow)
                    SkillTreeManager skillTreeManager = manager.getSkillTreeManager();
                    if (skillTreeManager != null) {
                        Optional<SkillNode> skillNodeOpt = skillTreeManager.getNode(connectedId);
                        if (skillNodeOpt.isPresent()) {
                            SkillNode skillNode = skillNodeOpt.get();

                            // Update visual item + subtitle position
                            updateNodeVisual(world, store, entityRef, skillNode, NodeState.AVAILABLE);
                            LOGGER.atInfo().log("Adjacent node %s updated: LOCKED → AVAILABLE", connectedId);
                        }
                    }

                    updatedCount++;
                }
            }

            LOGGER.atInfo().log("Adjacent node update complete: %d nodes updated", updatedCount);
        });
    }

    /**
     * Updates a node entity's visual to reflect a new state.
     *
     * <p>Handles both paths:
     * <ul>
     *   <li>Notable/Keystone: respawns entity (client can't refresh complex models in-place)</li>
     *   <li>Basic: updates item in-place + repositions subtitle for new state Y offset</li>
     * </ul>
     *
     * <p>This is the SINGLE entry point for all visual state changes. Every code path
     * that changes a node's state (allocation, deallocation, adjacent updates, respec)
     * should call this method instead of duplicating the respawn/in-place logic.
     *
     * <p><b>MUST be called from the world thread!</b>
     *
     * @param store     The entity store
     * @param entityRef The node entity reference (already resolved)
     * @param node      The skill node config
     * @param state     The new state
     */
    private void updateNodeVisual(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull SkillNode node,
            @Nonnull NodeState state) {

        boolean needsRespawn = node.isKeystone() || node.isNotable();
        if (needsRespawn) {
            // Respawn creates a new entity + new subtitle at the correct position
            respawnNodeEntity(world, store, entityRef, node, state);
        } else {
            // In-place item update + subtitle reposition
            SkillSanctumNodeSpawner.updateNodeItem(store, entityRef, node, state);
            String newItemId = getItemIdForNode(node, state);
            sendItemUpdateToPlayer(store, entityRef, newItemId);
            repositionSubtitleEntity(store, node, entityRef, state);
        }
    }

    /**
     * Updates a node's item to reflect a new state.
     *
     * <p>Uses custom items with different light levels:
     * <ul>
     *   <li>LOCKED: _Light0 (no glow)</li>
     *   <li>AVAILABLE: _Light50 (medium glow)</li>
     *   <li>ALLOCATED: _Light100 (full glow)</li>
     * </ul>
     *
     * @param nodeId The node ID
     * @param node   The skill node
     * @param state  The new state
     */
    private void updateNodeItemState(@Nonnull String nodeId, @Nonnull SkillNode node, @Nonnull NodeState state) {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot update node %s - world is null or not alive", nodeId);
            return;
        }

        LOGGER.atInfo().log("Scheduling item update for node %s -> state %s",
            nodeId, state);

        world.execute(() -> {
            // Look up entity dynamically by nodeId (stored refs can become invalid)
            Ref<EntityStore> entityRef = findNodeEntityByNodeId(world, nodeId);
            if (entityRef == null) {
                LOGGER.atWarning().log("Cannot update node %s - entity not found via ECS lookup", nodeId);
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            updateNodeVisual(world, store, entityRef, node, state);

            LOGGER.atInfo().log("Executed item update for node %s to state %s", nodeId, state);
        });
    }

    /**
     * Respawns a node entity with a new item and correct positioning for the new state.
     *
     * <p>This is needed for notable/keystone nodes (crystals/gems) because the client
     * doesn't properly refresh their complex models when receiving a ComponentUpdate.
     * Despawning and respawning forces a full visual refresh.
     *
     * <p>Uses the BASE position from the layout mapper (not the old entity's position)
     * because Y offsets differ by state — reusing the old adjusted position would place
     * the node at the wrong height for the new state.
     *
     * <p>Also removes and respawns the associated subtitle entity if it exists.
     */
    private void respawnNodeEntity(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> oldEntityRef,
            @Nonnull SkillNode node,
            @Nonnull NodeState state) {

        // Get BASE position from the layout mapper (no Y adjustment baked in).
        // spawnSingleNode will apply the correct Y offset for the NEW state.
        Vector3d basePosition = GalaxySpiralLayoutMapper.toWorldPosition(node);

        // Clear any pending entity tracker updates before removal to prevent
        // "Entity can't be removed and also receive an update" errors
        clearPendingUpdatesForEntity(store, oldEntityRef);

        // Remove the old node entity
        store.removeEntity(oldEntityRef, RemoveReason.REMOVE);
        LOGGER.atFine().log("Removed old entity for node %s", node.getId());

        // Remove old subtitle entity if present
        removeSubtitleEntityByNodeId(store, node.getId());

        // Get the spawner to create new entities
        SkillSanctumNodeSpawner spawner = manager.getNodeSpawner();
        if (spawner == null) {
            LOGGER.atWarning().log("Cannot respawn node %s - no spawner available", node.getId());
            return;
        }

        // Spawn new entity using the base position — spawnSingleNode applies the correct
        // Y offset for the NEW state internally (and registers the entity in nodeEntities)
        Ref<EntityStore> newEntityRef = spawner.spawnSingleNode(this, store, node, basePosition, state);
        if (newEntityRef != null && newEntityRef.isValid()) {
            // Get the new entity's actual position (with correct Y offset applied)
            TransformComponent newTransform = store.getComponent(newEntityRef, TransformComponent.getComponentType());
            Vector3d newPosition = newTransform != null ? newTransform.getPosition() : basePosition;

            LOGGER.atInfo().log("Respawned node %s at (%.1f, %.1f, %.1f) with state %s",
                node.getId(), newPosition.x, newPosition.y, newPosition.z, state);

            // Spawn new subtitle entity at the new node's actual position
            Ref<EntityStore> subtitleRef = spawner.spawnSubtitleEntity(
                store, node, newPosition, state, playerId);
            if (subtitleRef != null && subtitleRef.isValid()) {
                subtitleEntities.put(node.getId(), subtitleRef);
            }
        } else {
            LOGGER.atWarning().log("Failed to respawn node %s", node.getId());
        }
    }

    /**
     * Repositions the subtitle entity for a node after a state change.
     *
     * <p>For basic nodes (which are updated in-place rather than respawned),
     * the subtitle Y offset changes per state but wasn't being updated.
     * This method finds the subtitle entity and moves it to the correct position.
     *
     * <p><b>MUST be called from the world thread!</b>
     */
    private void repositionSubtitleEntity(
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillNode node,
            @Nonnull Ref<EntityStore> mainEntityRef,
            @Nonnull NodeState state) {

        Ref<EntityStore> subtitleRef = subtitleEntities.get(node.getId());

        // Stored refs can go stale after archetype changes — fall back to ECS lookup
        if (subtitleRef == null || !subtitleRef.isValid()) {
            subtitleRef = findSubtitleEntityByNodeId(store, node.getId());
            if (subtitleRef != null) {
                subtitleEntities.put(node.getId(), subtitleRef);
            } else {
                LOGGER.atFine().log("Cannot reposition subtitle for %s - entity not found (stale ref + ECS miss)",
                    node.getId());
                return;
            }
        }

        // Get the main node entity's current position
        TransformComponent mainTransform = store.getComponent(mainEntityRef, TransformComponent.getComponentType());
        if (mainTransform == null) {
            return;
        }
        Vector3d mainPos = mainTransform.getPosition();

        // Calculate the new subtitle Y offset for the new state
        SkillSanctumNodeSpawner spawner = manager.getNodeSpawner();
        if (spawner == null) {
            return;
        }
        double yOffset = spawner.calculateSubtitleYOffset(node, state);

        // Update the subtitle entity's position
        TransformComponent subtitleTransform = store.getComponent(subtitleRef, TransformComponent.getComponentType());
        if (subtitleTransform != null) {
            subtitleTransform.setPosition(new Vector3d(mainPos.x, mainPos.y + yOffset, mainPos.z));
        }
    }

    /**
     * Removes a subtitle entity by its parent node ID.
     *
     * <p>Uses ECS iteration to find the subtitle entity matching both the node ID
     * and this player's UUID, then removes it from the store.
     *
     * <p><b>MUST be called from the world thread!</b>
     *
     * @param store  The entity store
     * @param nodeId The parent node ID
     */
    private void removeSubtitleEntityByNodeId(@Nonnull Store<EntityStore> store, @Nonnull String nodeId) {
        // First try the tracked ref (fast path)
        Ref<EntityStore> trackedRef = subtitleEntities.remove(nodeId);
        if (trackedRef != null && trackedRef.isValid()) {
            clearPendingUpdatesForEntity(store, trackedRef);
            store.removeEntity(trackedRef, RemoveReason.REMOVE);
            LOGGER.atFine().log("Removed subtitle entity for node %s (tracked ref)", nodeId);
            return;
        }

        // Fallback: ECS iteration (ref may have been invalidated by archetype changes)
        ComponentType<EntityStore, SkillNodeSubtitleComponent> subtitleType =
            manager.getPlugin().getSkillNodeSubtitleComponentType();
        if (subtitleType == null) {
            return;
        }

        AtomicReference<Ref<EntityStore>> foundRef = new AtomicReference<>();
        store.forEachChunk(subtitleType, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
            if (foundRef.get() != null) return;
            for (int i = 0; i < chunk.size(); i++) {
                SkillNodeSubtitleComponent subtitle = chunk.getComponent(i, subtitleType);
                if (subtitle != null && nodeId.equals(subtitle.getNodeId()) && playerId.equals(subtitle.getOwnerPlayerId())) {
                    foundRef.set(chunk.getReferenceTo(i));
                    return;
                }
            }
        });

        Ref<EntityStore> ref = foundRef.get();
        if (ref != null && ref.isValid()) {
            clearPendingUpdatesForEntity(store, ref);
            store.removeEntity(ref, RemoveReason.REMOVE);
            LOGGER.atFine().log("Removed subtitle entity for node %s (ECS fallback)", nodeId);
        }
    }

    /**
     * Finds a subtitle entity by its parent node ID using ECS iteration.
     *
     * <p>This is a fallback for when the stored Ref in {@code subtitleEntities} has gone
     * stale due to archetype changes (component add/remove). The entity still exists in
     * the store but the old Ref points to the wrong slot.
     *
     * <p><b>MUST be called from the world thread!</b>
     *
     * @param store  The entity store
     * @param nodeId The parent node ID to find
     * @return The subtitle entity Ref, or null if not found
     */
    @Nullable
    private Ref<EntityStore> findSubtitleEntityByNodeId(
            @Nonnull Store<EntityStore> store,
            @Nonnull String nodeId) {

        ComponentType<EntityStore, SkillNodeSubtitleComponent> subtitleType =
            manager.getPlugin().getSkillNodeSubtitleComponentType();
        if (subtitleType == null) {
            return null;
        }

        AtomicReference<Ref<EntityStore>> foundRef = new AtomicReference<>();
        store.forEachChunk(subtitleType, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
            if (foundRef.get() != null) return;
            for (int i = 0; i < chunk.size(); i++) {
                SkillNodeSubtitleComponent subtitle = chunk.getComponent(i, subtitleType);
                if (subtitle != null && nodeId.equals(subtitle.getNodeId()) && playerId.equals(subtitle.getOwnerPlayerId())) {
                    foundRef.set(chunk.getReferenceTo(i));
                    return;
                }
            }
        });

        return foundRef.get();
    }

    /**
     * Clears pending entity tracker updates for an entity ref before it is removed.
     *
     * <p>When we queue interaction hints via {@code viewerComponent.updates}, then remove
     * the entity in the same tick, the {@code EntityTrackerSystems$SendPackets} system
     * detects the conflict: "Entity can't be removed and also receive an update!"
     * Clearing the updates before removal prevents this.
     *
     * @param store The entity store
     * @param entityRef The entity ref about to be removed
     */
    private void clearPendingUpdatesForEntity(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid()) return;

            Ref<EntityStore> playerEntityRef = getPlayerRefInSanctumWorld(playerRef, store);
            if (playerEntityRef == null) return;

            EntityTrackerSystems.EntityViewer viewer = store.getComponent(
                    playerEntityRef, EntityTrackerSystems.EntityViewer.getComponentType());
            if (viewer != null) {
                viewer.updates.remove(entityRef);
            }
        } catch (Exception e) {
            // Non-critical — entity removal will still succeed
            LOGGER.atFine().log("Could not clear pending updates for entity: %s", e.getMessage());
        }
    }

    /**
     * Gets the item ID for a node based on its type and state.
     */
    @Nonnull
    private String getItemIdForNode(@Nonnull SkillNode node, @Nonnull NodeState state) {
        SkillTreeRegion region = node.getSkillTreeRegion();
        String itemId;
        String nodeType;

        if (node.isKeystone()) {
            itemId = region.getKeystoneGemItemForState(state);
            nodeType = "KEYSTONE";
        } else if (node.isNotable()) {
            itemId = region.getMediumCrystalItemForState(state);
            nodeType = "NOTABLE";
        } else if (node.isStartNode() || "origin".equals(node.getId())) {
            itemId = region.getKeystoneGemItemForState(state);
            nodeType = "ORIGIN";
        } else {
            itemId = region.getEssenceItemForState(state);
            nodeType = "BASIC";
        }

        LOGGER.atInfo().log("getItemIdForNode: %s (%s, region=%s) -> %s",
            node.getId(), nodeType, region, itemId);
        return itemId;
    }

    /**
     * Sends an item ComponentUpdate to the player to sync visual changes.
     *
     * <p>ItemComponent doesn't have automatic network sync (unlike Interactions),
     * so we manually send the update via EntityViewer's update queue.
     */
    private void sendItemUpdateToPlayer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull String newItemId) {

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) {
            LOGGER.atWarning().log("Cannot send item update - player not found");
            return;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            LOGGER.atWarning().log("Cannot send item update - player entity ref invalid");
            return;
        }

        // Get player's EntityViewer component
        EntityTrackerSystems.EntityViewer viewerComponent =
            store.getComponent(playerEntityRef, EntityTrackerSystems.EntityViewer.getComponentType());

        if (viewerComponent == null) {
            LOGGER.atWarning().log("Cannot send item update - player has no EntityViewer");
            return;
        }

        // Only send if player knows about this entity
        if (!viewerComponent.sent.containsKey(entityRef)) {
            LOGGER.atFine().log("Skipping item update - entity not yet sent to player");
            return;
        }

        // Create ItemUpdate with new item data
        ItemUpdate update = new ItemUpdate(
            new ItemWithAllMetadata(
                newItemId,  // itemId
                1,          // quantity
                0.0,        // durability (not used)
                0.0,        // maxDurability (not used)
                false,      // overrideDroppedItemAnimation
                null        // metadata
            ),
            0f  // entityScale
        );

        // Queue the update for this entity
        viewerComponent.updates
            .computeIfAbsent(entityRef, k -> new EntityTrackerSystems.EntityUpdate())
            .queueUpdate(update);

        LOGGER.atInfo().log("Sent item update to player: entity=%s, item=%s", entityRef, newItemId);
    }

    /**
     * Ticks the proximity-based connection rendering.
     *
     * <p>Sends laser beam packets for the closest connections to the player.
     * Called every tick from the manager's tick loop — the renderer internally
     * throttles to its configured refresh interval.
     *
     * @implNote Must be called from the world thread.
     */
    public void tickConnectionRendering() {
        if (!active || !nodesSpawned || connectionRenderer == null || !connectionRenderer.isReady()) {
            if (System.currentTimeMillis() % 5000 < 100) {
                LOGGER.atWarning().log("[BEAM TICK] Skipped: active=%b, nodesSpawned=%b, renderer=%b, ready=%b",
                    active, nodesSpawned, connectionRenderer != null,
                    connectionRenderer != null && connectionRenderer.isReady());
            }
            return;
        }

        // Guard: player's entity ref must belong to the sanctum store.
        // Without this, resolvePlayerNetworkId() can throw IllegalStateException
        // from Ref.validate(Store) if the ref hasn't fully transitioned yet.
        if (!isPlayerInSanctumWorld()) {
            if (System.currentTimeMillis() % 5000 < 100) {
                LOGGER.atWarning().log("[BEAM TICK] Skipped: player NOT in sanctum world (store mismatch)");
            }
            return;
        }

        connectionRenderer.tickProximityRefresh(this);
    }

    /**
     * Updates nameplate visibility based on player distance.
     *
     * <p>Nameplates are spawned with the node and their text is updated based on distance:
     * <ul>
     *   <li>Within {@value #NAMEPLATE_VISIBLE_DISTANCE} blocks: Shows node name</li>
     *   <li>Beyond that distance: Shows empty text (effectively hidden)</li>
     * </ul>
     *
     * @implNote Must be called from the world thread.
     */
    public void tickNameplateVisibility() {
        if (!active || !nodesSpawned) {
            return;
        }

        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        // Skip if player is not in the sanctum world (e.g., died and respawned elsewhere)
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid() || ref.getStore() != world.getEntityStore().getStore()) {
            if (System.currentTimeMillis() % 5000 < 100) {
                LOGGER.atWarning().log("[NAMEPLATE TICK] Skipped: player store mismatch (ref=%s, valid=%b, sameStore=%b)",
                    ref, ref != null && ref.isValid(),
                    ref != null && ref.isValid() && ref.getStore() == world.getEntityStore().getStore());
            }
            return;
        }

        // Capture player position (thread-safe)
        Transform playerTransform = playerRef.getTransform();
        if (playerTransform == null) {
            return;
        }

        final double playerX = playerTransform.getPosition().x;
        final double playerY = playerTransform.getPosition().y;
        final double playerZ = playerTransform.getPosition().z;

        // Get component types
        TrailOfOrbis plugin = manager.getPlugin();
        final com.hypixel.hytale.component.ComponentType<EntityStore, SkillNodeComponent> skillNodeType =
            plugin.getSkillNodeComponentType();

        if (skillNodeType == null) {
            return;
        }

        final ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();
        final ComponentType<EntityStore, Nameplate> nameplateType = Nameplate.getComponentType();

        Store<EntityStore> store = world.getEntityStore().getStore();

        // Use array for mutable counter in forEachChunk lambda
        final int[] entitiesFound = {0};
        final int[] closeNodes = {0};

            // Use forEachChunk to iterate entities with SkillNodeComponent (main nodes)
            store.forEachChunk(skillNodeType, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    SkillNodeComponent skillNode = chunk.getComponent(i, skillNodeType);
                    if (skillNode == null) {
                        continue;
                    }

                    // Only process nodes owned by this player
                    if (!playerId.equals(skillNode.getOwnerPlayerId())) {
                        continue;
                    }

                    entitiesFound[0]++;
                    String nodeId = skillNode.getNodeId();

                    // Get node position from chunk
                    TransformComponent nodeTransform = chunk.getComponent(i, transformType);
                    if (nodeTransform == null) {
                        continue;
                    }

                    Vector3d nodePos = nodeTransform.getPosition();
                    if (nodePos == null) {
                        continue;
                    }

                    // Calculate squared distance
                    double dx = playerX - nodePos.x;
                    double dy = playerY - nodePos.y;
                    double dz = playerZ - nodePos.z;
                    double distSq = dx * dx + dy * dy + dz * dz;

                    boolean shouldBeVisible = distSq <= NAMEPLATE_VISIBLE_DISTANCE_SQ;

                    if (shouldBeVisible) {
                        closeNodes[0]++;
                    }

                    // Get existing nameplate from chunk (main node name)
                    Nameplate nameplate = chunk.getComponent(i, nameplateType);
                    if (nameplate != null) {
                        // Update nameplate text based on visibility
                        String currentText = nameplate.getText();
                        String desiredText = "";
                        if (shouldBeVisible) {
                            // Look up the node name from config
                            Optional<SkillNode> nodeOpt = manager.getSkillTreeManager().getNode(nodeId);
                            desiredText = nodeOpt.map(SkillNode::getName).orElse(nodeId);
                        }

                        if (!desiredText.equals(currentText)) {
                            nameplate.setText(desiredText);
                        }
                    }

                }
            });

            // Iterate subtitle entities with SkillNodeSubtitleComponent
            final ComponentType<EntityStore, SkillNodeSubtitleComponent> subtitleType =
                plugin.getSkillNodeSubtitleComponentType();
            if (subtitleType != null) {
                store.forEachChunk(subtitleType, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        SkillNodeSubtitleComponent subtitle = chunk.getComponent(i, subtitleType);
                        if (subtitle == null || !playerId.equals(subtitle.getOwnerPlayerId())) {
                            continue;
                        }

                        TransformComponent subtitleTransform = chunk.getComponent(i, transformType);
                        if (subtitleTransform == null) {
                            continue;
                        }

                        Vector3d subtitlePos = subtitleTransform.getPosition();
                        if (subtitlePos == null) {
                            continue;
                        }

                        double dx2 = playerX - subtitlePos.x;
                        double dy2 = playerY - subtitlePos.y;
                        double dz2 = playerZ - subtitlePos.z;
                        double distSq2 = dx2 * dx2 + dy2 * dy2 + dz2 * dz2;

                        boolean subtitleVisible = distSq2 <= NAMEPLATE_VISIBLE_DISTANCE_SQ;

                        Nameplate subtitleNameplate = chunk.getComponent(i, nameplateType);
                        if (subtitleNameplate != null) {
                            String currentSubtitle = subtitleNameplate.getText();
                            String desiredSubtitle = "";
                            if (subtitleVisible) {
                                String nodeId = subtitle.getNodeId();
                                Optional<SkillNode> nodeOpt = manager.getSkillTreeManager().getNode(nodeId);
                                desiredSubtitle = nodeOpt.map(NodeLabelFormatter::getSubtitleText).orElse("");
                                if (desiredSubtitle == null) {
                                    desiredSubtitle = "";
                                }
                            }

                            if (!desiredSubtitle.equals(currentSubtitle)) {
                                subtitleNameplate.setText(desiredSubtitle);
                            }
                        }
                    }
                });
            }

        // Debug log once per second
        if (System.currentTimeMillis() % 1000 < 100) {
            LOGGER.atFine().log("Nameplate tick: %d nodes, %d close, player at (%.0f, %.0f, %.0f)",
                entitiesFound[0], closeNodes[0], playerX, playerY, playerZ);
        }
    }

    /**
     * Gets the entity Ref for a node orb.
     *
     * @param nodeId The node ID
     * @return The entity Ref, or null if not found
     */
    @Nullable
    public Ref<EntityStore> getNodeEntity(@Nonnull String nodeId) {
        return nodeEntities.get(nodeId);
    }

    /**
     * Finds a node entity by its nodeId using ECS iteration.
     *
     * <p>This is more reliable than using stored Refs because Refs can become
     * invalid when entity archetypes change (e.g., components added/removed).
     *
     * <p><b>MUST be called from the world thread!</b>
     *
     * @param world  The sanctum world
     * @param nodeId The node ID to find
     * @return The entity Ref, or null if not found
     */
    @Nullable
    private Ref<EntityStore> findNodeEntityByNodeId(@Nonnull World world, @Nonnull String nodeId) {
        TrailOfOrbis plugin = manager.getPlugin();
        ComponentType<EntityStore, SkillNodeComponent> componentType = plugin.getSkillNodeComponentType();
        if (componentType == null) {
            return null;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        AtomicReference<Ref<EntityStore>> foundRef = new AtomicReference<>();

        store.forEachChunk(componentType, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
            if (foundRef.get() != null) {
                return; // Already found
            }

            for (int i = 0; i < chunk.size(); i++) {
                SkillNodeComponent skillNode = chunk.getComponent(i, componentType);
                if (skillNode == null) {
                    continue;
                }

                // Check if this is the node we're looking for AND it belongs to this player
                if (nodeId.equals(skillNode.getNodeId()) && playerId.equals(skillNode.getOwnerPlayerId())) {
                    foundRef.set(chunk.getReferenceTo(i));
                    return;
                }
            }
        });

        return foundRef.get();
    }

    /**
     * Checks if a node is allocated in this sanctum.
     *
     * @param nodeId The node ID to check
     * @return true if allocated
     */
    public boolean isNodeAllocated(@Nonnull String nodeId) {
        return allocatedNodes.contains(nodeId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL (for NodeSpawner)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers a spawned node entity.
     *
     * @param nodeId    The node ID
     * @param entityRef The entity Ref
     */
    void registerNodeEntity(@Nonnull String nodeId, @Nonnull Ref<EntityStore> entityRef) {
        nodeEntities.put(nodeId, entityRef);
    }

    /**
     * Registers a spawned subtitle entity.
     *
     * @param nodeId    The parent node ID
     * @param entityRef The subtitle entity Ref
     */
    void registerSubtitleEntity(@Nonnull String nodeId, @Nonnull Ref<EntityStore> entityRef) {
        subtitleEntities.put(nodeId, entityRef);
    }

    /**
     * Called by the spawner when all entities have been created and registered.
     * This enables nameplate visibility ticking.
     */
    void markSpawningComplete() {
        nodesSpawned = true;
        LOGGER.atInfo().log("Spawning complete for sanctum %s - %d nodes registered",
            playerId, nodeEntities.size());
    }

    /**
     * Gets all node IDs that have spawned entities.
     */
    @Nonnull
    public Set<String> getSpawnedNodeIds() {
        return nodeEntities.keySet();
    }

    // ═══════════════════════════════════════════════════════════════════
    // VISUAL COMPONENT RESYNC
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns whether all component resync phases are complete.
     */
    public boolean isComponentResyncDone() {
        return componentResyncDone && delayedComponentResyncDone;
    }

    /**
     * Ticks the visual component resync for this instance.
     *
     * <p>After all node entities finish spawning, re-sets every visual
     * component's value to itself. This re-flags {@code isNetworkOutdated} on each
     * component, forcing the entity tracker to re-send the update to the client.
     *
     * <p>Two phases:
     * <ol>
     *   <li><b>Immediate</b>: runs as soon as nodes are fully spawned</li>
     *   <li><b>Delayed (500ms)</b>: safety net in case some tracker cycles were missed</li>
     * </ol>
     *
     * @implNote Must be called from the world thread.
     */
    public void tickComponentResync() {
        if (!active || !nodesSpawned || isComponentResyncDone()) {
            return;
        }

        // Verify player is still in the sanctum world
        if (!isPlayerInSanctumWorld()) {
            return;
        }

        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        // Phase 1: Immediate resync
        if (!componentResyncDone) {
            componentResyncDone = true;
            componentResyncTimestamp = System.currentTimeMillis();
            resyncAllVisualComponents(world, "Phase1-Immediate");
            return;
        }

        // Phase 2: Delayed resync (~500ms after phase 1)
        if (!delayedComponentResyncDone
                && System.currentTimeMillis() - componentResyncTimestamp >= 500) {
            delayedComponentResyncDone = true;
            resyncAllVisualComponents(world, "Phase2-Delayed");
        }
    }

    /**
     * Schedules a deferred visual component resync ~200ms from now.
     *
     * <p>Called when an inventory change is detected for a player in the sanctum.
     * The delay ensures this runs AFTER the debounced UpdateItems packet (100ms debounce
     * + network RTT) that causes the visual reset.
     */
    public void scheduleVisualRefresh() {
        this.visualRefreshAt = System.currentTimeMillis() + 200;
    }

    /**
     * Checks if a deferred visual refresh is due and executes it.
     *
     * <p>Called from the tick loop. When the scheduled time arrives, resyncs all
     * visual components on the world thread to correct the client-side reset
     * caused by UpdateItems packets.
     *
     * @implNote Must be called from the world thread.
     */
    public void tickVisualRefresh() {
        if (visualRefreshAt == 0 || !active || !nodesSpawned) return;
        if (System.currentTimeMillis() < visualRefreshAt) return;

        visualRefreshAt = 0;
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) return;
        resyncAllVisualComponents(world, "InventoryChange");
    }

    /**
     * Re-sets all visual components on spawned entities to force network re-sync.
     *
     * <p>For each entity, calls {@code setX(getX())} on:
     * <ul>
     *   <li>{@link EntityScaleComponent} — entity size</li>
     *   <li>{@link ItemComponent} — item model</li>
     *   <li>{@link DynamicLight} — glow/light color</li>
     * </ul>
     *
     * <p>Each setter internally flags {@code isNetworkOutdated = true}, which tells
     * the entity tracker to re-send that component's state on the next tick.
     *
     * <p><b>Must be called on the world thread.</b>
     *
     * @param world The sanctum world
     * @param phase Label for logging (e.g., "Phase1-Immediate", "Phase2-Delayed")
     */
    private void resyncAllVisualComponents(@Nonnull World world, @Nonnull String phase) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        int scaleCount = 0;
        int itemCount = 0;
        int lightCount = 0;

        // Resync all node entities
        for (Ref<EntityStore> ref : nodeEntities.values()) {
            if (ref == null || !ref.isValid()) continue;

            EntityScaleComponent scale = store.getComponent(ref, EntityScaleComponent.getComponentType());
            if (scale != null) {
                scale.setScale(scale.getScale());
                scaleCount++;
            }

            ItemComponent item = store.getComponent(ref, ItemComponent.getComponentType());
            if (item != null) {
                item.setItemStack(item.getItemStack());
                itemCount++;
            }

            DynamicLight light = store.getComponent(ref, DynamicLight.getComponentType());
            if (light != null) {
                light.setColorLight(light.getColorLight());
                lightCount++;
            }
        }

        LOGGER.atInfo().log("Component resync %s for sanctum %s: %d scales, %d items, %d lights",
            phase, playerId, scaleCount, itemCount, lightCount);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Gets the sanctum world from the underlying realm instance.
     */
    @Nullable
    public World getSanctumWorld() {
        return realmInstance != null ? realmInstance.getWorld() : null;
    }

    /**
     * Gets the underlying realm instance.
     */
    @Nullable
    public RealmInstance getRealmInstance() {
        return realmInstance;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Checks if the player is currently in the sanctum world.
     *
     * <p>This verifies that the player's entity ref belongs to the sanctum store,
     * not just that it's valid. A ref can be valid in a different world's store
     * (e.g., after death respawn), which would cause AIOOBE if used with the
     * sanctum store.
     *
     * @return true if the player is in the sanctum world
     */
    public boolean isPlayerInSanctumWorld() {
        World world = getSanctumWorld();
        if (world == null || !world.isAlive()) return false;

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) return false;

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return false;

        return ref.getStore() == world.getEntityStore().getStore();
    }

    /**
     * Gets the player's entity ref only if they are in the sanctum world.
     *
     * <p>Returns null if the player has left this world. This prevents cross-store
     * AIOOBE crashes where a ref valid in world A is used with world B's store.
     *
     * @param playerRef    The player reference
     * @param sanctumStore The sanctum world's entity store
     * @return The entity ref if in sanctum world, or null
     */
    @Nullable
    private Ref<EntityStore> getPlayerRefInSanctumWorld(@Nonnull PlayerRef playerRef, @Nonnull Store<EntityStore> sanctumStore) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        // Verify ref belongs to THIS store (player is in sanctum world)
        if (ref.getStore() != sanctumStore) {
            return null;
        }
        return ref;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the age of this instance in milliseconds.
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - createdAt;
    }

    /**
     * Gets the number of spawned node entities.
     */
    public int getSpawnedNodeCount() {
        return nodeEntities.size();
    }

    /**
     * Gets the number of allocated nodes.
     */
    public int getAllocatedNodeCount() {
        return allocatedNodes.size();
    }

    /**
     * Checks if nodes have been spawned.
     */
    public boolean areNodesSpawned() {
        return nodesSpawned;
    }

    @Override
    public String toString() {
        return String.format(
            "SkillSanctumInstance{player=%s, active=%b, nodes=%d, allocated=%d}",
            playerId.toString().substring(0, 8),
            active,
            nodeEntities.size(),
            allocatedNodes.size()
        );
    }
}
