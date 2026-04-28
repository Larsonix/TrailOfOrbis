package io.github.larsonix.trailoforbis.gear.conversion;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Classifies ItemStacks as weapons, armor, or other items.
 *
 * <p>Uses Hytale's Item API to detect item types:
 * <ul>
 *   <li>Weapons have {@code Item.getWeapon() != null}</li>
 *   <li>Armor has {@code Item.getArmor() != null}</li>
 * </ul>
 *
 * <p>Also handles whitelist/blacklist filtering from config.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class ItemClassifier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final VanillaConversionConfig config;

    /**
     * Creates a new ItemClassifier with the given config.
     */
    public ItemClassifier(@Nonnull VanillaConversionConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Classifies an ItemStack as weapon, armor, or other.
     */
    @Nonnull
    public Classification classify(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return Classification.OTHER;
        }

        Item item = itemStack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return Classification.OTHER;
        }

        // Check for weapon
        ItemWeapon weapon = item.getWeapon();
        if (weapon != null) {
            return new Classification(ItemType.WEAPON, "weapon");
        }

        // Check for armor
        ItemArmor armor = item.getArmor();
        if (armor != null) {
            String slot = armorSlotToGearSlot(armor.getArmorSlot(), itemStack.getItemId());
            return new Classification(ItemType.ARMOR, slot);
        }

        return Classification.OTHER;
    }

    /**
     * Converts Hytale's ItemArmorSlot to our gear slot string.
     */
    @Nonnull
    private String armorSlotToGearSlot(@Nonnull ItemArmorSlot armorSlot, @Nonnull String itemId) {
        return switch (armorSlot) {
            case Head -> "head";
            case Chest -> "chest";
            case Legs -> "legs";
            case Hands -> "hands";
        };
    }

    /**
     * Checks if an item is allowed by the whitelist/blacklist config.
     */
    public boolean isAllowedByConfig(@Nonnull String itemId) {
        Objects.requireNonNull(itemId, "itemId cannot be null");

        // Blacklist takes priority
        List<String> blacklist = config.getBlacklist();
        if (blacklist != null && !blacklist.isEmpty()) {
            if (blacklist.contains(itemId)) {
                LOGGER.atFine().log("Item %s is blacklisted from conversion", itemId);
                return false;
            }
        }

        // Whitelist check (empty = allow all)
        List<String> whitelist = config.getWhitelist();
        if (whitelist != null && !whitelist.isEmpty()) {
            if (!whitelist.contains(itemId)) {
                LOGGER.atFine().log("Item %s is not in whitelist, skipping conversion", itemId);
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if an ItemStack is a weapon or armor that can be converted.
     */
    public boolean isConvertible(@Nullable ItemStack itemStack) {
        Classification classification = classify(itemStack);
        return classification.type() != ItemType.OTHER;
    }

    // ==================== Item Types ====================

    public enum ItemType {
        /** A weapon item */
        WEAPON,
        /** An armor item */
        ARMOR,
        /** Neither weapon nor armor */
        OTHER
    }

    // ==================== Classification Result ====================

    /**
     * Result of classifying an ItemStack.
     *
     * @param type The item type (WEAPON, ARMOR, or OTHER)
     * @param slot The equipment slot ("weapon", "head", "chest", "legs", "hands") or null for OTHER
     */
    public record Classification(
        @Nonnull ItemType type,
        @Nullable String slot
    ) {
        /** Singleton for non-convertible items */
        public static final Classification OTHER = new Classification(ItemType.OTHER, null);

        /**
         * @return true if the item is a weapon or armor
         */
        public boolean isConvertible() {
            return type != ItemType.OTHER;
        }
    }
}
