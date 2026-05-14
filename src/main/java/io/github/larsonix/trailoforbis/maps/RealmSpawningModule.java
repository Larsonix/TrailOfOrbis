package io.github.larsonix.trailoforbis.maps;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodePedestalPlacer;
import io.github.larsonix.trailoforbis.maps.config.MobPoolConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.instance.RealmPortalMarkerProvider;
import io.github.larsonix.trailoforbis.maps.spawning.BiomeMobPool;
import io.github.larsonix.trailoforbis.maps.spawning.RealmEntitySpawner;
import io.github.larsonix.trailoforbis.maps.spawning.RealmLabyrinthPlacer;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobSpawner;
import io.github.larsonix.trailoforbis.maps.spawning.RealmStructurePlacer;
import io.github.larsonix.trailoforbis.maps.spawning.StructureBoundsRegistry;
import io.github.larsonix.trailoforbis.maps.ui.RealmMobMarkerProvider;
import io.github.larsonix.trailoforbis.util.ChunkLoadHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all mob spawning, structure placement, and boundary enforcement for realms.
 *
 * <p>Owns the full spawning pipeline: entity spawner, mob spawner, structure/labyrinth
 * placers, bounds registry, Hexcode pedestal placer, and mob marker providers.
 */
final class RealmSpawningModule {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Sub-components
    @Nullable private RealmEntitySpawner entitySpawner;
    @Nullable private RealmMobSpawner mobSpawner;
    @Nullable private RealmStructurePlacer structurePlacer;
    @Nullable private RealmLabyrinthPlacer labyrinthPlacer;
    @Nullable private StructureBoundsRegistry boundsRegistry;
    @Nullable private HexcodePedestalPlacer pedestalPlacer;

    /** Mob marker providers per realm — refreshed every tick by RealmTickModule. */
    private final Map<UUID, RealmMobMarkerProvider> mobMarkerProviders = new ConcurrentHashMap<>();

    // Config
    @Nullable private MobPoolConfig mobPoolConfig;

    /**
     * Initializes all spawning sub-components.
     *
     * @param plugin The plugin instance
     * @param mobPoolConfig The mob pool configuration
     */
    void initialize(@Nonnull TrailOfOrbis plugin, @Nonnull MobPoolConfig mobPoolConfig) {
        this.mobPoolConfig = mobPoolConfig;

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

        registerMobPoolsFromConfig();
        LOGGER.atInfo().log("Spawning module initialized with %d biome pools",
            RealmBiomeType.values().length);
    }

    void shutdown() {
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
        if (mobSpawner != null) {
            mobSpawner.shutdown();
            mobSpawner = null;
        }
        if (entitySpawner != null) {
            entitySpawner.clearPendingSpawns();
            entitySpawner = null;
        }
        mobMarkerProviders.clear();
    }

    boolean isInitialized() {
        return mobSpawner != null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOB POOL REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

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

            BiomeMobPool.Builder builder = BiomeMobPool.builder(biome);

            for (Map.Entry<String, Integer> entry : configPool.getRegularMobs().entrySet()) {
                builder.addNormal(entry.getKey(), entry.getValue());
            }

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
    // REALM SPAWNING INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initializes mob spawning for a realm on first player entry.
     *
     * <p>This method handles the full spawning pipeline:
     * <ol>
     *   <li>Await chunk loading in the spawn area</li>
     *   <li>Register spawn exclusion zone</li>
     *   <li>Place structures (labyrinth for MOUNTAINS, scatter for others)</li>
     *   <li>Place Hexcode pedestal (if Hexcode is loaded)</li>
     *   <li>Initialize mob spawning</li>
     * </ol>
     *
     * <p>Must only be called for non-utility biomes. Dispatches all work
     * to the realm's world thread.
     *
     * @param realm The realm instance (must have a world)
     */
    void initializeSpawningForRealm(@Nonnull RealmInstance realm) {
        if (mobSpawner == null) return;
        if (realm.getBiome().isUtilityBiome()) return;

        World realmWorld = realm.getWorld();
        if (realmWorld == null) {
            LOGGER.atWarning().log("Cannot initialize spawning: realm %s has no world",
                realm.getRealmId().toString().substring(0, 8));
            return;
        }

        int radiusInChunks = (realm.getMapData().computeArenaRadius() / ChunkUtil.SIZE) + 1;

        realmWorld.execute(() -> {
            ChunkLoadHelper.awaitAreaLoaded(realmWorld, 0, 0, radiusInChunks)
                .thenRun(() -> placeAndSpawn(realmWorld, realm))
                .exceptionally(ex -> {
                    LOGGER.atWarning().log("Chunk load timeout for realm %s, spawning with delay",
                        realm.getRealmId().toString().substring(0, 8));
                    realmWorld.execute(() -> placeAndSpawn(realmWorld, realm));
                    return null;
                });
        });
    }

    /**
     * Places structures and spawns mobs. Called on world thread after chunks are loaded.
     */
    private void placeAndSpawn(World realmWorld, RealmInstance realm) {
        // Pre-register spawn zone as occupied bounds
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

        // Spawn mobs
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
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOB LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Despawns all mobs in a realm. Dispatches to the realm's world thread.
     *
     * @param realm The realm instance
     * @param reason Log message context
     */
    void despawnAllMobs(@Nonnull RealmInstance realm, @Nonnull String reason) {
        if (mobSpawner == null) return;
        World realmWorld = realm.getWorld();
        if (realmWorld == null || !realmWorld.isAlive()) return;
        realmWorld.execute(() -> {
            int despawned = mobSpawner.despawnAllMobs(realm);
            if (despawned > 0) {
                LOGGER.atFine().log("Despawned %d remaining mobs on realm %s %s",
                    despawned, realm.getRealmId().toString().substring(0, 8), reason);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOB MARKERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets up compass mob markers for a realm. Called when the realm becomes ready.
     *
     * @param realm The realm instance
     */
    void setupMobMarkers(@Nonnull RealmInstance realm) {
        if (realm.getBiome().isUtilityBiome()) return;

        World realmWorld = realm.getWorld();
        if (realmWorld == null) return;

        // Compass + portal marker are independent of mob spawning
        realmWorld.setCompassUpdating(true);
        realmWorld.getWorldMapManager().addMarkerProvider(
            "realmPortal", new RealmPortalMarkerProvider(realm.getMapData()));

        // Mob markers require the mob spawner
        if (mobSpawner != null) {
            var mobMarkerProvider = new RealmMobMarkerProvider(realm.getRealmId(), mobSpawner);
            realmWorld.getWorldMapManager().addMarkerProvider("realmMobs", mobMarkerProvider);
            mobMarkerProviders.put(realm.getRealmId(), mobMarkerProvider);
        }
    }

    /**
     * Removes mob markers for a closed realm.
     */
    void removeMobMarkers(@Nonnull UUID realmId) {
        mobMarkerProviders.remove(realmId);
    }

    /**
     * Full cleanup for a closed realm: mob spawner state, entity refs, bounds registry.
     *
     * @return UUIDs of all mobs tracked in the realm (for external cleanup, e.g. ailments)
     */
    @Nonnull
    java.util.Set<UUID> cleanupRealm(@Nonnull UUID realmId) {
        java.util.Set<UUID> mobUuids = java.util.Collections.emptySet();
        if (mobSpawner != null) {
            mobUuids = mobSpawner.cleanupRealm(realmId);
        }
        if (boundsRegistry != null) {
            boundsRegistry.onRealmClosed(realmId);
        }
        return mobUuids;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    RealmEntitySpawner getEntitySpawner() { return entitySpawner; }

    @Nullable
    RealmMobSpawner getMobSpawner() { return mobSpawner; }

    @Nonnull
    Map<UUID, RealmMobMarkerProvider> getMobMarkerProviders() { return mobMarkerProviders; }
}
