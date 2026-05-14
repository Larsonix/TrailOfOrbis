package io.github.larsonix.trailoforbis.combat.indicators;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ui.hud.PersistentHud;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages invisible "ghost" entities used as CombatText anchors for self-feedback.
 *
 * <p>Hytale's client does not render CombatText on the local player entity (the camera
 * entity). To show avoidance text ("Dodged", "Blocked") and recovery text ("+15" life
 * steal) to the player, we spawn a separate invisible entity above their head and queue
 * CombatText on it instead.
 *
 * <p>Each player gets ONE persistent ghost entity per world. The ghost is:
 * <ul>
 *   <li>Invisible — {@code ProjectileComponent("Projectile")} shell, no model</li>
 *   <li>Intangible — no hitbox, no collision, no nameplate</li>
 *   <li>Network-tracked — has {@code NetworkId}, appears in {@code EntityViewer.visible}</li>
 *   <li>Positioned above the player — updated lazily before each CombatText queue</li>
 * </ul>
 *
 * <p>Implements {@link PersistentHud} to integrate with {@code HudLifecycleManager}
 * for automatic drain→restore across world transitions, disconnect cleanup, and shutdown.
 *
 * @see CombatIndicatorService#sendSelfCombatText
 */
public final class CombatFeedbackGhostManager implements PersistentHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Distance in blocks from the player's eye to place the ghost in the look direction. */
    private static final double GHOST_LOOK_DISTANCE = 0.5;

    /** Approximate player eye height above feet position. */
    private static final double PLAYER_EYE_HEIGHT = 1.62;

    /** Tracks active ghost entity refs per player (keyed by UUID). */
    private final Map<UUID, GhostEntry> activeGhosts = new ConcurrentHashMap<>();

    /** Per-player cooldown to prevent infinite restore loops when ghost entities despawn. */
    private final Map<UUID, Long> lastGhostRestore = new ConcurrentHashMap<>();
    private static final long GHOST_RESTORE_COOLDOWN_NS = TimeUnit.SECONDS.toNanos(60);

    /**
     * Tracks one ghost entity per player.
     *
     * @param ghostRef  Reference to the ghost entity in the world's entity store
     * @param worldRef  The world the ghost was spawned in (for cleanup validation)
     */
    private record GhostEntry(
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull World world
    ) {}

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API — called by CombatIndicatorService
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the ghost entity ref for a player, or null if not available.
     *
     * <p>Before returning, updates the ghost's position to match the player's
     * current position + Y offset. This lazy update avoids per-tick overhead
     * while ensuring the ghost is at the right place when text is queued.
     *
     * @param playerId The player's UUID
     * @param store    The current entity store
     * @param playerRef The player's entity ref (for position lookup)
     * @return The ghost entity ref, or null if ghost not spawned or invalid
     */
    @Nullable
    public Ref<EntityStore> getGhostRef(
        @Nonnull UUID playerId,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        GhostEntry entry = activeGhosts.get(playerId);
        if (entry == null) {
            return null;
        }

        Ref<EntityStore> ghostRef = entry.ghostRef();
        if (!ghostRef.isValid()) {
            activeGhosts.remove(playerId);
            return null;
        }

        // Lazy position update — move ghost above player's current position
        updateGhostPosition(store, ghostRef, playerRef);

        return ghostRef;
    }

    /**
     * Checks whether the ghost is in the player's visible set.
     *
     * <p>The ghost needs one tick after spawning to appear in {@code CollectVisible}.
     * This method should be called before queuing CombatText to avoid
     * {@code IllegalArgumentException} from {@code queueUpdate}.
     *
     * @param viewer   The player's entity viewer
     * @param ghostRef The ghost entity ref
     * @return true if the ghost is visible and CombatText can be queued on it
     */
    public boolean isGhostVisible(
        @Nonnull EntityTrackerSystems.EntityViewer viewer,
        @Nonnull Ref<EntityStore> ghostRef
    ) {
        return viewer.visible.contains(ghostRef);
    }

    // ═══════════════════════════════════════════════════════════════
    // PersistentHud LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Nonnull
    @Override
    public String hudName() {
        return "combat-feedback-ghost";
    }

    /**
     * Discards the ghost entity reference during a world transition.
     *
     * <p>The ghost entity is bound to its world ��� when the world is drained,
     * the entity is destroyed automatically. We only need to clear our tracking.
     * NEVER attempt to remove the entity here (world may already be shutting down).
     */
    @Override
    public void discardStale(@Nonnull UUID playerId) {
        GhostEntry removed = activeGhosts.remove(playerId);
        lastGhostRestore.remove(playerId);
        if (removed != null) {
            LOGGER.atFine().log("Discarded combat feedback ghost for %s (world transition)",
                playerId.toString().substring(0, 8));
        }
    }

    @Override
    public boolean isActive(@Nonnull UUID playerId) {
        GhostEntry entry = activeGhosts.get(playerId);
        return entry != null && entry.ghostRef().isValid();
    }

    /**
     * Spawns a fresh ghost entity for a player after a world transition.
     *
     * <p>Called from {@code HudLifecycleManager.restoreAll()} at LATE priority,
     * after player stats and data are fully loaded. The ghost is spawned at the
     * player's current position + Y offset.
     *
     * <p>Idempotent — if a valid ghost already exists, this is a no-op.
     */
    @Override
    public void restore(
        @Nonnull UUID playerId,
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        // Dedup — don't spawn if already active (handles double ClientReady)
        if (isActive(playerId)) {
            return;
        }

        // Prevent infinite restore loop — ghost entities may despawn (entity lifecycle),
        // making isActive() return false every health check cycle. Without this cooldown,
        // the health checker restores every 5 seconds forever (confirmed in logs).
        Long lastRestore = lastGhostRestore.get(playerId);
        if (lastRestore != null && (System.nanoTime() - lastRestore) < GHOST_RESTORE_COOLDOWN_NS) {
            return;
        }
        lastGhostRestore.put(playerId, System.nanoTime());

        // Get player position for initial ghost placement
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            LOGGER.atFine().log("Cannot spawn combat ghost for %s — player entity ref unavailable",
                playerId.toString().substring(0, 8));
            return;
        }

        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Vector3d playerPos = transform.getPosition();
        Vector3d ghostPos = computeGhostPosition(store, playerEntityRef, playerPos);

        // Resolve the world for tracking
        World world = PlayerWorldCache.getPlayerWorld(playerId).orElse(null);
        if (world == null) {
            return;
        }

        Ref<EntityStore> ghostRef = spawnGhostEntity(store, ghostPos);
        if (ghostRef == null) {
            LOGGER.atWarning().log("Failed to spawn combat feedback ghost for %s",
                playerId.toString().substring(0, 8));
            return;
        }

        activeGhosts.put(playerId, new GhostEntry(ghostRef, world));
        LOGGER.atFine().log("Spawned combat feedback ghost for %s at (%.0f, %.0f, %.0f)",
            playerId.toString().substring(0, 8), ghostPos.x, ghostPos.y, ghostPos.z);
    }

    /**
     * Removes the ghost entity on player disconnect.
     *
     * <p>Unlike {@link #discardStale}, this actively removes the entity from the world
     * since the world is still alive — just the player is leaving.
     */
    @Override
    public void removeOnDisconnect(@Nonnull UUID playerId) {
        lastGhostRestore.remove(playerId);
        GhostEntry entry = activeGhosts.remove(playerId);
        if (entry == null) {
            return;
        }

        if (entry.ghostRef().isValid() && entry.world().isAlive()) {
            try {
                Store<EntityStore> store = entry.world().getEntityStore().getStore();
                store.removeEntity(entry.ghostRef(), RemoveReason.REMOVE);
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to remove combat ghost on disconnect for %s: %s",
                    playerId.toString().substring(0, 8), e.getMessage());
            }
        }
    }

    /**
     * Removes all ghost entities during plugin shutdown.
     */
    @Override
    public void shutdown() {
        for (Map.Entry<UUID, GhostEntry> entry : activeGhosts.entrySet()) {
            GhostEntry ghost = entry.getValue();
            if (ghost.ghostRef().isValid() && ghost.world().isAlive()) {
                try {
                    Store<EntityStore> store = ghost.world().getEntityStore().getStore();
                    store.removeEntity(ghost.ghostRef(), RemoveReason.REMOVE);
                } catch (Exception e) {
                    // Best-effort during shutdown
                }
            }
        }
        activeGhosts.clear();
        LOGGER.atInfo().log("CombatFeedbackGhostManager shut down");
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — Entity spawning and position management
    // ═══════════════════════════════════════════════════════════════

    /**
     * Spawns an invisible ghost entity at the given position.
     *
     * <p>Uses the proven ProjectileComponent shell pattern from
     * {@code TooAdminTestColorCommand.spawnGhostEntity()}. The entity is:
     * <ul>
     *   <li>Invisible — ProjectileComponent provides no visual model</li>
     *   <li>Network-tracked — NetworkId enables EntityViewer visibility</li>
     *   <li>Intangible — no hitbox or collision components</li>
     * </ul>
     *
     * <p>CRITICAL: {@code ProjectileComponent.initialize()} must be called
     * BEFORE adding NetworkId, or the entity tracker will fail.
     *
     * @param store    The entity store to spawn in
     * @param position The initial world position
     * @return The spawned entity's ref, or null on failure
     */
    @Nullable
    private Ref<EntityStore> spawnGhostEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position
    ) {
        try {
            Holder<EntityStore> holder = store.getRegistry().newHolder();

            // Invisible shell — no model, no visual
            ProjectileComponent projectile = new ProjectileComponent("Projectile");
            holder.putComponent(ProjectileComponent.getComponentType(), projectile);

            // Position in world
            holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, new Vector3f(0f, 0f, 0f)));

            // Server-side identity
            holder.ensureComponent(UUIDComponent.getComponentType());

            // Entity UI setup — enables client-side CombatText rendering.
            // Without UIComponentList, UIComponentSystems.Setup skips non-LivingEntity
            // entities, and the client silently drops all CombatText updates.
            holder.ensureComponent(UIComponentList.getComponentType());
            UIComponentList uiComponents = holder.getComponent(UIComponentList.getComponentType());
            if (uiComponents != null) {
                uiComponents.update();
            }

            // Initialize projectile BEFORE NetworkId (required ordering)
            if (projectile.getProjectile() == null) {
                projectile.initialize();
            }

            // Network tracking — enables CollectVisible to include this entity
            holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));

            return store.addEntity(holder, AddReason.SPAWN);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn combat feedback ghost entity");
            return null;
        }
    }

    /**
     * Moves the ghost entity into the player's look direction.
     *
     * <p>Called lazily from {@link #getGhostRef} right before CombatText is queued.
     * Places the ghost at the player's eye position + look direction * distance,
     * so floating text appears in the center of the player's view.
     *
     * <p>Falls back to a fixed Y offset above the player if HeadRotation is
     * unavailable (e.g., during early initialization).
     */
    private void updateGhostPosition(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        if (!playerRef.isValid() || !ghostRef.isValid()) {
            return;
        }

        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            return;
        }

        TransformComponent ghostTransform = store.getComponent(ghostRef, TransformComponent.getComponentType());
        if (ghostTransform == null) {
            return;
        }

        Vector3d targetPos = computeGhostPosition(store, playerRef, playerTransform.getPosition());
        Vector3d ghostPos = ghostTransform.getPosition();
        ghostPos.x = targetPos.x;
        ghostPos.y = targetPos.y;
        ghostPos.z = targetPos.z;
    }

    /**
     * Computes the ghost's world position: player eye + look direction * distance.
     *
     * <p>Uses {@link HeadRotation} to get the player's actual camera direction
     * (pitch + yaw), so the ghost is placed directly where the player is looking.
     * CombatText rendered on the ghost entity will appear in the center of the
     * player's screen.
     *
     * @param store     The entity store
     * @param playerRef The player entity
     * @param playerPos The player's feet position
     * @return The computed ghost world position
     */
    @Nonnull
    private Vector3d computeGhostPosition(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Vector3d playerPos
    ) {
        // Eye position = feet + eye height
        double eyeX = playerPos.x;
        double eyeY = playerPos.y + PLAYER_EYE_HEIGHT;
        double eyeZ = playerPos.z;

        // Try to get head rotation for look direction
        HeadRotation headRotation = store.getComponent(playerRef, HeadRotation.getComponentType());
        if (headRotation != null) {
            Vector3d direction = headRotation.getDirection();
            return new Vector3d(
                eyeX + direction.x * GHOST_LOOK_DISTANCE,
                eyeY + direction.y * GHOST_LOOK_DISTANCE,
                eyeZ + direction.z * GHOST_LOOK_DISTANCE
            );
        }

        // Fallback: place above head if HeadRotation unavailable
        return new Vector3d(eyeX, eyeY + 0.5, eyeZ);
    }
}
