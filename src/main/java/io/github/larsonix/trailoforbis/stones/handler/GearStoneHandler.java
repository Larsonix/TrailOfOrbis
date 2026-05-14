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

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.asset.type.itemsound.config.ItemSoundSet;

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

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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
    public StoneActionResult rerollPrefixTypes(@Nonnull GearData item, @Nonnull Random random) {
        var ctx = resolveContext(item);
        GearData result = roller.rerollPrefixTypes(item, ctx.slot(), ctx.equipmentType(), random);
        return StoneActionResult.success(result, "Prefixes rerolled.");
    }

    @Override
    @Nonnull
    public StoneActionResult rerollSuffixTypes(@Nonnull GearData item, @Nonnull Random random) {
        var ctx = resolveContext(item);
        GearData result = roller.rerollSuffixTypes(item, ctx.slot(), ctx.equipmentType(), random);
        return StoneActionResult.success(result, "Suffixes rerolled.");
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
     * Resolves both slot and equipment type from the item's base ID.
     *
     * <p>Uses a three-layer resolution strategy (matching the generation path):
     * <ol>
     *   <li><b>Name-based</b>: Fast keyword matching on baseItemId
     *       ({@link WeaponType#fromItemId}, {@link ArmorMaterial#fromItemId})</li>
     *   <li><b>Hytale asset API</b>: Definitive lookup via {@code Item.getArmor()}/
     *       {@code Item.getWeapon()} — catches non-standard names (modded/special items)</li>
     *   <li><b>Implicit-based</b>: Uses the item's own implicit data to infer category
     *       — handles legacy items without baseItemId</li>
     * </ol>
     *
     * <p>The last-resort fallback uses an armor-safe default ("chest" slot with
     * {@code UNKNOWN_ARMOR}), never the weapon pool — weapon modifiers on armor
     * is far worse than slightly-wrong armor modifiers.
     */
    @Nonnull
    private ModifierContext resolveContext(@Nonnull GearData item) {
        String baseItemId = item.baseItemId();

        if (baseItemId != null && !baseItemId.isEmpty()) {
            // Layer 1: Name-based resolution (fast, handles standard Weapon_*/Armor_* naming)
            ModifierContext fromName = resolveFromName(baseItemId);
            if (fromName != null) {
                return fromName;
            }

            // Layer 2: Hytale asset API (definitive, handles ALL items including modded)
            ModifierContext fromAsset = resolveFromHytaleAsset(baseItemId);
            if (fromAsset != null) {
                LOGGER.atInfo().log("Stone modifier context resolved via asset API for '%s' → %s/%s",
                        baseItemId, fromAsset.slot(), fromAsset.equipmentType());
                return fromAsset;
            }
        }

        // Layer 3: Implicit-based inference (handles legacy items without baseItemId)
        ModifierContext fromImplicits = resolveFromImplicits(item, baseItemId);
        if (fromImplicits != null) {
            LOGGER.atWarning().log(
                    "Stone modifier context resolved via implicit inference for baseItemId='%s' → %s/%s. "
                    + "Item may be legacy (pre-baseItemId) or have a non-standard ID.",
                    baseItemId, fromImplicits.slot(), fromImplicits.equipmentType());
            return fromImplicits;
        }

        // Last resort: armor-safe default (weapon modifiers on armor is far worse)
        LOGGER.atWarning().log(
                "Stone modifier context unresolvable for baseItemId='%s'. "
                + "Using armor-safe fallback (chest/UNKNOWN_ARMOR). Item may need re-generation.",
                baseItemId);
        return new ModifierContext("chest", EquipmentType.UNKNOWN_ARMOR);
    }

    /**
     * Layer 1: Fast name-based resolution from item ID patterns.
     */
    @Nullable
    private ModifierContext resolveFromName(@Nonnull String baseItemId) {
        // Weapons (covers spellbooks, shields, staves, bows, etc.)
        Optional<WeaponType> weaponType = WeaponType.fromItemId(baseItemId);
        if (weaponType.isPresent()) {
            EquipmentType equipType = EquipmentType.resolve(weaponType.get(), null, null);
            return new ModifierContext(equipType.getSlot(), equipType);
        }

        // Armor (requires "Armor_" prefix)
        Optional<ArmorMaterial> material = ArmorMaterial.fromItemId(baseItemId);
        if (material.isPresent()) {
            ArmorSlot armorSlot = deriveArmorSlot(baseItemId);
            if (armorSlot != null) {
                EquipmentType equipType = EquipmentType.resolve(null, material.get(), armorSlot);
                return new ModifierContext(armorSlot.getSlotName(), equipType);
            }
        }

        return null;
    }

    /**
     * Layer 2: Definitive resolution using Hytale's Item asset system.
     *
     * <p>Reads the actual item definition to determine weapon/armor status.
     * Works for ALL items regardless of naming convention (modded items included).
     * Mirrors {@code EquipmentTypeResolver.resolveFromHytaleAsset()}.
     */
    @Nullable
    private ModifierContext resolveFromHytaleAsset(@Nonnull String baseItemId) {
        try {
            Item item = Item.getAssetMap().getAsset(baseItemId);
            if (item == null || item == Item.UNKNOWN) {
                return null;
            }

            // Check weapon
            ItemWeapon weapon = item.getWeapon();
            if (weapon != null) {
                Optional<WeaponType> weaponType = WeaponType.fromItemId(baseItemId);
                if (weaponType.isPresent() && weaponType.get() != WeaponType.UNKNOWN) {
                    EquipmentType equipType = EquipmentType.resolve(weaponType.get(), null, null);
                    return new ModifierContext(equipType.getSlot(), equipType);
                }
                return new ModifierContext("weapon", EquipmentType.UNKNOWN_WEAPON);
            }

            // Check armor
            ItemArmor armor = item.getArmor();
            if (armor != null) {
                ArmorSlot slot = hytaleSlotToArmorSlot(armor.getArmorSlot());
                ArmorMaterial material = resolveArmorMaterial(item, baseItemId);
                EquipmentType equipType = EquipmentType.resolve(null, material, slot);
                return new ModifierContext(slot.getSlotName(), equipType);
            }

            // Check shield
            if (baseItemId.toLowerCase().contains("shield")) {
                return new ModifierContext("shield", EquipmentType.SHIELD);
            }

        } catch (Exception e) {
            LOGGER.atFine().log("Asset resolution failed for '%s': %s", baseItemId, e.getMessage());
        }
        return null;
    }

    /**
     * Layer 3: Infer equipment category from the item's own implicit data.
     *
     * <p>Handles legacy items without baseItemId by using the implicit's type
     * to determine whether this is a weapon or armor piece.
     */
    @Nullable
    private ModifierContext resolveFromImplicits(@Nonnull GearData item, @Nullable String baseItemId) {
        // Weapon implicit → this is a weapon
        if (item.hasWeaponImplicit()) {
            if (baseItemId != null) {
                Optional<WeaponType> wt = WeaponType.fromItemId(baseItemId);
                if (wt.isPresent()) {
                    EquipmentType equipType = EquipmentType.resolve(wt.get(), null, null);
                    return new ModifierContext(equipType.getSlot(), equipType);
                }
            }
            return new ModifierContext("weapon", EquipmentType.UNKNOWN_WEAPON);
        }

        // Armor implicit → this is armor
        if (item.hasArmorImplicit()) {
            // Try to derive the armor slot from the implicit's defense type
            String defenseType = item.armorImplicit().defenseType();
            if ("block_chance".equals(defenseType)) {
                return new ModifierContext("shield", EquipmentType.SHIELD);
            }
            // Can't determine exact slot from implicit alone — use chest as broadest
            return new ModifierContext("chest", EquipmentType.UNKNOWN_ARMOR);
        }

        return null;
    }

    /**
     * Converts Hytale's ItemArmorSlot enum to our ArmorSlot.
     */
    @Nonnull
    private ArmorSlot hytaleSlotToArmorSlot(@Nonnull ItemArmorSlot hytaleSlot) {
        return switch (hytaleSlot) {
            case Head -> ArmorSlot.HEAD;
            case Chest -> ArmorSlot.CHEST;
            case Legs -> ArmorSlot.LEGS;
            case Hands -> ArmorSlot.HANDS;
        };
    }

    /**
     * Resolves armor material using ItemSoundSet first, then name fallback.
     * Same approach as {@code EquipmentTypeResolver.resolveArmorMaterial()}.
     */
    @Nonnull
    private ArmorMaterial resolveArmorMaterial(@Nonnull Item item, @Nonnull String itemId) {
        try {
            int soundSetIndex = item.getItemSoundSetIndex();
            ItemSoundSet soundSet = ItemSoundSet.getAssetMap().getAsset(soundSetIndex);
            if (soundSet != null) {
                Optional<ArmorMaterial> fromSoundSet = ArmorMaterial.fromSoundSetId(soundSet.getId());
                if (fromSoundSet.isPresent()) {
                    return fromSoundSet.get();
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("SoundSet lookup failed for '%s': %s", itemId, e.getMessage());
        }
        return ArmorMaterial.fromItemIdOrSpecial(itemId);
    }

    /** Holds resolved slot + equipment type for modifier operations. */
    private record ModifierContext(@Nonnull String slot, @Nullable EquipmentType equipmentType) {}
}
