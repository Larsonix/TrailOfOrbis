# Hytale Player Stats

Use this skill when reading or modifying player/entity stats (health, stamina, mana, etc.) in Hytale plugins. Stats are managed through the `EntityStatMap` component and accessed via `DefaultEntityStatTypes`.

> **Source:** <https://hytalemodding.dev/en/docs/guides/plugin/player-stats>

---

## Quick Reference

| Task | Code |
|------|------|
| Get stat map component | `store.getComponent(playerRef, EntityStatMap.getComponentType())` |
| Set a stat value | `statMap.setStatValue(statIndex, value)` |
| Add to a stat | `statMap.addStatValue(statIndex, amount)` |
| Subtract from a stat | `statMap.subtractStatValue(statIndex, amount)` |
| Maximize a stat (full restore) | `statMap.maximizeStatValue(statIndex)` |
| Reset a stat | `statMap.resetStatValue(statIndex)` |

---

## Available Stats

Hytale provides default stats via `DefaultEntityStatTypes`:

| Stat | Accessor |
|------|----------|
| Health | `DefaultEntityStatTypes.getHealth()` |
| Stamina | `DefaultEntityStatTypes.getStamina()` |
| Mana | `DefaultEntityStatTypes.getMana()` |
| Oxygen | `DefaultEntityStatTypes.getOxygen()` |
| Signature Energy | `DefaultEntityStatTypes.getSignatureEnergy()` |
| Ammo | `DefaultEntityStatTypes.getAmmo()` |

---

## Key Concepts

### EntityStatMap

`EntityStatMap` is an ECS component that holds all stat values for an entity. Retrieve it from the store:

```java
EntityStatMap statMap = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
```

### World Thread Safety

All stat modifications **must** be performed on the world thread using `world.execute(() -> { ... })` to ensure thread safety.

### Stat Indices

Each stat type (e.g., `DefaultEntityStatTypes.getHealth()`) returns a stat index used by `EntityStatMap` methods. Pass these indices to set/add/subtract/maximize/reset operations.

---

## Required Imports

```java
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
```

---

## EntityStatMap Methods

| Method | Description |
|--------|-------------|
| `setStatValue(statIndex, value)` | Sets the stat to an exact value |
| `addStatValue(statIndex, amount)` | Adds to the current stat value |
| `subtractStatValue(statIndex, amount)` | Subtracts from the current stat value |
| `maximizeStatValue(statIndex)` | Restores the stat to its maximum value |
| `resetStatValue(statIndex)` | Resets the stat to its default value |

---

## Access Pattern

The standard pattern for accessing and modifying stats:

```java
// 1. Get the player reference
Ref<EntityStore> playerRef = /* obtain player ref */;

// 2. Get the store and world
Store<EntityStore> store = playerRef.getStore();
EntityStore entityStore = store.getExternalData();
World world = entityStore.getWorld();

// 3. Modify on the world thread
world.execute(() -> {
    EntityStatMap statMap = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
    if (statMap != null) {
        // Perform stat operations here
    }
});
```

---

## Example: Heal Command

Restores the player's health to its maximum value.

```java
public class HealCommand extends CommandBase {
    public HealCommand() {
        super("heal", "Restores your health to maximum.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        // 1. Get the player reference
        Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();
        if (playerRef == null) return;

        // 2. Get the store and the world
        Store<EntityStore> store = playerRef.getStore();
        EntityStore entityStore = store.getExternalData();
        World world = entityStore.getWorld();

        // 3. Perform modification on the world thread
        world.execute(() -> {
            EntityStatMap statMap = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
                ctx.sendMessage(Message.raw("Your health has been restored!"));
            }
        });
    }
}
```

---

## Example: Damage Self Command

Removes a specified amount of health from the player.

```java
public class DamageSelfCommand extends CommandBase {
    private final Argument amountArg;

    public DamageSelfCommand() {
        super("damageself", "Damages yourself by a specific amount.");
        this.setPermissionGroup(GameMode.Adventure);
        this.amountArg = this.withRequiredArg("amount", "Amount of damage", ArgTypes.FLOAT);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        // 1. Get the player reference
        Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();
        if (playerRef == null) return;

        // 2. Get command arg
        Float amount = (Float) this.amountArg.get(ctx);
        if (amount == null) return;

        // 3. Get the store and the world
        Store<EntityStore> store = playerRef.getStore();
        EntityStore entityStore = store.getExternalData();
        World world = entityStore.getWorld();

        // 4. Perform modification on the world thread
        world.execute(() -> {
            EntityStatMap statMap = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                statMap.subtractStatValue(DefaultEntityStatTypes.getHealth(), amount);
                ctx.sendMessage(Message.raw("Ouch! You took " + amount + " damage."));
            }
        });
    }
}
```

---

## Common Patterns

### Restore All Stats

```java
world.execute(() -> {
    EntityStatMap statMap = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
    if (statMap != null) {
        statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
        statMap.maximizeStatValue(DefaultEntityStatTypes.getStamina());
        statMap.maximizeStatValue(DefaultEntityStatTypes.getMana());
        statMap.maximizeStatValue(DefaultEntityStatTypes.getOxygen());
    }
});
```

### Set Stat to Specific Value

```java
world.execute(() -> {
    EntityStatMap statMap = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
    if (statMap != null) {
        statMap.setStatValue(DefaultEntityStatTypes.getHealth(), 50.0f);
    }
});
```

### Add to a Stat (Partial Heal)

```java
world.execute(() -> {
    EntityStatMap statMap = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
    if (statMap != null) {
        statMap.addStatValue(DefaultEntityStatTypes.getHealth(), 20.0f);
    }
});
```

---

## Reading Stat Values

Use `EntityStatValue` to read current, min, and max values for any stat:

```java
EntityStatMap statMap = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());

int healthIndex = DefaultEntityStatTypes.getHealth();
EntityStatValue health = statMap.get(healthIndex);

float current  = health.get();           // Current value (clamped to min/max)
float min      = health.getMin();        // Minimum bound
float max      = health.getMax();        // Maximum bound (includes modifiers)
float percent  = health.asPercentage();  // (current - min) / (max - min), 0.0 to 1.0
```

### Required Imports for Reading

```java
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
```

### Stat Snapshot Pattern

Capture before/after values for displaying stat diffs (e.g., after equipping gear or allocating a skill point):

```java
// Before
EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
float oldMax = health.getMax();

// ... apply changes (equip gear, add modifier, etc.) ...

// After
float newMax = health.getMax();
float diff = newMax - oldMax;
// Display: "Health: 100 → 120 (+20)"
```

---

## Stat Modifiers

Modifiers change a stat's min or max bounds. They are keyed by a string name, so they can be added and removed independently.

### Required Imports for Modifiers

```java
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.CalculationType;
```

### Add a Named Modifier

```java
int healthIndex = DefaultEntityStatTypes.getHealth();

// +50 flat max health
Modifier flatBonus = new StaticModifier(
    ModifierTarget.MAX,
    CalculationType.ADDITIVE,
    50.0f
);
statMap.putModifier(healthIndex, "rpg_gear_bonus", flatBonus);

// +20% max health (multiply by 1.2)
Modifier percentBonus = new StaticModifier(
    ModifierTarget.MAX,
    CalculationType.MULTIPLICATIVE,
    1.2f
);
statMap.putModifier(healthIndex, "rpg_skill_bonus", percentBonus);
```

### Remove a Named Modifier

```java
statMap.removeModifier(healthIndex, "rpg_gear_bonus");
// Max health reverts (recalculated from remaining modifiers)
```

### Get Existing Modifier

```java
Modifier mod = statMap.getModifier(healthIndex, "rpg_gear_bonus");
if (mod != null) {
    // Modifier exists
}
```

### Modifier Application Order

1. All `ADDITIVE` modifiers are summed: `base + sum(additive_amounts)`
2. All `MULTIPLICATIVE` modifiers are multiplied: `result * product(multiplicative_amounts)`
3. Current value is clamped to the new min/max bounds

### ModifierTarget

| Value | Description |
|-------|-------------|
| `MIN` | Modifies the stat's minimum bound |
| `MAX` | Modifies the stat's maximum bound (most common) |

### CalculationType

| Value | Description | Example |
|-------|-------------|---------|
| `ADDITIVE` | Adds amount to base | `+50` flat bonus |
| `MULTIPLICATIVE` | Multiplies base by amount | `×1.2` for 20% increase |

---

## Predictable Enum (Client Prediction)

All stat modification methods have overloads accepting a `Predictable` parameter for client-side prediction:

```java
// With prediction (client predicts the change immediately)
statMap.setStatValue(Predictable.ALL, healthIndex, 100f);
statMap.putModifier(Predictable.ALL, healthIndex, "bonus", modifier);
statMap.removeModifier(Predictable.ALL, healthIndex, "bonus");
```

| Value | Description |
|-------|-------------|
| `Predictable.NONE` | No client prediction (default) |
| `Predictable.SELF` | Predict for the owning player |
| `Predictable.ALL` | Predict for all players |

Use `Predictable.ALL` for responsive UI feedback (e.g., health bar updates immediately on the client without waiting for server confirmation).

---

## Bulk Stat Changes

Apply multiple stat changes at once (useful for effect-driven stat modifications):

```java
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.ValueType;

Int2FloatMap changes = new Int2FloatOpenHashMap();
changes.put(DefaultEntityStatTypes.getHealth(), 50f);
changes.put(DefaultEntityStatTypes.getStamina(), 25f);

statMap.processStatChanges(
    Predictable.NONE,
    changes,
    ValueType.Percent,                        // Percent = percentage of (max - min)
    EntityStatMap.ChangeStatBehaviour.ADD      // ADD or SET
);
```

---

## EntityStatType Asset

Each stat is defined as an asset with default values, regen rules, and boundary effects:

```java
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;

var assetMap = EntityStatType.getAssetMap();
EntityStatType healthType = assetMap.getAsset("Health");

float initialValue = healthType.getInitialValue();
float min = healthType.getMin();
float max = healthType.getMax();
boolean shared = healthType.isShared();  // synced to other players?
```

---

## Important Notes

- Always null-check `playerRef` and `statMap` before use.
- Always wrap stat modifications in `world.execute(() -> { ... })` for thread safety.
- `maximizeStatValue` restores to the entity's configured maximum (including modifiers), not a hardcoded value.
- These APIs work on any entity with an `EntityStatMap` component, not just players.
- Stat access by index (`get(int)`) is O(1) array lookup. Access by name (`get(String)`) does an asset map lookup — avoid in hot paths.
- Modifiers are recalculated on every `putModifier`/`removeModifier` call. Batch changes if possible.
