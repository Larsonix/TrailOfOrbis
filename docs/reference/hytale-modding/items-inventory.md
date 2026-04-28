# Items & Inventory

Reference for ItemStack, inventory, transactions, dropped items, drop lists, and crafting in Hytale server plugins.

---

## ItemStack

Immutable value object representing an item instance. All `with*` methods return a **new** ItemStack.

```java
import com.hypixel.hytale.server.core.inventory.ItemStack;

new ItemStack("Weapon_Sword_Iron", 5);                           // id + quantity
new ItemStack("Weapon_Sword_Iron");                              // quantity = 1
new ItemStack("Weapon_Sword_Iron", 1, 100.0, 100.0, null);      // id, qty, dur, maxDur, metadata
new ItemStack("Weapon_Sword_Iron", 1, myBsonDocument);           // id, qty, metadata
```

### Sentinel & Null-Safety

```java
ItemStack.EMPTY                      // Singleton empty item — use instead of null
ItemStack.isEmpty(someStack)         // Static: true if null, EMPTY, or quantity 0
ItemStack.EMPTY_ARRAY                // Empty ItemStack[] constant
```

### Accessors

| Method | Returns | Notes |
|--------|---------|-------|
| `getItemId()` | `String` | Asset ID (e.g. `"Weapon_Sword_Iron"`) |
| `getQuantity()` | `int` | Stack count |
| `getItem()` | `Item` | Resolved Item asset definition |
| `getDurability()` / `getMaxDurability()` | `double` | Current / max durability |
| `isUnbreakable()` / `isBroken()` / `isEmpty()` / `isValid()` | `boolean` | |
| `getBlockKey()` | `String` | Block ID if this item places a block |
| `getMetadata()` | `BsonDocument` | Full BSON metadata document |

### Immutable Transforms

```java
stack.withQuantity(10);
stack.withDurability(50.0);
stack.withMaxDurability(200.0);
stack.withIncreasedDurability(25.0);                    // Adds to current
stack.withRestoredDurability(100.0);                    // Sets to max
stack.withState("Mode_Ranged");                         // Weapon mode switching

// Metadata variants
stack.withMetadata("rpg_level", new BsonInt32(5));                  // Single BSON key
stack.withMetadata("rpg_data", GearData.CODEC, gearData);          // Typed via Codec
stack.withMetadata(GearData.KEYED_CODEC, gearData);                // KeyedCodec (key embedded)
stack.withMetadata(newBsonDocument);                                // Replace entire document
```

### Reading Metadata

```java
GearData data = stack.getFromMetadataOrNull("rpg_data", GearData.CODEC);   // null if absent
GearData data = stack.getFromMetadataOrNull(GearData.KEYED_CODEC);         // KeyedCodec variant
MyConfig cfg  = stack.getFromMetadataOrDefault("rpg_cfg", MyConfig.CODEC);  // Default if absent
BsonDocument doc = stack.getMetadata();                                     // Raw BSON
```

### Comparison

```java
stack.isStackableWith(other);          // Can merge into one slot?
stack.isEquivalentType(other);         // Same type (ignoring qty/durability)?
ItemStack.isSameItemType(a, b);        // Same item ID only (static, null-safe)
// All three have static null-safe variants: ItemStack.isStackableWith(a, b), etc.
```

### Serialization & Misc

```java
ItemWithAllMetadata packet = stack.toPacket();           // For UI/client sync
ItemStack from = ItemStack.fromPacket(itemQuantity);     // Reconstruct from packet
stack.setOverrideDroppedItemAnimation(true);             // Mutable setter (rare exception)
```

---

## Item Asset API

Static item definitions loaded from JSON. Import: `com.hypixel.hytale.server.core.asset.type.item.config.Item`

```java
Item sword = Item.getAssetMap().getAsset("Weapon_Sword_Iron");  // Look up by ID
Item.UNKNOWN                                                     // Returned for unknown IDs
```

| Method | Returns | Notes |
|--------|---------|-------|
| `getId()` / `getModel()` / `getTexture()` / `getIcon()` | `String` | Asset paths |
| `getScale()` | `float` | Display scale |
| `getTranslationKey()` / `getDescriptionTranslationKey()` | `String` | Localization keys |
| `getQualityIndex()` | `int` | Quality/rarity index |
| `getCategories()` | `String[]` | Creative menu categories |
| `hasBlockType()` / `getBlockId()` | `boolean` / `String` | Block type info |
| `isConsumable()` / `isVariant()` | `boolean` | |
| `getWeapon()` | `ItemWeapon` | **Nullable** — null = not a weapon |
| `getData().getRawTags()` | `Map<String, String[]>` | Family/Type tags |

### Item States (Mode Switching)

```java
String rangedId = item.getItemIdForState("Mode_Ranged");
Item rangedItem = item.getItemForState("Mode_Ranged");
String state = item.getStateForItem("Weapon_Bow_Iron_Melee");
boolean isVariant = item.isState();
```

### Runtime Registration

```java
Item.getAssetStore().loadAssets("MyPlugin:CustomItems", List.of(customItem1, customItem2));
// After loading: Item.getAssetMap().getAsset(customId) resolves correctly
```

---

## Inventory System

```java
import com.hypixel.hytale.server.core.inventory.Inventory;
Inventory inventory = player.getInventory();
```

### Sections

Each returns an `ItemContainer`:

| Method | ID Constant | Default Capacity |
|--------|-------------|------------------|
| `getHotbar()` | `HOTBAR_SECTION_ID` (-1) | 9 |
| `getStorage()` | `STORAGE_SECTION_ID` (-2) | 36 |
| `getArmor()` | `ARMOR_SECTION_ID` (-3) | varies |
| `getUtility()` | `UTILITY_SECTION_ID` (-5) | 4 |
| `getTools()` | `TOOLS_SECTION_ID` (-8) | 23 |
| `getBackpack()` | `BACKPACK_SECTION_ID` (-9) | varies |

`inventory.getSectionById(Inventory.HOTBAR_SECTION_ID)` — look up by constant.

### Active Slot Queries

```java
byte slot = inventory.getActiveHotbarSlot();
ItemStack item = inventory.getActiveHotbarItem();
ItemStack inHand = inventory.getItemInHand();         // Accounts for tools mode
ItemStack util = inventory.getUtilityItem();
ItemStack tool = inventory.getToolsItem();
```

### Active Slot Mutation

```java
inventory.setActiveHotbarSlot(ref, (byte) 3, componentAccessor);
inventory.setActiveUtilitySlot(ref, (byte) 1, componentAccessor);
inventory.setActiveUtilitySlot(holder, (byte) 1);     // Holder variant
```

### Combined Containers

Span multiple sections for add-with-overflow or search:

```java
inventory.getCombinedHotbarFirst();                    // Hotbar -> Storage
inventory.getCombinedStorageFirst();                   // Storage -> Hotbar
inventory.getCombinedBackpackStorageHotbar();           // Backpack -> Storage -> Hotbar
inventory.getCombinedArmorHotbarUtilityStorage();       // All equip + storage
```

### Inventory-Wide Operations

```java
inventory.clear();
inventory.dropAllItemStacks();                         // Returns List<ItemStack>
inventory.sortStorage();
inventory.moveItem(fromSectionId, fromSlot, qty, toSectionId, toSlot);
inventory.takeAll(sectionId, playerSettings);
inventory.putAll(sectionId);
inventory.quickStack(sectionId);
```

---

## ItemContainer Operations

Core slot-level API. Import: `com.hypixel.hytale.server.core.inventory.container.ItemContainer`

### Reading

```java
short capacity = container.getCapacity();
ItemStack item = container.getItemStack((short) 0);
boolean canAdd = container.canAddItemStack(itemStack);
```

### Adding

```java
ItemStackTransaction tx = container.addItemStack(itemStack);
ItemStackTransaction tx = container.addItemStack(itemStack, allOrNothing, fullStacks, filter);
ItemStackSlotTransaction tx = container.addItemStackToSlot((short) 0, itemStack);
ItemStackSlotTransaction tx = container.addItemStackToSlot((short) 0, itemStack, allOrNothing, filter);
```

### Removing

```java
SlotTransaction tx = container.removeItemStackFromSlot((short) 3);                      // Remove all
ItemStackSlotTransaction tx = container.removeItemStackFromSlot((short) 3, 5);           // Remove qty
ItemStackSlotTransaction tx = container.removeItemStackFromSlot((short) 3, 5, aon, f);   // With options
ItemStackSlotTransaction tx = container.removeItemStackFromSlot((short) 3, item, 1);     // Match + remove
```

### Replacing

```java
container.setItemStackForSlot((short) 0, newItem);                        // Direct set
container.replaceItemStackInSlot((short) 0, expectedOldItem, newItem);    // Atomic swap

// Functional bulk replace (atomic across all slots)
container.replaceAll((slot, existing) -> shouldReplace(existing) ? replacement : existing);
```

### Clearing & Moving

```java
ClearTransaction tx = container.clear();      // tx.succeeded(), tx.getItems()
MoveTransaction<ItemStackTransaction> tx = container.moveItemStackFromSlot((short) 0, targetContainer);
```

### Slot Filtering

```java
container.setGlobalFilter(filterType);
container.setSlotFilter(FilterActionType.ADD, (short) 0, mySlotFilter);
```

---

## Transactions

All mutations return transaction objects. **Always check `succeeded()`.**

```java
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;

ItemStackTransaction tx = container.addItemStack(item);
tx.succeeded();                    // Did it work?
tx.getRemainder();                 // ItemStack that didn't fit (null if all fit)
tx.getAction();                    // ActionType (ADD, REMOVE, etc.)
tx.getQuery();                     // Original ItemStack
tx.getSlotTransactions();          // Per-slot detail list
```

### Give with Overflow Pattern

```java
// Try hotbar, fall back to backpack
ItemStackTransaction tx = inventory.getHotbar().addItemStack(item);
if (!tx.succeeded() || !ItemStack.isEmpty(tx.getRemainder())) {
    tx = inventory.getBackpack().addItemStack(item);
}
```

### Player.giveItem (High-Level)

```java
// Handles overflow automatically
ItemStackTransaction tx = player.giveItem(itemStack, ref, componentAccessor);
```

---

## Dropped Items (ItemComponent)

Item entities are ECS entities with an `ItemComponent`. Import: `com.hypixel.hytale.server.core.modules.entity.item.ItemComponent`

### Read/Write

```java
ItemComponent ic = store.getComponent(ref, ItemComponent.getComponentType());
ItemStack dropped = ic.getItemStack();
ic.setItemStack(newStack);
```

### Spawning Drops

```java
// Single item with velocity
Holder<EntityStore> h = ItemComponent.generateItemDrop(store, itemStack, pos, rot, vx, vy, vz);

// Batch drop (default scatter velocity)
Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, itemList, pos, rot);

// Spawn into world
commandBuffer.addEntities(drops, AddReason.SPAWN);

// Single holder needs array wrapping:
@SuppressWarnings("unchecked")
Holder<EntityStore>[] arr = (Holder<EntityStore>[]) new Holder<?>[]{h};
commandBuffer.addEntities(arr, AddReason.SPAWN);
```

### Utility Methods

```java
ItemStack remaining = ItemComponent.addToItemContainer(store, itemRef, container);  // Pickup simulation
Holder<EntityStore> entity = ItemComponent.generatePickedUpItem(/* params */);
```

### Pickup & Merge Prevention

Marker components (presence-only, no data):

```java
// ECS: PreventPickup.getComponentType(), PreventItemMerging.getComponentType()
// Prefab JSON: { "PreventPickup": {}, "PreventItemMerging": {} }
```

### Timing Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `DEFAULT_PICKUP_DELAY` | 0.5s | Before item can be picked up |
| `PICKUP_DELAY_DROPPED` | 1.5s | Player-dropped items |
| `PICKUP_THROTTLE` | 0.25s | Between pickup attempts |
| `DEFAULT_MERGE_DELAY` | 1.5s | Before auto-merge |

---

## Item Drop Lists

```java
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
```

### Resolving

```java
List<ItemStack> items = ItemModule.get().getRandomItemDrops("MyMod_BossDrops");  // RNG-rolled
boolean exists = ItemModule.exists("Weapon_Sword_Iron");
```

### Direct Access & Runtime Modification

```java
ItemDropList dl = ItemDropList.getAssetMap().getAsset("MyMod_BossDrops");
List<ItemDrop> all = dl.getContainer().getAllDrops(new ObjectArrayList<>());

ItemDropList.getAssetStore().removeAssets(List.of("DropList_To_Remove"));
ItemDropList.getAssetStore().loadAssets("MyPlugin:Drops", List.of(customDropList));
```

---

## Crafting Recipes

```java
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
```

### Lookup & Properties

```java
CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset("Recipe_Sword_Iron");

recipe.getId();                          // String
recipe.getPrimaryOutput();               // MaterialQuantity (nullable)
recipe.getOutputs();                     // MaterialQuantity[]
recipe.getInput();                       // MaterialQuantity[]
recipe.getBenchRequirement();            // BenchRequirement[]
recipe.getTimeSeconds();                 // float
recipe.isKnowledgeRequired();            // boolean
recipe.getRequiredMemoriesLevel();       // int
```

### MaterialQuantity

```java
MaterialQuantity mq = recipe.getPrimaryOutput();
mq.getItemId();           // String
mq.getQuantity();         // int
mq.getMetadata();         // BsonDocument
mq.getResourceTypeId();   // String
mq.toItemStack();         // Convert to ItemStack
```

### Runtime Modification & Events

```java
CraftingRecipe.getAssetStore().removeAssets(List.of("Recipe_To_Remove"));

// CraftRecipeEvent (ECS event — register via EntityEventSystem, not registerGlobal)
CraftingRecipe recipe = event.getCraftedRecipe();
MaterialQuantity output = recipe.getPrimaryOutput();
if (output == null) {
    MaterialQuantity[] outputs = recipe.getOutputs();
    if (outputs != null && outputs.length > 0) output = outputs[0];
}
```

---

## Key Imports

```java
// Items & Inventory
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;

// Assets
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;

// Entities & Modules
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;

// Events
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
```

---

*Verified against Hytale server source, Loot4Everyone, and community mod implementations.*
