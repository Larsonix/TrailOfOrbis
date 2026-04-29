---
id: combat-overview
name: Combat Overview
title: Combat System
description: How damage is calculated in Trail of Orbis - the 11-step pipeline from swing to impact
author: Larsonix
sort-index: 0
order: 40
published: true
sub-topics:
  - damage-pipeline
  - damage-types
  - critical-strikes
  - armor
  - resistances
  - evasion
  - blocking
  - attack-speed
  - death-recap
---

# Combat System

Every attack in Trail of Orbis passes through an **11-step Damage Pipeline** before the final number hits your target. The pipeline handles flat damage, percentage scaling, elemental conversion, critical strikes, and multiple layers of defense. Once you understand it, you know exactly where to invest for maximum impact.

| | |
|:--|:--|
| **Pipeline Stages** | 11 (Base → True Damage) |
| **Damage Categories** | 3 (Physical, Elemental, True) |
| **Base Crit Chance** | 5% (increased by [Lightning](attributes#lightning)) |
| **Base Crit Multiplier** | 150% (increased by [Fire](attributes#fire)) |
| **Armor Cap** | 90% physical reduction |
| **Resistance Cap** | 75% per element |

---

## The Pipeline at a Glance

| Step | Operation | What It Does |
|:----:|:----------|:-------------|
| 1 | Base Weapon Damage | Your weapon's raw output |
| 2 | + Flat Physical | Flat damage from gear |
| 3 | + Flat Elemental | Flat elemental from gear |
| 4 | Conversion | Physical → elemental |
| 5 | x % Increased Physical | Additive % bonuses |
| 6 | x % Elemental Modifiers | Per-element scaling |
| 7 | x % More Multipliers | Multiplicative chain |
| 8 | x Conditionals | Realm bonus, execute, etc. |
| 9 | x Critical Strike | If rolled, multiplies everything |
| 10 | / Defenses | Armor, Resistances reduce damage |
| 11 | + True Damage | Bypasses everything |
| | **Recovery** | Life Leech, Thorns, etc. |

> [!IMPORTANT]
> The order matters. You add flat damage early (steps 2-3), so it benefits from ALL percentage scaling that follows. Your critical strikes happen late (step 9), so they multiply the fully-scaled damage. True damage lands last (step 11) and bypasses all defenses.

---

## Avoidance - Before Damage Calculates

Before the pipeline even runs, your target gets avoidance checks in this order :

1. **Dodge** - Flat % chance to fully avoid the attack
2. **Evasion** - PoE-style formula : your Accuracy vs their Evasion
3. **Active Block** - If holding a shield, chance to block and reduce damage
4. **Passive Block** - Random proc without a shield (from Earth attribute)

If any check succeeds, the attack is avoided (dodge/evasion) or reduced (block). See [Evasion & Dodge](evasion-dodge) and [Blocking](blocking) for details.

---

## Damage Types

Trail of Orbis has 3 categories of damage :

| Category | Types | Reduced By |
|----------|-------|-----------|
| Physical | Physical | Armor + Physical Resistance |
| Elemental | Fire, Water, Lightning, Earth, Wind, Void | Per-element Resistance |
| True | True Damage | Nothing - bypasses all defenses |

See [Damage Types](damage-types) for the full breakdown with colors.

---

## Defense Layers

Your damage gets reduced by multiple independent defense layers :

| Defense | Protects Against | Stat Source | Cap |
|:--------|:-----------------|:-----------|:----|
| Armor | Physical damage | Gear + Earth attribute | 90% reduction |
| Resistances | Elemental damage (per element) | Gear modifiers | 75% per element |
| Evasion | All attacks (full avoidance) | Wind attribute + gear | Diminishing returns |
| Block | All attacks (partial reduction) | Earth attribute + shield | No cap on chance |

These stack multiplicatively. Armor reduces physical first, then Physical Resistance reduces what remains.

---

## Key Concepts

**Flat vs Percent** - Flat damage (+25 Physical Damage) gets added before percentage scaling, so it's more valuable than it looks. Percent damage (+15% Physical Damage) multiplies the total.

**Increased vs More** - "Increased" bonuses are additive with each other (10% + 10% = 20%). "More" bonuses are multiplicative (10% more x 10% more = 21%). This distinction matters once your gear gets serious.

**Conversion** - You can convert physical damage to elemental (e.g., 40% of physical to fire). Converted damage benefits from BOTH physical AND elemental modifiers.

**Penetration** - Reduces your target's effective resistance, but can't push it below 0% (PoE2-style). Excess penetration is wasted. Only debuffs can make resistance go negative.

**Critical Strikes** - Base 5% chance, base 150% (1.5x) multiplier. Lightning increases your chance, Fire increases your multiplier. See [Critical Strikes](critical-strikes).

**Recovery** - After you deal damage, recovery effects trigger : life leech, life steal, mana leech, mana steal, thorns damage, and parry reflect. These happen automatically based on your stats.

For the full formula with worked examples, see [Damage Pipeline](the-11-step-damage-pipeline).
