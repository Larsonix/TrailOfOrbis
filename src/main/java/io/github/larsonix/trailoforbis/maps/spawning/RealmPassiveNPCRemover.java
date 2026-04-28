package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationContext;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationService;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ECS system that removes passive wildlife NPCs (birds, critters, etc.) from realm instances.
 *
 * <p>This is a brute-force approach: it intercepts ALL NPCs entering the entity store,
 * checks if they're in a realm world, classifies them, and removes any PASSIVE NPCs.
 * This catches wildlife regardless of which spawning mechanism created them
 * (WorldSpawningSystem, SpawnMarkerSystems, LocalSpawnController, world gen, etc.).
 *
 * <p>Only affects realm worlds (world name starts with "instance-realm_"). Combat NPCs
 * spawned by our {@link RealmEntitySpawner} are classified as HOSTILE or higher, so they
 * are never removed.
 */
public class RealmPassiveNPCRemover extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String REALM_WORLD_PREFIX = "instance-realm_";

    private final TrailOfOrbis plugin;

    public RealmPassiveNPCRemover(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        World world = store.getExternalData().getWorld();
        if (world == null || !world.getName().startsWith(REALM_WORLD_PREFIX)) {
            return;
        }

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }

        MobScalingManager scalingManager = plugin.getMobScalingManager();
        if (scalingManager == null) {
            return;
        }

        MobClassificationService classificationService = scalingManager.getClassificationService();
        if (classificationService == null) {
            return;
        }

        MobClassificationContext context = scalingManager.createContext(npc);
        RPGMobClass classification = classificationService.classify(context);

        if (classification == RPGMobClass.PASSIVE) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            LOGGER.atInfo().log("Removed passive NPC '%s' from realm world '%s'",
                    npc.getRoleName(), world.getName());
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // No cleanup needed on removal
    }
}
