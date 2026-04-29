---
id: ailments-overview
name: Ailments Overview
title: Status Effects
description: The 4 elemental Ailments - Burn, Freeze, Shock, and Poison
author: Larsonix
sort-index: 0
order: 55
published: true
sub-topics:
  - burn
  - freeze
  - shock
  - poison
---

# Status Effects

4 elemental Ailments layer damage-over-time, crowd control, and damage amplification into your combat. Each one is tied to a specific Element and scales in its own way.

---

## The 4 Ailments

| Ailment | Element | Type | Duration | Max Stacks | Base Chance |
|:--------|:-------:|:-----|:--------:|:----------:|------------:|
| [**Burn**](burn-fire-dot) | [Fire](attributes#fire) | Damage over Time | 4 seconds | 1 (refreshes) | 10% |
| [**Freeze**](freeze-water-slow) | [Water](attributes#water) | Movement Slow | 3 seconds | 1 (refreshes) | 10% |
| [**Shock**](shock-lightning-damage-amp) | [Lightning](attributes#lightning) | Damage Amplification | 2 seconds | 1 (refreshes) | 10% |
| [**Poison**](poison-void-stacking-dot) | [Void](attributes#void) | Stacking *DoT* | 5 seconds | 10 stacks | 10% |

> [!NOTE]
> **[Earth](attributes#earth) and [Wind](attributes#wind) have no Ailments.** They contribute purely through defensive stats (Earth) and evasion/accuracy (Wind). Not every Element needs a status effect.

---

## How Ailments Apply

Every time you deal elemental damage, you have a chance to apply the corresponding Ailment :

```
finalChance = baseChance (10%) + statusEffectChance (from stats)
```

**Bonus chance sources :**
- [Fire](attributes#fire) attribute : +0.1% Ignite Chance per point
- [Water](attributes#water) attribute : +0.1% Freeze Chance per point
- [Lightning](attributes#lightning) attribute : +0.1% Shock Chance per point
- Gear modifiers : +X% ailment chance

At 100 attribute points, your bonus chance is +10%, for a total of 20% application rate.

---

## Ailment Thresholds

[Burn](burn-fire-dot), [Freeze](freeze-water-slow), and [Shock](shock-lightning-damage-amp) have **damage thresholds** on targets. If your hit damage is below the threshold, the ailment won't apply even if the chance roll succeeds. This prevents weak hits from reliably applying powerful ailments on strong enemies.

> [!IMPORTANT]
> **[Poison](poison-void-stacking-dot) has NO damage threshold.** If the chance roll succeeds, Poison always applies regardless of how weak your hit was. This makes Poison uniquely reliable for fast, low-damage attack styles.

---

## Duration Scaling

Ailment duration is modified by the **Status Effect Duration** stat, which comes from [Void](attributes#void) attribute (+0.3% per point) :

```
finalDuration = baseDuration x (1 + statusEffectDuration / 100)
```

At 100 Void points (+30% duration) :

| Ailment | Base Duration | With 100 Void |
|---------|--------------|---------------|
| Burn | 4.0s | 5.2s |
| Freeze | 3.0s | 3.9s |
| Shock | 2.0s | 2.6s |
| Poison | 5.0s | 6.5s per stack |

---

## Ailment Categories

### Damage Ailments (*DoT*)

[**Burn**](burn-fire-dot) and [**Poison**](poison-void-stacking-dot) deal damage over time. Burn is a single instance that refreshes. Poison stacks up to 10 independent instances. Both are amplified by [Void](attributes#void)'s *DoT* Damage % stat.

### Control Ailments (CC)

[**Freeze**](freeze-water-slow) slows movement and action speed by 5-30%, scaling with your hit damage relative to the target's max health. Stronger hits produce stronger slows, but the effect naturally weakens against high-HP targets like bosses.

### Amplification Ailments

[**Shock**](shock-lightning-damage-amp) makes the target take 5-50% increased damage from ALL sources. Like Freeze, the amplification scales with hit damage relative to target max health. In group play, Shock benefits every attacker, not just you.

---

## Damage Type Ailment Map

| Damage Deals | Can Cause |
|-------------|-----------|
| [Fire](attributes#fire) damage | [Burn](burn-fire-dot) |
| [Water](attributes#water) damage | [Freeze](freeze-water-slow) |
| [Lightning](attributes#lightning) damage | [Shock](shock-lightning-damage-amp) |
| [Void](attributes#void) damage | [Poison](poison-void-stacking-dot) |
| Physical damage | No ailment |
| [Earth](attributes#earth) damage | No ailment |
| [Wind](attributes#wind) damage | No ailment |

---

## Ailment Synergies with Attributes

| Attribute | Ailment Connection |
|-----------|-------------------|
| [Fire](attributes#fire) | Grants Burn Damage % and Ignite Chance |
| [Water](attributes#water) | Grants Freeze Chance |
| [Lightning](attributes#lightning) | Grants Shock Chance |
| [Void](attributes#void) | Grants *DoT* Damage % (amplifies Burn AND Poison) and Status Effect Duration (extends ALL ailments) |

> [!TIP]
> **[Void](attributes#void) is the universal Ailment amplifier.** Its *DoT* Damage % boosts both [Burn](burn-fire-dot) and [Poison](poison-void-stacking-dot) damage, and its Status Effect Duration extends all 4 Ailments. A [Fire](attributes#fire) + Void build has stronger, longer-lasting burns than pure Fire.

---

For details on each Ailment's formula and mechanics, see the individual pages below.
