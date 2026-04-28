package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;

import javax.annotation.Nonnull;

/**
 * Represents an item discovered from Hytale's asset registry at runtime.
 *
 * <p>This record holds metadata about items that can drop as loot,
 * discovered dynamically by scanning {@code Item.getAssetMap()} for
 * items with {@code item.getWeapon() != null} or {@code item.getArmor() != null}.
 *
 * <p>Unlike static configuration, discovered items work automatically with
 * any mod that properly registers weapons or armor using Hytale's Item API.
 *
 * @param itemId    The item's ID (e.g., "Weapon_Sword_Iron", "Keb_Katana_Adamantite")
 * @param slot      The equipment slot this item belongs to
 * @param modSource The mod/pack that registered this item (e.g., "Hytale:Hytale" for vanilla)
 * @param qualityId The Hytale ItemQuality ID (e.g., "Common", "Rare", "Epic") for skin filtering
 */
public record DiscoveredItem(
        @Nonnull String itemId,
        @Nonnull EquipmentSlot slot,
        @Nonnull String modSource,
        @Nonnull String qualityId
) {
    /**
     * Default mod source for vanilla Hytale items.
     */
    public static final String VANILLA_SOURCE = "Hytale:Hytale";

    /**
     * Creates a DiscoveredItem with all required fields.
     *
     * @param itemId    The item ID (must not be null)
     * @param slot      The equipment slot (must not be null)
     * @param modSource The mod source (must not be null)
     */
    /**
     * Default quality ID for items without an explicit quality assignment.
     */
    public static final String DEFAULT_QUALITY = "Common";

    public DiscoveredItem {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId cannot be null");
        }
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        if (modSource == null) {
            throw new IllegalArgumentException("modSource cannot be null");
        }
        if (qualityId == null) {
            throw new IllegalArgumentException("qualityId cannot be null");
        }
    }

    /**
     * Whether this item is from vanilla Hytale (not a mod).
     *
     * @return true if this is a vanilla item
     */
    public boolean isVanilla() {
        return VANILLA_SOURCE.equals(modSource);
    }

    /**
     * Gets a short display name for the mod source.
     *
     * <p>The full mod source format is "PackId:PackName". This returns
     * just the pack name portion for display purposes.
     *
     * @return The pack name (or full source if format doesn't match)
     */
    @Nonnull
    public String getModDisplayName() {
        int colonIndex = modSource.indexOf(':');
        if (colonIndex > 0 && colonIndex < modSource.length() - 1) {
            return modSource.substring(colonIndex + 1);
        }
        return modSource;
    }

    /**
     * Gets the pack ID portion of the mod source.
     *
     * <p>The full mod source format is "PackId:PackName". This returns
     * just the pack ID portion.
     *
     * @return The pack ID (or full source if format doesn't match)
     */
    @Nonnull
    public String getPackId() {
        int colonIndex = modSource.indexOf(':');
        if (colonIndex > 0) {
            return modSource.substring(0, colonIndex);
        }
        return modSource;
    }

    /**
     * Whether this item is a weapon (slot is WEAPON or OFF_HAND).
     *
     * @return true if this is a weapon slot
     */
    public boolean isWeapon() {
        return slot == EquipmentSlot.WEAPON || slot == EquipmentSlot.OFF_HAND;
    }

    /**
     * Whether this item is armor (slot is HEAD, CHEST, LEGS, or HANDS).
     *
     * @return true if this is an armor slot
     */
    public boolean isArmor() {
        return slot == EquipmentSlot.HEAD ||
               slot == EquipmentSlot.CHEST ||
               slot == EquipmentSlot.LEGS ||
               slot == EquipmentSlot.HANDS;
    }
}
