---
title: "Bosses"
order: 3
published: true
draft: false
---

# Bosses

The boss system adds an animated health bar HUD to a specific NPC, tracks all players who dealt damage to it during the fight, and distributes kill rewards to every contributing player when it dies.

## How It Works

When an NPC spawns, the framework checks if there is a boss asset whose ID matches the NPC's role ID. If there is a match, the NPC is flagged as a boss. From that point on the boss bar HUD appears for nearby players, damage contributors are tracked, and rewards are distributed on death.

The boss asset ID must exactly match the NPC role ID. If your NPC role file is named `MyBoss.json`, your boss asset must also be named `MyBoss.json`.

## Step 1 - Create the Boss Asset

Create a file at `Server/NPC/Boss/MyBoss.json`:

```json
{
  "BossBar": {
    "DisplayName": "My Boss",
    "NameColor": "#cc3333",
    "ShowRadius": 30.0,
    "HideRadius": 50.0
  },
  "KillRewardDropLists": [
    "MyMod_Drop_MyBoss_Reward"
  ]
}
```

| Field | Description |
|-------|-------------|
| `BossBar.DisplayName` | Name shown on the health bar HUD |
| `BossBar.NameColor` | Hex color for the name text. Defaults to red `#cc3333` |
| `BossBar.ShowRadius` | Distance in blocks at which the boss bar appears |
| `BossBar.HideRadius` | Distance in blocks at which the boss bar disappears. Should be larger than `ShowRadius` |
| `KillRewardDropLists` | Array of drop list IDs. Each list is rolled and the results are given to every player who dealt damage |

The `KillRewardDropLists` is separate from the NPC's regular `DropList`. The regular drop list spawns items on the ground as usual. The kill reward drop lists give items directly to each contributing player's inventory, no matter where they are in the instance as long as they dealt any damage to the boss.

## Step 2 - Create the NPC Role

The NPC role is a standard Hytale NPC role file. The only requirement is that the filename matches your boss asset filename.

Create a file at `Server/NPC/Roles/MyBoss.json`. Here is a minimal example:

```json
{
  "Type": "Variant",
  "Reference": "Template_Intelligent",
  "Parameters": {
    "WanderRadius": {
      "Value": 2,
      "Description": "Wander radius for idle movement."
    },
    "FollowPatrolPath": {
      "Value": false,
      "Description": "Whether this NPC follows patrol paths."
    },
    "Patrol": {
      "Value": false,
      "Description": "Whether this NPC patrols."
    },
    "ApplySeparation": {
      "Value": false,
      "Description": "Whether this NPC avoids other entities."
    }
  },
  "Modify": {
    "MaxHealth": 2000,
    "MaxSpeed": 8,
    "WakingPeriod": [0, 24],
    "DropList": "MyMod_Drop_MyBoss",
    "Appearance": "MyBossAppearance",
    "Weapons": ["My_Boss_Weapon"],
    "WanderRadius": { "Compute": "WanderRadius" },
    "FollowPatrolPath": { "Compute": "FollowPatrolPath" },
    "Patrol": { "Compute": "Patrol" },
    "ApplySeparation": { "Compute": "ApplySeparation" },
    "IsMemory": false
  }
}
```

The `Appearance` field points to a model definition. The `DropList` here is the standard NPC loot that drops on the ground, not the kill reward. Refer to Hytale's NPC documentation for the full list of NPC role fields.

## Step 3 - Create the Kill Reward Drop List

The kill reward drop list is a standard Hytale drop list asset. Create a file at `Server/Drops/MyMod_Drop_MyBoss_Reward.json`:

```json
{
  "Entries": [
    {
      "ItemId": "MyMod_TreasureBag",
      "Quantity": 1,
      "Weight": 1,
      "Guaranteed": true
    }
  ]
}
```

Every player who dealt damage to the boss receives one roll of this drop list. Setting `Guaranteed` to `true` means the item always drops. You can have multiple entries in the list with different weights to create a loot table with varied outcomes.

## Step 4 - Place the Boss in the Dungeon

Place the NPC in your instance world using a spawner block or a prefab. The boss spawner block item should reference the NPC role ID (`MyBoss`) in its spawner configuration.

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/NPC/Boss/MyBoss.json` | Boss asset, defines the health bar and kill rewards |
| `Server/NPC/Roles/MyBoss.json` | NPC role (must share the same filename) |
| `Server/Drops/MyMod_Drop_MyBoss_Reward.json` | Drop list given to all damage contributors on death |
