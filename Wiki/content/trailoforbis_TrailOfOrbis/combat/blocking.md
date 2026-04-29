---
name: Blocking
title: Blocking
description: Active shield blocking, passive block chance, damage reduction, and stamina costs
author: Larsonix
sort-index: 6
order: 47
published: true
---

# Blocking

Blocking is your last line of defense in the [avoidance chain](evasion-dodge). Unlike dodge and evasion which fully avoid attacks, blocking **reduces** incoming damage while consuming stamina. Two types : active (requires holding a shield) and passive (random proc).

---

## Active Block (Shield)

Active blocking triggers when you're holding a shield and an attack connects :

```
if holdingShield AND random(0-100) < blockChance:
  damageMultiplier = 1 - min(damageReduction, 100) / 100
  blockedDamage = incomingDamage × damageMultiplier
  staminaCost = baseCost × (1 - min(staminaDrainReduction, 75) / 100)
```

| Component | Range | Source |
|-----------|-------|--------|
| Block Chance | 0-100% | Shield stats + [Earth](attributes#earth) attribute |
| Damage Reduction | 0-100% | Shield's block damage reduction stat |
| Stamina Cost | Base cost from shield | Reduced by stamina drain reduction |
| Stamina Drain Reduction | 0-75% (capped) | Gear modifiers |

> [!WARNING]
> Blocking costs stamina. If you run out, you can't sustain blocking. [Earth](attributes#earth) attribute's stamina drain reduction (capped at 75%) helps you keep blocking through long fights.

---

## Passive Block (No Shield)

Passive blocking is a random proc that doesn't require holding a shield :

```
if random(0-100) < passiveBlockChance:
  partial damage reduction applied
```

Passive block doesn't consume stamina and doesn't require any specific weapon or stance. It's a free defensive bonus for [Earth](attributes#earth)-invested builds.

---

## Stamina Drain Reduction

Blocking drains your stamina. The stamina drain reduction stat reduces how much each block costs :

```
effectiveCost = baseCost × (1 - min(staminaDrainReduction, 75) / 100)
```

| Stamina Reduction | Effective Cost |
|-------------------|---------------|
| 0% | 100% (full cost) |
| 25% | 75% of base cost |
| 50% | 50% of base cost |
| 75% (cap) | 25% of base cost |

> [!IMPORTANT]
> Stamina drain reduction is **capped at 75%**. You always pay at least 25% of the base stamina cost per block. This prevents infinite blocking.

---

## Block Heal

When you successfully block an attack, the **block heal** recovery effect can trigger. This heals you on every successful block, making shield builds self-sustaining against sustained damage.

Block heal is part of the recovery phase that runs after the [Damage Pipeline](the-11-step-damage-pipeline) resolves.

---

## Block in the Avoidance Chain

Block is checked **last** in the avoidance chain (after dodge and evasion). Here's what that means :

1. If you dodge, block is never checked
2. If evasion avoids the attack, block is never checked
3. If dodge and evasion both fail, active block is checked (if holding shield)
4. If active block fails or no shield, passive block is checked

Block is your safety net when evasion fails. [Earth](attributes#earth) + [Wind](attributes#wind) builds get both evasion (Wind) and passive block (Earth) for double-layered avoidance.

---

## Block Result

A successful block produces a result that includes :

| Data | Description |
|------|-------------|
| Damage reduced | How much damage the block absorbed |
| Stamina spent | How much stamina the block consumed |
| Block type | Active (shield) or passive (proc) |

You see "Blocked" as floating text when a block triggers.

> [!TIP]
> Shield builds pair naturally with [Earth](attributes#earth) attribute. Earth increases your passive block chance, provides stamina drain reduction, and gives armor as a backup for hits that get through. [Wind](attributes#wind) + Earth hybrids layer evasion on top of blocking for maximum avoidance.

---

## Related Pages

- [Evasion & Dodge](evasion-dodge) - Prior checks in the avoidance chain before blocking
- [Earth Attribute](attributes#earth) - Grants Passive Block Chance and Armor for layered defense
- [Combat System](combat-system) - Full avoidance chain and pipeline overview
