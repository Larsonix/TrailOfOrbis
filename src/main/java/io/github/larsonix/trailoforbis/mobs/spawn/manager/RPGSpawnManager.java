package io.github.larsonix.trailoforbis.mobs.spawn.manager;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationService;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.spawn.component.RPGSpawnedMarker;
import io.github.larsonix.trailoforbis.mobs.spawn.config.MobSpawnConfig;
import io.github.larsonix.trailoforbis.mobs.spawn.weight.ClassBasedWeightModifier;
import io.github.larsonix.trailoforbis.mobs.spawn.weight.LevelBasedWeightModifier;
import io.github.larsonix.trailoforbis.mobs.spawn.weight.SpawnWeightModifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Orchestrates mob spawn weight modifications and additional spawning.
 *
 * <p>This manager replaces the old {@code AdditionalMobSpawner} and
 * {@code SpawnMultiplierCalculator} with a more flexible weight modifier system.
 *
 * <p>Key improvements over the old system:
 * <ul>
 *   <li>Per-class spawn multipliers (not just "all hostile")</li>
 *   <li>Component-based loop prevention (not position TTL tracking)</li>
 *   <li>Modular weight modifiers (class-based, level-based, etc.)</li>
 *   <li>Probabilistic spawning for non-integer multipliers</li>
 * </ul>
 */
public class RPGSpawnManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Maximum size of the pending spawn queue before overflow protection kicks in.
     * This prevents memory exhaustion from runaway spawn multipliers.
     */
    private static final int MAX_QUEUE_SIZE = 50;

    /**
     * Hytale chunk size in blocks (16x16).
     */
    private static final int CHUNK_SIZE = 16;

    private final TrailOfOrbis plugin;
    private final MobSpawnConfig config;
    private final List<SpawnWeightModifier> modifiers;

    // Configuration for additional spawning
    private final double spawnOffsetRadius;
    private final double spawnOffsetMinDistance;

    /**
     * Queue of pending spawn requests to be processed outside of store processing.
     * This prevents "Store is currently processing!" errors when spawning from onEntityAdd.
     */
    private final Queue<PendingSpawn> pendingSpawns = new ConcurrentLinkedQueue<>();

    /**
     * Tracks spawn counts per chunk to enforce chunk-based caps.
     * Key: packed chunk coordinates (chunkX << 32 | chunkZ)
     * Value: count of RPG-spawned mobs in that chunk
     */
    private final Map<Long, AtomicInteger> spawnedPerChunk = new ConcurrentHashMap<>();

    private boolean initialized = false;

    /**
     * Represents a deferred spawn request.
     */
    private record PendingSpawn(
        int roleIndex,
        String roleName,
        Vector3d originalPosition,
        int count
    ) {}

    /**
     * Creates a new RPG spawn manager.
     *
     * @param plugin          The plugin instance
     * @param config          The mob spawn configuration
     * @param levelingService Service for getting player levels (nullable)
     */
    public RPGSpawnManager(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull MobSpawnConfig config,
            @Nullable LevelingService levelingService) {

        this.plugin = plugin;
        this.config = config;
        this.modifiers = new ArrayList<>();

        // Default spawn offset values (can be made configurable later)
        this.spawnOffsetRadius = 250.0;
        this.spawnOffsetMinDistance = 50.0;

        // Register weight modifiers in order
        modifiers.add(new ClassBasedWeightModifier(config));
        modifiers.add(new LevelBasedWeightModifier(config, levelingService));

        initialized = true;
        LOGGER.at(Level.INFO).log("RPGSpawnManager initialized with %d weight modifiers", modifiers.size());
    }

    /**
     * Calculates the combined spawn multiplier for a mob.
     *
     * <p>Chains all registered weight modifiers together, multiplying their results.
     *
     * @param npc           The NPC entity
     * @param classification The mob's RPG classification
     * @param position      The spawn position
     * @param store         The entity store
     * @return Combined spawn multiplier (1.0 = normal, >1 = more spawns)
     */
    public double calculateSpawnMultiplier(
            @Nonnull NPCEntity npc,
            @Nonnull RPGMobClass classification,
            @Nonnull Vector3d position,
            @Nonnull Store<EntityStore> store) {

        if (!config.isEnabled()) {
            return 1.0;
        }

        int roleIndex = npc.getRoleIndex();
        double combined = 1.0;

        for (SpawnWeightModifier modifier : modifiers) {
            double weight = modifier.getWeightMultiplier(roleIndex, classification, position, store);
            combined *= weight;
        }

        return combined;
    }

    /**
     * Determines the number of additional mobs to spawn based on the multiplier.
     *
     * <p>Uses probabilistic rounding for non-integer multipliers:
     * <ul>
     *   <li>Multiplier 2.0 → 1 additional spawn (100%)</li>
     *   <li>Multiplier 2.7 → 1 additional spawn (100%) + 70% chance of another</li>
     *   <li>Multiplier 1.3 → 30% chance of 1 additional spawn</li>
     * </ul>
     *
     * @param multiplier The spawn multiplier
     * @return Number of additional mobs to spawn (0 = no additional)
     */
    public int getAdditionalSpawnCount(double multiplier) {
        return getAdditionalSpawnCount(multiplier, ThreadLocalRandom.current());
    }

    /**
     * Determines the number of additional mobs to spawn with a specific random source.
     *
     * @param multiplier The spawn multiplier
     * @param random     Random number generator
     * @return Number of additional mobs to spawn
     */
    public int getAdditionalSpawnCount(double multiplier, @Nonnull ThreadLocalRandom random) {
        if (multiplier <= 1.0) {
            return 0;
        }

        // Guaranteed additional spawns (integer part minus 1)
        int guaranteed = (int) multiplier - 1;

        // Probabilistic additional spawn for fractional part
        double fractional = multiplier - (int) multiplier;
        int additional = random.nextDouble() < fractional ? 1 : 0;

        return guaranteed + additional;
    }

    /**
     * Queues additional mob spawns to be processed on the next tick.
     *
     * <p>This method is safe to call from within store processing (e.g., onEntityAdd)
     * because it only queues the spawn request. Actual spawning happens in
     * {@link #processPendingSpawns(Store)}.
     *
     * @param originalNpc      The original NPC that spawned
     * @param originalPosition Position of the original spawn
     * @param count            Number of additional mobs to spawn
     * @param store            The entity store (unused, kept for API compatibility)
     */
    public void spawnAdditional(
            @Nonnull NPCEntity originalNpc,
            @Nonnull Vector3d originalPosition,
            int count,
            @Nonnull Store<EntityStore> store) {

        if (count <= 0) {
            return;
        }

        // Queue overflow protection: prevent memory exhaustion from runaway multipliers
        if (pendingSpawns.size() >= MAX_QUEUE_SIZE) {
            LOGGER.at(Level.WARNING).log("[MobSpawn] Queue overflow: %d pending spawns, clearing queue to prevent crash",
                pendingSpawns.size());
            pendingSpawns.clear();
            return;
        }

        // Queue the spawn request instead of spawning immediately
        // This prevents "Store is currently processing!" errors
        pendingSpawns.add(new PendingSpawn(
            originalNpc.getRoleIndex(),
            originalNpc.getRoleName(),
            originalPosition,
            count
        ));

        if (plugin.getConfigManager().getRPGConfig().isDebugMode()) {
            LOGGER.at(Level.FINE).log("[MobSpawn] Queued %d additional %s spawns (queue size: %d)",
                count, originalNpc.getRoleName(), pendingSpawns.size());
        }
    }

    /**
     * Processes a single pending spawn from the queue.
     *
     * <p>This method processes one spawn at a time to spread the workload
     * across multiple ticks and prevent lag spikes from burst spawning.
     *
     * @param store The entity store to spawn into
     * @return true if a spawn was processed, false if queue was empty
     */
    public boolean processOneSpawn(@Nonnull Store<EntityStore> store) {
        PendingSpawn pending = pendingSpawns.poll();
        if (pending == null) {
            return false;
        }

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.at(Level.SEVERE).log("NPCPlugin not available for additional spawns");
            return false;
        }

        ComponentType<EntityStore, RPGSpawnedMarker> markerType = plugin.getRPGSpawnedMarkerType();
        if (markerType == null) {
            LOGGER.at(Level.SEVERE).log("RPGSpawnedMarker component type not registered");
            return false;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean debugMode = plugin.getConfigManager().getRPGConfig().isDebugMode();
        int spawned = 0;

        for (int i = 0; i < pending.count(); i++) {
            Vector3d spawnPos = calculateSpawnOffset(pending.originalPosition(), random);

            // Check chunk-based spawn cap
            if (!canSpawnInChunk(spawnPos)) {
                if (debugMode) {
                    LOGGER.at(Level.FINE).log("[MobSpawn] Chunk cap reached, skipping spawn at (%.0f, %.0f, %.0f)",
                        spawnPos.x, spawnPos.y, spawnPos.z);
                }
                continue;
            }

            try {
                float rotation = random.nextFloat() * 360f;
                Vector3f rotationVec = new Vector3f(0f, rotation, 0f);

                var result = npcPlugin.spawnEntity(
                    store,
                    pending.roleIndex(),
                    spawnPos,
                    rotationVec,
                    null,
                    (npc, holder, entityStore) -> {
                        if (holder != null) {
                            holder.addComponent(markerType, new RPGSpawnedMarker());
                        }
                    },
                    null
                );

                if (result != null) {
                    spawned++;
                    incrementChunkCount(spawnPos);
                    if (debugMode) {
                        LOGGER.at(Level.INFO).log("[MobSpawn] Spawned additional %s at (%.0f, %.0f, %.0f)",
                            pending.roleName(), spawnPos.x, spawnPos.y, spawnPos.z);
                    }
                }
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Failed to spawn additional %s", pending.roleName());
            }
        }

        if (debugMode && spawned > 0) {
            LOGGER.at(Level.INFO).log("[MobSpawn] Spawned %d/%d additional %s near (%.0f, %.0f, %.0f)",
                spawned, pending.count(), pending.roleName(),
                pending.originalPosition().x, pending.originalPosition().y, pending.originalPosition().z);
        }

        return true;
    }

    /**
     * Processes all pending spawn requests.
     *
     * <p>This method should be called from a ticking system OUTSIDE of store processing
     * (e.g., at the start of a tick before entities are processed).
     *
     * @param store The entity store to spawn into
     * @deprecated Use {@link #processOneSpawn(Store)} for rate-limited spawning.
     *             This method processes all spawns at once which can cause lag spikes.
     */
    @Deprecated
    public void processPendingSpawns(@Nonnull Store<EntityStore> store) {
        while (processOneSpawn(store)) {
            // Process all pending spawns (legacy behavior)
        }
    }

    /**
     * Checks if a spawn is allowed at the given position based on chunk caps.
     *
     * @param position The spawn position
     * @return true if spawn is allowed, false if chunk cap exceeded
     */
    private boolean canSpawnInChunk(@Nonnull Vector3d position) {
        int maxPerChunk = config.getSpawnCaps().getMaxHostilePerChunk();
        if (maxPerChunk <= 0) {
            // 0 means unlimited (disabled)
            return true;
        }

        long chunkKey = getChunkKey(position);
        AtomicInteger count = spawnedPerChunk.get(chunkKey);

        return count == null || count.get() < maxPerChunk;
    }

    /**
     * Increments the spawn count for the chunk containing the given position.
     *
     * @param position The spawn position
     */
    private void incrementChunkCount(@Nonnull Vector3d position) {
        int maxPerChunk = config.getSpawnCaps().getMaxHostilePerChunk();
        if (maxPerChunk <= 0) {
            // 0 means unlimited, no tracking needed
            return;
        }

        long chunkKey = getChunkKey(position);
        spawnedPerChunk.computeIfAbsent(chunkKey, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Gets a unique key for the chunk containing the given position.
     *
     * @param position World position
     * @return Packed chunk key (chunkX in high 32 bits, chunkZ in low 32 bits)
     */
    private long getChunkKey(@Nonnull Vector3d position) {
        int chunkX = (int) Math.floor(position.x / CHUNK_SIZE);
        int chunkZ = (int) Math.floor(position.z / CHUNK_SIZE);
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Clears the chunk spawn tracking data.
     *
     * <p>This should be called periodically (e.g., every few minutes) to prevent
     * memory buildup and allow respawning in previously capped chunks.
     */
    public void clearChunkTracking() {
        spawnedPerChunk.clear();
    }

    /**
     * Checks if there are pending spawns waiting to be processed.
     *
     * @return true if there are pending spawns
     */
    public boolean hasPendingSpawns() {
        return !pendingSpawns.isEmpty();
    }

    /**
     * Calculates a random spawn offset from the original position.
     *
     * @param origin Original spawn position
     * @param random Random number generator
     * @return Offset position within configured radius
     */
    private Vector3d calculateSpawnOffset(@Nonnull Vector3d origin, @Nonnull ThreadLocalRandom random) {
        // Random angle (0 to 2π)
        double angle = random.nextDouble() * Math.PI * 2;

        // Random distance between min and max
        double distance = spawnOffsetMinDistance + random.nextDouble() * (spawnOffsetRadius - spawnOffsetMinDistance);

        // Calculate horizontal offset
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;

        // Keep same Y level - the spawn system will adjust for ground
        return new Vector3d(
            origin.x + offsetX,
            origin.y,
            origin.z + offsetZ
        );
    }

    /**
     * Checks if the spawn system is enabled.
     *
     * @return true if enabled and initialized
     */
    public boolean isEnabled() {
        return initialized && config.isEnabled();
    }

    /**
     * Gets the spawn configuration.
     *
     * @return The mob spawn config
     */
    @Nonnull
    public MobSpawnConfig getConfig() {
        return config;
    }

    /**
     * Checks if a mob class is considered hostile for spawn multiplier purposes.
     *
     * @param mobClass The mob class to check
     * @return true if hostile
     */
    public static boolean isHostileClass(@Nonnull RPGMobClass mobClass) {
        return mobClass.isHostile();
    }
}
