---
name: Gem System
title: Gem System
description: Active and support gems - abilities socketed into gear with tag-based compatibility
author: Larsonix
sort-index: 0
order: 75
published: true
---

# Gem System

> [!WARNING]
> **Work in Progress.** The Gem system is designed but not yet implemented in-game. The information below describes the planned mechanics.

Gems are droppable items that define your combat abilities. **Active gems** give you the abilities themselves : fireballs, cleaves, and other skills. **Support gems** modify active gems with additional effects like chaining, area increases, or damage multipliers. Both are socketed into your gear.

---

## Two Gem Types

| Type | Role | Socket Slot |
|------|------|------------|
| **Active** | Defines an ability (damage, cost, cooldown, element) | Slot 0 (one per weapon) |
| **Support** | Modifies the active gem (adds behaviors, changes stats) | Slots 1+ (multiple allowed) |

Every weapon has exactly **1 active gem slot** and **multiple support gem slots**. Your active gem determines what ability you use. Support gems enhance how that ability works.

---

## Gem Properties

### Active Gem Properties

| Property | Description |
|----------|-------------|
| `id` | Unique identifier (e.g., `active_fireball`) |
| `name` | Display name |
| `description` | What the gem does |
| `tags` | Hierarchical tags for support gem compatibility |
| `weaponCategories` | Which weapon types can use this gem |
| `gearSlots` | Which gear slots accept this gem |
| `damage` | Base percent of weapon damage, element, AoE radius |
| `cost` | Resource cost (stamina, mana, or per-second variants) |
| `cooldown` | Seconds between uses |
| `castType` | Instant, Quick Cast, Committed, or Channeled |
| `ailment` | Optional base ailment chance |
| `qualityBonuses` | Stats that scale with gem quality |
| `visuals` | Projectile models, sounds, particles |

### Support Gem Properties

| Property | Description |
|----------|-------------|
| `id` | Unique identifier |
| `name` | Display name |
| `description` | What the support does |
| `requiresTags` | List of active gem tags needed for compatibility |
| `modifications` | List of stat/behavior changes applied to the active gem |
| `qualityBonuses` | Stats that scale with gem quality |

---

## Tag-Based Compatibility

Support gems declare which **tags** they require on the active gem. A support that requires `[Projectile]` only works with active gems that have the Projectile tag. This prevents nonsensical combinations (a projectile-chaining support on a melee cone attack).

Tags are hierarchical, covering categories like :
- Element ([Fire](attributes#fire), Physical, [Lightning](attributes#lightning), etc.)
- Delivery (Melee, Ranged, Projectile, AoE, Cone)
- Timing (Instant, Channel)
- Behavior (Strike, Hit, Crit, Applier)

---

## Active Gems

Active gems are your combat abilities. Each one defines what happens when you use a skill : the damage it deals, its element, how it's delivered, what it costs, and how often you can use it.

### Cast Types

| Cast Type | Behavior |
|-----------|----------|
| **Instant** | Activates immediately on use |
| **Quick Cast** | Fast activation with brief commitment |
| **Committed** | Full animation commitment, cannot cancel |
| **Channeled** | Sustained effect that continues while channeling |

### Cost Types

| Cost Type | Description |
|-----------|-------------|
| **Stamina** | Physical resource, regenerates quickly |
| **Mana** | Magical resource, regenerates based on attributes |
| **Per-second variants** | Sustained cost for channeled abilities |

### Damage Formula

All gem damage is expressed as a **percentage of weapon damage** :

```

abilityDamage = weaponDamage x (basePercent / 100)

```

Your weapon's implicit and modifier stats directly power your gem abilities. A stronger weapon makes ALL your active gems proportionally stronger.

### Weapon Restrictions

Each active gem specifies which **weapon categories** can use it :

| Category | Weapons |
|----------|---------|
| Melee One-Handed | Sword, Dagger, Axe, Mace, Claws, Club |
| Melee Two-Handed | Longsword, Battleaxe, Spear |
| Ranged | Shortbow, Crossbow, Blowgun |
| Thrown | Bomb, Dart, Kunai |
| Magic | Staff, Wand, Spellbook |

### Ailment Integration

Active gems can have a **base ailment chance** independent of your attribute-based ailment chance. A gem with 60% [Burn](burn-fire-dot) chance adds that 60% on top of any Burn chance from your [Fire](attributes#fire) attribute.

---

## Support Gems

Support gems modify how your active gems behave. They don't provide abilities on their own. Instead, they enhance the active gem they are linked to by adding behaviors, multiplying stats, or changing mechanics.

### How Support Gems Work

A support gem declares :
1. **Required tags** - Which active gem tags it needs to be compatible
2. **Modifications** - What stat or behavior changes it applies

When socketed alongside a compatible active gem, the support gem's modifications apply automatically. Multiple support gems stack their effects on the same active gem.

### Modification Types

| Modification Type | Example |
|-------------------|---------|
| **Add behavior** | Add projectile chaining (bounce to additional targets) |
| **Multiply stat** | Reduce primary damage by 15% (trade-off for added behavior) |
| **Change value** | Increase AoE radius, reduce cooldown |
| **Add effect** | Add elemental conversion, extra ailment chance |

> [!IMPORTANT]
> Always check tag compatibility before socketing. A support gem that requires `[Projectile]` won't work with a melee active gem, even if both are socketed in the same weapon. The socket UI shows compatibility status.

---

## Socket Mechanics

| Action | How |
|--------|-----|
| **Socket a gem** | Use the Gem Socket UI page |
| **Quick socket** | Drop a gem onto a piece of gear to auto-open the socket page |
| **Unsocket a gem** | Remove via the Gem Socket UI page |
| **Replace a gem** | Socket a new gem into an occupied slot (replaces the old one) |

### Socket Layout

```

Slot 0: [Active Gem]     <- Your ability
Slot 1: [Support Gem]    <- Modifies the active gem
Slot 2: [Support Gem]    <- Additional modification
Slot 3: [Support Gem]    <- And so on...

```

The number of available support slots (`supportSlotCount`) is tracked per item.

---

## Gem Data

Every gem instance tracks :

| Field | Description |
|-------|-------------|
| `gemId` | Which gem definition (e.g., `active_fireball`) |
| `level` | Gem level, 1 to 1000 |
| `quality` | Gem quality, 1 to 100 |
| `xp` | Experience toward next level |
| `gemType` | ACTIVE or SUPPORT |

Gems level up through use, gaining XP and becoming stronger. Quality affects the gem's bonus stats as defined by each gem's `qualityBonuses` map.

---

## Related Pages

- [Equipment System](equipment-system) - Gems socket into weapons alongside modifiers
- [Gear Modifiers](modifiers) - Modifiers and gems coexist on the same items
- [The 6 Elements](attributes) - Gem damage types align with the elemental system
