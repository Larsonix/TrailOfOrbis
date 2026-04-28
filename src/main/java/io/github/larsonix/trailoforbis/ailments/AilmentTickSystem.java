package io.github.larsonix.trailoforbis.ailments;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.config.AilmentConfig;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.EnergyShieldTracker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * ECS tick system that processes ailment damage over time (DoT).
 *
 * <p>This system runs every tick and:
 * <ul>
 *   <li>Decrements ailment durations</li>
 *   <li>Applies Burn and Poison DoT damage</li>
 *   <li>Removes expired ailments</li>
 * </ul>
 *
 * <p><b>DoT Damage Application:</b>
 * DoT damage is applied directly to health via EntityStatMap, similar to
 * how RegenerationTickSystem adds health. This bypasses the damage event
 * system for smooth per-tick damage application.
 *
 * <p><b>Formula:</b> damageThisTick = magnitude (DPS) × dt
 *
 * <p><b>Thread Safety:</b> Uses AilmentTracker's ConcurrentHashMap for
 * thread-safe lookups. Individual tick operations are atomic per-entity.
 *
 * @see AilmentTracker
 * @see io.github.larsonix.trailoforbis.systems.RegenerationTickSystem
 */
public class AilmentTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Component types for efficient query */
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;

    /** Query for player entities with stats */
    private Archetype<EntityStore> playerQuery = null;

    /** Ailment tracker instance */
    private final AilmentTracker tracker;

    /** Configuration */
    private final AilmentConfig config;

    /** Energy shield tracker for SHIELD_REGEN_ON_DOT (nullable) */
    private final EnergyShieldTracker energyShieldTracker;

    /** Stats lookup function for SHIELD_REGEN_ON_DOT (nullable) */
    private final Function<UUID, ComputedStats> statsLookup;

    /** Health stat index for direct damage application */
    private static final int HEALTH_STAT_INDEX = DefaultEntityStatTypes.getHealth();

    /** Creates a new ailment tick system. */
    public AilmentTickSystem(@Nonnull AilmentTracker tracker, @Nonnull AilmentConfig config) {
        this(tracker, config, null, null);
    }

    /**
     * Creates a new ailment tick system with shield regen support.
     *
     * @param energyShieldTracker  Energy shield tracker (nullable, enables SHIELD_REGEN_ON_DOT)
     * @param statsLookup          Function to look up player stats by UUID (nullable)
     */
    public AilmentTickSystem(
            @Nonnull AilmentTracker tracker,
            @Nonnull AilmentConfig config,
            @Nullable EnergyShieldTracker energyShieldTracker,
            @Nullable Function<UUID, ComputedStats> statsLookup
    ) {
        this.tracker = tracker;
        this.config = config;
        this.energyShieldTracker = energyShieldTracker;
        this.statsLookup = statsLookup;
        this.playerRefType = PlayerRef.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Query for entities with both PlayerRef and EntityStatMap
        // This targets player entities specifically
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
        // Skip if ailments disabled
        if (!config.isEnabled()) {
            return;
        }

        // Get entity reference
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        // Get player UUID (query guarantees PlayerRef exists)
        PlayerRef playerRef = store.getComponent(entityRef, playerRefType);
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        // Check if player has any ailments
        EntityAilmentState ailmentState = tracker.get(uuid);
        if (ailmentState == null || !ailmentState.hasAnyAilment()) {
            return;
        }

        // Get stat map for health modification
        EntityStatMap statMap = store.getComponent(entityRef, statMapType);
        if (statMap == null) {
            return;
        }

        // Snapshot DoT sources before tick (tick may expire ailments, losing source info)
        Map<UUID, Float> dotSourceDps = null;
        if (energyShieldTracker != null && statsLookup != null) {
            dotSourceDps = ailmentState.getDotDpsPerSource();
        }

        // Process ailments and get total DoT damage
        float dotDamage = tracker.tickEntity(uuid, dt);

        // Apply DoT damage if any
        if (dotDamage > 0) {
            applyDotDamage(statMap, dotDamage, uuid);

            // SHIELD_REGEN_ON_DOT: restore shield to DoT sources
            if (dotSourceDps != null && !dotSourceDps.isEmpty()) {
                processShieldRegenFromDots(dotSourceDps, dotDamage, dt);
            }
        }
    }

    /**
     * Processes SHIELD_REGEN_ON_DOT for each DoT source that has the stat.
     *
     * <p>Distributes the total DoT damage proportionally among sources based on
     * their DPS contribution, then restores a percentage of that damage as
     * energy shield to the source player.
     *
     * @param dotSourceDps Per-source DPS snapshot (taken before tick)
     * @param totalDotDamage Total DoT damage dealt this tick
     * @param dt Delta time in seconds
     */
    private void processShieldRegenFromDots(
            @Nonnull Map<UUID, Float> dotSourceDps,
            float totalDotDamage,
            float dt
    ) {
        // Calculate total DPS for proportional distribution
        float totalDps = 0f;
        for (float dps : dotSourceDps.values()) {
            totalDps += dps;
        }
        if (totalDps <= 0) {
            return;
        }

        for (Map.Entry<UUID, Float> entry : dotSourceDps.entrySet()) {
            UUID sourceUuid = entry.getKey();
            float sourceDps = entry.getValue();

            ComputedStats sourceStats = statsLookup.apply(sourceUuid);
            if (sourceStats == null) {
                continue;
            }

            float regenPct = sourceStats.getShieldRegenOnDot();
            if (regenPct <= 0) {
                continue;
            }

            // This source's share of the total DoT damage
            float sourceDamageShare = totalDotDamage * (sourceDps / totalDps);
            float shieldRestore = sourceDamageShare * (regenPct / 100f);

            if (shieldRestore > 0) {
                float maxShield = sourceStats.getEnergyShield();
                energyShieldTracker.addShield(sourceUuid, shieldRestore, maxShield);
                LOGGER.atFine().log("Shield regen from DoT: %.2f shield to %s (%.0f%% of %.2f damage)",
                        shieldRestore, sourceUuid.toString().substring(0, 8), regenPct, sourceDamageShare);
            }
        }
    }

    /**
     * Applies DoT damage directly to entity health.
     *
     * <p>This reduces health by the specified damage amount, clamped to
     * not go below 0 (entities die at 0 health via vanilla systems).
     *
     * @param statMap   The entity's stat map
     * @param damage    The DoT damage to apply
     * @param entityUuid Entity UUID for logging
     */
    private void applyDotDamage(
            @Nonnull EntityStatMap statMap,
            float damage,
            @Nonnull UUID entityUuid
    ) {
        EntityStatValue healthStat = statMap.get(HEALTH_STAT_INDEX);
        if (healthStat == null) {
            return;
        }

        float currentHealth = healthStat.get();
        if (currentHealth <= 0) {
            // Already dead - clean up ailments to free resources
            tracker.cleanup(entityUuid);
            return;
        }

        // Calculate new health (clamp to 0 minimum)
        float newHealth = Math.max(0f, currentHealth - damage);

        // Apply the damage (SELF for immediate client-side health bar update)
        statMap.setStatValue(EntityStatMap.Predictable.SELF, HEALTH_STAT_INDEX, newHealth);

        // Debug logging (fine level to avoid spam)
        LOGGER.atFine().log("DoT damage: %.2f to %s (%.1f -> %.1f HP)",
                damage, entityUuid.toString().substring(0, 8), currentHealth, newHealth);

        // If entity died from DoT, clean up ailments immediately
        if (newHealth <= 0) {
            tracker.cleanup(entityUuid);
            LOGGER.atFine().log("Entity %s died from DoT, ailments cleared",
                    entityUuid.toString().substring(0, 8));
        }
    }

    /** Gets the ailment tracker used by this system. */
    @Nonnull
    public AilmentTracker getTracker() {
        return tracker;
    }

    /** Gets the configuration used by this system. */
    @Nonnull
    public AilmentConfig getConfig() {
        return config;
    }
}
