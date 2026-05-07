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

    /** Accumulation interval — fire damage every 0.5s instead of every ECS tick (~50ms).
     *  Without this, each tick deals DPS×dt (micro-damage) which floors to "0" in combat text
     *  and gets over-reduced by per-tick defense application. 0.5s matches PoE/D4 DOT cadence. */
    private static final float DOT_TICK_INTERVAL = 0.5f;

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

        // Snapshot DPS and source BEFORE ticking (tickStacks may remove expired stacks)
        float currentDps = poison.getTotalDps();
        UUID primarySource = poison.getPrimarySourceUuid();

        // Tick all stack durations (removes expired stacks internally)
        poison.tickStacks(dt);

        // Accumulate time — only fire damage at intervals (not every ECS tick)
        float elapsed = poison.getElapsedSinceTick() + dt;
        boolean allExpired = poison.isEmpty();

        if (elapsed >= DOT_TICK_INTERVAL || allExpired) {
            // Fire accumulated damage using pre-tick DPS snapshot
            float damage = currentDps * elapsed;
            if (damage > 0) {
                Damage.Source source = DotSourceResolver.resolveSource(primarySource, store);
                Damage dmgEvent = new Damage(source, getPoisonCause(), damage);
                DamageSystems.executeDamage(ref, commandBuffer, dmgEvent);
            }
            poison.setElapsedSinceTick(0f);
        } else {
            poison.setElapsedSinceTick(elapsed);
        }

        // Remove component if all stacks expired
        if (allExpired) {
            commandBuffer.removeComponent(ref, RpgPoisonComponent.TYPE);

            // Sync tracker
            UUID entityUuid = resolveEntityUuid(ref, store);
            if (entityUuid != null) {
                tracker.removeAilment(entityUuid, AilmentType.POISON);
            }

            LOGGER.atFine().log("All poison stacks expired, component removed");
        }
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
