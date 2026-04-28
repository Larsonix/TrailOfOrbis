package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Lightweight ticking system that polls hex state for players and
 * delegates to {@link CastingAuraInjector} to send particle packets.
 *
 * <p>Queries for player entities. On each tick, checks if the player
 * is in CASTING state and sends aura particles to the Casting_Anchor
 * entity for RPG staffs that don't get particles through the normal
 * entity tracker model sync.
 */
public class CastingAuraTickSystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
    private Archetype<EntityStore> cachedQuery;

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        if (cachedQuery == null) {
            cachedQuery = Archetype.of(playerRefType);
        }
        return cachedQuery;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> buffer) {

        PlayerRef playerRef = chunk.getComponent(index, playerRefType);
        if (playerRef == null) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        CastingAuraInjector.tick(store, ref, playerRef.getUuid(), playerRef);
    }
}
