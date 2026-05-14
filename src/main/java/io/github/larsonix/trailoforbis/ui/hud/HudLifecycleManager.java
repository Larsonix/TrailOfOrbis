package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.utils.MultiHudWrapper;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.RealmsManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the full lifecycle of persistent HUDs across world transitions.
 *
 * <p>Persistent HUDs are always-visible overlays (XP bar, energy shield, etc.) that
 * must survive world transitions reliably. This manager centralizes the drain→restore
 * cycle into a single, well-tested path instead of scattering it across multiple files.
 *
 * <h2>Architecture</h2>
 * <pre>
 * DrainPlayerFromWorldEvent → discardAll() → each provider.discardStale()
 *                                                ↓
 *                                    [JoinWorld(clearWorld=true) clears client DOM]
 *                                                ↓
 * PlayerReadyEvent (LATE)   → restoreAll() → each provider.restore()
 * </pre>
 *
 * <h2>Why LATE Priority</h2>
 * <p>The EARLY-priority handler ({@code PlayerJoinListener.onPlayerReady}) loads player
 * data, calculates stats, and applies ECS modifiers. HUDs need this data to render
 * correctly (e.g., XP bar needs leveling data, shield bar needs computed stats).
 * By running at LATE, we guarantee initialization has completed.
 *
 * <h2>Fresh Ref Resolution</h2>
 * <p>Never uses captured PlayerRefs — they become invalid if the player teleports
 * between the scheduling tick and execution tick. Instead, resolves a fresh ref
 * from {@link Universe#get()}.getPlayer() at execution time and validates the player
 * is still in the expected world via store equality.
 *
 * <h2>Extending</h2>
 * <p>To add a new persistent HUD:
 * <ol>
 *   <li>Have the manager implement {@link PersistentHud}</li>
 *   <li>Call {@code hudLifecycleManager.register(manager)} in onEnable</li>
 *   <li>Done — drain, restore, disconnect, and shutdown are automatic</li>
 * </ol>
 *
 * @see PersistentHud
 */
public class HudLifecycleManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final List<PersistentHud> providers = new ArrayList<>();

    /** Periodic safety net — detects and restores missing HUDs. */
    @Nullable private HudHealthChecker healthChecker;

    /**
     * Tracks the last world name where HUDs were successfully restored per player.
     * Used to deduplicate the second {@code ClientReady} packet that Hytale sends
     * ~30 seconds after each world transition on remote connections. Without this,
     * every second ClientReady would trigger redundant discard+restore cycles.
     */
    private final Map<UUID, String> lastRestoredWorld = new ConcurrentHashMap<>();

    /**
     * MCHUD names of non-persistent HUDs that must be removed after world transitions.
     *
     * <p>Context-specific HUDs (realm combat, victory, defeat, sanctum skill points, etc.)
     * are discarded from our tracking maps during transitions but NOT from the server-side
     * {@code MultipleCustomUIHud.customHuds} HashMap (can't send packets during transitions).
     * Hytale rebuilds the MCHUD via {@code show()} after {@code JoinWorld(clearWorld=true)},
     * re-rendering orphaned entries — causing stale HUDs to reappear in the wrong world.
     *
     * <p>After restoring persistent HUDs, we remove these entries by their deterministic
     * names. {@code hideCustomHud()} is a safe no-op if the name doesn't exist. Context
     * handlers (realm, sanctum) re-add their HUDs via NORMAL-priority tasks that execute
     * BEFORE this LATE-priority cleanup on the world thread — FIFO ordering guarantees
     * new entries survive.
     */
    private static final String[] NON_PERSISTENT_HUD_NAMES = {
        "too-realm-combat",
        "too-realm-victory",
        "too-realm-defeat",
        "too-skill-points",
        "too-skill-node"
    };

    // ═══════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers a persistent HUD provider.
     *
     * <p>Call during plugin initialization (onEnable), before event listeners fire.
     *
     * @param hud The persistent HUD to manage
     */
    public void register(@Nonnull PersistentHud hud) {
        providers.add(hud);
        LOGGER.atInfo().log("Registered persistent HUD: %s", hud.hudName());
    }

    /**
     * Registers the PlayerReadyEvent listener for HUD restoration.
     *
     * <p>Must be called AFTER all providers are registered. Uses LATE priority
     * to ensure player initialization (stats, leveling) completes first.
     */
    public void registerEventListeners(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(
            EventPriority.LATE,
            PlayerReadyEvent.class,
            this::onPlayerReady
        );

        LOGGER.atInfo().log("HudLifecycleManager registered with %d providers", providers.size());
    }

    /**
     * Starts the periodic HUD health checker (safety net for disappeared HUDs).
     *
     * <p>Call AFTER all providers are registered and event listeners are set up.
     * The checker runs on a daemon thread, performs zero-cost map lookups to detect
     * missing HUDs, and defers actual restoration to the world thread.
     *
     * @param intervalSeconds seconds between health checks (e.g. 5)
     * @param realmsManager   the realms manager for realm combat HUD checks (nullable)
     */
    public void startHealthChecker(int intervalSeconds, @Nullable RealmsManager realmsManager) {
        var realmHudManager = realmsManager != null ? realmsManager.getHudManager() : null;
        healthChecker = new HudHealthChecker(providers, realmsManager, realmHudManager, 8);
        healthChecker.start(intervalSeconds);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DRAIN (called from DrainPlayerFromWorldEvent handler)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Discards all persistent HUDs for a player during a world transition.
     *
     * <p>Called from the central DrainPlayerFromWorldEvent handler. Removes from
     * tracking maps and cancels refresh tasks without sending packets.
     */
    public void discardAll(@Nonnull UUID playerId) {
        lastRestoredWorld.remove(playerId);
        for (PersistentHud hud : providers) {
            try {
                hud.discardStale(playerId);
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to discard %s for player %s: %s",
                    hud.hudName(), playerId.toString().substring(0, 8), e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESTORE (triggered by PlayerReadyEvent)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Restores all persistent HUDs that should be active for a player.
     *
     * <p>Resolves a fresh PlayerRef from Universe at call time (never stale captures).
     * Validates the player is still in the target world before creating HUDs.
     * Skips HUDs that are already active (idempotent). Each HUD has isolated
     * error handling — one failure doesn't prevent others from restoring.
     *
     * @param playerId The player's UUID
     * @param world    The world the player should be in
     * @param player   The Player component from the event — used for direct MultiHud registration
     */
    public void restoreAll(@Nonnull UUID playerId, @Nonnull World world,
                           @Nullable Player player) {
        if (!world.isAlive()) {
            return;
        }

        // Resolve fresh ref at execution time — immune to stale captures
        PlayerRef freshRef = Universe.get().getPlayer(playerId);
        if (freshRef == null) {
            return; // Player disconnected
        }

        // Use the world's store directly for HUD creation.
        // NOTE: Do NOT validate PlayerRef.getReference() here — it can return null
        // during early world transitions even though the player IS in the world.
        // The combat HUD (which always works) uses this same pattern: fresh PlayerRef
        // from Universe + world store, without entity ref validation.
        // Each provider handles its own entity ref needs internally.
        Store<EntityStore> worldStore = world.getEntityStore().getStore();

        for (PersistentHud hud : providers) {
            if (hud.isActive(playerId)) {
                continue;
            }

            try {
                hud.restore(playerId, freshRef, worldStore, player);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log(
                    "Failed to restore %s HUD for player %s",
                    hud.hudName(), playerId.toString().substring(0, 8));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISCONNECT (called from PlayerDisconnectEvent handler)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Removes all persistent HUDs on player disconnect.
     *
     * <p>Unlike {@link #discardAll}, this sends packets (hide + remove) since
     * the client is still connected.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        lastRestoredWorld.remove(playerId);
        if (healthChecker != null) healthChecker.onDisconnect(playerId);
        for (PersistentHud hud : providers) {
            try {
                hud.removeOnDisconnect(playerId);
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to remove %s on disconnect for player %s: %s",
                    hud.hudName(), playerId.toString().substring(0, 8), e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHUTDOWN (called from onDisable)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Removes all HUDs for all players during plugin shutdown.
     */
    public void shutdown() {
        if (healthChecker != null) {
            healthChecker.shutdown();
            healthChecker = null;
        }
        for (PersistentHud hud : providers) {
            try {
                hud.shutdown();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to shutdown HUD: %s", hud.hudName());
            }
        }
        LOGGER.atInfo().log("HudLifecycleManager shutdown complete");
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        UUID playerId = player.getUuid();
        if (world == null || playerId == null) {
            return;
        }

        final UUID pid = playerId;
        final World w = world;

        // Deferred to world.execute() because handleClientReady(true) — the 10-second
        // timeout fallback — runs on HytaleServer.SCHEDULED_EXECUTOR, not the world thread.
        // The normal path (ClientReady packet) already dispatches on the world thread,
        // making this a harmless same-thread re-post in the common case.
        //
        // The event's Player is passed directly — guaranteed valid at event time. This
        // bypasses HyUI's safeAdd() which resolves Player via getReference() (transiently
        // null during transitions). Each provider uses this Player to call
        // MultiHudWrapper.setCustomHud() directly for synchronous registration.
        //
        // Discard before restore: DrainPlayerFromWorldEvent only fires from
        // World.drainPlayersTo() (realm close/world shutdown). Normal portal teleports
        // go through TeleportSystems which bypasses the drain event entirely. Without
        // this discard, activeHuds retains stale entries from the old world, isActive()
        // returns true, and restoreAll skips all providers → invisible HUDs.
        world.execute(() -> {
            String worldName = w.getName();

            // Dedup: Hytale sends TWO ClientReady packets per world transition. The
            // second arrives ~30s later on remote connections. Without this guard, the
            // second event destroys all active HUDs and creates new ones with different
            // random names (HYUIHUD<uuid>). The old HUDs are orphaned inside
            // MultipleCustomUIHud — never removed, never updated, accumulating across
            // transitions. Skip if already restored in this world AND all HUDs are active.
            if (worldName != null && worldName.equals(lastRestoredWorld.get(pid))) {
                boolean allActive = true;
                for (PersistentHud hud : providers) {
                    if (!hud.isActive(pid)) {
                        allActive = false;
                        break;
                    }
                }
                if (allActive) {
                    LOGGER.atFine().log("Skipping redundant HUD restore for %s (already restored in %s)",
                        pid.toString().substring(0, 8), worldName);
                    return;
                }
            }

            discardAll(pid);
            restoreAll(pid, w, player);

            // Remove orphaned non-persistent HUDs from MCHUD. During transitions,
            // discardStale() removes from tracking but NOT from MCHUD (can't send
            // packets mid-transition). Hytale's show() rebuilds all MCHUD entries
            // after JoinWorld(clearWorld=true), re-rendering orphans. We remove
            // them here by deterministic name. This covers ALL transition paths
            // (portal, command, drain, death, realm close, sanctum exit — everything).
            // hideCustomHud() is a safe no-op if the name doesn't exist.
            removeOrphanedNonPersistentHuds(pid, player);

            // Notify health checker that event-driven path succeeded — sets cooldown
            if (healthChecker != null) healthChecker.notifyRestored(pid);

            // Track for dedup
            if (worldName != null) {
                lastRestoredWorld.put(pid, worldName);
            }

            // Verify all HUDs were created — diagnostic for remaining issues
            for (PersistentHud hud : providers) {
                if (!hud.isActive(pid)) {
                    LOGGER.atWarning().log("HUD '%s' NOT active for player %s after restoreAll",
                        hud.hudName(), pid.toString().substring(0, 8));
                }
            }
        });
    }

    /**
     * Removes orphaned non-persistent HUD entries from the MCHUD.
     *
     * <p>Must be called on the world thread AFTER {@link #restoreAll}. Uses the
     * deterministic HUD names to call {@code hideCustomHud()} which sends a
     * {@code Remove} command targeting the MCHUD's DOM group for each name.
     *
     * <p>Safe in all contexts:
     * <ul>
     *   <li>If the entry doesn't exist, {@code hideCustomHud} is a no-op</li>
     *   <li>If a context handler (realm, sanctum) just created the HUD at NORMAL
     *       priority, it uses a NESTED {@code world.execute()} so the actual creation
     *       fires AFTER this cleanup — the entry doesn't exist yet at cleanup time</li>
     *   <li>If the entry is genuinely orphaned (player left realm/sanctum), it's removed</li>
     * </ul>
     */
    private void removeOrphanedNonPersistentHuds(@Nonnull UUID playerId, @Nullable Player player) {
        if (player == null) return;

        PlayerRef freshRef = Universe.get().getPlayer(playerId);
        if (freshRef == null) return;

        for (String name : NON_PERSISTENT_HUD_NAMES) {
            try {
                MultiHudWrapper.hideCustomHud(player, freshRef, name);
            } catch (Exception ignored) {
                // Safe to ignore — entry may not exist or player may be mid-transition
            }
        }
    }
}
