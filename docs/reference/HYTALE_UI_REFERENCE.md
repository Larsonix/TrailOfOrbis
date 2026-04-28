# Hytale Inventory UI Reference

Complete reference for Hytale's inventory, container, and panel UI styling. Use this document to maintain visual consistency with vanilla Hytale when building custom UIs.

## Source Files

All UI files located at:
```
/home/larsonix/work/Hytale-Decompiled-Full-Game/Assets/Common/UI/
├── Custom/
│   ├── Common.ui (master constants and styles)
│   ├── Common/
│   │   ├── ActionButton.ui
│   │   └── TextButton.ui
│   ├── Pages/
│   │   ├── BarterPage.ui
│   │   ├── BarterTradeRow.ui
│   │   ├── BarterGridSpacer.ui
│   │   ├── ShopPage.ui
│   │   ├── ShopElementButton.ui
│   │   ├── ItemRepairPage.ui
│   │   ├── ItemRepairElement.ui
│   │   ├── BasicTextButton.ui
│   │   └── DroppedItemSlot.ui
│   └── Hud/
├── ItemQualities/
│   ├── Slots/ (quality-specific slot backgrounds)
│   └── Tooltips/ (quality-specific tooltip backgrounds)
```

---

## Key Constants (from Common.ui)

### Button Dimensions
| Constant | Value | Usage |
|----------|-------|-------|
| `@PrimaryButtonHeight` | 44 | Standard button height |
| `@SmallButtonHeight` | 32 | Compact buttons |
| `@BigButtonHeight` | 48 | Emphasized buttons |
| `@ButtonPadding` | 24 | Horizontal padding inside buttons |
| `@DefaultButtonMinWidth` | 172 | Minimum button width |
| `@ButtonBorder` | 12 | 9-patch border for buttons |

### Container & Padding
| Constant | Value | Usage |
|----------|-------|-------|
| `@TitleHeight` | 38 | Panel header height |
| `@TitleOffset` | 4 | Title vertical offset |
| `@InnerPaddingValue` | 8 | Inner content padding |
| `@FullPaddingValue` | 17 | Full padding (8 + 9) |
| `@ContentPaddingFull` | 17 | Content area padding (9 + 8) |
| `@DropdownBoxHeight` | 32 | Dropdown element height |

### Overlay
| Constant | Value | Usage |
|----------|-------|-------|
| `@PageOverlay` | `#000000(0.45)` | Dimmed background behind modals |

---

## Typography

### Title Style (Panel Headers)
```
FontSize: 15
RenderUppercase: true
TextColor: #b4c8c9
FontName: "Secondary"
RenderBold: true
LetterSpacing: 0
```

### Subtitle Style
```
FontSize: 15
RenderUppercase: true
TextColor: #96a9be
```

### Default Label Style
```
FontSize: 16
TextColor: #96a9be
```

### Button Label Style
```
FontSize: 17
TextColor: #bfcdd5
RenderBold: true
RenderUppercase: true
HorizontalAlignment: Center
VerticalAlignment: Center
```

---

## Button Styling

### Primary Button
| Property | Value |
|----------|-------|
| Height | 44px |
| Horizontal Padding | 24px |
| Border (9-patch) | 12px |
| Label Font | 17pt, bold, uppercase |
| Label Color | #bfcdd5 |
| Texture Default | `Common/Buttons/Primary.png` |
| Texture Hovered | `Common/Buttons/Primary_Hovered.png` |
| Texture Pressed | `Common/Buttons/Primary_Pressed.png` |
| Texture Disabled | `Common/Buttons/Disabled.png` |

### Secondary Button
| Property | Value |
|----------|-------|
| Height | 44px |
| Horizontal Padding | 24px |
| Border (9-patch) | 12px |
| Label Color | #bdcbd3 |
| Texture Default | `Common/Buttons/Secondary.png` |
| Texture Hovered | `Common/Buttons/Secondary_Hovered.png` |
| Texture Pressed | `Common/Buttons/Secondary_Pressed.png` |
| Texture Disabled | `Common/Buttons/Disabled.png` |

### Small Button
| Property | Value |
|----------|-------|
| Height | 32px |
| Horizontal Padding | 16px |
| Border (9-patch) | 6px |
| Label Font | 14pt (smaller) |
| Uses Secondary textures | |

### Tertiary Button
| Property | Value |
|----------|-------|
| Height | 44px |
| Horizontal Padding | 24px |
| Texture Default | `Common/Buttons/Tertiary.png` |
| Texture Hovered | `Common/Buttons/Tertiary_Hovered.png` |
| Texture Pressed | `Common/Buttons/Tertiary_Pressed.png` |

### Destructive/Cancel Button
| Property | Value |
|----------|-------|
| Height | 44px |
| Horizontal Padding | 24px |
| Texture Default | `Common/Buttons/Destructive.png` |
| Texture Hovered | `Common/Buttons/Destructive_Hovered.png` |
| Texture Pressed | `Common/Buttons/Destructive_Pressed.png` |

### Close Button (X in corner)
| Property | Value |
|----------|-------|
| Anchor | `(Top: -16, Right: -16, Width: 32, Height: 32)` |
| Texture Default | `Common/ContainerCloseButton.png` |
| Texture Hovered | `Common/ContainerCloseButtonHovered.png` |
| Texture Pressed | `Common/ContainerCloseButtonPressed.png` |

---

## Container Layouts

### Standard Container (@Container)
```
Title Height: 38px
Content Padding: Full 17px

Textures:
  Header: Common/ContainerHeaderNoRunes.png (HorizontalBorder: 35)
  Content: Common/ContainerPatch.png (Border: 23)
```

### Decorated Container (@DecoratedContainer)
```
Title Height: 38px
Content Padding: Full 17px

Textures:
  Header: Common/ContainerHeader.png (HorizontalBorder: 50)
  Content: Common/ContainerPatch.png (Border: 23)
  Top Decoration: Common/ContainerDecorationTop.png (236x11)
  Bottom Decoration: Common/ContainerDecorationBottom.png (236x11)

Decoration Positioning:
  Top: Anchor (Top: -12) - overlaps header from above
  Bottom: Anchor (Bottom: -6) - overlaps content from below
```

### Container Structure Pattern
```
Group @Container {
  // Title bar
  Group #TitleGroup {
    Anchor: (Height: 38, Top: 0);
    Padding: (Top: 7);
    Background: Header texture;

    Label #Title {
      // Title style
    }
  }

  // Content area
  Group #Content {
    Anchor: (Top: 38);  // Below title
    Padding: (Full: 17);
    Background: Content patch;
    LayoutMode: Top;

    // Content here
  }

  // Close button (overlapping)
  ImageButton #CloseButton {
    Anchor: (Top: -16, Right: -16, Width: 32, Height: 32);
  }
}
```

---

## Item Slot & Grid Layouts

### Item Slot Sizes

| Slot Type | Outer Size | Inner Size | Border | Quantity Position |
|-----------|------------|------------|--------|-------------------|
| Large (Output) | 68x68px | 64x64px | 2px | Right: 8, Bottom: 5 |
| Medium (Input/Cost) | 52x52px | 48x48px | 2px | Right: 6, Bottom: 4 |
| Small | 32x32px | 28x28px | 2px | - |

### Quantity Label Style
- Large slots: 15pt, bold, white (#ffffff)
- Medium slots: 12pt, bold, white (#ffffff)

### Dropped Item Slot
```
Total: 72x72px
Border: 68x68px with 2px padding
Inner ItemSlot: 64x64px
Quantity Label: (Right: 5, Bottom: 0)
Border Color: #be1717 (red)
```

### Grid Layout Pattern (Wrapping)
```
// Outer scrollable container
Group #ScrollContainer {
  FlexWeight: 1;
  LayoutMode: TopScrolling;
  ScrollbarStyle: @DefaultScrollbarStyle;
  Padding: (Top: 10, Left: 8, Right: 8);

  // Inner wrapping grid
  Group #Grid {
    LayoutMode: LeftCenterWrap;
    Anchor: (Horizontal: 0);  // Full width

    // Children are fixed-size cards (e.g., 230x185)
    // Auto-wrap to next row when full
    // Use spacer elements to fill incomplete rows
  }
}
```

---

## Scrollbar Styling

### Default Scrollbar (@DefaultScrollbarStyle)
| Property | Value |
|----------|-------|
| Spacing | 6px |
| Size | 6px |
| Background | `Common/Scrollbar.png` (Border: 3) |
| Handle | `Common/ScrollbarHandle.png` (Border: 3) |
| Hovered Handle | `Common/ScrollbarHandleHovered.png` (Border: 3) |
| Dragged Handle | `Common/ScrollbarHandleDragged.png` (Border: 3) |

### Extra Spacing Scrollbar (@DefaultExtraSpacingScrollbarStyle)
| Property | Value |
|----------|-------|
| Spacing | 12px (wider) |
| Other properties | Same as Default |

### Translucent Scrollbar (@TranslucentScrollbarStyle)
| Property | Value |
|----------|-------|
| Spacing | 6px |
| Size | 6px |
| OnlyVisibleWhenHovered | true |
| Handle | `Common/ScrollbarHandle.png` (Border: 3) |

---

## Input Fields & Dropdowns

### Text Field
| Property | Value |
|----------|-------|
| Height | 38px |
| Horizontal Padding | 10px |
| Border (9-patch) | 16px |
| Placeholder Color | #6e7da1 |
| Background Default | `Common/InputBox.png` |
| Background Hovered | `Common/InputBoxHovered.png` |
| Background Pressed | `Common/InputBoxPressed.png` |
| Background Selected | `Common/InputBoxSelected.png` |

### Dropdown Box
| Property | Value |
|----------|-------|
| Width | 330px |
| Height | 32px |
| Border (9-patch) | 16px |
| Horizontal Padding | 8px |
| Arrow Icon | `Common/DropdownCaret.png` (13x18) |
| Entry Height | 31px |
| Entries in Viewport | 10 |
| Panel Padding | 6px |
| Panel Offset | 7px |
| Hovered Entry BG | #0a0f17 |
| Pressed Entry BG | #0f1621 |

---

## Color Palette

### Primary UI Colors
| Usage | Color |
|-------|-------|
| Button text (primary) | #bfcdd5 |
| Button text (secondary) | #bdcbd3 |
| Default text | #96a9be |
| Muted text | #7a8a9a |
| Disabled text | #797b7c |
| Placeholder text | #6e7da1 |

### Header/Title Colors
| Usage | Color |
|-------|-------|
| Title text | #b4c8c9 |
| Subtitle text | #96a9be |
| Panel title | #afc2c3 |
| Header button | #d3d6db |
| Header button hovered | #eaebee |
| Header button pressed | #b6bbc2 |

### Status Colors
| Usage | Color |
|-------|-------|
| Timer/status | #7caacc |
| Available/have (green) | #3d913f |
| Cost label | #8a9aaa |
| Out of stock/error | #cc4444 |
| Dropped item border | #be1717 |

### Background/Divider Colors
| Usage | Color |
|-------|-------|
| Divider | #252F3A |
| Divider (darker) | #19252F |
| Dark background | #1c2835 |
| Border (dark) | #1a2530 |
| Border (green accent) | #2a5a3a |

### Transparency Values
| Usage | Opacity |
|-------|---------|
| Slight tint/hover | 0.2 (20%) |
| Page overlay | 0.45 (45%) |
| Heavy overlay | 0.75 (75%) |

---

## Page Layout Examples

### Barter/Trade Page (740x480px)
```
DecoratedContainer (740x480)
├── Title Group (height: 38)
│   └── Title Label "Trade"
├── Content (padding: Horizontal 10)
│   ├── Grid Container (flex: 1, TopScrolling)
│   │   │   Scrollbar: 6px, padding: Top 10, Left 8, Right 8
│   │   └── Grid (LeftCenterWrap)
│   │       ├── Trade Card (230x185) with 5px padding
│   │       │   ├── Output Section (80px)
│   │       │   │   └── Slot (68x68, 2px border)
│   │       │   ├── Divider (8px, line 140x1 #252F3A)
│   │       │   ├── Cost Section (64px)
│   │       │   │   ├── "Cost:" label (#8a9aaa, 14pt)
│   │       │   │   ├── Input Slot (52x52)
│   │       │   │   └── "Have: X" label (#3d913f, 13pt)
│   │       │   └── Stock Section (16px, right-aligned)
│   │       ├── Trade Card ...
│   │       └── Grid Spacer (230x185, invisible fill)
│   └── Footer (40px)
│       ├── Divider (2px, #19252F)
│       └── Timer Label (#7caacc, 14pt)
└── Close Button (Top: -16, Right: -16, 32x32)
```

### Shop Page (600x700px)
```
Standard Container (600x700)
├── Title Group (height: 38)
├── Content (Left layout)
│   └── Element List (flex: 1, TopScrolling)
│       └── Shop Element Buttons...
└── Close Button
```

### Item Repair Page (600x400px)
```
DecoratedContainer (600x400)
├── Title Group (height: 38)
├── Content
│   ├── Header Row (Left layout)
│   │   ├── "Item" Label (flex: 1)
│   │   └── "Durability" Label
│   └── Element List (flex: 1, TopScrolling)
│       └── Repair Elements (Left layout, Full 6px padding)
│           ├── Icon (32x32)
│           ├── Name Label (flex: 1, bold)
│           ├── Durability (#ffffff(0.6), padding: H 10, V 5)
│           └── Bottom Border (2px, #ffffff(0.6))
└── Close Button
```

---

## Texture Reference

### Panel/Container Textures
```
Common/ContainerHeaderNoRunes.png     - Header (no decorations)
Common/ContainerHeader.png            - Header (with runes)
Common/ContainerPatch.png             - Content background (Border: 23)
Common/ContainerFullPatch.png         - Full overlay (Border: 20)
Common/ContainerVerticalSeparator.png - Vertical divider
Common/ContainerDecorationTop.png     - Top ornament (236x11)
Common/ContainerDecorationBottom.png  - Bottom ornament (236x11)
Common/ContainerCloseButton.png       - Close button states
Common/ContainerCloseButtonHovered.png
Common/ContainerCloseButtonPressed.png
```

### Button Textures
```
Common/Buttons/Primary.png            - Primary button states
Common/Buttons/Primary_Hovered.png
Common/Buttons/Primary_Pressed.png
Common/Buttons/Primary_Square.png
Common/Buttons/Secondary.png          - Secondary button states
Common/Buttons/Secondary_Hovered.png
Common/Buttons/Secondary_Pressed.png
Common/Buttons/Tertiary.png           - Tertiary button states
Common/Buttons/Tertiary_Hovered.png
Common/Buttons/Tertiary_Pressed.png
Common/Buttons/Destructive.png        - Destructive button states
Common/Buttons/Destructive_Hovered.png
Common/Buttons/Destructive_Pressed.png
Common/Buttons/Disabled.png           - Disabled state (shared)
```

### Input/Form Textures
```
Common/InputBox.png                   - Text input states
Common/InputBoxHovered.png
Common/InputBoxPressed.png
Common/InputBoxSelected.png
Common/Dropdown.png                   - Dropdown states
Common/DropdownHovered.png
Common/DropdownPressed.png
Common/DropdownCaret.png              - Dropdown arrow
Common/DropdownPressedCaret.png
Common/DropdownBox.png                - Dropdown panel
Common/CheckBoxFrame.png              - Checkbox
Common/Checkmark.png
```

### Scrollbar Textures
```
Common/Scrollbar.png                  - Track background
Common/ScrollbarHandle.png            - Handle states
Common/ScrollbarHandleHovered.png
Common/ScrollbarHandleDragged.png
```

### Item Slot Textures (by Quality)
```
ItemQualities/Slots/SlotDefault@2x.png
ItemQualities/Slots/SlotCommon@2x.png
ItemQualities/Slots/SlotUncommon@2x.png
ItemQualities/Slots/SlotRare@2x.png
ItemQualities/Slots/SlotEpic@2x.png
ItemQualities/Slots/SlotLegendary@2x.png
ItemQualities/Slots/SlotJunk@2x.png
ItemQualities/Slots/SlotTool@2x.png
ItemQualities/Slots/SlotDeveloper@2x.png
```

### Tooltip Textures (by Quality)
```
ItemQualities/Tooltips/ItemTooltipDefault@2x.png
ItemQualities/Tooltips/ItemTooltipCommon@2x.png
ItemQualities/Tooltips/ItemTooltipUncommon@2x.png
ItemQualities/Tooltips/ItemTooltipRare@2x.png
ItemQualities/Tooltips/ItemTooltipEpic@2x.png
ItemQualities/Tooltips/ItemTooltipLegendary@2x.png
ItemQualities/Tooltips/ItemTooltipJunk@2x.png
ItemQualities/Tooltips/ItemTooltipTechnical@2x.png
(+ corresponding Arrow variants)
```

---

## Quick Reference Card

### Essential Dimensions
```
Panel title height:     38px
Content padding:        17px (Full)
Button height:          44px (primary), 32px (small), 48px (big)
Button padding:         24px (horizontal)
Button min width:       172px
Close button:           32x32px at (Top: -16, Right: -16)
Item slot (large):      68x68px outer, 64x64px inner
Item slot (medium):     52x52px outer, 48x48px inner
Scrollbar:              6px wide, 6px spacing
```

### Essential Colors
```
Primary text:           #bfcdd5
Default text:           #96a9be
Title text:             #b4c8c9
Muted text:             #7a8a9a
Disabled:               #797b7c
Success/available:      #3d913f
Error/unavailable:      #cc4444
Divider:                #252F3A
Page overlay:           #000000(0.45)
```

### Essential Layout Modes
```
Top           - Vertical stack from top (most common)
Left          - Horizontal stack from left
TopScrolling  - Scrollable vertical
LeftCenterWrap - Horizontal grid with wrapping
Center        - Center horizontally
Middle        - Center vertically
Full          - Fill parent
```
