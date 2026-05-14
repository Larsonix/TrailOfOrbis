package io.github.larsonix.trailoforbis.maps.ui;

import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.utils.MultiHudWrapper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import io.github.larsonix.trailoforbis.ui.hud.HudRefreshHelper;
import io.github.larsonix.trailoforbis.ui.hud.HudToggleService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages realm HUDs for players.
 *
 * <p>Each player has at most one active HUD at a time (combat, victory, or defeat).
 * HUD lifecycle follows a simple pattern:
 * <ul>
 *   <li>Combat HUD shown on realm entry</li>
 *   <li>Combat HUD replaced by Victory HUD on realm completion</li>
 *   <li>Combat HUD replaced by Defeat HUD on realm timeout</li>
 *   <li>HUDs removed when player leaves world (via DrainPlayerFromWorldEvent)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>HUD removal MUST happen on the world thread where the HUD was created.
 * All removal is done synchronously via {@link #removeAllHudsForPlayerSync(UUID)},
 * called from the DrainPlayerFromWorldEvent handler which fires on the world thread
 * BEFORE the player leaves.
 *
 * <h2>Full Rerender Strategy</h2>
 * <p>Combat HUD refresh uses {@link HudRefreshHelper} which atomically Clears +
 * Appends instead of the default diff-based {@code Set} commands. The diff approach
 * references auto-generated {@code #HYUUIDGroupNNN} element IDs that can become
 * stale and crash the client. See {@link HudRefreshHelper} for details.
 *
 * @see RealmCombatHud
 * @see RealmVictoryHud
 * @see RealmDefeatHud
 */
public class RealmHudManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Active combat HUDs per player UUID. */
    private final Map<UUID, HyUIHud> combatHuds = new ConcurrentHashMap<>();

    /** Active victory HUDs per player UUID. */
    private final Map<UUID, HyUIHud> victoryHuds = new ConcurrentHashMap<>();

    /** Active defeat HUDs per player UUID. */
    private final Map<UUID, HyUIHud> defeatHuds = new ConcurrentHashMap<>();

    @Nullable private HudToggleService hudToggleService;

    public void setHudToggleService(@Nullable HudToggleService service) {
        this.hudToggleService = service;
    }

    /**
     * Applies toggle state (hide/unhide) to all active HUDs for a player.
     * Called by the {@code /hud} command.
     */
    public void applyToggle(@Nonnull UUID playerId, boolean hide) {
        applyToHud(combatHuds.get(playerId), hide);
        applyToHud(victoryHuds.get(playerId), hide);
        applyToHud(defeatHuds.get(playerId), hide);
    }

    private void applyToHud(@Nullable HyUIHud hud, boolean hide) {
        if (hud == null) return;
        HudRefreshHelper.safeSetVisibility(hud, !hide);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMBAT HUD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Shows the combat HUD for a player entering a realm.
     *
     * <p><b>IMPORTANT:</b> HUD creation is deferred using {@code world.execute()} to ensure
     * the player is fully in the realm world before accessing their PlayerRef. Immediate
     * access after teleport results in invalid refs because the player entity hasn't
     * finished transferring to the new world's store.
     *
     * @param playerId The player's UUID
     * @param player   The player reference (may be stale, used as fallback)
     * @param realm    The realm instance
     */
    public void showCombatHud(@Nonnull UUID playerId, @Nonnull PlayerRef player, @Nonnull RealmInstance realm) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(realm, "realm cannot be null");

        // Discard any existing HUDs — NOT hide()+remove() which sends Set commands
        // to elements that may have been cleared by resetManagers() during world transition.
        discardHud(playerId, combatHuds, "combat");
        discardHud(playerId, victoryHuds, "victory");
        discardHud(playerId, defeatHuds, "defeat");

        World world = realm.getWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot show combat HUD - realm world not available");
            return;
        }

        // Defer HUD creation to next tick - player may not be fully in world yet
        world.execute(() -> {
            PlayerRef freshPlayerRef = Universe.get().getPlayer(playerId);
            if (freshPlayerRef == null) {
                LOGGER.atWarning().log("Cannot show combat HUD - player %s not found after deferral",
                    playerId.toString().substring(0, 8));
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();

            try {
                HyUIHud hud = RealmCombatHud.create(freshPlayerRef, realm, store);

                // Direct MultiHud registration — bypasses safeAdd()'s getReference()
                // null check that can fail during realm transitions.
                // resetHasBuilt() neutralizes the deferred safeAdd() from .show().
                Ref<EntityStore> entityRef = freshPlayerRef.getReference();
                if (entityRef != null && entityRef.isValid()) {
                    Player resolvedPlayer = store.getComponent(entityRef, Player.getComponentType());
                    if (resolvedPlayer != null) {
                        MultiHudWrapper.setCustomHud(resolvedPlayer, freshPlayerRef, hud.name, hud);
                        HudRefreshHelper.resetHasBuilt(hud);
                    }
                }

                combatHuds.put(playerId, hud);
                if (hudToggleService != null) hudToggleService.applyToggleState(playerId, hud);
                LOGGER.atInfo().log("Showed combat HUD for player %s in realm %s",
                    playerId.toString().substring(0, 8),
                    realm.getRealmId().toString().substring(0, 8));
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to show combat HUD for player %s",
                    playerId.toString().substring(0, 8));
            }
        });
    }

    /**
     * Shows the combat HUD synchronously on the world thread (no nested world.execute).
     *
     * <p>Used by {@link io.github.larsonix.trailoforbis.ui.hud.HudHealthChecker} when
     * the caller is already on the realm world thread and the player is fully loaded
     * (8-13s after transition). Uses direct {@code MultiHudWrapper.setCustomHud()} +
     * {@code resetHasBuilt()} to guarantee synchronous registration.
     */
    public void showCombatHudDirect(
            @Nonnull UUID playerId,
            @Nonnull PlayerRef playerRef,
            @Nonnull Player player,
            @Nonnull RealmInstance realm) {

        discardHud(playerId, combatHuds, "combat");
        discardHud(playerId, victoryHuds, "victory");
        discardHud(playerId, defeatHuds, "defeat");

        World world = realm.getWorld();
        if (world == null || !world.isAlive()) return;

        Store<EntityStore> store = world.getEntityStore().getStore();

        try {
            HyUIHud hud = RealmCombatHud.create(playerRef, realm, store);
            MultiHudWrapper.setCustomHud(player, playerRef, hud.name, hud);
            HudRefreshHelper.resetHasBuilt(hud);
            combatHuds.put(playerId, hud);
            if (hudToggleService != null) hudToggleService.applyToggleState(playerId, hud);
            LOGGER.atInfo().log("Showed combat HUD (direct) for player %s in realm %s",
                playerId.toString().substring(0, 8),
                realm.getRealmId().toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to show combat HUD (direct) for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // VICTORY HUD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Replaces the combat HUD with a victory HUD for a player.
     *
     * <p>Explicitly removes the combat HUD before showing the victory HUD.
     *
     * @param playerId The player's UUID
     * @param player   The player reference
     * @param realm    The realm instance
     */
    public void showVictoryHud(
            @Nonnull UUID playerId,
            @Nonnull PlayerRef player,
            @Nonnull RealmInstance realm) {

        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(realm, "realm cannot be null");

        // Remove combat HUD using hide()+remove() — player is still in the realm
        // world (not transitioning), so getStore() is valid and packets are safe.
        // discardHud() would only cancel the refresh task without removing from client.
        removeHudSync(playerId, combatHuds, "combat");

        World world = realm.getWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot show victory HUD - realm world not available");
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        try {
            HyUIHud hud = RealmVictoryHud.create(player, realm, store);
            victoryHuds.put(playerId, hud);
            if (hudToggleService != null) hudToggleService.applyToggleState(playerId, hud);
            LOGGER.atFine().log("Showed victory HUD for player %s in realm %s",
                playerId.toString().substring(0, 8),
                realm.getRealmId().toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to show victory HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEFEAT HUD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Replaces the combat HUD with a defeat HUD for a player.
     *
     * <p>Called when the realm timer expires. The defeat HUD shows stats
     * and a "teleporting out" message. The player is teleported out
     * after 10 seconds by {@code RealmTimerSystem}.
     *
     * @param playerId The player's UUID
     * @param player   The player reference
     * @param realm    The realm instance
     */
    public void showDefeatHud(
            @Nonnull UUID playerId,
            @Nonnull PlayerRef player,
            @Nonnull RealmInstance realm) {

        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(realm, "realm cannot be null");

        // Remove combat HUD using hide()+remove() — same-world operation (not transitioning)
        removeHudSync(playerId, combatHuds, "combat");

        World world = realm.getWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot show defeat HUD - realm world not available");
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        try {
            HyUIHud hud = RealmDefeatHud.create(player, realm, store);
            defeatHuds.put(playerId, hud);
            if (hudToggleService != null) hudToggleService.applyToggleState(playerId, hud);
            LOGGER.atFine().log("Showed defeat HUD for player %s in realm %s",
                playerId.toString().substring(0, 8),
                realm.getRealmId().toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to show defeat HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYNCHRONOUS REMOVAL (caller MUST be on world thread)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Removes all HUDs for a player synchronously using hide + remove.
     *
     * <p><b>WARNING: Do NOT use during world transitions!</b> Use
     * {@link #discardAllHudsForPlayer(UUID)} instead. During world transitions,
     * {@code Player.resetManagers()} has already sent {@code CustomHud(clear=true)}
     * to the client. Calling {@code hide()} sends {@code Set} commands referencing
     * cleared elements, crashing the client.
     *
     * <p><b>CRITICAL: Caller MUST be on the realm world thread!</b>
     *
     * @param playerId The player's UUID
     */
    public void removeAllHudsForPlayerSync(@Nonnull UUID playerId) {
        LOGGER.atFine().log("removeAllHudsForPlayerSync: player=%s, hasCombat=%s, hasVictory=%s, hasDefeat=%s",
            playerId.toString().substring(0, 8),
            combatHuds.containsKey(playerId),
            victoryHuds.containsKey(playerId),
            defeatHuds.containsKey(playerId));

        removeHudSync(playerId, combatHuds, "combat");
        removeHudSync(playerId, victoryHuds, "victory");
        removeHudSync(playerId, defeatHuds, "defeat");
    }

    /**
     * Discards all stale HUDs for a player during world transitions WITHOUT
     * sending packets to the client.
     *
     * <p>During world transitions, {@code Player.resetManagers()} already sends
     * {@code CustomHud(clear=true)} which destroys all HyUI elements on the client.
     * This method only removes from tracking maps and cancels refresh tasks
     * (via {@code remove()} which skips the crash-causing {@code hide()} call).
     *
     * @param playerId The player's UUID
     */
    public void discardAllHudsForPlayer(@Nonnull UUID playerId) {
        discardHud(playerId, combatHuds, "combat");
        discardHud(playerId, victoryHuds, "victory");
        discardHud(playerId, defeatHuds, "defeat");
    }

    /**
     * Discards a single HUD type — remove from map + cancel refresh, no hide().
     *
     * <p>Cancels the refresh task directly via reflection. HyUI's {@code hud.remove()}
     * early-returns when {@code getStore()} returns null during transitions, skipping
     * {@code refreshTask.cancel()}. No explicit MCHUD removal needed: deterministic
     * names (e.g. "too-realm-combat") ensure the next show replaces the orphaned entry.
     */
    private void discardHud(
            @Nonnull UUID playerId,
            @Nonnull Map<UUID, HyUIHud> hudMap,
            @Nonnull String hudType) {

        HyUIHud hud = hudMap.remove(playerId);
        if (hud == null) {
            return;
        }

        HudRefreshHelper.cancelRefreshTask(hud);
        LOGGER.atFine().log("Discarded stale %s HUD for player %s (world transition)",
            hudType, playerId.toString().substring(0, 8));
    }

    /**
     * Removes a specific HUD type synchronously.
     */
    private void removeHudSync(
            @Nonnull UUID playerId,
            @Nonnull Map<UUID, HyUIHud> hudMap,
            @Nonnull String hudType) {

        HyUIHud hud = hudMap.remove(playerId);
        if (hud == null) {
            return;
        }

        try {
            hud.remove();
            LOGGER.atFine().log("Removed %s HUD for player %s",
                hudType, playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove %s HUD for player %s",
                hudType, playerId.toString().substring(0, 8));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD-THREAD REFRESH (replaces HyUI's ScheduledExecutorService)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Ticks combat HUDs for players in a specific realm.
     *
     * <p>Only refreshes HUDs for players in {@code realmPlayers}. This is critical
     * because each realm dispatches its tick to its own world thread, but this
     * manager is a singleton with a global {@code combatHuds} map. Without scoping,
     * realm A's tick would refresh HUDs created on realm B's thread, triggering
     * HyUI's thread assertion ({@code "Assert not in thread!"}) and evicting the HUD.
     *
     * <p>Uses {@link HudRefreshHelper#safeRefreshWithToggle} for atomic Clear+Append
     * rerenders. See {@link HudRefreshHelper} for why diff-based refresh is unsafe.
     *
     * @param realmPlayers the set of player UUIDs currently in the ticking realm
     */
    public void tickCombatHuds(@Nonnull Set<UUID> realmPlayers) {
        var iterator = combatHuds.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID playerId = entry.getKey();

            // Only tick HUDs belonging to this realm's players
            if (!realmPlayers.contains(playerId)) {
                continue;
            }

            HyUIHud hud = entry.getValue();
            try {
                HudRefreshHelper.safeRefreshWithToggle(hud, playerId, hudToggleService);
            } catch (Exception e) {
                // Evict stale HUD to prevent repeated crash on every tick
                iterator.remove();
                HudRefreshHelper.cancelRefreshTask(hud);
                LOGGER.atWarning().log("Evicted stale combat HUD for player %s: %s",
                    playerId.toString().substring(0, 8), e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // REALM CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Cleans up all HUDs for players in a realm being closed (synchronous).
     *
     * <p><b>CRITICAL: Caller MUST be on the realm world thread!</b>
     * Use this inside {@code world.execute()} blocks or world event handlers.
     *
     * <p>Note: In normal operation, HUDs are already removed by the
     * DrainPlayerFromWorldEvent handler when players leave. This method
     * serves as a safety net for edge cases.
     *
     * @param realmId   The realm ID (for logging)
     * @param playerIds Set of players to clean up
     */
    public void cleanupRealmSync(@Nonnull UUID realmId, @Nonnull Set<UUID> playerIds) {
        Objects.requireNonNull(playerIds, "playerIds cannot be null");

        for (UUID playerId : playerIds) {
            removeAllHudsForPlayerSync(playerId);
        }

        LOGGER.atFine().log("Cleaned up HUDs for %d players from realm %s",
            playerIds.size(), realmId.toString().substring(0, 8));
    }

    /**
     * Removes all HUDs for all players.
     * Called during shutdown - uses direct removal since we're shutting down anyway.
     */
    public void removeAllHuds() {
        int total = combatHuds.size() + victoryHuds.size() + defeatHuds.size();
        LOGGER.atInfo().log("Removing all realm HUDs (%d total)", total);

        for (UUID playerId : Set.copyOf(combatHuds.keySet())) {
            removeHudSync(playerId, combatHuds, "combat");
        }
        for (UUID playerId : Set.copyOf(victoryHuds.keySet())) {
            removeHudSync(playerId, victoryHuds, "victory");
        }
        for (UUID playerId : Set.copyOf(defeatHuds.keySet())) {
            removeHudSync(playerId, defeatHuds, "defeat");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /** Checks if a player has an active combat HUD. */
    public boolean hasCombatHud(@Nonnull UUID playerId) {
        return combatHuds.containsKey(playerId);
    }

    /** Gets the active combat HUD for a player, or null. */
    @Nullable
    public HyUIHud getCombatHud(@Nonnull UUID playerId) {
        return combatHuds.get(playerId);
    }

    /** Checks if a player has an active victory HUD. */
    public boolean hasVictoryHud(@Nonnull UUID playerId) {
        return victoryHuds.containsKey(playerId);
    }

    /** Checks if a player has an active defeat HUD. */
    public boolean hasDefeatHud(@Nonnull UUID playerId) {
        return defeatHuds.containsKey(playerId);
    }

    /** Gets the total count of active HUDs. */
    public int getActiveHudCount() {
        return combatHuds.size() + victoryHuds.size() + defeatHuds.size();
    }
}
