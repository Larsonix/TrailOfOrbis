---
name: Tooltips
title: Reading Item Tooltips
description: How to interpret gear tooltips - rarity badges, quality ratings, stat modifiers, and attribute requirements
author: Larsonix
sort-index: 5
order: 71
published: true
---

# Reading Item Tooltips

Every RPG gear piece shows a custom tooltip with all its stats. Learning to read tooltips quickly is essential for evaluating drops and making gear decisions.

---

## Tooltip Sections

A gear tooltip contains these sections, from top to bottom :

### 1. Rarity Badge

A colored badge showing the item's rarity tier. The badge color matches the rarity :

| Rarity | Badge Color |
|--------|------------|
| [Common](gear-rarities#common) | Silver-gray (#C9D2DD) |
| [Uncommon](gear-rarities#uncommon) | Green (#3E9049) |
| [Rare](gear-rarities#rare) | Blue (#2770B7) |
| [Epic](gear-rarities#epic) | Purple (#8B339E) |
| [Legendary](gear-rarities#legendary) | Gold (#BB8A2C) |
| [Mythic](gear-rarities#mythic) | Orange-red (#FF4500) |
| [Unique](gear-rarities#unique) | Copper-brown (#AF6025) |

### 2. Item Level

Displayed as a level number. This determines implicit scaling and modifier value scaling. Higher item level means stronger base stats.

### 3. Quality Rating

Shows your item's quality value and its tier. Quality is displayed with tier-specific colors so you can quickly gauge whether the quality is poor, average, or exceptional.

| Quality Range | Assessment |
|--------------|------------|
| 1-25 | Poor quality |
| 26-49 | Below average |
| 50 | Baseline (neutral) |
| 51-75 | Above average |
| 76-100 | Excellent |
| 101 | Perfect (0.5% natural drop chance, or guaranteed via Gaia's Perfection) |

### 4. Implicit Line

For weapons :
```
[153-198] Physical Damage
```

For armor :
```
[72-98] Armor
```

The implicit is the guaranteed base stat, scaled by item level. NOT affected by quality.

### 5. Stat Modifiers

Each modifier appears on its own line :
```
+35 Physical Damage          (Prefix - offense)
+12% Physical Damage         (Prefix - offense)
+180 Armor                   (Suffix - defense)
+45 Max Health               (Suffix - defense)
```

Modifier values shown on tooltips are the final values AFTER quality multiplication. Color coding :
- **Green** values indicate positive bonuses
- **Red** values indicate negative effects

### 6. Attribute Requirements

If the item has attribute requirements, they appear color-coded :
- **Green** text : you meet this requirement
- **Red** text : you don't meet this requirement and can't equip the item

---

## Quick Evaluation Guide

When a piece of gear drops, here is what to check in order :

1. **Rarity** - Sets the power ceiling. [Epic](gear-rarities#epic)+ is where gear starts getting exciting.
2. **Quality** - Above 50 is good, above 75 is excellent. Below 25 is disappointing.
3. **Modifier types** - Do the stats match your build ? Physical Damage prefixes on a melee weapon are great. Spell damage on a melee weapon is wasted.
4. **Modifier count** - More modifiers = more total power. [Mythic](gear-rarities#mythic) and [Unique](gear-rarities#unique) always have the maximum 6.

> [!TIP]
> **Quick power estimate** : Multiply the rarity stat multiplier by quality effect. A Rare (0.8x) at high quality is roughly equivalent to an Epic (1.2x) at low quality in per-modifier power. But the Epic still has an extra modifier slot.

---

## Related Pages

- [Quality System](quality-system) - What the quality rating on tooltips means
- [Gear Rarities](gear-rarities) - What the colored rarity badge means
- [Gear Modifiers](modifiers) - The stat lines shown on your tooltip
- [Implicit Stats](implicit-stats) - The guaranteed base stat line on every item

