package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.tooltip.ItemNameFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.StoneItemData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Centralized service for generating item display names.
 *
 * <p>This service provides a single source of truth for item display names across
 * all plugin systems (UIs, notifications, tooltips, chat messages). Using this
 * service ensures consistent formatting everywhere.
 *
 * <h2>Name Resolution Hierarchy</h2>
 * <table>
 *   <tr><th>Item Type</th><th>Format</th><th>Example</th></tr>
 *   <tr><td>Gear</td><td>[Prefix] BaseName [Suffix]</td><td>"Sharp Iron Sword"</td></tr>
 *   <tr><td>Map</td><td>Biome Map</td><td>"Boreal Map"</td></tr>
 *   <tr><td>Stone</td><td>DisplayName</td><td>"Warden's Seal"</td></tr>
 *   <tr><td>Vanilla</td><td>formatItemId()</td><td>"Iron Sword"</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ItemDisplayNameService service = gearManager.getItemDisplayNameService();
 *
 * // For any ModifiableItem (gear or map)
 * String name = service.getDisplayName(modifiableItem, itemStack);
 *
 * // For styled chat messages
 * Message styledName = service.getStyledName(itemStack);
 * player.sendMessage(Message.raw("You picked up: ").insert(styledName));
 * }</pre>
 *
 * @see ItemNameFormatter
 * @see GearData
 * @see RealmMapData
 */
public final class ItemDisplayNameService {

    private final ModifierConfig modifierConfig;

    /**
     * Creates a new ItemDisplayNameService.
     *
     * @param modifierConfig The modifier configuration for prefix/suffix display names
     */
    public ItemDisplayNameService(@Nonnull ModifierConfig modifierConfig) {
        this.modifierConfig = Objects.requireNonNull(modifierConfig, "modifierConfig cannot be null");
    }

    // =========================================================================
    // PLAIN TEXT DISPLAY NAMES (for UIs, logging)
    // =========================================================================

    /**
     * Gets the display name for any ItemStack.
     *
     * <p>Automatically detects the item type and formats appropriately:
     * <ul>
     *   <li>RPG Gear: "[Prefix] Iron Sword [Suffix]"</li>
     *   <li>Realm Map: "Boreal Map"</li>
     *   <li>Stone: "Warden's Seal"</li>
     *   <li>Vanilla: "Iron Sword"</li>
     * </ul>
     *
     * @param itemStack The item to get the display name for
     * @return Human-readable display name
     */
    @Nonnull
    public String getDisplayName(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        // Try gear first
        var gearData = GearUtils.readGearData(itemStack);
        if (gearData.isPresent()) {
            return getGearDisplayName(gearData.get(), itemStack);
        }

        // Try realm map
        var mapData = io.github.larsonix.trailoforbis.maps.items.RealmMapUtils.readMapData(itemStack);
        if (mapData.isPresent()) {
            return getMapDisplayName(mapData.get());
        }

        // Try stone
        var stoneData = io.github.larsonix.trailoforbis.stones.StoneUtils.readStoneData(itemStack);
        if (stoneData.isPresent()) {
            return getStoneDisplayName(stoneData.get());
        }

        // Vanilla item - format the item ID
        return formatVanillaItemId(itemStack.getItemId());
    }

    /**
     * Gets the display name for a ModifiableItem (gear or map).
     *
     * <p>This is the primary method for stone UIs where you already have
     * the extracted ModifiableItem data.
     *
     * @param data The modifiable item data
     * @param itemStack The ItemStack (needed for gear base name lookup)
     * @return Human-readable display name
     */
    @Nonnull
    public String getDisplayName(@Nonnull ModifiableItem data, @Nonnull ItemStack itemStack) {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        if (data instanceof RealmMapData mapData) {
            return getMapDisplayName(mapData);
        }

        if (data instanceof GearData gearData) {
            return getGearDisplayName(gearData, itemStack);
        }

        // Fallback for unknown ModifiableItem types
        return "Item";
    }

    /**
     * Gets the display name for gear items.
     *
     * <p>Format: "[Prefix] BaseName [Suffix]"
     *
     * @param gearData The gear data
     * @param itemStack The ItemStack (for base name lookup)
     * @return Formatted gear name like "Sharp Iron Sword of the Bear"
     */
    @Nonnull
    public String getGearDisplayName(@Nonnull GearData gearData, @Nonnull ItemStack itemStack) {
        Objects.requireNonNull(gearData, "gearData cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        StringBuilder name = new StringBuilder();

        // Add prefix if present
        if (!gearData.prefixes().isEmpty()) {
            String prefixName = getModifierDisplayName(gearData.prefixes().get(0));
            if (prefixName != null && !prefixName.isEmpty()) {
                name.append(prefixName).append(" ");
            }
        }

        // Add base item name (prioritize gearData.baseItemId() for reliability)
        name.append(getBaseItemDisplayName(gearData, itemStack));

        // Add suffix if present
        if (!gearData.suffixes().isEmpty()) {
            String suffixName = getModifierDisplayName(gearData.suffixes().get(0));
            if (suffixName != null && !suffixName.isEmpty()) {
                name.append(" ").append(suffixName);
            }
        }

        return name.toString();
    }

    /**
     * Gets the display name for realm map items.
     *
     * <p>Format: "Biome Map"
     *
     * @param mapData The realm map data
     * @return Formatted map name like "Boreal Map"
     */
    @Nonnull
    public String getMapDisplayName(@Nonnull RealmMapData mapData) {
        Objects.requireNonNull(mapData, "mapData cannot be null");
        return String.format("%s Map", mapData.biome().getDisplayName());
    }

    /**
     * Gets the display name for stone items.
     *
     * @param stoneData The stone item data
     * @return The stone's display name like "Warden's Seal"
     */
    @Nonnull
    public String getStoneDisplayName(@Nonnull StoneItemData stoneData) {
        Objects.requireNonNull(stoneData, "stoneData cannot be null");
        return stoneData.getDisplayName();
    }

    /**
     * Formats a vanilla Hytale item ID into a readable display name.
     *
     * <p>Delegates to {@link ItemNameFormatter#formatItemId(String)}.
     *
     * <p>Examples:
     * <ul>
     *   <li>"Weapon_Sword_Iron" → "Iron Sword"</li>
     *   <li>"Armor_Iron_Chest" → "Iron Chestplate"</li>
     * </ul>
     *
     * @param itemId The Hytale item ID
     * @return Formatted display name
     */
    @Nonnull
    public String formatVanillaItemId(@Nonnull String itemId) {
        Objects.requireNonNull(itemId, "itemId cannot be null");
        return ItemNameFormatter.resolveDisplayName(itemId);
    }

    // =========================================================================
    // STYLED MESSAGES (for chat notifications)
    // =========================================================================

    /**
     * Gets a styled Message for any ItemStack.
     *
     * <p>The message includes:
     * <ul>
     *   <li>Rarity-based color</li>
     *   <li>Bold styling for Epic+ items</li>
     *   <li>Full formatted name with prefix/suffix</li>
     * </ul>
     *
     * @param itemStack The item to get the styled name for
     * @return Styled Message suitable for chat
     */
    @Nonnull
    public Message getStyledName(@Nonnull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        // Try gear first
        var gearData = GearUtils.readGearData(itemStack);
        if (gearData.isPresent()) {
            return getStyledGearName(gearData.get(), itemStack);
        }

        // Try realm map
        var mapData = io.github.larsonix.trailoforbis.maps.items.RealmMapUtils.readMapData(itemStack);
        if (mapData.isPresent()) {
            return getStyledMapName(mapData.get());
        }

        // Try stone
        var stoneData = io.github.larsonix.trailoforbis.stones.StoneUtils.readStoneData(itemStack);
        if (stoneData.isPresent()) {
            return getStyledStoneName(stoneData.get());
        }

        // Vanilla item - no styling
        return Message.raw(formatVanillaItemId(itemStack.getItemId()));
    }

    /**
     * Gets a styled Message for gear items.
     *
     * @param gearData The gear data
     * @param itemStack The ItemStack (for base name lookup)
     * @return Styled Message with rarity coloring
     */
    @Nonnull
    public Message getStyledGearName(@Nonnull GearData gearData, @Nonnull ItemStack itemStack) {
        Objects.requireNonNull(gearData, "gearData cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");

        String displayName = getGearDisplayName(gearData, itemStack);
        return applyRarityStyle(displayName, gearData.rarity());
    }

    /**
     * Gets a styled Message for realm map items.
     *
     * @param mapData The realm map data
     * @return Styled Message with rarity coloring
     */
    @Nonnull
    public Message getStyledMapName(@Nonnull RealmMapData mapData) {
        Objects.requireNonNull(mapData, "mapData cannot be null");

        String displayName = getMapDisplayName(mapData);
        return applyRarityStyle(displayName, mapData.rarity());
    }

    /**
     * Gets a styled Message for stone items.
     *
     * @param stoneData The stone item data
     * @return Styled Message with rarity coloring
     */
    @Nonnull
    public Message getStyledStoneName(@Nonnull StoneItemData stoneData) {
        Objects.requireNonNull(stoneData, "stoneData cannot be null");

        String displayName = stoneData.getDisplayName();
        return applyRarityStyle(displayName, stoneData.getRarity());
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Gets the base item display name from GearData and ItemStack.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>gearData.baseItemId() - most reliable, passed from caller</li>
     *   <li>Stored base item ID in metadata</li>
     *   <li>Item asset ID from the ItemStack</li>
     *   <li>Fallback to "Unknown Item"</li>
     * </ol>
     *
     * @param gearData The gear data (may contain baseItemId)
     * @param itemStack The ItemStack to get the base name from
     * @return Formatted base item name like "Iron Sword"
     */
    @Nonnull
    private String getBaseItemDisplayName(@Nonnull GearData gearData, @Nonnull ItemStack itemStack) {
        // 1. Try gearData's baseItemId first (most reliable - passed from caller)
        String baseItemId = gearData.baseItemId();
        if (baseItemId != null && !baseItemId.isEmpty()) {
            return ItemNameFormatter.resolveDisplayName(baseItemId);
        }

        // 2. Fall back to ItemStack-only resolution
        return getBaseItemDisplayName(itemStack);
    }

    /**
     * Gets the base item display name from an ItemStack.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Stored base item ID in metadata (for custom gear with instance IDs)</li>
     *   <li>Item asset ID from the ItemStack</li>
     *   <li>Fallback to "Unknown Item"</li>
     * </ol>
     *
     * @param itemStack The ItemStack to get the base name from
     * @return Formatted base item name like "Iron Sword"
     */
    @Nonnull
    private String getBaseItemDisplayName(@Nonnull ItemStack itemStack) {
        // 1. Try stored base item ID (for custom gear with instance IDs)
        String baseItemId = GearUtils.getBaseItemId(itemStack);
        if (baseItemId != null && !baseItemId.isEmpty()) {
            return ItemNameFormatter.resolveDisplayName(baseItemId);
        }

        // 2. Try Item asset ID
        Item itemAsset = itemStack.getItem();
        if (itemAsset != null) {
            String assetId = itemAsset.getId();
            if (assetId != null && !assetId.isEmpty()) {
                return ItemNameFormatter.resolveDisplayName(assetId);
            }
        }

        // 3. Fallback
        return "Unknown Item";
    }

    /**
     * Gets the display name for a gear modifier.
     *
     * <p>Tries to get from config first, falls back to modifier's own display name.
     *
     * @return null if not available
     */
    @Nullable
    private String getModifierDisplayName(@Nonnull GearModifier modifier) {
        // Try config first (checks both prefixes and suffixes)
        return modifierConfig.getPrefix(modifier.id())
                .or(() -> modifierConfig.getSuffix(modifier.id()))
                .map(ModifierConfig.ModifierDefinition::displayName)
                .orElse(modifier.displayName());
    }

    /**
     * Applies rarity-based styling to a name string.
     *
     * @param name The name text
     * @param rarity The rarity for color and bold decisions
     * @return Styled Message
     */
    @Nonnull
    private Message applyRarityStyle(@Nonnull String name, @Nonnull GearRarity rarity) {
        String color = TooltipStyles.getRarityColor(rarity);
        boolean shouldBold = rarity.isAtLeast(GearRarity.EPIC);

        Message message = Message.raw(name).color(color);

        if (shouldBold) {
            message = message.bold(true);
        }

        return message;
    }
}
