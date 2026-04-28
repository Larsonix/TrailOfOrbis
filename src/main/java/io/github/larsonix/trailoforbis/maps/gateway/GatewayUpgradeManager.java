package io.github.larsonix.trailoforbis.maps.gateway;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeConfig.GatewayTier;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeConfig.TierMaterial;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Business logic for the Ancient Gateway upgrade system.
 *
 * <p>Handles:
 * <ul>
 *   <li>Checking if a player has required materials for an upgrade</li>
 *   <li>Consuming materials and advancing the gateway tier</li>
 *   <li>Validating map levels against gateway tier caps</li>
 * </ul>
 *
 * @see GatewayUpgradeConfig
 * @see GatewayTierRepository
 */
public class GatewayUpgradeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String COLOR_PREFIX = MessageColors.DARK_PURPLE;
    private static final String COLOR_ERROR = MessageColors.ERROR;
    private static final String COLOR_SUCCESS = MessageColors.SUCCESS;
    private static final String COLOR_HIGHLIGHT = MessageColors.GOLD;

    private final GatewayUpgradeConfig config;
    private final GatewayTierRepository repository;

    public GatewayUpgradeManager(
            @Nonnull GatewayUpgradeConfig config,
            @Nonnull GatewayTierRepository repository) {

        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the current tier index of a gateway block.
     */
    public int getGatewayTier(@Nonnull UUID worldUuid, int x, int y, int z) {
        return repository.getTier(worldUuid, x, y, z);
    }

    /**
     * Gets the tier config for a gateway at a given position.
     */
    @Nullable
    public GatewayTier getGatewayTierConfig(@Nonnull UUID worldUuid, int x, int y, int z) {
        int tierIndex = repository.getTier(worldUuid, x, y, z);
        return config.getTier(tierIndex);
    }

    /**
     * Gets the maximum realm level a gateway can channel.
     */
    public int getMaxRealmLevel(@Nonnull UUID worldUuid, int x, int y, int z) {
        int tierIndex = repository.getTier(worldUuid, x, y, z);
        return config.getMaxRealmLevel(tierIndex);
    }

    /**
     * Checks if a map level can be used on this gateway.
     */
    public boolean canChannelMapLevel(@Nonnull UUID worldUuid, int x, int y, int z, int mapLevel) {
        return mapLevel <= getMaxRealmLevel(worldUuid, x, y, z);
    }

    /**
     * Checks if a gateway is at the maximum tier.
     */
    public boolean isMaxTier(@Nonnull UUID worldUuid, int x, int y, int z) {
        int tierIndex = repository.getTier(worldUuid, x, y, z);
        return tierIndex >= config.getMaxTierIndex();
    }

    /**
     * Checks if a block position is a registered gateway.
     */
    public boolean isGateway(@Nonnull UUID worldUuid, int x, int y, int z) {
        return repository.isGateway(worldUuid, x, y, z);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MATERIAL CHECKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Result of checking a player's inventory for upgrade materials.
     *
     * @param canUpgrade True if the player has all required materials
     * @param materialStatus List of (itemId, required, available) for each material
     */
    public record MaterialCheckResult(
            boolean canUpgrade,
            @Nonnull List<MaterialStatus> materialStatus) {
    }

    /**
     * Status of a single material requirement.
     *
     * @param itemId The Hytale item ID
     * @param required Number required
     * @param available Number the player has
     */
    public record MaterialStatus(
            @Nonnull String itemId,
            int required,
            int available) {

        public boolean isSatisfied() {
            return available >= required;
        }
    }

    /**
     * Checks if a player has the materials for the next tier upgrade.
     *
     * @param player The player component (for inventory access)
     * @param worldUuid The world containing the gateway
     * @param x, y, z The gateway block position
     * @return Check result with per-material breakdown
     */
    @Nonnull
    public MaterialCheckResult checkMaterials(
            @Nonnull Player player,
            @Nonnull UUID worldUuid, int x, int y, int z) {

        int currentTier = repository.getTier(worldUuid, x, y, z);
        GatewayTier nextTier = config.getNextTier(currentTier);

        if (nextTier == null) {
            // Already at max tier
            return new MaterialCheckResult(false, List.of());
        }

        Inventory inventory = player.getInventory();
        List<MaterialStatus> statuses = new ArrayList<>();
        boolean canUpgrade = true;

        for (TierMaterial material : nextTier.materials()) {
            int available = countItemInInventory(inventory, material.itemId());
            statuses.add(new MaterialStatus(material.itemId(), material.count(), available));
            if (available < material.count()) {
                canUpgrade = false;
            }
        }

        return new MaterialCheckResult(canUpgrade, statuses);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UPGRADE EXECUTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Attempts to upgrade a gateway to the next tier.
     *
     * <p>Consumes materials from the player's inventory and advances
     * the gateway tier in the database.
     *
     * @param player The player component
     * @param playerRef The player reference (for messages)
     * @param worldUuid The world UUID
     * @param x, y, z The gateway block position
     * @return true if the upgrade succeeded
     */
    public boolean tryUpgrade(
            @Nonnull Player player,
            @Nonnull PlayerRef playerRef,
            @Nonnull UUID worldUuid, int x, int y, int z) {

        int currentTier = repository.getTier(worldUuid, x, y, z);
        GatewayTier currentConfig = config.getTier(currentTier);
        GatewayTier nextTierConfig = config.getNextTier(currentTier);

        if (nextTierConfig == null) {
            sendError(playerRef, "This gateway is already at maximum tier !");
            return false;
        }

        // Check materials
        MaterialCheckResult check = checkMaterials(player, worldUuid, x, y, z);
        if (!check.canUpgrade()) {
            sendError(playerRef, "You don't have the required materials !");
            return false;
        }

        // Consume materials
        Inventory inventory = player.getInventory();
        for (TierMaterial material : nextTierConfig.materials()) {
            boolean consumed = consumeItemFromInventory(inventory, material.itemId(), material.count());
            if (!consumed) {
                // This shouldn't happen after the check, but be safe
                LOGGER.atWarning().log("Failed to consume %dx %s from player %s during gateway upgrade",
                    material.count(), material.itemId(), playerRef.getUuid());
                sendError(playerRef, "Failed to consume materials — upgrade aborted !");
                return false;
            }
        }

        // Advance tier
        int newTier = currentTier + 1;
        repository.setTier(worldUuid, x, y, z, newTier);

        // Success message
        String levelText = nextTierConfig.isUnlimited() ? "unlimited" : String.valueOf(nextTierConfig.maxRealmLevel());
        sendSuccess(playerRef, String.format("Gateway upgraded to %s ! Max realm level: %s",
            nextTierConfig.name(), levelText));

        LOGGER.atInfo().log("Player %s upgraded gateway at (%d,%d,%d) to tier %d (%s)",
            playerRef.getUuid(), x, y, z, newTier, nextTierConfig.name());

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INVENTORY HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Counts how many of a specific item a player has across all inventory containers.
     */
    private int countItemInInventory(@Nonnull Inventory inventory, @Nonnull String itemId) {
        int count = 0;
        count += countItemInContainer(inventory.getHotbar(), itemId);
        count += countItemInContainer(inventory.getStorage(), itemId);
        count += countItemInContainer(inventory.getBackpack(), itemId);
        return count;
    }

    private int countItemInContainer(@Nullable ItemContainer container, @Nonnull String itemId) {
        if (container == null) return 0;

        int count = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack item = container.getItemStack(i);
            if (item != null && !item.isEmpty() && itemId.equals(item.getItemId())) {
                count += item.getQuantity();
            }
        }
        return count;
    }

    /**
     * Consumes a specified quantity of an item from the player's inventory.
     * Scans hotbar, then storage, then backpack.
     *
     * @return true if the full quantity was consumed
     */
    private boolean consumeItemFromInventory(
            @Nonnull Inventory inventory,
            @Nonnull String itemId,
            int quantity) {

        int remaining = quantity;

        remaining = consumeFromContainer(inventory.getHotbar(), itemId, remaining);
        if (remaining <= 0) return true;

        remaining = consumeFromContainer(inventory.getStorage(), itemId, remaining);
        if (remaining <= 0) return true;

        remaining = consumeFromContainer(inventory.getBackpack(), itemId, remaining);
        return remaining <= 0;
    }

    /**
     * Consumes items from a single container.
     *
     * @return Remaining quantity still needed
     */
    private int consumeFromContainer(
            @Nullable ItemContainer container,
            @Nonnull String itemId,
            int remaining) {

        if (container == null || remaining <= 0) return remaining;

        for (short i = 0; i < container.getCapacity(); i++) {
            if (remaining <= 0) break;

            ItemStack item = container.getItemStack(i);
            if (item == null || item.isEmpty() || !itemId.equals(item.getItemId())) {
                continue;
            }

            int available = item.getQuantity();
            int toRemove = Math.min(available, remaining);
            container.removeItemStackFromSlot(i, toRemove);
            remaining -= toRemove;
        }

        return remaining;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIG ACCESS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public GatewayUpgradeConfig getConfig() {
        return config;
    }

    @Nonnull
    public GatewayTierRepository getRepository() {
        return repository;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MESSAGING
    // ═══════════════════════════════════════════════════════════════════

    private void sendError(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        playerRef.sendMessage(
            Message.raw("[Gateway] ").color(COLOR_PREFIX)
                .insert(Message.raw(message).color(COLOR_ERROR)));
    }

    private void sendSuccess(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        playerRef.sendMessage(
            Message.raw("[Gateway] ").color(COLOR_PREFIX)
                .insert(Message.raw(message).color(COLOR_SUCCESS)));
    }
}
