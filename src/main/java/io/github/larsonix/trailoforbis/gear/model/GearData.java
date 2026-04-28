package io.github.larsonix.trailoforbis.gear.model;

import io.github.larsonix.trailoforbis.gear.instance.GearInstanceId;
import io.github.larsonix.trailoforbis.stones.ItemModifier;
import io.github.larsonix.trailoforbis.stones.ItemTargetType;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.ModifiableItemBuilder;
import io.github.larsonix.trailoforbis.gems.model.GemData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Complete RPG data for a piece of gear.
 *
 * <p>This record contains all custom data that makes a vanilla item into RPG gear:
 * <ul>
 *   <li>Instance ID (unique identifier for dynamic display)</li>
 *   <li>Item level (affects stat scaling and requirements)</li>
 *   <li>Rarity (affects modifier count, stat multiplier, visuals)</li>
 *   <li>Quality (1-101, multiplies modifier values)</li>
 *   <li>Prefix modifiers (offensive stats)</li>
 *   <li>Suffix modifiers (defensive/utility stats)</li>
 *   <li>Corruption state (prevents further stone modifications)</li>
 * </ul>
 *
 * <p>Implements {@link ModifiableItem} to enable shared stone functionality
 * with realm map items (rerolling, locking, corruption, etc.).
 *
 * <p>This is an immutable record. Use the {@link Builder} for construction.
 * Lists are defensively copied - modifications to the original lists after
 * construction will not affect this record.
 *
 * @param instanceId Unique instance identifier for per-item display (may be null for legacy items)
 * @param level      Item level (1+), affects scaling and requirements
 * @param rarity     Rarity tier (COMMON to MYTHIC)
 * @param quality    Quality percentage (1-101), multiplies modifier values
 * @param prefixes   List of prefix modifiers (may be empty, never null)
 * @param suffixes   List of suffix modifiers (may be empty, never null)
 * @param corrupted  Whether this item is corrupted (prevents most stone modifications)
 * @param implicit      Weapon implicit damage (null for non-weapons or legacy items)
 * @param armorImplicit Armor implicit defense (null for non-armor or legacy items)
 * @param baseItemId    Original Hytale item ID before custom ID assignment (e.g., "Weapon_Staff_Wood")
 */
public record GearData(
    @Nullable GearInstanceId instanceId,
    int level,
    @Nonnull GearRarity rarity,
    int quality,
    @Nonnull List<GearModifier> prefixes,
    @Nonnull List<GearModifier> suffixes,
    boolean corrupted,
    @Nullable WeaponImplicit implicit,
    @Nullable ArmorImplicit armorImplicit,
    @Nullable String baseItemId,
    @Nullable GemData activeGem,
    @Nonnull List<GemData> supportGems,
    int supportSlotCount
) implements ModifiableItem {

    /** Minimum valid item level */
    public static final int MIN_LEVEL = 1;

    /** Maximum valid item level (matches max player level) */
    public static final int MAX_LEVEL = 1_000_000;

    /** Minimum valid quality */
    public static final int MIN_QUALITY = 1;

    /** Maximum normal quality (dropped items) */
    public static final int MAX_QUALITY = 100;

    /** Perfect quality (special drops only) */
    public static final int PERFECT_QUALITY = 101;

    /** Quality value that gives 1.0x multiplier (baseline) */
    public static final int QUALITY_BASELINE = 50;

    /**
     * Compact constructor with validation and defensive copying.
     */
    public GearData {
        // Validate level
        if (level < MIN_LEVEL) {
            throw new IllegalArgumentException(
                "Item level must be at least " + MIN_LEVEL + ", got: " + level
            );
        }
        if (level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                "Item level must be at most " + MAX_LEVEL + ", got: " + level
            );
        }

        // Validate rarity
        Objects.requireNonNull(rarity, "Rarity cannot be null");

        // Validate quality
        if (quality < MIN_QUALITY || quality > PERFECT_QUALITY) {
            throw new IllegalArgumentException(
                "Quality must be between " + MIN_QUALITY + " and " + PERFECT_QUALITY +
                ", got: " + quality
            );
        }

        // Validate and copy prefixes
        Objects.requireNonNull(prefixes, "Prefixes list cannot be null (use empty list)");
        for (GearModifier mod : prefixes) {
            Objects.requireNonNull(mod, "Prefix modifier cannot be null");
            if (mod.type() != ModifierType.PREFIX) {
                throw new IllegalArgumentException(
                    "Modifier in prefixes list must be PREFIX type: " + mod
                );
            }
        }
        prefixes = List.copyOf(prefixes); // Immutable copy

        // Validate and copy suffixes
        Objects.requireNonNull(suffixes, "Suffixes list cannot be null (use empty list)");
        for (GearModifier mod : suffixes) {
            Objects.requireNonNull(mod, "Suffix modifier cannot be null");
            if (mod.type() != ModifierType.SUFFIX) {
                throw new IllegalArgumentException(
                    "Modifier in suffixes list must be SUFFIX type: " + mod
                );
            }
        }
        suffixes = List.copyOf(suffixes); // Immutable copy

        // Validate total modifier count against rarity
        int totalModifiers = prefixes.size() + suffixes.size();
        if (totalModifiers > rarity.getMaxModifiers()) {
            throw new IllegalArgumentException(
                "Total modifiers (" + totalModifiers + ") exceeds maximum for " +
                rarity + " rarity (" + rarity.getMaxModifiers() + ")"
            );
        }
        // corrupted is a primitive boolean, no validation needed
        // implicit is nullable (null for non-weapons or legacy items), no validation needed

        // Defensive copy of gem support list
        supportGems = supportGems != null ? List.copyOf(supportGems) : List.of();
        if (supportSlotCount < 0) {
            supportSlotCount = 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates an uncorrupted GearData with the given parameters (no implicit, no baseItemId).
     *
     * <p>This factory method provides backwards compatibility for code
     * that was written before the corrupted, implicit, and baseItemId fields were added.
     *
     * @param instanceId Instance ID (may be null)
     * @param level      Item level
     * @param rarity     Rarity tier
     * @param quality    Quality value
     * @param prefixes   Prefix modifiers
     * @param suffixes   Suffix modifiers
     * @return A new uncorrupted GearData without implicit or baseItemId
     */
    @Nonnull
    public static GearData of(
            @Nullable GearInstanceId instanceId,
            int level,
            @Nonnull GearRarity rarity,
            int quality,
            @Nonnull List<GearModifier> prefixes,
            @Nonnull List<GearModifier> suffixes) {
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, false, null, null, null, null, List.of(), 0);
    }

    /**
     * Calculate the quality multiplier for modifier values.
     *
     * <p>Formula: 0.5 + quality / 100.0
     * <ul>
     *   <li>Quality 1 = 0.51x multiplier</li>
     *   <li>Quality 25 = 0.75x multiplier</li>
     *   <li>Quality 50 = 1.0x multiplier (baseline)</li>
     *   <li>Quality 75 = 1.25x multiplier</li>
     *   <li>Quality 100 = 1.5x multiplier</li>
     *   <li>Quality 101 = 1.51x multiplier (perfect)</li>
     * </ul>
     *
     * <p>The compressed range (0.51x–1.51x, ~3× spread) ensures quality is meaningful
     * (±50% swing) but cannot override a full rarity tier.
     *
     * @return Quality multiplier (0.51 to 1.51)
     */
    public double qualityMultiplier() {
        return 0.5 + quality / 100.0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ModifiableItem INTERFACE IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public int level() {
        return level;
    }

    @Override
    @Nonnull
    public GearRarity rarity() {
        return rarity;
    }

    @Override
    public int quality() {
        return quality;
    }

    @Override
    public boolean corrupted() {
        return corrupted;
    }

    @Override
    @Nonnull
    public List<? extends ItemModifier> modifiers() {
        return allModifiers();
    }

    @Override
    public int mapQuantityBonus() {
        return 0; // Gear doesn't have map quantity bonus
    }

    @Override
    @Nonnull
    public ItemTargetType itemTargetType() {
        return ItemTargetType.GEAR_ONLY;
    }

    @Override
    @Nonnull
    public ModifiableItemBuilder<?> toModifiableBuilder() {
        return new GearModifiableBuilder(this);
    }

    /**
     * Get all modifiers (prefixes + suffixes) as a single list.
     *
     * <p>The returned list is a new unmodifiable list containing prefixes first,
     * then suffixes. Order is consistent.
     *
     * @return Unmodifiable list of all modifiers
     */
    public List<GearModifier> allModifiers() {
        if (prefixes.isEmpty() && suffixes.isEmpty()) {
            return Collections.emptyList();
        }
        if (prefixes.isEmpty()) {
            return suffixes; // Already unmodifiable from constructor
        }
        if (suffixes.isEmpty()) {
            return prefixes; // Already unmodifiable from constructor
        }

        List<GearModifier> all = new ArrayList<>(prefixes.size() + suffixes.size());
        all.addAll(prefixes);
        all.addAll(suffixes);
        return Collections.unmodifiableList(all);
    }

    /**
     * Get the total number of modifiers on this item.
     *
     * @return Count of prefixes + suffixes
     */
    public int modifierCount() {
        return prefixes.size() + suffixes.size();
    }

    /**
     * Check if this item has any modifiers.
     *
     * @return true if there is at least one prefix or suffix
     */
    public boolean hasModifiers() {
        return !prefixes.isEmpty() || !suffixes.isEmpty();
    }

    /**
     * Check if this item has perfect quality.
     *
     * @return true if quality is 101
     */
    public boolean isPerfectQuality() {
        return quality == PERFECT_QUALITY;
    }

    /**
     * Check if this item has an instance ID assigned.
     *
     * <p>Legacy items (created before instance IDs) may not have one.
     * Use {@code GearUtils.ensureInstanceId()} to migrate legacy items.
     *
     * @return true if instanceId is not null
     */
    public boolean hasInstanceId() {
        return instanceId != null;
    }

    /**
     * Get the Hytale item ID for this gear instance.
     *
     * <p>This ID is used as the key in UpdateItems packets for per-instance display.
     *
     * @return The item ID string, or null if no instanceId assigned
     */
    @Nullable
    public String getItemId() {
        return instanceId != null ? instanceId.toItemId() : null;
    }

    /**
     * Check if this item can have more modifiers added.
     *
     * <p>Based on rarity maximum.
     *
     * @return true if modifierCount < rarity.getMaxModifiers()
     */
    public boolean canAddModifier() {
        return modifierCount() < rarity.getMaxModifiers();
    }

    /**
     * Check if a prefix can be added (has room and below rarity's max prefixes).
     *
     * <p>Uses the rarity's default max prefix limit. For config-specific limits,
     * check against {@code GearBalanceConfig.RarityConfig.maxPrefixes()} instead.
     *
     * @return true if a prefix can be added
     */
    public boolean canAddPrefix() {
        return canAddModifier() && prefixes.size() < rarity.getMaxPrefixes();
    }

    /**
     * Check if a suffix can be added (has room and below rarity's max suffixes).
     *
     * <p>Uses the rarity's default max suffix limit. For config-specific limits,
     * check against {@code GearBalanceConfig.RarityConfig.maxSuffixes()} instead.
     *
     * @return true if a suffix can be added
     */
    public boolean canAddSuffix() {
        return canAddModifier() && suffixes.size() < rarity.getMaxSuffixes();
    }

    /**
     * Create a new GearData with an additional modifier.
     *
     * @param modifier The modifier to add
     * @return New GearData with the modifier added
     * @throws IllegalArgumentException if modifier would exceed limits
     */
    @Nonnull
    public GearData withAddedModifier(@Nonnull GearModifier modifier) {
        Objects.requireNonNull(modifier, "Modifier cannot be null");

        List<GearModifier> newPrefixes = prefixes;
        List<GearModifier> newSuffixes = suffixes;

        if (modifier.isPrefix()) {
            if (!canAddPrefix()) {
                throw new IllegalArgumentException("Cannot add prefix: limit reached");
            }
            newPrefixes = new ArrayList<>(prefixes);
            newPrefixes.add(modifier);
        } else {
            if (!canAddSuffix()) {
                throw new IllegalArgumentException("Cannot add suffix: limit reached");
            }
            newSuffixes = new ArrayList<>(suffixes);
            newSuffixes.add(modifier);
        }

        return new GearData(instanceId, level, rarity, quality, newPrefixes, newSuffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with a modifier removed.
     *
     * @param modifier The modifier to remove (by reference equality)
     * @return New GearData without the modifier, or same instance if not found
     */
    @Nonnull
    public GearData withRemovedModifier(@Nonnull GearModifier modifier) {
        Objects.requireNonNull(modifier, "Modifier cannot be null");

        if (modifier.isPrefix()) {
            List<GearModifier> newPrefixes = new ArrayList<>(prefixes);
            if (newPrefixes.remove(modifier)) {
                return new GearData(instanceId, level, rarity, quality, newPrefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
            }
        } else {
            List<GearModifier> newSuffixes = new ArrayList<>(suffixes);
            if (newSuffixes.remove(modifier)) {
                return new GearData(instanceId, level, rarity, quality, prefixes, newSuffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
            }
        }

        return this; // Modifier not found, return same instance
    }

    /**
     * Create a new GearData with updated quality.
     *
     * @param newQuality The new quality value
     * @return New GearData with updated quality
     */
    @Nonnull
    public GearData withQuality(int newQuality) {
        return new GearData(instanceId, level, rarity, newQuality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated rarity.
     *
     * <p>Note: If the new rarity has a lower max modifier count than current
     * modifiers, this will throw an exception.
     *
     * @param newRarity The new rarity
     * @return New GearData with updated rarity
     * @throws IllegalArgumentException if current modifiers exceed new rarity max
     */
    @Nonnull
    public GearData withRarity(@Nonnull GearRarity newRarity) {
        return new GearData(instanceId, level, newRarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated instance ID.
     *
     * <p>Use this to assign an instance ID to legacy items.
     *
     * @param newInstanceId The new instance ID
     * @return New GearData with updated instanceId
     */
    @Nonnull
    public GearData withInstanceId(@Nullable GearInstanceId newInstanceId) {
        return new GearData(newInstanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated level.
     *
     * @param newLevel The new level value
     * @return New GearData with updated level
     */
    @Nonnull
    public GearData withLevel(int newLevel) {
        return new GearData(instanceId, newLevel, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated prefixes.
     *
     * @param newPrefixes The new prefix list
     * @return New GearData with updated prefixes
     */
    @Nonnull
    public GearData withPrefixes(@Nonnull List<GearModifier> newPrefixes) {
        return new GearData(instanceId, level, rarity, quality, newPrefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated suffixes.
     *
     * @param newSuffixes The new suffix list
     * @return New GearData with updated suffixes
     */
    @Nonnull
    public GearData withSuffixes(@Nonnull List<GearModifier> newSuffixes) {
        return new GearData(instanceId, level, rarity, quality, prefixes, newSuffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated corruption state.
     *
     * @param newCorrupted The new corruption state
     * @return New GearData with updated corruption
     */
    @Nonnull
    public GearData withCorrupted(boolean newCorrupted) {
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, newCorrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a corrupted copy of this gear.
     *
     * <p>If already corrupted, returns this instance (optimization).
     *
     * @return New corrupted GearData, or this if already corrupted
     */
    @Nonnull
    public GearData corrupt() {
        if (corrupted) {
            return this;
        }
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, true, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated implicit.
     *
     * @param newImplicit The new implicit (may be null to remove)
     * @return New GearData with updated implicit
     */
    @Nonnull
    public GearData withImplicit(@Nullable WeaponImplicit newImplicit) {
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, newImplicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated armor implicit.
     *
     * @param newArmorImplicit The new armor implicit (may be null to remove)
     * @return New GearData with updated armorImplicit
     */
    @Nonnull
    public GearData withArmorImplicit(@Nullable ArmorImplicit newArmorImplicit) {
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, newArmorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated baseItemId.
     *
     * @param newBaseItemId The new base item ID (may be null)
     * @return New GearData with updated baseItemId
     */
    @Nonnull
    public GearData withBaseItemId(@Nullable String newBaseItemId) {
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, newBaseItemId, activeGem, supportGems, supportSlotCount);
    }

    /**
     * Get the original Hytale item ID for this gear.
     *
     * <p>This is the item ID before the custom rpg_gear_* ID was assigned.
     * Used for weapon type detection and other operations that need the
     * original item type.
     *
     * @return The base item ID, or null if not stored (legacy items)
     */
    @Nullable
    public String getBaseItemId() {
        return baseItemId;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GEM SOCKET METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if this gear has an active gem socketed.
     */
    public boolean hasActiveGem() {
        return activeGem != null;
    }

    /**
     * Get the number of used support gem slots.
     */
    public int usedSupportSlots() {
        return supportGems.size();
    }

    /**
     * Get the number of available (empty) support gem slots.
     */
    public int availableSupportSlots() {
        return Math.max(0, supportSlotCount - supportGems.size());
    }

    /**
     * Create a new GearData with updated active gem.
     *
     * @param gem The new active gem (null to remove)
     * @return New GearData with updated active gem
     */
    @Nonnull
    public GearData withActiveGem(@Nullable GemData gem) {
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, gem, supportGems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated support gems list.
     *
     * @param gems The new support gems list
     * @return New GearData with updated support gems
     */
    @Nonnull
    public GearData withSupportGems(@Nonnull List<GemData> gems) {
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, gems, supportSlotCount);
    }

    /**
     * Create a new GearData with updated support slot count.
     *
     * @param count The new support slot count
     * @return New GearData with updated support slot count
     */
    @Nonnull
    public GearData withSupportSlotCount(int count) {
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, count);
    }

    /**
     * Create a new GearData with an additional support gem appended.
     *
     * @param gem The support gem to add
     * @return New GearData with the gem added to the support list
     */
    @Nonnull
    public GearData withAddedSupportGem(@Nonnull GemData gem) {
        List<GemData> newSupports = new ArrayList<>(supportGems);
        newSupports.add(gem);
        return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, newSupports, supportSlotCount);
    }

    /**
     * Check if this gear has any implicit stat (weapon damage or armor defense).
     *
     * @return true if either weapon implicit or armor implicit is present
     */
    @Override
    public boolean hasImplicit() {
        return implicit != null || armorImplicit != null;
    }

    /**
     * Check if this gear has an armor implicit defense stat.
     *
     * @return true if armorImplicit is not null
     */
    public boolean hasArmorImplicit() {
        return armorImplicit != null;
    }

    /**
     * Check if this gear has a weapon implicit damage stat.
     *
     * @return true if implicit is not null
     */
    public boolean hasWeaponImplicit() {
        return implicit != null;
    }

    /**
     * Create a builder pre-populated with this GearData's values.
     *
     * @return Builder for creating modified copies
     */
    @Nonnull
    public Builder toBuilder() {
        return new Builder()
            .instanceId(instanceId)
            .level(level)
            .rarity(rarity)
            .quality(quality)
            .prefixes(prefixes)
            .suffixes(suffixes)
            .corrupted(corrupted)
            .implicit(implicit)
            .armorImplicit(armorImplicit)
            .baseItemId(baseItemId)
            .activeGem(activeGem)
            .supportGems(supportGems)
            .supportSlotCount(supportSlotCount);
    }

    /**
     * Create a new builder for constructing GearData.
     *
     * @return New builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "GearData[level=%d, rarity=%s, quality=%d%s, prefixes=%d, suffixes=%d",
            level, rarity, quality,
            quality == PERFECT_QUALITY ? " (PERFECT)" : "",
            prefixes.size(), suffixes.size()));
        if (implicit != null) {
            sb.append(", implicit=").append(implicit);
        }
        if (armorImplicit != null) {
            sb.append(", armorImplicit=").append(armorImplicit);
        }
        if (corrupted) {
            sb.append(", CORRUPTED");
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Builder for constructing GearData instances.
     *
     * <p>Required fields: level, rarity. Defaults: quality=50, empty modifier lists, null instanceId.
     *
     * <h3>Modifier Methods Interaction</h3>
     *
     * <p>There are two ways to set modifiers:
     * <ul>
     *   <li>{@link #prefixes(List)} / {@link #suffixes(List)} - <b>Replaces</b> the entire list</li>
     *   <li>{@link #addPrefix(GearModifier)} / {@link #addSuffix(GearModifier)} - <b>Appends</b> to the existing list</li>
     * </ul>
     *
     * <p><b>Important:</b> Calling {@code prefixes(list)} after {@code addPrefix(mod)} will
     * discard any previously added prefixes. Order matters:
     *
     * <pre>{@code
     * // Example 1: addPrefix then prefixes - addPrefix result is lost!
     * builder.addPrefix(modA)
     *        .prefixes(List.of(modB)); // Result: [modB] - modA discarded!
     *
     * // Example 2: prefixes then addPrefix - both included
     * builder.prefixes(List.of(modA))
     *        .addPrefix(modB);         // Result: [modA, modB]
     *
     * // Example 3: Only addPrefix - accumulates
     * builder.addPrefix(modA)
     *        .addPrefix(modB);         // Result: [modA, modB]
     *
     * // Example 4: Only prefixes - sets exactly what you provide
     * builder.prefixes(List.of(modA, modB));  // Result: [modA, modB]
     * }</pre>
     *
     * <p>Recommendation: Use either the list-replacement methods OR the add methods,
     * but avoid mixing them in the same builder chain.
     */
    public static final class Builder {
        private GearInstanceId instanceId = null;
        private int level = 1;
        private GearRarity rarity;
        private int quality = QUALITY_BASELINE; // Default to baseline
        private List<GearModifier> prefixes = new ArrayList<>();
        private List<GearModifier> suffixes = new ArrayList<>();
        private boolean corrupted = false;
        private WeaponImplicit implicit = null;
        private ArmorImplicit armorImplicit = null;
        private String baseItemId = null;
        private GemData activeGem = null;
        private List<GemData> supportGems = new ArrayList<>();
        private int supportSlotCount = 0;

        private Builder() {}

        /**
         * Sets the instance ID for this gear.
         *
         * <p>If not set, the gear will have no instance ID (legacy mode).
         * Use {@code GearInstanceIdGenerator.generate()} to create new IDs.
         *
         * @param instanceId The instance ID (may be null)
         * @return this builder
         */
        public Builder instanceId(@Nullable GearInstanceId instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder rarity(GearRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder quality(int quality) {
            this.quality = quality;
            return this;
        }

        /**
         * Sets the prefix list, <b>replacing</b> any previously set or added prefixes.
         *
         * <p>Note: This discards any prefixes added via {@link #addPrefix(GearModifier)}.
         * See class documentation for interaction details.
         *
         * @param prefixes The complete list of prefixes (defensive copy made)
         * @return this builder
         */
        public Builder prefixes(List<GearModifier> prefixes) {
            this.prefixes = new ArrayList<>(prefixes);
            return this;
        }

        /**
         * Sets the suffix list, <b>replacing</b> any previously set or added suffixes.
         *
         * <p>Note: This discards any suffixes added via {@link #addSuffix(GearModifier)}.
         * See class documentation for interaction details.
         *
         * @param suffixes The complete list of suffixes (defensive copy made)
         * @return this builder
         */
        public Builder suffixes(List<GearModifier> suffixes) {
            this.suffixes = new ArrayList<>(suffixes);
            return this;
        }

        /**
         * Adds a prefix to the current list (<b>appends</b>, does not replace).
         *
         * <p>Note: If {@link #prefixes(List)} is called after this method,
         * the added prefix will be discarded. See class documentation.
         *
         * @param prefix The prefix to add
         * @return this builder
         */
        public Builder addPrefix(GearModifier prefix) {
            this.prefixes.add(prefix);
            return this;
        }

        /**
         * Adds a suffix to the current list (<b>appends</b>, does not replace).
         *
         * <p>Note: If {@link #suffixes(List)} is called after this method,
         * the added suffix will be discarded. See class documentation.
         *
         * @param suffix The suffix to add
         * @return this builder
         */
        public Builder addSuffix(GearModifier suffix) {
            this.suffixes.add(suffix);
            return this;
        }

        public Builder clearModifiers() {
            this.prefixes.clear();
            this.suffixes.clear();
            return this;
        }

        /**
         * Sets the corruption state.
         *
         * @param corrupted Whether the item is corrupted
         * @return this builder
         */
        public Builder corrupted(boolean corrupted) {
            this.corrupted = corrupted;
            return this;
        }

        /**
         * Sets the weapon implicit damage.
         *
         * <p>Only weapons should have implicits. For non-weapon gear, leave as null.
         *
         * @param implicit The weapon implicit (may be null)
         * @return this builder
         */
        public Builder implicit(@Nullable WeaponImplicit implicit) {
            this.implicit = implicit;
            return this;
        }

        /**
         * Sets the armor implicit defense stat.
         *
         * <p>Only armor pieces should have armor implicits. For non-armor gear, leave as null.
         *
         * @param armorImplicit The armor implicit (may be null)
         * @return this builder
         */
        public Builder armorImplicit(@Nullable ArmorImplicit armorImplicit) {
            this.armorImplicit = armorImplicit;
            return this;
        }

        /**
         * Sets the base item ID (original Hytale item ID before custom ID assignment).
         *
         * <p>This is used for weapon type detection when the item already has a custom
         * rpg_gear_* ID. Without this, weapon type detection fails on regenerated items.
         *
         * @param baseItemId The original item ID (may be null)
         * @return this builder
         */
        public Builder baseItemId(@Nullable String baseItemId) {
            this.baseItemId = baseItemId;
            return this;
        }

        public Builder activeGem(@Nullable GemData activeGem) {
            this.activeGem = activeGem;
            return this;
        }

        public Builder supportGems(@Nonnull List<GemData> supportGems) {
            this.supportGems = new ArrayList<>(supportGems);
            return this;
        }

        public Builder supportSlotCount(int supportSlotCount) {
            this.supportSlotCount = supportSlotCount;
            return this;
        }

        /**
         * Build the GearData instance.
         *
         * @return New GearData
         * @throws IllegalArgumentException if validation fails
         * @throws NullPointerException if required fields are null
         */
        @Nonnull
        public GearData build() {
            Objects.requireNonNull(rarity, "Rarity must be set");
            return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, this.armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIABLE ITEM BUILDER (for stone operations)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builder implementation for ModifiableItemBuilder interface.
     *
     * <p>Used by stone operations to create modified copies of gear.
     * Handles the prefix/suffix distinction when modifiers are manipulated.
     */
    private static final class GearModifiableBuilder
            implements ModifiableItemBuilder<GearModifiableBuilder> {

        private GearInstanceId instanceId;
        private int level;
        private GearRarity rarity;
        private int quality;
        private List<GearModifier> prefixes;
        private List<GearModifier> suffixes;
        private boolean corrupted;
        private WeaponImplicit implicit;
        private ArmorImplicit armorImplicit;
        private String baseItemId;
        private GemData activeGem;
        private List<GemData> supportGems;
        private int supportSlotCount;

        GearModifiableBuilder(@Nonnull GearData source) {
            this.instanceId = source.instanceId;
            this.level = source.level;
            this.rarity = source.rarity;
            this.quality = source.quality;
            this.prefixes = new ArrayList<>(source.prefixes);
            this.suffixes = new ArrayList<>(source.suffixes);
            this.corrupted = source.corrupted;
            this.implicit = source.implicit;
            this.armorImplicit = source.armorImplicit;
            this.baseItemId = source.baseItemId;
            this.activeGem = source.activeGem;
            this.supportGems = new ArrayList<>(source.supportGems);
            this.supportSlotCount = source.supportSlotCount;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder level(int level) {
            this.level = level;
            return this;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder rarity(@Nonnull GearRarity rarity) {
            this.rarity = Objects.requireNonNull(rarity);
            return this;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder quality(int quality) {
            this.quality = quality;
            return this;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder corrupted(boolean corrupted) {
            this.corrupted = corrupted;
            return this;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder modifiers(@Nonnull List<? extends ItemModifier> modifiers) {
            // Clear and re-add, sorting by type
            this.prefixes.clear();
            this.suffixes.clear();
            for (ItemModifier mod : modifiers) {
                if (mod instanceof GearModifier gm) {
                    if (gm.isPrefix()) {
                        this.prefixes.add(gm);
                    } else {
                        this.suffixes.add(gm);
                    }
                }
            }
            return this;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder addModifier(@Nonnull ItemModifier modifier) {
            if (modifier instanceof GearModifier gm) {
                if (gm.isPrefix()) {
                    this.prefixes.add(gm);
                } else {
                    this.suffixes.add(gm);
                }
            }
            return this;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder removeModifier(int index) {
            // Index maps to combined list (prefixes first, then suffixes)
            if (index < 0) {
                return this; // Invalid index, ignore
            }
            if (index < prefixes.size()) {
                prefixes.remove(index);
            } else {
                int suffixIndex = index - prefixes.size();
                if (suffixIndex < suffixes.size()) {
                    suffixes.remove(suffixIndex);
                }
            }
            return this;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder clearModifiers() {
            this.prefixes.clear();
            this.suffixes.clear();
            return this;
        }

        @Override
        @Nonnull
        public GearModifiableBuilder mapQuantityBonus(int bonus) {
            // No-op for gear - gear doesn't have map quantity bonus
            return this;
        }

        @Override
        @Nonnull
        public ModifiableItem build() {
            return new GearData(instanceId, level, rarity, quality, prefixes, suffixes, corrupted, implicit, armorImplicit, baseItemId, activeGem, supportGems, supportSlotCount);
        }
    }
}
