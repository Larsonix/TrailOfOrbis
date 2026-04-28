package io.github.larsonix.trailoforbis.maps.spawn;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import io.github.larsonix.trailoforbis.database.repository.SpawnGatewayRepository;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayTierRepository;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;
import io.github.larsonix.trailoforbis.util.TerrainUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages spawn gateway portals placed around world spawn.
 *
 * <p>Spawn gateways are a ring of Portal_Device blocks placed around the
 * world spawn point to help new players discover the realm map system.
 *
 * <h2>Portal Behavior</h2>
 * <p>The portals use {@code Portal_Device} blocks which:
 * <ul>
 *   <li>Have built-in MagicPortal particle effects</li>
 *   <li>Emit light and have animations</li>
 *   <li>Only activate when a player with a realm map interacts (F key)</li>
 *   <li>Are safe for new players who don't have maps yet</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 * <p>Gateway placement state is persisted per-world in the database to prevent
 * re-creating portals on server restart. The portals themselves are physical
 * blocks that persist in the world.
 *
 * @see RealmsConfig.SpawnGatewayConfig
 * @see SpawnGatewayRepository
 */
public class SpawnGatewayManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Minimum Y below which we consider "no solid ground". */
    private static final int SCAN_MIN_Y = 30;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private final RealmsConfig.SpawnGatewayConfig config;
    private final SpawnGatewayRepository repository;

    /** Gateway tier repository for registering placed gateways at tier 0. */
    @Nullable
    private GatewayTierRepository gatewayTierRepository;

    /** Cached portal block type. */
    @Nullable
    private volatile BlockType portalBlockType;

    /** Tracks worlds currently being processed to prevent duplicate placement. */
    private final Map<UUID, Boolean> processingWorlds = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new SpawnGatewayManager.
     *
     * @param config The spawn gateway configuration
     * @param repository The repository for persistence
     */
    public SpawnGatewayManager(
            @Nonnull RealmsConfig.SpawnGatewayConfig config,
            @Nonnull SpawnGatewayRepository repository) {

        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Ensures spawn gateways exist in a world.
     *
     * <p>This method is idempotent - it checks the database before placing
     * and skips if gateways already exist. Safe to call multiple times.
     *
     * @param world The world to place gateways in
     * @return CompletableFuture that completes with true if gateways were placed
     */
    @Nonnull
    public CompletableFuture<Boolean> ensureGatewaysExist(@Nonnull World world) {
        Objects.requireNonNull(world, "world cannot be null");

        // Check if feature is enabled
        if (!config.isEnabled()) {
            LOGGER.atFine().log("Spawn gateways disabled by config");
            return CompletableFuture.completedFuture(false);
        }

        UUID worldUuid = world.getWorldConfig().getUuid();

        // Prevent concurrent placement for same world
        if (processingWorlds.putIfAbsent(worldUuid, true) != null) {
            LOGGER.atFine().log("World %s already being processed, skipping",
                worldUuid.toString().substring(0, 8));
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Check database for existing placement
        boolean alreadyPlaced = repository.hasGatewaysPlaced(worldUuid);
        if (alreadyPlaced) {
            LOGGER.atFine().log("Spawn gateways already placed in world %s",
                worldUuid.toString().substring(0, 8));
            processingWorlds.remove(worldUuid);
            future.complete(false);
            return future;
        }

        // Execute placement on world thread
        world.execute(() -> {
            try {
                boolean success = placeGatewaysInternal(world);
                if (success) {
                    // Persist state to database
                    repository.markGatewaysPlacedSync(
                        worldUuid,
                        config.getPortalCount(),
                        config.getRingRadius()
                    );
                }
                future.complete(success);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to place spawn gateways in world %s",
                    worldUuid.toString().substring(0, 8));
                future.complete(false);
            } finally {
                processingWorlds.remove(worldUuid);
            }
        });

        return future;
    }

    /**
     * Forces re-placement of spawn gateways in a world.
     *
     * <p>This removes the database record and places new gateways.
     * Use with caution as it may create duplicate blocks if old ones weren't removed.
     *
     * @param world The world to re-place gateways in
     * @return CompletableFuture that completes with true if gateways were placed
     */
    @Nonnull
    public CompletableFuture<Boolean> forceReplaceGateways(@Nonnull World world) {
        Objects.requireNonNull(world, "world cannot be null");

        UUID worldUuid = world.getWorldConfig().getUuid();

        // Remove database record
        repository.removeGatewayState(worldUuid);

        // Place new gateways
        return ensureGatewaysExist(world);
    }

    /**
     * Sets the gateway tier repository for registering placed gateways.
     *
     * @param gatewayTierRepository The tier repository (nullable)
     */
    public void setGatewayTierRepository(@Nullable GatewayTierRepository gatewayTierRepository) {
        this.gatewayTierRepository = gatewayTierRepository;
    }

    /**
     * Checks if spawn gateways are enabled in config.
     *
     * @return true if spawn gateways are enabled
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Places spawn gateways around the world spawn (must run on world thread).
     *
     * @param world The world to place gateways in
     * @return true if all gateways were placed successfully
     */
    private boolean placeGatewaysInternal(@Nonnull World world) {
        // Resolve block type
        BlockType portalType = resolvePortalBlockType();

        if (portalType == null) {
            LOGGER.atWarning().log("Cannot place spawn gateways - portal block type not found: %s",
                config.getPortalBlockType());
            return false;
        }

        // Get spawn origin
        Vector3d spawnOrigin = DistanceBonusCalculator.getSpawnOrigin(world);
        LOGGER.atInfo().log("Spawn origin for gateway ring: (%.1f, %.1f, %.1f)",
            spawnOrigin.x, spawnOrigin.y, spawnOrigin.z);

        // Calculate ring positions
        List<Vector3i> positions = calculateRingPositions(spawnOrigin);

        UUID worldUuid = world.getWorldConfig().getUuid();
        int placedCount = 0;
        int skippedCount = 0;

        for (Vector3i pos2d : positions) {
            // Find ground level at this position
            int groundY = findGroundLevel(world, pos2d.x, pos2d.z);

            // Skip if in water (no solid ground)
            if (groundY < SCAN_MIN_Y) {
                LOGGER.atFine().log("Skipping gateway at (%d, %d) - no solid ground", pos2d.x, pos2d.z);
                skippedCount++;
                continue;
            }

            Vector3i portalPos = new Vector3i(pos2d.x, groundY, pos2d.z);

            // Ensure chunk is loaded
            WorldChunk chunk = world.getChunkIfInMemory(
                ChunkUtil.indexChunkFromBlock(portalPos.x, portalPos.z));
            if (chunk == null) {
                LOGGER.atFine().log("Skipping gateway at %s - chunk not loaded", portalPos);
                skippedCount++;
                continue;
            }

            // Place portal block
            try {
                chunk.setBlock(portalPos.x, portalPos.y, portalPos.z, portalType.getId());
                placedCount++;
                LOGGER.atInfo().log("Placed gateway portal at %s", portalPos);

                // Register as tier 0 gateway in the tier repository
                if (gatewayTierRepository != null) {
                    gatewayTierRepository.registerGateway(worldUuid, portalPos.x, portalPos.y, portalPos.z);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to place gateway at %s", portalPos);
                skippedCount++;
            }
        }

        LOGGER.atInfo().log("Spawn gateways placed: %d portals, %d skipped (world: %s)",
            placedCount, skippedCount, worldUuid.toString().substring(0, 8));

        return placedCount > 0;
    }

    /**
     * Calculates ring positions around the spawn point.
     *
     * @param spawn The spawn center point
     * @return List of XZ positions (Y will be determined by ground detection)
     */
    @Nonnull
    private List<Vector3i> calculateRingPositions(@Nonnull Vector3d spawn) {
        List<Vector3i> positions = new ArrayList<>();
        int portalCount = config.getPortalCount();
        int radius = config.getRingRadius();

        for (int i = 0; i < portalCount; i++) {
            // Evenly spaced angles starting from north (0 degrees)
            double angle = (2 * Math.PI / portalCount) * i;

            // Calculate offset from spawn
            // Note: In Hytale, +Z is south, -Z is north, +X is east, -X is west
            int x = (int) Math.round(spawn.x + radius * Math.sin(angle));
            int z = (int) Math.round(spawn.z - radius * Math.cos(angle));

            positions.add(new Vector3i(x, 0, z)); // Y will be set later
        }

        return positions;
    }

    /**
     * Finds the ground level at a given X,Z position.
     *
     * <p>Delegates to {@link TerrainUtils#findGroundLevel(World, int, int)} which
     * uses opacity and draw type checks to skip vegetation (leaves, bushes, etc.)
     * and land on actual terrain blocks.
     *
     * @param world The world to scan
     * @param x The X coordinate
     * @param z The Z coordinate
     * @return The Y coordinate one block above ground
     */
    private int findGroundLevel(@Nonnull World world, int x, int z) {
        return TerrainUtils.findGroundLevel(world, x, z);
    }

    /**
     * Resolves the portal block type from config.
     *
     * @return The portal block type, or null if not found
     */
    @Nullable
    private BlockType resolvePortalBlockType() {
        if (portalBlockType != null) {
            return portalBlockType;
        }

        String typeName = config.getPortalBlockType();
        BlockType blockType = BlockType.getAssetMap().getAsset(typeName);
        if (blockType != null && !blockType.isUnknown()) {
            portalBlockType = blockType;
            LOGGER.atInfo().log("Resolved portal block type: %s", typeName);
            return portalBlockType;
        }

        LOGGER.atWarning().log("Portal block type not found: %s", typeName);
        return null;
    }

    /**
     * Clears cached block types (call when shutting down).
     */
    public void clearCache() {
        portalBlockType = null;
        processingWorlds.clear();
    }
}
