# Hytale Objectives Skill

Use this skill when working with quest/objective systems in Hytale plugins. This covers the `ObjectivePlugin` (core objective engine) and `NPCObjectivesPlugin` (NPC-specific task extensions) APIs for managing objectives, objective lines, tasks, completions, co-op participation, and data persistence.

> **Related skills:** For events and ECS event systems, see `docs/hytale-api/events.md`. For NPC behavior templates that trigger objectives, see `docs/hytale-api/npc-templates.md`. For shop integration with objectives, see `docs/hytale-api/shop.md`. For reputation rewards, see `docs/hytale-api/reputation.md`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Start an objective for a player | `ObjectivePlugin.get().startObjective(objectiveId, playerUUIDs, worldUUID, markerUUID, store)` |
| Start an objective line (chain) | `ObjectivePlugin.get().startObjectiveLine(store, lineId, playerUUIDs, worldUUID, markerUUID)` |
| Cancel an objective | `ObjectivePlugin.get().cancelObjective(objectiveUUID, store)` |
| Check if player can do an objective | `ObjectivePlugin.get().canPlayerDoObjective(player, objectiveAssetId)` |
| Check if player can do an objective line | `ObjectivePlugin.get().canPlayerDoObjectiveLine(player, objectiveLineId)` |
| Add a player to co-op objective | `ObjectivePlugin.get().addPlayerToExistingObjective(store, playerUUID, objectiveUUID)` |
| Remove a player from co-op objective | `ObjectivePlugin.get().removePlayerFromExistingObjective(store, playerUUID, objectiveUUID)` |
| Mark objective complete (internal) | `ObjectivePlugin.get().objectiveCompleted(objective, store)` |
| Register a custom task type | `ObjectivePlugin.get().registerTask(id, assetClass, assetCodec, implClass, implCodec, generator)` |
| Register a custom completion type | `ObjectivePlugin.get().registerCompletion(id, assetClass, codec, generator)` |
| Check NPC task progress | `NPCObjectivesPlugin.hasTask(playerUUID, npcId, taskId)` |
| Update NPC task completion | `NPCObjectivesPlugin.updateTaskCompletion(store, ref, playerRef, npcId, taskId)` |
| Start objective from NPC | `NPCObjectivesPlugin.startObjective(playerRef, taskId, store)` |
| Debug dump all objectives | `ObjectivePlugin.get().getObjectiveDataDump()` |

---

## Architecture Overview

The objective system is split across two plugins:

| Plugin | Package | Purpose |
|--------|---------|---------|
| `ObjectivePlugin` | `com.hypixel.hytale.builtin.adventure.objectives` | Core engine: objectives, lines, tasks, completions, persistence, history |
| `NPCObjectivesPlugin` | `com.hypixel.hytale.builtin.adventure.npcobjectives` | NPC extensions: kill tasks, bounties, NPC builder actions |

### Data Model

```
ObjectiveLine (chain of objectives)
  └─ Objective (single quest)
       ├─ TaskSet[] (groups of tasks, completed sequentially)
       │    └─ ObjectiveTask[] (individual goals within a set)
       └─ ObjectiveCompletion[] (rewards/actions on completion)
```

- **ObjectiveLine** — An ordered sequence of `ObjectiveAsset` IDs. Completing one objective automatically starts the next. Lines can chain to other lines via `NextObjectiveLineIds`.
- **Objective** — A runtime instance created from an `ObjectiveAsset`. Tracks player participants, task progress, and world/marker references.
- **ObjectiveTask** — An individual goal (gather items, kill mobs, reach location, etc.). Tasks within a TaskSet must all be completed before the next TaskSet begins.
- **ObjectiveCompletion** — An action that runs when all tasks are done (give items, clear objective items, etc.).

---

## Key Classes

### Imports (Core)

```java
// Plugin entry points
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.builtin.adventure.npcobjectives.NPCObjectivesPlugin;

// Runtime objective
import com.hypixel.hytale.builtin.adventure.objectives.Objective;

// Asset definitions
import com.hypixel.hytale.builtin.adventure.objectives.config.ObjectiveAsset;
import com.hypixel.hytale.builtin.adventure.objectives.config.ObjectiveLineAsset;

// Tasks and completions
import com.hypixel.hytale.builtin.adventure.objectives.task.ObjectiveTask;
import com.hypixel.hytale.builtin.adventure.objectives.config.task.ObjectiveTaskAsset;
import com.hypixel.hytale.builtin.adventure.objectives.completion.ObjectiveCompletion;

// Player history
import com.hypixel.hytale.builtin.adventure.objectives.components.ObjectiveHistoryComponent;

// Interactions
import com.hypixel.hytale.builtin.adventure.objectives.interactions.StartObjectiveInteraction;

// ECS fundamentals
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
```

---

## Task Types

### Core Tasks (registered by ObjectivePlugin)

| Type ID | Asset Class | Implementation | Description |
|---------|-------------|----------------|-------------|
| `Craft` | `CraftObjectiveTaskAsset` | `CraftObjectiveTask` | Craft specific items |
| `Gather` | `GatherObjectiveTaskAsset` | `GatherObjectiveTask` | Collect items into inventory |
| `UseBlock` | `UseBlockObjectiveTaskAsset` | `UseBlockObjectiveTask` | Interact with a specific block |
| `UseEntity` | `UseEntityObjectiveTaskAsset` | `UseEntityObjectiveTask` | Interact with a specific entity |
| `TreasureMap` | `TreasureMapObjectiveTaskAsset` | `TreasureMapObjectiveTask` | Find a treasure location |
| `ReachLocation` | `ReachLocationTaskAsset` | `ReachLocationTask` | Travel to a specific area |

### NPC Extension Tasks (registered by NPCObjectivesPlugin)

| Type ID | Asset Class | Implementation | Description |
|---------|-------------|----------------|-------------|
| `KillNPC` | `KillObjectiveTaskAsset` | `KillNPCObjectiveTask` | Kill specific NPCs |
| `KillSpawnBeacon` | `KillSpawnBeaconObjectiveTaskAsset` | `KillSpawnBeaconObjectiveTask` | Destroy a spawn beacon |
| `KillSpawnMarker` | `KillSpawnMarkerObjectiveTaskAsset` | `KillSpawnMarkerObjectiveTask` | Clear a spawn marker |
| `Bounty` | `BountyObjectiveTaskAsset` | `BountyObjectiveTask` | Bounty hunt target |

---

## Completion Types

| Type ID | Asset Class | Implementation | Description |
|---------|-------------|----------------|-------------|
| `GiveItems` | `GiveItemsCompletionAsset` | `GiveItemsCompletion` | Award items to participants |
| `ClearObjectiveItems` | `ClearObjectiveItemsCompletionAsset` | `ClearObjectiveItemsCompletion` | Remove objective-tagged items from inventory |

---

## ObjectivePlugin API

Access via `ObjectivePlugin.get()`.

### Starting Objectives

| Method | Returns | Description |
|--------|---------|-------------|
| `startObjective(String objectiveId, Set<UUID> playerUUIDs, UUID worldUUID, UUID markerUUID, Store)` | `Objective` or `null` | Start an objective for one or more players |
| `startObjectiveLine(Store, String lineId, Set<UUID> playerUUIDs, UUID worldUUID, UUID markerUUID)` | `Objective` or `null` | Start the first objective in a line |

**Parameters:**
- `objectiveId` — The `ObjectiveAsset` ID (JSON filename)
- `playerUUIDs` — Set of player UUIDs to participate (co-op support)
- `worldUUID` — The world where the objective takes place
- `markerUUID` — Optional UUID of the triggering marker entity (can be `null` for player-initiated objectives)
- `store` — The `Store<EntityStore>` for the world

**Returns `null` when:**
- The objective asset ID doesn't exist
- `markerUUID` is null and the objective isn't valid for players
- The objective fails to set up or register in the data store
- All players are already doing this objective/line

### Checking Eligibility

| Method | Returns | Description |
|--------|---------|-------------|
| `canPlayerDoObjective(Player, String objectiveAssetId)` | `boolean` | `false` if player already has an active objective with this asset ID |
| `canPlayerDoObjectiveLine(Player, String objectiveLineId)` | `boolean` | `false` if player already has an active objective in this line |

### Cancelling Objectives

| Method | Returns | Description |
|--------|---------|-------------|
| `cancelObjective(UUID objectiveUUID, Store)` | `void` | Cancel and clean up an objective. Untracks for all players, removes from data store and disk. |

### Co-Op Management

| Method | Returns | Description |
|--------|---------|-------------|
| `addPlayerToExistingObjective(Store, UUID playerUUID, UUID objectiveUUID)` | `void` | Add a player to an in-progress objective |
| `removePlayerFromExistingObjective(Store, UUID playerUUID, UUID objectiveUUID)` | `void` | Remove a player. If no active players remain, the objective is saved to disk and unloaded. |

### Completion Flow

| Method | Returns | Description |
|--------|---------|-------------|
| `objectiveCompleted(Objective, Store)` | `void` | Handle completion: untrack from players, run completions, advance line |

**Completion flow:**
1. Untracks the objective for all players
2. Removes from data store and disk
3. If the objective is part of a line:
   - Stores objective history data
   - Starts the next objective in the line (if any)
   - If the line is finished, starts any `NextObjectiveLineIds`
4. If standalone: stores objective history data

### Client Sync

| Method | Returns | Description |
|--------|---------|-------------|
| `untrackObjectiveForPlayer(Objective, UUID playerUUID)` | `void` | Remove objective from player's active list and send `UntrackObjective` packet |

### Debug

| Method | Returns | Description |
|--------|---------|-------------|
| `getObjectiveDataDump()` | `String` | Formatted dump of all active objectives with IDs, UUIDs, and player lists |

### Registering Custom Types

| Method | Description |
|--------|-------------|
| `registerTask(String id, Class<T> assetClass, Codec<T> assetCodec, Class<U> implClass, Codec<U> implCodec, TriFunction<T, Integer, Integer, U> generator)` | Register a new objective task type |
| `registerCompletion(String id, Class<T> assetClass, Codec<T> codec, Function<T, U> generator)` | Register a new completion type |

---

## NPCObjectivesPlugin API

Access via `NPCObjectivesPlugin.get()`. Extends the objective system with NPC-specific features.

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `hasTask(UUID playerUUID, UUID npcId, String taskId)` | `boolean` | Check if a player has an active task involving this NPC |
| `updateTaskCompletion(Store, Ref, PlayerRef, UUID npcId, String taskId)` | `String` or `null` | Increment task progress by 1. Returns animation ID to play, or `null` if no task matched. |
| `startObjective(Ref playerReference, String taskId, Store)` | `void` | Start an objective from an NPC interaction context |

### NPC Builder Actions

`NPCObjectivesPlugin` registers three NPC builder action types for use in NPC templates:

| Action Type | Builder Class | Description |
|-------------|---------------|-------------|
| `CompleteTask` | `BuilderActionCompleteTask` | NPC action to complete a task step |
| `StartObjective` | `BuilderActionStartObjective` | NPC action to start an objective |
| `HasTask` | `BuilderSensorHasTask` | NPC sensor to check if player has a task |

---

## Objective Lifecycle

```
1. startObjective() / startObjectiveLine()
     ↓
2. Objective.setup(store)  →  Creates tasks from asset TaskSets
     ↓
3. Tasks run (gather, kill, craft, etc.)
     ↓  Player completes all tasks in current TaskSet
4. Objective.checkTaskSetCompletion()
     ↓  All TaskSets complete
5. objectiveCompleted(objective, store)
     ↓
6a. If part of a line:
      → Start next objective in line
      → Or if line done: start NextObjectiveLineIds (if any)
6b. If standalone:
      → Store history data per-player
```

### Player Disconnect Handling

When a player disconnects:
1. `ObjectivePlugin` iterates their `activeObjectiveUUIDs`
2. Removes them from each objective's active player set
3. If no active players remain, the objective is saved to disk and unloaded from memory
4. When the player reconnects and tasks resume, objectives are reloaded from disk

---

## Data Persistence

### Auto-Save

Objectives are automatically saved to disk every **5 minutes** via a scheduled executor. They are also saved on server shutdown.

### Data Store

The `ObjectiveDataStore` uses a `DataStoreProvider` (default: `DiskDataStoreProvider` writing to `objectives/` in the plugin data directory).

Configuration in the plugin JSON:
```json
{
  "DataStore": {
    "Type": "Disk",
    "Path": "objectives"
  }
}
```

### Objective History

Completed objectives are stored per-player via `ObjectiveHistoryComponent` (an ECS component). History tracks:
- Which objectives have been completed
- Which objective lines have been completed
- Reward data (items received)
- Completion count (for repeatable objectives)

Access history:

```java
ObjectiveHistoryComponent history = store.getComponent(
    playerRef, ObjectivePlugin.get().getObjectiveHistoryComponentType());
Map<String, ObjectiveHistoryData> completed = history.getObjectiveHistoryMap();
Map<String, ObjectiveLineHistoryData> completedLines = history.getObjectiveLineHistoryMap();
```

---

## Asset Paths

| Asset Type | Path | Description |
|------------|------|-------------|
| `ObjectiveAsset` | `Objective/Objectives/` | Individual objective definitions |
| `ObjectiveLineAsset` | `Objective/ObjectiveLines/` | Ordered chains of objectives |
| `ObjectiveLocationMarkerAsset` | `Objective/ObjectiveLocationMarkers/` | Visual markers in the world |
| `ReachLocationMarkerAsset` | `Objective/ReachLocationMarkers/` | Location trigger areas |

---

## ObjectiveLineAsset JSON

```json
{
  "Category": "MainQuest",
  "ObjectiveIds": ["quest_01_gather", "quest_02_deliver", "quest_03_fight"],
  "TitleId": "My Quest Line",
  "DescriptionId": "A thrilling adventure.",
  "NextObjectiveLineIds": ["sequel_questline"]
}
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `Category` | `String` | Quest category (for filtering/display) |
| `ObjectiveIds` | `String[]` | Ordered list of objective asset IDs (non-empty, unique) |
| `TitleId` | `String` | Localization key (auto-generated: `objectivelines.{id}.title`) |
| `DescriptionId` | `String` | Localization key (auto-generated: `objectivelines.{id}.desc`) |
| `NextObjectiveLineIds` | `String[]` | Lines to start after this line completes |

**Note:** `TitleId` and `DescriptionId` values in JSON are placeholders. After decoding, they are overwritten with `objectivelines.{assetId}.title` and `objectivelines.{assetId}.desc` formats. Add actual translations to your `.lang` file.

---

## StartObjectiveInteraction

A built-in `Interaction` that starts an objective when triggered (e.g., from picking up an item or interacting with an entity).

### JSON Usage

```json
{
  "Type": "StartObjective",
  "ObjectiveId": "quest_gather_iron"
}
```

### Metadata Integration

Items with an `OBJECTIVE_UUID` metadata key are tracked. If a player drops an objective item and the objective has `RemoveOnItemDrop: true`, the player is automatically removed from the objective.

---

## Code Examples

### Starting a Quest Line from an RPG NPC

```java
public void startQuestForPlayer(PlayerRef playerRef, Store<EntityStore> store) {
    UUID playerUUID = playerRef.getUuid();
    World world = store.getExternalData().getWorld();

    Objective objective = ObjectivePlugin.get().startObjectiveLine(
        store,
        "rpg_main_questline",
        Set.of(playerUUID),
        world.getWorldConfig().getUuid(),
        null  // no marker
    );

    if (objective != null) {
        playerRef.sendMessage(Message.raw("Quest started!"));
    } else {
        playerRef.sendMessage(Message.raw("You're already on this quest."));
    }
}
```

### Registering a Custom Task Type

```java
@Override
protected void setup() {
    // Register custom "CollectRPGGear" task type
    ObjectivePlugin.get().registerTask(
        "CollectRPGGear",
        CollectRPGGearTaskAsset.class,
        CollectRPGGearTaskAsset.CODEC,
        CollectRPGGearTask.class,
        CollectRPGGearTask.CODEC,
        CollectRPGGearTask::new
    );
}
```

### Registering a Custom Completion That Grants XP

```java
@Override
protected void setup() {
    ObjectivePlugin.get().registerCompletion(
        "GrantRPGXP",
        GrantXPCompletionAsset.class,
        GrantXPCompletionAsset.CODEC,
        asset -> new GrantXPCompletion(asset)
    );
}
```

### Checking if a Player Has Completed a Prerequisite

```java
public boolean hasCompletedPrerequisite(Store<EntityStore> store, Ref<EntityStore> playerRef, String questLineId) {
    ObjectiveHistoryComponent history = store.getComponent(
        playerRef, ObjectivePlugin.get().getObjectiveHistoryComponentType());
    if (history == null) return false;

    return history.getObjectiveLineHistoryMap().containsKey(questLineId);
}
```

---

## Best Practices

1. **Check eligibility first**: Always call `canPlayerDoObjective()` or `canPlayerDoObjectiveLine()` before starting, to avoid the "already doing" error message.

2. **Co-op objectives use UUID sets**: Pass multiple player UUIDs to `startObjective()` for cooperative quests. All participants share task progress.

3. **Marker-based vs player-based**: If `markerUUID` is `null`, the objective must have `isValidForPlayer() == true`. Marker-based objectives are tied to a specific world entity (e.g., a quest giver NPC or location marker).

4. **Disk cleanup**: Completed objectives are removed from disk. Cancelled objectives are also cleaned up. The 5-minute auto-save prevents data loss for in-progress objectives.

5. **Player disconnect safety**: The system gracefully handles disconnects by saving and unloading objectives with no active players. Objectives resume when players reconnect.

6. **Objective lines chain automatically**: When the last objective in a line completes, `NextObjectiveLineIds` are started automatically. Design quest chains by linking lines together.

7. **Custom task types**: Use `registerTask()` in your plugin's `setup()` to add RPG-specific task types (e.g., "reach level 10", "allocate 5 skill points", "complete a realm").

8. **Item drop tracking**: Objectives with `RemoveOnItemDrop: true` listen for `InventoryChangeEvent` (ECS event) and auto-remove players who drop the objective item. (**Updated 2026-03-26**: Renamed from `LivingEntityInventoryChangeEvent`; now requires `EntityEventSystem` registration.)

---

## Integration Notes (TrailOfOrbis)

Potential uses for TrailOfOrbis:

- **Realm objectives**: Create objective lines for realm challenges (kill waves, find boss, escape timer)
- **Skill tree prerequisites**: Gate skill nodes behind `ObjectiveHistoryComponent` checks (completed a specific quest)
- **Daily quests**: Programmatically start objectives from a daily rotation system
- **Custom kill tasks**: Register an RPG "KillScaledMob" task type that counts RPG-classified mobs
- **XP completion rewards**: Register a `"GrantRPGXP"` completion type for quest XP rewards
- **Co-op realm parties**: Use multi-player objective UUIDs to share realm objective progress
