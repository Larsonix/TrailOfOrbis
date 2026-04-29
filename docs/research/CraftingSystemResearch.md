# Crafting System Research

Comprehensive research into Hytale's crafting system for RPG gear integration.

**Research Date**: 2026-01-23
**Status**: Complete
**Priority**: 5 (Crafting System)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Crafting System Architecture](#crafting-system-architecture)
3. [Recipe Definition System](#recipe-definition-system)
4. [Crafting Events & Hooks](#crafting-events--hooks)
5. [Crafting Stations](#crafting-stations)
6. [Crafted Item Output](#crafted-item-output)
7. [Key Injection Points for RPG](#key-injection-points-for-rpg)
8. [Implementation Recommendations](#implementation-recommendations)

---

## Executive Summary

### Questions Answered

| Question | Answer |
|----------|--------|
| How does crafting work? | `CraftingPlugin` → `CraftingManager` component → `CraftingWindow` UI → Queue/Instant crafting via `craftItem()` or `queueCraft()` |
| Are there crafting events? | YES - `CraftRecipeEvent.Pre` (cancellable, before inputs removed), `CraftRecipeEvent.Post` (cancellable, after inputs removed) |
| Can we intercept crafted output? | YES - Cancel `Post` event to prevent output, or listen to `DropItemEvent.Drop` for overflow items |
| How are recipes defined? | JSON files with `MaterialQuantity[]` inputs/outputs, `BenchRequirement[]`, `timeSeconds`, metadata support |
| Can we add custom crafting stations? | YES - Extend `Bench`/`CraftingBench`/`ProcessingBench`, register BlockType, create recipes with `BenchRequirement` |

### Key Findings

1. **Events are Cancellable**: `CraftRecipeEvent.Pre` prevents entire craft, `CraftRecipeEvent.Post` prevents output (inputs consumed)
2. **Metadata Supported**: `MaterialQuantity.metadata` (BsonDocument) passes through to crafted `ItemStack`
3. **Bench Tier System**: Benches have upgrade tiers with crafting time reduction modifiers
4. **Recipe Categories**: Recipes organized by bench ID and categories for UI filtering
5. **Time-Based Crafting**: Recipes with `timeSeconds > 0` are queued, otherwise instant
6. **Nearby Chest Support**: Benches auto-search nearby chests for materials (configurable radius)

---

## Crafting System Architecture

### Core Classes Overview

```
CraftingPlugin (Plugin Entry Point)
    │
    ├── CraftingManager (ECS Component per Player)
    │       ├── craftItem() - Instant crafting
    │       ├── queueCraft() - Time-based crafting
    │       ├── tick() - Process queued jobs
    │       └── giveOutput() - Distribute crafted items
    │
    ├── BenchRecipeRegistry (Recipe Lookup)
    │       ├── addRecipe() - Register recipe
    │       ├── getRecipesForCategory() - Filter by category
    │       └── isValidCraftingMaterial() - Validate inputs
    │
    └── Window System (UI)
            ├── SimpleCraftingWindow - Standard crafting
            ├── DiagramCraftingWindow - Diagram-based
            ├── StructuralCraftingWindow - Building
            ├── ProcessingBenchWindow - Furnaces/kilns
            └── FieldCraftingWindow - Portable crafting
```

### File Locations

| Component | Path |
|-----------|------|
| Plugin | `.../builtin/crafting/CraftingPlugin.java` |
| Manager | `.../builtin/crafting/component/CraftingManager.java` |
| ECS Systems | `.../builtin/crafting/system/PlayerCraftingSystems.java` |
| Recipe Registry | `.../builtin/crafting/BenchRecipeRegistry.java` |
| Recipe Asset | `.../asset/type/item/config/CraftingRecipe.java` |
| Bench Interaction | `.../builtin/crafting/interaction/OpenBenchPageInteraction.java` |

### Complete Crafting Flow

```
Player Opens Crafting UI
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ OpenBenchPageInteraction.interactWithBlock()                │
│ Lines 61-86                                                 │
│ • Validates BenchState                                      │
│ • Creates window (Simple/Diagram/Structural)                │
│ • Opens UI page                                             │
└─────────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ BenchWindow.onOpen0()                                       │
│ Lines 58-67                                                 │
│ • Calls CraftingManager.setBench(x, y, z, blockType)        │
│ • Associates bench with player                              │
└─────────────────────────────────────────────────────────────┘
       │
       ▼
Player selects recipe and quantity
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ SimpleCraftingWindow.handleAction() - CraftRecipeAction     │
│ Lines 41-81                                                 │
│ • Checks if recipe.getTimeSeconds() > 0                     │
└─────────────────────────────────────────────────────────────┘
       │
       ├─────────────────────────────┬─────────────────────────┐
       ▼                             ▼                         │
┌─────────────────────┐    ┌─────────────────────┐            │
│ INSTANT CRAFTING    │    │ QUEUED CRAFTING     │            │
│ (timeSeconds ≤ 0)   │    │ (timeSeconds > 0)   │            │
│                     │    │                     │            │
│ craftItem()         │    │ queueCraft()        │            │
│ Lines 146-181       │    │ Lines 189-205       │            │
└─────────────────────┘    └─────────────────────┘            │
       │                             │                         │
       ▼                             ▼                         │
┌─────────────────────────────────────────────────────────────┐
│ CraftRecipeEvent.Pre (CANCELLABLE)                          │
│ Line 151                                                    │
│ • Can cancel to prevent entire craft                        │
└─────────────────────────────────────────────────────────────┘
       │
       ▼ (if not cancelled)
┌─────────────────────────────────────────────────────────────┐
│ Remove Inputs from Inventory                                │
│ Lines 162 (instant) or 228-244 (queued)                     │
│ • Validates materials available                             │
│ • Removes MaterialQuantity[] from containers                │
└─────────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ CraftRecipeEvent.Post (CANCELLABLE)                         │
│ Line 163                                                    │
│ • Inputs ALREADY consumed                                   │
│ • Can cancel to prevent output (ingredients lost)           │
└─────────────────────────────────────────────────────────────┘
       │
       ▼ (if not cancelled)
┌─────────────────────────────────────────────────────────────┐
│ giveOutput()                                                │
│ Lines 338-342                                               │
│ • getOutputItemStacks() → List<ItemStack>                   │
│ • SimpleItemContainer.addOrDropItemStacks()                 │
│   ├─ Items fit → Added to inventory                         │
│   └─ Overflow → DropItemEvent.Drop → Dropped to ground      │
└─────────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ PlayerCraftEvent (Deprecated - notification only)           │
│ Lines 169-171                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Recipe Definition System

### CraftingRecipe Structure

**File**: `.../asset/type/item/config/CraftingRecipe.java`

```java
public class CraftingRecipe {
    protected String id;                          // Recipe identifier
    protected MaterialQuantity[] input;           // Required ingredients
    protected MaterialQuantity[] outputs;         // All output items
    protected MaterialQuantity primaryOutput;     // Main output (for UI)
    protected int primaryOutputQuantity = 1;      // Primary output amount
    protected BenchRequirement[] benchRequirement; // Which benches can craft
    protected float timeSeconds;                  // Crafting duration (0 = instant)
    protected boolean knowledgeRequired;          // Must learn recipe first
    protected int requiredMemoriesLevel = 1;      // World progression requirement
}
```

### MaterialQuantity Structure

**File**: `.../inventory/MaterialQuantity.java`

```java
public class MaterialQuantity {
    @Nullable protected String itemId;            // Specific item ID
    @Nullable protected String resourceTypeId;    // Resource type (e.g., "Wood")
    protected String tag;                         // Item tag filter
    protected int tagIndex = Integer.MIN_VALUE;
    protected int quantity = 1;                   // Amount required
    @Nullable protected BsonDocument metadata;    // Custom metadata (passed to output!)
}
```

### BenchRequirement Structure

**File**: `.../protocol/BenchRequirement.java`

```java
public class BenchRequirement {
    @Nonnull public BenchType type = BenchType.Crafting;  // Crafting, Processing, etc.
    @Nullable public String id;                           // Bench ID (e.g., "Workbench")
    @Nullable public String[] categories;                 // Recipe categories
    public int requiredTierLevel;                         // Minimum bench tier
}
```

### BenchType Enum

```java
public enum BenchType {
    Crafting(0),           // Standard crafting benches
    Processing(1),         // Furnaces, ovens, kilns
    DiagramCrafting(2),    // Recipe diagram crafting
    StructuralCrafting(3); // Building/construction
}
```

### Example Recipe JSON

**File**: `Assets/Server/Item/Recipes/Salvage/Salvage_Weapon_Axe_Crude.json`

```json
{
  "Input": [
    {
      "ItemId": "Weapon_Axe_Crude",
      "Quantity": 1
    }
  ],
  "PrimaryOutput": {
    "ItemId": "Ingredient_Stick",
    "Quantity": 1
  },
  "Output": [
    { "ItemId": "Ingredient_Stick", "Quantity": 1 },
    { "ItemId": "Rubble_Stone", "Quantity": 1 },
    { "ItemId": "Ingredient_Fibre", "Quantity": 1 },
    { "ItemId": "Ingredient_Tree_Sap", "Quantity": 1 }
  ],
  "BenchRequirement": [
    {
      "Type": "Processing",
      "Id": "Salvagebench"
    }
  ],
  "TimeSeconds": 4
}
```

### Recipe with Metadata

```json
{
  "Output": [
    {
      "ItemId": "Weapon_Sword_Iron",
      "Quantity": 1,
      "Metadata": {
        "rpg": {
          "rarity": "Common",
          "level": 5,
          "bonusStats": { "Strength": 2 }
        }
      }
    }
  ]
}
```

### Recipe Loading Pipeline

```
LoadedAssetsEvent<CraftingRecipe>
    │
    ▼
CraftingPlugin.onRecipeLoad()
    │
    ▼
For each BenchRequirement:
    │
    ▼
BenchRecipeRegistry.addRecipe(requirement, recipe)
    │
    ▼
computeBenchRecipeRegistries() → recompute()
    │
    ▼
Recipes available in UI by bench/category
```

---

## Crafting Events & Hooks

### CraftRecipeEvent (Primary - CANCELLABLE)

**File**: `.../event/events/ecs/CraftRecipeEvent.java`

```java
public class CraftRecipeEvent extends CancellableEcsEvent {
    private final CraftingRecipe craftedRecipe;
    private final int quantity;

    public CraftingRecipe getCraftedRecipe();
    public int getQuantity();

    // Inner classes
    public static class Pre extends CraftRecipeEvent { }   // Before inputs removed
    public static class Post extends CraftRecipeEvent { }  // After inputs removed
}
```

### Event Timing

| Event | When Fired | Cancellation Effect |
|-------|------------|---------------------|
| `CraftRecipeEvent.Pre` | Before materials removed | Entire craft prevented, materials kept |
| `CraftRecipeEvent.Post` | After materials removed, before output | Output prevented, materials LOST |
| `PlayerCraftEvent` | After output given | Cannot cancel (notification only, deprecated) |

### Event Registration Example

```java
getEventRegistry().register(CraftRecipeEvent.Pre.class, event -> {
    CraftingRecipe recipe = event.getCraftedRecipe();
    int quantity = event.getQuantity();

    // Check player level, cancel if too low
    if (playerLevel < requiredLevel) {
        event.setCancelled(true);
        return;
    }
});

getEventRegistry().register(CraftRecipeEvent.Post.class, event -> {
    CraftingRecipe recipe = event.getCraftedRecipe();

    // Could give custom output here and cancel default
    if (shouldCustomize(recipe)) {
        giveCustomOutput(playerRef, recipe);
        event.setCancelled(true); // Prevent default output
    }
});
```

### Related Events

| Event | File | Purpose |
|-------|------|---------|
| `DropItemEvent.Drop` | `.../events/ecs/DropItemEvent.java` | Intercept overflow items dropped to ground |
| `InteractivelyPickupItemEvent` | `.../events/ecs/InteractivelyPickupItemEvent.java` | Intercept item pickup |

---

## Crafting Stations

### Bench Class Hierarchy

```
Bench (Abstract Base)
    │
    ├── CraftingBench (Categories-based)
    │       └── DiagramCraftingBench
    │
    ├── ProcessingBench (Fuel-based: furnaces, kilns)
    │
    └── StructuralCraftingBench (Building/construction)
```

### Bench Properties

**File**: `.../blocktype/config/bench/Bench.java`

```java
public abstract class Bench {
    protected BenchType type;
    protected String id;
    protected String descriptiveLabel;
    protected BenchTierLevel[] tierLevels;

    // Sound events
    protected String localOpenSoundEventId;
    protected String localCloseSoundEventId;
    protected String completedSoundEventId;
    protected String failedSoundEventId;
    protected String benchUpgradeSoundEventId;
    protected String benchUpgradeCompletedSoundEventId;
}
```

### CraftingBench (Categories)

**File**: `.../blocktype/config/bench/CraftingBench.java`

```java
public class CraftingBench extends Bench {
    protected BenchCategory[] categories;

    public static class BenchCategory {
        String id;
        String name;
        String icon;
        BenchItemCategory[] itemCategories;
    }

    public static class BenchItemCategory {
        String id;
        String name;
        String icon;
        String diagram;
        int slots = 1;
        boolean specialSlot = true;
    }
}
```

### ProcessingBench (Fuel-based)

**File**: `.../blocktype/config/bench/ProcessingBench.java`

```java
public class ProcessingBench extends Bench {
    protected ProcessingSlot[] input;      // Input slots
    protected ProcessingSlot[] fuel;       // Fuel slots
    protected int maxFuel;                 // Max fuel capacity
    protected String fuelDropItemId;       // Item when fuel exhausts
    protected int outputSlotsCount;        // Output slots
    protected ExtraOutput extraOutput;     // Bonus outputs
    protected boolean allowNoInputProcessing;

    public static class ProcessingSlot extends BenchSlot {
        String resourceTypeId;             // Resource filter
        boolean filterValidIngredients;
    }
}
```

### Bench Tier System

**File**: `.../blocktype/config/bench/BenchTierLevel.java`

```java
public class BenchTierLevel {
    protected BenchUpgradeRequirement upgradeRequirement;
    protected float craftingTimeReductionModifier;  // 0.0-1.0 reduction
    protected int extraInputSlot;                   // Additional inputs per tier
    protected int extraOutputSlot;                  // Additional outputs per tier
}
```

### BenchState (Runtime State)

**File**: `.../builtin/crafting/state/BenchState.java`

```java
public class BenchState {
    private int tierLevel = 1;            // Current tier
    protected ItemStack[] upgradeItems;   // Items for upgrade
    protected Bench bench;                // Configuration

    public int getTierLevel();
    public void setTierLevel(int level);  // Triggers block state change
    public Bench getBench();
}
```

### Window Types

| Window Class | BenchType | Purpose |
|--------------|-----------|---------|
| `SimpleCraftingWindow` | Crafting | Standard recipe selection |
| `DiagramCraftingWindow` | DiagramCrafting | Visual diagram crafting |
| `StructuralCraftingWindow` | StructuralCrafting | Building recipes |
| `ProcessingBenchWindow` | Processing | Furnaces, kilns, smelters |
| `FieldCraftingWindow` | (Portable) | Inventory-based crafting |

### Creating Custom Stations

**Step 1: Define Bench Configuration**
```java
public class RpgCraftingBench extends CraftingBench {
    protected int requiredPlayerLevel;

    public static final BuilderCodec<RpgCraftingBench> CODEC =
        BuilderCodec.builder(RpgCraftingBench.class,
                           RpgCraftingBench::new,
                           CraftingBench.CODEC).build();
}
```

**Step 2: Create Block JSON**
```json
{
  "Bench": {
    "Type": "Crafting",
    "Id": "RpgForge",
    "DescriptiveLabel": "RPG Forge",
    "TierLevels": [
      {
        "CraftingTimeReductionModifier": 0.0,
        "ExtraInputSlot": 0,
        "ExtraOutputSlot": 0
      },
      {
        "UpgradeRequirement": { ... },
        "CraftingTimeReductionModifier": 0.2,
        "ExtraInputSlot": 1,
        "ExtraOutputSlot": 0
      }
    ],
    "Categories": [
      {
        "Id": "weapons",
        "Name": "Weapons",
        "Icon": "weapon_icon"
      }
    ]
  }
}
```

**Step 3: Create Recipes**
```json
{
  "BenchRequirement": [
    {
      "Type": "Crafting",
      "Id": "RpgForge",
      "Categories": ["weapons"],
      "RequiredTierLevel": 1
    }
  ]
}
```

---

## Crafted Item Output

### ItemStack Generation

**File**: `.../builtin/crafting/component/CraftingManager.java`

```java
// Single output item
public static ItemStack getOutputItemStack(MaterialQuantity outputMaterial, int quantity) {
    String itemId = outputMaterial.getItemId();
    int materialQuantity = outputMaterial.getQuantity() <= 0 ? 1 : outputMaterial.getQuantity();
    return new ItemStack(itemId, materialQuantity * quantity, outputMaterial.getMetadata());
}

// All outputs
public static List<ItemStack> getOutputItemStacks(CraftingRecipe recipe, int quantity) {
    MaterialQuantity[] output = recipe.getOutputs();
    if (output == null) return List.of();

    ObjectArrayList<ItemStack> outputItemStacks = new ObjectArrayList<>();
    for (MaterialQuantity outputMaterial : output) {
        outputItemStacks.add(getOutputItemStack(outputMaterial, quantity));
    }
    return outputItemStacks;
}
```

### Output Distribution

```java
private static void giveOutput(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store,
                               CraftingRecipe recipe, int quantity) {
    Player player = store.getComponent(ref, Player.getComponentType());
    List<ItemStack> itemStacks = getOutputItemStacks(recipe, quantity);

    // Add to inventory, drop overflow
    SimpleItemContainer.addOrDropItemStacks(store, ref,
        player.getInventory().getCombinedArmorHotbarStorage(), itemStacks);
}
```

### Overflow Handling

```java
// In SimpleItemContainer
public static boolean addOrDropItemStacks(ComponentAccessor<EntityStore> store,
                                         Ref<EntityStore> ref,
                                         ItemContainer container,
                                         List<ItemStack> itemStacks) {
    ListTransaction<ItemStackTransaction> transaction = container.addItemStacks(itemStacks);
    boolean droppedItem = false;

    for (ItemStackTransaction stackTransaction : transaction.getList()) {
        ItemStack remainder = stackTransaction.getRemainder();
        if (!ItemStack.isEmpty(remainder)) {
            ItemUtils.dropItem(ref, remainder, store);  // Triggers DropItemEvent.Drop
            droppedItem = true;
        }
    }
    return droppedItem;
}
```

### Metadata Preservation

Metadata flows through the entire pipeline:

```
Recipe Definition (JSON)
    │ Metadata: { "rpg": { "rarity": "Rare" } }
    ▼
MaterialQuantity.metadata (BsonDocument)
    │
    ▼
getOutputItemStack()
    │ new ItemStack(itemId, quantity, outputMaterial.getMetadata())
    ▼
ItemStack.metadata
    │
    ▼
Player Inventory / Ground Drop
```

---

## Key Injection Points for RPG

### 1. CraftRecipeEvent.Pre (Validation)

**Use Case**: Level requirements, recipe restrictions

```java
getEventRegistry().register(CraftRecipeEvent.Pre.class, event -> {
    CraftingRecipe recipe = event.getCraftedRecipe();

    // Get RPG requirements from recipe metadata
    int requiredLevel = getRequiredLevel(recipe);
    int playerLevel = getPlayerLevel(event.getRef());

    if (playerLevel < requiredLevel) {
        event.setCancelled(true);
        sendMessage(event.getRef(), "Level " + requiredLevel + " required!");
    }
});
```

### 2. CraftRecipeEvent.Post (Custom Output)

**Use Case**: Add random stats, generate RPG gear

```java
getEventRegistry().register(CraftRecipeEvent.Post.class, event -> {
    CraftingRecipe recipe = event.getCraftedRecipe();

    if (isRpgRecipe(recipe)) {
        // Generate custom RPG item with random stats
        ItemStack rpgItem = generateRpgItem(recipe, playerLevel);

        // Give custom item
        giveItem(event.getRef(), rpgItem);

        // Cancel default output
        event.setCancelled(true);
    }
});
```

### 3. Recipe Metadata (Static Bonuses)

**Use Case**: Define base stats in recipes

```json
{
  "Output": [{
    "ItemId": "Weapon_Sword_Steel",
    "Quantity": 1,
    "Metadata": {
      "rpg": {
        "baseLevel": 10,
        "rarity": "Uncommon",
        "statRanges": {
          "Strength": { "min": 5, "max": 10 },
          "Agility": { "min": 2, "max": 5 }
        }
      }
    }
  }]
}
```

### 4. Bench Tier Bonuses

**Use Case**: Higher tier = better stats

```java
getEventRegistry().register(CraftRecipeEvent.Post.class, event -> {
    CraftingManager manager = getManager(event.getRef());
    BenchState benchState = getBenchState(manager);
    int tierLevel = benchState.getTierLevel();

    // Apply tier bonus to crafted item
    float tierBonus = 1.0f + (tierLevel - 1) * 0.1f;  // 10% per tier

    ItemStack output = generateOutput(recipe);
    applyTierBonus(output, tierBonus);
});
```

### 5. Custom Crafting Validation

**Use Case**: Check player has required skills

```java
getEventRegistry().register(CraftRecipeEvent.Pre.class, event -> {
    String recipeId = event.getCraftedRecipe().getId();

    // Check if player has learned this recipe via RPG system
    if (!playerKnowsRecipe(event.getRef(), recipeId)) {
        event.setCancelled(true);
        sendMessage(event.getRef(), "You haven't learned this recipe!");
    }

    // Check crafting skill level
    int craftingSkill = getCraftingSkill(event.getRef());
    int requiredSkill = getRecipeSkillRequirement(recipeId);

    if (craftingSkill < requiredSkill) {
        event.setCancelled(true);
        sendMessage(event.getRef(), "Crafting skill " + requiredSkill + " required!");
    }
});
```

---

## Implementation Recommendations

### Recommended Approach: Event-Driven Customization

1. **Recipe Requirements via Pre Event**
   - Check player level/skills before crafting
   - Cancel and notify if requirements not met

2. **Custom Output via Post Event**
   - Generate RPG items with random stats
   - Apply player-based modifiers (luck, skill level)
   - Replace default output with custom items

3. **Static Bonuses via Recipe Metadata**
   - Define base stat ranges in recipe JSON
   - Use `MaterialQuantity.metadata` for configuration
   - Parse metadata in Post event handler

4. **Bench Tier Integration**
   - Access `BenchState.getTierLevel()` during crafting
   - Apply tier-based quality bonuses
   - Higher tiers = better stat rolls

### Example RPG Crafting Integration

```java
public class RpgCraftingHandler {

    public void register(EventRegistry registry) {
        // Pre-craft validation
        registry.register(CraftRecipeEvent.Pre.class, this::onPreCraft);

        // Post-craft customization
        registry.register(CraftRecipeEvent.Post.class, this::onPostCraft);
    }

    private void onPreCraft(CraftRecipeEvent.Pre event) {
        CraftingRecipe recipe = event.getCraftedRecipe();
        Ref<EntityStore> playerRef = event.getRef();

        // Check level requirement
        int requiredLevel = getRpgRequiredLevel(recipe);
        int playerLevel = rpgManager.getPlayerLevel(playerRef);

        if (playerLevel < requiredLevel) {
            event.setCancelled(true);
            notifyPlayer(playerRef, "Requires level " + requiredLevel);
            return;
        }

        // Check crafting skill
        int requiredSkill = getRpgRequiredSkill(recipe);
        int craftingSkill = rpgManager.getCraftingSkill(playerRef);

        if (craftingSkill < requiredSkill) {
            event.setCancelled(true);
            notifyPlayer(playerRef, "Requires Crafting " + requiredSkill);
        }
    }

    private void onPostCraft(CraftRecipeEvent.Post event) {
        CraftingRecipe recipe = event.getCraftedRecipe();
        Ref<EntityStore> playerRef = event.getRef();

        // Check if this is an RPG recipe
        if (!isRpgRecipe(recipe)) {
            return;  // Let default output happen
        }

        // Get crafting context
        int playerLevel = rpgManager.getPlayerLevel(playerRef);
        int craftingSkill = rpgManager.getCraftingSkill(playerRef);
        int benchTier = getBenchTier(playerRef);

        // Generate RPG item with random stats
        for (int i = 0; i < event.getQuantity(); i++) {
            ItemStack rpgItem = rpgItemGenerator.generate(
                recipe,
                playerLevel,
                craftingSkill,
                benchTier
            );

            giveItemToPlayer(playerRef, rpgItem);
        }

        // Cancel default output
        event.setCancelled(true);
    }
}
```

### Key Files Reference

| Purpose | File Path |
|---------|-----------|
| Crafting Events | `.../event/events/ecs/CraftRecipeEvent.java` |
| Crafting Manager | `.../builtin/crafting/component/CraftingManager.java` |
| Recipe Definition | `.../asset/type/item/config/CraftingRecipe.java` |
| Material Quantity | `.../inventory/MaterialQuantity.java` |
| Bench Configuration | `.../blocktype/config/bench/Bench.java` |
| Bench State | `.../builtin/crafting/state/BenchState.java` |
| Recipe Registry | `.../builtin/crafting/BenchRecipeRegistry.java` |
| Crafting Plugin | `.../builtin/crafting/CraftingPlugin.java` |
| Crafting Windows | `.../builtin/crafting/window/*.java` |
| Crafting Config | `.../asset/type/gameplay/CraftingConfig.java` |

---

## Summary

The Hytale crafting system provides excellent hooks for RPG integration:

1. **CraftRecipeEvent.Pre** - Block crafting based on level/skill requirements
2. **CraftRecipeEvent.Post** - Replace output with custom RPG items
3. **MaterialQuantity.metadata** - Pass configuration through to crafted items
4. **BenchState.tierLevel** - Apply quality bonuses based on bench tier
5. **BenchRecipeRegistry** - Filter recipes by requirements

**Recommended Strategy**:
- Use `CraftRecipeEvent.Pre` for level/skill validation
- Use `CraftRecipeEvent.Post` to generate custom RPG items with random stats
- Store RPG config in recipe metadata (BsonDocument)
- Apply bench tier bonuses to stat generation
- Cancel default output and give custom items

This architecture allows full RPG crafting integration while preserving vanilla crafting for non-RPG recipes.
