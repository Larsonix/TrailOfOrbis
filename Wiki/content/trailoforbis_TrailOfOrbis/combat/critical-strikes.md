---
name: Critical Strikes
title: Critical Strikes
description: How crits work - base 5% chance, base 150% multiplier, one roll per attack
author: Larsonix
sort-index: 2
order: 43
published: true
---

# Critical Strikes

Critical strikes multiply your fully-scaled damage at step 9 of the [Damage Pipeline](the-11-step-damage-pipeline). One roll decides if your entire attack crits - physical and elemental damage alike.

---

## How Crits Work

```
if random(0-100) < critChance:
  allDamage = allDamage × (critMultiplier / 100)
```

| Property | Value |
|----------|-------|
| Base crit chance | 5.0% |
| Base crit multiplier | 150% (1.5x damage) |
| Roll timing | After all scaling, before defenses (step 9 of 11) |
| Applies to | ALL damage types equally - physical + every element |
| Roll count | **One roll per attack** - your entire attack crits or doesn't |

> [!IMPORTANT]
> The base crit multiplier is 150%, meaning your critical strike deals 1.5x damage before any bonuses. That's a 50% damage increase over a normal hit at baseline.

---

## Scaling Crit

Two stats control your critical strikes :

### Critical Chance (Lightning)

| Source | Value |
|--------|-------|
| Base | 5.0% |
| [Lightning](attributes#lightning) attribute | +0.1% per point |
| Gear modifiers | Variable |

At 100 Lightning, you have 5% + 10% = **15% crit chance** from base + attributes alone. Combined with gear, 25-35% crit chance is achievable.

### Critical Multiplier (Fire)

| Source | Value |
|--------|-------|
| Base | 150% (1.5x damage) |
| [Fire](attributes#fire) attribute | +0.6% per point |
| Gear modifiers | Variable |

At 100 Fire, your crit multiplier is 150% + 60% = **210% (2.1x damage)**. Combined with gear, 250%+ is achievable.

---

## Scaling Examples

| Fire | Lightning | Crit Multiplier | Crit Chance | Expected DPS Increase |
|-----:|----------:|:----------------|------------:|:----------------------|
| 0 | 0 | 150% (1.5x) | 5.0% | +2.5% average |
| 50 | 50 | 180% (1.8x) | 10.0% | +8.0% average |
| 100 | 50 | 210% (2.1x) | 10.0% | +11.0% average |
| 50 | 100 | 180% (1.8x) | 15.0% | +12.0% average |
| 100 | 100 | 210% (2.1x) | 15.0% | +16.5% average |

> [!NOTE]
> Expected DPS increase from crits = `critChance × (critMultiplier / 100 - 1)`. At 15% chance and 210% multiplier : `0.15 × 1.1 = 16.5%` average damage increase.

---

## Why Crits Are Strong

Crits happen at step 9 - after ALL your percentage scaling, conversion, and conditional multipliers. That means :

```
NonCrit: 100 base → +50% increased → 150 damage → defenses
Crit:    100 base → +50% increased → 150 damage → ×2.1 = 315 damage → defenses
```

The crit multiplier applies to your ALREADY-SCALED number. This is why [Fire](attributes#fire) + [Lightning](attributes#lightning) is such a powerful combo :
- Lightning gives you more crits (chance)
- Fire makes each crit devastating (multiplier)

---

## Crit and Multi-Element Damage

If your attack deals both physical and fire damage (e.g., through conversion), one crit roll multiplies BOTH. You don't roll separately for each element. This makes conversion builds particularly strong with crits - the multiplier amplifies everything at once.

> [!TIP]
> In combat, your critical strikes show with a " !" suffix on the damage number and appear in a brighter color variant than normal hits. Physical crits show as red instead of white. This visual feedback helps you gauge your effective crit rate during gameplay.

---

## Related Pages

- [Fire Attribute](attributes#fire) - Increases Critical Multiplier (+0.6% per point)
- [Lightning Attribute](attributes#lightning) - Increases Critical Chance (+0.1% per point)
- [Damage Pipeline](the-11-step-damage-pipeline) - Crits apply at Step 9, after all scaling
- [Attack Speed](attack-speed) - More attacks per second means more crit rolls
