---
title: "Locked Doors and Keys"
order: 7
published: true
draft: false
---

# Locked Doors and Keys

The unlock system lets you create block items (doors, chests, gates, etc.) that can only be opened with a specific key item. When a player without the correct key tries to use the block, they see a notification showing what key is required and hear a locked sound. When a player with the correct key uses the block, the key is consumed, the block transforms into its unlocked version, and the unlocked block's own interaction chain fires automatically.

## How It Works

A locked block uses the `Unlock` interaction type on its `Use` interaction chain. The interaction checks the player's held item against the configured `KeyItemId`. If it matches, the block is replaced with the unlocked block type after a short delay. If it does not match, the player gets a notification.

## Step 1 - Create the Key Item

The key item is a regular item. The only requirement is that it has an `ItemId` that you will reference in the locked block. Create a file at `Server/Item/Items/Keys/MyMod_Key.json`:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.MyKey.name",
    "Description": "MyMod.items.MyKey.description"
  },
  "Icon": "Icons/MyKeyIcon.png",
  "Quality": "Uncommon",
  "PlayerAnimationsId": "Item",
  "Tags": {
    "Type": ["Ephemeral"]
  },
  "MaxStack": 1
}
```

The `Ephemeral` tag marks the item as a dungeon-only item that is removed from the player's inventory when they leave an instance. Remove this tag if you want the key to be kept after leaving.

## Step 2 - Create the Unlocked Block

The unlocked block is the version the door becomes after the key is used. It can have its own interactions (such as opening like a normal door or revealing a chest). Create a file at `Server/Item/Items/Doors/MyMod_Door.json`:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.MyDoor.name"
  },
  "Quality": "Technical",
  "BlockType": {
    "DrawType": "Model",
    "Material": "Solid",
    "Opacity": "Transparent",
    "CustomModel": "Blocks/Decorative_Sets/MyDoorOpen.blockymodel"
  }
}
```

This is the block that replaces the locked version once the key is used. The `BlockType` section defines how it renders. If you want the door to do something when interacted with after unlocking (like opening an animation), add an `Interactions.Use` block here.

## Step 3 - Create the Locked Block

The locked block is what players see and interact with before using a key. Create a file at `Server/Item/Items/Doors/MyMod_Door_Locked.json`:

```json
{
  "TranslationProperties": {
    "Name": "MyMod.items.MyDoorLocked.name"
  },
  "Quality": "Technical",
  "BlockType": {
    "DrawType": "Model",
    "Material": "Solid",
    "Opacity": "Transparent",
    "CustomModel": "Blocks/Decorative_Sets/MyDoorClosed.blockymodel",
    "Interactions": {
      "Use": {
        "Interactions": [
          {
            "Type": "Unlock",
            "KeyItemId": "MyMod_Key",
            "UnlockedBlockId": "MyMod_Door",
            "UnlockSfxId": "SFX_DoorUnlock_Standard",
            "UnlockDelayInSeconds": 1.4
          }
        ]
      }
    }
  }
}
```

| Field | Description |
|-------|-------------|
| `KeyItemId` | The item ID of the key required to unlock. Must match the filename of your key item (without `.json`) |
| `UnlockedBlockId` | The block item ID to replace this block with on successful unlock. Must match the filename of your unlocked block |
| `UnlockSfxId` | Sound event played on successful unlock. `SFX_DoorUnlock_Standard` and `SFX_DoorUnlock_Boss` are included with Major Dungeons |
| `UnlockDelayInSeconds` | How many seconds to wait before swapping the block. This gives the unlock animation time to play |

When the player uses this block without the key, they see a notification saying "Block Locked" and the name of the required key. When they use it with the correct key in hand, the key is consumed, the sound plays, and after the configured delay the block is replaced.

## Using Multiple Lock Types

You can have many different locked blocks in the same dungeon, each requiring a different key. Just create a separate key item and a separate locked block for each one. The `KeyItemId` in each locked block points to its specific key.

A common dungeon pattern is to have the boss drop a key that unlocks the exit door to the treasure room. The boss drop list gives the key directly to all contributing players, and the exit door checks for that specific key.

## Summary of Files

| File | Purpose |
|------|---------|
| `Server/Item/Items/Keys/MyMod_Key.json` | The key item the player must hold to unlock the door |
| `Server/Item/Items/Doors/MyMod_Door_Locked.json` | The locked block with the Unlock interaction |
| `Server/Item/Items/Doors/MyMod_Door.json` | The unlocked block that replaces the locked one |
