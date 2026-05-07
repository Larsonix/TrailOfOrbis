package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents a droppable item category in the implicit-driven loot pipeline.
 *
 * <p>The loot pipeline works in three tiers:
 * <ol>
 *   <li><b>Super-category</b>: WEAPON / ARMOR / OFFHAND</li>
 *   <li><b>Category</b>: The specific item type (sword, chest, shield, etc.)</li>
 *   <li><b>Implicit</b>: Rolled from this category's pool — determines the item's identity</li>
 * </ol>
 *
 * <p>For armor, the implicit determines the material (armor→PLATE, evasion→LEATHER, etc.)
 * and therefore the visual skin. For weapons, the implicit determines the damage element.
 */
public final class LootCategory {

    /**
     * Top-level grouping for loot selection.
     */
    public enum SuperCategory {
        WEAPON,
        ARMOR,
        OFFHAND
    }

    private final String id;
    private final SuperCategory superCategory;
    private final String slotString;
    private final double weight;
    private final List<ImplicitEntry> implicitPool;

    // Resolved from id for weapon categories
    @Nullable
    private final WeaponType weaponType;

    // Resolved from slotString for armor categories
    @Nullable
    private final ArmorSlot armorSlot;

    public LootCategory(
            @Nonnull String id,
            @Nonnull SuperCategory superCategory,
            @Nonnull String slotString,
            double weight,
            @Nonnull List<ImplicitEntry> implicitPool
    ) {
        this.id = Objects.requireNonNull(id);
        this.superCategory = Objects.requireNonNull(superCategory);
        this.slotString = Objects.requireNonNull(slotString);
        this.weight = weight;
        this.implicitPool = List.copyOf(implicitPool);

        // Resolve weapon type for weapon/offhand categories
        if (superCategory == SuperCategory.WEAPON || superCategory == SuperCategory.OFFHAND) {
            this.weaponType = resolveWeaponType(id);
            this.armorSlot = null;
        } else {
            this.weaponType = null;
            this.armorSlot = ArmorSlot.fromSlotName(slotString).orElse(null);
        }
    }

    public String id() { return id; }
    public SuperCategory superCategory() { return superCategory; }
    public String slotString() { return slotString; }
    public double weight() { return weight; }
    public List<ImplicitEntry> implicitPool() { return implicitPool; }

    @Nullable
    public WeaponType weaponType() { return weaponType; }

    @Nullable
    public ArmorSlot armorSlot() { return armorSlot; }

    /**
     * Whether this is a weapon category (main-hand weapons).
     */
    public boolean isWeapon() {
        return superCategory == SuperCategory.WEAPON;
    }

    /**
     * Whether this is an armor category (head/chest/legs/hands).
     */
    public boolean isArmor() {
        return superCategory == SuperCategory.ARMOR;
    }

    /**
     * Whether this is an offhand category (shield/spellbook).
     */
    public boolean isOffhand() {
        return superCategory == SuperCategory.OFFHAND;
    }

    /**
     * Resolves a WeaponType from a category id string (e.g., "sword" → SWORD).
     * Tries enum name match first, then item ID pattern match as fallback.
     */
    @Nonnull
    private static WeaponType resolveWeaponType(@Nonnull String categoryId) {
        // Try direct enum name match (e.g., "sword" → "SWORD")
        try {
            return WeaponType.valueOf(categoryId.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        // Fallback: try via item ID pattern (e.g., "shortbow" → fromItemId("Weapon_Shortbow_X"))
        return WeaponType.fromItemIdOrUnknown("Weapon_" + categoryId + "_X");
    }

    @Override
    public String toString() {
        return "LootCategory{" + id + ", super=" + superCategory + ", slot=" + slotString +
                ", weight=" + weight + ", implicits=" + implicitPool.size() + "}";
    }

    // =========================================================================
    // IMPLICIT ENTRY
    // =========================================================================

    /**
     * One entry in an implicit pool — a possible implicit type with its weight.
     *
     * <p>For armor implicits, the entry also carries the resolved {@link ArmorMaterial}
     * that determines which skins to use and which modifier pool to draw from.
     *
     * <p>For weapon implicits, the entry carries an optional {@link ElementType}
     * for elemental damage (null = physical).
     */
    public record ImplicitEntry(
            String implicitType,
            double weight,
            @Nullable ArmorMaterial resolvedMaterial,
            @Nullable ElementType resolvedElement
    ) {

        /**
         * Creates an armor implicit entry.
         */
        public static ImplicitEntry armor(String implicitType, double weight, ArmorMaterial material) {
            return new ImplicitEntry(implicitType, weight, material, null);
        }

        /**
         * Creates a weapon implicit entry with an element.
         */
        public static ImplicitEntry elemental(String implicitType, double weight, ElementType element) {
            return new ImplicitEntry(implicitType, weight, null, element);
        }

        /**
         * Creates a weapon implicit entry for physical damage.
         */
        public static ImplicitEntry physical(double weight) {
            return new ImplicitEntry("physical", weight, null, null);
        }

        /**
         * Creates a fixed implicit entry (weight=100, no material/element context).
         */
        public static ImplicitEntry fixed(String implicitType) {
            return new ImplicitEntry(implicitType, 100.0, null, null);
        }

        /**
         * Whether this is a physical (non-elemental) weapon implicit.
         */
        public boolean isPhysical() {
            return "physical".equals(implicitType) && resolvedElement == null;
        }
    }

    // =========================================================================
    // IMPLICIT ROLL RESULT
    // =========================================================================

    /**
     * Result of rolling an implicit from a category's pool.
     *
     * <p>Contains everything needed to resolve the item's identity:
     * the implicit type, the equipment type for modifier filtering,
     * and the skin lookup parameters.
     */
    public record ImplicitRoll(
            ImplicitEntry entry,
            EquipmentType equipmentType,
            @Nullable ArmorMaterial skinMaterial,
            @Nullable WeaponType skinWeaponType,
            @Nullable ElementType element,
            String slotString
    ) {}
}
