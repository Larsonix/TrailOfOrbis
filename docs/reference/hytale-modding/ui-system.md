# Hytale UI System Reference

Three UI approaches, all using the same `.ui` template format and `UICommandBuilder`/`UIEventBuilder`.

| Approach | Class | Use Case |
|----------|-------|----------|
| **Page** | `InteractiveCustomUIPage<T>` | Full-screen overlay (shops, settings, skill trees) |
| **HUD** | `CustomUIHud` | Persistent overlay (boss bars, XP bars, status) |
| **Anchor** | `AnchorActionModule` | Inject into native pages (map overlay, inventory addon) |

---

## InteractiveCustomUIPage

Full-screen pages with event callbacks via a CODEC-defined data class.

```java
public class MyPage extends InteractiveCustomUIPage<MyPage.MyData> {

    public MyPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, MyData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append("Pages/MyPage.ui");
        ui.set("#TitleLabel.Text", "My Page");
        ui.set("#EnableCheckbox.Value", true);

        // Button click
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton",
            EventData.of("action", "save"), false);

        // Dropdown change — @prefix captures current value at event time
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ModeDropdown",
            EventData.of("action", "mode_change")
                     .append("@DropdownValue", "#ModeDropdown.Value"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store, @Nonnull MyData data) {
        super.handleDataEvent(ref, store, data);
        if ("save".equals(data.action)) { /* ... */ }
    }

    public static class MyData {
        public static final BuilderCodec<MyData> CODEC =
            BuilderCodec.<MyData>builder(MyData.class, MyData::new)
                .addField(new KeyedCodec<>("action", Codec.STRING),
                    (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("@DropdownValue", Codec.STRING),
                    (d, v) -> d.dropdownValue = v, d -> d.dropdownValue)
                .build();
        private String action;
        private String dropdownValue;
    }
}
```

**Open/Close/Refresh:**

```java
player.getPageManager().openCustomPage(ref, store, new MyPage(playerRef));  // open
this.close();                                                                // close from within
player.getPageManager().setPage(ref, store, Page.None);                      // close to game

// Incremental update (inside handleDataEvent)
UICommandBuilder ui = new UICommandBuilder();
ui.set("#StatusLabel.Text", "Updated!");
sendUpdate(ui, new UIEventBuilder(), false);  // false = delta, not full rebuild
```

**CustomPageLifetime:** `CanDismiss` (Escape closes), `CanDismissOrCloseThroughInteraction`, `CantClose` (server must close).

**Custom page supplier (NPC interactions):**
```java
getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC)
    .register("MyCustomPage", MyPageSupplier.class, MyPageSupplier.CODEC);
```

---

## CustomUIHud

Persistent overlay. **Single slot per player** -- setting a new HUD replaces the previous. Use `MultipleCustomUIHud` (HyUI library) for multiple simultaneous HUDs.

```java
public class MyHud extends CustomUIHud {
    public MyHud(@Nonnull PlayerRef playerRef) { super(playerRef); }

    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        ui.append("Hud/MyHud/StatusBar.ui");
        ui.set("#HealthLabel.Text", "100/100");
        ui.set("#HealthBar.Value", 1.0f);
    }
}
```

**Attach/Remove:**
```java
player.getHudManager().setCustomHud(playerRef, new MyHud(playerRef));  // attach
player.getHudManager().setCustomHud(playerRef, null);                   // remove
CustomUIHud current = player.getHudManager().getCustomHud();            // get
```

**Incremental update:**
```java
UICommandBuilder delta = new UICommandBuilder();
delta.set("#HealthBar.Value", 0.75f);
hud.update(false, delta);  // false = don't clear, apply delta

// Append dynamic content then access by index
delta.append("#BarsList", "Hud/MyHud/BarEntry.ui");
delta.set("#BarsList[0] #NameLabel.Text", "Strength");
```

**HUD cleanup (critical):** Always `hide()` before `remove()` when a world change follows. Without `hide()`, the removal packet may not reach the client before the teleport.

```java
world.execute(() -> {
    hud.hide();    // immediate visual update
    hud.remove();  // remove from tracking
});
```

---

## AnchorActionModule

Inject UI into anchor points on native Hytale pages. Known anchor: `"MapServerContent"` (world map).

```java
// Register action handler
AnchorActionModule.get().register("mymod_action",
    (PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, JsonObject data) -> {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        player.getPageManager().openCustomPage(ref, store, new MyPage(playerRef));
    });

// Send anchor UI
UICommandBuilder commands = new UICommandBuilder();
UIEventBuilder events = new UIEventBuilder();
commands.append("Hud/MyMod/MapPanel.ui");
commands.set("#Count.Text", "Items: " + count);
events.addEventBinding(CustomUIEventBindingType.Activating, "#ManageBtn",
    EventData.of("action", "mymod_action"), false);

playerRef.getPacketHandler().writeNoCache(
    new UpdateAnchorUI("MapServerContent", true, commands.getCommands(), events.getEvents()));

// Clear anchor UI
playerRef.getPacketHandler().writeNoCache(new UpdateAnchorUI("MapServerContent", true, null, null));

// Cleanup on disable
AnchorActionModule.get().unregister("mymod_action");
```

---

## UICommandBuilder

Constructs batched UI mutations. Used by all three approaches.

```java
UICommandBuilder ui = new UICommandBuilder();

// Properties
ui.set("#Label.Text", "Hello");                        // text
ui.set("#Label.TextSpans", Message.raw("Bold").bold(true)); // rich text
ui.set("#Panel.Visible", true);                        // visibility
ui.set("#Btn.Disabled", false);                        // disabled state
ui.set("#Bar.Value", 0.75f);                           // progress (0.0-1.0)
ui.set("#Img.AssetPath", "Common/Icon.png");           // image
ui.set("#Img.Background", "Assets/Banner.png");        // background image
ui.set("#Img.Background.Color", "#FF0000");            // background tint
ui.set("#Slot.TooltipText", "Iron Sword");             // tooltip
ui.set("#Field.Value", 100);                           // number field
ui.set("#Check.Value", true);                          // checkbox
ui.set("#Drop.Value", "hard");                         // dropdown selected key
ui.set("#Drop.Entries", List.of(                       // dropdown options
    new DropdownEntryInfo(LocalizableString.fromString("Easy"), "easy"),
    new DropdownEntryInfo(LocalizableString.fromString("Hard"), "hard")));

// Structured objects
Anchor anchor = new Anchor();
anchor.setWidth(Value.of(200));
anchor.setLeft(Value.of(20));
ui.setObject("#Panel.Anchor", anchor);

// Template operations
ui.append("#List", "Pages/ItemRow.ui");          // append child template
ui.appendInline("#Container", "<div>...</div>"); // inline HYUIML (rare)
ui.clear("#List");                               // clear children
ui.remove("#Panel");                             // remove element
ui.setNull("#Field.Value");                      // set to null
```

**Selector syntax:** `#Id`, `#Parent #Child`, `#List[0] #Child`, `.Property`

---

## UIEventBuilder

Binds client events to server callbacks.

```java
UIEventBuilder events = new UIEventBuilder();
events.addEventBinding(type, selector, eventData, continuous);
```

| Type | Fires On | Elements |
|------|----------|----------|
| `Activating` | Click | `TextButton`, `Button` |
| `ValueChanged` | Value change | `DropdownBox`, `CheckBox`, `TextField`, `NumberField`, `ColorPicker` |
| `SlotClicking` | Slot click | Item grids with activatable slots |
| `RightClicking` | Right-click | Any supporting element |

**EventData:** `EventData.of("key", "value")` for static data. `.put("key", val)` to chain. `.append("@Key", "#Element.Value")` for dynamic capture -- the `@` prefix tells the client to read the element's current value at event time.

---

## Native .ui Format

Custom declarative format. Not HTML/XML. Curly-brace blocks, colon properties, macro imports.

### Elements

| Element | Children | Text | Purpose |
|---------|----------|------|---------|
| `Group` | Yes | No | Layout container |
| `Label` | No | Yes | Text display |
| `TextButton` | No | Yes | Clickable button with label |
| `Button` | No | No | Clickable button (icon only) |
| `ProgressBar` | No | No | Fill bar (0.0-1.0) |
| `AssetImage` | No | No | Image display |
| `TextField` | No | Editable | Text input |
| `CompactTextField` | No | Editable | Compact text input |
| `DropdownBox` | No | Via entries | Dropdown selector |
| `CheckBox` | No | No | Boolean toggle |
| `ItemIcon` | No | No | Item model rendering |

### Layout Modes

`Top` (stack down), `Bottom` (stack up), `Left` (row), `Right` (row RTL), `Center` (h-center), `Middle` (v-center), `Full` (fill), `TopScrolling` (scrollable list -- requires `ScrollbarStyle: $C.@DefaultScrollbarStyle`).

### Anchor, FlexWeight, Padding

```
Anchor: (Width: 600, Height: 400);              // fixed size
Anchor: (Full: 0);                               // fill parent
Anchor: (Horizontal: 0);                         // stretch width
Anchor: (Width: 200, Left: 10, Top: 5);          // size + offset

FlexWeight: 1;                                    // fill remaining space in Left/Top layout

Padding: (Full: 12);                              // all sides
Padding: (Horizontal: 8, Vertical: 4);            // pairs
Padding: (Top: 10, Bottom: 5, Left: 8, Right: 8); // individual
```

### Background and Colors

```
Background: #1a2533;                 // solid hex
Background: #1a2533(0.6);           // hex + opacity
Background: #1a2533aa;              // RRGGBBAA
Background: "Images/Texture.png";    // texture
Background: @MyPatchStyle;          // 9-slice

OutlineColor: #3a4a5a(0.5);
OutlineSize: 1;
```

### Text Styling (Label)

```
Style: (FontSize: 16, TextColor: #ffffff, RenderBold: true, RenderUppercase: true,
        HorizontalAlignment: Center, VerticalAlignment: Center, Wrap: true,
        ShrinkTextToFit: true, MinShrinkTextToFitFontSize: 8,
        OutlineColor: #000000(0.5), FontName: "Secondary");
```

### Imports, Macros, Variables

```
$C = "../Common.ui";                              // import
$C.@PageOverlay { }                               // dimmed fullscreen overlay
$C.@DecoratedContainer { #Title { ... } #Content { ... } }  // standard window
$C.@BackButton { }                                // ESC button
$C.@Title { @Text = "My Title"; }                 // title text
$C.@DropdownBox #MyDrop { Anchor: (Width: 200); } // styled elements
$C.@CheckBox #MyCheck { }
$C.@NumberField #MyField { Anchor: (Width: 120); Format: (MaxDecimalPlaces: 3, Step: 1); }
$C.@TextField #MyText { Anchor: (Height: 28); }

// Variables and styles
@MyStyle = LabelStyle(FontSize: 18, TextColor: #93844c, RenderBold: true);
@MyPatch = PatchStyle(TexturePath: "Bg.png", Border: 6);

// TextButtonStyle with states
@BtnStyle = TextButtonStyle(
    Default: (Background: #2a4a3a, LabelStyle: (FontSize: 14, TextColor: #bfcdd5)),
    Hovered: (Background: #3a5a4a, LabelStyle: (FontSize: 14, TextColor: #ffffff)),
    Pressed: (Background: #4a6a5a, LabelStyle: (FontSize: 14, TextColor: #ffffff)),
    Disabled: (Background: #1a2a1a, LabelStyle: (FontSize: 14, TextColor: #555555)),
    Sounds: $C.@ButtonSounds);

// Spread (inherit + override)
@HoverStyle = (...@BaseStyle, TextColor: #ffffff);

// Reusable element template
@FieldRow = Group { LayoutMode: Left; Anchor: (Height: 36); Padding: (Vertical: 4); };
@FieldRow { Label { Text: "Name"; FlexWeight: 1; } }

// Translation reference
Label { Text: %server.customUI.myLabel; }  // % prefix = i18n key
```

### ProgressBar

```
ProgressBar #Bar {
    Anchor: (Width: 200, Height: 8);
    BarTexturePath: "Images/Fill.png";
    EffectTexturePath: "../../Common/ProgressBarEffect.png";
    EffectWidth: 100; EffectHeight: 8; EffectOffset: 5;
}
```

### ColorPicker

```
ColorPicker #Picker { Anchor: (Width: 240, Height: 220); Format: Rgb; Style: $C.@DefaultColorPickerStyle; }
```

---

## Complete Page Example (.ui)

```
$C = "../Common.ui";
@FieldLabelStyle = LabelStyle(FontSize: 14, TextColor: #96a9be, VerticalAlignment: Center);
@FieldRow = Group { LayoutMode: Left; Anchor: (Height: 36); Padding: (Vertical: 4); };

$C.@PageOverlay {}

$C.@DecoratedContainer {
    Anchor: (Width: 600, Height: 500);
    #Title { $C.@Title { @Text = "Settings"; } }
    #Content {
        LayoutMode: Top;
        Group #ContentArea {
            FlexWeight: 1;
            LayoutMode: TopScrolling;
            ScrollbarStyle: $C.@DefaultScrollbarStyle;
            Group { LayoutMode: Top;
                @FieldRow {
                    Label { Text: "Difficulty"; Style: @FieldLabelStyle; FlexWeight: 1; }
                    $C.@DropdownBox #DifficultyDrop { Anchor: (Width: 200, Height: 28); }
                }
                @FieldRow { Anchor: (Top: 4);
                    $C.@CheckBox #PvpEnabled {}
                    Label { Text: "Enable PvP"; Anchor: (Left: 10);
                            Style: (FontSize: 14, TextColor: #bfcdd5); }
                }
                @FieldRow { Anchor: (Top: 4);
                    Label { Text: "Spawn Radius"; Style: @FieldLabelStyle; FlexWeight: 1; }
                    $C.@NumberField #SpawnRadius { Anchor: (Width: 120, Height: 28); }
                }
            }
        }
        Group { LayoutMode: Left; Anchor: (Height: 50, Top: 10);
            Group { FlexWeight: 1; }
            TextButton #CloseBtn { Style: $C.@DefaultTextButtonStyle;
                Anchor: (Width: 120, Height: 40); Text: "Close"; }
        }
    }
}
$C.@BackButton {}
```

---

## Notifications and Titles

Lightweight feedback without a page or HUD.

```java
// Toast notification
NotificationUtil.sendNotification(
    playerRef.getPacketHandler(),
    Message.raw("Level Up!"),
    Message.raw("You reached level 50"),
    NotificationStyle.Success);

// Fullscreen event title/banner
EventTitleUtil.showEventTitleToPlayer(
    playerRef,
    Message.raw("LEVEL 50").color("#ffd700").bold(true),  // title
    Message.raw("Milestone Reached!"),                      // subtitle
    true,    // isMajor (larger treatment)
    null,    // reserved (pass null)
    3.0f,    // duration seconds
    0.5f,    // fadeIn seconds
    0.5f);   // fadeOut seconds
```

---

## Key Imports

```java
// Page
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

// HUD
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;

// Anchor
import com.hypixel.hytale.server.core.modules.anchoraction.AnchorActionModule;
import com.hypixel.hytale.protocol.packets.interface_.UpdateAnchorUI;

// Shared
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

// Notifications
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
```

---

## Source Mods Analyzed

Built from 118+ `.ui` templates and Java implementations across Loot4Everyone and community mods.
