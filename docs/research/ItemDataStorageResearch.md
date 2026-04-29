# Item Data Storage Research

Research findings from investigating the Hytale decompiled codebase for item data storage systems.

**Research Date**: 2026-01-23
**Source**: `/home/larsonix/work/Hytale-Decompiled-Full-Game`

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Core Architecture](#core-architecture)
3. [ItemStack - Runtime Instance](#itemstack---runtime-instance)
4. [Item - Definition/Configuration](#item---definitionconfiguration)
5. [Custom Data Storage (Metadata)](#custom-data-storage-metadata)
6. [Serialization](#serialization)
7. [Item Categories](#item-categories)
8. [Inventory & Container System](#inventory--container-system)
9. [Item Entity (Dropped Items)](#item-entity-dropped-items)
10. [Extension Points for Our Plugin](#extension-points-for-our-plugin)
11. [Key File Locations](#key-file-locations)
12. [Implications for Gear System](#implications-for-gear-system)

---

## Executive Summary

### Key Findings

| Aspect | Finding | Impact on Plugin |
|--------|---------|------------------|
| **Custom Data** | ✅ BsonDocument metadata on ItemStack | We CAN store rarity, quality, modifiers, level |
| **Mutability** | ItemStack is immutable (returns new instances) | Must use `withMetadata()` pattern |
| **Durability** | ✅ Native durability field exists | Can leverage or extend existing system |
| **Quality System** | ✅ Native "Quality" field in Item definitions | May be able to hook into existing system |
| **Serialization** | Codec-based, metadata persists | Our custom data WILL be saved |
| **Item Types** | Weapon/Armor/Tool configs exist | Can extend or work alongside |

### Verdict: FEASIBLE

The metadata system (`BsonDocument`) is essentially an NBT-like system that allows arbitrary key-value storage on any item. This is exactly what we need.

---

## Core Architecture

Hytale uses a **two-class pattern** for items:

```
┌─────────────────────────────────────────────────────────────┐
│                      ITEM DEFINITION                        │
│  Item.java - Loaded from asset configs                      │
│  - Defines base properties (maxStack, maxDurability, etc.)  │
│  - Contains Weapon/Armor/Tool configs                       │
│  - Immutable at runtime, cached                             │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ references via itemId
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      ITEM INSTANCE                          │
│  ItemStack.java - Runtime representation                    │
│  - Holds itemId, quantity, durability                       │
│  - Contains BsonDocument metadata (CUSTOM DATA!)            │
│  - Immutable (all modifications return new instance)        │
└─────────────────────────────────────────────────────────────┘
```

---

## ItemStack - Runtime Instance

**Location**: `com/hypixel/hytale/server/core/inventory/ItemStack.java`

This is the **primary class** we'll interact with. Every item in a player's inventory, on the ground, or in a container is an ItemStack.

### Fields

```java
public class ItemStack implements NetworkSerializable<ItemWithAllMetadata> {
    protected String itemId;              // Reference to Item definition (e.g., "Weapon_Axe_Iron")
    protected int quantity = 1;           // Stack size (1 to maxStack)
    protected double durability;          // Current durability (0 to maxDurability)
    protected double maxDurability;       // Max durability (0 = unbreakable)
    protected boolean overrideDroppedItemAnimation;
    protected BsonDocument metadata;      // ⭐ CUSTOM DATA STORAGE - This is our key!
    private ItemWithAllMetadata cachedPacket;
}
```

### Key Methods

```java
// Get the Item definition
public Item getItem()

// Quantity operations (returns new ItemStack)
public ItemStack withQuantity(int quantity)

// Durability operations (returns new ItemStack)
public ItemStack withDurability(double durability)
public ItemStack withRestoredDurability(double maxDurability)
public ItemStack withIncreasedDurability(double increment)

// ⭐ METADATA OPERATIONS - This is how we store custom data
public ItemStack withMetadata(@Nullable BsonDocument metadata)
public <T> ItemStack withMetadata(@Nonnull String key, @Nonnull Codec<T> codec, @Nullable T data)
public <T> T getFromMetadataOrNull(@Nonnull String key, @Nonnull Codec<T> codec)

// State variants
public ItemStack withState(String state)

// Stacking check (compares itemId, durability, maxDurability, AND metadata)
public boolean isStackableWith(ItemStack other)
```

### Immutability Pattern

**Critical**: ItemStack is immutable. All modifications return a NEW ItemStack:

```java
// WRONG - This does nothing
itemStack.withDurability(50);

// CORRECT - Capture the new instance
ItemStack newStack = itemStack.withDurability(50);
```

---

## Item - Definition/Configuration

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/Item.java`

This defines the **base template** for an item type. Loaded from JSON asset files.

### Fields

```java
public class Item implements JsonAssetWithMap<String, DefaultAssetMap<String, Item>> {
    protected String id;                          // Unique item ID
    protected String icon;                        // UI icon path
    protected int maxStack = 100;                 // Max stack size
    protected double maxDurability;               // Base durability

    // Type-specific configs (only one is set based on item type)
    protected ItemWeapon weapon;                  // Weapon properties
    protected ItemArmor armor;                    // Armor properties
    protected ItemTool tool;                      // Tool properties
    protected ItemGlider glider;                  // Glider properties
    protected ItemUtility utility;                // Utility properties

    // Visual
    protected String model;                       // 3D model path
    protected String texture;                     // Texture path
    protected ModelParticle[] particles;          // Particle effects

    // Behavior
    protected boolean consumable;                 // Is consumable
    protected boolean variant;                    // Is variant of another
    protected boolean dropOnDeath;                // Drops on player death
    protected Map<InteractionType, String> interactions;  // Click handlers
    protected Map<String, String> interactionVars;        // Interaction variables

    // Container/storage
    protected ItemStackContainerConfig itemStackContainerConfig;
    protected ItemEntityConfig itemEntityConfig;  // Physics when dropped
}
```

### Item Lookup

```java
// Get Item definition from registry
Item axe = Item.getAssetMap().getAsset("Weapon_Axe_Iron");

// Get Item from an ItemStack
Item itemDef = itemStack.getItem();
```

---

## Custom Data Storage (Metadata)

### The BsonDocument System

Hytale uses **BSON (Binary JSON)** for custom item metadata. This is functionally equivalent to Minecraft's NBT system.

```java
// ItemStack has a BsonDocument field
protected BsonDocument metadata;
```

### Writing Custom Data

```java
// Method 1: Replace entire metadata document
BsonDocument meta = new BsonDocument();
meta.put("Rarity", new BsonString("Epic"));
meta.put("Quality", new BsonInt32(78));
meta.put("ItemLevel", new BsonInt32(25));
ItemStack newStack = itemStack.withMetadata(meta);

// Method 2: Add/modify single key with codec (preferred)
ItemStack newStack = itemStack.withMetadata("Rarity", Codec.STRING, "Epic");
ItemStack newStack2 = newStack.withMetadata("Quality", Codec.INTEGER, 78);
```

### Reading Custom Data

```java
// Read with codec
String rarity = itemStack.getFromMetadataOrNull("Rarity", Codec.STRING);
Integer quality = itemStack.getFromMetadataOrNull("Quality", Codec.INTEGER);

// Returns null if key doesn't exist
if (rarity != null) {
    // Item has our custom rarity
}
```

### Complex Data with Custom Codecs

For storing complex objects (like our modifier list), we can create custom codecs:

```java
// Example: Storing a list of modifiers
public class GearModifier {
    String type;      // "sharp", "blazing", etc.
    int tier;
    double value;
}

// Create a codec for it
public static final BuilderCodec<GearModifier> MODIFIER_CODEC =
    BuilderCodec.builder(GearModifier.class, GearModifier::new)
        .append(new KeyedCodec<>("Type", Codec.STRING), ...)
        .append(new KeyedCodec<>("Tier", Codec.INTEGER), ...)
        .append(new KeyedCodec<>("Value", Codec.DOUBLE), ...)
        .build();

// Store in metadata
ItemStack newStack = itemStack.withMetadata("Modifiers",
    Codec.list(MODIFIER_CODEC), modifierList);
```

### Metadata Persistence

**Metadata is persisted** through the codec system:

```java
public static final BuilderCodec<ItemStack> CODEC =
    BuilderCodec.builder(ItemStack.class, ItemStack::new)
        // ... other fields ...
        .append(new KeyedCodec<BsonDocument>("Metadata", Codec.BSON_DOCUMENT),
                (itemStack, bsonDocument) -> itemStack.metadata = bsonDocument,
                itemStack -> itemStack.metadata)
        .build();
```

This means our custom data **will be saved and loaded** automatically.

---

## Serialization

### Network Serialization

**Location**: `com/hypixel/hytale/protocol/ItemWithAllMetadata.java`

When items are sent over the network:

```java
public class ItemWithAllMetadata {
    public String itemId;
    public int quantity;
    public double durability;
    public double maxDurability;
    public boolean overrideDroppedItemAnimation;
    public String metadata;  // Metadata as JSON string
}
```

**Note**: Metadata is serialized as a JSON string for network transmission.

### Persistence Serialization

Uses `BuilderCodec` pattern with BSON encoding. Full fidelity is maintained.

---

## Item Categories

### ItemWeapon

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemWeapon.java`

```java
public class ItemWeapon {
    // Stat modifiers applied when equipped
    protected Map<String, List<EntityStatModifier>> statModifiers;

    // Entity stats to clear when this weapon is equipped
    protected List<String> entityStatsToClear;

    // Visual
    protected String dualWieldedHeldModel;
}
```

**Interesting**: Weapons already have a `statModifiers` system! We may be able to work with or extend this.

### ItemArmor

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemArmor.java`

```java
public class ItemArmor {
    // Which slot this armor goes in
    protected String slot;

    // Damage and knockback
    protected ItemArmorDamageProperties damageProperties;
    protected ItemArmorKnockbackProperties knockbackProperties;

    // Visual
    protected boolean hideCosmetics;
    protected String animation;
}
```

### ItemTool

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemTool.java`

```java
public class ItemTool {
    // Tool specifications per block type
    protected Map<String, ToolSpec> toolSpecs;
}

public class ToolSpec {
    float breakSpeed;           // How fast it breaks blocks
    float durabilityLossBase;   // Durability loss per use
}
```

---

## Inventory & Container System

### Inventory Structure

**Location**: `com/hypixel/hytale/server/core/inventory/Inventory.java`

```java
public class Inventory implements NetworkSerializable<UpdatePlayerInventory> {
    protected ItemContainer storage;      // Main inventory grid
    protected ItemContainer armor;        // Armor slots
    protected ItemContainer hotbar;       // Hotbar (9 slots)
    protected ItemContainer utility;      // Utility slots (4 slots)
    protected ItemContainer tools;        // Tool slots (23 slots)
    protected ItemContainer backpack;     // Backpack storage
}
```

### ItemContainer

**Location**: `com/hypixel/hytale/server/core/inventory/container/ItemContainer.java`

Abstract container with:
- Item filtering by type/tag
- Add/remove/move operations
- Change event tracking
- Transaction support

---

## Item Entity (Dropped Items)

**Location**: `com/hypixel/hytale/server/core/modules/entity/item/ItemComponent.java`

When items are dropped in the world:

```java
public class ItemComponent implements Component<EntityStore> {
    private ItemStack itemStack;           // The item data (includes our metadata!)
    private float mergeDelay = 1.5f;       // Delay before merging with similar items
    private float pickupDelay = 0.5f;      // Delay before player can pick up
    private float pickupThrottle = 0.25f;  // Throttle pickup attempts
    private boolean removedByPlayerPickup;
}
```

**Important**: Dropped items preserve the full ItemStack including metadata.

---

## Extension Points for Our Plugin

### 1. Metadata Storage (Primary Method)

Store all our custom gear data in the metadata BsonDocument:

```java
// Our custom gear data structure
{
    "RPG:Level": 25,
    "RPG:Rarity": "Epic",
    "RPG:Quality": 78,
    "RPG:Durability": {
        "Current": 847,
        "Max": 1000
    },
    "RPG:Prefixes": [
        {"Type": "sharp", "Tier": 3, "Value": 28},
        {"Type": "blazing", "Tier": 2, "Value": 15}
    ],
    "RPG:Suffixes": [
        {"Type": "of_strength", "Tier": 3, "Value": 22},
        {"Type": "of_speed", "Tier": 2, "Value": 12}
    ]
}
```

**Namespace with "RPG:" prefix** to avoid conflicts with other systems.

### 2. Existing Quality System

The Item definition has a `quality` field and references `ItemQuality`. We should investigate if we can:
- Use the existing quality system for our rarity
- Or create our own parallel system in metadata

### 3. Stat Modifiers

`ItemWeapon.statModifiers` already exists. Options:
- Hook into this system to apply our modifier bonuses
- Create our own stat application system that runs in addition

### 4. Durability

Native durability exists on ItemStack. Options:
- Use the existing `durability`/`maxDurability` fields directly
- Store our own in metadata if we need different behavior

---

## Key File Locations

| Component | Path |
|-----------|------|
| **ItemStack** (Runtime) | `com/hypixel/hytale/server/core/inventory/ItemStack.java` |
| **Item** (Definition) | `com/hypixel/hytale/server/core/asset/type/item/config/Item.java` |
| **ItemWeapon** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemWeapon.java` |
| **ItemArmor** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemArmor.java` |
| **ItemTool** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemTool.java` |
| **ItemQuality** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemQuality.java` |
| **ItemContainer** | `com/hypixel/hytale/server/core/inventory/container/ItemContainer.java` |
| **Inventory** | `com/hypixel/hytale/server/core/inventory/Inventory.java` |
| **ItemComponent** | `com/hypixel/hytale/server/core/modules/entity/item/ItemComponent.java` |
| **Network Packet** | `com/hypixel/hytale/protocol/ItemWithAllMetadata.java` |
| **ItemEntityConfig** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemEntityConfig.java` |

---

## Implications for Gear System

### What We CAN Do

| Feature | How | Confidence |
|---------|-----|------------|
| Store item level | `metadata.put("RPG:Level", level)` | ✅ High |
| Store rarity | `metadata.put("RPG:Rarity", rarity)` | ✅ High |
| Store quality | `metadata.put("RPG:Quality", quality)` | ✅ High |
| Store modifiers | `metadata.put("RPG:Modifiers", list)` | ✅ High |
| Custom durability | Use native or metadata | ✅ High |
| Data persistence | Automatic via codec | ✅ High |
| Data on dropped items | Preserved in ItemComponent | ✅ High |

### What Needs More Research

| Feature | Research Needed |
|---------|-----------------|
| Blocking equip by level | Equipment events investigation |
| Applying stat bonuses | Combat/stat system investigation |
| Visual rarity indicators | Rendering/UI API investigation |
| Intercepting drops | Loot/death event investigation |
| Crafting restrictions | Crafting system investigation |

### Recommended Data Structure

```java
// Namespace all keys with "RPG:" to avoid conflicts
public static class MetadataKeys {
    public static final String LEVEL = "RPG:Level";
    public static final String RARITY = "RPG:Rarity";
    public static final String QUALITY = "RPG:Quality";
    public static final String PREFIXES = "RPG:Prefixes";
    public static final String SUFFIXES = "RPG:Suffixes";
    public static final String DURABILITY_CURRENT = "RPG:DurabilityCurrent";
    public static final String DURABILITY_MAX = "RPG:DurabilityMax";
    public static final String IS_UNIQUE = "RPG:IsUnique";
    public static final String UNIQUE_ID = "RPG:UniqueId";
}
```

### Stacking Consideration

**Important**: `ItemStack.isStackableWith()` compares metadata for equality. This means:
- Two items with different RPG stats will NOT stack (correct behavior!)
- We don't need to disable stacking manually

---

## Next Research Steps

Based on these findings, the next priority research areas are:

1. **Equipment Events** - How to detect equip/unequip, block equipping
2. **Stat/Combat System** - How to apply our modifier bonuses to damage
3. **Loot/Drop System** - How to intercept mob deaths and generate custom drops

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial research findings |
