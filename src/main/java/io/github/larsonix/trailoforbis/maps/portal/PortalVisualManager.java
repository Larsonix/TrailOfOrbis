package io.github.larsonix.trailoforbis.maps.portal;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages all visual layers for active realm portals.
 *
 * <p>When a realm portal is activated, this manager:
 * <ol>
 *   <li>Swaps the Portal_Device block to a biome-colored variant</li>
 *   <li>Places decorative crystal blocks around the portal (scaled by map size)</li>
 *   <li>Spawns rarity-colored particle aura (ticking every 500ms)</li>
 *   <li>Adds corrupted overlay particles for corrupted maps</li>
 * </ol>
 *
 * <p>On deactivation (realm close), all visuals are reverted:
 * block swapped back to Portal_Device, decoratives removed, particles stop.
 */
public class PortalVisualManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String BASE_PORTAL_BLOCK = "Portal_Device";
    private static final long PARTICLE_TICK_INTERVAL_MS = 500;
    private static final double PARTICLE_Y_OFFSET = 1.0;
    private static final double CORRUPTED_Y_OFFSET = 0.5;
    private static final String SMOKE_PARTICLE = "Realm_Rarity_Smoke";
    private static final String CORE_PARTICLE = "Realm_Rarity_Core";
    private static final String SPARKLE_PARTICLE = "Realm_Rarity_Sparkle";

    /** Active portal visual states, keyed by "worldUuid:x:y:z". */
    private final Map<String, PortalVisualState> activePortals = new ConcurrentHashMap<>();

    /** Scheduler for particle ticking. */
    @Nullable
    private ScheduledFuture<?> tickTask;

    @Nullable
    private ScheduledExecutorService scheduler;

    // ═══════════════════════════════════════════════════════════════════
    // ACTIVATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Activates portal visuals for a realm map.
     *
     * <p>Must be called on the world thread, BEFORE activatePortalDevice().
     * This swaps the block, places decoratives, and starts particle tracking.
     *
     * @param world    The overworld containing the portal
     * @param position The portal block position
     * @param mapData  The realm map being activated
     */
    public void activate(@Nonnull World world, @Nonnull Vector3i position, @Nonnull RealmMapData mapData) {
        RealmBiomeType biome = mapData.biome();
        GearRarity rarity = mapData.rarity();
        RealmLayoutSize size = mapData.size();
        int quality = mapData.quality();
        boolean corrupted = mapData.corrupted();
        UUID worldUuid = world.getWorldConfig().getUuid();

        String key = createKey(worldUuid, position);

        // Deactivate any existing visuals at this position
        if (activePortals.containsKey(key)) {
            deactivateInternal(world, position, key);
        }

        // Layer 1: Swap portal block to biome variant
        if (biome.hasPortalVariant()) {
            swapPortalBlock(world, position, biome.getPortalBlockId());
        }

        // Layer 4: Place decorative blocks
        PortalVisualState state = new PortalVisualState(
                worldUuid, position, biome, rarity, size, quality, corrupted);
        placeDecorativeBlocks(world, position, biome, size, state.getDecorativePositions());

        // Register for particle ticking
        activePortals.put(key, state);
        ensureTickingStarted();

        LOGGER.atInfo().log("Activated portal visuals at %s: biome=%s, rarity=%s, size=%s, quality=%d%%, corrupted=%b",
                position, biome, rarity, size, quality, corrupted);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEACTIVATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Deactivates portal visuals at a position.
     *
     * <p>Must be called on the world thread. Reverts the block to Portal_Device,
     * removes decorative blocks, and stops particle tracking.
     *
     * @param world    The world containing the portal
     * @param position The portal block position
     */
    public void deactivate(@Nonnull World world, @Nonnull Vector3i position) {
        UUID worldUuid = world.getWorldConfig().getUuid();
        String key = createKey(worldUuid, position);
        deactivateInternal(world, position, key);
    }

    private void deactivateInternal(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String key) {
        PortalVisualState state = activePortals.remove(key);
        if (state == null) return;

        // Layer 1: Revert portal block to base Portal_Device
        if (state.getBiome().hasPortalVariant()) {
            swapPortalBlock(world, position, BASE_PORTAL_BLOCK);
        }

        // Layer 4: Remove decorative blocks
        removeDecorativeBlocks(world, state.getDecorativePositions());

        LOGGER.atInfo().log("Deactivated portal visuals at %s", position);

        // Stop ticking if no more active portals
        if (activePortals.isEmpty()) {
            stopTicking();
        }
    }

    /**
     * Deactivates visuals for a set of portal positions.
     *
     * <p>Removes from activePortals IMMEDIATELY (stops particle ticking on next tick),
     * then defers block/decorative cleanup to the world thread.
     * Safe to call from any thread.
     *
     * @param portals The portal positions to deactivate
     */
    public void deactivateForPortals(@Nonnull java.util.Set<io.github.larsonix.trailoforbis.maps.instance.RealmPortalManager.PortalPosition> portals) {
        LOGGER.atInfo().log("deactivateForPortals called with %d portals, activePortals has %d entries",
                portals.size(), activePortals.size());

        for (var portal : portals) {
            String key = createKey(portal.worldUuid(), portal.position());
            PortalVisualState state = activePortals.remove(key);
            if (state == null) {
                LOGGER.atInfo().log("No visual state found for key %s (keys in map: %s)",
                        key, activePortals.keySet());
                continue;
            }

            // Defer block/decorative cleanup to world thread
            World world = portal.getWorld();
            if (world != null && world.isAlive()) {
                final PortalVisualState s = state;
                world.execute(() -> {
                    if (s.getBiome().hasPortalVariant()) {
                        swapPortalBlock(world, s.getPosition(), BASE_PORTAL_BLOCK);
                    }
                    removeDecorativeBlocks(world, s.getDecorativePositions());
                });
            }

            LOGGER.atInfo().log("Deactivated portal visuals at %s", portal.position());
        }

        if (activePortals.isEmpty()) {
            stopTicking();
        }
    }

    /**
     * Deactivates all portal visuals for a given world.
     * Called during world shutdown or realm cleanup.
     */
    public void deactivateAllInWorld(@Nonnull UUID worldUuid) {
        activePortals.entrySet().removeIf(entry -> {
            PortalVisualState state = entry.getValue();
            if (state.getWorldUuid().equals(worldUuid)) {
                World world = Universe.get().getWorld(worldUuid);
                if (world != null && world.isAlive()) {
                    if (state.getBiome().hasPortalVariant()) {
                        swapPortalBlock(world, state.getPosition(), BASE_PORTAL_BLOCK);
                    }
                    removeDecorativeBlocks(world, state.getDecorativePositions());
                }
                return true;
            }
            return false;
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 1: BLOCK SWAP
    // ═══════════════════════════════════════════════════════════════════

    private void swapPortalBlock(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String targetBlockId) {
        try {
            BlockType targetType = BlockType.getAssetMap().getAsset(targetBlockId);
            if (targetType == null || targetType.isUnknown()) {
                LOGGER.atWarning().log("Portal block type not found: %s", targetBlockId);
                return;
            }

            WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(position.x, position.z));
            if (chunk == null) {
                LOGGER.atWarning().log("Chunk not loaded for portal swap at %s", position);
                return;
            }

            chunk.setBlock(position.x, position.y, position.z, targetType.getId());
            LOGGER.atFine().log("Swapped portal block at %s to %s", position, targetBlockId);

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to swap portal block at %s to %s", position, targetBlockId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 3: RARITY PARTICLE AURA (ticking)
    // ═══════════════════════════════════════════════════════════════════

    private void ensureTickingStarted() {
        if (tickTask != null && !tickTask.isDone()) return;

        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "portal-visual-tick");
                t.setDaemon(true);
                return t;
            });
        }

        tickTask = scheduler.scheduleAtFixedRate(this::tickAllPortals,
                PARTICLE_TICK_INTERVAL_MS, PARTICLE_TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopTicking() {
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
    }

    private void tickAllPortals() {
        for (PortalVisualState state : activePortals.values()) {
            try {
                World world = Universe.get().getWorld(state.getWorldUuid());
                if (world == null || !world.isAlive()) continue;

                // Must execute particle spawning on world thread
                world.execute(() -> spawnRarityParticles(world, state));
            } catch (Exception e) {
                LOGGER.atFine().withCause(e).log("Error ticking portal particles at %s", state.getPosition());
            }
        }
    }

    private void spawnRarityParticles(@Nonnull World world, @Nonnull PortalVisualState state) {
        try {
            ComponentAccessor<EntityStore> accessor = world.getEntityStore().getStore();
            SpatialResource<Ref<EntityStore>, EntityStore> spatial =
                    accessor.getResource(EntityModule.get().getPlayerSpatialResourceType());

            Vector3i pos = state.getPosition();
            Vector3d center = new Vector3d(pos.x + 0.5, pos.y + PARTICLE_Y_OFFSET, pos.z + 0.5);

            List<Ref<EntityStore>> nearbyPlayers = SpatialResource.getThreadLocalReferenceList();
            spatial.getSpatialStructure().collect(center, ParticleUtil.DEFAULT_PARTICLE_DISTANCE, nearbyPlayers);

            if (nearbyPlayers.isEmpty()) {
                LOGGER.atFine().log("No nearby players for rarity particles at %s (tick %d)",
                        state.getPosition(), state.getTickCounter());
                return;
            }

            state.incrementTick();
            Color rarityColor = parseHexColor(state.getRarity().getParticleHexColor());
            var rng = java.util.concurrent.ThreadLocalRandom.current();

            // ── Layer 1: Smoke ring — colored smoke drifting upward in a circle ──
            int smokeCount = state.getSmokeParticleCount();
            float smokeScale = state.getSmokeParticleScale();
            double radius = state.getSmokeRingRadius();

            for (int i = 0; i < smokeCount; i++) {
                double angle = (2 * Math.PI / smokeCount) * i + (state.getTickCounter() * 0.1);
                double jitterX = (rng.nextDouble() - 0.5) * 0.3;
                double jitterZ = (rng.nextDouble() - 0.5) * 0.3;
                double jitterY = rng.nextDouble() * 0.5;
                double px = center.x + radius * Math.cos(angle) + jitterX;
                double pz = center.z + radius * Math.sin(angle) + jitterZ;
                double py = center.y + jitterY;

                ParticleUtil.spawnParticleEffect(
                        SMOKE_PARTICLE, px, py, pz,
                        0, 0, 0, smokeScale, rarityColor,
                        null, nearbyPlayers, accessor);
            }

            // ── Layer 2: Core glow — bright pulse at portal center ──
            float coreScale = state.getCoreParticleScale();
            double coreJitterY = rng.nextDouble() * 0.3;
            ParticleUtil.spawnParticleEffect(
                    CORE_PARTICLE, center.x, center.y + coreJitterY, center.z,
                    0, 0, 0, coreScale, rarityColor,
                    null, nearbyPlayers, accessor);

            // ── Layer 3: Sparkle accents — Epic+ only, bright flashes ──
            if (state.hasSparkleLayer()) {
                int sparkleCount = state.getSparkleCount();
                for (int i = 0; i < sparkleCount; i++) {
                    double angle = rng.nextDouble() * 2 * Math.PI;
                    double sparkleRadius = radius * (0.5 + rng.nextDouble() * 0.8);
                    double px = center.x + sparkleRadius * Math.cos(angle);
                    double pz = center.z + sparkleRadius * Math.sin(angle);
                    double py = center.y + rng.nextDouble() * 0.8;

                    ParticleUtil.spawnParticleEffect(
                            SPARKLE_PARTICLE, px, py, pz,
                            0, 0, 0, 1.0f, rarityColor,
                            null, nearbyPlayers, accessor);
                }
            }

            // ── Corrupted overlay — darker, slower motes below portal ──
            if (state.isCorrupted()) {
                Color corruptedColor = new Color((byte) 0x40, (byte) 0x00, (byte) 0x00);
                double corruptedY = pos.y + CORRUPTED_Y_OFFSET;
                for (int i = 0; i < 2; i++) {
                    double angle = (2 * Math.PI / 2) * i + (state.getTickCounter() * 0.08);
                    double jX = (rng.nextDouble() - 0.5) * 0.2;
                    double jZ = (rng.nextDouble() - 0.5) * 0.2;
                    double px = center.x + 0.6 * Math.cos(angle) + jX;
                    double pz = center.z + 0.6 * Math.sin(angle) + jZ;

                    ParticleUtil.spawnParticleEffect(
                            SMOKE_PARTICLE, px, corruptedY + rng.nextDouble() * 0.3, pz,
                            0, 0, 0, smokeScale * 0.8f, corruptedColor,
                            null, nearbyPlayers, accessor);
                }
            }

        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log("Error spawning rarity particles at %s", state.getPosition());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 4: DECORATIVE BLOCKS
    // ═══════════════════════════════════════════════════════════════════

    private static final int[][] SMALL_OFFSETS = {{2, 2, 0}, {-2, 2, 0}};
    private static final int[][] MEDIUM_OFFSETS = {{2, 2, 0}, {-2, 2, 0}, {0, 2, 2}, {0, 2, -2}};
    private static final int[][] LARGE_OFFSETS = {{2, 2, 0}, {-2, 2, 0}, {0, 2, 2}, {0, 2, -2}, {1, 3, 0}, {-1, 3, 0}};
    private static final int[][] MASSIVE_OFFSETS = {{2, 2, 0}, {-2, 2, 0}, {0, 2, 2}, {0, 2, -2}, {1, 3, 1}, {-1, 3, 1}, {1, 3, -1}, {-1, 3, -1}};

    private void placeDecorativeBlocks(
            @Nonnull World world,
            @Nonnull Vector3i portalPos,
            @Nonnull RealmBiomeType biome,
            @Nonnull RealmLayoutSize size,
            @Nonnull List<Vector3i> outPositions) {

        String decoBlockId = biome.getDecorativeBlockId();
        if (decoBlockId == null) return;

        BlockType decoType = BlockType.getAssetMap().getAsset(decoBlockId);
        if (decoType == null || decoType.isUnknown()) {
            LOGGER.atWarning().log("Decorative block type not found: %s", decoBlockId);
            return;
        }

        int[][] offsets = switch (size) {
            case SMALL -> SMALL_OFFSETS;
            case MEDIUM -> MEDIUM_OFFSETS;
            case LARGE -> LARGE_OFFSETS;
            case MASSIVE -> MASSIVE_OFFSETS;
        };

        for (int[] offset : offsets) {
            int bx = portalPos.x + offset[0];
            int by = portalPos.y + offset[1];
            int bz = portalPos.z + offset[2];

            try {
                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(bx, bz));
                if (chunk == null) continue;

                chunk.setBlock(bx, by, bz, decoType.getId());
                outPositions.add(new Vector3i(bx, by, bz));
            } catch (Exception e) {
                LOGGER.atFine().withCause(e).log("Failed to place decorative block at (%d,%d,%d)", bx, by, bz);
            }
        }

        LOGGER.atFine().log("Placed %d decorative blocks around portal at %s", outPositions.size(), portalPos);
    }

    private void removeDecorativeBlocks(@Nonnull World world, @Nonnull List<Vector3i> positions) {
        BlockType airType = BlockType.getAssetMap().getAsset("hytale:air");
        if (airType == null) return;

        for (Vector3i pos : positions) {
            try {
                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
                if (chunk == null) continue;

                chunk.setBlock(pos.x, pos.y, pos.z, airType.getId());
            } catch (Exception e) {
                LOGGER.atFine().withCause(e).log("Failed to remove decorative block at %s", pos);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    private static String createKey(@Nonnull UUID worldUuid, @Nonnull Vector3i position) {
        return worldUuid + ":" + position.x + ":" + position.y + ":" + position.z;
    }

    private static Color parseHexColor(@Nonnull String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        int rgb = Integer.parseInt(clean, 16);
        return new Color((byte) ((rgb >> 16) & 0xFF), (byte) ((rgb >> 8) & 0xFF), (byte) (rgb & 0xFF));
    }

    /**
     * Checks if a portal at the given position has active visuals.
     */
    public boolean hasActiveVisuals(@Nonnull UUID worldUuid, @Nonnull Vector3i position) {
        return activePortals.containsKey(createKey(worldUuid, position));
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Shuts down the visual manager, stopping all particle ticking.
     * Called during plugin disable.
     */
    public void shutdown() {
        stopTicking();

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        activePortals.clear();
        LOGGER.atInfo().log("Portal visual manager shut down");
    }
}
