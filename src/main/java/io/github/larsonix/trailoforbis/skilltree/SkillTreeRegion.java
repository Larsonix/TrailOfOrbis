package io.github.larsonix.trailoforbis.skilltree;

import io.github.larsonix.trailoforbis.util.OklchColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the 14 arms + central core of the 3D Galaxy Skill Tree.
 *
 * <p>The skill tree is structured as a 3D galaxy with:
 * <ul>
 *   <li>CORE: Central origin point connecting all arms</li>
 *   <li>6 elemental arms along cardinal 3D directions</li>
 *   <li>8 octant arms along diagonal 3D directions (cube corners)</li>
 * </ul>
 *
 * <p>Elemental arm layout (horizontal ring + vertical hubs):
 * <pre>
 *              VOID (+Y up)
 *            /  |  |  \
 *    FIRE ─────+──+───── LIGHTNING
 *      |    \  |  |  /    |
 *      |     CORE(center) |
 *      |    /  |  |  \    |
 *    EARTH ────+──+───── WATER
 *            \  |  |  /
 *              WIND (-Y down)
 * </pre>
 *
 * <p>Horizontal ring: FIRE ↔ LIGHTNING ↔ WATER ↔ EARTH ↔ FIRE
 * <p>Vertical hubs: VOID and WIND connect to all 4 horizontal arms
 * <p>Opposite pairs: FIRE↔WATER, LIGHTNING↔EARTH, VOID↔WIND
 *
 * <p>Octant arms sit at the 8 cube corners between elemental axes.
 * Each octant connects to CORE + 3 neighboring elemental arms via bridge midpoints.
 *
 * <p>Each elemental arm connects to:
 * <ul>
 *   <li>CORE (always)</li>
 *   <li>Horizontal arms: two ring neighbors + VOID + WIND (4 adjacent)</li>
 *   <li>Vertical arms (VOID, WIND): all 4 horizontal arms (4 adjacent)</li>
 * </ul>
 *
 * <p>Each octant arm connects to:
 * <ul>
 *   <li>CORE + 3 neighboring elemental arms</li>
 * </ul>
 */
public enum SkillTreeRegion {

    // ═══════════════════════════════════════════════════════════════════
    // CORE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Core region - central origin connecting all arms.
     * Universal bonuses, hybrid builds, and arm transitions.
     */
    CORE("Core", "Universal origin and cross-arm bridges", "#EFE3CF", "Ingredient_Core_Essence", 0),

    // ═══════════════════════════════════════════════════════════════════
    // ELEMENTAL ARMS (cardinal 3D directions)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Fire arm - Glass Cannon: physical damage, charged attacks, crit multiplier.
     * Direction: +X (East)
     */
    FIRE("Fire", "Glass Cannon - physical damage and burning", "#FF7881", "Ingredient_Fire_Essence", 1),

    /**
     * Water arm - Arcane Mage: spell damage, mana, energy shield.
     * Direction: -X (West)
     */
    WATER("Water", "Arcane Mage - spell damage and mana", "#00CBFF", "Ingredient_Water_Essence", 2),

    /**
     * Lightning arm - Storm Blitz: attack speed, move speed, crit chance.
     * Direction: +Z (South)
     */
    LIGHTNING("Lightning", "Storm Blitz - speed and chain effects", "#F1A900", "Ingredient_Lightning_Essence", 3),

    /**
     * Earth arm - Iron Fortress: max health, armor, health regen, block.
     * Direction: -Z (North)
     */
    EARTH("Earth", "Iron Fortress - health, armor, and defense", "#7BD23B", "Ingredient_Earth_Essence", 4),

    /**
     * Void arm - Life Devourer: life steal, true damage, DoT.
     * Direction: +Y (Up)
     */
    VOID("Void", "Life Devourer - life steal and corruption", "#DE8DFF", "Ingredient_Void_Essence", 5),

    /**
     * Wind arm - Ghost Ranger: evasion, accuracy, projectile damage.
     * Direction: -Y (Down)
     */
    WIND("Wind", "Ghost Ranger - evasion and projectiles", "#00DEBC", "Ingredient_Wind_Essence", 6),

    // ═══════════════════════════════════════════════════════════════════
    // OCTANT ARMS (diagonal 3D directions — cube corners)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Havoc arm - Offensive Chaos: crit, DoT, and attack speed.
     * Direction: +X +Y +Z (Fire/Void/Lightning corner)
     */
    HAVOC("Havoc", "Offensive Chaos — crit, DoT, and attack speed", "#DE77AB", "Ingredient_Havoc_Essence", 7),

    /**
     * Juggernaut arm - Unkillable Bruiser: physical and sustain.
     * Direction: +X +Y -Z (Fire/Void/Earth corner)
     */
    JUGGERNAUT("Juggernaut", "Unkillable Bruiser — physical and sustain", "#FEA8F6", "Ingredient_Juggernaut_Essence", 8),

    /**
     * Striker arm - Speed Assassin: crit, evasion, and attack speed.
     * Direction: +X -Y +Z (Fire/Wind/Lightning corner)
     */
    STRIKER("Striker", "Speed Assassin — crit, evasion, and attack speed", "#FFAE74", "Ingredient_Striker_Essence", 9),

    /**
     * Warden arm - Physical Ranger: projectiles and block.
     * Direction: +X -Y -Z (Fire/Wind/Earth corner)
     */
    WARDEN("Warden", "Physical Ranger — projectiles and block", "#A4A528", "Ingredient_Warden_Essence", 10),

    /**
     * Warlock arm - Dark Caster: spell damage and DoT.
     * Direction: -X +Y +Z (Water/Void/Lightning corner)
     */
    WARLOCK("Warlock", "Dark Caster — spell damage and DoT", "#9E8EEF", "Ingredient_Warlock_Essence", 11),

    /**
     * Lich arm - Tanky Dark Mage: DoT, defense, and shield.
     * Direction: -X +Y -Z (Water/Void/Earth corner)
     */
    LICH("Lich", "Tanky Dark Mage — DoT, defense, and shield", "#94CBFF", "Ingredient_Lich_Essence", 12),

    /**
     * Tempest arm - Mobile Mage: spell, projectile, and speed.
     * Direction: -X -Y +Z (Water/Wind/Lightning corner)
     */
    TEMPEST("Tempest", "Mobile Mage — spell, projectile, and speed", "#00B5CB", "Ingredient_Tempest_Essence", 13),

    /**
     * Sentinel arm - Defensive Support: block, evasion, and regen.
     * Direction: -X -Y -Z (Water/Wind/Earth corner)
     */
    SENTINEL("Sentinel", "Defensive Support — block, evasion, and regen", "#87E496", "Ingredient_Sentinel_Essence", 14);

    private final String displayName;
    private final String description;
    private final String themeColor;
    private final String essenceItem;
    private final int armIndex; // 0 = core, 1-6 = elemental arms, 7-14 = octant arms

    SkillTreeRegion(String displayName, String description, String themeColor, String essenceItem, int armIndex) {
        this.displayName = displayName;
        this.description = description;
        this.themeColor = themeColor;
        this.essenceItem = essenceItem;
        this.armIndex = armIndex;
    }

    /**
     * Gets the human-readable display name.
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets a short description of this region's focus.
     */
    @Nonnull
    public String getDescription() {
        return description;
    }

    /**
     * Gets the theme color for this region (hex format).
     */
    @Nonnull
    public String getThemeColor() {
        return themeColor;
    }

    /**
     * Gets the essence item ID used for nodes in this arm.
     */
    @Nonnull
    public String getEssenceItem() {
        return essenceItem;
    }

    /**
     * Gets the arm index (0 = core, 1-6 = elemental, 7-14 = octant).
     * Used for spiral position calculations.
     */
    public int getArmIndex() {
        return armIndex;
    }

    /**
     * Checks if this is the central core region.
     */
    public boolean isCore() {
        return this == CORE;
    }

    /**
     * Checks if this is any arm (not core).
     */
    public boolean isArm() {
        return this != CORE;
    }

    /**
     * Checks if this is an octant arm (diagonal direction, armIndex 7-14).
     */
    public boolean isOctant() {
        return armIndex >= 7;
    }

    /**
     * Gets the UI file name for this region.
     * Example: FIRE -> "TrailOfOrbis_SkillTreeFIRE.ui"
     */
    @Nonnull
    public String getUiFileName() {
        return "TrailOfOrbis_SkillTree" + name() + ".ui";
    }

    /**
     * Gets adjacent regions for bridge connections.
     *
     * <p>Adjacency topology:
     * <ul>
     *   <li>CORE: Adjacent to all 14 arms</li>
     *   <li>Horizontal elemental arms (FIRE, LIGHTNING, WATER, EARTH): CORE + 2 ring neighbors + VOID + WIND</li>
     *   <li>Vertical elemental arms (VOID, WIND): CORE + all 4 horizontal arms</li>
     *   <li>Octant arms: CORE + 3 neighboring elemental arms</li>
     * </ul>
     */
    @Nonnull
    public SkillTreeRegion[] getAdjacentRegions() {
        return switch (this) {
            case CORE -> new SkillTreeRegion[]{FIRE, WATER, LIGHTNING, EARTH, VOID, WIND,
                HAVOC, JUGGERNAUT, STRIKER, WARDEN, WARLOCK, LICH, TEMPEST, SENTINEL};
            case FIRE -> new SkillTreeRegion[]{CORE, LIGHTNING, EARTH, VOID, WIND};
            case LIGHTNING -> new SkillTreeRegion[]{CORE, FIRE, WATER, VOID, WIND};
            case WATER -> new SkillTreeRegion[]{CORE, LIGHTNING, EARTH, VOID, WIND};
            case EARTH -> new SkillTreeRegion[]{CORE, FIRE, WATER, VOID, WIND};
            case VOID -> new SkillTreeRegion[]{CORE, FIRE, LIGHTNING, WATER, EARTH};
            case WIND -> new SkillTreeRegion[]{CORE, FIRE, LIGHTNING, WATER, EARTH};
            // Octant arms: CORE + 3 neighboring elemental arms (from direction vector components)
            case HAVOC -> new SkillTreeRegion[]{CORE, FIRE, VOID, LIGHTNING};
            case JUGGERNAUT -> new SkillTreeRegion[]{CORE, FIRE, VOID, EARTH};
            case STRIKER -> new SkillTreeRegion[]{CORE, FIRE, WIND, LIGHTNING};
            case WARDEN -> new SkillTreeRegion[]{CORE, FIRE, WIND, EARTH};
            case WARLOCK -> new SkillTreeRegion[]{CORE, WATER, VOID, LIGHTNING};
            case LICH -> new SkillTreeRegion[]{CORE, WATER, VOID, EARTH};
            case TEMPEST -> new SkillTreeRegion[]{CORE, WATER, WIND, LIGHTNING};
            case SENTINEL -> new SkillTreeRegion[]{CORE, WATER, WIND, EARTH};
        };
    }

    /**
     * Gets the clockwise neighboring arm in the horizontal ring.
     * Returns null for CORE, VOID, WIND, and all octant arms (not in the horizontal ring).
     */
    @Nullable
    public SkillTreeRegion getClockwiseNeighbor() {
        return switch (this) {
            case CORE, VOID, WIND,
                 HAVOC, JUGGERNAUT, STRIKER, WARDEN, WARLOCK, LICH, TEMPEST, SENTINEL -> null;
            case FIRE -> LIGHTNING;
            case LIGHTNING -> WATER;
            case WATER -> EARTH;
            case EARTH -> FIRE;
        };
    }

    /**
     * Gets the counter-clockwise neighboring arm in the horizontal ring.
     * Returns null for CORE, VOID, WIND, and all octant arms (not in the horizontal ring).
     */
    @Nullable
    public SkillTreeRegion getCounterClockwiseNeighbor() {
        return switch (this) {
            case CORE, VOID, WIND,
                 HAVOC, JUGGERNAUT, STRIKER, WARDEN, WARLOCK, LICH, TEMPEST, SENTINEL -> null;
            case FIRE -> EARTH;
            case LIGHTNING -> FIRE;
            case WATER -> LIGHTNING;
            case EARTH -> WATER;
        };
    }

    /**
     * Gets the opposite arm across the galaxy.
     * Returns null for CORE and octant arms.
     */
    @Nullable
    public SkillTreeRegion getOppositeArm() {
        return switch (this) {
            case CORE,
                 HAVOC, JUGGERNAUT, STRIKER, WARDEN, WARLOCK, LICH, TEMPEST, SENTINEL -> null;
            case FIRE -> WATER;      // Fire <-> Water (offense vs arcane)
            case WATER -> FIRE;
            case LIGHTNING -> EARTH;  // Lightning <-> Earth (speed vs defense)
            case EARTH -> LIGHTNING;
            case VOID -> WIND;       // Void <-> Wind (darkness vs freedom)
            case WIND -> VOID;
        };
    }

    /**
     * Checks if this region is adjacent to another region.
     */
    public boolean isAdjacentTo(@Nonnull SkillTreeRegion other) {
        for (SkillTreeRegion adjacent : getAdjacentRegions()) {
            if (adjacent == other) return true;
        }
        return false;
    }

    /**
     * Parses a region from a string (case-insensitive).
     * Returns CORE if not found.
     *
     * <p>Also handles legacy region names:
     * <ul>
     *   <li>STR, STRENGTH -> FIRE</li>
     *   <li>DEX, DEXTERITY -> WATER</li>
     *   <li>INT, INTELLIGENCE -> WIND</li>
     *   <li>VIT, VITALITY -> EARTH</li>
     *   <li>ICE, COLD -> WATER</li>
     *   <li>NATURE -> EARTH</li>
     *   <li>CHAOS -> VOID</li>
     * </ul>
     */
    @Nonnull
    public static SkillTreeRegion fromString(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return CORE;
        }

        String upper = value.toUpperCase().trim();

        // Try direct match first
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException ignored) {
            // Fall through to legacy mapping
        }

        // Handle legacy region names (for backward compatibility with configs)
        return switch (upper) {
            case "STR", "STRENGTH" -> FIRE;
            case "DEX", "DEXTERITY" -> WATER;
            case "INT", "INTELLIGENCE" -> WIND;
            case "VIT", "VITALITY" -> EARTH;
            case "ICE", "COLD" -> WATER;
            case "NATURE" -> EARTH;
            case "CHAOS" -> VOID;
            default -> {
                // Try matching by display name
                for (SkillTreeRegion region : values()) {
                    if (region.displayName.equalsIgnoreCase(value)) {
                        yield region;
                    }
                }
                yield CORE;
            }
        };
    }

    /**
     * Gets the arm by its index (1-14).
     * Returns CORE if index is 0 or out of range.
     */
    @Nonnull
    public static SkillTreeRegion fromArmIndex(int index) {
        for (SkillTreeRegion region : values()) {
            if (region.armIndex == index) {
                return region;
            }
        }
        return CORE;
    }

    /**
     * Gets all arms (excluding CORE) — all 14 elemental + octant arms.
     */
    @Nonnull
    public static SkillTreeRegion[] getArms() {
        return new SkillTreeRegion[]{
            FIRE, WATER, LIGHTNING, EARTH, VOID, WIND,
            HAVOC, JUGGERNAUT, STRIKER, WARDEN, WARLOCK, LICH, TEMPEST, SENTINEL
        };
    }

    /**
     * Gets only the 6 elemental arms (cardinal directions).
     */
    @Nonnull
    public static SkillTreeRegion[] getElementalArms() {
        return new SkillTreeRegion[]{FIRE, WATER, LIGHTNING, EARTH, VOID, WIND};
    }

    // ═══════════════════════════════════════════════════════════════════
    // VISUAL ITEMS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the gem item ID for connection lights in this arm.
     * Uses gems instead of crystals for proper lighting effect.
     * Each arm uses its own uniquely-colored gem.
     */
    @Nonnull
    public String getCrystalItem() {
        // All regions use custom Rock_Gem_[Region] items (no vanilla dependency)
        return "Rock_Gem_" + displayName;
    }

    /**
     * Gets the medium crystal item ID for notable nodes in this arm.
     * Each arm uses its own uniquely-colored crystal.
     */
    @Nonnull
    public String getMediumCrystalItem() {
        // All regions use custom Rock_Crystal_[Region]_Medium items (no vanilla dependency)
        return "Rock_Crystal_" + displayName + "_Medium";
    }

    /**
     * Gets the gem item ID for keystone nodes in this arm.
     * Each arm uses its own uniquely-colored gem.
     */
    @Nonnull
    public String getKeystoneGemItem() {
        // All regions use custom Rock_Gem_[Region] items (no vanilla dependency)
        return "Rock_Gem_" + displayName;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE-BASED ITEMS (with controllable light levels)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the light level suffix based on node state.
     * <ul>
     *   <li>LOCKED: 0% light (dark)</li>
     *   <li>AVAILABLE: 50% light (medium glow)</li>
     *   <li>ALLOCATED: 100% light (full glow)</li>
     * </ul>
     */
    @Nonnull
    private static String getLightSuffix(@Nonnull NodeState state) {
        return switch (state) {
            case LOCKED -> "_Light0";
            case AVAILABLE -> "_Light50";
            case ALLOCATED -> "_Light100";
        };
    }

    /**
     * Gets the essence item ID for basic nodes with appropriate light level.
     *
     * <p>Uses custom items from TrailOfOrbis_Realms mod with darkened textures:
     * <ul>
     *   <li>LOCKED: _Light0 suffix (15% brightness texture)</li>
     *   <li>AVAILABLE: _Light50 suffix (50% brightness texture)</li>
     *   <li>ALLOCATED: _Light100 suffix (100% brightness texture)</li>
     * </ul>
     *
     * <p>Each arm uses its own uniquely-colored essence.
     */
    @Nonnull
    public String getEssenceItemForState(@Nonnull NodeState state) {
        // All regions use properly-named Ingredient_[Region]_Essence items
        return "Ingredient_" + displayName + "_Essence" + getLightSuffix(state);
    }

    /**
     * Gets the medium crystal item ID for notable nodes with appropriate light level.
     * Uses custom items from TrailOfOrbis_Realms mod with variable glow.
     */
    @Nonnull
    public String getMediumCrystalItemForState(@Nonnull NodeState state) {
        // All regions use custom Rock_Crystal_[Region]_Medium items
        return "Rock_Crystal_" + displayName + "_Medium" + getLightSuffix(state);
    }

    /**
     * Gets the gem item ID for keystone nodes with appropriate light level.
     * Uses custom items from TrailOfOrbis_Realms mod with variable glow.
     */
    @Nonnull
    public String getKeystoneGemItemForState(@Nonnull NodeState state) {
        // All regions use custom Rock_Gem_[Region] items
        return "Rock_Gem_" + displayName + getLightSuffix(state);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPUTED COLORS (derived from themeColor via OKLCH)
    // ═══════════════════════════════════════════════════════════════════
    // All variant colors are computed from the base themeColor by adjusting
    // lightness in OKLCH space. This guarantees perceptual uniformity:
    // changing brightness never shifts the perceived hue.

    /** OKLCH lightness for allocated node glow (bright, prominent). */
    private static final double L_ALLOCATED = 0.85;
    /** OKLCH lightness for notable node glow (brighter than allocated). */
    private static final double L_NOTABLE = 0.90;
    /** OKLCH lightness for keystone node glow (deeper, intense). */
    private static final double L_KEYSTONE = 0.65;

    /**
     * Gets the allocated node color for this region.
     * Computed from theme color at bright OKLCH lightness.
     */
    @Nonnull
    public String getAllocatedColor() {
        int base = OklchColorUtil.fromHexString(themeColor);
        int adjusted = OklchColorUtil.adjustLightness(base, L_ALLOCATED);
        return OklchColorUtil.toHexString(adjusted) + "(0.9)";
    }

    /**
     * Gets the notable node color for this region (brightened theme).
     * Computed from theme color at very bright OKLCH lightness.
     */
    @Nonnull
    public String getNotableColor() {
        int base = OklchColorUtil.fromHexString(themeColor);
        int adjusted = OklchColorUtil.adjustLightness(base, L_NOTABLE);
        return OklchColorUtil.toHexString(adjusted) + "(0.95)";
    }

    /**
     * Gets the keystone node color for this region (darkened theme).
     * Computed from theme color at deeper OKLCH lightness.
     */
    @Nonnull
    public String getKeystoneColor() {
        int base = OklchColorUtil.fromHexString(themeColor);
        int adjusted = OklchColorUtil.adjustLightness(base, L_KEYSTONE);
        return OklchColorUtil.toHexString(adjusted) + "(0.95)";
    }

    // ═══════════════════════════════════════════════════════════════════
    // DYNAMIC LIGHT RGB
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the red component of this region's theme color (0-255).
     */
    public byte getLightRed() {
        return (byte) Integer.parseInt(themeColor.substring(1, 3), 16);
    }

    /**
     * Gets the green component of this region's theme color (0-255).
     */
    public byte getLightGreen() {
        return (byte) Integer.parseInt(themeColor.substring(3, 5), 16);
    }

    /**
     * Gets the blue component of this region's theme color (0-255).
     */
    public byte getLightBlue() {
        return (byte) Integer.parseInt(themeColor.substring(5, 7), 16);
    }
}
