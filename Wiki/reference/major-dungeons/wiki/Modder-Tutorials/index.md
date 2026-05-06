---
title: "Modder Tutorials"
order: 13
published: true
draft: false
---

# Modder Tutorials

This section is for other modders who want to use the Major Dungeons framework in their own asset packs or plugins. Every feature the mod adds is designed to be used by anyone, purely through JSON data files. No Java code is required for any of these features.

## How to Add Major Dungeons as a Dependency

For most features, you'll want to add MajorDungeons as a dependency so that players make sure to install it for the features to work. Some of the features only kick in if MajorDungeons is installed (like Boss Bars), making it optional. In your mod's `manifest.json`, add `MAJOR76:MajorDungeons` to your `Dependencies` or `OptionalDependencies` block:

```json
{
  "Group": "MyGroup",
  "Name": "MyMod",
  "Version": "1.0.0",
  "Dependencies": {
    "MAJOR76:MajorDungeons": "*"
  }
}
```

## What is Covered

Each page in this section walks through one feature from scratch, using the bare minimum JSON needed to get it working:

[//]: # (- [Basic Dungeon Setup]&#40;./basic-dungeon-setup&#41; - instances, portal types, and portal keys)
- [Bosses](./bosses) - boss bars and kill rewards
- [Mimic Blocks](./mimic-blocks) - blocks, like treasure chests, that spawn NPCs when used
- [Summonable Mounts](./summonable-mounts) - items that summon and mount an NPC
- [Locked Doors and Keys](./locked-doors-and-keys) - blocks that require a key item to open
- [Loot Packs](./loot-packs) - items that open and roll drop lists
- [Tabbed Barter Shops](./tabbed-barter-shops) - merchants with multiple tab categories
- [Mutating Barter Shops](./mutating-barter-shops) - merchants with randomly generated trades on server start

[//]: # ([Instance Objectives]&#40;./instance-objectives&#41; - contract-style objectives tied to specific instances)
[//]: # (- [Lore Pages]&#40;./lore-pages&#41; - items that teach readable lore chapters)
