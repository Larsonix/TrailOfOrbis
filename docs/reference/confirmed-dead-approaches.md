# Confirmed Dead Approaches

Approaches, patterns, and techniques that have been tried and proven NOT to work under specific conditions. Organized by domain.

**How to use this file:**
- Check BEFORE attempting an approach — it may already be known-dead
- Each entry records WHAT failed, WHY, and under WHAT CONDITIONS
- "Dead under condition X" does NOT mean dead under condition Y — always note the specifics
- Add new entries when you discover something doesn't work. Date every entry.

---

## HyUI / Client UI

### Crash-Inducing Patterns

| Pattern | Why It Fails | Date |
|---------|-------------|------|
| `<div>` or `<p>` inside `<button>` (non-raw) | Standard buttons only accept text content. Children crash the client. Use `class="raw-button"` for complex children. | 2026-02 |
| Custom button class + `addEventListener(Activating)` | Only recognized HyUI button classes support `Activating` events. Custom classes silently fail or crash. Use `secondary-button`, `tertiary-button`, `small-*` variants as base + inline `style=""`. | 2026-02 |
| `back-button` class + `addEventListener(Activating)` | `back-button` doesn't support `Activating` event binding. Use `secondary-button` or `tertiary-button` instead. | 2026-02 |
| `flex-weight` / `anchor-*` on `<p>` or `<label>` | Labels only support `color` and `font-size`. Layout properties on labels crash the client. Wrap in a `<div>` instead. | 2026-02 |
| `text-align` on labels | Not supported. Use parent `layout-mode: Center` instead. | 2026-02 |
| `font-weight` on labels | Not supported in HyUI. No workaround. | 2026-02 |
| `text-transform` on labels | Not supported in HyUI. No workaround. | 2026-02 |

### Layout Patterns That Don't Work

| Pattern | What Actually Happens | Correct Alternative | Date |
|---------|----------------------|-------------------|------|
| `data-hyui-are-items-draggable="false"` | Disables ALL slot events (SlotClicking, SlotMouseEntered, etc.), not just drag. | Keep `true`, use `setActivatable(true)` per slot. Don't register a `Dropped` handler — items snap back automatically. | 2026-02 |
| `layout-mode: Full` on `container-contents` | Content escapes the decorated container bounds entirely. | Use negative `margin-top` on content rows to position vertically. | 2026-02 |
| `layout-mode: Middle` / `MiddleCenter` on `container-contents` | Does nothing — decorated container body always bottom-aligns. | Use negative `margin-top` on the content row to pull it up from bottom. | 2026-02 |
| `anchor-vertical: 0` on `container-contents` | Does nothing inside decorated container body. | Negative `margin-top`. | 2026-02 |
| `padding` on `container-contents` | Does nothing inside decorated container body. | Negative `margin-top`. | 2026-02 |
| `flex-weight` spacers above/below in `container-contents` | Does nothing — bottom-aligned layout ignores spacers. | Negative `margin-top`. | 2026-02 |
| `layout-mode: MiddleCenter` wrapping stretchy children | MiddleCenter shrink-wraps children to intrinsic size. Kills `flex-weight` and `anchor-horizontal: 0` stretching. | Place stretchy rows as direct children of their parent, not inside MiddleCenter wrapper. | 2026-02 |

### Native UI Server-Side Overrides

| Pattern | Why It Fails | Correct Alternative | Date |
|---------|-------------|-------------------|------|
| `UICommandBuilder.set("#NativeTextButton.Text", ...)` | Native `.ui` TextButton elements don't support server-side `.Text` property override. Causes immediate client crash (Crash - Crash disconnect). | Don't override native button text. The text comes from translation keys baked into the `.ui` template. Accept the vanilla text. | 2026-03 |
| Targeting anonymous `.ui` elements (no `id=`) | UICommandBuilder selectors only work with `#id`-based targeting. Anonymous Labels, Groups, etc. in native `.ui` templates cannot be selected or modified. | Use `cmd.clear("#Parent")` to wipe all children (including anonymous ones), then `cmd.appendInline("#Parent", "Label { ... }")` to rebuild with your own content. See `native-ui-command-builder.md`. | 2026-03 |
| Hiding `#FlavorText` when `#Objectives`/`#Tips` are hidden in PortalDeviceSummon.ui | A `FlexWeight: 1` spacer above `#FlavorText` expands when sections are hidden, pushing `#FlavorText` out of visible bounds. Multi-line content in `#FlavorLabel` becomes invisible. | Keep `#Objectives`/`#Tips` visible. Use `clear()` + `appendInline()` to replace their content instead of hiding them. | 2026-03 |

### Networking / Packets

| Pattern | What Actually Happens | Correct Alternative | Date |
|---------|----------------------|-------------------|------|
| `BuilderToolLaserPointer` with arbitrary `playerNetworkId` | Client validates against tracked entities. Unknown IDs silently discarded — beam never renders. | Must use real `NetworkId` component values from entities the client is tracking (nearby, within view range). | 2026-03 |
| Sending packets during client world loading | Packets are lost. Client drops them during load. | Wait ~2.5s after world load before sending beams or other visual packets. | 2026-03 |

## Events / Lifecycle

| Pattern | Why It Fails | Correct Alternative | Date |
|---------|-------------|-------------------|------|
| `@EventHandler` annotations | Don't work in Hytale. Events are never dispatched to annotated methods. | `getEventRegistry().register(EventClass.class, handler)` | 2026-02 |
| `hud.remove()` before world change | Queued removal may not reach client before teleport. HUD stays visible. | `hud.hide(); hud.remove();` — hide forces immediate visual update. | 2026-02 |
| `LivingEntityInventoryChangeEvent` | Removed in Hytale 2026.03.26. | `InventoryChangeEvent` via `EntityEventSystem` (ECS event). | 2026-03 |
| `Inventory.markChanged()` | Removed in Hytale 2026.03.26. | `InventoryComponent.markDirty()` | 2026-03 |
| `BlockStateModule` / `ItemContainerState` | Removed in Hytale 2026.03.26. | `ItemContainerBlock` | 2026-03 |
| `MapImage.data` direct access | Removed in Hytale 2026.03.26. | Use palette + packedIndices API. | 2026-03 |
| `SpatialResource.getThreadLocalReferenceList()` returning `ObjectList` | Changed in 2026.03.26 to return `List`. | Update type declarations to `List`. | 2026-03 |

## Data / Persistence

| Pattern | Why It Fails | Correct Alternative | Date |
|---------|-------------|-------------------|------|
| String concatenation in SQL | SQL injection risk. Also breaks with special characters. | Prepared statements with `?` parameters. Tables prefixed `rpg_`. | 2026-02 |
| Storing `PlayerRef` long-term | Handle becomes invalid after disconnect/world change. | Store `UUID` via `playerRef.getUuid()`. Resolve PlayerRef from UUID when needed. | 2026-02 |

## Text / Chat

| Pattern | Why It Fails | Correct Alternative | Date |
|---------|-------------|-------------------|------|
| Unicode box drawing (`═` U+2550, `─` U+2500, etc.) in chat/banners/toasts | Not in any Hytale font atlas. Max chat font glyph is U+04FF. Renders as `?`. | ASCII `=`, `-`, `|` for borders. | 2026-03 |
| Unicode arrows (`→` U+2192, `←` U+2190) in chat messages | Not in any Hytale font atlas. Renders as `?`. | ASCII `>`, `->`, `<-` for transitions. | 2026-03 |
| Unicode stars/symbols (`★` U+2605, `●` U+25CF, `►` U+25BA) in chat | Not in any Hytale font atlas. Renders as `?`. | ASCII `*`, `o`, `>` for decoration. | 2026-03 |
| Any character above U+04FF in chat messages | Hytale's NunitoSans chat font only covers U+0020-U+04FF (448 glyphs). | Stick to ASCII (U+0020-U+007E) for all decorative characters. See `docs/reference/hytale-font-charset.md`. | 2026-03 |

## WorldGen / Props

| Pattern | Why It Fails | Correct Alternative | Date |
|---------|-------------|-------------------|------|
| Wall pattern on Prefab props to detect previously-placed props (trees, rocks) | `Prop.Context` has separate `materialReadSpace` and `materialWriteSpace`. The Scanner reads from `materialReadSpace` which contains only terrain blocks, not blocks written by earlier prop layers. Wall pattern can only match terrain materials (soil, rock), never prop-placed materials (wood trunks). Community mods confirm: they only use Wall with terrain materials. | Use FieldFunction zone correlation (same noise seed as the target prop layer) for landscape-scale co-location, or merge into the same prop layer with Floor pattern. | 2026-04 |
| Wall pattern with terrain surface materials (Soil_Grass_Full) | Terrain noise creates 1-block height variations. Adjacent XZ positions at the same Y can be air vs solid soil, causing the Wall pattern to match on visually "flat" ground. | Exclude surface materials from Wall blockset if using Wall for terrain features. | 2026-04 |
| ColumnLinear Scanner with TopDownOrder=true + Wall pattern | Scans from top of range downward. First match is near treetops/high structures, not at ground level. Props appear 30+ blocks above ground. | Use `TopDownOrder: false` (bottom-up) with constrained `MaxY: 3-5` for near-ground Wall placement. | 2026-04 |
| Encampment/Layout prefab paths in WorldGen V2 Props (`Npc/Trork/Tier_*/Encampment/*`) | Layout prefabs contain `PrefabSpawnerBlock` child references. Vanilla Hytale resolves these from its full asset catalog, but our mod can't — `PrefabLoader.traverseAllPrefabBuffersUnder()` finds the layout file but can't resolve child references → empty sub-pool → `PrefabProp` constructor throws `IllegalArgumentException: prefab pool contains empty list` → entire biome fails → void world. Happens regardless of `LoadEntities`, `scanner_max_y`, or density settings. Tested extensively (Apr 19). | **WorldGen**: Only individual prefabs (`Tent`, `Warehouse`, `Misc/Large`, `Watchtower`, `Store`, `Bonfire`, `Fireplace`, `Trap`, `Warning`, `Burrow`, `Resource/*`). **Runtime**: Encampment paths DO work via `PrefabUtil.paste()` (BossStructurePlacer) because that bypasses PrefabLoader validation entirely. Use runtime placement + spawner expansion for the large designed layouts (Castle=461KB, Quarry=137KB, Lumber=38KB). | 2026-04 |
| Gradual wall-to-void density slope (d → -2 over 10+ blocks) | Creates a descending outer wall surface where terrain Y comes back down into `scanner_max_y` range. Props spawn on this outer downslope near the void. | Use 1-block cliff: `{"In": solid_end, "Out": d}, {"In": solid_end+1, "Out": -2}`. Instant drop, no outer surface for props. | 2026-04 |
| Noise-based MaterialProvider paths to connect structures | `SimplexNoise2D` FieldFunction in MaterialProvider creates dirt strips through grass, but noise is random — cannot anchor paths between specific structure positions. | Use runtime Bresenham block placement in BossStructurePlacer (knows all camp piece positions). | 2026-04 |
| `Monuments/*` prefabs in WorldGen V2 prop layers | ALL Monument prefabs (Incidental, Encounter, Treasure_Rooms) contain PrefabSpawnerBlock child references → `prefab pool contains empty list` → void world. Even individual-looking prefabs (Tents, Camps, Wells) have internal furniture/NPC spawners. | Use `RealmStructurePlacer` (runtime PrefabUtil.paste). Proven April 20, 2026. | 2026-04-20 |
| `Rock_Formations/Fossils/*` in WorldGen V2 prop layers | Fossil prefabs (Small through Gigantic) contain internal PrefabSpawnerBlocks → same crash as Monuments. | Use `RealmStructurePlacer` for runtime placement. | 2026-04-20 |
| `Rock_Formations/Hotsprings/*` in WorldGen V2 prop layers | Hotspring prefabs contain water/steam effect spawners → same crash. | Use `RealmStructurePlacer` for runtime placement. | 2026-04-20 |
| `Scale` density node (X/Y/Z parameters) | Server 2026.03.26 reports X, Y, Z as "Unused key(s)". Node either doesn't exist or uses different parameter names (ScaleX/ScaleY/ScaleZ?). Wrapping SimplexNoise2D in Scale does nothing. | Removed. Dune stretching deferred until correct params discovered. | 2026-04-20 |
| Outlander faction prefabs for Desert biome | Outlander structures are ICE/SNOW themed — spawn ice, snow, boats. NOT desert. In-game testing confirmed April 20. | Reserve Outlanders for Tundra/ice biome. Desert uses Monuments/Sandstone + Fossils via RealmStructurePlacer. | 2026-04-20 |
| Prefab directories with only subdirectories (no direct .lpf files) | WorldGen's PrefabProp uses `Files.list()` (non-recursive). If a Path has only subdirs (e.g., `Shale/Bare/` → `Small/Medium/Large/`), pool is empty → crash. | Use specific leaf directory paths (e.g., `Shale/Bare/Small` not `Shale/Bare`). | 2026-04-20 |
| `chunk.setBlock()` / `chunk.setState()` to clear waterlogged fluid | Fluid is stored in a SEPARATE ECS component (`FluidSection`), completely independent from the block layer. Block operations don't touch it. Fluid persists visually even after block replaced with Empty. | Access `FluidSection` via `chunkStore.getChunkSectionReference()` → `getComponent(FluidSection.getComponentType())` → `setFluid(x, y, z, 0, (byte)0)`. | 2026-04-20 |
| `PrefabUtil.paste(force=true)` for runtime structures | Overwrites ALL blocks including air → cuts rectangular holes in surrounding trees/props, destroys vegetation, creates floating structures. Air blocks from prefab erase existing world content. | Use `force=false` — uses `placeBlock()` which respects existing blocks. Prefab solid blocks place normally, air blocks don't overwrite surroundings. | 2026-04-20 |
| `BlockMaterial.Solid` check for waterlog detection | Model/transparent blocks (barnacles, seaweed, coral decorations) with `DrawType: Model, Opacity: Transparent` do NOT report as `BlockMaterial.Solid` despite being visible placed blocks. The check skips them → they stay waterlogged. | Compare against `BlockType.getAssetMap().getAsset("Empty")` instead. Any block that isn't literally the Empty air block and has fluid = waterlogged. | 2026-04-21 |
| Snapshot/reconcile fluid approach for waterlog removal | Comparing fluid before vs after paste fails when structures are placed in areas that already have water (Beach ocean, Swamp marsh, Tundra lake). Can't distinguish "original biome water" from "prefab-embedded water" at the same position — both existed before AND after. | Simple approach: after paste, scan volume for non-Empty blocks with fluid → clear fluid. No snapshots needed. | 2026-04-21 |
| Seaweed prefabs (`Plants/Seaweed/*`) in WorldGen props or near water | Seaweed prefabs contain embedded fluid blocks in their `"fluids": []` array. When placed, they create waterlogged vertical columns visible above the water surface. Same issue in Swamp (removed) and Beach (removed). | Don't use seaweed prefabs near or in water. The embedded fluid persists regardless of FluidSection clearing because it's baked into the prefab data. | 2026-04-21 |

## General / Build

| Pattern | Why It Fails | Correct Alternative | Date |
|---------|-------------|-------------------|------|
| `System.out.println` | Output doesn't go through Hytale's logging system. Not timestamped, not leveled, not filterable. | `HytaleLogger.forEnclosingClass()` + `.atInfo().log()` | 2026-02 |
| Java 25 for Gradle builds | Breaks Kotlin DSL parsing with `IllegalArgumentException: 25`. | Gradle wrapper auto-prefers Java 21. Override: `HYTALE_GRADLE_JAVA21_HOME`. | 2026-02 |
| Shell scripts created with Write tool on WSL2 | CRLF line endings. Shebang becomes `#!/bin/bash\r`. Script fails with `cannot exec`. | Run `sed -i 's/\r$//' script.sh` after creating. | 2026-02 |
| Deploying only the JAR (no asset pack) | Items show as "Invalid Item", weapons don't do damage, stats not applied. | Use `./scripts/deploy.sh` for full deployment (JAR + asset pack + configs). | 2026-02 |
| Modifying config YAML without deployment | Server loads from `mods/.../config/`, not from JAR. Changes aren't visible until deployed. | Run `./scripts/deploy.sh` or manually copy to server config dir. | 2026-02 |

## Neutral NPC Faction Spawning — DEAD (April 20, 2026)

**ALL `Intelligent/Neutral/*` NPCs** fail to spawn via `NPCPlugin.spawnEntity()` — returns null. Confirmed across multiple mob types in server logs. This is a hard engine limitation, not a configuration issue.

**Confirmed dead (Intelligent/Neutral):**
- `Feran_Longtooth`, `Feran_Sharptooth`, `Feran_Windwalker`, `Feran_Burrower`, `Feran_Cub` — Intelligent/Neutral/Feran
- `Bramblekin`, `Bramblekin_Shaman` — Intelligent/Neutral (plant creatures)
- `Kweebec_Razorleaf` — Intelligent/Neutral/Kweebec

**Special case — Hedera:**
- Classified as `Intelligent/Aggressive` in `db/NPCS.tsv` but still returns null
- Server logs show `RealmPassiveNPCRemover` removing prefab-spawned Hedera instances before our spawn attempt — possible conflict

**Confirmed WORKING:**
- ALL `Creature/*` NPCs: Spider, Crocodile, Wolf_Black, Raptor_Cave, Rex_Cave, Snapdragon, Fen_Stalker, Toad_Rhino, Snake_Cobra, Snake_Marsh, Bear_*, Hyena, Scorpion, etc.
- ALL `Intelligent/Aggressive/*` NPCs: Trork_*, Goblin_*, Outlander_*, Skeleton_*, Scarak_*, Shadow_Knight, Werewolf, etc.

**Rule for realm mob pools:** Only use mobs that are PROVEN spawnable in existing working biomes. The category rule (`Creature/*` = works) is UNRELIABLE — many Creature/* mobs are flagged as passive internally and get removed by `RealmPassiveNPCRemover`.

**Structures STILL WORK** — prefabs at `Npc/Feran/Tier1-3/`, `Npc/Kweebec/*`, etc. paste correctly via PrefabUtil.paste(). Only mob spawning is dead.

**Working mob types for jungle:** Snapdragon, Kweebec_Razorleaf, Bramblekin_Shaman, Bramblekin, Spider, Snake_Cobra, Raptor_Cave — all proven spawnable.

## Passive/Ambient Creature Spawning — DEAD (April 20, 2026)

Many `Creature/*` NPCs pass `getIndex()` and even partially spawn via `spawnEntity()`, but are IMMEDIATELY removed by `RealmPassiveNPCRemover` because they have passive AI behavior (no attack sequences). Server log shows: `[RealmPassiveNPCRemover] Removed passive NPC '{name}' from realm world`.

**Confirmed dead (passive — no hostile AI):**
- `Molerat` — Creature/Vermin (burrowing passive rodent)
- `Trillodon` — Creature/Mythic (prehistoric passive creature)
- `Hatworm` — Creature/Mythic (passive worm)
- `Larva_Silk` — Creature/Vermin (passive silk larva)
- `Bat` — Avian/Aerial (passive flying creature)
- `Hound_Bleached` — Undead (passive undead dog — no attack AI)

**Confirmed WORKING (hostile AI verified in-game April 20, 2026):**
- `Spider_Cave` — Creature/Vermin (attacks, webs)
- `Rat` — Creature/Vermin (attacks in swarms)
- `Golem_Crystal_Earth` — Elemental/Golem (full combat AI)
- `Skeleton_Incandescent_Fighter` — Undead/Skeleton (melee attacks) — visually RED/VOLCANIC
- `Skeleton_Incandescent_Mage` — Undead/Skeleton (ranged magic) — visually RED/VOLCANIC
- `Skeleton_Incandescent_Footman` — Undead/Skeleton (melee) — visually RED/VOLCANIC
- `Zombie_Aberrant` — Undead/Zombie (mutant attacks) — visually MUTATED/DEFORMED
- `Zombie_Aberrant_Big` — Undead/Zombie (large mutant) — visually MUTATED/DEFORMED

**The pattern:** Mobs with defined attack sequences (Combat AI) work. Ambient/passive wildlife (burrow, fly, wander) gets removed. You CANNOT tell from the NPC category alone — must test or check if the mob has `Component_Instruction_Attack_Sequence_*` entries in NPCS.tsv.

## Floating Prefab Structures — WRONG PLACEMENT (April 20, 2026)

`Rock_Formations/Geode_Floating/*` prefabs are designed to float in mid-air (decorative floating crystal islands). When placed via `RealmStructurePlacer` at ground level, they either:
- Float above the ground looking broken
- Have empty space at the bottom (the crystal cluster is elevated within the prefab)

**Rule:** Never use prefabs with "Floating" in the path for ground-based `RealmStructurePlacer` placement. These are designed for void/sky biomes or decorative WorldGen placement at elevated Y positions.

## Skeleton_Incandescent — VOLCANIC THEME (April 20, 2026)

`Skeleton_Incandescent_Fighter/Footman/Mage` spawn correctly and have full combat AI, BUT they are visually dark red with a glowing red aura — clearly volcanic/fire themed. They belong in the **Volcano** biome, NOT in crystal caves or generic underground.

**Assigned to:** Volcano biome (added April 20, 2026)

## PondFiller Prop Type — DEAD (April 20, 2026)

`"Type": "PondFiller"` in biome JSON prop layers causes the ENTIRE biome to fail asset registration. Server 2026.03.26 silently rejects the biome — no "Unused keys" warning, just never registers it. When a realm tries to use that biome tier: "Couldn't find Biome asset with id: Realm_X_RN" → void world.

PondFiller is documented in official Hytale WorldGen V2 docs but NOT functional in current server build. All tiers containing PondFiller fail; tiers without it load fine.

## ClusterProp Prop Type — DEAD (April 21, 2026)

`"Type": "Cluster"` in biome JSON prop layers causes void world — same failure pattern as PondFiller. ClusterProp is in the `deprecated` package of decompiled code. It IS used successfully in a community mod, but that mod may target a different server version. Server 2026.03.26 rejects it silently. Use dense Prefab scatter with small mesh_scale as alternative for organic groupings.

## Ghoul NPC — DEAD (April 21, 2026)

`Ghoul` (Undead/Ghoul.json) returns null from `NPCPlugin.spawnEntity()`. Exists in the NPC database under Undead category but the engine refuses to instantiate it. Same pattern as neutral NPCs (Feran, Bramblekin, etc.). 100 consecutive spawn failures in Swamp testing — zero successes. Removed from Swamp and Corrupted biome pools.

## Normalizer min/max Constraint — CRASH (April 21, 2026)

`NormalizerDensity` validates `min < max` at construction time (NormalizerDensity.java:19). You CANNOT invert a density output by swapping ToMin/ToMax (e.g., `ToMin: 2.25, ToMax: -2.25`). This throws `IllegalArgumentException: min larger than max` which crashes the entire biome builder → void world.

**Fix**: Use `"Type": "Inverter"` node (multiplies by -1) AFTER the Normalizer instead. Example: CellNoise2D Distance2Div outputs [-1, 0]. Normalizer scales to [-amp, +amp] (valid min < max). Inverter flips to [+amp, -amp]. Cell centers become islands, boundaries become channels.

## Sending UpdateItems/UpdateTranslations During World Transitions — CLIENT CRASH (April 25, 2026)

Sending `UpdateItems` or `UpdateTranslations` packets between `DrainPlayerFromWorldEvent` and `ClientReady` (`PlayerReadyEvent`) causes the Hytale client to crash with `System.NullReferenceException`. The client receives asset update packets while still processing `JoinWorldPacket` — the asset update pipeline races with world teardown.

**Crash signature**: `NullReferenceException` at `+0x494b26 → +0x41301a → +0x411d28` (obfuscated C# stack). Always during `Items: Starting AddOrUpdate` overlapping with world join.

**Root cause**: `RPGItemPreSyncSystem` fires on `world.addPlayer()` which runs BEFORE `JoinWorldPacket` is sent. During world transitions, the client is still in the OLD world when UpdateItems arrives. Then JoinWorldPacket tears down the old world mid-processing.

**Fix**: Use `ItemSyncCoordinator.isPlayerSuppressed()` to gate ALL packet sending. During world transitions, only do server-side asset map registration (no packets). The coordinator's post-PlayerReady flush sends packets after the client confirms it's in the new world. First connects (no old world) are safe — suppression only applies to transitions.

**Impact**: This was the #1 cause of client crashes during realm gameplay. Reproduced on every 2nd+ realm entry. Fixed by deferring packets to post-ClientReady.

## Loot Filter / Item Pickup Interception

### InteractivelyPickupItemEvent for Ground Item Filtering (2026-04-27)

**What was tried**: Created `FilteredPickupInteraction` (custom Hytale Interaction) + `LootFilterPickupSystem` (`EntityEventSystem<InteractivelyPickupItemEvent>`) to intercept item pickups and cancel them when the loot filter blocks an item.

**Why it fails — THREE independent reasons**:

1. **Items never get the custom interaction assigned**: `FilteredPickupInteraction` was registered as a codec but never assigned to any item definition or dropped item. RPG gear uses vanilla item definitions with metadata — no custom Pickup interaction.

2. **InteractivelyPickupItemEvent never fires for ground pickup**: This event ONLY fires in `BlockHarvestUtils` (mining) and `FarmingUtil` (crop harvesting). `PlayerItemEntityPickupSystem` (the ground item pickup system) NEVER fires this event — it either executes an interaction chain (Path A) or directly adds to inventory (Path B).

3. **Entity removal is unconditional**: Even if the event fired, `PlayerItemEntityPickupSystem` removes the item entity on line 134 regardless of interaction success. Re-dropped items (new entities) would lack the custom interaction and be picked up via Path B on the next tick.

**Working approach**: `InventoryChangeEvent`-based post-pickup filtering via `LootFilterInventoryHandler`. Items enter inventory, handler evaluates filter, blocked items are removed from inventory and dropped at player's feet with extended pickup delay + rejection stamp metadata.

**Key pitfalls in the working approach**:
- `store.addEntity()` inside ECS system throws `IllegalStateException: Store is currently processing` — must defer to `world.execute()`
- `addItemStack()` returns `ItemStackTransaction` (NOT `SlotTransaction`) — must handle both transaction types
- Container operations (`removeItemStackFromSlot`) are safe inside systems (not store ops)

