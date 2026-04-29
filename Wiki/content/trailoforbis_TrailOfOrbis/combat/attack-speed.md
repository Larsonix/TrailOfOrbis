---
name: Attack Speed
title: Attack Speed
description: How attack speed scales weapon cooldowns - formula, bounds, and the Lightning connection
author: Larsonix
sort-index: 7
order: 48
published: true
---

# Attack Speed

Attack speed reduces the cooldown between your attacks. Faster attacks mean more hits per second, more chances to crit, more ailment applications, and more recovery procs.

---

## The Formula

```
scaledCooldown = baseCooldown / (1 + attackSpeedPercent / 100)
```

| Parameter | Value |
|-----------|-------|
| Maximum bonus | +500% (6x attack speed) |
| Minimum bonus | -75% (4x slower) |
| Minimum cooldown floor | 0.05 seconds (50ms) |

### Speed Examples

| Attack Speed % | Cooldown Multiplier | Effect |
|---------------:|:-------------------:|:-------|
| -75% (min) | 4.0x | 4x slower than base |
| 0% (base) | 1.0x | Normal speed |
| +50% | 0.67x | 50% faster |
| +100% | 0.50x | Double speed |
| +200% | 0.33x | Triple speed |
| +500% (cap) | 0.17x | 6x speed |

> [!NOTE]
> The formula uses **division**, not subtraction. +100% attack speed doesn't halve your cooldown by removing 100% - it divides by 2.0 (1 + 100/100). This means diminishing returns : going from 0% to +100% doubles your speed, but going from +100% to +200% only bumps it by 50%.

---

## Practical Impact

A weapon with a 1.0 second base cooldown :

| Attack Speed | Cooldown | Attacks per Second |
|-------------:|---------:|-------------------:|
| 0% | 1.00s | 1.0 |
| +30% | 0.77s | 1.3 |
| +100% | 0.50s | 2.0 |
| +200% | 0.33s | 3.0 |
| +500% | 0.17s | 6.0 |

A weapon with a 0.5 second base cooldown would reach the 50ms floor at lower attack speed values.

---

## Why Attack Speed Matters

Attack speed is a **multiplier on everything that procs per hit** :

- **Critical strikes** - More attacks = more crit rolls per second
- **Ailment application** - More attacks = more chances to apply [Burn](burn-fire-dot), [Freeze](freeze-water-slow), [Shock](shock-lightning-damage-amp), [Poison](poison-void-stacking-dot)
- **Life leech / life steal** - More attacks = more healing per second
- **Mana recovery** - More attacks = more mana leech and mana steal procs
- **DPS** - Even without other stats, faster attacks = proportionally more damage per second

> [!TIP]
> Attack speed is most valuable when you have strong per-hit effects. If each hit has a 10% chance to [Shock](shock-lightning-damage-amp) and you attack twice as fast, you apply Shock twice as often. [Lightning](attributes#lightning) (attack speed + crit chance) is designed around this synergy.

---

## The 50ms Floor

No matter how much attack speed you stack, your attacks can never happen faster than every 50 milliseconds (20 attacks per second). This prevents input-speed exploits and keeps combat visually readable.

```
finalCooldown = max(scaledCooldown, 0.05)
```

In practice, only very fast base weapons with extreme attack speed bonuses will hit this floor. For most builds, the +500% cap is the limiting factor, not the 50ms floor.

---

## Negative Attack Speed

Your attack speed can go negative (minimum -75%), making your attacks 4x slower than base :

```
At -75%: cooldown = baseCooldown / (1 + (-75) / 100) = baseCooldown / 0.25 = 4x slower
```

Negative attack speed can come from debuffs, realm modifiers, or certain ailment effects. A 1.0 second weapon at -75% attack speed has a 4.0 second cooldown between attacks.

> [!WARNING]
> Watch for realm modifiers that reduce your attack speed. A -50% attack speed modifier doubles your cooldown, which halves your effective DPS, crit rate, and ailment application rate. Check realm info before entering.

---

## Related Pages

- [Lightning Attribute](attributes#lightning) - Primary source of Attack Speed from attributes
- [Critical Strikes](critical-strikes) - Faster attacks mean more crit rolls per second
- [Poison](poison-void-stacking-dot) - Attack speed directly accelerates Poison stack building
- [Status Effects](status-effects) - All ailments benefit from more hits per second
