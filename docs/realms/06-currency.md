# Stone System

Stones are the unified crafting currency for both **Gear** and **Realm Maps**. This document details the complete implementation using a sealed interface architecture for type-safe, maintainable code.

> **Design Decision**: We use a sealed `ModifiableItem` interface that both `GearData` and `RealmMapData` implement. This provides compile-time safety and eliminates code duplication.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STONE SYSTEM ARCHITECTURE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                        ┌──────────────────────┐                             │
│                        │   ModifiableItem     │  ◄── Sealed interface       │
│                        │   (sealed)           │                             │
│                        └──────────┬───────────┘                             │
│                                   │                                         │
│                    ┌──────────────┴──────────────┐                          │
│                    │                             │                          │
│           ┌────────▼────────┐          ┌────────▼────────┐                  │
│           │    GearData     │          │  RealmMapData   │                  │
│           │  (implements)   │          │  (implements)   │                  │
│           └────────┬────────┘          └────────┬────────┘                  │
│                    │                             │                          │
│                    └──────────────┬──────────────┘                          │
│                                   │                                         │
│                        ┌──────────▼───────────┐                             │
│                        │    StoneActions      │  ◄── Works on interface     │
│                        │  (static methods)    │                             │
│                        └──────────┬───────────┘                             │
│                                   │                                         │
│                        ┌──────────▼───────────┐                             │
│                        │    StoneService      │  ◄── Public API             │
│                        └──────────────────────┘                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
io.github.larsonix.trailoforbis/
├── stones/                              # NEW PACKAGE
│   ├── ModifiableItem.java              # Sealed interface
│   ├── ModifiableItemBuilder.java       # Sealed builder interface
│   ├── StoneType.java                   # Stone definitions enum
│   ├── ItemTargetType.java              # GEAR_ONLY, MAP_ONLY, BOTH
│   ├── StoneActions.java                # All stone effect implementations
│   ├── StoneService.java                # Public API for applying stones
│   ├── StoneResult.java                 # Sealed result type
│   ├── StoneUtils.java                  # ItemStack read/write for stones
│   ├── StoneConfig.java                 # Configuration POJO
│   └── StoneDropListener.java           # Drop on mob kill
│
├── gear/
│   └── model/
│       ├── GearData.java                # MODIFIED: implements ModifiableItem
│       └── GearModifier.java            # (unchanged)
│
└── maps/
    └── core/
        └── RealmMapData.java            # NEW: implements ModifiableItem
```

---

## Core Interface: ModifiableItem

This sealed interface defines the contract for any item that can be modified by stones.

```java
package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;

import java.util.List;

/**
 * Sealed interface for items that can be modified by stones.
 *
 * <p>This interface is implemented by both {@link GearData} and {@link RealmMapData},
 * allowing the stone system to work uniformly on both item types.
 *
 * <p>The sealed nature ensures exhaustive pattern matching in switch expressions,
 * providing compile-time safety when handling different item types.
 *
 * <h2>Design Rationale</h2>
 * <ul>
 *   <li><b>Sealed</b> - Only GearData and RealmMapData can implement this</li>
 *   <li><b>Immutable</b> - All implementations must be immutable records</li>
 *   <li><b>Builder pattern</b> - Modifications return new instances via builders</li>
 * </ul>
 *
 * @see GearData
 * @see RealmMapData
 * @see StoneActions
 */
public sealed interface ModifiableItem permits GearData, RealmMapData {

    // =========================================================================
    // IDENTITY
    // =========================================================================

    /**
     * Get the item level.
     *
     * <p>For gear: affects stat scaling and attribute requirements.
     * <p>For maps: determines monster levels in the realm.
     *
     * @return Item level (1 to 10000)
     */
    int level();

    /**
     * Get the item rarity.
     *
     * <p>Rarity determines:
     * <ul>
     *   <li>Maximum modifier count ({@link GearRarity#getMaxModifiers()})</li>
     *   <li>Visual appearance (color, effects)</li>
     *   <li>Which stones can be applied</li>
     * </ul>
     *
     * @return The rarity tier (COMMON to MYTHIC)
     */
    GearRarity rarity();

    /**
     * Get the item quality percentage.
     *
     * <p>Quality affects modifier effectiveness:
     * <ul>
     *   <li>1-49: Below baseline (weaker)</li>
     *   <li>50: Baseline (1.0x multiplier)</li>
     *   <li>51-100: Above baseline (stronger)</li>
     *   <li>101: Perfect quality (drop-only, cannot be crafted)</li>
     * </ul>
     *
     * @return Quality percentage (1 to 101)
     */
    int quality();

    // =========================================================================
    // CORRUPTION STATE
    // =========================================================================

    /**
     * Check if this item is corrupted.
     *
     * <p>Corrupted items cannot be modified by any stone except Corruption Stone
     * (which does nothing on already-corrupted items).
     *
     * @return true if corrupted
     */
    boolean isCorrupted();

    // =========================================================================
    // MODIFIER ACCESS
    // =========================================================================

    /**
     * Get all modifiers on this item.
     *
     * <p>For gear: returns combined list of prefixes and suffixes.
     * <p>For maps: returns realm modifiers.
     *
     * @return Unmodifiable list of all modifiers
     */
    List<? extends ItemModifier> modifiers();

    /**
     * Get the total number of modifiers.
     *
     * @return Count of all modifiers
     */
    default int modifierCount() {
        return modifiers().size();
    }

    /**
     * Get the maximum allowed modifiers for this item's rarity.
     *
     * @return Maximum modifier count
     */
    default int maxModifiers() {
        return rarity().getMaxModifiers();
    }

    /**
     * Check if this item has any modifiers.
     *
     * @return true if at least one modifier exists
     */
    default boolean hasModifiers() {
        return !modifiers().isEmpty();
    }

    /**
     * Check if a new modifier can be added.
     *
     * @return true if modifierCount < maxModifiers
     */
    default boolean canAddModifier() {
        return modifierCount() < maxModifiers();
    }

    /**
     * Check if this item has any unlocked (non-protected) modifiers.
     *
     * <p>Lock Stone protects modifiers from being changed by other stones.
     *
     * @return true if at least one unlocked modifier exists
     */
    boolean hasUnlockedModifiers();

    /**
     * Get the count of unlocked modifiers.
     *
     * @return Number of modifiers that are not locked
     */
    int unlockedModifierCount();

    // =========================================================================
    // MAP-SPECIFIC (default implementations for gear)
    // =========================================================================

    /**
     * Get the map-specific quality bonus (IIQ%).
     *
     * <p>This is separate from item quality and only applies to realm maps.
     * Returns 0 for gear items.
     *
     * @return Map IIQ bonus (0-20 for maps, 0 for gear)
     */
    default int mapQuantityBonus() {
        return 0;
    }

    // =========================================================================
    // BUILDER ACCESS
    // =========================================================================

    /**
     * Create a builder pre-populated with this item's values.
     *
     * <p>Used by stone actions to create modified copies.
     *
     * @return Builder for creating a modified copy
     */
    ModifiableItemBuilder toModifiableBuilder();
}
```

---

## Builder Interface: ModifiableItemBuilder

```java
package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;

/**
 * Sealed builder interface for creating modified copies of {@link ModifiableItem}.
 *
 * <p>This interface provides the common mutation operations needed by stones.
 * Implementations are the nested Builder classes in GearData and RealmMapData.
 *
 * <p>All methods return {@code this} for fluent chaining.
 */
public sealed interface ModifiableItemBuilder
    permits GearData.Builder, RealmMapData.Builder {

    // =========================================================================
    // COMMON PROPERTIES
    // =========================================================================

    /**
     * Set the item level.
     *
     * @param level New level (1 to 10000)
     * @return this builder
     */
    ModifiableItemBuilder level(int level);

    /**
     * Set the item rarity.
     *
     * <p>Note: Changing rarity may require adjusting modifier count
     * if the new rarity has a lower maximum.
     *
     * @param rarity New rarity tier
     * @return this builder
     */
    ModifiableItemBuilder rarity(GearRarity rarity);

    /**
     * Set the item quality.
     *
     * @param quality New quality (1 to 100, stones cannot create 101 Perfect)
     * @return this builder
     */
    ModifiableItemBuilder quality(int quality);

    /**
     * Set the corruption state.
     *
     * @param corrupted true to mark as corrupted
     * @return this builder
     */
    ModifiableItemBuilder corrupted(boolean corrupted);

    // =========================================================================
    // MODIFIER OPERATIONS
    // =========================================================================

    /**
     * Clear all modifiers from the item.
     *
     * @return this builder
     */
    ModifiableItemBuilder clearModifiers();

    /**
     * Clear only unlocked modifiers, preserving locked ones.
     *
     * @return this builder
     */
    ModifiableItemBuilder clearUnlockedModifiers();

    // =========================================================================
    // BUILD
    // =========================================================================

    /**
     * Build the modified item.
     *
     * @return New immutable ModifiableItem instance
     * @throws IllegalArgumentException if validation fails
     */
    ModifiableItem build();
}
```

---

## Item Modifier Interface

```java
package io.github.larsonix.trailoforbis.stones;

/**
 * Common interface for modifiers on modifiable items.
 *
 * <p>Implemented by:
 * <ul>
 *   <li>{@link io.github.larsonix.trailoforbis.gear.model.GearModifier} - For gear prefixes/suffixes</li>
 *   <li>{@link io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier} - For map modifiers</li>
 * </ul>
 */
public interface ItemModifier {

    /**
     * Get the modifier's unique identifier.
     *
     * @return Modifier ID (e.g., "sharp", "monster_damage")
     */
    String id();

    /**
     * Get the modifier's display name.
     *
     * @return Human-readable name for tooltips
     */
    String displayName();

    /**
     * Get the modifier's numeric value.
     *
     * @return The rolled value (e.g., 15.5 for "+15.5% damage")
     */
    double value();

    /**
     * Check if this modifier is locked (protected from stone modifications).
     *
     * @return true if locked by Lock Stone
     */
    boolean isLocked();

    /**
     * Create a copy with the locked state changed.
     *
     * @param locked New locked state
     * @return New modifier instance with updated lock state
     */
    ItemModifier withLocked(boolean locked);

    /**
     * Create a copy with a new value.
     *
     * <p>Used by Divine Stones to reroll values.
     *
     * @param newValue The new value
     * @return New modifier instance with updated value
     */
    ItemModifier withValue(double newValue);
}
```

---

## GearData Modifications

The existing `GearData` class needs these changes:

```java
package io.github.larsonix.trailoforbis.gear.model;

import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.ModifiableItemBuilder;
import io.github.larsonix.trailoforbis.stones.ItemModifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Complete RPG data for a piece of gear.
 *
 * <p>Implements {@link ModifiableItem} to support the unified stone system.
 */
public record GearData(
    int level,
    GearRarity rarity,
    int quality,
    List<GearModifier> prefixes,
    List<GearModifier> suffixes,
    boolean corrupted  // NEW FIELD
) implements ModifiableItem {

    // ... existing constants (MIN_LEVEL, MAX_LEVEL, etc.) ...

    /**
     * Compact constructor with validation and defensive copying.
     */
    public GearData {
        // ... existing validation ...

        // NEW: Validate corrupted state (no additional validation needed)
    }

    // =========================================================================
    // ModifiableItem IMPLEMENTATION
    // =========================================================================

    @Override
    public List<? extends ItemModifier> modifiers() {
        return allModifiers();
    }

    @Override
    public boolean isCorrupted() {
        return corrupted;
    }

    @Override
    public boolean hasUnlockedModifiers() {
        for (GearModifier mod : prefixes) {
            if (!mod.isLocked()) return true;
        }
        for (GearModifier mod : suffixes) {
            if (!mod.isLocked()) return true;
        }
        return false;
    }

    @Override
    public int unlockedModifierCount() {
        int count = 0;
        for (GearModifier mod : prefixes) {
            if (!mod.isLocked()) count++;
        }
        for (GearModifier mod : suffixes) {
            if (!mod.isLocked()) count++;
        }
        return count;
    }

    @Override
    public ModifiableItemBuilder toModifiableBuilder() {
        return toBuilder();
    }

    // =========================================================================
    // EXISTING METHODS (unchanged)
    // =========================================================================

    public List<GearModifier> allModifiers() {
        // ... existing implementation ...
    }

    public GearData withAddedModifier(GearModifier modifier) {
        // ... existing implementation ...
    }

    public GearData withRemovedModifier(GearModifier modifier) {
        // ... existing implementation ...
    }

    public GearData withQuality(int newQuality) {
        return new GearData(level, rarity, newQuality, prefixes, suffixes, corrupted);
    }

    public GearData withRarity(GearRarity newRarity) {
        return new GearData(level, newRarity, quality, prefixes, suffixes, corrupted);
    }

    // NEW METHOD
    public GearData withCorrupted(boolean newCorrupted) {
        return new GearData(level, rarity, quality, prefixes, suffixes, newCorrupted);
    }

    public Builder toBuilder() {
        return new Builder()
            .level(level)
            .rarity(rarity)
            .quality(quality)
            .prefixes(prefixes)
            .suffixes(suffixes)
            .corrupted(corrupted);
    }

    // =========================================================================
    // BUILDER (implements ModifiableItemBuilder)
    // =========================================================================

    public static final class Builder implements ModifiableItemBuilder {
        private int level = 1;
        private GearRarity rarity;
        private int quality = QUALITY_BASELINE;
        private List<GearModifier> prefixes = new ArrayList<>();
        private List<GearModifier> suffixes = new ArrayList<>();
        private boolean corrupted = false;

        // ... existing methods ...

        @Override
        public Builder level(int level) {
            this.level = level;
            return this;
        }

        @Override
        public Builder rarity(GearRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        @Override
        public Builder quality(int quality) {
            this.quality = quality;
            return this;
        }

        @Override
        public Builder corrupted(boolean corrupted) {
            this.corrupted = corrupted;
            return this;
        }

        @Override
        public Builder clearModifiers() {
            this.prefixes.clear();
            this.suffixes.clear();
            return this;
        }

        @Override
        public Builder clearUnlockedModifiers() {
            this.prefixes.removeIf(mod -> !mod.isLocked());
            this.suffixes.removeIf(mod -> !mod.isLocked());
            return this;
        }

        // Gear-specific builder methods
        public Builder prefixes(List<GearModifier> prefixes) {
            this.prefixes = new ArrayList<>(prefixes);
            return this;
        }

        public Builder suffixes(List<GearModifier> suffixes) {
            this.suffixes = new ArrayList<>(suffixes);
            return this;
        }

        public Builder addPrefix(GearModifier prefix) {
            this.prefixes.add(prefix);
            return this;
        }

        public Builder addSuffix(GearModifier suffix) {
            this.suffixes.add(suffix);
            return this;
        }

        @Override
        public GearData build() {
            Objects.requireNonNull(rarity, "Rarity must be set");
            return new GearData(level, rarity, quality, prefixes, suffixes, corrupted);
        }
    }
}
```

---

## GearModifier Modifications

Add `isLocked` field and implement `ItemModifier`:

```java
package io.github.larsonix.trailoforbis.gear.model;

import io.github.larsonix.trailoforbis.stones.ItemModifier;

/**
 * Represents a single modifier (prefix or suffix) on a piece of gear.
 *
 * <p>Implements {@link ItemModifier} for stone system compatibility.
 */
public record GearModifier(
    String id,
    String displayName,
    ModifierType type,
    String statId,
    String statType,
    double value,
    boolean locked  // NEW FIELD (default false)
) implements ItemModifier {

    // Backwards-compatible constructor (locked = false)
    public GearModifier(String id, String displayName, ModifierType type,
                        String statId, String statType, double value) {
        this(id, displayName, type, statId, statType, value, false);
    }

    // ... existing validation in compact constructor ...

    // =========================================================================
    // ItemModifier IMPLEMENTATION
    // =========================================================================

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public ItemModifier withLocked(boolean locked) {
        return new GearModifier(id, displayName, type, statId, statType, value, locked);
    }

    @Override
    public ItemModifier withValue(double newValue) {
        return new GearModifier(id, displayName, type, statId, statType, newValue, locked);
    }

    // ... existing methods (isFlat, isPercent, isPrefix, isSuffix, formatForTooltip) ...
}
```

---

## RealmMapData Implementation

```java
package io.github.larsonix.trailoforbis.maps.core;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.ModifiableItemBuilder;
import io.github.larsonix.trailoforbis.stones.ItemModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable data for a Realm Map item.
 *
 * <p>Implements {@link ModifiableItem} to support the unified stone system.
 *
 * <p>A Realm Map contains:
 * <ul>
 *   <li>Identity: UUID, level, rarity</li>
 *   <li>Layout: biome type, shape, size</li>
 *   <li>Modifiers: list of realm modifiers affecting difficulty/rewards</li>
 *   <li>Quality: base quality + map-specific IIQ bonus</li>
 *   <li>State: corruption status</li>
 * </ul>
 */
public record RealmMapData(
    UUID mapId,
    int level,
    GearRarity rarity,
    int quality,
    RealmBiomeType biomeType,
    RealmLayoutShape layoutShape,
    RealmLayoutSize layoutSize,
    List<RealmModifier> modifiers,
    int mapQuantityBonus,
    boolean corrupted
) implements ModifiableItem {

    // =========================================================================
    // CONSTANTS
    // =========================================================================

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10000;
    public static final int MIN_QUALITY = 1;
    public static final int MAX_QUALITY = 100;
    public static final int PERFECT_QUALITY = 101;
    public static final int MAX_MAP_QUANTITY_BONUS = 20;

    // =========================================================================
    // COMPACT CONSTRUCTOR (validation)
    // =========================================================================

    public RealmMapData {
        Objects.requireNonNull(mapId, "Map ID cannot be null");

        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                "Level must be between " + MIN_LEVEL + " and " + MAX_LEVEL + ", got: " + level
            );
        }

        Objects.requireNonNull(rarity, "Rarity cannot be null");

        if (quality < MIN_QUALITY || quality > PERFECT_QUALITY) {
            throw new IllegalArgumentException(
                "Quality must be between " + MIN_QUALITY + " and " + PERFECT_QUALITY + ", got: " + quality
            );
        }

        Objects.requireNonNull(biomeType, "Biome type cannot be null");
        Objects.requireNonNull(layoutShape, "Layout shape cannot be null");
        Objects.requireNonNull(layoutSize, "Layout size cannot be null");

        Objects.requireNonNull(modifiers, "Modifiers list cannot be null");
        modifiers = List.copyOf(modifiers);  // Immutable copy

        if (modifiers.size() > rarity.getMaxModifiers()) {
            throw new IllegalArgumentException(
                "Too many modifiers (" + modifiers.size() + ") for " + rarity +
                " (max " + rarity.getMaxModifiers() + ")"
            );
        }

        if (mapQuantityBonus < 0 || mapQuantityBonus > MAX_MAP_QUANTITY_BONUS) {
            throw new IllegalArgumentException(
                "Map quantity bonus must be 0-" + MAX_MAP_QUANTITY_BONUS + ", got: " + mapQuantityBonus
            );
        }
    }

    // =========================================================================
    // ModifiableItem IMPLEMENTATION
    // =========================================================================

    @Override
    public List<? extends ItemModifier> modifiers() {
        return modifiers;
    }

    @Override
    public boolean isCorrupted() {
        return corrupted;
    }

    @Override
    public boolean hasUnlockedModifiers() {
        for (RealmModifier mod : modifiers) {
            if (!mod.isLocked()) return true;
        }
        return false;
    }

    @Override
    public int unlockedModifierCount() {
        int count = 0;
        for (RealmModifier mod : modifiers) {
            if (!mod.isLocked()) count++;
        }
        return count;
    }

    @Override
    public int mapQuantityBonus() {
        return mapQuantityBonus;
    }

    @Override
    public ModifiableItemBuilder toModifiableBuilder() {
        return toBuilder();
    }

    // =========================================================================
    // COMPUTED PROPERTIES
    // =========================================================================

    /**
     * Calculate total Item Quantity bonus from quality + map bonus + modifiers.
     */
    public float totalItemQuantity() {
        float total = mapQuantityBonus / 100f;
        for (RealmModifier mod : modifiers) {
            if (mod.type() == RealmModifierType.ITEM_QUANTITY) {
                total += mod.value() / 100f;
            }
        }
        return total;
    }

    /**
     * Calculate total Item Rarity bonus from modifiers.
     */
    public float totalItemRarity() {
        float total = 0;
        for (RealmModifier mod : modifiers) {
            if (mod.type() == RealmModifierType.ITEM_RARITY) {
                total += mod.value() / 100f;
            }
        }
        return total;
    }

    /**
     * Calculate total XP bonus from modifiers.
     */
    public float totalXpBonus() {
        float total = 0;
        for (RealmModifier mod : modifiers) {
            if (mod.type() == RealmModifierType.EXPERIENCE_BONUS) {
                total += mod.value() / 100f;
            }
        }
        return total;
    }

    /**
     * Calculate difficulty rating (1-5 stars) based on modifiers.
     */
    public int difficultyRating() {
        int total = 0;
        for (RealmModifier mod : modifiers) {
            total += mod.difficultyContribution();
        }
        return Math.min(5, Math.max(1, total / 3));
    }

    // =========================================================================
    // CONVENIENCE METHODS
    // =========================================================================

    /**
     * Get list of unlocked modifiers only.
     */
    public List<RealmModifier> unlockedModifiers() {
        List<RealmModifier> unlocked = new ArrayList<>();
        for (RealmModifier mod : modifiers) {
            if (!mod.isLocked()) {
                unlocked.add(mod);
            }
        }
        return unlocked;
    }

    /**
     * Get list of locked modifiers only.
     */
    public List<RealmModifier> lockedModifiers() {
        List<RealmModifier> locked = new ArrayList<>();
        for (RealmModifier mod : modifiers) {
            if (mod.isLocked()) {
                locked.add(mod);
            }
        }
        return locked;
    }

    // =========================================================================
    // BUILDER
    // =========================================================================

    public Builder toBuilder() {
        return new Builder()
            .mapId(mapId)
            .level(level)
            .rarity(rarity)
            .quality(quality)
            .biomeType(biomeType)
            .layoutShape(layoutShape)
            .layoutSize(layoutSize)
            .modifiers(modifiers)
            .mapQuantityBonus(mapQuantityBonus)
            .corrupted(corrupted);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RealmMapData.
     *
     * <p>Implements {@link ModifiableItemBuilder} for stone system compatibility.
     */
    public static final class Builder implements ModifiableItemBuilder {
        private UUID mapId;
        private int level = 1;
        private GearRarity rarity;
        private int quality = 50;
        private RealmBiomeType biomeType;
        private RealmLayoutShape layoutShape;
        private RealmLayoutSize layoutSize;
        private List<RealmModifier> modifiers = new ArrayList<>();
        private int mapQuantityBonus = 0;
        private boolean corrupted = false;

        private Builder() {}

        // =====================================================================
        // ModifiableItemBuilder IMPLEMENTATION
        // =====================================================================

        @Override
        public Builder level(int level) {
            this.level = level;
            return this;
        }

        @Override
        public Builder rarity(GearRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        @Override
        public Builder quality(int quality) {
            this.quality = quality;
            return this;
        }

        @Override
        public Builder corrupted(boolean corrupted) {
            this.corrupted = corrupted;
            return this;
        }

        @Override
        public Builder clearModifiers() {
            this.modifiers.clear();
            return this;
        }

        @Override
        public Builder clearUnlockedModifiers() {
            this.modifiers.removeIf(mod -> !mod.isLocked());
            return this;
        }

        @Override
        public RealmMapData build() {
            if (mapId == null) {
                mapId = UUID.randomUUID();
            }
            Objects.requireNonNull(rarity, "Rarity must be set");
            Objects.requireNonNull(biomeType, "Biome type must be set");
            Objects.requireNonNull(layoutShape, "Layout shape must be set");
            Objects.requireNonNull(layoutSize, "Layout size must be set");

            return new RealmMapData(
                mapId, level, rarity, quality,
                biomeType, layoutShape, layoutSize,
                modifiers, mapQuantityBonus, corrupted
            );
        }

        // =====================================================================
        // MAP-SPECIFIC BUILDER METHODS
        // =====================================================================

        public Builder mapId(UUID mapId) {
            this.mapId = mapId;
            return this;
        }

        public Builder biomeType(RealmBiomeType biomeType) {
            this.biomeType = biomeType;
            return this;
        }

        public Builder layoutShape(RealmLayoutShape layoutShape) {
            this.layoutShape = layoutShape;
            return this;
        }

        public Builder layoutSize(RealmLayoutSize layoutSize) {
            this.layoutSize = layoutSize;
            return this;
        }

        public Builder modifiers(List<RealmModifier> modifiers) {
            this.modifiers = new ArrayList<>(modifiers);
            return this;
        }

        public Builder addModifier(RealmModifier modifier) {
            this.modifiers.add(modifier);
            return this;
        }

        public Builder mapQuantityBonus(int bonus) {
            this.mapQuantityBonus = bonus;
            return this;
        }
    }
}
```

---

## RealmModifier Record

```java
package io.github.larsonix.trailoforbis.maps.modifiers;

import io.github.larsonix.trailoforbis.stones.ItemModifier;

/**
 * A modifier on a Realm Map.
 *
 * <p>Implements {@link ItemModifier} for stone system compatibility.
 */
public record RealmModifier(
    String id,
    String displayName,
    RealmModifierType type,
    double value,
    int difficultyContribution,
    boolean locked
) implements ItemModifier {

    // Convenience constructor (locked = false)
    public RealmModifier(String id, String displayName, RealmModifierType type,
                         double value, int difficultyContribution) {
        this(id, displayName, type, value, difficultyContribution, false);
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public ItemModifier withLocked(boolean locked) {
        return new RealmModifier(id, displayName, type, value, difficultyContribution, locked);
    }

    @Override
    public ItemModifier withValue(double newValue) {
        return new RealmModifier(id, displayName, type, newValue, difficultyContribution, locked);
    }

    /**
     * Format for tooltip display.
     */
    public String formatForTooltip() {
        return type.formatValue(value);
    }
}
```

---

## StoneType Enum

```java
package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import java.util.function.Predicate;

/**
 * Defines all stone types and their application rules.
 *
 * <p>Each stone has:
 * <ul>
 *   <li>Display name and description</li>
 *   <li>Target type (gear only, map only, or both)</li>
 *   <li>Predicate to validate if it can be applied</li>
 * </ul>
 *
 * <p>Stones are consumed when applied and modify the target item.
 */
public enum StoneType {

    // =========================================================================
    // RARITY UPGRADE STONES
    // =========================================================================

    UNCOMMON_UPGRADE(
        "Uncommon Upgrade Stone",
        "Upgrades a Common item to Uncommon and adds 1 modifier",
        ItemTargetType.BOTH,
        item -> item.rarity() == GearRarity.COMMON && !item.isCorrupted()
    ),

    RARE_UPGRADE(
        "Rare Upgrade Stone",
        "Upgrades an Uncommon item to Rare and adds 1 modifier",
        ItemTargetType.BOTH,
        item -> item.rarity() == GearRarity.UNCOMMON && !item.isCorrupted()
    ),

    EPIC_UPGRADE(
        "Epic Upgrade Stone",
        "Upgrades a Rare item to Epic and adds 1 modifier",
        ItemTargetType.BOTH,
        item -> item.rarity() == GearRarity.RARE && !item.isCorrupted()
    ),

    // =========================================================================
    // MODIFIER MANIPULATION STONES
    // =========================================================================

    CHAOS(
        "Chaos Stone",
        "Rerolls all unlocked modifiers, keeping the same count",
        ItemTargetType.BOTH,
        item -> item.rarity().isAtLeast(GearRarity.RARE) &&
                !item.isCorrupted() &&
                item.hasUnlockedModifiers()
    ),

    ADDITION(
        "Addition Stone",
        "Adds one random modifier",
        ItemTargetType.BOTH,
        item -> item.canAddModifier() && !item.isCorrupted()
    ),

    REMOVAL(
        "Removal Stone",
        "Removes one random unlocked modifier",
        ItemTargetType.BOTH,
        item -> item.hasUnlockedModifiers() && !item.isCorrupted()
    ),

    CLEANSING(
        "Cleansing Stone",
        "Removes all modifiers and resets rarity to Common",
        ItemTargetType.BOTH,
        item -> item.rarity() != GearRarity.MYTHIC &&
                !item.isCorrupted() &&
                item.hasModifiers()
    ),

    SWAP(
        "Swap Stone",
        "Removes one random unlocked modifier and adds a new one",
        ItemTargetType.BOTH,
        item -> item.hasUnlockedModifiers() && !item.isCorrupted()
    ),

    LOCK(
        "Lock Stone",
        "Locks one random unlocked modifier, protecting it from changes",
        ItemTargetType.BOTH,
        item -> item.hasUnlockedModifiers() && !item.isCorrupted()
    ),

    // =========================================================================
    // VALUE REROLL STONES
    // =========================================================================

    LESSER_DIVINE(
        "Lesser Divine Stone",
        "Rerolls the value of one random unlocked modifier",
        ItemTargetType.BOTH,
        item -> item.hasUnlockedModifiers() && !item.isCorrupted()
    ),

    GREATER_DIVINE(
        "Greater Divine Stone",
        "Rerolls the values of all unlocked modifiers",
        ItemTargetType.BOTH,
        item -> item.hasUnlockedModifiers() && !item.isCorrupted()
    ),

    // =========================================================================
    // SPECIAL STONES
    // =========================================================================

    CORRUPTION(
        "Corruption Stone",
        "Corrupts the item with unpredictable results. Cannot be undone.",
        ItemTargetType.BOTH,
        item -> !item.isCorrupted()
    ),

    QUALITY(
        "Quality Stone",
        "Rerolls item quality between 1-100%",
        ItemTargetType.BOTH,
        item -> !item.isCorrupted() && item.quality() != 101  // Cannot reroll Perfect
    ),

    // =========================================================================
    // MAP-SPECIFIC STONES
    // =========================================================================

    CARTOGRAPHER(
        "Cartographer's Stone",
        "Adds +5% Item Quantity to the map (maximum 20%)",
        ItemTargetType.MAP_ONLY,
        item -> item.mapQuantityBonus() < 20 && !item.isCorrupted()
    ),

    HORIZON(
        "Horizon Stone",
        "Changes the map's biome type randomly",
        ItemTargetType.MAP_ONLY,
        item -> !item.isCorrupted()
    ),

    EXPLORER(
        "Explorer's Stone",
        "Rerolls the map level within ±3 of current level",
        ItemTargetType.MAP_ONLY,
        item -> !item.isCorrupted()
    );

    // =========================================================================
    // ENUM FIELDS
    // =========================================================================

    private final String displayName;
    private final String description;
    private final ItemTargetType targetType;
    private final Predicate<ModifiableItem> canApplyPredicate;

    StoneType(String displayName, String description,
              ItemTargetType targetType, Predicate<ModifiableItem> canApplyPredicate) {
        this.displayName = displayName;
        this.description = description;
        this.targetType = targetType;
        this.canApplyPredicate = canApplyPredicate;
    }

    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public ItemTargetType getTargetType() {
        return targetType;
    }

    /**
     * Check if this stone can be applied to the given item.
     *
     * @param item The item to check
     * @return true if the stone can be applied
     */
    public boolean canApplyTo(ModifiableItem item) {
        // First check target type compatibility
        if (targetType == ItemTargetType.GEAR_ONLY && !(item instanceof GearData)) {
            return false;
        }
        if (targetType == ItemTargetType.MAP_ONLY && !(item instanceof RealmMapData)) {
            return false;
        }

        // Then check item-specific predicate
        return canApplyPredicate.test(item);
    }

    /**
     * Check if this stone works on the given item type.
     *
     * @param type The item target type
     * @return true if this stone can work on that type
     */
    public boolean worksOn(ItemTargetType type) {
        return targetType == ItemTargetType.BOTH || targetType == type;
    }

    /**
     * Get a human-readable reason why this stone cannot be applied.
     *
     * @param item The item that was rejected
     * @return Reason string for user feedback
     */
    public String getCannotApplyReason(ModifiableItem item) {
        if (item.isCorrupted()) {
            return "Cannot modify corrupted items";
        }

        if (targetType == ItemTargetType.GEAR_ONLY && !(item instanceof GearData)) {
            return displayName + " can only be used on gear";
        }

        if (targetType == ItemTargetType.MAP_ONLY && !(item instanceof RealmMapData)) {
            return displayName + " can only be used on realm maps";
        }

        // Stone-specific reasons
        return switch (this) {
            case UNCOMMON_UPGRADE -> "Item must be Common rarity";
            case RARE_UPGRADE -> "Item must be Uncommon rarity";
            case EPIC_UPGRADE -> "Item must be Rare rarity";
            case CHAOS -> "Item must be Rare or higher with unlocked modifiers";
            case ADDITION -> "Item already has maximum modifiers";
            case REMOVAL, SWAP, LOCK, LESSER_DIVINE, GREATER_DIVINE ->
                "Item has no unlocked modifiers";
            case CLEANSING -> "Cannot cleanse Mythic items or items without modifiers";
            case CORRUPTION -> "Item is already corrupted";
            case QUALITY -> "Cannot reroll Perfect quality items";
            case CARTOGRAPHER -> "Map already has maximum quantity bonus";
            case HORIZON, EXPLORER -> "Unknown error";
        };
    }
}
```

---

## ItemTargetType Enum

```java
package io.github.larsonix.trailoforbis.stones;

/**
 * Defines which item types a stone can be applied to.
 */
public enum ItemTargetType {

    /**
     * Stone only works on gear items (weapons, armor, accessories).
     */
    GEAR_ONLY("Gear"),

    /**
     * Stone only works on realm maps.
     */
    MAP_ONLY("Map"),

    /**
     * Stone works on both gear and realm maps.
     */
    BOTH("Any");

    private final String displayName;

    ItemTargetType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

---

## StoneActions (Unified Implementation)

```java
package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.generation.ModifierPool;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Implements all stone effects using the {@link ModifiableItem} interface.
 *
 * <p>All methods:
 * <ul>
 *   <li>Take a {@link ModifiableItem} and return a new modified instance</li>
 *   <li>Are stateless and thread-safe</li>
 *   <li>Throw exceptions on invalid operations (caller should validate first)</li>
 * </ul>
 *
 * <p>Pattern matching with sealed types ensures compile-time completeness
 * when handling gear vs map-specific logic.
 */
public final class StoneActions {

    private StoneActions() {} // Utility class

    // =========================================================================
    // RARITY UPGRADES
    // =========================================================================

    /**
     * Apply Uncommon Upgrade Stone: COMMON → UNCOMMON, add 1 modifier.
     */
    public static ModifiableItem applyUncommonUpgrade(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        validateRarity(item, GearRarity.COMMON, "Uncommon Upgrade Stone");

        return switch (item) {
            case GearData gear -> {
                GearModifier newMod = gearPool.rollModifier(
                    gear.level(), GearRarity.UNCOMMON, random
                );
                yield gear.withRarity(GearRarity.UNCOMMON)
                          .withAddedModifier(newMod);
            }
            case RealmMapData map -> {
                RealmModifier newMod = mapPool.rollModifier(
                    map.level(), GearRarity.UNCOMMON, random
                );
                yield map.toBuilder()
                    .rarity(GearRarity.UNCOMMON)
                    .addModifier(newMod)
                    .build();
            }
        };
    }

    /**
     * Apply Rare Upgrade Stone: UNCOMMON → RARE, add 1 modifier.
     */
    public static ModifiableItem applyRareUpgrade(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        validateRarity(item, GearRarity.UNCOMMON, "Rare Upgrade Stone");

        return switch (item) {
            case GearData gear -> {
                GearModifier newMod = gearPool.rollModifier(
                    gear.level(), GearRarity.RARE, random
                );
                yield gear.withRarity(GearRarity.RARE)
                          .withAddedModifier(newMod);
            }
            case RealmMapData map -> {
                RealmModifier newMod = mapPool.rollModifier(
                    map.level(), GearRarity.RARE, random
                );
                yield map.toBuilder()
                    .rarity(GearRarity.RARE)
                    .addModifier(newMod)
                    .build();
            }
        };
    }

    /**
     * Apply Epic Upgrade Stone: RARE → EPIC, add 1 modifier.
     */
    public static ModifiableItem applyEpicUpgrade(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        validateRarity(item, GearRarity.RARE, "Epic Upgrade Stone");

        return switch (item) {
            case GearData gear -> {
                GearModifier newMod = gearPool.rollModifier(
                    gear.level(), GearRarity.EPIC, random
                );
                yield gear.withRarity(GearRarity.EPIC)
                          .withAddedModifier(newMod);
            }
            case RealmMapData map -> {
                RealmModifier newMod = mapPool.rollModifier(
                    map.level(), GearRarity.EPIC, random
                );
                yield map.toBuilder()
                    .rarity(GearRarity.EPIC)
                    .addModifier(newMod)
                    .build();
            }
        };
    }

    // =========================================================================
    // MODIFIER MANIPULATION
    // =========================================================================

    /**
     * Apply Chaos Stone: Reroll all unlocked modifiers, keeping count.
     */
    public static ModifiableItem applyChaos(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        return switch (item) {
            case GearData gear -> {
                // Count unlocked prefixes and suffixes
                List<GearModifier> lockedPrefixes = new ArrayList<>();
                List<GearModifier> lockedSuffixes = new ArrayList<>();
                int unlockedPrefixCount = 0;
                int unlockedSuffixCount = 0;

                for (GearModifier mod : gear.prefixes()) {
                    if (mod.isLocked()) {
                        lockedPrefixes.add(mod);
                    } else {
                        unlockedPrefixCount++;
                    }
                }
                for (GearModifier mod : gear.suffixes()) {
                    if (mod.isLocked()) {
                        lockedSuffixes.add(mod);
                    } else {
                        unlockedSuffixCount++;
                    }
                }

                // Roll new modifiers
                List<GearModifier> newPrefixes = gearPool.rollPrefixes(
                    unlockedPrefixCount, gear.level(), null, gear.rarity(), random
                );
                List<GearModifier> newSuffixes = gearPool.rollSuffixes(
                    unlockedSuffixCount, gear.level(), null, gear.rarity(), random
                );

                // Combine locked + new
                List<GearModifier> allPrefixes = new ArrayList<>(lockedPrefixes);
                allPrefixes.addAll(newPrefixes);
                List<GearModifier> allSuffixes = new ArrayList<>(lockedSuffixes);
                allSuffixes.addAll(newSuffixes);

                yield gear.toBuilder()
                    .prefixes(allPrefixes)
                    .suffixes(allSuffixes)
                    .build();
            }
            case RealmMapData map -> {
                List<RealmModifier> locked = map.lockedModifiers();
                int unlockedCount = map.unlockedModifierCount();

                List<RealmModifier> newMods = mapPool.rollModifiers(
                    unlockedCount, map.level(), map.rarity(), random
                );

                List<RealmModifier> allMods = new ArrayList<>(locked);
                allMods.addAll(newMods);

                yield map.toBuilder()
                    .modifiers(allMods)
                    .build();
            }
        };
    }

    /**
     * Apply Addition Stone: Add 1 random modifier.
     */
    public static ModifiableItem applyAddition(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        if (!item.canAddModifier()) {
            throw new IllegalStateException("Item already has maximum modifiers");
        }

        return switch (item) {
            case GearData gear -> {
                // 50/50 prefix or suffix (respecting limits)
                boolean addPrefix = gear.canAddPrefix() &&
                    (!gear.canAddSuffix() || random.nextBoolean());

                GearModifier newMod = addPrefix
                    ? gearPool.rollPrefix(gear.level(), null, gear.rarity(), random)
                    : gearPool.rollSuffix(gear.level(), null, gear.rarity(), random);

                yield gear.withAddedModifier(newMod);
            }
            case RealmMapData map -> {
                RealmModifier newMod = mapPool.rollModifier(
                    map.level(), map.rarity(), random
                );
                yield map.toBuilder()
                    .addModifier(newMod)
                    .build();
            }
        };
    }

    /**
     * Apply Removal Stone: Remove 1 random unlocked modifier.
     */
    public static ModifiableItem applyRemoval(ModifiableItem item, Random random) {
        return switch (item) {
            case GearData gear -> {
                List<GearModifier> unlocked = getUnlockedGearModifiers(gear);
                if (unlocked.isEmpty()) {
                    throw new IllegalStateException("No unlocked modifiers to remove");
                }

                GearModifier toRemove = unlocked.get(random.nextInt(unlocked.size()));
                yield gear.withRemovedModifier(toRemove);
            }
            case RealmMapData map -> {
                List<RealmModifier> unlocked = map.unlockedModifiers();
                if (unlocked.isEmpty()) {
                    throw new IllegalStateException("No unlocked modifiers to remove");
                }

                RealmModifier toRemove = unlocked.get(random.nextInt(unlocked.size()));
                List<RealmModifier> remaining = new ArrayList<>(map.modifiers());
                remaining.remove(toRemove);

                yield map.toBuilder()
                    .modifiers(remaining)
                    .build();
            }
        };
    }

    /**
     * Apply Cleansing Stone: Remove all modifiers, reset to COMMON.
     */
    public static ModifiableItem applyCleansing(ModifiableItem item) {
        return item.toModifiableBuilder()
            .rarity(GearRarity.COMMON)
            .clearModifiers()
            .build();
    }

    /**
     * Apply Swap Stone: Remove 1 unlocked modifier, add 1 new modifier.
     */
    public static ModifiableItem applySwap(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        // Remove one
        ModifiableItem afterRemoval = applyRemoval(item, random);

        // Add one (if space, which there should be after removal)
        return applyAddition(afterRemoval, gearPool, mapPool, random);
    }

    /**
     * Apply Lock Stone: Lock 1 random unlocked modifier.
     */
    public static ModifiableItem applyLock(ModifiableItem item, Random random) {
        return switch (item) {
            case GearData gear -> {
                List<GearModifier> unlocked = getUnlockedGearModifiers(gear);
                if (unlocked.isEmpty()) {
                    throw new IllegalStateException("No unlocked modifiers to lock");
                }

                GearModifier toLock = unlocked.get(random.nextInt(unlocked.size()));
                GearModifier locked = (GearModifier) toLock.withLocked(true);

                yield replaceGearModifier(gear, toLock, locked);
            }
            case RealmMapData map -> {
                List<RealmModifier> unlocked = map.unlockedModifiers();
                if (unlocked.isEmpty()) {
                    throw new IllegalStateException("No unlocked modifiers to lock");
                }

                RealmModifier toLock = unlocked.get(random.nextInt(unlocked.size()));
                RealmModifier locked = (RealmModifier) toLock.withLocked(true);

                List<RealmModifier> newMods = new ArrayList<>(map.modifiers());
                int idx = newMods.indexOf(toLock);
                if (idx >= 0) {
                    newMods.set(idx, locked);
                }

                yield map.toBuilder()
                    .modifiers(newMods)
                    .build();
            }
        };
    }

    // =========================================================================
    // VALUE REROLLS
    // =========================================================================

    /**
     * Apply Lesser Divine Stone: Reroll value of 1 random unlocked modifier.
     */
    public static ModifiableItem applyLesserDivine(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        return switch (item) {
            case GearData gear -> {
                List<GearModifier> unlocked = getUnlockedGearModifiers(gear);
                if (unlocked.isEmpty()) {
                    throw new IllegalStateException("No unlocked modifiers to reroll");
                }

                GearModifier old = unlocked.get(random.nextInt(unlocked.size()));
                double newValue = gearPool.rerollValue(old, gear.level(), gear.rarity(), random);
                GearModifier rerolled = (GearModifier) old.withValue(newValue);

                yield replaceGearModifier(gear, old, rerolled);
            }
            case RealmMapData map -> {
                List<RealmModifier> unlocked = map.unlockedModifiers();
                if (unlocked.isEmpty()) {
                    throw new IllegalStateException("No unlocked modifiers to reroll");
                }

                RealmModifier old = unlocked.get(random.nextInt(unlocked.size()));
                double newValue = mapPool.rerollValue(old, map.level(), map.rarity(), random);
                RealmModifier rerolled = (RealmModifier) old.withValue(newValue);

                List<RealmModifier> newMods = new ArrayList<>(map.modifiers());
                int idx = newMods.indexOf(old);
                if (idx >= 0) {
                    newMods.set(idx, rerolled);
                }

                yield map.toBuilder()
                    .modifiers(newMods)
                    .build();
            }
        };
    }

    /**
     * Apply Greater Divine Stone: Reroll values of all unlocked modifiers.
     */
    public static ModifiableItem applyGreaterDivine(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        return switch (item) {
            case GearData gear -> {
                List<GearModifier> newPrefixes = new ArrayList<>();
                for (GearModifier mod : gear.prefixes()) {
                    if (mod.isLocked()) {
                        newPrefixes.add(mod);
                    } else {
                        double newValue = gearPool.rerollValue(mod, gear.level(), gear.rarity(), random);
                        newPrefixes.add((GearModifier) mod.withValue(newValue));
                    }
                }

                List<GearModifier> newSuffixes = new ArrayList<>();
                for (GearModifier mod : gear.suffixes()) {
                    if (mod.isLocked()) {
                        newSuffixes.add(mod);
                    } else {
                        double newValue = gearPool.rerollValue(mod, gear.level(), gear.rarity(), random);
                        newSuffixes.add((GearModifier) mod.withValue(newValue));
                    }
                }

                yield gear.toBuilder()
                    .prefixes(newPrefixes)
                    .suffixes(newSuffixes)
                    .build();
            }
            case RealmMapData map -> {
                List<RealmModifier> newMods = new ArrayList<>();
                for (RealmModifier mod : map.modifiers()) {
                    if (mod.isLocked()) {
                        newMods.add(mod);
                    } else {
                        double newValue = mapPool.rerollValue(mod, map.level(), map.rarity(), random);
                        newMods.add((RealmModifier) mod.withValue(newValue));
                    }
                }

                yield map.toBuilder()
                    .modifiers(newMods)
                    .build();
            }
        };
    }

    // =========================================================================
    // SPECIAL STONES
    // =========================================================================

    /**
     * Apply Corruption Stone: Random outcome, item becomes corrupted.
     *
     * @return Modified item, or null if item was destroyed (Shatter outcome)
     */
    public static ModifiableItem applyCorruption(
            ModifiableItem item,
            ModifierPool gearPool,
            RealmModifierPool mapPool,
            Random random) {

        float roll = random.nextFloat();

        // Outcome distribution:
        // 0.00-0.15: Ascend (upgrade rarity)
        // 0.15-0.30: Empower (add 1-2 corruption modifiers)
        // 0.30-0.45: Transform (upgrade one modifier)
        // 0.45-0.70: Stabilize (nothing, just corrupts)
        // 0.70-0.85: Fracture (lose 1 modifier)
        // 0.85-0.95: Shatter (destroy item)
        // 0.95-1.00: Paradox (invert quality)

        if (roll < 0.15f) {
            // ASCEND: Upgrade rarity
            Optional<GearRarity> nextRarity = item.rarity().getNextTier();
            if (nextRarity.isPresent()) {
                return item.toModifiableBuilder()
                    .rarity(nextRarity.get())
                    .corrupted(true)
                    .build();
            }
            // Already max rarity, just corrupt
            return item.toModifiableBuilder().corrupted(true).build();

        } else if (roll < 0.30f) {
            // EMPOWER: Add corruption-only modifiers
            int bonus = random.nextInt(1, 3);
            return switch (item) {
                case GearData gear -> {
                    GearData.Builder builder = gear.toBuilder().corrupted(true);
                    for (int i = 0; i < bonus && builder.build().canAddModifier(); i++) {
                        GearModifier mod = gearPool.rollCorruptedModifier(gear.level(), random);
                        builder.addPrefix(mod);  // Corruption mods are always prefixes
                    }
                    yield builder.build();
                }
                case RealmMapData map -> {
                    RealmMapData.Builder builder = map.toBuilder().corrupted(true);
                    for (int i = 0; i < bonus && builder.build().canAddModifier(); i++) {
                        RealmModifier mod = mapPool.rollCorruptedModifier(map.level(), random);
                        builder.addModifier(mod);
                    }
                    yield builder.build();
                }
            };

        } else if (roll < 0.45f) {
            // TRANSFORM: Upgrade one modifier to corruption version
            return switch (item) {
                case GearData gear -> {
                    if (!gear.hasModifiers()) {
                        yield gear.toBuilder().corrupted(true).build();
                    }
                    List<GearModifier> all = gear.allModifiers();
                    GearModifier old = all.get(random.nextInt(all.size()));
                    GearModifier corrupted = gearPool.upgradeToCorrupted(old, random);
                    yield replaceGearModifier(gear, old, corrupted)
                        .toBuilder().corrupted(true).build();
                }
                case RealmMapData map -> {
                    if (!map.hasModifiers()) {
                        yield map.toBuilder().corrupted(true).build();
                    }
                    List<RealmModifier> mods = new ArrayList<>(map.modifiers());
                    int idx = random.nextInt(mods.size());
                    RealmModifier corrupted = mapPool.upgradeToCorrupted(mods.get(idx), random);
                    mods.set(idx, corrupted);
                    yield map.toBuilder().modifiers(mods).corrupted(true).build();
                }
            };

        } else if (roll < 0.70f) {
            // STABILIZE: Nothing happens, just corrupts
            return item.toModifiableBuilder().corrupted(true).build();

        } else if (roll < 0.85f) {
            // FRACTURE: Lose 1 modifier
            if (!item.hasModifiers()) {
                return item.toModifiableBuilder().corrupted(true).build();
            }
            ModifiableItem afterRemoval = applyRemoval(item, random);
            return afterRemoval.toModifiableBuilder().corrupted(true).build();

        } else if (roll < 0.95f) {
            // SHATTER: Item is destroyed
            return null;

        } else {
            // PARADOX: Invert quality (1 ↔ 100)
            int newQuality = 101 - item.quality();
            newQuality = Math.max(1, Math.min(100, newQuality));
            return item.toModifiableBuilder()
                .quality(newQuality)
                .corrupted(true)
                .build();
        }
    }

    /**
     * Apply Quality Stone: Reroll quality 1-100.
     */
    public static ModifiableItem applyQuality(ModifiableItem item, Random random) {
        int newQuality = random.nextInt(1, 101);  // 1-100, never 101 Perfect
        return item.toModifiableBuilder()
            .quality(newQuality)
            .build();
    }

    // =========================================================================
    // MAP-SPECIFIC STONES
    // =========================================================================

    /**
     * Apply Cartographer's Stone: +5% IIQ (max 20%).
     */
    public static RealmMapData applyCartographer(RealmMapData map) {
        int current = map.mapQuantityBonus();
        int newBonus = Math.min(current + 5, RealmMapData.MAX_MAP_QUANTITY_BONUS);
        return map.toBuilder()
            .mapQuantityBonus(newBonus)
            .build();
    }

    /**
     * Apply Horizon Stone: Change biome randomly.
     */
    public static RealmMapData applyHorizon(RealmMapData map, Random random) {
        RealmBiomeType[] biomes = RealmBiomeType.values();
        RealmBiomeType current = map.biomeType();

        RealmBiomeType newBiome;
        do {
            newBiome = biomes[random.nextInt(biomes.length)];
        } while (newBiome == current && biomes.length > 1);

        return map.toBuilder()
            .biomeType(newBiome)
            .build();
    }

    /**
     * Apply Explorer's Stone: Reroll level ±3.
     */
    public static RealmMapData applyExplorer(RealmMapData map, Random random) {
        int delta = random.nextInt(-3, 4);  // -3 to +3
        int newLevel = Math.max(1, map.level() + delta);
        newLevel = Math.min(newLevel, RealmMapData.MAX_LEVEL);

        return map.toBuilder()
            .level(newLevel)
            .build();
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static void validateRarity(ModifiableItem item, GearRarity expected, String stoneName) {
        if (item.rarity() != expected) {
            throw new IllegalStateException(
                stoneName + " requires " + expected + " rarity, got: " + item.rarity()
            );
        }
    }

    private static List<GearModifier> getUnlockedGearModifiers(GearData gear) {
        List<GearModifier> unlocked = new ArrayList<>();
        for (GearModifier mod : gear.prefixes()) {
            if (!mod.isLocked()) unlocked.add(mod);
        }
        for (GearModifier mod : gear.suffixes()) {
            if (!mod.isLocked()) unlocked.add(mod);
        }
        return unlocked;
    }

    private static GearData replaceGearModifier(GearData gear, GearModifier old, GearModifier replacement) {
        if (old.isPrefix()) {
            List<GearModifier> newPrefixes = new ArrayList<>(gear.prefixes());
            int idx = newPrefixes.indexOf(old);
            if (idx >= 0) {
                newPrefixes.set(idx, replacement);
            }
            return gear.toBuilder()
                .prefixes(newPrefixes)
                .build();
        } else {
            List<GearModifier> newSuffixes = new ArrayList<>(gear.suffixes());
            int idx = newSuffixes.indexOf(old);
            if (idx >= 0) {
                newSuffixes.set(idx, replacement);
            }
            return gear.toBuilder()
                .suffixes(newSuffixes)
                .build();
        }
    }
}
```

---

## StoneService (Public API)

```java
package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gear.generation.ModifierPool;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.util.RealmMapUtils;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierPool;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Public API for applying stones to items.
 *
 * <p>Usage:
 * <pre>{@code
 * StoneService service = new StoneService(gearPool, mapPool);
 *
 * StoneResult result = service.applyStone(itemStack, StoneType.CHAOS);
 *
 * switch (result) {
 *     case StoneResult.Success s -> updateInventory(s.newItem());
 *     case StoneResult.Failure f -> showError(f.reason());
 *     case StoneResult.Destroyed d -> removeFromInventory();
 * }
 * }</pre>
 */
public class StoneService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ModifierPool gearPool;
    private final RealmModifierPool mapPool;

    public StoneService(ModifierPool gearPool, RealmModifierPool mapPool) {
        this.gearPool = gearPool;
        this.mapPool = mapPool;
    }

    /**
     * Apply a stone to an item.
     *
     * @param item The ItemStack to modify
     * @param stone The stone to apply
     * @return Result indicating success, failure, or destruction
     */
    public StoneResult applyStone(ItemStack item, StoneType stone) {
        Random random = ThreadLocalRandom.current();

        // Read modifiable data from item
        Optional<ModifiableItem> optData = readModifiableItem(item);
        if (optData.isEmpty()) {
            return StoneResult.failure("Item cannot be modified by stones");
        }

        ModifiableItem data = optData.get();

        // Validate stone can be applied
        if (!stone.canApplyTo(data)) {
            return StoneResult.failure(stone.getCannotApplyReason(data));
        }

        // Apply stone effect
        try {
            ModifiableItem result = applyStoneEffect(stone, data, random);

            // Handle destruction (corruption can destroy)
            if (result == null) {
                LOGGER.atInfo().log("Stone %s destroyed item", stone);
                return StoneResult.destroyed();
            }

            // Write back to ItemStack
            ItemStack newItem = writeModifiableItem(item, result);

            LOGGER.atInfo().log("Applied %s successfully", stone);
            return StoneResult.success(newItem, result);

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to apply %s", stone);
            return StoneResult.failure("Stone application failed: " + e.getMessage());
        }
    }

    /**
     * Check if a stone can be applied to an item (without applying).
     */
    public boolean canApply(ItemStack item, StoneType stone) {
        return readModifiableItem(item)
            .map(stone::canApplyTo)
            .orElse(false);
    }

    /**
     * Get reason why a stone cannot be applied.
     */
    public String getCannotApplyReason(ItemStack item, StoneType stone) {
        return readModifiableItem(item)
            .map(stone::getCannotApplyReason)
            .orElse("Item cannot be modified by stones");
    }

    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================

    private Optional<ModifiableItem> readModifiableItem(ItemStack item) {
        // Try gear first
        Optional<GearData> gear = GearUtils.readGearData(item);
        if (gear.isPresent()) {
            return Optional.of(gear.get());
        }

        // Try map
        Optional<RealmMapData> map = RealmMapUtils.readMapData(item);
        if (map.isPresent()) {
            return Optional.of(map.get());
        }

        return Optional.empty();
    }

    private ItemStack writeModifiableItem(ItemStack item, ModifiableItem data) {
        return switch (data) {
            case GearData gear -> GearUtils.setGearData(item, gear);
            case RealmMapData map -> RealmMapUtils.setMapData(item, map);
        };
    }

    private ModifiableItem applyStoneEffect(StoneType stone, ModifiableItem item, Random random) {
        return switch (stone) {
            // Rarity upgrades
            case UNCOMMON_UPGRADE -> StoneActions.applyUncommonUpgrade(item, gearPool, mapPool, random);
            case RARE_UPGRADE -> StoneActions.applyRareUpgrade(item, gearPool, mapPool, random);
            case EPIC_UPGRADE -> StoneActions.applyEpicUpgrade(item, gearPool, mapPool, random);

            // Modifier manipulation
            case CHAOS -> StoneActions.applyChaos(item, gearPool, mapPool, random);
            case ADDITION -> StoneActions.applyAddition(item, gearPool, mapPool, random);
            case REMOVAL -> StoneActions.applyRemoval(item, random);
            case CLEANSING -> StoneActions.applyCleansing(item);
            case SWAP -> StoneActions.applySwap(item, gearPool, mapPool, random);
            case LOCK -> StoneActions.applyLock(item, random);

            // Value rerolls
            case LESSER_DIVINE -> StoneActions.applyLesserDivine(item, gearPool, mapPool, random);
            case GREATER_DIVINE -> StoneActions.applyGreaterDivine(item, gearPool, mapPool, random);

            // Special
            case CORRUPTION -> StoneActions.applyCorruption(item, gearPool, mapPool, random);
            case QUALITY -> StoneActions.applyQuality(item, random);

            // Map-specific (require cast)
            case CARTOGRAPHER -> {
                if (!(item instanceof RealmMapData map)) {
                    throw new IllegalArgumentException("Cartographer's Stone requires a realm map");
                }
                yield StoneActions.applyCartographer(map);
            }
            case HORIZON -> {
                if (!(item instanceof RealmMapData map)) {
                    throw new IllegalArgumentException("Horizon Stone requires a realm map");
                }
                yield StoneActions.applyHorizon(map, random);
            }
            case EXPLORER -> {
                if (!(item instanceof RealmMapData map)) {
                    throw new IllegalArgumentException("Explorer's Stone requires a realm map");
                }
                yield StoneActions.applyExplorer(map, random);
            }
        };
    }
}
```

---

## StoneResult (Sealed Result Type)

```java
package io.github.larsonix.trailoforbis.stones;

import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Result of applying a stone to an item.
 *
 * <p>Sealed interface with three possible outcomes:
 * <ul>
 *   <li>{@link Success} - Stone applied, returns new ItemStack</li>
 *   <li>{@link Failure} - Stone could not be applied, returns reason</li>
 *   <li>{@link Destroyed} - Item was destroyed (Corruption Shatter)</li>
 * </ul>
 */
public sealed interface StoneResult {

    /**
     * Stone applied successfully.
     *
     * @param newItem The modified ItemStack
     * @param newData The modified data (GearData or RealmMapData)
     */
    record Success(ItemStack newItem, ModifiableItem newData) implements StoneResult {}

    /**
     * Stone could not be applied.
     *
     * @param reason Human-readable reason for failure
     */
    record Failure(String reason) implements StoneResult {}

    /**
     * Item was destroyed (Corruption Stone Shatter outcome).
     */
    record Destroyed() implements StoneResult {}

    // Factory methods
    static StoneResult success(ItemStack item, ModifiableItem data) {
        return new Success(item, data);
    }

    static StoneResult failure(String reason) {
        return new Failure(reason);
    }

    static StoneResult destroyed() {
        return new Destroyed();
    }

    // Convenience methods
    default boolean isSuccess() { return this instanceof Success; }
    default boolean isFailure() { return this instanceof Failure; }
    default boolean isDestroyed() { return this instanceof Destroyed; }
}
```

---

## Stone Summary

| Stone | Target | Effect | Rarity Requirement |
|-------|--------|--------|-------------------|
| **Uncommon Upgrade Stone** | Both | COMMON → UNCOMMON + 1 mod | COMMON |
| **Rare Upgrade Stone** | Both | UNCOMMON → RARE + 1 mod | UNCOMMON |
| **Epic Upgrade Stone** | Both | RARE → EPIC + 1 mod | RARE |
| **Chaos Stone** | Both | Reroll all unlocked mods | RARE+ |
| **Addition Stone** | Both | Add 1 modifier | Below max |
| **Removal Stone** | Both | Remove 1 unlocked mod | Has unlocked |
| **Cleansing Stone** | Both | Reset to COMMON | Not MYTHIC |
| **Swap Stone** | Both | -1 mod, +1 mod | Has unlocked |
| **Lock Stone** | Both | Lock 1 modifier | Has unlocked |
| **Lesser Divine Stone** | Both | Reroll 1 value | Has unlocked |
| **Greater Divine Stone** | Both | Reroll all values | Has unlocked |
| **Corruption Stone** | Both | Random outcome | Not corrupted |
| **Quality Stone** | Both | Reroll quality 1-100 | Not Perfect |
| **Cartographer's Stone** | Map | +5% IIQ (max 20%) | Below 20% |
| **Horizon Stone** | Map | Change biome | — |
| **Explorer's Stone** | Map | Level ±3 | — |

---

## Files to Create/Modify Summary

### New Files (stones package)
- `ModifiableItem.java` - Sealed interface
- `ModifiableItemBuilder.java` - Sealed builder interface
- `ItemModifier.java` - Common modifier interface
- `StoneType.java` - Stone definitions enum
- `ItemTargetType.java` - Target type enum
- `StoneActions.java` - All stone implementations
- `StoneService.java` - Public API
- `StoneResult.java` - Sealed result type

### Modified Files
- `gear/model/GearData.java` - Add `implements ModifiableItem`, `corrupted` field
- `gear/model/GearModifier.java` - Add `implements ItemModifier`, `locked` field

### New Files (maps package)
- `maps/core/RealmMapData.java` - Implements ModifiableItem
- `maps/modifiers/RealmModifier.java` - Implements ItemModifier
- `maps/modifiers/RealmModifierType.java` - Modifier type enum
- `maps/modifiers/RealmModifierPool.java` - Modifier generation
- `maps/util/RealmMapUtils.java` - ItemStack read/write
