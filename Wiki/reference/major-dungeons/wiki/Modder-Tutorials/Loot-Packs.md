---
title: "Loot Packs"
order: 8
published: true
draft: false
---

# Loot Packs

A loot pack is an item that, when used, rolls one or more drop lists and gives the resulting items directly to the player's inventory. The pack item is consumed on use. This is useful for reward bags, booster packs, gift boxes, or any item that is meant to be "opened" for randomized loot.

## How It Works

An item uses the `LootPack` interaction on its secondary use. When the player uses the item, every drop list in the `DropLists` array is rolled and the results are delivered to the player. The pack item is consumed. An open sound effect is played, and if any rolled item ID ends with `_Ultra`, a special reveal sound effect plays instead.

## Step 1 - Create the Drop Lists

Create at least one drop list that the loot pack will roll. Create a file at `Server/Drops/MyMod_Drop_MyPack.json`:

```json
{
  "Entries": [
    {
      "ItemId": "MyMod_RewardItem_Sword",
      "Quantity": 1,
      "Weight": 10
    },
    {
      "ItemId": "MyMod_RewardItem_Armor",
      "Quantity": 1,
      "Weight": 10
    },
    {
      "ItemId": "MyMod_RewardItem_Rare",
      "Quantity": 1,
      "Weight": 1
    }
  ]
}
```

One item is chosen from the list according to the weights. Higher weight means more likely. You can list as many entries as you want.

You can also have multiple drop lists in one loot pack. Each list is rolled independently, so the player could receive one item from each list. Major Dungeons' booster pack for example rolls three lists to give three cards at once.

## Step 2 - Create the Loot Pack Item

Create a file at `Server/Item/Items/MyMod_LootPack.json`:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.MyLootPack.name",
    "Description": "MyMod.items.MyLootPack.description"
  },
  "Icon": "Icons/MyLootPackIcon.png",
  "Quality": "Legendary",
  "PlayerAnimationsId": "Item",
  "MaxStack": 1,
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "LootPack",
          "DropLists": [
            "MyMod_Drop_MyPack"
          ]
        }
      ]
    }
  }
}
```

| Field | Description |
|-------|-------------|
| `DropLists` | Array of drop list IDs to roll when the pack is opened. All lists are rolled every time |

To roll multiple drop lists (giving multiple items per open), add more IDs to the `DropLists` array:

```json
"DropLists": [
  "MyMod_Drop_MyPack",
  "MyMod_Drop_MyPack",
  "MyMod_Drop_MyPack_Bonus"
]
```

This example rolls the main pack list twice and also rolls a separate bonus list.

## The Ultra Sound Effect

If any item ID in the rolled results ends with `_Ultra`, the framework automatically plays a special reveal sound effect (`SFX_DevilCard_Reveal_Ultra`) instead of the standard open sound. This is a cosmetic signal to the player that something rare dropped. To take advantage of this, name your rarest items with the `_Ultra` suffix in their item ID. This may be changed in the future to something that doesn't require the item ID to be something specific. Likely, just checking for "Ultra" in the item's tags.

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/Item/Items/MyMod_LootPack.json` | The loot pack item with the LootPack interaction |
| `Server/Drops/MyMod_Drop_MyPack.json` | The drop list rolled when the pack is opened |
