# Gear System Research Todo

Tracking research progress for the Trail of Orbis Gear System implementation.

> **Note**: Research is complete! For implementation, see:
> - [Implementation Roadmap](../plan/ROADMAP.md)
> - [Progress Tracking](../plan/PROGRESS.md)
> - [Design Documents](../design/)

---

## Research Priority Order

| Priority | System | Status | Blocks | Risk if Missing |
|----------|--------|--------|--------|-----------------|
| 1 | Item Data Storage | ✅ DONE | Everything | System impossible |
| 2 | Equipment Events | ✅ DONE | Level gating, stat application | Core feature broken |
| 3 | Loot/Drop System | ✅ DONE | Gear acquisition | No way to get gear |
| 4 | Combat System | ✅ DONE | Modifiers working | Stats don't matter |
| 5 | Crafting System | ✅ DONE | Crafting restrictions, reroll | Secondary acquisition broken |
| 6 | Text Formatting | ✅ DONE | Visual feedback | Playable but ugly |
| 7 | Tooltip System | ✅ DONE | Stat display | Players can't see stats easily |
| 8 | Advanced Visuals | ✅ DONE | Polish | Functional but less satisfying |

---

## Detailed Research Goals

### 1. Item Data Storage ✅ DONE
**Research Document**: [ItemDataStorageResearch.md](./ItemDataStorageResearch.md)

**Questions Answered**:
- ✅ How are items represented? → `ItemStack` (runtime) + `Item` (definition)
- ✅ Can items store custom data? → Yes, `BsonDocument metadata` field
- ✅ Is data persisted? → Yes, via codec serialization
- ✅ How to modify items? → Immutable pattern with `withMetadata()` methods

**Key Finding**: BsonDocument metadata system allows arbitrary key-value storage. Fully feasible.

---

### 2. Equipment Events ✅ DONE
**Research Document**: [EquipmentSystemResearch.md](./EquipmentSystemResearch.md)

**Questions Answered**:
- ✅ How does equipment work? → SmartMoveItemStack packets → Inventory.smartMoveItem() → SlotFilter validation → container move
- ✅ Are there events? → `InventoryChangeEvent` (ECS event, all changes), `SwitchActiveSlotEvent` (slot switching). **Updated 2026-03-26**: `LivingEntityInventoryChangeEvent` renamed to `InventoryChangeEvent` and changed from IEvent to ECS event.
- ✅ Can we block equipping? → YES! `SwitchActiveSlotEvent` is cancellable for weapons/utilities, `SlotFilter` for armor
- ✅ How are slots managed? → 4 armor slots (Head/Chest/Hands/Legs), hotbar for weapons, utility container
- ✅ When are stats applied? → `StatModifiersManager.recalculateEntityStatModifiers()` after equipment invalidation

**Key Finding**: `SwitchActiveSlotEvent.setCancelled(true)` blocks weapon/utility equipping. Custom `SlotFilter` can block armor.

---

### 3. Loot/Drop System ✅ DONE
**Research Document**: [LootDropSystemResearch.md](./LootDropSystemResearch.md)

**Questions Answered**:
- ✅ How do mobs drop loot? → `NPCDamageSystems.DropDeathItems` → `ItemModule.getRandomItemDrops()` → `ItemComponent.generateItemDrops()`
- ✅ Death/kill events? → `DeathComponent` triggers `OnDeathSystem` chain, `DropItemEvent.Drop` is cancellable
- ✅ Loot table structure? → `ItemDropList` → `ItemDropContainer` hierarchy (Single/Choice/Multiple/Droplist/Empty)
- ✅ Can we intercept drops? → YES! Hook death systems, intercept `DropItemEvent.Drop`, modify `itemsToDrop` list
- ✅ How are items spawned? → `ItemComponent.generateItemDrop()` creates entity with physics, metadata preserved

**Key Findings**:
- `ItemDrop.metadata` is BsonDocument - can attach full RPG stats to drops
- `DamageData` tracks all attackers with damage contribution - supports party loot
- `DropItemEvent.Drop` is cancellable and allows modifying itemStack
- Weight system in containers perfect for rarity distribution

---

### 4. Combat System ✅ DONE
**Research Document**: [CombatSystemResearch.md](./CombatSystemResearch.md)

**Questions Answered**:
- ✅ How is damage calculated? → Multi-stage pipeline: Base → Type Scaling → Random Variance → Broken Penalty → Armor Enhancement → Sequential Modifier → Armor Reduction → Wielding Reduction → Apply to Health
- ✅ Where can we inject modifier bonuses? → `StatModifiersManager.recalculateEntityStatModifiers()` for stats, `DamageEventSystem` in Filter group for damage, `Damage.setAmount()` in event listeners
- ✅ How does ItemWeapon.statModifiers work? → `Int2ObjectMap<StaticModifier[]>` applied via StatModifiersManager when weapon equipped, supports ADDITIVE and MULTIPLICATIVE calculation types
- ✅ Are there damage events we can hook into? → YES! `Damage` extends `CancellableEcsEvent` - fully cancellable with modifiable amount, Filter group systems can intercept before application
- ✅ How is armor/defense calculated? → Two-component model: `finalDamage = (damage - flatResistance) * (1 - multiplierResistance)`, accumulated from all armor pieces per DamageCause

**Key Findings**:
- `Damage` event is cancellable via `setCancelled(true)` and amount modifiable via `setAmount()`
- `DamageCause` supports inheritance chain (e.g., Slash → Physical → All)
- `ItemArmor.damageEnhancementValues` allows armor to INCREASE outgoing damage
- `Damage.getMetaStore()` allows attaching custom RPG data to damage events
- Filter group in `DamageSystems` is the ideal injection point for RPG stat modifiers
- `StaticModifier` supports both ADDITIVE (flat) and MULTIPLICATIVE (percentage) bonuses
- Broken items have reduced effectiveness via `BrokenPenalties` config

---

### 5. Crafting System ✅ DONE
**Research Document**: [CraftingSystemResearch.md](./CraftingSystemResearch.md)

**Questions Answered**:
- ✅ How does crafting work? → `CraftingPlugin` → `CraftingManager` component → `CraftingWindow` UI → Queue/instant crafting via `craftItem()` or `queueCraft()`
- ✅ Are there crafting events? → YES! `CraftRecipeEvent.Pre` (cancellable, before inputs removed), `CraftRecipeEvent.Post` (cancellable, after inputs removed, before output)
- ✅ Can we intercept crafted item output? → YES! Cancel `Post` event and give custom items, or listen to `DropItemEvent.Drop` for overflow
- ✅ How are recipes defined? → JSON files with `MaterialQuantity[]` inputs/outputs, `BenchRequirement[]`, `timeSeconds`, metadata support via BsonDocument
- ✅ Can we add custom crafting stations? → YES! Extend `Bench`/`CraftingBench`/`ProcessingBench`, register BlockType, create recipes with matching `BenchRequirement`

**Key Findings**:
- `CraftRecipeEvent.Pre` cancellation prevents entire craft (materials kept)
- `CraftRecipeEvent.Post` cancellation prevents output but materials already consumed
- `MaterialQuantity.metadata` (BsonDocument) passes through to crafted `ItemStack.metadata`
- Bench tier system provides `craftingTimeReductionModifier` per tier level
- Benches auto-search nearby chests for materials (configurable radius in `CraftingConfig`)
- Four bench types: Crafting, Processing, DiagramCrafting, StructuralCrafting
- `BenchRecipeRegistry` manages recipe→bench mappings and category filtering

---

### 6. Text Formatting ✅ DONE
**Research Document**: [TextFormattingResearch.md](./TextFormattingResearch.md)

**Questions Answered**:
- ✅ Does Hytale support colored text? → YES! Full RGB/RGBA hex color support via FormattedMessage.color field
- ✅ Are there color codes or rich text formatting? → YES! Supports bold, italic, monospace, underline, nested colors, hyperlinks
- ✅ Can item names have custom colors? → YES! Via ItemQuality.textColor or custom Message.color() chains
- ✅ How is text rendered in UI? → FormattedMessage sent via ServerMessage packet (ID: 210), client renders styled text

**Key Findings**:
- `FormattedMessage` - Core protocol class with color, bold, italic, monospace, underlined, link fields
- `Message` - Fluent API wrapper: `.color("#RRGGBB").bold(true).italic(true)`
- Color formats: `#RGB`, `#RRGGBB`, `#RGBA`, `#RRGGBBAA`, `rgb(r,g,b)`, `rgba(r,g,b,a)`
- `ItemQuality.textColor` - Automatic coloring by quality tier
- Default colors: Common=#c9d2dd, Uncommon=#3e9049, Rare=#2770b7, Epic=#8b339e, Legendary=#bb8a2c
- `PlayerRef.sendMessage(Message)` - Send formatted messages to players
- Parameter substitution: `{key}`, `{count, plural, one {item} other {items}}`

---

### 7. Tooltip System ✅ DONE
**Research Document**: [TooltipSystemResearch.md](./TooltipSystemResearch.md)

**Questions Answered**:
- ✅ How do item tooltips work? → Quality-based system: `ItemQuality` defines textures/colors, `ItemTranslationProperties` provides text, `ItemWeapon/Armor.statModifiers` show stats
- ✅ Can we customize tooltip content? → YES! `ItemGridSlot.name/description` override, custom translation keys, metadata for RPG data
- ✅ Can we change tooltip border/frame color? → YES! Custom `ItemQuality` with custom `itemTooltipTexture` and `textColor`
- ✅ Is there a tooltip API or do we need to override? → Limited API via `ItemGridSlot`, styling via `ItemQuality`, custom content via description override
- ✅ How is tooltip layout structured? → Fixed client-side layout, server sends data via `ItemBase` with quality index, translation props, and stat modifiers

**Key Findings**:
- `ItemQuality` - Controls tooltip texture (`itemTooltipTexture`), text color (`textColor`), slot backgrounds
- `ItemGridSlot` - UI component with `name`, `description` override fields
- `ItemGridInfoDisplayMode` - Enum: Tooltip, Adjacent, None
- `Modifier` - Stat modifier with `target`, `calculationType` (Additive/Multiplicative), `amount`
- `ItemWeapon.statModifiers` / `ItemArmor.statModifiers` - Native stat display
- `ItemWithAllMetadata.metadata` - JSON string for custom RPG data
- Tooltip textures at `Assets/Common/UI/ItemQualities/Tooltips/ItemTooltip{Quality}@2x.png`
- Strategy: Store RPG stats in metadata, format as description string for tooltip display

---

### 8. Advanced Visuals ✅ DONE
**Research Document**: [AdvancedVisualsResearch.md](./AdvancedVisualsResearch.md)

**Questions Answered**:
- ✅ Can we tint/recolor item models? → YES! `Model.gradientSet/gradientId`, `ApplicationEffects.entityBottomTint/entityTopTint`
- ✅ How does the particle system work? → `ParticleUtil.spawnParticleEffect()` utility, `SpawnParticleSystem` packet (ID 152) with position, rotation, scale, color
- ✅ Can we add effects to dropped items? → YES! `ItemEntityConfig.particleSystemId` + `particleColor` per ItemQuality
- ✅ Can we modify inventory slot rendering? → YES! `ItemQuality.slotTexture`, `ItemGridSlot.background/overlay` for custom styling
- ✅ Can we attach visual effects to worn equipment? → YES! `ModelParticle` with `targetEntityPart` (Self/PrimaryItem/SecondaryItem), `ItemAppearanceCondition` for conditional effects

**Key Findings**:
- `ModelParticle` - Particle attachment with `systemId`, `scale`, `color`, `targetEntityPart`, `targetNodeName`
- `ModelTrail` - Trail effects attached to models
- `SpawnParticleSystem` (Packet ID 152) - Spawn particles with position, rotation, scale, color
- `ParticleUtil` - Server utility for spawning particles programmatically
- `ApplicationEffects` - Full visual bundle: `entityBottomTint`, `entityTopTint`, `particles[]`, `modelVFXId`, `screenEffect`
- `ItemEntityConfig` - Dropped item particles: `particleSystemId`, `particleColor`, `showItemParticles`
- `ItemAppearanceCondition` - Conditional visuals based on durability/charge with model/texture/VFX overrides
- Quality drop effects: `Drop_Rare` (blue), `Drop_Epic` (purple), `Drop_Legendary` (gold) with particles + tinting
- `ColorLight` - Model light emission with radius and RGB

---

## Progress Summary

| Status | Count |
|--------|-------|
| ✅ Done | 8/8 |
| ⬚ Todo | 0/8 |

**ALL RESEARCH COMPLETE!**

---

## Research Documents Index

| Research Area | Document | Status |
|---------------|----------|--------|
| Item Data Storage | [ItemDataStorageResearch.md](./ItemDataStorageResearch.md) | ✅ Complete |
| Equipment Events | [EquipmentSystemResearch.md](./EquipmentSystemResearch.md) | ✅ Complete |
| Loot/Drop System | [LootDropSystemResearch.md](./LootDropSystemResearch.md) | ✅ Complete |
| Combat System | [CombatSystemResearch.md](./CombatSystemResearch.md) | ✅ Complete |
| Crafting System | [CraftingSystemResearch.md](./CraftingSystemResearch.md) | ✅ Complete |
| Text Formatting | [TextFormattingResearch.md](./TextFormattingResearch.md) | ✅ Complete |
| Tooltip System | [TooltipSystemResearch.md](./TooltipSystemResearch.md) | ✅ Complete |
| Advanced Visuals | [AdvancedVisualsResearch.md](./AdvancedVisualsResearch.md) | ✅ Complete |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial creation, Item Data Storage marked complete |
| 1.1 | 2026-01-23 | Equipment Events research completed |
| 1.2 | 2026-01-23 | Loot/Drop System research completed |
| 1.3 | 2026-01-23 | Combat System research completed |
| 1.4 | 2026-01-23 | Crafting System research completed |
| 1.5 | 2026-01-23 | Text Formatting research completed |
| 1.6 | 2026-01-23 | Tooltip System research completed |
| 1.7 | 2026-01-23 | Advanced Visuals research completed - ALL RESEARCH DONE! |
