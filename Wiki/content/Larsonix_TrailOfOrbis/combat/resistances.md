---
name: Resistances
title: Elemental Resistances
description: Per-element resistance system - 75% cap, penetration to 0% floor
author: Larsonix
sort-index: 4
order: 45
published: true
---

# Elemental Resistances

Each element has an independent resistance stat that reduces incoming elemental damage by a flat percentage. Resistance is capped at 75% to prevent immunity. Penetration reduces resistance but can't push it below 0%.

---

## The Formula

```

cappedResist = min(resistance, 75)

if cappedResist > 0 and penetration > 0:
    effectiveResist = max(0, cappedResist - penetration)     // penetration floors at 0%
else:
    effectiveResist = max(-100, cappedResist)                 // debuffs can go to -100%

finalDamage = elementalDamage × (1 - effectiveResist / 100)

```

---

## Resistance Cap

| Boundary | Value | Meaning |
|:---------|------:|:--------|
| **Cap** | 75% | Maximum damage reduction per element |
| **Penetration floor** | 0% | Penetration can only reduce your resistance to zero |
| **Debuff floor** | -100% | Naturally negative resistance (from debuffs) floors here |

| Effective Resistance | Damage You Take |
|---------------------:|----------------:|
| 75% (cap) | 25% of incoming elemental damage |
| 50% | 50% |
| 0% | 100% (full damage) |

> [!NOTE]
> Your resistance can only go negative through debuffs, not through penetration. At -100% (debuff floor), you take DOUBLE elemental damage.

---

## Penetration

Penetration reduces your effective resistance, but **can't push it below 0%** (PoE2-style). Excess penetration is wasted.

```

effectiveResist = max(0, cappedResist - penetration)

```

| Your Resistance | Enemy Penetration | Effective Resistance | Damage You Take |
|----------------:|------------------:|---------------------:|----------------:|
| 50% | 0% | 50% | 50% |
| 50% | 20% | 30% | 70% |
| 50% | 50% | 0% | 100% |
| 50% | 80% | 0% | 100% |
| 30% | 60% | 0% | 100% |
| 0% | 50% | 0% | 100% |

> [!IMPORTANT]
> Penetration has diminishing returns - once it exceeds your resistance, the extra is wasted. Against 50% resistance, 50 penetration gives full effect, but 80 penetration gives the same result as 50. Penetration is most valuable when enemies have moderate resistance.

---

## The 6 Elemental Resistances

| Resistance | Reduces | Common Sources |
|-----------|---------|----------------|
| Fire Resistance | [Fire](attributes#fire) damage, [Burn](burn-fire-dot) *DoT* | Gear suffixes |
| Water Resistance | [Water](attributes#water)/ice damage | Gear suffixes |
| Lightning Resistance | [Lightning](attributes#lightning) damage | Gear suffixes |
| Earth Resistance | [Earth](attributes#earth) damage | Gear suffixes |
| Wind Resistance | [Wind](attributes#wind) damage | Gear suffixes |
| Void Resistance | [Void](attributes#void) damage, [Poison](poison-void-stacking-dot) *DoT* | Gear suffixes |

> [!NOTE]
> Resistances come primarily from **gear suffix modifiers** (e.g., "+15% Fire Resistance"). No attribute grants resistance directly - this is a gear-dependent stat, so your equipment choices matter for elemental defense.

---

## Resistance vs Armor

| Feature | Armor | Resistance |
|---------|-------|-----------|
| Protects against | Physical only | One element each |
| Formula | Diminishing returns (depends on hit size) | Flat percentage |
| Cap | 90% | 75% |
| Scaling | Better vs small hits | Equal vs all hit sizes |
| Penetration floor | -100% effective resistance | -100% effective resistance |

Both systems apply independently. Physical attacks are reduced by armor first, then by Physical Resistance. Elemental attacks are reduced only by the relevant elemental resistance.

---

## Building Resistance

For general content, aim for positive resistance in the elements you expect to face. For realm content with specific elemental modifiers, check the realm info before entering and gear accordingly.

> [!TIP]
> You don't need 75% in every element. Focus on what's relevant to the content you're running. Against a fire-heavy realm, capping your Fire Resistance is far more valuable than spreading points across all 6 elements.

---

## Related Pages

- [Armor & Physical Defense](armor-physical-defense) - Physical damage reduction formula with 90% cap
- [Damage Types](damage-types) - All damage categories and what reduces each one
- [Gear Modifiers](modifiers) - Resistance comes primarily from gear suffix modifiers
