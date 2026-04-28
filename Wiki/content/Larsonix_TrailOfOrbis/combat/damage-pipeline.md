---
name: Damage Pipeline
title: The 11-Step Damage Pipeline
description: Complete walkthrough of every damage calculation step with exact formulas
author: Larsonix
sort-index: 0
order: 41
published: true
---

# The 11-Step Damage Pipeline

Flat damage goes in early (steps 2-3) so it benefits from everything after. Crits multiply late (step 9) so they amplify your fully-scaled number. True damage lands last (step 11) and bypasses all defenses. That's the whole pipeline in three sentences.

Every attack follows this exact sequence.

---

## Step 1 : Base Damage

Your weapon's raw damage value. For weapons, this is the implicit base damage scaled by item level.

---

## Step 2 : Flat Physical Damage

```
damage = baseDamage + physicalDamage + meleeDamage (if melee attack)
```

Your flat physical damage from gear modifiers (e.g., "+25 Physical Damage") gets added here. For melee attacks, melee-specific flat damage is also included.

> [!TIP]
> Flat damage goes in BEFORE all percentage scaling. A "+25 Physical Damage" modifier on your weapon gets multiplied by every step that follows - making flat damage way more valuable than it looks.

This step is **skipped for mobs** (their damage is already factored into base) and **skipped for *DoT* effects** (ailments use their own calculation).

---

## Step 3 : Flat Elemental Damage

```
For each element:
  elementalDamage[element] += flatElementalDamage[element]
```

Flat elemental damage from your gear (e.g., "+15 Fire Damage") gets added per element. Like flat physical, these values benefit from all percentage scaling that follows.

---

## Step 4 : Damage Conversion

```
For each element with conversion:
  converted = physicalDamage × (conversionPercent / 100)
  elementalDamage[element] += converted
  physicalDamage -= converted
```

You can convert physical damage to elemental types. Total conversion is capped at 100%. If your conversions add up to more than 100%, each is scaled proportionally :

```
If totalConversion > 100%:
  scale = 100 / totalConversion
  effectiveConversion = conversionPercent × scale
```

**Example** : 40% Fire + 40% Lightning + 40% Water = 120% total, so each becomes 33.3%.

> [!NOTE]
> Converted damage benefits from BOTH physical modifiers (step 5) AND elemental modifiers (step 6). This is why conversion builds can be extremely powerful.

---

## Step 5 : % Increased Physical

```
percentBonus = physDmgPercent + attackTypeBonus + damagePercent
physicalDamage = physicalDamage × (1 + percentBonus / 100)
```

Here's what feeds in : `physDmgPercent` is your physical damage percent, `attackTypeBonus` is melee damage percent (for melee) or projectile damage percent (for ranged), and `damagePercent` is your general damage percent stat. All 3 are **added together** first, then applied as one multiplier. This is the "increased" layer - additive within itself.

---

## Step 6 : % Elemental Modifiers

```
For each element:
  damage = damage × (1 + percentIncrease / 100) × (1 + multiplierMore / 100)
```

Each element has its own percentage scaling. Both "increased" (additive) and "more" (multiplicative) modifiers get applied per element.

---

## Step 7 : % More Multipliers (Global)

```
damage = damage × (1 + allDamagePercent / 100) × (1 + damageMultiplier / 100)
```

Global "more" multipliers form a separate multiplicative layer after all percentage increases. These are rare but powerful - they multiply everything that came before.

---

## Step 8 : Conditional Multipliers

Situational bonuses that kick in after general scaling :

| Conditional | When It Applies |
|-------------|----------------|
| Realm Damage Bonus | Inside a realm instance |
| Execute Bonus | Target is at low HP |
| Damage vs Frozen | Target has the [Freeze](freeze-water-slow) ailment |
| Damage vs Shocked | Target has the [Shock](shock-lightning-damage-amp) ailment |
| Damage at Low Life | Your HP is at or below 35% |

These are multiplicative with each other and with all previous steps.

---

## Step 9 : Critical Strike

```
if random(0-100) < critChance:
  finalDamage = damage × (critMultiplier / 100)
```

One roll per attack - if it crits, ALL damage types (physical AND elemental) get multiplied equally.

| Property | Value |
|----------|-------|
| Base crit chance | 5.0% |
| Base crit multiplier | 150% (1.5x damage) |
| Lightning attribute | +0.1% crit chance per point |
| Fire attribute | +0.6% crit multiplier per point |

See [Critical Strikes](critical-strikes) for details.

---

## Step 10 : Defenses

After all your offensive scaling is done, defenses kick in :

**Physical Damage :**
```
armorReduction = armor / (armor + 10 × damage)
armorReduction = min(armorReduction, 0.90)           // 90% cap
physDamage = physDamage × (1 - armorReduction)
```

Then Physical Resistance :
```
physDamage = physDamage × (1 - min(physicalResistance, 75) / 100)
```

**Elemental Damage (per element) :**
```
effectiveResist = max(0, min(resistance, 75) - penetration)    // pen floors at 0%
elemDamage = elemDamage × (1 - effectiveResist / 100)
```

See [Armor](armor-physical-defense) and [Resistances](elemental-resistances) for full details.

---

## Step 11 : True Damage

```
finalDamage = postDefenseDamage + trueDamage
```

True damage gets added **after** all defense calculations. It bypasses armor, resistances, and all other reduction entirely. The Void attribute grants +0.05% of each hit as true damage per point.

> [!IMPORTANT]
> True damage is the only damage that can't be reduced by any defense. It goes in last and bypasses everything.

---

## Recovery Phase

After your damage lands, recovery effects trigger automatically :

| Recovery Type | Effect |
|---------------|--------|
| Life Leech | You heal a % of damage dealt |
| Life Steal | You heal a % of damage dealt |
| Mana Leech | You restore mana as a % of damage dealt |
| Mana Steal | You gain mana FROM the enemy (they lose it) |
| Block Heal | You heal on successful block |
| Thorns Damage | Attacker takes reflected damage (see below) |
| Parry Reflect | Reflects damage back to the attacker |

**Thorns formula :**
```
flatThorns = thornsDamageFlat × (1 + thornsDamagePercent / 100)
reflectedDamage = damageTaken × (reflectPercent / 100)
totalThorns = flatThorns + reflectedDamage
```

Thorns damage is non-lethal - the attacker is kept at a minimum of 1 HP.

---

## Worked Example

**Level 50 player with Fire 40, hitting a level 50 mob :**

Weapon : 80 base damage, +20 flat physical from gear

| Step | What Happens | Value |
|:----:|:-------------|------:|
| 1 - Base | Weapon damage | 80 |
| 2 - Flat | +20 flat physical | 100 |
| 3 - Elemental | No flat elemental | 100 |
| 4 - Conversion | No conversion | 100 |
| 5 - % Physical | +16% phys bonus (Fire 40) | 116 |
| 6 - % Elemental | No elemental mods | 116 |
| 7 - % More | No more multipliers | 116 |
| 8 - Conditionals | No conditionals | 116 |
| 9 - **Crit !** | 174% multiplier | **201.8** |
| 10 - Defenses | 300 armor = 12.9% reduction | **175.8** |
| 11 - True Damage | None | **175.8 final** |

Without the crit, the hit would have been 116 with armor reducing to ~102. The crit bumped your output by roughly 72%.

> [!NOTE]
> The base crit multiplier is 150% (1.5x). At Fire 40, your crit multiplier becomes 150% + (40 × 0.6%) = 174% (1.74x). This is why investing in Fire makes your crits significantly stronger.
