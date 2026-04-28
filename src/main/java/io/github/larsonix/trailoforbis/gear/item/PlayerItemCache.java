package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.logger.HytaleLogger;

import io.github.larsonix.trailoforbis.gear.instance.GearInstanceId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player cache tracking which item definitions have been sent to each player.
 *
 * <p>Used to avoid redundant UpdateItems packets when a player already has
 * the current version of an item definition. Each entry stores a hash of the
 * definition content, allowing efficient change detection.
 *
 * <h2>Purpose</h2>
 * <ul>
 *   <li>Prevent sending duplicate item definitions</li>
 *   <li>Track when definitions need updating (e.g., player stats changed)</li>
 *   <li>Clean up player data on disconnect</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All operations are thread-safe using ConcurrentHashMap.
 *
 * <h2>Memory Management</h2>
 * <p>Call {@link #removePlayer(UUID)} when players disconnect to prevent memory leaks.
 */
public final class PlayerItemCache {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Maximum items per player before triggering cleanup warning.
     * This is a soft limit for logging purposes.
     */
    private static final int MAX_ITEMS_PER_PLAYER_WARN = 500;

    /**
     * Map of player UUID → (item instance ID → definition hash).
     */
    private final Map<UUID, Map<String, Integer>> playerCaches = new ConcurrentHashMap<>();

    // =========================================================================
    // PLAYER LIFECYCLE
    // =========================================================================

    /**
     * Initializes the cache for a player.
     *
     * <p>Should be called when a player connects.
     *
     * @param playerId The player's UUID
     */
    public void initPlayer(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        playerCaches.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        LOGGER.atFine().log("Initialized item cache for player %s", playerId);
    }

    /**
     * Removes all cached data for a player.
     *
     * <p>Should be called when a player disconnects to free memory.
     *
     * @param playerId The player's UUID
     */
    public void removePlayer(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Map<String, Integer> removed = playerCaches.remove(playerId);
        if (removed != null) {
            LOGGER.atFine().log(
                "Removed item cache for player %s (%d items)",
                playerId, removed.size()
            );
        }
    }

    /**
     * Checks if a player has an initialized cache.
     *
     * @param playerId The player's UUID
     * @return true if cache exists for this player
     */
    public boolean hasPlayer(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        return playerCaches.containsKey(playerId);
    }

    // =========================================================================
    // ITEM TRACKING
    // =========================================================================

    /**
     * Checks if a player needs an item definition update.
     *
     * <p>Returns true if:
     * <ul>
     *   <li>The player has never received this item definition</li>
     *   <li>The item definition has changed (different hash)</li>
     * </ul>
     *
     * @param playerId The player's UUID
     * @param instanceId The gear instance ID
     * @param definitionHash Hash of the current definition content
     * @return true if the player needs this definition sent
     */
    public boolean needsUpdate(
            @Nonnull UUID playerId,
            @Nonnull GearInstanceId instanceId,
            int definitionHash) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(instanceId, "instanceId cannot be null");

        Map<String, Integer> playerCache = playerCaches.get(playerId);
        if (playerCache == null) {
            // No cache = definitely needs update
            return true;
        }

        String itemId = instanceId.toItemId();
        Integer cachedHash = playerCache.get(itemId);

        // Needs update if not cached or hash changed
        return cachedHash == null || cachedHash != definitionHash;
    }

    /**
     * Convenience overload using item ID string directly.
     *
     * @param playerId The player's UUID
     * @param itemId The item ID string
     * @param definitionHash Hash of the current definition content
     * @return true if the player needs this definition sent
     */
    public boolean needsUpdate(
            @Nonnull UUID playerId,
            @Nonnull String itemId,
            int definitionHash) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");

        Map<String, Integer> playerCache = playerCaches.get(playerId);
        if (playerCache == null) {
            return true;
        }

        Integer cachedHash = playerCache.get(itemId);
        return cachedHash == null || cachedHash != definitionHash;
    }

    /**
     * Records that an item definition was sent to a player.
     *
     * @param playerId The player's UUID
     * @param instanceId The gear instance ID
     * @param definitionHash Hash of the sent definition
     */
    public void markSent(
            @Nonnull UUID playerId,
            @Nonnull GearInstanceId instanceId,
            int definitionHash) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(instanceId, "instanceId cannot be null");

        markSent(playerId, instanceId.toItemId(), definitionHash);
    }

    /**
     * Records that an item definition was sent to a player.
     *
     * @param playerId The player's UUID
     * @param itemId The item ID string
     * @param definitionHash Hash of the sent definition
     */
    public void markSent(
            @Nonnull UUID playerId,
            @Nonnull String itemId,
            int definitionHash) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");

        Map<String, Integer> playerCache = playerCaches.computeIfAbsent(
            playerId, k -> new ConcurrentHashMap<>()
        );

        playerCache.put(itemId, definitionHash);

        // Warn if cache is growing too large
        if (playerCache.size() == MAX_ITEMS_PER_PLAYER_WARN) {
            LOGGER.atWarning().log(
                "Player %s item cache reached %d items - consider cleanup",
                playerId, MAX_ITEMS_PER_PLAYER_WARN
            );
        }
    }

    /**
     * Removes a specific item from a player's cache.
     *
     * <p>Use when an item is destroyed or dropped.
     *
     * @param playerId The player's UUID
     * @param instanceId The gear instance ID to remove
     */
    public void removeItem(@Nonnull UUID playerId, @Nonnull GearInstanceId instanceId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(instanceId, "instanceId cannot be null");

        removeItem(playerId, instanceId.toItemId());
    }

    /**
     * Removes a specific item from a player's cache.
     *
     * @param playerId The player's UUID
     * @param itemId The item ID string to remove
     */
    public void removeItem(@Nonnull UUID playerId, @Nonnull String itemId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");

        Map<String, Integer> playerCache = playerCaches.get(playerId);
        if (playerCache != null) {
            playerCache.remove(itemId);
        }
    }

    // =========================================================================
    // BATCH OPERATIONS
    // =========================================================================

    /**
     * Clears all cached items for a player (but keeps the player entry).
     *
     * <p>Useful when player reconnects and needs full resync.
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerItems(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Map<String, Integer> playerCache = playerCaches.get(playerId);
        if (playerCache != null) {
            int count = playerCache.size();
            playerCache.clear();
            LOGGER.atFine().log("Cleared %d cached items for player %s", count, playerId);
        }
    }

    /**
     * Gets all item IDs currently cached for a player.
     *
     * @param playerId The player's UUID
     * @return Set of item IDs (may be empty, never null)
     */
    @Nonnull
    public Set<String> getCachedItemIds(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Map<String, Integer> playerCache = playerCaches.get(playerId);
        if (playerCache == null) {
            return Set.of();
        }
        return Set.copyOf(playerCache.keySet());
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    /**
     * Gets the number of tracked players.
     *
     * @return Number of players with caches
     */
    public int getPlayerCount() {
        return playerCaches.size();
    }

    /**
     * Gets the number of cached items for a player.
     *
     * @param playerId The player's UUID
     * @return Number of cached items, or 0 if player not found
     */
    public int getItemCount(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Map<String, Integer> playerCache = playerCaches.get(playerId);
        return playerCache != null ? playerCache.size() : 0;
    }

    /**
     * Gets total number of cached items across all players.
     *
     * @return Total item count
     */
    public int getTotalItemCount() {
        return playerCaches.values().stream()
            .mapToInt(Map::size)
            .sum();
    }

    /**
     * Gets the cached hash for a specific item.
     *
     * @param playerId The player's UUID
     * @param itemId The item ID
     * @return The cached hash, or null if not cached
     */
    @Nullable
    public Integer getCachedHash(@Nonnull UUID playerId, @Nonnull String itemId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");

        Map<String, Integer> playerCache = playerCaches.get(playerId);
        return playerCache != null ? playerCache.get(itemId) : null;
    }
}
