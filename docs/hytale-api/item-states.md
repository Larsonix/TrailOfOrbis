# Hytale Item States & Metadata

Use this skill when working with multi-state items (items that change appearance/behavior) or storing arbitrary data on item stacks. Covers item state definitions in JSON, programmatic state changes, and the KeyedCodec-based metadata system.

> **Related skills:** For item definitions and crafting, see `docs/hytale-api/items.md`. For custom interactions, see `docs/hytale-api/interactions.md`. For inventory management, see `docs/hytale-api/inventory.md`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Define item states | `"State": { "StateName": { ... } }` in item JSON |
| Change item state in code | `item.withState("StateName")` (returns new ItemStack) |
| Read current item state | `item.getItem().getStateForItem(item.getItemId())` |
| Store data on an item | `item.withMetadata(keyedCodec, value)` (returns new ItemStack) |
| Read data from an item | `item.getFromMetadataOrNull(keyedCodec)` |
| Clear metadata | `item.withMetadata(keyedCodec, null)` |
| Update item in inventory | `container.setItemStackForSlot(slot, newItem)` |

---

## Item States

### Overview

An item can have multiple **states**, each overriding properties like texture, icon, quality, name, and interactions. States are defined in the item's JSON file.

### JSON Definition

```json
{
  "Model": "Items/MyItem.blockymodel",
  "Texture": "Items/MyItemDefault.png",
  "Icon": "Icons/ItemsGenerated/MyItem.png",
  "TranslationProperties": {
    "Name": "items.my_item.name",
    "Description": "items.my_item.description"
  },
  "Interactions": {
    "Secondary": {
      "Interactions": [{ "Type": "MyInteraction" }]
    }
  },
  "State": {
    "Normal": {
      "Quality": "Developer"
    },
    "Linking": {
      "Texture": "Items/MyItemActive.png",
      "Icon": "Icons/ItemsGenerated/MyItemActive.png",
      "Quality": "Developer",
      "TranslationProperties": {
        "Name": "items.my_item_active.name",
        "Description": "items.my_item_active.description"
      }
    }
  }
}
```

### State Overrides

Each state can override:

| Property | Description |
|----------|-------------|
| `Texture` | 3D model texture (held in hand) |
| `Icon` | Inventory icon |
| `Quality` | Item quality/rarity tier |
| `TranslationProperties` | Display name and description |
| `Model` | 3D model |

Properties not specified in a state inherit from the item's root definition.

### Reading State in Code

```java
import com.hypixel.hytale.server.core.inventory.ItemStack;

ItemStack item = interactionContext.getHeldItem();
String state = item.getItem().getStateForItem(item.getItemId());

// state is null if no state is set (default state)
if (state == null) {
    state = "Normal";
}

if ("Linking".equalsIgnoreCase(state)) {
    // Handle linking state
}
```

### Changing State in Code

`ItemStack` is immutable — `withState()` returns a new instance:

```java
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

ItemContainer container = interactionContext.getHeldItemContainer();
short slot = interactionContext.getHeldItemSlot();
ItemStack item = container.getItemStack(slot);

if (item != null) {
    // Change state (returns new ItemStack)
    item = item.withState("Linking");

    // Write back to inventory
    container.setItemStackForSlot(slot, item);
}
```

**Important**: Always write the new ItemStack back to the container. The original item in the slot is not modified.

---

## Item Metadata

### Overview

Item metadata stores arbitrary key-value data on an individual `ItemStack`. The data is serialized/deserialized via Hytale's codec system and persists with the item.

### Defining Metadata Keys

Use `KeyedCodec<T>` to define typed metadata keys:

```java
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.math.vector.Vector3i;

// Single value key
public static final KeyedCodec<Vector3i> POSITION_KEY =
    new KeyedCodec<>("Position", Vector3i.CODEC);

// Collection key
public static final KeyedCodec<Set<Vector3i>> TARGETS_KEY =
    new KeyedCodec<>("Targets", new SetCodec<>(Vector3i.CODEC, HashSet::new, false));
```

### Writing Metadata

```java
ItemStack item = container.getItemStack(slot);
if (item != null) {
    item = item.withMetadata(POSITION_KEY, new Vector3i(10, 64, 20));
    item = item.withMetadata(TARGETS_KEY, targetSet);
    container.setItemStackForSlot(slot, item);
}
```

### Reading Metadata

```java
ItemStack item = interactionContext.getHeldItem();
Vector3i position = item.getFromMetadataOrNull(POSITION_KEY);
Set<Vector3i> targets = item.getFromMetadataOrNull(TARGETS_KEY);

if (position != null) {
    // Use position
}
```

### Clearing Metadata

```java
item = item.withMetadata(POSITION_KEY, null);  // Removes the key
container.setItemStackForSlot(slot, item);
```

### Chaining Operations

Since `withState()` and `withMetadata()` both return new `ItemStack` instances, chain them:

```java
item = item.withState("Linking");
item = item.withMetadata(POSITION_KEY, sourcePos);
item = item.withMetadata(TARGETS_KEY, targetSet);
container.setItemStackForSlot(slot, item);
```

---

## Common Patterns

### State Machine Item (Linking Tool)

A tool that toggles between "Normal" and "Linking" states, storing context data in metadata:

```java
public class MyToolInteraction extends SimpleInstantInteraction {

    static KeyedCodec<Vector3i> SOURCE_POS = new KeyedCodec<>("SourcePos", Vector3i.CODEC);

    @Override
    protected void firstRun(InteractionType type, InteractionContext ctx, CooldownHandler cooldown) {
        ItemStack item = ctx.getHeldItem();
        String state = item.getItem().getStateForItem(item.getItemId());
        if (state == null) state = "Normal";

        ItemContainer container = ctx.getHeldItemContainer();
        short slot = ctx.getHeldItemSlot();

        if ("Normal".equalsIgnoreCase(state)) {
            // Start linking — switch to Linking state, store source position
            Vector3i target = TargetUtil.getTargetBlock(ctx.getEntity(), 10.0, ctx.getCommandBuffer());
            if (target != null) {
                item = item.withState("Linking");
                item = item.withMetadata(SOURCE_POS, target);
                container.setItemStackForSlot(slot, item);
            }
        } else {
            // Already linking — complete action, reset to Normal
            Vector3i source = item.getFromMetadataOrNull(SOURCE_POS);
            // ... use source position ...

            // Reset
            item = item.withState("Normal");
            item = item.withMetadata(SOURCE_POS, null);
            container.setItemStackForSlot(slot, item);
        }
    }
}
```

### Reading State in an EntityStore System

For visual feedback (particles, HUD) based on held item state:

```java
public class MyFeedbackSystem extends DelayedEntitySystem<EntityStore> {
    public MyFeedbackSystem() { super(0.5f); }  // Tick every 0.5s

    @Override
    public void tick(float v, int i, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {
        Player player = chunk.getComponent(i, Player.getComponentType());
        ItemStack item = player.getInventory().getItemInHand();
        if (item == null) return;

        String state = item.getItem().getStateForItem(item.getItemId());
        if ("Linking".equalsIgnoreCase(state)) {
            Set<Vector3i> targets = item.getFromMetadataOrNull(TARGETS_KEY);
            if (targets != null) {
                for (Vector3i target : targets) {
                    ParticleUtil.spawnParticleEffect("Block_Select_System", target.toVector3d(), cmd);
                }
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() { return Player.getComponentType(); }
}
```

---

## Key Imports

```java
// Item state and metadata
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.math.vector.Vector3i;
```

---

## Reference

- Source: Verified against Hytale server source and community mod implementations
- Related: `docs/hytale-api/items.md` (item definitions)
- Related: `docs/hytale-api/interactions.md` (custom interactions)
- Related: `docs/hytale-api/inventory.md` (inventory access)

> **TrailOfOrbis project notes:** Item metadata could be useful for realm maps (storing destination instance), linking tools, or consumable items with dynamic state.
