# Hytale Crafting Skill

Use this skill when working with crafting systems in Hytale plugins. This covers the `CraftingPlugin` API for managing recipes, crafting benches, recipe knowledge, and crafting events.

> **Related skills:** For item creation and item registry, see `docs/hytale-api/items.md`. For event handling (including `CraftRecipeEvent`), see `docs/hytale-api/events.md`. For inventory management during crafting, see `docs/hytale-api/inventory.md`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Get all recipes for a bench | `CraftingPlugin.getBenchRecipes(bench)` |
| Get recipes by bench type + category | `CraftingPlugin.getBenchRecipes(benchType, benchId, category)` |
| Check available recipes for a category | `CraftingPlugin.getAvailableRecipesForCategory(benchId, categoryId)` |
| Teach a recipe to a player | `CraftingPlugin.learnRecipe(ref, recipeId, componentAccessor)` |
| Remove a learned recipe | `CraftingPlugin.forgetRecipe(ref, itemId, componentAccessor)` |
| Sync known recipes to client | `CraftingPlugin.sendKnownRecipes(ref, componentAccessor)` |
| Validate crafting material for bench | `CraftingPlugin.isValidCraftingMaterialForBench(benchState, itemStack)` |
| Validate upgrade material for bench | `CraftingPlugin.isValidUpgradeMaterialForBench(benchState, itemStack)` |
| Block a crafting attempt | Extend `EntityEventSystem<EntityStore, CraftRecipeEvent.Pre>`, call `setCancelled(true)` |
| React after crafting completes | Extend `EntityEventSystem<EntityStore, CraftRecipeEvent.Post>` |

---

## Key Classes

### Imports

```java
// Plugin entry point
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;

// Recipe data
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

// Bench types and state
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.builtin.crafting.state.BenchState;

// Recipe ingredients
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

// Crafting events
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;

// Interactions
import com.hypixel.hytale.builtin.crafting.interaction.LearnRecipeInteraction;

// ECS fundamentals (for event systems)
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
```

---

## CraftingPlugin API

`CraftingPlugin` is the central singleton for all crafting operations. Access it via `CraftingPlugin.get()`.

### Recipe Query Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getBenchRecipes(Bench bench)` | `List<CraftingRecipe>` | All recipes for a specific bench instance |
| `getBenchRecipes(BenchType type, String name)` | `List<CraftingRecipe>` | All recipes for bench by type and ID |
| `getBenchRecipes(BenchType type, String benchId, String category)` | `List<CraftingRecipe>` | Filtered by bench type, ID, and optional category |
| `getAvailableRecipesForCategory(String benchId, String categoryId)` | `Set<String>` or `null` | Recipe IDs available in a specific bench category |

### Material Validation Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `isValidCraftingMaterialForBench(BenchState, ItemStack)` | `boolean` | Check if an item can be used as a crafting input at this bench |
| `isValidUpgradeMaterialForBench(BenchState, ItemStack)` | `boolean` | Check if an item can be used to upgrade this bench's tier |

### Recipe Knowledge Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `learnRecipe(Ref<EntityStore>, String recipeId, ComponentAccessor)` | `boolean` | Teach a recipe to a player. Returns `true` if newly learned. |
| `forgetRecipe(Ref<EntityStore>, String itemId, ComponentAccessor)` | `boolean` | Remove a recipe from a player's known set. Returns `true` if removed. |
| `sendKnownRecipes(Ref<EntityStore>, ComponentAccessor)` | `void` | Sync the player's known recipes to the client |

**Note:** `learnRecipe` stores the recipe ID in `PlayerConfigData.knownRecipes` and automatically calls `sendKnownRecipes` to sync. Known recipes are persisted across sessions.

---

## CraftingRecipe

`CraftingRecipe` is a JSON asset loaded from `Server/Item/Recipes/` (or generated from `Item` assets at load time). It defines what goes in, what comes out, and where the crafting happens.

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | Unique recipe identifier |
| `input` | `MaterialQuantity[]` | Required input materials |
| `primaryOutput` | `MaterialQuantity` | Main output item |
| `outputs` | `MaterialQuantity[]` | All output items (includes primary if set) |
| `primaryOutputQuantity` | `int` | Output quantity (default: 1) |
| `benchRequirement` | `BenchRequirement[]` | Which benches can craft this (type, ID, categories, tier) |
| `timeSeconds` | `float` | Crafting duration in seconds (0 = instant) |
| `knowledgeRequired` | `boolean` | Whether the player must know this recipe first |
| `requiredMemoriesLevel` | `int` | Minimum Memories level (starts from 1, so level 1 = always available) |

### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getInput()` | `MaterialQuantity[]` | Input materials |
| `getOutputs()` | `MaterialQuantity[]` | All output items |
| `getPrimaryOutput()` | `MaterialQuantity` | Main output |
| `getBenchRequirement()` | `BenchRequirement[]` | Bench requirements |
| `getTimeSeconds()` | `float` | Crafting time |
| `isKnowledgeRequired()` | `boolean` | Requires recipe knowledge |
| `getRequiredMemoriesLevel()` | `int` | Min Memories level |
| `isRestrictedByBenchTierLevel(String benchId, int tierLevel)` | `boolean` | Whether the bench tier is too low |
| `getId()` | `String` | Recipe ID |
| `toPacket(String id)` | `CraftingRecipe (protocol)` | Convert to client packet |

### Accessing the Asset Store

```java
// Get all loaded recipes
DefaultAssetMap<String, CraftingRecipe> allRecipes = CraftingRecipe.getAssetMap();

// Look up a specific recipe
CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset("Iron_Sword_Recipe");
```

---

## BenchType Enum

`BenchType` determines the crafting UI and behavior:

| Value | Description |
|-------|-------------|
| `Crafting` | Standard grid-based crafting bench (supports `knowledgeRequired`) |
| `DiagramCrafting` | Diagram/blueprint crafting (supports `knowledgeRequired`, max 1 output) |
| `StructuralCrafting` | Structural building/construction bench |

**Constraint:** `KnowledgeRequired` can only be set on `Crafting` and `DiagramCrafting` bench types. Validation fails for other types.

**Constraint:** `DiagramCrafting` recipes can only have 1 output item.

---

## BenchRequirement

Each recipe can require one or more benches. The `BenchRequirement` specifies:

| Field | Type | Description |
|-------|------|-------------|
| `type` | `BenchType` | Required bench type |
| `id` | `String` | Bench asset ID (e.g., `"Fieldcraft"`, `"Anvil"`) |
| `categories` | `String[]` | Optional category filters |
| `requiredTierLevel` | `int` | Minimum bench tier level |

**Special bench:** The `"Fieldcraft"` bench ID represents pocket/field crafting (no physical bench required). Fieldcraft recipes should not have `TimeSeconds` set.

---

## BenchState

`BenchState` is a block state attached to crafting bench blocks in the world. It tracks:

| Property | Type | Description |
|----------|------|-------------|
| `tierLevel` | `int` | Current bench tier (starts at 1) |
| `upgradeItems` | `ItemStack[]` | Items deposited for upgrade |
| `bench` | `Bench` | The bench asset configuration |
| `windows` | `Map<UUID, BenchWindow>` | Active crafting windows per player |

### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getTierLevel()` | `int` | Current tier |
| `setTierLevel(int)` | `void` | Change tier (triggers visual update) |
| `getBench()` | `Bench` | Bench configuration |
| `getNextLevelUpgradeMaterials()` | `BenchUpgradeRequirement` | Materials needed for next tier |
| `addUpgradeItems(List<ItemStack>)` | `void` | Add items toward upgrade |

---

## CraftRecipeEvent

`CraftRecipeEvent` is a `CancellableEcsEvent` fired when a player crafts. It has two phases:

### CraftRecipeEvent.Pre

Fired **before** crafting occurs. Cancel it to prevent the craft.

```java
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class CraftBlockerSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    @Override
    protected void processEvent(Store<EntityStore> store, Ref<EntityStore> ref, CraftRecipeEvent.Pre event) {
        CraftingRecipe recipe = event.getCraftedRecipe();
        int quantity = event.getQuantity();

        // Example: block crafting of specific recipes
        if (recipe.getId().equals("Forbidden_Weapon_Recipe")) {
            event.setCancelled(true);
        }
    }
}
```

### CraftRecipeEvent.Post

Fired **after** crafting completes. Use for granting XP, updating stats, etc.

```java
public class CraftXPSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {

    @Override
    protected void processEvent(Store<EntityStore> store, Ref<EntityStore> ref, CraftRecipeEvent.Post event) {
        CraftingRecipe recipe = event.getCraftedRecipe();
        // Grant crafting XP based on recipe complexity
        int xp = recipe.getInput().length * 10;
        // ... grant XP to player
    }
}
```

### Event Properties

| Method | Returns | Description |
|--------|---------|-------------|
| `getCraftedRecipe()` | `CraftingRecipe` | The recipe being crafted |
| `getQuantity()` | `int` | Number of items being crafted |
| `setCancelled(boolean)` | `void` | Cancel the craft (Pre only, but method exists on both) |

**Registration:** Register these as ECS event systems in your plugin's `start()` method:

```java
@Override
protected void start() {
    this.getEntityStoreRegistry().registerSystem(new CraftBlockerSystem());
    this.getEntityStoreRegistry().registerSystem(new CraftXPSystem());
}
```

---

## LearnRecipeInteraction

`LearnRecipeInteraction` is a built-in `SimpleInstantInteraction` that teaches recipes when triggered. It can be used in JSON interaction configs.

### How It Works

1. Checks the held item's metadata for an `"ItemId"` key
2. If not found, falls back to the interaction's configured `itemId`
3. Calls `CraftingPlugin.learnRecipe()` with the resolved ID
4. Sends success/failure message to the player

### JSON Usage

```json
{
  "Type": "LearnRecipe",
  "ItemId": "Iron_Sword"
}
```

### Programmatic Alternative

```java
// Directly teach a recipe without the interaction system
Ref<EntityStore> playerRef = /* ... */;
ComponentAccessor<EntityStore> accessor = /* ... */;

boolean learned = CraftingPlugin.learnRecipe(playerRef, "Iron_Sword", accessor);
if (learned) {
    // Player now knows the Iron Sword recipe
}
```

---

## Recipe JSON Format

Recipes are defined in `Server/Item/Recipes/` as JSON files:

```json
{
  "Input": [
    { "ItemId": "Iron_Ingot", "Quantity": 3 },
    { "ItemId": "Wood_Stick", "Quantity": 1 }
  ],
  "PrimaryOutput": { "ItemId": "Weapon_Sword_Iron", "Quantity": 1 },
  "BenchRequirement": [
    {
      "Type": "Crafting",
      "Id": "Anvil",
      "Categories": ["Weapons"],
      "RequiredTierLevel": 1
    }
  ],
  "TimeSeconds": 5.0,
  "KnowledgeRequired": true,
  "RequiredMemoriesLevel": 1
}
```

### Auto-Generated Recipes

Items can define recipes inline via their item JSON. When an `Item` asset has `hasRecipesToGenerate() == true`, the `CraftingPlugin` automatically generates `CraftingRecipe` assets at load time with IDs like `{ItemId}_Recipe_Generated_{index}`.

---

## Common Patterns

### Granting a Recipe on Quest Completion

```java
public void onQuestComplete(Ref<EntityStore> playerRef, Store<EntityStore> store) {
    boolean learned = CraftingPlugin.learnRecipe(playerRef, "Legendary_Sword", store);
    if (learned) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("rpg.recipe.unlocked"));
        }
    }
}
```

### Querying Available Recipes for a Bench

```java
Bench bench = benchState.getBench();
List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(bench);

for (CraftingRecipe recipe : recipes) {
    // Check if recipe is tier-restricted
    if (!recipe.isRestrictedByBenchTierLevel(bench.getId(), benchState.getTierLevel())) {
        // Recipe is available at this bench's current tier
    }
}
```

### Checking if a Player Knows a Recipe

Recipe knowledge is stored in `PlayerConfigData`:

```java
Player player = store.getComponent(ref, Player.getComponentType());
Set<String> knownRecipes = player.getPlayerConfigData().getKnownRecipes();
boolean knows = knownRecipes.contains("Iron_Sword");
```

---

## Best Practices

1. **Recipe IDs are item IDs for knowledge**: `learnRecipe` stores the recipe ID, but `sendKnownRecipes` looks up items by that ID to find matching recipes. This means recipe knowledge is keyed by the output item ID.

2. **Fieldcraft has no delay**: Recipes with `BenchRequirement.id == "Fieldcraft"` should have `TimeSeconds: 0`. The validator warns about this.

3. **DiagramCrafting is single-output**: `DiagramCrafting` recipes are validated to have at most 1 output item. Use standard `Crafting` for multi-output recipes.

4. **Bench tiers gate recipes**: Use `requiredTierLevel` in `BenchRequirement` to create progression. Players upgrade benches by depositing materials via `BenchState.addUpgradeItems()`.

5. **Recipe hot-reloading**: The `CraftingPlugin` listens for `LoadedAssetsEvent<CraftingRecipe>` and `RemovedAssetsEvent<CraftingRecipe>`, so recipes can be added/removed at runtime via asset pack changes without a server restart.

6. **Thread safety**: `learnRecipe`, `forgetRecipe`, and `sendKnownRecipes` must be called with a valid `Ref<EntityStore>` and `ComponentAccessor`. Ensure you're on the correct world thread.

---

## Integration Notes (TrailOfOrbis)

Potential uses for TrailOfOrbis:

- **Skill-gated crafting**: Block recipes via `CraftRecipeEvent.Pre` unless the player has allocated specific skill tree nodes
- **Crafting XP**: Grant leveling XP on `CraftRecipeEvent.Post` based on recipe complexity
- **Recipe unlocks**: Use `learnRecipe()` as rewards for realm completions or milestone levels
- **Custom RPG benches**: Gate recipes behind RPG-specific bench types using `BenchRequirement` categories

---

## Resources

- [Hytale Modding — Item Interaction](https://hytalemodding.dev/docs/en/guides/plugin/item-interaction) (covers recipe basics)
