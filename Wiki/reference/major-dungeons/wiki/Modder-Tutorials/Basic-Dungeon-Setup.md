---
title: "Basic Dungeon Setup"
order: 2
published: false
draft: true
---

# Basic Dungeon Setup

This page walks through creating an instanced dungeon that players access through an Ancient Gateway using a portal key item. The result is a self-contained dungeon world with a timed entry and a dedicated portal key that the player uses to open a portal.

## How It Works

The flow from the player's perspective is:

1. The player crafts or obtains a portal key item
2. The player uses the portal key on an Ancient Gateway block
3. A portal opens and the player steps through
4. The player is teleported into a fresh copy of the dungeon instance
5. When the player exits (or the time limit runs out), they are returned to where they came from

On the data side, three things connect to make this work: an **instance template**, a **portal type**, and a **portal key item**.

## Step 1 - Build the Dungeon

The dungeon itself is built in-game using Hytale's prefab and instance tools. Here is the general process:

1. Create a prefab editing world with `/editprefab new MyDungeon`
2. Build your dungeon layout in that world
3. Use the selection brush to select the area, then `/prefab save` to save it
4. Use `/editprefab exit` to leave the editing world

The saved prefab can be placed in your dungeon instance world using the Paste brush. Once the dungeon world looks the way you want, it needs to be saved as an instance template.

An instance template lives at:

```
Server/Instances/MyDungeon/instance.bson
```

The `instance.bson` file is a binary world snapshot created and managed by the server. You create it by building your dungeon world in-game and using the instance system to export it. The folder name (`MyDungeon` in this example) becomes the instance ID that everything else references.

## Step 2 - Create the Portal Type

A portal type tells the game which instance to load when a portal key is used, along with the display information shown on the portal splash screen.

Create a file at `Server/PortalTypes/MyPortalType.json`:

```json
{
  "InstanceId": "MyDungeon",
  "GameplayConfig": "Spawn_In_Dungeon",
  "Description": {
    "DisplayName": "MyMod.portals.MyDungeon.name",
    "FlavorText": "MyMod.portals.MyDungeon.description",
    "ThemeColor": "#3355cc",
    "SplashImage": "MyDungeon.png",
    "Tips": [
      "MyMod.portals.MyDungeon.tip1"
    ]
  }
}
```

| Field | Description |
|-------|-------------|
| `InstanceId` | Must match the folder name inside `Server/Instances/` exactly |
| `GameplayConfig` | Set to `Spawn_In_Dungeon` for dungeon-style instances |
| `DisplayName` | Translation key for the dungeon name shown on the portal screen |
| `FlavorText` | Translation key for the short description shown on the portal screen |
| `ThemeColor` | Hex color used for the portal splash theme |
| `SplashImage` | Image filename shown on the portal splash screen |
| `Tips` | Array of translation keys for tips shown to the player before entering |

The `SplashImage` file should be placed in your asset pack's image assets folder and referenced by filename only.

If you do not have translations set up yet, you can put plain strings directly into `DisplayName` and `FlavorText` for testing.

## Step 3 - Create the Portal Key Item

The portal key is an item the player holds and uses on an Ancient Gateway block. It references the portal type from Step 2 via the `PortalKey.PortalType` field.

Create a file at `Server/Item/Items/Portal/MyPortalKey.json`:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.MyPortalKey.name",
    "Description": "MyMod.items.MyPortalKey.description"
  },
  "Icon": "Icons/MyPortalKeyIcon.png",
  "PortalKey": {
    "PortalType": "MyPortalType",
    "TimeLimitSeconds": 1800
  },
  "PlayerAnimationsId": "Item",
  "Tags": {
    "Type": [
      "Portal",
      "Temporary"
    ]
  },
  "Quality": "Uncommon",
  "MaxStack": 1
}
```

| Field | Description |
|-------|-------------|
| `PortalKey.PortalType` | Must match the filename of your portal type JSON (without `.json`) |
| `PortalKey.TimeLimitSeconds` | How long the player has in the dungeon before being forced out. `1800` is 30 minutes |
| `Tags.Type` | `Portal` marks this as a portal key. `Temporary` means the item is consumed on use |

The `Temporary` tag causes the portal key to be consumed when the portal is opened. If you want the key to be reusable, remove `Temporary` from the tags.

## Step 4 - Give Players the Key

The portal key can be handed out however your mod sees fit: as a crafting recipe, a drop from an NPC, a reward from a quest, a shop purchase, or given directly by a command. There is nothing special about how it is distributed, it is just an item.

To add a crafting recipe, add a `Recipe` block to the portal key item:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.MyPortalKey.name",
    "Description": "MyMod.items.MyPortalKey.description"
  },
  "Icon": "Icons/MyPortalKeyIcon.png",
  "PortalKey": {
    "PortalType": "MyPortalType",
    "TimeLimitSeconds": 1800
  },
  "Recipe": {
    "TimeSeconds": 5,
    "KnowledgeRequired": false,
    "Input": [
      {
        "ItemId": "Ingredient_Bar_Iron",
        "Quantity": 10
      }
    ],
    "BenchRequirement": [
      {
        "Id": "Arcanebench",
        "Type": "Crafting",
        "Categories": [
          "Arcane_Portals"
        ]
      }
    ]
  },
  "PlayerAnimationsId": "Item",
  "Tags": {
    "Type": [
      "Portal",
      "Temporary"
    ]
  },
  "Quality": "Uncommon",
  "MaxStack": 1
}
```

This example makes the portal key craftable at an Arcanist's Workbench using 10 iron bars.

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/Instances/MyDungeon/instance.bson` | The dungeon world snapshot (created in-game) |
| `Server/PortalTypes/MyPortalType.json` | Links the portal key to the instance and provides display info |
| `Server/Item/Items/Portal/MyPortalKey.json` | The item the player uses to open a portal |
