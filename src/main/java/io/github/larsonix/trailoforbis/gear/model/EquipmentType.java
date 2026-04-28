package io.github.larsonix.trailoforbis.gear.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Unified equipment classification combining weapon types and armor slot+material.
 *
 * <p>Used by the modifier pool to determine which stat modifiers can roll on gear.
 * Each equipment type has a distinct identity that shapes its stat profile:
 *
 * <h3>Weapons</h3>
 * <ul>
 *   <li><b>Melee (swords, axes, maces)</b>: Physical damage, attack speed, crit</li>
 *   <li><b>Daggers</b>: Crit chance, attack speed, life steal (no % phys damage)</li>
 *   <li><b>Ranged (bows, crossbows)</b>: Projectile damage, accuracy</li>
 *   <li><b>Magic (staves, wands)</b>: Spell damage, elemental damage (no physical)</li>
 *   <li><b>Shields</b>: Block chance, armor, stability (no offensive stats)</li>
 * </ul>
 *
 * <h3>Armor</h3>
 * <ul>
 *   <li><b>Cloth</b>: Mana, spell damage, elemental resistances</li>
 *   <li><b>Leather</b>: Evasion, stamina, speed, flexibility</li>
 *   <li><b>Plate</b>: Health, armor, knockback resistance</li>
 *   <li><b>Slot modifiers</b>: Legs get movement stats (Hytale has no feet slot)</li>
 * </ul>
 *
 * @see WeaponType
 * @see ArmorMaterial
 */
public enum EquipmentType {

    // =========================================================================
    // WEAPONS - Melee (One-Handed)
    // =========================================================================
    SWORD(Category.WEAPON, WeaponType.SWORD, null, null),
    DAGGER(Category.WEAPON, WeaponType.DAGGER, null, null),
    AXE(Category.WEAPON, WeaponType.AXE, null, null),
    MACE(Category.WEAPON, WeaponType.MACE, null, null),
    CLAWS(Category.WEAPON, WeaponType.CLAWS, null, null),
    CLUB(Category.WEAPON, WeaponType.CLUB, null, null),

    // =========================================================================
    // WEAPONS - Melee (Two-Handed)
    // =========================================================================
    LONGSWORD(Category.WEAPON, WeaponType.LONGSWORD, null, null),
    BATTLEAXE(Category.WEAPON, WeaponType.BATTLEAXE, null, null),
    SPEAR(Category.WEAPON, WeaponType.SPEAR, null, null),

    // =========================================================================
    // WEAPONS - Ranged
    // =========================================================================
    SHORTBOW(Category.WEAPON, WeaponType.SHORTBOW, null, null),
    CROSSBOW(Category.WEAPON, WeaponType.CROSSBOW, null, null),
    BLOWGUN(Category.WEAPON, WeaponType.BLOWGUN, null, null),

    // =========================================================================
    // WEAPONS - Thrown
    // =========================================================================
    BOMB(Category.WEAPON, WeaponType.BOMB, null, null),
    DART(Category.WEAPON, WeaponType.DART, null, null),
    KUNAI(Category.WEAPON, WeaponType.KUNAI, null, null),

    // =========================================================================
    // WEAPONS - Magic
    // =========================================================================
    STAFF(Category.WEAPON, WeaponType.STAFF, null, null),
    WAND(Category.WEAPON, WeaponType.WAND, null, null),
    SPELLBOOK(Category.WEAPON, WeaponType.SPELLBOOK, null, null),

    // =========================================================================
    // OFFHAND
    // =========================================================================
    SHIELD(Category.OFFHAND, WeaponType.SHIELD, null, null),

    // =========================================================================
    // ARMOR - Cloth (magic-focused)
    // =========================================================================
    CLOTH_HEAD(Category.ARMOR, null, ArmorMaterial.CLOTH, ArmorSlot.HEAD),
    CLOTH_CHEST(Category.ARMOR, null, ArmorMaterial.CLOTH, ArmorSlot.CHEST),
    CLOTH_LEGS(Category.ARMOR, null, ArmorMaterial.CLOTH, ArmorSlot.LEGS),
    CLOTH_HANDS(Category.ARMOR, null, ArmorMaterial.CLOTH, ArmorSlot.HANDS),

    // =========================================================================
    // ARMOR - Leather (agility-focused)
    // =========================================================================
    LEATHER_HEAD(Category.ARMOR, null, ArmorMaterial.LEATHER, ArmorSlot.HEAD),
    LEATHER_CHEST(Category.ARMOR, null, ArmorMaterial.LEATHER, ArmorSlot.CHEST),
    LEATHER_LEGS(Category.ARMOR, null, ArmorMaterial.LEATHER, ArmorSlot.LEGS),
    LEATHER_HANDS(Category.ARMOR, null, ArmorMaterial.LEATHER, ArmorSlot.HANDS),

    // =========================================================================
    // ARMOR - Plate (defense-focused)
    // =========================================================================
    PLATE_HEAD(Category.ARMOR, null, ArmorMaterial.PLATE, ArmorSlot.HEAD),
    PLATE_CHEST(Category.ARMOR, null, ArmorMaterial.PLATE, ArmorSlot.CHEST),
    PLATE_LEGS(Category.ARMOR, null, ArmorMaterial.PLATE, ArmorSlot.LEGS),
    PLATE_HANDS(Category.ARMOR, null, ArmorMaterial.PLATE, ArmorSlot.HANDS),

    // =========================================================================
    // ARMOR - Wood (hybrid)
    // =========================================================================
    WOOD_HEAD(Category.ARMOR, null, ArmorMaterial.WOOD, ArmorSlot.HEAD),
    WOOD_CHEST(Category.ARMOR, null, ArmorMaterial.WOOD, ArmorSlot.CHEST),
    WOOD_LEGS(Category.ARMOR, null, ArmorMaterial.WOOD, ArmorSlot.LEGS),
    WOOD_HANDS(Category.ARMOR, null, ArmorMaterial.WOOD, ArmorSlot.HANDS),

    // =========================================================================
    // ARMOR - Special (full access)
    // =========================================================================
    SPECIAL_HEAD(Category.ARMOR, null, ArmorMaterial.SPECIAL, ArmorSlot.HEAD),
    SPECIAL_CHEST(Category.ARMOR, null, ArmorMaterial.SPECIAL, ArmorSlot.CHEST),
    SPECIAL_LEGS(Category.ARMOR, null, ArmorMaterial.SPECIAL, ArmorSlot.LEGS),
    SPECIAL_HANDS(Category.ARMOR, null, ArmorMaterial.SPECIAL, ArmorSlot.HANDS),

    // =========================================================================
    // FALLBACK
    // =========================================================================
    UNKNOWN_WEAPON(Category.WEAPON, WeaponType.UNKNOWN, null, null),
    UNKNOWN_ARMOR(Category.ARMOR, null, ArmorMaterial.SPECIAL, null);

    private final Category category;
    private final WeaponType weaponType;
    private final ArmorMaterial armorMaterial;
    private final ArmorSlot armorSlot;

    EquipmentType(
            Category category,
            @Nullable WeaponType weaponType,
            @Nullable ArmorMaterial armorMaterial,
            @Nullable ArmorSlot armorSlot
    ) {
        this.category = category;
        this.weaponType = weaponType;
        this.armorMaterial = armorMaterial;
        this.armorSlot = armorSlot;
    }

    /**
     * The category of this equipment (weapon, armor, offhand).
     */
    public Category getCategory() {
        return category;
    }

    /**
     * The weapon type (for weapons/offhand only).
     */
    @Nullable
    public WeaponType getWeaponType() {
        return weaponType;
    }

    /**
     * The armor material (for armor only).
     */
    @Nullable
    public ArmorMaterial getArmorMaterial() {
        return armorMaterial;
    }

    /**
     * The armor slot (for armor only).
     */
    @Nullable
    public ArmorSlot getArmorSlot() {
        return armorSlot;
    }

    /**
     * Whether this is a weapon type.
     */
    public boolean isWeapon() {
        return category == Category.WEAPON;
    }

    /**
     * Whether this is an armor type.
     */
    public boolean isArmor() {
        return category == Category.ARMOR;
    }

    /**
     * Whether this is an offhand type (shield).
     */
    public boolean isOffhand() {
        return category == Category.OFFHAND;
    }

    /**
     * Returns the config key for this equipment type.
     *
     * <p>Used to look up allowed modifiers in equipment-stats.yml.
     *
     * @return Config key (e.g., "sword", "dagger", "cloth_head", "plate_chest")
     */
    @Nonnull
    public String getConfigKey() {
        return name().toLowerCase();
    }

    /**
     * Returns the slot string for slot-based filtering.
     *
     * <p>Maps to the existing slot system: weapon, head, chest, legs, hands, shield.
     *
     * @return Slot string for modifier filtering
     */
    @Nonnull
    public String getSlot() {
        return switch (category) {
            case WEAPON -> "weapon";
            case OFFHAND -> "shield";
            case ARMOR -> armorSlot != null ? armorSlot.getSlotName() : "chest";
        };
    }

    /**
     * Resolves equipment type from weapon type and armor info.
     *
     * @param weaponType The weapon type (for weapons)
     * @param armorMaterial The armor material (for armor)
     * @param armorSlot The armor slot (for armor)
     * @return The matching equipment type
     */
    @Nonnull
    public static EquipmentType resolve(
            @Nullable WeaponType weaponType,
            @Nullable ArmorMaterial armorMaterial,
            @Nullable ArmorSlot armorSlot
    ) {
        // Weapon resolution
        if (weaponType != null && weaponType != WeaponType.UNKNOWN) {
            return switch (weaponType) {
                case SWORD -> SWORD;
                case DAGGER -> DAGGER;
                case AXE -> AXE;
                case MACE -> MACE;
                case CLAWS -> CLAWS;
                case CLUB -> CLUB;
                case LONGSWORD -> LONGSWORD;
                case BATTLEAXE -> BATTLEAXE;
                case SPEAR -> SPEAR;
                case SHORTBOW -> SHORTBOW;
                case CROSSBOW -> CROSSBOW;
                case BLOWGUN -> BLOWGUN;
                case BOMB -> BOMB;
                case DART -> DART;
                case KUNAI -> KUNAI;
                case STAFF -> STAFF;
                case WAND -> WAND;
                case SPELLBOOK -> SPELLBOOK;
                case SHIELD -> SHIELD;
                case UNKNOWN -> UNKNOWN_WEAPON;
            };
        }

        // Armor resolution
        if (armorMaterial != null && armorSlot != null) {
            return switch (armorMaterial) {
                case CLOTH -> switch (armorSlot) {
                    case HEAD -> CLOTH_HEAD;
                    case CHEST -> CLOTH_CHEST;
                    case LEGS -> CLOTH_LEGS;
                    case HANDS -> CLOTH_HANDS;
                };
                case LEATHER -> switch (armorSlot) {
                    case HEAD -> LEATHER_HEAD;
                    case CHEST -> LEATHER_CHEST;
                    case LEGS -> LEATHER_LEGS;
                    case HANDS -> LEATHER_HANDS;
                };
                case PLATE -> switch (armorSlot) {
                    case HEAD -> PLATE_HEAD;
                    case CHEST -> PLATE_CHEST;
                    case LEGS -> PLATE_LEGS;
                    case HANDS -> PLATE_HANDS;
                };
                case WOOD -> switch (armorSlot) {
                    case HEAD -> WOOD_HEAD;
                    case CHEST -> WOOD_CHEST;
                    case LEGS -> WOOD_LEGS;
                    case HANDS -> WOOD_HANDS;
                };
                case SPECIAL -> switch (armorSlot) {
                    case HEAD -> SPECIAL_HEAD;
                    case CHEST -> SPECIAL_CHEST;
                    case LEGS -> SPECIAL_LEGS;
                    case HANDS -> SPECIAL_HANDS;
                };
            };
        }

        return UNKNOWN_ARMOR;
    }

    /**
     * Equipment categories.
     */
    public enum Category {
        /** Weapon items (main hand) */
        WEAPON,
        /** Armor items */
        ARMOR,
        /** Offhand items (shields) */
        OFFHAND
    }

    /**
     * Armor slots in Hytale.
     *
     * <p><b>IMPORTANT</b>: Hytale has only 4 armor slots - there is NO feet slot!
     * Movement stats should go on LEGS (the closest equivalent to boots).
     */
    public enum ArmorSlot {
        HEAD("head"),
        CHEST("chest"),
        LEGS("legs"),
        HANDS("hands");

        private final String slotName;

        ArmorSlot(String slotName) {
            this.slotName = slotName;
        }

        public String getSlotName() {
            return slotName;
        }

        /**
         * Parses an armor slot from a slot name string.
         */
        @Nonnull
        public static Optional<ArmorSlot> fromSlotName(@Nullable String slotName) {
            if (slotName == null) {
                return Optional.empty();
            }
            return switch (slotName.toLowerCase()) {
                case "head" -> Optional.of(HEAD);
                case "chest" -> Optional.of(CHEST);
                case "legs" -> Optional.of(LEGS);
                case "hands" -> Optional.of(HANDS);
                default -> Optional.empty();
            };
        }
    }
}
