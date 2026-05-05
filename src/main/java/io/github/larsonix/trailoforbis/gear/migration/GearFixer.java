package io.github.larsonix.trailoforbis.gear.migration;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDamageCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDefenseCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ModifierPool;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ImplicitStatus;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ModifierStatus;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Applies fixes to gear based on {@link GearValidator.ValidationResult}.
 *
 * <p>Reuses existing generation machinery:
 * <ul>
 *   <li>{@link ImplicitDamageCalculator} for rerolling weapon implicits</li>
 *   <li>{@link ImplicitDefenseCalculator} for rerolling armor implicits</li>
 *   <li>{@link ModifierPool} for rolling replacement modifiers</li>
 * </ul>
 *
 * <p>Locked modifiers are NEVER modified — even if they're technically invalid.
 * This respects the player's investment in locking mods with stones.
 */
public final class GearFixer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ModifierConfig modifierConfig;
    private final GearBalanceConfig balanceConfig;
    private final ModifierPool modifierPool;
    private final ImplicitDamageCalculator implicitDamageCalculator;
    private final ImplicitDefenseCalculator implicitDefenseCalculator;
    private final GearValidator validator;

    public GearFixer(
            @Nonnull ModifierConfig modifierConfig,
            @Nonnull GearBalanceConfig balanceConfig,
            @Nonnull ModifierPool modifierPool,
            @Nonnull ImplicitDamageCalculator implicitDamageCalculator,
            @Nonnull ImplicitDefenseCalculator implicitDefenseCalculator,
            @Nonnull GearValidator validator) {
        this.modifierConfig = modifierConfig;
        this.balanceConfig = balanceConfig;
        this.modifierPool = modifierPool;
        this.implicitDamageCalculator = implicitDamageCalculator;
        this.implicitDefenseCalculator = implicitDefenseCalculator;
        this.validator = validator;
    }

    /**
     * Applies fixes to gear based on the validation result.
     *
     * @param gear The original gear data
     * @param result The validation result from GearValidator
     * @param equipmentType The equipment type (null = skip equipment-specific fixes)
     * @param weaponType The weapon type (null = not a weapon)
     * @param slot The slot string for modifier rolling
     * @return Fixed gear data (or original if no changes needed)
     */
    @Nonnull
    public GearData fix(
            @Nonnull GearData gear,
            @Nonnull ValidationResult result,
            @Nullable EquipmentType equipmentType,
            @Nullable WeaponType weaponType,
            @Nullable String slot) {

        if (!result.needsMigration()) {
            return gear;
        }

        Random random = ThreadLocalRandom.current();
        GearData fixed = gear;

        // Note: Implicit fixing is handled by ItemMigrationService.fixImplicitsFromGenerator()
        // which uses GearGenerator as the source of truth. The fixer only handles MODIFIERS.

        // Fix prefixes
        List<GearModifier> fixedPrefixes = fixModifiers(
                gear.prefixes(), result.prefixStatuses(), ModifierType.PREFIX,
                gear, slot, equipmentType, random);
        if (fixedPrefixes != gear.prefixes()) {
            fixed = fixed.withPrefixes(fixedPrefixes);
        }

        // Fix suffixes
        List<GearModifier> fixedSuffixes = fixModifiers(
                gear.suffixes(), result.suffixStatuses(), ModifierType.SUFFIX,
                gear, slot, equipmentType, random);
        if (fixedSuffixes != gear.suffixes()) {
            fixed = fixed.withSuffixes(fixedSuffixes);
        }

        // Enforce modifier count limits (handles rarity max decreases between versions)
        fixed = enforceModifierLimits(fixed);

        return fixed;
    }

    private List<GearModifier> fixModifiers(
            @Nonnull List<GearModifier> originals,
            @Nonnull List<ModifierStatus> statuses,
            @Nonnull ModifierType type,
            @Nonnull GearData gear,
            @Nullable String slot,
            @Nullable EquipmentType equipmentType,
            @Nonnull Random random) {

        // Quick check: all valid?
        boolean allValid = statuses.stream().allMatch(s -> s == ModifierStatus.VALID);
        if (allValid) {
            return originals; // Return same reference (identity check in caller)
        }

        List<GearModifier> result = new ArrayList<>(originals.size());

        // Collect IDs already used (for dedup when rolling replacements)
        Set<String> usedIds = originals.stream()
                .map(GearModifier::id)
                .collect(Collectors.toCollection(HashSet::new));

        for (int i = 0; i < originals.size(); i++) {
            GearModifier original = originals.get(i);
            ModifierStatus status = statuses.get(i);

            // NEVER modify locked modifiers — respect player investment
            if (original.locked()) {
                result.add(original);
                continue;
            }

            switch (status) {
                case VALID -> result.add(original);

                case VALUES_OUT_OF_RANGE -> {
                    // Clamp value to current valid bounds (preserves modifier identity)
                    ModifierDefinition def = modifierConfig.getModifier(original.id(), type).orElse(null);
                    if (def != null) {
                        double clamped = validator.clampToValidRange(
                                original.value(), def, gear.level(), gear.rarity());
                        GearModifier fixed = original.withNewValue(clamped);
                        result.add(fixed);
                        LOGGER.at(Level.INFO).log("Migration: clamped '%s' value %.2f → %.2f (level %d, %s)",
                                original.id(), original.value(), clamped, gear.level(), gear.rarity());
                    } else {
                        result.add(original); // Shouldn't happen (caught by earlier checks)
                    }
                }

                case REMOVED_FROM_GAME, INVALID_FOR_SLOT, INVALID_FOR_EQUIPMENT -> {
                    // Full replacement — roll a new valid modifier
                    GearModifier replacement = rollReplacement(type, gear, slot, equipmentType, usedIds, random);
                    if (replacement != null) {
                        result.add(replacement);
                        usedIds.add(replacement.id());
                        LOGGER.at(Level.INFO).log("Migration: replaced modifier '%s' → '%s' (%s)",
                                original.id(), replacement.id(), status);
                    } else {
                        // No valid replacement available — drop the modifier
                        LOGGER.at(Level.WARNING).log("Migration: dropped modifier '%s' (no valid replacement in pool)",
                                original.id());
                    }
                    usedIds.remove(original.id()); // Allow the old ID to be reused
                }
            }
        }

        return result;
    }

    /**
     * Trims excess modifiers if total count exceeds rarity maximum.
     * Removes unlocked modifiers from the end first. Locked modifiers are always preserved.
     */
    @Nonnull
    private GearData enforceModifierLimits(@Nonnull GearData gear) {
        int maxTotal = gear.rarity().getMaxModifiers();
        int totalCount = gear.prefixes().size() + gear.suffixes().size();

        if (totalCount <= maxTotal) {
            return gear; // Within limits
        }

        int excess = totalCount - maxTotal;
        LOGGER.at(Level.INFO).log("Migration: trimming %d excess modifier(s) for %s (max=%d, have=%d)",
                excess, gear.rarity(), maxTotal, totalCount);

        // Remove unlocked modifiers from end of suffixes first, then prefixes
        List<GearModifier> trimmedSuffixes = trimUnlocked(gear.suffixes(), excess);
        int removed = gear.suffixes().size() - trimmedSuffixes.size();
        excess -= removed;

        List<GearModifier> trimmedPrefixes = gear.prefixes();
        if (excess > 0) {
            trimmedPrefixes = trimUnlocked(gear.prefixes(), excess);
        }

        return gear.withPrefixes(trimmedPrefixes).withSuffixes(trimmedSuffixes);
    }

    /**
     * Removes up to {@code maxRemove} unlocked modifiers from the end of the list.
     */
    @Nonnull
    private List<GearModifier> trimUnlocked(@Nonnull List<GearModifier> modifiers, int maxRemove) {
        if (maxRemove <= 0 || modifiers.isEmpty()) {
            return modifiers;
        }

        List<GearModifier> result = new ArrayList<>(modifiers);
        int removed = 0;

        // Remove from end first (least "important" positionally)
        for (int i = result.size() - 1; i >= 0 && removed < maxRemove; i--) {
            if (!result.get(i).locked()) {
                GearModifier dropped = result.remove(i);
                removed++;
                LOGGER.at(Level.INFO).log("Migration: dropped excess modifier '%s'", dropped.id());
            }
        }

        return result;
    }

    /**
     * Rolls a replacement modifier from the valid pool, excluding already-used IDs.
     */
    @Nullable
    private GearModifier rollReplacement(
            @Nonnull ModifierType type,
            @Nonnull GearData gear,
            @Nullable String slot,
            @Nullable EquipmentType equipmentType,
            @Nonnull Set<String> excludedIds,
            @Nonnull Random random) {

        String effectiveSlot = slot != null ? slot : "weapon"; // Default fallback

        List<GearModifier> rolled = modifierPool.rollModifiersExcluding(
                type, 1, gear.level(), effectiveSlot, gear.rarity(),
                equipmentType, excludedIds, random);

        return rolled.isEmpty() ? null : rolled.get(0);
    }
}
