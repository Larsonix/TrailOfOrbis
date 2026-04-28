package io.github.larsonix.trailoforbis.ailments;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central tracker for all entity ailment states.
 *
 * <p>Uses UUID-based tracking (similar to {@code EnergyShieldTracker})
 * to support both players and mobs.
 *
 * <p><b>Thread Safety:</b> This class uses {@link ConcurrentHashMap} for
 * thread-safe entity lookup. Individual {@link EntityAilmentState} operations
 * are atomic at the entity level via compute operations.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // Apply ailment
 * tracker.applyAilment(targetUuid, AilmentState.burn(10f, 4f, attackerUuid));
 *
 * // Check shock damage amp
 * float shockBonus = tracker.getShockDamageIncreasePercent(targetUuid);
 *
 * // Cleanup on death/disconnect
 * tracker.cleanup(entityUuid);
 * }</pre>
 */
public class AilmentTracker {

    /** Per-entity ailment states. Key: Entity UUID, Value: Ailment state container */
    private final ConcurrentHashMap<UUID, EntityAilmentState> entities = new ConcurrentHashMap<>();

    /** Maximum poison stacks (configurable) */
    private int maxPoisonStacks = 10;

    public AilmentTracker() {
    }

    /**
     * Sets the maximum poison stacks for new entities.
     */
    public void setMaxPoisonStacks(int maxStacks) {
        this.maxPoisonStacks = Math.max(1, maxStacks);
    }

    /**
     * @return The entity's ailment state (never null)
     */
    @Nonnull
    public EntityAilmentState getOrCreate(@Nonnull UUID entityUuid) {
        return entities.computeIfAbsent(entityUuid, k -> {
            EntityAilmentState state = new EntityAilmentState();
            state.setMaxPoisonStacks(maxPoisonStacks);
            return state;
        });
    }

    /**
     * @return The entity's ailment state, or null if no ailments
     */
    @Nullable
    public EntityAilmentState get(@Nonnull UUID entityUuid) {
        return entities.get(entityUuid);
    }

    /** Cleans up ailment state when an entity is removed (death, disconnect, despawn). */
    public void cleanup(@Nonnull UUID entityUuid) {
        entities.remove(entityUuid);
    }

    /**
     * @return true if applied successfully
     */
    public boolean applyAilment(@Nonnull UUID targetUuid, @Nonnull AilmentState state) {
        return getOrCreate(targetUuid).applyAilment(state);
    }

    /**
     * @return true if the entity has that ailment active
     */
    public boolean hasAilment(@Nonnull UUID entityUuid, @Nonnull AilmentType type) {
        EntityAilmentState state = entities.get(entityUuid);
        return state != null && state.hasAilment(type);
    }

    /**
     * @return true if entity has any ailment
     */
    public boolean hasAnyAilment(@Nonnull UUID entityUuid) {
        EntityAilmentState state = entities.get(entityUuid);
        return state != null && state.hasAnyAilment();
    }

    /**
     * @return Slow percentage (0-30), or 0 if not frozen
     */
    public float getFreezeSlowPercent(@Nonnull UUID entityUuid) {
        EntityAilmentState state = entities.get(entityUuid);
        return state != null ? state.getFreezeSlowPercent() : 0f;
    }

    /**
     * @return Damage increase percentage (0-50), or 0 if not shocked
     */
    public float getShockDamageIncreasePercent(@Nonnull UUID entityUuid) {
        EntityAilmentState state = entities.get(entityUuid);
        return state != null ? state.getShockDamageIncreasePercent() : 0f;
    }

    /**
     * @return Number of poison stacks, or 0 if not poisoned
     */
    public int getPoisonStackCount(@Nonnull UUID entityUuid) {
        EntityAilmentState state = entities.get(entityUuid);
        return state != null ? state.getPoisonStackCount() : 0;
    }

    /**
     * @return Combined DPS from all poison stacks, or 0 if not poisoned
     */
    public float getTotalPoisonDps(@Nonnull UUID entityUuid) {
        EntityAilmentState state = entities.get(entityUuid);
        return state != null ? state.getTotalPoisonDps() : 0f;
    }

    /**
     * Gets the total remaining DoT damage on an entity from all active
     * damage-over-time ailments (Burn + Poison stacks).
     *
     * @return Total remaining DoT damage, or 0 if no DoTs active
     */
    public float getRemainingDotDamage(@Nonnull UUID entityUuid) {
        EntityAilmentState state = entities.get(entityUuid);
        return state != null ? state.getRemainingDotDamage() : 0f;
    }

    /**
     * Detonates all DoT ailments on an entity, removing Burn and Poison
     * while keeping Freeze and Shock intact.
     */
    public void detonateAllDots(@Nonnull UUID entityUuid) {
        EntityAilmentState state = entities.get(entityUuid);
        if (state != null) {
            state.detonateAllDots();
            if (!state.hasAnyAilment()) {
                entities.remove(entityUuid);
            }
        }
    }

    /** Removes a specific ailment from an entity. */
    public void removeAilment(@Nonnull UUID entityUuid, @Nonnull AilmentType type) {
        EntityAilmentState state = entities.get(entityUuid);
        if (state != null) {
            state.removeAilment(type);
            // Clean up empty states
            if (!state.hasAnyAilment()) {
                entities.remove(entityUuid);
            }
        }
    }

    /** Gets all entities with active ailments (for tick processing). */
    @Nonnull
    public Set<UUID> getAffectedEntities() {
        return new HashSet<>(entities.keySet());
    }

    /** Gets the number of tracked entities (for metrics/debugging). */
    public int getTrackedEntityCount() {
        return entities.size();
    }

    /**
     * Processes tick for an entity and returns DoT damage.
     *
     * <p>This method atomically ticks the entity's ailments and returns
     * the total DoT damage to apply.
     *
     * @return Total DoT damage from Burn + Poison, or 0 if no ailments
     */
    public float tickEntity(@Nonnull UUID entityUuid, float dt) {
        EntityAilmentState state = entities.get(entityUuid);
        if (state == null) {
            return 0f;
        }

        float damage = state.tickAndGetDamage(dt);

        // Clean up empty states
        if (!state.hasAnyAilment()) {
            entities.remove(entityUuid);
        }

        return damage;
    }

    /** Gets all active ailments for an entity (for UI/debugging). */
    @Nonnull
    public List<AilmentState> getAllAilments(@Nonnull UUID entityUuid) {
        EntityAilmentState state = entities.get(entityUuid);
        return state != null ? state.getAllAilments() : Collections.emptyList();
    }

    /**
     * Clears all tracked ailments (for shutdown/reset).
     */
    public void clearAll() {
        entities.clear();
    }

    @Override
    public String toString() {
        return String.format("AilmentTracker[%d entities tracked]", entities.size());
    }
}
