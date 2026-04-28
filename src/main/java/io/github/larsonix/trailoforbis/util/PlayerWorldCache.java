package io.github.larsonix.trailoforbis.util;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized cache mapping player UUIDs to their current World.
 *
 * <p>Eliminates O(worlds x players) iteration when looking up which world
 * a player is in. Updated via {@link AddPlayerToWorldEvent} and
 * {@link DrainPlayerFromWorldEvent} listeners.
 *
 * <p>Thread-safe: backed by {@link ConcurrentHashMap} with event-driven updates.
 */
public final class PlayerWorldCache {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ConcurrentHashMap<UUID, World> CACHE = new ConcurrentHashMap<>();

    private PlayerWorldCache() {
        // Utility class
    }

    /** Call once during plugin {@code setup()}. */
    public static void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(
            EventPriority.FIRST,
            AddPlayerToWorldEvent.class,
            PlayerWorldCache::onPlayerAddedToWorld
        );
        eventRegistry.registerGlobal(
            EventPriority.FIRST,
            DrainPlayerFromWorldEvent.class,
            PlayerWorldCache::onPlayerDrainedFromWorld
        );
        LOGGER.atInfo().log("PlayerWorldCache registered");
    }

    /** @return empty if player not online */
    @Nonnull
    public static Optional<World> getPlayerWorld(@Nonnull UUID playerId) {
        return Optional.ofNullable(CACHE.get(playerId));
    }

    /** O(players in world), typically 1-10. */
    @Nullable
    public static PlayerRef findPlayerRef(@Nonnull UUID playerId, @Nonnull World world) {
        for (PlayerRef ref : world.getPlayerRefs()) {
            if (ref.getUuid().equals(playerId)) {
                return ref;
            }
        }
        return null;
    }

    /** Combines {@link #getPlayerWorld} + {@link #findPlayerRef(UUID, World)}. */
    @Nullable
    public static PlayerRef findPlayerRef(@Nonnull UUID playerId) {
        World world = CACHE.get(playerId);
        if (world == null) {
            return null;
        }
        return findPlayerRef(playerId, world);
    }

    public static int size() {
        return CACHE.size();
    }

    /**
     * Clears the cache. Call during plugin shutdown.
     */
    public static void shutdown() {
        CACHE.clear();
        LOGGER.atInfo().log("PlayerWorldCache cleared");
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    private static void onPlayerAddedToWorld(@Nonnull AddPlayerToWorldEvent event) {
        UUID playerId = extractUuid(event.getHolder());
        if (playerId == null) {
            return;
        }
        CACHE.put(playerId, event.getWorld());
        LOGGER.atFine().log("Cached player %s → world %s",
            playerId.toString().substring(0, 8), event.getWorld().getName());
    }

    private static void onPlayerDrainedFromWorld(@Nonnull DrainPlayerFromWorldEvent event) {
        UUID playerId = extractUuid(event.getHolder());
        if (playerId == null) {
            return;
        }
        CACHE.remove(playerId);
        LOGGER.atFine().log("Evicted player %s from cache", playerId.toString().substring(0, 8));
    }

    @Nullable
    private static UUID extractUuid(@Nonnull Holder<EntityStore> holder) {
        try {
            UUIDComponent uuidComponent = holder.getComponent(UUIDComponent.getComponentType());
            return uuidComponent != null ? uuidComponent.getUuid() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
