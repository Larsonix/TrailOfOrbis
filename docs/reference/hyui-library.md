# HyUI Library Reference

HyUI is a fluent UI builder library for Hytale that provides HTML/CSS-like syntax for building UIs. This file guides Claude when working with HyUI code.

**Current Version**: 0.8.4 (Feb 10, 2026)

## When to Use This Reference

**Read the HyUI documentation** (`docs/HyUI/`) whenever you need to:
- Create, modify, or refactor any UI code
- Build pages, HUDs, or UI components
- Use HYUIML (HTML-like syntax)
- Work with the Template Processor
- Handle UI events

## Documentation Location

All HyUI documentation is in markdown format at:
```
/home/larsonix/work/trail-of-orbis/docs/HyUI/
```

### Key Files to Read

| Task | Read These Files |
|------|-----------------|
| **Getting started** | `getting-started/installation.md`, `getting-started/README.md` |
| **HYUIML syntax** | `hyuiml-htmlish-in-hytale.md` |
| **HYUIML elements** | `hyuiml-elements.md` (complete element reference) |
| **HYUIML CSS** | `hyuiml-css.md` (all CSS properties) |
| **HYUIML limitations** | `hyuiml-limitations.md` (what HYUIML can't do) |
| **Template processor** | `template-processor.md`, `tutorial-template-processor/` |
| **Styling with builders** | `tutorial-styling-with-builders/` (4-part tutorial) |
| **Element types** | `element-examples.md`, `element-validation.md` |
| **Page building** | `tutorial-page-building/` |
| **HUD building** | `getting-started/hud-building.md` |
| **Item grids** | `tutorial-working-with-item-grids.md`, `item-grid-event-data.md` |
| **Tab navigation** | `tutorials/tutorial-tab-navigation/` |
| **Advanced patterns** | `tutorial-advanced-page-building/` |
| **Changelog** | `changelog.md` (check for breaking changes) |

## Quick Reference

### Opening a Page (HYUIML)
```java
String html = """
    <div class="page-overlay">
        <div class="decorated-container" data-hyui-title="My Page">
            <div class="container-contents">
                <p>Content here</p>
                <button id="my-btn">Click Me</button>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .addEventListener("my-btn", CustomUIEventBindingType.Activating, (data, ctx) -> {
        playerRef.sendMessage(Message.raw("Clicked!"));
    })
    .open(store);
```

### Hywind-Style UI (0.7.0+)
```java
// Use Hywind styling instead of default Hytale styling
PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html, UIType.HYWIND)
    .open(store);

HudBuilder.hudForPlayer(playerRef)
    .fromHtml(html, UIType.HYWIND)
    .open(store);
```

### Page Close Listener (0.7.0+)
```java
PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .onDismiss((ctx) -> {
        // Called when page is closed
        LOGGER.atInfo().log("Page dismissed");
    })
    .open(store);
```

### Closing a Page Programmatically
To close a page from an event handler (e.g., a close button), use the `UIContext` to access the page:

```java
// CORRECT - Use ctx.getPage() from within an event handler
builder.addEventListener("close-btn", CustomUIEventBindingType.Activating,
    (data, ctx) -> ctx.getPage().ifPresent(page -> page.close()));
```

**Why this pattern?**
- `ctx.getPage()` returns `Optional<HyUIPage>` - the page this context belongs to
- `page.close()` properly closes the page via HyUI's internal mechanisms
- Works correctly with ESC key and other close methods

**WRONG approaches (don't use these):**
```java
// ❌ WRONG - UIManager doesn't have closePage method
plugin.getUIManager().closePage(playerRef.getUuid());

// ❌ WRONG - page variable may be null or stale
page.close();  // Captured from builder, not from context
```

### Navigating Between Pages

When navigating from one page to another (e.g., Stats → Attributes), **do NOT close the current page first**. Opening a new page automatically replaces the current one.

```java
// ✅ CORRECT - Don't close, just open the new page
builder.addEventListener("navigate-btn", CustomUIEventBindingType.Activating,
    (data, ctx) -> {
        // Get ref from the captured player
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        // Get store from ref (ensures consistency)
        Store<EntityStore> freshStore = ref.getStore();
        World world = freshStore.getExternalData().getWorld();

        world.execute(() -> {
            // Re-get playerRef from store for consistency
            PlayerRef freshPlayer = freshStore.getComponent(ref, PlayerRef.getComponentType());
            if (freshPlayer != null) {
                new OtherPage(plugin, freshPlayer).open(freshStore);
            }
        });
    });
```

**Why this pattern?**
1. **No `page.close()` before opening**: Closing the page first corrupts event bindings on the new page. HyUI handles page replacement automatically.
2. **Consistent ref/store/playerRef chain**: Derive everything from `player.getReference()`:
   - `Ref` → from `player.getReference()`
   - `Store` → from `ref.getStore()`
   - `World` → from `store.getExternalData().getWorld()`
   - `PlayerRef` → from `store.getComponent(ref, PlayerRef.getComponentType())`
3. **Deferred execution**: Use `world.execute()` to run on the world thread.

**WRONG approach (causes unresponsive buttons on new page):**
```java
// ❌ WRONG - closing before opening breaks event bindings
builder.addEventListener("navigate-btn", CustomUIEventBindingType.Activating,
    (data, ctx) -> {
        ctx.getPage().ifPresent(page -> page.close());  // DON'T DO THIS
        // ... open new page
    });
```

### Template Processor (Dynamic Content)
```java
TemplateProcessor template = new TemplateProcessor()
    .setVariable("playerName", playerRef.getUsername())
    .setVariable("items", itemList)
    .registerComponent("itemCard", """
        <div style="background-color: #2a2a2a; padding: 8;">
            <p>{{$name}}</p>
        </div>
    """);

String html = template.process("""
    <p>Player: {{$playerName}}</p>
    {{#each items}}
        {{@itemCard:name={{$name}}}}
    {{/each}}
""");

// Access template processor from context (0.6.1+)
TemplateProcessor tp = ctx.getTemplateProcessor();
```

### CSS-like Styling
```html
<div style="
    layout-mode: Top;
    anchor-width: 200;
    anchor-height: 100;
    anchor-left: 50;
    anchor-top: 30;
    background-color: #2a2a2a;
    padding: 10;
">
```

### Margin Aliases (0.6.1+)
```html
<!-- margin-* works as an alias for anchor-* -->
<div style="margin-left: 10; margin-top: 20;">
```

### Text Spans in Labels (0.8.0+)
```html
<p>
    <span data-hyui-color="#ff0000" data-hyui-bold="true">Red bold</span>
    <span data-hyui-color="#00ff00" data-hyui-italic="true">Green italic</span>
    <span data-hyui-monospace="true">Monospace text</span>
</p>
```

### Tooltip Text Spans (0.8.0+)
```html
<button id="my-btn">
    Hover me
    <tooltip>
        <span data-hyui-color="#ffcc00" data-hyui-bold="true">Title</span>
        <span data-hyui-color="#aaaaaa">Description text here</span>
    </tooltip>
</button>
```

### Native Tab Navigation (0.8.0+)
```html
<nav id="nav" class="native-tab-navigation" data-selected-tab="tab1">
    <button class="native-tab-button" data-hyui-tab-id="tab1">Overview</button>
    <button class="native-tab-button" data-hyui-tab-id="tab2">Details</button>
</nav>

<!-- Style variants -->
<nav class="native-tab-navigation header-style">...</nav>  <!-- Header tabs -->
<nav class="native-tab-navigation icon-style">...</nav>    <!-- Icon-only tabs -->
```

### Dynamic Panes (0.8.0+)
```html
<div class="dynamic-pane-container" style="layout-mode: Left;">
    <div class="dynamic-pane" data-hyui-min-size="100" data-hyui-resize-at="End">
        <p>Left pane (resizable)</p>
    </div>
    <div class="dynamic-pane">
        <p>Right pane</p>
    </div>
</div>
```

### New Element Types (0.8.0+)
```html
<!-- Action button -->
<button class="action-button" data-hyui-action="Submit">Submit</button>

<!-- Sliders -->
<input type="range" min="0" max="100" value="50" />
<input type="range" class="float-slider" min="0.0" max="1.0" value="0.5" step="0.1" />

<!-- Color picker -->
<input type="color" value="#ff0000" />
<color-picker-dropdown-box format="rgba" display-text-field="true"></color-picker-dropdown-box>

<!-- Block selector -->
<block-selector capacity="64"></block-selector>

<!-- Native timer label -->
<label class="native-timer-label" data-hyui-seconds="30" data-hyui-direction="Down"></label>
```

### Item Grid with Section ID (0.8.0+)
```html
<div class="item-grid"
     data-hyui-slots-per-row="5"
     data-hyui-inventory-section-id="42"
     data-hyui-are-items-draggable="true"
     data-hyui-display-item-quantity="true">
</div>
```

### Drawing Lines (for Skill Trees)
Lines are thin divs with background colors:
```html
<!-- Horizontal line -->
<div style="anchor-width: 100; anchor-height: 2; anchor-left: 50; anchor-top: 80; background-color: #44ff44;"></div>

<!-- Vertical line -->
<div style="anchor-width: 2; anchor-height: 60; anchor-left: 74; anchor-top: 80; background-color: #555555;"></div>
```

### Activatable Item Grid Slots (Clickable Item Displays)
Use `ItemGridSlot.setActivatable(true)` on slots within an `item-grid` that has `are-items-draggable="false"`. This enables `SlotClicking` events without drag behavior — the proper way to make clickable item displays.
```java
// Programmatic approach (dynamic content)
builder.getById("item-grid", ItemGridBuilder.class).ifPresent(grid -> {
    ItemGridSlot slot = new ItemGridSlot(new ItemStack("Weapon_Sword_Iron", 1));
    slot.setActivatable(true);
    slot.setName("Iron Sword");
    slot.setDescription("Lv12 Hotbar [GEAR]");
    grid.addSlot(slot);
});

// HYUIML approach (static content)
// <div class="item-grid-slot" data-hyui-item-id="Weapon_Sword_Iron"
//      data-hyui-activatable="true" data-hyui-name="Iron Sword"></div>
```
- Native quality backgrounds via `data-hyui-render-item-quality-background="true"` on the grid
- Hover tooltips from `setName()`/`setDescription()`
- `setActivatable()` returns void (not chainable)
- Used in `StonePickerPage`. See `hyui-element-constraints.md` Pattern 5 for full docs.

### Layout Modes
- `Top`, `Bottom`, `Left`, `Right` - Stack children in direction
- `Center`, `Middle`, `MiddleCenter` - Center content
- `Full` - Fill container (allows absolute positioning)
- `TopScrolling`, `BottomScrolling` - Scrollable
- `LeftCenterWrap` - Grid-like wrapping

### Key Imports
```java
import au.ellie.hyui.builder.*;
import au.ellie.hyui.template.TemplateProcessor;
import au.ellie.hyui.style.*;
import au.ellie.hyui.types.UIType;  // For Hywind support
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
```

## Important Notes

1. **HYUIML vs .ui files**: Elements from HYUIML support `addEventListener`. Elements from `.fromFile()` do NOT - use `.editElement()` instead.

2. **ID sanitization**: HyUI sanitizes IDs internally. Always use your original ID (e.g., `my-button`) in `addEventListener` and `getById`.

3. **Flex-weight**: Applies to wrapper groups (not the element itself). Be aware of layout behavior changes.

4. **Dynamic images**: Limited to 10 per page per player. Use `PngDownloadUtils.prefetchPngForPlayer()` to cache.

5. **No scripting**: `<script>` tags are ignored. All logic must be in Java.

6. **Dynamic pane attributes**: Currently not parsed from HYUIML. Use builders for `min-size`, `resize-at`, `resizer-size`, `resizer-background`.

7. **Text spans**: Use `<span>` or `<text-span>` children in labels. Supports `data-hyui-bold`, `data-hyui-italic`, `data-hyui-monospace`, `data-hyui-color`, `data-hyui-link`.

8. **Closing pages**: Use `ctx.getPage().ifPresent(page -> page.close())` from event handlers. Never try to close pages through UIManager or by capturing the page variable.

9. **Page navigation**: When navigating between pages (e.g., Stats → Attributes), **do NOT call `page.close()` before opening the new page**. Opening a new page auto-replaces the current one. Closing first corrupts event bindings on the new page.

10. **Decorated container body is bottom-aligned**: The `container-contents` area always places children at the bottom. You cannot change this with `layout-mode`, `anchor-vertical`, `padding`, `flex-weight` spacers, or `anchor-bottom`. Use **negative `margin-top`** on the content row to pull it upward. See `hyui-element-constraints.md` Pattern 6.

11. **MiddleCenter shrink-wraps children**: Never wrap content in `layout-mode: MiddleCenter` if children use `flex-weight` or `anchor-horizontal: 0` — it collapses their parent to content-width, giving spacers zero space. Place stretchy rows as direct children instead. See `hyui-element-constraints.md` Pattern 7.

12. **Negative margins work**: HyUI supports negative `margin-top` values (e.g., `margin-top: -8`) to pull elements upward from their default position. Confirmed working inside `container-contents`.

## Version History Highlights

### 0.8.x (Current)
- **0.8.4**: Buttons can now have background images on style states
- **0.8.3**: Fix: Label Builder now supports padding again
- Native tab navigation and dynamic pane builders
- Expanded HYUIML elements (action buttons, sliders, color pickers, dropdowns, item grids/slots, block selectors, native timer labels)
- Item grids: inventory section ID support (Integer type), source differentiation
- Tooltip textspans (multiple styled spans in tooltips)
- Label textspans (multiple styled spans in labels)
- Fix: Events work better for Mouse Released/Dismissing/Validating

### 0.7.0
- **Reworked styles system** - new styling approach (see `tutorial-styling-with-builders/`)
- **Hywind support** - `.fromHtml(html, UIType.HYWIND)` for Hywind-style UIs
- Page dismissal/close listener
- Fix: anchor null on height for NumberFieldBuilder

### 0.6.x
- Getter for template processor on UIContext
- `margin-*` aliases for `anchor-*` properties
- Removed clip children
- Per-player image caching
- Page refreshing at set rate
- Register template components from resources

## Updating HyUI

When updating HyUI version:
1. Check `changelog.md` for breaking changes
2. Update `hyuiFileId` in `build.gradle.kts`
3. Pull latest docs: `cd docs/HyUI && git pull`

## HyUI Discord

For help: https://discord.gg/NYeK9JqmNB
