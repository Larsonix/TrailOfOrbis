---
name: Burn
title: "Burn - Fire DoT"
description: Fire damage over time - formula, tick rate, duration, refresh mechanics
author: Larsonix
sort-index: 0
order: 56
published: true
---

# Burn - Fire *DoT*

Burn is [Fire](attributes#fire)'s Ailment. It deals a percentage of your fire hit damage as damage over time. When you reapply it, the duration refreshes and the stronger magnitude wins. It does not stack.

**Display :** "Burning" | **Color :** Red

---

## The Formula

```

magnitude (DPS) = (hitDamage x 0.5 + flatBurnDamage) / duration
totalBurnDamage = magnitude x duration

```

| Parameter | Value |
|-----------|-------|
| Damage ratio | 50% of the triggering fire hit |
| Base duration | 4 seconds |
| Tick rate | Every 0.25s (250ms) |
| Max stacks | 1 (refreshes on reapplication) |
| Base application chance | 10% |
| Damage threshold | Yes (minimum hit damage required) |

---

## How It Works

1. You deal fire damage to a target
2. Application chance : `10% + igniteChance` ([Fire](attributes#fire) gives +0.1% per point)
3. If your hit damage meets the target's damage threshold, Burn applies
4. Burn deals **50% of the hit damage** over 4 seconds as fire *DoT*
5. Damage ticks every 0.25 seconds (16 ticks over the full duration)
6. If Burn is reapplied while already active :
   - Duration refreshes to full
   - If the new magnitude is stronger, it replaces the old one
   - If weaker, the old (stronger) magnitude stays

---

## Tick Mechanics

Burn ticks every 250ms (0.25 seconds). Over a 4-second duration, that's 16 ticks total.

```

damagePerTick = magnitude (DPS) x 0.25

```

**Example** - 200 fire damage hit, no bonus stats :
```

magnitude = (200 x 0.5) / 4.0 = 25 DPS
damagePerTick = 25 x 0.25 = 6.25 damage per tick
totalBurnDamage = 25 x 4.0 = 100 damage over 16 ticks

```

---

## Scaling Burn Damage

Two stats directly increase your Burn damage :

| Stat | Source | Effect |
|------|--------|--------|
| **Burn Damage %** | [Fire](attributes#fire) attribute (+0.4% per point) | Increases the flat burn damage component |
| **DoT Damage %** | [Void](attributes#void) attribute (+0.3% per point) | Multiplies ALL *DoT* damage including Burn |

### Worked Example - 50 Fire + 50 Void, hitting for 200 fire damage

```

baseBurn = 200 x 0.5 = 100 total burn damage
burnDamageBonus = 50 x 0.4% = +20% burn damage
dotDamageBonus = 50 x 0.3% = +15% *DoT* damage

With Burn Damage: 100 x 1.20 = 120
With *DoT* Damage: 120 x 1.15 = 138 total burn damage
DPS = 138 / 4.0 = 34.5 DPS

```

### Worked Example - 100 Fire + 100 Void, hitting for 200 fire damage

```

baseBurn = 200 x 0.5 = 100 total burn damage
burnDamageBonus = 100 x 0.4% = +40%
dotDamageBonus = 100 x 0.3% = +30%

With Burn Damage: 100 x 1.40 = 140
With *DoT* Damage: 140 x 1.30 = 182 total burn damage
DPS = 182 / 4.0 = 45.5 DPS

```

---

## Duration Scaling

Burn duration is extended by [Void](attributes#void)'s Status Effect Duration (+0.3% per point) :

```

finalDuration = 4.0 x (1 + statusEffectDuration / 100)

```

| Void Points | Duration Bonus | Burn Duration | Total Ticks |
|------------:|---------------:|--------------:|------------:|
| 0 | +0% | 4.0s | 16 |
| 50 | +15% | 4.6s | ~18 |
| 100 | +30% | 5.2s | ~21 |

Longer duration means more total damage from the same DPS, and more time for the *DoT* to work before it expires.

---

## Refresh Behavior

Burn does not stack. When you apply a new Burn to a target that already has one :

- The duration **always refreshes** to full
- The magnitude takes the **stronger** value (higher DPS wins)
- Weaker subsequent hits do not reduce the existing Burn's damage

> [!TIP]
> Burn is strongest when fed by big individual hits. A single massive fire crit creates a proportionally massive Burn. [Fire](attributes#fire) + [Lightning](attributes#lightning) (crit chance + crit multiplier) maximizes both the initial hit and the resulting Burn magnitude. Fast weak hits just refresh the timer without improving the damage.

---

## Application Chance

```

finalChance = 10% (base) + igniteChance (from Fire attribute + gear)

```

| Fire Points | Ignite Chance Bonus | Total Burn Chance |
|------------:|--------------------:|------------------:|
| 0 | +0% | 10% |
| 50 | +5% | 15% |
| 100 | +10% | 20% |

---

## Tactical Considerations

- Burn rewards **burst damage** - one big crit applies a stronger Burn than many small hits
- [Fire](attributes#fire) + [Void](attributes#void) is the strongest Burn combo : Burn Damage %, *DoT* Damage %, and longer duration
- Burn's damage threshold prevents trivial chip damage from applying the effect on strong enemies
- In group play, multiple Fire users all refresh the same Burn - the strongest magnitude always wins

---

## Related Pages

- [Fire Attribute](attributes#fire) - Grants Burn Damage % and Ignite Chance
- [Void Attribute](attributes#void) - *DoT* Damage % and Status Effect Duration amplify Burn
- [Poison](poison-void-stacking-dot) - Comparison : stacking *DoT* vs refreshing *DoT*
