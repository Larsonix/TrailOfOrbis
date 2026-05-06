---
title: "Lore Pages"
order: 6
published: false
draft: true
---

# Lore Pages

The lore system lets you create readable lore entries that players discover by using physical items. Once a player discovers a lore chapter, it is permanently saved to their character and readable at any time through the Lore UI. Each lore entry belongs to a series and has a chapter number, so you can create multi-part stories spread across several items.

## How It Works

An item uses the `DiscoverLore` interaction on its secondary use. When the player uses the item for the first time, the chapter is saved to their character data and a success message is shown. The item is then consumed. Using the same item again (a duplicate) when the chapter is already known does nothing and the item is not consumed.

The Lore UI is accessible through the game's journal and shows all discovered series and chapters grouped together in a sidebar.

## Step 1 - Create the Lore Item

Create a file at `Server/Item/Items/Lore/MyMod_Lore_Chapter1.json`:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.Lore_MyStory_I.name"
  },
  "Icon": "Icons/ItemsGenerated/Recipe_Plant_Seeds_Health1.png",
  "Quality": "Rare",
  "PlayerAnimationsId": "Item",
  "Consumable": true,
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "DiscoverLore",
          "SeriesId": "my_story",
          "ChapterNumber": 1,
          "SeriesDisplayNameKey": "MyMod.lore.series.my_story",
          "ChapterTitleKey": "MyMod.lore.my_story.1.title",
          "ContentKey": "MyMod.lore.my_story.1.content",
          "Next": {
            "Type": "ModifyInventory",
            "AdjustHeldItemQuantity": -1
          }
        }
      ]
    }
  },
  "Tags": {
    "Type": ["Lore"],
    "Family": ["Knowledge"]
  },
  "MaxStack": 1
}
```

| Field | Description |
|-------|-------------|
| `SeriesId` | A unique identifier for this lore series. Used to group chapters together in the Lore UI. Use lowercase with underscores |
| `ChapterNumber` | The chapter number within the series. Chapters are sorted by this number in the sidebar |
| `SeriesDisplayNameKey` | Translation key for the series name shown as a header in the sidebar |
| `ChapterTitleKey` | Translation key for this chapter's title |
| `ContentKey` | Translation key for the full body text of this chapter |
| `Next` | The interaction that runs after the lore is discovered. The `ModifyInventory` block here consumes the item |

The `Next` block with `AdjustHeldItemQuantity: -1` consumes the item on first discovery. If you want to keep the item and allow the player to re-read it (without opening the Lore UI), remove the `Next` block, though the Lore UI is the intended reading location.

## Step 2 - Add Translation Entries

Add your translations to your mod's translation files. The keys used in the item file correspond to entries in your translation JSON:

```json
{
  "MyMod.items.Lore_MyStory_I.name": "The Tale of the Ruins, Part I",
  "MyMod.lore.series.my_story": "The Tale of the Ruins",
  "MyMod.lore.my_story.1.title": "Chapter I: The Beginning",
  "MyMod.lore.my_story.1.content": "Long ago, before the ruins were ruins, there stood a great city..."
}
```

The `ContentKey` text is shown as a paragraph in the Lore UI's content panel. You can include newlines in the translation string for paragraph breaks.

## Step 3 - Create Additional Chapters

For each additional chapter in the same series, create a new item file with the same `SeriesId` and an incremented `ChapterNumber`. The framework automatically groups all chapters that share a `SeriesId` together in the Lore UI sidebar.

For chapter 2:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.Lore_MyStory_II.name"
  },
  "Icon": "Icons/ItemsGenerated/Recipe_Plant_Seeds_Health1.png",
  "Quality": "Rare",
  "PlayerAnimationsId": "Item",
  "Consumable": true,
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "DiscoverLore",
          "SeriesId": "my_story",
          "ChapterNumber": 2,
          "SeriesDisplayNameKey": "MyMod.lore.series.my_story",
          "ChapterTitleKey": "MyMod.lore.my_story.2.title",
          "ContentKey": "MyMod.lore.my_story.2.content",
          "Next": {
            "Type": "ModifyInventory",
            "AdjustHeldItemQuantity": -1
          }
        }
      ]
    }
  },
  "Tags": {
    "Type": ["Lore"],
    "Family": ["Knowledge"]
  },
  "MaxStack": 1
}
```

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/Item/Items/Lore/MyMod_Lore_Chapter1.json` | The item that teaches lore chapter 1 |
| `Server/Item/Items/Lore/MyMod_Lore_Chapter2.json` | The item that teaches lore chapter 2 |
| Translation file entries | The series name, chapter titles, and full text content |
