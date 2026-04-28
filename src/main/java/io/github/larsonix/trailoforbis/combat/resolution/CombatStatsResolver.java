package io.github.larsonix.trailoforbis.combat.resolution;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.mobs.MobScalingService;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Resolves combat stats for players and mobs.
 *
 * <p>This class extracts stats retrieval logic from RPGDamageSystem,
 * handling:
 * <ul>
 *   <li>Player stats from AttributeService</li>
 *   <li>Mob stats from MobScalingService</li>
 *   <li>Elemental stats for both players and mobs</li>
 * </ul>
 */
public class CombatStatsResolver {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CombatEntityResolver entityResolver;
    private final ComponentType<EntityStore, MobScalingComponent> mobScalingComponentType;

    /**
     * Creates a new CombatStatsResolver.
     *
     * @param entityResolver The entity resolver for attacker/defender lookups
     * @param mobScalingComponentType The component type for mob scaling (from TrailOfOrbis)
     */
    public CombatStatsResolver(
        @Nonnull CombatEntityResolver entityResolver,
        @Nullable ComponentType<EntityStore, MobScalingComponent> mobScalingComponentType
    ) {
        this.entityResolver = entityResolver;
        this.mobScalingComponentType = mobScalingComponentType;
    }

    /**
     * Result of entity stats lookup, containing stats and metadata about the entity type.
     *
     * @param stats The computed stats (null if not available)
     * @param isPlayer Whether the entity is a player
     * @param playerRef The player reference (null if not a player)
     */
    public record EntityStatsResult(
        @Nullable ComputedStats stats,
        boolean isPlayer,
        @Nullable PlayerRef playerRef
    ) {
        public static EntityStatsResult notFound() {
            return new EntityStatsResult(null, false, null);
        }

        public static EntityStatsResult forPlayer(@Nullable ComputedStats stats, @Nonnull PlayerRef playerRef) {
            return new EntityStatsResult(stats, true, playerRef);
        }

        public static EntityStatsResult forMob(@Nullable ComputedStats stats) {
            return new EntityStatsResult(stats, false, null);
        }
    }

    /**
     * Gets the attacker's computed stats from the damage source.
     *
     * <p>First checks if the attacker is a player, then falls back to
     * checking if it's a scaled mob with stats.
     *
     * @param store The entity store
     * @param damage The damage event
     * @return The attacker's stats, or null if not available
     */
    @Nullable
    public ComputedStats getAttackerStats(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage
    ) {
        // Check if damage came from an entity
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            LOGGER.at(Level.FINE).log("Damage source is not EntitySource: %s",
                damage.getSource().getClass().getSimpleName());
            return null;
        }

        // Get immediate attacker entity reference
        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            LOGGER.at(Level.FINE).log("Attacker ref is invalid (entity removed or unloaded)");
            return null;
        }

        // Resolve true attacker (handle projectiles/proxies)
        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        boolean isProxy = attackerRef != null && !attackerRef.equals(immediateRef);

        if (attackerRef == null) {
            return null;
        }

        // Use shared lookup logic
        EntityStatsResult result = getStatsForEntityRef(attackerRef, store, "attacker");

        if (result.isPlayer()) {
            PlayerRef playerRef = result.playerRef();
            if (isProxy && playerRef != null) {
                LOGGER.at(Level.FINE).log("Resolved player %s from proxy entity", playerRef.getUsername());
            }

            // Handle null player stats gracefully instead of crashing
            if (result.stats() == null && playerRef != null) {
                String diagnostics = buildStatsDiagnostics(
                    playerRef.getUsername(), playerRef.getUuid(),
                    ServiceRegistry.get(AttributeService.class).orElse(null));
                LOGGER.at(Level.WARNING).log(
                    "ComputedStats is null for player %s (%s) during combat - using base damage.%s",
                    playerRef.getUsername(), playerRef.getUuid(), diagnostics);
                return null;
            }
            return result.stats();
        }

        if (!isProxy && result.stats() == null) {
            LOGGER.at(Level.FINE).log("No PlayerRef on attacker entity (likely NPC/mob)");
        }

        return result.stats();
    }

    /**
     * Gets the defender's computed stats from the damage target.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the target entity
     * @param store The entity store
     * @return The defender's stats, or null if not available
     */
    @Nullable
    public ComputedStats getDefenderStats(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store
    ) {
        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null) {
            return null;
        }

        // Use shared lookup logic - returns null for unavailable stats (acceptable for defenders)
        EntityStatsResult result = getStatsForEntityRef(defenderRef, store, "defender");
        return result.stats();
    }

    /**
     * Gets the attacker's elemental stats for damage calculation.
     *
     * <p>For players, converts ComputedStats to ElementalStats.
     * For mobs, retrieves ElementalStats from MobScalingComponent.
     *
     * @param store The entity store
     * @param damage The damage event
     * @return The attacker's elemental stats, or null if not available
     */
    @Nullable
    public ElementalStats getAttackerElementalStats(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage
    ) {
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return null;
        }

        Ref<EntityStore> immediateRef = entitySource.getRef();
        if (immediateRef == null || !immediateRef.isValid()) {
            return null;
        }

        Ref<EntityStore> attackerRef = entityResolver.resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return null;
        }

        // Check if attacker is a player
        PlayerRef playerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            try {
                AttributeService service = ServiceRegistry.require(AttributeService.class);
                ComputedStats stats = service.getStats(playerRef.getUuid());
                return stats != null ? stats.toElementalStats() : null;
            } catch (IllegalStateException e) {
                return null;
            }
        }

        // Check if attacker is a scaled mob with elemental stats
        try {
            if (mobScalingComponentType != null) {
                MobScalingComponent scaling = store.getComponent(attackerRef, mobScalingComponentType);
                if (scaling != null) {
                    MobStats mobStats = scaling.getStats();
                    if (mobStats != null && mobStats.elementalStats() != null) {
                        return mobStats.elementalStats();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error getting attacker elemental stats: %s", e.getMessage());
        }

        return null;
    }

    /**
     * Gets the defender's elemental stats (resistances) for damage calculation.
     *
     * <p>For players, converts ComputedStats to ElementalStats.
     * For mobs, retrieves ElementalStats from MobScalingComponent.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     * @param defenderStats The already-retrieved defender ComputedStats (may be null)
     * @return The defender's elemental stats, or null if not available
     */
    @Nullable
    public ElementalStats getDefenderElementalStats(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nullable ComputedStats defenderStats
    ) {
        // If we already have ComputedStats for a player, use those
        if (defenderStats != null) {
            return defenderStats.toElementalStats();
        }

        // Try to get from mob scaling component
        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return null;
        }

        try {
            if (mobScalingComponentType != null) {
                MobScalingComponent scaling = store.getComponent(defenderRef, mobScalingComponentType);
                if (scaling != null) {
                    MobStats mobStats = scaling.getStats();
                    if (mobStats != null && mobStats.elementalStats() != null) {
                        return mobStats.elementalStats();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error getting defender elemental stats: %s", e.getMessage());
        }

        return null;
    }

    /**
     * Gets computed stats for an entity reference (player or mob).
     *
     * @param entityRef The entity reference to get stats for
     * @param store The entity store
     * @param entityRole Description for logging (e.g., "attacker", "defender")
     * @return Result containing stats and entity type information
     */
    @Nonnull
    private EntityStatsResult getStatsForEntityRef(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String entityRole
    ) {
        if (!entityRef.isValid()) {
            LOGGER.at(Level.FINE).log("%s ref is invalid (entity removed or unloaded)", entityRole);
            return EntityStatsResult.notFound();
        }

        // Check if entity is a player
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            ComputedStats stats = getPlayerStatsFromService(playerRef);
            return EntityStatsResult.forPlayer(stats, playerRef);
        }

        // Not a player - check if it's a scaled mob
        ComputedStats mobStats = getMobStatsFromService(entityRef, store, entityRole);
        return EntityStatsResult.forMob(mobStats);
    }

    /**
     * Gets player stats from the AttributeService.
     *
     * @param playerRef The player reference
     * @return The player's computed stats, or null if service unavailable
     */
    @Nullable
    private ComputedStats getPlayerStatsFromService(@Nonnull PlayerRef playerRef) {
        try {
            AttributeService service = ServiceRegistry.require(AttributeService.class);
            return service.getStats(playerRef.getUuid());
        } catch (IllegalStateException e) {
            LOGGER.at(Level.FINE).log("AttributeService not available for player %s", playerRef.getUsername());
            return null;
        }
    }

    /**
     * Gets mob stats from the MobScalingService.
     *
     * @param entityRef The mob entity reference
     * @param store The entity store
     * @param entityRole Description for logging
     * @return The mob's computed stats, or null if not a scaled mob
     */
    @Nullable
    private ComputedStats getMobStatsFromService(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String entityRole
    ) {
        try {
            MobScalingService mobService = ServiceRegistry.get(MobScalingService.class).orElse(null);
            if (mobService == null || !mobService.isEnabled()) {
                return null;
            }

            ComputedStats mobStats = mobService.getMobComputedStats(entityRef, store);
            if (mobStats != null) {
                LOGGER.at(Level.FINE).log("Got %s mob stats: crit=%.1f%%/%.0f%%, pen=%.1f%%",
                    entityRole, mobStats.getCriticalChance(), mobStats.getCriticalMultiplier(),
                    mobStats.getArmorPenetration());
            }
            return mobStats;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Error getting %s mob stats: %s", entityRole, e.getMessage());
            return null;
        }
    }

    /**
     * Builds detailed diagnostics when ComputedStats is unexpectedly null.
     *
     * @param playerName The player's username
     * @param uuid The player's UUID
     * @param service The AttributeService for cache inspection
     * @return Formatted diagnostic string
     */
    @Nonnull
    private String buildStatsDiagnostics(String playerName, UUID uuid, @Nullable AttributeService service) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n═══ STATS NULL DIAGNOSTIC ═══\n");
        sb.append("Player: ").append(playerName).append(" (").append(uuid).append(")\n");

        if (service == null) {
            sb.append("AttributeService: NOT AVAILABLE\n");
            sb.append("═════════════════════════════\n");
            return sb.toString();
        }

        PlayerDataRepository repo = service.getPlayerDataRepository();
        sb.append("Cache Size: ").append(repo.getCacheSize()).append("\n");
        sb.append("In Cache: ").append(repo.getCachedUuids().contains(uuid)).append("\n");

        Optional<PlayerData> dataOpt = repo.get(uuid);
        if (dataOpt.isPresent()) {
            PlayerData data = dataOpt.get();
            sb.append("PlayerData: FOUND\n");
            sb.append("  Attributes: FIRE=").append(data.getFire())
              .append(", WATER=").append(data.getWater())
              .append(", LIGHTNING=").append(data.getLightning())
              .append(", EARTH=").append(data.getEarth())
              .append(", WIND=").append(data.getWind())
              .append(", VOID=").append(data.getVoidAttr()).append("\n");
            sb.append("  ComputedStats: ").append(data.getComputedStats() == null ? "NULL" : "EXISTS").append("\n");
        } else {
            sb.append("PlayerData: NOT FOUND\n");
        }
        sb.append("═════════════════════════════\n");
        return sb.toString();
    }
}
