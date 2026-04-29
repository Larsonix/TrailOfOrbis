---
name: Loot Filter
title: Loot Filter
description: Player-configurable loot filtering with profiles, ordered rules, and 11+ condition types
author: Larsonix
sort-index: 4
order: 125
published: true
---

# Loot Filter

As you level up, lower-rarity drops become clutter. The loot filter lets you configure exactly what items you want to see, using profiles with ordered rules and granular conditions.

---

## Quick Start

The fastest way to set up filtering :

```
/lf quick rare
```

This hides all [Common](gear-rarities#common) and Uncommon drops. Only [Rare](gear-rarities#rare) and above appear. To see everything again :

```
/lf quick off
```

---

## Commands

| Command | What It Does |
|---------|-------------|
| `/lf` | Open the Loot Filter UI page |
| `/lf toggle` | Toggle filtering on/off |
| `/lf on` / `/lf off` | Enable/disable filtering |
| `/lf status` | Show current filter status and active profile |
| `/lf quick <rarity>` | Quick filter : hide everything below a rarity |
| `/lf quick off` | Clear the quick filter |
| `/lf switch <name>` | Switch to a saved filter profile |
| `/lf list` | List all your saved profiles |
| `/lf preset [name]` | List presets or create a profile from one |
| `/lf test` | Test your filter against your held item |

---

## How Filtering Works

### Filter Actions

Each rule has one of two actions :

| Action | Effect |
|--------|--------|
| **ALLOW** | Items matching this rule are shown |
| **BLOCK** | Items matching this rule are hidden |

### Rule Evaluation

Rules are evaluated in order within a profile. The first matching rule determines the outcome. Rules use **AND logic** : all conditions within a rule must match for the rule to apply. Disabled rules are skipped entirely and never match.

---

## Condition Types

Rules are built from conditions. Each condition tests one aspect of the item :

| Condition | What It Checks |
|-----------|---------------|
| **MIN_RARITY** | Item is at least this rarity |
| **MAX_RARITY** | Item is at most this rarity |
| **EQUIPMENT_SLOT** | Item fits a specific equipment slot |
| **WEAPON_TYPE** | Item is a specific weapon type |
| **ARMOR_MATERIAL** | Item is made of a specific material |
| **ITEM_LEVEL_RANGE** | Item level is within a range |
| **QUALITY_RANGE** | Item quality is within a range |
| **REQUIRED_MODIFIERS** | Item has specific modifiers |
| **MODIFIER_VALUE_RANGE** | A modifier's value is within a range |
| **MIN_MODIFIER_COUNT** | Item has at least N modifiers |
| **IMPLICIT_CONDITION** | Item has a specific implicit modifier |
| **CORRUPTION_STATE** | Item corruption status matches |

### Example Rule Logic

A rule with conditions `MIN_RARITY: Rare` + `EQUIPMENT_SLOT: Weapon` would only match weapons that are Rare or better. Both conditions must be true (AND logic).

---

## Filter Profiles

You can save multiple filter configurations as named profiles and switch between them. Profiles persist across sessions.

### Profile Structure

Each profile contains :
- An ordered list of rules
- Each rule has an action (ALLOW or BLOCK) and a list of conditions
- Rules can be individually enabled or disabled

### Presets

Presets provide starting configurations you can customize :
- Use `/lf preset` to see available presets
- Use `/lf preset <name>` to create a profile from a preset

---

## The Loot Filter UI

The loot filter page has 3 views :

| View | Purpose |
|------|---------|
| **Home** | Quick filter presets, profile list, toggle on/off |
| **Rules** | Ordered list of rules in the active profile |
| **Edit Rule** | Condition editor for a specific rule |

Open the full UI with `/lf` to visually manage your filter profiles and rules.

---

## Recommended Progression

| Your Level | Suggested Filter | Why |
|-----------|-----------------|-----|
| 1-20 | No filter | You need everything you can get |
| 20-50 | `/lf quick uncommon` | Hide only Common drops |
| 50+ | `/lf quick rare` | Focus on Rare and above |
| 100+ | `/lf quick epic` | Only [Epic](gear-rarities#epic)+ is worth equipping |

> [!WARNING]
> Filtering too aggressively early can slow your progression. At Level 10, an Uncommon drop is still a meaningful upgrade.

> [!TIP]
> Use `/lf test` while holding an item to check if your current filter would hide it. Great for fine-tuning rules before committing.

---

## Related Pages

- [Drop Mechanics](drop-mechanics) - What drops and at what rates
- [Gear Rarities](gear-rarities) - Understanding the rarity tiers you filter by
- [Loot System](loot-system) - Full loot system overview
