package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.guide.GuideMilestone;

import javax.annotation.Nonnull;

/**
 * Fires the FIRST_CRAFT guide on CraftRecipeEvent.Pre — before the craft completes.
 * Players see the guide explaining crafting tooltips and material-based levels
 * at the moment they first interact with the crafting system.
 */
public final class CraftGuidePreSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    public CraftGuidePreSystem() {
        super(CraftRecipeEvent.Pre.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull CraftRecipeEvent.Pre event) {
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) return;

        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg != null && rpg.getGuideManager() != null) {
            rpg.getGuideManager().tryShow(playerRef.getUuid(), GuideMilestone.FIRST_CRAFT);
        }
    }
}
