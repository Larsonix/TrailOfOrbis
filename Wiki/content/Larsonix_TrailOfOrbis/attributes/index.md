---
name: Attributes
title: Attributes
description: 6 elemental Attributes - Fire, Water, Lightning, Earth, Wind, and Void - each granting 5 unique stats
author: Larsonix
sort-index: 0
order: 20
published: true
sub-topics: []
---

# Attributes

Trail of Orbis has no classes. Attributes are one of the systems that shape your build. Each point you put into an Element gives you 5 unique stats. No two Elements share a single stat. 30 attribute-derived stats total, zero overlap.

You start with **1 attribute point** and gain **1 more per level**. Allocation is free and respec costs nothing.

---

## All Elements

| Element | Color | Stats Per Point | Ailment |
|:--------|:-----:|:----------------|:-------:|
| [**Fire**](#fire) | #FF7755 | +0.4% Physical Damage, +0.3% Charged Attack Damage, +0.6% Crit Multiplier, +0.4% Burn Damage, +0.1% Ignite Chance | [Burn](burn-fire-dot) |
| [**Water**](#water) | #55CCEE | +0.5% Spell Damage, +1.5 Max Mana, +2.0 Energy Shield, +0.15/s Mana Regen, +0.1% Freeze Chance | [Freeze](freeze-water-slow) |
| [**Lightning**](#lightning) | #FFEE55 | +0.3% Attack Speed, +0.15% Move Speed, +0.1% Crit Chance, +0.1/s Stamina Regen, +0.1% Shock Chance | [Shock](shock-lightning-damage-amp) |
| [**Earth**](#earth) | #DDAA55 | +0.5% Max Health, +5.0 Armor, +0.2/s Health Regen, +0.2% Passive Block Chance, +0.3% Knockback Resistance | - |
| [**Wind**](#wind) | #77DD77 | +5.0 Evasion, +3.0 Accuracy, +0.5% Projectile Damage, +0.15% Jump Force, +0.3% Projectile Speed | - |
| [**Void**](#void) | #BB77DD | +0.1% Life Steal, +0.05% True Damage, +0.3% *DoT* Damage, +0.5 Mana on Kill, +0.3% Effect Duration | [Poison](poison-void-stacking-dot) |

---

## How Attributes Work

### Allocation

- Open `/stats` and go to the Attributes tab, or type `/attr` directly
- Click an Element to spend 1 point
- Each point immediately gives you 5 stats
- No cap per Element - you can dump all your points into one

### Respec

- Type `/too attr reset` to refund ALL points instantly
- No cost, no cooldown, no limit
- Experiment freely. There is zero penalty for trying different builds.

### Stat Computation

Your final stats come from 4 sources, added together :

1. **Base stats** - every character starts with the same base values
2. **Attribute grants** - from the points you allocate (this page)
3. **Gear modifiers** - from your equipped items
4. **Skill tree bonuses** - from allocated skill tree nodes

### Base Stats

Every character starts with these vanilla resource pools :

| Resource | Base Value |
|----------|-----------|
| Max Health | 100 |
| Max Mana | 0 |
| Max Stamina | 10 |
| Max Oxygen | 100 |
| Max Signature Energy | 100 |

> [!NOTE]
> Max Mana starts at 0. You need Water attribute points (+1.5 per point) or gear with mana bonuses to have any mana at all.

---

## Ailment Connections

4 of the 6 Elements have associated [Ailments](status-effects) :

| Element | Ailment | Effect |
|---------|---------|--------|
| Fire | [Burn](burn-fire-dot) | Damage over time |
| Water | [Freeze](freeze-water-slow) | Movement/action speed slow |
| Lightning | [Shock](shock-lightning-damage-amp) | Target takes increased damage |
| Void | [Poison](poison-void-stacking-dot) | Stacking damage over time |
| Earth | - | No ailment |
| Wind | - | No ailment |

---

## Lore

The 6 Elements are rooted in Hytale's canonical lore :

- **Water**, **Earth**, and **Wind** are Gaia's Elements - tied to nature and the world
- **Void** is Varyn's Element - tied to corruption and entropy
- **Fire** and **Lightning** are primal forces - independent of either faction

---

For the complete stat table covering all 246 computed stats, see [Stat Reference](complete-stat-reference).

---

## Fire

Fire is pure offense. Every point cranks up your raw damage through physical scaling, Charged Attack bonuses, and critical hit damage. You also empower the [Burn](burn-fire-dot) ailment, making your hits deal damage over time.

**Color :** #FF7755

### Stats Per Point

| Stat | Per Point | At 10 Points | At 50 Points | At 100 Points |
|:-----|----------:|-------------:|-------------:|--------------:|
| Physical Damage | +0.4% | +4% | +20% | +40% |
| Charged Attack Damage | +0.3% | +3% | +15% | +30% |
| Critical Multiplier | +0.6% | +6% | +30% | +60% |
| Burn Damage | +0.4% | +4% | +20% | +40% |
| Ignite Chance | +0.1% | +1% | +5% | +10% |

### What These Stats Do

**Physical Damage %** - Multiplies all physical damage you deal. Melee weapons, physical projectiles, anything tagged as physical. This is your main damage scaling stat.

**Charged Attack Damage %** - Bonus damage specifically for charged/heavy attacks. Hytale's combat has light, heavy, and signature attack animations. This boosts the heavy ones.

**Critical Multiplier %** - Makes your crits hit harder. The base Critical Multiplier is 150% (1.5x damage). At 100 Fire points, your crits deal 210% damage instead.

> [!IMPORTANT]
> Critical Multiplier increases the multiplier, not the bonus. Base 150% + 60% from 100 Fire = 210% total crit damage. To actually land crits, you need Critical Chance, which comes from [Lightning](#lightning) (+0.1% per point, on top of the base 5%).

**Burn Damage %** - Increases the damage of your [Burn](burn-fire-dot) ailment. Burn deals 50% of your fire hit damage as *DoT* over 4 seconds. This stat makes that *DoT* hit harder.

**Ignite Chance %** - Additional chance to apply [Burn](burn-fire-dot) on hit, added to the base 10% chance. At 100 Fire, your total Ignite Chance is 20%.

---

## Water

Water is the magic Element. It's the only source of Max Mana from attributes (your base mana is 0), it gives you Energy Shield as a secondary health buffer, and it powers spell-based builds. Water's ailment, [Freeze](freeze-water-slow), slows enemy movement and actions.

**Color :** #55CCEE

### Stats Per Point

| Stat | Per Point | At 10 Points | At 50 Points | At 100 Points |
|:-----|----------:|-------------:|-------------:|--------------:|
| Spell Damage | +0.5% | +5% | +25% | +50% |
| Max Mana | +1.5 flat | +15 | +75 | +150 |
| Energy Shield | +2.0 flat | +20 | +100 | +200 |
| Mana Regen | +0.15/s | +1.5/s | +7.5/s | +15.0/s |
| Freeze Chance | +0.1% | +1% | +5% | +10% |

### What These Stats Do

**Spell Damage %** - Multiplies all spell/magic damage you deal. Your primary scaling stat for magic weapons and abilities.

**Max Mana (flat)** - Added directly to your mana pool. Since base Max Mana is 0, Water is essential for any mana-using build. At 50 Water you have 75 mana. At 100 Water you have 150.

> [!IMPORTANT]
> Without Water points or mana gear, you have 0 mana. Any spell or ability that costs mana is completely unusable until you invest in Water or equip mana-granting gear.

**Energy Shield (flat)** - A secondary health buffer that absorbs damage before your actual health. It sits on top of your base stats and takes hits first.

**Mana Regen /s** - Passive mana regeneration per second. At 50 Water, you regenerate 7.5 mana per second, letting you sustain spell use in prolonged fights.

**Freeze Chance %** - Additional chance to apply [Freeze](freeze-water-slow) on water-damage hits, added to the base 10% chance. At 100 Water, your total Freeze Chance is 20%.

---

## Lightning

Lightning is the speed Element. You attack faster, move faster, and land critical hits more often. Lightning's ailment, [Shock](shock-lightning-damage-amp), makes targets take increased damage from all sources - a force multiplier for your entire party.

**Color :** #FFEE55

### Stats Per Point

| Stat | Per Point | At 10 Points | At 50 Points | At 100 Points |
|:-----|----------:|-------------:|-------------:|--------------:|
| Attack Speed | +0.3% | +3% | +15% | +30% |
| Movement Speed | +0.15% | +1.5% | +7.5% | +15% |
| Critical Chance | +0.1% | +1% | +5% | +10% |
| Stamina Regen | +0.1/s | +1.0/s | +5.0/s | +10.0/s |
| Shock Chance | +0.1% | +1% | +5% | +10% |

### What These Stats Do

**Attack Speed %** - Makes your attack animations faster. More hits per second means more damage, more ailment procs, and more Life Steal ticks. This is a universal DPS multiplier.

**Movement Speed %** - You move faster. Critical for kiting, dodging telegraphed attacks, and closing gaps on ranged enemies.

**Critical Chance %** - Your probability of landing a critical hit. The base Critical Chance is 5%. At 100 Lightning, you're at 15%. Crits deal damage based on your Critical Multiplier (base 150%, increased by [Fire](#fire)).

> [!NOTE]
> Critical Chance and Critical Multiplier live on separate Elements by design. Lightning gives you the chance to crit. Fire makes those crits hit harder. A Fire + Lightning hybrid maximizes critical damage output.

**Stamina Regen /s** - Faster passive stamina regeneration. Base Max Stamina is 10. Faster regen means more dodges, sprints, and stamina-consuming abilities.

**Shock Chance %** - Additional chance to apply [Shock](shock-lightning-damage-amp) on lightning-damage hits, added to the base 10% chance. At 100 Lightning, your total Shock Chance is 20%.

---

## Earth

Earth is pure defense. Every point makes you harder to kill through health, Armor, regeneration, and passive blocking. Earth has no associated ailment - its entire power budget goes into keeping you alive.

**Color :** #DDAA55

### Stats Per Point

| Stat | Per Point | At 10 Points | At 50 Points | At 100 Points |
|:-----|----------:|-------------:|-------------:|--------------:|
| Max Health | +0.5% | +5% | +25% | +50% |
| Armor | +5.0 flat | +50 | +250 | +500 |
| Health Regen | +0.2/s | +2.0/s | +10.0/s | +20.0/s |
| Passive Block Chance | +0.2% | +2% | +10% | +20% |
| Knockback Resistance | +0.3% | +3% | +15% | +30% |

### What These Stats Do

**Max Health %** - Percentage increase to your base health pool. Base Max Health is 100. At 50 Earth, you have 125 HP. At 100 Earth, you have 150 HP. This also increases the effective value of all healing and Health Regen.

**Armor (flat)** - Flat Armor added on top of your equipment armor. Armor reduces incoming physical damage. At 100 Earth, you have +500 Armor from attributes alone, before any gear.

**Health Regen /s** - Passive health regeneration per second. At 50 Earth, you regenerate 10 HP/s. This is constant, in and out of combat - a slow but reliable sustain mechanic.

**Passive Block Chance %** - Chance to automatically block incoming attacks without actively raising your shield. At 100 Earth, you passively block 20% of attacks. This stacks with active blocking from the [blocking system](blocking).

> [!NOTE]
> Passive Block Chance triggers automatically. You don't need to hold a shield or press the block button. It's a free layer of defense on every incoming hit.

**Knockback Resistance %** - Reduces how far enemies knock you back. At 100 Earth, you resist 30% of knockback. This keeps you in melee range and prevents enemies from disrupting your positioning.

---

## Wind

Wind is the ranged and evasion Element. Your projectiles hit harder and faster, you dodge attacks entirely, and your own attacks always land through Accuracy. Wind has no associated ailment - its power is in avoiding damage and dealing it from range.

**Color :** #77DD77

### Stats Per Point

| Stat | Per Point | At 10 Points | At 50 Points | At 100 Points |
|:-----|----------:|-------------:|-------------:|--------------:|
| Evasion | +5.0 flat | +50 | +250 | +500 |
| Accuracy | +3.0 flat | +30 | +150 | +300 |
| Projectile Damage | +0.5% | +5% | +25% | +50% |
| Jump Force | +0.15% | +1.5% | +7.5% | +15% |
| Projectile Speed | +0.3% | +3% | +15% | +30% |

### What These Stats Do

**Evasion (flat)** - Your chance to dodge incoming attacks entirely. Evasion competes against the attacker's Accuracy to determine hit chance. Higher Evasion means more attacks whiff completely. See [Evasion](evasion-dodge) for the formula.

**Accuracy (flat)** - Your chance to hit enemies. Base Accuracy is 10. Accuracy competes against the target's Evasion to determine if your attack connects. At 100 Wind, your Accuracy is 310 (10 base + 300 from Wind), making it very hard for enemies to dodge you.

> [!IMPORTANT]
> Evasion and Accuracy form a push-pull system. A high-Evasion target can be countered by a high-Accuracy attacker. Wind gives you both - making you hard to hit while ensuring your own attacks land.

**Projectile Damage %** - Multiplies all projectile damage you deal. Arrows, thrown weapons, any ranged ability tagged as projectile. Your primary damage scaling stat for ranged builds.

**Jump Force %** - You jump higher. At 100 Wind, you jump 15% higher. Useful for vertical mobility, reaching ledges, and aerial combat positioning.

**Projectile Speed %** - Your projectiles travel faster. Faster projectiles are harder to dodge and reach targets sooner. At 100 Wind, your arrows travel 30% faster.

---

## Void

Void is sustain and amplification. You heal through damage dealt, bypass defenses with True Damage, and amplify all damage-over-time effects. Void is also the universal ailment amplifier - its Status Effect Duration extends all 4 ailments, not just its own.

**Color :** #BB77DD

### Stats Per Point

| Stat | Per Point | At 10 Points | At 50 Points | At 100 Points |
|:-----|----------:|-------------:|-------------:|--------------:|
| Life Steal | +0.1% | +1% | +5% | +10% |
| Hit as True Damage | +0.05% | +0.5% | +2.5% | +5% |
| *DoT* Damage | +0.3% | +3% | +15% | +30% |
| Mana on Kill | +0.5 flat | +5 | +25 | +50 |
| Status Effect Duration | +0.3% | +3% | +15% | +30% |

### What These Stats Do

**Life Steal %** - A percentage of the damage you deal comes back to you as healing. Works on all damage types. Clamped between 0% and 50% (you can never heal more than half the damage you deal).

> [!WARNING]
> Life Steal caps at 50%, no matter how many points and gear bonuses you stack. At 100 Void you have 10% from attributes alone - the remaining 40% has to come from gear if you want to hit the cap.

**Hit as True Damage %** - A percentage of every hit becomes True Damage, which ignores all Armor and resistances. At 100 Void, 5% of every hit bypasses all defenses. Small per-hit, but it adds up - especially with fast attack speeds from [Lightning](#lightning).

**DoT Damage %** - Amplifies all damage-over-time effects you apply. This includes [Burn](burn-fire-dot) (from Fire), [Poison](poison-void-stacking-dot) (from Void), and any other *DoT* sources. Void is the universal *DoT* amplifier.

> [!TIP]
> *DoT* Damage amplifies both [Burn](burn-fire-dot) and [Poison](poison-void-stacking-dot). A Fire + Void hybrid gets Burn *DoT* from Fire AND *DoT* amplification from Void - stronger burns that last longer (via Status Effect Duration). This is the highest sustained-damage combo in the game.

**Mana on Kill (flat)** - Flat mana restored when you kill an enemy. At 100 Void, each kill returns 50 mana. This gives you mana sustain without investing in Water, especially in mob-dense content like realms.

**Status Effect Duration %** - Extends the duration of ALL ailments you apply ([Burn](burn-fire-dot), [Freeze](freeze-water-slow), [Shock](shock-lightning-damage-amp), and [Poison](poison-void-stacking-dot)). At 100 Void :

| Ailment | Base Duration | With 100 Void (+30%) |
|---------|--------------|---------------------|
| Burn | 4.0s | 5.2s |
| Freeze | 3.0s | 3.9s |
| Shock | 2.0s | 2.6s |
| Poison | 5.0s | 6.5s per stack |

---

## Related Pages

- [Stat Reference](complete-stat-reference) - All 246 computed stats
- [Status Effects](status-effects) - The 4 elemental Ailments
- [Combat System](combat-system) - How attributes feed the damage pipeline
