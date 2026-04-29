# Equipment System Research

Research findings from investigating the Hytale decompiled codebase for equipment mechanics.

**Research Date**: 2026-01-23
**Source**: `/home/larsonix/work/Hytale-Decompiled-Full-Game`

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Equipment Architecture](#equipment-architecture)
3. [Equipment Slots](#equipment-slots)
4. [Equip/Unequip Flow](#equipunequip-flow)
5. [Slot Filtering & Validation](#slot-filtering--validation)
6. [Equipment Events](#equipment-events)
7. [Stat Application](#stat-application)
8. [Network Synchronization](#network-synchronization)
9. [Blocking Equipment (Level Requirements)](#blocking-equipment-level-requirements)
10. [Key File Locations](#key-file-locations)
11. [Implications for Gear System](#implications-for-gear-system)

---

## Executive Summary

### Key Findings

| Aspect | Finding | Impact on Plugin |
|--------|---------|------------------|
| **Cancellable Events** | ✅ `SwitchActiveSlotEvent` is cancellable | Can block weapon/utility equipping |
| **Slot Filters** | ✅ `SlotFilter` interface exists | Can add custom armor validation |
| **Inventory Events** | ✅ `LivingEntityInventoryChangeEvent` fires | Can detect all equipment changes |
| **Stat Application** | ✅ `StatModifiersManager` handles stats | Know where to inject our modifiers |
| **Equipment Structure** | 4 armor + hotbar + utility | Matches expected slots |

### Verdict: FEASIBLE

Multiple interception points exist for blocking equipment by level:
- `SwitchActiveSlotEvent` (cancellable) for weapons/utilities
- `SlotFilter` system for armor slots
- Event listeners for detecting changes

---

## Equipment Architecture

### Two-Layer Structure

```
┌─────────────────────────────────────────────────────────────┐
│                    EQUIPMENT PROTOCOL                        │
│  Equipment.java - Network representation                     │
│  - String[] armorIds (4 slots)                              │
│  - String rightHandItemId (weapon)                          │
│  - String leftHandItemId (utility)                          │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ builds from
                              │
┌─────────────────────────────────────────────────────────────┐
│                    INVENTORY SYSTEM                          │
│  Inventory.java - Server-side management                     │
│  - ItemContainer armor (4 slots)                            │
│  - ItemContainer hotbar (weapon selection)                  │
│  - ItemContainer utility (utility selection)                │
│  - ItemContainer tools, storage, backpack...                │
└─────────────────────────────────────────────────────────────┘
```

### Inventory Container Structure

**Location**: `com/hypixel/hytale/server/core/inventory/Inventory.java`

```java
public class Inventory implements NetworkSerializable<UpdatePlayerInventory> {
    // Equipment containers
    private ItemContainer armor;      // 4 slots (Head, Chest, Hands, Legs)
    private ItemContainer hotbar;     // Weapon slots (one active at a time)
    private ItemContainer utility;    // Utility slots (4 slots, one active)
    private ItemContainer tools;      // Tool slots (23 slots)

    // Storage containers
    private ItemContainer storage;    // Main inventory grid
    private ItemContainer backpack;   // Backpack storage

    // Active slot tracking
    private byte activeHotbarSlot = -1;   // Currently held weapon
    private byte activeUtilitySlot = -1;  // Currently active utility
    private byte activeToolSlot = -1;     // Currently active tool
}
```

### Container Section IDs

| Section ID | Container | Purpose |
|------------|-----------|---------|
| -1 | Hotbar | Weapon selection |
| -3 | Armor | Armor equipment |
| -5 | Utility | Utility items |
| -8 | Tools | Tool selection |

---

## Equipment Slots

### Armor Slots

**Location**: `com/hypixel/hytale/protocol/ItemArmorSlot.java`

```java
public enum ItemArmorSlot {
    Head,   // Index 0
    Chest,  // Index 1
    Hands,  // Index 2
    Legs    // Index 3
}
```

### Equipment Protocol

**Location**: `com/hypixel/hytale/protocol/Equipment.java`

```java
public class Equipment {
    public String[] armorIds;        // One per armor slot
    public String rightHandItemId;   // Active weapon
    public String leftHandItemId;    // Active utility
}
```

---

## Equip/Unequip Flow

### Complete Flow Diagram

```
PLAYER ACTION: Double-click armor item in hotbar
                    ↓
CLIENT SENDS: SmartMoveItemStack packet (EquipOrMergeStack type)
                    ↓
SERVER: InventoryPacketHandler.handle(SmartMoveItemStack)
                    ↓
INVENTORY.smartMoveItem() checks:
    ├── Is item armor? → Get armor slot type
    └── Is item weapon/utility? → Handle differently
                    ↓
VALIDATION PHASE:
    └── ArmorSlotAddFilter.test() - Does item match slot?
                    ↓
IF VALID: ItemContainer.moveItemStackFromSlotToSlot()
    ├── Remove from source container
    └── Place in armor container
                    ↓
EVENT DISPATCH:
    └── LivingEntityInventoryChangeEvent fired
                    ↓
STAT RECALCULATION:
    ├── entity.invalidateEquipmentNetwork()
    ├── StatModifiersManager.setRecalculate(true)
    └── Next tick: recalculateEntityStatModifiers()
                    ↓
NETWORK SYNC:
    └── LegacyEquipment system sends Equipment to all viewers
```

### Smart Move Handling

**Location**: `Inventory.java` lines 339-397

```java
case EquipOrMergeStack: {
    Item item = targetContainer.getItemStack(fromSlotId).getItem();
    ItemArmor itemArmor = item.getArmor();

    // If armor, move to appropriate armor slot
    if (itemArmor != null && fromSectionId != -3) {
        targetContainer.moveItemStackFromSlotToSlot(
            fromSlotId,
            quantity,
            this.armor,
            (short)itemArmor.getArmorSlot().ordinal()  // Head=0, Chest=1, etc.
        );
        return;
    }
    // Otherwise merge with hotbar/utility
    this.combinedHotbarFirst.combineItemStacksIntoSlot(targetContainer, fromSlotId);
}
```

### Equip Item Interaction

**Location**: `com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/EquipItemInteraction.java`

```java
public class EquipItemInteraction extends SimpleInstantInteraction {
    @Override
    protected void firstRun(InteractionType type, InteractionContext context,
                           CooldownHandler cooldownHandler) {
        // 1. Get held item
        ItemStack itemInHand = context.getHeldItem();

        // 2. Check if it's armor
        ItemArmor armor = item.getArmor();
        if (armor == null) return;  // Not armor, abort

        // 3. Get target slot
        short slotId = armor.getArmorSlot().ordinal();

        // 4. Validate capacity
        if (slotId > inventory.getArmor().getCapacity()) return;

        // 5. Perform move transaction
        MoveTransaction<ItemStackTransaction> transaction =
            context.getHeldItemContainer().moveItemStackFromSlot(
                activeSlot,
                itemInHand.getQuantity(),
                armorContainer
            );

        // 6. Handle failure
        if (!transaction.succeeded()) {
            context.getState().state = InteractionState.Failed;
        }
    }
}
```

---

## Slot Filtering & Validation

### SlotFilter Interface

**Location**: `com/hypixel/hytale/server/core/inventory/container/filter/SlotFilter.java`

```java
public interface SlotFilter {
    // Predefined filters
    public static final SlotFilter ALLOW = (actionType, container, slot, itemStack) -> true;
    public static final SlotFilter DENY = (actionType, container, slot, itemStack) -> false;

    // Validation method
    public boolean test(FilterActionType actionType, ItemContainer container,
                       short slot, @Nullable ItemStack itemStack);
}
```

### ArmorSlotAddFilter

**Location**: `com/hypixel/hytale/server/core/inventory/container/filter/ArmorSlotAddFilter.java`

```java
public class ArmorSlotAddFilter implements ItemSlotFilter {
    private final ItemArmorSlot itemArmorSlot;  // Head, Chest, Hands, or Legs

    @Override
    public boolean test(@Nullable Item item) {
        // Allow if:
        // 1. Slot is empty (null item), OR
        // 2. Item is armor AND matches this slot's type
        return item == null ||
               (item.getArmor() != null &&
                item.getArmor().getArmorSlot() == this.itemArmorSlot);
    }
}
```

### Filter Setup

**Location**: `com/hypixel/hytale/server/core/inventory/container/ItemContainerUtil.java`

```java
public static <T extends ItemContainer> T trySetArmorFilters(T container) {
    if (container instanceof SimpleItemContainer) {
        SimpleItemContainer itemContainer = (SimpleItemContainer)container;
        ItemArmorSlot[] slots = ItemArmorSlot.VALUES;

        for (short i = 0; i < itemContainer.getCapacity(); i++) {
            if (i < slots.length) {
                // Set armor type filter for each slot
                itemContainer.setSlotFilter(
                    FilterActionType.ADD,
                    i,
                    new ArmorSlotAddFilter(slots[i])
                );
            }
        }
    }
    return container;
}
```

### FilterActionType

```java
public enum FilterActionType {
    ADD,      // When adding item to slot
    REMOVE,   // When removing item from slot
    // Possibly others
}
```

---

## Equipment Events

### LivingEntityInventoryChangeEvent

**Location**: `com/hypixel/hytale/server/core/event/events/entity/LivingEntityInventoryChangeEvent.java`

Fired when ANY inventory container changes (including equipment):

```java
public class LivingEntityInventoryChangeEvent extends EntityEvent {
    private ItemContainer itemContainer;  // Which container changed
    private Transaction transaction;       // What changed

    public LivingEntityInventoryChangeEvent(
        LivingEntity entity,
        ItemContainer itemContainer,
        Transaction transaction) {
        // ...
    }
}
```

**Usage in Inventory.java**:

```java
protected void registerChangeEvents() {
    // Armor container changes
    this.armorChange = this.armor.registerChangeEvent(e -> {
        this.markChanged();
        dispatcher.dispatch(new LivingEntityInventoryChangeEvent(
            this.entity, e.container(), e.transaction()
        ));
        this.entity.invalidateEquipmentNetwork();
    });

    // Hotbar changes (weapon)
    this.hotbarChange = this.hotbar.registerChangeEvent(e -> {
        // Similar, plus stat recalculation if active slot changed
        if (e.transaction().wasSlotModified(this.activeHotbarSlot)) {
            StatModifiersManager mgr = this.entity.getStatModifiersManager();
            mgr.setRecalculate(true);

            // Clear old weapon stats
            int[] statsToClear = itemWeapon.getEntityStatsToClear();
            mgr.queueEntityStatsToClear(statsToClear);
        }
    });
}
```

### SwitchActiveSlotEvent (CANCELLABLE!)

**Location**: `com/hypixel/hytale/server/core/event/events/ecs/SwitchActiveSlotEvent.java`

This is our **key interception point** for weapons/utilities:

```java
public class SwitchActiveSlotEvent extends CancellableEcsEvent {
    private final int previousSlot;
    private final int inventorySectionId;  // -1 = hotbar, -5 = utility
    private byte newSlot;
    private final boolean serverRequest;

    // Inherited from CancellableEcsEvent:
    public void setCancelled(boolean cancelled);
    public boolean isCancelled();
}
```

**How it's used** (InventoryPacketHandler):

```java
public void handle(SetActiveSlot packet) {
    byte previousSlot = inventory.getActiveSlot(packet.inventorySectionId);
    byte targetSlot = packet.slot;

    // Fire cancellable event BEFORE switching
    SwitchActiveSlotEvent event = new SwitchActiveSlotEvent(
        packet.inventorySectionId,
        previousSlot,
        targetSlot,
        false
    );
    store.invoke(ref, event);

    // If cancelled, revert to previous slot
    if (event.isCancelled()) {
        targetSlot = previousSlot;
    }

    // Apply the (possibly unchanged) slot
    inventory.setActiveSlot(packet.inventorySectionId, targetSlot);
}
```

---

## Stat Application

### StatModifiersManager

**Location**: `com/hypixel/hytale/server/core/entity/StatModifiersManager.java`

Central class for applying equipment stats to entities.

#### Recalculation Flow

```java
public void recalculateEntityStatModifiers(
    Ref<EntityStore> ref,
    EntityStatMap statMap,
    ComponentAccessor<EntityStore> componentAccessor) {

    // 1. Compute armor stat modifiers
    Int2ObjectMap<Object2FloatMap<CalculationType>> armorMods =
        computeStatModifiers(brokenPenalties, inventory);

    // 2. Apply armor modifiers to stat map
    for (Entry entry : armorMods.int2ObjectEntrySet()) {
        int statId = entry.getIntKey();
        // Apply flat, additive, multiplicative modifiers...
    }

    // 3. Apply active weapon modifiers
    ItemStack itemInHand = inventory.getItemInHand();
    addItemStatModifiers(itemInHand, statMap, "*Weapon_",
        v -> v.getWeapon() != null ? v.getWeapon().getStatModifiers() : null);

    // 4. Apply utility modifiers (if compatible)
    if (itemInHand == null || itemInHand.getItem().getUtility().isCompatible()) {
        addItemStatModifiers(inventory.getUtilityItem(), statMap, "*Utility_",
            v -> v.getUtility().getStatModifiers());
    }
}
```

#### Armor Stat Extraction

```java
private static void addArmorStatModifiers(
    ItemStack itemStack,
    double brokenPenalty,
    Int2ObjectOpenHashMap<Object2FloatMap<CalculationType>> statModifiers) {

    if (ItemStack.isEmpty(itemStack)) return;

    ItemArmor armorItem = itemStack.getItem().getArmor();
    if (armorItem == null) return;

    // Get stat modifiers defined in ItemArmor config
    Int2ObjectMap<StaticModifier[]> itemStatModifiers = armorItem.getStatModifiers();
    if (itemStatModifiers == null) return;

    // Process each modifier
    computeStatModifiers(brokenPenalty, statModifiers, itemStack, itemStatModifiers);
}
```

### ItemArmor Stats

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemArmor.java`

```java
public class ItemArmor implements NetworkSerializable {
    @Nonnull
    protected ItemArmorSlot armorSlot = ItemArmorSlot.Head;

    // Stat modifiers (Health, Defense, etc.)
    @Nullable
    protected Int2ObjectMap<StaticModifier[]> statModifiers;

    // Damage resistance per damage type
    @Nullable
    protected Map<DamageCause, StaticModifier[]> damageResistanceValues;

    // Regeneration effects
    @Nullable
    protected Int2ObjectMap<List<RegeneratingValue>> regeneratingValues;

    // Knockback properties
    @Nullable
    protected Map<DamageCause, Float> knockbackResistances;
}
```

### ItemWeapon Stats

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemWeapon.java`

```java
public class ItemWeapon implements NetworkSerializable {
    // Stat modifiers (Damage, Attack Speed, etc.)
    @Nullable
    protected Int2ObjectMap<StaticModifier[]> statModifiers;

    // Stats to clear when switching weapons
    @Nullable
    protected int[] entityStatsToClear;

    protected boolean renderDualWielded;
}
```

---

## Network Synchronization

### LegacyEquipment System

**Location**: `com/hypixel/hytale/server/core/modules/entity/tracker/LegacyEntityTrackerSystems.java`

```java
public static class LegacyEquipment extends EntityTickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, ...) {
        LivingEntity entity = (LivingEntity)EntityUtils.getEntity(index, chunk);

        // Check if equipment was invalidated
        if (entity.consumeEquipmentNetworkOutdated()) {
            // Rebuild and send to all viewers
            queueUpdatesFor(ref, entity, visibleComponent.visibleTo);
        }
    }

    private static void queueUpdatesFor(Ref<EntityStore> ref, LivingEntity entity,
                                        Map<Ref<EntityStore>, EntityViewer> visibleTo) {
        ComponentUpdate update = new ComponentUpdate();
        update.type = ComponentUpdateType.Equipment;
        update.equipment = new Equipment();

        Inventory inventory = entity.getInventory();

        // Build armor array
        ItemContainer armor = inventory.getArmor();
        update.equipment.armorIds = new String[armor.getCapacity()];
        Arrays.fill(update.equipment.armorIds, "");
        armor.forEachWithMeta((slot, itemStack, armorIds) -> {
            armorIds[slot] = itemStack.getItemId();
        }, update.equipment.armorIds);

        // Set weapon and utility
        ItemStack weapon = inventory.getItemInHand();
        update.equipment.rightHandItemId = weapon != null ? weapon.getItemId() : "Empty";

        ItemStack utility = inventory.getUtilityItem();
        update.equipment.leftHandItemId = utility != null ? utility.getItemId() : "Empty";

        // Send to all viewers
        for (EntityViewer viewer : visibleTo.values()) {
            viewer.queueUpdate(ref, update);
        }
    }
}
```

---

## Blocking Equipment (Level Requirements)

### Interception Points Summary

| Method | Target | Cancellable | Complexity |
|--------|--------|-------------|------------|
| `SwitchActiveSlotEvent` | Weapons, Utilities | ✅ Yes | Low |
| Custom `SlotFilter` | Armor slots | ✅ Yes (via return false) | Medium |
| `LivingEntityInventoryChangeEvent` | All changes | ❌ No (reactive only) | N/A |

### Recommended Approach for Weapons/Utilities

```java
// Register event listener
eventRegistry.register(SwitchActiveSlotEvent.class, event -> {
    // Only care about hotbar (weapons) and utility
    int sectionId = event.getInventorySectionId();
    if (sectionId != -1 && sectionId != -5) return;

    // Get the item being switched to
    Inventory inventory = getPlayerInventory(event);
    ItemStack newItem = inventory.getContainer(sectionId).getItemStack(event.getNewSlot());

    if (newItem == null) return;  // Switching to empty slot is fine

    // Check our custom level requirement from metadata
    Integer requiredLevel = newItem.getFromMetadataOrNull("RPG:Level", Codec.INTEGER);
    if (requiredLevel == null) return;  // No requirement

    int playerLevel = getPlayerLevel(event);

    if (playerLevel < requiredLevel) {
        event.setCancelled(true);  // BLOCK THE EQUIP
        sendMessage(player, "You need level " + requiredLevel + " to equip this!");
    }
});
```

### Recommended Approach for Armor

Need to create a custom SlotFilter that wraps the existing ArmorSlotAddFilter:

```java
public class LevelRequirementArmorFilter implements SlotFilter {
    private final ArmorSlotAddFilter baseFilter;
    private final LevelingManager levelingManager;

    @Override
    public boolean test(FilterActionType actionType, ItemContainer container,
                       short slot, @Nullable ItemStack itemStack) {
        // First, check base armor slot validation
        if (!baseFilter.test(itemStack.getItem())) {
            return false;
        }

        // Then check level requirement
        if (itemStack == null) return true;  // Empty is fine

        Integer requiredLevel = itemStack.getFromMetadataOrNull("RPG:Level", Codec.INTEGER);
        if (requiredLevel == null) return true;  // No requirement

        int playerLevel = getPlayerLevelFromContainer(container);

        return playerLevel >= requiredLevel;
    }
}
```

### Implementation Challenges

| Challenge | Solution |
|-----------|----------|
| Getting player from container | May need to traverse container → inventory → entity |
| Replacing default filters | Hook into inventory creation or modify ItemContainerUtil |
| Feedback to player | Listen for failed moves, send message |

---

## Key File Locations

| Component | Path |
|-----------|------|
| **Equipment Protocol** | `com/hypixel/hytale/protocol/Equipment.java` |
| **Armor Slot Enum** | `com/hypixel/hytale/protocol/ItemArmorSlot.java` |
| **Inventory** | `com/hypixel/hytale/server/core/inventory/Inventory.java` |
| **Equip Interaction** | `com/hypixel/hytale/server/core/modules/interaction/.../EquipItemInteraction.java` |
| **ArmorSlotAddFilter** | `com/hypixel/hytale/server/core/inventory/container/filter/ArmorSlotAddFilter.java` |
| **SlotFilter Interface** | `com/hypixel/hytale/server/core/inventory/container/filter/SlotFilter.java` |
| **ItemContainerUtil** | `com/hypixel/hytale/server/core/inventory/container/ItemContainerUtil.java` |
| **StatModifiersManager** | `com/hypixel/hytale/server/core/entity/StatModifiersManager.java` |
| **ItemArmor Config** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemArmor.java` |
| **ItemWeapon Config** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemWeapon.java` |
| **Inventory Change Event** | `com/hypixel/hytale/server/core/event/events/entity/LivingEntityInventoryChangeEvent.java` |
| **Switch Slot Event** | `com/hypixel/hytale/server/core/event/events/ecs/SwitchActiveSlotEvent.java` |
| **Equipment Tracker** | `com/hypixel/hytale/server/core/modules/entity/tracker/LegacyEntityTrackerSystems.java` |
| **Packet Handler** | `com/hypixel/hytale/server/core/io/handlers/game/InventoryPacketHandler.java` |

---

## Implications for Gear System

### What We CAN Do

| Feature | How | Confidence |
|---------|-----|------------|
| Block weapon equip by level | `SwitchActiveSlotEvent.setCancelled(true)` | ✅ High |
| Block utility equip by level | `SwitchActiveSlotEvent.setCancelled(true)` | ✅ High |
| Block armor equip by level | Custom `SlotFilter` | ✅ Medium-High |
| Detect equipment changes | `LivingEntityInventoryChangeEvent` | ✅ High |
| Know when stats recalculate | Hook `StatModifiersManager` | ✅ Medium |

### What Needs More Research

| Feature | Why |
|---------|-----|
| Applying custom stat modifiers | Need to understand how to inject into StatModifiersManager |
| Getting player from container | Need to trace container ownership |
| Replacing default filters | Need to find inventory creation hook |

### Integration Strategy

1. **Level Gating (Weapons/Utilities)**:
   - Listen to `SwitchActiveSlotEvent`
   - Check `RPG:Level` from item metadata
   - Cancel if player level too low

2. **Level Gating (Armor)**:
   - Create custom `SlotFilter` wrapper
   - Replace default `ArmorSlotAddFilter` at inventory creation
   - Check `RPG:Level` from item metadata

3. **Stat Application**:
   - Either hook into `StatModifiersManager.recalculateEntityStatModifiers()`
   - Or create parallel system that listens to equipment events and applies stats separately

4. **Feedback**:
   - On cancelled equip, send chat message to player
   - Consider UI indication (red border, tooltip warning)

---

## Next Research Steps

Based on these findings, related areas to investigate:

1. **Stat/Combat System** - How to inject our modifier bonuses into damage calculations
2. **Player Access** - How to get player entity from various contexts (container, event, etc.)
3. **Inventory Creation** - Where inventories are created, to inject custom filters

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial research findings |
