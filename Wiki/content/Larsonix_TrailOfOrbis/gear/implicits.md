---
name: Implicits
title: Implicit Stats
description: Guaranteed base damage on weapons and base defense on armor - level-scaled, quality-independent
author: Larsonix
sort-index: 3
order: 69
published: true
---

# Implicit Stats

Every weapon and armor piece has a guaranteed **implicit** stat that scales with item level. Unlike modifiers, implicits are NOT affected by quality. They depend only on the item's level and type.

---

## Weapon Implicits

All weapons have an implicit that defines their base damage output. The implicit has a **range** `[min-max]` rolled when the item drops, then scaled by item level.

### Damage Types

| Weapon Category | Implicit Damage Type |
|----------------|---------------------|
| Melee One-Handed (Sword, Dagger, Axe, Mace, Claws, Club) | `physical_damage` |
| Melee Two-Handed (Longsword, Battleaxe, Spear) | `physical_damage` |
| Ranged (Shortbow, Crossbow, Blowgun) | `physical_damage` |
| Thrown (Bomb, Dart, Kunai) | *None (excluded from implicits)* |
| Magic (Staff, Wand, Spellbook) | `spell_damage` |

### Display Format

Weapon implicits show as a damage range on your tooltip :

```
[153-198] Physical Damage
```

The rolled value falls within the item's min-max range, scaled by level. Higher-level weapons have proportionally larger ranges.

### Weapon Type Differentiation

Not all weapons have the same implicit power. Larger, slower weapons (like Longswords) have higher implicit ranges than smaller, faster weapons (like Daggers). This creates meaningful trade-offs between weapon types.

### Rerolling Implicits

You can **reroll** weapon implicits using calibration stones. If you rolled near the minimum of your weapon's range, rerolling can significantly increase your base damage without changing any of the item's modifiers.

---

## Armor Implicits

Armor implicits provide a base defensive stat determined by the item's **material** :

| Material | Implicit Stat |
|----------|--------------|
| **Plate** | Armor |
| **Leather** | Evasion |
| **Cloth** | Energy Shield |
| **Wood** | Max Health |
| **Special** | Armor (default) |

Like weapon implicits, armor implicits have a `[min-max]` range that is level-scaled and slot-scaled.

### Slot Scaling

Not all armor slots provide the same amount of implicit defense. Larger armor pieces give more :

| Slot | Relative Power |
|------|---------------|
| Chest | Highest |
| Legs | Second highest |
| Head | Third |
| Hands | Lowest |

This matches what you would expect. A chestplate provides more protection than gloves.

### Display Format

Armor implicits show as a flat value on your tooltip :

```
[72-98] Armor
```

---

## Shield Implicits

Shields have a unique implicit : **Block Chance**. Instead of providing a flat defensive stat like armor, shields give you a percentage chance to block incoming attacks, reducing their damage.

---

## Why Implicits Matter

Implicits are the **quality-independent foundation** of every item. Two important implications :

1. **Weapon choice matters.** Even a [Common](gear-rarities#common) Q1 weapon at high level deals real damage from its implicit alone. The implicit is your damage floor.

2. **Material choice matters for armor.** Plate gives Armor (physical defense), Leather gives Evasion (dodge chance), Cloth gives Energy Shield (absorption), Wood gives raw health. Your armor material should match your attribute build.

> [!TIP]
> When comparing two weapons of the same level, check the implicit range first. A weapon with a high implicit roll provides a strong base that all your percentage modifiers and gem abilities scale from.

---

## Related Pages

- [Ethereal Calibration](consumable-currency#ethereal-calibration) - Rerolls the weapon implicit damage value
- [Equipment System](equipment-system) - Full gear overview including weapon types
- [Damage Pipeline](the-11-step-damage-pipeline) - Implicits feed into Step 1 (Base Damage)
