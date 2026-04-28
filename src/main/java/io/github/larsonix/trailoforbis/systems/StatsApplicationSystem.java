package io.github.larsonix.trailoforbis.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.attributes.StatMapBridge;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies ComputedStats to Hytale's Entity Component System.
 *
 * <p>This system bridges the gap between our RPG stat calculations
 * and Hytale's ECS. It applies:
 * <ul>
 *   <li>Health/Mana/Stamina bonuses via EntityStatMap modifiers</li>
 *   <li>Movement speed bonuses via MovementSettings</li>
 * </ul>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Reads vanilla base values from the game API (not hardcoded)</li>
 *   <li>Preserves current resource percentages when updating max values</li>
 *   <li>Applies all movement bonuses (walk, run, sprint, crouch, climb, jump)</li>
 *   <li>Syncs ComputedStats cache with actual ECS values</li>
 * </ul>
 *
 * <p><b>Usage:</b> Call {@link #applyAllStats} after recalculating player stats
 * (e.g., on join, level up, attribute allocation).
 */
public final class StatsApplicationSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Unique modifier key for RPG attribute bonuses */
    private static final String RPG_MODIFIER_ID = "rpg_attribute_bonus";

    private StatsApplicationSystem() {
        // Utility class - no instantiation
    }

    /**
     * Applies stats to ECS AND syncs ComputedStats cache with actual ECS values.
     *
     * <p><b>IMPORTANT:</b> Use this method instead of the basic applyAllStats when
     * you have access to the repository. This ensures ComputedStats reflects the
     * TRUE ECS values, including HP bonuses from external systems like LevelingCore.
     *
     * <p>Why this matters: LevelingCore adds +2 HP per level directly to ECS.
     * Without syncing, ComputedStats shows 180 HP while ECS has 248 HP.
     * This causes HP bar and UI discrepancies.
     *
     * @param playerRef The player reference
     * @param store The entity store
     * @param entityRef The entity reference
     * @param stats The computed stats to apply
     * @param repo The player data repository for cache sync
     * @param uuid The player's UUID
     * @return The actual max HP from ECS after all modifiers
     */
    public static float applyAllStatsAndSync(
            @Nonnull PlayerRef playerRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull ComputedStats stats,
            @Nonnull PlayerDataRepository repo,
            @Nonnull UUID uuid
    ) {
        // Apply stats to ECS
        float actualMaxHp = applyAllStats(playerRef, store, entityRef, stats);

        // Sync ComputedStats with actual ECS HP if different
        if (actualMaxHp > 0 && Math.abs(actualMaxHp - stats.getMaxHealth()) > 1.0f) {
            ComputedStats syncedStats = stats.toBuilder()
                    .maxHealth(actualMaxHp)
                    .build();

            // Update cache with synced stats
            Optional<PlayerData> dataOpt = repo.get(uuid);
            if (dataOpt.isPresent()) {
                PlayerData updated = dataOpt.get().withComputedStats(syncedStats);
                repo.updateCache(uuid, updated);
            }
        }

        return actualMaxHp;
    }

    /**
     * Applies all computed stats to the player's ECS components.
     *
     * <p>This method should be called whenever a player's stats change:
     * <ul>
     *   <li>On player join (after loading data)</li>
     *   <li>After attribute allocation</li>
     *   <li>After equipment changes (future)</li>
     *   <li>After buff/debuff application (future)</li>
     * </ul>
     *
     * <p><b>NOTE:</b> Returns the actual ECS max HP after all modifiers are applied.
     * This may differ from ComputedStats if other systems (LevelingCore, equipment)
     * also add HP modifiers. Use this value to update cached stats if needed.
     *
     * <p><b>PREFER:</b> Use {@link #applyAllStatsAndSync} when you have access to
     * the PlayerDataRepository, as it automatically syncs ComputedStats with ECS.
     *
     * @param playerRef The player reference
     * @param store The entity store
     * @param entityRef The entity reference
     * @param stats The computed stats to apply
     * @return The actual max HP from ECS after all modifiers, or -1 if unavailable
     */
    public static float applyAllStats(
            @Nonnull PlayerRef playerRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull ComputedStats stats
    ) {
        // Check if entity reference is still valid (player may have left the world)
        if (entityRef == null || !entityRef.isValid()) {
            return -1;
        }

        float actualMaxHp = applyResourceStats(store, entityRef, stats);
        StatMapBridge.applyToEntity(store, entityRef, stats, playerRef.getUuid());
        applyMovementSpeed(store, entityRef, playerRef, stats);
        // NOTE: Attack speed is handled by InteractionTimeShiftSystem (Tier 1) + AnimationSpeedSyncManager (Tier 2)

        return actualMaxHp;
    }

    /**
     * Applies resource stat bonuses (Health, Mana, Stamina) via EntityStatMap modifiers.
     *
     * <p>Uses the formula: Final = Base + Bonus
     * where Base is read from the game API (not hardcoded).
     *
     * <p><b>CRITICAL:</b> Preserves current resource percentages when updating modifiers.
     * Without this, changing max HP would cause current HP to be clamped to the
     * temporary lower max during the modifier swap, resulting in lost HP.
     *
     * @return The actual max HP from ECS after all modifiers are applied
     */
    private static float applyResourceStats(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull ComputedStats stats
    ) {
        EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return -1;
        }

        // Get actual vanilla base values from the game API
        float baseHealth = VanillaStatReader.getBaseHealth(store, entityRef);
        float baseMana = VanillaStatReader.getBaseMana(store, entityRef);
        float baseStamina = VanillaStatReader.getBaseStamina(store, entityRef);
        float baseOxygen = VanillaStatReader.getBaseOxygen(store, entityRef);
        float baseSignatureEnergy = VanillaStatReader.getBaseSignatureEnergy(store, entityRef);

        // Calculate bonuses (total - vanilla base)
        float healthBonus = stats.getMaxHealth() - baseHealth;
        // NOTE: Mana is handled by StatMapBridge (uses "rpg_max_mana" modifier key)
        // to support unified mana pool with Hexcode's ArmorManaPatcher.
        float staminaBonus = stats.getMaxStamina() - baseStamina;
        float oxygenBonus = stats.getMaxOxygen() - baseOxygen;
        float signatureEnergyBonus = stats.getMaxSignatureEnergy() - baseSignatureEnergy;

        // Apply bonuses with percentage preservation
        float actualMaxHp = applyStatModifierWithPreservation(
                statMap, DefaultEntityStatTypes.getHealth(), healthBonus
        );
        applyStatModifierWithPreservation(statMap, DefaultEntityStatTypes.getStamina(), staminaBonus);
        applyStatModifierWithPreservation(statMap, DefaultEntityStatTypes.getOxygen(), oxygenBonus);
        // SignatureEnergy is special: current value builds through combat (+1-2 per hit from vanilla's
        // EntityStatsOnHit) and is consumed by signature abilities. We should ONLY modify the MAX,
        // not forcibly set the current value, otherwise we overwrite vanilla's combat gains.
        applyMaxModifierOnly(statMap, DefaultEntityStatTypes.getSignatureEnergy(), signatureEnergyBonus);

        return actualMaxHp;
    }

    /**
     * Applies a stat modifier while preserving the current value as a percentage of max.
     *
     * <p><b>Why this is needed:</b> When removing the old modifier and adding a new one,
     * Hytale clamps the current value to the temporary lower max. Without preservation,
     * increasing max HP would paradoxically result in lost HP because:
     * <ol>
     *   <li>Old modifier removed → max HP drops to base (100)</li>
     *   <li>Current HP clamped to 100 if it was higher</li>
     *   <li>New modifier added → max HP increases, but current HP stays at clamped value</li>
     * </ol>
     *
     * <p><b>Solution:</b> Store current HP as percentage before swap, restore after.
     *
     * @param statMap The entity stat map
     * @param statIndex The stat index (from DefaultEntityStatTypes)
     * @param bonus The bonus value to add (can be negative)
     * @return The actual max HP from ECS after modification
     */
    private static float applyStatModifierWithPreservation(
            @Nonnull EntityStatMap statMap,
            int statIndex,
            float bonus
    ) {
        // Early exit: if the existing modifier already has the correct value,
        // skip the remove+add cycle. This prevents ECS dirty marking and breaks
        // the feedback loop where modifier swaps trigger InventoryChangeEvent → recalculate → swap again.
        Modifier existingModifier = statMap.getModifier(statIndex, RPG_MODIFIER_ID);
        if (existingModifier instanceof StaticModifier existingStatic) {
            if (Math.abs(existingStatic.getAmount() - bonus) < 0.001f) {
                // Modifier value unchanged — return current max without touching ECS
                EntityStatValue statValue = statMap.get(statIndex);
                return statValue != null ? statValue.getMax() : 100f;
            }
            LOGGER.atFine().log("[DIAG] Modifier CHANGED for stat %d: %.1f → %.1f", statIndex, existingStatic.getAmount(), bonus);
        } else if (existingModifier == null && Math.abs(bonus) < 0.001f) {
            // No modifier exists and bonus is ~0 — nothing to do
            EntityStatValue statValue = statMap.get(statIndex);
            return statValue != null ? statValue.getMax() : 100f;
        }

        LOGGER.atInfo().log("[DIAG] APPLYING modifier swap for stat %d: bonus=%.1f (existing=%s)",
            statIndex, bonus, existingModifier != null ? existingModifier.getClass().getSimpleName() : "null");

        // Step 1: Get current value and max BEFORE removing modifier
        EntityStatValue statValue = statMap.get(statIndex);
        float currentValue = 0f;
        float currentMax = 100f;
        float percentage = 1.0f;

        if (statValue != null) {
            currentValue = statValue.get();
            currentMax = statValue.getMax();
            if (currentMax > 0) {
                percentage = currentValue / currentMax;
            }
        }

        // Step 2: Remove old modifier
        statMap.removeModifier(statIndex, RPG_MODIFIER_ID);

        // Step 3: Add new modifier if there's an actual bonus
        if (Math.abs(bonus) > 0.001f) {
            StaticModifier modifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    bonus
            );
            statMap.putModifier(statIndex, RPG_MODIFIER_ID, modifier);
        }

        // Step 4: Restore current value to same percentage of NEW max
        statValue = statMap.get(statIndex);
        if (statValue != null) {
            float newMax = statValue.getMax();
            float restoredValue = newMax * percentage;
            restoredValue = Math.max(0f, Math.min(restoredValue, newMax));
            statMap.setStatValue(EntityStatMap.Predictable.SELF, statIndex, restoredValue);
            return newMax;
        }

        return currentMax;
    }

    /**
     * Applies a stat modifier that ONLY affects MAX, without touching current value.
     *
     * <p>Use this for stats like SignatureEnergy where current value has its own
     * dynamics (gains from combat hits, consumed by abilities) that we shouldn't
     * interfere with.
     *
     * <p><b>Why this exists:</b> Signature energy builds through combat via vanilla's
     * {@code EntityStatsOnHit} (+1-2 per hit). If we use {@code applyStatModifierWithPreservation},
     * we calculate percentage BEFORE vanilla adds the combat gain, then forcibly SET
     * the current value based on that old percentage — overwriting the combat gain.
     *
     * @param statMap The entity stat map
     * @param statIndex The stat index (from DefaultEntityStatTypes)
     * @param bonus The bonus value to add to MAX (can be negative)
     */
    private static void applyMaxModifierOnly(
            @Nonnull EntityStatMap statMap,
            int statIndex,
            float bonus
    ) {
        if (Math.abs(bonus) > 0.001f) {
            // Skip if existing modifier already has the correct value (prevent feedback loop)
            Modifier existingModifier = statMap.getModifier(statIndex, RPG_MODIFIER_ID);
            if (existingModifier instanceof StaticModifier existingStatic
                    && Math.abs(existingStatic.getAmount() - bonus) < 0.001f) {
                return;
            }

            // Non-zero bonus: remove old modifier and apply new one
            statMap.removeModifier(statIndex, RPG_MODIFIER_ID);
            StaticModifier modifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    bonus
            );
            statMap.putModifier(statIndex, RPG_MODIFIER_ID, modifier);
        }
        // When bonus is ~0, don't touch the modifier at all.
        // Calling removeModifier unconditionally causes a transient max-value drop
        // that can reset the current value (e.g., SignatureEnergy resets to 0 on slot switch).
        // NOTE: Do NOT set current value - let vanilla handle it
    }

    /**
     * Applies movement bonuses via MovementSettings.
     *
     * <p>Movement bonuses are applied multiplicatively:
     * <pre>
     * FinalSpeed = BaseSpeed * (1 + MovementSpeed%/100) * (1 + SprintSpeed%/100)
     * </pre>
     *
     * <p><b>CRITICAL:</b> Uses VanillaStatCache to prevent cascading multiplier bug.
     * Each stat recalculation must use CACHED vanilla base values, not the live
     * modified MovementSettings, otherwise speed bonuses compound exponentially.
     *
     * <p>Also applies:
     * <ul>
     *   <li>Jump force bonus (STR-based percentage)</li>
     *   <li>Sprint speed bonus (DEX-based percentage)</li>
     *   <li>Climb speed bonus (DEX-based percentage)</li>
     *   <li>Walk/Run/Sprint/Crouch speed multipliers</li>
     * </ul>
     */
    private static void applyMovementSpeed(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull ComputedStats stats
    ) {
        UUID playerUuid = playerRef.getUuid();
        LOGGER.atFine().log("[MOVEMENT] applyMovementSpeed() entry for %s - MovSpeed=%.1f%%",
            playerUuid, stats.getMovementSpeedPercent());

        MovementManager movementManager = store.getComponent(entityRef, MovementManager.getComponentType());
        if (movementManager == null) {
            LOGGER.atWarning().log("[MOVEMENT] MovementManager is NULL for %s", playerUuid);
            return;
        }

        MovementSettings settings = movementManager.getSettings();
        if (settings == null) {
            LOGGER.atWarning().log("[MOVEMENT] MovementSettings is NULL for %s", playerUuid);
            return;
        }

        // Get CACHED vanilla base values to prevent cascading multiplier bug
        // These must come from VanillaStatCache, not live MovementSettings
        float baseSprint = VanillaStatReader.getBaseForwardSprintSpeed(playerUuid);
        float baseRun = VanillaStatReader.getBaseForwardRunSpeed(playerUuid);
        float baseBackwardRun = VanillaStatReader.getBaseBackwardRunSpeed(playerUuid);
        float baseStrafeRun = VanillaStatReader.getBaseStrafeRunSpeed(playerUuid);
        float baseWalk = VanillaStatReader.getBaseForwardWalkSpeed(playerUuid);
        float baseBackwardWalk = VanillaStatReader.getBaseBackwardWalkSpeed(playerUuid);
        float baseStrafeWalk = VanillaStatReader.getBaseStrafeWalkSpeed(playerUuid);
        float baseCrouch = VanillaStatReader.getBaseForwardCrouchSpeed(playerUuid);
        float baseBackwardCrouch = VanillaStatReader.getBaseBackwardCrouchSpeed(playerUuid);
        float baseStrafeCrouch = VanillaStatReader.getBaseStrafeCrouchSpeed(playerUuid);
        float baseJump = VanillaStatReader.getBaseJumpForce(playerUuid);
        float baseClimb = VanillaStatReader.getBaseClimbSpeed(playerUuid);
        float baseClimbLateral = VanillaStatReader.getBaseClimbSpeedLateral(playerUuid);

        // Calculate multipliers from RPG stats
        float speedMultiplier = 1.0f + (stats.getMovementSpeedPercent() / 100.0f);
        float sprintBonusMultiplier = 1.0f + (stats.getSprintSpeedBonus() / 100.0f);
        float jumpMultiplier = 1.0f + (stats.getJumpForceBonus() / 100.0f);
        float climbMultiplier = 1.0f + (stats.getClimbSpeedBonus() / 100.0f);

        // Apply all movement speed multipliers
        // Sprint: gets general movement + sprint-specific bonus
        settings.forwardSprintSpeedMultiplier = baseSprint * speedMultiplier * sprintBonusMultiplier;
        settings.forwardRunSpeedMultiplier = baseRun * speedMultiplier;
        settings.backwardRunSpeedMultiplier = baseBackwardRun * speedMultiplier;
        settings.strafeRunSpeedMultiplier = baseStrafeRun * speedMultiplier;

        // Walk speeds: general movement + walk-specific bonus
        float walkBonusMultiplier = 1.0f + (stats.getWalkSpeedPercent() / 100.0f);
        settings.forwardWalkSpeedMultiplier = baseWalk * speedMultiplier * walkBonusMultiplier;
        settings.backwardWalkSpeedMultiplier = baseBackwardWalk * speedMultiplier * walkBonusMultiplier;
        settings.strafeWalkSpeedMultiplier = baseStrafeWalk * speedMultiplier * walkBonusMultiplier;

        // Crouch speeds
        settings.forwardCrouchSpeedMultiplier = baseCrouch * speedMultiplier;
        settings.backwardCrouchSpeedMultiplier = baseBackwardCrouch * speedMultiplier;
        settings.strafeCrouchSpeedMultiplier = baseStrafeCrouch * speedMultiplier;

        // Jump force
        settings.jumpForce = baseJump * jumpMultiplier;

        // Climb speeds
        settings.climbSpeed = baseClimb * climbMultiplier;
        settings.climbSpeedLateral = baseClimbLateral * climbMultiplier;

        // Sync changes to client
        movementManager.update(playerRef.getPacketHandler());

        LOGGER.atFine().log("[MOVEMENT] SUCCESS - Applied to %s: speedMult=%.3f, sprintMult=%.3f, finalSprint=%.3f",
            playerUuid, speedMultiplier, sprintBonusMultiplier, settings.forwardSprintSpeedMultiplier);
    }

    /**
     * Retrieves the player's current ComputedStats from the entity, if available.
     *
     * <p>This is a convenience method for systems that need to read stats
     * without having direct access to PlayerData.
     *
     * @param playerRef The player reference
     * @return The computed stats, or null if not available
     */
    @Nullable
    public static ComputedStats getStatsFromPlayer(@Nonnull PlayerRef playerRef) {
        return null;
    }
}
