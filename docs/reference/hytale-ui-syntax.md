# Hytale UI Syntax Reference

This document defines valid syntax for Hytale `.ui` files. **Always consult this before writing UI code.**

## LayoutMode (Valid Values)

| Value | Description |
|-------|-------------|
| `Top` | Stack children vertically from top (most common) |
| `Left` | Stack children horizontally from left (most common) |
| `Right` | Stack children horizontally from right |
| `Middle` | Center children vertically |
| `Center` | Center children horizontally |
| `Bottom` | Stack children vertically from bottom |
| `TopScrolling` | Vertical scrollable container |
| `LeftCenterWrap` | Horizontal wrap layout (like flex-wrap) |
| `CenterMiddle` | Center both horizontally and vertically |
| `MiddleCenter` | Same as CenterMiddle |
| `Full` | Fill entire parent |

**INVALID values:** `WrappingLeft`, `Wrap`, `LeftWrap`, `HorizontalWrap`

## Padding (Valid Fields)

| Field | Description |
|-------|-------------|
| `Full` | Uniform padding on all sides |
| `Horizontal` | Left and right padding |
| `Vertical` | Top and bottom padding |
| `Top` | Top padding only |
| `Bottom` | Bottom padding only |
| `Left` | Left padding only |
| `Right` | Right padding only |

**INVALID fields:** `All` (use `Full` instead)

**Examples:**
```
Padding: (Full: 16);
Padding: (Horizontal: 10, Vertical: 8);
Padding: (Left: 24, Right: 24, Top: 24, Bottom: 24);
```

## Anchor (Valid Fields)

| Field | Description |
|-------|-------------|
| `Width` | Fixed width |
| `Height` | Fixed height |
| `Top` | Offset from top |
| `Bottom` | Offset from bottom |
| `Left` | Offset from left |
| `Right` | Offset from right |
| `Horizontal` | Horizontal stretch (use 0 for full width) |
| `Vertical` | Vertical stretch (use 0 for full height) |
| `Full` | Fill parent completely |

**INVALID fields:** `MinWidth`, `MinHeight` (not supported by Hytale UI)

**Examples:**
```
Anchor: (Width: 100, Height: 36);
Anchor: (Horizontal: 0, Vertical: 0);  // Fill parent
Anchor: (Height: 50, Horizontal: 0);   // Fixed height, full width
Anchor: (Width: 200, Top: 10, Left: 10);
```

## Alignment (Valid Values)

For `HorizontalAlignment` and `VerticalAlignment` in Style:

| Value | Description |
|-------|-------------|
| `Start` | Align to start (left for horizontal, top for vertical) |
| `Center` | Center alignment |
| `End` | Align to end (right for horizontal, bottom for vertical) |

**INVALID values:** `Left`, `Right`, `Top`, `Bottom`

**Examples:**
```
Style: (HorizontalAlignment: Center, VerticalAlignment: Center);
Style: (HorizontalAlignment: Start);   // Left-aligned
Style: (HorizontalAlignment: End);     // Right-aligned
```

## Style (Label/Text Properties)

| Property | Type | Description |
|----------|------|-------------|
| `FontSize` | number | Font size in pixels |
| `FontName` | string | Font family ("Secondary" for headers) |
| `TextColor` | color | Text color |
| `RenderBold` | bool | Bold text |
| `RenderUppercase` | bool | Force uppercase |
| `LetterSpacing` | number | Space between letters |
| `Wrap` | bool | Enable text wrapping |
| `HorizontalAlignment` | enum | Start, Center, End |
| `VerticalAlignment` | enum | Start, Center, End |

## TextButton Style

Use `TextButtonStyle(...)` with these states:
- `Default` - Normal state
- `Hovered` - Mouse over
- `Pressed` - Clicked
- `Disabled` - Inactive

Each state can have:
- `Background` - Background color
- `LabelStyle` - Nested style for text

**Example:**
```
Style: TextButtonStyle(
  Default: (LabelStyle: (FontSize: 14, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center)),
  Hovered: (Background: #3a3a3a),
  Pressed: (Background: #4a4a4a),
  Disabled: (Background: #1a1a1a, LabelStyle: (TextColor: #555555))
);
```

## Colors

Colors use hex format with optional alpha:
- `#rrggbb` - Solid color
- `#rrggbb(0.5)` - 50% transparent

**Examples:**
```
Background: #2a2a2a;
Background: #ff4444(0.3);   // 30% opacity red
TextColor: #ffffff;
```

## Visibility

```
Visible: true;
Visible: false;
```

## Flex Layout

```
FlexWeight: 1;   // Take available space proportionally
```

## Common Patterns

### Full-screen centered dialog:
```
Group {
  Anchor: (Horizontal: 0, Vertical: 0);
  Background: #0a0a0a;
  LayoutMode: Middle;

  Group {
    Anchor: (Width: 500, Height: 400);
    LayoutMode: Top;
    // Content here
  }
}
```

### Horizontal button row:
```
Group {
  LayoutMode: Left;
  Anchor: (Height: 50, Horizontal: 0);

  TextButton #LeftButton { ... }
  Group { FlexWeight: 1; }  // Spacer
  TextButton #RightButton { ... }
}
```

### Wrapping grid:
```
Group #Grid {
  LayoutMode: LeftCenterWrap;
  Anchor: (Horizontal: 0);
  // Children will wrap to next row when they don't fit
}
```

## Quick Reference Card

```
LayoutMode:    Top | Left | Right | Middle | Center | Bottom | TopScrolling | LeftCenterWrap | Full
Padding:       Full | Horizontal | Vertical | Top | Bottom | Left | Right
Anchor:        Width | Height | Top | Bottom | Left | Right | Horizontal | Vertical | Full
Alignment:     Start | Center | End (NOT Left/Right/Top/Bottom!)
```
