package io.github.larsonix.trailoforbis.stones.handler;

import io.github.larsonix.trailoforbis.gear.generation.GearModifierRoller;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDamageCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDefenseCalculator;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.stones.StoneActionResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Handler for gear-specific stone operations.
 *
 * <p>Wraps {@link GearModifierRoller} and {@link ImplicitDamageCalculator}
 * behind the {@link ItemTypeHandler} interface, translating each operation
 * into the roller's API (which takes {@code (GearData, slot, Random)}).
 *
 * <p>The slot is resolved from the item's {@code baseItemId} so that modifier
 * pool filtering is correct for all equipment types (weapons, shields, armor).
 */
public class GearStoneHandler implements ItemTypeHandler<GearData> {

    private static final String FALLBACK_SLOT = "weapon";

    private final GearModifierRoller roller;
    @Nullable
    private final ImplicitDamageCalculator implicitCalculator;
    @Nullable
    private final ImplicitDefenseCalculator implicitDefenseCalculator;

    /**
     * Creates a gear stone handler.
     *
     * @param roller The gear modifier roller for modifier operations
     * @param implicitCalculator Calculator for implicit damage rerolls (nullable)
     * @param implicitDefenseCalculator Calculator for implicit defense rerolls (nullable)
     */
    public GearStoneHandler(
            @Nonnull GearModifierRoller roller,
            @Nullable ImplicitDamageCalculator implicitCalculator,
            @Nullable ImplicitDefenseCalculator implicitDefenseCalculator) {
        this.roller = Objects.requireNonNull(roller, "roller cannot be null");
        this.implicitCalculator = implicitCalculator;
        this.implicitDefenseCalculator = implicitDefenseCalculator;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER ROLLER OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public StoneActionResult rerollValues(@Nonnull GearData item, @Nonnull Random random) {
        GearData result = roller.rerollValues(item, resolveSlot(item), random);
        return StoneActionResult.success(result, "Modifier values rerolled.");
    }

    @Override
    @Nonnull
    public StoneActionResult rerollOneValue(@Nonnull GearData item, @Nonnull Random random) {
        GearData result = roller.rerollOneValue(item, resolveSlot(item), random);
        return StoneActionResult.success(result, "Rerolled one modifier value.");
    }

    @Override
    @Nonnull
    public StoneActionResult rerollTypes(@Nonnull GearData item, @Nonnull Random random) {
        var ctx = resolveContext(item);
        GearData result = roller.rerollTypes(item, ctx.slot(), ctx.equipmentType(), random);
        return StoneActionResult.success(result, "Modifiers rerolled.");
    }

    @Override
    @Nonnull
    public StoneActionResult addModifier(@Nonnull GearData item, @Nonnull Random random) {
        var ctx = resolveContext(item);
        GearData result = roller.addModifier(item, ctx.slot(), ctx.equipmentType(), random);
        if (result.modifierCount() == item.modifierCount()) {
            return StoneActionResult.failure("No compatible modifiers available.");
        }
        return StoneActionResult.success(result, "Added a new modifier.");
    }

    @Override
    @Nonnull
    public StoneActionResult removeModifier(@Nonnull GearData item, @Nonnull Random random) {
        GearData result = roller.removeModifier(item, random);
        return StoneActionResult.success(result, "Removed a random modifier.");
    }

    @Override
    @Nonnull
    public StoneActionResult clearUnlockedModifiers(@Nonnull GearData item) {
        int originalCount = item.modifierCount();
        GearData result = roller.clearUnlockedModifiers(item);
        int removed = originalCount - result.modifierCount();
        return StoneActionResult.success(result,
            "Removed " + removed + " modifier" + (removed != 1 ? "s" : "") + ".");
    }

    @Override
    @Nonnull
    public StoneActionResult transmute(@Nonnull GearData item, @Nonnull Random random) {
        var ctx = resolveContext(item);
        GearData result = roller.transmute(item, ctx.slot(), ctx.equipmentType(), random);
        if (result.modifierCount() < item.modifierCount()) {
            return StoneActionResult.success(result,
                "Removed a modifier but no replacement available.");
        }
        return StoneActionResult.success(result,
            "Swapped: removed one modifier and added a new one.");
    }

    @Override
    @Nonnull
    public StoneActionResult fillModifiers(@Nonnull GearData item, @Nonnull Random random) {
        GearData result = item;
        var ctx = resolveContext(item);
        String slot = ctx.slot();
        EquipmentType equipType = ctx.equipmentType();
        int startCount = result.modifierCount();
        while (result.canAddModifier()) {
            GearData next = roller.addModifier(result, slot, equipType, random);
            if (next.modifierCount() == result.modifierCount()) {
                break; // No compatible modifiers available
            }
            result = next;
        }
        int added = result.modifierCount() - startCount;
        if (added == 0) {
            return StoneActionResult.failure("No compatible modifiers available.");
        }
        return StoneActionResult.success(result,
            "Filled " + added + " modifier slot" + (added != 1 ? "s" : "") + ".");
    }

    @Override
    @Nonnull
    public StoneActionResult lockModifier(@Nonnull GearData item, int combinedIndex) {
        String modName = item.modifiers().get(combinedIndex).displayName();
        GearData result = roller.lockModifierAt(item, combinedIndex);
        return StoneActionResult.success(result, "Locked: " + modName);
    }

    @Override
    @Nonnull
    public StoneActionResult unlockModifier(@Nonnull GearData item, int combinedIndex) {
        String modName = item.modifiers().get(combinedIndex).displayName();
        GearData result = roller.unlockModifierAt(item, combinedIndex);
        return StoneActionResult.success(result, "Unlocked: " + modName);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLEX MULTI-OUTCOME OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public StoneActionResult corrupt(@Nonnull GearData item, @Nonnull Random random) {
        int roll = random.nextInt(100);

        if (roll < 35) {
            // 35%: Just corrupt, no other change
            GearData result = item.corrupt();
            return StoneActionResult.success(result, "Item corrupted.");
        } else if (roll < 60) {
            // 25%: Reroll quality and corrupt
            int newQuality = 1 + random.nextInt(100);
            GearData result = item.withQuality(newQuality).corrupt();
            return StoneActionResult.success(result,
                "Item corrupted with quality changed to " + newQuality + "% !");
        } else if (roll < 85) {
            // 25%: Add a modifier if possible, then corrupt
            if (item.canAddModifier()) {
                var corruptCtx = resolveContext(item);
                GearData added = roller.addModifier(item, corruptCtx.slot(), corruptCtx.equipmentType(), random);
                GearData result = added.corrupt();
                return StoneActionResult.success(result,
                    "Item corrupted with a new modifier !");
            }
            GearData result = item.corrupt();
            return StoneActionResult.success(result, "Item corrupted.");
        } else {
            // 15%: Upgrade rarity and corrupt
            GearRarity next = getNextRarity(item.rarity());
            if (next != null) {
                GearData result = item.withRarity(next).corrupt();
                return StoneActionResult.success(result,
                    "Item corrupted and upgraded to " + next.getHytaleQualityId() + " !");
            }
            GearData result = item.corrupt();
            return StoneActionResult.success(result, "Item corrupted.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TYPE-SPECIFIC OPERATIONS (gear-only)
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public StoneActionResult rerollImplicit(@Nonnull GearData item, @Nonnull Random random) {
        // Try weapon implicit first
        if (item.hasWeaponImplicit()) {
            WeaponImplicit currentImplicit = item.implicit();

            WeaponImplicit rerolled;
            if (implicitCalculator != null) {
                rerolled = implicitCalculator.reroll(currentImplicit, random);
            } else {
                rerolled = currentImplicit.withRerolledValue(random);
            }

            GearData result = item.withImplicit(rerolled);

            int oldValue = currentImplicit.rolledValueAsInt();
            int newValue = rerolled.rolledValueAsInt();
            String damageType = rerolled.damageTypeDisplayName();

            return StoneActionResult.success(result,
                String.format("Implicit rerolled : %d > %d %s", oldValue, newValue, damageType));
        }

        // Try armor implicit
        if (item.hasArmorImplicit()) {
            ArmorImplicit currentImplicit = item.armorImplicit();

            ArmorImplicit rerolled;
            if (implicitDefenseCalculator != null) {
                rerolled = implicitDefenseCalculator.reroll(currentImplicit, random);
            } else {
                rerolled = currentImplicit.withRerolledValue(random);
            }

            GearData result = item.withArmorImplicit(rerolled);

            int oldValue = currentImplicit.rolledValueAsInt();
            int newValue = rerolled.rolledValueAsInt();
            String defenseType = rerolled.defenseTypeDisplayName();

            return StoneActionResult.success(result,
                String.format("Implicit rerolled : %d > %d %s", oldValue, newValue, defenseType));
        }

        return StoneActionResult.failure("This item has no implicit to reroll.");
    }

    /**
     * Changes level and rescales implicit damage/defense ranges for the new level.
     *
     * <p>Implicits are a function of level — when level changes, the range must
     * be recalculated. The original roll percentile is preserved: a 90th-percentile
     * implicit stays at the 90th percentile of the new level's range. This is a
     * deterministic operation — the {@code random} parameter is unused for implicits.
     *
     * <p>Modifier values are NOT recalculated (they're independent rolls).
     */
    @Override
    @Nonnull
    public StoneActionResult changeLevel(@Nonnull GearData item, int newLevel, @Nonnull Random random) {
        GearData result = item.withLevel(newLevel);

        // Rescale weapon implicit for new level (preserving roll percentile)
        if (result.hasWeaponImplicit() && implicitCalculator != null) {
            String baseItemId = result.getBaseItemId();
            WeaponType weaponType = WeaponType.fromItemIdOrUnknown(baseItemId);
            WeaponImplicit rescaled = implicitCalculator.rescaleForLevel(
                    item.implicit(), weaponType, newLevel);
            result = result.withImplicit(rescaled);
        }

        // Rescale armor implicit for new level (preserving roll percentile)
        if (result.hasArmorImplicit() && implicitDefenseCalculator != null) {
            String baseItemId = result.getBaseItemId();
            ArmorMaterial material = ArmorMaterial.fromItemIdOrSpecial(baseItemId);
            ArmorSlot slot = deriveArmorSlot(baseItemId);
            if (slot != null) {
                ArmorImplicit rescaled = implicitDefenseCalculator.rescaleForLevel(
                        item.armorImplicit(), material, slot, newLevel);
                if (rescaled != null) {
                    result = result.withArmorImplicit(rescaled);
                }
            }
        }

        return StoneActionResult.success(result,
            "Level changed to " + newLevel + ".");
    }

    /**
     * Derives the armor slot from a base item ID.
     *
     * <p>Hytale armor IDs follow the pattern {@code Armor_{Material}_{Slot}},
     * e.g., {@code Armor_Bronze_Chest}, {@code Armor_Cloth_Cotton_Legs}.
     * The slot is always the LAST segment.
     */
    @Nullable
    private ArmorSlot deriveArmorSlot(@Nullable String baseItemId) {
        if (baseItemId == null || !baseItemId.startsWith("Armor_")) {
            return null;
        }
        // Slot is the last underscore-separated segment: Armor_{Material...}_{Slot}
        int lastUnderscore = baseItemId.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore == baseItemId.length() - 1) {
            return null;
        }
        String slotPart = baseItemId.substring(lastUnderscore + 1);
        return ArmorSlot.fromSlotName(slotPart.toLowerCase()).orElse(null);
    }

    /**
     * Gear does not require identification — returns a specific failure message.
     */
    @Override
    @Nonnull
    public StoneActionResult identify(@Nonnull GearData item) {
        return StoneActionResult.failure("Gear does not require identification.");
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    private GearRarity getNextRarity(@Nonnull GearRarity current) {
        return switch (current) {
            case COMMON -> GearRarity.UNCOMMON;
            case UNCOMMON -> GearRarity.RARE;
            case RARE -> GearRarity.EPIC;
            case EPIC -> GearRarity.LEGENDARY;
            case LEGENDARY -> GearRarity.MYTHIC;
            case MYTHIC, UNIQUE -> null;
        };
    }

    /**
     * Resolves the equipment type from the item's base ID for modifier filtering.
     *
     * <p>This ensures stones only roll modifiers that are allowed for the specific
     * equipment type (e.g., spellbooks only get magic modifiers, not physical).
     */
    @Nullable
    private EquipmentType resolveEquipmentType(@Nonnull GearData item) {
        return resolveContext(item).equipmentType;
    }

    /**
     * Resolves the modifier pool slot string from the item's base ID.
     *
     * <p>This ensures stones roll modifiers from the correct pool:
     * weapons from "weapon", shields from "shield", armor from the armor slot name.
     */
    @Nonnull
    private String resolveSlot(@Nonnull GearData item) {
        return resolveContext(item).slot;
    }

    /**
     * Resolves both slot and equipment type from the item's base ID in a single pass.
     * Avoids redundant WeaponType/ArmorMaterial lookups when both are needed.
     */
    @Nonnull
    private ModifierContext resolveContext(@Nonnull GearData item) {
        String baseItemId = item.baseItemId();
        if (baseItemId == null || baseItemId.isEmpty()) {
            return new ModifierContext(FALLBACK_SLOT, null);
        }

        // Weapons (covers spellbooks, shields, staves, bows, etc.)
        Optional<WeaponType> weaponType = WeaponType.fromItemId(baseItemId);
        if (weaponType.isPresent()) {
            EquipmentType equipType = EquipmentType.resolve(weaponType.get(), null, null);
            return new ModifierContext(equipType.getSlot(), equipType);
        }

        // Armor
        Optional<ArmorMaterial> material = ArmorMaterial.fromItemId(baseItemId);
        if (material.isPresent()) {
            ArmorSlot armorSlot = deriveArmorSlot(baseItemId);
            if (armorSlot != null) {
                EquipmentType equipType = EquipmentType.resolve(null, material.get(), armorSlot);
                return new ModifierContext(armorSlot.getSlotName(), equipType);
            }
        }

        return new ModifierContext(FALLBACK_SLOT, null);
    }

    /** Holds resolved slot + equipment type for modifier operations. */
    private record ModifierContext(@Nonnull String slot, @Nullable EquipmentType equipmentType) {}
}
