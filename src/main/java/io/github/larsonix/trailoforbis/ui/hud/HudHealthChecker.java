package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.sanctum.ui.SkillPointHudManager;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodic safety net that detects missing HUDs and restores them.
 *
 * <p>The event-driven lifecycle ({@link HudLifecycleManager}) handles 99%+ of cases,
 * but edge cases in Hytale's world transition timing can silently drop HUD creation.
 * This checker runs every N seconds, performs zero-cost map lookups to detect gaps,
 * and only defers restoration to the world thread when something is actually missing.
 *
 * <h2>Performance</h2>
 * <p>When all HUDs are healthy (the common case): ~4 ConcurrentHashMap lookups per
 * player per interval. Zero packets, zero ECS access, zero world-thread work.
 *
 * <h2>Thread Safety</h2>
 * <p>The scheduler thread only reads ConcurrentHashMaps (thread-safe). All actual
 * HUD restoration is deferred to {@code world.execute()} for world-thread safety.
 */
final class HudHealthChecker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final List<PersistentHud> providers;
    @Nullable private final RealmsManager realmsManager;
    @Nullable private final RealmHudManager realmHudManager;

    /** Late-bound sanctum references (sanctum is created after health checker starts). */
    @Nullable private volatile SkillSanctumManager sanctumManager;
    @Nullable private volatile SkillPointHudManager skillPointHudManager;

    /** Per-player cooldown — nanoTime of last restore attempt. Prevents spam during transitions. */
    private final Map<UUID, Long> lastRestoreAttempt = new ConcurrentHashMap<>();

    /**
     * Consecutive restore counter per player. When a player's HUDs are restored
     * repeatedly without ever being "all healthy", we exponentially back off the
     * cooldown to avoid wasting world-thread time on a pathological loop.
     *
     * <p>Reset to 0 when a health check finds ALL HUDs present (healthy state).
     */
    private final Map<UUID, Integer> consecutiveRestores = new ConcurrentHashMap<>();

    /** After this many consecutive restores, start backing off. */
    private static final int BACKOFF_THRESHOLD = 5;

    /** Maximum backoff multiplier (caps at 6x the base cooldown = ~48s at 8s base). */
    private static final int MAX_BACKOFF_MULTIPLIER = 6;

    /**
     * Tracks players awaiting a one-time verification rerender.
     *
     * <p>Set to {@code true} by {@link #notifyRestored} when the event-driven path
     * creates HUDs. After the cooldown expires, the first health check forces a full
     * rerender of all active HUDs to ensure the client actually rendered them. This
     * catches remote connections where initial HUD packets are silently dropped during
     * the client's UI initialization window.
     */
    private final Map<UUID, Boolean> pendingVerification = new ConcurrentHashMap<>();

    /** Cooldown duration: skip players restored within this window. */
    private final long cooldownNanos;

    private ScheduledExecutorService scheduler;

    HudHealthChecker(
            @Nonnull List<PersistentHud> providers,
            @Nullable RealmsManager realmsManager,
            @Nullable RealmHudManager realmHudManager,
            long cooldownSeconds) {
        this.providers = providers;
        this.realmsManager = realmsManager;
        this.realmHudManager = realmHudManager;
        this.cooldownNanos = TimeUnit.SECONDS.toNanos(cooldownSeconds);
    }

    /**
     * Sets the sanctum references for sanctum HUD recovery.
     *
     * <p>Called after {@link SkillSanctumManager} is created (Phase 7.7.5),
     * which is after the health checker starts (Phase 6.10). Uses volatile
     * fields for safe publication to the scheduler thread.
     */
    void setSanctumReferences(@Nullable SkillSanctumManager sanctumManager,
                              @Nullable SkillPointHudManager skillPointHudManager) {
        this.sanctumManager = sanctumManager;
        this.skillPointHudManager = skillPointHudManager;
    }

    /**
     * Starts the periodic health check.
     *
     * @param intervalSeconds seconds between checks (e.g. 5)
     */
    void start(int intervalSeconds) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TOO-HudHealthCheck");
            t.setDaemon(true);
            return t;
        });

        // Initial delay = interval (give event-driven path time to initialize on startup)
        scheduler.scheduleAtFixedRate(
            this::runHealthCheck,
            intervalSeconds, intervalSeconds, TimeUnit.SECONDS
        );

        LOGGER.atInfo().log("HUD health checker started (interval=%ds, cooldown=%ds)",
            intervalSeconds, TimeUnit.NANOSECONDS.toSeconds(cooldownNanos));
    }

    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        lastRestoreAttempt.clear();
        pendingVerification.clear();
        consecutiveRestores.clear();
        LOGGER.atInfo().log("HUD health checker stopped");
    }

    /**
     * Called by the event-driven path after successful restoration.
     * Sets the cooldown so the health checker doesn't attempt a redundant restore.
     */
    void notifyRestored(@Nonnull UUID playerId) {
        lastRestoreAttempt.put(playerId, System.nanoTime());
        pendingVerification.put(playerId, Boolean.TRUE);
    }

    /**
     * Called on player disconnect to clean up tracking state.
     */
    void onDisconnect(@Nonnull UUID playerId) {
        lastRestoreAttempt.remove(playerId);
        pendingVerification.remove(playerId);
        consecutiveRestores.remove(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCHEDULER THREAD — zero packets, pure map lookups
    // ═══════════════════════════════════════════════════════════════════

    private void runHealthCheck() {
        try {
            Map<UUID, World> snapshot = PlayerWorldCache.snapshot();

            for (Map.Entry<UUID, World> entry : snapshot.entrySet()) {
                UUID uuid = entry.getKey();
                World world = entry.getValue();

                if (!world.isAlive()) continue;

                // Skip players within cooldown (event-driven path handles first N seconds)
                // Apply backoff multiplier for players with repeated consecutive restores
                Long last = lastRestoreAttempt.get(uuid);
                if (last != null) {
                    int restoreCount = consecutiveRestores.getOrDefault(uuid, 0);
                    int multiplier = 1;
                    if (restoreCount >= BACKOFF_THRESHOLD) {
                        multiplier = Math.min(restoreCount - BACKOFF_THRESHOLD + 2, MAX_BACKOFF_MULTIPLIER);
                    }
                    if ((System.nanoTime() - last) < cooldownNanos * multiplier) continue;
                }

                // One-time verification rerender: after cooldown expires, force-rerender
                // all active HUDs to ensure the client actually rendered them. This catches
                // remote connections where initial packets are silently dropped during the
                // client's UI initialization window.
                if (Boolean.TRUE.equals(pendingVerification.get(uuid))) {
                    pendingVerification.put(uuid, Boolean.FALSE);
                    lastRestoreAttempt.put(uuid, System.nanoTime());
                    world.execute(() -> verifyOnWorldThread(uuid, world));
                    continue;
                }

                // Check persistent HUDs (XP bar, energy shield, combat ghost)
                boolean anyPersistentMissing = false;
                for (PersistentHud p : providers) {
                    if (!p.isActive(uuid)) {
                        anyPersistentMissing = true;
                        break;
                    }
                }

                // Check realm combat HUD (not a PersistentHud — managed separately)
                boolean realmHudMissing = false;
                RealmInstance realm = null;
                if (realmsManager != null && realmsManager.isPlayerInRealm(uuid)) {
                    realm = realmsManager.getPlayerRealm(uuid).orElse(null);
                    if (realm != null
                            && !realm.getBiome().isUtilityBiome()
                            && (realm.isActive() || realm.isReady() || realm.isEnding())
                            && realmHudManager != null
                            && !realmHudManager.hasCombatHud(uuid)
                            && !realmHudManager.hasVictoryHud(uuid)
                            && !realmHudManager.hasDefeatHud(uuid)) {
                        realmHudMissing = true;
                    }
                }

                // Check sanctum skill point HUD (not a PersistentHud — managed separately)
                boolean sanctumHudMissing = false;
                if (sanctumManager != null && sanctumManager.hasActiveSanctum(uuid)
                        && skillPointHudManager != null
                        && skillPointHudManager.getHud(uuid) == null) {
                    sanctumHudMissing = true;
                }

                if (!anyPersistentMissing && !realmHudMissing && !sanctumHudMissing) {
                    // All HUDs healthy — reset backoff counter
                    consecutiveRestores.remove(uuid);
                    continue;
                }

                // Track consecutive restore attempts for backoff
                int count = consecutiveRestores.merge(uuid, 1, Integer::sum);
                if (count == BACKOFF_THRESHOLD) {
                    LOGGER.atWarning().log("[HUD-HEAL] Player %s hit %d consecutive restores — "
                        + "enabling backoff (persistent=%b, realm=%b, sanctum=%b)",
                        uuid, count, anyPersistentMissing, realmHudMissing, sanctumHudMissing);
                }

                // Set cooldown BEFORE deferring — prevents double-scheduling
                lastRestoreAttempt.put(uuid, System.nanoTime());

                // Determine correct world thread:
                // - Realm HUDs must be restored on the realm's world thread
                // - Persistent HUDs use the player's current world
                final World targetWorld;
                if (realmHudMissing && realm != null
                        && realm.getWorld() != null && realm.getWorld().isAlive()) {
                    targetWorld = realm.getWorld();
                } else {
                    targetWorld = world;
                }

                final boolean needsPersistent = anyPersistentMissing;
                final boolean needsRealm = realmHudMissing;
                final boolean needsSanctum = sanctumHudMissing;
                final RealmInstance capturedRealm = realm;

                targetWorld.execute(() ->
                    restoreOnWorldThread(uuid, targetWorld, needsPersistent,
                        needsRealm, capturedRealm, needsSanctum));
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[HUD-HEAL] Health check failed");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD THREAD — actual HUD restoration (packets sent here)
    // ═══════════════════════════════════════════════════════════════════

    private void restoreOnWorldThread(
            @Nonnull UUID uuid,
            @Nonnull World world,
            boolean needsPersistent,
            boolean needsRealm,
            @Nullable RealmInstance realm,
            boolean needsSanctum) {

        if (!world.isAlive()) return;

        // Fresh PlayerRef at execution time — never stale
        PlayerRef freshRef = Universe.get().getPlayer(uuid);
        if (freshRef == null) return; // Disconnected

        // Validate entity ref is reachable (not mid-transition)
        Ref<EntityStore> entityRef = freshRef.getReference();
        if (entityRef == null || !entityRef.isValid()) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) return;

        // Restore persistent HUDs (each provider handles its own conditions, e.g. creative mode)
        if (needsPersistent) {
            for (PersistentHud p : providers) {
                if (!p.isActive(uuid)) {
                    try {
                        p.restore(uuid, freshRef, store, player);
                        LOGGER.atInfo().log("[HUD-HEAL] Restored %s for player %s",
                            p.hudName(), uuid.toString().substring(0, 8));
                    } catch (Exception e) {
                        LOGGER.atWarning().log("[HUD-HEAL] Failed to restore %s for %s: %s",
                            p.hudName(), uuid.toString().substring(0, 8), e.getMessage());
                    }
                }
            }
        }

        // Restore realm combat HUD (direct — no nested world.execute)
        if (needsRealm && realm != null && realmHudManager != null
                && (realm.isActive() || realm.isReady() || realm.isEnding())
                && !realmHudManager.hasCombatHud(uuid)
                && !realmHudManager.hasVictoryHud(uuid)
                && !realmHudManager.hasDefeatHud(uuid)) {
            try {
                realmHudManager.showCombatHudDirect(uuid, freshRef, player, realm);
                LOGGER.atInfo().log("[HUD-HEAL] Restored combat HUD for player %s",
                    uuid.toString().substring(0, 8));
            } catch (Exception e) {
                LOGGER.atWarning().log("[HUD-HEAL] Failed to restore combat HUD for %s: %s",
                    uuid.toString().substring(0, 8), e.getMessage());
            }
        }

        // Restore sanctum skill point HUD (direct — same pattern as realm combat HUD)
        if (needsSanctum && sanctumManager != null && skillPointHudManager != null
                && sanctumManager.hasActiveSanctum(uuid)
                && skillPointHudManager.getHud(uuid) == null) {
            try {
                skillPointHudManager.showHudDirect(uuid, freshRef, player, store);
                LOGGER.atInfo().log("[HUD-HEAL] Restored skill point HUD for player %s",
                    uuid.toString().substring(0, 8));
            } catch (Exception e) {
                LOGGER.atWarning().log("[HUD-HEAL] Failed to restore skill point HUD for %s: %s",
                    uuid.toString().substring(0, 8), e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD THREAD — post-restore verification rerender
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detects HUDs that exist server-side but aren't registered in the client's
     * MCHUD, then discards the stale HyUIHud and creates a fresh one.
     *
     * <p>Old HyUIHud objects have {@code delegate.hasBuilt=true} from their first
     * build. Re-registering them via {@code MultiHudWrapper.setCustomHud()} generates
     * only Set commands (no AppendInline) → content elements are never created →
     * permanently invisible. Creating a FRESH HyUIHud via {@code restore()} produces
     * a new delegate with {@code hasBuilt=false} → correct first-build content.
     *
     * <p>Runs once per restore cycle, after the cooldown expires (~8-13s after
     * transition). By this time, {@code getReference()} is valid and there are no
     * competing deferred {@code safeAdd()} tasks.
     */
    private void verifyOnWorldThread(@Nonnull UUID uuid, @Nonnull World world) {
        if (!world.isAlive()) return;

        PlayerRef freshRef = Universe.get().getPlayer(uuid);
        if (freshRef == null) return;

        Ref<EntityStore> entityRef = freshRef.getReference();
        if (entityRef == null || !entityRef.isValid()) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) return;

        int recreated = 0;

        // Check persistent HUDs — discard + restore if not registered in client MCHUD.
        // With the MHUD shim injected, PartyPro no longer replaces our MCHUD.
        // This verification catches genuine safeAdd() failures from packet timing.
        for (PersistentHud p : providers) {
            if (!p.isActive(uuid)) continue;
            HyUIHud hud = p.getActiveHud(uuid);
            if (hud == null) continue; // Not a HyUI-based HUD (e.g. combat ghost)

            if (!HudRefreshHelper.isRegisteredInMchud(hud, player)) {
                try {
                    p.discardStale(uuid);
                    p.restore(uuid, freshRef, store, player);
                    LOGGER.atInfo().log("[HUD-VERIFY] Recreated %s for player %s",
                        p.hudName(), uuid.toString().substring(0, 8));
                    recreated++;
                } catch (Exception e) {
                    LOGGER.atWarning().log("[HUD-VERIFY] Failed to recreate %s for %s: %s",
                        p.hudName(), uuid.toString().substring(0, 8), e.getMessage());
                }
            }
        }

        // Check realm combat HUD
        if (realmHudManager != null && realmHudManager.hasCombatHud(uuid)) {
            HyUIHud combatHud = realmHudManager.getCombatHud(uuid);
            if (combatHud != null && !HudRefreshHelper.isRegisteredInMchud(combatHud, player)) {
                try {
                    RealmInstance realm = (realmsManager != null)
                        ? realmsManager.getPlayerRealm(uuid).orElse(null) : null;
                    if (realm != null) {
                        realmHudManager.discardAllHudsForPlayer(uuid);
                        realmHudManager.showCombatHudDirect(uuid, freshRef, player, realm);
                        LOGGER.atInfo().log("[HUD-VERIFY] Recreated combat HUD for player %s",
                            uuid.toString().substring(0, 8));
                        recreated++;
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().log("[HUD-VERIFY] Failed to recreate combat HUD for %s: %s",
                        uuid.toString().substring(0, 8), e.getMessage());
                }
            }
        }

        // Check sanctum skill point HUD
        if (skillPointHudManager != null && skillPointHudManager.getHud(uuid) != null) {
            HyUIHud spHud = skillPointHudManager.getHud(uuid);
            if (spHud != null && !HudRefreshHelper.isRegisteredInMchud(spHud, player)) {
                try {
                    skillPointHudManager.discardStaleHud(uuid);
                    skillPointHudManager.showHudDirect(uuid, freshRef, player, store);
                    LOGGER.atInfo().log("[HUD-VERIFY] Recreated skill point HUD for player %s",
                        uuid.toString().substring(0, 8));
                    recreated++;
                } catch (Exception e) {
                    LOGGER.atWarning().log("[HUD-VERIFY] Failed to recreate skill point HUD for %s: %s",
                        uuid.toString().substring(0, 8), e.getMessage());
                }
            }
        }

        if (recreated > 0) {
            LOGGER.atInfo().log("[HUD-VERIFY] Recreated %d HUDs for player %s",
                recreated, uuid.toString().substring(0, 8));
        }
    }
}
