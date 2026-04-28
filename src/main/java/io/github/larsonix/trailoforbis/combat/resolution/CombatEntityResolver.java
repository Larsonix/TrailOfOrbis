package io.github.larsonix.trailoforbis.combat.resolution;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.compat.HytaleAPICompat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Resolves entity references and UUIDs for combat calculations.
 *
 * <p>This class extracts entity resolution logic from RPGDamageSystem,
 * handling:
 * <ul>
 *   <li>True attacker resolution (projectile → owner)</li>
 *   <li>UUID lookups for players and mobs</li>
 *   <li>Health percentage calculations</li>
 * </ul>
 */
public class CombatEntityResolver {

    /**
     * Resolves the true attacker entity reference, handling projectiles/proxies.
     *
     * <p>If the source entity is a player, returns it directly.
     * If the source is a projectile, attempts to resolve the creator (owner).
     * Otherwise returns the source entity (e.g. mob).
     *
     * @param store The entity store
     * @param sourceRef The immediate source entity reference
     * @return The true attacker reference (player or mob), or sourceRef if resolution fails
     */
    @Nullable
    public Ref<EntityStore> resolveTrueAttacker(
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> sourceRef
    ) {
        if (sourceRef == null || !sourceRef.isValid()) {
            return null;
        }

        // 1. Check if source is already a player
        if (store.getComponent(sourceRef, PlayerRef.getComponentType()) != null) {
            return sourceRef;
        }

        // 2. Check if source is a projectile with an owner
        UUID creatorUuid = getCreatorFromProjectile(store, sourceRef);
        if (creatorUuid != null) {
            Ref<EntityStore> ownerRef = store.getExternalData().getRefFromUUID(creatorUuid);
            if (ownerRef != null && ownerRef.isValid()) {
                return ownerRef;
            }
        }

        // 3. Return original source (e.g. mob)
        return sourceRef;
    }

    /**
     * Attempts to resolve the creator UUID from a projectile entity.
     *
     * <p>Uses {@link HytaleAPICompat} for safe reflection-based access to the
     * private creatorUuid field in ProjectileComponent.
     *
     * @param store The entity store
     * @param entityRef The entity reference to check
     * @return The creator's UUID if found, null otherwise
     */
    @Nullable
    public UUID getCreatorFromProjectile(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        ProjectileComponent projectile = store.getComponent(entityRef, ProjectileComponent.getComponentType());
        if (projectile == null) {
            return null;
        }

        return HytaleAPICompat.getProjectileCreator(projectile).orElse(null);
    }

    /**
     * Gets the defender's UUID from the damage event context.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     * @return The defender's UUID, or null if not a player
     */
    @Nullable
    public UUID getDefenderUuid(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store
    ) {
        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return null;
        }

        PlayerRef playerRef = store.getComponent(defenderRef, PlayerRef.getComponentType());
        return playerRef != null ? playerRef.getUuid() : null;
    }

    /**
     * Gets the attacker's UUID from the damage event.
     * For players, returns their real UUID. For mobs, generates a pseudo-UUID
     * for ailment/death recap tracking.
     *
     * @param store The entity store
     * @param damage The damage event
     * @return The attacker's UUID (real for players, pseudo for mobs), or null if not identifiable
     */
    @Nullable
    public UUID getAttackerUuid(
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

        Ref<EntityStore> attackerRef = resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return null;
        }

        PlayerRef playerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());

        // For players, return their UUID
        if (playerRef != null) {
            return playerRef.getUuid();
        }

        // For mobs, generate a pseudo-UUID based on entity reference (consistent for the entity's lifetime)
        // This allows ailments to track mob sources for death recap
        return new UUID(attackerRef.hashCode(), System.identityHashCode(attackerRef));
    }

    /**
     * Gets the attacker's UUID ONLY if they are a player.
     * Returns null for mobs and non-entity damage sources.
     * Use this for skill tree/conditional triggers that only apply to players.
     *
     * @param store The entity store
     * @param damage The damage event
     * @return The attacker's UUID if they are a player, null otherwise
     */
    @Nullable
    public UUID getAttackerPlayerUuid(
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

        Ref<EntityStore> attackerRef = resolveTrueAttacker(store, immediateRef);
        if (attackerRef == null) {
            return null;
        }

        PlayerRef playerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());

        // Only return UUID for actual players
        return playerRef != null ? playerRef.getUuid() : null;
    }

    /**
     * Gets the attacker's entity reference from the damage event.
     *
     * @param store The entity store
     * @param damage The damage event
     * @return The true attacker reference, or null if not available
     */
    @Nullable
    public Ref<EntityStore> getAttackerRef(
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

        return resolveTrueAttacker(store, immediateRef);
    }

    /**
     * Gets the defender's current health as a percentage (0.0 to 1.0).
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     * @return Health percentage (0.0-1.0), or -1 if health cannot be determined
     */
    public float getDefenderHealthPercent(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store
    ) {
        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return -1f;
        }

        // Try to get health from entity stats
        EntityStatMap statMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            int healthIndex = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStat = statMap.get(healthIndex);
            if (healthStat != null) {
                // EntityStatValue.asPercentage() returns (value - min) / (max - min)
                return healthStat.asPercentage();
            }
        }
        return -1f;
    }

    /**
     * Gets the defender's entity reference from the archetype chunk.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @return The defender's entity reference, or null if invalid
     */
    @Nullable
    public Ref<EntityStore> getDefenderRef(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk
    ) {
        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return null;
        }
        return defenderRef;
    }
}
