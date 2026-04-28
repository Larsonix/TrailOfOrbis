---
name: Freeze
title: "Freeze - Water Slow"
description: Water movement and action speed slow - HP-proportional scaling, 5-30% range
author: Larsonix
sort-index: 1
order: 57
published: true
---

# Freeze - Water Slow

Freeze is [Water](attributes#water)'s Ailment. Instead of dealing damage, it slows the target's movement and action speed. The slow scales with how hard you hit relative to the target's max health - naturally weaker against bosses, stronger against trash mobs.

**Display :** "Chilled" | **Color :** Blue

---

## The Formula

```
slowPercent = clamp(hitDamage / targetMaxHealth x 100, 5%, 30%)
```

| Parameter | Value |
|-----------|-------|
| Minimum slow | 5% |
| Maximum slow | 30% |
| Base duration | 3 seconds |
| Max stacks | 1 (refreshes, stronger magnitude wins) |
| Base application chance | 10% |
| Damage threshold | Yes (minimum hit damage required) |

---

## How It Works

1. You deal water damage to a target
2. Application chance : `10% + freezeChance` ([Water](attributes#water) gives +0.1% per point)
3. If your hit damage meets the target's damage threshold, Freeze applies
4. Slow magnitude is calculated from **hit damage / target max HP**
5. The target's movement and action speed drop by the calculated percentage
6. If Freeze is reapplied :
   - Duration refreshes
   - Stronger magnitude replaces weaker

---

## HP-Proportional Scaling

The slow percentage depends on the ratio of your hit to the target's health :

| Your Hit | Target Max HP | Slow % |
|---------|---------------|--------|
| 50 | 100 | 30% (capped) |
| 50 | 500 | 10% |
| 50 | 1000 | **5% (floor)** |
| 100 | 500 | 20% |
| 200 | 1000 | 20% |
| 500 | 1000 | 30% (capped) |

> [!IMPORTANT]
> This HP-proportional scaling is deliberate. Freeze naturally self-balances : very effective against weak mobs (your damage is a large chunk of their HP) but limited against bosses (your damage is a tiny fraction of their massive HP pool). No special boss immunity needed - the math handles it.

---

## Duration Scaling

Freeze duration is extended by [Void](attributes#void)'s Status Effect Duration (+0.3% per point) :

```
finalDuration = 3.0 x (1 + statusEffectDuration / 100)
```

| Void Points | Duration Bonus | Freeze Duration |
|------------:|---------------:|----------------:|
| 0 | +0% | 3.0s |
| 50 | +15% | 3.45s |
| 100 | +30% | 3.9s |

Longer Freeze = more time for you to attack, reposition, or heal while the enemy is slowed.

---

## Application Chance

```
finalChance = 10% (base) + freezeChance (from Water attribute + gear)
```

| Water Points | Freeze Chance Bonus | Total Freeze Chance |
|-------------:|--------------------:|--------------------:|
| 0 | +0% | 10% |
| 50 | +5% | 15% |
| 100 | +10% | 20% |

---

## What Freeze Affects

Freeze reduces both movement speed and action speed :

- **Movement speed** - The target walks and runs slower, making it easier for you to kite or escape
- **Action speed** - The target attacks slower, reducing the incoming DPS you take

A 30% Freeze means the target moves 30% slower AND attacks 30% slower. That's a massive combat advantage, especially for ranged builds that rely on maintaining distance.

---

## Tactical Use

Freeze doesn't deal damage, but it enables damage and improves your survivability :

- **Kiting** - Frozen targets can't close the gap on you. [Water](attributes#water) + [Wind](attributes#wind) builds (Freeze + Evasion + Projectile Damage) can kite almost anything.
- **Damage window** - Slower enemy attacks give you more time to land your own hits safely
- **Party support** - Freeze benefits your entire group by reducing the boss's attack frequency

> [!TIP]
> Freeze is the best defensive Ailment. Against dangerous melee mobs, a 30% slow gives you time to reposition, heal, or land more hits. The floor of 5% ensures Freeze is never completely useless, even against bosses.

---

## Freeze vs Other Ailments

| Property | Freeze | [Shock](shock-lightning-damage-amp) |
|----------|--------|-------|
| Effect type | Reduces target speed | Increases target damage taken |
| Magnitude range | 5-30% | 5-50% |
| Scaling | Hit / target max HP | Hit / target max HP |
| Duration | 3.0s | 2.0s |
| Best for | Survivability, kiting | DPS amplification |

Both Freeze and [Shock](shock-lightning-damage-amp) use the same HP-proportional scaling but serve different roles. Freeze is defensive (reduce incoming danger). Shock is offensive (amplify outgoing damage).

---

## Related Pages

- [Water Attribute](attributes#water) - Grants Freeze Chance
- [Void Attribute](attributes#void) - Status Effect Duration extends Freeze
- [Evasion](evasion-dodge) - Another avoidance mechanic that pairs well with Freeze
