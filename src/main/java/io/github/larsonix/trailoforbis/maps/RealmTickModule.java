package io.github.larsonix.trailoforbis.maps;

import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
import com.hypixel.hytale.builtin.instances.removal.RemovalCondition;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.instance.RealmPortalManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmRemovalHandler;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestMonitor;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobSpawner;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;
import io.github.larsonix.trailoforbis.maps.ui.RealmMobMarkerProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Periodic task scheduler for the Realms system.
 *
 * <p>Runs a 1-second tick loop that dispatches to sub-managers:
 * <ul>
 *   <li>Realm timeout enforcement</li>
 *   <li>Portal timeout cleanup</li>
 *   <li>Arena boundary enforcement</li>
 *   <li>Despawned mob recovery</li>
 *   <li>Combat HUD refresh</li>
 *   <li>Mob marker position updates</li>
 *   <li>Reward chest monitoring</li>
 * </ul>
 */
final class RealmTickModule {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int DESPAWN_RECOVERY_INTERVAL_TICKS = 100;

    // Dependencies
    private final Map<UUID, RealmInstance> realmsById;
    private final RealmRemovalHandler removalHandler;
    private final RealmPortalManager portalManager;
    private final RealmHudManager hudManager;
    private final Map<UUID, RealmMobMarkerProvider> mobMarkerProviders;
    private final BiConsumer<UUID, RealmInstance.CompletionReason> onRealmTimedOut;

    // Nullable dependencies (set later or optional)
    @Nullable
    private RealmMobSpawner mobSpawner;
    @Nullable
    private RewardChestMonitor rewardChestMonitor;

    // State
    @Nullable
    private ScheduledExecutorService scheduler;
    private int timerDiagCounter = 0;
    private boolean timerDiagEnabled = false;
    private int despawnRecoveryCounter = 0;

    RealmTickModule(
            @Nonnull Map<UUID, RealmInstance> realmsById,
            @Nonnull RealmRemovalHandler removalHandler,
            @Nonnull RealmPortalManager portalManager,
            @Nonnull RealmHudManager hudManager,
            @Nonnull Map<UUID, RealmMobMarkerProvider> mobMarkerProviders,
            @Nonnull BiConsumer<UUID, RealmInstance.CompletionReason> onRealmTimedOut) {
        this.realmsById = realmsById;
        this.removalHandler = removalHandler;
        this.portalManager = portalManager;
        this.hudManager = hudManager;
        this.mobMarkerProviders = mobMarkerProviders;
        this.onRealmTimedOut = onRealmTimedOut;
    }

    void setMobSpawner(@Nullable RealmMobSpawner mobSpawner) {
        this.mobSpawner = mobSpawner;
    }

    void setRewardChestMonitor(@Nullable RewardChestMonitor rewardChestMonitor) {
        this.rewardChestMonitor = rewardChestMonitor;
    }

    void setTimerDiagEnabled(boolean enabled) {
        this.timerDiagEnabled = enabled;
    }

    /**
     * Starts the periodic scheduler (1-second interval, daemon thread).
     */
    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RealmsManager-Scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            this::processTick,
            1, 1, TimeUnit.SECONDS
        );
        LOGGER.atInfo().log("Realm tick scheduler started");
    }

    /**
     * Shuts down the scheduler, waiting up to 5 seconds for pending tasks.
     */
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }

    boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }

    // ═══════════════════════════════════════════════════════════════════
    // TICK PROCESSING
    // ═══════════════════════════════════════════════════════════════════

    private void processTick() {
        try {
            // Process pending removals
            removalHandler.processPendingRemovals();

            // Process portal timeouts
            int expiredPortals = portalManager.processPortalTimeouts();
            if (expiredPortals > 0) {
                LOGGER.atFine().log("Removed %d expired portals", expiredPortals);
            }

            // Check for timed out realms (runs EVERY tick - critical for timeout enforcement)
            checkRealmTimeouts();

            // Enforce arena boundaries — clamp mobs that wander outside the arena
            enforceArenaBoundaries();

            // Despawn recovery — respawn mobs that were lost to chunk unloading
            checkDespawnedMobs();

            // Refresh mob marker positions (dispatched to world thread for ECS access)
            refreshMobMarkerPositions();

            // Tick combat HUDs (timer countdown + kill progress)
            tickCombatHuds();

            // Check for closed reward chests
            if (rewardChestMonitor != null) {
                rewardChestMonitor.tick();
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error in realm tick processing");
        }
    }

    private void checkRealmTimeouts() {
        timerDiagCounter++;
        boolean doDiag = timerDiagEnabled && (timerDiagCounter % 10 == 1);

        for (RealmInstance realm : realmsById.values()) {
            if (!realm.isActive()) continue;

            // DIAGNOSTIC: Check vanilla timer state every 10 seconds (gated by config)
            if (doDiag) {
                logTimerDiagnostics(realm);
            }

            // Utility biomes (skill sanctum) have no timer — infinite duration
            if (realm.getBiome().isUtilityBiome()) {
                continue;
            }

            if (realm.isTimedOut()) {
                if (timerDiagEnabled) {
                    LOGGER.atInfo().log("[TIMER-DIAG] OUR TIMER triggered timeout for realm %s (elapsed=%ds >= timeout=%ds)",
                        realm.getRealmId().toString().substring(0, 8),
                        realm.getElapsedTime().toSeconds(),
                        realm.getTimeout().toSeconds());
                }
                realm.markTimedOut();
                onRealmTimedOut.accept(realm.getRealmId(), RealmInstance.CompletionReason.TIMEOUT);
            }
        }
    }

    private void logTimerDiagnostics(RealmInstance realm) {
        World world = realm.getWorld();
        if (world == null) return;
        try {
            Store<ChunkStore> cs = world.getChunkStore().getStore();
            InstanceDataResource idr = cs.getResource(InstanceDataResource.getResourceType());
            InstanceWorldConfig iwc = InstanceWorldConfig.get(world.getWorldConfig());
            int condCount = iwc != null ? iwc.getRemovalConditions().length : -1;
            String condTypes = "";
            if (iwc != null && condCount > 0) {
                StringBuilder sb = new StringBuilder();
                for (RemovalCondition c : iwc.getRemovalConditions()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(c.getClass().getSimpleName());
                }
                condTypes = sb.toString();
            }
            LOGGER.atInfo().log(
                "[TIMER-DIAG] Realm %s: elapsed=%ds, timeout=%ds, remaining=%ds | vanilla: conditions=%d [%s], timeoutTimer=%s, isRemoving=%b",
                realm.getRealmId().toString().substring(0, 8),
                realm.getElapsedTime().toSeconds(),
                realm.getTimeout().toSeconds(),
                realm.getRemainingTime().toSeconds(),
                condCount, condTypes,
                idr.getTimeoutTimer(),
                idr.isRemoving()
            );
        } catch (Exception e) {
            LOGGER.atFine().log("[TIMER-DIAG] Could not read vanilla state for realm %s: %s",
                realm.getRealmId().toString().substring(0, 8), e.getMessage());
        }
    }

    private void enforceArenaBoundaries() {
        if (mobSpawner == null) return;
        for (RealmInstance realm : realmsById.values()) {
            if (!realm.isActive()) continue;
            World world = realm.getWorld();
            if (world == null || !world.isAlive()) continue;
            UUID realmId = realm.getRealmId();
            world.execute(() -> mobSpawner.enforceArenaBoundaries(realmId, world));
        }
    }

    private void checkDespawnedMobs() {
        if (mobSpawner == null) return;
        despawnRecoveryCounter++;
        if (despawnRecoveryCounter < DESPAWN_RECOVERY_INTERVAL_TICKS) {
            return;
        }
        despawnRecoveryCounter = 0;

        for (RealmInstance realm : realmsById.values()) {
            if (!realm.isActive()) continue;
            World world = realm.getWorld();
            if (world == null || !world.isAlive()) continue;
            world.execute(() -> mobSpawner.checkAndRespawnDespawnedMobs(realm));
        }
    }

    private void tickCombatHuds() {
        for (RealmInstance realm : realmsById.values()) {
            if (!realm.isActive()) continue;
            World world = realm.getWorld();
            if (world == null || !world.isAlive()) continue;

            Set<UUID> realmPlayers = realm.getCurrentPlayers();
            world.execute(() -> hudManager.tickCombatHuds(realmPlayers));
        }
    }

    private void refreshMobMarkerPositions() {
        for (var entry : mobMarkerProviders.entrySet()) {
            RealmInstance realm = realmsById.get(entry.getKey());
            if (realm == null || !realm.isActive()) {
                continue;
            }
            World world = realm.getWorld();
            if (world == null || !world.isAlive()) continue;

            var provider = entry.getValue();
            world.execute(() -> provider.refreshPositions(world));
        }
    }
}
