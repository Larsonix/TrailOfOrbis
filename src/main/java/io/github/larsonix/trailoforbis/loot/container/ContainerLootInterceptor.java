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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.compat.L4EComponentBridge;
import io.github.larsonix.trailoforbis.gear.item.ItemWorldSyncService;
import io.github.larsonix.trailoforbis.gear.loot.RealmLootContext;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestManager;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;

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
 *   <li>Skips if it's a reward chest (handled by L4E bridge or {@link RewardChestManager})</li>
 *   <li>Recovers from L4E cancellations (stale locks, empty drops)</li>
 *   <li>Removes L4E auto-registered templates so we own the container</li>
 *   <li>Skips if already processed (per-player persistent tracking)</li>
 *   <li>Replaces vanilla weapons/armor with RPG gear via {@link ContainerLootReplacer}</li>
 *   <li>Does NOT cancel the event — vanilla opens the container UI normally</li>
 * </ol>
 *
 * <p>Overrides {@code shouldProcessEvent} to always return true, ensuring this
 * handler runs even when L4E has cancelled the event. This is critical because
 * L4E auto-claims all containers with droplists and has 6+ cancel paths that
 * can silently block chest opens.
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

    // L4E compatibility bridge (null when L4E is not installed)
    @Nullable
    private final L4EComponentBridge l4eBridge;

    // Item sync service for sending custom item definitions to clients (null when GearManager unavailable)
    @Nullable
    private final ItemWorldSyncService itemWorldSyncService;

    // Lazy-initialized distance calculator for overworld source level
    private DistanceBonusCalculator distanceBonusCalculator;

    /**
     * Creates a new container loot interceptor.
     *
     * @param lootSystem                The container loot system (provides replacer, tracker, tier classifier)
     * @param rewardChestManager        The reward chest manager (to skip reward chests), or null if unavailable
     * @param realmsManager             The realms manager (for realm world detection), or null if unavailable
     * @param processedContainerResType The persistent resource type for tracking processed containers
     * @param l4eBridge                 The L4E component bridge for stale lock cleanup and template removal, or null
     * @param itemWorldSyncService     The item sync service for sending custom definitions to clients, or null
     */
    public ContainerLootInterceptor(
            @Nonnull ContainerLootSystem lootSystem,
            @Nullable RewardChestManager rewardChestManager,
            @Nullable RealmsManager realmsManager,
            @Nonnull ResourceType<ChunkStore, ProcessedContainerResource> processedContainerResType,
            @Nullable L4EComponentBridge l4eBridge,
            @Nullable ItemWorldSyncService itemWorldSyncService) {
        super(UseBlockEvent.Pre.class);
        this.lootSystem = lootSystem;
        this.rewardChestManager = rewardChestManager;
        this.realmsManager = realmsManager;
        this.processedContainerResType = processedContainerResType;
        this.scope = lootSystem.getConfig().getReplacementScope();
        this.clearAllVanilla = lootSystem.getConfig().isClearAllVanilla();
        this.debugLogging = lootSystem.getConfig().getAdvanced().isDebugLogging();
        this.l4eBridge = l4eBridge;
        this.itemWorldSyncService = itemWorldSyncService;
    }

    /**
     * Always process events, even if another system (L4E) has cancelled them.
     *
     * <p>L4E cancels {@code UseBlockEvent.Pre} for multiple reasons (stale lock,
     * empty drops, etc.). The default {@code shouldProcessEvent} skips cancelled
     * events, which would make our handler blind to L4E failures. By overriding
     * to always return true, we can detect and recover from L4E cancellations.
     */
    @Override
    protected boolean shouldProcessEvent(@Nonnull UseBlockEvent.Pre event) {
        return true;
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

        // Skip reward chests — handled by L4E bridge or our RewardChestInterceptor
        if (rewardChestManager != null) {
            UUID worldId = world.getWorldConfig().getUuid();
            if (rewardChestManager.isRewardChest(target.x, target.y, target.z, worldId)) {
                return;
            }
        }

        // ── L4E cancelled-event recovery ──
        // If L4E (or another system) cancelled this event, determine if we should
        // recover or respect the cancellation.
        boolean removedL4eComponent = false;
        if (event.isCancelled()) {
            // Container genuinely open by another player — respect cancellation
            if (!containerState.getWindows().isEmpty()) {
                return;
            }

            // L4E stale OpenedContainerComponent lock — remove it
            if (l4eBridge != null && l4eBridge.hasOpenedContainer(store, playerRef)) {
                l4eBridge.removeOpenedContainerViaCommandBuffer(commandBuffer, playerRef);
                removedL4eComponent = true;
                LOGGER.atInfo().log("Removed stale L4E OpenedContainerComponent for player at (%d, %d, %d)",
                    target.x, target.y, target.z);
            }

            // Uncancelled so vanilla opens the container after we process it
            event.setCancelled(false);
        }

        // Check if L4E managed this container BEFORE removing the template.
        // L4E only adds OpenedContainerComponent when hasTemplate() is true, and
        // CommandBuffer.removeComponent() is deferred — our try-catch can't catch
        // the IllegalArgumentException thrown during Store.tick() if the component
        // was never added.
        boolean l4eHadTemplate = l4eBridge != null
            && l4eBridge.hasTemplate(chunkStoreStore, target.x, target.y, target.z);

        if (l4eHadTemplate) {
            l4eBridge.removeTemplate(chunkStoreStore, target.x, target.y, target.z);
            if (debugLogging) {
                LOGGER.atFine().log("Removed L4E auto-registered template at (%d, %d, %d)",
                    target.x, target.y, target.z);
            }
        }

        // Check scope — should we process containers in this world?
        boolean isRealmWorld = (realmsManager != null) && realmsManager.getRealmByWorld(world).isPresent();

        if (scope == ContainerLootConfig.ReplacementScope.REALM_ONLY && !isRealmWorld) {
            return; // Overworld container, realm-only mode — skip
        }
        if (scope == ContainerLootConfig.ReplacementScope.DISABLED) {
            return;
        }

        // Get player UUID via ECS PlayerRef (needed for per-player processing check)
        PlayerRef playerRefComp = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComp == null) return;
        UUID playerId = playerRefComp.getUuid();

        // Get block type ID for tier classification
        String blockTypeId = event.getBlockType() != null ? event.getBlockType().getId() : null;

        // Check persistent resource — per-player tracking (each player gets their own loot)
        ProcessedContainerResource processedResource =
            chunkStoreStore.getResource(processedContainerResType);
        if (processedResource.isProcessedByPlayer(target.x, target.y, target.z, playerId)) {
            // Defensive recovery: if the container was externally cleared (e.g., L4E's
            // ContainerMonitoringSystem clears on window close), re-process rather than
            // showing the player an empty chest.
            if (!isContainerEmpty(containerState.getItemContainer())) {
                if (debugLogging) {
                    LOGGER.atFine().log("Container at (%d, %d, %d) in %s already processed for player %s",
                        target.x, target.y, target.z, world.getName(), playerId);
                }
                return;
            }
            // Container was cleared externally — remove processed flag and re-process
            processedResource.removeForPlayer(target.x, target.y, target.z, playerId);
            LOGGER.atInfo().log("Container at (%d, %d, %d) in %s was externally cleared for player %s, re-processing",
                target.x, target.y, target.z, world.getName(), playerId);
        }

        int playerLevel = getPlayerLevel(playerId);

        // Build zone-aware loot context (mirrors LootListener's realm context extraction)
        ContainerLootContext lootContext = buildLootContext(world, target, isRealmWorld, playerLevel);

        // Classify container tier
        ContainerTier tier = lootSystem.getTierClassifier().classify(blockTypeId);

        // Mark as processed for THIS player (prevents double-replacement on rapid re-open)
        processedResource.markProcessed(target.x, target.y, target.z, playerId);

        // Choose replacement strategy based on config
        ContainerLootReplacer.ReplacementResult result;
        if (clearAllVanilla && isRealmWorld) {
            // Total replacement — clear everything, fill with RPG items
            result = lootSystem.getReplacer().replaceTotal(
                containerState.getItemContainer(), lootContext, tier, playerId);
        } else {
            // Selective replacement — remove weapons/armor, preserve materials
            result = lootSystem.getReplacer().replace(
                containerState.getItemContainer(), lootContext, tier, playerId);
        }

        // Sync custom item definitions (rpg_gear_xxx, rpg_map_xxx) to the opening player
        // BEFORE Hytale sends container contents to the client. Without this, the client
        // has no definition for custom item IDs and renders them as "?".
        syncContainerItemsToPlayer(containerState.getItemContainer(), playerRefComp);

        // Remove any items the client can't render (missing from asset map).
        // This catches broken items from any source: stale DynamicLootRegistry skins,
        // consumables that slipped past validation, or any other invalid item ID.
        removeInvalidItems(containerState.getItemContainer());

        // Prevent L4E from clearing our items on window close.
        // L4E's UseBlockEventPre adds OpenedContainerComponent only when hasTemplate()
        // is true. We must not queue a deferred removal for containers L4E never managed
        // (crashes during CommandBuffer.consume — try-catch can't catch deferred ops).
        // Also skip if we already removed it in stale lock recovery (prevents double-remove).
        if (l4eHadTemplate && !removedL4eComponent) {
            l4eBridge.removeOpenedContainerViaCommandBuffer(commandBuffer, playerRef);
        }

        LOGGER.atInfo().log(
            "Container loot at (%d, %d, %d) in %s: %s (srcLv%d, playerLv%d, block: %s, realm: %s)",
            target.x, target.y, target.z, world.getName(),
            result.summary(), lootContext.sourceLevel(), lootContext.playerLevel(),
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
     * Checks if a container has no items in any slot.
     *
     * @param container The item container to check
     * @return true if every slot is null or empty
     */
    private boolean isContainerEmpty(@Nonnull ItemContainer container) {
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && !ItemStack.isEmpty(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Syncs all custom item definitions in a container to the opening player.
     *
     * <p>Iterates every slot and calls {@link ItemWorldSyncService#syncItemToPlayer}
     * for each item with custom data (gear or realm map). Sends UpdateTranslations +
     * UpdateItems packets so the client can render rpg_gear_xxx and rpg_map_xxx items.
     *
     * <p>Must be called AFTER all container modifications and BEFORE the handler
     * returns, so the client has definitions before the container UI renders.
     */
    private void syncContainerItemsToPlayer(
            @Nonnull ItemContainer container,
            @Nonnull PlayerRef playerRef) {
        if (itemWorldSyncService == null) {
            return;
        }

        short capacity = container.getCapacity();
        int synced = 0;
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || ItemStack.isEmpty(item)) {
                continue;
            }
            if (itemWorldSyncService.syncItemToPlayer(playerRef, item)) {
                synced++;
            }
        }

        if (synced > 0 && debugLogging) {
            LOGGER.atFine().log("Synced %d custom item definition(s) to player for container open", synced);
        }
    }

    /**
     * Removes items from a container whose item ID doesn't resolve to a valid
     * Item in the asset map. These would render as "?" on the client and can
     * crash the client if interacted with (e.g., via the Stone picker item-grid).
     *
     * @param container The container to validate
     */
    private void removeInvalidItems(@Nonnull ItemContainer container) {
        short capacity = container.getCapacity();
        int removed = 0;
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || ItemStack.isEmpty(item)) {
                continue;
            }
            String itemId = item.getItemId();
            if (itemId == null || itemId.isEmpty()) {
                container.removeItemStackFromSlot(slot, item.getQuantity());
                removed++;
                continue;
            }
            // Custom RPG items (rpg_gear_*, rpg_map_*) are registered dynamically
            // and won't be in the vanilla asset map — they're validated by sync above.
            if (itemId.startsWith("rpg_")) {
                continue;
            }
            Item assetItem = Item.getAssetMap().getAsset(itemId);
            if (assetItem == null || assetItem == Item.UNKNOWN) {
                LOGGER.atWarning().log("Removed invalid item '%s' from container slot %d (not in asset map)", itemId, slot);
                container.removeItemStackFromSlot(slot, item.getQuantity());
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.atWarning().log("Removed %d invalid item(s) from container before player open", removed);
        }
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
     * Builds the loot context for a container based on its world type.
     *
     * <p>For realm containers, extracts realm level, DROP_LEVEL_BONUS, IIQ/IIR,
     * and GEAR_QUALITY_BONUS — mirroring the mob loot pipeline in
     * {@link io.github.larsonix.trailoforbis.gear.loot.LootListener}.
     *
     * <p>For overworld containers, calculates a distance-based source level
     * using the chest's position and
     * {@link DistanceBonusCalculator#estimateLevelFromDistance(double)}.
     *
     * @param world        The world containing the container
     * @param target       The container block position
     * @param isRealmWorld Whether the world is a realm world
     * @param playerLevel  The opening player's level
     * @return Context with zone-appropriate source level and realm bonuses
     */
    @Nonnull
    private ContainerLootContext buildLootContext(
            @Nonnull World world,
            @Nonnull Vector3i target,
            boolean isRealmWorld,
            int playerLevel) {

        if (isRealmWorld && realmsManager != null) {
            Optional<RealmInstance> realmOpt = realmsManager.getRealmByWorld(world);
            if (realmOpt.isPresent()) {
                RealmInstance realm = realmOpt.get();

                // Source level = realm level + DROP_LEVEL_BONUS (mirrors LootListener:140)
                int realmLevel = realm.getLevel();
                int dropLevelBonus = realm.getMapData().getModifierValue(RealmModifierType.DROP_LEVEL_BONUS);
                int sourceLevel = realmLevel + dropLevelBonus;

                // IIQ/IIR from realm modifiers (mirrors LootListener:357-362)
                double iiqBonus = realm.getMapData().getTotalItemQuantity();
                double iirBonus = realm.getMapData().getTotalItemRarity();
                RealmsConfig realmsConfig = realmsManager.getConfig();
                iiqBonus += realmsConfig.getBaseRealmIiqBonus();
                RealmLootContext realmCtx = RealmLootContext.of(iiqBonus, iirBonus);

                // Quality bonus (mirrors LootListener:396-410)
                int qualityBonus = realm.getMapData().getModifierValue(RealmModifierType.GEAR_QUALITY_BONUS);

                if (debugLogging) {
                    LOGGER.atFine().log("Realm container context: realmLv%d + DROP_LEVEL_BONUS=%d → srcLv%d, IIQ=%.1f%%, IIR=%.1f%%, quality+%d",
                        realmLevel, dropLevelBonus, sourceLevel, iiqBonus, iirBonus, qualityBonus);
                }

                return ContainerLootContext.realm(sourceLevel, playerLevel, realmCtx, qualityBonus);
            }
        }

        // Overworld: distance-based source level
        int sourceLevel = estimateSourceLevelFromDistance(target.x, target.z, world);

        if (debugLogging) {
            LOGGER.atFine().log("Overworld container context: distance-based srcLv%d, playerLv%d",
                sourceLevel, playerLevel);
        }

        return ContainerLootContext.overworld(sourceLevel, playerLevel);
    }

    /**
     * Estimates a source level from the chest's distance to world spawn.
     *
     * <p>Uses the same {@link DistanceBonusCalculator} that mob scaling uses,
     * ensuring containers and mobs in the same area drop similar-level gear.
     *
     * @param x     Block X coordinate
     * @param z     Block Z coordinate
     * @param world The world containing the container
     * @return Estimated level (minimum 1)
     */
    private int estimateSourceLevelFromDistance(int x, int z, @Nonnull World world) {
        DistanceBonusCalculator calc = getDistanceBonusCalculator();
        if (calc == null) {
            return 1; // Fallback: minimum level if mob scaling unavailable
        }
        double distance = DistanceBonusCalculator.calculateDistanceFromSpawn(x, z, world);
        return calc.estimateLevelFromDistance(distance);
    }

    /**
     * Lazily resolves the distance bonus calculator from MobScalingManager.
     *
     * <p>Returns null if MobScalingManager is not yet initialized (e.g., mob scaling disabled).
     */
    @Nullable
    private DistanceBonusCalculator getDistanceBonusCalculator() {
        if (distanceBonusCalculator == null) {
            TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
            if (rpg != null) {
                MobScalingManager scalingManager = rpg.getMobScalingManager();
                if (scalingManager != null) {
                    distanceBonusCalculator = scalingManager.getDistanceCalculator();
                }
            }
        }
        return distanceBonusCalculator;
    }

}
