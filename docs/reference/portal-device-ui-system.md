# Portal Device UI System — Complete Reference

How Hytale's Portal Device (Ancient Gateway) UI works, how we override it, and how to build a custom realm map summon page.

## Vanilla Architecture

### Flow: Player Presses F on Portal_Device

```
Player presses F on Portal_Device block
  |
  v
OpenCustomUIInteraction.firstRun()
  |-- Gets CustomPageSupplier from PAGE_CODEC registry (key: "PortalDevice")
  |-- Calls supplier.tryCreate(ref, store, playerRef, context)
  |
  |-- Returns CustomUIPage? --> Page.open(store) renders it
  |-- Returns null?          --> Nothing happens (interaction ends)
  |
  v
PortalDevicePageSupplier.tryCreate()
  |-- Gets block position from InteractionContext
  |-- Gets PortalDevice component from block entity
  |
  |-- No PortalDevice? --> Creates new one from config
  |-- Has destinationWorld? --> PortalDeviceActivePage (shows timer, players, re-entry)
  |-- No destination? --> PortalDeviceSummonPage (shows portal info, summon button)
```

### Native UI Templates

Located in `Assets/Common/UI/Custom/Pages/`:

| File | When Shown | Purpose |
|------|-----------|---------|
| `PortalDeviceSummon.ui` | No portal spawned, valid key held | Full summon UI with artwork, info, button |
| `PortalDeviceError.ui` | No key, wrong item, or error state | Compact error dialog |
| `PortalDeviceActive.ui` | Portal already spawned | Shows timer, players inside, death warning |

### PortalDeviceSummon.ui Layout

```
+------------------------------------------------------------------+
| [ANCIENT GATEWAY]  (title)                                       |
+------------------------------------------------------------------+
| +--LEFT PANE (431x746)--+ +--RIGHT PANE (431)------------------+ |
| |                        | |                                     | |
| | [Splash Art 345x629]  | | [Portal Name]  (centered, 24pt)     | |
| | with runic border     | | ----------------------------------- | |
| | and vignette overlay   | | [WIP Notice with icon]              | |
| |                        | |                                     | |
| | [Cursebreaker Logo]   | | TIME LIMIT                           | |
| |                        | |   * X mins to explore               | |
| |                        | |   * Y mins void invasion (if any)   | |
| |                        | |                                     | |
| |                        | | OBJECTIVES                          | |
| |                        | |   * Objective 1                     | |
| |                        | |   * Objective 2                     | |
| |                        | |                                     | |
| |                        | | TIPS                                | |
| |                        | |   * Tip 1                           | |
| |                        | |   * Tip 2                           | |
| |                        | |                                     | |
| |                        | | [Pills/Tags]                        | |
| |                        | |                                     | |
| |                        | | ----------------------------------- | |
| |                        | | [Flavor text / description]         | |
| |                        | |                                     | |
| |                        | | [Consumes your key]                 | |
| |                        | | [====  SUMMON PORTAL  ====]         | |
| +------------------------+ +-------------------------------------+ |
+------------------------------------------------------------------+
| [Back]                                                            |
+------------------------------------------------------------------+
```

### Key UI Element IDs (PortalDeviceSummon.ui)

| Element ID | Type | What It Shows | Set By Server |
|-----------|------|---------------|---------------|
| `#Artwork.Background` | TexturePath | Splash art image | `PortalDescription.getSplashImageFilename()` |
| `#Title0.TextSpans` | Message | Portal display name | `PortalDescription.getDisplayName()` |
| `#FlavorLabel.TextSpans` | Message | Lore/description text | `PortalDescription.getFlavorText()` |
| `#ExplorationTimeText.TextSpans` | Message | "X mins to explore" | Computed from `PortalKey.timeLimitSeconds` |
| `#BreachTimeBullet.Visible` | boolean | Show/hide void breach line | `portalType.isVoidInvasionEnabled()` |
| `#BreachTimeText.TextSpans` | Message | "Y mins void invasion" | From `PortalGameplayConfig` |
| `#ObjectivesList` | Container | Bullet-point objectives | `PortalDescription.getObjectivesKeys()` |
| `#TipsList` | Container | Bullet-point tips | `PortalDescription.getWisdomKeys()` |
| `#Pills` | Container | Pill tag badges | `PortalDescription.getPillTags()` |
| `#SummonButton` | TextButton | "Summon Portal" button | Event bindings only — **DO NOT set .Text** (native TextButton, crashes client) |

### Replacing Anonymous Section Headers (clear + rebuild pattern)

The `#Objectives` and `#Tips` groups each contain an **anonymous Label** (the "OBJECTIVES"/"WISDOM" header) that cannot be targeted by selector. The proper fix uses `UICommandBuilder.clear()`:

```java
// 1. Clear ALL children (removes anonymous header + #ObjectivesList)
cmd.clear("#Objectives");

// 2. Add our own header via appendInline
cmd.appendInline("#Objectives",
    "Label { Text: Prefixes; Style: (FontSize: 16, TextColor: #778292, RenderBold: true, RenderUppercase: true); Anchor: (Bottom: 4); }");

// 3. Add a new list container
cmd.appendInline("#Objectives", "Group #PrefixList { LayoutMode: Top; }");

// 4. Append bullet points to our new container
for (int i = 0; i < prefixes.size(); i++) {
    cmd.append("#PrefixList", "Pages/Portals/BulletPoint.ui");
    cmd.set("#PrefixList[" + i + "] #Label.TextSpans",
        Message.raw(formatModifier(prefixes.get(i), qualityMult)).color("#FF6666"));
}
```

See `docs/reference/native-ui-command-builder.md` for the full UICommandBuilder API reference.
| `#Vignette.Visible` | boolean | Hover glow effect | Toggled by MouseEntered/MouseExited events |

### How Server Populates the Template

```java
// In PortalDeviceSummonPage.build():

// 1. Load the template
commandBuilder.append("Pages/PortalDeviceSummon.ui");

// 2. Set artwork
commandBuilder.set("#Artwork.Background", "Pages/Portals/" + description.getSplashImageFilename());

// 3. Set text
commandBuilder.set("#Title0.TextSpans", description.getDisplayName());
commandBuilder.set("#FlavorLabel.TextSpans", description.getFlavorText());

// 4. Set time
commandBuilder.set("#ExplorationTimeText.TextSpans",
    Message.translation("server.customUI.portalDevice.durationMins").param("time", minutes));

// 5. Add pills
commandBuilder.append("#Pills", "Pages/Portals/Pill.ui");  // Per-pill sub-template
commandBuilder.set("#Pills[0].Background.Color", hexColor);
commandBuilder.set("#Pills[0] #Label.TextSpans", pillMessage);

// 6. Add objectives/tips bullet points
commandBuilder.append("#ObjectivesList", "Pages/Portals/BulletPoint.ui");  // Per-item

// 7. Bind events
eventBuilder.addEventBinding(Activating, "#SummonButton", EventData.of("Action", "SummonActivated"), false);
eventBuilder.addEventBinding(MouseEntered, "#SummonButton", EventData.of("Action", "SummonMouseEntered"), false);
eventBuilder.addEventBinding(MouseExited, "#SummonButton", EventData.of("Action", "SummonMouseExited"), false);
```

### PortalDeviceSummonPage.handleDataEvent() — Summon Flow

When player clicks "Summon Portal":

```java
// 1. Re-validate all preconditions (same as computeState)
// 2. Get PortalDevice, PortalKey, PortalType
// 3. Change block state to "Spawning" (visual animation)
world.setBlockInteractionState(position, blockType, config.getSpawningState());

// 4. Get portal key item and its PortalType
// 5. Play sound
SoundUtil.playSoundEvent3d(world, "SFX_Portal_Neutral_Summon", position);

// 6. Decrement key from inventory
player.getInventory().removeOne(hotbarSlotIndex);

// 7. Spawn instance async
InstancesPlugin.spawnInstance(instanceId, world).thenAcceptAsync(targetWorld -> {
    // Set gameplay config
    // Create PortalWorld resource
    // Find spawn point (PortalSpawnFinder)
    // Spawn return portal block
    // Set PortalDevice.destinationWorld = targetWorld
    // Change block state to "Active"
}, world::execute);
```

### PortalDeviceActivePage — Already-Active Portal

Shows when portal is already linked to a destination world:

| Element | Shows |
|---------|-------|
| `#PortalTitle.TextSpans` | Portal name |
| `#PortalDescription.TextSpans` | Time limit description |
| `#PlayersInside.TextSpans` | Players currently in portal (names if <= 4, count if > 4) |
| `#RemainingDuration.TextSpans` | "X:XX remaining" |
| `#PortalIsOpen.Visible` | "Be the first!" if timer not started |
| `#Died.Visible` | Shows death skull + "Forsaken" warning if player died |

---

## Our Current Override

### RealmPortalDevicePageSupplier.tryCreate()

We replace the vanilla `PortalDevicePageSupplier` via reflection in `TrailOfOrbis.replacePortalDevicePageSupplier()`.

**Current behavior:**

```
Player presses F on Portal_Device with realm map in hand
  |
  v
RealmPortalDevicePageSupplier.tryCreate()
  |-- Check: isRealmMap(heldItem)?
  |
  |-- YES (realm map):
  |   |-- handleRealmMapActivation() — RETURNS NULL (no UI shown)
  |   |-- Immediately: validates map, opens realm, activates portal, teleports player
  |   |-- All in background via CompletableFuture chain
  |   '-- Player gets chat messages for success/failure
  |
  |-- NO (vanilla fragment key or no item):
  |   |-- handleVanillaPortalDevice()
  |   '-- Delegates to original PortalDeviceSummonPage/PortalDeviceActivePage
```

**Key insight: We currently skip the UI entirely for realm maps.** The `tryCreate()` returns `null` after kicking off realm creation, so the player never sees the vanilla portal page. We do all feedback via chat messages.

### What the User Wants Changed

Instead of instant portal creation with chat feedback, show the **vanilla Portal Device UI** customized with realm map info:
- Replace portal name with map name/title
- Replace lore/flavor text with map stats (level, biome, modifiers, quality)
- Replace time limit with realm time limit
- Replace objectives/tips with modifier details
- Keep the "Summon Portal" button — portal only spawns when clicked
- Keep splash art (use biome-themed artwork)

---

## Realm Map Data Available for UI

From `RealmMapData` (what we can show in the portal page):

| Field | Type | Example | UI Mapping |
|-------|------|---------|------------|
| `level` | int | 50 | Title subtitle or info line |
| `rarity` | GearRarity | LEGENDARY | Title color + rarity badge |
| `quality` | int | 92 | Quality line |
| `biome` | RealmBiomeType | FOREST | Splash art + biome name |
| `size` | RealmLayoutSize | LARGE | Size indicator |
| `shape` | RealmLayoutShape | CIRCLE | Shape indicator |
| `prefixes` | List<RealmModifier> | +25% Monster Damage | Objectives/difficulty section |
| `suffixes` | List<RealmModifier> | +15% Item Quantity | Tips/reward section |
| `fortunesCompassBonus` | int | 10 | IIQ bonus line |
| `corrupted` | boolean | false | Corruption badge |
| `identified` | boolean | true | Whether mods are shown |

### RealmMapTooltipBuilder Colors (reference for UI consistency)

| Element | Hex Color |
|---------|-----------|
| Difficulty modifiers | `#FF6666` (red) |
| Reward modifiers | `#FFD700` (gold) |
| Fortune's Compass | `#7CF2A7` (green) |
| Biome name | `#88CCFF` (blue) |
| Size name | `#AAFFAA` (green) |
| Quality | Varies by tier |

---

## Implementation Approach: Custom PortalDeviceSummonPage

### Option A: Reuse Native PortalDeviceSummon.ui (Recommended)

Load the same `PortalDeviceSummon.ui` template that vanilla uses, but populate it with realm map data instead of PortalType/PortalKey data.

**Advantages:**
- Uses the exact same polished UI that vanilla fragments use
- No custom UI files needed in our asset pack
- Artwork panel, runic border, summon button all work natively
- Event bindings handled the same way
- Player gets the familiar portal interaction pattern

**How it works:**
- Create a new `RealmMapSummonPage extends InteractiveCustomUIPage<Data>`
- In `build()`: load `Pages/PortalDeviceSummon.ui`, set element values from `RealmMapData`
- In `handleDataEvent()`: when "SummonActivated", do our realm creation + portal activation
- Return this page from `RealmPortalDevicePageSupplier.tryCreate()` instead of `null`

**Mapping realm data to vanilla UI elements:**

| Vanilla Element | Vanilla Source | Our Source |
|----------------|---------------|------------|
| `#Title0.TextSpans` | `PortalDescription.getDisplayName()` | Map rarity + biome name (e.g., "Legendary Forest Realm") |
| `#FlavorLabel.TextSpans` | `PortalDescription.getFlavorText()` | Map level + quality + size summary |
| `#Artwork.Background` | `PortalDescription.getSplashImage()` | Biome-themed artwork from our asset pack |
| `#ExplorationTimeText.TextSpans` | PortalKey time limit | `RealmMapData` time limit (from config, based on size) |
| `#ObjectivesList` | Objectives translation keys | Difficulty modifiers (prefixes) |
| `#TipsList` | Tips translation keys | Reward modifiers (suffixes) |
| `#Pills` | `PillTag[]` badges | Rarity pill + biome pill + size pill |
| `#SummonButton` | "Summon Portal" | "Open Realm" |

**Sections to hide/customize:**
- `#BreachTimeBullet.Visible = false` — no void invasion in realms
- `#FlavorText` section — repurpose for quality/IIQ summary instead of lore
- WIP notice — hide it (`#WIPNotice.Visible = false` or similar)

### Option B: Custom HyUI Page (Not Recommended)

Build an entirely custom HyUI page for realm maps. More control but much more work, doesn't get the polished vanilla look, and has all the HyUI crash risks.

---

## Key Classes to Read Before Implementation

| Class | Path | Why |
|-------|------|-----|
| `PortalDeviceSummonPage` | Decompiled: `builtin/portals/ui/` | Reference implementation — copy structure |
| `InteractiveCustomUIPage` | Decompiled: `entity/entities/player/pages/` | Base class for pages with event handling |
| `UICommandBuilder` | Decompiled: `server/core/ui/builder/` | How to set UI element values |
| `UIEventBuilder` | Decompiled: `server/core/ui/builder/` | How to bind button events |
| `RealmPortalDevicePageSupplier` | Our code: `maps/listeners/` | Where to return the new page |
| `RealmMapTooltipBuilder` | Our code: `maps/tooltip/` | Template for how we format map info |
| `RealmMapData` | Our code: `maps/core/` | All available map data fields |

---

## Splash Art Assets

The vanilla UI loads artwork from `Pages/Portals/{filename}`. We need biome-themed artwork in our asset pack at `Common/UI/Custom/Pages/Portals/`.

Current vanilla artwork: `DefaultArtwork.png` (345x629 pixels).

We can either:
1. Create per-biome artwork files and reference them dynamically
2. Use a single "realm" artwork and tint via the background color
3. Reuse `DefaultArtwork.png` for now and add custom art later

---

## Thread Safety Notes

- `tryCreate()` runs on the **world thread** (inside interaction handling)
- `build()` runs on the **world thread** (page rendering)
- `handleDataEvent()` runs on the **world thread** (event dispatch)
- Realm creation (`realmsManager.openRealm()`) returns `CompletableFuture` — chain with `thenAcceptAsync(callback, world::execute)` to stay on world thread
- Portal device activation requires world thread access (block entity manipulation)

---

## History

- **2026-03-29**: Initial documentation. Compiled from decompiled vanilla source, native .ui templates, our current implementation, and API docs.
