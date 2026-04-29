# Reskin / Transmog System — Architecture & Pitfalls

Complete reference for the Builder's Workbench skin-change system. Written from hard-won experience — every section documents a real bug that took hours to find.

## Architecture Overview

Three components, each with a single responsibility:

| Component | Event | Role |
|-----------|-------|------|
| `ReskinRecipeGenerator` | Startup | Generates StructuralCrafting recipes grouped by (slot, quality) for armor, (slot, quality, weaponType) for weapons |
| `ReskinDataPreserver` | `InventoryChangeEvent` | Caches GearData when RPG item enters the workbench input |
| `ReskinCraftInterceptor` | `CraftRecipeEvent.Pre` | Cancels vanilla craft, consumes input, gives reskinned RPG output |

### Flow

```
Player places RPG item in workbench
  → InventoryChangeEvent fires
  → ReskinDataPreserver caches GearData (rarity, level, modifiers, gems, everything)

Player selects skin variant, clicks Craft
  → CraftRecipeEvent.Pre fires (from queueCraft)
  → ReskinCraftInterceptor:
      1. Checks recipe ID prefix (rpg_reskin_*)
      2. Consumes cached GearData (atomic remove from cache)
      3. Cancels the event (prevents vanilla queueCraft job)
      4. Clears workbench input slot (filter=false!)
      5. Creates reskinned RPG item (same stats, new baseItemId)
      6. Gives to player inventory
      7. Syncs item definition to client
```

## Critical Pitfalls (Each Was a Real Bug)

### 1. CraftRecipeEvent.Pre fires from queueCraft()

Despite the decompiled code suggesting `queueCraft()` doesn't fire events, the current Hytale build (2026.03.26) DOES fire `CraftRecipeEvent.Pre` from `queueCraft()` and `CraftRecipeEvent.Post` from `tick()`. The decompiled code may be from an older version. **Always verify empirically.**

### 2. Slot filter blocks clearing with null

`setItemStackForSlot(slot, null)` defaults to `filter=true`. The workbench input has an ADD filter (`isValidInput`) that rejects null items (no matching recipes). The SET silently fails — the slot appears occupied.

**Fix:** `setItemStackForSlot(slot, null, false)` — bypass the filter when clearing.

### 3. CraftingConversionSystem steals workbench output

`CraftRecipeEvent.Post` fires from StructuralCrafting. Our `CraftingConversionSystem` (which converts vanilla crafts to RPG gear) catches the output and converts it to RANDOM RPG gear before the reskin system can apply cached data.

**Fix:** `ReskinCraftInterceptor` handles Pre and cancels — no Post event ever fires for reskin recipes.

### 4. Armory mod ResourceTypes conflict

The Armory mod assigns its own ResourceTypes (e.g., `JesterHat`, `Resource_Weapon_Sword_Adamantite`) to items. RPG items inherit these via the Item copy constructor. Both Armory recipes AND our reskin recipes match, causing confusing mixed results and exceeding the 64-slot option limit.

**Fix:** `injectReskinResourceType()` REPLACES all ResourceTypes with only our `RPG_Reskin_*` type. Armory recipes no longer match RPG items.

### 5. Item.toPacket() cache prevents client sync

`Item.toPacket()` caches its result via `SoftReference`. When we inject ResourceTypes via reflection, the cached packet still has the OLD types. The client receives stale data and can't match recipes → options greyed out.

**Fix:** Clear `cachedPacket` field via reflection after modifying ResourceTypes.

### 6. ItemDefinitionBuilder uses base item, not custom item

`ItemDefinitionBuilder.build()` creates client definitions from the BASE item's `toPacket()`, not the registered custom item. The base item has original ResourceTypes (or none after stripping), not our injected `RPG_Reskin_*` type.

**Fix:** After cloning the base item's packet, override `definition.resourceTypes` with the registered custom item's ResourceTypes.

### 7. Cached items miss ResourceType injection

`ItemRegistryService.initialize()` loads cached items from DB before the reskin system initializes. These items are created via `createCustomItem()` when `reskinRegistry == null`, so `injectReskinResourceType()` is skipped.

**Fix:** `retroInjectReskinResourceTypes()` runs after recipe generation, re-injecting ALL cached items (always replace, don't skip items that already have a reskin type — the type ID may have changed between builds).

### 8. StructuralCrafting option limit is 64

`StructuralCraftingWindow.optionsContainer` has `MAX_OPTIONS = 64`. Exceeding this crashes the server with `Slot is outside capacity! 64 >= 64`.

**Fix:** Cap recipes at 50 per group (leaves room for edge cases).

### 9. InventoryChangeEvent timing is unreliable for crafting

`InventoryChangeEvent` fires via ECS dirty-flag detection — NOT synchronously from container operations. For StructuralCrafting with `timeSeconds=0`, the craft completes in `tick()` which runs on a later frame. The InventoryChangeEvent may fire seconds later.

**Fix:** Don't use InventoryChangeEvent for the apply phase. Use `CraftRecipeEvent.Pre` which fires synchronously from `queueCraft()`.

## Recipe Structure

```java
// Input: any item with the reskin ResourceType
MaterialQuantity input = new MaterialQuantity(null, resourceTypeId, null, 1, null);

// Output: specific vanilla item ID
MaterialQuantity output = new MaterialQuantity(targetItemId, null, null, 1, null);

// Bench: Builder's Workbench with WoodPlanks category (required for selectability)
BenchRequirement benchReq = new BenchRequirement(
    BenchType.StructuralCrafting, "Builders", new String[]{"WoodPlanks"}, 0);

// Recipe: instant, no knowledge required
CraftingRecipe recipe = new CraftingRecipe(
    inputs, output, outputs, 1, benchReqs, 0f, false, 1);

// ID: must have rpg_reskin_ prefix for interceptor to recognize
recipe.id = "rpg_reskin_" + targetItemId;  // set via reflection
```

### Grouping Strategy

| Slot Type | Grouping | Example ResourceType |
|-----------|----------|---------------------|
| Armor (HEAD, CHEST, LEGS, HANDS) | (slot, quality) | `RPG_Reskin_HEAD_Rare` |
| Weapons (WEAPON, OFF_HAND) | (slot, quality, weaponType) | `RPG_Reskin_WEAPON_Rare_SWORD` |

Armor groups all materials together (plate + leather + special). Weapons keep types separate (daggers only with daggers).

## File Map

| File | Purpose |
|------|---------|
| `gear/reskin/ReskinRecipeGenerator.java` | Generates recipes at startup from DynamicLootRegistry |
| `gear/reskin/ReskinResourceTypeRegistry.java` | Maps (slot, quality[, category]) → ResourceType ID |
| `gear/reskin/ReskinDataPreserver.java` | Caches GearData on InventoryChangeEvent (cache-only) |
| `gear/reskin/ReskinCraftInterceptor.java` | ECS system for CraftRecipeEvent.Pre — the actual reskin |
| `gear/item/ItemRegistryService.java` | injectReskinResourceType() + retroInjectReskinResourceTypes() |
| `gear/item/ItemDefinitionBuilder.java` | Overrides resourceTypes in client packet from registered item |
| `gear/systems/CraftingConversionSystem.java` | Vanilla→RPG conversion (does NOT handle reskin recipes) |

## Hytale Internals Reference

### Builder's Workbench (StructuralCraftingBench)
- Bench ID: `"Builders"`
- Bench Type: `StructuralCrafting`
- Categories: `WoodPlanks` (header), `OrnatePlanks`, `DecorativePlanks`, `Bricks`, `Decorative`, etc.
- Max option slots: 64 (hardcoded in `StructuralCraftingWindow`)
- Input container: `SimpleItemContainer(1)` — slot 0
- Options container: `SimpleItemContainer(64)` — slots 0-63
- Combined container: `CombinedItemContainer(input, options)` — what `getItemContainer()` returns

### Recipe Matching
- `CraftingManager.matches(MaterialQuantity, ItemStack)`: checks itemId first, then resourceTypeId, then tag
- `ItemStack.getItem()` returns `Item.getAssetMap().getAsset(itemId)` — for RPG items, returns our registered custom Item
- `Item.getResourceTypes()` returns the array we set via reflection

### Client Sync
- Recipes synced via `UpdateRecipes` packet (from `CraftingRecipePacketGenerator`)
- Items synced via `UpdateItems` packet (from `ItemPacketGenerator`) + our `ItemSyncService`
- Window state synced via `UpdateWindow` packet (from `WindowManager.updateWindows()`)
- Container changes trigger `WindowManager.markWindowChanged()` → `window.invalidate()` → next `updateWindows()` call sends packet

### Armory Mod (TheArmoryMod-1.15.0)
- Zero Java event handlers — purely JSON-driven
- 71 ResourceTypes, 803+ items, 39 recipe files
- Recipes inline in item JSON (`"Recipe"` field) with `BenchRequirement: [{"Type": "StructuralCrafting", "Id": "Builders", "Categories": ["WoodPlanks"]}]`
- Color variants share same ResourceType (e.g., all Adamantite Swords share `Resource_Weapon_Sword_Adamantite`)
