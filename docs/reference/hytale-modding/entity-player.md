# Entity & Player Systems

Hytale API reference for player resolution, entity stats, modifiers, movement, teleportation, visibility, nameplates, and effects.

**Verified against:** Hytale server source and community mod implementations.

---

## Player Resolution Chain

### Universe-Level Lookups

```java
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;

// All connected players
Iterable<PlayerRef> players = Universe.get().getPlayers();

// Direct lookup by UUID
PlayerRef playerRef = Universe.get().getPlayer(uuid);

// Player count
int count = Universe.get().getPlayerCount();
```

### PlayerRef Accessors

```java
UUID uuid          = playerRef.getUuid();           // Unique identity (safe for persistence)
UUID worldUuid     = playerRef.getWorldUuid();       // Current world UUID
Transform transform = playerRef.getTransform();      // Position + rotation
Ref<EntityStore> ref = playerRef.getReference();     // ECS entity reference
PacketHandler packets = playerRef.getPacketHandler(); // Send packets to client
String locale      = playerRef.getLanguage();        // Client locale (e.g., "en-US")
Vector3f headRot   = playerRef.getHeadRotation();    // Head rotation (.getYaw() for facing)
boolean valid      = playerRef.isValid();            // Still connected?

playerRef.sendMessage(Message.of("Hello!"));
```

> **Critical:** Never store `PlayerRef` long-term. Store `UUID` and re-resolve via `Universe.get().getPlayer(uuid)` when needed. `PlayerRef` becomes invalid on disconnect.

### Player Component (from ECS)

```java
import com.hypixel.hytale.server.core.entity.entities.Player;

Player player = store.getComponent(ref, Player.getComponentType());

player.getInventory()        // Player inventory access
player.getPageManager()      // UI page management
player.getHudManager()       // HUD management
player.getGameMode()         // Current game mode
player.getWorldMapTracker()  // World map state
```

### Ref and Store Navigation

```java
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// From PlayerRef to ECS
Ref<EntityStore> entityRef = playerRef.getReference();
Store<EntityStore> store = entityRef.getStore();  // Can throw IllegalStateException across store boundaries

// From UUID to Ref (within an EntityStore)
EntityStore entityStore = (EntityStore) store.getExternalData();
Ref<EntityStore> ref = entityStore.getRefFromUUID(uuid);
```

> **Gotcha:** `getRefFromUUID()` is on `EntityStore` (the external data), not on `Store` directly. Cast or access via `store.getExternalData()`.

---

## Entity Stats & Modifiers

### Reading Stats

```java
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());

// Read a stat
int healthIndex = DefaultEntityStatTypes.getHealth();
EntityStatValue healthValue = statMap.get(healthIndex);
float current = healthValue.get();      // Current value
float max     = healthValue.getMax();   // Maximum value
```

### Default Stat Type Accessors

```java
DefaultEntityStatTypes.getHealth()          // → int (stat index)
DefaultEntityStatTypes.getStamina()         // → int
DefaultEntityStatTypes.getMana()            // → int
DefaultEntityStatTypes.getOxygen()          // → int
DefaultEntityStatTypes.getSignatureEnergy() // → int
```

### Stat Lookup by Name

```java
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;

// String-based stat index lookup (for custom or non-default stats)
int index = EntityStatType.getAssetMap().getIndex("Health");
int sigIndex = EntityStatType.getAssetMap().getIndex("SignatureEnergy");
// Returns Integer.MIN_VALUE if not found
```

Known vanilla stats: `Health`, `Stamina`, `Mana`, `Oxygen`, `SignatureEnergy`, `Ammo`.

### Direct Stat Manipulation

```java
// Set to exact value (with prediction context)
statMap.setStatValue(EntityStatMap.Predictable.SELF, statIndex, newValue);

// Add to current value (positive or negative)
statMap.addStatValue(statIndex, amount);

// Set to max value
statMap.maximizeStatValue(EntityStatMap.Predictable.ALL, statIndex);

// Without prediction (less common)
statMap.setStatValue(statIndex, newValue);
```

#### Predictable Enum

| Value | Use When |
|-------|----------|
| `EntityStatMap.Predictable.SELF` | Server-initiated change, predict for this client only |
| `EntityStatMap.Predictable.ALL` | Server-initiated change, predict for all clients |
| `EntityStatMap.Predictable.NONE` | No prediction needed |

### Stat Modifiers

Modifiers alter the calculated max/current without directly setting values. They persist until removed.

```java
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;

// Create a modifier
StaticModifier modifier = new StaticModifier(
    Modifier.ModifierTarget.MAX,              // Affects max value
    StaticModifier.CalculationType.ADDITIVE,  // Flat addition
    50.0f                                     // +50 to max
);

// Apply modifier (keyed by string for later removal)
statMap.putModifier(statIndex, "rpg_health_bonus", modifier);

// Apply with prediction
statMap.putModifier(EntityStatMap.Predictable.SELF, statIndex, "rpg_health_bonus", modifier);

// Remove modifier
statMap.removeModifier(statIndex, "rpg_health_bonus");

// Remove with prediction
statMap.removeModifier(EntityStatMap.Predictable.SELF, statIndex, "rpg_health_bonus");
```

#### Modifier Configuration

| `ModifierTarget` | Effect |
|-------------------|--------|
| `Modifier.ModifierTarget.MAX` | Modifies the stat's maximum value |

| `CalculationType` | Effect |
|--------------------|--------|
| `StaticModifier.CalculationType.ADDITIVE` | Adds flat value to max |
| `StaticModifier.CalculationType.MULTIPLICATIVE` | Multiplies max value |

### Recalculating Stats

After modifying stats, you may need to force a recalculation:

```java
// Schedule async recalculation (preferred)
statMap.getStatModifiersManager().scheduleRecalculate();

// Immediate recalculation (use when you need values right after)
statMap.getStatModifiersManager().recalculateEntityStatModifiers(ref, statMap, store);
```

### Pattern: Percentage Preservation on Max Changes

When modifying max health/mana/stamina, read the current/max ratio *before* removing the old modifier, apply the new modifier, then restore the current value to the same percentage of the new max. Without this, a player at 100/100 HP gaining +50 max ends up at 100/150 instead of 150/150. See `StatsApplicationSystem.applyStatModifierWithPreservation()` for the production implementation.

---

## Movement & Velocity

### MovementManager (Player Movement Settings)

```java
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.protocol.MovementSettings;

MovementManager movementManager = store.getComponent(entityRef, MovementManager.getComponentType());

// Read current settings
MovementSettings settings = movementManager.getSettings();

// MovementSettings fields (all mutable):
settings.baseSpeed               // Default: 5.5f
settings.jumpForce               // Default: 11.8f
settings.climbSpeed              // Default: 0.035f
settings.climbSpeedLateral       // Default: 0.035f
settings.acceleration            // Default: 0.1f
settings.sprintSpeed             // Default: 1.65f (multiplier)
settings.runSpeed                // Default: 1.0f
settings.backwardRunSpeed        // Default: 0.65f
settings.strafeRunSpeed          // Default: 0.8f
settings.walkSpeed               // Default: 0.3f
settings.crouchSpeed             // Default: 0.55f
settings.backwardCrouchSpeed     // Default: 0.4f
settings.strafeCrouchSpeed       // Default: 0.45f
settings.canFly                  // boolean

// Push changes to client (REQUIRED after modifying settings)
movementManager.update(playerRef.getPacketHandler());
```

> **Critical:** Changes to `MovementSettings` are server-side only until you call `movementManager.update(packetHandler)`. Without this, the player won't feel the difference.

### Velocity (Direct Force Application)

```java
import com.hypixel.hytale.server.core.entity.Velocity;
import com.hypixel.hytale.protocol.ChangeVelocityType;

Velocity velocity = store.getComponent(entityRef, Velocity.getComponentType());

// Add force (cumulative with existing velocity)
velocity.addForce(0.0, 11.8, 0.0);  // Launch upward

// Set velocity instruction
velocity.addInstruction(
    new Vector3d(dx, dy, dz),
    null,                        // No source entity
    ChangeVelocityType.Add       // Add to current velocity
);

// ChangeVelocityType options:
// - ChangeVelocityType.Add   — adds to current velocity
// - ChangeVelocityType.Set   — replaces current velocity

// Read current velocity
double vy = velocity.getY();
```

### MovementStatesComponent (Current Movement State)

```java
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;

MovementStatesComponent comp = store.getComponent(
    entityRef, MovementStatesComponent.getComponentType()
);

MovementStates states = comp.getMovementStates();
// States include: jumping, sprinting, crouching, flying, swimming, etc.

// Force movement state on client (e.g., force into flying)
states.setFlying(true);
playerRef.getPacketHandler().write(new SetMovementStates(states));
```

---

## Teleportation

### Same-World Teleport

```java
import com.hypixel.hytale.server.core.entity.Teleport;

// Create teleport for a player
Teleport teleport = Teleport.createForPlayer(targetWorld, targetPosition);

// Optional: set look direction
teleport.setHeadRotation(new Vector3f(yaw, pitch, 0));

// ADDING the component triggers the teleport
store.addComponent(entityRef, Teleport.getComponentType(), teleport);
```

> **Key insight:** Teleport is component-based. You don't call a "teleport" method — you add a `Teleport` component to the entity, and the engine processes it.

### Cross-Instance Teleport

For moving players between instances, use the Instances plugin API:

```java
import com.hypixel.hytale.builtin.instances.InstancesPlugin;

InstancesPlugin.teleportPlayerToInstance(playerRef, instanceWorldId);
InstancesPlugin.exitInstance(playerRef);
```

### Instance Detection

```java
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;

// Check if a world is an instance
InstanceWorldConfig instanceConfig = InstanceWorldConfig.get(world.getWorldConfig());
if (instanceConfig != null) {
    // This is an instance world
}

// Get return point for instance entities
InstanceEntityConfig entityConfig = store.getComponent(
    entityRef, InstanceEntityConfig.getComponentType()
);
Vector3d returnPoint = entityConfig.getReturnPoint();
Vector3d overridePoint = entityConfig.getReturnPointOverride();
```

---

## Visibility

### Hiding Players from Each Other

```java
PlayerRef playerRef = Universe.get().getPlayer(playerId);

// Get the hidden players manager
HiddenPlayersManager hidden = playerRef.getHiddenPlayersManager();

// Hide another player (they become invisible to this player)
hidden.hidePlayer(targetUuid);

// Show a previously hidden player
hidden.showPlayer(targetUuid);
```

---

## Nameplates

### Text Above Entities

```java
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;

// Get existing nameplate
Nameplate nameplate = store.getComponent(entityRef, Nameplate.getComponentType());
nameplate.setText("Lv.15 Goblin Warrior");

// Or ensure it exists (creates if absent)
Nameplate nameplate = holder.ensureAndGetComponent(Nameplate.getComponentType());
nameplate.setText("[Boss] Ancient Dragon");

// Add nameplate to new entity via Holder
holder.addComponent(Nameplate.getComponentType(), new Nameplate("Label Text"));
```

> **Note:** `Nameplate.setText()` accepts plain strings. For styled text, formatting must be embedded in the string using Hytale's text format codes.

### Display Name Component

```java
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;

// Read display name
DisplayNameComponent displayName = store.getComponent(
    ref, DisplayNameComponent.getComponentType()
);

// Set display name (uses Message type)
// new DisplayNameComponent(Message.of("Custom Name"))
```

---

## Entity Effects

### EffectControllerComponent

```java
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;

EffectControllerComponent effectController = store.getComponent(
    entityRef, EffectControllerComponent.getComponentType()
);

// Apply an effect
boolean success = effectController.addEffect(
    entityRef,                   // Target entity
    entityEffect,                // EntityEffect instance
    durationSeconds,             // float duration
    OverlapBehavior.OVERWRITE,   // What happens if effect already active
    store                        // ComponentAccessor for the entity store
);

// Remove an effect by index
int effectIndex = EntityEffect.getAssetMap().getIndex("effect_id");
effectController.removeEffect(entityRef, effectIndex, store);

// Toggle invulnerability
effectController.setInvulnerable(true);   // Cannot take damage
effectController.setInvulnerable(false);  // Normal damage
```

### EntityEffect Asset Lookup

```java
// Look up effect by string ID
int effectIndex = EntityEffect.getAssetMap().getIndex("EffectId");
// Returns Integer.MIN_VALUE if not found

// Get effect asset directly
EntityEffect effect = EntityEffect.getAssetMap().getAsset("EffectId");
```

### Custom Effects (Programmatic)

Extend `EntityEffect` to create effects without JSON assets. Set protected fields (`applicationEffects`, `infinite`, `duration`, `overlapBehavior`, `removalBehavior`, `invulnerable`, `name`, `statusEffectIcon`, `debuff`) in the subclass. Register via `getAssetStore().loadAssets()` before use. See `RPGEntityEffect` for a production example.

---

## UUIDComponent

### Entity UUID Lookup

```java
import com.hypixel.hytale.server.core.entity.UUIDComponent;

UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
UUID entityUuid = uuidComp.getUuid();

// Ensure component exists on entity
holder.ensureComponent(UUIDComponent.getComponentType());
```

Not all entities have `UUIDComponent` by default. NPCs and spawned entities may need it ensured.

---

## Thread Safety

- **All ECS operations** must run on the world thread: `world.execute(() -> { ... })`
- **PlayerRef** may become invalid between checks — always re-validate before use
- **Store access** across world boundaries throws `IllegalStateException`
- **Stat modifiers** are not thread-safe — apply from world thread only
- **Never store `PlayerRef` or `Ref<EntityStore>`** long-term — re-resolve from UUID

### Safe Player Resolution (Full Chain)

```java
PlayerRef playerRef = Universe.get().getPlayer(playerId);
if (playerRef == null || !playerRef.isValid()) return;

World world = Universe.get().getWorld(playerRef.getWorldUuid());
if (world == null || !world.isAlive()) return;

world.execute(() -> {
    Ref<EntityStore> entityRef = playerRef.getReference();
    Store<EntityStore> store = entityRef.getStore();
    // Safe ECS access here
});
```

---

## Import Quick Reference

| Area | Key Imports |
|------|-------------|
| Universe | `c.h.h.server.core.universe.{Universe, PlayerRef}`, `...world.World` |
| ECS | `c.h.h.component.{Ref, Store, Holder, ComponentType}`, `...storage.EntityStore` |
| Player | `c.h.h.server.core.entity.entities.Player` |
| Stats | `...modules.entitystats.{EntityStatMap, EntityStatValue}`, `...asset.{DefaultEntityStatTypes, EntityStatType}` |
| Modifiers | `...entitystats.modifier.{Modifier, StaticModifier}` |
| Movement | `...player.movement.MovementManager`, `c.h.h.protocol.{MovementSettings, ChangeVelocityType}` |
| Velocity | `c.h.h.server.core.entity.Velocity`, `...movement.MovementStatesComponent` |
| Teleport | `c.h.h.server.core.entity.Teleport`, `c.h.h.builtin.instances.{InstancesPlugin, config.InstanceWorldConfig}` |
| Nameplate | `...entity.nameplate.Nameplate`, `...entity.component.DisplayNameComponent` |
| Effects | `...entity.effect.EffectControllerComponent`, `...entityeffect.config.{EntityEffect, OverlapBehavior, RemovalBehavior}` |
| UUID | `c.h.h.server.core.entity.UUIDComponent` |
| Messages | `c.h.h.server.core.Message` |

`c.h.h` = `com.hypixel.hytale`
