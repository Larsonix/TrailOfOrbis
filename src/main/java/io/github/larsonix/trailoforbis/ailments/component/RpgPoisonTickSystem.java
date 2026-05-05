package io.github.larsonix.trailoforbis.ailments.component;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS tick system that applies Poison DOT damage to entities with {@link RpgPoisonComponent}.
 *
 * <p>Only iterates entities that are actually poisoned — O(poisoned entities), not O(all entities).
 * Combines all stack DPS into a single {@link Damage} event per tick, fired through
 * {@link DamageSystems#executeDamage} with kill attribution to the primary stack source.
 *
 * <p>When all stacks expire, removes both the ECS component and the {@link AilmentTracker} entry.
 *
 * @see RpgPoisonComponent
 * @see io.github.larsonix.trailoforbis.combat.RPGDamageSystem#handleDOTDamage
 */
public class RpgPoisonTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final AilmentTracker tracker;
    private volatile DamageCause poisonCause;

    public RpgPoisonTickSystem(@Nonnull AilmentTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(RpgPoisonComponent.TYPE);
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        RpgPoisonComponent poison = archetypeChunk.getComponent(index, RpgPoisonComponent.TYPE);
        if (poison == null || poison.isEmpty()) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        // Calculate combined damage from all stacks
        float damage = poison.calculateDamageThisTick(dt);

        // Fire single damage event for all stacks
        if (damage > 0) {
            Damage.Source source = resolveSource(poison.getPrimarySourceUuid(), store);
            Damage dmgEvent = new Damage(source, getPoisonCause(), damage);
            DamageSystems.executeDamage(ref, commandBuffer, dmgEvent);
        }

        // Tick all stack durations (removes expired stacks internally)
        poison.tickStacks(dt);

        // Remove component if all stacks expired
        if (poison.isEmpty()) {
            commandBuffer.removeComponent(ref, RpgPoisonComponent.TYPE);

            // Sync tracker
            UUID entityUuid = resolveEntityUuid(ref, store);
            if (entityUuid != null) {
                tracker.removeAilment(entityUuid, AilmentType.POISON);
            }

            LOGGER.atFine().log("All poison stacks expired, component removed");
        }
    }

    @Nonnull
    private Damage.Source resolveSource(@Nonnull UUID sourceUuid, @Nonnull Store<EntityStore> store) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(sourceUuid);
            if (playerRef != null) {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    return new Damage.EntitySource(ref);
                }
            }
        } catch (Exception ignored) {}

        try {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(sourceUuid);
            if (ref != null && ref.isValid()) {
                return new Damage.EntitySource(ref);
            }
        } catch (Exception ignored) {}

        return Damage.NULL_SOURCE;
    }

    @javax.annotation.Nullable
    private UUID resolveEntityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) return playerRef.getUuid();

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp != null) return uuidComp.getUuid();

        return null;
    }

    @Nonnull
    private DamageCause getPoisonCause() {
        DamageCause cached = poisonCause;
        if (cached == null) {
            cached = DamageCause.getAssetMap().getAsset(DamageTypeClassifier.CAUSE_RPG_POISON_DOT);
            if (cached == null) cached = DamageCause.PHYSICAL;
            poisonCause = cached;
        }
        return cached;
    }
}
