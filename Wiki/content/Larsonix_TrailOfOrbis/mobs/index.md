---
name: Mob System Overview
title: Mob System
description: 5-tier classification, 52 stat types, Dirichlet distribution, and dynamic level scaling
author: Larsonix
sort-index: 0
order: 110
published: true
sub-topics:
  - scaling
  - elites-bosses
---

# Mob System

Every mob you fight falls into one of **5 tiers** and gets stats from **52 stat types**, distributed using a Dirichlet algorithm. In the overworld, mob difficulty scales with **distance from spawn** : the farther you go, the stronger mobs get. In Realms, mobs match the map's level. No two encounters feel exactly the same.

| | |
|:--|:--|
| **Classification Tiers** | 5 (Passive → Boss) |
| **Stat Types** | 52 (Dirichlet distribution) |
| **Stat Categories** | 10 (Combat, Movement, Crit, Defense, Sustain, 6x Elemental) |
| **Boss XP Multiplier** | 5.0x |
| **Boss Stat Multiplier** | 3.0x |
| **Elite Stat Multiplier** | 1.5x (2.0x in Realms) |

---

## The 5-Tier Classification

| Tier | XP Mult | Stat Mult | Description |
|:-----|--------:|----------:|:------------|
| **Passive** | 0.1x | 0.1x | Non-combat creatures (critters, ambient wildlife) |
| **Minor** | 0.5x | 1.0x | Weak enemies (larvae, foxes) |
| **Hostile** | 1.0x | 1.0x | Standard combat enemies (zombies, trorks) |
| **Elite** | 1.5x | 1.5x | Mini-bosses (captains, ogres) |
| **Boss** | 5.0x | 3.0x | Major bosses (dragons, yeti) |

> [!NOTE]
> **Passive mobs give 10% XP and have 0.1x stat multiplier.** Farming chickens won't level you up. Fight real enemies.

### Combat Relevance

- **`isCombatRelevant()`** returns `true` for everything except Passive. Minor mobs and above count as combat encounters.
- **`isHostile()`** returns `true` for Minor, Hostile, Elite, and Boss. These mobs actively engage you in combat.

---

## How Mobs Get Classified

Mobs are assigned a classification through a priority system. First match wins :

| Priority | Method | Description |
|----------|--------|-------------|
| 1 | **Role Override** | Explicit config sets classification directly |
| 2 | **Group Patterns** | NPCGroup name matching (e.g., "boss_" prefix) |
| 3 | **Name Patterns** | Role name matching (e.g., "elite_" in name) |
| 4 | **Direct Group** | Membership in a known group |
| 5 | **Attitude Fallback** | HOSTILE/NEUTRAL attitude = Hostile class ; all others = Passive |

Admins can override any mob's classification via config, but most mobs classify automatically based on their Hytale entity data.

---

## How Mob Levels Work

Mobs don't have preset levels. Their level is set dynamically :

| Context | Level Source |
|---------|-------------|
| Overworld | Your level + distance bonus |
| Realm | Map level (all mobs match the map) |

In the overworld, mob difficulty scales with your level and how far you've traveled from spawn. In Realms, all mobs match the map's level.

---

## What Makes Each Mob Unique

Every mob gets stats distributed across **52 stat types** organized into 10 categories. The Dirichlet distribution creates high variance. Each mob specializes heavily in a few stats while being weak in others :

- One Level 50 mob might roll high HP and Armor, becoming a tanky bruiser
- Another Level 50 mob might roll high crit and speed, becoming a fast glass cannon
- A third might roll high elemental damage, becoming a magic caster

### The 10 Stat Categories

| Category | Stat Count | Key Stats |
|----------|-----------|-----------|
| Combat | 4 | Max Health (base 100), Physical Damage (base 10), Armor (base 0), Accuracy (base 100) |
| Movement | 4 | Move Speed, Attack Speed, Attack Range, Attack Cooldown |
| Critical | 2 | Crit Chance (0-75%), Crit Multiplier (100-500%) |
| Defense | 4 | Dodge Chance (0-50%), Block Chance (0-40%), Parry Chance (0-30%), Knockback Resistance (0-100%) |
| Sustain | 2 | Life Steal (0-30%), Health Regen (0-50) |
| Elemental Damage | 6 | Fire, Water, Lightning, Earth, Wind, Void (0-5000) |
| Elemental Resistance | 6 | All elements (0-90%) |
| Elemental Penetration | 6 | All elements (0-75%) |
| AI | 4 | Aggro Range (base 16), Reaction Delay, Charge Time, Charge Distance |
| Special | 2 | Armor Penetration (0-75%), True Damage (0-1000) |

There are also **6 Elemental Increased Damage** stats (0-200%, additive) and **6 Elemental More Damage** stats (0-100%, multiplicative) for a total of **52 stats**.

> [!TIP]
> **Carry diverse defenses.** Because of the Dirichlet variance, you can't rely on one defense type. One mob might deal mostly physical (needs Armor), the next might deal fire (needs [Fire](attributes#fire) Resistance), the next might crit hard (needs raw health to survive spikes).

---

## Mob Speed Effects

Mobs can receive speed modifications through the ailment system :

- **Slows** applied by [Freeze](freeze-water-slow) (Water) and other ailment effects
- **Haste** from some mob modifiers that increase speed
- Speed effects are managed by the `MobSpeedEffectManager`

The `NO_REGENERATION` realm modifier disables mob health regeneration entirely, making sustained fights more manageable.

---

> [!IMPORTANT]
> **Elite mobs are stronger in Realms.** Realm Elites have 2.0x stats and 3.0x XP (vs 1.5x/1.5x in the overworld). Realms are designed to be harder and more rewarding. See [Overworld vs Realms](overworld-vs-realms) for the full comparison.

---

## Further Reading

- **[Scaling](mob-stat-scaling)** Dirichlet distribution details, all 52 stat types, level scaling, distance bonus
- **[Elites & Bosses](elites-bosses)** What makes Elite and Boss mobs different, XP multipliers, stat multipliers
