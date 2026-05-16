package io.github.larsonix.trailoforbis.maps;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager;
import io.github.larsonix.trailoforbis.maps.api.RealmsService;
import io.github.larsonix.trailoforbis.maps.config.MobPoolConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfigLoader;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;
import io.github.larsonix.trailoforbis.maps.listeners.RealmExitListener;
import io.github.larsonix.trailoforbis.maps.listeners.RealmMapUseListener;
import io.github.larsonix.trailoforbis.maps.listeners.RealmPlayerEnterListener;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.core.RealmState;
import io.github.larsonix.trailoforbis.maps.event.*;
import io.github.larsonix.trailoforbis.maps.instance.*;
import io.github.larsonix.trailoforbis.maps.reward.RealmRewardService;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestManager;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestMonitor;
import io.github.larsonix.trailoforbis.maps.spawning.RealmEntitySpawner;
import io.github.larsonix.trailoforbis.maps.spawning.RealmContainerClearer;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobSpawner;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;
import io.github.larsonix.trailoforbis.maps.completion.VictoryPortalManager;
import io.github.larsonix.trailoforbis.maps.completion.RealmVictorySequence;
import io.github.larsonix.trailoforbis.maps.spawn.SpawnGatewayManager;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeManager;
import io.github.larsonix.trailoforbis.util.EmoteCelebrationHelper;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Central coordinator for the Realms system.
 *
 * <p>This manager implements {@link RealmsService} and coordinates all realm
 * operations including:
 * <ul>
 *   <li>Realm creation and destruction</li>
 *   <li>Player tracking across realms</li>
 *   <li>State management and lifecycle</li>
 *   <li>Event firing</li>
 *   <li>Configuration management</li>
 * </ul>
 *
 * <h2>Initialization</h2>
 * <p>Must be initialized with {@link #initialize(EventRegistry)} before use.
 * Should be shutdown with {@link #shutdown()} during plugin disable.
 *
 * @see RealmsService
 * @see RealmInstance
 */
public class RealmsManager implements RealmsService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // SINGLETON
    // ═══════════════════════════════════════════════════════════════════

    private static RealmsManager instance;

    /**
     * Gets the global RealmsManager instance.
     *
     * @return The manager instance
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public static RealmsManager get() {
        if (instance == null) {
            throw new IllegalStateException("RealmsManager has not been initialized");
        }
        return instance;
    }

    /**
     * Checks if the manager has been initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPONENTS
    // ═══════════════════════════════════════════════════════════════════

    private final RealmsConfig config;
    private final RealmModifierConfig modifierConfig;
    private final MobPoolConfig mobPoolConfig;
    private final io.github.larsonix.trailoforbis.maps.config.RealmTemplateConfig templateConfig;
    private final RealmTemplateRegistry templateRegistry;
    private final RealmTeleportHandler teleportHandler;
    private final RealmRemovalHandler removalHandler;
    private final RealmInstanceFactory instanceFactory;
    private final RealmPortalManager portalManager;
    private final io.github.larsonix.trailoforbis.maps.portal.PortalVisualManager portalVisualManager;
    private final RealmMapGenerator mapGenerator;

    /** Spawning module — mob pools, structure placement, boundary enforcement. */
    @Nullable
    private RealmSpawningModule spawningModule;

    /** Persistence module — crash recovery and realm state tracking. */
    @Nullable
    private io.github.larsonix.trailoforbis.maps.persistence.RealmPersistenceModule persistenceModule;

    /** Reward service for distributing completion rewards. */
    @Nullable
    private RealmRewardService rewardService;

    /** HUD manager for combat and victory HUDs. */
    private final RealmHudManager hudManager;

    /** Spawn protection for realm entry invincibility until first movement. */
    private final RealmSpawnProtection spawnProtection;

    /** Victory portal manager for spawning exit portals. */
    private final VictoryPortalManager victoryPortalManager;

    /** Emote celebration helper for realm victory. */
    private final EmoteCelebrationHelper victoryEmoteHelper;

    /** Victory sequence orchestrator. */
    @Nullable
    private RealmVictorySequence victorySequence;

    /** Reward chest manager for per-player victory chests. */
    @Nullable
    private RewardChestManager rewardChestManager;

    /** Monitor for detecting when players close reward chests. */
    @Nullable
    private RewardChestMonitor rewardChestMonitor;

    /** Gateway module — spawn gateways + tier upgrades. */
    @Nullable
    private RealmGatewayModule gatewayModule;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** All active realm instances by ID. */
    private final Map<UUID, RealmInstance> realmsById = new ConcurrentHashMap<>();

    /** Mapping of player ID to their current realm. */
    private final Map<UUID, UUID> playerToRealm = new ConcurrentHashMap<>();

    /** Event bus for realm lifecycle events. */
    private final RealmEventBus eventBus = new RealmEventBus();

    /** Reference to event registry for possible future integration. */
    @Nullable
    private EventRegistry eventRegistry;

    /** Event-based listeners for realm system. */
    @Nullable
    private RealmMapUseListener mapUseListener;
    @Nullable
    private RealmExitListener exitListener;
    @Nullable
    private RealmPlayerEnterListener playerEnterListener;

    /** Periodic tick module for realm scheduling. */
    @Nullable
    private RealmTickModule tickModule;

    /** Whether the manager is initialized. */
    private volatile boolean initialized = false;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new RealmsManager.
     *
     * @param configLoader The config loader
     */
    public RealmsManager(@Nonnull RealmsConfigLoader configLoader) {
        Objects.requireNonNull(configLoader, "Config loader cannot be null");

        this.config = configLoader.getRealmsConfig();
        this.modifierConfig = configLoader.getModifierConfig();
        this.mobPoolConfig = configLoader.getMobPoolConfig();
        this.templateConfig = configLoader.getTemplateConfig();
        this.templateRegistry = new RealmTemplateRegistry();
        this.teleportHandler = new RealmTeleportHandler();
        this.removalHandler = new RealmRemovalHandler(teleportHandler);
        this.instanceFactory = new RealmInstanceFactory(templateRegistry, config, removalHandler);
        this.portalManager = new RealmPortalManager();
        this.portalVisualManager = new io.github.larsonix.trailoforbis.maps.portal.PortalVisualManager();
        this.mapGenerator = new RealmMapGenerator(config, modifierConfig);
        this.hudManager = new RealmHudManager();
        this.spawnProtection = new RealmSpawnProtection();
        this.victoryPortalManager = new VictoryPortalManager();
        this.victoryEmoteHelper = new EmoteCelebrationHelper(
            config.getVictoryEmoteId(), "realm-victory");

        // Wire up callbacks
        setupCallbacks();
    }

    /**
     * Creates a RealmsManager with default configuration.
     *
     * @param configPath The path to the config directory
     */
    public RealmsManager(@Nonnull java.nio.file.Path configPath) {
        this(new RealmsConfigLoader(configPath));
    }

    // ═══════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initializes the RealmsManager.
     *
     * @param eventRegistry The event registry for firing events
     */
    public void initialize(@Nonnull EventRegistry eventRegistry) {
        if (initialized) {
            LOGGER.atWarning().log("RealmsManager already initialized");
            return;
        }

        this.eventRegistry = Objects.requireNonNull(eventRegistry);

        // CRITICAL: Register event listeners FIRST, before anything else that might fail
        // This ensures realm map right-click works even if template loading fails
        try {
            LOGGER.atInfo().log("Registering realm event listeners...");
            registerEventListeners(eventRegistry);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to register realm event listeners!");
            // Continue - other systems may still work
        }

        // Load template registry
        try {
            LOGGER.atInfo().log("Loading template registry...");
            templateRegistry.loadFromConfig(config);
            templateRegistry.setTemplateConfig(templateConfig);
            LOGGER.atInfo().log("Template registry loaded with %d templates",
                templateRegistry.getValidTemplateCount());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to load template registry - realm creation will fail");
            // Continue - listeners still work, validation will catch missing templates
        }

        // Initialize spawning module
        try {
            LOGGER.atInfo().log("Initializing spawning module...");
            TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
            if (plugin != null) {
                spawningModule = new RealmSpawningModule();
                spawningModule.initialize(plugin, mobPoolConfig);
            } else {
                LOGGER.atWarning().log("TrailOfOrbis plugin not available - mob spawning disabled");
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to initialize spawning module");
            // Continue - realms work without spawning
        }

        // Set up removal condition checker
        try {
            RealmRemovalCondition.setRemovalChecker(this::shouldRemoveRealm);
            LOGGER.atInfo().log("Removal condition checker configured");
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to set up removal condition checker");
        }

        // Start tick module for periodic tasks
        try {
            LOGGER.atInfo().log("Starting tick module...");
            tickModule = new RealmTickModule(
                realmsById, removalHandler, portalManager, hudManager,
                spawningModule != null ? spawningModule.getMobMarkerProviders() : new ConcurrentHashMap<>(),
                this::closeRealm);
            if (spawningModule != null) {
                tickModule.setMobSpawner(spawningModule.getMobSpawner());
            }
            tickModule.setTimerDiagEnabled(getConfig().isDebugMode());
            tickModule.start();
            LOGGER.atInfo().log("Tick module started successfully");

            // Initialize victory sequence (requires tick module running)
            victorySequence = new RealmVictorySequence(hudManager, victoryPortalManager, rewardChestManager, victoryEmoteHelper);
            LOGGER.atInfo().log("Victory sequence initialized");
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to start tick module - realm timeouts disabled");
        }

        // Initialize gateway module (spawn portals + tier upgrades)
        try {
            gatewayModule = new RealmGatewayModule();
            gatewayModule.initialize(config);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to initialize gateway module");
            // Continue - spawn gateways are optional
        }

        // Initialize persistence module for crash recovery
        try {
            TrailOfOrbis pluginRef = TrailOfOrbis.getInstanceOrNull();
            if (pluginRef != null) {
                persistenceModule = new io.github.larsonix.trailoforbis.maps.persistence.RealmPersistenceModule(
                    pluginRef.getDataManager());
                int orphans = persistenceModule.loadOrphanedPlayers();
                if (orphans > 0) {
                    LOGGER.atInfo().log("Persistence module initialized — %d orphaned players awaiting recovery", orphans);
                } else {
                    LOGGER.atInfo().log("Persistence module initialized — no orphaned players");
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("FAILED to initialize persistence module — crash recovery disabled");
        }

        // Register spawn shield visual effect (must happen during init, never during world tick)
        spawnProtection.registerEffect();

        instance = this;
        initialized = true;

        // Clean up orphaned realm instance worlds from previous sessions.
        // These accumulate when the server is hard-killed while realms are active,
        // or from bugs where realm closure doesn't reach the world removal pipeline
        // (e.g., Skill Sanctum leak fixed in this version). Without cleanup, each
        // orphaned world consumes a thread + memory indefinitely, degrading performance.
        cleanupOrphanedRealmWorlds();

        LOGGER.atInfo().log("RealmsManager initialized with %d valid templates",
            templateRegistry.getValidTemplateCount());
    }

    /**
     * Removes orphaned realm instance worlds that persist from previous sessions.
     *
     * <p>Hytale persists all worlds to disk. If the server is hard-killed while
     * realms are active, those worlds are reloaded on next startup but have no
     * corresponding {@link RealmInstance} in our tracking. Each orphaned world
     * consumes a world thread, chunk generator, and ECS store — accumulating
     * over time and causing severe performance degradation.
     *
     * <p>At init time, {@code realmsById} is empty (no realms created yet), so
     * any world whose name starts with {@code "instance-realm_"} is guaranteed
     * to be an orphan. Players who were in those realms when the server crashed
     * are handled separately by {@code RealmPersistenceModule} (teleports them
     * to overworld on reconnect).
     */
    private void cleanupOrphanedRealmWorlds() {
        try {
            var allWorlds = Universe.get().getWorlds();
            int removed = 0;

            for (World world : allWorlds.values()) {
                String name = world.getName();
                if (name != null && name.startsWith("instance-realm_") && world.isAlive()) {
                    try {
                        InstancesPlugin.safeRemoveInstance(world);
                        removed++;
                        LOGGER.atInfo().log("Removed orphaned realm world: %s", name);
                    } catch (Exception e) {
                        LOGGER.atWarning().log("Failed to remove orphaned realm world %s: %s",
                            name, e.getMessage());
                    }
                }
            }

            if (removed > 0) {
                LOGGER.atWarning().log("Cleaned up %d orphaned realm world(s) from previous session", removed);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan for orphaned realm worlds");
        }
    }

    /**
     * Registers event-based listeners for the realm system.
     *
     * @param eventRegistry The event registry
     */
    private void registerEventListeners(@Nonnull EventRegistry eventRegistry) {
        // RealmMapUseListener - handles player right-clicking realm maps (shows hint message)
        mapUseListener = new RealmMapUseListener(this);
        mapUseListener.register(eventRegistry);
        LOGGER.atInfo().log("Registered RealmMapUseListener");

        // Note: Portal_Device interactions are handled by RealmPortalDevicePageSupplier,
        // which is registered in TrailOfOrbis.registerRealmMapInteractions() to replace
        // the vanilla "PortalDevice" page.

        // RealmExitListener - handles player disconnects from realms
        exitListener = new RealmExitListener(this);
        exitListener.register(eventRegistry);
        LOGGER.atInfo().log("Registered RealmExitListener");

        // RealmPlayerEnterListener - handles players entering realm worlds via portal
        // This is critical because portal teleportation bypasses RealmTeleportHandler
        playerEnterListener = new RealmPlayerEnterListener(this);
        playerEnterListener.register(eventRegistry);
        LOGGER.atInfo().log("Registered RealmPlayerEnterListener");
    }

    /**
     * Shuts down the RealmsManager.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }

        LOGGER.atInfo().log("Shutting down RealmsManager with %d active realms", realmsById.size());

        // Stop tick module
        if (tickModule != null) {
            tickModule.shutdown();
            tickModule = null;
        }

        // Shutdown victory sequence
        if (victorySequence != null) {
            victorySequence.shutdown();
            victorySequence = null;
        }

        // Remove all HUDs
        hudManager.removeAllHuds();

        // Clear spawn protections
        spawnProtection.shutdown();

        // Shutdown reward chest system
        if (rewardChestManager != null) {
            rewardChestManager.shutdown();
            rewardChestManager = null;
            rewardChestMonitor = null;
        }

        // Clear victory portals
        victoryPortalManager.clearAll();

        // Clear spawn gateway + upgrade managers
        if (gatewayModule != null) {
            gatewayModule.shutdown();
            gatewayModule = null;
        }

        // Close all realms (teleports players out)
        removalHandler.closeAll().join();

        // Clear persistence data AFTER players are safely out
        // (if crash happens during closeAll, DB rows remain for recovery)
        if (persistenceModule != null) {
            persistenceModule.persistAndClearAll();
        }

        // Shutdown spawning module
        if (spawningModule != null) {
            spawningModule.shutdown();
            spawningModule = null;
        }

        // Clear state
        realmsById.clear();
        playerToRealm.clear();
        templateRegistry.clear();
        portalManager.clearTracking();
        portalVisualManager.shutdown();

        initialized = false;
        instance = null;

        LOGGER.atInfo().log("RealmsManager shutdown complete");
    }

    /**
     * Deactivates portal visuals (block swap, decoratives, particles) for all portals of a realm.
     * Immediately stops particle ticking, defers block cleanup to world thread.
     */
    private void deactivatePortalVisuals(@Nonnull RealmInstance realm) {
        portalVisualManager.deactivateForPortals(
                portalManager.getPortalsForRealm(realm.getRealmId()));
    }

    /**
     * Sets up callbacks between components.
     */
    private void setupCallbacks() {
        // Factory callbacks
        instanceFactory.setOnRealmCreated(this::onRealmCreated);
        instanceFactory.setOnRealmReady(this::onRealmReady);

        // Teleport callbacks
        teleportHandler.setOnPlayerEnter(this::onPlayerEnterRealm);
        teleportHandler.setOnPlayerExit(this::onPlayerExitRealm);

        // Removal callbacks
        removalHandler.setOnRealmClosed(this::onRealmClosed);
    }

    // registerMobPoolsFromConfig extracted to RealmSpawningModule

    // ═══════════════════════════════════════════════════════════════════
    // REALM SERVICE IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public CompletableFuture<RealmInstance> openRealm(
            @Nonnull RealmMapData mapData,
            @Nonnull UUID ownerId,
            @Nonnull UUID originWorldId) {

        World originWorld = Universe.get().getWorld(originWorldId);
        if (originWorld == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Origin world not found: " + originWorldId));
        }

        // Default return point at world spawn
        Transform returnPoint = new Transform(
            new Vector3d(0, 64, 0),
            new Vector3f(0, 0, 0)
        );

        return instanceFactory.createRealm(mapData, ownerId, originWorld, returnPoint);
    }

    @Override
    @Nonnull
    public CompletableFuture<RealmInstance> openRealm(
            @Nonnull RealmMapData mapData,
            @Nonnull UUID ownerId,
            @Nonnull UUID originWorldId,
            double returnX, double returnY, double returnZ) {

        World originWorld = Universe.get().getWorld(originWorldId);
        if (originWorld == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Origin world not found: " + originWorldId));
        }

        Transform returnPoint = RealmInstanceFactory.createReturnPoint(returnX, returnY, returnZ);

        return instanceFactory.createRealm(mapData, ownerId, originWorld, returnPoint);
    }

    @Override
    @Nonnull
    public Optional<RealmInstance> getRealm(@Nonnull UUID realmId) {
        return Optional.ofNullable(realmsById.get(realmId));
    }

    @Override
    @Nonnull
    public Optional<RealmInstance> getPlayerRealm(@Nonnull UUID playerId) {
        UUID realmId = playerToRealm.get(playerId);
        return realmId != null ? getRealm(realmId) : Optional.empty();
    }

    @Override
    @Nonnull
    public Collection<RealmInstance> getActiveRealms() {
        return Collections.unmodifiableCollection(realmsById.values());
    }

    @Override
    @Nonnull
    public Collection<RealmInstance> getRealmsOwnedBy(@Nonnull UUID ownerId) {
        return realmsById.values().stream()
            .filter(r -> r.getOwnerId().equals(ownerId))
            .toList();
    }

    @Override
    public int getActiveRealmCount() {
        return realmsById.size();
    }

    @Override
    @Nonnull
    public CompletableFuture<Boolean> enterRealm(@Nonnull UUID playerId, @Nonnull UUID realmId) {
        RealmInstance realm = realmsById.get(realmId);
        if (realm == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (!canPlayerEnter(playerId, realmId)) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Get return point from player's current world
        UUID worldUuid = playerRef.getWorldUuid();
        World currentWorld = worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
        if (currentWorld == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Capture the player's actual pre-entry location for instance return.
        Transform returnPoint = playerRef.getTransform();
        if (returnPoint == null) {
            ISpawnProvider spawnProvider = currentWorld.getWorldConfig().getSpawnProvider();
            if (spawnProvider != null) {
                returnPoint = spawnProvider.getSpawnPoint(currentWorld, playerId);
            }
        }
        if (returnPoint == null) {
            LOGGER.atWarning().log(
                "Missing transform for player %s during realm entry, using emergency fallback return point",
                playerId.toString().substring(0, 8));
            returnPoint = new Transform(
                new Vector3d(0, 64, 0),
                new Vector3f(0, 0, 0)
            );
        } else {
            returnPoint = returnPoint.clone();
        }

        return teleportHandler.teleportIntoRealm(playerRef, realm, returnPoint);
    }

    @Override
    @Nonnull
    public CompletableFuture<Boolean> exitRealm(@Nonnull UUID playerId) {
        UUID realmId = playerToRealm.get(playerId);
        if (realmId == null) {
            return CompletableFuture.completedFuture(false);
        }

        RealmInstance realm = realmsById.get(realmId);
        if (realm == null) {
            playerToRealm.remove(playerId);
            return CompletableFuture.completedFuture(false);
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            return CompletableFuture.completedFuture(false);
        }

        return teleportHandler.teleportOutOfRealm(playerRef, realm, RealmPlayerExitedEvent.ExitReason.PORTAL);
    }

    @Override
    public boolean isPlayerInRealm(@Nonnull UUID playerId) {
        return playerToRealm.containsKey(playerId);
    }

    @Override
    public boolean canPlayerEnter(@Nonnull UUID playerId, @Nonnull UUID realmId) {
        if (isPlayerInRealm(playerId)) {
            return false; // Already in a realm
        }

        RealmInstance realm = realmsById.get(realmId);
        if (realm == null) {
            return false;
        }

        return realm.allowsEntry();
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> closeRealm(
            @Nonnull UUID realmId,
            @Nonnull RealmInstance.CompletionReason reason) {

        RealmInstance realm = realmsById.get(realmId);
        if (realm == null) {
            return CompletableFuture.completedFuture(null);
        }

        // DIAGNOSTIC: Log who is closing the realm and the call stack
        LOGGER.atInfo().log("[TIMER-DIAG] closeRealm called: realm=%s, reason=%s, elapsed=%ds, timeout=%ds, thread=%s",
            realmId.toString().substring(0, 8), reason,
            realm.getElapsedTime().toSeconds(), realm.getTimeout().toSeconds(),
            Thread.currentThread().getName());

        // Mark the realm with its completion reason for reward calculation
        realm.setCompletionReason(reason);

        // TIMEOUT = failed realm — no rewards. Only COMPLETED gets rewards (via triggerCompletion()).
        // ABANDONED and FORCE_CLOSED also don't get rewards.

        // Revert portal visuals (swap back to base Portal_Device, remove decoratives, stop particles)
        deactivatePortalVisuals(realm);

        // Immediately deactivate entry portals so new maps can be used
        portalManager.deactivateEntryPortalsNow(realm);

        // Despawn all mobs so they don't attack during the grace period
        if (spawningModule != null) {
            spawningModule.despawnAllMobs(realm, "close (reason: " + reason + ")");
        }

        // Remove all containers in the arena (prevents looting after realm ends)
        clearRealmContainers(realm);

        // Victory uses full grace period (60s) so players can use the portal.
        // Timeout uses shorter grace (15s = 10s defeat banner + 5s buffer) — no reason
        // to keep the world alive for 60s when the player is being teleported out.
        Duration gracePeriod;
        if (reason == RealmInstance.CompletionReason.TIMEOUT) {
            gracePeriod = Duration.ofSeconds(15);
        } else if (realm.isReady() || realm.getAllParticipants().isEmpty()) {
            // Never-entered realm — no players to wait for, close immediately
            gracePeriod = Duration.ZERO;
        } else {
            gracePeriod = Duration.ofSeconds(config.getCompletionGracePeriodSeconds());
        }
        removalHandler.scheduleRemoval(realm, reason, gracePeriod);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void forceCloseRealm(@Nonnull UUID realmId) {
        RealmInstance realm = realmsById.get(realmId);
        if (realm != null) {
            removalHandler.closeImmediately(realm, RealmInstance.CompletionReason.FORCE_CLOSED);
        }
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled() && initialized;
    }

    @Override
    public boolean isBiomeAvailable(@Nonnull RealmBiomeType biome) {
        // Check if at least the smallest tier template exists for this biome.
        // Tier system: realm_forest_r15, realm_forest_r20, etc.
        return config.isBiomeEnabled(biome) &&
               templateRegistry.doesTemplateExist(biome.getTemplateNameForRadius(25));
    }

    @Override
    public boolean isSizeAvailable(@Nonnull RealmLayoutSize size) {
        return config.isSizeEnabled(size);
    }

    @Override
    public boolean canCreateMoreRealms() {
        return realmsById.size() < config.getMaxConcurrentInstances();
    }

    @Override
    @Nonnull
    public ValidationResult validateMapData(@Nonnull RealmMapData mapData) {
        if (!isEnabled()) {
            return ValidationResult.disabled();
        }

        if (!config.isBiomeEnabled(mapData.biome())) {
            return ValidationResult.biomeUnavailable();
        }

        // Defense in depth: enforce biome level gates (prevents stone-rerolled maps
        // from bypassing min-level requirements even if changeBiome filtering fails)
        int biomeMinLevel = config.getBiomeSettings(mapData.biome()).getMinLevel();
        if (mapData.level() < biomeMinLevel) {
            return ValidationResult.biomeUnavailable();
        }

        if (!config.isSizeEnabled(mapData.size())) {
            return ValidationResult.sizeUnavailable();
        }

        if (!canCreateMoreRealms()) {
            return ValidationResult.limitReached();
        }

        if (!instanceFactory.hasValidTemplate(mapData)) {
            return ValidationResult.templateMissing();
        }

        return ValidationResult.OK;
    }

    // ═══════════════════════════════════════════════════════════════════
    // REALM LOOKUP METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if a player is currently in a combat realm.
     *
     * <p>Combat realms are non-utility biomes (Forest, Desert, Volcano, etc.)
     * where players fight monsters. Utility biomes like SKILL_SANCTUM are excluded.
     *
     * @param playerRef The player to check
     * @return true if the player is in a combat realm
     */
    public boolean isPlayerInCombatRealm(@Nonnull PlayerRef playerRef) {
        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) {
            return false;
        }

        World world = Universe.get().getWorld(worldUuid);
        if (world == null) {
            return false;
        }

        Optional<RealmInstance> realmOpt = getRealmByWorld(world);
        if (realmOpt.isEmpty()) {
            return false;
        }

        // Combat realm = any realm that is NOT a utility biome
        return !realmOpt.get().getBiome().isUtilityBiome();
    }

    /**
     * Gets a realm instance by its backing world.
     *
     * <p>This is used to detect when a player enters a realm world via portal,
     * as the portal system teleports players directly without going through
     * our RealmTeleportHandler.
     *
     * @param world The world to look up
     * @return Optional containing the realm if found
     */
    @Nonnull
    public Optional<RealmInstance> getRealmByWorld(@Nonnull World world) {
        Objects.requireNonNull(world, "World cannot be null");

        for (RealmInstance realm : realmsById.values()) {
            World realmWorld = realm.getWorld();
            if (realmWorld != null && realmWorld.equals(world)) {
                return Optional.of(realm);
            }
        }

        return Optional.empty();
    }

    /**
     * Gets the level fraction for AI-spawned minions in realms.
     *
     * @return The minion level fraction (default 0.75)
     */
    public float getMinionLevelFraction() {
        return mobPoolConfig.getMinionLevelFraction();
    }

    /**
     * Notifies the manager that a player has entered a realm via portal.
     *
     * <p>This method should be called by {@link RealmPlayerEnterListener} when
     * a player is added to a realm world. It handles:
     * <ul>
     *   <li>Recording the player in the realm's tracking</li>
     *   <li>Adding player to the manager's tracking</li>
     *   <li>Triggering mob spawning if this is the first player</li>
     * </ul>
     *
     * @param playerId The player's UUID
     * @param realm The realm they entered
     */
    public void notifyPlayerEnteredRealm(@Nonnull UUID playerId, @Nonnull RealmInstance realm) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(realm, "Realm cannot be null");

        // Check if already tracked (avoid duplicate processing)
        if (playerToRealm.containsKey(playerId)) {
            LOGGER.atFine().log("Player %s already tracked in a realm, skipping",
                playerId.toString().substring(0, 8));
            return;
        }

        // Record the player in the realm instance
        realm.onPlayerEnter(playerId);

        // Update party HUD location (skip SKILL_SANCTUM — it sets its own text)
        if (realm.getBiome() != RealmBiomeType.SKILL_SANCTUM) {
            ServiceRegistry.get(PartyIntegrationManager.class).ifPresent(party ->
                party.updateHudRealm(playerId, realm.getBiome().getDisplayName(), realm.getLevel()));
        }

        // Trigger the internal callback which handles tracking and spawning
        onPlayerEnterRealm(playerId, realm);
    }

    /**
     * Handles a player who exited a realm via Hytale's portal system.
     *
     * <p>When players use Hytale's built-in victory portal, the teleportation
     * is handled directly by Hytale, bypassing our {@link RealmTeleportHandler}.
     * This method is called by {@link RealmPlayerEnterListener} when it detects
     * a player entering a non-realm world while still tracked as being in a realm.
     *
     * <p>This triggers the same cleanup as a normal realm exit:
     * <ul>
     *   <li>Removes HUDs for the player</li>
     *   <li>Updates player tracking</li>
     *   <li>Notifies the realm instance</li>
     *   <li>Handles empty realm logic if applicable</li>
     * </ul>
     *
     * @param playerId The player's UUID
     */
    public void handlePlayerExitedViaPortal(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        UUID realmId = playerToRealm.get(playerId);
        if (realmId == null) {
            LOGGER.atFine().log("Player %s not tracked in any realm, skipping portal exit cleanup",
                playerId.toString().substring(0, 8));
            return;
        }

        RealmInstance realm = realmsById.get(realmId);
        if (realm == null) {
            // Realm was already removed, just clean up tracking
            playerToRealm.remove(playerId);
            // Discard HUDs — DrainPlayerFromWorldEvent does NOT fire for Teleport-based
            // exits (e.g. ExitInstance respawn controller on death), so the HUD may still
            // be in the map. If DrainPlayerFromWorldEvent already handled it, this is a no-op.
            hudManager.discardAllHudsForPlayer(playerId);
            LOGGER.atFine().log("Cleaned up tracking for player %s (realm already closed)",
                playerId.toString().substring(0, 8));
            return;
        }

        // Notify the realm instance that the player left
        realm.onPlayerLeave(playerId);

        // Discard HUDs — DrainPlayerFromWorldEvent does NOT fire for Teleport-based
        // exits (e.g. ExitInstance respawn controller on death), so the HUD may still
        // be in the combatHuds/victoryHuds maps, causing tickCombatHuds() to keep
        // re-rendering the stale HUD on the client. If DrainPlayerFromWorldEvent
        // already handled it, discardAllHudsForPlayer is a safe no-op.
        hudManager.discardAllHudsForPlayer(playerId);

        // Clean up spawn protection (safe no-op if already revoked by movement)
        spawnProtection.cleanup(playerId);

        // Update party HUD location back to overworld
        ServiceRegistry.get(PartyIntegrationManager.class).ifPresent(party ->
            party.updateHudOverworld(playerId));

        // Trigger the standard exit cleanup (tracking, empty realm handling)
        onPlayerExitRealm(playerId, realm);

        LOGGER.atFine().log("Player %s exited realm %s via portal - cleanup complete",
            playerId.toString().substring(0, 8),
            realmId.toString().substring(0, 8));
    }

    /**
     * Handles cleanup when a player disconnects while in a realm.
     *
     * <p>Unlike {@link #exitRealm(UUID)} which teleports the player out,
     * this method only performs cleanup (HUD removal, tracking updates, empty realm handling)
     * without attempting teleportation. This is necessary because when a player disconnects,
     * Hytale is already removing them from the world, so their entity reference becomes
     * invalid and teleportation would fail.
     *
     * <p>This method is called by {@link RealmExitListener} when handling
     * {@code PlayerDisconnectEvent}.
     *
     * @param playerId The disconnecting player's UUID
     */
    public void handlePlayerDisconnect(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        UUID realmId = playerToRealm.get(playerId);
        if (realmId == null) {
            LOGGER.atFine().log("Player %s not tracked in any realm, skipping disconnect cleanup",
                playerId.toString().substring(0, 8));
            return;
        }

        RealmInstance realm = realmsById.get(realmId);
        if (realm == null) {
            // Realm was already removed, just clean up tracking
            playerToRealm.remove(playerId);
            hudManager.discardAllHudsForPlayer(playerId);
            LOGGER.atFine().log("Cleaned up tracking for disconnected player %s (realm already closed)",
                playerId.toString().substring(0, 8));
            return;
        }

        // Notify the realm instance that the player left
        realm.onPlayerLeave(playerId);

        // Discard HUDs — same reasoning as handlePlayerExitedViaPortal
        hudManager.discardAllHudsForPlayer(playerId);

        // Clean up spawn protection (if still active during disconnect)
        spawnProtection.cleanup(playerId);

        // Trigger the standard exit cleanup (tracking, empty realm handling)
        onPlayerExitRealm(playerId, realm);

        LOGGER.atFine().log("Player %s disconnected from realm %s - cleanup complete",
            playerId.toString().substring(0, 8),
            realmId.toString().substring(0, 8));
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL CALLBACKS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when a realm is created.
     */
    private void onRealmCreated(RealmInstance realm) {
        realmsById.put(realm.getRealmId(), realm);

        // Persist realm state for crash recovery
        if (persistenceModule != null) {
            persistenceModule.onRealmCreated(realm);
        }

        // Fire event to listeners
        fireEvent(new RealmCreatedEvent(realm, realm.getOwnerId()));

        int total = realmsById.size();
        LOGGER.atInfo().log("Realm %s created (total: %d)", realm.getRealmId(), total);

        // Leak detection: warn if realm count grows beyond expected bounds.
        // A single player should never have more than ~3 concurrent realms
        // (1 active + 1 sanctum + 1 in grace period). Higher counts indicate a leak.
        if (total > 5) {
            LOGGER.atWarning().log("[LEAK-DETECT] Realm count %d exceeds threshold! Active realms:", total);
            for (var entry : realmsById.entrySet()) {
                RealmInstance r = entry.getValue();
                LOGGER.atWarning().log("[LEAK-DETECT]   %s — biome=%s, state=%s, players=%d, age=%ds",
                    entry.getKey().toString().substring(0, 8),
                    r.getBiome(), r.getState(),
                    r.getPlayerCount(), r.getElapsedTime().toSeconds());
            }
        }
    }

    /**
     * Called when a realm becomes ready.
     */
    private void onRealmReady(RealmInstance realm) {
        if (realm.getWorld() != null) {
            fireEvent(new RealmReadyEvent(realm, realm.getWorld()));

            // Set up map markers (compass, mobs) for combat realms
            if (spawningModule != null) {
                spawningModule.setupMobMarkers(realm);
            }
        }
    }

    /**
     * Called when a player enters a realm.
     */
    private void onPlayerEnterRealm(UUID playerId, RealmInstance realm) {
        playerToRealm.put(playerId, realm.getRealmId());

        // Persist player-in-realm for crash recovery.
        // On crash, Hytale's internal return coordinates are lost.
        // We fall back to default world spawn (0, 64, 0) — safe degradation.
        if (persistenceModule != null) {
            World defaultWorld = Universe.get().getDefaultWorld();
            if (defaultWorld != null) {
                persistenceModule.onPlayerEnteredRealm(playerId, realm.getRealmId(),
                    defaultWorld.getWorldConfig().getUuid(),
                    new Vector3d(0, 64, 0));
            }
        }

        // Trigger guide milestone for first realm entry (skip sanctum/utility biomes)
        if (!realm.getBiome().isUtilityBiome()) {
            TrailOfOrbis rpgGuide = TrailOfOrbis.getInstanceOrNull();
            if (rpgGuide != null && rpgGuide.getGuideManager() != null) {
                rpgGuide.getGuideManager().tryShow(playerId, io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_REALM);
            }
        }

        boolean isFirst = realm.getPlayerCount() == 1;

        // Transition to ACTIVE on first player
        if (isFirst && realm.isReady()) {
            realm.transitionTo(RealmState.ACTIVE);

            // Initialize mob spawning (structures + mobs, dispatched to world thread)
            if (spawningModule != null) {
                spawningModule.initializeSpawningForRealm(realm);
            }

            fireEvent(new RealmActivatedEvent(
                realm, realm.getCompletionTracker().getTotalMonsters()));
        }

        // NOTE: Combat HUD is NOT shown here. Creating HUDs during AddPlayerToWorldEvent
        // sends packets while the client is still processing JoinWorldPacket (2-3 second window).
        // The HyUI refresh scheduler fires every 100ms, sending Set commands via safeAdd() →
        // setCustomHud() → show() which reference elements that get cleared during JoinWorld
        // processing → client crash (#HYUUIDGroup0.Anchor not found).
        // Combat HUD is instead shown in RealmPlayerEnterListener.onPlayerReady(), which fires
        // AFTER ClientReady — when the client's UI state is stable.

        // Fire enter event
        fireEvent(new RealmPlayerEnteredEvent(realm, playerId, isFirst));
    }

    /**
     * Called when a player exits a realm.
     *
     * <p>Note: HUD removal is handled by DrainPlayerFromWorldEvent handler,
     * which fires before the player leaves the world. This method handles
     * unclaimed reward delivery, tracking, and empty realm logic.
     */
    private void onPlayerExitRealm(UUID playerId, RealmInstance realm) {
        // Deliver unclaimed rewards if realm was completed
        if (realm.isObjectiveComplete() && rewardChestManager != null) {
            deliverUnclaimedRewards(playerId, realm.getRealmId());
        }

        playerToRealm.remove(playerId);

        // Remove from crash recovery tracking
        if (persistenceModule != null) {
            persistenceModule.onPlayerExitedRealm(playerId);
        }

        boolean isLast = realm.isEmpty();

        // Fire exit event
        fireEvent(new RealmPlayerExitedEvent(
            realm, playerId, RealmPlayerExitedEvent.ExitReason.PORTAL, isLast));

        // Handle empty realm - close if ACTIVE or ENDING (victory phase)
        if (isLast && (realm.isActive() || realm.isEnding())) {
            handleEmptyRealm(realm);
        }
    }

    /**
     * Delivers unclaimed reward chest items directly to a player's inventory.
     *
     * <p>Called when a player exits a completed realm without looting the
     * reward chest (e.g., via {@code /too realm exit} or victory portal).
     */
    private void deliverUnclaimedRewards(UUID playerId, UUID realmId) {
        List<com.hypixel.hytale.server.core.inventory.ItemStack> unclaimed =
            rewardChestManager.getUnclaimedRewards(realmId, playerId);
        if (unclaimed.isEmpty()) {
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            LOGGER.atWarning().log("Cannot deliver %d unclaimed rewards — player %s offline",
                unclaimed.size(), playerId.toString().substring(0, 8));
            return;
        }

        // Get player inventory
        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entityRef =
            playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        com.hypixel.hytale.server.core.entity.entities.Player player =
            entityRef.getStore().getComponent(entityRef,
                com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return;
        }

        com.hypixel.hytale.server.core.inventory.Inventory inventory = player.getInventory();
        int delivered = 0;

        for (com.hypixel.hytale.server.core.inventory.ItemStack item : unclaimed) {
            if (item == null || item.isEmpty()) continue;

            com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction tx =
                inventory.getHotbar().addItemStack(item);
            if (!tx.succeeded()) {
                tx = inventory.getBackpack().addItemStack(item);
            }
            if (tx.succeeded()) {
                delivered++;
            }
        }

        // Remove from storage — delivered or not, we've tried
        rewardChestManager.removePlayerRewards(realmId, playerId);

        // Notify the player
        if (delivered > 0) {
            Message msg = Message.empty()
                .insert(Message.raw("[").color(MessageColors.GRAY))
                .insert(Message.raw("Rewards").color(MessageColors.GOLD).bold(true))
                .insert(Message.raw("] ").color(MessageColors.GRAY))
                .insert(Message.raw("Unclaimed chest rewards delivered to your inventory.")
                    .color(MessageColors.SUCCESS));
            playerRef.sendMessage(msg);
        }

        LOGGER.atInfo().log("Delivered %d/%d unclaimed rewards to player %s from realm %s",
            delivered, unclaimed.size(), playerId.toString().substring(0, 8),
            realmId.toString().substring(0, 8));
    }

    /**
     * Called when a realm is closed.
     *
     * <p>Note: HUD removal is handled by DrainPlayerFromWorldEvent handler
     * when players leave the world. By this point, all players should have
     * already had their HUDs removed.
     */
    private void onRealmClosed(RealmClosedEvent event) {
        RealmInstance realm = event.getRealm();
        LOGGER.atFine().log("onRealmClosed called for realm %s (reason: %s)",
            realm.getRealmId().toString().substring(0, 8), event.getReason());

        realmsById.remove(realm.getRealmId());
        if (spawningModule != null) {
            spawningModule.removeMobMarkers(realm.getRealmId());
            // Clean up mob spawn state, entity refs, bounds registry — returns mob UUIDs for ailment cleanup
            java.util.Set<java.util.UUID> mobUuids = spawningModule.cleanupRealm(realm.getRealmId());
            // Clean up ailment state for realm mobs
            TrailOfOrbis too = TrailOfOrbis.getInstanceOrNull();
            if (!mobUuids.isEmpty() && too != null && too.getAilmentTracker() != null) {
                int cleaned = too.getAilmentTracker().cleanupEntities(mobUuids);
                if (cleaned > 0) {
                    LOGGER.atFine().log("Cleaned up ailment state for %d mobs from realm %s",
                        cleaned, realm.getRealmId().toString().substring(0, 8));
                }
            }
        }

        // Remove from persistence tracking
        if (persistenceModule != null) {
            persistenceModule.onRealmClosed(realm.getRealmId());
        }

        // Clean up player mappings
        for (UUID playerId : realm.getAllParticipants()) {
            playerToRealm.remove(playerId, realm.getRealmId());
        }

        // Clean up victory portal if any
        victoryPortalManager.removeVictoryPortal(realm);

        // Clean up reward chest (unclaimed rewards delivered at exit time)
        if (rewardChestManager != null) {
            rewardChestManager.cleanup(realm.getRealmId());
        }

        // Cancel any active victory sequence
        if (victorySequence != null) {
            victorySequence.cancel(realm.getRealmId());
        }

        // Clean up portals associated with this realm
        // Use deactivation instead of removal to preserve Portal_Device blocks for reuse
        portalManager.deactivateAllPortalsForRealm(realm)
            .exceptionally(ex -> {
                LOGGER.atWarning().withCause(ex).log("Error deactivating portals for realm %s",
                    realm.getRealmId());
                return 0;
            });

        // Fire event
        fireEvent(event);

        LOGGER.atInfo().log("Realm %s closed (reason: %s, remaining: %d)",
            realm.getRealmId(), event.getReason(), realmsById.size());
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETION HANDLING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Triggers realm completion when all objectives are met (all mobs killed).
     *
     * <p>This method should be called by {@link io.github.larsonix.trailoforbis.maps.listeners.RealmMobDeathListener}
     * when the last mob is killed. It handles:
     * <ul>
     *   <li>State validation (must be ACTIVE)</li>
     *   <li>Marking the realm as completed</li>
     *   <li>Firing the {@link RealmCompletedEvent}</li>
     *   <li>Sending victory messages to players</li>
     *   <li>Distributing rewards</li>
     *   <li>Scheduling cleanup</li>
     * </ul>
     *
     * @param realm The realm that was completed
     */
    public void triggerCompletion(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "Realm cannot be null");

        // Guard: Only active realms can be completed
        if (!realm.isActive()) {
            LOGGER.atWarning().log("Cannot trigger completion for realm %s - not in ACTIVE state (current: %s)",
                realm.getRealmId().toString().substring(0, 8),
                realm.getState());
            return;
        }

        // Guard: Check not already completed
        if (realm.isObjectiveComplete() && realm.getCompletionReason() != null) {
            LOGGER.atFine().log("Realm %s already completed, skipping trigger",
                realm.getRealmId().toString().substring(0, 8));
            return;
        }

        LOGGER.atInfo().log("Triggering completion for realm %s", realm.getRealmId().toString().substring(0, 8));

        // Mark the realm as completed (transitions to ENDING state)
        realm.markCompleted();

        // Immediately deactivate entry portals — players can't re-enter after victory,
        // and the gateway is freed for a new map right away
        portalManager.deactivateEntryPortalsNow(realm);

        // Despawn any remaining mobs (must run on world thread)
        if (spawningModule != null) {
            spawningModule.despawnAllMobs(realm, "victory");
        }

        // Remove all containers in the arena (prevents post-combat looting)
        clearRealmContainers(realm);

        // Fire the completion event for listeners
        fireEvent(new RealmCompletedEvent(realm));

        // Distribute rewards immediately
        if (rewardService != null) {
            rewardService.distributeRewards(realm)
                .thenRun(() -> LOGGER.atFine().log("Rewards distributed for completed realm %s",
                    realm.getRealmId().toString().substring(0, 8)))
                .exceptionally(ex -> {
                    LOGGER.atWarning().withCause(ex).log("Failed to distribute rewards for realm %s",
                        realm.getRealmId().toString().substring(0, 8));
                    return null;
                });
        }

        // Execute victory sequence (handles HUDs, portal, countdown, evacuation)
        if (victorySequence != null) {
            victorySequence.execute(realm);
        } else {
            // Fallback if victory sequence not initialized: send message and schedule removal
            sendVictoryMessage(realm);
            Duration gracePeriod = Duration.ofSeconds(config.getCompletionGracePeriodSeconds());
            removalHandler.scheduleRemoval(realm, RealmInstance.CompletionReason.COMPLETED, gracePeriod);
        }

        LOGGER.atInfo().log("Realm %s completion triggered",
            realm.getRealmId().toString().substring(0, 8));

        // Schedule guide milestone check: if player is still in realm 30s after completion,
        // show the "stuck" popup with /too realm exit command
        TrailOfOrbis rpgStuck = TrailOfOrbis.getInstanceOrNull();
        if (rpgStuck != null && rpgStuck.getGuideManager() != null) {
            UUID realmId = realm.getRealmId();
            java.util.concurrent.CompletableFuture.delayedExecutor(30, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> {
                    RealmInstance stillActive = realmsById.get(realmId);
                    if (stillActive != null) {
                        for (UUID pid : stillActive.getCurrentPlayers()) {
                            rpgStuck.getGuideManager().tryShow(pid, io.github.larsonix.trailoforbis.guide.GuideMilestone.STUCK_IN_REALM);
                        }
                    }
                });
        }
    }

    /**
     * Sends a victory message to all players in the realm.
     *
     * @param realm The completed realm
     */
    private void sendVictoryMessage(@Nonnull RealmInstance realm) {
        var tracker = realm.getCompletionTracker();
        long elapsedSeconds = tracker.getElapsedSeconds();
        int totalKills = tracker.getKilledByPlayers();
        int participantCount = tracker.getParticipantCount();

        // Format time as mm:ss
        String timeStr = String.format("%d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);

        // Build victory message using proper Hytale Message API
        Message headerMessage = Message.raw("=== REALM COMPLETED ! ===").color(MessageColors.SUCCESS);

        Message statsMessage = Message.empty()
            .insert(Message.raw("Time : ").color(MessageColors.GRAY))
            .insert(Message.raw(timeStr).color(MessageColors.WHITE))
            .insert(Message.raw(" | Kills : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(totalKills)).color(MessageColors.WHITE))
            .insert(Message.raw(" | Players : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(participantCount)).color(MessageColors.WHITE));

        // Send to all current players
        for (UUID playerId : realm.getCurrentPlayers()) {
            getPlayerRef(playerId).ifPresent(playerRef -> {
                playerRef.sendMessage(headerMessage);
                playerRef.sendMessage(statsMessage);
            });
        }

        LOGGER.atFine().log("Sent victory message to %d players in realm %s",
            realm.getCurrentPlayers().size(),
            realm.getRealmId().toString().substring(0, 8));
    }

    /**
     * Handles an empty realm.
     *
     * <p>When a realm becomes empty (all players leave):
     * <ul>
     *   <li>If the realm is in ENDING state (victory phase), close normally</li>
     *   <li>If the realm is ACTIVE, the timer continues running - realm only closes
     *       on timeout or when all mobs are killed</li>
     *   <li>If allowReentry is false, close immediately</li>
     * </ul>
     *
     * <p>This ensures that realms persist for re-entry and the timer runs independently
     * of player presence, as required.
     */
    private void handleEmptyRealm(RealmInstance realm) {
        // If in ENDING state (victory phase) and all players left, close IMMEDIATELY.
        // No need for the 30s countdown or 60s grace period — everyone already left.
        if (realm.isEnding()) {
            UUID realmId = realm.getRealmId();

            // Cancel victory countdown to prevent race condition
            if (victorySequence != null) {
                victorySequence.cancel(realmId);
            }

            // Close immediately — onRealmClosed() handles final portal cleanup
            removalHandler.closeImmediately(realm, RealmInstance.CompletionReason.COMPLETED);

            LOGGER.atInfo().log("Realm %s in ENDING state and empty - closed instantly",
                realmId.toString().substring(0, 8));
            return;
        }

        // If re-entry is not allowed, close immediately
        if (!config.isAllowReentry()) {
            removalHandler.closeImmediately(realm, RealmInstance.CompletionReason.ABANDONED);
            LOGGER.atInfo().log("Realm %s empty and re-entry disabled - closing",
                realm.getRealmId().toString().substring(0, 8));
            return;
        }

        // Otherwise, the realm stays ACTIVE and the timer continues running.
        // The realm will close when:
        // - Timer expires (handled by checkRealmTimeouts)
        // - All mobs are killed (handled by triggerCompletion)
        LOGGER.atInfo().log("Realm %s is now empty - timer continues (remaining: %s)",
            realm.getRealmId().toString().substring(0, 8),
            formatDuration(realm.getRemainingTime()));
    }

    /**
     * Formats a duration as mm:ss.
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Dispatches container clearing for a realm arena to the world thread.
     * Removes all container blocks (chests, barrels, crates) and closes open windows.
     */
    private void clearRealmContainers(RealmInstance realm) {
        World realmWorld = realm.getWorld();
        if (realmWorld == null || !realmWorld.isAlive()) return;
        realmWorld.execute(() -> RealmContainerClearer.clearContainers(realmWorld, realm));
    }

    /**
     * Checks if a realm should be removed (for RemovalCondition).
     */
    private boolean shouldRemoveRealm(UUID realmId) {
        RealmInstance realm = realmsById.get(realmId);
        if (realm == null) {
            return true; // Unknown realm, remove
        }

        // Check if realm is in terminal state
        if (realm.isClosing()) {
            return true;
        }

        // Check timeout
        if (realm.isActive() && realm.isTimedOut()) {
            realm.markTimedOut();
            return false; // Will be handled by state transition
        }

        return false;
    }



    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the configuration.
     */
    @Nonnull
    public RealmsConfig getConfig() {
        return config;
    }

    /**
     * Gets a player reference by UUID.
     *
     * @param playerId The player's UUID
     * @return Optional containing the PlayerRef if online, empty otherwise
     */
    @Nonnull
    public java.util.Optional<PlayerRef> getPlayerRef(@Nonnull UUID playerId) {
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        return java.util.Optional.ofNullable(playerRef);
    }

    /**
     * Gets the persistence module for crash recovery.
     *
     * @return The persistence module, or null if not initialized
     */
    @Nullable
    public io.github.larsonix.trailoforbis.maps.persistence.RealmPersistenceModule getPersistenceModule() {
        return persistenceModule;
    }

    /**
     * Gets the map generator for creating realm map items.
     */
    @Nonnull
    public RealmMapGenerator getMapGenerator() {
        return mapGenerator;
    }

    /**
     * Gets the modifier configuration.
     */
    @Nonnull
    public RealmModifierConfig getModifierConfig() {
        return modifierConfig;
    }

    /**
     * Gets the template registry.
     */
    @Nonnull
    public RealmTemplateRegistry getTemplateRegistry() {
        return templateRegistry;
    }

    /**
     * Gets the teleport handler.
     */
    @Nonnull
    public RealmTeleportHandler getTeleportHandler() {
        return teleportHandler;
    }

    /**
     * Gets the removal handler.
     */
    @Nonnull
    public RealmRemovalHandler getRemovalHandler() {
        return removalHandler;
    }

    /**
     * Gets the instance factory.
     */
    @Nonnull
    public RealmInstanceFactory getInstanceFactory() {
        return instanceFactory;
    }

    /**
     * Gets the portal manager.
     */
    @Nonnull
    public RealmPortalManager getPortalManager() {
        return portalManager;
    }

    @Nonnull
    public io.github.larsonix.trailoforbis.maps.portal.PortalVisualManager getPortalVisualManager() {
        return portalVisualManager;
    }

    /**
     * Gets the reward service.
     *
     * @return The reward service, or null if not set
     */
    @Nullable
    public RealmRewardService getRewardService() {
        return rewardService;
    }

    /**
     * Sets the reward service.
     *
     * @param rewardService The reward service to use for distributing completion rewards
     */
    public void setRewardService(@Nullable RealmRewardService rewardService) {
        this.rewardService = rewardService;
        if (rewardService != null) {
            LOGGER.atInfo().log("RealmRewardService configured");
        }
    }

    /**
     * Sets the reward chest manager and creates a monitor for it.
     *
     * <p>This also updates the victory sequence with the chest manager
     * so reward chests are spawned during the victory phase.
     *
     * @param rewardChestManager The reward chest manager (may be null to disable)
     */
    public void setRewardChestManager(@Nullable RewardChestManager rewardChestManager) {
        this.rewardChestManager = rewardChestManager;
        if (rewardChestManager != null) {
            this.rewardChestMonitor = new RewardChestMonitor(rewardChestManager);
            if (tickModule != null) {
                tickModule.setRewardChestMonitor(rewardChestMonitor);
            }
            LOGGER.atInfo().log("RewardChestManager configured");

            // Re-create victory sequence with the chest manager if tick module is running
            if (tickModule != null && tickModule.isRunning()) {
                victorySequence = new RealmVictorySequence(
                    hudManager, victoryPortalManager, rewardChestManager, victoryEmoteHelper);
                LOGGER.atInfo().log("Victory sequence updated with RewardChestManager");
            }
        } else {
            this.rewardChestMonitor = null;
            if (tickModule != null) {
                tickModule.setRewardChestMonitor(null);
            }
        }
    }

    /**
     * Gets the reward chest manager.
     *
     * @return The reward chest manager, or null if not configured
     */
    @Nullable
    public RewardChestManager getRewardChestManager() {
        return rewardChestManager;
    }

    /**
     * Gets the entity spawner for NPC spawning via Hytale API.
     *
     * @return The entity spawner, or null if not initialized
     */
    @Nullable
    public RealmEntitySpawner getEntitySpawner() {
        return spawningModule != null ? spawningModule.getEntitySpawner() : null;
    }

    /**
     * Gets the mob spawner coordinator for managing realm mob waves.
     *
     * @return The mob spawner, or null if not initialized
     */
    @Nullable
    public RealmMobSpawner getMobSpawner() {
        return spawningModule != null ? spawningModule.getMobSpawner() : null;
    }

    /**
     * Gets the HUD manager for combat and victory HUDs.
     *
     * @return The HUD manager
     */
    @Nonnull
    public RealmHudManager getHudManager() {
        return hudManager;
    }

    /**
     * Gets the spawn protection manager for realm entry invincibility.
     *
     * @return The spawn protection manager
     */
    @Nonnull
    public RealmSpawnProtection getSpawnProtection() {
        return spawnProtection;
    }

    /**
     * Gets the spawn gateway manager for placing portals around world spawn.
     *
     * @return The spawn gateway manager, or null if not initialized or disabled
     */
    @Nullable
    public SpawnGatewayManager getSpawnGatewayManager() {
        return gatewayModule != null ? gatewayModule.getSpawnGatewayManager() : null;
    }

    /**
     * Gets the gateway upgrade manager for tier-based portal upgrades.
     *
     * @return The gateway upgrade manager, or null if not initialized
     */
    @Nullable
    public GatewayUpgradeManager getGatewayUpgradeManager() {
        return gatewayModule != null ? gatewayModule.getGatewayUpgradeManager() : null;
    }

    /**
     * Ensures spawn gateways exist in a world.
     *
     * <p>Call this when a world becomes active (e.g., when first player enters).
     * The method is idempotent - it will not re-create gateways that already exist.
     *
     * @param world The world to ensure gateways in
     * @return CompletableFuture that completes with true if gateways were placed
     */
    @Nonnull
    public CompletableFuture<Boolean> ensureSpawnGatewaysExist(@Nonnull World world) {
        if (gatewayModule == null) {
            return CompletableFuture.completedFuture(false);
        }
        return gatewayModule.ensureSpawnGatewaysExist(world);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT SYSTEM
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers an event listener for a specific event type.
     *
     * @param eventClass The event class to listen for
     * @param listener The listener to call when the event fires
     * @param <T> The event type
     */
    public <T extends RealmEvent> void addListener(
            @Nonnull Class<T> eventClass,
            @Nonnull Consumer<T> listener) {
        eventBus.addListener(eventClass, listener);
    }

    /**
     * Removes an event listener.
     *
     * @param eventClass The event class
     * @param listener The listener to remove
     * @param <T> The event type
     * @return true if the listener was removed
     */
    public <T extends RealmEvent> boolean removeListener(
            @Nonnull Class<T> eventClass,
            @Nonnull Consumer<T> listener) {
        return eventBus.removeListener(eventClass, listener);
    }

    /**
     * Fires an event to all registered listeners.
     *
     * @param event The event to fire
     * @param <T> The event type
     */
    private <T extends RealmEvent> void fireEvent(@Nonnull T event) {
        eventBus.fireEvent(event);
    }

    /**
     * Returns the event bus for direct module access.
     */
    RealmEventBus getEventBus() {
        return eventBus;
    }
}
