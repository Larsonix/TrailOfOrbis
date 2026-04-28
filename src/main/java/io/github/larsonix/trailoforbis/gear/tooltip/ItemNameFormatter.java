package io.github.larsonix.trailoforbis.gear.tooltip;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds styled item names using Hytale's Message API.
 *
 * <p>Creates dynamic item names with:
 * <ul>
 *   <li>Prefix integration (e.g., "Sharp" in "Sharp Iron Sword")</li>
 *   <li>Suffix integration (e.g., "of the Bear" in "Iron Sword of the Bear")</li>
 *   <li>Rarity-based coloring</li>
 *   <li>Bold styling for high-rarity items (Epic+)</li>
 * </ul>
 *
 * <p>Example output: "Sharp Iron Sword of the Bear" (gold, bold for Legendary)
 */
public final class ItemNameFormatter {

    /** Rarity threshold for bold styling (EPIC and above) */
    private static final GearRarity BOLD_THRESHOLD = GearRarity.EPIC;

    private final ModifierConfig modifierConfig;
    private final boolean includePrefix;
    private final boolean includeSuffix;
    private final GearRarity boldThreshold;

    /**
     * Creates an ItemNameFormatter with default settings.
     *
     * <p>Default: includes prefix and suffix, bold threshold at EPIC.
     *
     * @param modifierConfig The modifier configuration (for display names)
     */
    public ItemNameFormatter(@Nonnull ModifierConfig modifierConfig) {
        this(modifierConfig, true, true, BOLD_THRESHOLD);
    }

    /**
     * Creates an ItemNameFormatter with custom settings.
     *
     * @param modifierConfig The modifier configuration
     * @param includePrefix Whether to include prefix in name
     * @param includeSuffix Whether to include suffix in name
     * @param boldThreshold Minimum rarity for bold styling
     */
    public ItemNameFormatter(
            @Nonnull ModifierConfig modifierConfig,
            boolean includePrefix,
            boolean includeSuffix,
            @Nonnull GearRarity boldThreshold) {
        this.modifierConfig = Objects.requireNonNull(modifierConfig, "modifierConfig cannot be null");
        this.includePrefix = includePrefix;
        this.includeSuffix = includeSuffix;
        this.boldThreshold = Objects.requireNonNull(boldThreshold, "boldThreshold cannot be null");
    }

    // =========================================================================
    // MAIN BUILDING METHODS
    // =========================================================================

    /**
     * Builds a fully styled item name with prefix and suffix.
     *
     * <p>Format: "[Prefix] [BaseItemName] [Suffix]"
     * <p>Example: "Sharp Iron Sword of the Bear"
     *
     * @param baseItemName The base item name (e.g., "Iron Sword")
     * @param gearData The gear data containing modifiers and rarity
     * @return Styled Message with colored and optionally bold name
     */
    @Nonnull
    public Message buildItemName(@Nonnull String baseItemName, @Nonnull GearData gearData) {
        Objects.requireNonNull(baseItemName, "baseItemName cannot be null");
        Objects.requireNonNull(gearData, "gearData cannot be null");

        StringBuilder nameBuilder = new StringBuilder();

        // Add first prefix name if present and enabled
        if (includePrefix && !gearData.prefixes().isEmpty()) {
            GearModifier prefix = gearData.prefixes().get(0);
            String prefixName = getModifierDisplayName(prefix);
            if (prefixName != null && !prefixName.isEmpty()) {
                nameBuilder.append(prefixName).append(" ");
            }
        }

        // Add base item name
        nameBuilder.append(baseItemName);

        // Add first suffix name if present and enabled
        if (includeSuffix && !gearData.suffixes().isEmpty()) {
            GearModifier suffix = gearData.suffixes().get(0);
            String suffixName = getModifierDisplayName(suffix);
            if (suffixName != null && !suffixName.isEmpty()) {
                nameBuilder.append(" ").append(suffixName);
            }
        }

        // Apply rarity styling
        return applyRarityStyle(nameBuilder.toString(), gearData.rarity());
    }

    /**
     * Builds a simple styled item name without modifiers.
     *
     * <p>Useful for compact displays or when modifiers should only appear in tooltip.
     *
     * @param baseItemName The base item name
     * @param rarity The gear rarity
     * @return Styled Message with colored and optionally bold name
     */
    @Nonnull
    public Message buildSimpleName(@Nonnull String baseItemName, @Nonnull GearRarity rarity) {
        Objects.requireNonNull(baseItemName, "baseItemName cannot be null");
        Objects.requireNonNull(rarity, "rarity cannot be null");

        return applyRarityStyle(baseItemName, rarity);
    }

    /**
     * Builds a name with explicit prefix and suffix (overriding gear data).
     *
     * @param baseItemName The base item name
     * @param prefix Optional prefix text (e.g., "Sharp")
     * @param suffix Optional suffix text (e.g., "of the Bear")
     * @param rarity The gear rarity for styling
     * @return Styled Message
     */
    @Nonnull
    public Message buildCustomName(
            @Nonnull String baseItemName,
            @Nullable String prefix,
            @Nullable String suffix,
            @Nonnull GearRarity rarity) {
        Objects.requireNonNull(baseItemName, "baseItemName cannot be null");
        Objects.requireNonNull(rarity, "rarity cannot be null");

        StringBuilder nameBuilder = new StringBuilder();

        if (prefix != null && !prefix.isEmpty()) {
            nameBuilder.append(prefix).append(" ");
        }

        nameBuilder.append(baseItemName);

        if (suffix != null && !suffix.isEmpty()) {
            nameBuilder.append(" ").append(suffix);
        }

        return applyRarityStyle(nameBuilder.toString(), rarity);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

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
        boolean shouldBold = rarity.isAtLeast(boldThreshold);

        Message message = Message.raw(name).color(color);

        if (shouldBold) {
            message = message.bold(true);
        }

        return message;
    }

    /**
     * Gets the display name for a modifier.
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

    // =========================================================================
    // STATIC UTILITIES
    // =========================================================================

    /** Pattern to split camelCase/PascalCase boundaries and digit-word boundaries. */
    private static final Pattern CAMEL_CASE_SPLIT = Pattern.compile(
            "(?<=[a-z])(?=[A-Z])"        // lowercase → uppercase: "bladeStage" → "blade|Stage"
            + "|(?<=[A-Z])(?=[A-Z][a-z])" // acronym → word: "RPGItem" → "RPG|Item"
            + "|(?<=[a-zA-Z]{2})(?=[0-9])" // letter → digit (only if 2+ letters before): "stage3" → "stage|3" but "V2" stays
            + "|(?<=[0-9])(?=[a-zA-Z])"   // digit → letter: "3of" → "3|of"
    );

    /**
     * Resolves the display name for an item, using the modder's own translation
     * if available, falling back to ID-based formatting.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Hytale i18n system — the name the modder defined in their .lang file</li>
     *   <li>ID-based formatting via {@link #formatItemId(String)}</li>
     * </ol>
     *
     * @param itemId The item ID to resolve a name for
     * @return Human-readable display name
     */
    @Nonnull
    public static String resolveDisplayName(@Nonnull String itemId) {
        Objects.requireNonNull(itemId, "itemId cannot be null");

        // Try the modder's own translation first
        String translated = resolveTranslation(itemId);
        if (translated != null) {
            return translated;
        }

        // Fall back to ID-based formatting
        return formatItemId(itemId);
    }

    /**
     * Attempts to resolve the item's display name from Hytale's i18n system.
     *
     * <p>Looks up the item's translation key in the loaded .lang files. This gives
     * us the exact name the modder intended for their item.
     *
     * @param itemId The item ID
     * @return The translated name, or null if no translation exists
     */
    @Nullable
    static String resolveTranslation(@Nonnull String itemId) {
        try {
            // Get the Item asset to find its translation key
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null || item == Item.UNKNOWN) {
                return null;
            }

            String translationKey = item.getTranslationKey();
            if (translationKey == null || translationKey.isEmpty()) {
                return null;
            }

            // Resolve via the i18n module
            I18nModule i18n = I18nModule.get();
            if (i18n == null) {
                return null;
            }

            String translated = i18n.getMessage("en-US", translationKey);

            // Only use non-empty translations that differ from the raw key
            if (translated != null && !translated.isEmpty() && !translated.equals(translationKey)) {
                return translated.trim();
            }
        } catch (Exception ignored) {
            // i18n not available yet (during startup) or asset not found — fall through
        }

        return null;
    }

    /**
     * Formats a Hytale item ID into a readable display name.
     *
     * <p>Handles three naming conventions:
     * <ul>
     *   <li>Vanilla: "Weapon_Sword_Iron" → "Iron Sword"</li>
     *   <li>Mod-prefixed: "ExampleMod_Weapon_Mace_EventHorizon" → "Event Horizon Mace"</li>
     *   <li>CamelCase: "Runebladestage3" → "Runeblade Stage 3"</li>
     * </ul>
     *
     * <p>For accurate mod item names, prefer {@link #resolveDisplayName(String)}
     * which checks the modder's translation files first.
     *
     * @param itemId The Hytale item ID
     * @return Formatted display name
     */
    @Nonnull
    public static String formatItemId(@Nonnull String itemId) {
        Objects.requireNonNull(itemId, "itemId cannot be null");

        // Split by underscore
        String[] parts = itemId.split("_");
        if (parts.length < 2) {
            // No underscores — try camelCase splitting
            return formatCamelCase(itemId);
        }

        String category = parts[0].toLowerCase();

        // Check if the first part is a known mod prefix (not a Hytale category)
        // Mod prefixes are typically PascalCase identifiers that aren't weapon/armor/tool
        return switch (category) {
            case "weapon" -> formatWeaponName(parts);
            case "armor" -> formatArmorName(parts);
            case "tool" -> formatToolName(parts);
            default -> {
                // Could be a mod-prefixed item like "ExampleMod_Weapon_Mace_EventHorizon"
                // Try stripping the first part and re-parsing
                String withoutPrefix = itemId.substring(parts[0].length() + 1);
                String[] innerParts = withoutPrefix.split("_");
                if (innerParts.length >= 2) {
                    String innerCategory = innerParts[0].toLowerCase();
                    if (innerCategory.equals("weapon") || innerCategory.equals("armor") || innerCategory.equals("tool")) {
                        yield formatItemId(withoutPrefix);
                    }
                }
                // Not a mod-prefixed vanilla pattern — format all parts after the first
                yield formatGenericName(parts);
            }
        };
    }

    /**
     * Formats a weapon item ID.
     * "Weapon_Sword_Iron" → "Iron Sword"
     */
    @Nonnull
    private static String formatWeaponName(String[] parts) {
        if (parts.length >= 3) {
            // Join everything after the weapon type as the material/name
            // Handles multi-word names like "Weapon_Mace_EventHorizon" → "Event Horizon Mace"
            String weaponType = formatCamelCase(parts[1]);
            StringBuilder material = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (!material.isEmpty()) material.append(" ");
                material.append(formatCamelCase(parts[i]));
            }
            return material + " " + weaponType;
        }
        return formatGenericName(parts);
    }

    /**
     * Formats an armor item ID.
     * "Armor_Iron_Chest" → "Iron Chestplate"
     */
    @Nonnull
    private static String formatArmorName(String[] parts) {
        if (parts.length >= 3) {
            // Last part is the slot, everything in between is material
            String slot = formatArmorSlot(parts[parts.length - 1]);
            StringBuilder material = new StringBuilder();
            for (int i = 1; i < parts.length - 1; i++) {
                if (!material.isEmpty()) material.append(" ");
                material.append(formatCamelCase(parts[i]));
            }
            return material + " " + slot;
        }
        return formatGenericName(parts);
    }

    /**
     * Formats a tool item ID.
     * "Tool_Pickaxe_Stone" → "Stone Pickaxe"
     */
    @Nonnull
    private static String formatToolName(String[] parts) {
        if (parts.length >= 3) {
            String toolType = formatCamelCase(parts[1]);
            StringBuilder material = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (!material.isEmpty()) material.append(" ");
                material.append(formatCamelCase(parts[i]));
            }
            return material + " " + toolType;
        }
        return formatGenericName(parts);
    }

    /**
     * Formats a generic item ID by joining parts after the first (category).
     */
    @Nonnull
    private static String formatGenericName(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(formatCamelCase(parts[i]));
        }
        return sb.isEmpty() ? formatCamelCase(parts[0]) : sb.toString();
    }

    /**
     * Formats an armor slot name.
     */
    @Nonnull
    private static String formatArmorSlot(String slot) {
        return switch (slot.toLowerCase()) {
            case "head", "helmet" -> "Helmet";
            case "chest", "chestplate" -> "Chestplate";
            case "legs", "leggings" -> "Leggings";
            case "feet", "boots" -> "Boots";
            case "hands", "gloves", "gauntlets" -> "Gauntlets";
            default -> formatCamelCase(slot);
        };
    }

    /**
     * Splits a camelCase/PascalCase word into space-separated, capitalized words.
     *
     * <p>Also splits on digit-word boundaries:
     * <ul>
     *   <li>"Runebladestage3" → "Runeblade Stage 3"</li>
     *   <li>"EventHorizon" → "Event Horizon"</li>
     *   <li>"Iron" → "Iron"</li>
     *   <li>"V2" → "V2"</li>
     * </ul>
     */
    @Nonnull
    static String formatCamelCase(@Nonnull String word) {
        if (word == null || word.isEmpty()) return "";

        String[] words = CAMEL_CASE_SPLIT.split(word);
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(formatWord(w));
        }
        return sb.isEmpty() ? formatWord(word) : sb.toString();
    }

    /**
     * Capitalizes a word (first letter uppercase, rest lowercase).
     *
     * <p>Preserves all-uppercase sequences of 1-2 characters (likely abbreviations/version markers):
     * "V2" stays "V2", "HP" stays "HP".
     */
    @Nonnull
    static String formatWord(String word) {
        if (word == null || word.isEmpty()) return "";
        // Keep short all-caps words as-is (abbreviations like "V2", "HP", "II")
        if (word.length() <= 2 && word.equals(word.toUpperCase())) {
            return word;
        }
        // Pure digit tokens stay as-is
        if (word.chars().allMatch(Character::isDigit)) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
    }
}
