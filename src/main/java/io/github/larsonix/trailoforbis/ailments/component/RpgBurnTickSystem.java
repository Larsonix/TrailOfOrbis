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
 * ECS tick system that applies Burn DOT damage to entities with {@link RpgBurnComponent}.
 *
 * <p>Only iterates entities that are actually burning — O(burning entities), not O(all entities).
 * Each tick fires a {@link Damage} event through {@link DamageSystems#executeDamage} with
 * {@link Damage.EntitySource} pointing to the burn applicator for kill attribution.
 *
 * <p>When burn expires, removes both the ECS component and the {@link AilmentTracker} entry.
 *
 * @see RpgBurnComponent
 * @see io.github.larsonix.trailoforbis.combat.RPGDamageSystem#handleDOTDamage
 */
public class RpgBurnTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Accumulation interval — fire damage every 0.5s instead of every ECS tick (~50ms).
     *  Without this, each tick deals DPS×dt (micro-damage) which floors to "0" in combat text
     *  and gets over-reduced by per-tick defense application. 0.5s matches PoE/D4 DOT cadence. */
    private static final float DOT_TICK_INTERVAL = 0.5f;

    private final AilmentTracker tracker;
    private volatile DamageCause burnCause;

    public RpgBurnTickSystem(@Nonnull AilmentTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.and(RpgBurnComponent.TYPE);
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        RpgBurnComponent burn = archetypeChunk.getComponent(index, RpgBurnComponent.TYPE);
        if (burn == null) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        // Decrement duration
        burn.setRemainingDuration(burn.getRemainingDuration() - dt);

        // Accumulate time — only fire damage at intervals (not every ECS tick)
        float elapsed = burn.getElapsedSinceTick() + dt;
        boolean expired = burn.isExpired();

        if (elapsed >= DOT_TICK_INTERVAL || expired) {
            // Fire accumulated damage as a single chunk
            float damage = burn.getDps() * elapsed;
            if (damage > 0) {
                Damage.Source source = DotSourceResolver.resolveSource(burn.getSourceUuid(), store);
                Damage dmgEvent = new Damage(source, getBurnCause(), damage);
                DamageSystems.executeDamage(ref, commandBuffer, dmgEvent);
            }
            burn.setElapsedSinceTick(0f);
        } else {
            burn.setElapsedSinceTick(elapsed);
        }

        // Remove if expired
        if (expired) {
            commandBuffer.removeComponent(ref, RpgBurnComponent.TYPE);

            // Sync tracker
            UUID entityUuid = resolveEntityUuid(ref, store);
            if (entityUuid != null) {
                tracker.removeAilment(entityUuid, AilmentType.BURN);
            }

            LOGGER.atFine().log("Burn expired on entity, component removed");
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
    private DamageCause getBurnCause() {
        DamageCause cached = burnCause;
        if (cached == null) {
            cached = DamageCause.getAssetMap().getAsset(DamageTypeClassifier.CAUSE_RPG_BURN_DOT);
            if (cached == null) cached = DamageCause.PHYSICAL;
            burnCause = cached;
        }
        return cached;
    }
}
