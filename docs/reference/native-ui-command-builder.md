# Native UI: UICommandBuilder Complete Reference

How to modify native `.ui` templates from server-side Java code. This is the definitive reference for building `CustomUIPage` implementations that load and populate Hytale's native UI templates.

## UICommandBuilder API (All Public Methods)

### Loading Templates

```java
// Load a .ui template as the page root
cmd.append("Pages/PortalDeviceSummon.ui");

// Append a .ui template as a child of an element
cmd.append("#Pills", "Pages/Portals/Pill.ui");

// Append inline .ui markup (no file needed)
cmd.appendInline("#Container", "Label { Text: Hello; Style: (FontSize: 16, TextColor: #ffffff); }");

// Insert a .ui template BEFORE an element (preserves siblings)
cmd.insertBefore("#ExistingElement", "Pages/MyTemplate.ui");

// Insert inline markup BEFORE an element
cmd.insertBeforeInline("#ExistingElement", "Label { Text: Header; }");
```

### Setting Values

```java
// Text (plain string — for Labels)
cmd.set("#Label.Text", "Plain text");

// TextSpans (rich formatted Message — for Labels, supports color/bold/params)
cmd.set("#Label.TextSpans", Message.raw("Colored").color("#ff0000"));
cmd.set("#Label.TextSpans", Message.translation("server.key").param("name", value));

// Visibility (any element)
cmd.set("#Element.Visible", true);
cmd.set("#Element.Visible", false);

// Disabled state (buttons, inputs)
cmd.set("#Button.Disabled", true);

// Background (texture path or color)
cmd.set("#Panel.Background", "Pages/Portals/artwork.png");   // texture
cmd.set("#Panel.Background", "#2a2a2a");                     // color

// Nested background color
cmd.set("#Pill.Background.Color", "#ff0000");

// Null (clear a property)
cmd.setNull("#Element.Background");

// Float (progress bars)
cmd.set("#ProgressBar.Value", 0.75f);

// Boolean (checkboxes)
cmd.set("#CheckBox.Value", true);

// Style references (must reference pre-defined @variables from .ui files)
cmd.set("#Button.Style", Value.ref("Pages/Template.ui", "SelectedStyle"));

// Item display
cmd.set("#Slot.ItemId", "Weapon_Sword_Iron");
```

### Clearing & Removing

```java
// CLEAR: Remove ALL CHILDREN from a container (container itself stays)
cmd.clear("#ObjectivesList");
// Then re-append new children

// REMOVE: Remove the element itself (and all children)
cmd.remove("#Element");
// Exists in API but zero usage in vanilla codebase — use with caution
```

## Selector Syntax

```java
"#ElementId"                          // By ID
"#ElementId.Property"                 // Property of element
"#Parent #Child"                      // Descendant by ID
"#Parent[0]"                          // First appended child (0-indexed)
"#Parent[0].Property"                 // Property of indexed child
"#Parent[0] #Descendant"             // Descendant inside indexed child
"#Parent[0] #Child.Property"          // Property of descendant inside indexed child
"#Grid[row][col]"                     // Multi-level indexing (2D)
"#Grid[row][col] #Cell.TextSpans"     // Nested property in 2D grid
```

**CRITICAL**: Anonymous elements (no `id=`) CANNOT be targeted by any selector. Use `clear()` on the parent to remove them, then rebuild with your own content.

## What CAN Be Set

| Property | Accepts | Set On | Notes |
|----------|---------|--------|-------|
| `.Text` | String | Label | Plain text, no formatting |
| `.TextSpans` | Message | Label | Rich text with color/bold/params |
| `.Visible` | boolean | Any element | Show/hide |
| `.Disabled` | boolean | Buttons, inputs | Disable interaction |
| `.Background` | String | Any with background | Texture path or hex color |
| `.Background.Color` | String | Nested background | Hex color `#RRGGBB` |
| `.Color` | String | Elements with color | Hex color |
| `.Value` | float/boolean | ProgressBar, CheckBox | State value |
| `.Style` | Value\<String\> | TextButton, Label | Reference to @variable in .ui |
| `.ItemId` | String | ItemSlot elements | Item identifier |
| `.AssetPath` | String | Image elements | Texture file path |
| `.TooltipText` | String | Any element | Hover tooltip |

## What CANNOT Be Set

- **Layout properties** (LayoutMode, Anchor, Padding) — fixed at template load
- **Element type** — cannot change Group to Label
- **Children count directly** — use `append()`/`clear()` instead
- **Font properties directly** — use `.Style` with pre-defined references
- **TextButton `.Text`** — **CRASHES CLIENT** (use `.TextSpans` or keep template text)
- **Inline style definitions** — must reference existing `@variables` from `.ui` files

## The `clear()` + Rebuild Pattern

This is the key pattern for replacing content that includes anonymous (no-ID) elements:

```java
// PROBLEM: #Objectives contains an anonymous Label ("OBJECTIVES" header) + #ObjectivesList
// We can't target the anonymous Label, but we CAN:

// 1. Clear ALL children (removes both the anonymous label AND #ObjectivesList)
cmd.clear("#Objectives");

// 2. Rebuild with our own content using appendInline for headers
cmd.appendInline("#Objectives",
    "Label { Text: Prefixes; Style: (FontSize: 16, TextColor: #778292, RenderBold: true, RenderUppercase: true); Anchor: (Bottom: 4); }");

// 3. Add a container for bullet points
cmd.appendInline("#Objectives",
    "Group #MyList { LayoutMode: Top; }");

// 4. Append bullet points into our new container
cmd.append("#MyList", "Pages/Portals/BulletPoint.ui");
cmd.set("#MyList[0] #Label.TextSpans", Message.raw("+40% Monster Damage").color("#FF6666"));
```

**Why this works**: `clear()` removes all children of `#Objectives` but keeps `#Objectives` itself (with its Anchor, LayoutMode, Visible, etc.). Then `appendInline()` lets us inject our own Label with custom text and style — no anonymous targeting needed.

## PortalDeviceSummon.ui Element Map

```
DecoratedContainer
  #Content
    #Panes
      #LeftPane
        #Artwork              .Background = texture path
        #Vignette             .Visible = hover glow
      #RightPane
        #TopStuff
          #Title
            #Title0           .TextSpans = portal/realm name
          [WIP notice]        (anonymous, no ID)
          [Time limit section]
            #ExploTimeBullet
              #ExplorationTimeText  .TextSpans = duration
            #BreachTimeBullet      .Visible = show void breach
              #BreachTimeText      .TextSpans = void duration
          #Objectives              .Visible = show section
            Label (ANONYMOUS)      Text: "OBJECTIVES" (CANNOT TARGET)
            #ObjectivesList        Container for BulletPoint.ui children
          #Tips                    .Visible = show section
            Label (ANONYMOUS)      Text: "WISDOM" (CANNOT TARGET)
            #TipsList              Container for BulletPoint.ui children
          #Pills                   Container for Pill.ui children
          [FlexWeight spacer]      (anonymous)
          #FlavorText
            [separator line]       (anonymous)
            #FlavorLabel           .TextSpans = description text
        #BottomStuff
          #Summon
            [consume label]        (anonymous)
            #SummonButton          TextButton (DO NOT set .Text!)
  @BackButton
```

### Elements with Anonymous Children (Cannot Target Directly)

| Parent | Anonymous Child | Content | Solution |
|--------|----------------|---------|----------|
| `#Objectives` | First Label child | "OBJECTIVES" header | `clear("#Objectives")` then rebuild |
| `#Tips` | First Label child | "WISDOM" header | `clear("#Tips")` then rebuild |
| `#Summon` | First Label child | "Consumes your held Fragment Key!" | Cannot change without clear |
| `#TopStuff` | WIP notice Group | "This feature is under development" | Cannot hide without ID |
| `#FlavorText` | Separator Group | 1px divider line | Stays as-is (fine) |

### Sub-Templates for Dynamic Content

| Template | Path | Use |
|----------|------|-----|
| `BulletPoint.ui` | `Pages/Portals/BulletPoint.ui` | List item with bullet icon + `#Label` |
| `Pill.ui` | `Pages/Portals/Pill.ui` | Badge with `.Background.Color` + `#Label` |

### BulletPoint.ui Structure
```
Group (LayoutMode: Left)
  Group (bullet icon, 8x8, IconBullet.png)
  Label #Label (FontSize: 15, TextColor: #dee2ef)
```

### Pill.ui Structure
```
Group (Left: 6, Height: 12, Background: PillBg.png)
  Label #Label (FontSize: 10, Uppercase, White, Center)
```

## Message API Quick Reference

```java
// Plain text
Message.raw("text")

// Translation key with parameters
Message.translation("server.key").param("name", value)

// Formatting (all mutate + return this)
message.color("#hex")     // Set color
message.bold(true)        // Bold
message.italic(true)      // Italic
message.insert(other)     // Append another Message as child

// Empty (for building from scratch)
Message msg = Message.empty();
msg.insert(Message.raw("Part 1").color("#ff0000"));
msg.insert(Message.raw("Part 2").color("#00ff00"));
```

**Note**: `Message.insert()` MUTATES in place and returns `this`. Both `msg.insert(x)` and `msg = msg.insert(x)` work identically.

## Event Binding

```java
// Button click
events.addEventBinding(CustomUIEventBindingType.Activating, "#Button",
    EventData.of("Action", "DoThing"), false);

// Mouse hover (for visual effects)
events.addEventBinding(CustomUIEventBindingType.MouseEntered, "#Button",
    EventData.of("Action", "Hover"), false);

// Value change (inputs, checkboxes)
events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Input",
    EventData.of("Value", "@#Input.Value"), false);
```

### Data Codec Pattern (for handleDataEvent)
```java
protected static class Data {
    public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
        .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action)
        .add()
        .build();
    String action;
}
```

## Page Lifecycle

```java
// build() — called once when page opens
// handleDataEvent() — called on each UI event
// sendUpdate(cmd, events, false) — send delta changes without full rebuild
// rebuild() — clear and re-run build() from scratch
// close() — close the page
```

## Confirmed Dead Patterns (Client Crashes)

| Pattern | Crash | Alternative |
|---------|-------|------------|
| `cmd.set("#TextButton.Text", ...)` | Immediate crash | Keep template text or use translation key |
| Setting layout properties (Anchor, LayoutMode) at runtime | May crash | Design layout in .ui template |

## History

- **2026-03-29**: Initial documentation. Compiled from decompiled UICommandBuilder, UIEventBuilder, 36 CustomUIPage implementations, native .ui templates, and in-game testing results.
