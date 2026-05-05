# The Armory

## Identity

| Field | Value |
|-------|-------|
| Author | azurefoxx98 ([azurefoxx98](https://github.com/azurefoxx98)) |
| Repository | https://github.com/azurefoxx98/The-Armory-Mod |
| License | Unknown |
| Category | Plugin |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | `5857a12` |
| Last Updated | 2026-05-01 |
| Cloned From | main |

## What It Does

Equipment and weapon mod for Hytale. Adds custom weapons, armor sets, and equipment mechanics.

## What We Use

- **Equipment compatibility** — ensure our gear system (RPG stats, quality, rarity) works alongside Armory items
- **Item classification** — Armory items need to be correctly classified by our `ItemClassifier`
- **Reference** — study their equipment patterns for our own gear system design

## Our Integration

| File | Purpose |
|------|---------|
| TBD | No direct integration yet — tracking for future compat |

## Known Issues

- Not yet integrated — source not previously in vendor/
- Need to assess item definition overlap with our gear system

## Update Instructions

```bash
./external/scripts/update-externals.sh the-armory
```
