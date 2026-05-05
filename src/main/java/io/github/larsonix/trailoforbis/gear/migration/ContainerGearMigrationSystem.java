package io.github.larsonix.trailoforbis.gear.migration;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Migrates stale gear items inside world containers (chests, barrels, crates)
 * when a player opens them.
 *
 * <p>This system fires on EVERY container interaction, regardless of loot system
 * config or scope. It's unconditional because:
 * <ul>
 *   <li>Any container in any world might hold stale gear</li>
 *   <li>Per-item version check is O(1) — zero-cost for already-stamped items</li>
 *   <li>Runs BEFORE the player sees container contents</li>
 * </ul>
 *
 * <p>Completely independent from {@link io.github.larsonix.trailoforbis.loot.container.ContainerLootInterceptor}
 * (which handles loot generation/replacement and has its own scope + tracking logic).
 */
public class ContainerGearMigrationSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ComponentType<ChunkStore, ItemContainerBlock> CONTAINER_COMP_TYPE =
            ItemContainerBlock.getComponentType();

    private final ItemMigrationService migrationService;

    public ContainerGearMigrationSystem(@Nonnull ItemMigrationService migrationService) {
        super(UseBlockEvent.Pre.class);
        this.migrationService = migrationService;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event) {

        // Only handle right-click (open container)
        if (event.getInteractionType() != InteractionType.Use) {
            return;
        }

        // Get player for logging
        Ref<EntityStore> playerEntityRef = event.getContext().getEntity();
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        // Resolve the block's ItemContainerBlock component
        Vector3i target = event.getTargetBlock();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(target.x, target.z));
        if (chunk == null) return;

        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(target.x, target.y, target.z);
        if (blockRef == null || !blockRef.isValid()) return;

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        ItemContainerBlock containerBlock = chunkStore.getComponent(blockRef, CONTAINER_COMP_TYPE);
        if (containerBlock == null) {
            return; // Not a container block (crafting bench, etc.)
        }

        // Get the item container and process it
        ItemContainer container = containerBlock.getItemContainer();
        if (container == null) {
            return;
        }

        // Get player UUID for logging
        PlayerRef playerRefComp = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        UUID playerId = playerRefComp != null ? playerRefComp.getUuid() : null;

        // Process — converts vanilla items + fixes stale RPG items in world containers
        int[] counts = migrationService.processContainer(container, playerId, "world_container",
                ItemMigrationService.LEVEL_FROM_MATERIAL);
        int total = counts[0] + counts[1];
        if (total > 0) {
            LOGGER.at(Level.INFO).log("Item integrity: %d converted, %d migrated in container at (%d, %d, %d) in %s",
                    counts[0], counts[1], target.x, target.y, target.z, world.getName());
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
