# Hytale Client Data Reference

The Hytale client ships structured data files (`.ui`, `.xaml`, `.lang`, textures, audio, fonts) in `Client/Data/`. These files define how the client **renders** everything — combat text, tooltips, health bars, HUDs, status effects, design system colors, and more. Server decompilation only reveals what the server sends; client data reveals how it's displayed.

## Extraction & Indexes

**Extracted to**: `/home/larsonix/work/Hytale-Decompiled-Full-Game/ClientData/`

Run `~/tools/decompile-hytale.sh` (includes client extraction) or extract manually:
```bash
# Manual extraction
cp -r "/mnt/c/.../Hytale/install/release/package/game/latest/Client/Data/"* \
      ~/work/Hytale-Decompiled-Full-Game/ClientData/

# Re-index only
~/tools/index-hytale-client.sh
```

### Index Files

| File | Size | Content | Usage |
|------|------|---------|-------|
| `CLIENT_UI_INDEX.txt` | ~16K | 213 `.ui` file paths | `grep "CombatText" CLIENT_UI_INDEX.txt` |
| `CLIENT_XAML_INDEX.txt` | ~8K | 125 `.xaml` file paths | `grep "Colors" CLIENT_XAML_INDEX.txt` |
| `CLIENT_LANG_INDEX.txt` | ~136K | 2,364 localization key=value pairs | `grep "damage" CLIENT_LANG_INDEX.txt` |
| `CLIENT_FILE_TREE.md` | ~4K | Directory structure with file counts | Quick overview |
| `CLIENT_UI_SUMMARY.md` | ~100K | Element IDs, variables, layouts per .ui file | `grep -A 10 "## CombatText" CLIENT_UI_SUMMARY.md` |

All indexes are in `/home/larsonix/work/Hytale-Decompiled-Full-Game/.index/`.

## File Formats

### `.ui` Files (213 files) — Hytale Native UI

Hytale's proprietary UI markup. Same syntax as server-side `.ui` files (Groups, Labels, TextButtons with Anchor/Padding/Style).

Key syntax:
```
@VarName = value;              // Named expression (variable)
Group #ElementID { ... }       // Named group
Label #Name { Text: "hello"; } // Text label
LayoutMode: Top;               // Layout direction
Anchor: (Width: 200);          // Sizing/positioning
Style: (FontSize: 14);         // Text styling
```

### `.xaml` Files (125 files) — NoesisGUI

WPF-style XAML used by NoesisGUI (Hytale's secondary UI framework). Contains design system tokens, styles, templates, and theme definitions. Uses standard XAML/WPF syntax with NoesisGUI extensions.

### `.lang` Files (3 files) — Localization

Simple `key = value` format. Referenced from `.ui` files via `%translation.key` syntax.

```
itemTooltip.stats.Health = Health
hud.entityEffects.defaultBuffName = Buff
```

## Key Files for RPG Development

### Combat Text: `Game/Interface/InGame/EntityUI/CombatText.ui`

The client-side template for floating damage numbers.

```
Group #Container {
  LayoutMode: Middle;
  Anchor: (Width: 200, Height: 200);
  Padding: (Horizontal: 3, Vertical: 3);

  Label #Text {
    Style: (HorizontalAlignment: Center, VerticalAlignment: Center, RenderBold: true);
  }
}
```

**Key discovery**: The `.ui` template is minimal — just a centered bold label. All styling (color, font size, animation) comes from `CombatText.json` (server asset) and `DamageTextColor` on DamageCause. The client does **not** have built-in crit-specific rendering in the `.ui` file — any crit visual distinction must come from the server-side `CombatText.json` animation config or `DamageTextColor`.

### Item Tooltip: `Game/Interface/InGame/Tooltips/ItemTooltip.ui`

Complete tooltip structure with all display sections:

| Element | Purpose |
|---------|---------|
| `#Name` | Item name (bold, size 18) |
| `#Quality` | Quality label (e.g., "Legendary") |
| `#Id` | Item ID (gray italic, size 14) |
| `#Type` | Item type (gray, size 14) |
| `#Description` | Description text (gray, wrapped) |
| `#Stats` | Stat section (hidden by default) |
| `#StatHealth`, `#StatDefense`, `#StatStamina`, `#StatMana`, `#StatAttack` | Individual stat rows with icons |
| `#StatsContent` | Dynamic stat content area |
| `#Durability` | Durability display |
| `#Cursed` | Cursed item section (purple, with spiral icon) |

Variables: `@PrimaryTextColor = #bca57a` (gold), `@PrimaryCursedColor = #a020f0` (purple)

### Health Bar: `Game/Interface/InGame/EntityUI/HealthBar.ui`

Entity health bar template (rendered above mobs/players).

### HUD Elements

| Path | Content |
|------|---------|
| `Hud/Health/` | Player health bar HUD |
| `Hud/Mana/` | Mana bar HUD |
| `Hud/Stamina/` | Stamina bar HUD |
| `Hud/Abilities/` | Ability bar with cooldown icons |
| `Hud/StatusEffects/` | Buff/debuff display |
| `Hud/Chat.ui` | Chat window |
| `Hud/Hotbar.ui` | Hotbar layout |
| `Hud/KillFeed.ui` | Kill feed display |
| `Hud/PlayerList.ui` | Online player list |
| `Hud/Notification.ui` | Notification toast |
| `Hud/PortalPanel.ui` | Portal interaction panel |
| `Hud/Reticle.ui` | Crosshair/reticle |
| `Hud/InputBindings.ui` | Key binding display |

### Design System: `Game/UI/DesignSystem/`

The full Hytale design system in XAML. Key files:

| File | Content |
|------|---------|
| `Colors.xaml` | Global color tokens (White, Blue50-1200, Red500-700, Green, etc.) |
| `Button.xaml` | Button styles and states |
| `Slot.xaml` | Inventory slot styling |
| `HealthBar.xaml` | Health bar XAML styling |
| `StaminaBar.xaml` | Stamina bar styling |
| `Effects.xaml` | Visual effects |
| `Tooltip.xaml` | Tooltip styling |
| `Typography.xaml` | Font definitions and text styles |
| `Styles.xaml` | Shared element styles |

### Localization: `Shared/Language/en-US/client.lang`

2,364 strings. RPG-relevant sections:

| Key prefix | Content |
|------------|---------|
| `itemTooltip.stats.*` | Stat names: Health, Defense, Mana, Stamina, etc. |
| `itemTooltip.damageClass.*` | Light Attack, Charged Attack, Signature Attack |
| `itemTooltip.resistance` | "Resistance:" |
| `itemTooltip.oneHanded/twoHanded` | Weapon hand type |
| `itemTooltip.cursed.*` | Cursed item labels |
| `hud.entityEffects.*` | "Buff", "Debuff" |
| `hud.portalPanel.*` | Portal UI strings |
| `hud.networkQuality.*` | Network quality descriptions |

## Directory Structure

```
ClientData/                        (1,276 files)
├── Editor/                        (editor-only UI)
│   ├── CosmeticSchemas/           (cosmetic JSON schemas)
│   └── Interface/                 (AssetEditor, Common, MainMenu, Settings)
├── Game/                          (PRIMARY — game client)
│   ├── Backgrounds/               (menu backgrounds)
│   ├── Interface/
│   │   ├── Common/                (shared UI: buttons, settings)
│   │   ├── DevTools/              (dev tool UIs)
│   │   ├── GameLoading/           (loading screens)
│   │   ├── InGame/
│   │   │   ├── EntityUI/          ★ CombatText.ui, HealthBar.ui
│   │   │   ├── Hud/              ★ Health, Mana, Stamina, Abilities, Chat, KillFeed, StatusEffects
│   │   │   ├── Overlays/         (pause menu, quit confirm)
│   │   │   ├── Pages/            ★ Inventory, Map, Memories
│   │   │   └── Tooltips/         ★ ItemTooltip.ui, CreativeModeTooltip.ui
│   │   ├── MainMenu/             (main menu, adventure, shop, servers)
│   │   ├── Services/             (service connection UI)
│   │   └── Sounds/               (UI sound definitions)
│   ├── MainMenuMusic/            (audio files)
│   ├── Schema/                   (PlayerSkin.json + others)
│   ├── ShaderTextures/           (shader resources)
│   ├── Tools/                    (tool configs)
│   ├── UI/
│   │   ├── Common/               (shared XAML: ColorPicker, Timeline)
│   │   ├── DesignSystem/         ★ Colors.xaml, Button.xaml, Slot.xaml, HealthBar.xaml
│   │   ├── Gallery/              (UI gallery/showcase)
│   │   ├── Machinima/            (machinima tool UI)
│   │   ├── Textures/             ★ HUD, Icons, Inventory, cursors, decorations
│   │   └── Theme/                (NoesisGUI theme definitions)
│   └── WorldConfigPresets/       (world generation presets)
└── Shared/                       (shared between game and editor)
    ├── Fonts/                    (9 font files: TTF/OTF)
    └── Language/en-US/
        └── client.lang           ★ 2,364 localization strings
```

## Server → Client Relationship

The server sends structured data; the client renders it using these templates.

| Server Sends | Client Renders With |
|-------------|---------------------|
| `CombatTextUpdate` packet (text + angle) | `CombatText.ui` template + `CombatText.json` animation config |
| Item data + quality | `ItemTooltip.ui` template + quality textures |
| Entity health component | `HealthBar.ui` template |
| Player stats | `Hud/Health/`, `Hud/Mana/`, `Hud/Stamina/` |
| Status effects | `Hud/StatusEffects/` |
| `DamageInfo` packet (damageTextColor) | Red vignette + colored combat text |

## NoesisGUI Note

The 125 `.xaml` files use NoesisGUI (WPF-compatible XAML renderer). These coexist with `.ui` files — Hytale uses both systems:
- `.ui` files: Hytale's native UI system (used for in-game HUD, entity UI, tooltips)
- `.xaml` files: NoesisGUI (used for design system, menus, complex controls)

The `.xaml` files are the more modern system and contain the design tokens (colors, typography, component styles) that the `.ui` files reference indirectly.

## Quick Lookup

```bash
# Find a client .ui file by name
grep "CombatText" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLIENT_UI_INDEX.txt

# Find a XAML file
grep "Colors" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLIENT_XAML_INDEX.txt

# Search localization strings
grep "damage" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLIENT_LANG_INDEX.txt

# Get element IDs from a .ui file
grep -A 15 "## ItemTooltip" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLIENT_UI_SUMMARY.md

# Read a client .ui file directly
cat /home/larsonix/work/Hytale-Decompiled-Full-Game/ClientData/Game/Interface/InGame/EntityUI/CombatText.ui

# List all HUD .ui files
grep "Hud" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLIENT_UI_INDEX.txt

# Search for a color in the design system
grep "Red" /home/larsonix/work/Hytale-Decompiled-Full-Game/ClientData/Game/UI/DesignSystem/Colors.xaml
```
