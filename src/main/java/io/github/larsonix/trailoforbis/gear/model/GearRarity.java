package io.github.larsonix.trailoforbis.gear.model;

import com.hypixel.hytale.codec.codecs.EnumCodec;

import java.util.Set;

/**
 * Defines the seven rarity tiers for RPG gear.
 *
 * <p>Rarity affects:
 * <ul>
 *   <li>Maximum number of modifiers an item can have</li>
 *   <li>Stat value multiplier (higher rarity = stronger stats)</li>
 *   <li>Base durability multiplier</li>
 *   <li>Drop probability (lower = rarer)</li>
 *   <li>Visual appearance (name color, tooltip style)</li>
 * </ul>
 *
 * <p>These are default values. Actual values can be overridden via gear-balance.yml.
 * The enum provides sensible defaults for when config is unavailable.
 */
public enum GearRarity {

    COMMON(
        1,      // maxModifiers
        1,      // maxPrefixes
        1,      // maxSuffixes
        0.3,    // statMultiplier (accelerating curve: 9.3× total spread)
        1.0,    // durabilityMultiplier
        64.0,   // dropWeight (4× geometric: 75.0%)
        "#C9D2DD", // hexColor (light gray)
        "Common"   // hytaleQualityId
    ),

    UNCOMMON(
        2,
        1,      // maxPrefixes
        2,      // maxSuffixes
        0.5,    // +0.2 jump
        1.2,
        16.0,   // dropWeight (4× geometric: 18.75%)
        "#3E9049", // green
        "Uncommon"
    ),

    RARE(
        3,
        2,      // maxPrefixes
        2,      // maxSuffixes
        0.8,    // +0.3 jump
        1.5,
        4.0,    // dropWeight (4× geometric: 4.69%)
        "#2770B7", // blue
        "Rare"
    ),

    EPIC(
        4,
        2,      // maxPrefixes
        2,      // maxSuffixes
        1.2,    // +0.4 jump
        2.0,
        1.0,    // dropWeight (4× geometric: 1.17%)
        "#8B339E", // purple
        "Epic"
    ),

    LEGENDARY(
        5,      // maxModifiers (was 4)
        2,      // maxPrefixes
        3,      // maxSuffixes (was 2)
        1.7,    // +0.5 jump
        3.0,
        0.25,   // dropWeight (4× geometric: 0.29%)
        "#BB8A2C", // gold/orange
        "Legendary"
    ),

    MYTHIC(
        6,      // maxModifiers (was 4)
        3,      // maxPrefixes (was 2)
        3,      // maxSuffixes (was 2)
        2.3,    // +0.6 jump
        5.0,
        0.0625, // dropWeight (4× geometric: 0.073%)
        "#FF4500", // red-orange
        "Mythic"
    ),

    UNIQUE(
        6,      // maxModifiers (same as Mythic)
        3,      // maxPrefixes
        3,      // maxSuffixes
        2.8,    // +0.5 jump (best stats)
        5.0,    // durabilityMultiplier
        0.016,  // dropWeight (4× geometric: 0.018%)
        "#AF6025", // PoE-style orange
        "Unique"
    );

    private final int maxModifiers;
    private final int maxPrefixes;
    private final int maxSuffixes;
    private final double statMultiplier;
    private final double durabilityMultiplier;
    private final double dropWeight;
    private final String hexColor;
    private final String hytaleQualityId;

    GearRarity(int maxModifiers, int maxPrefixes, int maxSuffixes,
               double statMultiplier, double durabilityMultiplier,
               double dropWeight, String hexColor, String hytaleQualityId) {
        this.maxModifiers = maxModifiers;
        this.maxPrefixes = maxPrefixes;
        this.maxSuffixes = maxSuffixes;
        this.statMultiplier = statMultiplier;
        this.durabilityMultiplier = durabilityMultiplier;
        this.dropWeight = dropWeight;
        this.hexColor = hexColor;
        this.hytaleQualityId = hytaleQualityId;
    }

    /**
     * Maximum number of modifiers (prefixes + suffixes combined) for this rarity.
     *
     * <p>Distribution between prefixes and suffixes is determined by rarity config:
     * <ul>
     *   <li>COMMON: 0-1 prefix, 0-1 suffix (max 1 total)</li>
     *   <li>UNCOMMON: 0-1 prefix, 0-2 suffix (max 2 total)</li>
     *   <li>RARE: 1-2 prefix, 1-2 suffix (max 3 total)</li>
     *   <li>EPIC: 1-2 prefix, 1-2 suffix (max 4 total)</li>
     *   <li>LEGENDARY: 1-2 prefix, 1-3 suffix (max 5 total)</li>
     *   <li>MYTHIC: 1-3 prefix, 1-3 suffix (max 6 total)</li>
     *   <li>UNIQUE: 1-3 prefix, 1-3 suffix (max 6 total, stronger stats)</li>
     * </ul>
     *
     * @return Maximum total modifiers (1-6)
     */
    public int getMaxModifiers() {
        return maxModifiers;
    }

    /**
     * Maximum number of prefix modifiers for this rarity.
     *
     * <p>These are default values that can be overridden via gear-balance.yml.
     *
     * @return Maximum prefixes (1-2)
     */
    public int getMaxPrefixes() {
        return maxPrefixes;
    }

    /**
     * Maximum number of suffix modifiers for this rarity.
     *
     * <p>These are default values that can be overridden via gear-balance.yml.
     *
     * @return Maximum suffixes (1-2)
     */
    public int getMaxSuffixes() {
        return maxSuffixes;
    }

    /**
     * Multiplier applied to modifier stat values.
     *
     * <p>A Common item with a "+10 Physical Damage" modifier at statMultiplier 0.3
     * would actually grant +3 Physical Damage. An Epic item (1.2) grants +12.
     *
     * @return Stat multiplier (0.3 to 2.8)
     */
    public double getStatMultiplier() {
        return statMultiplier;
    }

    /**
     * Multiplier applied to the item's base max durability.
     *
     * <p>Applied to the vanilla item's maxDurability when generating RPG gear.
     * Higher rarity = more durable items.
     *
     * @return Durability multiplier (1.0 to 5.0)
     */
    public double getDurabilityMultiplier() {
        return durabilityMultiplier;
    }

    /**
     * Relative drop weight for loot generation.
     *
     * <p>Higher weight = more common drops. Used by RarityRoller for weighted selection.
     *
     * <p>Example weights: Common=50, Mythic=0.1 means Common drops 500x more often.
     *
     * @return Drop weight (0.1 to 50.0)
     */
    public double getDropWeight() {
        return dropWeight;
    }

    /**
     * Hex color code for display purposes.
     *
     * <p>Used for item name coloring and UI elements. Format: "#RRGGBB".
     *
     * @return Hex color string
     */
    public String getHexColor() {
        return hexColor;
    }

    /**
     * Corresponding Hytale ItemQuality identifier.
     *
     * <p>Used to map our rarity to vanilla's quality system for tooltip styling,
     * drop particles, and slot backgrounds.
     *
     * @return Hytale quality ID string
     */
    public String getHytaleQualityId() {
        return hytaleQualityId;
    }

    /**
     * Returns the set of Hytale ItemQuality IDs allowed as base item skins
     * when generating gear of this rarity.
     *
     * <p>This enforces visual identity per rarity tier — a Common RPG item
     * uses Common-quality skins (crude, wood, bone), while an Epic item uses
     * Epic-quality skins (adamantite, cindercloth).
     *
     * <p>Legendary and Mythic share Epic's pool because vanilla Legendary
     * has only ~5 items and Mythic has 0. Visual distinction at those tiers
     * comes from tooltip color, modifier count, and particle effects.
     *
     * @return Immutable set of allowed Hytale quality IDs
     */
    public Set<String> getAllowedSkinQualities() {
        return switch (this) {
            case COMMON -> Set.of("Common");
            case UNCOMMON -> Set.of("Uncommon");
            case RARE -> Set.of("Rare");
            case EPIC -> Set.of("Epic");
            case LEGENDARY, MYTHIC, UNIQUE -> Set.of("Epic", "Legendary");
        };
    }

    /**
     * Returns the next higher rarity tier, or empty if already at max.
     *
     * <p>Used by rarity upgrade stones. Note: UNIQUE is a special tier
     * that cannot be upgraded to - it's for quest items only.
     *
     * @return Next rarity tier, or empty Optional if this is MYTHIC or UNIQUE
     */
    public java.util.Optional<GearRarity> getNextTier() {
        // UNIQUE is a special tier that cannot be upgraded to
        // MYTHIC is the max upgradeable tier
        if (this == MYTHIC || this == UNIQUE) {
            return java.util.Optional.empty();
        }
        int nextOrdinal = this.ordinal() + 1;
        GearRarity[] values = values();
        if (nextOrdinal < values.length) {
            GearRarity next = values[nextOrdinal];
            // Skip UNIQUE when upgrading
            if (next == UNIQUE) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(next);
        }
        return java.util.Optional.empty();
    }

    /**
     * Returns the previous lower rarity tier, or empty if already at min.
     *
     * <p>Used by corruption failures or certain penalties.
     *
     * @return Previous rarity tier, or empty Optional if this is COMMON
     */
    public java.util.Optional<GearRarity> getPreviousTier() {
        int prevOrdinal = this.ordinal() - 1;
        if (prevOrdinal >= 0) {
            return java.util.Optional.of(values()[prevOrdinal]);
        }
        return java.util.Optional.empty();
    }

    /**
     * Check if this rarity is at least the specified minimum.
     *
     * <p>Useful for "rare+" loot table conditions.
     *
     * @param minimum The minimum rarity to check against
     * @return true if this rarity >= minimum
     */
    public boolean isAtLeast(GearRarity minimum) {
        return this.ordinal() >= minimum.ordinal();
    }

    /**
     * Parse rarity from string (case-insensitive).
     *
     * @param name Rarity name
     * @return The corresponding GearRarity
     * @throws IllegalArgumentException if name is not recognized
     */
    public static GearRarity fromString(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Rarity name cannot be null");
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown rarity: " + name);
        }
    }

    /**
     * Codec for serialization/deserialization of GearRarity.
     *
     * <p>Serializes as the enum name string (e.g., "COMMON", "LEGENDARY").
     * Used by ECS components and data persistence.
     */
    public static final EnumCodec<GearRarity> CODEC = new EnumCodec<>(GearRarity.class);
}
