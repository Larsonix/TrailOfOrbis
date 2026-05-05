# HyUI

## Identity

| Field | Value |
|-------|-------|
| Author | Elliesaur ([Elliesaur](https://github.com/Elliesaur)) |
| Repository | https://github.com/Elliesaur/HyUI |
| License | Unknown |
| Category | Plugin / Library |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | `0620b82` |
| Last Updated | 2026-05-01 |
| Cloned From | main |

## What It Does

UI utility library for Hytale modding. Provides HYUIML parsing, style utilities, element builders, tooltip helpers, and multi-HUD management. Also includes the MultipleHUD system (allows multiple custom HUDs to coexist).

## What We Use

- **HYUIML parsing** — HyUI's `UIFileParser` and element builders power all of our custom UI pages
- **Style utilities** — `StyleUtils`, `PropertyBatcher` for efficient UI property updates
- **Multi-HUD support** — `MultipleCustomUIHud` for coexisting HUDs (XP bar, realm info, party, etc.)
- **Tooltip system** — Text tooltip builders and alignment types
- **Button/element builders** — `CustomButtonBuilder`, `ItemSlotButtonBuilder`, etc.

## Our Integration

HyUI is a foundational dependency — virtually every UI file in our codebase depends on it (15+ files). Imports: `au.ellie.hyui.builders.*`

| File | Purpose |
|------|---------|
| `src/.../ui/UIManager.java` | Central UI initialization, uses HyUI builders |
| `src/.../ui/RPGStyles.java` | Custom styles built on HyUI's style system |
| `src/.../gear/tooltip/RichTooltipFormatter.java` | Tooltips via HyUI |
| `src/.../ui/hud/XpBarHud.java` | Multi-HUD via HyUI |
| `src/.../maps/ui/RealmCombatHud.java` | Realm combat HUD |
| `src/.../maps/gateway/GatewayUpgradePage.java` | Gateway UI pages |
| `src/.../guide/GuidePopupPage.java` | In-game guide popups |
| All `*Page.java` files | UI pages using HyUI element builders |

## Known Issues

- HyUI is also bundled inside other mods (`au.ellie.hyui.*`, v4.1.5) — version conflicts possible if both are loaded. The GitHub repo may diverge from bundled versions.
- Some HyUI button classes support `Activating` events and some don't — see `.claude/rules/hyui-element-constraints.md`

## Update Instructions

```bash
./external/scripts/update-externals.sh hyui
```
