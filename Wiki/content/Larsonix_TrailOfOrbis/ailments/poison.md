---
name: Poison
title: "Poison - Void Stacking DoT"
description: Void stacking damage over time - independent stacks, 10 max, scaling with *DoT* stats
author: Larsonix
sort-index: 3
order: 59
published: true
---

# Poison - Void Stacking *DoT*

Poison is [Void](attributes#void)'s Ailment and the most mechanically complex status effect. Unlike [Burn](burn-fire-dot) (which refreshes as a single instance), Poison **stacks** - each application adds a new independent *DoT* that ticks and expires on its own timer. Up to 10 stacks can be active at once.

**Display :** "Poisoned" | **Color :** Purple

---

## The Formula

```

DPS per stack = (hitDamage x 0.3 + flatPoisonDamage) / duration

```

| Parameter | Value |
|-----------|-------|
| Damage ratio | 30% of the triggering void hit (per stack) |
| Base duration | 5 seconds per stack |
| Max stacks | **10** (each independent) |
| Base application chance | 10% |
| Damage threshold | **None** (always applies if chance succeeds) |

> [!IMPORTANT]
> Poison has NO damage threshold. Unlike [Burn](burn-fire-dot), [Freeze](freeze-water-slow), and [Shock](shock-lightning-damage-amp), Poison always applies if the chance roll succeeds, regardless of how weak your hit was. This makes Poison uniquely reliable for fast, low-damage attack styles.

---

## How It Works

1. You deal void damage to a target
2. Application chance : `10% + statusEffectChance` (from gear/skills)
3. Each successful roll **adds a new stack** (up to 10) - doesn't refresh existing ones
4. Each stack deals **30% of its triggering hit damage** over 5 seconds
5. Each stack has its own independent timer
6. When a stack expires, it's removed - other stacks keep ticking

---

## Stacking Example

You attack 5 times over 3 seconds, applying Poison on hits 1, 3, and 5 :

```

t=0.0s  Hit 1 (200 dmg) -> Stack 1 starts: 12 DPS for 5s
t=1.5s  Hit 3 (180 dmg) -> Stack 2 starts: 10.8 DPS for 5s
t=3.0s  Hit 5 (220 dmg) -> Stack 3 starts: 13.2 DPS for 5s
t=3.0s  All 3 stacks active: 36 combined DPS
t=5.0s  Stack 1 expires (dealt 60 total damage)
t=6.5s  Stack 2 expires (dealt 54 total damage)
t=8.0s  Stack 3 expires (dealt 66 total damage)

```

During the overlap period (t=3.0s to t=5.0s), all 3 stacks deal damage simultaneously - tripling your effective *DoT* DPS.

---

## Scaling Poison Damage

| Stat | Source | Effect |
|------|--------|--------|
| **DoT Damage %** | [Void](attributes#void) attribute (+0.3% per point) | Multiplies ALL Poison DPS |
| **Status Effect Duration %** | [Void](attributes#void) attribute (+0.3% per point) | Extends each stack's duration |
| **Attack Speed** | [Lightning](attributes#lightning) attribute (+0.3% per point) | More attacks = more chances to apply stacks |

Void is Poison's natural home - both *DoT* Damage and Status Effect Duration come from Void, making high-Void builds the best Poison users.

### Worked Example - 100 Void, hitting for 300 void damage

```

Base DPS per stack = (300 x 0.3) / 5.0 = 18 DPS
DoT bonus = 100 x 0.3% = +30% -> 18 x 1.30 = 23.4 DPS per stack
Duration bonus = 100 x 0.3% = +30% -> 5.0 x 1.30 = 6.5s per stack

At 10 stacks: 23.4 x 10 = 234 combined DPS
Per stack total: 23.4 x 6.5 = 152.1 damage per stack

```

### Worked Example - 50 Void + 50 Lightning, hitting for 200 void damage

```

Base DPS per stack = (200 x 0.3) / 5.0 = 12 DPS
DoT bonus = 50 x 0.3% = +15% -> 12 x 1.15 = 13.8 DPS per stack
Duration bonus = 50 x 0.3% = +15% -> 5.0 x 1.15 = 5.75s per stack
Attack Speed bonus = 50 x 0.3% = +15% -> more hits = faster stack building

At 10 stacks: 13.8 x 10 = 138 combined DPS

```

The [Lightning](attributes#lightning) hybrid trades per-stack damage for faster stack accumulation via Attack Speed.

---

## Duration Scaling

Each Poison stack's duration is extended independently :

```

finalDuration = 5.0 x (1 + statusEffectDuration / 100)

```

| Void Points | Duration Bonus | Stack Duration |
|------------:|---------------:|---------------:|
| 0 | +0% | 5.0s |
| 50 | +15% | 5.75s |
| 100 | +30% | 6.5s |

Longer duration means more overlap between stacks and higher sustained DPS.

---

## Reaching Max Stacks

With 10% base chance, you need roughly 100 attacks to expect 10 Poison applications. At higher chance and faster attack speed :

| Scenario | Chance | Avg Hits to 10 Stacks |
|----------|--------|----------------------|
| Base (10%) | 10% | ~100 hits |
| 100 Void (10% + gear) | ~15% | ~67 hits |
| 100 Void + 100 Lightning | ~15% at 1.3x speed | ~52 hits equivalent time |

> [!TIP]
> Poison's power comes from **stack count**. A single Poison stack is modest (30% of one hit over 5s). Ten stacks running simultaneously is devastating. Fast-hitting [Void](attributes#void) builds (Void + [Lightning](attributes#lightning)) maximize stack count through rapid attack speed and high application frequency.

---

## Poison vs Burn

| Property | [Burn](burn-fire-dot) (Fire) | Poison (Void) |
|----------|-------------|---------------|
| Damage ratio | 50% per hit | 30% per hit per stack |
| Stacking | Refreshes (1 instance) | Independent stacks (up to 10) |
| Duration | 4 seconds | 5 seconds per stack |
| Max simultaneous DPS | 1 instance | 10 instances |
| Damage threshold | Yes | **No** |
| Best with | Big single hits (crits) | Many fast hits (attack speed) |

[Burn](burn-fire-dot) is better for burst-and-move playstyles. Poison is better for sustained DPS builds that maintain constant pressure.

> [!WARNING]
> Poison stacks have **independent timers**. If you stop attacking, stacks expire one by one. Unlike [Burn](burn-fire-dot) (which refreshes to full on reapplication), Poison requires continuous aggression to maintain peak DPS.

---

## Related Pages

- [Void Attribute](attributes#void) - *DoT* Damage % and Status Effect Duration amplify Poison
- [Lightning Attribute](attributes#lightning) - Attack Speed for faster stack building
- [Burn](burn-fire-dot) - Comparison : refreshing *DoT* vs stacking *DoT*
