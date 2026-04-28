package io.github.larsonix.trailoforbis.loot.container;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * ECS event system that intercepts container opens for world containers (chests,
 * barrels, crates in dungeons/caves/villages) and replaces vanilla weapons/armor
 * with RPG gear.
 *
 * <p>When a player right-clicks a container block, this system:
 * <ol>
 *   <li>Verifies it's an {@link ItemContainerBlock} (not a crafting bench, etc.)</li>
 *   <li>Skips if it's a reward chest (handled by {@link RewardChestManager})</li>
 *   <li>Skips if Loot4Everyone manages the container</li>
 *   <li>Skips if already processed by {@link ContainerTracker}</li>
 *   <li>Replaces vanilla weapons/armor with RPG gear via {@link ContainerLootReplacer}</li>
 *   <li>Does NOT cancel the event — vanilla opens the container UI normally</li>
 * </ol>
 *
 * <p>This follows the same {@code UseBlockEvent.Pre} pattern used by both
 * Loot4Everyone and our own {@link RewardChestManager}'s interceptor.
 *
 * @see ContainerLootSystem
 * @see ContainerLootReplacer
 * @see ContainerTracker
 */
public class ContainerLootInterceptor extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ComponentType<ChunkStore, ItemContainerBlock> CONTAINER_COMP_TYPE =
        ItemContainerBlock.getComponentType();

    private final ContainerLootSystem lootSystem;
    private final RewardChestManager rewardChestManager;
    private final RealmsManager realmsManager;
    private final ResourceType<ChunkStore, ProcessedContainerResource> processedContainerResType;
    private final ContainerLootConfig.ReplacementScope scope;
    private final boolean clearAllVanilla;
    private final boolean debugLogging;

    // Lazy-initialized L4E detection
    private Boolean loot4EveryonePresent;

    /**
     * Creates a new container loot interceptor.
     *
     * @param lootSystem                The container loot system (provides replacer, tracker, tier classifier)
     * @param rewardChestManager        The reward chest manager (to skip reward chests), or null if unavailable
     * @param realmsManager             The realms manager (for realm world detection), or null if unavailable
     * @param processedContainerResType The persistent resource type for tracking processed containers
     */
    public ContainerLootInterceptor(
            @Nonnull ContainerLootSystem lootSystem,
            @Nullable RewardChestManager rewardChestManager,
            @Nullable RealmsManager realmsManager,
            @Nonnull ResourceType<ChunkStore, ProcessedContainerResource> processedContainerResType) {
        super(UseBlockEvent.Pre.class);
        this.lootSystem = lootSystem;
        this.rewardChestManager = rewardChestManager;
        this.realmsManager = realmsManager;
        this.processedContainerResType = processedContainerResType;
        this.scope = lootSystem.getConfig().getReplacementScope();
        this.clearAllVanilla = lootSystem.getConfig().isClearAllVanilla();
        this.debugLogging = lootSystem.getConfig().getAdvanced().isDebugLogging();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event) {

        // Only handle "Use" interaction (right-click)
        if (event.getInteractionType() != InteractionType.Use) {
            return;
        }

        if (!lootSystem.isEnabled()) {
            return;
        }

        Vector3i target = event.getTargetBlock();
        Ref<EntityStore> playerRef = event.getContext().getEntity();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        World world = player.getWorld();

        // Get the block's ECS ref and look up the ItemContainerBlock component
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(target.x, target.z));
        if (chunk == null) return;
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(target.x, target.y, target.z);
        if (blockRef == null || !blockRef.isValid()) return;
        Store<ChunkStore> chunkStoreStore = world.getChunkStore().getStore();
        ItemContainerBlock containerState = chunkStoreStore.getComponent(blockRef, CONTAINER_COMP_TYPE);

        // Only process item containers (chests, barrels, crates)
        if (containerState == null) {
            return;
        }

        // Skip reward chests (handled by RewardChestInterceptor)
        if (rewardChestManager != null) {
            UUID worldId = world.getWorldConfig().getUuid();
            if (rewardChestManager.isRewardChest(target.x, target.y, target.z, worldId)) {
                return;
            }
        }

        // Skip containers managed by Loot4Everyone
        if (isLoot4EveryoneManaged(chunkStoreStore, target)) {
            if (debugLogging) {
                LOGGER.atFine().log("Container at (%d, %d, %d) managed by L4E, skipping",
                    target.x, target.y, target.z);
            }
            // Guide milestone: Loot4Everyone is active
            com.hypixel.hytale.server.core.universe.PlayerRef playerRefComp = store.getComponent(playerRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (playerRefComp != null) {
                io.github.larsonix.trailoforbis.TrailOfOrbis rpg = io.github.larsonix.trailoforbis.TrailOfOrbis.getInstanceOrNull();
                if (rpg != null && rpg.getGuideManager() != null) {
                    rpg.getGuideManager().tryShow(playerRefComp.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.LOOT4EVERYONE);
                }
            }
            return;
        }

        // Check scope — should we process containers in this world?
        boolean isRealmWorld = (realmsManager != null) && realmsManager.getRealmByWorld(world).isPresent();

        if (scope == ContainerLootConfig.ReplacementScope.REALM_ONLY && !isRealmWorld) {
            return; // Overworld container, realm-only mode — skip
        }
        if (scope == ContainerLootConfig.ReplacementScope.DISABLED) {
            return;
        }

        // Get block type ID for tier classification
        String blockTypeId = event.getBlockType() != null ? event.getBlockType().getId() : null;

        // Check persistent resource — survives server restarts
        ProcessedContainerResource processedResource =
            chunkStoreStore.getResource(processedContainerResType);
        if (processedResource.isProcessed(target.x, target.y, target.z)) {
            if (debugLogging) {
                LOGGER.atFine().log("Container at (%d, %d, %d) in %s already processed (persistent)",
                    target.x, target.y, target.z, world.getName());
            }
            return;
        }

        // Get player UUID via ECS PlayerRef (Entity.getUuid() deprecated for removal)
        PlayerRef playerRefComp = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComp == null) return;
        UUID playerId = playerRefComp.getUuid();
        int playerLevel = getPlayerLevel(playerId);

        // Classify container tier
        ContainerTier tier = lootSystem.getTierClassifier().classify(blockTypeId);

        // Mark as processed in persistent resource (prevents race conditions + survives restarts)
        boolean wasFirstOpener = processedResource.markProcessed(
            target.x, target.y, target.z, playerId);
        if (!wasFirstOpener) {
            return;
        }

        // Choose replacement strategy based on config
        ContainerLootReplacer.ReplacementResult result;
        if (clearAllVanilla && isRealmWorld) {
            // Total replacement — clear everything, fill with RPG items
            result = lootSystem.getReplacer().replaceTotal(
                containerState.getItemContainer(), playerLevel, tier, playerId);
        } else {
            // Selective replacement — remove weapons/armor, preserve materials
            result = lootSystem.getReplacer().replace(
                containerState.getItemContainer(), playerLevel, tier, playerId);
        }

        LOGGER.atInfo().log(
            "Container loot at (%d, %d, %d) in %s: %s (player lv%d, block: %s, realm: %s)",
            target.x, target.y, target.z, world.getName(),
            result.summary(), playerLevel,
            blockTypeId != null ? blockTypeId : "unknown",
            isRealmWorld ? "yes" : "no");

        // Do NOT cancel event — let vanilla Open_Container interaction proceed
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    /**
     * Gets a player's level from the leveling service.
     */
    private int getPlayerLevel(@Nonnull UUID playerId) {
        Optional<LevelingService> serviceOpt = ServiceRegistry.get(LevelingService.class);
        if (serviceOpt.isPresent()) {
            return serviceOpt.get().getLevel(playerId);
        }
        return 1;
    }

    /**
     * Checks if a container is managed by Loot4Everyone.
     *
     * <p>Uses reflection to avoid compile-time dependency on L4E.
     * When L4E is present, it manages containers that have a
     * {@code LootChestTemplate} entry — we must skip those to prevent
     * double-processing.
     *
     * @param containerState The container state to check
     * @param pos            The block position
     * @return true if L4E manages this container
     */
    private boolean isLoot4EveryoneManaged(
            @Nonnull Store<ChunkStore> chunkStoreStore,
            @Nonnull Vector3i pos) {

        if (!isLoot4EveryonePresent()) {
            return false;
        }

        try {
            // L4E check: get LootChestTemplate resource from the chunk store
            // and see if it has a template for this position.
            // This mirrors L4E's own UseBlockEventPre.java:51 logic.
            Object l4e = Class.forName("org.mimstar.plugin.Loot4Everyone")
                .getMethod("get")
                .invoke(null);
            Object resourceType = l4e.getClass()
                .getMethod("getlootChestTemplateResourceType")
                .invoke(l4e);

            Store<?> chunkStore = chunkStoreStore;
            Object template = chunkStore.getClass()
                .getMethod("getResource", resourceType.getClass().getInterfaces()[0])
                .invoke(chunkStore, resourceType);

            if (template != null) {
                Boolean hasTemplate = (Boolean) template.getClass()
                    .getMethod("hasTemplate", int.class, int.class, int.class)
                    .invoke(template, pos.x, pos.y, pos.z);
                return Boolean.TRUE.equals(hasTemplate);
            }
        } catch (Exception e) {
            // L4E integration failed — treat as not managed
            if (debugLogging) {
                LOGGER.atFine().withCause(e).log("L4E template check failed for (%d, %d, %d)",
                    pos.x, pos.y, pos.z);
            }
        }

        return false;
    }

    /**
     * Checks if Loot4Everyone is present at runtime.
     */
    private boolean isLoot4EveryonePresent() {
        if (loot4EveryonePresent == null) {
            try {
                Class.forName("org.mimstar.plugin.Loot4Everyone");
                loot4EveryonePresent = true;
                LOGGER.atInfo().log("Loot4Everyone detected — will skip L4E-managed containers");
            } catch (ClassNotFoundException e) {
                loot4EveryonePresent = false;
            }
        }
        return loot4EveryonePresent;
    }
}
