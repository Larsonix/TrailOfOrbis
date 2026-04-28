package io.github.larsonix.trailoforbis.combat.triggers;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalTriggerSystem;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Handles skill tree conditional triggers during combat.
 *
 * <p>This class extracts trigger logic from RPGDamageSystem, providing:
 * <ul>
 *   <li>ON_KILL trigger for attacker when killing an enemy</li>
 *   <li>ON_CRIT trigger for attacker after critical hits</li>
 *   <li>WHEN_HIT trigger for defender when taking damage</li>
 *   <li>ON_EVADE trigger for defender when dodging/evading</li>
 *   <li>ON_BLOCK trigger for defender when blocking</li>
 * </ul>
 */
public class CombatTriggerHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CombatEntityResolver entityResolver;
    private final ConditionalTriggerSystem triggerSystem;

    /**
     * Creates a new CombatTriggerHandler.
     *
     * @param entityResolver The entity resolver for UUID lookups
     * @param triggerSystem The conditional trigger system (may be null if not initialized)
     */
    public CombatTriggerHandler(
        @Nonnull CombatEntityResolver entityResolver,
        @Nullable ConditionalTriggerSystem triggerSystem
    ) {
        this.entityResolver = entityResolver;
        this.triggerSystem = triggerSystem;
    }

    /**
     * Fires ON_KILL conditional trigger for the attacker after killing an enemy.
     * Only fires for player attackers (mobs don't have skill trees).
     *
     * @param store The entity store
     * @param damage The damage event
     */
    public void fireOnKillTrigger(@Nonnull Store<EntityStore> store, @Nonnull Damage damage) {
        UUID attackerUuid = entityResolver.getAttackerPlayerUuid(store, damage);
        fireConditionalTrigger(attackerUuid, trigger -> trigger.onKill(attackerUuid, getSkillTreeData(attackerUuid)));
    }

    /**
     * Fires ON_CRIT conditional trigger for the attacker after a critical hit.
     * Only fires for player attackers (mobs don't have skill trees).
     *
     * @param store The entity store
     * @param damage The damage event
     */
    public void fireOnCritTrigger(@Nonnull Store<EntityStore> store, @Nonnull Damage damage) {
        UUID attackerUuid = entityResolver.getAttackerPlayerUuid(store, damage);
        fireConditionalTrigger(attackerUuid, trigger -> trigger.onCrit(attackerUuid, getSkillTreeData(attackerUuid)));
    }

    /**
     * Fires WHEN_HIT conditional trigger for the defender after taking damage.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     */
    public void fireWhenHitTrigger(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store
    ) {
        UUID defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);
        fireConditionalTrigger(defenderUuid, trigger -> trigger.onHit(defenderUuid, getSkillTreeData(defenderUuid)));
    }

    /**
     * Fires ON_EVADE conditional trigger for the defender after successfully evading.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     */
    public void fireOnEvadeTrigger(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store
    ) {
        UUID defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);
        fireConditionalTrigger(defenderUuid, trigger -> trigger.onEvade(defenderUuid, getSkillTreeData(defenderUuid)));
    }

    /**
     * Fires ON_BLOCK conditional trigger for the defender after successfully blocking.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     */
    public void fireOnBlockTrigger(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store
    ) {
        UUID defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);
        fireConditionalTrigger(defenderUuid, trigger -> trigger.onBlock(defenderUuid, getSkillTreeData(defenderUuid)));
    }

    /**
     * Helper to fire a conditional trigger if the system is available and player is valid.
     *
     * @param playerId The player's UUID (may be null for non-players)
     * @param action The trigger action to perform
     */
    private void fireConditionalTrigger(@Nullable UUID playerId, Consumer<ConditionalTriggerSystem> action) {
        if (playerId == null) {
            return;
        }
        if (triggerSystem == null) {
            return;
        }
        SkillTreeData data = getSkillTreeData(playerId);
        if (data == null) {
            return;
        }
        try {
            action.accept(triggerSystem);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Error firing conditional trigger for player %s", playerId);
        }
    }

    /**
     * Gets the skill tree data for a player.
     *
     * @param playerId The player's UUID
     * @return The skill tree data, or null if not available
     */
    @Nullable
    public SkillTreeData getSkillTreeData(@Nullable UUID playerId) {
        if (playerId == null) {
            return null;
        }
        SkillTreeService service = ServiceRegistry.get(SkillTreeService.class).orElse(null);
        if (service == null) {
            return null;
        }
        return service.getSkillTreeData(playerId);
    }
}
