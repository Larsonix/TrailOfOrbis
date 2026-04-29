---
name: Damage Types
title: Damage Types
description: Physical, elemental, and true damage - how each type works and what reduces it
author: Larsonix
sort-index: 1
order: 42
published: true
---

# Damage Types

Trail of Orbis has 3 categories of damage : physical, elemental, and true. Each one interacts differently with defenses and shows distinct visual indicators in combat.

---

## Physical Damage

Physical damage is the most common type. It comes from melee weapons, projectiles, and unarmed attacks.

**Defense layers (applied in order) :**

1. **Armor** - Reduces your damage using the formula `armor / (armor + 10 × damage)`, capped at 90% reduction
2. **Physical Resistance** - Flat percentage reduction, capped at 75%

Both layers stack multiplicatively. If armor cuts a hit by 50% and then physical resistance removes another 20%, only 40% of the original damage gets through.

See [Armor](armor-physical-defense) for the full formula and worked examples.

---

## Elemental Damage

6 elemental types exist, each with its own independent resistance stat :

| Element | Reduced By | Associated Ailment |
|---------|-----------|-------------------|
| [Fire](attributes#fire) | Fire Resistance | [Burn](burn-fire-dot) (damage over time) |
| [Water](attributes#water) | Water Resistance | [Freeze](freeze-water-slow) (slow/immobilize) |
| [Lightning](attributes#lightning) | Lightning Resistance | [Shock](shock-lightning-damage-amp) (increased damage taken) |
| [Earth](attributes#earth) | Earth Resistance | - |
| [Wind](attributes#wind) | Wind Resistance | - |
| [Void](attributes#void) | Void Resistance | [Poison](poison-void-stacking-dot) (damage over time) |

**Elemental resistance formula :**
```
effectiveResist = max(0, min(resistance, 75) - penetration)    // pen floors at 0%
elemDamage = elemDamage × (1 - effectiveResist / 100)
```

Each element's resistance is capped at 75% reduction. Your penetration reduces their effective resistance but can't push it below 0% - excess penetration is wasted.

See [Resistances](elemental-resistances) for full details.

---

## True Damage

True damage bypasses ALL defenses. Armor, resistances, nothing reduces it. True damage gets added at the very end of the [Damage Pipeline](the-11-step-damage-pipeline) (step 11), after every other calculation is done.

**Source :** The [Void](attributes#void) attribute grants +0.05% of each hit as true damage per point. At 100 Void, 5% of your total hit damage gets added as true damage on top of whatever survived defenses.

> [!IMPORTANT]
> True damage can't be reduced, reflected, or avoided once the attack connects. It's the only guaranteed damage in the game. However, avoidance (dodge, evasion, block) prevents the entire attack, including its true damage component.

---

## Damage Conversion

You can **convert** physical damage to any elemental type through gear modifiers or abilities :

```
converted = physicalDamage × (conversionPercent / 100)
elementalDamage[element] += converted
physicalDamage -= converted
```

Key rules :
- Total conversion is capped at **100%**. If your conversions exceed 100%, each is scaled proportionally
- Converted damage benefits from BOTH physical modifiers AND elemental modifiers
- Once converted, the damage is reduced by elemental resistance instead of armor

**Example :** With 60% physical-to-fire conversion and 100 physical damage :
- 60 damage becomes fire (reduced by Fire Resistance)
- 40 damage remains physical (reduced by Armor + Physical Resistance)
- Both portions benefit from physical % increased (step 5)
- The fire portion also benefits from fire % modifiers (step 6)

> [!TIP]
> Conversion builds are powerful because they double-dip on scaling. Your converted damage gets boosted by physical modifiers (since it started as physical) AND by elemental modifiers (since it ends as elemental). This makes conversion one of the strongest damage scaling strategies you can use.

---

## Combat Indicators

Each damage type has a unique color in combat, so you can instantly see what's hitting you :

| Type | Color | What It Means |
|------|-------|--------------|
| Physical | White | Melee/projectile damage, reduced by armor |
| Fire | Orange | Fire damage, check your Fire Resistance |
| Water | Light Blue | Water/ice damage, check your Water Resistance |
| Lightning | Yellow | Lightning damage, check your Lightning Resistance |
| Earth | Brown | Earth damage, check your Earth Resistance |
| Wind | Green | Wind damage, check your Wind Resistance |
| Void | Purple | Void damage, check your Void Resistance |

Critical strikes show in a brighter color variant than normal hits. Physical crits appear red instead of white, so you can gauge your crit rate at a glance.

Additional combat feedback :
- **"Dodged"** - Your attack was evaded
- **"Dodged"** - You dodged an incoming attack
- **"Blocked"** - Attack was blocked (damage reduced)
- **"Parried"** - Attack was parried (damage reflected)
- **"!"** suffix on damage numbers indicates a critical strike
- **Screen flash** when incoming damage exceeds a threshold, scaled to vanilla HP for visual intensity

> [!NOTE]
> You can toggle detailed combat breakdowns in chat with `/too combat detail`. This shows the full damage calculation per hit - great for theorycrafting and dialing in your build.

---

## Related Pages

- [Damage Pipeline](the-11-step-damage-pipeline) - How all damage types flow through the 11-step calculation
- [Elemental Resistances](elemental-resistances) - How to reduce incoming elemental damage
- [Armor & Physical Defense](armor-physical-defense) - How to reduce incoming physical damage
- [Status Effects](status-effects) - Elemental damage types can trigger ailments
