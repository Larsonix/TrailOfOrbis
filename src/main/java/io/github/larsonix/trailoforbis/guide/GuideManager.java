package io.github.larsonix.trailoforbis.guide;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the player guide system: milestone tracking, popup lifecycle,
 * priority gating, damage suppression, and safety polling.
 *
 * <h2>Popup Lifecycle</h2>
 * <ol>
 *   <li>Trigger fires → {@link #tryShow} queues the milestone</li>
 *   <li>Safety check polls every 1 second on the world thread</li>
 *   <li>First second with no hostile mobs within 15 blocks → popup opens</li>
 *   <li>Player dismisses popup → state returns to idle</li>
 * </ol>
 *
 * <h2>Core Rules</h2>
 * <ul>
 *   <li>One popup at a time per player (queued or showing)</li>
 *   <li>Higher-priority milestones replace queued lower-priority ones</li>
 *   <li>Deferred milestones stay unmarked and re-fire on next natural trigger</li>
 *   <li>Milestone marked complete in DB when popup is SHOWN, not when triggered</li>
 *   <li>Damage suppressed while popup is open (guidePopupOpen flag)</li>
 *   <li>60-second safety valve auto-clears stuck popups</li>
 * </ul>
 */
public class GuideManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long SAFETY_VALVE_MS = 60_000;
    private static final double SAFETY_CHECK_RADIUS = 15.0;
    private static final int MAX_SAFETY_CHECKS = 120; // 2 minutes max wait

    private static final com.hypixel.hytale.common.plugin.PluginIdentifier VOILE_PLUGIN_ID =
        new com.hypixel.hytale.common.plugin.PluginIdentifier("IwakuraEnterprises", "Voile");
    private static final String WIKI_BASE_URL = "https://wiki.hytalemodding.dev/mod/trail-of-orbis/";

    private final GuideRepository repository;
    private volatile boolean voileAvailable;
    private volatile boolean voileChecked;

    // Scheduler for safety-check polling (1-second delayed checks)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "guide-safety-poll");
        t.setDaemon(true);
        return t;
    });

    // Per-player state (in-memory, cleared on disconnect)
    private final Map<UUID, PlayerGuideState> playerStates = new ConcurrentHashMap<>();

    public GuideManager(@Nonnull DataManager dataManager) {
        this.repository = new GuideRepository(dataManager);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called on player join. Loads completed milestones from DB.
     */
    public void onPlayerJoin(@Nonnull UUID playerId) {
        // Detect Voile on first player join (all plugins loaded by then)
        if (!voileChecked) {
            voileChecked = true;
            try {
                voileAvailable = com.hypixel.hytale.server.core.plugin.PluginManager.get().getPlugin(VOILE_PLUGIN_ID) != null;
            } catch (Exception e) {
                voileAvailable = false;
            }
            LOGGER.atInfo().log("Voile wiki integration: %s", voileAvailable ? "available" : "not installed (fallback to chat URL)");
        }

        Set<String> completed = repository.getCompletedMilestones(playerId);
        playerStates.put(playerId, new PlayerGuideState(completed));
        LOGGER.atFine().log("Loaded %d completed milestones for %s",
            completed.size(), playerId.toString().substring(0, 8));
    }

    /**
     * Called on player disconnect. Clears in-memory state.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        playerStates.remove(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRIGGER API - Called from existing systems
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Attempts to show a guide milestone popup. This is the main entry point
     * called from all trigger integration points.
     *
     * <p>The milestone is queued for display if:
     * <ol>
     *   <li>Player has in-memory state (has joined)</li>
     *   <li>Milestone not already completed</li>
     *   <li>No popup is currently showing (or safety valve expired)</li>
     *   <li>No higher-priority milestone is already queued</li>
     * </ol>
     *
     * <p>Once queued, a 1-second safety polling loop begins. The popup opens
     * on the first second with no hostile mobs within 15 blocks of the player.
     */
    public void tryShow(@Nonnull UUID playerId, @Nonnull GuideMilestone milestone) {
        PlayerGuideState state = playerStates.get(playerId);
        if (state == null) return;

        // Already completed?
        if (state.isCompleted(milestone.getId())) return;

        // Don't interrupt realm combat with non-realm milestones.
        if (!milestone.canShowInRealm() && isPlayerInRealm(playerId)) {
            return;
        }

        // If a popup is currently SHOWING, check safety valve
        if (state.isPopupShowing()) {
            if (!state.isSafetyValveExpired(SAFETY_VALVE_MS)) {
                return; // Popup open, can't queue another
            }
            // Safety valve expired, force-clear the stuck popup
            LOGGER.atWarning().log("Safety valve expired for %s, clearing stuck popup",
                playerId.toString().substring(0, 8));
            state.clearPopup();
        }

        // If a milestone is already queued, only replace if the new one has higher priority
        if (state.isQueued()) {
            GuideMilestone queued = state.getQueuedMilestone();
            if (queued != null && milestone.getPriority().getWeight() >= queued.getPriority().getWeight()) {
                return; // Current queued milestone has equal or higher priority
            }
            // Replace with higher-priority milestone
            LOGGER.atFine().log("Replacing queued '%s' with higher-priority '%s' for %s",
                queued != null ? queued.getId() : "?", milestone.getId(),
                playerId.toString().substring(0, 8));
        }

        // Queue the milestone and start safety polling
        state.queueMilestone(milestone);
        scheduleSafetyCheck(playerId, 0);
    }

    /**
     * Attempts to show the highest-priority milestone from a set of candidates.
     * Use when multiple milestones could trigger in the same event (e.g., level up
     * triggers both MOB_SCALING and a level threshold).
     */
    public void tryShowBest(@Nonnull UUID playerId, @Nonnull GuideMilestone... candidates) {
        PlayerGuideState state = playerStates.get(playerId);
        if (state == null) return;

        GuideMilestone best = null;
        for (GuideMilestone candidate : candidates) {
            if (state.isCompleted(candidate.getId())) continue;
            if (best == null || candidate.getPriority().getWeight() < best.getPriority().getWeight()) {
                best = candidate;
            }
        }

        if (best != null) {
            tryShow(playerId, best);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAFETY CHECK POLLING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Schedules a safety check after 1 second. If the area is safe (no hostile
     * mobs within 15 blocks), the popup opens. Otherwise, reschedules.
     */
    private void scheduleSafetyCheck(@Nonnull UUID playerId, int checkCount) {
        try {
            scheduler.schedule(() -> performSafetyCheck(playerId, checkCount), 1, TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Scheduler shut down during plugin disable — ignore
        }
    }

    /**
     * Performs the safety check on the world thread. Scans for hostile mobs
     * within the safety radius. If safe, shows the popup immediately.
     */
    private void performSafetyCheck(@Nonnull UUID playerId, int checkCount) {
        PlayerGuideState state = playerStates.get(playerId);
        if (state == null || !state.isQueued()) return;

        GuideMilestone milestone = state.getQueuedMilestone();
        if (milestone == null) {
            state.clearQueue();
            return;
        }

        // Max retry limit — give up and let the trigger fire again naturally
        if (checkCount >= MAX_SAFETY_CHECKS) {
            LOGGER.atFine().log("Safety check timeout for '%s' on %s after %d checks",
                milestone.getId(), playerId.toString().substring(0, 8), checkCount);
            state.clearQueue();
            return;
        }

        PlayerRef playerRef = getPlayerRef(playerId);
        if (playerRef == null) {
            state.clearQueue();
            return;
        }

        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null || !world.isAlive()) {
            state.clearQueue();
            return;
        }

        world.execute(() -> {
            // Re-check state inside world thread (may have changed)
            if (!state.isQueued() || state.getQueuedMilestone() != milestone) return;

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                state.clearQueue();
                return;
            }

            Store<EntityStore> store = ref.getStore();

            if (hasHostileMobsNearby(store, ref)) {
                // Not safe yet — check again in 1 second
                scheduleSafetyCheck(playerId, checkCount + 1);
            } else {
                // Area is clear — show the popup now
                showPopupNow(playerId, milestone, state, playerRef, store);
            }
        });
    }

    /**
     * Scans for hostile mobs within the safety radius of the player using
     * Hytale's entity spatial resource (KD-tree).
     *
     * <p>A mob counts as "hostile nearby" if it:
     * <ul>
     *   <li>Has a {@link MobScalingComponent} (our RPG system processed it)</li>
     *   <li>Is classified as hostile (MINOR, HOSTILE, ELITE, or BOSS)</li>
     *   <li>Is not dead or dying</li>
     * </ul>
     *
     * <p>Must be called on the world thread.
     */
    @SuppressWarnings("unchecked")
    private boolean hasHostileMobsNearby(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        try {
            TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (playerTransform == null) return false;

            Vector3d position = playerTransform.getPosition();

            // Query the entity spatial resource (NPCs only, not players or items)
            ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> spatialType =
                EntityModule.get().getEntitySpatialResourceType();
            SpatialResource<Ref<EntityStore>, EntityStore> entitySpatial = store.getResource(spatialType);

            java.util.List<Ref<EntityStore>> results = SpatialResource.<EntityStore>getThreadLocalReferenceList();
            entitySpatial.getSpatialStructure().collect(position, SAFETY_CHECK_RADIUS, results);

            for (int i = 0; i < results.size(); i++) {
                Ref<EntityStore> entityRef = results.get(i);
                if (entityRef == null || !entityRef.isValid()) continue;

                // Check for MobScalingComponent — our RPG system's tag for combat mobs
                MobScalingComponent scaling = store.getComponent(entityRef, MobScalingComponent.getComponentType());
                if (scaling == null) continue;

                // Skip dead or dying mobs
                if (scaling.isDying()) continue;
                DeathComponent death = store.getComponent(entityRef, DeathComponent.getComponentType());
                if (death != null) continue;

                // Found a living hostile mob nearby
                if (scaling.getClassification().isHostile()) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            // If spatial query fails, assume safe (fail-open for UX)
            LOGGER.atFine().log("Safety check failed, assuming safe: %s", e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // POPUP DISPLAY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Shows the popup immediately. Called after the safety check passes.
     * Must be called on the world thread.
     */
    private void showPopupNow(
            @Nonnull UUID playerId,
            @Nonnull GuideMilestone milestone,
            @Nonnull PlayerGuideState state,
            @Nonnull PlayerRef playerRef,
            @Nonnull Store<EntityStore> store) {

        // Transition from QUEUED → SHOWING
        state.setPopupShowing(milestone);

        // Mark as completed in DB (idempotent)
        repository.markCompleted(playerId, milestone.getId());
        state.markCompleted(milestone.getId());

        GuidePopupPage page = new GuidePopupPage(
            playerRef,
            milestone,
            () -> onLearnMore(playerId, playerRef, milestone),
            () -> onDismiss(playerId)
        );

        page.open(store);

        LOGGER.atInfo().log("Showing guide popup '%s' to %s",
            milestone.getId(), playerRef.getUsername());
    }

    // ═══════════════════════════════════════════════════════════════════
    // POPUP CALLBACKS
    // ═══════════════════════════════════════════════════════════════════

    private void onLearnMore(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef, @Nonnull GuideMilestone milestone) {
        PlayerGuideState state = playerStates.get(playerId);
        if (state != null) {
            state.clearPopup();
        }

        if (!milestone.hasWikiLink()) return;

        if (voileAvailable) {
            try {
                CommandManager.get().handleCommand(playerRef, "voile " + milestone.getWikiTopic());
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to open Voile wiki for %s: %s",
                    milestone.getWikiTopic(), e.getMessage());
                sendWikiUrlFallback(playerRef, milestone);
            }
        } else {
            sendWikiUrlFallback(playerRef, milestone);
        }
    }

    private void sendWikiUrlFallback(@Nonnull PlayerRef playerRef, @Nonnull GuideMilestone milestone) {
        String topicPath = milestone.getWikiTopic();
        if (topicPath != null && topicPath.contains(":")) {
            String[] parts = topicPath.split(":");
            topicPath = parts[parts.length - 1];
        }

        String url = WIKI_BASE_URL + (topicPath != null ? topicPath : "getting-started");
        playerRef.sendMessage(
            com.hypixel.hytale.server.core.Message.empty()
                .insert(com.hypixel.hytale.server.core.Message.raw("[Guide] ").color("#FFD700").bold(true))
                .insert(com.hypixel.hytale.server.core.Message.raw("Read more here: ").color("#D0DCEA"))
                .insert(com.hypixel.hytale.server.core.Message.raw(url).color("#55FFFF"))
        );
    }

    private void onDismiss(@Nonnull UUID playerId) {
        PlayerGuideState state = playerStates.get(playerId);
        if (state != null) {
            state.clearPopup();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DAMAGE SUPPRESSION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called from RPGDamageSystem to check if damage should be suppressed.
     *
     * @return true if a guide popup is open and the milestone wants damage suppression
     */
    public boolean shouldSuppressDamage(@Nonnull UUID playerId) {
        PlayerGuideState state = playerStates.get(playerId);
        if (state == null) return false;

        if (!state.isPopupShowing()) return false;

        // Check safety valve — don't suppress damage forever
        if (state.isSafetyValveExpired(SAFETY_VALVE_MS)) {
            LOGGER.atWarning().log("Damage suppression safety valve expired for %s",
                playerId.toString().substring(0, 8));
            state.clearPopup();
            return false;
        }

        GuideMilestone current = state.getCurrentMilestone();
        return current != null && current.shouldSuppressDamage();
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if a milestone is completed for a player (in-memory cache).
     */
    public boolean isCompleted(@Nonnull UUID playerId, @Nonnull String milestoneId) {
        PlayerGuideState state = playerStates.get(playerId);
        return state != null && state.isCompleted(milestoneId);
    }

    /**
     * Checks if a popup is currently showing for a player.
     */
    public boolean isPopupShowing(@Nonnull UUID playerId) {
        PlayerGuideState state = playerStates.get(playerId);
        return state != null && state.isPopupShowing();
    }

    /**
     * Resets a milestone for a player (admin command). Removes from DB and in-memory cache.
     */
    public void resetMilestone(@Nonnull UUID playerId, @Nonnull String milestoneId) {
        repository.deleteMilestone(playerId, milestoneId);
        PlayerGuideState state = playerStates.get(playerId);
        if (state != null) {
            state.removeMilestone(milestoneId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GATEWAY PROXIMITY DETECTION
    // ═══════════════════════════════════════════════════════════════════

    private static final double GATEWAY_PROXIMITY_RADIUS = 5.0;
    private static final int MAX_GATEWAY_PROXIMITY_CHECKS = 120; // 4 minutes at 2s intervals

    /**
     * Starts periodic proximity checks for Ancient Gateways.
     * When the player gets within 5 blocks of any cached gateway,
     * the FIRST_GATEWAY milestone fires.
     *
     * <p>Call on player join/world entry. No-ops if already completed.
     */
    public void startGatewayProximityCheck(@Nonnull UUID playerId) {
        PlayerGuideState state = playerStates.get(playerId);
        if (state == null) return;
        if (state.isCompleted(GuideMilestone.FIRST_GATEWAY.getId())) return;

        scheduleGatewayProximityCheck(playerId, 0);
    }

    private void scheduleGatewayProximityCheck(@Nonnull UUID playerId, int checkCount) {
        try {
            scheduler.schedule(() -> performGatewayProximityCheck(playerId, checkCount), 2, TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Scheduler shut down during plugin disable
        }
    }

    private void performGatewayProximityCheck(@Nonnull UUID playerId, int checkCount) {
        PlayerGuideState state = playerStates.get(playerId);
        if (state == null) return;
        if (state.isCompleted(GuideMilestone.FIRST_GATEWAY.getId())) return;
        if (checkCount >= MAX_GATEWAY_PROXIMITY_CHECKS) return;

        PlayerRef playerRef = getPlayerRef(playerId);
        if (playerRef == null) return;

        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null || !world.isAlive()) return;

        // Skip instance worlds (realms/sanctum)
        String worldName = world.getName();
        if (worldName != null && worldName.startsWith("instance-")) {
            scheduleGatewayProximityCheck(playerId, checkCount + 1);
            return;
        }

        world.execute(() -> {
            if (state.isCompleted(GuideMilestone.FIRST_GATEWAY.getId())) return;

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;

            Store<EntityStore> store = ref.getStore();
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d pos = transform.getPosition();

            if (isNearGateway(playerRef.getWorldUuid(), pos.getX(), pos.getY(), pos.getZ())) {
                tryShow(playerId, GuideMilestone.FIRST_GATEWAY);
            } else {
                scheduleGatewayProximityCheck(playerId, checkCount + 1);
            }
        });
    }

    private boolean isNearGateway(@Nonnull UUID worldUuid, double px, double py, double pz) {
        try {
            io.github.larsonix.trailoforbis.TrailOfOrbis rpg = io.github.larsonix.trailoforbis.TrailOfOrbis.getInstanceOrNull();
            if (rpg == null || rpg.getRealmsManager() == null) return false;

            var upgradeManager = rpg.getRealmsManager().getGatewayUpgradeManager();
            if (upgradeManager == null) return false;

            double radiusSq = GATEWAY_PROXIMITY_RADIUS * GATEWAY_PROXIMITY_RADIUS;
            for (int[] gPos : upgradeManager.getRepository().getCachedPositionsForWorld(worldUuid)) {
                double dx = px - gPos[0];
                double dy = py - gPos[1];
                double dz = pz - gPos[2];
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cleanup on shutdown.
     */
    public void shutdown() {
        scheduler.shutdownNow();
        playerStates.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private boolean isPlayerInRealm(@Nonnull UUID playerId) {
        try {
            io.github.larsonix.trailoforbis.TrailOfOrbis rpg = io.github.larsonix.trailoforbis.TrailOfOrbis.getInstanceOrNull();
            if (rpg == null || rpg.getRealmsManager() == null) return false;
            return rpg.getRealmsManager().isPlayerInRealm(playerId);
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private PlayerRef getPlayerRef(@Nonnull UUID playerId) {
        try {
            return Universe.get().getPlayer(playerId);
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASS - Per-Player State
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Three-state machine per player:
     * <ul>
     *   <li><b>IDLE</b>: No popup queued or showing</li>
     *   <li><b>QUEUED</b>: Milestone queued, safety checks running</li>
     *   <li><b>SHOWING</b>: Popup displayed on screen</li>
     * </ul>
     */
    private static class PlayerGuideState {
        private final Set<String> completedMilestones;

        // QUEUED state
        private volatile GuideMilestone queuedMilestone;

        // SHOWING state
        private volatile boolean popupShowing;
        private volatile GuideMilestone currentMilestone;
        private volatile long popupShownAt;

        PlayerGuideState(@Nonnull Set<String> completed) {
            this.completedMilestones = ConcurrentHashMap.newKeySet();
            this.completedMilestones.addAll(completed);
        }

        // --- Completed milestones ---

        boolean isCompleted(@Nonnull String milestoneId) {
            return completedMilestones.contains(milestoneId);
        }

        void markCompleted(@Nonnull String milestoneId) {
            completedMilestones.add(milestoneId);
        }

        void removeMilestone(@Nonnull String milestoneId) {
            completedMilestones.remove(milestoneId);
        }

        // --- QUEUED state ---

        boolean isQueued() {
            return queuedMilestone != null;
        }

        @Nullable
        GuideMilestone getQueuedMilestone() {
            return queuedMilestone;
        }

        void queueMilestone(@Nonnull GuideMilestone milestone) {
            this.queuedMilestone = milestone;
        }

        void clearQueue() {
            this.queuedMilestone = null;
        }

        // --- SHOWING state ---

        boolean isPopupShowing() {
            return popupShowing;
        }

        @Nullable
        GuideMilestone getCurrentMilestone() {
            return currentMilestone;
        }

        void setPopupShowing(@Nonnull GuideMilestone milestone) {
            this.queuedMilestone = null; // Clear queue on transition to SHOWING
            this.popupShowing = true;
            this.currentMilestone = milestone;
            this.popupShownAt = System.currentTimeMillis();
        }

        void clearPopup() {
            this.popupShowing = false;
            this.currentMilestone = null;
            this.popupShownAt = 0;
        }

        boolean isSafetyValveExpired(long timeoutMs) {
            return popupShownAt > 0 && (System.currentTimeMillis() - popupShownAt) > timeoutMs;
        }
    }
}
