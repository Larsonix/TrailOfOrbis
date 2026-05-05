package io.github.larsonix.trailoforbis.combat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.LevelRequirementStatus;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.RequirementStatus;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Sends Hytale notifications when a player attacks while wearing gear
 * that doesn't meet attribute/level requirements.
 *
 * <p>Uses a per-player cooldown to prevent notification spam during combat.
 * Each unmet item triggers a notification at most once per cooldown window.
 *
 * <p>Called from {@link RPGDamageSystem} after attacker resolution.
 */
public final class CombatRequirementNotifier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Cooldown per player in milliseconds (10 seconds). */
    private static final long COOLDOWN_MS = 10_000;

    // Notification colors
    private static final String COLOR_REQUIRED = "#55FF55";   // Green — the requirement target
    private static final String COLOR_DEFICIT = "#FF5555";    // Red — what the player actually has
    private static final String COLOR_SEPARATOR = "#888888";  // Gray — parentheses/spacing

    private final EquipmentValidator validator;

    /**
     * Per-player last notification timestamp.
     * Key = player UUID, Value = last notification time (System.currentTimeMillis).
     */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public CombatRequirementNotifier(@Nonnull EquipmentValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
    }

    /**
     * Checks if the attacking player has any equipped gear that doesn't meet requirements.
     * If so, sends a notification with the item icon and missing requirements.
     *
     * <p>This should be called from the damage pipeline after the attacker is confirmed
     * to be a player.
     *
     * @param attackerUuid The attacking player's UUID
     * @param inventory The player's inventory (from ECS Player component)
     */
    public void checkAndNotify(@Nonnull UUID attackerUuid, @Nonnull Inventory inventory) {
        // Check cooldown — skip entirely if recently notified
        long now = System.currentTimeMillis();
        Long lastNotification = cooldowns.get(attackerUuid);
        if (lastNotification != null && (now - lastNotification) < COOLDOWN_MS) {
            return;
        }

        // Collect all equipped items that fail requirements
        ItemStack failedItem = findFirstUnmetItem(attackerUuid, inventory);
        if (failedItem == null) {
            return;
        }

        // Set cooldown BEFORE sending (prevents duplicate sends from concurrent attacks)
        cooldowns.put(attackerUuid, now);

        // Get the PlayerRef for sending the notification
        PlayerRef playerRef = PlayerWorldCache.findPlayerRef(attackerUuid);
        if (playerRef == null) {
            return;
        }

        // Get validation details for the failed item
        ValidationResult result = validator.checkRequirements(attackerUuid, failedItem);
        if (result.canEquip()) {
            return; // Race condition — item became valid between check and here
        }

        // Build notification messages
        Message primary = Message.raw("Requirements not met").color("#FF5555");
        Message secondary = buildSecondaryMessage(result);

        // Send with item icon
        ItemWithAllMetadata itemIcon = failedItem.toPacket();
        NotificationUtil.sendNotification(
            playerRef.getPacketHandler(),
            primary,
            secondary,
            itemIcon,
            NotificationStyle.Danger
        );

        LOGGER.at(Level.FINE).log("Sent requirement notification to %s for item %s",
            attackerUuid.toString().substring(0, 8), failedItem.getItemId());
    }

    /**
     * Finds the first equipped item that doesn't meet requirements.
     * Checks: active weapon, active utility, all armor slots.
     */
    @Nullable
    private ItemStack findFirstUnmetItem(@Nonnull UUID playerId, @Nonnull Inventory inventory) {
        // Check active weapon first (most impactful during combat)
        ItemStack weapon = inventory.getItemInHand();
        if (!ItemStack.isEmpty(weapon) && !validator.canEquip(playerId, weapon)) {
            return weapon;
        }

        // Check active utility item
        ItemStack utility = inventory.getUtilityItem();
        if (!ItemStack.isEmpty(utility) && !validator.canEquip(playerId, utility)) {
            return utility;
        }

        // Check armor slots
        ItemContainer armor = inventory.getArmor();
        if (armor != null) {
            for (short i = 0; i < armor.getCapacity(); i++) {
                ItemStack armorPiece = armor.getItemStack(i);
                if (!ItemStack.isEmpty(armorPiece) && !validator.canEquip(playerId, armorPiece)) {
                    return armorPiece;
                }
            }
        }

        return null;
    }

    /**
     * Builds a secondary message showing which requirements are unmet.
     *
     * <p>Format: "Lv 25 (12)  Fire 15 (8)" — names bold in element color, value in red.
     */
    @Nonnull
    private Message buildSecondaryMessage(@Nonnull ValidationResult result) {
        Message msg = Message.empty();
        boolean first = true;

        // Level requirement — white bold (no element color)
        LevelRequirementStatus levelStatus = result.levelRequirement();
        if (levelStatus != null && !levelStatus.met()) {
            msg = msg.insert(Message.raw("Lv").color("#FFFFFF").bold(true))
                     .insert(Message.raw(" " + levelStatus.requiredLevel()).color(COLOR_REQUIRED))
                     .insert(Message.raw(" (").color(COLOR_SEPARATOR))
                     .insert(Message.raw(String.valueOf(levelStatus.playerLevel())).color(COLOR_DEFICIT))
                     .insert(Message.raw(")").color(COLOR_SEPARATOR));
            first = false;
        }

        // Attribute requirements — name bold in element color, required value green, actual red
        for (Map.Entry<AttributeType, RequirementStatus> entry : result.requirements().entrySet()) {
            RequirementStatus status = entry.getValue();
            if (!status.met()) {
                if (!first) {
                    msg = msg.insert(Message.raw("  ").color(COLOR_SEPARATOR));
                }
                AttributeType attr = entry.getKey();
                msg = msg.insert(Message.raw(attr.getDisplayName()).color(attr.getHexColor()).bold(true))
                         .insert(Message.raw(" " + status.required()).color(COLOR_REQUIRED))
                         .insert(Message.raw(" (").color(COLOR_SEPARATOR))
                         .insert(Message.raw(String.valueOf(status.actual())).color(COLOR_DEFICIT))
                         .insert(Message.raw(")").color(COLOR_SEPARATOR));
                first = false;
            }
        }

        return msg;
    }

    /**
     * Cleans up cooldown entry for a disconnecting player.
     */
    public void cleanupPlayer(@Nonnull UUID playerId) {
        cooldowns.remove(playerId);
    }
}
