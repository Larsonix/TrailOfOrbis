# Loot/Drop System Research

Research findings from investigating the Hytale decompiled codebase for loot generation and item drop mechanics.

**Research Date**: 2026-01-23
**Source**: `/home/larsonix/work/Hytale-Decompiled-Full-Game`

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Death System Architecture](#death-system-architecture)
3. [Loot Table Structure](#loot-table-structure)
4. [Drop Container Types](#drop-container-types)
5. [Loot Generation Flow](#loot-generation-flow)
6. [Item Drop Spawning](#item-drop-spawning)
7. [Player Attribution & Damage Tracking](#player-attribution--damage-tracking)
8. [Events & Interception Points](#events--interception-points)
9. [Loot Modifiers](#loot-modifiers)
10. [Key File Locations](#key-file-locations)
11. [Implications for Gear System](#implications-for-gear-system)

---

## Executive Summary

### Key Findings

| Aspect | Finding | Impact on Plugin |
|--------|---------|------------------|
| **Death Systems** | ✅ Modular `OnDeathSystem` architecture | Can create custom death handler |
| **Loot Tables** | ✅ JSON-based with weighted containers | Can define gear drop tables |
| **Drop Events** | ✅ `DropItemEvent.Drop` is cancellable | Can intercept/modify/replace drops |
| **Killer Tracking** | ✅ `DamageData` tracks all attackers | Can scale loot by killer level |
| **Metadata Support** | ✅ `ItemDrop` supports BsonDocument | Can attach RPG stats to drops |
| **Item Spawning** | ✅ `ItemComponent.generateItemDrop()` | Can spawn custom gear entities |

### Verdict: FULLY FEASIBLE

Multiple interception points exist for custom gear drops:
- Hook into `NPCDamageSystems.DropDeathItems` for mob loot
- Intercept `DropItemEvent.Drop` to modify any item drop
- Access killer info via `DeathComponent.getDeathInfo()`
- Spawn custom items with full metadata via `ItemComponent`

---

## Death System Architecture

### OnDeathSystem Pattern

When an entity dies, multiple systems process the death in order:

```
DeathComponent added to entity
           ↓
┌──────────────────────────────────────┐
│     OnDeathSystem Chain (ordered)    │
├──────────────────────────────────────┤
│ 1. DeathAnimation                    │
│ 2. PlayerDeathMarker                 │
│ 3. PlayerDeathScreen                 │
│ 4. PlayerDropItemsConfig             │
│ 5. DropPlayerDeathItems ← PLAYER     │
│ 6. DropDeathItems ← NPC/MOB          │
│ 7. RunDeathInteractions              │
│ 8. KillFeed                          │
│ 9. ClearEntityEffects                │
│ 10. ClearInteractions                │
│ 11. ClearHealth                      │
│ 12. CorpseRemoval                    │
└──────────────────────────────────────┘
```

### DeathComponent

**Location**: `com/hypixel/hytale/server/core/modules/entity/damage/DeathComponent.java`

```java
public class DeathComponent implements Component<EntityStore> {
    private Damage deathInfo;  // Contains damage source, amount, cause

    public Damage getDeathInfo() {
        return this.deathInfo;
    }
}
```

### OnDeathSystem Base

**Location**: `com/hypixel/hytale/server/core/modules/entity/damage/DeathSystems.java`

```java
public abstract static class OnDeathSystem extends EntityTickingSystem<EntityStore> {
    // Called when DeathComponent is present on entity
    // Subclasses implement death handling logic
}
```

---

## Loot Table Structure

### Core Classes

#### ItemDrop - Single Item Definition

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemDrop.java`

```java
public class ItemDrop {
    protected String itemId;           // Item type to drop
    protected BsonDocument metadata;   // ⭐ Custom data (our RPG stats!)
    protected int quantityMin = 1;     // Minimum quantity
    protected int quantityMax = 1;     // Maximum quantity

    public int getRandomQuantity(Random random) {
        return quantityMin + random.nextInt(quantityMax - quantityMin + 1);
    }
}
```

**Key**: The `metadata` field is a BsonDocument - we can attach our full gear stats here!

#### ItemDropList - Named Drop Collection

**Location**: `com/hypixel/hytale/server/core/asset/type/item/config/ItemDropList.java`

```java
public class ItemDropList implements JsonAssetWithMap<String, DefaultAssetMap<String, ItemDropList>> {
    protected String id;                    // Unique identifier
    protected ItemDropContainer container;  // The drop definition

    // Supports inheritance from parent ItemDropList
}
```

---

## Drop Container Types

All containers extend `ItemDropContainer` and support weighted selection.

### Container Hierarchy

```
ItemDropContainer (abstract)
    ├── SingleItemDropContainer     → Drops one specific item
    ├── ChoiceItemDropContainer     → Weighted random selection
    ├── MultipleItemDropContainer   → Drops all with quantity variation
    ├── DroplistItemDropContainer   → References another droplist
    └── EmptyItemDropContainer      → No drops (weighted placeholder)
```

### SingleItemDropContainer

Drops exactly one item type:

```java
public class SingleItemDropContainer extends ItemDropContainer {
    protected ItemDrop itemDrop;  // The item to drop

    @Override
    public void populateDrops(List<ItemStack> drops, DoubleSupplier random,
                             Set<String> droplistReferences) {
        int quantity = itemDrop.getRandomQuantity(random);
        drops.add(new ItemStack(itemDrop.itemId, quantity, itemDrop.metadata));
    }
}
```

### ChoiceItemDropContainer

Weighted random selection with multiple rolls:

```java
public class ChoiceItemDropContainer extends ItemDropContainer {
    protected WeightedMap<ItemDropContainer> containers;  // Weighted options
    protected int rollsMin = 1;  // Minimum rolls
    protected int rollsMax = 1;  // Maximum rolls

    @Override
    public void populateDrops(List<ItemStack> drops, DoubleSupplier random,
                             Set<String> droplistReferences) {
        int rolls = rollsMin + (int)(random.getAsDouble() * (rollsMax - rollsMin + 1));

        for (int i = 0; i < rolls; i++) {
            // Select container based on weight
            ItemDropContainer selected = containers.getRandomWeighted(random);
            selected.populateDrops(drops, random, droplistReferences);
        }
    }
}
```

### MultipleItemDropContainer

Drops from all containers with count variation:

```java
public class MultipleItemDropContainer extends ItemDropContainer {
    protected ItemDropContainer[] containers;  // All containers
    protected int minCount = 1;  // Min containers to use
    protected int maxCount;      // Max containers to use (null = all)

    @Override
    public void populateDrops(List<ItemStack> drops, DoubleSupplier random,
                             Set<String> droplistReferences) {
        int count = calculateCount(random);
        for (int i = 0; i < count && i < containers.length; i++) {
            containers[i].populateDrops(drops, random, droplistReferences);
        }
    }
}
```

### DroplistItemDropContainer

References another droplist (for reuse/inheritance):

```java
public class DroplistItemDropContainer extends ItemDropContainer {
    protected String dropListId;  // Reference to another ItemDropList

    @Override
    public void populateDrops(List<ItemStack> drops, DoubleSupplier random,
                             Set<String> droplistReferences) {
        // Prevents circular references
        if (droplistReferences.contains(dropListId)) return;

        droplistReferences.add(dropListId);
        ItemDropList referenced = ItemDropList.getAssetMap().getAsset(dropListId);
        referenced.getContainer().populateDrops(drops, random, droplistReferences);
    }
}
```

---

## Loot Generation Flow

### Complete Flow Diagram

```
ENTITY DEATH
     ↓
DeathComponent.tryAddComponent(store, ref, damage)
     ↓
OnDeathSystem chain activated
     ↓
┌─────────────────────────────────────────────────────────────┐
│  NPCDamageSystems.DropDeathItems (for mobs)                 │
│  ─────────────────────────────────────────────              │
│  1. Check ItemsLossMode == ALL                              │
│  2. Get NPCEntity.role.dropListId                           │
│  3. ItemModule.getRandomItemDrops(dropListId)               │
│       ↓                                                     │
│     ItemDropList.getContainer().populateDrops()             │
│       ↓                                                     │
│     [Recursive container selection based on weights]        │
│       ↓                                                     │
│     Returns List<ItemStack>                                 │
│  4. Also include inventory items if role.isPickupDropOnDeath│
│  5. ItemComponent.generateItemDrops(itemsToDrop)            │
│  6. commandBuffer.addEntities(drops, AddReason.SPAWN)       │
└─────────────────────────────────────────────────────────────┘
     ↓
Items appear in world with physics
```

### NPC Loot Generation

**Location**: `com/hypixel/hytale/server/npc/systems/NPCDamageSystems.java`

```java
public static class DropDeathItems extends DeathSystems.OnDeathSystem {
    @Override
    public void onDeath(Ref<EntityStore> ref, DeathComponent deathComponent,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        NPCEntity npcEntity = (NPCEntity) entity;

        if (deathConfig.getItemsLossMode() == ItemsLossMode.ALL) {
            List<ItemStack> itemsToDrop = new ArrayList<>();

            // Get loot from drop list
            String dropListId = npcEntity.getRole().getDropListId();
            if (dropListId != null) {
                itemsToDrop.addAll(ItemModule.getRandomItemDrops(dropListId));
            }

            // Add inventory items if configured
            if (npcEntity.getRole().isPickupDropOnDeath()) {
                // Add items from NPC inventory
            }

            // ⭐ INTERCEPTION POINT: itemsToDrop can be modified here

            // Spawn item entities
            Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(
                store, itemsToDrop, dropPosition, headRotation
            );

            commandBuffer.addEntities(drops, AddReason.SPAWN);
        }
    }
}
```

### Player Death Drops

**Location**: `com/hypixel/hytale/server/core/modules/entity/damage/DeathSystems.java`

```java
public static class DropPlayerDeathItems extends OnDeathSystem {
    @Override
    public void onDeath(Ref<EntityStore> ref, DeathComponent deathComponent,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        if (gameMode == GameMode.Creative) return;

        // Apply durability loss
        if (deathConfig.getItemsDurabilityLossPercentage() > 0) {
            applyDurabilityLoss(inventory, percentage);
        }

        List<ItemStack> itemsToDrop = new ArrayList<>();

        switch (deathConfig.getItemsLossMode()) {
            case ALL:
                itemsToDrop = dropAllItemStacks(inventory);
                break;
            case CONFIGURED:
                itemsToDrop = dropConfiguredItems(inventory, percentage);
                break;
            case NONE:
                return;
        }

        // ⭐ INTERCEPTION POINT: itemsToDrop can be modified here

        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(
            store, itemsToDrop, position, headRotation
        );

        commandBuffer.addEntities(drops, AddReason.SPAWN);
    }
}
```

---

## Item Drop Spawning

### ItemComponent.generateItemDrops()

**Location**: `com/hypixel/hytale/server/core/modules/entity/item/ItemComponent.java`

Spawns multiple items with circular spread pattern:

```java
public static Holder<EntityStore>[] generateItemDrops(
    Store<EntityStore> store,
    List<ItemStack> items,
    Vector3d position,
    float headRotation) {

    Holder<EntityStore>[] drops = new Holder[items.size()];

    if (items.size() == 1) {
        // Single item: drop straight up
        drops[0] = generateItemDrop(store, items.get(0), position, 0, 3.25f, 0);
    } else {
        // Multiple items: arrange in circle
        float angleStep = 360f / items.size();
        float angleOffset = ThreadLocalRandom.current().nextFloat() * 360f;

        for (int i = 0; i < items.size(); i++) {
            float angle = angleOffset + (i * angleStep);
            float vx = (float) Math.cos(Math.toRadians(angle)) * 3.0f;
            float vz = (float) Math.sin(Math.toRadians(angle)) * 3.0f;

            drops[i] = generateItemDrop(store, items.get(i), position, vx, 3.25f, vz);
        }
    }

    return drops;
}
```

### ItemComponent.generateItemDrop()

Creates a single dropped item entity:

```java
public static Holder<EntityStore> generateItemDrop(
    Store<EntityStore> store,
    ItemStack itemStack,
    Vector3d position,
    float velocityX, float velocityY, float velocityZ) {

    Holder<EntityStore> holder = store.createHolder();

    // Item data
    holder.add(new ItemComponent(itemStack));

    // Position & rotation
    holder.add(new TransformComponent(position, rotation));

    // Physics
    holder.add(new VelocityComponent(velocityX, velocityY, velocityZ));
    holder.add(new PhysicsValuesComponent());

    // Identity
    holder.add(new UUIDComponent(UUID.randomUUID()));

    // Prevent collision damage
    holder.add(new IntangibleComponent());

    // Despawn timer (default 120 seconds)
    holder.add(new DespawnComponent(itemEntityConfig.getTTL()));

    // Pickup/merge delays
    ItemComponent itemComp = holder.get(ItemComponent.class);
    itemComp.setPickupDelay(0.5f);
    itemComp.setMergeDelay(1.5f);

    return holder;
}
```

### Item Physics Constants

| Property | Value | Description |
|----------|-------|-------------|
| Vertical velocity | 3.25f | Upward bounce on drop |
| Horizontal velocity | 3.0f | Spread velocity for multiple items |
| Pickup delay | 0.5f | Seconds before pickup allowed |
| Merge delay | 1.5f | Seconds before stacking with similar |
| Default TTL | 120s | Despawn time |

---

## Player Attribution & Damage Tracking

### DamageData Class

**Location**: `com/hypixel/hytale/server/npc/util/DamageData.java`

Tracks all damage dealt and received:

```java
public class DamageData {
    // Entities this entity has killed
    private Map<Ref<EntityStore>, Vector3d> kills;

    // Damage dealt to each target
    private Object2DoubleMap<Ref<EntityStore>> damageInflicted;

    // Damage received from each attacker
    private Object2DoubleMap<Ref<EntityStore>> damageSuffered;

    // Damage by cause type (Fire, Physical, etc.)
    private Object2DoubleMap<DamageCause> damageByCause;

    // Entity that dealt most cumulative damage
    private Ref<EntityStore> mostPersistentAttacker;

    // Entity that received most damage from this entity
    private Ref<EntityStore> mostDamagedVictim;

    public void onKill(Ref<EntityStore> victim, Vector3d position) {
        kills.put(victim, position);
    }

    public void onDamageInflicted(Ref<EntityStore> target, double amount) {
        damageInflicted.mergeDouble(target, amount, Double::sum);
    }

    public void onDamageSuffered(Ref<EntityStore> attacker, double amount) {
        damageSuffered.mergeDouble(attacker, amount, Double::sum);
        updateMostPersistentAttacker(attacker);
    }
}
```

### Damage Source Types

**Location**: `com/hypixel/hytale/server/core/modules/entity/damage/Damage.java`

```java
public interface Source {
    // Damage from another entity (melee, etc.)
    record EntitySource(Ref<EntityStore> ref) implements Source {}

    // Damage from projectile (arrow, spell, etc.)
    record ProjectileSource(Ref<EntityStore> shooter,
                           Ref<EntityStore> projectile) implements Source {}

    // Environmental damage (fall, lava, etc.)
    record EnvironmentSource(String type) implements Source {}

    // Command-based damage (/kill, etc.)
    record CommandSource(Ref<EntityStore> sender,
                        String commandName) implements Source {}

    // No source
    Source NULL_SOURCE = new Source() {};
}
```

### Getting the Killer

```java
// From DeathComponent
Damage deathInfo = deathComponent.getDeathInfo();
Damage.Source source = deathInfo.getSource();

if (source instanceof Damage.EntitySource entitySource) {
    Ref<EntityStore> killer = entitySource.ref();
    // Access killer entity for level-based loot scaling
}

if (source instanceof Damage.ProjectileSource projectileSource) {
    Ref<EntityStore> shooter = projectileSource.shooter();
    // Original shooter for ranged kills
}
```

### Getting Most Persistent Attacker

For group/party scenarios:

```java
NPCEntity npcEntity = (NPCEntity) entity;
DamageData damageData = npcEntity.getDamageData();

// Entity that dealt most total damage
Ref<EntityStore> mainAttacker = damageData.getMostPersistentAttacker();

// Or iterate all attackers for contribution-based loot
Object2DoubleMap<Ref<EntityStore>> allAttackers = damageData.getDamageSuffered();
for (var entry : allAttackers.object2DoubleEntrySet()) {
    Ref<EntityStore> attacker = entry.getKey();
    double damageDealt = entry.getDoubleValue();
    // Calculate loot share based on contribution
}
```

---

## Events & Interception Points

### DropItemEvent.Drop (CANCELLABLE!)

**Location**: `com/hypixel/hytale/server/core/event/events/ecs/DropItemEvent.java`

Fired when any item is about to be dropped:

```java
public class DropItemEvent {
    public static class Drop extends CancellableEcsEvent {
        private ItemStack itemStack;
        private float throwSpeed;

        // Modify the item being dropped
        public void setItemStack(ItemStack itemStack) {
            this.itemStack = itemStack;
        }

        // Modify throw physics
        public void setThrowSpeed(float throwSpeed) {
            this.throwSpeed = throwSpeed;
        }

        // Cancel the drop entirely
        public void setCancelled(boolean cancelled) {
            super.setCancelled(cancelled);
        }
    }
}
```

### KillFeedEvent

**Location**: `com/hypixel/hytale/server/core/modules/entity/damage/event/KillFeedEvent.java`

```java
public class KillFeedEvent {
    // Can cancel to hide death message
    public static class Display extends CancellableEcsEvent { }

    // Message shown to killer
    public static class KillerMessage extends EcsEvent { }

    // Message shown to victim
    public static class DecedentMessage extends EcsEvent { }
}
```

### InteractivelyPickupItemEvent

**Location**: For item pickup interception (before item enters inventory)

```java
// Can intercept item pickup
// Redirect to different container
// Cancel pickup entirely
```

---

## Loot Modifiers

### DeathConfig Settings

**Location**: `com/hypixel/hytale/server/core/asset/type/gameplay/DeathConfig.java`

```java
public class DeathConfig {
    // What items drop on death
    protected ItemsLossMode itemsLossMode;  // NONE, ALL, CONFIGURED

    // Percentage of items to drop (for CONFIGURED mode)
    protected float itemsAmountLossPercentage;  // 0-100

    // Durability loss on death
    protected float itemsDurabilityLossPercentage;  // 0-100
}

public enum ItemsLossMode {
    NONE,        // No items drop
    ALL,         // All items drop
    CONFIGURED   // Percentage-based
}
```

### Weight System

All `ItemDropContainer` types have a `Weight` property for probability:

```java
// In ChoiceItemDropContainer
WeightedMap<ItemDropContainer> containers;

// Higher weight = higher chance of selection
// Weight 0 = never selected
// Weight compared relative to other options
```

### Quantity Randomization

```java
// In ItemDrop
int quantity = quantityMin + random.nextInt(quantityMax - quantityMin + 1);

// Example: quantityMin=1, quantityMax=5
// Drops 1-5 items randomly
```

---

## Key File Locations

### Loot Table Classes

| Component | Path |
|-----------|------|
| **ItemDrop** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemDrop.java` |
| **ItemDropList** | `com/hypixel/hytale/server/core/asset/type/item/config/ItemDropList.java` |
| **ItemDropContainer** | `com/hypixel/hytale/server/core/asset/type/item/config/container/ItemDropContainer.java` |
| **SingleItemDropContainer** | `com/hypixel/hytale/server/core/asset/type/item/config/container/SingleItemDropContainer.java` |
| **ChoiceItemDropContainer** | `com/hypixel/hytale/server/core/asset/type/item/config/container/ChoiceItemDropContainer.java` |
| **MultipleItemDropContainer** | `com/hypixel/hytale/server/core/asset/type/item/config/container/MultipleItemDropContainer.java` |
| **DroplistItemDropContainer** | `com/hypixel/hytale/server/core/asset/type/item/config/container/DroplistItemDropContainer.java` |
| **EmptyItemDropContainer** | `com/hypixel/hytale/server/core/asset/type/item/config/container/EmptyItemDropContainer.java` |

### Death System Classes

| Component | Path |
|-----------|------|
| **DeathComponent** | `com/hypixel/hytale/server/core/modules/entity/damage/DeathComponent.java` |
| **DeathSystems** | `com/hypixel/hytale/server/core/modules/entity/damage/DeathSystems.java` |
| **DeathConfig** | `com/hypixel/hytale/server/core/asset/type/gameplay/DeathConfig.java` |
| **NPCDamageSystems** | `com/hypixel/hytale/server/npc/systems/NPCDamageSystems.java` |
| **NPCDeathSystems** | `com/hypixel/hytale/server/npc/systems/NPCDeathSystems.java` |

### Damage & Attribution Classes

| Component | Path |
|-----------|------|
| **Damage** | `com/hypixel/hytale/server/core/modules/entity/damage/Damage.java` |
| **DamageData** | `com/hypixel/hytale/server/npc/util/DamageData.java` |
| **DamageEventSystem** | `com/hypixel/hytale/server/core/modules/entity/damage/DamageEventSystem.java` |

### Item Spawning Classes

| Component | Path |
|-----------|------|
| **ItemComponent** | `com/hypixel/hytale/server/core/modules/entity/item/ItemComponent.java` |
| **ItemModule** | `com/hypixel/hytale/server/core/modules/item/ItemModule.java` |
| **ItemUtils** | `com/hypixel/hytale/server/core/entity/ItemUtils.java` |

### Event Classes

| Component | Path |
|-----------|------|
| **DropItemEvent** | `com/hypixel/hytale/server/core/event/events/ecs/DropItemEvent.java` |
| **KillFeedEvent** | `com/hypixel/hytale/server/core/modules/entity/damage/event/KillFeedEvent.java` |

---

## Implications for Gear System

### What We CAN Do

| Feature | How | Confidence |
|---------|-----|------------|
| Replace mob drops with custom gear | Hook `NPCDamageSystems.DropDeathItems` | ✅ High |
| Scale loot by player level | Access killer via `DeathComponent.getDeathInfo()` | ✅ High |
| Set gear stats on drops | Use `ItemDrop.metadata` (BsonDocument) | ✅ High |
| Control rarity distribution | Use `Weight` system in containers | ✅ High |
| Cancel/modify any drop | `DropItemEvent.Drop.setCancelled()` | ✅ High |
| Spawn custom items | `ItemComponent.generateItemDrop()` | ✅ High |
| Track damage contribution | `DamageData.getDamageSuffered()` | ✅ High |

### Integration Strategy

#### 1. Custom Gear Drop System

```java
// Register death event listener
eventRegistry.register(EntityDeathEvent.class, event -> {
    Entity entity = event.getEntity();
    if (!(entity instanceof NPCEntity npc)) return;

    // Get killer info
    Damage.Source source = event.getDeathComponent().getDeathInfo().getSource();
    if (!(source instanceof Damage.EntitySource entitySource)) return;

    Player killer = getPlayer(entitySource.ref());
    if (killer == null) return;

    int playerLevel = levelingManager.getLevel(killer);
    int mobLevel = getMobLevel(npc);

    // Generate custom gear
    ItemStack gear = gearGenerator.generateGear(
        mobLevel,
        playerLevel,
        getRarityRoll(),
        getQualityRoll()
    );

    // Attach RPG metadata
    gear = gear.withMetadata("RPG:Level", Codec.INTEGER, gearLevel);
    gear = gear.withMetadata("RPG:Rarity", Codec.STRING, rarity);
    gear = gear.withMetadata("RPG:Quality", Codec.INTEGER, quality);
    gear = gear.withMetadata("RPG:Modifiers", modifierCodec, modifiers);

    // Spawn the item
    ItemComponent.generateItemDrop(store, gear, npc.getPosition(), 0, 3.25f, 0);
});
```

#### 2. Intercept Existing Drops

```java
// Intercept any item drop
eventRegistry.register(DropItemEvent.Drop.class, event -> {
    ItemStack item = event.getItemStack();

    // Check if it's equipment
    if (isEquipment(item)) {
        // Replace with our custom gear version
        ItemStack customGear = convertToRPGGear(item);
        event.setItemStack(customGear);
    }
});
```

#### 3. Party Loot Distribution

```java
// Get all contributors
DamageData damageData = npc.getDamageData();
Object2DoubleMap<Ref<EntityStore>> contributors = damageData.getDamageSuffered();

// Calculate average party level
int avgLevel = calculateAverageLevel(contributors.keySet());

// Generate gear based on average
ItemStack gear = gearGenerator.generateGear(mobLevel, avgLevel, ...);

// Could implement need/greed or instanced loot here
```

### Recommended Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    GearDropManager                          │
├─────────────────────────────────────────────────────────────┤
│ + onMobDeath(NPCEntity, DeathComponent)                     │
│   - Get killer/contributors                                 │
│   - Calculate item level (mob level ± variance)             │
│   - Roll rarity (50/30/15/4/0.9/0.1)                       │
│   - Roll quality (1-101%)                                   │
│   - Generate modifiers (based on rarity)                    │
│   - Create ItemStack with metadata                          │
│   - Spawn via ItemComponent.generateItemDrop()              │
├─────────────────────────────────────────────────────────────┤
│ + generateGear(mobLevel, playerLevel, rarity, quality)      │
│   - Select base item from mob's gear pool                   │
│   - Calculate item level                                    │
│   - Roll prefix count (0-2)                                 │
│   - Roll suffix count (0-2)                                 │
│   - Select modifiers from pools                             │
│   - Roll modifier values                                    │
│   - Apply quality multiplier                                │
│   - Calculate durability                                    │
│   - Return complete ItemStack                               │
└─────────────────────────────────────────────────────────────┘
```

### XP Note

**Important**: XP is NOT part of the loot system. There are no XP orbs or experience drops. Our existing `LevelingManager` handles XP separately - we'll need to hook death events to award XP, which is already done in our plugin.

---

## Next Research Steps

Based on these findings, related areas to investigate:

1. **Combat System** - How damage calculation works, where to inject our stat modifiers
2. **Mob Levels** - How to determine/assign levels to mobs for loot scaling
3. **Loot Table Assets** - Examine actual JSON loot tables in Assets folder

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial research findings |
