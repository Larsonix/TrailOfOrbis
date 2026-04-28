# Hytale Native UI Modding Skill

Use this skill for Hytale's native .ui system and the server-side Java UI API.

Important: use native UI only. Do not use HyUI.

## Quick start

1. Read the architecture overview first. See references/overview.md.
2. Put .ui files under src/main/resources/Common/UI/Custom/.
3. Import Common.ui when you want shared styles and components. See references/common-styling.md.
4. Build layout with Anchor, Padding, and LayoutMode. See references/layout.md.
5. Use markup patterns like named expressions, templates, and translations. See references/markup.md.
6. Bind UI events with UIEventBuilder and always sendUpdate after handling events. See references/java-api.md and references/events.md.
7. For assets, use @2x.png and set IncludesAssetPack in manifest. See references/assets-and-packaging.md.
8. If something fails at runtime, check references/troubleshooting.md.

## Reference library

- references/INDEX.md
- references/overview.md
- references/common-styling.md
- references/layout.md
- references/markup.md
- references/type-documentation.md
- references/java-api.md
- references/events.md
- references/assets-and-packaging.md
- references/examples.md
- references/troubleshooting.md

## Project conventions

- .ui base path: Common/UI/Custom/. Relative paths inside .ui are resolved from the file location.
- Use %translation.key in .ui and add the key to the language files under src/main/resources/Server/Languages.
- Use MultipleHUD for multiple HUDs per player. Do not rely on a single CustomUIHud instance.

## Official documentation

- https://hytalemodding.dev/en/docs/official-documentation/custom-ui/common-styling
- https://hytalemodding.dev/en/docs/official-documentation/custom-ui/layout
- https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup
- https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation

## Notes on recent doc updates

- The official type documentation is a generated index. Use it when you need the exact property name, type, or enum values for an element.
- Common.ui is the preferred source for cohesive styling. Import it and reference styles instead of duplicating them.

## Client .ui File Reference

213 client-side `.ui` files are available at `ClientData/Game/Interface/` as real-world examples of Hytale's native UI system. Key files for learning patterns:

| File | What It Shows |
|------|---------------|
| `InGame/EntityUI/CombatText.ui` | Minimal floating text template |
| `InGame/Tooltips/ItemTooltip.ui` | Complex tooltip with sections, icons, stats |
| `InGame/Hud/Hotbar.ui` | Hotbar layout |
| `InGame/Hud/Chat.ui` | Chat window UI |
| `InGame/Hud/Health/` | Health bar HUD |
| `InGame/Pages/Inventory/` | Full inventory page |
| `Common/Buttons/` | Shared button styles |

Search with: `grep "Hotbar" /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/CLIENT_UI_INDEX.txt`

See `docs/hytale-api/client-data.md` for the full client data reference.

---

## Reference: assets-and-packaging.md

# Assets and Packaging

## UI assets

- Images must use the @2x.png suffix.
- Store assets under Common/UI/Custom/.
- Reference them with TexturePath: "MyImage.png".

Example:

```ui
Sprite {
  TexturePath: "Icons/MyIcon.png";
}
```

Files on disk:

- src/main/resources/Common/UI/Custom/Icons/MyIcon@2x.png

## manifest.json

Ensure IncludesAssetPack is enabled so custom UI assets are shipped to clients.

## UIPath rules

Paths are relative to the current .ui file:

- "MyButton.png" resolves next to the .ui file
- "../MyButton.png" goes up one folder


## Reference: common-styling.md

# Common Styling Reference

This document describes the shared UI components and styles defined in `Common.ui`. Use these to create custom UIs that match the base game's visual style.

## Overview

The `Common.ui` file provides shared styles and components that deliver a cohesive UI experience with the core game UI. These are pre-built, battle-tested components you should prefer over creating your own from scratch.

## Location

Common.ui is located at `Common/UI/Custom/Common.ui` within the Hytale pack.

---

## Importing Common.ui

### Direct Import (file in Common/UI/Custom/)

If your .ui file is directly in `Common/UI/Custom/`:

```ui
$Common = "Common.ui";

// Then reference styles and components:
$Common.@TextButton { @Text = "My Button"; }
$Common.@Container { ... }
```

### Relative Import (file in subfolder)

If your custom UI document is in a subfolder of `Common/UI/Custom/`, use relative path traversal:

```ui
$Common = "../Common.ui";
```

For deeper nesting:

```ui
// Two levels deep
$Common = "../../Common.ui";
```

See the [Markup path documentation](markup.md) for more details on path resolution.

---

## Referencing Styles

Once imported, reference styles from Common.ui using the `$Common.@StyleName` syntax:

```ui
$Common = "../Common.ui";

Label {
    Style: $Common.@DefaultLabelStyle;
}

Group {
    ScrollbarStyle: $Common.@DefaultScrollbar;
}
```

---

## Referencing Components (Templates)

Common.ui also provides pre-built component templates:

```ui
$Common = "../Common.ui";

Group #ButtonRow {
    LayoutMode: Left;
    
    // Use the TextButton template
    $Common.@TextButton #SaveButton {
        @Text = "Save";
    }
    
    $Common.@TextButton #CancelButton {
        @Text = "Cancel";
    }
}
```

---

## Common Components and Styles

The exact list of available styles and components can be viewed via the `/ui-gallery` command in-game. (Note: This command is planned for a future patch.)

### Frequently Used Styles

| Style Name | Description |
|------------|-------------|
| `@DefaultLabelStyle` | Standard label text styling |
| `@DefaultButtonStyle` | Standard button styling |
| `@DefaultScrollbar` | Default scrollbar for scrolling groups |

### Frequently Used Components

| Component | Description |
|-----------|-------------|
| `@TextButton` | Primary styled button |
| `@SecondaryTextButton` | Secondary styled button |
| `@TertiaryTextButton` | Tertiary styled button |
| `@CancelTextButton` | Cancel/destructive button |
| `@BackButton` | Back navigation button |
| `@Container` | Styled window frame with title |
| `@PageOverlay` | Full-screen overlay background |
| `@NumberField` | Numeric input field |
| `@AssetImage` | Asset image display |
| `@CheckBoxWithLabel` | Checkbox with text label |

---

## Best Practices

1. **Always import Common.ui** - Use shared styles instead of duplicating them locally
2. **Use relative paths correctly** - Adjust the path based on your file's location relative to `Common/UI/Custom/`
3. **Prefer templates over raw elements** - Use `$Common.@TextButton` instead of building a button from scratch
4. **Check /ui-gallery** - When available, use this command to see live examples of all Common.ui styles

---

## Value References from Java

In Java code, you can reference Common.ui styles:

```java
import com.hypixel.hytale.server.core.ui.Value;

// Reference a style from Common.ui
commands.set("#Element.Style", Value.ref("Common.ui", "DefaultButtonStyle"));
commands.set("#ScrollGroup.ScrollbarStyle", Value.ref("Common.ui", "DefaultScrollbar"));
```

---

Source: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/common-styling


## Reference: events.md

# UI Event Binding Types

Use UIEventBuilder.addEventBinding to bind events to elements in your UI.

## Event Binding Syntax

```java
events.addEventBinding(
    CustomUIEventBindingType.EventType,  // The event type
    "#ElementSelector",                   // Element to bind to
    eventData,                            // Data to send when triggered
    locksInterface                        // Whether to lock UI during processing
);
```

---

## Event Types Reference

### Interaction Events

| Event | Trigger | Common Use |
|-------|---------|------------|
| `Activating` | Element is activated (button click, enter key) | Buttons, clickable elements |
| `RightClicking` | Right mouse button click | Context menus |
| `DoubleClicking` | Double click | Quick actions |
| `MouseEntered` | Mouse cursor enters element bounds | Hover effects, tooltips |
| `MouseExited` | Mouse cursor leaves element bounds | Remove hover effects |
| `MouseButtonReleased` | Mouse button released over element | Drag completion |

### Input Events

| Event | Trigger | Common Use |
|-------|---------|------------|
| `ValueChanged` | Input value changes | TextField, Slider, CheckBox, DropdownBox |
| `FocusGained` | Element receives input focus | Input highlighting |
| `FocusLost` | Element loses input focus | Input validation, save on blur |
| `KeyDown` | Key pressed while element has focus | Keyboard shortcuts, special keys |
| `Validating` | Input validation requested | Form validation |

### Page Events

| Event | Trigger | Common Use |
|-------|---------|------------|
| `Dismissing` | Page dismiss attempt (ESC key, close button) | Confirm dialogs, save prompts |

### Tab Events

| Event | Trigger | Common Use |
|-------|---------|------------|
| `SelectedTabChanged` | Tab selection changes | Tab content switching |

### Item Grid Events

| Event | Trigger | Common Use |
|-------|---------|------------|
| `SlotClicking` | ItemGrid slot clicked | Item selection, item actions |
| `SlotDoubleClicking` | ItemGrid slot double-clicked | Quick equip, quick transfer |
| `SlotMouseEntered` | Mouse enters slot | Slot hover, tooltip display |
| `SlotMouseExited` | Mouse leaves slot | Remove tooltips |

### Drag and Drop Events

| Event | Trigger | Common Use |
|-------|---------|------------|
| `DragCancelled` | Drag operation cancelled | Reset drag state |
| `Dropped` | Item dropped on element | Item transfer, placement |
| `SlotMouseDragCompleted` | Drag completed over a slot | Item move between slots |
| `SlotMouseDragExited` | Drag exited a slot | Visual feedback |
| `SlotClickReleaseWhileDragging` | Click released while dragging | Split stacks, drop items |
| `SlotClickPressWhileDragging` | Click pressed while dragging | Multi-select |

### Layout Events

| Event | Trigger | Common Use |
|-------|---------|------------|
| `ElementReordered` | Element order changed in ReorderableList | List sorting |

---

## Usage Patterns

### Button Click

```java
events.addEventBinding(
    CustomUIEventBindingType.Activating,
    "#SaveButton",
    EventData.of("Action", "save"),
    false
);
```

### Text Input Change

Use the `@` prefix in the EventData key to pull the value from the UI element:

```java
events.addEventBinding(
    CustomUIEventBindingType.ValueChanged,
    "#SearchField",
    EventData.of("@Query", "#SearchField.Value"),
    false
);
```

In your EventData class, the key `@Query` will receive the current value of `#SearchField.Value`.

### Slider Value Change

```java
events.addEventBinding(
    CustomUIEventBindingType.ValueChanged,
    "#VolumeSlider",
    EventData.of("@Volume", "#VolumeSlider.Value"),
    false
);
```

### Item Grid Slot Click

```java
events.addEventBinding(
    CustomUIEventBindingType.SlotClicking,
    "#InventoryGrid",
    EventData.of("@SlotIndex", "#InventoryGrid.SelectedSlotIndex"),
    false
);
```

### Tab Selection

```java
events.addEventBinding(
    CustomUIEventBindingType.SelectedTabChanged,
    "#TabNav",
    EventData.of("@Tab", "#TabNav.SelectedTab"),
    false
);
```

### Page Dismiss Confirmation

```java
events.addEventBinding(
    CustomUIEventBindingType.Dismissing,
    "#Root",  // Or the page root element
    EventData.of("Action", "dismiss"),
    false
);
```

In handleDataEvent, you can prevent dismissal by not closing the page and showing a confirmation dialog instead.

---

## Value References

The `@` prefix in EventData keys indicates that the value should be pulled from a UI element property:

```java
EventData.of("@Key", "#ElementId.Property")
```

| Syntax | Meaning |
|--------|---------|
| `"Action"` | Static key, value comes from Java code |
| `"@Value"` | Dynamic key, value comes from UI element specified in the second parameter |

### Common Value References

| Reference | Source |
|-----------|--------|
| `#TextField.Value` | Text field current value |
| `#Slider.Value` | Slider current value |
| `#CheckBox.Value` | Checkbox checked state |
| `#DropdownBox.Value` | Selected dropdown value |
| `#ItemGrid.SelectedSlotIndex` | Selected slot index |

---

## Event Data Class Pattern

```java
public static class EventData {
    public static final BuilderCodec<EventData> CODEC = BuilderCodec.builder(EventData.class, EventData::new)
        // Static fields
        .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action)
        .add()
        // Dynamic fields (@ prefix means value from UI)
        .append(new KeyedCodec<>("@Query", Codec.STRING), (e, s) -> e.query = s, e -> e.query)
        .add()
        .append(new KeyedCodec<>("@SlotIndex", Codec.INT), (e, i) -> e.slotIndex = i, e -> e.slotIndex)
        .add()
        .build();

    String action;
    String query;
    int slotIndex;
    
    public static EventData of(String key, String value) {
        EventData data = new EventData();
        // Map keys to fields...
        return data;
    }
}
```

---

## Important Notes

1. **Always call sendUpdate after handling events** in InteractiveCustomUIPage
2. **Value references (@) pull current values** from the UI at the time the event fires
3. **locksInterface parameter**: Set to `true` if you need to prevent user interaction during processing
4. **Selector must match element ID** exactly (with # prefix)


## Reference: examples.md

# Complete UI Examples

This document provides full, working examples of common UI patterns.

---

## Example 1: Simple HUD

A basic HUD that displays a status message.

### Hud/SimpleHud.ui

```ui
$Common = "../Common.ui";

Group #Root {
    Anchor: (Bottom: 100, Left: 20, Width: 200, Height: 40);
    Background: PatchStyle(Color: #1a1a2eD0);
    Padding: (Full: 8);
    LayoutMode: CenterMiddle;
    
    Label #StatusText {
        Text: "Status: Ready";
        Style: (FontSize: 14, TextColor: #ffffff);
    }
}
```

### Java Implementation

```java
public class SimpleHud extends CustomUIHud {
    private String statusText = "Status: Ready";
    
    public SimpleHud(PlayerRef playerRef) {
        super(playerRef);
    }
    
    @Override
    protected void build(UICommandBuilder cmd) {
        cmd.append("Hud/SimpleHud.ui");
    }
    
    public void setStatus(String text) {
        this.statusText = text;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#StatusText.Text", text);
        sendUpdate(cmd);
    }
}
```

---

## Example 2: Interactive Dialog Page

A confirmation dialog with OK/Cancel buttons.

### Pages/ConfirmDialog.ui

```ui
$Common = "../Common.ui";

Group #Root {
    Anchor: (Full: 0);
    Background: PatchStyle(Color: #000000(0.6));
    LayoutMode: CenterMiddle;
    
    Group #Dialog {
        Anchor: (Width: 400, Height: 200);
        Background: PatchStyle(Color: #1a1a2eF0, Border: 4);
        LayoutMode: Top;
        Padding: (Full: 20);
        
        Label #Title {
            Text: %ui.confirm.title;
            Style: (FontSize: 20, RenderBold: true, HorizontalAlignment: Center);
            Anchor: (Height: 32);
        }
        
        Label #Message {
            Text: %ui.confirm.message;
            Style: (FontSize: 14, HorizontalAlignment: Center, Wrap: true);
            Anchor: (Height: 60);
        }
        
        Group #Spacer {
            FlexWeight: 1;
        }
        
        Group #ButtonRow {
            LayoutMode: CenterMiddle;
            Anchor: (Height: 50);
            
            Button #CancelButton {
                Text: %ui.general.cancel;
                Anchor: (Width: 120, Height: 36, Right: 10);
                Style: $Common.@SecondaryButtonStyle;
            }
            
            Button #OkButton {
                Text: %ui.general.ok;
                Anchor: (Width: 120, Height: 36);
                Style: $Common.@PrimaryButtonStyle;
            }
        }
    }
}
```

### Java Implementation

```java
public class ConfirmDialog extends InteractiveCustomUIPage<ConfirmDialog.EventData> {
    
    private final String titleKey;
    private final String messageKey;
    private final Runnable onConfirm;
    
    public ConfirmDialog(PlayerRef playerRef, String titleKey, String messageKey, Runnable onConfirm) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventData.CODEC);
        this.titleKey = titleKey;
        this.messageKey = messageKey;
        this.onConfirm = onConfirm;
    }
    
    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/ConfirmDialog.ui");
        
        // Set dynamic text if needed
        if (titleKey != null) {
            cmd.set("#Title.Text", "%" + titleKey);
        }
        if (messageKey != null) {
            cmd.set("#Message.Text", "%" + messageKey);
        }
        
        // Bind button events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#OkButton",
            EventData.action("confirm"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
            EventData.action("cancel"), false);
    }
    
    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, EventData data) {
        if ("confirm".equals(data.action)) {
            if (onConfirm != null) {
                onConfirm.run();
            }
            close(ref, store);
            return;
        }
        if ("cancel".equals(data.action)) {
            close(ref, store);
            return;
        }
        sendUpdate(null, false);
    }
    
    private void close(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        player.getPageManager().setPage(ref, store, Page.None);
    }
    
    public static class EventData {
        public static final BuilderCodec<EventData> CODEC = BuilderCodec.builder(EventData.class, EventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action)
            .add()
            .build();
        
        String action;
        
        public static EventData action(String action) {
            EventData data = new EventData();
            data.action = action;
            return data;
        }
    }
}
```

---

## Example 3: Search Page with Dynamic Results

A page with a search field that updates results dynamically.

### Pages/SearchPage.ui

```ui
$Common = "../Common.ui";

@ResultItem = Group {
    Anchor: (Height: 40);
    LayoutMode: Left;
    Padding: (Horizontal: 10, Vertical: 5);
    Background: PatchStyle(Color: #2a2a3e);
    
    Label #Name {
        Text: @ItemName;
        Style: (FontSize: 14);
        Anchor: (Width: 200);
    }
    
    Label #Description {
        Text: @ItemDescription;
        Style: (FontSize: 12, TextColor: #aaaaaa);
        FlexWeight: 1;
    }
    
    Button #SelectButton {
        Text: "Select";
        Anchor: (Width: 80, Height: 30);
    }
};

Group #Root {
    Anchor: (Full: 0);
    Background: PatchStyle(Color: #000000(0.7));
    LayoutMode: CenterMiddle;
    
    Group #Container {
        Anchor: (Width: 600, Height: 500);
        Background: PatchStyle(Color: #1a1a2eF0, Border: 4);
        LayoutMode: Top;
        Padding: (Full: 16);
        
        Label #Title {
            Text: %ui.search.title;
            Style: (FontSize: 22, RenderBold: true);
            Anchor: (Height: 36);
        }
        
        Group #SearchRow {
            LayoutMode: Left;
            Anchor: (Height: 40, Bottom: 10);
            
            TextField #SearchInput {
                PlaceholderText: %ui.search.placeholder;
                Anchor: (Height: 36);
                FlexWeight: 1;
            }
            
            Button #ClearButton {
                Text: "X";
                Anchor: (Width: 36, Height: 36, Left: 8);
            }
        }
        
        Group #ResultsContainer {
            FlexWeight: 1;
            LayoutMode: TopScrolling;
            ScrollbarStyle: $Common.@DefaultScrollbar;
            
            // Results will be dynamically added here
        }
        
        Group #Footer {
            LayoutMode: Right;
            Anchor: (Height: 50, Top: 10);
            
            Label #ResultCount {
                Text: "0 results";
                Style: (FontSize: 12, TextColor: #888888);
                Anchor: (Width: 100);
            }
            
            Group #Spacer { FlexWeight: 1; }
            
            Button #CloseButton {
                Text: %ui.general.close;
                Anchor: (Width: 100, Height: 36);
            }
        }
    }
}
```

### Java Implementation

```java
public class SearchPage extends InteractiveCustomUIPage<SearchPage.EventData> {
    
    private final List<SearchResult> allResults;
    private List<SearchResult> filteredResults;
    
    public SearchPage(PlayerRef playerRef, List<SearchResult> results) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventData.CODEC);
        this.allResults = results;
        this.filteredResults = new ArrayList<>(results);
    }
    
    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/SearchPage.ui");
        
        // Build initial results
        buildResults(cmd, events);
        
        // Bind events
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
            EventData.of("@Query", "#SearchInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearButton",
            EventData.of("Action", "clear"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "close"), false);
    }
    
    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, EventData data) {
        if ("close".equals(data.action)) {
            close(ref, store);
            return;
        }
        
        if ("clear".equals(data.action)) {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#SearchInput.Value", "");
            filteredResults = new ArrayList<>(allResults);
            rebuildResults(cmd);
            sendUpdate(cmd, false);
            return;
        }
        
        if ("select".equals(data.action) && data.itemId != null) {
            handleSelection(data.itemId);
            close(ref, store);
            return;
        }
        
        if (data.query != null) {
            filterResults(data.query);
            UICommandBuilder cmd = new UICommandBuilder();
            rebuildResults(cmd);
            sendUpdate(cmd, false);
            return;
        }
        
        sendUpdate(null, false);
    }
    
    private void filterResults(String query) {
        if (query == null || query.isEmpty()) {
            filteredResults = new ArrayList<>(allResults);
        } else {
            String lowerQuery = query.toLowerCase();
            filteredResults = allResults.stream()
                .filter(r -> r.name().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
        }
    }
    
    private void buildResults(UICommandBuilder cmd, UIEventBuilder events) {
        for (int i = 0; i < filteredResults.size(); i++) {
            SearchResult result = filteredResults.get(i);
            String id = "Result" + i;
            
            cmd.appendInline("#ResultsContainer", String.format(
                "@ResultItem #%s { @ItemName = \"%s\"; @ItemDescription = \"%s\"; }",
                id, escapeString(result.name()), escapeString(result.description())
            ));
            
            events.addEventBinding(CustomUIEventBindingType.Activating,
                "#" + id + " #SelectButton",
                EventData.select(result.id()), false);
        }
        
        cmd.set("#ResultCount.Text", filteredResults.size() + " results");
    }
    
    private void rebuildResults(UICommandBuilder cmd) {
        cmd.clear("#ResultsContainer");
        UIEventBuilder events = new UIEventBuilder();
        buildResults(cmd, events);
        // Note: In a full implementation, you'd need to send events too
    }
    
    private String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    private void handleSelection(String itemId) {
        // Handle the selection
    }
    
    private void close(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        player.getPageManager().setPage(ref, store, Page.None);
    }
    
    public static class EventData {
        public static final BuilderCodec<EventData> CODEC = BuilderCodec.builder(EventData.class, EventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action)
            .add()
            .append(new KeyedCodec<>("@Query", Codec.STRING), (e, s) -> e.query = s, e -> e.query)
            .add()
            .append(new KeyedCodec<>("ItemId", Codec.STRING), (e, s) -> e.itemId = s, e -> e.itemId)
            .add()
            .build();
        
        String action;
        String query;
        String itemId;
        
        public static EventData of(String key, String value) {
            EventData data = new EventData();
            if ("Action".equals(key)) data.action = value;
            return data;
        }
        
        public static EventData select(String itemId) {
            EventData data = new EventData();
            data.action = "select";
            data.itemId = itemId;
            return data;
        }
    }
    
    public record SearchResult(String id, String name, String description) {}
}
```

---

## Example 4: Item Grid Inventory

An inventory page with an item grid.

### Pages/Inventory.ui

```ui
$Common = "../Common.ui";

Group #Root {
    Anchor: (Full: 0);
    Background: PatchStyle(Color: #000000(0.6));
    LayoutMode: CenterMiddle;
    
    Group #Container {
        Anchor: (Width: 450, Height: 400);
        Background: PatchStyle(Color: #1a1a2eF0, Border: 4);
        LayoutMode: Top;
        Padding: (Full: 16);
        
        Label #Title {
            Text: %ui.inventory.title;
            Style: (FontSize: 20, RenderBold: true);
            Anchor: (Height: 32, Bottom: 12);
        }
        
        ItemGrid #InventoryGrid {
            FlexWeight: 1;
            SlotsPerRow: 8;
            AreItemsDraggable: false;
            ShowScrollbar: true;
            KeepScrollPosition: true;
            RenderItemQualityBackground: true;
            Style: $Common.@DefaultItemGridStyle;
        }
        
        Group #Footer {
            LayoutMode: Right;
            Anchor: (Height: 50, Top: 12);
            
            Button #CloseButton {
                Text: %ui.general.close;
                Anchor: (Width: 100, Height: 36);
            }
        }
    }
}
```

### Java Implementation

```java
public class InventoryPage extends InteractiveCustomUIPage<InventoryPage.EventData> {
    
    private final List<ItemStack> items;
    
    public InventoryPage(PlayerRef playerRef, List<ItemStack> items) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventData.CODEC);
        this.items = items;
    }
    
    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/Inventory.ui");
        
        // Build item grid slots
        ItemGridSlot[] slots = items.stream()
            .map(item -> new ItemGridSlot()
                .setItemStack(item)
                .setActivatable(true))
            .toArray(ItemGridSlot[]::new);
        
        cmd.setObject("#InventoryGrid.Slots", slots);
        
        // Bind events
        events.addEventBinding(CustomUIEventBindingType.SlotClicking, "#InventoryGrid",
            EventData.of("@SlotIndex", "#InventoryGrid.SelectedSlotIndex"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "close"), false);
    }
    
    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, EventData data) {
        if ("close".equals(data.action)) {
            close(ref, store);
            return;
        }
        
        if (data.slotIndex >= 0 && data.slotIndex < items.size()) {
            ItemStack selectedItem = items.get(data.slotIndex);
            handleItemClick(selectedItem, data.slotIndex);
        }
        
        sendUpdate(null, false);
    }
    
    private void handleItemClick(ItemStack item, int slotIndex) {
        // Handle item selection
    }
    
    private void close(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        player.getPageManager().setPage(ref, store, Page.None);
    }
    
    public static class EventData {
        public static final BuilderCodec<EventData> CODEC = BuilderCodec.builder(EventData.class, EventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action)
            .add()
            .append(new KeyedCodec<>("@SlotIndex", Codec.INT), (e, i) -> e.slotIndex = i, e -> e.slotIndex)
            .add()
            .build();
        
        String action;
        int slotIndex = -1;
        
        public static EventData of(String key, String value) {
            EventData data = new EventData();
            if ("Action".equals(key)) data.action = value;
            return data;
        }
    }
}
```

---

## Key Patterns Summary

1. **Always import Common.ui** for consistent styling
2. **Use templates (@Name)** for reusable UI components
3. **Use translation keys (%key)** for all user-facing text
4. **Use @ prefix in EventData** for dynamic value references
5. **Always call sendUpdate()** after handling events
6. **Escape strings** when building inline UI dynamically
7. **Use namespaced IDs** for HUDs with MultipleHUD
8. **Run UI operations on world thread**


## Reference: java-api.md

# Java UI API Reference

This project uses the native Hytale UI Java API. This reference covers the core classes and patterns for building custom UIs.

> **Important**: This project uses native Hytale UI only. Do not use HyUI library.

---

## Class Overview

| Class | Purpose |
|-------|---------|
| `CustomUIHud` | Persistent overlay elements (always visible) |
| `MultipleHUD` | Library enabling multiple simultaneous HUDs per player |
| `CustomUIPage` | Static full-screen modal pages |
| `InteractiveCustomUIPage<T>` | Interactive pages with event handling |
| `UICommandBuilder` | Java API for building/modifying UI |
| `UIEventBuilder` | Java API for binding events |

---

## CustomUIHud

CustomUIHud is used for persistent overlay elements that remain visible while the player plays.

**Important:** Hytale only supports one CustomUIHud per player by default. Use MultipleHUD when you need more than one HUD.

### Basic HUD Implementation

```java
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class MyHud extends CustomUIHud {
    
    public MyHud(PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(UICommandBuilder commandBuilder) {
        // Append a .ui file
        commandBuilder.append("Hud/MyHud.ui");
        
        // Or use inline UI:
        commandBuilder.appendInline(null, "Label #Status { Text: \"Hello\"; }");
    }
}
```

### Showing a HUD

```java
MyHud hud = new MyHud(playerRef);
hud.show();
```

### Updating a HUD

```java
// After building, you can send updates
UICommandBuilder commands = new UICommandBuilder();
commands.set("#Status.Text", "Updated text");
hud.sendUpdate(commands);
```

---

## MultipleHUD (MHUD)

By default, Hytale only allows **one** CustomUIHud per player. The MultipleHUD library (by Buuz135) provides a wrapper that allows multiple HUD elements simultaneously.

**Dependency:** Already included in project via CurseForge Maven (`com.buuz135:MultipleHUD:1.0.2`)

### Basic Usage

```java
import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

// Add/replace a HUD with a unique identifier
MultipleHUD.getInstance().setCustomHud(player, playerRef, "MyHudId", new MyCustomHud(playerRef));

// Add multiple HUDs
MultipleHUD.getInstance().setCustomHud(player, playerRef, "HealthBar", new HealthBarHud(playerRef));
MultipleHUD.getInstance().setCustomHud(player, playerRef, "Buffs", new BuffDisplayHud(playerRef));
MultipleHUD.getInstance().setCustomHud(player, playerRef, "Minimap", new MinimapHud(playerRef));

// Remove a specific HUD by identifier
MultipleHUD.getInstance().hideCustomHud(player, playerRef, "MyHudId");

// Replace a HUD (same identifier replaces existing)
MultipleHUD.getInstance().setCustomHud(player, playerRef, "HealthBar", new NewHealthBarHud(playerRef));
```

### MHUD API Reference

| Method | Description |
|--------|-------------|
| `MultipleHUD.getInstance()` | Get the singleton instance |
| `setCustomHud(player, playerRef, id, hud)` | Add or replace a HUD by identifier |
| `hideCustomHud(player, playerRef, id)` | Remove a HUD by identifier |

### How MultipleHUD Works

MHUD creates a wrapper `MultipleCustomUIHud` that contains a root group `#MultipleHUD`. Each individual HUD is added as a child group with ID `#<normalizedId>`. The library automatically:

- Converts HUD identifiers to valid element IDs (strips non-alphanumeric chars)
- Prefixes all selectors in your HUD with the container path
- Handles build/update lifecycle for each HUD independently

### Empty HUD Placeholder

Use `EmptyHUD` as a placeholder when you need a HUD slot but no content:

```java
import com.buuz135.mhud.EmptyHUD;

// Create an empty placeholder
MultipleHUD.getInstance().setCustomHud(player, playerRef, "Placeholder", new EmptyHUD(playerRef));
```

### Recommended ECS Pattern for HUD Systems

When creating HUD systems for Hyforged, follow this pattern (used by `CurrencyHudSystem`, `ResourceStatsHudSystem`, `CombatLogHudSystem`):

```java
public class MyHudSystem extends DelayedEntitySystem<EntityStore> {
    
    /** Check for MHUD availability at class load */
    private static final boolean MULTIPLE_HUD_AVAILABLE;
    static {
        boolean available = false;
        try {
            Class.forName("com.buuz135.mhud.MultipleHUD");
            available = true;
        } catch (ClassNotFoundException e) {
            LOGGER.warning("MultipleHUD not available - HUD disabled");
        }
        MULTIPLE_HUD_AVAILABLE = available;
    }
    
    /** Unique namespaced ID for this HUD */
    public static final String HUD_ID = "hyforged:my_hud";
    
    /** Track HUD instances per player */
    private static final Map<UUID, MyHud> playerHuds = new ConcurrentHashMap<>();
    
    @Override
    public void tick(...) {
        if (!MULTIPLE_HUD_AVAILABLE) return;
        
        UUID playerUuid = uuidComponent.getUuid();
        boolean shouldShowHud = /* your logic */;
        
        com.buuz135.mhud.MultipleHUD multipleHUD = com.buuz135.mhud.MultipleHUD.getInstance();
        MyHud existingHud = playerHuds.get(playerUuid);
        
        if (!shouldShowHud) {
            if (existingHud != null) {
                multipleHUD.hideCustomHud(player, playerRef, HUD_ID);
                playerHuds.remove(playerUuid);
            }
            return;
        }
        
        // Create HUD if not exists
        if (existingHud == null) {
            MyHud hud = new MyHud(playerRef);
            multipleHUD.setCustomHud(player, playerRef, HUD_ID, hud);
            playerHuds.put(playerUuid, hud);
            existingHud = hud;
        }
        
        // Update HUD with new values
        existingHud.updateValues(...);
    }
}
```

**Key Points:**
- Use `DelayedEntitySystem` to avoid updating every tick
- Check `MULTIPLE_HUD_AVAILABLE` before any MHUD calls
- Use namespaced HUD IDs like `"hyforged:my_hud"`
- Track HUD instances per player UUID
- Hide HUD before removing from tracking map
- Only create new HUD if one doesn't exist for the player

---

## CustomUIPage

CustomUIPage is used for static full-screen modal pages.

### Basic Page Implementation

```java
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

public class MyPage extends CustomUIPage {
    
    public MyPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/MyPage.ui");
    }
}
```

### Opening a Page

```java
Player player = store.getComponent(ref, Player.getComponentType());
player.getPageManager().setPage(ref, store, new MyPage(playerRef));
```

### Page Lifetime Options

```java
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

CustomPageLifetime.CanDismiss   // Player can close with ESC
CustomPageLifetime.Dismiss      // Closes immediately (not typically used)
```

---

## InteractiveCustomUIPage<T>

InteractiveCustomUIPage is used for pages with event handling. It uses a generic type parameter for the event data class.

### Complete Interactive Page Implementation

```java
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.Codec;

public class MyInteractivePage extends InteractiveCustomUIPage<MyInteractivePage.EventData> {

    public MyInteractivePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventData.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/MyPage.ui");
        
        // Bind button click event
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#MyButton",
            EventData.of("Action", "buttonClicked"),
            false  // locksInterface - if true, locks UI during processing
        );
        
        // Bind text input value change
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SearchInput",
            EventData.of("@SearchValue", "#SearchInput.Value"),  // @ prefix = UI value reference
            false
        );
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, EventData data) {
        if ("buttonClicked".equals(data.action)) {
            // Handle button click
        }
        if (data.searchValue != null) {
            // Handle search input change
            updateSearchResults(data.searchValue);
        }
        // IMPORTANT: Always call sendUpdate after handling events
        sendUpdate(null, false);
    }
    
    private void updateSearchResults(String query) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.clear("#Results");
        // Build updated content...
        sendUpdate(commands, events, false);
    }

    // Event data class with codec
    public static class EventData {
        public static final BuilderCodec<EventData> CODEC = BuilderCodec.builder(EventData.class, EventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action)
            .add()
            .append(new KeyedCodec<>("@SearchValue", Codec.STRING), (e, s) -> e.searchValue = s, e -> e.searchValue)
            .add()
            .build();

        String action;
        String searchValue;
        
        public static EventData of(String key, String value) {
            EventData data = new EventData();
            if (key.equals("Action")) data.action = value;
            else if (key.equals("@SearchValue")) data.searchValue = value;
            return data;
        }
    }
}
```

### Critical: Always Call sendUpdate

After handling events in `handleDataEvent`, you **must** call `sendUpdate`:

```java
@Override
public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, EventData data) {
    // Handle your logic here...
    
    // ALWAYS call sendUpdate at the end
    sendUpdate(null, false);  // null for no UI changes, false for not closing
}
```

If you forget `sendUpdate`, the page will get stuck on "Loading...".

### Closing a Page

```java
private void close(Ref<EntityStore> ref, Store<EntityStore> store) {
    Player player = store.getComponent(ref, Player.getComponentType());
    player.getPageManager().setPage(ref, store, Page.None);
}
```

---

## UICommandBuilder

UICommandBuilder is used to construct UI modifications.

### Methods

```java
UICommandBuilder commands = new UICommandBuilder();

// Append .ui file content
commands.append("Pages/MyPage.ui");              // Append to root
commands.append("#Container", "Pages/Item.ui"); // Append to selector

// Append inline UI content
// IMPORTANT: Text values MUST be quoted in inline .ui syntax
commands.appendInline("#List", "Label { Text: \"Item\"; }");

// Insert before element
commands.insertBefore("#Target", "Pages/Header.ui");
commands.insertBeforeInline("#Target", "Label { Text: \"Before\"; }");

// Set properties
commands.set("#Label.Text", "Hello World");
commands.set("#Label.Visible", true);
commands.set("#Slider.Value", 50);
commands.set("#Progress.Value", 0.75f);

// Set complex objects
commands.setObject("#Element.Anchor", new Anchor().setWidth(Value.of(200)));
commands.setObject("#Grid.Slots", new ItemGridSlot[]{ new ItemGridSlot(itemStack) });

// Set with value reference (reference styles from Common.ui)
commands.set("#Button.Style", Value.ref("Common.ui", "DefaultButtonStyle"));

// Remove/clear
commands.remove("#Element");     // Remove element
commands.clear("#Container");    // Clear children
commands.setNull("#Label.Text"); // Set to null
```

### Selector Syntax

Selectors target elements by their ID and optionally their properties:

| Selector | Meaning |
|----------|----------|
| `#ElementId` | Target element by ID |
| `#ElementId.Property` | Target element's property |
| `#Parent #Child` | Nested element selection |
| `#List[0]` | First child of element "List" (indexed access) |
| `#List[0] #Title` | Element "Title" within the first child of "List" |

---

## UIEventBuilder

UIEventBuilder is used to bind UI events to handler methods.

### Basic Event Binding

```java
UIEventBuilder events = new UIEventBuilder();

// Basic event binding (no data)
events.addEventBinding(CustomUIEventBindingType.Activating, "#Button");

// With data payload
events.addEventBinding(
    CustomUIEventBindingType.Activating,
    "#Button",
    EventData.of("Action", "save"),
    false  // locksInterface
);

// Value reference (gets value from UI element)
events.addEventBinding(
    CustomUIEventBindingType.ValueChanged,
    "#TextField",
    EventData.of("@Value", "#TextField.Value"),  // @ prefix = UI value reference
    false
);
```

### Event Binding Parameters

| Parameter | Description |
|-----------|-------------|
| Event type | Type of event to listen for (see events.md) |
| Selector | Element ID to attach the event to |
| Data | Event data to send when triggered |
| locksInterface | If true, locks the UI during event processing |

See [events.md](events.md) for the complete list of event types.

---

## Threading (CRITICAL)

**UI operations MUST run on the world thread** or the game will crash.

### For Commands

```java
public class MyCommand extends AbstractAsyncCommand {
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        if (context.sender() instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                // Open page on world thread
                player.getPageManager().setPage(ref, store, new MyPage(playerRef));
            }, world);
        }
        return CompletableFuture.completedFuture(null);
    }
}
```

### For HUDs

```java
World world = store.getExternalData().getWorld();
world.execute(() -> {
    MyHud hud = new MyHud(playerRef);
    hud.show();
});
```

---

## Value Objects Reference

### ItemGridSlot

```java
new ItemGridSlot()
    .setItemStack(new ItemStack(itemId, quantity))
    .setBackground(Value.of(patchStyle))
    .setOverlay(Value.of(overlayStyle))
    .setIcon(Value.of(iconStyle))
    .setName("Custom Name")
    .setDescription("Custom description")
    .setItemIncompatible(false)
    .setActivatable(true)
    .setItemUncraftable(false);
```

### DropdownEntryInfo

```java
new DropdownEntryInfo(LocalizableString.fromString("Option 1"), "value1")
```

### LocalizableString

```java
// Plain string
LocalizableString.fromString("Hello World")

// Localization key
LocalizableString.fromMessageId("server.ui.myKey")

// With parameters
LocalizableString.fromMessageId("server.ui.greeting", Map.of("name", playerName))
```

---

## Checklist

1. ✅ Place .ui files in `resources/Common/UI/Custom/`
2. ✅ Add `"IncludesAssetPack": true` to `manifest.json`
3. ✅ Image files must end with `@2x.png`
4. ✅ Run UI operations on world thread
5. ✅ Call `sendUpdate()` after handling events in InteractiveCustomUIPage
6. ✅ Use proper selectors with `#` prefix
7. ✅ Use MultipleHUD for multiple HUDs per player
8. ✅ Use namespaced IDs for HUDs (e.g., `hyforged:my_hud`)


## Reference: layout.md

# Layout Reference

The layout system determines how UI elements are positioned and sized on screen. Understanding layout is crucial for creating well-structured, responsive interfaces.

## Layout Fundamentals

Every UI element has four key layout concepts:

1. **Container Rectangle** - The space allocated by the parent element
2. **Anchor** - How the element positions and sizes itself within the container
3. **Padding** - Inner spacing that affects where children are positioned
4. **LayoutMode** - How the element arranges its children (if it's a container)

Visual representation:

```
┌─────────────────────────────────────┐
│  Container Rectangle (from parent)  │
│  ┌───────────────────────────────┐  │
│  │  Anchored Rectangle           │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │ Padding                 │  │  │
│  │  │  ┌───────────────────┐  │  │  │
│  │  │  │  Content Area     │  │  │  │
│  │  │  └───────────────────┘  │  │  │
│  │  └─────────────────────────┘  │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

---

## Anchor

Anchor controls how an element positions and sizes itself within its container rectangle.

### Anchor Properties

| Property | Type | Description |
|----------|------|-------------|
| `Left` | int | Distance from container's left edge |
| `Right` | int | Distance from container's right edge |
| `Top` | int | Distance from container's top edge |
| `Bottom` | int | Distance from container's bottom edge |
| `Width` | int | Fixed width in pixels |
| `Height` | int | Fixed height in pixels |
| `MinWidth` | int | Minimum width constraint |
| `MaxWidth` | int | Maximum width constraint |
| `MinHeight` | int | Minimum height constraint |
| `MaxHeight` | int | Maximum height constraint |

### Shorthand Properties

| Shorthand | Expands To |
|-----------|------------|
| `Full` | Left, Top, Right, Bottom (all sides) |
| `Horizontal` | Left and Right |
| `Vertical` | Top and Bottom |

### Fixed Size

Creates an element with explicit dimensions:

```ui
Button {
    Anchor: (Width: 200, Height: 40);
}
```

Result: A 200×40 pixel button.

### Positioning

Position an element at specific offsets from the container edges:

```ui
Label {
    Anchor: (Top: 10, Left: 20, Width: 100, Height: 30);
}
```

- Top: 10 pixels from container's top edge
- Left: 20 pixels from container's left edge
- Width: 100 pixels wide
- Height: 30 pixels tall

### Anchoring to Edges

Anchor to bottom-right corner:

```ui
Button {
    Anchor: (Bottom: 10, Right: 10, Width: 100, Height: 30);
}
```

Anchors the button 10 pixels from the bottom and right edges.

### Stretching

Fill the entire container:

```ui
Group {
    Anchor: (Top: 0, Bottom: 0, Left: 0, Right: 0);
}
```

Or use the shorthand:

```ui
Group {
    Anchor: (Full: 0);
}
```

Stretch with margins:

```ui
Group {
    Anchor: (Full: 10);
}
```

This creates 10 pixels of margin on all sides.

### Mixed Anchoring

Combine fixed dimensions with stretching:

```ui
Panel {
    Anchor: (Top: 10, Bottom: 10, Left: 20, Width: 300);
}
```

- Fixed width of 300 pixels
- Stretches vertically between top and bottom edges
- 10 pixels from top and bottom
- 20 pixels from left

---

## Padding

Padding creates inner spacing, affecting where children are positioned.

### Uniform Padding

Apply the same padding to all sides:

```ui
Group {
    Padding: (Full: 20);
}
```

Result: 20 pixels of padding on all sides.

### Directional Padding

Different padding per edge:

```ui
Group {
    Padding: (Top: 10, Bottom: 20, Left: 15, Right: 15);
}
```

### Shorthand

Combine horizontal and vertical:

```ui
Group {
    Padding: (Horizontal: 20, Vertical: 10);
}
// Equivalent to:
// Top: 10, Bottom: 10, Left: 20, Right: 20
```

### Effect on Children

When a child uses `Anchor: (Full: 0)`, it fills the parent but respects padding:

```ui
Group {
    Anchor: (Width: 200, Height: 100);
    Padding: (Full: 10);
    Label {
        Anchor: (Full: 0);
    }
}
```

Visual result:

```
┌──────────────────────┐
│ Group (200×100)      │
│  ┌────────────────┐  │
│  │ Label          │  │ ← 10px padding all around
│  │                │  │
│  └────────────────┘  │
└──────────────────────┘
```

---

## LayoutMode

LayoutMode determines how a container arranges its children.

### Top (Vertical Stack)

Children stack vertically from top to bottom:

```ui
Group {
    LayoutMode: Top;
    Button { Anchor: (Height: 30); }
    Button { Anchor: (Height: 30); }
    Button { Anchor: (Height: 30); }
}
```

Result:

```
┌──────────┐
│ Button 1 │
├──────────┤
│ Button 2 │
├──────────┤
│ Button 3 │
└──────────┘
```

**Spacing:** Use `Anchor.Bottom` to add spacing after each element:

```ui
Button { Anchor: (Height: 30, Bottom: 10); }  // 10px gap after this button
```

### Bottom (Vertical Stack, Bottom-Aligned)

Children stack vertically but align to the bottom edge:

```ui
Group {
    LayoutMode: Bottom;
    Button { Anchor: (Height: 30); }
    Button { Anchor: (Height: 30); }
    Button { Anchor: (Height: 30); }
}
```

### Left (Horizontal Stack)

Children arrange horizontally from left to right:

```ui
Group {
    LayoutMode: Left;
    Button { Anchor: (Width: 80); }
    Button { Anchor: (Width: 80); }
    Button { Anchor: (Width: 80); }
}
```

Result:

```
┌────────┬────────┬────────┐
│ Button │ Button │ Button │
│   1    │   2    │   3    │
└────────┴────────┴────────┘
```

**Spacing:** Use `Anchor.Right` for spacing between elements.

### Right (Horizontal Stack, Right-Aligned)

Children arrange horizontally, aligned to the right side of the parent:

```ui
Group {
    LayoutMode: Right;
    Button { Anchor: (Width: 80); }
    Button { Anchor: (Width: 80); }
    Button { Anchor: (Width: 80); }
}
```

### Center

Centers children horizontally within the parent:

```ui
Group {
    LayoutMode: Center;
    Group #Dialog {
        Anchor: (Width: 400, Height: 300);
    }
}
```

### Middle

Centers children vertically within the parent:

```ui
Group {
    LayoutMode: Middle;
    Group #Dialog {
        Anchor: (Width: 400, Height: 300);
    }
}
```

### CenterMiddle (Horizontal Stack, Fully Centered)

Children stack horizontally from left to right, centered both horizontally and vertically:

```ui
Group {
    LayoutMode: CenterMiddle;
    Button { Anchor: (Width: 80); }
    Button { Anchor: (Width: 80); }
    Button { Anchor: (Width: 80); }
}
```

Result:

```
┌────────────────────────────────────────────┐
│                                            │
│                                            │
│         ┌──────┐ ┌──────┐ ┌──────┐         │
│         │  B1  │ │  B2  │ │  B3  │         │
│         └──────┘ └──────┘ └──────┘         │
│                                            │
│                                            │
└────────────────────────────────────────────┘
```

### MiddleCenter (Vertical Stack, Fully Centered)

Children stack vertically from top to bottom, centered both horizontally and vertically:

```ui
Group {
    LayoutMode: MiddleCenter;
    Button { Anchor: (Height: 30); }
    Button { Anchor: (Height: 30); }
    Button { Anchor: (Height: 30); }
}
```

Result:

```
┌────────────────────────────────────────────┐
│                                            │
│              ┌──────────┐                  │
│              │ Button 1 │                  │
│              ├──────────┤                  │
│              │ Button 2 │                  │
│              ├──────────┤                  │
│              │ Button 3 │                  │
│              └──────────┘                  │
│                                            │
└────────────────────────────────────────────┘
```

### Full (Absolute Positioning)

Children use absolute positioning via their Anchor properties:

```ui
Group {
    LayoutMode: Full;
    Label {
        Anchor: (Top: 20, Left: 20, Width: 100, Height: 30);
    }
}
```

### TopScrolling / BottomScrolling

Like Top/Bottom, but adds a scrollbar if content exceeds container height:

```ui
Group {
    LayoutMode: TopScrolling;
    ScrollbarStyle: $Common.@DefaultScrollbar;
    // ... many children
}
```

### LeftScrolling / RightScrolling

Like Left/Right, but adds a scrollbar for horizontal scrolling:

```ui
Group {
    LayoutMode: LeftScrolling;
    ScrollbarStyle: $Common.@DefaultScrollbar;
    // ... many children
}
```

### LeftCenterWrap (Wrapping Horizontal Stack)

Children flow left to right. When there's no more horizontal space, they wrap to the next row. Each row is horizontally centered:

```ui
Group {
    LayoutMode: LeftCenterWrap;
    Button { Anchor: (Width: 80, Height: 30); }
    Button { Anchor: (Width: 80, Height: 30); }
    Button { Anchor: (Width: 80, Height: 30); }
    Button { Anchor: (Width: 80, Height: 30); }
    Button { Anchor: (Width: 80, Height: 30); }
}
```

Result:

```
┌────────────────────────────────────────────┐
│                                            │
│       ┌──────┐ ┌──────┐ ┌──────┐           │
│       │  B1  │ │  B2  │ │  B3  │           │
│       └──────┘ └──────┘ └──────┘           │
│            ┌──────┐ ┌──────┐               │
│            │  B4  │ │  B5  │               │
│            └──────┘ └──────┘               │
│                                            │
└────────────────────────────────────────────┘
```

### Complete LayoutMode Reference

| Mode | Direction | Alignment | Scrollable Variant |
|------|-----------|-----------|-------------------|
| `Top` | Vertical | Top | `TopScrolling` |
| `Bottom` | Vertical | Bottom | `BottomScrolling` |
| `Left` | Horizontal | Left | `LeftScrolling` |
| `Right` | Horizontal | Right | `RightScrolling` |
| `Center` | - | Horizontal center | - |
| `Middle` | - | Vertical center | - |
| `CenterMiddle` | Horizontal | Both centered | - |
| `MiddleCenter` | Vertical | Both centered | - |
| `Full` | Absolute | Via Anchor | - |
| `LeftCenterWrap` | Horizontal wrap | Centered rows | - |
| `RightCenterWrap` | Horizontal wrap | Centered rows | - |

---

## FlexWeight

FlexWeight distributes remaining space among children after fixed-size elements are placed.

### Basic Usage

```ui
Group {
    LayoutMode: Left;
    Anchor: (Width: 400);
    Button {
        Anchor: (Width: 100);
    }
    Group {
        FlexWeight: 1;  // Takes all remaining space
    }
    Button {
        Anchor: (Width: 100);
    }
}
```

Result:
- First button: 100px
- Middle group: 200px (400 - 100 - 100 = 200)
- Last button: 100px

### Multiple FlexWeights

When multiple elements have FlexWeight, space is distributed proportionally:

```ui
Group {
    LayoutMode: Left;
    Anchor: (Width: 600);
    Group { FlexWeight: 1; }
    Group { FlexWeight: 2; }
    Group { FlexWeight: 1; }
}
```

Remaining space (600px) is split:
- First group: 600 × (1/4) = 150px
- Second group: 600 × (2/4) = 300px
- Third group: 600 × (1/4) = 150px

---

## Visibility

Control whether an element is displayed:

```ui
Button #HiddenButton {
    Visible: false;
}
```

Effect:
- Element and its children are not displayed
- Element is **not** included in layout (doesn't take up space)

---

Source: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/layout


## Reference: markup.md

# Markup Reference

A UI document (.ui file) contains trees of elements. There can be multiple root elements in a single document.

## Basic Syntax

An element is the basic building block of a user interface. Here's the fundamental syntax:

```ui
// Basic declaration of an element
// with its Anchor property set to attach on all sides of its parent
// with 10 pixels of margin
Group { Anchor: (Left: 10, Top: 10, Right: 10, Bottom: 10); }
Group { Anchor: (Full: 10); } // More concise version

// Declaration of a Label with a name
// (can be used to access the element from game code)
Label #MyLabel {
    Style: LabelStyle(FontSize: 16); // or just Style: (FontSize: 16), type can be inferred.
    Text: "Hi! I am text.";
}

// Declaration of a Group containing 2 children
Group {
    LayoutMode: Left;
    Label { Text: "Child 1"; FlexWeight: 2; }
    Label { Text: "Child 2"; FlexWeight: 1; }
}
```

### Syntax Rules

- **Elements**: `ElementType [#Id] { properties... children... }`
- **IDs**: Prefix with `#` for Java/event access (e.g., `#MyLabel`)
- **Properties**: `PropertyName: value;`
- **Comments**: `// single line comment`
- **Tuples/Objects**: `(Key1: value1, Key2: value2)`

---

## Documents

A UI document can have multiple root elements. Each root element becomes a separate tree in the UI hierarchy.

---

## Named Expressions

Named expressions are reusable values declared with the `@` prefix. They must be declared at the top of the block (before properties and children).

### Basic Named Expressions

```ui
// Example of named expressions, declared and used with @ prefix
@Title = "Hytale";
@ExtraSpacing = 5;

Label {
    Text: @Title;
    Style: (LetterSpacing: 2 + @ExtraSpacing);
}
```

### Named Expression Scoping

Named expressions are scoped to the subtree where they are declared. They can be declared at any level, including document root.

### Spread Operator

Use the spread operator `...` to reuse a named expression while overriding some of its fields:

```ui
@MyBaseStyle = LabelStyle(FontSize: 24, LetterSpacing: 2);

Label {
    Style: (...@MyBaseStyle, FontSize: 36);
}
```

### Layering Multiple Named Expressions

You can combine multiple named expressions:

```ui
@TitleStyle = LabelStyle(FontSize: 24, HorizontalAlignment: Center);
@BigTextStyle = LabelStyle(FontSize: 36);
@SpacedTextStyle = LabelStyle(LetterSpacing: 2);

Label {
    Style: (...@BigTextStyle, ...@SpacedTextStyle);
}
```

### Document References

A document can reference another document and access its named expressions using the `$` prefix:

```ui
// Document references are defined with $ prefix
$Common = "../Common.ui";

TextButton {
    Style: $Common.@DefaultButtonStyle;
}
```

---

## Templates

Templates are named expressions that contain element trees. You can instantiate them multiple times with customizations.

### Declaring and Using Templates

```ui
// This is the template
@Row = Group {
    Anchor: (Height: 50);
    Label #Label { Anchor: (Left: 0, Width: 100); Text: @LabelText; }
    Group #Content { Anchor: (Left: 100); }
};

// Here we'll be using it twice in the document tree
Group #Rows {
    LayoutMode: TopScrolling;
    @Row #MyFirstRow {
        @LabelText = "First row";
        #Content { TextField {} }
    }
    @Row #MySecondRow {
        @LabelText = "Second row";
    }
}
```

### Template Customization Rules

- You can override local named expressions inside template instances
- You can insert additional children at any point in the template tree by targeting the child's ID
- Local named expressions must be defined at the very top of the block, before properties and child elements

---

## Property Types

### Basic Types

| Type | Example | Notes |
|------|---------|-------|
| Boolean | `Visible: false;` | `true` or `false` |
| Int | `Height: 20;` | Whole numbers |
| Float, Double, Decimal | `Min: 0.2;` | Decimal numbers |
| String | `Text: "Hi!";` | Quoted text |
| Char | `PasswordChar: "*";` | Single character only (same syntax as string) |
| Color | `Background: #ffffff;` | Hex color literals |
| Object | `Style: (Background: #ffffff)` | Parentheses with key-value pairs |
| Array | `TextSpans: [(Text: "Hi", IsBold: true)]` | Square brackets with objects |

### Translations

Translation keys can be referenced anywhere you can provide a string. They are converted to localized strings when the element is instantiated:

```ui
Label {
    Text: %ui.general.cancel;
}
```

The translation key uses the `%` prefix and references keys from language files.

### Colors

Color literals can be written in several formats:

| Format | Description | Example |
|--------|-------------|---------|
| `#rrggbb` | 6-digit hex (fully opaque) | `#ffffff` |
| `#rrggbb(a.a)` | 6-digit hex with alpha (0-1) | `#000000(0.3)` |
| `#rrggbbaa` | 8-digit hex with alpha | `#ffffff80` |

**Preferred:** The `#rrggbb(a.a)` format is recommended for readability.

```ui
Group {
    Background: #000000(0.3);  // 30% opacity black
}
```

### Font Names

Font names are strings that map to `UIFontName` internally:

```ui
Label {
    Text: "Hi";
    Style: (FontName: "Secondary");
}
```

**Available Font Names:**

| Name | Use Case |
|------|----------|
| `Default` | Standard text; used unless specified otherwise |
| `Secondary` | Headlines or elements that should stand out |
| `Mono` | Development only (profiling, error overlays) |

### Paths (UIPath)

Paths reference other UI assets and are always relative to the current file location:

```ui
// UIPath syntax is the same as String
Sprite {
    TexturePath: "MyButton.png";
}
```

**Path Resolution Examples:**

| Reference | Current File | Resolved Path |
|-----------|--------------|---------------|
| `MyButton.png` | `Menu/MyAwesomeMenu.ui` | `Menu/MyButton.png` |
| `../MyButton.png` | `Menu/MyAwesomeMenu.ui` | `MyButton.png` |
| `../../MyButton.png` | `Menu/Popup/Templates/MyAwesomeMenu.ui` | `Menu/MyButton.png` |

### Objects

Objects contain a set of properties in parentheses:

```ui
Group {
    Anchor: (
        Height: 10,
        Width: 20
    );
}
```

Type inference is supported. If the property type is known, you don't need to specify the type name:

```ui
// Explicit type
Style: LabelStyle(FontSize: 16);

// Inferred type (when property expects LabelStyle)
Style: (FontSize: 16);
```

### Arrays

Arrays use square brackets and contain objects:

```ui
Label {
    TextSpans: [
        (Text: "Hello ", IsBold: true),
        (Text: "World", IsBold: false)
    ];
}
```

---

## Visual Studio Code Extension

There is an official VS Code extension that adds syntax highlighting for `.ui` files:

https://marketplace.visualstudio.com/items?itemName=HypixelStudiosCanadaInc.vscode-hytaleui

---

Source: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup


## Reference: overview.md

```markdown
# Custom UI Overview

Custom UI is Hytale's framework for creating **custom user interfaces** controlled by the game server. Unlike the built-in Client UI (which is part of the game client and cannot be modified), Server UI allows you to create interactive screens and HUD overlays through Java plugins and asset packs.

## What You Can Do

- **Create custom interactive pages** - Shop interfaces, quest dialogs, server settings menus, admin panels
- **Add custom HUD overlays** - Quest trackers, status displays, custom health bars, server information
- **Design with markup** - Use `.ui` files to define reusable UI templates
- **Handle user interactions** - Respond to button clicks, form submissions, and other events
- **Localize your UI** - Support multiple languages using the game's translation system

---

## How Custom UI Fits Into Hytale's UI

Hytale's user interface is divided into two categories:

### Client UI (Not Moddable)

Built-in interfaces controlled by the C# game client:

- Main menu and settings
- Character creation
- Built-in HUD (health, hotbar, chat)
- Inventory and crafting screens
- Development tools

**You cannot modify these** - they are part of the core game client.

### In-Game UI (Moddable via Server)

Server-controlled interfaces that you can create and customize:

#### Custom Pages

Full-screen interactive overlays that appear during gameplay:

- Can be dismissed by the player (ESC key)
- Capture all input (keyboard and mouse)
- Support loading states while waiting for server responses
- Perfect for: shops, dialogs, menus, configuration screens

#### Custom HUDs

Persistent overlay elements drawn on top of the game world:

- Display-only (no user interaction)
- Always visible during gameplay
- Lightweight and non-intrusive
- Perfect for: quest objectives, status indicators, server info panels

---

## Architecture Overview

Server UI uses a **command-based architecture**:

```
┌─────────────────────┐          ┌──────────────────────┐
│   Java Server       │          │    C# Client         │
│   (Your Plugin)     │          │    (Game)            │
├─────────────────────┤          ├──────────────────────┤
│                     │          │                      │
│ InteractiveCustomUI │          │   CustomPage or      │
│ Page                │          │   CustomHud          │
│   ↓ build()         ├────────→ │     ↓ Apply          │
│ UICommandBuilder    │          │   Element Tree       │
│   - append()        │          │     ↓ Layout         │
│   - set()           │          │   Rendered UI        │
│   - clear()         │          │                      │
│                     │          │                      │
│   handleDataEvent() │←─────────│   User Interaction   │
│   Process input     │ Events   │   (click, type, etc) │
│   sendUpdate()      │          │                      │
└─────────────────────┘          └──────────────────────┘
```

**The flow:**

1. Your Java code builds UI using `UICommandBuilder`
2. Commands are sent to the client as data
3. Client parses `.ui` markup files and creates visual elements
4. User interacts with the UI
5. Events are sent back to your Java code
6. You process events and send updates back

---

## Key Principles

### Declarative, Not Imperative

You don't create UI objects directly. Instead, you send **commands** that describe what you want:

- "Append this button template to that container"
- "Set this label's text to 'Hello World'"
- "Clear all children from this list"

### Asset-Driven

UI structure is defined in `.ui` markup files (assets), not hardcoded in Java. This enables:

- Designers to modify layouts without touching code
- Reusable UI components
- Consistent visual language

### Event-Driven

User interactions trigger events that flow back to your server code. You register event bindings and handle them in `handleDataEvent()`.

### Selector-Based

You target specific UI elements using **selectors:**

| Selector | Meaning |
|----------|---------|
| `#MyButton` | Element with ID "MyButton" |
| `#List[0]` | First child of element "List" |
| `#List[0] #Title` | Element "Title" in the first child of "List" |
| `#Label.TextColor` | The TextColor property of element "Label" |

---

Source: https://hytalemodding.dev/en/docs/official-documentation/custom-ui

```


## Reference: translations.md

# Translations & Localization

This document covers localization in Hytale UI - both translating your mod's UI text and contributing to the HytaleModding community translations.

---

## Translating Your Mod's UI

### Translation Keys in .ui Files

Use the `%` prefix to reference translation keys in your UI markup:

```ui
Label {
    Text: %ui.mymod.greeting;
}

Button {
    Text: %ui.mymod.button.save;
}
```

### Language Files

Translation strings are stored in `.lang` files under `Server/Languages/`:

```
src/main/resources/
└── Server/
    └── Languages/
        ├── en-US/
        │   └── ui.lang
        ├── es-ES/
        │   └── ui.lang
        └── fallback.lang
```

### Language File Format

Language files use a simple key-value format:

```properties
# en-US/ui.lang
ui.mymod.greeting = Hello, adventurer!
ui.mymod.button.save = Save
ui.mymod.button.cancel = Cancel
ui.mymod.inventory.title = Inventory
```

```properties
# es-ES/ui.lang
ui.mymod.greeting = ¡Hola, aventurero!
ui.mymod.button.save = Guardar
ui.mymod.button.cancel = Cancelar
ui.mymod.inventory.title = Inventario
```

### Fallback Configuration

The `fallback.lang` file maps locales to their fallback:

```properties
# fallback.lang
en-GB = en-US
es-MX = es-ES
pt-BR = pt-PT
```

### Using Translations in Java

```java
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.Message;

// From a translation key
LocalizableString text = LocalizableString.fromMessageId("ui.mymod.greeting");

// With parameters
LocalizableString text = LocalizableString.fromMessageId(
    "ui.mymod.welcome", 
    Map.of("name", playerName)
);

// In language file:
// ui.mymod.welcome = Welcome, {name}!

// Plain string (no translation)
LocalizableString text = LocalizableString.fromString("Static text");

// Using Message class for chat
Message.translation("chat.mymod.joined", Map.of("player", playerName));
```

### Translation Parameters

Use `{paramName}` for dynamic values:

```properties
# Language file
ui.mymod.level = Level: {level}
ui.mymod.damage = You dealt {amount} damage to {target}!
```

```ui
Label {
    Text: %ui.mymod.level;
}
```

Set the parameter value from Java:

```java
UICommandBuilder cmd = new UICommandBuilder();
cmd.set("#LevelLabel.Text", 
    LocalizableString.fromMessageId("ui.mymod.level", Map.of("level", String.valueOf(playerLevel))));
```

### Best Practices

1. **Use namespaced keys** - Prefix with your mod name: `ui.mymod.feature.key`
2. **Keep keys descriptive** - `ui.mymod.inventory.empty` not `ui.mymod.ie`
3. **Externalize all user-facing text** - Never hardcode display strings
4. **Support parameters** - Use `{param}` for dynamic content
5. **Provide fallback locale** - Always have en-US as base
6. **Test all locales** - Verify translations fit in your UI layouts

---

## Contributing to HytaleModding Translations

Help translate the HytaleModding documentation website to your language.

### How to Contribute

All translations are managed via Crowdin:

1. Visit [translate.hytalemodding.dev](https://translate.hytalemodding.dev/)
2. Log in (create account if needed)
3. Click on your language
4. Click on the file/article you wish to translate
5. Start translating!
6. Approved translations will appear on the website

### Translation Guidelines

- **Be confident** - You should be able to read your translation and understand the meaning easily
- **Keep it simple** - Use the simplest form of language possible
- **Use English words** if the translation is unknown to the majority in your country
- **Use your imagination** - Feel free to change context as long as meaning is preserved
- **Follow Hytale's official translations** when available

### What NOT to Translate

**Callout types** - Only translate title and content:
```mdx
<Callout type="warning" title="Translate this title!">
  Translate the text in between!
</Callout>
```
Do NOT translate "warning" - it's a technical identifier.

**Icon names** - These are technical identifiers:
```mdx
icon: Globe
```
Translating icon names will break icon rendering.

**Code blocks** - Keep code samples in their original form.

### Discussion & Support

1. Join the [HytaleModding Discord](https://discord.gg/hytalemodding)
2. Open the `#translation` channel for general translation discussion
3. Run `/translator <your-language>` to join your language's thread
4. If your language isn't available:
   - Request it on Crowdin if not listed
   - Ping **Neil** on Discord if it's on Crowdin but missing from the bot

---

## Source

- Official UI Markup: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup
- Translation Guidelines PR: https://github.com/HytaleModding/site/pull/396


## Reference: troubleshooting.md

# Troubleshooting

## Common issues

- Failed to apply Custom UI HUD commands: syntax error in .ui. Enable Diagnostic Mode in Hytale settings.
- Could not find document for Custom UI Append: wrong path or file not under Common/UI/Custom/.
- Unknown node type: unsupported or misspelled element type.
- Page stuck on Loading: missing sendUpdate in interactive page event handling.
- Client disconnect when opening UI: UI work not on world thread.
- Texture missing: wrong path or missing @2x.png suffix.
- Events not firing: selector does not match element ID.


## Reference: type-documentation.md

# Type Documentation Reference

The type documentation is a generated index of all UI elements, property types, and enums available in markup. Use it as your reference when you need exact property names, types, or valid enum values.

---

## Elements

Elements are the building blocks of UI. Each element type has specific properties.

### Commonly Used Elements

| Element | Purpose | Key Properties |
|---------|---------|----------------|
| `Group` | Container/layout | `LayoutMode`, `Background`, `Padding`, `ScrollbarStyle` |
| `Label` | Text display | `Text`, `TextSpans`, `Style`, `TextColor` |
| `Button` | Clickable button | `Text`, `Disabled`, `Style`, `Background` |
| `TextField` | Text input | `Value`, `PlaceholderText`, `MaxLength`, `ReadOnly`, `Password`, `PasswordChar`, `AutoGrow`, `MaxVisibleLines` |
| `Slider` | Range input | `Value`, `Min`, `Max`, `Step`, `Style` |
| `CheckBox` | Toggle input | `Value` (boolean) |
| `DropdownBox` | Dropdown selection | `Value`, `Entries` (DropdownEntryInfo[]) |
| `ProgressBar` | Progress display | `Value` (0.0-1.0), `BarTexturePath`, `EffectTexturePath`, `Direction`, `Alignment`, `Color` |
| `CircularProgressBar` | Circular progress | `Value`, `MaskTexturePath` |
| `Sprite` | Animated image | `TexturePath`, `Frame`, `FramesPerSecond` |
| `ItemIcon` | Item display | `ItemId`, `Quantity` |
| `ItemSlot` | Full item slot | `ItemStack`, `Background`, `Overlay`, `Icon` |
| `ItemGrid` | Scrollable item grid | `Slots`, `SlotsPerRow`, `AreItemsDraggable`, `ShowScrollbar`, `KeepScrollPosition`, `RenderItemQualityBackground` |
| `TabNavigation` | Tab bar | Works with tab content groups |
| `TimerLabel` | Timer display | Specialized label for timers |

### Full Element List

- ActionButton
- AssetImage
- BackButton
- BlockSelector
- Button
- CharacterPreviewComponent
- CheckBox
- CheckBoxContainer
- CircularProgressBar
- CodeEditor
- ColorOptionGrid
- ColorPicker
- ColorPickerDropdownBox
- CompactTextField
- DropdownBox
- DropdownEntry
- DynamicPane
- DynamicPaneContainer
- FloatSlider
- FloatSliderNumberField
- Group
- HotkeyLabel
- ItemGrid
- ItemIcon
- ItemPreviewComponent
- ItemSlot
- ItemSlotButton
- Label
- LabeledCheckBox
- MenuItem
- MultilineTextField
- NumberField
- Panel
- ProgressBar
- ReorderableList
- ReorderableListGrip
- SceneBlur
- Slider
- SliderNumberField
- Sprite
- TabButton
- TabNavigation
- TextButton
- TextField
- TimerLabel
- ToggleButton

---

## Property Types

Property types define the structure of complex values used in element properties.

### Commonly Used Property Types

| Type | Purpose |
|------|---------|
| `Anchor` | Element positioning and sizing |
| `Padding` | Inner spacing |
| `PatchStyle` | Nine-slice scalable backgrounds |
| `ScrollbarStyle` | Scrollbar appearance |
| `LabelStyle` | Text styling (font, size, color, alignment) |
| `ButtonStyle` | Button visual states |
| `SliderStyle` | Slider appearance |
| `TabStyle` | Tab appearance |
| `TabNavigationStyle` | Tab bar styling |
| `TextButtonStyle` | Text button styling |
| `ItemGridSlot` | Item grid slot data |
| `LabelSpan` | Rich text span |

### Full Property Type List

- Anchor
- BlockSelectorStyle
- ButtonSounds
- ButtonStyle
- ButtonStyleState
- CheckBoxStyle
- CheckBoxStyleState
- ClientItemStack
- ColorOptionGridStyle
- ColorPickerDropdownBoxStateBackground
- ColorPickerDropdownBoxStyle
- ColorPickerStyle
- DropdownBoxSearchInputStyle
- DropdownBoxSounds
- DropdownBoxStyle
- InputFieldButtonStyle
- InputFieldDecorationStyle
- InputFieldDecorationStyleState
- InputFieldIcon
- InputFieldStyle
- ItemGridSlot
- ItemGridStyle
- LabeledCheckBoxStyle
- LabeledCheckBoxStyleState
- LabelSpan
- LabelStyle
- NumberFieldFormat
- Padding
- PatchStyle
- PopupStyle
- ScrollbarStyle
- SliderStyle
- SoundStyle
- SpriteFrame
- SubMenuItemStyle
- SubMenuItemStyleState
- Tab
- TabNavigationStyle
- TabStyle
- TabStyleState
- TextButtonStyle
- TextButtonStyleState
- TextTooltipStyle
- ToggleButtonStyle
- ToggleButtonStyleState

---

## Enums

Enums define valid values for certain properties.

### Commonly Used Enums

| Enum | Values | Purpose |
|------|--------|---------|
| `LayoutMode` | Top, Bottom, Left, Right, Center, Middle, CenterMiddle, MiddleCenter, Full, TopScrolling, BottomScrolling, LeftScrolling, RightScrolling, LeftCenterWrap, RightCenterWrap | How a container arranges children |
| `LabelAlignment` | Left, Right, Center | Text horizontal alignment |
| `ProgressBarDirection` | LeftToRight, RightToLeft, BottomToTop, TopToBottom | Progress bar fill direction |
| `ProgressBarAlignment` | Start, Center, End | Progress bar alignment |
| `TimerDirection` | Up, Down | Timer count direction |
| `ResizeType` | None, Horizontal, Vertical, Both | Resize behavior |

### Full Enum List

- ActionButtonAlignment
- CodeEditorLanguage
- ColorFormat
- DropdownBoxAlign
- InputFieldButtonSide
- InputFieldIconSide
- ItemGridInfoDisplayMode
- LabelAlignment
- LayoutMode
- MouseWheelScrollBehaviourType
- ProgressBarAlignment
- ProgressBarDirection
- ResizeType
- TimerDirection
- TooltipAlignment

---

## How to Use Type Documentation

When you need specific details:

1. **Find the element type** you're working with
2. **Look up its properties** in the element documentation
3. **Check the property type** to understand what structure is expected
4. **Reference enums** for valid values when a property expects an enum

### Example: ProgressBar

To create a progress bar:

```ui
ProgressBar #HealthBar {
    Anchor: (Width: 200, Height: 20);
    Value: 0.75;
    Direction: LeftToRight;  // ProgressBarDirection enum
    Alignment: Start;         // ProgressBarAlignment enum
    Color: #22cc22;
}
```

---

## Online Reference

For the most up-to-date and detailed documentation, visit:
https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation

The online documentation includes:
- Complete property lists for each element
- Detailed descriptions of each property type
- All valid enum values with descriptions

---

Source: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation


## Reference: types.md

# UI Types Reference

Complete reference of all UI elements, property types, and enums available in Hytale UI markup.

---

## Elements

Elements are the building blocks of UI. Each element has specific properties and event callbacks.

### Base Properties (Inherited by All Elements)

These properties are available on virtually all elements:

| Property | Type | Description |
|----------|------|-------------|
| `Visible` | Boolean | Hides the element. Makes parent layouting skip this element as well |
| `HitTestVisible` | Boolean | If true, element will be returned during HitTest (enables click detection) |
| `TooltipText` | String | Enables a text tooltip shown on hover |
| `TooltipTextSpans` | List&lt;LabelSpan&gt; | Tooltip with formatted text spans |
| `TextTooltipStyle` | TextTooltipStyle | Style options for the tooltip |
| `TextTooltipShowDelay` | Float | Delay in seconds before tooltip appears |
| `Anchor` | Anchor | How the element is laid out inside its allocated area |
| `Padding` | Padding | Space around content (background unaffected) |
| `FlexWeight` | Integer | Distribution of remaining space after explicit sizes |
| `Background` | PatchStyle / String | Background image or color |
| `MaskTexturePath` | UI Path (String) | Mask texture for clipping |
| `OutlineColor` | Color | Color for outline |
| `OutlineSize` | Float | Draws outline with specified size |

---

### Group

**Container element** - Accepts children: Yes

The fundamental container for laying out child elements.

| Property | Type | Description |
|----------|------|-------------|
| `LayoutMode` | LayoutMode | How child elements are arranged |
| `ScrollbarStyle` | ScrollbarStyle | Scrollbar appearance |
| `ContentWidth` | Integer | If set, displays horizontal scrollbar |
| `ContentHeight` | Integer | If set, displays vertical scrollbar |
| `AutoScrollDown` | Boolean | Auto-scroll to bottom (unless scrolled up) |
| `KeepScrollPosition` | Boolean | Keep scroll position after unmount |
| `MouseWheelScrollBehaviour` | MouseWheelScrollBehaviourType | Scroll behavior |
| `Overscroll` | Boolean | Extend scrolling areas by element size |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `Validating` | Triggered on Enter key press |
| `Dismissing` | Triggered on Escape key press |
| `Scrolled` | Triggered after scrolling |

**Example:**
```ui
Group #Container {
    LayoutMode: Top;
    Padding: (Full: 10);
    Background: PatchStyle(Color: #1a1a2eF0, Border: 4);
    
    Label { Text: "Child 1"; }
    Label { Text: "Child 2"; }
}
```

---

### Label

**Text display** - Accepts children: No

Displays text with optional formatting via spans.

| Property | Type | Description |
|----------|------|-------------|
| `Text` | String | Plain text content |
| `TextSpans` | List&lt;LabelSpan&gt; | Formatted text spans |
| `Style` | LabelStyle | Text styling |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `LinkActivating` | Called when a link is clicked |
| `TagMouseEntered` | Called when hovering a tag |

**Example:**
```ui
Label #Title {
    Text: "Hello World";
    Style: (FontSize: 20, RenderBold: true, TextColor: #ffffff);
}

// With rich text
Label #RichText {
    TextSpans: [
        (Text: "Bold ", IsBold: true),
        (Text: "and ", Color: #aaaaaa),
        (Text: "Colored", Color: #ff6600)
    ];
}
```

---

### Button

**Clickable button** - Accepts children: Yes

| Property | Type | Description |
|----------|------|-------------|
| `LayoutMode` | LayoutMode | How child elements are arranged |
| `Disabled` | Boolean | Whether button is clickable |
| `Style` | ButtonStyle | Button visual style |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `Activating` | Triggered on click |
| `DoubleClicking` | Triggered on double click |
| `RightClicking` | Triggered on right click |
| `MouseEntered` | Mouse cursor entered bounds |
| `MouseExited` | Mouse cursor left bounds |

**Example:**
```ui
Button #SaveButton {
    Anchor: (Width: 120, Height: 36);
    Disabled: false;
    Style: $Common.@DefaultButtonStyle;
    
    Label { Text: "Save"; }
}
```

---

### TextField

**Text input** - Accepts children: No

| Property | Type | Description |
|----------|------|-------------|
| `Value` | String | Current text value |
| `PlaceholderText` | String | Text shown when empty |
| `PasswordChar` | Char | Character to replace text (for passwords) |
| `Style` | InputFieldStyle | Text style |
| `PlaceholderStyle` | InputFieldStyle | Placeholder text style |
| `Decoration` | InputFieldDecorationStyle | Field decoration style |
| `AutoFocus` | Boolean | Auto-focus when mounted |
| `AutoSelectAll` | Boolean | Auto-select all text (requires AutoFocus) |
| `IsReadOnly` | Boolean | Whether editable |
| `MaxLength` | Integer | Maximum character count |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `RightClicking` | Right click on field |
| `Validating` | Enter key pressed |
| `Dismissing` | Escape key pressed |
| `FocusLost` | Field lost focus |
| `FocusGained` | Field gained focus |
| `ValueChanged` | Text value changed |

**Example:**
```ui
TextField #Username {
    Anchor: (Height: 36);
    FlexWeight: 1;
    PlaceholderText: "Enter username...";
    MaxLength: 32;
}

TextField #Password {
    Anchor: (Height: 36);
    PasswordChar: "*";
}
```

---

### Slider

**Range input with draggable handle** - Accepts children: No

| Property | Type | Description |
|----------|------|-------------|
| `Value` | Integer | Current value |
| `Min` | Integer | Minimum allowed value |
| `Max` | Integer | Maximum allowed value |
| `Step` | Integer | Increment/decrement amount |
| `IsReadOnly` | Boolean | Whether editable |
| `Style` | SliderStyle | Slider style |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `MouseButtonReleased` | Drag completed |
| `ValueChanged` | Value changed |

**Example:**
```ui
Slider #VolumeSlider {
    Anchor: (Height: 24);
    FlexWeight: 1;
    Value: 50;
    Min: 0;
    Max: 100;
    Step: 1;
}
```

---

### CheckBox

**Toggle input** - Accepts children: No

| Property | Type | Description |
|----------|------|-------------|
| `Value` | Boolean | Checked state |
| `Disabled` | Boolean | Whether clickable |
| `Style` | CheckBoxStyle | CheckBox style |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `ValueChanged` | Checked state changed |

**Example:**
```ui
CheckBox #EnableSound {
    Value: true;
}
```

---

### DropdownBox

**Dropdown selection** - Accepts children: Yes

| Property | Type | Description |
|----------|------|-------------|
| `Entries` | IReadOnlyList | Dropdown entries |
| `SelectedValues` | List&lt;String&gt; | Selected values (multi-select) |
| `Value` | String | Selected value (single-select) |
| `Disabled` | Boolean | Whether clickable |
| `Style` | DropdownBoxStyle | Dropdown style |
| `PanelTitleText` | String | Title for dropdown panel |
| `IsReadOnly` | Boolean | Whether editable |
| `MaxSelection` | Integer | Maximum selections allowed |
| `ShowSearchInput` | Boolean | Show search filter |
| `ShowLabel` | Boolean | Show selected label |
| `ForcedLabel` | String | Override label text |
| `NoItemsText` | String | Text when empty |
| `DisplayNonExistingValue` | Boolean | Show value not in entries |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `ValueChanged` | Selection changed |
| `DropdownToggled` | Dropdown opened/closed |

**Example:**
```ui
DropdownBox #LanguageSelect {
    Anchor: (Width: 200, Height: 36);
    Value: "en-US";
    ShowSearchInput: true;
}
```

---

### ProgressBar

**Progress display** - Accepts children: No

| Property | Type | Description |
|----------|------|-------------|
| `Value` | Float | Progress value (0.0 - 1.0) |
| `Bar` | PatchStyle / String | Bar appearance |
| `BarTexturePath` | UI Path (String) | Bar texture |
| `EffectTexturePath` | UI Path (String) | Effect overlay texture |
| `EffectWidth` | Integer | Effect width |
| `EffectHeight` | Integer | Effect height |
| `EffectOffset` | Integer | Effect offset |
| `Alignment` | ProgressBarAlignment | Bar alignment |
| `Direction` | ProgressBarDirection | Fill direction |

**Example:**
```ui
ProgressBar #HealthBar {
    Anchor: (Width: 200, Height: 20);
    Value: 0.75;
    Direction: LeftToRight;
    Background: PatchStyle(Color: #333333);
    Bar: PatchStyle(Color: #22cc22);
}
```

---

### ItemGrid

**Scrollable item grid** - Accepts children: No

| Property | Type | Description |
|----------|------|-------------|
| `Slots` | ItemGridSlot[] | Grid slot data |
| `ItemStacks` | ClientItemStack[] | Simple item stacks |
| `SlotsPerRow` | Integer | Items per row |
| `ShowScrollbar` | Boolean | Show scrollbar |
| `Style` | ItemGridStyle | Grid style |
| `ScrollbarStyle` | ScrollbarStyle | Scrollbar style |
| `RenderItemQualityBackground` | Boolean | Show quality backgrounds |
| `InfoDisplay` | ItemGridInfoDisplayMode | Info display mode |
| `AdjacentInfoPaneGridWidth` | Integer | Info pane width |
| `AreItemsDraggable` | Boolean | Enable drag and drop |
| `InventorySectionId` | Integer | Inventory section ID |
| `AllowMaxStackDraggableItems` | Boolean | Allow full stack dragging |
| `DisplayItemQuantity` | Boolean | Show stack quantities |
| `KeepScrollPosition` | Boolean | Preserve scroll position |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `SlotDoubleClicking` | Slot double-clicked |
| `DragCancelled` | Drag operation cancelled |
| `SlotMouseEntered` | Mouse entered slot |
| `SlotMouseExited` | Mouse left slot |

**Example:**
```ui
ItemGrid #InventoryGrid {
    FlexWeight: 1;
    SlotsPerRow: 8;
    ShowScrollbar: true;
    AreItemsDraggable: false;
    RenderItemQualityBackground: true;
    Style: $Common.@DefaultItemGridStyle;
}
```

---

### Sprite

**Animated spritesheet image** - Accepts children: No

| Property | Type | Description |
|----------|------|-------------|
| `TexturePath` | UI Path (String) | Spritesheet texture |
| `Frame` | SpriteFrame | Spritesheet layout info |
| `FramesPerSecond` | Integer | Animation speed |
| `IsPlaying` | Boolean | Animation playing state |
| `AutoPlay` | Boolean | Auto-play on mount |
| `Angle` | Float | Rotation in degrees |
| `RepeatCount` | Integer | Repeat count (0 = infinite) |

**Example:**
```ui
Sprite #LoadingSpinner {
    Anchor: (Width: 32, Height: 32);
    TexturePath: "spinner@2x.png";
    FramesPerSecond: 12;
    AutoPlay: true;
    RepeatCount: 0;
}
```

---

### TabNavigation

**Tab bar** - Accepts children: Yes

| Property | Type | Description |
|----------|------|-------------|
| `Tabs` | Tab[] | Tab definitions |
| `SelectedTab` | String | Currently selected tab |
| `AllowUnselection` | Boolean | Allow no selection |
| `Style` | TabNavigationStyle | Tab bar style |

**Event Callbacks:**

| Event | Description |
|-------|-------------|
| `SelectedTabChanged` | Tab selection changed |

**Example:**
```ui
TabNavigation #MainTabs {
    Anchor: (Height: 40);
    SelectedTab: "inventory";
    Style: $Common.@DefaultTabNavigationStyle;
}
```

---

### All Elements List

| Element | Children | Description |
|---------|----------|-------------|
| **Group** | Yes | Container/layout element |
| **Label** | No | Text display |
| **Button** | Yes | Clickable button |
| **TextField** | No | Single-line text input |
| **MultilineTextField** | No | Multi-line text input |
| **NumberField** | No | Numeric input |
| **Slider** | No | Range slider |
| **FloatSlider** | No | Float range slider |
| **CheckBox** | No | Boolean toggle |
| **DropdownBox** | Yes | Dropdown selection |
| **ProgressBar** | No | Progress indicator |
| **CircularProgressBar** | No | Circular progress |
| **ItemGrid** | No | Item slot grid |
| **ItemSlot** | No | Single item slot |
| **ItemIcon** | No | Item icon display |
| **ItemSlotButton** | No | Clickable item slot |
| **Sprite** | No | Animated spritesheet |
| **TabNavigation** | Yes | Tab bar |
| **TabButton** | Yes | Individual tab |
| **TextButton** | Yes | Text-labeled button |
| **ToggleButton** | Yes | Toggle button |
| **ActionButton** | Yes | Action button |
| **BackButton** | Yes | Back navigation |
| **Panel** | Yes | Styled panel |
| **SceneBlur** | No | Background blur effect |
| **TimerLabel** | No | Timer display |
| **HotkeyLabel** | No | Keybind display |
| **ReorderableList** | Yes | Drag-sortable list |
| **ReorderableListGrip** | No | Drag handle |
| **ColorPicker** | No | Color selection |
| **ColorPickerDropdownBox** | Yes | Color dropdown |
| **ColorOptionGrid** | No | Color option grid |
| **CodeEditor** | No | Code editing |
| **AssetImage** | No | Asset image display |
| **CharacterPreviewComponent** | No | Character preview |
| **ItemPreviewComponent** | No | Item preview |
| **BlockSelector** | No | Block selection |
| **DynamicPane** | Yes | Dynamic content pane |
| **DynamicPaneContainer** | Yes | Dynamic pane container |
| **LabeledCheckBox** | No | CheckBox with label |
| **SliderNumberField** | No | Slider with number field |
| **FloatSliderNumberField** | No | Float slider with number field |
| **CompactTextField** | No | Compact text input |
| **DropdownEntry** | No | Dropdown list item |
| **MenuItem** | Yes | Menu item |
| **CheckBoxContainer** | Yes | CheckBox container |

---

## Property Types

Property types define complex value structures used as element properties.

### Anchor

Defines element positioning and sizing within its container.

| Property | Type | Description |
|----------|------|-------------|
| `Left` | Integer | Distance from container's left edge |
| `Right` | Integer | Distance from container's right edge |
| `Top` | Integer | Distance from container's top edge |
| `Bottom` | Integer | Distance from container's bottom edge |
| `Width` | Integer | Fixed width in pixels |
| `Height` | Integer | Fixed height in pixels |
| `MinWidth` | Integer | Minimum width constraint |
| `MaxWidth` | Integer | Maximum width constraint |
| `MinHeight` | Integer | Minimum height constraint |
| `MaxHeight` | Integer | Maximum height constraint |

**Shorthand:**
- `Full` - All sides (Left, Top, Right, Bottom)
- `Horizontal` - Left and Right
- `Vertical` - Top and Bottom

**Example:**
```ui
Anchor: (Width: 200, Height: 40);           // Fixed size
Anchor: (Full: 10);                          // 10px margin all sides
Anchor: (Top: 0, Bottom: 0, Left: 20, Width: 300);  // Mixed
```

---

### Padding

Inner spacing around content.

| Property | Type | Description |
|----------|------|-------------|
| `Left` | Integer | Left padding |
| `Right` | Integer | Right padding |
| `Top` | Integer | Top padding |
| `Bottom` | Integer | Bottom padding |

**Shorthand:**
- `Full` - All sides
- `Horizontal` - Left and Right
- `Vertical` - Top and Bottom

**Example:**
```ui
Padding: (Full: 16);
Padding: (Horizontal: 20, Vertical: 10);
```

---

### PatchStyle

Nine-slice scalable backgrounds.

| Property | Type | Description |
|----------|------|-------------|
| `Color` | Color | Background color |
| `TexturePath` | UI Path (String) | Background texture |
| `Border` | Integer | Border thickness (all sides) |
| `HorizontalBorder` | Integer | Horizontal border thickness |
| `VerticalBorder` | Integer | Vertical border thickness |
| `Area` | Padding | Content area |
| `Anchor` | Anchor | Positioning |

**Example:**
```ui
Background: PatchStyle(Color: #1a1a2eF0, Border: 4);
Background: PatchStyle(TexturePath: "frame@2x.png", Border: 8);
```

---

### LabelStyle

Text styling properties.

| Property | Type | Description |
|----------|------|-------------|
| `FontName` | String | Font name (Default, Secondary, Mono) |
| `FontSize` | Float | Font size |
| `TextColor` | Color | Text color |
| `OutlineColor` | Color | Text outline color |
| `LetterSpacing` | Float | Space between letters |
| `HorizontalAlignment` | LabelAlignment | Horizontal alignment |
| `VerticalAlignment` | LabelAlignment | Vertical alignment |
| `Alignment` | LabelAlignment | Combined alignment |
| `Wrap` | Boolean | Enable text wrapping |
| `RenderUppercase` | Boolean | Render as uppercase |
| `RenderBold` | Boolean | Render bold |
| `RenderItalics` | Boolean | Render italic |
| `RenderUnderlined` | Boolean | Render underlined |

**Example:**
```ui
Style: LabelStyle(
    FontSize: 16,
    TextColor: #ffffff,
    RenderBold: true,
    HorizontalAlignment: Center
);
```

---

### LabelSpan

Rich text span for formatted text.

| Property | Type | Description |
|----------|------|-------------|
| `Text` | String | Span text content |
| `Color` | Color | Text color |
| `OutlineColor` | Color | Outline color |
| `IsBold` | Boolean | Bold text |
| `IsItalics` | Boolean | Italic text |
| `IsUppercase` | Boolean | Uppercase text |
| `IsUnderlined` | Boolean | Underlined text |
| `IsMonospace` | Boolean | Monospace font |
| `Link` | String | Clickable link URL |
| `Params` | Dictionary | Additional parameters |

**Example:**
```ui
TextSpans: [
    (Text: "Normal "),
    (Text: "Bold ", IsBold: true),
    (Text: "Colored", Color: #ff6600)
];
```

---

### ItemGridSlot

Item grid slot data.

| Property | Type | Description |
|----------|------|-------------|
| `ItemStack` | ClientItemStack | Item to display |
| `Background` | PatchStyle / String | Slot background |
| `Overlay` | PatchStyle / String | Overlay on top of item |
| `Icon` | PatchStyle / String | Icon overlay |
| `ExtraOverlays` | List&lt;PatchStyle&gt; | Additional overlays |
| `Name` | String | Custom name override |
| `Description` | String | Custom description |
| `InventorySlotIndex` | Integer | Inventory slot index |
| `IsItemIncompatible` | Boolean | Mark as incompatible |
| `IsActivatable` | Boolean | Can be activated |
| `IsItemUncraftable` | Boolean | Mark as uncraftable |
| `SkipItemQualityBackground` | Boolean | Skip quality background |

---

### All Property Types List

| Type | Description |
|------|-------------|
| **Anchor** | Element positioning/sizing |
| **Padding** | Inner spacing |
| **PatchStyle** | Nine-slice backgrounds |
| **LabelStyle** | Text styling |
| **LabelSpan** | Rich text span |
| **ItemGridSlot** | Item grid slot |
| **ItemGridStyle** | Item grid styling |
| **ClientItemStack** | Item stack data |
| **ButtonStyle** | Button styling |
| **ButtonStyleState** | Button state styling |
| **ButtonSounds** | Button sound effects |
| **CheckBoxStyle** | CheckBox styling |
| **CheckBoxStyleState** | CheckBox state styling |
| **SliderStyle** | Slider styling |
| **InputFieldStyle** | Text input styling |
| **InputFieldDecorationStyle** | Input decoration |
| **InputFieldDecorationStyleState** | Input state decoration |
| **InputFieldIcon** | Input field icon |
| **InputFieldButtonStyle** | Input button styling |
| **DropdownBoxStyle** | Dropdown styling |
| **DropdownBoxSounds** | Dropdown sounds |
| **DropdownBoxSearchInputStyle** | Dropdown search styling |
| **ScrollbarStyle** | Scrollbar styling |
| **TabStyle** | Tab styling |
| **TabStyleState** | Tab state styling |
| **TabNavigationStyle** | Tab bar styling |
| **Tab** | Tab definition |
| **TextButtonStyle** | TextButton styling |
| **TextButtonStyleState** | TextButton state styling |
| **ToggleButtonStyle** | ToggleButton styling |
| **ToggleButtonStyleState** | ToggleButton state styling |
| **LabeledCheckBoxStyle** | LabeledCheckBox styling |
| **LabeledCheckBoxStyleState** | LabeledCheckBox state styling |
| **PopupStyle** | Popup styling |
| **TextTooltipStyle** | Tooltip styling |
| **SoundStyle** | Sound effect |
| **SpriteFrame** | Spritesheet frame info |
| **NumberFieldFormat** | Number formatting |
| **ColorPickerStyle** | ColorPicker styling |
| **ColorPickerDropdownBoxStyle** | Color dropdown styling |
| **ColorPickerDropdownBoxStateBackground** | Color dropdown state |
| **ColorOptionGridStyle** | Color grid styling |
| **BlockSelectorStyle** | Block selector styling |
| **SubMenuItemStyle** | Submenu styling |
| **SubMenuItemStyleState** | Submenu state styling |

---

## Enums

Enums define valid values for specific properties.

### LayoutMode

How a container arranges its children.

| Value | Description |
|-------|-------------|
| `Full` | Children fill parent; positioned via Anchor |
| `Left` | Left-to-right, aligned left |
| `Center` | Left-to-right, centered horizontally |
| `Right` | Left-to-right, aligned right |
| `Top` | Top-to-bottom, aligned top |
| `Middle` | Top-to-bottom, centered vertically |
| `Bottom` | Top-to-bottom, aligned bottom |
| `CenterMiddle` | Left-to-right, centered both axes |
| `MiddleCenter` | Top-to-bottom, centered both axes |
| `LeftScrolling` | Like Left with scrolling |
| `RightScrolling` | Like Right with scrolling |
| `TopScrolling` | Like Top with scrolling |
| `BottomScrolling` | Like Bottom with scrolling |
| `LeftCenterWrap` | Left-to-right, wrap to next row, centered |

---

### LabelAlignment

Text alignment.

| Value | Description |
|-------|-------------|
| `Left` | Align left |
| `Center` | Align center |
| `Right` | Align right |

---

### ProgressBarDirection

Progress bar fill direction.

| Value | Description |
|-------|-------------|
| `LeftToRight` | Fill from left to right |
| `RightToLeft` | Fill from right to left |
| `TopToBottom` | Fill from top to bottom |
| `BottomToTop` | Fill from bottom to top |

---

### ProgressBarAlignment

Progress bar alignment within container.

| Value | Description |
|-------|-------------|
| `Start` | Align to start |
| `Center` | Align to center |
| `End` | Align to end |

---

### TimerDirection

Timer count direction.

| Value | Description |
|-------|-------------|
| `Up` | Count up |
| `Down` | Count down |

---

### All Enums List

| Enum | Values |
|------|--------|
| **LayoutMode** | Full, Left, Center, Right, Top, Middle, Bottom, CenterMiddle, MiddleCenter, LeftScrolling, RightScrolling, TopScrolling, BottomScrolling, LeftCenterWrap |
| **LabelAlignment** | Left, Center, Right |
| **ProgressBarDirection** | LeftToRight, RightToLeft, TopToBottom, BottomToTop |
| **ProgressBarAlignment** | Start, Center, End |
| **TimerDirection** | Up, Down |
| **ResizeType** | None, Horizontal, Vertical, Both |
| **TooltipAlignment** | (alignment values) |
| **ActionButtonAlignment** | (alignment values) |
| **DropdownBoxAlign** | (alignment values) |
| **InputFieldButtonSide** | (side values) |
| **InputFieldIconSide** | (side values) |
| **ItemGridInfoDisplayMode** | (display modes) |
| **ColorFormat** | (color formats) |
| **CodeEditorLanguage** | (language values) |
| **MouseWheelScrollBehaviourType** | (behavior types) |

---

## Source

Full type documentation: https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation


