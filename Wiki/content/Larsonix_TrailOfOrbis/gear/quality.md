---
name: Quality
title: Quality System
description: How quality multiplies modifier values - the 1-101 range, baseline at 50, and the pursuit of perfection
author: Larsonix
sort-index: 1
order: 67
published: true
---

# Quality System

Every piece of gear rolls a Quality value from **1 to 101**. This number multiplies ALL your modifier values. A high-quality [Rare](gear-rarities#rare) can outperform a low-quality [Epic](gear-rarities#epic) on specific stats, making quality one of the most impactful properties on any item.

---

## Key Numbers

| Quality | Role |
|---------|------|
| **1** | Minimum quality, worst possible roll |
| **50** | `QUALITY_BASELINE` - the neutral point (1.0x multiplier) |
| **100** | Maximum normal quality |
| **101** | `PERFECT` quality - 0.5% natural drop chance, also guaranteed via Gaia's Perfection stone |

Quality 50 is your baseline where modifiers work at their listed values. Everything below 50 reduces them. Everything above 50 amplifies them.

---

## The Quality Multiplier

```

qualityMultiplier = 0.5 + quality / 100.0

```

| Quality | Multiplier | Example : +20% modifier |
|--------:|-----------:|:------------------------|
| 1 | 0.51x | Becomes +10.2% |
| 25 | 0.75x | Becomes +15% |
| **50** | **1.00x** | **Stays +20% (baseline)** |
| 75 | 1.25x | Becomes +25% |
| 100 | 1.50x | Becomes +30% |
| **101** | **1.51x** | **Becomes +30.2%** |

A Q101 item has modifier values nearly **3x stronger** than a Q1 item (1.51x vs 0.51x).

Quality applies to both **gear modifiers** and **Realm Map modifier values**.

---

## Quality vs Rarity

Quality and rarity are **independent rolls**. This creates interesting power comparisons.

A [Rare](gear-rarities#rare) item (0.8x rarity mult) at Q100 can match or exceed an [Epic](gear-rarities#epic) item (1.2x rarity mult) at Q25 on per-modifier power. However, the Epic still has more modifier **slots**. It can roll up to 4 modifiers versus the Rare's 3.

> [!TIP]
> When evaluating gear, consider both dimensions. Rarity determines how many modifiers you get and their multiplier ceiling. Quality determines how hard each modifier hits. The best items have both high rarity AND high quality.

---

## Perfect Quality (101)

Quality 101 is special :
- It has a **0.5% natural drop chance** (config : `perfect: 0.005`)
- It's also **guaranteed** via the **Gaia's Perfection** stone, one of the rarest stones in the game
- It provides the absolute maximum modifier multiplier

> [!IMPORTANT]
> Quality 101 has a 0.5% chance to drop naturally (`perfect: 0.005` in config). It can also be guaranteed via the Gaia's Perfection stone. Either way, perfect-quality items are rare and valuable.

---

## Improving Quality

| Stone | Effect |
|-------|--------|
| **Orbisian Blessing** | Rerolls quality randomly (1-100) |
| **Cartographer's Polish** | Incremental quality improvement (max 100, maps only) |
| **Gaia's Perfection** | Sets quality to 101 (perfect) |

---

## What Quality Does NOT Affect

Quality multiplies **modifier values** only. It does NOT affect :

- **Weapon implicits** - Base damage scales with item level, not quality
- **Armor implicits** - Base defense scales with item level, not quality
- **Number of modifiers** - Determined by rarity, not quality
- **Which modifiers roll** - Random selection independent of quality
- **Durability** - Determined by rarity multiplier, not quality

---

## Related Pages

- [Gear Modifiers](modifiers) - Quality multiplies all modifier values on the item
- [Orbisian Blessing](consumable-currency#orbisian-blessing) - Rerolls quality randomly (1-100)
- [Gaia's Perfection](consumable-currency#gaias-perfection) - The only way to guarantee Quality 101
- [Gear Rarities](gear-rarities) - Rarity and quality are independent power axes
