---
title: "Instance Objectives"
order: 9
published: false
draft: true
---

# Instance Objectives

Instance objectives (sometimes called "Devil's Deals") are contract-style objectives that activate automatically when a player enters an instance carrying a specific item. Each objective gives the player a task to complete inside the instance and a reward when they leave voluntarily after completing it.

## How It Works

When a player enters an instance, the framework checks their inventory for any items tagged with `InstanceObjectiveItem`. For each such item found, it looks up the matching objective pool for the current instance and randomly assigns an objective. The player completes tasks inside the dungeon (such as killing enemies or looting chests) and then leaves. If they leave voluntarily, the reward is given. If the instance timer forces them out, the objective is cancelled with no reward.

## The Three Files Involved

You need three things:

1. An **item** that carries the `InstanceObjectiveItem` tag and is in the player's inventory when they enter
2. An **objective pool** that maps your item ID to a set of weighted objectives for a specific instance pattern
3. One or more **objective** files that define the tasks and rewards

## Step 1 - Create the Objective Item

This is the item the player brings into the dungeon to trigger the objective. Create a file at `Server/Item/Items/MyMod_ObjectiveToken.json`:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.MyObjectiveToken.name",
    "Description": "MyMod.items.MyObjectiveToken.description"
  },
  "Icon": "Icons/MyObjectiveTokenIcon.png",
  "Quality": "Common",
  "PlayerAnimationsId": "Item",
  "MaxStack": 1,
  "Tags": {
    "Type": ["InstanceObjectiveItem"]
  }
}
```

The `InstanceObjectiveItem` tag is what triggers the framework to check for an objective pool when the player enters an instance. The item ID (the filename without `.json`) is used as the key in the objective pool to look up which objectives can be assigned.

## Step 2 - Create an Objective

Each objective defines what tasks the player must complete and what reward they receive. Objectives are stored as Hytale `Objective` assets at `Server/Objective/Objectives/`.

Create a file at `Server/Objective/Objectives/MyMod_Objective_KillEnemies.json`:

```json
{
  "Category": "InstanceObjective",
  "TitleId": "MyMod.objectives.MyObjective.title",
  "TaskSets": [
    {
      "Tasks": [
        {
          "Type": "KillNPC",
          "DescriptionId": "MyMod.objectives.KillEnemies.task",
          "Count": 10,
          "NPCGroupId": "MyMod_EnemyGroup"
        }
      ]
    },
    {
      "Tasks": [
        {
          "Type": "LeaveInstance",
          "DescriptionId": "MyMod.objectives.LeaveInstance.task"
        }
      ]
    }
  ],
  "Completions": [
    {
      "Type": "GiveItemsSafely",
      "DropList": "MyMod_Drop_ObjectiveReward"
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `Category` | Must be `InstanceObjective` for this system to manage it |
| `TitleId` | Translation key for the objective title shown in the UI |
| `TaskSets` | Ordered list of task groups. Each group completes before the next begins |
| `Type: KillNPC` | Task requiring the player to kill a number of NPCs from a specific group |
| `Count` | How many NPCs must be killed |
| `NPCGroupId` | The NPC attitude group that counts toward this task |
| `Type: LeaveInstance` | Final task. Completes when the player exits voluntarily. Always include this as the last task set |
| `Completions` | What happens when the objective finishes. `GiveItemsSafely` gives items to the player's inventory |
| `DropList` | The drop list rolled to determine the reward items |

### Supported Task Types

The framework supports these task types inside `InstanceObjective` objectives:

- `KillNPC` - kill a set number of NPCs from a specific group
- `UseBlockOnce` - use a specific block type (such as opening chests). Uses `BlockSet` and `Count` instead of `NPCGroupId`
- `LeaveInstance` - exit the instance. Always the final task set

A `UseBlockOnce` task example:

```json
{
  "Type": "UseBlockOnce",
  "DescriptionId": "MyMod.objectives.LootChests.task",
  "BlockSet": "Chest",
  "Count": 5
}
```

## Step 3 - Create the Objective Pool

The objective pool maps item IDs to weighted lists of objectives for a specific instance (or pattern of instances). Create a file at `Server/Objective/InstanceObjectivePools/MyMod_MyDungeon.json`:

```json
{
  "InstanceIdPattern": "MyDungeon",
  "ItemIdObjectivePools": {
    "MyMod_ObjectiveToken": [
      {
        "ObjectiveId": "MyMod_Objective_KillEnemies",
        "Weight": 15
      },
      {
        "ObjectiveId": "MyMod_Objective_LootChests",
        "Weight": 10
      }
    ]
  }
}
```

| Field | Description |
|-------|-------------|
| `InstanceIdPattern` | The instance ID or a glob pattern using `*` as a wildcard. `"MyDungeon"` matches exactly. `"MyDungeon*"` matches any instance whose ID starts with `MyDungeon` |
| `ItemIdObjectivePools` | Map of item ID to a weighted list of objectives. The item ID must match the filename of the objective item (without `.json`) |
| `ObjectiveId` | Must match the filename of the objective JSON (without `.json`) |
| `Weight` | Relative probability of this objective being chosen. Higher weight means more likely |

When a player enters the instance carrying `MyMod_ObjectiveToken`, the framework picks one objective from this list at random using the configured weights.

### Using a Global Supplement

You can also create an `All_Dungeons.json` pool with `"InstanceIdPattern": "*"` that applies to every instance. The framework merges global pool objectives with the dungeon-specific pool, so you can have objectives that work in any dungeon alongside objectives specific to one dungeon.

## Step 4 - Create the Reward Drop List

Create a file at `Server/Drops/MyMod_Drop_ObjectiveReward.json`:

```json
{
  "Entries": [
    {
      "ItemId": "MyMod_RewardItem",
      "Quantity": 1,
      "Weight": 1,
      "Guaranteed": true
    }
  ]
}
```

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/Item/Items/MyMod_ObjectiveToken.json` | The item the player brings into the dungeon |
| `Server/Objective/Objectives/MyMod_Objective_KillEnemies.json` | Defines the tasks and reward |
| `Server/Objective/InstanceObjectivePools/MyMod_MyDungeon.json` | Maps the item and instance to a set of possible objectives |
| `Server/Drops/MyMod_Drop_ObjectiveReward.json` | The reward given on voluntary exit after completing tasks |
