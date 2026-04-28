package io.github.larsonix.trailoforbis.gear.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Classifies weapons into subtypes based on Hytale item IDs.
 *
 * <p>Each weapon type has a distinct playstyle and therefore should
 * have access to different stat modifiers:
 * <ul>
 *   <li><b>Melee (one-handed)</b>: SWORD, DAGGER, AXE, MACE, CLAWS, CLUB</li>
 *   <li><b>Melee (two-handed)</b>: LONGSWORD, BATTLEAXE, SPEAR</li>
 *   <li><b>Ranged (physical)</b>: SHORTBOW, CROSSBOW, BLOWGUN</li>
 *   <li><b>Ranged (thrown)</b>: BOMB, DART, KUNAI</li>
 *   <li><b>Magic</b>: STAFF, WAND, SPELLBOOK</li>
 *   <li><b>Offhand</b>: SHIELD</li>
 * </ul>
 *
 * <p>Supports multiple item ID conventions for mod compatibility:
 * {@code Weapon_{Type}_{Material}}, {@code {Type}_{Material}_{Color}},
 * or compound names like {@code GhostSword}.
 */
public enum WeaponType {
    // One-handed melee
    SWORD("Sword", Category.MELEE, "balanced melee"),
    DAGGER("Daggers", Category.MELEE, "fast, crit-focused"),
    AXE("Axe", Category.MELEE, "high damage melee"),
    MACE("Mace", Category.MELEE, "blunt damage"),
    CLAWS("Claws", Category.MELEE, "fast dual attacks"),
    CLUB("Club", Category.MELEE, "primitive blunt"),

    // Two-handed melee
    LONGSWORD("Longsword", Category.MELEE_2H, "slow, high damage"),
    BATTLEAXE("Battleaxe", Category.MELEE_2H, "very high damage"),
    SPEAR("Spear", Category.MELEE_2H, "reach, balanced"),

    // Ranged (physical projectile)
    SHORTBOW("Shortbow", Category.RANGED, "fast projectile"),
    CROSSBOW("Crossbow", Category.RANGED, "slow, high damage projectile"),
    BLOWGUN("Blowgun", Category.RANGED, "fast, low damage"),

    // Ranged (thrown)
    BOMB("Bomb", Category.THROWN, "explosive"),
    DART("Dart", Category.THROWN, "throwing"),
    KUNAI("Kunai", Category.THROWN, "fast throwing"),

    // Magic
    STAFF("Staff", Category.MAGIC, "spell damage"),
    WAND("Wand", Category.MAGIC, "fast casting"),
    SPELLBOOK("Spellbook", Category.MAGIC, "mana-focused"),

    // Offhand
    SHIELD("Shield", Category.OFFHAND, "defensive"),

    // Fallback for unknown weapon types
    UNKNOWN("Unknown", Category.MELEE, "generic weapon");

    private final String idPattern;
    private final Category category;
    private final String description;

    // Lookup map for fast ID resolution — includes aliases for mod compatibility
    private static final Map<String, WeaponType> BY_KEYWORD;

    static {
        Map<String, WeaponType> map = new HashMap<>();
        // Primary patterns (from idPattern field)
        for (WeaponType t : values()) {
            if (t != UNKNOWN) {
                map.put(t.idPattern.toLowerCase(), t);
            }
        }
        // Aliases for mod items that use singular/alternate naming
        map.put("dagger", DAGGER);       // TheArmory: Dagger_Iron_Black (singular)
        map.put("dualswords", DAGGER);   // TheArmory: Weapon_Dualswords_Iron
        map.put("zweihander", LONGSWORD); // German two-handed sword
        map.put("flail", MACE);          // Flails share mace gameplay
        // Hexcode mod: Hexstaff_Basic_Iron → STAFF, Fire_Hexbook → SPELLBOOK
        map.put("hexstaff", STAFF);
        map.put("hexbook", SPELLBOOK);
        map.put("hex_book", SPELLBOOK);  // Hex_Book (segment "hex" + "book" won't match individually)
        BY_KEYWORD = Collections.unmodifiableMap(map);
    }

    WeaponType(String idPattern, Category category, String description) {
        this.idPattern = idPattern;
        this.category = category;
        this.description = description;
    }

    /**
     * The pattern to match in item IDs (e.g., "Sword" matches "Weapon_Sword_Iron").
     */
    public String getIdPattern() {
        return idPattern;
    }

    /**
     * The weapon category (melee, ranged, magic, etc.).
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Human-readable description of this weapon type.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Whether this weapon deals physical damage as its primary type.
     */
    public boolean isPhysical() {
        return category != Category.MAGIC;
    }

    /**
     * Whether this weapon is a magic/spell damage weapon.
     */
    public boolean isMagic() {
        return category == Category.MAGIC;
    }

    /**
     * Whether this weapon is ranged (including bows and thrown).
     */
    public boolean isRanged() {
        return category == Category.RANGED || category == Category.THROWN;
    }

    /**
     * Whether this weapon is melee (one-handed or two-handed).
     */
    public boolean isMelee() {
        return category == Category.MELEE || category == Category.MELEE_2H;
    }

    /**
     * Whether this is an offhand item (shield).
     */
    public boolean isOffhand() {
        return category == Category.OFFHAND;
    }

    /**
     * Whether this weapon requires two hands.
     *
     * <p>Two-handed weapons: LONGSWORD, BATTLEAXE, SPEAR (MELEE_2H),
     * SHORTBOW, CROSSBOW, BLOWGUN (RANGED), STAFF (MAGIC — staves are two-handed).
     */
    public boolean isTwoHanded() {
        return category == Category.MELEE_2H
                || category == Category.RANGED
                || this == STAFF;
    }

    /**
     * Whether this weapon is a thrown consumable (BOMB, DART, KUNAI).
     */
    public boolean isThrown() {
        return category == Category.THROWN;
    }

    /**
     * Whether this weapon is eligible for RPG stat generation.
     *
     * <p>Excludes offhand items (shields) and thrown consumables
     * (bombs, darts, kunai) — these are expendable items that
     * should not receive modifiers, implicits, or quality rolls.
     */
    public boolean isStatEligible() {
        return !isOffhand() && !isThrown();
    }

    /**
     * Resolves weapon type from a Hytale item ID.
     *
     * <p>Uses a two-layer approach for mod compatibility:
     * <ol>
     *   <li><b>Segment matching</b>: Split item ID by underscore, check each segment
     *       against known weapon type keywords. Matches longest keywords first
     *       (e.g., "longsword" before "sword") to avoid false positives.</li>
     *   <li><b>Substring fallback</b>: For items with no underscores (e.g., "GhostSword",
     *       "Zweihander"), scan the full ID for keyword substrings.</li>
     * </ol>
     *
     * <p>This handles all naming conventions:
     * <ul>
     *   <li>{@code Weapon_Sword_Iron} (vanilla prefix)</li>
     *   <li>{@code Sword_Iron_Black} (mod prefix-free)</li>
     *   <li>{@code SomeMod_Soulblight_Longsword} (mod prefix + type at end)</li>
     *   <li>{@code GhostSword} (compound name)</li>
     * </ul>
     *
     * @param itemId The item ID
     * @return The weapon type, or empty if unrecognized
     */
    @Nonnull
    public static Optional<WeaponType> fromItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return Optional.empty();
        }

        String lower = itemId.toLowerCase();

        // Layer 1: Segment matching — split by underscore, match each segment.
        // Check longest keywords first to avoid "sword" matching before "longsword".
        String[] segments = lower.split("_");
        WeaponType best = null;
        int bestLen = 0;
        for (String segment : segments) {
            WeaponType match = BY_KEYWORD.get(segment);
            if (match != null && segment.length() > bestLen) {
                best = match;
                bestLen = segment.length();
            }
        }
        if (best != null) {
            return Optional.of(best);
        }

        // Layer 2: Substring fallback for compound names (GhostSword, Zweihander).
        // Sort keywords by length descending so "longsword" matches before "sword".
        return BY_KEYWORD.entrySet().stream()
                .filter(e -> lower.contains(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue);
    }

    /**
     * Resolves weapon type from item ID, returning UNKNOWN if not found.
     *
     * @param itemId The item ID
     * @return The weapon type, or UNKNOWN if not recognized
     */
    @Nonnull
    public static WeaponType fromItemIdOrUnknown(@Nullable String itemId) {
        return fromItemId(itemId).orElse(UNKNOWN);
    }

    /**
     * Weapon categories for grouping stat restrictions.
     */
    public enum Category {
        /** One-handed melee weapons */
        MELEE,
        /** Two-handed melee weapons */
        MELEE_2H,
        /** Ranged physical weapons (bows) */
        RANGED,
        /** Thrown weapons */
        THROWN,
        /** Magic weapons */
        MAGIC,
        /** Offhand items (shields) */
        OFFHAND
    }
}
