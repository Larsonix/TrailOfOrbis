package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.config.WorldReturnPoint;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.sanctum.interactions.SkillNodeInteraction;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.sanctum.config.SkillSanctumConfig;
import io.github.larsonix.trailoforbis.sanctum.ui.SkillNodeHudManager;
import io.github.larsonix.trailoforbis.sanctum.ui.SkillPointHudManager;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.api.SkillTreeEvents;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main orchestrator for the Skill Sanctum system.
 *
 * <p>The Skill Sanctum is a dedicated 3D realm where players physically explore
 * their skill tree. Instead of a UI panel, players walk among floating orbs
 * representing skill nodes and press F to allocate skill points.
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Track active sanctum instances per player</li>
 *   <li>Handle opening and closing of sanctums</li>
 *   <li>Coordinate node spawning via {@link SkillSanctumNodeSpawner}</li>
 *   <li>Bridge between sanctum orbs and {@link SkillTreeManager}</li>
 * </ul>
 *
 * <h3>Lifecycle:</h3>
 * <ol>
 *   <li>Player requests sanctum entry (command or portal)</li>
 *   <li>Manager creates {@link SkillSanctumInstance} for player</li>
 *   <li>Instance creates realm, spawns 121 node orbs</li>
 *   <li>Player teleports to sanctum spawn point</li>
 *   <li>Player interacts with orbs to allocate nodes</li>
 *   <li>Player exits via command or portal</li>
 *   <li>Instance cleans up entities and realm</li>
 * </ol>
 *
 * @see SkillSanctumInstance
 * @see io.github.larsonix.trailoforbis.sanctum.components.SkillNodeComponent
 */
public class SkillSanctumManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final TrailOfOrbis plugin;
    private final SkillTreeManager skillTreeManager;
    private final SkillSanctumConfig config;
    private final SkillNodeHudManager skillNodeHudManager;
    private final SkillPointHudManager skillPointHudManager;

    /**
     * Node spawner for creating orb entities.
     */
    @Nullable
    private SkillSanctumNodeSpawner nodeSpawner;

    /**
     * Scheduled executor for periodic connection rendering.
     */
    @Nullable
    private ScheduledExecutorService tickExecutor;

    /**
     * Active sanctum instances keyed by player UUID.
     * Each player can only have one active sanctum at a time.
     */
    private final Map<UUID, SkillSanctumInstance> activeInstances;

    /**
     * Whether the sanctum system is enabled.
     */
    private boolean enabled;

    /**
     * Skill tree event listeners for forwarding to sanctum instances.
     */
    @Nullable
    private SkillTreeEvents.NodeAllocatedListener nodeAllocatedListener;
    @Nullable
    private SkillTreeEvents.NodeDeallocatedListener nodeDeallocatedListener;
    @Nullable
    private SkillTreeEvents.RespecListener respecListener;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new SkillSanctumManager.
     *
     * @param plugin           The main plugin instance
     * @param skillTreeManager The skill tree manager for allocation
     */
    public SkillSanctumManager(@Nonnull TrailOfOrbis plugin, @Nonnull SkillTreeManager skillTreeManager,
                               @Nonnull SkillSanctumConfig config) {
        this.plugin = plugin;
        this.skillTreeManager = skillTreeManager;
        this.config = config;
        this.skillNodeHudManager = new SkillNodeHudManager();
        this.skillPointHudManager = new SkillPointHudManager(skillTreeManager);
        this.activeInstances = new ConcurrentHashMap<>();
        this.enabled = false;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initializes the sanctum system.
     *
     * <p>This registers necessary components, systems, and listeners.
     *
     * @return true if initialization succeeded
     */
    public boolean initialize() {
        if (!config.isEnabled()) {
            LOGGER.atInfo().log("Skill Sanctum system disabled via config");
            return false;
        }

        LOGGER.atInfo().log("Initializing Skill Sanctum system...");

        // Create node spawner
        this.nodeSpawner = new SkillSanctumNodeSpawner(plugin, skillTreeManager);

        // Register skill node interaction (F-key for allocation/inspection)
        registerSkillNodeInteraction();

        // Register skill tree event listeners for real-time sanctum updates
        registerSkillTreeEventListeners();

        // Register inventory change listener for visual refresh in sanctum
        registerInventoryChangeListener();

        // Register skill point HUD event listeners for instant refresh on allocation/respec
        skillPointHudManager.registerEventListeners();

        // Start periodic tick executor for sanctum operations
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SanctumTicker");
            t.setDaemon(true);
            return t;
        });
        tickExecutor.scheduleAtFixedRate(this::tickAllInstances, 50, 50, TimeUnit.MILLISECONDS);

        this.enabled = true;

        // Clean up orphaned sanctum instance worlds from previous server sessions.
        // Hytale persists instance worlds to disk and reloads them on restart.
        // Since sanctums are ephemeral (single-player, session-scoped), any that survive
        // a restart are orphans — no SkillSanctumInstance tracks them, so they pile up
        // and cause Store-shutdown errors in Hytale's PortalsPlugin.
        purgeOrphanedSanctumWorlds();

        LOGGER.atInfo().log("Skill Sanctum system initialized");
        return true;
    }

    /**
     * Removes any sanctum instance worlds left over from a previous server session.
     *
     * <p>Sanctum worlds follow the naming pattern {@code instance-realm_skill_sanctum-<uuid>}.
     * On a fresh startup, none of these should exist — any that do are orphans from a
     * previous session that weren't properly cleaned up (e.g., server crash, or the bug
     * where {@code close()} didn't call {@code safeRemoveInstance()}).
     *
     * <p><b>Note:</b> This is a best-effort safety net. Hytale loads persisted worlds
     * before plugins initialize, so orphans already cause ~2s per world of load time
     * before this runs. The primary defense is {@code setDeleteOnUniverseStart(true)}
     * set at world creation time, which makes Hytale delete orphan files before loading.
     */
    private void purgeOrphanedSanctumWorlds() {
        try {
            Map<String, World> allWorlds = Universe.get().getWorlds();
            int found = 0;
            int purged = 0;
            for (Map.Entry<String, World> entry : allWorlds.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith("instance-realm_skill_sanctum-")) {
                    found++;
                    World world = entry.getValue();
                    try {
                        InstancesPlugin.safeRemoveInstance(world);
                        purged++;
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Failed to purge orphaned sanctum world: %s", name);
                    }
                }
            }
            if (found > 0) {
                LOGGER.atWarning().log("Found %d orphaned sanctum world(s) from previous session, purged %d. "
                    + "If this persists, delete universe/worlds/instance-realm_skill_sanctum-* directories.",
                    found, purged);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to enumerate worlds for orphan cleanup");
        }
    }

    /**
     * Registers listeners for skill tree events to update sanctums in real-time.
     *
     * <p>This enables sanctum orbs to react immediately when nodes are allocated
     * or deallocated via commands (e.g., /tooadmin skilltree allocateall, /skilltree respec).
     */
    private void registerSkillTreeEventListeners() {
        // Node allocated listener - forward to sanctum instance
        nodeAllocatedListener = (playerId, node, pointsRemaining, statsBefore, statsAfter) -> {
            SkillSanctumInstance instance = activeInstances.get(playerId);
            if (instance != null && instance.isActive()) {
                LOGGER.atFine().log("Forwarding node allocated event to sanctum: %s -> %s",
                    playerId, node.getId());
                instance.onNodeAllocated(node.getId());
            }
        };
        skillTreeManager.registerNodeAllocatedListener(nodeAllocatedListener);

        // Node deallocated listener - forward to sanctum instance
        nodeDeallocatedListener = (playerId, node, pointsRefunded, pointsRemaining, refundPointsRemaining, statsBefore, statsAfter) -> {
            SkillSanctumInstance instance = activeInstances.get(playerId);
            if (instance != null && instance.isActive()) {
                LOGGER.atFine().log("Forwarding node deallocated event to sanctum: %s -> %s",
                    playerId, node.getId());
                instance.onNodeDeallocated(node.getId());
            }
        };
        skillTreeManager.registerNodeDeallocatedListener(nodeDeallocatedListener);

        // Respec listener - forward to sanctum instance
        respecListener = (playerId, nodesCleared, pointsRefunded, freeRespecsRemaining) -> {
            SkillSanctumInstance instance = activeInstances.get(playerId);
            if (instance != null && instance.isActive()) {
                LOGGER.atFine().log("Forwarding respec event to sanctum: %s (cleared %d nodes)",
                    playerId, nodesCleared.size());
                instance.onRespec(nodesCleared);
            }
        };
        skillTreeManager.registerRespecListener(respecListener);

        LOGGER.atInfo().log("Registered skill tree event listeners for sanctum updates");
    }

    /**
     * Registers a listener for inventory changes to schedule visual refreshes.
     *
     * <p>When a player moves modded RPG gear in their inventory, {@code ItemSyncService.syncAllItems()}
     * sends UpdateItems packets that cause the Hytale client to reset visual components
     * (scale, light, item model) on nearby entities — including sanctum node orbs.
     *
     * <p>This listener detects inventory changes for players in active sanctums and
     * schedules a deferred visual resync (~400ms) that re-sends the correct component
     * values after the UpdateItems packet has already been processed by the client.
     */
    private void registerInventoryChangeListener() {
        // Note: The actual registration happens in TrailOfOrbis via InventoryChangeEventSystem.
        // The handler is registered as: system.addHandler(sanctumManager::onInventoryChange)
        LOGGER.atInfo().log("Registered inventory change listener for sanctum visual refresh");
    }

    /**
     * Handles inventory change events for sanctum visual refresh.
     *
     * <p>When a player moves modded RPG gear, UpdateItems packets reset visual
     * components on nearby entities (including sanctum node orbs). This handler
     * schedules a deferred visual resync after the packet is processed.
     *
     * @param player The player whose inventory changed
     * @param event  The inventory change event
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        UUID uuid = playerRef.getUuid();
        SkillSanctumInstance instance = activeInstances.get(uuid);
        if (instance != null && instance.isActive()) {
            instance.scheduleVisualRefresh();
        }
    }

    /**
     * Ticks all active sanctum instances for node spawning and connection block placement.
     */
    private void tickAllInstances() {
        // Process pending node spawns per instance (batch processing)
        if (nodeSpawner != null) {
            for (SkillSanctumInstance instance : activeInstances.values()) {
                if (!instance.isActive()) continue;

                // Wait for chunks to load AND player to arrive before spawning entities
                // (spawning before the player is in-world causes entity tracker to consume
                // isNetworkOutdated flags with no viewer, permanently losing visual updates)
                if (!instance.areChunksReady()) continue;
                if (!instance.isPlayerInSanctumWorld()) continue;

                try {
                    if (nodeSpawner.hasPendingSpawnsForInstance(instance.getPlayerId())) {
                        nodeSpawner.processPendingSpawnsForInstance(instance);
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error processing node spawns for %s", instance.getPlayerId());
                }
            }
        }

        // Detect stale instances (player left sanctum world via death, teleport, etc.)
        List<UUID> staleInstances = null;
        for (SkillSanctumInstance instance : activeInstances.values()) {
            if (instance.isActive() && instance.isFlightEnabled() && !instance.isPlayerInSanctumWorld()) {
                // Only consider stale after flight was enabled (player was actually in the sanctum).
                // Before flight is enabled, the player is still teleporting in.
                if (staleInstances == null) staleInstances = new ArrayList<>();
                staleInstances.add(instance.getPlayerId());
            }
        }
        if (staleInstances != null) {
            for (UUID stalePlayerId : staleInstances) {
                LOGGER.atWarning().log("Auto-closing stale sanctum for %s (player left world)", stalePlayerId);
                // Skip teleportation - player is already gone (disconnect, death, etc.)
                closeSanctum(stalePlayerId, false);
            }
        }

        // Tick operations with throttled intervals (tick loop runs every 100ms)
        // All tick methods access ECS components and MUST run on the world thread.
        // We dispatch a single world.execute() per instance with per-method try-catch
        // so one method's failure doesn't block others.
        long currentTime = System.currentTimeMillis();

        for (SkillSanctumInstance instance : activeInstances.values()) {
            if (!instance.isActive()) continue;

            World world = instance.getSanctumWorld();
            if (world == null || !world.isAlive()) continue;

            // Pre-compute throttle conditions on the ticker thread (no ECS access needed)
            boolean shouldTickForceFlight = instance.isFlightEnabled() && currentTime % 1000 < 100;
            boolean shouldTickHints = !instance.areInitialHintsSent() || currentTime % 5000 < 100;

            // Dispatch ALL tick work to the world thread (single dispatch, correct thread)
            world.execute(() -> {
                // Refresh proximity-based laser beams (internally throttled)
                try { instance.tickConnectionRendering(); }
                catch (Exception e) { LOGGER.atWarning().withCause(e).log("tickConnectionRendering failed for %s", instance.getPlayerId()); }

                // Update nameplate visibility every tick for responsive show/hide
                try { instance.tickNameplateVisibility(); }
                catch (Exception e) { LOGGER.atWarning().withCause(e).log("tickNameplateVisibility failed for %s", instance.getPlayerId()); }

                // Enable flight once player is in the sanctum (teleport is async)
                if (!instance.isFlightEnabled()) {
                    try { instance.tickFlightEnable(); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log("tickFlightEnable failed for %s", instance.getPlayerId()); }
                } else if (shouldTickForceFlight) {
                    try { instance.tickForceFlight(); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log("tickForceFlight failed for %s", instance.getPlayerId()); }
                }

                // Setup interactions once player is in the sanctum (teleport is async)
                if (!instance.areInteractionsSetUp()) {
                    try { instance.tickInteractionSetup(); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log("tickInteractionSetup failed for %s", instance.getPlayerId()); }
                }

                // Send interaction hints
                if (shouldTickHints) {
                    try { instance.tickInteractionHints(); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log("tickInteractionHints failed for %s", instance.getPlayerId()); }
                }

                // Resync visual components after all entities finish spawning
                if (!instance.isComponentResyncDone()) {
                    try { instance.tickComponentResync(); }
                    catch (Exception e) { LOGGER.atWarning().withCause(e).log("tickComponentResync failed for %s", instance.getPlayerId()); }
                }

                // Check for deferred visual refresh (inventory change correction)
                try { instance.tickVisualRefresh(); }
                catch (Exception e) { LOGGER.atWarning().withCause(e).log("tickVisualRefresh failed for %s", instance.getPlayerId()); }
            });
        }
    }

    /**
     * Shuts down the sanctum system.
     *
     * <p>Closes all active instances and cleans up resources.
     */
    public void shutdown() {
        LOGGER.atInfo().log("Shutting down Skill Sanctum system...");

        // Unregister skill tree event listeners
        unregisterSkillTreeEventListeners();

        // Unregister and remove skill point HUDs
        skillPointHudManager.unregisterEventListeners();
        skillPointHudManager.removeAllHuds();

        // Stop the tick executor
        if (tickExecutor != null) {
            tickExecutor.shutdown();
            try {
                if (!tickExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    tickExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                tickExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            tickExecutor = null;
        }

        // Remove all skill node detail HUDs
        skillNodeHudManager.removeAllHuds();

        // Close all active instances (close() now calls safeRemoveInstance)
        for (SkillSanctumInstance instance : activeInstances.values()) {
            try {
                instance.close();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to close sanctum for player %s",
                    instance.getPlayerId());
            }
        }
        activeInstances.clear();

        // Safety net: purge any sanctum worlds that escaped instance tracking.
        // This catches edge cases like worlds created but never registered (spawn failure),
        // or worlds from a previous session that weren't cleaned up at startup.
        purgeOrphanedSanctumWorlds();

        this.nodeSpawner = null;
        this.enabled = false;
        LOGGER.atInfo().log("Skill Sanctum system shutdown complete");
    }

    /**
     * Unregisters skill tree event listeners.
     */
    private void unregisterSkillTreeEventListeners() {
        if (nodeAllocatedListener != null) {
            skillTreeManager.unregisterNodeAllocatedListener(nodeAllocatedListener);
            nodeAllocatedListener = null;
        }
        if (nodeDeallocatedListener != null) {
            skillTreeManager.unregisterNodeDeallocatedListener(nodeDeallocatedListener);
            nodeDeallocatedListener = null;
        }
        if (respecListener != null) {
            skillTreeManager.unregisterRespecListener(respecListener);
            respecListener = null;
        }
        LOGGER.atInfo().log("Unregistered skill tree event listeners");
    }

    /**
     * Registers skill node interactions with Hytale's interaction system.
     *
     * <p>This enables F-key (Use) → SkillNodeInteraction for node allocation.
     *
     * <p>Node inspection (detail page) is handled separately via the damage system:
     * when a player left-clicks a skill node, RPGDamageSystem intercepts the damage
     * and opens the detail page instead.
     *
     * <p>The F-key registration follows the pattern used by UseNPCInteraction:
     * <ol>
     *   <li>Register the codec with Interaction.CODEC</li>
     *   <li>Load the interaction instance via Interaction.getAssetStore()</li>
     *   <li>Load the RootInteraction via RootInteraction.getAssetStore()</li>
     * </ol>
     */
    private void registerSkillNodeInteraction() {
        try {
            // Register SkillNodeInteraction (F-key for allocation only)
            Interaction.CODEC.register(
                "AllocateSkillNode",
                SkillNodeInteraction.class,
                SkillNodeInteraction.CODEC
            );

            Interaction.getAssetStore().loadAssets(
                "TrailOfOrbis:TrailOfOrbis",
                List.of(new SkillNodeInteraction(SkillNodeInteraction.DEFAULT_ID))
            );

            RootInteraction.getAssetStore().loadAssets(
                "TrailOfOrbis:TrailOfOrbis",
                List.of(SkillNodeInteraction.DEFAULT_ROOT)
            );

            LOGGER.atInfo().log("Registered SkillNodeInteraction (F-key allocation)");

            // Note: Node inspection (left-click) is handled by RPGDamageSystem.handleSkillNodeInspection()
            // which intercepts damage on skill nodes and opens the detail page

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to register skill node interactions");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens a skill sanctum for a player.
     *
     * <p>If the player already has an active sanctum, this teleports them
     * back to it instead of creating a new one.
     *
     * @param playerRef The player to open the sanctum for
     * @return CompletableFuture that completes when the sanctum is ready
     */
    @Nonnull
    public CompletableFuture<Boolean> openSanctum(@Nonnull PlayerRef playerRef) {
        if (!enabled) {
            LOGGER.atWarning().log("Sanctum system not enabled, cannot open for %s",
                playerRef.getUuid());
            return CompletableFuture.completedFuture(false);
        }

        UUID playerId = playerRef.getUuid();

        // Check for existing instance
        SkillSanctumInstance existing = activeInstances.get(playerId);
        if (existing != null && existing.isActive()) {
            LOGGER.atInfo().log("Player %s already has an active sanctum, teleporting back",
                playerId);
            return CompletableFuture.completedFuture(existing.teleportPlayerToSpawn(playerRef));
        }

        // Trigger guide milestone for first sanctum visit
        if (plugin.getGuideManager() != null) {
            plugin.getGuideManager().tryShow(playerId, io.github.larsonix.trailoforbis.guide.GuideMilestone.SKILL_SANCTUM);
        }

        // Get realms manager
        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            LOGGER.atWarning().log("RealmsManager not available - cannot open sanctum for %s", playerId);
            return CompletableFuture.completedFuture(false);
        }

        // Create sanctum map data
        RealmMapData sanctumMap = createSanctumMapData();

        // Get origin world (player's current world)
        World originWorld = Universe.get().getWorld(playerRef.getWorldUuid());
        if (originWorld == null) {
            LOGGER.atWarning().log("Could not determine origin world for %s", playerId);
            return CompletableFuture.completedFuture(false);
        }

        // Get player's current position for return point
        double returnX = 0, returnY = 64, returnZ = 0;
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef != null && playerEntityRef.isValid()) {
            Store<EntityStore> store = originWorld.getEntityStore().getStore();
            TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                returnX = pos.x;
                returnY = pos.y;
                returnZ = pos.z;
            }
        }

        // Create return point Transform for player exit
        final Transform returnPoint = new Transform(
            new Vector3d(returnX, returnY, returnZ),
            new Vector3f(0, 0, 0)
        );

        // Resolve ultimate return world (follow instance chain to find permanent world)
        // This is used as a fallback if the immediate return world is destroyed
        final UUID ultimateReturnWorldId = resolveUltimateReturnWorld(originWorld);

        LOGGER.atInfo().log("Creating skill sanctum for player %s (return: %.1f, %.1f, %.1f, ultimate: %s)",
            playerId, returnX, returnY, returnZ,
            ultimateReturnWorldId != null ? ultimateReturnWorldId.toString().substring(0, 8) : "null");

        // Open the realm
        return realmsManager.openRealm(sanctumMap, playerId, originWorld.getWorldConfig().getUuid(), returnX, returnY, returnZ)
            .thenCompose(realmInstance -> {
                if (realmInstance == null) {
                    LOGGER.atWarning().log("Failed to create realm instance for %s", playerId);
                    return CompletableFuture.completedFuture(false);
                }

                // Create sanctum instance wrapper with return point and fallback world
                SkillSanctumInstance instance = new SkillSanctumInstance(
                    this, playerId, realmInstance, returnPoint, ultimateReturnWorldId);

                // Start async chunk loading — spawning deferred to tick loop
                // (entities must spawn AFTER the player arrives so the entity tracker
                // sends component updates to a client that can receive them)
                instance.startChunkLoadAwait(null);

                // Initialize and spawn nodes
                return instance.initializeAndSpawnNodes(nodeSpawner)
                    .thenApply(success -> {
                        if (success) {
                            activeInstances.put(playerId, instance);
                            LOGGER.atInfo().log("Sanctum ready for %s, teleporting player", playerId);
                            return instance.teleportPlayerToSpawn(playerRef);
                        } else {
                            LOGGER.atWarning().log("Failed to initialize sanctum for %s", playerId);
                            instance.close();
                            return false;
                        }
                    });
            })
            .exceptionally(ex -> {
                LOGGER.atWarning().withCause(ex).log("Exception opening sanctum for %s", playerId);
                return false;
            });
    }

    /**
     * Closes a player's skill sanctum.
     *
     * <p>This properly exits the player from the Hytale instance (teleporting them
     * back to their original position) before closing the sanctum.
     *
     * @param playerId The player whose sanctum to close
     * @return true if the sanctum was closed
     */
    public boolean closeSanctum(@Nonnull UUID playerId) {
        return closeSanctum(playerId, true);
    }

    /**
     * Closes a player's skill sanctum with optional teleportation.
     *
     * <p>This properly exits the player from the Hytale instance before closing
     * the sanctum. If {@code teleportPlayer} is false (e.g., during disconnect),
     * the teleportation step is skipped since the player is already leaving.
     *
     * @param playerId The player whose sanctum to close
     * @param teleportPlayer If true, teleports the player back to their return point.
     *                       If false (e.g., during disconnect), skips teleportation.
     * @return true if the sanctum was closed
     */
    public boolean closeSanctum(@Nonnull UUID playerId, boolean teleportPlayer) {
        SkillSanctumInstance instance = activeInstances.remove(playerId);
        if (instance == null) {
            LOGGER.atFine().log("No active sanctum to close for %s", playerId);
            return false;
        }

        try {
            // Remove any active skill node detail HUD for this player
            skillNodeHudManager.removeHud(playerId);

            // Remove skill point HUD
            skillPointHudManager.removeHud(playerId);

            if (teleportPlayer) {
                PlayerRef playerRef = Universe.get().getPlayer(playerId);
                if (playerRef != null && playerRef.isValid()) {
                    // Restore player's original flight state before exiting
                    instance.restoreFlightForPlayer(playerRef);

                    // Exit player from the instance (teleports back to return point)
                    instance.exitPlayer(playerRef);
                }
            }

            instance.close();
            LOGGER.atInfo().log("Closed skill sanctum for %s (teleport=%b)", playerId, teleportPlayer);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error closing sanctum for %s", playerId);
            return false;
        }
    }

    /**
     * Checks if a player has an active sanctum (in-memory tracking).
     *
     * <p><b>Note:</b> This only checks the in-memory tracking map. It does NOT
     * detect if a player is physically in a sanctum world after a server restart
     * or relog. For world-based detection, use {@link #isPlayerInSanctumWorld(PlayerRef)}.
     *
     * @param playerId The player UUID to check
     * @return true if the player has an active sanctum in our tracking map
     */
    public boolean hasActiveSanctum(@Nonnull UUID playerId) {
        SkillSanctumInstance instance = activeInstances.get(playerId);
        return instance != null && instance.isActive();
    }

    /**
     * Checks if a player is currently physically in a Skill Sanctum world.
     *
     * <p>This queries the player's actual world, not the in-memory tracking map.
     * Use this for detection when a command is executed, as it correctly handles:
     * <ul>
     *   <li>Server restarts (in-memory map is cleared but player is still in sanctum)</li>
     *   <li>Player relogs (tracking may be lost but world position is preserved)</li>
     * </ul>
     *
     * @param playerRef The player to check
     * @return true if the player is in a Skill Sanctum world
     */
    public boolean isPlayerInSanctumWorld(@Nonnull PlayerRef playerRef) {
        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) {
            return false;
        }

        World world = Universe.get().getWorld(worldUuid);
        if (world == null) {
            return false;
        }

        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            return false;
        }

        java.util.Optional<RealmInstance> realmOpt = realmsManager.getRealmByWorld(world);
        if (realmOpt.isEmpty()) {
            return false;
        }

        return realmOpt.get().getBiome() == RealmBiomeType.SKILL_SANCTUM;
    }

    /**
     * Gets a player's active sanctum instance.
     *
     * @param playerId The player UUID
     * @return The sanctum instance, or null if none active
     */
    @Nullable
    public SkillSanctumInstance getSanctumInstance(@Nonnull UUID playerId) {
        return activeInstances.get(playerId);
    }

    /**
     * Gets the skill node HUD manager.
     *
     * <p>Used by the damage system to show node detail HUDs when players
     * click on skill nodes in the sanctum.
     *
     * @return The skill node HUD manager
     */
    @Nonnull
    public SkillNodeHudManager getSkillNodeHudManager() {
        return skillNodeHudManager;
    }

    /**
     * Gets the skill point HUD manager.
     *
     * <p>Used by {@link SkillSanctumInstance} to show the skill point allocation
     * HUD once the player arrives in the sanctum.
     *
     * @return The skill point HUD manager
     */
    @Nonnull
    public SkillPointHudManager getSkillPointHudManager() {
        return skillPointHudManager;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates the map data for a skill sanctum realm.
     *
     * <p>Sanctum maps have:
     * <ul>
     *   <li>SKILL_SANCTUM biome (void environment)</li>
     *   <li>LARGE size (enough room for the tree)</li>
     *   <li>CIRCULAR shape</li>
     *   <li>No modifiers</li>
     * </ul>
     */
    @Nonnull
    private RealmMapData createSanctumMapData() {
        return RealmMapData.builder()
            .level(1)
            .rarity(GearRarity.COMMON)
            .quality(50)
            .biome(RealmBiomeType.SKILL_SANCTUM)
            // SKILL_SANCTUM only has a MEDIUM template (realm_skill_sanctum)
            .size(RealmLayoutSize.MEDIUM)
            .shape(RealmLayoutShape.CIRCULAR)
            .prefixes(List.of())
            .suffixes(List.of())
            .corrupted(false)
            .identified(true)
            .build();
    }

    /**
     * Gets the main plugin instance.
     */
    @Nonnull
    TrailOfOrbis getPlugin() {
        return plugin;
    }

    /**
     * Gets the skill tree manager for node allocation.
     */
    @Nonnull
    SkillTreeManager getSkillTreeManager() {
        return skillTreeManager;
    }

    /**
     * Gets the sanctum configuration.
     */
    @Nonnull
    SkillSanctumConfig getConfig() {
        return config;
    }

    /**
     * Gets the node spawner.
     */
    @Nullable
    SkillSanctumNodeSpawner getNodeSpawner() {
        return nodeSpawner;
    }

    /**
     * Called when a sanctum instance is closed.
     * Removes it from the active instances map.
     *
     * @param playerId The player whose sanctum was closed
     */
    void onInstanceClosed(@Nonnull UUID playerId) {
        activeInstances.remove(playerId);
    }

    /**
     * Resolves the ultimate permanent world by following the instance return chain.
     *
     * <p>This walks up the chain of return points until it finds a world that is NOT
     * an instance (i.e., a permanent world like the Overworld). This ultimate return
     * world is used as a fallback if the immediate return world is destroyed while
     * the player is in the sanctum.
     *
     * <p>Example chain: Overworld → Forgotten Temple → Skill Sanctum
     * <ul>
     *   <li>Immediate return: Forgotten Temple (might be destroyed)</li>
     *   <li>Ultimate return: Overworld (permanent, always exists)</li>
     * </ul>
     *
     * @param originWorld The world the player is currently in
     * @return The UUID of the first non-instance world in the chain, or default world
     */
    @Nullable
    private UUID resolveUltimateReturnWorld(@Nonnull World originWorld) {
        World current = originWorld;
        Set<UUID> visited = new HashSet<>(); // Prevent infinite loops in case of misconfiguration

        while (current != null) {
            UUID currentUuid = current.getWorldConfig().getUuid();

            // Cycle detection
            if (!visited.add(currentUuid)) {
                LOGGER.atWarning().log("Cycle detected in instance chain at world %s", currentUuid);
                break;
            }

            // Check if this world is an instance
            InstanceWorldConfig instanceConfig = InstanceWorldConfig.get(current.getWorldConfig());

            // Not an instance? This is our ultimate return world
            if (instanceConfig == null) {
                LOGGER.atFine().log("Found ultimate return world: %s (non-instance)", currentUuid);
                return currentUuid;
            }

            // No return point? This is the end of the chain
            WorldReturnPoint returnPoint = instanceConfig.getReturnPoint();
            if (returnPoint == null) {
                LOGGER.atFine().log("Found ultimate return world: %s (instance with no return point)", currentUuid);
                return currentUuid;
            }

            // Follow the chain to the next world
            current = Universe.get().getWorld(returnPoint.getWorld());
            if (current == null) {
                LOGGER.atFine().log("Return world %s not found, using origin as fallback", returnPoint.getWorld());
                break;
            }
        }

        // Fallback to default world if chain walking failed
        World defaultWorld = Universe.get().getDefaultWorld();
        if (defaultWorld != null) {
            LOGGER.atFine().log("Using default world as ultimate return world");
            return defaultWorld.getWorldConfig().getUuid();
        }

        // Last resort: return origin world UUID
        LOGGER.atWarning().log("Could not resolve ultimate return world, using origin");
        return originWorld.getWorldConfig().getUuid();
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if the sanctum system is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the number of active sanctum instances.
     *
     * @return Active instance count
     */
    public int getActiveInstanceCount() {
        return activeInstances.size();
    }
}
