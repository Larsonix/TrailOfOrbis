package io.github.larsonix.trailoforbis.maps.reward;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplateRegistry;
import io.github.larsonix.trailoforbis.util.TerrainUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central coordinator for realm reward chests.
 *
 * <p>When a realm is completed, instead of dumping items directly into player
 * inventories, this manager stores per-player rewards and spawns a physical
 * chest block near the victory portal. Each player sees their own items inside
 * (per-player instanced loot).
 *
 * <p>All state is in-memory only (no persistence) since realms are temporary.
 * When a realm closes, unclaimed rewards are delivered directly to inventory
 * as a fallback.
 *
 * <h2>L4E Compatibility</h2>
 * <p>When Loot4Everyone is detected at runtime, this manager defers chest
 * handling to L4E's proven per-player system instead of using its own
 * interceptor and monitor.
 *
 * @see RewardChestInterceptor
 * @see RewardChestMonitor
 */
public class RewardChestManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Primary chest block type — a large epic dungeon chest with 36 slots. */
    private static final String CHEST_BLOCK_TYPE = "Furniture_Dungeon_Chest_Epic_Large";

    /** Fallback chest block type if the primary isn't found. */
    private static final String FALLBACK_CHEST_BLOCK_TYPE = "Furniture_Dungeon_Chest_Epic";

    /** Offset from the victory portal (at 0,Y,0) to the chest position. */
    private static final int CHEST_OFFSET_X = 3;
    private static final int CHEST_OFFSET_Z = 0;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Per-realm chest placement info. */
    private final Map<UUID, RewardChestInfo> chestsByRealm = new ConcurrentHashMap<>();

    /** Per-realm per-player reward items. */
    private final Map<UUID, Map<UUID, List<ItemStack>>> rewardsByRealm = new ConcurrentHashMap<>();

    /** Tracks which players currently have the reward chest open (for monitoring). */
    private final Map<UUID, RewardChestInfo> openChestByPlayer = new ConcurrentHashMap<>();

    /** Cached chest block type. */
    @Nullable
    private volatile BlockType chestBlockType;

    /** Whether L4E is present (checked once on first use). */
    @Nullable
    private Boolean loot4EveryonePresent;

    /** L4E bridge for per-player chest integration (null if L4E not present). */
    @Nullable
    private volatile io.github.larsonix.trailoforbis.compat.Loot4EveryoneBridge l4eBridge;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new RewardChestManager.
     *
     * <p>Reward delivery happens exclusively through the physical reward chest.
     * If a realm closes before the player loots the chest, unclaimed items are
     * discarded — the chest IS the reward mechanism.
     */
    public RewardChestManager() {
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Stores pre-generated rewards for a player in a realm.
     *
     * <p>Call this during reward distribution, before the chest is spawned.
     * Items are held in memory until the player opens the chest.
     *
     * @param realmId   The realm ID
     * @param playerId  The player UUID
     * @param rewards   The generated rewards
     */
    public void storeRewards(
            @Nonnull UUID realmId,
            @Nonnull UUID playerId,
            @Nonnull VictoryRewardGenerator.VictoryRewards rewards) {
        Objects.requireNonNull(realmId, "realmId cannot be null");
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(rewards, "rewards cannot be null");

        if (!rewards.hasRewards()) {
            LOGGER.atFine().log("No rewards to store for player %s in realm %s",
                playerId.toString().substring(0, 8),
                realmId.toString().substring(0, 8));
            return;
        }

        List<ItemStack> items = rewards.allItems();
        rewardsByRealm
            .computeIfAbsent(realmId, k -> new ConcurrentHashMap<>())
            .put(playerId, new ArrayList<>(items));

        LOGGER.atInfo().log("Stored %d reward items for player %s in realm %s",
            items.size(),
            playerId.toString().substring(0, 8),
            realmId.toString().substring(0, 8));
    }

    /**
     * Spawns a reward chest block near the victory portal.
     *
     * <p>The chest is placed 3 blocks to the right of the portal (which is at 0,Y,0).
     * Must be called after the portal is spawned so we can find ground level.
     *
     * @param realm The completed realm instance
     * @return A future that completes with true if the chest was placed
     */
    @Nonnull
    public CompletableFuture<Boolean> spawnRewardChest(@Nonnull RealmInstance realm) {
        Objects.requireNonNull(realm, "realm cannot be null");

        World realmWorld = realm.getWorld();
        if (realmWorld == null || !realmWorld.isAlive()) {
            LOGGER.atWarning().log("Cannot spawn reward chest - realm world not available");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        realmWorld.execute(() -> {
            try {
                boolean success = spawnChestInternal(realm, realmWorld);
                future.complete(success);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to spawn reward chest for realm %s",
                    realm.getRealmId().toString().substring(0, 8));
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Checks if a block position is a reward chest.
     *
     * @param x       Block X coordinate
     * @param y       Block Y coordinate
     * @param z       Block Z coordinate
     * @param worldId The world UUID
     * @return true if this position is a reward chest
     */
    public boolean isRewardChest(int x, int y, int z, @Nonnull UUID worldId) {
        for (RewardChestInfo info : chestsByRealm.values()) {
            if (info.worldId().equals(worldId)
                    && info.position().x == x
                    && info.position().y == y
                    && info.position().z == z) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the realm ID that owns a reward chest at the given position.
     *
     * @param x       Block X coordinate
     * @param y       Block Y coordinate
     * @param z       Block Z coordinate
     * @param worldId The world UUID
     * @return The realm ID, or null if not a reward chest
     */
    @Nullable
    public UUID findRealmIdForChest(int x, int y, int z, @Nonnull UUID worldId) {
        for (Map.Entry<UUID, RewardChestInfo> entry : chestsByRealm.entrySet()) {
            RewardChestInfo info = entry.getValue();
            if (info.worldId().equals(worldId)
                    && info.position().x == x
                    && info.position().y == y
                    && info.position().z == z) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Gets the per-player reward items for a specific realm and player.
     *
     * @param realmId  The realm ID
     * @param playerId The player UUID
     * @return The reward items, or an empty list if none
     */
    @Nonnull
    public List<ItemStack> getPlayerRewards(@Nonnull UUID realmId, @Nonnull UUID playerId) {
        Map<UUID, List<ItemStack>> realmRewards = rewardsByRealm.get(realmId);
        if (realmRewards == null) {
            return List.of();
        }
        List<ItemStack> items = realmRewards.get(playerId);
        return items != null ? items : List.of();
    }

    /**
     * Saves the current chest contents back as the player's rewards.
     *
     * <p>Called when a player closes the chest. Items they took are no longer
     * in the list; items they left remain for the next opening.
     *
     * @param realmId  The realm ID
     * @param playerId The player UUID
     * @param items    The remaining items in the chest
     */
    public void savePlayerState(
            @Nonnull UUID realmId,
            @Nonnull UUID playerId,
            @Nonnull List<ItemStack> items) {

        Map<UUID, List<ItemStack>> realmRewards = rewardsByRealm.get(realmId);
        if (realmRewards == null) {
            return;
        }

        // Filter out null/empty items
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.isEmpty()) {
                remaining.add(item);
            }
        }

        if (remaining.isEmpty()) {
            realmRewards.remove(playerId);
            LOGGER.atFine().log("Player %s claimed all rewards from realm %s",
                playerId.toString().substring(0, 8),
                realmId.toString().substring(0, 8));
        } else {
            realmRewards.put(playerId, remaining);
            LOGGER.atFine().log("Player %s has %d remaining rewards in realm %s",
                playerId.toString().substring(0, 8),
                remaining.size(),
                realmId.toString().substring(0, 8));
        }
    }

    /**
     * Marks a player as having the reward chest open.
     *
     * @param playerId The player UUID
     * @param info     The chest info
     */
    public void markChestOpen(@Nonnull UUID playerId, @Nonnull RewardChestInfo info) {
        openChestByPlayer.put(playerId, info);
    }

    /**
     * Marks a player as having closed the reward chest.
     *
     * @param playerId The player UUID
     */
    public void markChestClosed(@Nonnull UUID playerId) {
        openChestByPlayer.remove(playerId);
    }

    /**
     * Gets the chest info for a player's currently open chest.
     *
     * @param playerId The player UUID
     * @return The chest info, or null if no chest is open
     */
    @Nullable
    public RewardChestInfo getOpenChest(@Nonnull UUID playerId) {
        return openChestByPlayer.get(playerId);
    }

    /**
     * Gets the set of players who currently have a chest open.
     *
     * @return Unmodifiable set of player UUIDs
     */
    @Nonnull
    public Set<UUID> getPlayersWithOpenChests() {
        return Collections.unmodifiableSet(openChestByPlayer.keySet());
    }

    /**
     * Gets the chest info for a realm.
     *
     * @param realmId The realm ID
     * @return The chest info, or null if no chest exists
     */
    @Nullable
    public RewardChestInfo getChestInfo(@Nonnull UUID realmId) {
        return chestsByRealm.get(realmId);
    }

    /**
     * Cleans up all state for a realm.
     *
     * <p>Called when a realm closes. Any rewards not yet claimed from the
     * physical chest are discarded — the chest is the reward mechanism.
     *
     * @param realmId The realm ID
     */
    public void cleanup(@Nonnull UUID realmId) {
        Objects.requireNonNull(realmId, "realmId cannot be null");

        // Discard unclaimed rewards — player had the chest, chose not to loot
        Map<UUID, List<ItemStack>> realmRewards = rewardsByRealm.remove(realmId);
        if (realmRewards != null && !realmRewards.isEmpty()) {
            int totalItems = realmRewards.values().stream().mapToInt(List::size).sum();
            LOGGER.atInfo().log("Discarding %d unclaimed reward items from %d players for realm %s (chest not looted)",
                totalItems, realmRewards.size(), realmId.toString().substring(0, 8));
        }

        // Remove chest tracking
        RewardChestInfo chestInfo = chestsByRealm.remove(realmId);

        // Clean up L4E data (template + per-player loot) before removing the chest
        if (chestInfo != null && isLoot4EveryonePresent() && l4eBridge != null) {
            World world = Universe.get().getWorld(chestInfo.worldId());
            if (world != null && world.isAlive()) {
                world.execute(() -> cleanupL4EData(world, realmId, chestInfo));
            }
        }

        // Clean up open chest tracking for any player who had this chest open
        openChestByPlayer.entrySet().removeIf(entry -> {
            RewardChestInfo info = entry.getValue();
            return info.realmId().equals(realmId);
        });

        // Remove physical chest block
        if (chestInfo != null) {
            removeChestBlock(chestInfo);
        }

        LOGGER.atInfo().log("Cleaned up reward chest for realm %s",
            realmId.toString().substring(0, 8));
    }

    /**
     * Checks if Loot4Everyone is present at runtime.
     *
     * @return true if L4E is detected
     */
    public boolean isLoot4EveryonePresent() {
        if (loot4EveryonePresent == null) {
            try {
                Class.forName("org.mimstar.plugin.Loot4Everyone");
                l4eBridge = new io.github.larsonix.trailoforbis.compat.Loot4EveryoneBridge();
                loot4EveryonePresent = true;
                LOGGER.atInfo().log("Loot4Everyone detected — reward chests will use L4E per-player instancing");
            } catch (ClassNotFoundException e) {
                loot4EveryonePresent = false;
                LOGGER.atInfo().log("Loot4Everyone not detected — using standalone reward chest system");
            } catch (ReflectiveOperationException e) {
                loot4EveryonePresent = false;
                LOGGER.atWarning().log("Loot4Everyone found but bridge init failed: %s", e.getMessage());
            }
        }
        return loot4EveryonePresent;
    }

    /**
     * Gets the L4E bridge (only available when L4E is present and bridge initialized).
     */
    @Nullable
    public io.github.larsonix.trailoforbis.compat.Loot4EveryoneBridge getLoot4EveryoneBridge() {
        return l4eBridge;
    }

    /**
     * Shuts down the manager, cleaning up all state.
     */
    public void shutdown() {
        int realmCount = rewardsByRealm.size();
        if (realmCount > 0) {
            int totalItems = rewardsByRealm.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToInt(List::size)
                .sum();
            LOGGER.atInfo().log("Discarding %d unclaimed reward items across %d realms on shutdown",
                totalItems, realmCount);
        }

        rewardsByRealm.clear();
        chestsByRealm.clear();
        openChestByPlayer.clear();

        LOGGER.atInfo().log("RewardChestManager shutdown complete");
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Spawns the chest block (must be called on world thread).
     */
    private boolean spawnChestInternal(
            @Nonnull RealmInstance realm,
            @Nonnull World realmWorld) {

        BlockType blockType = resolveChestBlockType();
        if (blockType == null) {
            LOGGER.atWarning().log("Cannot spawn reward chest - no chest block type available");
            return false;
        }

        // Find ground level at offset from arena center using biome-specific terrain
        // materials. The generic prefix-based check (Soil_*/Rock_*) matches decorative
        // props like Rock_Stone_Mossy, Soil_Leaves, Rock_Crystal_* that WorldGen places
        // above terrain, causing the chest to float. The biome whitelist only matches
        // actual terrain blocks (e.g., Forest = Soil_Grass_Full, Soil_Dirt, Rock_Stone).
        int maxScanY = (int) RealmTemplateRegistry.getBaseYForBiome(realm.getBiome()) + 30;
        Set<String> terrainMaterials = realm.getBiome().getTerrainMaterials();
        int groundY = TerrainUtils.findGroundLevel(realmWorld, CHEST_OFFSET_X, CHEST_OFFSET_Z,
            maxScanY, terrainMaterials);
        Vector3i position = new Vector3i(CHEST_OFFSET_X, groundY, CHEST_OFFSET_Z);

        // Ensure chunk is loaded
        WorldChunk chunk = realmWorld.getChunkIfInMemory(
            ChunkUtil.indexChunkFromBlock(position.x, position.z));
        if (chunk == null) {
            LOGGER.atWarning().log("Cannot spawn reward chest - chunk not loaded at %s", position);
            return false;
        }

        // Place the chest block
        try {
            chunk.setBlock(position.x, position.y, position.z, blockType.getId());
            LOGGER.atFine().log("Placed reward chest block: %s at %s", blockType.getId(), position);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to place reward chest block at %s", position);
            return false;
        }

        // Track the chest
        UUID realmId = realm.getRealmId();
        UUID worldId = realmWorld.getWorldConfig().getUuid();
        RewardChestInfo info = new RewardChestInfo(realmId, position, worldId);
        chestsByRealm.put(realmId, info);

        LOGGER.atInfo().log("Spawned reward chest for realm %s at %s",
            realmId.toString().substring(0, 8), position);

        // When L4E is present, register the chest as a template and
        // pre-populate each player's per-player loot
        if (isLoot4EveryonePresent() && l4eBridge != null) {
            populateL4ERewards(realmWorld, realmId, position);
        }

        return true;
    }

    /**
     * Populates L4E with per-player rewards for a freshly spawned chest.
     *
     * <p>Registers the chest as a L4E template (so L4E recognizes it as a loot chest),
     * then pre-sets each player's inventory so they see their own rewards when opening.
     *
     * <p>MUST be called on the world thread (inside spawnChestInternal).
     */
    private void populateL4ERewards(@Nonnull World world, @Nonnull UUID realmId,
                                      @Nonnull Vector3i pos) {
        Map<UUID, List<ItemStack>> playerRewards = rewardsByRealm.get(realmId);
        if (playerRewards == null || playerRewards.isEmpty()) {
            LOGGER.atFine().log("No rewards to populate for L4E in realm %s",
                realmId.toString().substring(0, 8));
            return;
        }

        // Register the chest as a L4E template (empty items, "custom" droplist)
        // This tells L4E to treat this position as a managed loot chest
        l4eBridge.registerTemplate(world, pos.x, pos.y, pos.z, List.of());

        // Determine chest capacity (default 36 for large epic chest)
        int capacity = 36;

        // Pre-set each player's rewards in L4E's PlayerLoot
        int populated = 0;
        for (Map.Entry<UUID, List<ItemStack>> entry : playerRewards.entrySet()) {
            UUID playerId = entry.getKey();
            List<ItemStack> items = entry.getValue();
            if (items.isEmpty()) continue;

            if (l4eBridge.presetPlayerLoot(world, playerId, pos.x, pos.y, pos.z, items, capacity)) {
                populated++;
            }
        }

        LOGGER.atInfo().log("L4E: Populated %d/%d player rewards for realm %s at %s",
            populated, playerRewards.size(),
            realmId.toString().substring(0, 8), pos);
    }

    /**
     * Cleans up L4E data for a realm chest (template + per-player loot entries).
     *
     * <p>Called on the world thread during realm cleanup to prevent data accumulation
     * from temporary realm worlds.
     */
    private void cleanupL4EData(@Nonnull World world, @Nonnull UUID realmId,
                                  @Nonnull RewardChestInfo chestInfo) {
        Vector3i pos = chestInfo.position();

        // Remove the template registration
        l4eBridge.removeTemplate(world, pos.x, pos.y, pos.z);

        // Reset per-player loot entries for this chest
        Map<UUID, List<ItemStack>> playerRewards = rewardsByRealm.get(realmId);
        if (playerRewards != null) {
            for (UUID playerId : playerRewards.keySet()) {
                l4eBridge.resetPlayerLoot(world, playerId, pos.x, pos.y, pos.z);
            }
        }

        LOGGER.atFine().log("L4E: Cleaned up data for realm %s chest at %s",
            realmId.toString().substring(0, 8), pos);
    }

    /**
     * Resolves the chest block type, trying primary then fallback.
     */
    @Nullable
    private BlockType resolveChestBlockType() {
        if (chestBlockType != null) {
            return chestBlockType;
        }

        BlockType blockType = BlockType.getAssetMap().getAsset(CHEST_BLOCK_TYPE);
        if (blockType != null && !blockType.isUnknown()) {
            chestBlockType = blockType;
            LOGGER.atFine().log("Resolved chest block type: %s", CHEST_BLOCK_TYPE);
            return chestBlockType;
        }
        LOGGER.atWarning().log("Could not find chest block type '%s', trying fallback", CHEST_BLOCK_TYPE);

        blockType = BlockType.getAssetMap().getAsset(FALLBACK_CHEST_BLOCK_TYPE);
        if (blockType != null && !blockType.isUnknown()) {
            chestBlockType = blockType;
            LOGGER.atWarning().log("Using fallback chest block type: %s", FALLBACK_CHEST_BLOCK_TYPE);
            return chestBlockType;
        }

        LOGGER.atSevere().log("Failed to resolve any chest block type!");
        return null;
    }

    /**
     * Removes a chest block from the world.
     */
    private void removeChestBlock(@Nonnull RewardChestInfo info) {
        World world = Universe.get().getWorld(info.worldId());
        if (world == null || !world.isAlive()) {
            return;
        }

        Vector3i pos = info.position();
        world.execute(() -> {
            try {
                WorldChunk chunk = world.getChunkIfInMemory(
                    ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
                if (chunk == null) {
                    return;
                }

                BlockType airType = BlockType.getAssetMap().getAsset("hytale:air");
                if (airType != null) {
                    chunk.setBlock(pos.x, pos.y, pos.z, airType.getId());
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error removing reward chest block at %s", pos);
            }
        });
    }


    // ═══════════════════════════════════════════════════════════════════
    // NESTED TYPES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Information about a spawned reward chest.
     */
    public record RewardChestInfo(
        @Nonnull UUID realmId,
        @Nonnull Vector3i position,
        @Nonnull UUID worldId
    ) {
        public RewardChestInfo {
            Objects.requireNonNull(realmId);
            Objects.requireNonNull(position);
            Objects.requireNonNull(worldId);
        }
    }
}
