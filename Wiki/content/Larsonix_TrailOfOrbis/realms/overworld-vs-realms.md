---
name: Overworld vs Realms
title: Overworld vs Realms
description: The overworld is your base, Realms are the main loop - how they compare and why Realms are more rewarding
author: Larsonix
sort-index: -1
order: 99
published: true
---

# Overworld vs Realms

The overworld is your base. Realms are where the action is. Realm Maps drop at 12% per kill from level 1, making Realms the core game loop from almost the moment you start. Mobs inside are stronger, drop better loot, give more XP, and have bonuses that don't exist in the overworld.

---

## The Short Version

| System | Overworld | Realms | Difference |
|--------|-----------|--------|------------|
| Elite XP Multiplier | 1.5x | 3.0x | **Double** |
| Elite Stat Multiplier | 1.5x | 2.0x | **+33%** |
| Loot IIQ Bonuses | None | From map suffixes + Fortune's Compass | **Realm-only** |
| Loot IIR Bonuses | None | From map suffixes | **Realm-only** |
| Damage Bonus | None | Realm Damage conditional | **Realm-only** |
| Completion Rewards | None | Bonus loot on clearing all mobs | **Realm-only** |
| Mob Level | Distance-scaled from spawn | Map level (fixed) | Predictable in realms |

> [!IMPORTANT]
> The overworld is your base - mobs scale with distance from spawn, so pushing outward requires real investment. Realms are the core loop where you get the best rewards. If you're farming for gear upgrades, stones, or XP, running Realms is always more efficient than killing overworld mobs.

---

## XP & Stat Differences

| Classification | Overworld XP | Overworld Stats | Realm XP | Realm Stats |
|----------------|-------------|----------------|----------|-------------|
| Passive | 0.1x | 0.1x | - | - |
| Minor | 0.5x | 1.0x | - | - |
| Hostile | 1.0x | 1.0x | 1.0x | 1.0x |
| **Elite** | 1.5x | 1.5x | **3.0x** | **2.0x** |
| Boss | 5.0x | 3.0x | 5.0x | 3.0x |

Realm Elites also get a separate **1.5x Health Multiplier** on top of their stat multiplier. Elite Bosses are possible and receive both elite AND boss multipliers stacked.

---

## Loot Differences

### Base Drop Rates (same everywhere)

| Drop Type | Base Chance |
|-----------|------------|
| Gear | 8% per kill |
| Stone | 5% per kill |

### Mob Type Bonuses (same everywhere)

| Mob Type | Quantity Bonus | Rarity Bonus |
|----------|---------------|-------------|
| Normal | +0% | +0% |
| Elite | +50% | +25% |
| Boss | +100% | +50% |

### Realm-Only Bonuses (stacked on top)

Realms add Item Quantity (IIQ) and Item Rarity (IIR) from map modifiers :

| Source | Bonus Type | Range |
|--------|-----------|-------|
| Item Quantity suffix | IIQ | +5-50% |
| Item Rarity suffix | IIR | +5-40% |
| Fortune's Compass stone | IIQ | +5% each, max +20% |

These stack additively with mob type bonuses. A Boss in a Realm with +30% IIQ suffix and +20% Fortune's Compass gets +100% (boss) + 30% + 20% = **+150% total quantity bonus**.

> [!TIP]
> Always use Fortune's Compass on maps before running them. 4 [Uncommon](gear-rarities#uncommon) stones give +20% IIQ, a permanent boost to every kill in that Realm.

---

## Damage Differences

The [Damage Pipeline](the-11-step-damage-pipeline) Step 8 (Conditional Multipliers) includes a **Realm Damage Bonus** that only activates when you're fighting inside a Realm. Your character deals more damage in Realms than against equivalent mobs in the overworld.

---

## Completion Rewards

Realms have a completion system that the overworld does not :

- Kill all mobs in the Realm to trigger completion
- `RealmRewardService` distributes bonus rewards to all participants
- Rewards scale with map level, rarity, and modifier bonuses
- Reward multiplier scales with size : Small 1.0x, Medium 1.5x, Large 2.5x, Massive 4.0x

There is no equivalent mechanic in the overworld. You only get per-kill drops there.

---

## Why This Design

The overworld is your base. Mobs scale with distance from spawn (0.5 bonus stat pool per block), so the area around spawn stays safe while the outer world becomes progressively harder. You need to level and gear up to push farther outward. Realms are the core game loop from almost level 1 : harder, more dangerous, but proportionally more rewarding. This creates a natural progression :

1. **Early game** : Craft starter gear, kill mobs near spawn, enter first Realms from your very first map drops
2. **Mid game** : Push deeper into the overworld, run harder and larger Realm maps, build your character
3. **Late game** : Optimize gear with Stones, stack map modifiers, push Large and Massive Realms
4. **Deep game** : Farm specific biomes, craft perfect gear, push harder modifiers for better rewards

The incentive gap is intentional. Realms drop more loot, give more XP, and offer completion bonuses the overworld doesn't have. The overworld rewards exploration and gives you the maps to fuel the Realm loop.

---

## Related Pages

- [Procedural Dungeons](procedural-dungeons) - Full Realm system overview
- [Realm Rewards](realm-rewards) - Victory rewards and timeout penalties
- [Map Crafting](map-crafting) - Preparing maps for maximum efficiency
- [Elites & Bosses](elites-bosses) - Elite mobs are stronger in Realms
