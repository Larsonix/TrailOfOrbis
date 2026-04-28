package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.RarityConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ValueRange;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides stone-compatible modifier operations for gear items.
 *
 * <p>This class mirrors {@link io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierRoller}
 * but operates on gear items with prefix/suffix distinction.
 *
 * <p>All operations respect locked modifiers - they are never modified or removed.
 *
 * <h3>Stone Usage</h3>
 * <table>
 *   <tr><th>Method</th><th>Stone</th></tr>
 *   <tr><td>{@link #rerollValues}</td><td>GAIAS_CALIBRATION</td></tr>
 *   <tr><td>{@link #rerollOneValue}</td><td>EMBER_OF_TUNING</td></tr>
 *   <tr><td>{@link #rerollTypes}</td><td>ALTERVERSE_SHARD</td></tr>
 *   <tr><td>{@link #addModifier}</td><td>GAIAS_GIFT, tier upgrades</td></tr>
 *   <tr><td>{@link #removeModifier}</td><td>EROSION_SHARD</td></tr>
 * </table>
 *
 * @see ModifierPool
 */
public final class GearModifierRoller {

    private final ModifierPool modifierPool;
    private final ModifierConfig modifierConfig;
    private final GearBalanceConfig balanceConfig;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a GearModifierRoller with the given pool.
     *
     * @param modifierPool The modifier pool for rolling new modifiers
     * @param modifierConfig The modifier configuration
     * @param balanceConfig The gear balance configuration
     */
    public GearModifierRoller(
            @Nonnull ModifierPool modifierPool,
            @Nonnull ModifierConfig modifierConfig,
            @Nonnull GearBalanceConfig balanceConfig) {
        this.modifierPool = Objects.requireNonNull(modifierPool, "modifierPool cannot be null");
        this.modifierConfig = Objects.requireNonNull(modifierConfig, "modifierConfig cannot be null");
        this.balanceConfig = Objects.requireNonNull(balanceConfig, "balanceConfig cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // REROLL OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Rerolls all unlocked modifier values while keeping their types.
     *
     * <p>Used by GAIAS_CALIBRATION stone. Locked modifiers are preserved unchanged.
     *
     * @param gear The gear to modify
     * @param slot The gear slot (for value scaling)
     * @param random Random source
     * @return New GearData with rerolled values
     */
    @Nonnull
    public GearData rerollValues(
            @Nonnull GearData gear,
            @Nonnull String slot,
            @Nonnull Random random) {

        List<GearModifier> newPrefixes = gear.prefixes().stream()
            .map(mod -> mod.locked() ? mod : rerollModifierValue(mod, gear.level(), gear.rarity(), random))
            .toList();

        List<GearModifier> newSuffixes = gear.suffixes().stream()
            .map(mod -> mod.locked() ? mod : rerollModifierValue(mod, gear.level(), gear.rarity(), random))
            .toList();

        return gear.withPrefixes(newPrefixes).withSuffixes(newSuffixes);
    }

    /**
     * Rerolls a single random unlocked modifier's value.
     *
     * <p>Used by EMBER_OF_TUNING stone. Selects one unlocked modifier at random
     * and rerolls only that modifier's value.
     *
     * @param gear The gear to modify
     * @param slot The gear slot (for value scaling)
     * @param random Random source
     * @return New GearData with one rerolled value, or unchanged if all locked
     */
    @Nonnull
    public GearData rerollOneValue(
            @Nonnull GearData gear,
            @Nonnull String slot,
            @Nonnull Random random) {

        // Collect all unlocked modifiers with their type and index
        List<ModifierLocation> unlocked = new ArrayList<>();

        for (int i = 0; i < gear.prefixes().size(); i++) {
            GearModifier mod = gear.prefixes().get(i);
            if (!mod.locked()) {
                unlocked.add(new ModifierLocation(ModifierType.PREFIX, i, mod));
            }
        }
        for (int i = 0; i < gear.suffixes().size(); i++) {
            GearModifier mod = gear.suffixes().get(i);
            if (!mod.locked()) {
                unlocked.add(new ModifierLocation(ModifierType.SUFFIX, i, mod));
            }
        }

        if (unlocked.isEmpty()) {
            return gear; // All modifiers are locked
        }

        // Pick random unlocked modifier
        ModifierLocation target = unlocked.get(random.nextInt(unlocked.size()));
        GearModifier rerolled = rerollModifierValue(target.modifier, gear.level(), gear.rarity(), random);

        // Replace at the correct position
        if (target.type == ModifierType.PREFIX) {
            List<GearModifier> newPrefixes = new ArrayList<>(gear.prefixes());
            newPrefixes.set(target.index, rerolled);
            return gear.withPrefixes(newPrefixes);
        } else {
            List<GearModifier> newSuffixes = new ArrayList<>(gear.suffixes());
            newSuffixes.set(target.index, rerolled);
            return gear.withSuffixes(newSuffixes);
        }
    }

    /**
     * Rerolls unlocked modifier types (with new values).
     *
     * <p>Used by ALTERVERSE_SHARD stone. Keeps the same count of prefixes/suffixes
     * but randomizes which modifiers they are. Locked modifiers are preserved.
     *
     * @param gear The gear to modify
     * @param slot The gear slot (for filtering available modifiers)
     * @param random Random source
     * @return New GearData with rerolled modifier types
     */
    @Nonnull
    public GearData rerollTypes(
            @Nonnull GearData gear,
            @Nonnull String slot,
            @Nonnull Random random) {

        // Process prefixes
        List<GearModifier> lockedPrefixes = gear.prefixes().stream()
            .filter(GearModifier::locked)
            .toList();
        int unlockedPrefixCount = gear.prefixes().size() - lockedPrefixes.size();

        // Get IDs to exclude (locked modifiers)
        Set<String> excludedPrefixIds = lockedPrefixes.stream()
            .map(GearModifier::id)
            .collect(Collectors.toSet());

        // Roll new prefixes
        List<GearModifier> newPrefixes = new ArrayList<>(lockedPrefixes);
        if (unlockedPrefixCount > 0) {
            List<GearModifier> rolledPrefixes = rollModifiersExcluding(
                ModifierType.PREFIX, unlockedPrefixCount, gear.level(), slot, gear.rarity(), excludedPrefixIds, random);
            newPrefixes.addAll(rolledPrefixes);
        }

        // Process suffixes
        List<GearModifier> lockedSuffixes = gear.suffixes().stream()
            .filter(GearModifier::locked)
            .toList();
        int unlockedSuffixCount = gear.suffixes().size() - lockedSuffixes.size();

        Set<String> excludedSuffixIds = lockedSuffixes.stream()
            .map(GearModifier::id)
            .collect(Collectors.toSet());

        // Roll new suffixes
        List<GearModifier> newSuffixes = new ArrayList<>(lockedSuffixes);
        if (unlockedSuffixCount > 0) {
            List<GearModifier> rolledSuffixes = rollModifiersExcluding(
                ModifierType.SUFFIX, unlockedSuffixCount, gear.level(), slot, gear.rarity(), excludedSuffixIds, random);
            newSuffixes.addAll(rolledSuffixes);
        }

        return gear.withPrefixes(newPrefixes).withSuffixes(newSuffixes);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADD/REMOVE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Adds a random modifier to the gear.
     *
     * <p>Used by GAIAS_GIFT stone and tier upgrades. Will add either a prefix
     * or suffix based on what has available slots.
     *
     * @param gear The gear to modify
     * @param slot The gear slot (for filtering available modifiers)
     * @param random Random source
     * @return New GearData with added modifier, or unchanged if at max
     */
    @Nonnull
    public GearData addModifier(
            @Nonnull GearData gear,
            @Nonnull String slot,
            @Nonnull Random random) {

        if (!gear.canAddModifier()) {
            return gear; // At maximum modifiers
        }

        // Determine which type to add based on available slots
        boolean canAddPrefix = gear.canAddPrefix();
        boolean canAddSuffix = gear.canAddSuffix();

        ModifierType typeToAdd;
        if (canAddPrefix && canAddSuffix) {
            // Randomly choose between prefix and suffix
            typeToAdd = random.nextBoolean() ? ModifierType.PREFIX : ModifierType.SUFFIX;
        } else if (canAddPrefix) {
            typeToAdd = ModifierType.PREFIX;
        } else if (canAddSuffix) {
            typeToAdd = ModifierType.SUFFIX;
        } else {
            return gear; // Cannot add either type
        }

        // Get existing modifier IDs of this type to exclude
        Set<String> existingIds = (typeToAdd == ModifierType.PREFIX ? gear.prefixes() : gear.suffixes())
            .stream()
            .map(GearModifier::id)
            .collect(Collectors.toSet());

        // Roll a new modifier
        List<GearModifier> rolled = rollModifiersExcluding(
            typeToAdd, 1, gear.level(), slot, gear.rarity(), existingIds, random);

        if (rolled.isEmpty()) {
            return gear; // No available modifiers
        }

        GearModifier newModifier = rolled.get(0);
        return gear.withAddedModifier(newModifier);
    }

    /**
     * Adds a modifier of a specific type to the gear.
     *
     * <p>Used when tier upgrades want to add a specific type (e.g., always add a prefix).
     *
     * @param gear The gear to modify
     * @param type The modifier type to add (PREFIX or SUFFIX)
     * @param slot The gear slot
     * @param random Random source
     * @return New GearData with added modifier, or unchanged if at max or none available
     */
    @Nonnull
    public GearData addModifierOfType(
            @Nonnull GearData gear,
            @Nonnull ModifierType type,
            @Nonnull String slot,
            @Nonnull Random random) {

        boolean canAdd = type == ModifierType.PREFIX ? gear.canAddPrefix() : gear.canAddSuffix();
        if (!canAdd) {
            return gear;
        }

        Set<String> existingIds = (type == ModifierType.PREFIX ? gear.prefixes() : gear.suffixes())
            .stream()
            .map(GearModifier::id)
            .collect(Collectors.toSet());

        List<GearModifier> rolled = rollModifiersExcluding(
            type, 1, gear.level(), slot, gear.rarity(), existingIds, random);

        if (rolled.isEmpty()) {
            return gear;
        }

        return gear.withAddedModifier(rolled.get(0));
    }

    /**
     * Removes a random unlocked modifier from the gear.
     *
     * <p>Used by EROSION_SHARD stone. Only unlocked modifiers can be removed.
     *
     * @param gear The gear to modify
     * @param random Random source
     * @return New GearData with one modifier removed, or unchanged if all locked
     */
    @Nonnull
    public GearData removeModifier(
            @Nonnull GearData gear,
            @Nonnull Random random) {

        // Collect all unlocked modifiers
        List<ModifierLocation> unlocked = new ArrayList<>();

        for (int i = 0; i < gear.prefixes().size(); i++) {
            GearModifier mod = gear.prefixes().get(i);
            if (!mod.locked()) {
                unlocked.add(new ModifierLocation(ModifierType.PREFIX, i, mod));
            }
        }
        for (int i = 0; i < gear.suffixes().size(); i++) {
            GearModifier mod = gear.suffixes().get(i);
            if (!mod.locked()) {
                unlocked.add(new ModifierLocation(ModifierType.SUFFIX, i, mod));
            }
        }

        if (unlocked.isEmpty()) {
            return gear; // All modifiers are locked
        }

        // Pick random unlocked modifier
        ModifierLocation target = unlocked.get(random.nextInt(unlocked.size()));
        return gear.withRemovedModifier(target.modifier);
    }

    /**
     * Removes all unlocked modifiers from the gear.
     *
     * <p>Used by PURGING_EMBER stone. Locked modifiers are preserved.
     *
     * @param gear The gear to modify
     * @return New GearData with only locked modifiers remaining
     */
    @Nonnull
    public GearData clearUnlockedModifiers(@Nonnull GearData gear) {
        List<GearModifier> lockedPrefixes = gear.prefixes().stream()
            .filter(GearModifier::locked)
            .toList();
        List<GearModifier> lockedSuffixes = gear.suffixes().stream()
            .filter(GearModifier::locked)
            .toList();

        return gear.withPrefixes(lockedPrefixes).withSuffixes(lockedSuffixes);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRANSMUTATION (atomic remove + add)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Removes one unlocked modifier and adds a new random one.
     *
     * <p>Used by TRANSMUTATION_CRYSTAL stone. This is an atomic operation:
     * the new modifier replaces the removed one's slot (prefix or suffix).
     *
     * @param gear The gear to modify
     * @param slot The gear slot
     * @param random Random source
     * @return New GearData with one modifier replaced, or unchanged if all locked
     */
    @Nonnull
    public GearData transmute(
            @Nonnull GearData gear,
            @Nonnull String slot,
            @Nonnull Random random) {

        // Collect unlocked modifiers
        List<ModifierLocation> unlocked = new ArrayList<>();

        for (int i = 0; i < gear.prefixes().size(); i++) {
            GearModifier mod = gear.prefixes().get(i);
            if (!mod.locked()) {
                unlocked.add(new ModifierLocation(ModifierType.PREFIX, i, mod));
            }
        }
        for (int i = 0; i < gear.suffixes().size(); i++) {
            GearModifier mod = gear.suffixes().get(i);
            if (!mod.locked()) {
                unlocked.add(new ModifierLocation(ModifierType.SUFFIX, i, mod));
            }
        }

        if (unlocked.isEmpty()) {
            return gear; // All modifiers are locked
        }

        // Pick random unlocked modifier to replace
        ModifierLocation target = unlocked.get(random.nextInt(unlocked.size()));

        // Get all existing IDs of the same type, excluding the one being replaced
        Set<String> existingIds;
        if (target.type == ModifierType.PREFIX) {
            existingIds = gear.prefixes().stream()
                .filter(m -> m != target.modifier)
                .map(GearModifier::id)
                .collect(Collectors.toSet());
        } else {
            existingIds = gear.suffixes().stream()
                .filter(m -> m != target.modifier)
                .map(GearModifier::id)
                .collect(Collectors.toSet());
        }

        // Roll a new modifier of the same type
        List<GearModifier> rolled = rollModifiersExcluding(
            target.type, 1, gear.level(), slot, gear.rarity(), existingIds, random);

        if (rolled.isEmpty()) {
            return gear; // No replacement available
        }

        GearModifier replacement = rolled.get(0);

        // Replace at the correct position
        if (target.type == ModifierType.PREFIX) {
            List<GearModifier> newPrefixes = new ArrayList<>(gear.prefixes());
            newPrefixes.set(target.index, replacement);
            return gear.withPrefixes(newPrefixes);
        } else {
            List<GearModifier> newSuffixes = new ArrayList<>(gear.suffixes());
            newSuffixes.set(target.index, replacement);
            return gear.withSuffixes(newSuffixes);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCKING OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Locks the modifier at the specified combined index.
     *
     * <p>Used by WARDENS_SEAL stone. Index maps to allModifiers() order
     * (prefixes first, then suffixes).
     *
     * @param gear The gear to modify
     * @param index Combined index (0-based)
     * @return New GearData with modifier locked, or unchanged if invalid index
     */
    @Nonnull
    public GearData lockModifierAt(@Nonnull GearData gear, int index) {
        return setModifierLockedAt(gear, index, true);
    }

    /**
     * Unlocks the modifier at the specified combined index.
     *
     * <p>Used by WARDENS_KEY stone.
     *
     * @param gear The gear to modify
     * @param index Combined index (0-based)
     * @return New GearData with modifier unlocked, or unchanged if invalid index
     */
    @Nonnull
    public GearData unlockModifierAt(@Nonnull GearData gear, int index) {
        return setModifierLockedAt(gear, index, false);
    }

    @Nonnull
    private GearData setModifierLockedAt(@Nonnull GearData gear, int index, boolean locked) {
        if (index < 0) {
            return gear;
        }

        if (index < gear.prefixes().size()) {
            List<GearModifier> newPrefixes = new ArrayList<>(gear.prefixes());
            GearModifier mod = newPrefixes.get(index);
            newPrefixes.set(index, mod.withLockedState(locked));
            return gear.withPrefixes(newPrefixes);
        }

        int suffixIndex = index - gear.prefixes().size();
        if (suffixIndex < gear.suffixes().size()) {
            List<GearModifier> newSuffixes = new ArrayList<>(gear.suffixes());
            GearModifier mod = newSuffixes.get(suffixIndex);
            newSuffixes.set(suffixIndex, mod.withLockedState(locked));
            return gear.withSuffixes(newSuffixes);
        }

        return gear; // Invalid index
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Rerolls a modifier's value based on its definition.
     */
    @Nonnull
    private GearModifier rerollModifierValue(
            @Nonnull GearModifier mod,
            int itemLevel,
            @Nonnull GearRarity rarity,
            @Nonnull Random random) {

        // Find the modifier definition
        ModifierDefinition def = findDefinition(mod.id(), mod.type());
        if (def == null) {
            // Unknown modifier, keep original value
            return mod;
        }

        // Calculate new value using similar logic to ModifierPool
        double newValue = calculateValue(def, itemLevel, rarity, random);
        return mod.withNewValue(newValue);
    }

    /**
     * Finds a modifier definition by ID and type.
     */
    @Nullable
    private ModifierDefinition findDefinition(@Nonnull String id, @Nonnull ModifierType type) {
        List<ModifierDefinition> list = type == ModifierType.PREFIX
            ? modifierConfig.prefixList()
            : modifierConfig.suffixList();

        return list.stream()
            .filter(def -> def.id().equalsIgnoreCase(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * Calculates the value for a modifier (mirrors ModifierPool logic).
     *
     * <p>Includes exponential scaling for dramatic power progression.
     */
    private double calculateValue(
            @Nonnull ModifierDefinition definition,
            int itemLevel,
            @Nonnull GearRarity rarity,
            @Nonnull Random random) {

        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        double rollVariance = balanceConfig.modifierScaling().rollVariance();

        // Get base range for this level (linear scaling from ModifierDefinition)
        ValueRange baseRange = definition.calculateRange(itemLevel);

        // Apply exponential scaling multiplier to the base range
        // This transforms linear growth into exponential growth for dramatic progression
        double expMultiplier = balanceConfig.exponentialScaling().calculateMultiplier(itemLevel);
        double scaledMin = baseRange.min() * expMultiplier;
        double scaledMax = baseRange.max() * expMultiplier;

        // Apply rarity stat multiplier for extended range
        double extendedMax = scaledMax * rarityConfig.statMultiplier();
        double effectiveMin = scaledMin;
        double effectiveMax = extendedMax;

        // Roll factor (0.0 to 1.0)
        double rollFactor = random.nextDouble();

        // For Mythic, enforce minimum roll percentile
        if (rarityConfig.minRollPercentile() > 0) {
            double minPercentile = rarityConfig.minRollPercentile();
            rollFactor = minPercentile + (rollFactor * (1.0 - minPercentile));
        }

        // Calculate base value from range
        double baseValue = effectiveMin + (effectiveMax - effectiveMin) * rollFactor;

        // Apply roll variance
        double varianceFactor = 1.0 + ((random.nextDouble() * 2 - 1) * rollVariance);
        double finalValue = baseValue * varianceFactor;

        // Ensure minimum is at least effectiveMin after variance
        return Math.max(effectiveMin * (1 - rollVariance), finalValue);
    }

    /**
     * Rolls modifiers while excluding certain IDs.
     */
    @Nonnull
    private List<GearModifier> rollModifiersExcluding(
            @Nonnull ModifierType type,
            int count,
            int itemLevel,
            @Nonnull String slot,
            @Nonnull GearRarity rarity,
            @Nonnull Set<String> excludedIds,
            @Nonnull Random random) {

        if (count <= 0) {
            return List.of();
        }

        // Get available modifiers for this slot
        List<ModifierDefinition> available = type == ModifierType.PREFIX
            ? modifierConfig.prefixesForSlot(slot)
            : modifierConfig.suffixesForSlot(slot);

        // Filter out excluded IDs
        List<ModifierDefinition> filtered = available.stream()
            .filter(def -> !excludedIds.contains(def.id()))
            .toList();

        if (filtered.isEmpty()) {
            return List.of();
        }

        // Track selected IDs to prevent duplicates
        Set<String> selectedIds = new HashSet<>(excludedIds);
        List<GearModifier> result = new ArrayList<>();

        for (int i = 0; i < count && !filtered.isEmpty(); i++) {
            // Filter out already selected
            List<ModifierDefinition> remaining = filtered.stream()
                .filter(m -> !selectedIds.contains(m.id()))
                .toList();

            if (remaining.isEmpty()) {
                break;
            }

            // Roll one modifier
            ModifierDefinition selected = selectWeighted(remaining, random);
            selectedIds.add(selected.id());

            // Calculate value
            double value = calculateValue(selected, itemLevel, rarity, random);

            // Create GearModifier
            result.add(GearModifier.of(
                selected.id(),
                selected.displayName(),
                type,
                selected.stat(),
                selected.statType().name().toLowerCase(),
                value
            ));
        }

        return List.copyOf(result);
    }

    /**
     * Weighted random selection from candidates.
     */
    @Nonnull
    private ModifierDefinition selectWeighted(
            @Nonnull List<ModifierDefinition> candidates,
            @Nonnull Random random) {

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        int totalWeight = candidates.stream()
            .mapToInt(ModifierDefinition::weight)
            .sum();

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (ModifierDefinition mod : candidates) {
            cumulative += mod.weight();
            if (roll < cumulative) {
                return mod;
            }
        }

        // Fallback (should never reach)
        return candidates.get(candidates.size() - 1);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the underlying modifier pool.
     */
    @Nonnull
    public ModifierPool getModifierPool() {
        return modifierPool;
    }

    /**
     * Helper record to track modifier locations during operations.
     */
    private record ModifierLocation(
        ModifierType type,
        int index,
        GearModifier modifier
    ) {}
}
