package io.github.larsonix.trailoforbis.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches vanilla (unmodified) stat values for players.
 *
 * <p>Critical for movement speed calculations - prevents cascading multipliers
 * when stats are recalculated (e.g., after skill tree deallocation).
 *
 * <p>Pattern: Cache on player join, use cached values for all stat calculations.
 *
 * <p><b>Bug this fixes:</b> Without caching, each stat recalculation reads the
 * previously-modified MovementSettings and applies multipliers again, causing
 * exponential speed growth:
 * <ul>
 *   <li>Recalculation 1: base × 1.1 = 1.815</li>
 *   <li>Recalculation 2: 1.815 × 1.1 = 1.9965</li>
 *   <li>Recalculation 3: 1.9965 × 1.1 = 2.196 (continues growing!)</li>
 * </ul>
 */
public final class VanillaStatCache {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Player UUID -> Cached vanilla movement settings */
    private static final Map<UUID, MovementSettings> movementCache = new ConcurrentHashMap<>();

    // Correct vanilla fallback values from MovementManager.MASTER_DEFAULT
    // These are CRITICAL for correct movement speed when cache is empty
    private static final float FALLBACK_SPRINT_SPEED = 1.65f;
    private static final float FALLBACK_RUN_SPEED = 1.0f;
    private static final float FALLBACK_BACKWARD_RUN_SPEED = 0.65f;
    private static final float FALLBACK_STRAFE_RUN_SPEED = 0.8f;
    private static final float FALLBACK_WALK_SPEED = 0.3f;        // NOT 1.0! Walk is ~30% of run speed
    private static final float FALLBACK_FORWARD_CROUCH_SPEED = 0.55f;
    private static final float FALLBACK_BACKWARD_CROUCH_SPEED = 0.4f;
    private static final float FALLBACK_STRAFE_CROUCH_SPEED = 0.45f;
    private static final float FALLBACK_JUMP_FORCE = 11.8f;
    private static final float FALLBACK_CLIMB_SPEED = 0.035f;

    private VanillaStatCache() {
        // Utility class - no instantiation
    }

    /**
     * Caches vanilla movement settings when player joins.
     * Should be called once per player session.
     *
     * @param uuid Player's UUID
     * @param store Entity store
     * @param entityRef Entity reference
     */
    public static void cacheOnJoin(
        @Nonnull UUID uuid,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getLiveMovementSettings(store, entityRef);
        if (settings != null) {
            movementCache.put(uuid, new MovementSettings(settings));
            LOGGER.atFine().log("Cached vanilla movement settings for %s", uuid.toString().substring(0, 8));
        } else {
            LOGGER.atWarning().log(
                "Failed to cache vanilla movement settings for %s - will use fallback defaults",
                uuid.toString().substring(0, 8));
        }
    }

    /**
     * Gets the CURRENT live movement settings (not cached).
     * Used internally for cache initialization.
     */
    @Nullable
    public static MovementSettings getLiveMovementSettings(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementManager movementManager = store.getComponent(entityRef, MovementManager.getComponentType());
        if (movementManager == null) {
            return null;
        }
        return movementManager.getSettings();
    }

    /**
     * Gets cached vanilla movement settings for a player.
     * Returns a copy to prevent external modification.
     *
     * @param uuid Player's UUID
     * @return Cached settings, or null if not cached
     */
    @Nullable
    public static MovementSettings getMovementSettings(@Nonnull UUID uuid) {
        MovementSettings cached = movementCache.get(uuid);
        return cached != null ? new MovementSettings(cached) : null;
    }

    /**
     * Gets cached vanilla forward sprint speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla sprint speed multiplier, or 1.65 if not cached
     */
    public static float getBaseForwardSprintSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.forwardSprintSpeedMultiplier : FALLBACK_SPRINT_SPEED;
    }

    /**
     * Gets cached vanilla forward run speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla run speed multiplier, or 1.0 if not cached
     */
    public static float getBaseForwardRunSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.forwardRunSpeedMultiplier : FALLBACK_RUN_SPEED;
    }

    /**
     * Gets cached vanilla backward run speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla backward run speed multiplier, or 0.65 if not cached
     */
    public static float getBaseBackwardRunSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.backwardRunSpeedMultiplier : FALLBACK_BACKWARD_RUN_SPEED;
    }

    /**
     * Gets cached vanilla strafe run speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla strafe run speed multiplier, or 0.8 if not cached
     */
    public static float getBaseStrafeRunSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.strafeRunSpeedMultiplier : FALLBACK_STRAFE_RUN_SPEED;
    }

    /**
     * Gets cached vanilla forward walk speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla walk speed multiplier, or 0.3 if not cached
     */
    public static float getBaseForwardWalkSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.forwardWalkSpeedMultiplier : FALLBACK_WALK_SPEED;
    }

    /**
     * Gets cached vanilla backward walk speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla backward walk speed multiplier, or 0.3 if not cached
     */
    public static float getBaseBackwardWalkSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.backwardWalkSpeedMultiplier : FALLBACK_WALK_SPEED;
    }

    /**
     * Gets cached vanilla strafe walk speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla strafe walk speed multiplier, or 0.3 if not cached
     */
    public static float getBaseStrafeWalkSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.strafeWalkSpeedMultiplier : FALLBACK_WALK_SPEED;
    }

    /**
     * Gets cached vanilla forward crouch speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla crouch speed multiplier, or 0.55 if not cached
     */
    public static float getBaseForwardCrouchSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.forwardCrouchSpeedMultiplier : FALLBACK_FORWARD_CROUCH_SPEED;
    }

    /**
     * Gets cached vanilla backward crouch speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla backward crouch speed multiplier, or 0.4 if not cached
     */
    public static float getBaseBackwardCrouchSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.backwardCrouchSpeedMultiplier : FALLBACK_BACKWARD_CROUCH_SPEED;
    }

    /**
     * Gets cached vanilla strafe crouch speed multiplier.
     *
     * @param uuid Player's UUID
     * @return Vanilla strafe crouch speed multiplier, or 0.45 if not cached
     */
    public static float getBaseStrafeCrouchSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.strafeCrouchSpeedMultiplier : FALLBACK_STRAFE_CROUCH_SPEED;
    }

    /**
     * Gets cached vanilla jump force.
     *
     * @param uuid Player's UUID
     * @return Vanilla jump force, or 11.8 if not cached
     */
    public static float getBaseJumpForce(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.jumpForce : FALLBACK_JUMP_FORCE;
    }

    /**
     * Gets cached vanilla climb speed.
     *
     * @param uuid Player's UUID
     * @return Vanilla climb speed, or 0.035 if not cached
     */
    public static float getBaseClimbSpeed(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.climbSpeed : FALLBACK_CLIMB_SPEED;
    }

    /**
     * Gets cached vanilla lateral climb speed.
     *
     * @param uuid Player's UUID
     * @return Vanilla lateral climb speed, or 0.035 if not cached
     */
    public static float getBaseClimbSpeedLateral(@Nonnull UUID uuid) {
        MovementSettings settings = movementCache.get(uuid);
        return settings != null ? settings.climbSpeedLateral : FALLBACK_CLIMB_SPEED;
    }

    /**
     * Clears cached data for a player (on disconnect).
     *
     * @param uuid Player's UUID
     */
    public static void clear(@Nonnull UUID uuid) {
        movementCache.remove(uuid);
    }

    /**
     * Clears all cached data (on plugin shutdown).
     */
    public static void clearAll() {
        movementCache.clear();
    }

    /**
     * Checks if a player has cached stats.
     *
     * @param uuid Player's UUID
     * @return true if cached, false otherwise
     */
    public static boolean hasCached(@Nonnull UUID uuid) {
        return movementCache.containsKey(uuid);
    }

    /**
     * Gets the number of cached players.
     *
     * @return Number of cached players
     */
    public static int getCacheSize() {
        return movementCache.size();
    }
}
