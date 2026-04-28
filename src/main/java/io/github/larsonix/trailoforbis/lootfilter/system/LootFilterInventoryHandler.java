package io.github.larsonix.trailoforbis.lootfilter.system;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.lootfilter.LootFilterManager;
import io.github.larsonix.trailoforbis.lootfilter.feedback.BlockFeedbackService;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterAction;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces loot filter rules by intercepting inventory additions via
 * {@link InventoryChangeEvent} and ejecting blocked RPG gear items.
 *
 * <p>This replaces the previous approach of using {@code InteractivelyPickupItemEvent},
 * which never fires for ground item pickups. Instead, we react to successful inventory
 * additions and immediately reverse them when the filter blocks the item.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Player picks up item → Hytale adds to inventory → {@code InventoryChangeEvent} fires</li>
 *   <li>Transaction is checked: only ADD actions on non-equipment containers</li>
 *   <li>If RPG gear: check rejection stamp (fast path) or evaluate full filter</li>
 *   <li>If BLOCKED: remove from inventory, stamp metadata, drop at player's feet with delay</li>
 * </ol>
 *
 * <h3>Anti-Loop Mechanisms</h3>
 * <ul>
 *   <li><b>Pickup delay</b>: Re-dropped items have a 5-second pickup delay</li>
 *   <li><b>Rejection stamp</b>: Items carry metadata marking which player rejected them
 *       and when, preventing repeated full filter evaluations (60s TTL)</li>
 *   <li><b>Reentrancy guard</b>: Prevents cascade from removal triggering another event</li>
 * </ul>
 *
 * <p>Register as the FIRST handler on {@code InventoryChangeEventSystem} so blocked items
 * are ejected before other handlers process them.
 *
 * @see LootFilterManager
 * @see BlockFeedbackService
 */
public final class LootFilterInventoryHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Metadata key storing "{playerUUID}:{epochMillis}" for rejection tracking. */
    private static final String KEY_REJECTION_STAMP = "RPG:FilterRejected";

    /** How long a rejection stamp remains valid before the filter re-evaluates. */
    private static final long STAMP_TTL_MS = 60_000L;

    /** Pickup delay on re-dropped items (seconds). Prevents tight re-pickup loops. */
    private static final float REJECTION_PICKUP_DELAY = 5.0f;

    /** Slight upward velocity so the re-dropped item visibly "pops" out. */
    private static final float DROP_VELOCITY_Y = 1.5f;

    private final LootFilterManager filterManager;
    private final BlockFeedbackService feedbackService;

    /** Reentrancy guard — prevents cascade when our removal fires another event. */
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public LootFilterInventoryHandler(
            @Nonnull LootFilterManager filterManager,
            @Nonnull BlockFeedbackService feedbackService) {
        this.filterManager = filterManager;
        this.feedbackService = feedbackService;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT HANDLER (registered on InventoryChangeEventSystem)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called on every player inventory change. Checks if a filtered RPG gear item
     * entered the inventory and ejects it if blocked.
     *
     * <p>Handles two transaction types:
     * <ul>
     *   <li>{@code SlotTransaction} — direct single-slot operations (e.g., setItemStackForSlot)</li>
     *   <li>{@code ItemStackTransaction} — pickup/addItemStack operations that wrap
     *       a list of {@code ItemStackSlotTransaction} (each extending SlotTransaction)</li>
     * </ul>
     */
    public void onInventoryChange(@Nonnull Player player, @Nonnull InventoryChangeEvent event) {
        // Fast exit: system disabled
        if (!filterManager.isSystemEnabled()) return;

        // Only check non-equipment containers (backpack, storage, hotbar pickups)
        Inventory inventory = player.getInventory();
        if (inventory == null) return;
        ItemContainer container = event.getItemContainer();
        if (isEquipmentContainer(inventory, container)) return;

        // Get player identity (before transaction check — avoids repeated lookups)
        UUID playerId = getPlayerId(player);
        if (playerId == null) return;

        // Fast exit: player has no active filter
        if (!filterManager.isFilteringEnabled(playerId)) return;

        // Reentrancy guard: our removeItemStackFromSlot() fires another event
        if (!processing.add(playerId)) return;
        try {
            Transaction transaction = event.getTransaction();

            // Pickup path: addItemStack() produces ItemStackTransaction wrapping slot transactions
            if (transaction instanceof ItemStackTransaction ist) {
                if (!ist.succeeded()) return;
                if (ist.getAction() == null || !ist.getAction().isAdd()) return;
                for (ItemStackSlotTransaction slot : ist.getSlotTransactions()) {
                    if (!slot.succeeded()) continue;
                    processSlot(player, container, slot, playerId);
                }
                return;
            }

            // Direct slot path: setItemStackForSlot() produces SlotTransaction
            if (transaction instanceof SlotTransaction slot) {
                if (!slot.succeeded()) return;
                if (!slot.getAction().isAdd()) return;
                processSlot(player, container, slot, playerId);
            }
        } finally {
            processing.remove(playerId);
        }
    }

    /**
     * Processes a single slot transaction, checking if the added item should be filtered.
     */
    private void processSlot(Player player, ItemContainer container,
                             SlotTransaction slot, UUID playerId) {
        ItemStack itemStack = slot.getSlotAfter();
        if (itemStack == null || itemStack.isEmpty()) return;
        if (!GearUtils.isRpgGear(itemStack)) return;

        evaluateAndEject(player, container, slot, itemStack, playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVALUATION + EJECTION
    // ═══════════════════════════════════════════════════════════════════

    private void evaluateAndEject(Player player, ItemContainer container,
                                  SlotTransaction slot, ItemStack itemStack, UUID playerId) {
        // Fast path: rejection stamp still valid for this player
        if (hasValidRejectionStamp(itemStack, playerId)) {
            ejectItem(container, slot.getSlot(), itemStack, player, playerId);
            LOGGER.atFine().log("Re-rejected stamped item for %s", playerId.toString().substring(0, 8));
            return;
        }

        // Full filter evaluation
        Optional<GearData> gearOpt = GearUtils.readGearData(itemStack);
        if (gearOpt.isEmpty()) return;
        GearData gearData = gearOpt.get();

        EquipmentType equipType = resolveEquipmentType(gearData);
        FilterAction action = filterManager.evaluate(playerId, gearData, equipType);

        if (action == FilterAction.BLOCK) {
            // Stamp the item before ejecting (stamp goes on the dropped copy)
            ItemStack stamped = stampRejection(itemStack, playerId);
            ejectItem(container, slot.getSlot(), stamped, player, playerId);
            feedbackService.onItemBlocked(playerId, gearData, getPlayerRef(player));

            LOGGER.atFine().log("Blocked pickup for %s: %s Lv%d (Q%d)",
                    playerId.toString().substring(0, 8),
                    gearData.rarity().name(), gearData.level(), gearData.quality());
        }
    }

    /**
     * Removes the item from the inventory slot and drops it at the player's feet.
     */
    private void ejectItem(ItemContainer container, short slot, ItemStack itemStack,
                           Player player, UUID playerId) {
        // Step 1: Remove from inventory (safe — fires another event caught by reentrancy guard)
        container.removeItemStackFromSlot(slot);

        // Step 2: Drop at player's feet with extended pickup delay
        dropItemNearPlayer(player, itemStack);
    }

    private void dropItemNearPlayer(Player player, ItemStack itemStack) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        // Capture position now — the transform may not be valid after deferral
        Vector3d position = transform.getPosition().clone();

        // Defer entity spawn to after the ECS tick completes.
        // Store is locked during system processing — addEntity() would throw
        // "Store is currently processing! Ensure you aren't calling a store method from a system."
        com.hypixel.hytale.server.core.universe.world.World world =
                store.getExternalData().getWorld();
        world.execute(() -> {
            Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                    store, itemStack, position, Vector3f.ZERO, 0.0f, DROP_VELOCITY_Y, 0.0f);
            if (holder == null) return;

            // Set extended pickup delay to prevent immediate re-pickup loop
            ItemComponent itemComp = holder.getComponent(ItemComponent.getComponentType());
            if (itemComp != null) {
                itemComp.setPickupDelay(REJECTION_PICKUP_DELAY);
            }

            store.addEntity(holder, AddReason.SPAWN);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // REJECTION STAMP — prevents repeated filter evaluation loops
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Stamps an item with the rejecting player's UUID and current timestamp.
     * Returns a new ItemStack with the stamp metadata (ItemStack is immutable).
     */
    @Nonnull
    private static ItemStack stampRejection(@Nonnull ItemStack itemStack, @Nonnull UUID playerId) {
        String stamp = playerId.toString() + ":" + System.currentTimeMillis();
        return itemStack.withMetadata(KEY_REJECTION_STAMP, Codec.STRING, stamp);
    }

    /**
     * Checks if an item has a valid (non-expired) rejection stamp for this player.
     */
    private static boolean hasValidRejectionStamp(@Nonnull ItemStack itemStack, @Nonnull UUID playerId) {
        String stamp = itemStack.getFromMetadataOrNull(KEY_REJECTION_STAMP, Codec.STRING);
        if (stamp == null) return false;

        int colonIdx = stamp.lastIndexOf(':');
        if (colonIdx <= 0) return false;

        String stampPlayerId = stamp.substring(0, colonIdx);
        if (!stampPlayerId.equals(playerId.toString())) return false;

        try {
            long stampTime = Long.parseLong(stamp.substring(colonIdx + 1));
            return (System.currentTimeMillis() - stampTime) < STAMP_TTL_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static boolean isEquipmentContainer(@Nonnull Inventory inventory,
                                                @Nonnull ItemContainer container) {
        return container == inventory.getArmor()
                || container == inventory.getUtility();
    }

    @javax.annotation.Nullable
    private static UUID getPlayerId(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return null;
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        return playerRef != null ? playerRef.getUuid() : null;
    }

    @javax.annotation.Nullable
    private static PlayerRef getPlayerRef(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return null;
        Store<EntityStore> store = ref.getStore();
        return store.getComponent(ref, PlayerRef.getComponentType());
    }

    /**
     * Resolves the {@link EquipmentType} from the gear's base item ID.
     * Migrated from the deleted {@code LootFilterPickupSystem}.
     */
    @Nonnull
    public static EquipmentType resolveEquipmentType(@Nonnull GearData gearData) {
        String baseItemId = gearData.baseItemId();
        if (baseItemId == null || baseItemId.isEmpty()) {
            return EquipmentType.UNKNOWN_WEAPON;
        }

        Optional<WeaponType> weaponType = WeaponType.fromItemId(baseItemId);
        if (weaponType.isPresent()) {
            return EquipmentType.resolve(weaponType.get(), null, null);
        }

        Optional<ArmorMaterial> material = ArmorMaterial.fromItemId(baseItemId);
        if (material.isPresent()) {
            EquipmentType.ArmorSlot armorSlot = resolveArmorSlot(baseItemId);
            return EquipmentType.resolve(null, material.get(), armorSlot);
        }

        if (baseItemId.toLowerCase().contains("shield")) {
            return EquipmentType.SHIELD;
        }

        return EquipmentType.UNKNOWN_WEAPON;
    }

    @Nonnull
    private static EquipmentType.ArmorSlot resolveArmorSlot(@Nonnull String itemId) {
        String lower = itemId.toLowerCase();
        if (lower.contains("head") || lower.contains("helmet")) return EquipmentType.ArmorSlot.HEAD;
        if (lower.contains("chest")) return EquipmentType.ArmorSlot.CHEST;
        if (lower.contains("legs") || lower.contains("legging")) return EquipmentType.ArmorSlot.LEGS;
        if (lower.contains("hands") || lower.contains("gauntlet") || lower.contains("glove"))
            return EquipmentType.ArmorSlot.HANDS;
        return EquipmentType.ArmorSlot.CHEST;
    }
}
