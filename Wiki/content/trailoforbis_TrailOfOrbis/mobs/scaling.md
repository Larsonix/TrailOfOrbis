---
name: Scaling
title: Mob Stat Scaling
description: Dirichlet distribution, all 52 stat types, level scaling, distance bonus, and stat generation
author: Larsonix
sort-index: 0
order: 111
published: true
---

# Mob Stat Scaling

Each mob rolls 52 stats via Dirichlet distribution, creating emergent specialization : one mob becomes a tanky bruiser, the next a glass cannon, the next an elemental caster. Carry diverse defenses because you can't predict what you'll face.

No two mobs of the same level fight identically.

---

## Level Scaling

Mob levels are set dynamically based on context :

| Context | How Level Is Set |
|---------|-----------------|
| Overworld | **Distance from spawn** (0.5 bonus stat pool per block from origin) |
| Realm | All mobs match the map's level |

The overworld acts as your base. Mobs near spawn are weak. The farther you go from the world origin (0,0), the stronger mobs become at a rate of 0.5 bonus stat pool per block. This means pushing outward requires real investment in your build. In Realms, mob levels are fixed to the map's level, making difficulty predictable.

---

## Stat Generation : The Dirichlet Distribution

Each mob's stats are generated using a **Dirichlet distribution** for balanced but varied allocation :

- Each of the 52 stat types has an **alpha weight** (ranging from 0.1 to 1.0) that controls how likely it is to receive a large share of the pool
- The distribution naturally creates **specialization**. Most stats get small values while a few get large ones
- Every stat is clamped to its defined min/max range after generation

### What This Creates

The Dirichlet distribution with its alpha weights gives each mob a distinct combat personality :

| Mob Profile | What Happened |
|-------------|---------------|
| Tanky bruiser | High rolls on Max Health, Armor, Knockback Resistance |
| Glass cannon | High rolls on Physical Damage, Crit Chance, Crit Multiplier |
| Elemental caster | High rolls on one or more Elemental Damage types |
| Evasive fighter | High rolls on Dodge Chance, Move Speed, Attack Speed |
| Sustain tank | High rolls on Life Steal, Health Regen, Max Health |

> [!NOTE]
> The `MobStatProfile` provides a base template per classification tier. The Dirichlet distribution is applied on top of these templates, so higher-tier mobs (Elite, Boss) start with a stronger baseline before variance kicks in.

---

## Classification Stat Multipliers

Each tier applies a stat multiplier that scales the mob's overall power :

| Tier | Stat Multiplier | Effect |
|:-----|----------------:|:-------|
| Passive | 0.1x | Minimal combat stats |
| Minor | 1.0x | Standard stats |
| Hostile | 1.0x | Standard stats |
| Elite | 1.5x | 50% stronger |
| Boss | 3.0x | Triple stats |

---

## All 52 Stat Types

### Combat (4 stats)

| Stat | Base Value | Notes |
|:-----|-----------:|:------|
| Max Health | 100 | Total hit points |
| Physical Damage | 10 | Melee/ranged physical output |
| Armor | 0 | Physical damage reduction |
| Accuracy | 100 | Hit chance vs player evasion |

### Movement (4 stats)

| Stat | Base Value | Notes |
|:-----|-----------:|:------|
| Move Speed | 1.0 | Movement velocity multiplier |
| Attack Speed | 1.0 | Attack frequency multiplier |
| Attack Range | 2.0 | Melee reach in blocks |
| Attack Cooldown | 1.0 | Seconds between attacks |

### Critical (2 stats)

| Stat | Base Value | Range | Notes |
|:-----|-----------:|:------|:------|
| Critical Chance | 5 | 0-75% | Chance to land a critical hit |
| Critical Multiplier | 150 | 100-500% | Damage multiplier on crit |

### Defense (4 stats)

| Stat | Range | Notes |
|:-----|:------|:------|
| Dodge Chance | 0-50% | Chance to completely avoid an attack |
| Block Chance | 0-40% | Chance to block incoming damage |
| Parry Chance | 0-30% | Chance to parry and counter |
| Knockback Resistance | 0-100% | Resistance to displacement effects |

### Sustain (2 stats)

| Stat | Range | Notes |
|:-----|:------|:------|
| Life Steal | 0-30% | Percentage of damage healed |
| Health Regen | 0-50 | HP regenerated per tick |

### Elemental Damage (6 stats)

| Stat | Range |
|:-----|:------|
| Fire Damage | 0-5000 |
| Water Damage | 0-5000 |
| Lightning Damage | 0-5000 |
| Earth Damage | 0-5000 |
| Wind Damage | 0-5000 |
| Void Damage | 0-5000 |

### Elemental Resistance (6 stats)

| Stat | Range |
|:-----|:------|
| Fire Resistance | 0-90% |
| Water Resistance | 0-90% |
| Lightning Resistance | 0-90% |
| Earth Resistance | 0-90% |
| Wind Resistance | 0-90% |
| Void Resistance | 0-90% |

### Elemental Penetration (6 stats)

| Stat | Range |
|:-----|:------|
| Fire Penetration | 0-75% |
| Water Penetration | 0-75% |
| Lightning Penetration | 0-75% |
| Earth Penetration | 0-75% |
| Wind Penetration | 0-75% |
| Void Penetration | 0-75% |

### Elemental Increased Damage (6 stats)

| Stat | Range | Type |
|:-----|:------|:-----|
| Fire Increased Damage | 0-200% | Additive |
| Water Increased Damage | 0-200% | Additive |
| Lightning Increased Damage | 0-200% | Additive |
| Earth Increased Damage | 0-200% | Additive |
| Wind Increased Damage | 0-200% | Additive |
| Void Increased Damage | 0-200% | Additive |

### Elemental More Damage (6 stats)

| Stat | Range | Type |
|:-----|:------|:-----|
| Fire More Damage | 0-100% | Multiplicative |
| Water More Damage | 0-100% | Multiplicative |
| Lightning More Damage | 0-100% | Multiplicative |
| Earth More Damage | 0-100% | Multiplicative |
| Wind More Damage | 0-100% | Multiplicative |
| Void More Damage | 0-100% | Multiplicative |

> [!IMPORTANT]
> **Increased vs More** : "Increased" damage is additive (multiple sources stack linearly). "More" damage is multiplicative (each source multiplies the total independently). This mirrors the Path of Exile distinction. A mob with 50% Increased Fire Damage and 20% More Fire Damage deals `baseDamage x 1.50 x 1.20` fire damage.

### AI (4 stats)

| Stat | Base Value | Notes |
|:-----|-----------:|:------|
| Aggro Range | 16 | Detection distance in blocks |
| Reaction Delay | 1.0s | Time before responding to threats |
| Charge Time | 1.0s | Wind-up time for charge attacks |
| Charge Distance | 5.0 | Distance covered during charges |

### Special (2 stats)

| Stat | Range | Notes |
|:-----|:------|:------|
| Armor Penetration | 0-75% | Ignores a portion of target's armor |
| True Damage | 0-1000 | Damage that bypasses all defenses |

---

## Stat Application : EntityStatMap

Mob stats are stored and modified through the `MobScalingComponent` (ECS component) which holds :

- **scaledStats** the final computed stat values
- **level** the mob's effective level
- **classification** the mob's tier (Passive through Boss)
- **realm context** whether the mob is in a realm instance

Stat modifiers are applied via the `EntityStatMap` system using 3 modifier types :

| Modifier Type | How It Works |
|---------------|-------------|
| **FLAT** | Adds a fixed value (e.g., +50 Max Health) |
| **PERCENT** | Adds a percentage of the base value (e.g., +20% Max Health) |
| **MULTIPLY** | Multiplies the final value (e.g., x1.5 Max Health) |

These stack in order : first all FLAT modifiers are summed, then PERCENT modifiers scale the base+flat total, then MULTIPLY modifiers are applied last.

---

## Practical Implications

> [!TIP]
> **Carry diverse defenses.** The Dirichlet variance means one mob might deal mostly physical damage (needs Armor), the next might deal fire (needs [Fire](attributes#fire) Resistance), and the next might crit hard (needs raw health to survive spikes). Building exclusively into one defense type leaves you vulnerable to the others.

> [!WARNING]
> **Watch for Armor Penetration and True Damage.** Some mobs can roll high in these special stats, partially or entirely bypassing your armor. If a mob is dealing unexpected damage despite high armor, check if it has penetration or true damage stats.

---

## Related Pages

- [Elites & Bosses](elites-bosses) - Stat multipliers for Elite (1.5x) and Boss (3.0x) tiers
- [Mob System](mob-system) - Classification, XP multipliers, and level scaling
- [Death Recap](death-recap) - Identify what a mob specialized in after it kills you
- [Realm Biomes](realm-biomes) - Biome-specific mob pools
