package io.github.larsonix.trailoforbis.maps.ui;

import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
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
 * <p>Combat HUD refresh uses {@code resetHasBuilt()} + {@code refreshOrRerender(true, true)}
 * instead of the default diff-based {@code refreshOrRerender(false, false)}. The diff
 * approach generates {@code Set} commands referencing auto-generated {@code #HYUUIDGroupNNN}
 * element IDs. When the MultipleHUD mod rebuilds the DOM (triggered by any mod
 * adding/removing a HUD), those IDs vanish and the client crashes. The full rerender
 * approach atomically Clears + Appends — immune to DOM rebuilds.
 *
 * @see RealmCombatHud
 * @see RealmVictoryHud
 * @see RealmDefeatHud
 */
public class RealmHudManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // HYUI REFLECTION — reset hasBuilt to force Append instead of Set
    // ═══════════════════════════════════════════════════════════════════

    private static final Field DELEGATE_FIELD;
    private static final Field HAS_BUILT_FIELD;

    static {
        Field df = null, hf = null;
        try {
            df = HyUIHud.class.getDeclaredField("delegate");
            df.setAccessible(true);
            hf = Class.forName("au.ellie.hyui.builders.HyUInterface").getDeclaredField("hasBuilt");
            hf.setAccessible(true);
        } catch (Exception e) {
            // Will fall back to diff-based refresh if reflection fails
        }
        DELEGATE_FIELD = df;
        HAS_BUILT_FIELD = hf;
    }

    /**
     * Resets HyUI's internal {@code hasBuilt} flag so the next build generates
     * {@code Append} commands instead of {@code Set} commands.
     *
     * @return true if reset succeeded, false if reflection failed
     */
    public static boolean resetHasBuilt(@Nonnull HyUIHud hud) {
        if (DELEGATE_FIELD == null || HAS_BUILT_FIELD == null) {
            return false;
        }
        try {
            Object delegate = DELEGATE_FIELD.get(hud);
            HAS_BUILT_FIELD.set(delegate, false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Active combat HUDs per player UUID. */
    private final Map<UUID, HyUIHud> combatHuds = new ConcurrentHashMap<>();

    /** Active victory HUDs per player UUID. */
    private final Map<UUID, HyUIHud> victoryHuds = new ConcurrentHashMap<>();

    /** Active defeat HUDs per player UUID. */
    private final Map<UUID, HyUIHud> defeatHuds = new ConcurrentHashMap<>();

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
            // Get fresh PlayerRef after deferral - should now be valid in this world
            PlayerRef freshPlayerRef = Universe.get().getPlayer(playerId);
            if (freshPlayerRef == null) {
                LOGGER.atWarning().log("Cannot show combat HUD - player %s not found after deferral",
                    playerId.toString().substring(0, 8));
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();

            try {
                HyUIHud hud = RealmCombatHud.create(freshPlayerRef, realm, store);

                // CRITICAL: Defer map registration by 1 tick. HyUIHud.add() → safeAdd()
                // defers the Append packet via world.execute(). If we put the HUD in the
                // map NOW, tickCombatHuds() fires BEFORE the Append reaches the client and
                // sends commands referencing non-existent elements → client crash.
                world.execute(() -> {
                    combatHuds.put(playerId, hud);
                    LOGGER.atInfo().log("Showed combat HUD for player %s in realm %s",
                        playerId.toString().substring(0, 8),
                        realm.getRealmId().toString().substring(0, 8));
                });
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to show combat HUD for player %s",
                    playerId.toString().substring(0, 8));
            }
        });
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

        // Explicitly discard combat HUD first — NOT hide() which sends Set commands
        // to elements that may have been cleared during world transition.
        discardHud(playerId, combatHuds, "combat");

        World world = realm.getWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot show victory HUD - realm world not available");
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        try {
            HyUIHud hud = RealmVictoryHud.create(player, realm, store);
            victoryHuds.put(playerId, hud);
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

        // Explicitly discard combat HUD first
        discardHud(playerId, combatHuds, "combat");

        World world = realm.getWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("Cannot show defeat HUD - realm world not available");
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        try {
            HyUIHud hud = RealmDefeatHud.create(player, realm, store);
            defeatHuds.put(playerId, hud);
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
     */
    private void discardHud(
            @Nonnull UUID playerId,
            @Nonnull Map<UUID, HyUIHud> hudMap,
            @Nonnull String hudType) {

        HyUIHud hud = hudMap.remove(playerId);
        if (hud == null) {
            return;
        }

        try {
            // ONLY remove() — cancels refresh task. No hide() — that sends Set commands
            // to elements already cleared by resetManagers(), crashing the client.
            hud.remove();
            LOGGER.atFine().log("Discarded stale %s HUD for player %s (world transition)",
                hudType, playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to discard stale %s HUD for player %s: %s",
                hudType, playerId.toString().substring(0, 8), e.getMessage());
        }
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
            hud.hide();
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
     * Ticks all active combat HUDs. Called from the realm's world-thread tick loop.
     *
     * <p>Uses full rerender ({@code resetHasBuilt} + {@code refreshOrRerender(true, true)})
     * instead of diff-based {@code refreshOrRerender(false, false)}. The diff approach
     * generates {@code Set} commands targeting auto-generated {@code #HYUUIDGroupNNN}
     * element IDs that can vanish when the MultipleHUD mod rebuilds the DOM. The full
     * rerender atomically Clears the HUD group and Appends fresh elements — no
     * dependency on stale element IDs.
     *
     * <p>Falls back to diff-based refresh if the reflection-based reset is unavailable.
     */
    public void tickCombatHuds() {
        var iterator = combatHuds.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            HyUIHud hud = entry.getValue();
            try {
                hud.triggerRefresh();

                // Full rerender: reset hasBuilt → forces Append instead of Set
                if (resetHasBuilt(hud)) {
                    hud.refreshOrRerender(true, true);
                } else {
                    // Fallback: diff-based (may crash if DOM was rebuilt by MultipleHUD)
                    hud.refreshOrRerender(false, false);
                }
            } catch (Exception e) {
                // Evict stale HUD to prevent repeated crash on every tick
                iterator.remove();
                try { hud.remove(); } catch (Exception ignored) {}
                LOGGER.atWarning().log("Evicted stale combat HUD for player %s: %s",
                    entry.getKey().toString().substring(0, 8), e.getMessage());
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
