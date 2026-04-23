---
name: Stat Reference
title: Complete Stat Reference
description: All 153 computed stats organized by category, plus the 30 attribute grants
author: Larsonix
sort-index: 10
order: 28
published: true
---

# Complete Stat Reference

30 attribute-derived stats (5 per element, zero overlap) feed into 153 computed fields across resources, offense, defense, movement, elements, and utility. Your final stats are the sum of base values + attribute grants + gear modifiers + skill tree bonuses.

This page is the full reference. For a quick overview, see [The 6 Elements](attributes).

---

## Attribute Grants (30 Stats)

Every Element grants exactly 5 unique stats. No two Elements share a stat - 30 stats total, zero overlap.

| Stat | Fire | Water | Lightning | Earth | Wind | Void |
|:-----|:----:|:-----:|:---------:|:-----:|:----:|:----:|
| Physical Damage % | **+0.4** | - | - | - | - | - |
| Charged Attack Damage % | **+0.3** | - | - | - | - | - |
| Critical Multiplier % | **+0.6** | - | - | - | - | - |
| Burn Damage % | **+0.4** | - | - | - | - | - |
| Ignite Chance % | **+0.1** | - | - | - | - | - |
| Spell Damage % | - | **+0.5** | - | - | - | - |
| Max Mana (flat) | - | **+1.5** | - | - | - | - |
| Energy Shield (flat) | - | **+2.0** | - | - | - | - |
| Mana Regen /s | - | **+0.15** | - | - | - | - |
| Freeze Chance % | - | **+0.1** | - | - | - | - |
| Attack Speed % | - | - | **+0.3** | - | - | - |
| Movement Speed % | - | - | **+0.15** | - | - | - |
| Critical Chance % | - | - | **+0.1** | - | - | - |
| Stamina Regen /s | - | - | **+0.1** | - | - | - |
| Shock Chance % | - | - | **+0.1** | - | - | - |
| Max Health % | - | - | - | **+0.5** | - | - |
| Armor (flat) | - | - | - | **+5.0** | - | - |
| Health Regen /s | - | - | - | **+0.2** | - | - |
| Passive Block Chance % | - | - | - | **+0.2** | - | - |
| Knockback Resistance % | - | - | - | **+0.3** | - | - |
| Evasion (flat) | - | - | - | - | **+5.0** | - |
| Accuracy (flat) | - | - | - | - | **+3.0** | - |
| Projectile Damage % | - | - | - | - | **+0.5** | - |
| Jump Force % | - | - | - | - | **+0.15** | - |
| Projectile Speed % | - | - | - | - | **+0.3** | - |
| Life Steal % | - | - | - | - | - | **+0.1** |
| Hit as True Damage % | - | - | - | - | - | **+0.05** |
| *DoT* Damage % | - | - | - | - | - | **+0.3** |
| Mana on Kill (flat) | - | - | - | - | - | **+0.5** |
| Status Effect Duration % | - | - | - | - | - | **+0.3** |

> [!NOTE]
> All values are **per point allocated**. A player with 50 [Fire](attributes#fire) points gets +20% Physical Damage, +15% Charged Attack Damage, +30% Critical Multiplier, +20% Burn Damage, and +5% Ignite Chance.

---

## Scaling Examples

### 50 Points in One Element

| Element | Key Stat Totals |
|---------|----------------|
| Fire 50 | +20% Phys Dmg, +15% Charged Atk, +30% Crit Multi, +20% Burn Dmg, +5% Ignite |
| Water 50 | +25% Spell Dmg, +75 Mana, +100 Energy Shield, +7.5/s Mana Regen, +5% Freeze |
| Lightning 50 | +15% Atk Speed, +7.5% Move Speed, +5% Crit Chance, +5.0/s Stamina Regen, +5% Shock |
| Earth 50 | +25% Max HP, +250 Armor, +10.0/s HP Regen, +10% Block, +15% KB Resist |
| Wind 50 | +250 Evasion, +150 Accuracy, +25% Proj Dmg, +7.5% Jump Force, +15% Proj Speed |
| Void 50 | +5% Life Steal, +2.5% True Dmg, +15% *DoT* Dmg, +25 Mana/Kill, +15% Duration |

### 100 Points in One Element

| Element | Key Stat Totals |
|---------|----------------|
| Fire 100 | +40% Phys Dmg, +30% Charged Atk, +60% Crit Multi, +40% Burn Dmg, +10% Ignite |
| Water 100 | +50% Spell Dmg, +150 Mana, +200 Energy Shield, +15.0/s Mana Regen, +10% Freeze |
| Lightning 100 | +30% Atk Speed, +15% Move Speed, +10% Crit Chance, +10.0/s Stamina Regen, +10% Shock |
| Earth 100 | +50% Max HP, +500 Armor, +20.0/s HP Regen, +20% Block, +30% KB Resist |
| Wind 100 | +500 Evasion, +300 Accuracy, +50% Proj Dmg, +15% Jump Force, +30% Proj Speed |
| Void 100 | +10% Life Steal, +5% True Dmg, +30% *DoT* Dmg, +50 Mana/Kill, +30% Duration |

### Hybrid Example : 50 Fire + 50 Earth

| Stat | Value |
|------|-------|
| Physical Damage | +20% |
| Charged Attack Damage | +15% |
| Critical Multiplier | +30% |
| Burn Damage | +20% |
| Ignite Chance | +5% |
| Max Health | +25% |
| Armor | +250 |
| Health Regen | +10.0/s |
| Passive Block Chance | +10% |
| Knockback Resistance | +15% |

A bruiser build : hits hard, survives counterattacks.

---

## Computed Stats by Category

The full ComputedStats structure contains 153 fields organized into 6 categories plus 2 utility fields. These are the stats the game engine actually reads to determine combat outcomes.

### Resource Stats (20 fields)

Your health, mana, stamina, oxygen, and signature energy pools.

| Stat | Description | Primary Source |
|------|-------------|---------------|
| maxHealth | Maximum health pool | Base (100) + Earth % |
| healthRegen | HP restored per second | Earth attribute |
| healthRegenPercent | HP regen as % of max | Gear/skills |
| maxMana | Maximum mana pool | Base (0) + Water flat |
| manaRegen | Mana restored per second | Water attribute |
| manaRegenPercent | Mana regen as % of max | Gear/skills |
| maxStamina | Maximum stamina pool | Base (10) |
| staminaRegen | Stamina restored per second | Lightning attribute |
| staminaRegenPercent | Stamina regen as % of max | Gear/skills |
| maxOxygen | Maximum oxygen pool | Base (100) |
| oxygenRegen | Oxygen restored per second | Gear/skills |
| oxygenRegenPercent | Oxygen regen as % of max | Gear/skills |
| maxSignatureEnergy | Maximum signature energy pool | Base (100) |
| signatureEnergyRegen | Signature energy restored per second | Gear/skills |
| signatureEnergyRegenPercent | Signature energy regen as % of max | Gear/skills |
| maxEnergyShield | Energy Shield absorption pool | Water flat |
| energyShieldRegen | Shield restored per second | Gear/skills |
| energyShieldRegenPercent | Shield regen as % of max | Gear/skills |
| manaOnKill | Flat mana restored on kill | Void attribute |

### Offensive Stats (63 fields)

Everything related to dealing damage, landing crits, applying ailments, and attack properties.

| Stat | Description | Primary Source |
|------|-------------|---------------|
| physicalDamagePercent | % increase to physical damage | Fire attribute |
| spellDamagePercent | % increase to spell damage | Water attribute |
| projectileDamagePercent | % increase to projectile damage | Wind attribute |
| chargedAttackDamagePercent | % increase to charged attack damage | Fire attribute |
| dotDamagePercent | % increase to all *DoT* damage | Void attribute |
| criticalChance | Chance to land a critical hit | Base (5%) + Lightning |
| criticalMultiplier | Damage multiplier on crit | Base (150%) + Fire |
| attackSpeed | % increase to attack animation speed | Lightning attribute |
| lifeSteal | % of damage dealt returned as health | Void attribute (clamped 0-50%) |
| hitAsTrueDamage | % of hit bypassing all defenses | Void attribute |
| statusEffectChance | Bonus chance to apply any ailment | Gear/skills |
| statusEffectDuration | % increase to ailment duration | Void attribute |
| burnDamagePercent | % increase to Burn *DoT* damage | Fire attribute |
| igniteChance | Bonus chance to apply Burn | Fire attribute |
| freezeChance | Bonus chance to apply Freeze | Water attribute |
| shockChance | Bonus chance to apply Shock | Lightning attribute |
| projectileSpeed | % increase to projectile velocity | Wind attribute |
| accuracy | Rating determining hit chance vs Evasion | Base (10) + Wind flat |

> [!NOTE]
> The remaining offensive fields (45) cover per-element damage conversions, flat elemental damage, ailment-specific damage stats, and keystone-related combat modifiers from the skill tree.

### Defensive Stats (30 fields)

Damage reduction, avoidance, blocking, and resistances.

| Stat | Description | Primary Source |
|------|-------------|---------------|
| armor | Flat Armor reducing physical damage | Earth flat + gear |
| evasion | Rating determining dodge chance vs Accuracy | Wind flat |
| blockChance | Passive chance to block attacks | Earth attribute |
| blockEffectiveness | % of damage blocked when block triggers | Gear/skills |
| parryWindow | Parry timing window | Gear/skills |
| knockbackResistance | % reduction to knockback distance | Earth attribute |
| fireResistance | % reduction to fire damage taken | Gear/skills |
| waterResistance | % reduction to water damage taken | Gear/skills |
| lightningResistance | % reduction to lightning damage taken | Gear/skills |
| earthResistance | % reduction to earth damage taken | Gear/skills |
| windResistance | % reduction to wind damage taken | Gear/skills |
| voidResistance | % reduction to void damage taken | Gear/skills |
| thorns | Flat damage reflected to attacker | Gear/skills |

> [!NOTE]
> The remaining defensive fields (18) cover per-element penetration, additional resistance modifiers, and keystone-related defensive stats from the skill tree.

### Movement Stats (8 fields)

| Stat | Description | Primary Source |
|------|-------------|---------------|
| movementSpeed | % increase to base movement speed | Lightning attribute |
| jumpForce | % increase to jump height | Wind attribute |
| sprintSpeed | Sprint speed modifier | Gear/skills |
| climbSpeed | Climb speed modifier | Gear/skills |
| walkSpeed | Walk speed modifier | Gear/skills |
| runSpeed | Run speed modifier | Gear/skills |
| crouchSpeed | Crouch speed modifier | Gear/skills |
| swimSpeed | Swim speed modifier | Gear/skills |

### Elemental Stats (30 fields)

Per-element stats across 6 elements. Each element has :

| Per-Element Field | Description |
|-------------------|-------------|
| flatDamage | Flat bonus damage of this element |
| percentDamage | % increase to this element's damage |
| multiplierDamage | Multiplier on this element's damage |
| resistance | % reduction to incoming damage of this element |
| penetration | % of target's resistance ignored |

6 elements x 5 fields = 30, plus 2 additional elemental fields for cross-element interactions.

### Utility Stats (2 fields)

| Stat | Description | Primary Source |
|------|-------------|---------------|
| experienceGainPercent | % bonus to XP gained | Gear/skills |
| manaAsDamageBuffer | % of mana pool used to absorb damage | Skills |

---

## Stat Sources

Your stats are computed by adding 4 layers :

```

finalStat = baseStat + attributeGrants + gearModifiers + skillTreeBonuses

```

| Layer | Description | Example |
|-------|-------------|---------|
| Base stats | Same for all characters | Max Health = 100, Crit Chance = 5%, Accuracy = 10 |
| Attribute grants | Per-point scaling from your allocated attributes | 50 Fire = +20% Physical Damage |
| Gear modifiers | Bonuses from your equipped items | Iron Sword = +15 Physical Damage |
| Skill tree bonuses | Bonuses from your allocated skill nodes | Arms node = +5% Attack Speed |

---

## Ailment Associations

| Element | Ailment | Base Chance | Effect |
|---------|---------|-------------|--------|
| [Fire](attributes#fire) | [Burn](burn-fire-dot) | 10% + Ignite Chance | *DoT* : 50% of hit damage over 4s |
| [Water](attributes#water) | [Freeze](freeze-water-slow) | 10% + Freeze Chance | Slow : 5-30% based on hit/maxHP |
| [Lightning](attributes#lightning) | [Shock](shock-lightning-damage-amp) | 10% + Shock Chance | Amp : 5-50% increased damage taken |
| [Earth](attributes#earth) | - | - | No ailment |
| [Wind](attributes#wind) | - | - | No ailment |
| [Void](attributes#void) | [Poison](poison-void-stacking-dot) | 10% + base chance | Stacking *DoT* : 30% over 5s, max 10 stacks |
