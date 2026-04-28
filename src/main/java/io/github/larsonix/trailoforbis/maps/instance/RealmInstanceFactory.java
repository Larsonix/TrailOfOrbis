package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
import com.hypixel.hytale.builtin.instances.removal.RemovalCondition;
import com.hypixel.hytale.builtin.portals.integrations.PortalGameplayConfig;
import com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.core.RealmState;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.spawning.SpawnZoneClearer;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplate;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Factory for creating realm instances.
 *
 * <p>This class handles the creation of RealmInstance objects and spawning
 * the backing Hytale world instances. It coordinates with:
 * <ul>
 *   <li>{@link RealmTemplateRegistry} - for template lookup</li>
 *   <li>{@link InstancesPlugin} - for world spawning</li>
 *   <li>{@link RealmRemovalHandler} - for configuring removal conditions</li>
 * </ul>
 *
 * <h2>Creation Process</h2>
 * <ol>
 *   <li>Validate map data and lookup template</li>
 *   <li>Create RealmInstance in CREATING state</li>
 *   <li>Spawn world using InstancesPlugin</li>
 *   <li>Configure removal conditions</li>
 *   <li>Transition to READY state when world loads</li>
 * </ol>
 *
 * @see RealmInstance
 * @see RealmTemplate
 */
public class RealmInstanceFactory {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RealmTemplateRegistry templateRegistry;
    private final RealmsConfig config;
    private final RealmRemovalHandler removalHandler;

    /**
     * Callback for newly created realms.
     */
    @Nullable
    private Consumer<RealmInstance> onRealmCreated;

    /**
     * Callback when realm becomes ready.
     */
    @Nullable
    private Consumer<RealmInstance> onRealmReady;

    /**
     * Creates a new realm factory.
     *
     * @param templateRegistry The template registry
     * @param config The realms configuration
     * @param removalHandler The removal handler
     */
    public RealmInstanceFactory(
            @Nonnull RealmTemplateRegistry templateRegistry,
            @Nonnull RealmsConfig config,
            @Nonnull RealmRemovalHandler removalHandler) {
        this.templateRegistry = Objects.requireNonNull(templateRegistry);
        this.config = Objects.requireNonNull(config);
        this.removalHandler = Objects.requireNonNull(removalHandler);
    }

    /**
     * Sets the callback for when a realm is created.
     */
    public void setOnRealmCreated(@Nullable Consumer<RealmInstance> callback) {
        this.onRealmCreated = callback;
    }

    /**
     * Sets the callback for when a realm becomes ready.
     */
    public void setOnRealmReady(@Nullable Consumer<RealmInstance> callback) {
        this.onRealmReady = callback;
    }

    /**
     * Creates a new realm instance.
     *
     * @param mapData The map data defining the realm
     * @param ownerId The player who is opening the realm
     * @param originWorld The world where the portal will be
     * @param returnPoint The location to return players to
     * @return A future that completes with the realm instance
     */
    @Nonnull
    public CompletableFuture<RealmInstance> createRealm(
            @Nonnull RealmMapData mapData,
            @Nonnull UUID ownerId,
            @Nonnull World originWorld,
            @Nonnull Transform returnPoint) {

        Objects.requireNonNull(mapData, "Map data cannot be null");
        Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        Objects.requireNonNull(originWorld, "Origin world cannot be null");
        Objects.requireNonNull(returnPoint, "Return point cannot be null");

        // Compute arena radius to select the right terrain tier.
        // The tier determines wall position — walls match the arena, not a fixed 140.
        var arenaConfig = config.getArenaScalingConfig();
        int arenaRadius = mapData.computeArenaRadius(arenaConfig);

        // Utility biomes (Skill Sanctum) use a single fixed template — no radius tiers.
        // Combat biomes use the radius tier system for correctly sized arena walls.
        String tierTemplateName;
        RealmTemplate template;
        if (mapData.biome().isUtilityBiome()) {
            tierTemplateName = mapData.biome().getTemplateName(mapData.size());
            template = templateRegistry.getOrCreateTemplate(mapData.biome(), mapData.size());
        } else {
            tierTemplateName = mapData.biome().getTemplateNameForRadius(arenaRadius);
            template = templateRegistry.getOrCreateTemplateByName(
                tierTemplateName, mapData.biome(), mapData.size());
        }

        // Generate realm ID
        UUID realmId = UUID.randomUUID();

        // Create instance
        RealmInstance realm = new RealmInstance(realmId, mapData, template, ownerId);

        LOGGER.atInfo().log("Creating realm %s for player %s (biome: %s, level: %d, arena: %d, tier: %s)",
            realmId, ownerId, mapData.biome(), mapData.level(), arenaRadius, tierTemplateName);

        // Notify callback
        if (onRealmCreated != null) {
            try {
                onRealmCreated.accept(realm);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in realm created callback");
            }
        }

        // Spawn the world, then async-load arena chunks, then transition to READY.
        // CRITICAL: Do NOT use .join() inside .thenApply() — it deadlocks because
        // chunk generation needs the same thread the future callback runs on.
        // Use .thenCompose() to chain async steps without blocking.
        return spawnWorld(realm, template, originWorld, returnPoint)
            .thenCompose(world -> {
                // Store world reference
                String worldName = "realm-" + realmId;
                realm.setWorld(world, worldName);

                                // Timer computed from mob count (arenaConfig/arenaRadius already computed above)
                int timeoutSeconds = mapData.computeTimeoutSeconds(arenaConfig);

                // Initialize portal world and disable vanilla timer
                initializePortalWorldAndDisableVanillaTimer(world, timeoutSeconds);

                // Configure custom removal conditions
                double gracePeriod = config.getCompletionGracePeriodSeconds();
                removalHandler.configureWorldRemoval(realm, gracePeriod);

                // Async-load ALL chunks covering the computed arena radius.
                int chunkRadius = (arenaRadius / 16) + 1;
                var chunkFutures = new java.util.ArrayList<CompletableFuture<?>>();
                for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
                    for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                        long idx = ChunkUtil.indexChunkFromBlock(cx * 16, cz * 16);
                        chunkFutures.add(world.getChunkAsync(idx));
                    }
                }

                // Wait for ALL chunks to load, then transition to READY
                return CompletableFuture.allOf(chunkFutures.toArray(CompletableFuture[]::new))
                    .thenApply(unused -> {
                        LOGGER.atFine().log("Preloaded %d chunks for realm %s (radius=%d)",
                            chunkFutures.size(), realmId, arenaRadius);

                        // Scan from Base height + 1 (not Y=256 or Y=80) to avoid hitting
                        // dome ceilings in Caverns. Base+1 is always below any ceiling.
                        int maxScanY = (int) io.github.larsonix.trailoforbis.maps.templates
                            .RealmTemplateRegistry.getBaseYForBiome(mapData.biome()) + 1;

                        // Clear WorldGen props (trees, rocks, bushes) around spawn.
                        // Must run BEFORE spawn transform is set so ground detection
                        // sees clean terrain. Skip MOUNTAINS (labyrinth carves its own
                        // spawn clearing) and utility biomes (no combat, fixed template).
                        RealmBiomeType biome = mapData.biome();
                        if (biome != RealmBiomeType.MOUNTAINS && !biome.isUtilityBiome()) {
                            SpawnZoneClearer.clearSpawnArea(
                                world, biome, 0, 0,
                                SpawnZoneClearer.DEFAULT_CLEAR_RADIUS, maxScanY);
                        }

                        // Override world spawn point to actual terrain height at (0,0).
                        int groundY = io.github.larsonix.trailoforbis.util.TerrainUtils
                            .findGroundLevel(world, 0, 0, maxScanY);
                        Transform spawnTransform = new Transform(
                            new Vector3d(0, groundY + 0.5, 0), new Vector3f(0, 0, 0));
                        world.getWorldConfig().setSpawnProvider(
                            new com.hypixel.hytale.server.core.universe.world.spawn
                                .IndividualSpawnProvider(spawnTransform));
                        LOGGER.atFine().log("Set realm spawn to terrain height Y=%d for %s",
                            groundY, realmId);

                        // Transition to READY (all arena chunks guaranteed loaded)
                        realm.transitionTo(RealmState.READY);

                        // Notify callback
                        if (onRealmReady != null) {
                            try {
                                onRealmReady.accept(realm);
                            } catch (Exception e) {
                                LOGGER.atWarning().withCause(e).log("Error in realm ready callback");
                            }
                        }

                        LOGGER.atInfo().log("Realm %s is ready (world: %s)", realmId, worldName);
                        return realm;
                    });
            })
            .exceptionally(ex -> {
                LOGGER.atSevere().withCause(ex).log("Failed to create realm %s", realmId);
                realm.forceClose(RealmInstance.CompletionReason.ERROR);
                throw new RuntimeException("Failed to create realm", ex);
            });
    }

    /**
     * Spawns the backing world for a realm.
     */
    @Nonnull
    private CompletableFuture<World> spawnWorld(
            @Nonnull RealmInstance realm,
            @Nonnull RealmTemplate template,
            @Nonnull World originWorld,
            @Nonnull Transform returnPoint) {

        String templateName = template.templateName();

        LOGGER.atFine().log("[DEBUG] Checking if instance template exists: '%s'", templateName);

        // Check if template exists
        boolean exists = InstancesPlugin.doesInstanceAssetExist(templateName);
        LOGGER.atFine().log("[DEBUG] InstancesPlugin.doesInstanceAssetExist('%s') = %s", templateName, exists);

        if (!exists) {
            LOGGER.atWarning().log("Template %s does not exist in any loaded asset pack!", templateName);
            // Could use a fallback template here
            return CompletableFuture.failedFuture(
                new IllegalStateException("Template not found: " + templateName));
        }

        LOGGER.atFine().log("[DEBUG] Spawning instance with template: '%s'", templateName);

        // Spawn instance using InstancesPlugin
        // We initialize PortalWorld AFTER spawning (see initializePortalWorldForRealm)
        // to make portals validate properly, while suppressing the vanilla timer UI
        // in RealmPlayerEnterListener.
        return InstancesPlugin.get().spawnInstance(templateName, originWorld, returnPoint)
            .thenApply(world -> {
                // Randomize seed so each realm generates unique terrain and prop placement.
                // Without this, every instance of the same template produces identical worlds.
                // Must happen BEFORE chunks are loaded (they're loaded in the caller's thenCompose).
                long seed = ThreadLocalRandom.current().nextLong();
                world.getWorldConfig().setSeed(seed);
                LOGGER.atFine().log("Realm world seed randomized to %d for template %s", seed, templateName);
                return world;
            });
    }

    /**
     * Initializes PortalWorld and disables vanilla timer system in one atomic operation.
     *
     * <p>This method performs THREE critical tasks in a single {@code world.execute()} block
     * to prevent race conditions with Hytale's system ticks:
     *
     * <h3>1. Initialize PortalWorld</h3>
     * <p>Required for victory portal validation. Without this, {@code PortalWorld.exists()}
     * returns false and victory portals fail with "not in portal world".
     *
     * <h3>2. Disable Vanilla Timer UI</h3>
     * <p>The vanilla timer UI ({@code PortalTrackerSystems.UiTickingSystem}) shows when:
     * <ol>
     *   <li>{@code PortalWorld.exists() == true}</li>
     *   <li>{@code InstanceDataResource.getTimeoutTimer() != null}</li>
     * </ol>
     * <p>By setting {@code timeoutTimer = null}, we keep PortalWorld for portal validation
     * but prevent the timer UI from appearing. Our custom {@code RealmCombatHud} provides
     * the timer display for combat realms instead.
     *
     * <h3>3. Disable Vanilla Auto-Removal</h3>
     * <p>Hytale's {@code RemovalSystem} checks {@code InstanceWorldConfig.removalConditions}
     * each tick. If conditions are met, it auto-removes the world, kicking players.
     * <p><b>Critical:</b> {@code TimeoutCondition.shouldRemoveWorld()} will SET the
     * {@code timeoutTimer} if it's null! By clearing {@code removalConditions} to an empty
     * array FIRST, we prevent this from happening.
     *
     * <h3>Why Atomic?</h3>
     * <p>If these operations are in separate {@code world.execute()} calls, system ticks
     * may occur between them. For example:
     * <ol>
     *   <li>We set {@code timeoutTimer = null}</li>
     *   <li>System tick: {@code RemovalSystem} calls {@code TimeoutCondition.shouldRemoveWorld()}</li>
     *   <li>{@code TimeoutCondition} sees null timer, SETS it to {@code now + timeout}</li>
     *   <li>Our second call to clear removal conditions runs, but timer is already set</li>
     *   <li>Vanilla timer UI appears!</li>
     * </ol>
     * <p>By combining everything in one {@code world.execute()}, we ensure all changes
     * happen in a single tick, before any system can interfere.
     *
     * @param world The realm world to initialize
     * @param timeoutSeconds The realm timeout in seconds (used for PortalWorld init)
     */
    private void initializePortalWorldAndDisableVanillaTimer(@Nonnull World world, int timeoutSeconds) {
        // DIAGNOSTIC: Log state BEFORE world.execute (from the calling thread)
        LOGGER.atInfo().log("[TIMER-DIAG] Queuing world.execute for '%s' with timeoutSeconds=%d (calling thread: %s)",
            world.getName(), timeoutSeconds, Thread.currentThread().getName());

        world.execute(() -> {
            try {
                LOGGER.atInfo().log("[TIMER-DIAG] world.execute() running for '%s' (world thread: %s)",
                    world.getName(), Thread.currentThread().getName());

                // ═══════════════════════════════════════════════════════════════
                // DIAGNOSTIC: Dump BSON-loaded state BEFORE any changes
                // ═══════════════════════════════════════════════════════════════
                InstanceWorldConfig preConfig = InstanceWorldConfig.get(world.getWorldConfig());
                Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
                InstanceDataResource instanceData = chunkStore.getResource(InstanceDataResource.getResourceType());

                if (preConfig != null) {
                    RemovalCondition[] preConds = preConfig.getRemovalConditions();
                    LOGGER.atInfo().log("[TIMER-DIAG] BEFORE clearing - RemovalConditions count: %d", preConds.length);
                    for (int i = 0; i < preConds.length; i++) {
                        LOGGER.atInfo().log("[TIMER-DIAG]   condition[%d]: %s (class: %s)",
                            i, preConds[i], preConds[i].getClass().getSimpleName());
                    }
                } else {
                    LOGGER.atInfo().log("[TIMER-DIAG] BEFORE clearing - InstanceWorldConfig is NULL (no Plugin.Instance in BSON)");
                }
                LOGGER.atInfo().log("[TIMER-DIAG] BEFORE clearing - timeoutTimer: %s, hadPlayer: %b",
                    instanceData.getTimeoutTimer(), instanceData.hadPlayer());

                // ═══════════════════════════════════════════════════════════════
                // STEP 0: Apply realm GameplayConfig (disables block interactions)
                // ═══════════════════════════════════════════════════════════════
                world.getWorldConfig().setGameplayConfig("Realm_Default");

                // ═══════════════════════════════════════════════════════════════
                // STEP 0b: Disable vanilla NPC spawning and chunk unloading
                // ═══════════════════════════════════════════════════════════════
                world.getWorldConfig().setSpawningNPC(false);
                world.getWorldConfig().setIsSpawnMarkersEnabled(false);
                // Prevent chunk unloading �� mobs in chunks beyond the player's
                // 96-block view radius (LARGE/MASSIVE arenas) are silently removed
                // with RemoveReason.UNLOAD when their chunk unloads. Realm instances
                // are short-lived (5-20 min) so the memory cost is bounded.
                world.getWorldConfig().setCanUnloadChunks(false);
                world.getWorldConfig().markChanged();

                // ═══════════════════════════════════════════════════════════════
                // STEP 1: Disable vanilla removal conditions FIRST
                // ═══════════════════════════════════════════════════════════════
                InstanceWorldConfig worldConfig = InstanceWorldConfig.ensureAndGet(world.getWorldConfig());
                worldConfig.setRemovalConditions(); // Empty array = no auto-removal

                // ═══════════════════════════════════════════════════════════════
                // STEP 2: Set timeoutTimer to null
                // ═══════════════════════════════════════════════════════════════
                instanceData.setTimeoutTimer(null);

                LOGGER.atInfo().log("[TIMER-DIAG] AFTER clearing - conditions: %d, timeoutTimer: %s",
                    worldConfig.getRemovalConditions().length, instanceData.getTimeoutTimer());

                // ═══════════════════════════════════════════════════════════════
                // STEP 3: Initialize PortalWorld (for victory portal validation)
                // ═══════════════════════════════════════════════════════════════
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                PortalWorld portalWorld = entityStore.getResource(PortalWorld.getResourceType());

                PortalType portalType = PortalType.getAssetMap().getAsset("Realm_Default");
                if (portalType == null) {
                    portalType = PortalType.getAssetMap().getAsset("Henges");
                }
                if (portalType == null) {
                    portalType = PortalType.getAssetMap().getAssetMap().values().stream()
                        .findFirst().orElse(null);
                }

                if (portalType == null) {
                    LOGGER.atWarning().log("No PortalType assets found - victory portals will fail!");
                    return;
                }

                PortalRemovalCondition removalCondition = new PortalRemovalCondition(timeoutSeconds);
                PortalGameplayConfig gameplayConfig = new PortalGameplayConfig();
                portalWorld.init(portalType, timeoutSeconds, removalCondition, gameplayConfig);

                Transform spawnPoint = getWorldSpawnPoint(world);
                if (spawnPoint != null) {
                    portalWorld.setSpawnPoint(spawnPoint);
                }

                // ═══════════════════════════════════════════════════════════════
                // STEP 4: Re-confirm timeoutTimer is null (init may have set it)
                // ═══════════════════════════════════════════════════════════════
                instanceData.setTimeoutTimer(null);

                // ═══════════════════════════════════════════════════════════════
                // DIAGNOSTIC: Final state
                // ═══════════════════════════════════════════════════════════════
                LOGGER.atInfo().log("[TIMER-DIAG] FINAL STATE for '%s':", world.getName());
                LOGGER.atInfo().log("[TIMER-DIAG]   conditions: %d", worldConfig.getRemovalConditions().length);
                LOGGER.atInfo().log("[TIMER-DIAG]   timeoutTimer: %s", instanceData.getTimeoutTimer());
                LOGGER.atInfo().log("[TIMER-DIAG]   portalWorld.exists: %b, timeLimitSeconds: %d",
                    portalWorld.exists(), portalWorld.getTimeLimitSeconds());
                LOGGER.atInfo().log("[TIMER-DIAG]   PortalWorld type: %s", portalType.getId());

            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to initialize PortalWorld for realm");
            }
        });
    }

    /**
     * Gets the spawn point from a world's configuration.
     *
     * @param world The world
     * @return The spawn transform, or null if not available
     */
    @Nullable
    private Transform getWorldSpawnPoint(@Nonnull World world) {
        try {
            var spawnProvider = world.getWorldConfig().getSpawnProvider();
            if (spawnProvider != null) {
                // Use a default position since we don't have a specific player ref here
                return spawnProvider.getSpawnPoint(world, null);
            }
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log("Could not get world spawn point");
        }
        return null;
    }

    /**
     * Creates a realm with a specific world name.
     *
     * @param mapData The map data
     * @param ownerId The owner
     * @param originWorld The origin world
     * @param returnPoint The return point
     * @param worldName Custom world name
     * @return A future with the realm instance
     */
    @Nonnull
    public CompletableFuture<RealmInstance> createRealmWithName(
            @Nonnull RealmMapData mapData,
            @Nonnull UUID ownerId,
            @Nonnull World originWorld,
            @Nonnull Transform returnPoint,
            @Nonnull String worldName) {

        RealmTemplate template = templateRegistry.getOrCreateTemplate(mapData.biome(), mapData.size());
        UUID realmId = UUID.randomUUID();
        RealmInstance realm = new RealmInstance(realmId, mapData, template, ownerId);

        if (onRealmCreated != null) {
            onRealmCreated.accept(realm);
        }

        String templateName = template.templateName();

        return InstancesPlugin.get().spawnInstance(templateName, worldName, originWorld, returnPoint)
            .thenApply(world -> {
                // Randomize seed for unique terrain (same as primary path)
                long seed = ThreadLocalRandom.current().nextLong();
                world.getWorldConfig().setSeed(seed);

                realm.setWorld(world, worldName);

                // Initialize portal world and disable vanilla spawns (same as main path)
                int timeoutSeconds = mapData.computeTimeoutSeconds(config.getArenaScalingConfig());
                initializePortalWorldAndDisableVanillaTimer(world, timeoutSeconds);

                removalHandler.configureWorldRemoval(realm, config.getCompletionGracePeriodSeconds());
                realm.transitionTo(RealmState.READY);

                if (onRealmReady != null) {
                    onRealmReady.accept(realm);
                }

                return realm;
            });
    }

    /**
     * Creates a return point transform.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Transform for the return point
     */
    @Nonnull
    public static Transform createReturnPoint(float x, float y, float z) {
        return new Transform(
            new Vector3d(x, y, z),
            new Vector3f(0, 0, 0)
        );
    }

    /**
     * Creates a return point from doubles.
     */
    @Nonnull
    public static Transform createReturnPoint(double x, double y, double z) {
        return new Transform(
            new Vector3d(x, y, z),
            new Vector3f(0, 0, 0)
        );
    }

    /**
     * Validates that a template exists for the given map data.
     *
     * @param mapData The map data to validate
     * @return true if a valid template exists
     */
    public boolean hasValidTemplate(@Nonnull RealmMapData mapData) {
        if (mapData.biome().isUtilityBiome()) {
            String templateName = mapData.biome().getTemplateName(mapData.size());
            return templateRegistry.doesTemplateExist(templateName);
        }
        // Use tier-based template lookup: compute arena radius → nearest tier → check exists.
        var arenaConfig = config.getArenaScalingConfig();
        int arenaRadius = mapData.computeArenaRadius(arenaConfig);
        String tierTemplate = mapData.biome().getTemplateNameForRadius(arenaRadius);
        return templateRegistry.doesTemplateExist(tierTemplate);
    }

    /**
     * Gets the template for map data.
     *
     * @param mapData The map data
     * @return The template, or null if not found
     */
    @Nullable
    public RealmTemplate getTemplate(@Nonnull RealmMapData mapData) {
        return templateRegistry.getTemplate(mapData.biome(), mapData.size());
    }


}
