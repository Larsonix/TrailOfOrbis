package io.github.larsonix.trailoforbis.maps.systems;

import com.hypixel.hytale.builtin.npccombatactionevaluator.NPCCombatActionEvaluatorPlugin;
import com.hypixel.hytale.builtin.npccombatactionevaluator.evaluator.CombatActionEvaluator;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ECS tick system that applies attack speed modifiers to realm mob NPCs.
 *
 * <p>This system processes mobs with {@link RealmMobComponent} that have an
 * attack speed multiplier > 1.0 and accelerates their attack cooldown accordingly.
 *
 * <p><b>How it works:</b>
 * <ul>
 *   <li>Hytale's {@link CombatActionEvaluator} tracks {@code basicAttackCooldown}</li>
 *   <li>Each tick, the cooldown is reduced by delta time (dt)</li>
 *   <li>This system adds extra cooldown reduction: {@code dt * (multiplier - 1.0)}</li>
 *   <li>Net effect: cooldown ticks down at {@code multiplier}x speed</li>
 * </ul>
 *
 * <p><b>Example:</b>
 * <ul>
 *   <li>Attack speed multiplier = 1.3 (+30% monster attack speed)</li>
 *   <li>Normal cooldown reduction per tick = dt</li>
 *   <li>Extra reduction = dt * 0.3</li>
 *   <li>Total reduction = dt * 1.3 (30% faster attacks)</li>
 * </ul>
 *
 * @see RealmMobComponent#getAttackSpeedMultiplier()
 * @see CombatActionEvaluator#tickBasicAttackCoolDown(float)
 */
public class RealmAttackSpeedSystem extends EntityTickingSystem<EntityStore> {

    private final TrailOfOrbis plugin;

    // Component types for query
    private final ComponentType<EntityStore, RealmMobComponent> realmMobType;
    private final ComponentType<EntityStore, NPCEntity> npcType;

    // Combat action evaluator component type (lazily initialized)
    @Nullable
    private ComponentType<EntityStore, CombatActionEvaluator> combatEvaluatorType;

    // Cached query (lazily initialized)
    @Nullable
    private Query<EntityStore> mobQuery;

    /**
     * Creates a new realm attack speed system.
     *
     * @param plugin The TrailOfOrbis plugin instance
     */
    public RealmAttackSpeedSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.realmMobType = RealmMobComponent.getComponentType();
        this.npcType = NPCEntity.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Lazy initialization to ensure component types are registered
        if (mobQuery == null) {
            // Query for entities with RealmMobComponent and NPCEntity
            // CombatActionEvaluator is checked per-entity since not all NPCs have it
            mobQuery = Archetype.of(realmMobType, npcType);
        }
        return mobQuery;
    }

    /**
     * Gets the CombatActionEvaluator component type lazily.
     * <p>This is done lazily because the plugin providing this component
     * may not be loaded when our system is registered.
     *
     * @return The component type, or null if not available
     */
    @Nullable
    private ComponentType<EntityStore, CombatActionEvaluator> getCombatEvaluatorType() {
        if (combatEvaluatorType == null) {
            try {
                combatEvaluatorType = NPCCombatActionEvaluatorPlugin.get()
                    .getCombatActionEvaluatorComponentType();
            } catch (Exception e) {
                // Plugin not available - will retry next tick
                return null;
            }
        }
        return combatEvaluatorType;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Get entity reference
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        // Get RealmMobComponent to check attack speed multiplier
        RealmMobComponent realmMob = store.getComponent(entityRef, realmMobType);
        if (realmMob == null) {
            return;
        }

        float attackSpeedMultiplier = realmMob.getAttackSpeedMultiplier();

        // Skip if no attack speed bonus (multiplier is 1.0 or less)
        if (attackSpeedMultiplier <= 1.0f) {
            return;
        }

        // Get the CombatActionEvaluator component
        ComponentType<EntityStore, CombatActionEvaluator> evalType = getCombatEvaluatorType();
        if (evalType == null) {
            return;
        }

        CombatActionEvaluator combatEval = store.getComponent(entityRef, evalType);
        if (combatEval == null) {
            // Not all NPCs have combat evaluators (e.g., passive mobs)
            return;
        }

        // Apply bonus cooldown reduction
        // Normal tick already reduces by dt, we add extra: dt * (multiplier - 1)
        // Total reduction = dt + dt * (multiplier - 1) = dt * multiplier
        float extraReduction = dt * (attackSpeedMultiplier - 1.0f);
        combatEval.tickBasicAttackCoolDown(extraReduction);
    }
}
