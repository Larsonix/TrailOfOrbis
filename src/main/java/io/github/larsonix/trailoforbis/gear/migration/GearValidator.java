package io.github.larsonix.trailoforbis.gear.migration;

import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ExponentialScalingConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.RarityConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ValueRange;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validates gear data against current generation rules.
 *
 * <p><b>All validation is derived from the same code that generates items.</b>
 * No manual rules — the validator asks the generation systems "would you produce this?"
 *
 * <ul>
 *   <li>Weapon implicit validity → {@link ImplicitDamageCalculator#getValidDamageTypes}</li>
 *   <li>Armor implicit presence → {@link ImplicitDefenseCalculator#shouldHaveArmorImplicit}</li>
 *   <li>Modifier existence → {@link ModifierConfig#getModifier}</li>
 *   <li>Modifier slot validity → {@link ModifierDefinition#isAllowedOnSlot}</li>
 *   <li>Modifier equipment-type validity → {@link EquipmentStatConfig#isPrefixAllowed}/{@link EquipmentStatConfig#isSuffixAllowed}</li>
 *   <li>Modifier value range → mirrors {@code GearModifierRoller.calculateValue()} formula</li>
 * </ul>
 */
public final class GearValidator {

    private final ModifierConfig modifierConfig;
    private final GearBalanceConfig balanceConfig;
    private final EquipmentStatConfig equipmentStatConfig;

    public GearValidator(@Nonnull ModifierConfig modifierConfig,
                         @Nonnull GearBalanceConfig balanceConfig,
                         @Nonnull EquipmentStatConfig equipmentStatConfig) {
        this.modifierConfig = modifierConfig;
        this.balanceConfig = balanceConfig;
        this.equipmentStatConfig = equipmentStatConfig;
    }

    /**
     * Validates gear data against current generation rules.
     *
     * @param gear The gear data to validate
     * @param equipmentType The equipment type (null = lenient)
     * @param weaponType The weapon type (null = not a weapon)
     * @param slot The slot string for modifier filtering
     * @return Validation result describing all issues found
     */
    @Nonnull
    public ValidationResult validate(
            @Nonnull GearData gear,
            @Nullable EquipmentType equipmentType,
            @Nullable WeaponType weaponType,
            @Nullable String slot) {

        ImplicitStatus weaponImplicitStatus = validateWeaponImplicit(gear, weaponType);
        ImplicitStatus armorImplicitStatus = validateArmorImplicit(gear, equipmentType);
        List<ModifierStatus> prefixStatuses = validateModifiers(
                gear.prefixes(), ModifierType.PREFIX, slot, equipmentType, gear.level(), gear.rarity());
        List<ModifierStatus> suffixStatuses = validateModifiers(
                gear.suffixes(), ModifierType.SUFFIX, slot, equipmentType, gear.level(), gear.rarity());

        return new ValidationResult(weaponImplicitStatus, armorImplicitStatus,
                prefixStatuses, suffixStatuses, gear.level(), gear.rarity());
    }

    // ═══════════════════════════════════════════════════════════════════
    // IMPLICIT VALIDATION — derived from ImplicitDamageCalculator + ImplicitDefenseCalculator
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Implicit validation is intentionally minimal here.
     * The REAL implicit correctness check happens in ItemMigrationService by calling
     * GearGenerator's implicit generation and comparing. This avoids duplicating
     * generation rules — the generator IS the source of truth.
     *
     * This validator only checks modifiers (existence, slot, range).
     * Implicit status is always VALID here — ItemMigrationService handles implicits directly.
     */
    private ImplicitStatus validateWeaponImplicit(@Nonnull GearData gear, @Nullable WeaponType weaponType) {
        return ImplicitStatus.VALID;
    }

    private ImplicitStatus validateArmorImplicit(@Nonnull GearData gear, @Nullable EquipmentType equipmentType) {
        return ImplicitStatus.VALID;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER VALIDATION — derived from ModifierConfig + EquipmentStatConfig + value formula
    // ═══════════════════════════════════════════════════════════════════

    private List<ModifierStatus> validateModifiers(
            @Nonnull List<GearModifier> modifiers,
            @Nonnull ModifierType type,
            @Nullable String slot,
            @Nullable EquipmentType equipmentType,
            int itemLevel,
            @Nonnull GearRarity rarity) {

        List<ModifierStatus> statuses = new ArrayList<>(modifiers.size());
        for (GearModifier modifier : modifiers) {
            statuses.add(validateSingleModifier(modifier, type, slot, equipmentType, itemLevel, rarity));
        }
        return statuses;
    }

    private ModifierStatus validateSingleModifier(
            @Nonnull GearModifier modifier,
            @Nonnull ModifierType type,
            @Nullable String slot,
            @Nullable EquipmentType equipmentType,
            int itemLevel,
            @Nonnull GearRarity rarity) {

        // Step 1: Does this modifier exist in current config?
        // (Source: ModifierConfig — the definition registry)
        Optional<ModifierDefinition> defOpt = modifierConfig.getModifier(modifier.id(), type);
        if (defOpt.isEmpty()) {
            return ModifierStatus.REMOVED_FROM_GAME;
        }

        ModifierDefinition def = defOpt.get();

        // Step 2: Is it allowed on this slot?
        // (Source: ModifierDefinition.allowedSlots from gear-modifiers.yml)
        if (slot != null && !def.isAllowedOnSlot(slot)) {
            return ModifierStatus.INVALID_FOR_SLOT;
        }

        // Step 3: Is it allowed on this equipment type?
        // (Source: EquipmentStatConfig — same filter used by ModifierPool during generation)
        if (equipmentType != null) {
            boolean allowed = (type == ModifierType.PREFIX)
                    ? equipmentStatConfig.isPrefixAllowed(equipmentType, modifier.id())
                    : equipmentStatConfig.isSuffixAllowed(equipmentType, modifier.id());
            if (!allowed) {
                return ModifierStatus.INVALID_FOR_EQUIPMENT;
            }
        }

        // Step 4: Is the value within the valid range?
        // (Source: same formula as GearModifierRoller.calculateValue)
        if (!isValueInValidRange(modifier.value(), def, itemLevel, rarity)) {
            return ModifierStatus.VALUES_OUT_OF_RANGE;
        }

        return ModifierStatus.VALID;
    }

    /**
     * Checks if a modifier value is within the valid range.
     * Mirrors the exact formula in {@code GearModifierRoller.calculateValue()}.
     */
    private boolean isValueInValidRange(double value, ModifierDefinition def,
                                         int itemLevel, GearRarity rarity) {
        ValueRange baseRange = def.calculateRange(itemLevel);
        ExponentialScalingConfig expConfig = balanceConfig.exponentialScaling();
        double expMultiplier = expConfig.calculateMultiplier(itemLevel);

        double scaledMin = baseRange.min() * expMultiplier;
        double scaledMax = baseRange.max() * expMultiplier;

        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        double extendedMax = scaledMax * rarityConfig.statMultiplier();

        double rollVariance = balanceConfig.modifierScaling().rollVariance();
        double absoluteMin = scaledMin * (1.0 - rollVariance);
        double absoluteMax = extendedMax * (1.0 + rollVariance);

        // Handle negative-range modifiers (reduction stats)
        if (absoluteMin > absoluteMax) {
            double temp = absoluteMin;
            absoluteMin = absoluteMax;
            absoluteMax = temp;
        }

        return value >= absoluteMin && value <= absoluteMax;
    }

    /**
     * Computes the clamped value for a modifier that's out of range.
     * Used by {@link GearFixer} to bring values back into valid bounds.
     */
    public double clampToValidRange(double currentValue, ModifierDefinition def,
                                     int itemLevel, GearRarity rarity) {
        ValueRange baseRange = def.calculateRange(itemLevel);
        ExponentialScalingConfig expConfig = balanceConfig.exponentialScaling();
        double expMultiplier = expConfig.calculateMultiplier(itemLevel);

        double scaledMin = baseRange.min() * expMultiplier;
        double scaledMax = baseRange.max() * expMultiplier;

        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        double extendedMax = scaledMax * rarityConfig.statMultiplier();

        double rollVariance = balanceConfig.modifierScaling().rollVariance();
        double absoluteMin = scaledMin * (1.0 - rollVariance);
        double absoluteMax = extendedMax * (1.0 + rollVariance);

        if (absoluteMin > absoluteMax) {
            double temp = absoluteMin;
            absoluteMin = absoluteMax;
            absoluteMax = temp;
        }

        return Math.max(absoluteMin, Math.min(absoluteMax, currentValue));
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESULT TYPES
    // ═══════════════════════════════════════════════════════════════════

    public enum ImplicitStatus {
        VALID,
        INVALID_TYPE,
        VALUE_OUT_OF_RANGE
    }

    public enum ModifierStatus {
        /** Modifier passes all checks */
        VALID,
        /** Modifier ID no longer exists in current config */
        REMOVED_FROM_GAME,
        /** Modifier exists but not allowed on this slot (gear-modifiers.yml) */
        INVALID_FOR_SLOT,
        /** Modifier exists but not allowed on this equipment type (equipment-stats.yml) */
        INVALID_FOR_EQUIPMENT,
        /** Modifier valid but value exceeds current range for level + rarity */
        VALUES_OUT_OF_RANGE
    }

    /**
     * Complete validation result for a single gear item.
     */
    public record ValidationResult(
            ImplicitStatus weaponImplicitStatus,
            ImplicitStatus armorImplicitStatus,
            List<ModifierStatus> prefixStatuses,
            List<ModifierStatus> suffixStatuses,
            int itemLevel,
            GearRarity rarity
    ) {
        public boolean needsMigration() {
            if (weaponImplicitStatus != ImplicitStatus.VALID) return true;
            if (armorImplicitStatus != ImplicitStatus.VALID) return true;
            for (ModifierStatus s : prefixStatuses) {
                if (s != ModifierStatus.VALID) return true;
            }
            for (ModifierStatus s : suffixStatuses) {
                if (s != ModifierStatus.VALID) return true;
            }
            return false;
        }
    }
}
