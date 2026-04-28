package io.github.larsonix.trailoforbis.gear.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Classifies armor into material types based on Hytale item IDs.
 *
 * <p>Armor material determines which defensive stat modifiers can roll:
 * <ul>
 *   <li><b>CLOTH</b>: Mana, spell damage, elemental resistances</li>
 *   <li><b>LEATHER</b>: Evasion, stamina, speed, flexibility</li>
 *   <li><b>PLATE</b>: Health, armor, knockback resistance, tanking</li>
 *   <li><b>WOOD</b>: Nature/physical hybrid (for primitive/druid gear)</li>
 *   <li><b>SPECIAL</b>: Unique materials with full modifier access</li>
 * </ul>
 *
 * <p>Classification uses two layers for mod compatibility:
 * <ol>
 *   <li><b>ItemSoundSet</b>: {@code ISS_Armor_Heavy} → PLATE, {@code ISS_Armor_Leather} → LEATHER,
 *       {@code ISS_Armor_Cloth} → CLOTH. Works for any mod inheriting vanilla templates.</li>
 *   <li><b>Item ID pattern</b>: {@code Armor_{Material}_{Slot}} name parsing as fallback.</li>
 * </ol>
 */
public enum ArmorMaterial {
    /**
     * Cloth armor (Cotton, Linen, Silk, Wool, Cindercloth).
     * Magic-focused with mana and spell stats.
     */
    CLOTH(Set.of("cloth", "cotton", "linen", "silk", "wool", "cindercloth")),

    /**
     * Leather armor (Light, Medium, Heavy, Soft, Raven).
     * Agility-focused with evasion and speed.
     */
    LEATHER(Set.of("leather", "leather_light", "leather_medium", "leather_heavy",
            "leather_soft", "leather_raven")),

    /**
     * Metal/Plate armor (Iron, Steel, Bronze, Copper, Cobalt, Mithril, etc.).
     * Defense-focused with health, armor, and stability.
     */
    PLATE(Set.of("iron", "steel", "bronze", "copper", "cobalt", "mithril",
            "adamantite", "onyxium", "thorium", "prisma", "steel_ancient",
            "bronze_ornate", "electrum", "silversteel")),

    /**
     * Wood armor (primitive/druid gear).
     * Hybrid physical/nature stats.
     */
    WOOD(Set.of("wood", "bone")),

    /**
     * Special/unique armor materials.
     * Full modifier access for quest/unique items.
     */
    SPECIAL(Set.of("kweebec", "trooper", "trork", "diving", "diving_crude",
            "qa", "debug", "_debug"));

    private final Set<String> materialPatterns;

    /**
     * Default mapping from ItemSoundSet IDs to armor materials.
     * Vanilla Hytale uses these sound sets on all template armor items,
     * and mods inherit them via parent templates.
     */
    private static final Map<String, ArmorMaterial> DEFAULT_SOUND_SET_MAP = Map.of(
            "iss_armor_heavy", PLATE,
            "iss_armor_leather", LEATHER,
            "iss_armor_cloth", CLOTH
    );

    ArmorMaterial(Set<String> materialPatterns) {
        this.materialPatterns = materialPatterns;
    }

    /**
     * The item ID patterns that match this material.
     */
    public Set<String> getMaterialPatterns() {
        return materialPatterns;
    }

    /**
     * Resolves armor material from a Hytale item ID.
     *
     * <p>Expected format: {@code Armor_{Material}[_Variant]_{Slot}}
     *
     * @param itemId The item ID (e.g., "Armor_Leather_Light_Chest")
     * @return The armor material, or empty if not armor or unrecognized
     */
    @Nonnull
    public static Optional<ArmorMaterial> fromItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return Optional.empty();
        }

        // Must start with "Armor_"
        if (!itemId.startsWith("Armor_")) {
            return Optional.empty();
        }

        // Extract material part: Armor_{Material}[_Variant]_{Slot}
        // We need to identify the material, which may have multiple parts
        String afterPrefix = itemId.substring("Armor_".length()).toLowerCase();

        // Try increasingly specific patterns
        // e.g., "leather_light_chest" → try "leather_light", then "leather"
        for (ArmorMaterial material : values()) {
            for (String pattern : material.materialPatterns) {
                if (afterPrefix.startsWith(pattern + "_") || afterPrefix.equals(pattern)) {
                    return Optional.of(material);
                }
            }
        }

        // Fallback: check if it contains any metal material (for compound names)
        // e.g., "Steel_Ancient_Chest" should match PLATE via "steel"
        for (ArmorMaterial material : values()) {
            for (String pattern : material.materialPatterns) {
                if (afterPrefix.contains(pattern)) {
                    return Optional.of(material);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves armor material from item ID, returning SPECIAL if not found.
     *
     * @param itemId The item ID
     * @return The armor material, or SPECIAL if not recognized
     */
    @Nonnull
    public static ArmorMaterial fromItemIdOrSpecial(@Nullable String itemId) {
        return fromItemId(itemId).orElse(SPECIAL);
    }

    /**
     * Resolves armor material from an ItemSoundSet ID.
     *
     * <p>Uses a two-layer approach:
     * <ol>
     *   <li>Exact match against known sound set IDs (with optional config overrides)</li>
     *   <li>Substring fallback for custom mod sound sets (e.g., "ISS_MyMod_Heavy" → PLATE)</li>
     * </ol>
     *
     * @param soundSetId The ItemSoundSet ID (e.g., "ISS_Armor_Heavy")
     * @param configOverrides Optional config-provided overrides (nullable, lower-cased keys)
     * @return The armor material, or empty if unrecognized
     */
    @Nonnull
    public static Optional<ArmorMaterial> fromSoundSetId(
            @Nullable String soundSetId,
            @Nullable Map<String, String> configOverrides
    ) {
        if (soundSetId == null || soundSetId.isEmpty()) {
            return Optional.empty();
        }

        String lower = soundSetId.toLowerCase();

        // Layer 1a: Config overrides take priority
        if (configOverrides != null) {
            String override = configOverrides.get(lower);
            if (override != null) {
                try {
                    return Optional.of(ArmorMaterial.valueOf(override.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // Invalid material name in config — fall through
                }
            }
        }

        // Layer 1b: Default exact match
        ArmorMaterial exact = DEFAULT_SOUND_SET_MAP.get(lower);
        if (exact != null) {
            return Optional.of(exact);
        }

        // Layer 2: Substring fallback for custom mod sound sets
        // e.g., "ISS_MyMod_PlateArmor" contains "heavy"? No — but "plate" isn't a keyword.
        // Match on the weight-class keywords that Hytale conventions use.
        if (lower.contains("heavy") || lower.contains("plate") || lower.contains("metal")) {
            return Optional.of(PLATE);
        }
        if (lower.contains("leather") || lower.contains("medium") || lower.contains("hide")) {
            return Optional.of(LEATHER);
        }
        if (lower.contains("cloth") || lower.contains("light") || lower.contains("robe") || lower.contains("silk")) {
            return Optional.of(CLOTH);
        }

        return Optional.empty();
    }

    /**
     * Convenience overload without config overrides.
     */
    @Nonnull
    public static Optional<ArmorMaterial> fromSoundSetId(@Nullable String soundSetId) {
        return fromSoundSetId(soundSetId, null);
    }

    /**
     * Whether this material provides primarily magic-focused stats.
     */
    public boolean isMagicFocused() {
        return this == CLOTH;
    }

    /**
     * Whether this material provides primarily agility-focused stats.
     */
    public boolean isAgilityFocused() {
        return this == LEATHER;
    }

    /**
     * Whether this material provides primarily defense-focused stats.
     */
    public boolean isDefenseFocused() {
        return this == PLATE || this == WOOD;
    }

    /**
     * Whether this material has unrestricted modifier access.
     */
    public boolean hasFullAccess() {
        return this == SPECIAL;
    }
}
