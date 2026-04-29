package io.github.larsonix.trailoforbis.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.attributes.BaseStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Reads vanilla Hytale base stat values from the game API.
 *
 * <p>This class provides a centralized way to access vanilla base values
 * instead of relying on hardcoded constants. It provides sensible defaults
 * when the game API is unavailable.
 *
 * <p><b>Usage:</b>
 * <pre>
 * float baseHealth = VanillaStatReader.getBaseHealth(store, entityRef);
 * MovementSettings baseMovement = VanillaStatReader.getBaseMovementSettings(store, entityRef);
 * </pre>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. All read operations
 * are non-blocking and use no mutable shared state.
 */
public final class VanillaStatReader {

    // Resource stat defaults (vanilla Hytale)
    private static final float DEFAULT_HEALTH = 100.0f;
    private static final float DEFAULT_MANA = 0.0f;
    private static final float DEFAULT_STAMINA = 10.0f;
    private static final float DEFAULT_OXYGEN = 100.0f;
    private static final float DEFAULT_SIGNATURE_ENERGY = 0.0f;

    // Movement defaults from MovementManager.MASTER_DEFAULT
    private static final float DEFAULT_JUMP_FORCE = 11.8f;
    private static final float DEFAULT_CLIMB_SPEED = 0.035f;
    private static final float DEFAULT_CLIMB_SPEED_LATERAL = 0.035f;
    private static final float DEFAULT_BASE_SPEED = 5.5f;
    private static final float DEFAULT_ACCELERATION = 0.1f;
    private static final float DEFAULT_SPRINT_SPEED = 1.65f;
    private static final float DEFAULT_RUN_SPEED = 1.0f;
    private static final float DEFAULT_BACKWARD_RUN_SPEED = 0.65f;
    private static final float DEFAULT_STRAFE_RUN_SPEED = 0.8f;
    private static final float DEFAULT_WALK_SPEED = 0.3f;
    private static final float DEFAULT_CROUCH_SPEED = 0.55f;
    private static final float DEFAULT_BACKWARD_CROUCH_SPEED = 0.4f;
    private static final float DEFAULT_STRAFE_CROUCH_SPEED = 0.45f;

    private VanillaStatReader() {
        // Utility class - no instantiation
    }

    /**
     * Reads all base stats from the entity at once.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return A BaseStats object containing the read values
     */
    public static BaseStats readBaseStats(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        return new BaseStats(
            getBaseHealth(store, entityRef),
            getBaseMana(store, entityRef),
            getBaseStamina(store, entityRef),
            getBaseOxygen(store, entityRef),
            getBaseSignatureEnergy(store, entityRef)
        );
    }

    /**
     * Gets the base health value from the entity's stat map.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base health value (default: 100)
     */
    public static float getBaseHealth(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        return getResourceBaseValue(store, entityRef, DefaultEntityStatTypes.getHealth(), DEFAULT_HEALTH);
    }

    /**
     * Gets the base mana value from the entity's stat map.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base mana value (default: 100)
     */
    public static float getBaseMana(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        return getResourceBaseValue(store, entityRef, DefaultEntityStatTypes.getMana(), DEFAULT_MANA);
    }

    /**
     * Gets the base stamina value from the entity's stat map.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base stamina value (default: 100)
     */
    public static float getBaseStamina(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        return getResourceBaseValue(store, entityRef, DefaultEntityStatTypes.getStamina(), DEFAULT_STAMINA);
    }

    /**
     * Gets the base oxygen value from the entity's stat map.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base oxygen value (default: 100)
     */
    public static float getBaseOxygen(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        return getResourceBaseValue(store, entityRef, DefaultEntityStatTypes.getOxygen(), DEFAULT_OXYGEN);
    }

    /**
     * Gets the base signature energy value from the entity's stat map.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base signature energy value (default: 100)
     */
    public static float getBaseSignatureEnergy(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        return getResourceBaseValue(store, entityRef, DefaultEntityStatTypes.getSignatureEnergy(), DEFAULT_SIGNATURE_ENERGY);
    }

    /**
     * Gets the base movement settings from the entity's MovementManager.
     *
     * <p>This returns a copy of the vanilla base movement settings that
     * can be modified without affecting the entity's actual settings.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return A copy of the base movement settings, or defaults if unavailable
     */
    @Nullable
    public static MovementSettings getBaseMovementSettings(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        try {
            MovementManager movementManager = store.getComponent(entityRef, MovementManager.getComponentType());
            if (movementManager == null) {
                return createDefaultMovementSettings();
            }

            MovementSettings settings = movementManager.getSettings();
            return settings != null ? new MovementSettings(settings) : createDefaultMovementSettings();
        } catch (Exception e) {
            return createDefaultMovementSettings();
        }
    }

    /**
     * Gets the base jump force from MovementSettings.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base jump force value (default: 11.8)
     */
    public static float getBaseJumpForce(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.jumpForce : DEFAULT_JUMP_FORCE;
    }

    /**
     * Gets the base climb speed from MovementSettings.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base climb speed value (default: 0.035)
     */
    public static float getBaseClimbSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.climbSpeed : DEFAULT_CLIMB_SPEED;
    }

    /**
     * Gets the base climb lateral speed from MovementSettings.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base climb lateral speed value (default: 0.035)
     */
    public static float getBaseClimbSpeedLateral(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.climbSpeedLateral : DEFAULT_CLIMB_SPEED_LATERAL;
    }

    /**
     * Gets the base forward sprint speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base sprint speed multiplier (default: 1.65)
     */
    public static float getBaseForwardSprintSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.forwardSprintSpeedMultiplier : DEFAULT_SPRINT_SPEED;
    }

    /**
     * Gets the base forward run speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base run speed multiplier (default: 1.0)
     */
    public static float getBaseForwardRunSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.forwardRunSpeedMultiplier : DEFAULT_RUN_SPEED;
    }

    /**
     * Gets the base backward run speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base backward run speed multiplier (default: 0.65)
     */
    public static float getBaseBackwardRunSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.backwardRunSpeedMultiplier : DEFAULT_BACKWARD_RUN_SPEED;
    }

    /**
     * Gets the base strafe run speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base strafe run speed multiplier (default: 0.8)
     */
    public static float getBaseStrafeRunSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.strafeRunSpeedMultiplier : DEFAULT_STRAFE_RUN_SPEED;
    }

    /**
     * Gets the base forward walk speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base walk speed multiplier (default: 0.3)
     */
    public static float getBaseForwardWalkSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.forwardWalkSpeedMultiplier : DEFAULT_WALK_SPEED;
    }

    /**
     * Gets the base backward walk speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base backward walk speed multiplier (default: 0.3)
     */
    public static float getBaseBackwardWalkSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.backwardWalkSpeedMultiplier : DEFAULT_WALK_SPEED;
    }

    /**
     * Gets the base strafe walk speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base strafe walk speed multiplier (default: 0.3)
     */
    public static float getBaseStrafeWalkSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.strafeWalkSpeedMultiplier : DEFAULT_WALK_SPEED;
    }

    /**
     * Gets the base forward crouch speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base crouch speed multiplier (default: 0.55)
     */
    public static float getBaseForwardCrouchSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.forwardCrouchSpeedMultiplier : DEFAULT_CROUCH_SPEED;
    }

    /**
     * Gets the base backward crouch speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base backward crouch speed multiplier (default: 0.4)
     */
    public static float getBaseBackwardCrouchSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.backwardCrouchSpeedMultiplier : DEFAULT_BACKWARD_CROUCH_SPEED;
    }

    /**
     * Gets the base strafe crouch speed multiplier.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base strafe crouch speed multiplier (default: 0.45)
     */
    public static float getBaseStrafeCrouchSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.strafeCrouchSpeedMultiplier : DEFAULT_STRAFE_CROUCH_SPEED;
    }

    /**
     * Gets the base acceleration value.
     *
     * @param store The entity store
     * @param entityRef The entity reference
     * @return The base acceleration value (default: 0.1)
     */
    public static float getBaseAcceleration(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.acceleration : DEFAULT_ACCELERATION;
    }

     /**
      * Gets the base speed value (ground movement).
      *
      * @param store The entity store
      * @param entityRef The entity reference
      * @return The base speed value (default: 5.5)
      */
    public static float getBaseSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementSettings settings = getBaseMovementSettings(store, entityRef);
        return settings != null ? settings.baseSpeed : DEFAULT_BASE_SPEED;
    }

    // ==================== Cached Vanilla Values (for Stat Application) ====================

    /**
     * Gets base forward sprint speed using CACHED vanilla value.
     *
     * <p>CRITICAL: This method uses VanillaStatCache to prevent the cascading
     * multiplier bug where each stat recalculation compounds speed bonuses.
     *
     * @param uuid Player's UUID
     * @return Vanilla sprint speed multiplier from cache
     */
    public static float getBaseForwardSprintSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseForwardSprintSpeed(uuid);
    }

    /**
     * Gets base forward run speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla run speed multiplier from cache
     */
    public static float getBaseForwardRunSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseForwardRunSpeed(uuid);
    }

    /**
     * Gets base backward run speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla backward run speed multiplier from cache
     */
    public static float getBaseBackwardRunSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseBackwardRunSpeed(uuid);
    }

    /**
     * Gets base strafe run speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla strafe run speed multiplier from cache
     */
    public static float getBaseStrafeRunSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseStrafeRunSpeed(uuid);
    }

    /**
     * Gets base forward walk speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla walk speed multiplier from cache
     */
    public static float getBaseForwardWalkSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseForwardWalkSpeed(uuid);
    }

    /**
     * Gets base backward walk speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla backward walk speed multiplier from cache
     */
    public static float getBaseBackwardWalkSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseBackwardWalkSpeed(uuid);
    }

    /**
     * Gets base strafe walk speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla strafe walk speed multiplier from cache
     */
    public static float getBaseStrafeWalkSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseStrafeWalkSpeed(uuid);
    }

    /**
     * Gets base forward crouch speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla crouch speed multiplier from cache
     */
    public static float getBaseForwardCrouchSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseForwardCrouchSpeed(uuid);
    }

    /**
     * Gets base backward crouch speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla backward crouch speed multiplier from cache
     */
    public static float getBaseBackwardCrouchSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseBackwardCrouchSpeed(uuid);
    }

    /**
     * Gets base strafe crouch speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla strafe crouch speed multiplier from cache
     */
    public static float getBaseStrafeCrouchSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseStrafeCrouchSpeed(uuid);
    }

    /**
     * Gets base jump force using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla jump force from cache
     */
    public static float getBaseJumpForce(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseJumpForce(uuid);
    }

    /**
     * Gets base climb speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla climb speed from cache
     */
    public static float getBaseClimbSpeed(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseClimbSpeed(uuid);
    }

    /**
     * Gets base lateral climb speed using CACHED vanilla value.
     *
     * @param uuid Player's UUID
     * @return Vanilla lateral climb speed from cache
     */
    public static float getBaseClimbSpeedLateral(@Nonnull UUID uuid) {
        return VanillaStatCache.getBaseClimbSpeedLateral(uuid);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Returns the known vanilla base value for a resource stat.
     *
     * <p>Always returns the hardcoded default — never reads from EntityStatMap.
     * Reading getMax() from the stat map creates a feedback loop because it
     * includes ALL modifiers (vanilla equipment + RPG + effects). Since tick
     * system ordering is non-deterministic, the read can happen before or after
     * vanilla equipment modifiers are applied, producing inconsistent "base"
     * values that cause health to oscillate between correct and incorrect.
     *
     * <p>The defaults are Hytale's vanilla player base values. Our RPG system
     * is the sole authority on player stats — the true base is always known.
     */
    private static float getResourceBaseValue(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef,
            int statIndex,
            float defaultValue
    ) {
        return defaultValue;
    }

    private static MovementSettings createDefaultMovementSettings() {
        MovementSettings settings = new MovementSettings();
        settings.jumpForce = DEFAULT_JUMP_FORCE;
        settings.climbSpeed = DEFAULT_CLIMB_SPEED;
        settings.climbSpeedLateral = DEFAULT_CLIMB_SPEED_LATERAL;
        settings.baseSpeed = DEFAULT_BASE_SPEED;
        settings.acceleration = DEFAULT_ACCELERATION;
        settings.forwardSprintSpeedMultiplier = DEFAULT_SPRINT_SPEED;
        settings.forwardRunSpeedMultiplier = DEFAULT_RUN_SPEED;
        settings.backwardRunSpeedMultiplier = DEFAULT_BACKWARD_RUN_SPEED;
        settings.strafeRunSpeedMultiplier = DEFAULT_STRAFE_RUN_SPEED;
        settings.forwardWalkSpeedMultiplier = DEFAULT_WALK_SPEED;
        settings.backwardWalkSpeedMultiplier = DEFAULT_WALK_SPEED;
        settings.strafeWalkSpeedMultiplier = DEFAULT_WALK_SPEED;
        settings.forwardCrouchSpeedMultiplier = DEFAULT_CROUCH_SPEED;
        settings.backwardCrouchSpeedMultiplier = DEFAULT_BACKWARD_CROUCH_SPEED;
        settings.strafeCrouchSpeedMultiplier = DEFAULT_STRAFE_CROUCH_SPEED;
        return settings;
    }
}
