---
title: "Mimic Blocks"
order: 4
published: true
draft: false
---

# Mimic Chests

The mimic system lets you place blocks like treasure chests that turn into an NPC when interacted with. When a player tries to open the chest, the framework suppresses the normal open interaction, removes the block, and spawns the configured NPC in its place. When the mimic is killed, it drops the NPC's normal loot as well as the configured loot.

## How It Works

A block is flagged as a mimic using the `MimicBlock` component in a block spawner entry. When the player interacts with that block, the mimic NPC is spawned at the block's position facing inward and the block is removed. The NPC carries a loot configuration that is rolled on death.

## Step 1 - Create the Block Spawner Entry

Block spawner entries are JSON files that define what block is placed when a spawner block is used. A mimic block uses the `MimicBlock` component to mark it.

Create a file at `Server/Item/Block/Spawners/MyMimicChest.json`:

```json
{
  "Entries": [
    {
      "Name": "Furniture_Human_Ruins_Chest_Small",
      "Weight": 100,
      "Components": {
        "Components": {
          "MimicBlock": {
            "EntityId": "Mimic_Common",
            "Droplists": [
              "MyMod_Drop_MimicLoot"
            ]
          }
        }
      }
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `Name` | The block type ID to use for the chest. This is what the block looks like before the mimic is revealed |
| `EntityId` | The NPC role ID of the mimic that spawns when the player opens the chest |
| `Droplists` | Array of drop list IDs rolled when the mimic is killed |

The `EntityId` can be `Mimic_Common` to use the mimic NPC already defined by Major Dungeons, or you can define your own NPC role for a custom mimic variant.

## Step 2 - Create the Loot Drop List

Create a file at `Server/Drops/MyMod_Drop_MimicLoot.json`:

```json
{
  "Entries": [
    {
      "ItemId": "MyMod_SomeTreasureItem",
      "Quantity": 1,
      "Weight": 50
    },
    {
      "ItemId": "Ingredient_Bar_Iron",
      "Quantity": 3,
      "Weight": 50
    }
  ]
}
```

The loot is spawned as item drops on the ground at the mimic's position when it dies. You can add as many entries as you want with different weights to control the probability of each item.

## Step 3 - Place the Mimic in Your Dungeon

Place the mimic spawner block in your dungeon instance world. The spawner block itself is invisible in the final result. Players see a normal chest and only discover the mimic when they try to open it.

You can place multiple different mimic chest spawners across your dungeon, each with a different loot drop list to create variety.

## Using the Built-in Mimic NPC

If you use `"EntityId": "Mimic_Common"`, the spawned NPC uses the mimic model and behavior already defined by Major Dungeons. No extra setup is needed on your end for that. If you want a custom mimic (different model, health, or combat behavior), you would need to define your own NPC role in a plugin, since NPC roles with custom appearance require Java code or a full model asset.

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/Item/Block/Spawners/MyMimicChest.json` | Defines the chest block with the MimicBlock component |
| `Server/Drops/MyMod_Drop_MimicLoot.json` | Loot that drops when the mimic is killed |
