---
name: Resistances
title: Elemental Resistances
description: Per-element resistance system - 75% cap, penetration into negatives, overcapping as pen buffer
author: Larsonix
sort-index: 4
order: 45
published: true
---

# Elemental Resistances

Each element has an independent resistance stat that reduces incoming elemental damage by a flat percentage. Resistance is capped at **75%** to prevent immunity. Penetration subtracts from your **raw** resistance before the cap is applied — meaning overcapping your resistance provides a buffer against penetration. Penetration can push resistance into the negatives, causing you to take **up to 3× damage**.

---

## The Formula

```
effectiveResist = max(-200, min(75, resistance - penetration))

finalDamage = elementalDamage × (1 - effectiveResist / 100)
```

Penetration subtracts first, then the result is clamped between -200% and 75%.

---

## Resistance Boundaries

| Boundary | Value | Meaning |
|:---------|------:|:--------|
| **Cap** | **75%** | Maximum damage reduction per element |
| **Floor** | **-200%** | Maximum vulnerability (3× damage taken) |

| Effective Resistance | Damage Multiplier |
|---------------------:|------------------:|
| **75%** (cap) | **0.25×** — you take 25% |
| **50%** | **0.50×** — you take half |
| **0%** | **1.00×** — full damage |
| **-50%** | **1.50×** — 50% extra |
| **-100%** | **2.00×** — double damage |
| **-200%** (floor) | **3.00×** — triple damage |

---

## Overcapping

Stacking resistance above 75% is **not wasted** — it acts as a buffer against penetration. The 75% cap applies *after* penetration is subtracted from your raw resistance.

| Your Resistance | Enemy Penetration | After Pen | Effective (capped) | Damage |
|----------------:|------------------:|----------:|-------------------:|-------:|
| **120%** | **30%** | **90%** | **75%** (capped) | **25%** |
| **120%** | **50%** | **70%** | **70%** | **30%** |
| **75%** | **30%** | **45%** | **45%** | **55%** |
| **75%** | **0%** | **75%** | **75%** | **25%** |

> [!TIP]
> Against enemies with high penetration, overcapping your resistance keeps you at the 75% cap where a player with exactly 75% would lose significant protection. Every point of overcap absorbs one point of penetration.

---

## Penetration

Penetration subtracts from the target's raw resistance. Unlike overcapping, penetration can push resistance **below zero**, making targets take bonus damage up to the -200% floor (3× damage).

| Target Resistance | Your Penetration | Effective Resistance | Damage Multiplier |
|------------------:|------------------:|---------------------:|------------------:|
| **50%** | **0%** | **50%** | **0.50×** |
| **50%** | **20%** | **30%** | **0.70×** |
| **50%** | **50%** | **0%** | **1.00×** |
| **50%** | **80%** | **-30%** | **1.30×** |
| **30%** | **60%** | **-30%** | **1.30×** |
| **0%** | **50%** | **-50%** | **1.50×** |

> [!IMPORTANT]
> Penetration is most impactful against targets with low resistance. Against 0% resistance, every point of penetration directly increases your damage. Against 120% overcapped resistance, the first 45 points of penetration are absorbed before your damage even increases.

---

## The 6 Elemental Resistances

| Resistance | Reduces | Common Sources |
|-----------|---------|----------------|
| [Fire](attributes#fire) Resistance | [Fire](attributes#fire) damage, [Burn](burn-fire-dot) *DoT* | Gear suffixes |
| [Water](attributes#water) Resistance | [Water](attributes#water)/ice damage | Gear suffixes |
| [Lightning](attributes#lightning) Resistance | [Lightning](attributes#lightning) damage | Gear suffixes |
| [Earth](attributes#earth) Resistance | [Earth](attributes#earth) damage | Gear suffixes |
| [Wind](attributes#wind) Resistance | [Wind](attributes#wind) damage | Gear suffixes |
| [Void](attributes#void) Resistance | [Void](attributes#void) damage, [Poison](poison-void-stacking-dot) *DoT* | Gear suffixes |

> [!NOTE]
> Resistances come primarily from **gear suffix modifiers** (e.g., "+15% Fire Resistance"). No attribute grants resistance directly — this is a gear-dependent stat, so your equipment choices matter for elemental defense.

---

## Resistance vs Armor

| Feature | Armor | Resistance |
|---------|-------|-----------|
| Protects against | [Physical](damage-types#physical) only | One element each |
| Formula | Diminishing returns (depends on hit size) | Flat percentage |
| Cap | **90%** | **75%** |
| Floor | **-100%** | **-200%** |
| Scaling | Better vs small hits | Equal vs all hit sizes |

Both systems apply independently. Physical attacks are reduced by armor first, then by Physical Resistance. Elemental attacks are reduced only by the relevant elemental resistance.

---

## Building Resistance

For general content, aim for positive resistance in the elements you expect to face. For realm content with elemental mob modifiers, check the realm info before entering and gear accordingly.

> [!TIP]
> You don't need 75% in every element. Focus on what's relevant to the content you're running. Against a fire-heavy realm, capping your Fire Resistance is far more valuable than spreading points across all 6 elements. If enemies have high penetration, consider overcapping to maintain your defense.

---

## Related Pages

- [Armor & Physical Defense](armor-physical-defense) — Physical damage reduction formula with 90% cap
- [Damage Types](damage-types) — All damage categories and what reduces each one
- [Gear Modifiers](modifiers) — Resistance comes primarily from gear suffix modifiers
