# NPCs — Hytale Modding Reference

Spawning, roles, custom actions, AI behavior, mounts, animations, spawn suppression, and entity creation from scratch.

**Verified against:** Hytale server source and community mod implementations.

---

## Spawning NPCs

### Basic Spawn

```java
import com.hytale.server.npc.NPCPlugin;
import com.hytale.server.world.entity.EntityStore;
import com.hytale.server.world.entity.Ref;
import org.joml.Vector3f;

// Returns Pair<Ref, ?> — the Ref is the entity reference
Pair<Ref, ?> result = NPCPlugin.get().spawnNPC(
    store,       // EntityStore — the world's entity store
    roleId,      // String — the NPC role name (e.g., "Trork_Grunt")
    null,        // String — variant (null for default)
    pos,         // Vector3f — spawn position
    rotation     // float — Y-axis rotation in degrees
);

Ref npcRef = result.getFirst();
```

### Spawn with Callbacks

```java
// Pre-spawn callback fires BEFORE the entity is added to the world.
// Post-spawn callback fires AFTER. Both receive the entity Ref.

NPCPlugin.get().spawnEntity(
    store,               // EntityStore
    roleIndex,           // int — numeric index from getIndex()
    pos,                 // Vector3f
    rot,                 // float
    null,                // String variant (null for default)
    preSpawnCallback,    // Consumer<Ref> — runs before entity enters world
    postSpawnCallback    // Consumer<Ref> — runs after entity enters world
);
```

**Pre-spawn callback uses**: Attach custom components, set initial state, configure display name, mark as intangible — anything that must be set before the entity is visible to clients.

**Post-spawn callback uses**: Start timers, register the entity in tracking maps, play spawn animations.

### Getting the Role Index

```java
// Role name → numeric index (used by spawnEntity)
int roleIndex = NPCPlugin.get().getIndex("Trork_Grunt");
```

The role name matches the JSON filename under `Server/NPC/Roles/` without the `.json` extension.

---

## NPC Role JSON Structure

NPC roles are defined in `Server/NPC/Roles/<RoleName>.json`. The behavior tree follows an `Init → Idle → Watching → $Interaction` state pattern.

```json
{
  "Identifier": "My_Custom_NPC",
  "Model": "Common/Models/NPCs/My_NPC.blockymodel",
  "Health": 100,
  "MovementSpeed": 3.5,
  "DetectionRange": 15.0,
  "AttackDamage": 10,
  "AttackRange": 2.0,
  "Loot": "Server/Loot/Tables/My_NPC_Loot.json",
  "States": {
    "Init": {
      "Actions": [
        { "Type": "SetIdle" }
      ]
    },
    "Idle": {
      "Actions": [
        { "Type": "Wander", "Range": 5.0 },
        { "Type": "LookForTarget", "Range": 15.0, "TransitionTo": "Watching" }
      ]
    },
    "Watching": {
      "Actions": [
        { "Type": "ChaseTarget", "Speed": 4.0 },
        { "Type": "AttackTarget", "Cooldown": 1.0 }
      ]
    },
    "$Interaction": {
      "Actions": [
        { "Type": "FaceTarget" },
        { "Type": "MyCustomAction" }
      ]
    }
  }
}
```

**Key patterns**:
- `$Interaction` is a special state name that activates when a player interacts with the NPC
- Custom action types are registered via `registerCoreComponentType()` (see below)
- States transition via `TransitionTo` fields in actions

---

## Custom Actions

Custom NPC actions let you define behavior that executes within the NPC's state machine.

### Registering a Custom Action Type

```java
NPCPlugin.get().registerCoreComponentType(
    "MyCustomAction",          // String — matches "Type" in role JSON
    MyCustomAction::new        // Factory — creates action instances
);
```

Register during plugin `onEnable`, before any NPCs using the action type are spawned.

### Action Implementation

Extend `ActionBase` for simple actions:

```java
public class MyCustomAction extends ActionBase {

    @Override
    public void execute(Role role, InfoProvider infoProvider, float dt) {
        // role — the NPC's Role instance (state machine, entity data)
        // infoProvider — provides world context
        // dt — delta time since last tick

        // Get the player interacting with this NPC
        PlayerRef player = role.getStateSupport()
            .getInteractionIterationTarget();

        if (player != null) {
            // Do something with the player
        }
    }
}
```

Extend `BuilderActionBase` for actions that need initialization from JSON parameters:

```java
public class MyParameterizedAction extends BuilderActionBase {

    private float range;
    private String message;

    @Override
    public void build(JsonObject params) {
        this.range = params.get("Range").getAsFloat();
        this.message = params.get("Message").getAsString();
    }

    @Override
    public void execute(Role role, InfoProvider infoProvider, float dt) {
        // Use this.range, this.message, etc.
    }
}
```

JSON usage for parameterized actions:
```json
{ "Type": "MyParameterizedAction", "Range": 10.0, "Message": "Hello!" }
```

### Getting the Interacting Player

```java
// Inside an action's execute() method:
PlayerRef player = role.getStateSupport().getInteractionIterationTarget();

// Returns null if no player is currently interacting
if (player != null) {
    UUID playerId = player.getUUID();
    // ...
}
```

---

## NPCEntity Operations

Once you have a `Ref`, retrieve the `NPCEntity` component to inspect and control the NPC.

```java
NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());

// Get the NPC's type information
ComponentType componentType = npcEntity.getComponentType();
String typeId = npcEntity.getNPCTypeId();   // e.g., "Trork_Grunt"

// Mark for despawn (graceful — finishes current action)
npcEntity.setToDespawn();

// Force despawn immediately
npcEntity.setDespawning(true);
```

---

## Animations

### Playing an Animation

```java
import com.hytale.server.world.entity.animation.AnimationSlot;

NPCEntity.playAnimation(
    ref,                    // Ref — the entity reference
    AnimationSlot.Status,   // AnimationSlot — which slot to play on
    "Wave",                 // String — animation name from the blockymodel
    store                   // EntityStore
);
```

**Animation slots**: `Status` is the most common for one-shot animations. Other slots may include movement, combat, etc.

### Checking Animation State

```java
import com.hytale.server.world.entity.animation.ActiveAnimationComponent;

ActiveAnimationComponent animComp = store.getComponent(
    ref,
    ActiveAnimationComponent.getComponentType()
);
// Inspect current animation state
```

---

## Mount System

```java
import com.hytale.server.npc.mount.MountPlugin;
import com.hytale.server.npc.mount.NPCMountComponent;

// Check if NPC can be dismounted
MountPlugin.checkDismountNpc(npcRef, store);

// Get mount component from an NPC
NPCMountComponent mountComp = store.getComponent(
    ref,
    NPCMountComponent.getComponentType()
);

// Check what a player is riding
int mountEntityId = player.getMountEntityId();
// Returns -1 or 0 if not mounted (check the specific sentinel value)
```

---

## Spawn Suppression

Prevent natural NPC spawning in an area using `SpawnSuppressionComponent`.

```java
import com.hytale.server.world.entity.SpawnSuppressionComponent;

// Attach to an entity (e.g., a marker entity at the center of a protected area)
SpawnSuppressionComponent suppression = store.getComponent(
    ref,
    SpawnSuppressionComponent.getComponentType()
);
suppression.setRadius(50.0f);  // Suppresses spawns within 50 blocks
```

Useful for arenas, boss rooms, safe zones, and custom dungeons where you control all spawning manually.

---

## NPC Spawn Markers (File-Based Pattern)

To prevent double-spawning of persistent NPCs (e.g., town vendors, quest givers), use a file-based marker pattern:

```java
Path markerFile = dataDir.resolve("spawned-npcs").resolve(npcId + ".marker");

// On startup: check if already spawned
if (Files.exists(markerFile)) {
    // NPC was already spawned in a previous session — skip
    return;
}

// Spawn the NPC
Pair<Ref, ?> result = NPCPlugin.get().spawnNPC(store, roleId, null, pos, rot);

// Write marker file
Files.createDirectories(markerFile.getParent());
Files.writeString(markerFile, String.valueOf(System.currentTimeMillis()));
```

This survives server restarts. Delete the marker file to allow re-spawning.

---

## Stale Reference Cleanup

When an NPC despawns while a player is mid-interaction, `StateSupport.interactablePlayers` can retain stale player references. This causes issues on subsequent interactions.

**Workaround** (reflection-based — use as last resort):

```java
// Access the internal interactablePlayers collection via reflection
Field field = StateSupport.class.getDeclaredField("interactablePlayers");
field.setAccessible(true);
Collection<?> players = (Collection<?>) field.get(role.getStateSupport());
players.clear();
```

This is a known engine limitation. Check for stale refs before processing interactions, and clear them when an NPC is about to despawn.

---

## Entity Creation from Scratch

For custom entities that don't use the NPC role system (markers, projectiles, visual effects):

```java
import com.hytale.server.world.entity.EntityStore;
import com.hytale.server.world.entity.Ref;

// Create a new entity holder
Ref ref = EntityStore.REGISTRY.newHolder();

// Attach components as needed
store.addComponent(ref, positionComponentType, positionData);
store.addComponent(ref, networkIdComponentType, networkIdData);
// ... add all required components ...

// Add the entity to the world
store.addEntity(ref);
```

### Common Components for Custom Entities

| Component | Purpose |
|-----------|---------|
| `Nameplate` | Floating name text above the entity |
| `DisplayNameComponent` | The entity's display name (used by Nameplate) |
| `Intangible` | Makes the entity non-collidable (pass-through) |
| `RespondToHit` | Enables hit detection / damage reception |
| `Interactable` | Allows player interaction (E key) |
| `NetworkId` | Required for client-side visibility — every networked entity needs this |

```java
// Example: making an entity interactable but intangible
store.addComponent(ref, Intangible.getComponentType(), new Intangible());
store.addComponent(ref, Interactable.getComponentType(), new Interactable());
store.addComponent(ref, NetworkId.getComponentType(), networkId);

// Example: setting a display name
DisplayNameComponent displayName = store.getComponent(ref, DisplayNameComponent.getComponentType());
displayName.setName("Quest Giver");
```

---

## Quick Reference

| Operation | Code |
|-----------|------|
| Spawn NPC (basic) | `NPCPlugin.get().spawnNPC(store, roleId, null, pos, rot)` |
| Spawn NPC (callbacks) | `NPCPlugin.get().spawnEntity(store, idx, pos, rot, null, pre, post)` |
| Get role index | `NPCPlugin.get().getIndex(roleName)` |
| Register custom action | `NPCPlugin.get().registerCoreComponentType(name, factory)` |
| Get interacting player | `role.getStateSupport().getInteractionIterationTarget()` |
| Play animation | `NPCEntity.playAnimation(ref, AnimationSlot.Status, name, store)` |
| Mark for despawn | `npcEntity.setToDespawn()` |
| Force despawn | `npcEntity.setDespawning(true)` |
| Get NPC type | `npcEntity.getNPCTypeId()` |
| Suppress spawning | `SpawnSuppressionComponent.setRadius(float)` |
| Check mount | `player.getMountEntityId()` |
| Dismount NPC | `MountPlugin.checkDismountNpc(ref, store)` |
| Create entity | `EntityStore.REGISTRY.newHolder()` + components + `store.addEntity(ref)` |
