package io.github.larsonix.trailoforbis.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.protocol.MovementStates;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.EnergyShieldTracker;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import com.hypixel.hytale.server.core.modules.entity.stamina.SprintStaminaRegenDelay;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Entity ticking system that handles resource regeneration.
 *
 * <p>This system applies smooth per-tick regeneration using delta time (dt).
 * Instead of applying large chunks once per second, it applies small amounts
 * every tick for smooth resource recovery.
 *
 * <p><b>Hybrid Approach:</b> This system adds BONUS regeneration on top of
 * Hytale's vanilla base regeneration, providing class differentiation.
 *
 * <p><b>Regeneration Stats (per second):</b>
 * <ul>
 *   <li>healthRegen: HP recovered (from VIT)</li>
 *   <li>manaRegen: Mana recovered (from INT)</li>
 *   <li>staminaRegen: Stamina recovered (from DEX)</li>
 *   <li>oxygenRegen: Oxygen recovered (from VIT)</li>
 *   <li>signatureEnergyRegen: Signature energy recovered (from INT)</li>
 * </ul>
 *
 * <p>Regeneration only occurs when:
 * <ul>
 *   <li>Player is below max for that resource</li>
 *   <li>Player is alive</li>
 * </ul>
 *
 * <p><b>Formula:</b> regenThisTick = regenPerSecond × dt
 */
public class RegenerationTickSystem extends EntityTickingSystem<EntityStore> {

    /** Shield regenerates at 20% of max per second (full recovery in 5s after delay). */
    private static final float SHIELD_REGEN_RATE_PER_SECOND = 0.20f;

    /** Set of player UUIDs that have been seen (for cleanup tracking) */
    private final java.util.concurrent.ConcurrentHashMap<UUID, Boolean> trackedPlayers =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Component types for efficient query - cached for performance */
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;

    /** Archetype query matching only player entities with stats - lazily initialized */
    private Archetype<EntityStore> playerQuery = null;

    /** Cached service reference — resolved once on first tick, never changes */
    @Nullable private AttributeService cachedAttributeService;

    public RegenerationTickSystem() {
        this.playerRefType = PlayerRef.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Use Archetype to filter at query level - only entities with BOTH
        // PlayerRef AND EntityStatMap will be processed. This is far more
        // efficient than Query.any() which would iterate all entities.
        if (playerQuery == null) {
            playerQuery = Archetype.of(playerRefType, statMapType);
        }
        return playerQuery;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Get entity reference for this index
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        // Query guarantees PlayerRef exists (Archetype.of filters for it)
        PlayerRef playerRef = store.getComponent(entityRef, playerRefType);
        UUID uuid = playerRef.getUuid();

        // Track player for cleanup purposes
        trackedPlayers.putIfAbsent(uuid, Boolean.TRUE);

        // Get player's computed stats (lazy-init cached service — resolves once, never changes)
        if (cachedAttributeService == null) {
            cachedAttributeService = ServiceRegistry.get(AttributeService.class).orElse(null);
            if (cachedAttributeService == null) {
                return;
            }
        }

        Optional<PlayerData> dataOpt = cachedAttributeService.getPlayerDataRepository().get(uuid);
        if (dataOpt.isEmpty()) {
            return; // No player data
        }

        ComputedStats stats = dataOpt.get().getComputedStats();
        if (stats == null) {
            return; // Stats not calculated
        }

        // Apply smooth regeneration scaled by delta time
        applyRegeneration(store, entityRef, stats, dt, uuid);
    }

    /**
     * Cleans up player tracking when they disconnect.
     * Called from PlayerJoinListener.onPlayerDisconnect.
     *
     * @param uuid The player's UUID
     */
    public void cleanupPlayer(@Nonnull UUID uuid) {
        trackedPlayers.remove(uuid);
    }

    /**
     * Applies smooth resource regeneration scaled by delta time.
     *
     * <p>This is BONUS regeneration that stacks with vanilla's base regen.
     * Vanilla handles base regen via EntityStatType.Regenerating[] config,
     * and we add attribute-based bonus regen on top.
     *
     * <p><b>IMPORTANT:</b> We use the actual max value from {@link EntityStatValue#getMax()}
     * rather than {@link ComputedStats} because EntityStatValue contains the true max
     * with ALL modifiers applied (RPG attributes, equipment, buffs, etc.).
     *
     * @param store The entity store
     * @param entityRef Reference to the player entity
     * @param stats The player's computed stats (regen values are per second)
     * @param dt Delta time in seconds since last tick
     * @param uuid The player's UUID (needed for energy shield tracking)
     */
    private void applyRegeneration(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComputedStats stats,
        float dt,
        @Nonnull UUID uuid
    ) {
        // Query guarantees EntityStatMap exists (Archetype.of filters for it)
        EntityStatMap statMap = store.getComponent(entityRef, statMapType);
        if (statMap == null) {
            return;
        }

        // Health regeneration (VIT-based bonus) with healthRegenPercent and healthRecoveryPercent multipliers
        // - healthRegenPercent: Multiplier for regeneration only (stacks additively with regen-specific bonuses)
        // - healthRecoveryPercent: Global multiplier for ALL health recovery (regen, lifesteal, block heal)
        // Formula: effectiveRegen = baseRegen × (1 + regenPct/100) × (1 + recoveryPct/100)
        float healthRegen = stats.getHealthRegen();
        if (healthRegen > 0) {
            float healthRegenPct = stats.getHealthRegenPercent();
            float healthRecoveryPct = stats.getHealthRecoveryPercent();
            float effectiveHealthRegen = healthRegen;
            if (healthRegenPct != 0) {
                effectiveHealthRegen *= (1.0f + healthRegenPct / 100.0f);
            }
            if (healthRecoveryPct != 0) {
                effectiveHealthRegen *= (1.0f + healthRecoveryPct / 100.0f);
            }
            regenerateStat(statMap, DefaultEntityStatTypes.getHealth(), effectiveHealthRegen * dt);
        }

        // Mana regeneration (INT-based bonus)
        float manaRegen = stats.getManaRegen();
        if (manaRegen > 0) {
            regenerateStat(statMap, DefaultEntityStatTypes.getMana(), manaRegen * dt);
        }

        // Stamina regeneration (DEX-based bonus)
        // CRITICAL: Only regenerate stamina when NOT actively consuming it
        // Also respects Hytale's native StaminaRegenDelay (post-sprint cooldown)
        float staminaRegen = stats.getStaminaRegen();
        if (staminaRegen > 0 && !isConsumingStamina(store, entityRef)) {
            if (!isStaminaRegenDelayed(store, statMap)) {
                // Apply staminaRegenPercent multiplier (mirrors healthRegenPercent pattern)
                float staminaRegenPct = stats.getStaminaRegenPercent();
                float effectiveStaminaRegen = staminaRegen;
                if (staminaRegenPct != 0) {
                    effectiveStaminaRegen *= (1.0f + staminaRegenPct / 100.0f);
                }
                regenerateStat(statMap, DefaultEntityStatTypes.getStamina(), effectiveStaminaRegen * dt);
            }
            // Accelerate delay recovery even while delay is active
            accelerateDelayRecovery(store, statMap, stats, dt);
        }

        // Oxygen regeneration (VIT-based bonus)
        float oxygenRegen = stats.getOxygenRegen();
        if (oxygenRegen > 0) {
            regenerateStat(statMap, DefaultEntityStatTypes.getOxygen(), oxygenRegen * dt);
        }

        // Signature Energy regeneration (INT-based bonus)
        float signatureEnergyRegen = stats.getSignatureEnergyRegen();
        if (signatureEnergyRegen > 0) {
            regenerateStat(statMap, DefaultEntityStatTypes.getSignatureEnergy(), signatureEnergyRegen * dt);
        }

        // Energy shield regeneration (delayed after taking shield damage)
        float maxShield = stats.getEnergyShield();
        if (maxShield > 0) {
            TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
            if (rpg != null) {
                EnergyShieldTracker shieldTracker = rpg.getEnergyShieldTracker();
                if (shieldTracker != null) {
                    float shieldRegenPerSec = maxShield * SHIELD_REGEN_RATE_PER_SECOND;
                    shieldTracker.tickRegen(uuid, maxShield, shieldRegenPerSec, dt);
                }
            }
        }
    }

    /**
     * Regenerates a single stat by the given amount, capped at the actual max.
     *
     * <p><b>CRITICAL:</b> Uses {@link EntityStatValue#getMax()} to get the true max value
     * which includes ALL modifiers (RPG attributes, equipment, buffs, etc.). This ensures
     * regeneration always fills to the correct maximum, regardless of what sources
     * contributed to that maximum.
     *
     * @param statMap The entity stat map
     * @param statIndex The stat index (Health, Mana, etc.)
     * @param regenAmount Amount to regenerate this tick (already scaled by dt)
     */
    private void regenerateStat(
        @Nonnull EntityStatMap statMap,
        int statIndex,
        float regenAmount
    ) {
        EntityStatValue statValue = statMap.get(statIndex);
        if (statValue == null) {
            return;
        }

        float currentValue = statValue.get();
        // Get the ACTUAL max from EntityStatValue - this includes ALL modifiers
        float actualMax = statValue.getMax();

        // Only regenerate if below max
        if (currentValue >= actualMax) {
            return;
        }

        // Calculate new value, capped at actual max
        float newValue = Math.min(currentValue + regenAmount, actualMax);

        // Apply the regeneration (SELF for immediate client-side health/mana bar update)
        statMap.setStatValue(EntityStatMap.Predictable.SELF, statIndex, newValue);
    }

    /**
     * Checks if Hytale's native stamina regen delay is active.
     *
     * <p>After sprinting stops, Hytale enforces a cooldown period (StaminaRegenDelay)
     * before stamina starts regenerating. The delay is tracked as a negative float
     * on a stat index — when the value is negative, the delay is active.
     *
     * @param store The entity store
     * @param statMap The entity stat map
     * @return true if the stamina regen delay is currently active
     */
    private boolean isStaminaRegenDelayed(
        @Nonnull Store<EntityStore> store,
        @Nonnull EntityStatMap statMap
    ) {
        SprintStaminaRegenDelay regenDelay = store.getResource(SprintStaminaRegenDelay.getResourceType());
        if (regenDelay == null || !regenDelay.validate()) {
            return false; // No delay configured
        }

        int delayIndex = regenDelay.getIndex();
        if (delayIndex == 0) {
            return false; // No stat index assigned
        }

        EntityStatValue delayValue = statMap.get(delayIndex);
        return delayValue != null && delayValue.get() < 0;
    }

    /**
     * Accelerates the stamina regen delay recovery based on the staminaRegenStartDelay stat.
     *
     * <p>When the delay timer is active (value < 0), this adds extra recovery on top
     * of vanilla's natural tick-toward-zero, making the delay resolve faster.
     *
     * <p>The acceleration formula converts the percentage stat to a speed factor:
     * <ul>
     *   <li>At 50%: speedFactor = 1.0 → doubles vanilla recovery speed (~halves delay)</li>
     *   <li>At 75% (cap): speedFactor = 3.0 → quadruples recovery speed (~quarters delay)</li>
     * </ul>
     *
     * @param store The entity store
     * @param statMap The entity stat map
     * @param stats The player's computed stats
     * @param dt Delta time in seconds
     */
    private void accelerateDelayRecovery(
        @Nonnull Store<EntityStore> store,
        @Nonnull EntityStatMap statMap,
        @Nonnull ComputedStats stats,
        float dt
    ) {
        float staminaRegenStartDelay = stats.getStaminaRegenStartDelay();
        if (staminaRegenStartDelay <= 0) {
            return;
        }

        SprintStaminaRegenDelay regenDelay = store.getResource(SprintStaminaRegenDelay.getResourceType());
        if (regenDelay == null || !regenDelay.validate()) {
            return;
        }

        int delayIndex = regenDelay.getIndex();
        if (delayIndex == 0) {
            return;
        }

        EntityStatValue delayValue = statMap.get(delayIndex);
        if (delayValue == null) {
            return;
        }

        float current = delayValue.get();
        if (current < 0) {
            float cappedPct = Math.min(staminaRegenStartDelay, 75f);
            float speedFactor = cappedPct / (100f - cappedPct);
            float acceleration = speedFactor * dt;
            float newValue = Math.min(0f, current + acceleration);
            statMap.setStatValue(delayIndex, newValue);
        }
    }

    /**
     * Checks if the entity is actively consuming stamina.
     *
     * <p>Stamina is consumed during movement actions like:
     * <ul>
     *   <li>Sprinting</li>
     *   <li>Swimming</li>
     *   <li>Climbing</li>
     *   <li>Rolling</li>
     *   <li>Mantling</li>
     *   <li>Gliding</li>
     * </ul>
     *
     * <p>When any of these states are active, we should NOT apply
     * bonus stamina regeneration to avoid the stat bonus negating
     * the stamina cost of these actions.
     *
     * @param store The entity store
     * @param entityRef Reference to the entity
     * @return true if the entity is currently consuming stamina
     */
    private boolean isConsumingStamina(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        MovementStatesComponent movementComp = store.getComponent(
            entityRef, MovementStatesComponent.getComponentType());
        if (movementComp == null) {
            return false; // No movement component, assume not consuming
        }

        MovementStates states = movementComp.getMovementStates();
        if (states == null) {
            return false;
        }

        // Check all stamina-consuming states
        // These are actions that actively drain stamina while performed
        return states.sprinting
            || states.swimming
            || states.climbing
            || states.rolling
            || states.mantling
            || states.gliding;
    }
}
