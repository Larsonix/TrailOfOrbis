package io.github.larsonix.trailoforbis.gear.conversion;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps item materials to maximum rarity tiers.
 *
 * <p>Extracts material from Hytale item IDs using pattern matching:
 * <ul>
 *   <li>{@code Weapon_Sword_Iron} → "iron"</li>
 *   <li>{@code Armor_Steel_Chest} → "steel"</li>
 *   <li>{@code Armor_Leather_Light_Head} → "leather_light"</li>
 * </ul>
 *
 * <p>Uses configuration to map materials to rarity caps, preventing low-tier
 * materials from rolling high rarities (e.g., wooden swords can't be Legendary).
 *
 * <p>This class is stateless and thread-safe.
 */
public final class MaterialTierMapper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Common patterns for Hytale item IDs
    // Weapon format: Weapon_Type_Material (e.g., Weapon_Sword_Iron)
    // Armor format: Armor_Material_Slot (e.g., Armor_Steel_Chest)
    // Armor with variant: Armor_Material_Variant_Slot (e.g., Armor_Leather_Light_Head)

    /** Pattern for weapon IDs: Weapon_Type_Material */
    private static final Pattern WEAPON_PATTERN = Pattern.compile(
        "^(?:Weapon|weapon)_[A-Za-z]+_([A-Za-z_]+)$"
    );

    /** Pattern for armor IDs: Armor_Material[_Variant]_Slot */
    private static final Pattern ARMOR_PATTERN = Pattern.compile(
        "^(?:Armor|armor)_([A-Za-z]+(?:_[A-Za-z]+)?)_(?:Head|Chest|Legs|Hands)$"
    );

    /** Pattern for tool IDs that might be weapons: Tool_Type_Material */
    private static final Pattern TOOL_PATTERN = Pattern.compile(
        "^(?:Tool|tool)_[A-Za-z]+_([A-Za-z_]+)$"
    );

    /** Fallback pattern: try to extract last segment before common suffixes */
    private static final Pattern FALLBACK_PATTERN = Pattern.compile(
        "_([A-Za-z]+)(?:_(?:Head|Chest|Legs|Hands|Sword|Axe|Hammer|Spear|Bow|Staff))?$"
    );

    private final VanillaConversionConfig config;

    /**
     * Creates a new MaterialTierMapper with the given config.
     */
    public MaterialTierMapper(@Nonnull VanillaConversionConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Gets the maximum rarity allowed for the given item based on its material.
     */
    @Nonnull
    public GearRarity getMaxRarity(@Nonnull String itemId) {
        Objects.requireNonNull(itemId, "itemId cannot be null");

        Optional<String> material = extractMaterial(itemId);

        if (material.isEmpty()) {
            LOGGER.atFine().log("Could not extract material from item ID '%s', using default rarity", itemId);
            return config.getDefaultMaxRarityEnum();
        }

        String materialName = material.get().toLowerCase();
        GearRarity maxRarity = config.getMaxRarityForMaterial(materialName);

        LOGGER.atFine().log("Item '%s' has material '%s' with max rarity %s",
            itemId, materialName, maxRarity);

        return maxRarity;
    }

    /**
     * Extracts the material name from an item ID.
     *
     * @return the material name, or empty if extraction failed
     */
    @Nonnull
    public Optional<String> extractMaterial(@Nonnull String itemId) {
        Objects.requireNonNull(itemId, "itemId cannot be null");

        // Try weapon pattern first
        Matcher weaponMatcher = WEAPON_PATTERN.matcher(itemId);
        if (weaponMatcher.matches()) {
            return Optional.of(weaponMatcher.group(1));
        }

        // Try armor pattern
        Matcher armorMatcher = ARMOR_PATTERN.matcher(itemId);
        if (armorMatcher.matches()) {
            return Optional.of(armorMatcher.group(1));
        }

        // Try tool pattern (some tools function as weapons)
        Matcher toolMatcher = TOOL_PATTERN.matcher(itemId);
        if (toolMatcher.matches()) {
            return Optional.of(toolMatcher.group(1));
        }

        // Fallback: try to extract something useful
        Matcher fallbackMatcher = FALLBACK_PATTERN.matcher(itemId);
        if (fallbackMatcher.find()) {
            String extracted = fallbackMatcher.group(1);
            // Skip if it's a slot or type name, not a material
            if (!isSlotOrTypeName(extracted)) {
                return Optional.of(extracted);
            }
        }

        // Last resort: try splitting by underscore and taking middle segments
        String[] parts = itemId.split("_");
        if (parts.length >= 3) {
            // For patterns like Type_Material_Slot, material is in the middle
            return Optional.of(parts[1]);
        }

        return Optional.empty();
    }

    private boolean isSlotOrTypeName(@Nonnull String name) {
        String lower = name.toLowerCase();
        return lower.equals("head") || lower.equals("chest") ||
               lower.equals("legs") || lower.equals("hands") ||
               lower.equals("weapon") || lower.equals("armor") ||
               lower.equals("sword") || lower.equals("axe") ||
               lower.equals("hammer") || lower.equals("spear") ||
               lower.equals("bow") || lower.equals("staff") ||
               lower.equals("tool") || lower.equals("shield");
    }

    /**
     * Gets the distance range for the given item based on its material.
     * Uses the shared mob scaling formula to derive gear level ranges.
     */
    @Nonnull
    public VanillaConversionConfig.DistanceRange getDistanceRange(@Nonnull String itemId) {
        Objects.requireNonNull(itemId, "itemId cannot be null");
        Optional<String> material = extractMaterial(itemId);
        if (material.isEmpty()) {
            return config.getDefaultDistance();
        }
        return config.getDistanceForMaterial(material.get());
    }

    /**
     * Caps a rolled rarity at the material's maximum.
     *
     * @param rolledRarity The rarity that was randomly rolled
     * @param maxRarity The maximum allowed rarity for the material
     * @return The capped rarity (minimum of rolled and max)
     */
    @Nonnull
    public GearRarity capRarity(@Nonnull GearRarity rolledRarity, @Nonnull GearRarity maxRarity) {
        Objects.requireNonNull(rolledRarity, "rolledRarity cannot be null");
        Objects.requireNonNull(maxRarity, "maxRarity cannot be null");

        // Compare by ordinal - higher ordinal means rarer
        if (rolledRarity.ordinal() > maxRarity.ordinal()) {
            LOGGER.atFine().log("Capping rarity from %s to %s due to material limit",
                rolledRarity, maxRarity);
            return maxRarity;
        }
        return rolledRarity;
    }

    /**
     * Convenience method: caps a rolled rarity at the material's maximum for the given item.
     *
     * @param rolledRarity The rarity that was randomly rolled
     * @param itemId The item ID to determine material cap from
     * @return The capped rarity
     */
    @Nonnull
    public GearRarity capRarityForItem(@Nonnull GearRarity rolledRarity, @Nonnull String itemId) {
        return capRarity(rolledRarity, getMaxRarity(itemId));
    }
}
