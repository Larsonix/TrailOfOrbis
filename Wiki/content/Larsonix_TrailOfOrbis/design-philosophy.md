---
name: Design Philosophy
title: Design Philosophy
description: Why Trail of Orbis works the way it does - the principles behind every system
author: Larsonix
sort-index: 910
order: 3
published: true
---

# Design Philosophy

Trail of Orbis is built on deliberate design principles. Understanding them helps you understand why systems work the way they do.

---

## No Classes, Just Choices

There's no class selection screen. Your build emerges from everything you do : the Gear Modifiers you roll and sculpt with Stones, the Skill Tree nodes you allocate, the weapon type you choose, and the Attribute points you spread across 6 Elements. Each system shapes your character differently, and they all interact.

The 6 Elements (Fire, Water, Lightning, Earth, Wind, Void) are one layer - each granting 5 stats with zero overlap. But your gear defines your power just as much : a crit-focused weapon with attack speed modifiers plays completely differently from a life-steal weapon with defensive suffixes, even with the same Attribute distribution. The Skill Tree adds another axis with 485 nodes across 15 regions, including build-defining keystones. And Stones let you sculpt gear toward your vision.

No two builds need to be alike, because identity comes from the *combination* of all these systems, not any single one.

---

## Risk and Reward Everywhere

Every system has a meaningful tradeoff :

| System | Risk | Reward |
|--------|------|--------|
| **[Fire](attributes#fire) Attribute** | No defensive stats | Highest damage output |
| **[Void](attributes#void) Attribute** | Must deal damage to survive | Life steal + True Damage |
| **Harder Realms** | Stronger mobs, difficulty Modifiers | More XP, better loot |
| **Map Modifiers** | 7 prefix modifiers increase difficulty | 6 suffix modifiers increase rewards |
| **Death** | 50% progress XP lost (Level 21+) | Teaches you what to improve |

No decision is free. Better loot requires harder fights. Stronger offense requires weaker defense. Perfect Gear requires gambling with Stones. Every decision stays meaningful from Level 1 to Level 1,000,000.

---

## No Hard Walls

Trail of Orbis has **soft gates**, not hard walls :

- There's no content you're literally prevented from attempting. Just content that will kill you if you're not ready
- Respec is free, always. You're never locked into a build
- Attribute and Skill Points are earned every level, no exceptions
- No level gates, no gear score requirements. You decide when you're ready

---

## Infinite Progression

The level cap is 1,000,000. Not a joke. The effort-based leveling formula and content scaling are designed for indefinite play :

- Early game : craft starter gear, kill first mobs, enter first Realms
- Mid game : push farther in the overworld, run harder maps, build your character
- Late game : optimize Gear with Stones, stack map modifiers, push larger Realms
- Deep game : hunt [Mythic](gear-rarities#mythic) and [Unique](gear-rarities#unique) drops, perfect builds, push limits

Realms are the core loop from almost level 1, not a late unlock. There's always something to do and always room to grow.

---

## Build Diversity

The math is designed to prevent "one best build" :

- 30 unique stats across 6 Elements (no overlaps)
- 485 Skill Tree nodes across 15 regions with 12 bridge paths connecting elements
- 101 Gear Modifier definitions across Prefixes and Suffixes
- 25 crafting Stones across 7 categories
- 4 Ailments : [Burn](burn-fire-dot), [Freeze](freeze-water-slow), [Shock](shock-lightning-damage-amp), [Poison](poison-void-stacking-dot)
- 13 Realm Modifiers creating different challenges for different builds

A build that trivializes damage-heavy Realms might struggle with no-regeneration Modifiers. A build that excels in high-difficulty content might be overkill for efficient low-level farming. Context matters, and different builds excel in different contexts.

---

## Damage Pipeline Transparency

Combat isn't a black box. The damage pipeline processes every hit through clearly defined stages :

> **Base → Flat → Elemental → Conversion → % Physical → % Elemental → % More → Conditionals → Crit → Defenses → True Damage**

Every stage is inspectable. `/too combat detail` shows the full breakdown per hit. The [Damage Pipeline](the-11-step-damage-pipeline) page documents every stage. If you understand the pipeline, you can optimize your build far beyond what intuition suggests.

Base crit chance is 5% with a 150% base crit multiplier. These are starting values. Attributes, gear, and skill nodes modify them.

---

## Inspired By

Trail of Orbis draws inspiration from :

- **Path of Exile** Damage pipeline, evasion formula, Ailment mechanics, Skill Tree structure, currency crafting system
- **Hytale's combat** Attack animations, weapon categories, the feel of melee and ranged combat
- **Hytale's lore** The 6 Elements ([Fire](attributes#fire), [Water](attributes#water), [Lightning](attributes#lightning), [Earth](attributes#earth), [Wind](attributes#wind), [Void](attributes#void)), Gaia vs Varyn, the Alterverse

The goal : PoE's depth in Hytale's world.
