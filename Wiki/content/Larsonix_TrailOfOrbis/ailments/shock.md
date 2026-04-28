---
name: Shock
title: "Shock - Lightning Damage Amp"
description: Lightning damage amplification - HP-proportional scaling, 5-50% range
author: Larsonix
sort-index: 2
order: 58
published: true
---

# Shock - Lightning Damage Amp

Shock is [Lightning](attributes#lightning)'s Ailment. It makes the target take **increased damage from all sources** - not just from you. The amplification scales with how hard you hit relative to the target's max health, so it naturally weakens against bosses but still makes an impact.

**Display :** "Shocked" | **Color :** Yellow

---

## The Formula

```
damageIncrease = clamp(hitDamage / targetMaxHealth x 100, 5%, 50%)
```

| Parameter | Value |
|-----------|-------|
| Minimum amplification | 5% |
| Maximum amplification | 50% |
| Base duration | 2 seconds |
| Max stacks | 1 (refreshes, stronger magnitude wins) |
| Base application chance | 10% |
| Damage threshold | Yes (minimum hit damage required) |

---

## How It Works

1. You deal lightning damage to a target
2. Application chance : `10% + shockChance` ([Lightning](attributes#lightning) gives +0.1% per point)
3. If your hit damage meets the target's damage threshold, Shock applies
4. Amplification is calculated from **hit damage / target max HP**
5. The target takes increased damage from **ALL sources** for the duration
6. If Shock is reapplied :
   - Duration refreshes
   - Stronger magnitude replaces weaker

---

## HP-Proportional Scaling

| Your Hit | Target Max HP | Damage Amp |
|---------|---------------|-----------|
| 50 | 100 | **50% (capped)** |
| 50 | 500 | 10% |
| 50 | 1000 | **5% (floor)** |
| 200 | 1000 | 20% |
| 500 | 1000 | **50% (capped)** |

Like [Freeze](freeze-water-slow), Shock uses HP-proportional scaling so it self-balances against different enemy types. Against bosses, the amplification naturally drops to the 5-10% range rather than trivializing the fight.

---

## Why Shock Is Powerful

Shock amplifies damage from **all sources**, not just yours. In a group :

- You Shock a boss for +20% damage taken
- Every other player's attacks deal 20% more to that boss
- Your own attacks also deal 20% more
- All DoTs ([Burn](burn-fire-dot), [Poison](poison-void-stacking-dot)) also deal 20% more while Shock is active

> [!IMPORTANT]
> In group play, Shock is arguably the strongest Ailment in the game. A [Lightning](attributes#lightning) player who keeps Shock active on the boss effectively gives the entire party a damage buff equal to the Shock magnitude.

---

## Duration Scaling

Shock has the **shortest base duration** of all Ailments (2 seconds), making [Void](attributes#void)'s Status Effect Duration especially valuable :

```
finalDuration = 2.0 x (1 + statusEffectDuration / 100)
```

| Void Points | Duration Bonus | Shock Duration |
|------------:|---------------:|---------------:|
| 0 | +0% | 2.0s |
| 50 | +15% | 2.3s |
| 100 | +30% | 2.6s |

> [!WARNING]
> At only 2 seconds base duration, Shock expires fast. Without [Void](attributes#void) investment or rapid reapplication from Lightning's Attack Speed, you'll have significant downtime between Shock windows.

---

## Application Chance

```
finalChance = 10% (base) + shockChance (from Lightning attribute + gear)
```

| Lightning Points | Shock Chance Bonus | Total Shock Chance |
|-----------------:|-------------------:|-------------------:|
| 0 | +0% | 10% |
| 50 | +5% | 15% |
| 100 | +10% | 20% |

---

## The Lightning + Shock Loop

[Lightning](attributes#lightning)'s stats create a self-reinforcing damage cycle with Shock :

1. **Attack Speed** (+0.3% per Lightning point) = more attacks per second
2. More attacks = more Shock application rolls
3. Shock makes every subsequent hit deal more damage
4. More damage = stronger Shock magnitude on the next application

At 100 Lightning, you attack 30% faster with 20% Shock chance. Combined with Shock's short 2-second duration, fast attacks ensure near-constant Shock uptime.

> [!TIP]
> The Lightning + Shock loop is the strongest self-synergy among the 4 ailments. [Fire](attributes#fire) + Lightning combines this loop with Critical Chance + Critical Multiplier for devastating burst damage on Shocked targets.

---

## Shock vs Freeze

| Property | Shock | [Freeze](freeze-water-slow) |
|----------|-------|--------|
| Effect type | Increases target damage taken | Reduces target speed |
| Magnitude range | 5-50% | 5-30% |
| Scaling | Hit / target max HP | Hit / target max HP |
| Duration | 2.0s | 3.0s |
| Best for | DPS amplification | Survivability, kiting |
| Group benefit | Party-wide damage buff | Party-wide safety |

Both use the same HP-proportional scaling but serve opposite roles. Shock maximizes offense. Freeze maximizes defense.

---

## Related Pages

- [Lightning Attribute](attributes#lightning) - Grants Shock Chance and Attack Speed
- [Void Attribute](attributes#void) - Status Effect Duration extends Shock
- [Freeze](freeze-water-slow) - The other HP-proportional ailment
