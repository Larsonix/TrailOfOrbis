# Hytale Shop Skill

Use this skill when working with vendor/shop systems in Hytale plugins. This covers the `ShopPlugin` (standard shops and barter shops), `NPCShopPlugin` (NPC integration), and `ObjectiveShopPlugin` (objective integration) APIs.

> **Related skills:** For reputation-gated shop items, see `docs/hytale-api/reputation.md`. For objectives that can be started from shops, see `docs/hytale-api/objectives.md`. For NPC behavior templates, see `docs/hytale-api/npc-templates.md`. For custom UI pages, see `docs/hytale-api/native-ui.md`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Define a standard shop | Create JSON in `Shops/` with `ChoiceElement` content |
| Define a barter shop | Create JSON in `BarterShops/` with trades and trade slots |
| Open a shop via NPC | Use `OpenShop` or `OpenBarterShop` NPC builder actions |
| Open a shop via interaction | Use `OpenCustomUI` interaction with `ShopPageSupplier` |
| Add a shop item with cost | Use `ShopElement` type in shop JSON |
| Give items on purchase | Use `GiveItem` choice interaction in shop JSON |
| Gate items behind reputation | Use `"Reputation"` `ChoiceRequirement` type |
| Gate items behind objectives | Use `"CanStartObjective"` `ChoiceRequirement` type |
| Look up a shop asset | `ShopAsset.getAssetMap().getAsset(shopId)` |
| Look up a barter shop asset | `BarterShopAsset.getAssetMap().getAsset(shopId)` |

---

## Architecture: Two Shop Types

Hytale has two fundamentally different shop systems:

| Shop Type | Asset Class | UI | Restock | Description |
|-----------|-------------|-----|---------|-------------|
| **Standard Shop** | `ShopAsset` | Native choice-page UI | No | Static menu of `ChoiceElement` items with requirements and interactions |
| **Barter Shop** | `BarterShopAsset` | Barter trade UI | Yes | Item-for-item trades with limited stock, periodic refresh |

---

## Key Classes

### Imports

```java
// Plugin entry points
import com.hypixel.hytale.builtin.adventure.shop.ShopPlugin;
import com.hypixel.hytale.builtin.adventure.npcshop.NPCShopPlugin;
import com.hypixel.hytale.builtin.adventure.objectiveshop.ObjectiveShopPlugin;

// Standard shop assets
import com.hypixel.hytale.builtin.adventure.shop.ShopAsset;
import com.hypixel.hytale.builtin.adventure.shop.ShopPageSupplier;
import com.hypixel.hytale.builtin.adventure.shop.ShopElement;
import com.hypixel.hytale.builtin.adventure.shop.GiveItemInteraction;

// Barter shop assets
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopAsset;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopState;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterTrade;
import com.hypixel.hytale.builtin.adventure.shop.barter.TradeSlot;
import com.hypixel.hytale.builtin.adventure.shop.barter.RefreshInterval;

// Choice system (shared by shops)
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;

// Opening shops via interactions
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
```

---

## Plugin Architecture

### ShopPlugin (Core)

The main plugin that manages both shop types:

- Registers `ShopAsset` and `BarterShopAsset` asset stores
- Registers `ShopElement` as a `ChoiceElement` type
- Registers `GiveItem` as a `ChoiceInteraction` type
- Registers `ShopPageSupplier` for opening shops via `OpenCustomUI`
- Initializes `BarterShopState` on startup and saves on shutdown

### NPCShopPlugin (NPC Integration)

Adds NPC builder actions to open shops from NPC interactions:

| Action Type | Builder Class | Description |
|-------------|---------------|-------------|
| `OpenShop` | `BuilderActionOpenShop` | Open a standard shop from NPC interaction |
| `OpenBarterShop` | `BuilderActionOpenBarterShop` | Open a barter shop from NPC interaction |

### ObjectiveShopPlugin (Objective Integration)

Bridges shops and the objective system:

- Registers `StartObjective` as a `ChoiceInteraction` type — shop items can start objectives
- Registers `CanStartObjective` as a `ChoiceRequirement` type — shop items can be gated by objective eligibility
- Ensures `ShopAsset` loads after `ObjectiveAsset` (dependency ordering)

---

## Standard Shops

### ShopAsset JSON

Located in `Shops/`. Defines a choice-page UI with elements.

```json
{
  "Content": [
    {
      "Type": "ShopElement",
      "DisplayNameKey": "shop.item.iron_sword.name",
      "DescriptionKey": "shop.item.iron_sword.desc",
      "Cost": 100,
      "Icon": "UI/Shop/Icons/Iron_Sword.png",
      "Interactions": [
        {
          "Type": "GiveItem",
          "ItemId": "Weapon_Sword_Iron",
          "Quantity": 1
        }
      ],
      "Requirements": [
        {
          "Type": "Reputation",
          "GroupId": "Villagers",
          "MinRank": "Friendly"
        }
      ]
    }
  ]
}
```

### ShopElement Properties

`ShopElement` extends `ChoiceElement` with shop-specific fields:

| Property | Type | Description |
|----------|------|-------------|
| `DisplayNameKey` | `String` | Localization key for item name (inherited from `ChoiceElement`) |
| `DescriptionKey` | `String` | Localization key for item description (inherited from `ChoiceElement`) |
| `Cost` | `int` | Price (must be >= 0) |
| `Icon` | `String` | Icon texture path |
| `Interactions` | `ChoiceInteraction[]` | Actions when purchased (inherited) |
| `Requirements` | `ChoiceRequirement[]` | Conditions to unlock (inherited) |

### ShopElement Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `addButton(UICommandBuilder, UIEventBuilder, String, PlayerRef)` | `void` | Renders the shop element using `Pages/ShopElementButton.ui` |
| `canFulfillRequirements(Store, Ref, PlayerRef)` | `boolean` | Checks all `ChoiceRequirement`s |

---

## GiveItemInteraction

The built-in `ChoiceInteraction` for giving items when a shop element is activated.

### JSON Format

```json
{
  "Type": "GiveItem",
  "ItemId": "Weapon_Sword_Iron",
  "Quantity": 1
}
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `ItemId` | `String` | Item asset ID (validated against `Item` asset store) |
| `Quantity` | `int` | Number of items to give (must be >= 1) |

### Behavior

Adds items directly to the player's combined inventory (hotbar-first):

```java
// Internal implementation
player.getInventory().getCombinedHotbarFirst().addItemStack(new ItemStack(itemId, quantity));
```

---

## Barter Shops

Barter shops use item-for-item trading with limited stock and periodic restocking.

### BarterShopAsset JSON

Located in `BarterShops/`.

```json
{
  "DisplayNameKey": "shop.blacksmith.name",
  "RefreshInterval": {
    "Days": 3
  },
  "RestockHour": 7,
  "Trades": [
    {
      "Output": { "ItemId": "Weapon_Sword_Iron", "Quantity": 1 },
      "Input": [
        { "ItemId": "Iron_Ingot", "Quantity": 5 },
        { "ItemId": "Wood_Stick", "Quantity": 2 }
      ],
      "Stock": 3
    }
  ],
  "TradeSlots": [
    {
      "Type": "Fixed",
      "Trades": [0, 1]
    },
    {
      "Type": "Pool",
      "Count": 2,
      "Trades": [2, 3, 4, 5]
    }
  ]
}
```

### BarterShopAsset Properties

| Property | Type | Description |
|----------|------|-------------|
| `DisplayNameKey` | `String` | Localization key for shop name |
| `RefreshInterval` | `RefreshInterval` | How often stock refreshes (in days, min 1) |
| `RestockHour` | `Integer` (optional) | Hour of day to restock (default: 7) |
| `Trades` | `BarterTrade[]` | All available trade definitions |
| `TradeSlots` | `TradeSlot[]` (optional) | How trades are presented in the UI |

### BarterShopAsset Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getId()` | `String` | Shop ID |
| `getDisplayNameKey()` | `String` | Localization key |
| `getRefreshInterval()` | `RefreshInterval` | Refresh config |
| `getTrades()` | `BarterTrade[]` | Trade definitions |
| `getTradeSlots()` | `TradeSlot[]` or `null` | Slot layout |
| `hasTradeSlots()` | `boolean` | Whether trade slots are defined |
| `getRestockHour()` | `int` | Restock hour (default: 7) |

---

## BarterTrade

Defines a single trade: what the player gives and what they receive.

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `Output` | `BarterItemStack` | What the player receives |
| `Input` | `BarterItemStack[]` | What the player must provide |
| `Stock` | `int` | Maximum stock (min 1, default 10) |

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getOutput()` | `BarterItemStack` | Output item |
| `getInput()` | `BarterItemStack[]` | Input requirements |
| `getMaxStock()` | `int` | Max stock count |

---

## TradeSlot

Controls how trades appear in the barter UI. Two types:

### Fixed Trade Slot

Always shows specific trades:
```json
{
  "Type": "Fixed",
  "Trades": [0, 1]
}
```

### Pool Trade Slot

Randomly selects from a pool each refresh:
```json
{
  "Type": "Pool",
  "Count": 2,
  "Trades": [2, 3, 4, 5]
}
```

---

## RefreshInterval

Controls barter shop restock timing:

| Property | Type | Description |
|----------|------|-------------|
| `Days` | `int` | Number of days between restocks (min 1) |

---

## BarterShopState

Manages barter shop runtime state (stock levels, refresh timers). Persisted to disk.

```java
// Initialized on plugin start
BarterShopState.initialize(dataDirectory);

// Saved on plugin shutdown
BarterShopState.shutdown();
```

---

## Opening Shops

### Via NPC Builder Actions

In NPC templates, use the registered builder actions:

```json
{
  "Actions": [
    {
      "Type": "OpenShop",
      "ShopId": "village_general_store"
    }
  ]
}
```

```json
{
  "Actions": [
    {
      "Type": "OpenBarterShop",
      "ShopId": "blacksmith_trades"
    }
  ]
}
```

### Via OpenCustomUI Interaction

For non-NPC contexts (e.g., block interactions), use `OpenCustomUI` with `ShopPageSupplier`:

```json
{
  "Type": "OpenCustomUI",
  "Page": {
    "Type": "Shop",
    "ShopId": "village_general_store"
  }
}
```

The `ShopPageSupplier` creates a `ShopPage` for the given shop ID:

```java
// Internal implementation
@Override
public CustomUIPage tryCreate(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
                               PlayerRef playerRef, InteractionContext context) {
    return new ShopPage(playerRef, this.shopId);
}
```

### Accessing Shop Assets Programmatically

```java
// Standard shop
ShopAsset shop = ShopAsset.getAssetMap().getAsset("village_general_store");
ChoiceElement[] items = shop.getElements();

// Barter shop
BarterShopAsset barterShop = BarterShopAsset.getAssetMap().getAsset("blacksmith_trades");
BarterTrade[] trades = barterShop.getTrades();
```

---

## Requirement Types

Shop items can be gated behind requirements. These are `ChoiceRequirement` subtypes:

### Reputation Requirement

Registered by `ReputationPlugin`. Gates items behind faction reputation:

```json
{
  "Type": "Reputation",
  "GroupId": "Villagers",
  "MinRank": "Friendly"
}
```

### CanStartObjective Requirement

Registered by `ObjectiveShopPlugin`. Gates items based on objective eligibility:

```json
{
  "Type": "CanStartObjective",
  "ObjectiveId": "quest_gather_iron"
}
```

---

## Interaction Types

Shop items trigger actions via `ChoiceInteraction` subtypes:

### GiveItem

Registered by `ShopPlugin`. Gives items to the player:

```json
{
  "Type": "GiveItem",
  "ItemId": "Weapon_Sword_Iron",
  "Quantity": 1
}
```

### StartObjective

Registered by `ObjectiveShopPlugin`. Starts an objective:

```json
{
  "Type": "StartObjective",
  "ObjectiveId": "quest_gather_iron"
}
```

---

## Code Examples

### Defining a Custom Shop JSON for an RPG Vendor

```json
{
  "Content": [
    {
      "Type": "ShopElement",
      "DisplayNameKey": "rpg.shop.health_potion.name",
      "DescriptionKey": "rpg.shop.health_potion.desc",
      "Cost": 50,
      "Icon": "UI/Shop/Icons/Health_Potion.png",
      "Interactions": [
        { "Type": "GiveItem", "ItemId": "Consumable_Health_Potion", "Quantity": 5 }
      ]
    },
    {
      "Type": "ShopElement",
      "DisplayNameKey": "rpg.shop.realm_map.name",
      "DescriptionKey": "rpg.shop.realm_map.desc",
      "Cost": 500,
      "Icon": "UI/Shop/Icons/Realm_Map.png",
      "Interactions": [
        { "Type": "GiveItem", "ItemId": "RPG_Realm_Map_Forest", "Quantity": 1 }
      ],
      "Requirements": [
        { "Type": "Reputation", "GroupId": "Adventurers_Guild", "MinRank": "Friendly" }
      ]
    }
  ]
}
```

### Configuring a Barter Shop with Restock

```json
{
  "DisplayNameKey": "rpg.shop.blacksmith.name",
  "RefreshInterval": { "Days": 7 },
  "RestockHour": 6,
  "Trades": [
    {
      "Output": { "ItemId": "Weapon_Sword_Iron", "Quantity": 1 },
      "Input": [
        { "ItemId": "Iron_Ingot", "Quantity": 10 }
      ],
      "Stock": 5
    },
    {
      "Output": { "ItemId": "Armor_Chest_Iron", "Quantity": 1 },
      "Input": [
        { "ItemId": "Iron_Ingot", "Quantity": 15 },
        { "ItemId": "Leather", "Quantity": 3 }
      ],
      "Stock": 2
    }
  ],
  "TradeSlots": [
    { "Type": "Fixed", "Trades": [0] },
    { "Type": "Pool", "Count": 1, "Trades": [0, 1] }
  ]
}
```

---

## Best Practices

1. **Standard vs Barter**: Use standard shops for simple buy menus (currency-based). Use barter shops for item-trading vendors with stock limits and refresh cycles.

2. **Shop asset loading order**: `ShopAsset` loads after `Item` (for item validation). If using objective requirements, `ObjectiveShopPlugin` ensures shops load after `ObjectiveAsset` too.

3. **Barter state persistence**: `BarterShopState` is initialized on `ShopPlugin.start()` and saved on `shutdown()`. Stock levels persist across restarts via the plugin's data directory.

4. **Cost is display-only for ShopElement**: The `Cost` field on `ShopElement` is rendered in the UI but actual currency deduction must be handled by a `ChoiceInteraction` or custom logic. The built-in `GiveItemInteraction` only gives items — it doesn't deduct currency.

5. **Combine requirements**: Stack multiple `ChoiceRequirement` entries to create complex unlock conditions (e.g., reputation AND objective completion).

6. **Pool trade slots for variety**: Use `"Type": "Pool"` trade slots to create rotating inventories that change each refresh cycle, keeping barter shops interesting.

7. **Restock timing**: The `RestockHour` defaults to 7 (7 AM game time). Combine with `RefreshInterval.Days` for predictable restock schedules.

---

## Integration Notes (TrailOfOrbis)

Potential uses for TrailOfOrbis:

- **RPG vendors**: Standard shops selling potions, realm maps, and skill stones
- **Reputation-gated gear**: Gate high-tier vendor items behind reputation ranks
- **Skill stone shops**: Barter shops where players trade essences for enhancement stones
- **Realm reward shops**: Unlock shop items after completing realm objectives
- **Daily rotating vendors**: Use barter shops with Pool trade slots for daily-rotating deals
- **Quest-starting shops**: Use `StartObjective` interaction to sell quest-starting items
