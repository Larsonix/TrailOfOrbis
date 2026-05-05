# MHUD (MultipleHUD)

## Identity

| Field | Value |
|-------|-------|
| Author | Buuz135 ([Buuz135](https://github.com/Buuz135)) |
| Repository | https://github.com/Buuz135/MHUD |
| License | Unknown |
| Category | Plugin |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | `d85a7df` |
| Last Updated | 2026-05-01 |
| Cloned From | main |

## What It Does

Allows multiple Hytale mods to display custom HUDs simultaneously. Without this, only one mod's HUD can be visible at a time. Provides a managed HUD layer system.

## What We Use

- **Multi-HUD coexistence** — ensures our XP bar, realm HUD, party HUD, and death recap all display alongside other mods' HUDs

## Our Integration

| File | Purpose |
|------|---------|
| `src/.../ui/UIManager.java` | HUD registration through MHUD system |

## Known Issues

- Relationship with HyUI's own MultipleCustomUIHud — both solve similar problems

## Update Instructions

```bash
./external/scripts/update-externals.sh mhud
```
