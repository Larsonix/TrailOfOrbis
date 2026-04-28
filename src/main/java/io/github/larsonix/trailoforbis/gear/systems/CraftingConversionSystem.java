package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaItemConverter;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ECS event system that converts crafted vanilla weapons/armor to RPG gear.
 *
 * <p>This system listens for {@link CraftRecipeEvent.Post} (the modern ECS event)
 * instead of the deprecated {@code PlayerCraftEvent}. When a player crafts a
 * vanilla weapon or armor, this system:
 * <ol>
 *   <li>Gets the crafted recipe's output item</li>
 *   <li>Checks if it's a weapon or armor</li>
 *   <li>Finds the item in the player's inventory</li>
 *   <li>Converts and replaces it with RPG gear</li>
 * </ol>
 *
 * <p>The item level is determined by the player's current RPG level.
 *
 * @see VanillaItemConverter
 * @see CraftRecipeEvent.Post
 */
public final class CraftingConversionSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final long DEFAULT_RETRY_DELAY_MS = 75L;
    private static final long DEFAULT_REQUEST_TTL_MS = 2000L;

    private final VanillaItemConverter converter;
    private final LevelingService levelingService;
    private final RetryScheduler retryScheduler;
    private final int maxRetries;
    private final long retryDelayMs;
    private final long requestTtlMs;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, Player> playerType;
    private final ComponentType<EntityStore, UUIDComponent> uuidType;
    private final Map<UUID, Deque<PendingCraftConversion>> pendingByPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> scheduledPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new CraftingConversionSystem.
     *
     * @param converter The vanilla item converter
     * @param levelingService The leveling service to get player levels
     */
    public CraftingConversionSystem(
            @Nonnull VanillaItemConverter converter,
            @Nonnull LevelingService levelingService) {
        this(
            converter,
            levelingService,
            CraftingConversionSystem::scheduleOnWorldThread,
            DEFAULT_MAX_RETRIES,
            DEFAULT_RETRY_DELAY_MS,
            DEFAULT_REQUEST_TTL_MS,
            PlayerRef.getComponentType(),
            Player.getComponentType(),
            UUIDComponent.getComponentType()
        );
    }

    CraftingConversionSystem(
            @Nonnull VanillaItemConverter converter,
            @Nonnull LevelingService levelingService,
            @Nonnull RetryScheduler retryScheduler,
            int maxRetries,
            long retryDelayMs,
            long requestTtlMs,
            @Nonnull ComponentType<EntityStore, PlayerRef> playerRefType,
            @Nonnull ComponentType<EntityStore, Player> playerType,
            @Nonnull ComponentType<EntityStore, UUIDComponent> uuidType) {
        super(CraftRecipeEvent.Post.class);
        this.converter = Objects.requireNonNull(converter, "converter cannot be null");
        this.levelingService = Objects.requireNonNull(levelingService, "levelingService cannot be null");
        this.retryScheduler = Objects.requireNonNull(retryScheduler, "retryScheduler cannot be null");
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelayMs = Math.max(0L, retryDelayMs);
        this.requestTtlMs = Math.max(250L, requestTtlMs);
        this.playerRefType = Objects.requireNonNull(playerRefType, "playerRefType cannot be null");
        this.playerType = Objects.requireNonNull(playerType, "playerType cannot be null");
        this.uuidType = Objects.requireNonNull(uuidType, "uuidType cannot be null");
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Only process events for entities with PlayerRef, Player, and UUID components
        return Query.and(playerRefType, Query.and(playerType, uuidType));
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull CraftRecipeEvent.Post event
    ) {
        try {
            handleCraftEvent(index, archetypeChunk, event);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error handling craft event");
        }
    }

    /**
     * Handles the craft event, converting applicable items.
     */
    private void handleCraftEvent(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull CraftRecipeEvent.Post event
    ) {
        if (!converter.isSourceEnabled(VanillaItemConverter.AcquisitionSource.CRAFTING)) {
            return;
        }

        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe == null) {
            return;
        }

        // Get the primary output item
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null) {
            // Try getting from outputs array
            MaterialQuantity[] outputs = recipe.getOutputs();
            if (outputs == null || outputs.length == 0) {
                return;
            }
            primaryOutput = outputs[0];
        }

        String outputItemId = primaryOutput.getItemId();
        if (outputItemId == null || outputItemId.isEmpty()) {
            return;
        }

        // Get player components from ECS (instead of event)
        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        Player player = archetypeChunk.getComponent(index, playerType);
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, uuidType);

        if (playerRef == null || player == null || uuidComponent == null) {
            return;
        }

        UUID playerUuid = uuidComponent.getUuid();

        // Get player's level for item generation
        int playerLevel = levelingService.getLevel(playerUuid);

        // Guide milestone: first Portal_Device (Ancient Gateway) crafted
        if ("Portal_Device".equals(outputItemId)) {
            TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
            if (rpg != null && rpg.getGuideManager() != null) {
                rpg.getGuideManager().tryShow(playerUuid, io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_GATEWAY);
            }
        }

        queueCraftConversionForItem(player, playerUuid, outputItemId, event.getQuantity(), playerLevel);
    }

    /**
     * Queues a pending crafted-item conversion request.
     */
    boolean queueCraftConversionForItem(
            @Nonnull Player player,
            @Nonnull UUID playerUuid,
            @Nonnull String outputItemId,
            int quantity,
            int playerLevel,
            boolean scheduleRetry) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(outputItemId, "outputItemId cannot be null");

        if (!isConvertibleOutputItem(outputItemId)) {
            LOGGER.atFine().log("Crafted item '%s' is not a weapon or armor, skipping", outputItemId);
            return false;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }

        int normalizedQuantity = Math.max(1, quantity);
        PendingCraftConversion request = new PendingCraftConversion(
            playerUuid,
            outputItemId,
            normalizedQuantity,
            playerLevel,
            snapshotMatchingVanillaQuantities(inventory, outputItemId),
            System.currentTimeMillis()
        );

        pendingByPlayer.computeIfAbsent(playerUuid, ignored -> new ArrayDeque<>()).addLast(request);

        if (scheduleRetry) {
            scheduleProcessing(playerUuid, player, 0L);
        }
        return true;
    }

    boolean queueCraftConversionForItem(
            @Nonnull Player player,
            @Nonnull UUID playerUuid,
            @Nonnull String outputItemId,
            int quantity,
            int playerLevel) {
        return queueCraftConversionForItem(player, playerUuid, outputItemId, quantity, playerLevel, true);
    }

    int processPendingRequestsNowForTesting(@Nonnull Player player, @Nonnull UUID playerUuid) {
        return processPendingRequests(playerUuid, player);
    }

    int pendingRequestCountForTesting(@Nonnull UUID playerUuid) {
        Deque<PendingCraftConversion> queue = pendingByPlayer.get(playerUuid);
        return queue == null ? 0 : queue.size();
    }

    void enqueuePendingRequestForTesting(
            @Nonnull Player player,
            @Nonnull UUID playerUuid,
            @Nonnull String outputItemId,
            int quantity,
            int playerLevel) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(outputItemId, "outputItemId cannot be null");

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        PendingCraftConversion request = new PendingCraftConversion(
            playerUuid,
            outputItemId,
            Math.max(1, quantity),
            playerLevel,
            snapshotMatchingVanillaQuantities(inventory, outputItemId),
            System.currentTimeMillis()
        );
        pendingByPlayer.computeIfAbsent(playerUuid, ignored -> new ArrayDeque<>()).addLast(request);
    }

    private void scheduleProcessing(@Nonnull UUID playerUuid, @Nonnull Player player, long delayMs) {
        if (!scheduledPlayers.add(playerUuid)) {
            return;
        }

        retryScheduler.schedule(player, delayMs, () -> {
            try {
                processPendingRequests(playerUuid, player);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Error processing deferred crafting conversions for %s", playerUuid);
            } finally {
                scheduledPlayers.remove(playerUuid);
                if (hasPendingRequests(playerUuid)) {
                    scheduleProcessing(playerUuid, player, retryDelayMs);
                }
            }
        });
    }

    private boolean hasPendingRequests(@Nonnull UUID playerUuid) {
        Deque<PendingCraftConversion> queue = pendingByPlayer.get(playerUuid);
        return queue != null && !queue.isEmpty();
    }

    private int processPendingRequests(@Nonnull UUID playerUuid, @Nonnull Player player) {
        Deque<PendingCraftConversion> queue = pendingByPlayer.get(playerUuid);
        if (queue == null || queue.isEmpty()) {
            return 0;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return 0;
        }

        int totalConverted = 0;

        while (true) {
            PendingCraftConversion request = queue.peekFirst();
            if (request == null) {
                break;
            }

            if (request.isExpired(requestTtlMs)) {
                LOGGER.atWarning().log(
                    "Discarding expired crafted conversion request for %s (player=%s, converted=%d/%d, attempts=%d)",
                    request.itemId, request.playerUuid, request.convertedQuantity, request.requestedQuantity, request.attempts);
                queue.pollFirst();
                continue;
            }

            int convertedNow = convertProducedDelta(inventory, request);
            totalConverted += convertedNow;
            request.attempts++;

            if (request.convertedQuantity >= request.requestedQuantity) {
                queue.pollFirst();
                LOGGER.atInfo().log(
                    "Converted %d crafted %s to RPG gear for player %s (level %d, attempts=%d)",
                    request.convertedQuantity,
                    request.itemId,
                    request.playerUuid,
                    request.playerLevel,
                    request.attempts
                );
                continue;
            }

            if (request.attempts >= maxRetries) {
                LOGGER.atWarning().log(
                    "Crafted conversion retries exhausted for %s (player=%s, converted=%d/%d, attempts=%d)",
                    request.itemId,
                    request.playerUuid,
                    request.convertedQuantity,
                    request.requestedQuantity,
                    request.attempts
                );
                queue.pollFirst();
                continue;
            }

            // Preserve craft order: if the oldest request still needs retry, process it first.
            break;
        }

        if (queue.isEmpty()) {
            pendingByPlayer.remove(playerUuid, queue);
        }

        return totalConverted;
    }

    private int convertProducedDelta(
            @Nonnull Inventory inventory,
            @Nonnull PendingCraftConversion request) {

        int convertedNow = 0;
        ItemContainer overflowContainer = inventory.getCombinedArmorHotbarUtilityStorage();
        List<ContainerView> views = getCraftSearchContainers(inventory);

        outer:
        for (ContainerView view : views) {
            short capacity = view.container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                if (request.convertedQuantity >= request.requestedQuantity) {
                    break outer;
                }

                ItemStack stack = view.container.getItemStack(slot);
                if (ItemStack.isEmpty(stack)) {
                    continue;
                }
                if (!request.itemId.equals(stack.getItemId())) {
                    continue;
                }
                if (GearUtils.isRpgGear(stack)) {
                    continue;
                }

                SlotKey slotKey = new SlotKey(view.section, slot);
                int baselineQty = request.baselineVanillaQty.getOrDefault(slotKey, 0);
                int consumedQty = request.consumedDeltaBySlot.getOrDefault(slotKey, 0);
                int currentQty = stack.getQuantity();
                int availableDeltaQty = currentQty - baselineQty - consumedQty;
                if (availableDeltaQty <= 0) {
                    continue;
                }

                int remainingToConvert = request.requestedQuantity - request.convertedQuantity;
                int convertQty = Math.min(availableDeltaQty, remainingToConvert);
                if (convertQty <= 0) {
                    continue;
                }

                boolean splitRequired = convertQty < currentQty;
                ItemStack conversionInput = stack;
                if (splitRequired) {
                    conversionInput = stack.withQuantity(convertQty);
                    if (ItemStack.isEmpty(conversionInput)) {
                        continue;
                    }
                }

                Optional<ItemStack> convertedOpt = converter.convertCrafted(conversionInput, request.playerLevel);
                if (convertedOpt.isEmpty()) {
                    continue;
                }

                ItemStack convertedStack = convertedOpt.get();
                if (convertedStack.getQuantity() != convertQty) {
                    ItemStack adjusted = convertedStack.withQuantity(convertQty);
                    if (ItemStack.isEmpty(adjusted)) {
                        continue;
                    }
                    convertedStack = adjusted;
                }

                if (!splitRequired) {
                    view.container.setItemStackForSlot(slot, convertedStack);
                } else {
                    int remainingQty = currentQty - convertQty;
                    ItemStack remainingStack = stack.withQuantity(remainingQty);
                    if (ItemStack.isEmpty(remainingStack)) {
                        continue;
                    }

                    view.container.setItemStackForSlot(slot, remainingStack);
                    ItemContainer insertContainer = overflowContainer != null ? overflowContainer : view.container;
                    ItemStackTransaction addTx = insertContainer.addItemStack(convertedStack, true, false, true);
                    if (!addTx.succeeded() || !ItemStack.isEmpty(addTx.getRemainder())) {
                        // Revert split change if insertion fails.
                        view.container.setItemStackForSlot(slot, stack);
                        LOGGER.atWarning().log(
                            "Unable to place split crafted conversion for %s (player=%s, slot=%s:%d)",
                            request.itemId,
                            request.playerUuid,
                            view.section,
                            slot
                        );
                        continue;
                    }
                }

                request.convertedQuantity += convertQty;
                request.consumedDeltaBySlot.merge(slotKey, convertQty, Integer::sum);
                convertedNow += convertQty;
            }
        }

        return convertedNow;
    }

    private boolean isConvertibleOutputItem(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        Item itemAsset = Item.getAssetMap().getAsset(itemId);
        if (itemAsset == null) {
            return false;
        }
        return itemAsset.getWeapon() != null || itemAsset.getArmor() != null;
    }

    private Map<SlotKey, Integer> snapshotMatchingVanillaQuantities(
            @Nonnull Inventory inventory,
            @Nonnull String itemId) {
        Map<SlotKey, Integer> snapshot = new HashMap<>();
        for (ContainerView view : getCraftSearchContainers(inventory)) {
            short capacity = view.container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = view.container.getItemStack(slot);
                if (ItemStack.isEmpty(stack)) {
                    continue;
                }
                if (!itemId.equals(stack.getItemId())) {
                    continue;
                }
                if (GearUtils.isRpgGear(stack)) {
                    continue;
                }
                snapshot.put(new SlotKey(view.section, slot), stack.getQuantity());
            }
        }
        return snapshot;
    }

    private List<ContainerView> getCraftSearchContainers(@Nonnull Inventory inventory) {
        List<ContainerView> views = new ArrayList<>(ContainerSection.VALUES.size());
        addContainer(views, ContainerSection.ARMOR, inventory.getArmor());
        addContainer(views, ContainerSection.HOTBAR, inventory.getHotbar());
        addContainer(views, ContainerSection.UTILITY, inventory.getUtility());
        addContainer(views, ContainerSection.STORAGE, inventory.getStorage());
        addContainer(views, ContainerSection.BACKPACK, inventory.getBackpack());
        return views;
    }

    private static void addContainer(
            @Nonnull List<ContainerView> views,
            @Nonnull ContainerSection section,
            @Nullable ItemContainer container) {
        if (container != null) {
            views.add(new ContainerView(section, container));
        }
    }

    private static void scheduleOnWorldThread(@Nonnull Player player, long delayMs, @Nonnull Runnable runnable) {
        if (player.getWorld() == null) {
            runnable.run();
            return;
        }
        CompletableFuture.delayedExecutor(Math.max(0L, delayMs), TimeUnit.MILLISECONDS)
            .execute(() -> {
                try {
                    if (player.getWorld() != null) {
                        player.getWorld().execute(runnable);
                    } else {
                        runnable.run();
                    }
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Async operation failed: %s", e.getMessage());
                }
            });
    }

    @FunctionalInterface
    interface RetryScheduler {
        void schedule(@Nonnull Player player, long delayMs, @Nonnull Runnable runnable);
    }

    private enum ContainerSection {
        ARMOR,
        HOTBAR,
        UTILITY,
        STORAGE,
        BACKPACK;

        private static final Set<ContainerSection> VALUES = EnumSet.allOf(ContainerSection.class);
    }

    private record ContainerView(@Nonnull ContainerSection section, @Nonnull ItemContainer container) { }

    private record SlotKey(@Nonnull ContainerSection section, short slot) { }

    private static final class PendingCraftConversion {
        private final UUID playerUuid;
        private final String itemId;
        private final int requestedQuantity;
        private final int playerLevel;
        private final Map<SlotKey, Integer> baselineVanillaQty;
        private final Map<SlotKey, Integer> consumedDeltaBySlot = new HashMap<>();
        private final long createdAtMs;
        private int convertedQuantity;
        private int attempts;

        private PendingCraftConversion(
                @Nonnull UUID playerUuid,
                @Nonnull String itemId,
                int requestedQuantity,
                int playerLevel,
                @Nonnull Map<SlotKey, Integer> baselineVanillaQty,
                long createdAtMs) {
            this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
            this.itemId = Objects.requireNonNull(itemId, "itemId cannot be null");
            this.requestedQuantity = Math.max(1, requestedQuantity);
            this.playerLevel = playerLevel;
            this.baselineVanillaQty = new HashMap<>(Objects.requireNonNull(baselineVanillaQty, "baselineVanillaQty cannot be null"));
            this.createdAtMs = createdAtMs;
        }

        private boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - createdAtMs > ttlMs;
        }
    }
}
