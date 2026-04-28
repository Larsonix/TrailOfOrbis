package io.github.larsonix.trailoforbis.maps;

import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
import com.hypixel.hytale.builtin.instances.removal.RemovalCondition;
import com.hypixel.hytale.component.Store;
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
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.larsonix.trailoforbis.maps.api.RealmsService;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodePedestalPlacer;
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
import io.github.larsonix.trailoforbis.maps.spawning.BiomeMobPool;
import io.github.larsonix.trailoforbis.maps.spawning.RealmEntitySpawner;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobSpawner;
import io.github.larsonix.trailoforbis.maps.spawning.RealmLabyrinthPlacer;
import io.github.larsonix.trailoforbis.maps.spawning.RealmStructurePlacer;
import io.github.larsonix.trailoforbis.maps.spawning.StructureBoundsRegistry;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;
import io.github.larsonix.trailoforbis.maps.completion.VictoryPortalManager;
import io.github.larsonix.trailoforbis.maps.completion.RealmVictorySequence;
import io.github.larsonix.trailoforbis.maps.spawn.SpawnGatewayManager;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayTierRepository;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeConfig;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeManager;
import io.github.larsonix.trailoforbis.database.repository.SpawnGatewayRepository;
import io.github.larsonix.trailoforbis.util.ChunkLoadHelper;
import io.github.larsonix.trailoforbis.util.EmoteCelebrationHelper;
import io.github.larsonix.trailoforbis.util.MessageColors;
import com.hypixel.hytale.math.util.ChunkUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private final RealmTemplateRegistry templateRegistry;
    private final RealmTeleportHandler teleportHandler;
    private final RealmRemovalHandler removalHandler;
    private final RealmInstanceFactory instanceFactory;
    private final RealmPortalManager portalManager;
    private final RealmMapGenerator mapGenerator;

    /** Entity spawner for actual NPC spawning via Hytale API. */
    @Nullable
    private RealmEntitySpawner entitySpawner;

    /** Mob spawner coordinator for managing realm mob waves. */
    @Nullable
    private RealmMobSpawner mobSpawner;

    /** Runtime structure scatterer for complex vanilla prefabs (Monuments, Fossils, etc.). */
    @Nullable
    private RealmStructurePlacer structurePlacer;

    /** Labyrinth carver for MOUNTAINS biome — carves Goblin_Lair corridors from solid terrain. */
    @Nullable
    private RealmLabyrinthPlacer labyrinthPlacer;

    /** Shared registry of placed structure bounds — prevents overlap between placers. */
    @Nullable
    private StructureBoundsRegistry boundsRegistry;

    /** Hexcode pedestal placer — places spell-crafting pedestals in realms when Hexcode is loaded. */
    @Nullable
    private HexcodePedestalPlacer pedestalPlacer;

    /** Reward service for distributing completion rewards. */
    @Nullable
    private RealmRewardService rewardService;

    /** HUD manager for combat and victory HUDs. */
    private final RealmHudManager hudManager;

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

    /** Spawn gateway manager for placing portals around world spawn. */
    @Nullable
    private SpawnGatewayManager spawnGatewayManager;

    /** Gateway upgrade manager for tier-based portal upgrades. */
    @Nullable
    private GatewayUpgradeManager gatewayUpgradeManager;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** All active realm instances by ID. */
    private final Map<UUID, RealmInstance> realmsById = new ConcurrentHashMap<>();

    /** Mapping of player ID to their current realm. */
    private final Map<UUID, UUID> playerToRealm = new ConcurrentHashMap<>();
    /** Mob marker providers per realm — refreshed every tick on world thread. */
    private final Map<UUID, io.github.larsonix.trailoforbis.maps.ui.RealmMobMarkerProvider> mobMarkerProviders = new ConcurrentHashMap<>();

    /** Event listeners for realm events (keyed by event class). */
    private final Map<Class<? extends RealmEvent>, List<Consumer<? extends RealmEvent>>> eventListeners = new ConcurrentHashMap<>();

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

    /** Scheduled executor for periodic tasks. */
    @Nullable
    private ScheduledExecutorService scheduler;

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
        this.templateRegistry = new RealmTemplateRegistry();
        this.teleportHandler = new RealmTeleportHandler();
        this.removalHandler = new RealmRemovalHandler(teleportHandler);
        this.instanceFactory = new RealmInstanceFactory(templateRegistry, config, removalHandler);
        this.portalManager = new RealmPortalManager();
        this.mapGenerator = new RealmMapGenerator(config, modifierConfig);
        this.hudManager = new RealmHudManager();
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
            LOGGER.atInfo().log("Template registry loaded with %d templates",
                templateRegistry.getValidTemplateCount());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to load template registry - realm creation will fail");
            // Continue - listeners still work, validation will catch missing templates
        }

        // Initialize spawning systems
        try {
            LOGGER.atInfo().log("Initializing spawning systems...");
            TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
            if (plugin != null) {
                entitySpawner = new RealmEntitySpawner(plugin);
                mobSpawner = new RealmMobSpawner(entitySpawner, mobPoolConfig);
                boundsRegistry = new StructureBoundsRegistry();
                structurePlacer = new RealmStructurePlacer(boundsRegistry);
                labyrinthPlacer = new RealmLabyrinthPlacer(boundsRegistry);
                mobSpawner.setBoundsRegistry(boundsRegistry);

                // Initialize Hexcode pedestal placer if Hexcode is loaded
                if (HexcodeCompat.isLoaded()) {
                    var pedestalConfig = plugin.getConfigManager().getHexcodeSpellConfig().getRealm_pedestal();
                    pedestalPlacer = new HexcodePedestalPlacer(pedestalConfig);
                    LOGGER.atInfo().log("Hexcode pedestal placer initialized (spawn chance: %.0f%%)",
                        pedestalConfig.getSpawn_chance());
                }

                // Register mob pools for each biome from config
                registerMobPoolsFromConfig();

                LOGGER.atInfo().log("RealmEntitySpawner and RealmMobSpawner initialized with %d biome pools",
                    RealmBiomeType.values().length);
            } else {
                LOGGER.atWarning().log("TrailOfOrbis plugin not available - mob spawning disabled");
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to initialize spawning systems");
            // Continue - realms work without spawning
        }

        // Set up removal condition checker
        try {
            RealmRemovalCondition.setRemovalChecker(this::shouldRemoveRealm);
            LOGGER.atInfo().log("Removal condition checker configured");
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to set up removal condition checker");
        }

        // Start scheduler for periodic tasks
        try {
            LOGGER.atInfo().log("Starting scheduler...");
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "RealmsManager-Scheduler");
                t.setDaemon(true);
                return t;
            });

            // Schedule periodic removal processing
            scheduler.scheduleAtFixedRate(
                this::processTick,
                1, 1, TimeUnit.SECONDS
            );
            LOGGER.atInfo().log("Scheduler started successfully");

            // Initialize victory sequence (requires scheduler)
            victorySequence = new RealmVictorySequence(hudManager, victoryPortalManager, rewardChestManager, victoryEmoteHelper);
            LOGGER.atInfo().log("Victory sequence initialized");
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to start scheduler - realm timeouts disabled");
        }

        // Initialize spawn gateway manager + gateway upgrade system
        try {
            TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
            if (plugin != null && config.getSpawnGatewayConfig().isEnabled()) {
                SpawnGatewayRepository gatewayRepository = new SpawnGatewayRepository(plugin.getDataManager());
                spawnGatewayManager = new SpawnGatewayManager(config.getSpawnGatewayConfig(), gatewayRepository);

                // Initialize gateway upgrade system
                GatewayTierRepository tierRepository = new GatewayTierRepository(plugin.getDataManager());
                GatewayUpgradeConfig upgradeConfig = GatewayUpgradeConfig.createDefault();
                gatewayUpgradeManager = new GatewayUpgradeManager(upgradeConfig, tierRepository);

                // Wire tier repository into spawn gateway manager for auto-registration
                spawnGatewayManager.setGatewayTierRepository(tierRepository);

                LOGGER.atInfo().log("SpawnGatewayManager + GatewayUpgradeManager initialized");
            } else if (plugin == null) {
                LOGGER.atWarning().log("TrailOfOrbis plugin not available - spawn gateways disabled");
            } else {
                LOGGER.atInfo().log("Spawn gateways disabled by config");
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("FAILED to initialize SpawnGatewayManager");
            // Continue - spawn gateways are optional
        }

        instance = this;
        initialized = true;

        LOGGER.atInfo().log("RealmsManager initialized with %d valid templates",
            templateRegistry.getValidTemplateCount());
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

        // Stop scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown victory sequence
        if (victorySequence != null) {
            victorySequence.shutdown();
            victorySequence = null;
        }

        // Remove all HUDs
        hudManager.removeAllHuds();

        // Shutdown reward chest system
        if (rewardChestManager != null) {
            rewardChestManager.shutdown();
            rewardChestManager = null;
            rewardChestMonitor = null;
        }

        // Clear victory portals
        victoryPortalManager.clearAll();

        // Clear spawn gateway + upgrade managers
        if (gatewayUpgradeManager != null) {
            gatewayUpgradeManager.getRepository().clearCache();
            gatewayUpgradeManager = null;
        }
        if (spawnGatewayManager != null) {
            spawnGatewayManager.clearCache();
            spawnGatewayManager = null;
        }

        // Close all realms
        removalHandler.closeAll().join();

        // Shutdown structure placer and bounds registry
        if (structurePlacer != null) {
            structurePlacer.shutdown();
            structurePlacer = null;
        }
        if (labyrinthPlacer != null) {
            labyrinthPlacer.shutdown();
            labyrinthPlacer = null;
        }
        if (boundsRegistry != null) {
            boundsRegistry.shutdown();
            boundsRegistry = null;
        }

        // Shutdown mob spawner
        if (mobSpawner != null) {
            mobSpawner.shutdown();
            mobSpawner = null;
        }

        // Clear entity spawner pending spawns
        if (entitySpawner != null) {
            entitySpawner.clearPendingSpawns();
            entitySpawner = null;
        }

        // Clear state
        realmsById.clear();
        playerToRealm.clear();
        templateRegistry.clear();
        portalManager.clearTracking();

        initialized = false;
        instance = null;

        LOGGER.atInfo().log("RealmsManager shutdown complete");
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

    /**
     * Registers mob pools from configuration for all biomes.
     *
     * <p>Converts {@link MobPoolConfig.BiomeMobPool} (config class) to
     * {@link BiomeMobPool} (spawning class) and registers with the mob spawner.
     */
    private void registerMobPoolsFromConfig() {
        if (mobSpawner == null || mobPoolConfig == null) {
            LOGGER.atWarning().log("Cannot register mob pools: spawner or config not available");
            return;
        }

        int registered = 0;
        for (RealmBiomeType biome : RealmBiomeType.values()) {
            MobPoolConfig.BiomeMobPool configPool = mobPoolConfig.getMobPool(biome);

            // Convert config pool to spawning pool
            // Note: Elite is now a spawn-time modifier, not a separate pool
            BiomeMobPool.Builder builder = BiomeMobPool.builder(biome);

            // Add regular mobs (includes former elite mobs)
            for (Map.Entry<String, Integer> entry : configPool.getRegularMobs().entrySet()) {
                builder.addNormal(entry.getKey(), entry.getValue());
            }

            // Add boss mobs
            for (Map.Entry<String, Integer> entry : configPool.getBossMobs().entrySet()) {
                builder.addBoss(entry.getKey(), entry.getValue());
            }

            BiomeMobPool pool = builder.build();
            mobSpawner.registerPool(pool);
            registered++;

            LOGGER.atFine().log("Registered mob pool for %s: %d normal, %d boss",
                biome.name(),
                configPool.getRegularMobs().size(),
                configPool.getBossMobs().size());
        }

        LOGGER.atInfo().log("Registered %d biome mob pools", registered);
    }

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

        // Immediately deactivate entry portals so new maps can be used
        portalManager.deactivateEntryPortalsNow(realm);

        // Despawn all mobs so they don't attack during the grace period
        if (mobSpawner != null) {
            World realmWorld = realm.getWorld();
            if (realmWorld != null && realmWorld.isAlive()) {
                realmWorld.execute(() -> {
                    int despawned = mobSpawner.despawnAllMobs(realm);
                    if (despawned > 0) {
                        LOGGER.atFine().log("Despawned %d remaining mobs on realm %s close (reason: %s)",
                            despawned, realmId.toString().substring(0, 8), reason);
                    }
                });
            }
        }

        // Victory uses full grace period (60s) so players can explore portal + chest.
        // Timeout uses shorter grace (15s = 10s defeat banner + 5s buffer) — no reason
        // to keep the world alive for 60s when the player is being teleported out.
        Duration gracePeriod;
        if (reason == RealmInstance.CompletionReason.TIMEOUT) {
            gracePeriod = Duration.ofSeconds(15);
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

        // Fire event to listeners
        fireEvent(new RealmCreatedEvent(realm, realm.getOwnerId()));

        LOGGER.atInfo().log("Realm %s created (total: %d)", realm.getRealmId(), realmsById.size());
    }

    /**
     * Called when a realm becomes ready.
     */
    private void onRealmReady(RealmInstance realm) {
        if (realm.getWorld() != null) {
            fireEvent(new RealmReadyEvent(realm, realm.getWorld()));

            // Add map markers for combat realms (not skill sanctum)
            if (!realm.getBiome().isUtilityBiome()) {
                World realmWorld = realm.getWorld();
                realmWorld.setCompassUpdating(true);
                realmWorld.getWorldMapManager().addMarkerProvider(
                    "realmPortal", new RealmPortalMarkerProvider(realm.getMapData()));
                // Red markers for alive mobs — helps players locate remaining monsters
                var mobMarkerProvider = new io.github.larsonix.trailoforbis.maps.ui
                    .RealmMobMarkerProvider(realm.getRealmId(), mobSpawner);
                realmWorld.getWorldMapManager().addMarkerProvider("realmMobs", mobMarkerProvider);
                mobMarkerProviders.put(realm.getRealmId(), mobMarkerProvider);
            }
        }
    }

    /**
     * Called when a player enters a realm.
     */
    private void onPlayerEnterRealm(UUID playerId, RealmInstance realm) {
        playerToRealm.put(playerId, realm.getRealmId());

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

            // Initialize mob spawning for this realm (skip utility biomes like sanctum)
            // CRITICAL: Spawning must run on the realm's world thread, not the caller's thread.
            // The PlayerAddedToWorldEvent fires on the OLD world's thread, but NPCPlugin.spawnEntity()
            // requires running on the target world's thread.
            if (mobSpawner != null && !realm.getBiome().isUtilityBiome()) {
                World realmWorld = realm.getWorld();
                if (realmWorld != null) {
                    // Await chunks in the spawn area before spawning mobs.
                    // Arena centered at (0, 0), radius varies by realm size (25-200 blocks).
                    int radiusInChunks = (realm.getMapData().computeArenaRadius() / ChunkUtil.SIZE) + 1;
                    // Use thenRun (not thenRunAsync) so mob spawning runs inline on the
                    // world thread in the same tick chunks finish loading — zero gap means
                    // no "chunk not loaded" warnings from UpdateLocationSystems.
                    realmWorld.execute(() -> {
                        ChunkLoadHelper.awaitAreaLoaded(realmWorld, 0, 0, radiusInChunks)
                            .thenRun(() -> {
                                // Pre-register spawn zone as occupied bounds so boss camp
                                // satellites and scattered structures can't overlap it.
                                if (boundsRegistry != null) {
                                    boundsRegistry.registerSpawnExclusion(
                                        realm.getRealmId(), 0, 0, 12);
                                }
                                // Place structures before mobs. MOUNTAINS uses labyrinth carver;
                                // all other biomes use scatter placer.
                                if (realm.getBiome() == RealmBiomeType.MOUNTAINS && labyrinthPlacer != null) {
                                    var labResult = labyrinthPlacer.generate(realmWorld, realm);
                                    realm.setLabyrinthResult(labResult);
                                } else if (structurePlacer != null) {
                                    structurePlacer.placeStructures(realmWorld, realm);
                                }
                                // Place Hexcode pedestal (after structures, before mobs)
                                if (pedestalPlacer != null) {
                                    pedestalPlacer.tryPlacePedestal(realmWorld, realm);
                                }
                                // SUCCESS: on world thread, same tick — zero gap
                                mobSpawner.initializeSpawning(realm)
                                    .thenAccept(result -> {
                                        if (result.success()) {
                                            LOGGER.atInfo().log("Realm %s spawning initialized: %d/%d mobs",
                                                realm.getRealmId().toString().substring(0, 8),
                                                result.spawned(), result.target());
                                        } else {
                                            LOGGER.atWarning().log("Realm %s spawning failed: %s",
                                                realm.getRealmId().toString().substring(0, 8),
                                                result.error());
                                        }
                                    })
                                    .exceptionally(ex -> {
                                        LOGGER.atWarning().withCause(ex).log("Error initializing spawning for realm %s",
                                            realm.getRealmId());
                                        return null;
                                    });
                            })
                            .exceptionally(ex -> {
                                // TIMEOUT: runs on JVM scheduler thread, dispatch to world thread
                                LOGGER.atWarning().log("Chunk load timeout for realm %s, spawning with delay",
                                    realm.getRealmId().toString().substring(0, 8));
                                realmWorld.execute(() -> {
                                    // Pre-register spawn exclusion (timeout path)
                                    if (boundsRegistry != null) {
                                        boundsRegistry.registerSpawnExclusion(
                                            realm.getRealmId(), 0, 0, 12);
                                    }
                                    // Place structures before mobs (timeout path)
                                    if (realm.getBiome() == RealmBiomeType.MOUNTAINS && labyrinthPlacer != null) {
                                        var labResult = labyrinthPlacer.generate(realmWorld, realm);
                                        realm.setLabyrinthResult(labResult);
                                    } else if (structurePlacer != null) {
                                        structurePlacer.placeStructures(realmWorld, realm);
                                    }
                                    // Place Hexcode pedestal (timeout path)
                                    if (pedestalPlacer != null) {
                                        pedestalPlacer.tryPlacePedestal(realmWorld, realm);
                                    }
                                    mobSpawner.initializeSpawning(realm)
                                        .thenAccept(result -> {
                                            if (result.success()) {
                                                LOGGER.atInfo().log("Realm %s spawning initialized (after timeout): %d/%d mobs",
                                                    realm.getRealmId().toString().substring(0, 8),
                                                    result.spawned(), result.target());
                                            } else {
                                                LOGGER.atWarning().log("Realm %s spawning failed (after timeout): %s",
                                                    realm.getRealmId().toString().substring(0, 8),
                                                    result.error());
                                            }
                                        })
                                        .exceptionally(ex2 -> {
                                            LOGGER.atWarning().withCause(ex2).log("Error initializing spawning for realm %s (after timeout)",
                                                realm.getRealmId());
                                            return null;
                                        });
                                });
                                return null;
                            });
                    });
                } else {
                    LOGGER.atWarning().log("Cannot initialize spawning: realm %s has no world",
                        realm.getRealmId().toString().substring(0, 8));
                }
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
     * which fires before the player leaves the world. This method only
     * handles tracking and empty realm logic.
     */
    private void onPlayerExitRealm(UUID playerId, RealmInstance realm) {
        playerToRealm.remove(playerId);

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
        mobMarkerProviders.remove(realm.getRealmId());

        // Clean up player mappings
        for (UUID playerId : realm.getAllParticipants()) {
            playerToRealm.remove(playerId, realm.getRealmId());
        }

        // Clean up victory portal if any
        victoryPortalManager.removeVictoryPortal(realm);

        // Clean up reward chest (unclaimed items are discarded — chest is the reward mechanism)
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
        if (mobSpawner != null) {
            World realmWorld = realm.getWorld();
            if (realmWorld != null && realmWorld.isAlive()) {
                realmWorld.execute(() -> {
                    int despawned = mobSpawner.despawnAllMobs(realm);
                    if (despawned > 0) {
                        LOGGER.atFine().log("Despawned %d remaining mobs on realm %s victory",
                            despawned, realm.getRealmId().toString().substring(0, 8));
                    }
                });
            }
        }

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
    // PERIODIC TASKS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called every second to process periodic tasks.
     *
     * <p>This method runs independently of player presence, ensuring that:
     * <ul>
     *   <li>Realm timeouts are checked even when no players are in the realm</li>
     *   <li>Portal and removal cleanup happens regularly</li>
     *   <li>Spawn gateway particles are spawned periodically</li>
     * </ul>
     */
    private void processTick() {
        try {
            // Process pending removals
            removalHandler.processPendingRemovals();

            // Process portal timeouts
            int expiredPortals = portalManager.processPortalTimeouts();
            if (expiredPortals > 0) {
                LOGGER.atFine().log("Removed %d expired portals", expiredPortals);
            }

            // Check for timed out realms (runs EVERY tick - critical for timeout enforcement)
            // This runs regardless of whether players are in the realm
            checkRealmTimeouts();

            // Enforce arena boundaries — clamp mobs that wander outside the arena
            enforceArenaBoundaries();

            // Despawn recovery — respawn mobs that were lost to chunk unloading,
            // NPC behavior tree ActionDespawn, or other unexpected removal.
            // Throttled to every ~5 seconds to avoid per-tick overhead.
            checkDespawnedMobs();

            // Refresh mob marker positions (dispatched to world thread for ECS access)
            refreshMobMarkerPositions();

            // Tick combat HUDs (timer countdown + kill progress) — dispatched to world thread.
            // This replaces HyUI's withRefreshRate() which uses a ScheduledExecutorService
            // on its own thread and races with world transitions.
            tickCombatHuds();

            // Check for closed reward chests
            if (rewardChestMonitor != null) {
                rewardChestMonitor.tick();
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error in realm tick processing");
        }
    }

    /**
     * Checks all active realms for timeout expiration.
     *
     * <p>This runs independently of player presence. The realm timer starts when
     * the first player enters (ACTIVE state) and continues even if all players leave.
     * A realm only closes on:
     * <ul>
     *   <li>Timer expiration (failure/timeout)</li>
     *   <li>All mobs killed (victory)</li>
     * </ul>
     */
    private int timerDiagCounter = 0;

    /** Tick counter for throttling despawn recovery checks (every ~5 seconds = 100 ticks). */
    private int despawnRecoveryCounter = 0;
    private static final int DESPAWN_RECOVERY_INTERVAL_TICKS = 100;

    private void checkRealmTimeouts() {
        timerDiagCounter++;
        boolean doDiag = (timerDiagCounter % 10 == 1); // Log every 10 seconds

        for (RealmInstance realm : realmsById.values()) {
            if (!realm.isActive()) continue;

            // DIAGNOSTIC: Check vanilla timer state every 10 seconds
            if (doDiag) {
                World world = realm.getWorld();
                if (world != null) {
                    try {
                        Store<ChunkStore> cs = world.getChunkStore().getStore();
                        InstanceDataResource idr = cs.getResource(InstanceDataResource.getResourceType());
                        InstanceWorldConfig iwc = InstanceWorldConfig.get(world.getWorldConfig());
                        int condCount = iwc != null ? iwc.getRemovalConditions().length : -1;
                        String condTypes = "";
                        if (iwc != null && condCount > 0) {
                            StringBuilder sb = new StringBuilder();
                            for (RemovalCondition c : iwc.getRemovalConditions()) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(c.getClass().getSimpleName());
                            }
                            condTypes = sb.toString();
                        }
                        LOGGER.atInfo().log(
                            "[TIMER-DIAG] Realm %s: elapsed=%ds, timeout=%ds, remaining=%ds | vanilla: conditions=%d [%s], timeoutTimer=%s, isRemoving=%b",
                            realm.getRealmId().toString().substring(0, 8),
                            realm.getElapsedTime().toSeconds(),
                            realm.getTimeout().toSeconds(),
                            realm.getRemainingTime().toSeconds(),
                            condCount, condTypes,
                            idr.getTimeoutTimer(),
                            idr.isRemoving()
                        );
                    } catch (Exception e) {
                        LOGGER.atFine().log("[TIMER-DIAG] Could not read vanilla state for realm %s: %s",
                            realm.getRealmId().toString().substring(0, 8), e.getMessage());
                    }
                }
            }

            // Utility biomes (skill sanctum) have no timer — infinite duration
            if (realm.getBiome().isUtilityBiome()) {
                continue;
            }

            if (realm.isTimedOut()) {
                LOGGER.atInfo().log("[TIMER-DIAG] OUR TIMER triggered timeout for realm %s (elapsed=%ds >= timeout=%ds)",
                    realm.getRealmId().toString().substring(0, 8),
                    realm.getElapsedTime().toSeconds(),
                    realm.getTimeout().toSeconds());
                realm.markTimedOut();
                closeRealm(realm.getRealmId(), RealmInstance.CompletionReason.TIMEOUT);
            }
        }
    }


    /**
     * Refreshes mob marker positions for all active realms.
     * Dispatches to each realm's world thread for ECS store access.
     */
    /**
     * Enforces arena boundaries for all active realms.
     * Dispatches to each realm's world thread for ECS store access.
     */
    private void enforceArenaBoundaries() {
        for (RealmInstance realm : realmsById.values()) {
            if (!realm.isActive()) continue;
            World world = realm.getWorld();
            if (world == null || !world.isAlive()) continue;
            UUID realmId = realm.getRealmId();
            world.execute(() -> mobSpawner.enforceArenaBoundaries(realmId, world));
        }
    }

    /**
     * Checks for and respawns mobs that have despawned unexpectedly.
     *
     * <p>Throttled to every {@link #DESPAWN_RECOVERY_INTERVAL_TICKS} ticks (~5 seconds).
     * Dispatches the actual respawn work to each realm's world thread since
     * {@code spawnNow()} requires world thread access for ECS store operations.
     */
    private void checkDespawnedMobs() {
        despawnRecoveryCounter++;
        if (despawnRecoveryCounter < DESPAWN_RECOVERY_INTERVAL_TICKS) {
            return;
        }
        despawnRecoveryCounter = 0;

        for (RealmInstance realm : realmsById.values()) {
            if (!realm.isActive()) continue;
            World world = realm.getWorld();
            if (world == null || !world.isAlive()) continue;
            world.execute(() -> mobSpawner.checkAndRespawnDespawnedMobs(realm));
        }
    }

    private void tickCombatHuds() {
        // Iterate active realms and dispatch HUD refresh to each realm's world thread
        for (RealmInstance realm : realmsById.values()) {
            if (!realm.isActive()) continue;
            World world = realm.getWorld();
            if (world == null || !world.isAlive()) continue;

            world.execute(() -> hudManager.tickCombatHuds());
        }
    }

    private void refreshMobMarkerPositions() {
        for (var entry : mobMarkerProviders.entrySet()) {
            RealmInstance realm = realmsById.get(entry.getKey());
            if (realm == null || !realm.isActive()) {
                continue;
            }
            World world = realm.getWorld();
            if (world == null || !world.isAlive()) continue;

            var provider = entry.getValue();
            world.execute(() -> provider.refreshPositions(world));
        }
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
            LOGGER.atInfo().log("RewardChestManager configured");

            // Re-create victory sequence with the chest manager if scheduler is available
            if (scheduler != null) {
                victorySequence = new RealmVictorySequence(
                    hudManager, victoryPortalManager, rewardChestManager, victoryEmoteHelper);
                LOGGER.atInfo().log("Victory sequence updated with RewardChestManager");
            }
        } else {
            this.rewardChestMonitor = null;
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
        return entitySpawner;
    }

    /**
     * Gets the mob spawner coordinator for managing realm mob waves.
     *
     * @return The mob spawner, or null if not initialized
     */
    @Nullable
    public RealmMobSpawner getMobSpawner() {
        return mobSpawner;
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
     * Gets the spawn gateway manager for placing portals around world spawn.
     *
     * @return The spawn gateway manager, or null if not initialized or disabled
     */
    @Nullable
    public SpawnGatewayManager getSpawnGatewayManager() {
        return spawnGatewayManager;
    }

    /**
     * Gets the gateway upgrade manager for tier-based portal upgrades.
     *
     * @return The gateway upgrade manager, or null if not initialized
     */
    @Nullable
    public GatewayUpgradeManager getGatewayUpgradeManager() {
        return gatewayUpgradeManager;
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
        if (spawnGatewayManager == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Load existing gateway tiers into cache when world activates
        if (gatewayUpgradeManager != null) {
            UUID worldUuid = world.getWorldConfig().getUuid();
            gatewayUpgradeManager.getRepository().loadWorldGateways(worldUuid);
        }

        return spawnGatewayManager.ensureGatewaysExist(world);
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
        Objects.requireNonNull(eventClass, "Event class cannot be null");
        Objects.requireNonNull(listener, "Listener cannot be null");

        eventListeners
            .computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
            .add(listener);
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
        List<Consumer<? extends RealmEvent>> listeners = eventListeners.get(eventClass);
        if (listeners != null) {
            return listeners.remove(listener);
        }
        return false;
    }

    /**
     * Fires an event to all registered listeners.
     *
     * @param event The event to fire
     * @param <T> The event type
     */
    @SuppressWarnings("unchecked")
    private <T extends RealmEvent> void fireEvent(@Nonnull T event) {
        Class<?> eventClass = event.getClass();

        // Fire to specific listeners
        List<Consumer<? extends RealmEvent>> listeners = eventListeners.get(eventClass);
        if (listeners != null) {
            for (Consumer<? extends RealmEvent> listener : listeners) {
                try {
                    ((Consumer<T>) listener).accept(event);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in realm event listener for %s",
                        eventClass.getSimpleName());
                }
            }
        }

        // Fire to base RealmEvent listeners (if any registered)
        if (eventClass != RealmEvent.class) {
            List<Consumer<? extends RealmEvent>> baseListeners = eventListeners.get(RealmEvent.class);
            if (baseListeners != null) {
                for (Consumer<? extends RealmEvent> listener : baseListeners) {
                    try {
                        ((Consumer<RealmEvent>) listener).accept(event);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Error in base realm event listener");
                    }
                }
            }
        }
    }
}
