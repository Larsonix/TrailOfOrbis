---
name: Rewards
title: Realm Rewards
description: Victory rewards, timeout penalties, reward scaling by size, level, rarity, and modifiers
author: Larsonix
sort-index: 4
order: 105
published: true
---

# Realm Rewards

Completing a Realm (killing all mobs before the timer expires) triggers victory rewards on top of everything you earned from individual kills during the session.

---

## During the Realm

Every mob you kill inside a Realm drops loot at the standard rates :

| Drop Type | Base Chance |
|-----------|-------------|
| Gear | 8% per kill |
| Stones | 5% per kill |

These drops are yours regardless of whether you complete the Realm or time out. Map modifiers (Item Quantity, Item Rarity, etc.) apply to these drops.

---

## Victory Rewards

When all mobs are killed before the timer expires, you receive additional rewards. Victory reward scaling depends on multiple factors :

### Reward Multiplier by Size

| Size | Reward Multiplier | Guaranteed Bosses |
|------|-------------------|-------------------|
| Small | 1.0x | 0 |
| Medium | 1.5x | 1 |
| Large | 2.5x | 1 |
| Massive | 4.0x | 2 |

### Reward Scaling Factors

Victory rewards scale based on :

| Factor | Effect |
|--------|--------|
| **Map Level** | Higher level maps yield higher level rewards |
| **Map Rarity** | Higher rarity maps have more modifiers, affecting drops |
| **Size Reward Multiplier** | 1.0x to 4.0x based on Realm size |
| **IIQ from Suffixes** | Item Quantity suffix modifier increases drop quantity |
| **IIR from Suffixes** | Item Rarity suffix modifier improves drop rarity |
| **Fortune's Compass Bonus** | 0-20% additional IIQ from map preparation |

> [!TIP]
> Fortune's Compass bonus (up to +20%) stacks with Item Quantity suffix modifiers. Preparing every map with 4x Fortune's Compass before running is one of the most efficient ways to boost your rewards.

---

## Timeout - What Happens

If the timer expires before all mobs are killed :

- You **keep** all gear, stones, and XP earned from individual mob kills
- You do **not** receive victory rewards
- You're exited from the Realm automatically

> [!NOTE]
> Timing out is not a total loss. You keep everything earned from kills. But the victory rewards (which scale up to 4.0x on Massive maps) are forfeited.

---

## Completion Requirements

The Realm is completed when the **last mob dies**. The completion tracking system monitors :

- Total and remaining monster count
- Per-player kills and damage dealt
- Elites killed and bosses killed
- Start time and completion time

There is no partial completion. Either all mobs die before the timer expires (full victory rewards) or they don't (no victory rewards).

---

## Emergency Exit

If you get stuck inside a Realm :

`/too realm exit`

> [!WARNING]
> `/too realm exit` is an emergency command. Using it forfeits victory rewards and immediately exits the Realm. Only use it if you're genuinely stuck.

---

## Related Pages

- [Realm Sizes](realm-sizes) - Reward multiplier scales 1.0x to 4.0x by size
- [Realm Modifiers](realm-modifiers) - Suffix modifiers boost rewards
- [Map Crafting](map-crafting) - Fortune's Compass adds up to +20% IIQ
- [Overworld vs Realms](overworld-vs-realms) - Full comparison of reward structures
