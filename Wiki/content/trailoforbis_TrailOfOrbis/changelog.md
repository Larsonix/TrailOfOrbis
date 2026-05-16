---
name: Changelog
title: Changelog
description: Version history for Trail of Orbis
author: Larsonix
sort-index: 999
order: 230
published: true
---

# Changelog

## 1.0.10

### Attack Speed
- **Full Rework.** Combo system — attacks speed up the longer you fight. Hit 1 is normal, hit 2 is faster, hit 3 is peak
- **Animation Matches Server.** The "swing is done but damage hasn't landed" feel is gone
- **6 New Speed Stats.** Cooldown recovery, combo speed, cast speed, projectile speed
- **Hexcode Speed Buffs.** Hex mods can now push temporary speed overrides (haste, slow)

### Weapons
- **Two-Handed Weapons Got a Buff.** All modifier values on two-handed weapons are now 50% stronger than one-handed
- **Mace is Two-Handed Now.** Gets the full 1.5x treatment

### Portals
- **Full Visual Overhaul.** Biome-colored particles, rarity glow, animated biome preview through the portal frame
- **Rarity Particle Aura.** Higher rarity maps glow more
- **Stuck Gateways Fixed.** Realms that were activated but never entered auto-close after 120s
- **Gateways No Longer Spawn in Instances.** Forgotten Temple was generating gateways inside realm worlds

### Compass
- **Height Indicators.** Mob markers show up/down arrows when mobs are above or below you
- **Distance Scaling.** Markers get smaller and fade out the further away the mob is

### Loot
- **Rarity Drop Particles.** Now show properly
- **Stone Pickup Notifications.** Picking up a stone shows a rarity-colored toast like gear does
- **Loot Filter UI Reworked.** The modifier conditions are fully interactive now — category tabs, stat pickers, value ranges, all in the UI
- **Chests Don't Reroll on Restart.** Server restart was re-generating chest contents from vanilla drop tables

### Stones
- **Fortune's Compass Bulk Apply.** Sneak-click to apply all your compasses to a map at once
- **All Changes Show in Toast.** Stone modification notification now shows every stat line
- **Alterverse Shard Crash Fixed.** Redistributing between prefix and suffix could exceed the rarity's max modifier count
- **Escape Closes the Picker.** Stone picker and skill node detail both respond to Escape properly

### Combat
- **Thorns Kills Count.** Thorns damage killing a mob now gives XP, drops loot, counts for realm progress
- **Tamed Horses Survive World Transitions.** Mounts were dying immediately after teleporting
- **Red Screen Toned Down.** DOT damage no longer triggers the red flash

### Hexcode Compatibility
- **Glyph HUD Works now!**

### UI & HUDs
- **Skill Point HUD Survives the Sanctum.** Entering the skill sanctum was destroying the skill point HUD
- **Signature Ability HUD Restored.** The weapon ability indicator was missing for RPG weapons
- **Chat Item Links.** Shift-clicking items into chat shows rarity-colored item names

### Stability
- **No More Stutter on Mob Kills.** Loot generation was blocking the main thread — full async pipeline now
- **Container First-Drag Fixed.** Opening a container for the first time and dragging an item could freeze
- **Memory Bloat Fixed.** Item registry was loading all items into memory at boot and never cleaning up
- **Multi-World Damage Freeze Fixed.** A race condition between realm worlds could permanently disable damage processing
- **Cross-World Combat Crash Fixed.** Players getting kicked during combat in a different world could crash the server

## 1.0.9

### Skill Tree
- **Branches Got a Second Pass.** All 6 main branches, 8 octant branches, and 12 bridge chains reworked again with proper cluster themes
- **New Octant Keystones.** Soul Siphon, Colossus, Storm Runner, Momentum, plus retuned drawbacks across the board
- **Bridge Payoffs Buffed.** Cross-element investments were too weak, all 12 synergies and Void-adjacent nodes brought up

### Combat
- **Crit Chance Goes Past 100% Now.** 150% crit chance = guaranteed crit + 50% chance for a double crit. Stack that crit
- **Critical Damage Reduction.** New defensive stat, only comes from gear and skill tree bonuses
- **Resistance Penetration Reworked.** Pen applies before the 75% cap and can push mobs into negative resistance, so your penetration stats actually do something now
- **Dodge and Evasion are Separate.** Two distinct stats, displayed independently everywhere
- **Mobs Can Actually Block Now.** Mobs blocking was just for show before, now it reduces the damage they take
- **AoE Spells No Longer Freeze the Server.** Hex AoE or projectiles hitting a large amount of targets would freeze the server
- **DOT Damage Fixed (Again).** Burn and poison DOTs from hex constructs could crash the server or get silently blocked. Both fixed
- **Flat Elemental Damage Fixed.** Was getting lost in the pipeline, correctly applies now
- **Thorns Work.** Thorns damage and health recovery apply on all healing paths now
- **Combat Feedback Rewritten.** "physDmg%" is now "Physical Damage", armor math is correct, ailment thresholds show roll-vs-threshold, crit colors make sense
- **Offhand Stats Work With Non-RPG Items.** Holding a torch, food, or blocks was suppressing your offhand gear stats entirely
- **Armor and Accuracy Percent Bonuses Apply.** Were just not being applied in the stat pipeline, fixed

### Mobs
- **Monster Modifiers.** Some mobs now spawn with special abilities, stuff like Pack Leader, Blazing Trail, Rallying Cry, Venomous Cloud
- **Modified Mobs Drop Better Loot.** Each modifier increases the mob's item quantity and rarity
- **Late-Game Mobs Hit Harder.** Mobs past level 120 scale armor faster
- **Elites Give More XP.** 2x multiplier instead of 1.5x

### Leveling
- **You Lose XP When You Die.** Can't drop a level though
- **XP Curve Adjusted.** Plus a realm XP exploit has been fixed
- **Party XP Notifications Work.** You see what your party earns now and the party HUD shows the correct level
- **Your Level is Protected.** If the XP curve changes in an update, your level won't drop

### Loot
- **Potions, Food, Maps, and Stones Drop in Map Chests.** Not just gear anymore
- **Victory Chest Stones Scale With Level.** 1 stone from level 1-9, +1 per 10 levels
- **Victory Chest Maps are Always Identified.** No mystery maps from your rewards
- **Loot Chests Disappear on Map End.** Win or lose
- **Loot Filter Supports Maps.** Filter realm maps by biome, size, and modifiers
- **No "Unique" Drops anymore** It's not an implemented mechanic yet
- **Chests Don't Empty on Reopen.** If you opened a chest but didn't take anything, it would be empty next time. Fixed

### Stones
- **2 New Uncommon Stones.** Alterverse Splinter (rerolls suffixes) and Alterverse Fragment (rerolls prefixes)
- **Alterverse Shard Shuffles Types.** Rerolls now randomly redistribute between prefixes and suffixes instead of keeping the old split
- **Bulk Identify.** Sneak-click a Lorekeeper's Scroll to identify everything at once
- **Stone Rerolls Can't Give Wrong Mods.** Rerolling armor could give you weapon modifiers, uses the correct pool now

### Weapons
- **Weapon Tracking Completely Rewritten.** The game could lose track of which weapon you were actually holding, will not happen anymore
- **Switching Weapons Always Updates Your Stats Now.** Even swapping between two Iron Swords with different elements properly recalculates everything like it should

### Hexcode Compatibility
- **Hex Spells Scale With Your RPG Stats.** Your casting power, magic damage, and elemental bonuses feed directly into hex spell damage now
- **Hexcode v0.7.0 Support.** Spell echo actually works now, plus updated for the latest Hexcode - No imbuement support yet
- **Per-Glyph Damage Tuning.** Each hex glyph (bolt, combust, gust...) has its own damage multiplier so they can be balanced individually in the settings
- **Mana Cost Reduction Works on Hex Spells.** Your mana cost reduction applies to Hexcode now
- **Damage Glyphs Scale Mana Cost Now.**
- **Glaciate, ensnare etc... Damage Fixed.**
- **Glaciate Construct Damage Fixed Too.** The ice construct was doing about 10x damage
- **Spellbook Implicits are Diverse Now.** Spellbooks randomly roll one of three implicit types now : mana regen, volatility, or magic power
- **Magic Weapons Roll Hex Stats.** Staves, wands, and spellbooks can now roll volatility, magic power, draw accuracy, and cast speed as modifiers
- **Hex Damage Can't Be Abused Anymore.** Two-layer volatility and mana cost scaling : go big or go home
- **Zero Reflection.** The entire Hexcode compatibility layer was rewritten to direct imports

### RPG Items
- **Hexcode Staff Stats Don't Double-Stack Anymore.** Staff JSON stats were stacking on top of RPG-computed stats, the same bug vanilla weapons had. Now properly suppressed too
- **Crafting Preview Works Everywhere.** Workbenches, pocket crafting, inventory, all of them
- **Elemental Resistances Roll on All Armor.** Were cloth-only, every material can roll them now
- **Unique Rarity Removed From Drops.** Uniques are hand-placed, they won't roll from loot
- **Transmog Skin List Fixed.** Wrong skins at the workbench, was using vanilla quality instead of RPG rarity -> still not fully working

### Balance
- **Armor and Evasion Scaling Rebalanced.** Gear implicits and formulas adjusted, you have more defenses at same level
- **Movement Speed Caps.** Diminishing returns on movement speed so you can't stack infinite speed, tunable in the settings

### UI & HUDs
- **Stats Page Shows Way More Stats.** Keystones, magic, cast speed, physical resistance, all that good stuff
- **HUDs Actually Stay This Time.** Yeah, 1.0.7 said "forever". Different problem, was caused by PartyPro, cost 4 days of debugging, properly fixed now
- **Energy Shield Absorbs Damage Again.** Was not initialized, just sitting there doing nothing sometimes
- **Signature Ability HUD Restored.** The weapon ability indicator was missing for RPG weapons
- **Death Recap Works.** Was completely broken, initialization bug
- **Crafting Preview Works From Inventory.** Only worked on placed crafting benches before
- **Stat Tooltips Fixed.** Armor and accuracy breakdowns show the right numbers now

### Realms
- **Portal Management.** You can close portals in the UI now and can't infinitely reopen them anymore
- **Jungle Map Added.** New realm map visual - still problem with map models being inverted
- **Realm Crash Recovery.** If the server crashes while you're in a realm, it remembers where you were and your loot
- **Realm Modifier Names Rewritten.** 14 vague names replaced with clear ones, "Superior Gear" is now "Gear Quality" and so on
- **Realm Modifiers Softened.** Lower base values, gentler scaling, max caps on map modifiers
- **Completion at 90%.** Down from 95%, you don't need to hunt the last mob anymore
- **No Suffocation on Realm Spawn.** Could spawn inside blocks, now you're teleported to a safe place everytime
- **Swamp Biome Temporarily Disabled.** Was crashing remote players, will be back

### Stability
- **Several Server Crashes Fixed.** Container interactions, portal UI, stone UI, ECS timing issues
- **Memory Leaks Fixed.** Playing for a long time no longer eats all the server memory
- **Client Connection Hang Fixed.** Mob modifier system could freeze your connection on join, fixed
- **Way Less Stutter.** Item sync reworked with batching, loot drops and gear switches don't hitch anymore
- **Skill Sanctum Safe for Visitors.** Teleporting to a friend in the sanctum was dropping you into the void
- **Config Updates Automatically.** When you update the mod, new config values are added without losing your edits

## 1.0.7

### Skill Tree
- **Complete Skill Tree Rework.** All 485 nodes redesigned from scratch, proper power budgets, tiered progression, the whole thing
- **12 Cross-Element Bridge Payoffs.** Invest in two elements and you get a unique hybrid effect at the intersection
- **Stat Names are Readable now.** "Phys DMG" is "Physical Damage", "Light" is "Lightning", full words everywhere. No more guessing what a stat doess

### Mobs
- **Caster Mobs were dealing 10-14x more damage than other mobs** Almost too easy
- **Elemental Damage is Caster-Only now.** Every other mob type was getting it too on specific maps
- **Mob Elemental Penetration reduced ~20x.** Level 100 fire mage had 40% fire pen, now it's 3%, better balance to come later

### Loot
- **Loot Selection Rebuilt.** Some loot Categories (Like an Energy Shield based armor) were unobtainable because the loot system was based on skins and not implicits categories.
- **Chest Loot now scales to Zone Level, not Player Level.** Well, you'll probably hate me for this, no more chests full of mythic items
- **Chest Loot is now Per-Player.** Before this, only the first player to open a chest got RPG loot, I don't want you guys to fight over loot. (PvP to come)
- **Realm Modifiers apply to Chest Loot.** IIR, Quality Bonus, Drop Level Bonus - all that good stuff
- **Chest Compatibility with L4E.** Chests could get permanently stuck with L4E installed, fixed

### RPG Items
- **Shields now properly craft as RPG Gear.** Timed-craft shields were stuck in an infinite conversion loop and never actually became RPG items. They now get block_chance, modifiers, and proper tooltips like everything else
- **Crafting Preview was still not working** For real real real this time
- **Crafted Weapons can Roll Elemental now.** Same 30% chance as mob drops and chest loot, a crafted sword can be a fire sword now
- **Gear Requirements Aligned with Attributes.** Spell/mana/ES require Water, health regen requires Earth, ailment thresholds match their element... ~48 modifiers fixed

### UI & HUDs
- **XP Gains are Toast Notifications now.** No more chat spam every time you kill something
- **HUDs no longer disappear after Realm Transitions.** XP bar, energy shield, and combat HUD all survive world transitions reliably now, forever
- **Combat HUD properly disappears on Realm Victory/Defeat.**

### Damage Pipeline
- **Elemental Weapons Actually Work Now.** Physical weapons (swords, axes, bows...) can now roll elemental damage.
- **Melee % and Damage % Scale Elemental Melee Weapons.**
- **Ailments Trigger on Elemental Weapons**
- **Ailment DOTs Deal Real Damage now.** They were doing basically nothing, now they scale off the actual hit
- **DOT Combat Text Fixed.** No more "0" damage spam every tick, DOTs accumulate properly and always show at least "1"
- **Cross-World DOT Crash Fixed.** DOT killing a mob after you left that world could crash the server

### Magic & Hexcode
- **Staves Show Their Element Without Hexcode.** Staves were always saying "Spell Damage" even without Hexcode installed. Now they properly show "Fire Damage", "Lightning Damage", etc...
- **Legacy Spell Damage Staves Migrated.** Old staves with the legacy "spell_damage" type are automatically converted to an actual element on login, preserving roll quality

### Skill Sanctum
- **Sanctum Nodes no longer glitch when scrolling your Hotbar.** Well still not perfect, but better

### Realms
- **Glowing Ground Cover in every Realm.** All biomes now have light-emitting vegetation on the ground. You can actually see now, insane
- **Swamp doesn't crash/black screen you anymore** (I hope...)

## 1.0.0 - ModJam Release

The initial public release of Trail of Orbis, featuring :

- **6 Elemental Attributes** [Fire](attributes#fire), [Water](attributes#water), [Lightning](attributes#lightning), [Earth](attributes#earth), [Wind](attributes#wind), [Void](attributes#void) with 30 unique stats
- **11-Step Damage Pipeline** Full PoE-inspired damage calculation
- **4 Ailments** [Burn](burn), [Freeze](freeze), [Shock](shock), [Poison](poison) with HP-proportional scaling
- **7 Gear Rarities** [Common](gear-rarities#common) through [Unique](gear-rarities#unique) with 101 Modifier definitions
- **Quality System** 1-101 Quality multiplier on all Gear
- **25 Consumable Stones** Reroll, enhance, remove, lock, and corrupt Gear
- **485-Node Skill Tree** 6 elemental arms, 8 octant hybrid arms, 12 bridge paths, and keystones
- **Skill Sanctum** 3D instance world for Skill Tree visualization
- **14 Realm Biomes** Procedural dungeons from Forest to Corrupted (13 combat + 1 utility)
- **4 Realm Sizes** Small to Massive with scaling mob counts and rewards
- **13 Map Modifiers** 7 difficulty Prefixes + 6 reward Suffixes
- **5-Tier Mob Classification** Passive through Boss with dynamic Elite spawns
- **Dirichlet Stat Distribution** Each mob has a unique stat profile
- **Death Recap** Full damage breakdown on death
- **Loot Filter** Configurable item filtering by Rarity, type, Quality
- **3 Skill Gems** Fireball, Cleave, Chain (more to come)
- **Full UI System** Stats, Attributes, and Skill Sanctum with cross-page navigation
- **90 Commands** Player and admin tools for every system
