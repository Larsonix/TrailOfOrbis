---
title: "Summonable Mounts"
order: 5
published: true
draft: false
---

# Summoned Mounts

The summoned mount system lets you create items that summon an NPC and mount the player on it. Using the item again while already mounted dismounts the player and despawns the NPC. If the mount or player takes damage, the player is automatically dismounted.

## How It Works

An item uses the `SpawnAndMount` interaction type on its secondary use. When triggered, the framework checks whether the player is already mounted. If not, it validates there is enough clear space to spawn the mount, spawns the NPC, and mounts the player on it. You configure where the NPC spawns relative to the player, where the player sits on the NPC, and which movement config to apply while riding.

Mounts are disabled by default inside instance worlds whose names match `*MJ_Instance*`. This blacklist is configurable in the plugin's `MajorDungeons.json` config file.

## Step 1 - Create the Mount Item

Create a file at `Server/Item/Items/Mounts/MyMountToken.json`:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.MyMountToken.name",
    "Description": "MyMod.items.MyMountToken.description"
  },
  "Icon": "Icons/MyMountIcon.png",
  "Quality": "Uncommon",
  "PlayerAnimationsId": "Item",
  "MaxStack": 1,
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SpawnAndMount",
          "EntityId": "MyMod_Mount_Wolf",
          "SpawnOffset": {
            "X": 0,
            "Y": 0.2,
            "Z": 0
          },
          "Anchor": {
            "X": 0.0,
            "Y": 1.4,
            "Z": 0.0
          },
          "MovementConfigId": "MD_Mount_Uncommon"
        }
      ],
      "Cooldown": {
        "Id": "MyMod_Mount",
        "Cooldown": 2
      }
    }
  }
}
```

| Field | Description |
|-------|-------------|
| `EntityId` | The NPC role ID to spawn as the mount |
| `SpawnOffset` | Where the mount spawns relative to the player. `Y: 0.2` lifts it slightly off the ground |
| `Anchor` | Where on the mount's body the player sits. `Y: 1.4` puts the player on the mount's back |
| `MovementConfigId` | Movement configuration applied to the player while riding. See movement configs below |
| `Cooldown.Id` | A shared cooldown group ID. All mounts should share one ID so you cannot summon two at once |
| `Cooldown.Cooldown` | Cooldown duration in seconds after dismounting before the item can be used again |

### Movement Configs

Major Dungeons includes four movement configs you can use directly:

| ID | Speed |
|----|-------|
| `MD_Mount_Uncommon` | Slow |
| `MD_Mount_Rare` | Medium |
| `MD_Mount_Epic` | Fast |
| `MD_Mount_Legendary` | Very fast |

## Step 2 - Create the Mount NPC Role

The mount NPC role is a standard NPC role file. It needs to be non-hostile and non-wandering. A minimal example at `Server/NPC/Roles/Mounts/MyMod_Mount_Wolf.json`:

```json
{
  "Type": "Variant",
  "Reference": "Template_Mount",
  "Parameters": {
    "WanderRadius": {
      "Value": 0,
      "Description": "No wandering while waiting to be mounted."
    }
  },
  "Modify": {
    "MaxHealth": 100,
    "Appearance": "MD_Mount_Wolf_Black",
    "WakingPeriod": [0, 24],
    "WanderRadius": { "Compute": "WanderRadius" },
    "IsMemory": false
  }
}
```

The `Appearance` field points to a model definition. You can reuse any of the mount model definitions already included with Major Dungeons (such as `MD_Mount_Wolf_Black`) or define your own. NPC mount behavior is handled by the `Template_Mount` reference template.

## How the Toggle Works

The first time the player uses the item, the mount NPC is spawned and the player is mounted. Using the item a second time while already on the mount despawns the NPC and dismounts the player. The framework tracks whether the player is currently on a summoned mount and handles the toggle automatically.

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/Item/Items/Mounts/MyMountToken.json` | The item that summons and dismisses the mount |
| `Server/NPC/Roles/Mounts/MyMod_Mount_Wolf.json` | The NPC role used as the mount |
