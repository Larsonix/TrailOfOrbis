---
id: gear-modifiers
name: Modifiers
title: Gear Modifiers
description: 101 modifier definitions across prefixes and suffixes - flat vs percent stats, locking, and level scaling
author: Larsonix
sort-index: 2
order: 68
published: true
---

# Gear Modifiers

Modifiers are the stats rolled on your equipment. With **101 distinct modifier definitions** loaded from `gear-modifiers.yml`, every item is unique. One clean rule : **Prefixes are offense, Suffixes are defense**.

---

## Prefix vs Suffix

| Type | Role | Examples |
|------|------|---------|
| **Prefix** | Offensive stats | Physical Damage, Crit Chance, Attack Speed, Spell Damage |
| **Suffix** | Defensive stats | Max Health, Armor, Evasion, Resistances, Health Regen |

Every rarity has a maximum number of prefixes AND suffixes :

| Rarity | Max Prefix | Max Suffix | Max Total |
|:------:|-----------:|-----------:|----------:|
| [Common](gear-rarities#common) | 1 | 1 | 1 |
| [Uncommon](gear-rarities#uncommon) | 1 | 2 | 2 |
| [Rare](gear-rarities#rare) | 2 | 2 | 3 |
| [Epic](gear-rarities#epic) | 2 | 2 | 4 |
| [Legendary](gear-rarities#legendary) | 2 | 3 | 5 |
| [Mythic](gear-rarities#mythic) | 3 | 3 | 6 |
| [Unique](gear-rarities#unique) | 3 | 3 | 6 |

---

## Modifier Properties

Each of the 101 modifier definitions has these properties :

| Property | Description |
|----------|-------------|
| `id` | Internal identifier |
| `displayName` | What you see on tooltips |
| `type` | PREFIX (offense) or SUFFIX (defense) |
| `statId` | Which attribute or stat this modifier affects |
| `statType` | FLAT (additive) or PERCENT (multiplicative) |
| `value` | Base value, scaled by item level |
| `locked` | Whether this modifier is protected from rerolling |

---

## Flat vs Percent

Modifiers come in two stat types that stack differently :

| Type | Tooltip Example | How It Works |
|------|----------------|-------------|
| **FLAT** | `+50 Armor` | Added directly to your stat total |
| **PERCENT** | `+12% Physical Damage` | Multiplies the stat after all flat bonuses |

Flat modifiers are added early in calculations. They benefit from all percentage scaling that follows. A flat damage modifier on a weapon is more valuable than it looks because it gets multiplied by percentage bonuses, critical strikes, and other multipliers downstream.

---

## Level Scaling

Modifier values scale with **item level**. The same modifier definition produces larger numbers on higher-level gear. This is further multiplied by :
1. The item's **rarity** stat multiplier (0.3x to 2.8x)
2. The item's **quality** multiplier

A modifier on a Level 50 [Mythic](gear-rarities#mythic) item with Q100 is dramatically stronger than the same modifier on a Level 10 [Common](gear-rarities#common) item with Q25.

---

## Locked Modifiers

You can **lock** modifiers to protect them from rerolling. When a modifier is locked :
- It's preserved when using stones that reroll or replace modifiers
- It can't be removed by removal stones
- The lock is applied via the **Warden's Seal** stone

Locking is the key endgame crafting mechanic. When you roll a perfect modifier, lock it down before rerolling the rest.

> [!TIP]
> Lock your best modifier before using reroll stones. A locked +Physical Damage prefix stays safe while you reroll the remaining modifiers hunting for better rolls.

---

## Modifier Interactions with Stones

Various crafting stones interact with modifiers :

| Action | Description |
|--------|-------------|
| Reroll values | Keep modifier types but randomize their values |
| Replace modifiers | Remove unlocked modifiers and roll new ones |
| Add modifier | Add a new modifier if empty slots exist |
| Remove modifier | Remove a specific or random unlocked modifier |
| Lock modifier | Protect a modifier from future changes |

See the [Stones](consumable-currency) page for the full crafting system.

---

## Building Around Modifiers

**Offensive builds** want prefix-heavy gear. Weapons naturally favor prefixes (damage, crit, speed), making them the most important slot for offense.

**Defensive builds** want suffix-heavy gear. Chest, head, and legs armor naturally favor suffixes (health, armor, resistances, regen).

**Hybrid builds** target the Hands slot. It can roll both offensive prefixes and defensive suffixes, making it the most versatile armor piece.

> [!NOTE]
> With 101 distinct modifier definitions, the pool is large enough that getting the exact combination you want requires either many drops or strategic use of crafting stones. This is intentional. Perfect gear is a long-term goal, not a quick achievement.

---

## Related Pages

- [Quality System](quality-system) - Quality multiplies all modifier values
- [Stones](consumable-currency) - Lock, remove, and reroll modifiers
- [Reading Item Tooltips](reading-item-tooltips) - How modifiers appear on gear
