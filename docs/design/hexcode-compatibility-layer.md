# Hexcode + TrailOfOrbis Compatibility Layer — Design Document

**Status**: Design decisions resolved, ready for implementation
**Date**: 2026-04-26
**Research basis**: 9 parallel deep-dive agents across 407 Hexcode files + 730 ToO files

---

## Executive Summary

Hexcode (spell-crafting) and TrailOfOrbis (RPG framework) are designed to run on the same server. After tracing every integration surface through actual code, this document identifies **3 crash-level bugs**, **4 major integration gaps**, and **6 design decisions** that need to be resolved for seamless coexistence.

The goal: when both mods load together, Hexcode IS the magic system of Trail of Orbis's RPG world. Spells use ToO's mana, deal damage through ToO's pipeline, trigger ToO's ailments, respect ToO's resistances, and scale with ToO's attributes.

---

## Part 1: Critical Bugs (Will Crash or Break)

### BUG-1: Crafting State Persists Across World Transitions

**Severity**: CRASH
**Trigger**: Player in CRAFTING state when ToO teleports them to/from a realm
**Root Cause**: Hexcode does NOT listen to `DrainPlayerFromWorldEvent`. `HexcasterComponent` state persists. `CraftingSystem.tick()` tries to resolve `PedestalBlockComponent` at old-world coordinates in the new world → null reference crash.

**Evidence**:
- Hexcode registers: `PlayerConnectEvent`, `PlayerDisconnectEvent` only (Hexcode.java:350-351)
- No `DrainPlayerFromWorldEvent` handler exists anywhere in hexcode source
- `CraftingSystem.tick()` calls `PedestalBlockUtil.resolvePedestal()` every tick
- Pedestal refs point to old realm world after teleport

**Fix (ToO side — we can do this unilaterally)**:
```java
// In DrainPlayerFromWorldEvent handler, BEFORE teleport:
HexcasterComponent hexcaster = store.getComponent(ref, HexcasterComponent.getComponentType());
if (hexcaster != null && hexcaster.getState() != HexState.IDLE) {
    hexcaster.requestStateChange(HexState.IDLE);
}
```

**Fix (Hexcode side — Riprod should add)**:
- Register `DrainPlayerFromWorldEvent` listener
- Force state to IDLE on world drain
- Clear session refs

### BUG-2: Casting State + Teleport = Orphaned Constructs + Wrong-World Spawns

**Severity**: HIGH (functional break, not crash)
**Trigger**: Player casting a sustained spell (Domain, Freeze, Drain) when teleported
**Root Cause**: Active `HexEffectsComponent` constructs continue ticking. `HexConstructSystem` may spawn new entities in the destination world. Volatile constructs in the old world tick with depleted context.

**Evidence**:
- `HexConstructSystem` (EntityTickingSystem) runs on ALL entities with `HexEffectsComponent`
- Construct handlers reference `HexContext.getChunkAccessor()` which may point to old world
- `VolatilityTracker` continues draining budget across worlds

**Fix**: Same as BUG-1 — force IDLE before teleport. `CastingSystem.lastTick()` runs cleanup on all active hexes + constructs.

### BUG-3: HexStaff Auto-Discovery as RPG Loot

**Severity**: MEDIUM (unintended behavior)
**Trigger**: HexStaff registers as `ItemWeapon` in Hytale → ToO's `DynamicLootRegistry` discovers it → drops as RPG gear with random modifiers
**Root Cause**: ToO scans `Item.getAssetMap()` for anything with `Item.getWeapon() != null` and adds to loot pool.

**Evidence**:
- `DynamicLootRegistry.discoverItems()` iterates all items
- `HexStaffAsset` likely registers as weapon (staves are weapons in Hytale)
- No exclusion list exists in ToO's loot system

**Fix (ToO side)**: Add item exclusion list to `DynamicLootRegistry`:
```java
private static final Set<String> EXCLUDED_PREFIXES = Set.of("Hexcode_");
// In discoverItems(): skip items with excluded prefixes
```

---

## Part 2: The Mana Problem (Two Ships Passing)

### Current State (Broken)

```
TrailOfOrbis:
  WATER attribute → ComputedStats.maxMana (in-memory only)
  NEVER applied to Hytale's EntityStatMap

Hexcode:
  ArmorManaPatcher → injects StaticModifier into ItemArmor.statModifiers
  Reads mana from EntityStatMap via DefaultEntityStatTypes.getMana()

Result: ToO calculates mana that Hexcode can't see.
        Hexcode patches mana onto armor that ToO doesn't read.
        Two independent mana systems, neither aware of the other.
```

### Unified Mana Design

**Principle**: ONE mana pool, derived from BOTH sources, visible to BOTH systems.

**Architecture**:
1. ToO becomes the **source of truth** for max mana (WATER attribute + gear bonuses + skill tree)
2. ToO applies its computed `maxMana` to Hytale's `EntityStatMap` as a `StaticModifier`
3. Hexcode's `ArmorManaPatcher` adds armor-based mana ON TOP via its own `StaticModifier`
4. Both read from `EntityStatMap` — unified pool

**Implementation (ToO side)**:
```java
// In AttributeManager.recalculateStats(), after computing all stats:
EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
int manaIndex = DefaultEntityStatTypes.getMana();

// Remove old RPG modifier
statMap.removeModifier(manaIndex, "rpg_max_mana");

// Apply new computed value
statMap.addModifier(manaIndex, new StaticModifier(
    "rpg_max_mana",
    ModifierTarget.MAX,
    CalculationType.ADDITIVE,
    computedStats.getMaxMana()
));
```

**Hexcode side**: No changes needed. `ArmorManaPatcher` adds its own modifier keyed differently. Both stack additively on the same `EntityStatMap` entry.

**Result**:
```
Player equips Iron armor (Hexcode: +50 mana) and has 20 WATER (ToO: +30 mana)
EntityStatMap.getMana().getMax() = baseMana + 50 (armor) + 30 (attribute) = 80
Both mods read 80. Both mods can consume from the same pool.
```

### Volatility and Magic Power

**Decision needed**: Should ToO attributes affect Hexcode's Volatility and Magic Power?

**Option A — Keep Separate**: Hexcode's Volatility/Magic Power come only from Hexcode armor patches. ToO doesn't touch them.
- Pro: Simple, no coordination needed
- Con: Magic stats feel disconnected from RPG progression

**Option B — ToO Feeds Into Hexcode Stats**: ToO computes magic power from attributes (e.g., WATER/FIRE) and applies to EntityStatMap where Hexcode reads it.
- Pro: Spells scale with RPG progression
- Con: Requires new attribute grants in ToO config

**Recommendation**: Option B. Add to `ComputedStats`:
```java
float magicPower;   // Derived from attributes (WATER × 0.5 + FIRE × 0.3?)
float volatility;   // Derived from attributes (base 30 + WATER × 1.5?)
int magicCharges;   // Derived from level (base 1, +1 every 20 levels?)
```

Then apply to EntityStatMap same as mana. Hexcode reads them naturally.

**Config-driven**: All grants should be in `config.yml` so balance is tunable without code changes.

---

## Part 3: Damage Pipeline Integration

### Current State

Hexcode uses `DamageSystems.executeDamage()` with `Damage.EnvironmentSource("hex_xxx")`. This fires through Hytale's standard damage events. ToO's `RPGDamageSystem` intercepts ALL damage events.

**What already works**: Hexcode spell damage DOES go through our pipeline. It hits `RPGDamageSystem.handle()`.

**What breaks**: `EnvironmentSource` triggers our `handleEnvironmentalDamage()` path, which:
- Scales damage as % of max HP (wrong for spells)
- Doesn't apply attacker stats (no caster identity)
- Doesn't apply elemental resistances properly
- Doesn't record death recap with caster info
- Doesn't apply ailments
- Doesn't trigger lifesteal/mana leech for the caster

### The Fix: Spell Damage Branch

**Approach**: Detect `EnvironmentSource` with `"hex_"` prefix and route through a spell-specific damage path.

```java
// In RPGDamageSystem.handle():
if (damage.getSource() instanceof Damage.EnvironmentSource envSource) {
    String sourceId = envSource.getSourceId();
    if (sourceId != null && sourceId.startsWith("hex_")) {
        handleSpellDamage(damage, envSource, sourceId, ...);
        return;
    }
    handleEnvironmentalDamage(damage, ...);
    return;
}
```

**Spell damage path**:
1. Extract caster UUID from `HexContext` (stored in damage meta or looked up)
2. Resolve caster's `ComputedStats` (for spell damage %, elemental bonuses)
3. Determine damage type from glyph category:
   - `hex_bolt` → MAGIC
   - `hex_combust` → FIRE
   - `hex_glaciate` → WATER
   - `hex_ensnare` → EARTH
   - etc.
4. Apply spell damage scaling: `baseDamage × (1 + spellDamage% / 100)`
5. Apply elemental resistance on defender
6. Apply crit chance (from caster stats)
7. Record death recap
8. Emit combat indicators
9. Trigger ailments (fire spell → burn chance, ice spell → freeze chance)

**Caster Identity Problem**: Currently `Damage.EnvironmentSource` has no entity ref. Two options:

**Option A — MetaKey**: Hexcode stores caster UUID in damage meta:
```java
// Hexcode side (Riprod adds):
Damage damage = new Damage(new Damage.EnvironmentSource("hex_bolt"), cause, amount);
damage.putMetaObject(HexcodeMeta.CASTER_UUID, casterUuid);

// ToO side reads:
UUID casterUuid = damage.getMetaObject(HexcodeMeta.CASTER_UUID);
```

**Option B — EntitySource**: Hexcode switches to `Damage.EntitySource(casterRef)` with a custom cause:
```java
// Hexcode side:
Damage damage = new Damage(new Damage.EntitySource(casterRef), "hex_bolt", amount);
```

**Recommendation**: Option A (MetaKey). Less invasive for Hexcode — Riprod just adds one line per damage call. We define the MetaKey in a shared constants class.

### Damage Type Mapping

| Hexcode Source ID | Glyph | ToO DamageType | ToO Element | Ailment Trigger |
|---|---|---|---|---|
| `hex_bolt` | Bolt | MAGIC | LIGHTNING | Shock |
| `hex_combust` | Combust | FIRE | FIRE | Burn |
| `hex_gust` | Gust | WIND | WIND | — |
| `hex_glaciate` | Glaciate | WATER | WATER | Freeze |
| `hex_ensnare` | Ensnare | EARTH | EARTH | — |
| `hex_phase` | Phase | MAGIC | VOID | — |
| (generic/unmapped) | Any | MAGIC | — | — |

**Config-driven**: Map stored in `config.yml` so new glyphs can be mapped without code changes:
```yaml
hexcode:
  damage-type-map:
    hex_bolt: { type: MAGIC, element: LIGHTNING }
    hex_combust: { type: FIRE, element: FIRE }
    hex_glaciate: { type: WATER, element: WATER }
    # ...
```

---

## Part 4: Ailment Synchronization

### Hexcode Effects → ToO Ailments

When Hexcode applies its effects, ToO should apply corresponding ailments:

| Hexcode Effect | Hexcode Mechanism | ToO Ailment | Integration |
|---|---|---|---|
| Freeze | Ice blocks + `Hexcode_Freeze` EntityEffect | FREEZE (speed reduction) | **Layer on top**: Hexcode freezes visually, ToO applies speed debuff |
| Ignite | `Burn` EntityEffect (Hytale vanilla) | BURN (DoT) | **Replace**: Use ToO's burn system (configurable DPS, resistance-aware) |
| Burning | NOT IMPLEMENTED in Hexcode | BURN | N/A |
| Halt | Physics stop (drag=999) | FREEZE (100% slow?) | **Complement**: Halt is a hard stun, freeze is a slow. Different. |
| Erode | DamageEventSystem filter (+damage%) | SHOCK (+damage taken) | **Stack carefully**: Both amplify damage. Need cap. |
| Drain | Stat drain (HP→Mana) | — | No ailment equivalent. Keep as-is. |

### Implementation

**Hook into Hexcode events**: Listen to `GlyphDrawnEvent` or intercept construct creation.

**Simpler approach**: In `RPGDamageSystem.handleSpellDamage()`, after applying spell damage, call `CombatAilmentApplicator.tryApplyAilments()` with the mapped element:

```java
// After spell damage is calculated:
ElementType element = spellDamageMap.getElement(sourceId); // hex_combust → FIRE
if (element != null) {
    ailmentApplicator.tryApplyAilments(
        element, finalDamage, casterStats, defenderMaxHealth, casterUuid
    );
}
```

This way:
- Fire spells (Combust) have a chance to apply ToO Burn (DoT)
- Ice spells (Glaciate, Freeze glyph) have a chance to apply ToO Freeze (slow)
- Lightning spells (Bolt) have a chance to apply ToO Shock (damage amp)

Hexcode's own effects (ice blocks, EntityEffects) still apply as visual/mechanical effects. ToO's ailments layer on top as RPG stat effects.

### Erode + Shock Stacking

Both amplify incoming damage:
- Erode: `damage × (1 + vulnerability)` in Hytale's FilterDamageGroup
- Shock: `damage × (1 + shockMagnitude/100)` in ToO's RPGDamageCalculator

They operate at different pipeline stages, so they multiply:
```
finalDamage = baseDamage × (1 + erodeBonus) × (1 + shockBonus)
```

**Design decision**: Is this intended?
- **Yes, if**: Magic builds should amplify damage multiplicatively (high skill ceiling)
- **No, if**: It makes combo builds too strong

**Recommendation**: Allow it but add a config cap:
```yaml
hexcode:
  max-damage-amplification: 2.0  # Cap total multiplier at 200%
```

---

## Part 5: World Transition Safety

### Required Changes (ToO Side)

Add Hexcode state cleanup to our `DrainPlayerFromWorldEvent` handler:

```java
// In TrailOfOrbis.java, DrainPlayerFromWorldEvent handler:
// EXISTING: discard realm HUDs, xp bar, skill sanctum
// ADD: Reset Hexcode state
try {
    HexcasterComponent hexcaster = store.getComponent(ref, HexcasterComponent.getComponentType());
    if (hexcaster != null && hexcaster.getState() != HexState.IDLE) {
        hexcaster.requestStateChange(HexState.IDLE);
        LOGGER.atFine().log("Reset Hexcode state to IDLE for player %s on world drain", playerId);
    }
} catch (Exception e) {
    // Hexcode not loaded — ignore gracefully
    LOGGER.atFine().log("Hexcode not available, skipping state reset");
}
```

**Soft dependency**: Use reflection or `try/catch` to avoid hard compile-time dependency on Hexcode classes. If Hexcode isn't loaded, skip gracefully.

### Required Changes (Hexcode Side — Riprod)

1. Add `DrainPlayerFromWorldEvent` listener:
```java
getEventRegistry().register(DrainPlayerFromWorldEvent.class, event -> {
    // Force IDLE, clear session refs, abort active constructs
});
```

2. Add `AddPlayerToWorldEvent` listener:
```java
// Validate all session refs are in current world
// If pedestal refs point to different world, clear session
```

---

## Part 6: Item System — Hexcode Items as RPG Gear Base

### The Core Insight (Verified from Code)

Two discoveries make zero-dependency integration possible:

**1. CasterInventory reads metadata FIRST** (CasterInventory.java:112-116, 49-53):
```java
HexStaffComponent existing = mainHandItem.getFromMetadataOrNull("HexStaff", CODEC);
if (existing != null) return existing;  // ← Never hits asset lookup!
```
If we inject `HexStaff`/`HexBook` metadata onto RPG items, Hexcode reads it directly.

**2. Hexcode staffs already carry stat modifiers in their JSON** (Hexstaff_Basic_Iron.json):
```json
"Weapon": {
  "StatModifiers": {
    "Magic_Power": [{ "Target": "Max", "CalculationType": "Additive", "Amount": 0.45 }],
    "Volatility": [{ "Target": "Max", "CalculationType": "Additive", "Amount": 35 }],
    "MagicCharges": [{ "CalculationType": "Additive", "Amount": 4 }]
  }
}
```
Each staff tier gives different Magic_Power/Volatility/MagicCharges. The staff IS the stat source.

### Integration Approach: Use Hexcode Items as Base Items

When Hexcode is detected, `DynamicLootRegistry` discovers Hexcode's staff and book items (they ARE weapons — `Item.getWeapon() != null`). These become the **base items** for our magic weapon loot pool.

**Loot generation flow:**
```
1. DynamicLootRegistry discovers Hexstaff_Basic_Iron (weapon, material=Iron)
2. Player kills mob → loot roll → magic weapon drop
3. GearManager selects Hexstaff_Basic_Iron as base item
4. Start with base ItemStack (inherits Template_HexStaff interactions + tags + stat modifiers)
5. Inject HexStaff metadata: itemStack.withMetadata("HexStaff", CODEC, staffComp)
6. Inject HexBook metadata for books: itemStack.withMetadata("HexBook", CODEC, bookComp)
7. GearUtils.setGearData() adds RPG modifiers (level, rarity, prefixes, suffixes)
8. Creates rpg_gear_xxx ItemStack — ALL metadata preserved (hex + RPG)
```

**Result:** Player gets an item that is simultaneously:
- A Hexcode casting focus (interactions from Template_HexStaff, tags, hex metadata)
- An RPG weapon (level, rarity, random modifiers like "+15% spell damage")
- A stat provider (base Magic_Power/Volatility from JSON tier + RPG modifier bonuses)

### Material Tier Mapping

| Hexcode Staff | Material | Magic_Power | Volatility | MagicCharges | Approx RPG Level |
|---|---|---|---|---|---|
| Hexstaff_Basic_Crude | Crude | ~0.15 | ~10 | 2 | 1-10 |
| Hexstaff_Basic_Copper | Copper | ~0.20 | ~15 | 2 | 5-15 |
| Hexstaff_Basic_Bronze | Bronze | ~0.25 | ~20 | 3 | 10-20 |
| Hexstaff_Basic_Iron | Iron | 0.45 | 35 | 4 | 15-30 |
| Hexstaff_Basic_Thorium | Thorium | ~0.55 | ~45 | 4 | 25-40 |
| Hexstaff_Basic_Cobalt | Cobalt | ~0.65 | ~55 | 5 | 35-50 |
| Hexstaff_Basic_Adamantite | Adamantite | ~0.80 | ~70 | 5 | 45-60 |
| Hexstaff_Basic_Mithril | Mithril | ~0.90 | ~85 | 6 | 55-70 |
| Hexstaff_Basic_Onyxium | Onyxium | ~1.00 | ~100 | 6 | 65-80 |
| Hexstaff_Special_Arcane | Special | ~1.20 | ~120 | 7 | 70+ |

*(Exact values TBD — read from each JSON during implementation)*

### Book Capacity Scaling

Hexcode books define `MaxGlyphs` per variant. We inject `HexBookComponent` with `maxGlyphs` scaled by RPG rarity:

| RPG Rarity | MaxGlyphs | Maps to Hexcode Book |
|---|---|---|
| Common | 4 | Below Tier 1 |
| Uncommon | 6 | Elemental books (Fire/Ice/Life/Void) |
| Rare | 8 | Generic Hex Book |
| Epic | 10 | Above base |
| Legendary | 12 | Arcane Hexbook |
| Mythic | 15 | Beyond Hexcode tiers |

### Why This Needs Zero Hexcode Changes

| Concern | Resolution |
|---|---|
| Interactions | Inherited from `Template_HexStaff` parent via base item |
| Tags | Inherited from base item JSON (`Family: ["HexStaff"]`) |
| HexStaffAsset lookup | Bypassed — CasterInventory reads metadata first (line 112) |
| Stat modifiers (Magic_Power, etc.) | Inherited from base item `Weapon.StatModifiers` JSON |
| RPG metadata coexistence | Different keys: `RPG:*` vs `HexStaff`/`HexBook` — no conflict |
| Instance ID | Our `rpg_gear_xxx` ID; base item stored in `RPG:BaseItemId` |

### Vanilla Staffs

Vanilla staffs (Tool_Staff_Iron, etc.) do NOT get hex capability. They lack hex interactions and metadata. This is intentional — RPG hex-staffs are strictly better, and the loot system replaces gear constantly. Players naturally upgrade from vanilla to RPG hex-staffs.

### Armor Stat Patching

Both mods patch armor stats via different mechanisms:
- Hexcode: Reflection on `ItemArmor.statModifiers` (adds Mana, Volatility as `StaticModifier`)
- ToO: Reads `ItemArmor.getBaseDamageResistance()` (vanilla armor value) + applies `GearData` modifiers

**These don't conflict** — different fields. Hexcode's armor mana stacks additively with ToO's attribute-based mana in the unified `EntityStatMap`.

### Tooltip Integration

When Hexcode is detected, magic weapon tooltips gain hex-specific sections:

**Staff tooltip** — adds "Hex Casting" section after modifiers:
- Cast Style (Ring/Arc/Sphere)
- Base Magic Power / Volatility / Charges from the staff tier
- Prompt to pair with spellbook

**Spellbook tooltip** — adds "Hex Spellbook" section after modifiers:
- Spell Capacity (MaxGlyphs)
- Off-hand usage hint

**Implementation**: New builder methods in `RichTooltipFormatter`:
- `buildHexCastingSection(GearData)` — for staffs
- `buildHexSpellbookSection(GearData, int maxGlyphs)` — for spellbooks
- Gated behind `TooltipConfig.showHexInfo()` (true only when Hexcode detected)
- Section placed after modifiers, before gems/requirements

### Equipment Mechanics (Confirmed from Code)

- **Staff**: MANDATORY for casting, MainHand only
- **Book**: OPTIONAL, OffHand preferred (fallback to MainHand if OffHand empty)
- **Two-handed**: Hexcode has ZERO two-handed logic — checks slots independently
- **Detection**: Metadata first → tag fallback → ID fallback

---

## Part 7: Stat/Attribute Integration Architecture

### ComputedStats Fields for Magic

```java
// EXISTING fields that affect hex magic (no changes needed):
float maxMana;                    // from WATER attribute
float manaRegen;                  // from WATER attribute
float spellDamage;                // from WATER attribute (% scaling)
float spellDamagePercent;         // from gear/skills
float allElementalDamagePercent;  // from gear/skills
float statusEffectChance;         // from attributes/gear
float statusEffectDuration;       // from VOID attribute
float manaCostReduction;          // from gear/skills
float spellPenetration;           // from gear/skills
float spellEchoChance;            // from keystone
float dotDamagePercent;           // from gear/skills
// + per-element flatDamage, percentDamage, resistance, penetration

// NEW fields to add to ComputedStats.java:
float volatilityMax;    // Max glyph budget per cast
float magicPower;       // Direct multiplier on hex effect magnitude (1.0 = no bonus)
int magicCharges;       // Concurrent active spell limit
float drawAccuracy;     // Bonus to glyph draw quality (0.0 = no bonus)
float castSpeed;        // Affects cast decay rate (0.0 = no bonus)
```

### Attribute Grant Configuration

```yaml
# config.yml additions:
attributes:
  water-grants:
    max-mana: 3.0              # per point (EXISTING)
    mana-regen: 0.15           # per point (EXISTING)
    magic-power: 0.02          # per point (NEW — +2% spell power per WATER)
    volatility-max: 1.5        # per point (NEW — +1.5 volatility budget per WATER)
  fire-grants:
    magic-power: 0.01          # per point (NEW — +1% spell power per FIRE)
  lightning-grants:
    cast-speed: 0.01           # per point (NEW — +1% cast speed per LIGHTNING)

  # Magic charges from level, not attributes
  magic-charges:
    base: 1
    per-levels: 25             # +1 charge every 25 levels

# Gear modifiers that can roll on magic weapons:
# volatility-max (flat), magic-power (%), draw-accuracy (flat), cast-speed (%)
# These join the existing modifier pool for STAFF/WAND/SPELLBOOK weapon types
```

### EntityStatMap Bridge

New system: `StatMapBridge` — applies ComputedStats to Hytale's EntityStatMap:

```java
public class StatMapBridge {
    public void applyToEntity(Ref<EntityStore> ref, ComputedStats stats, CommandBuffer buffer) {
        EntityStatMap statMap = buffer.getComponent(ref, EntityStatMap.getComponentType());

        // Mana
        applyModifier(statMap, DefaultEntityStatTypes.getMana(), "rpg_max_mana", stats.getMaxMana());

        // Hexcode stats (if indices resolved)
        if (HexcodeEntityStatTypes.getVolatility() != Integer.MIN_VALUE) {
            applyModifier(statMap, HexcodeEntityStatTypes.getVolatility(), "rpg_volatility", stats.getVolatilityMax());
            applyModifier(statMap, HexcodeEntityStatTypes.getMagicPower(), "rpg_magic_power", stats.getMagicPower());
            applyModifier(statMap, HexcodeEntityStatTypes.getMagicCharges(), "rpg_magic_charges", stats.getMagicCharges());
        }
    }
}
```

**Soft dependency**: Check if Hexcode stat indices are resolved. If Hexcode isn't loaded, skip magic stat application (indices stay at `Integer.MIN_VALUE`).

---

## Part 7.5: Skill Tree Hexcode Overlay

When Hexcode is detected at startup, a `skill-tree-hexcode.yml` overlay is loaded that appends Hexcode-relevant modifiers to existing magic nodes. The base tree is unchanged — the overlay only ADDS stats and updates descriptions.

### Architecture

```
Server starts
  → Load skill-tree.yml (base: ~600 nodes, 7,455 lines)
  → Check: Hexcode plugin loaded?
    → YES: Load skill-tree-hexcode.yml overlay
      → For each entry: find matching node → append modifiers → update description
    → NO: Skip overlay, pure RPG tree
  → SkillTreeStatAggregator sees all modifiers (base + overlay) uniformly
```

### WATER ARM — "Arcane Mage" (Primary Hex Caster)

| Node | Type | Base Stats | Hexcode Overlay |
|---|---|---|---|
| `water_frostbite_notable` **Arcane Surge** | Notable | +14% Water, +12.5% Spell | +5 Volatility Max |
| `water_evasion_notable` **Wellspring** | Notable | +12.5% Max Mana, +5 Regen, +10 ES | +3 Volatility Max, +0.5% Magic Power |
| `water_shatter_notable` **Spell Mastery** | Notable | Spell Damage + Water Pen | +2% Draw Accuracy, +3% Magic Power |
| `water_precision_notable` **Permafrost** | Notable | +12% Freeze, +15% vs Frozen | +3% Draw Accuracy |
| `water_synergy_1` **Arcane Accumulation** | Synergy | Per 3 Water: +2% Spell | Also: per 3 Water: +1 Volatility Max |
| `water_synergy_hub` **Heart of Water** | Notable | +5% Water/Spell | +5 Volatility Max, +1% Magic Power |
| `water_keystone_1` **Glacial Mastery** | Keystone | +10% Freeze, +30% vs Frozen, +10% Water Mult | +5% Draw Accuracy, +8 Volatility Max |
| `water_keystone_2` **Arcane Reservoir** | Keystone | +20% Damage from Mana, +20% Max Mana | +1 Magic Charge, +15 Volatility Max |

**Overlay descriptions** (replace base descriptions when active):
- **Arcane Surge**: *"Your hex glyphs carry greater arcane weight, expanding your volatility reserves."*
- **Wellspring**: *"A deep well of mana sustains longer hex chains and amplifies their power."*
- **Spell Mastery**: *"Mastery over glyph strokes — your drawn glyphs are more precise and powerful."*
- **Arcane Reservoir**: *"Your mana pool fuels an additional active hex. Volatility budget deepens substantially, at the cost of higher mana consumption."*

### WARLOCK ARM — "Dark Caster" (Hex Power Amplification)

| Node | Type | Base Stats | Hexcode Overlay |
|---|---|---|---|
| `warlock_hex_notable` **Dark Arcana** | Notable | +15% Spell, +12.5% DoT, +2 Mana on Kill | +5% Magic Power |
| `warlock_ritual_notable` **Mind Drain** | Notable | +5 Mana Regen, +3 Life Steal, +5 ES | +3 Volatility Max |
| `warlock_malice_notable` **Cursed Knowledge** | Notable | Spell/DoT bonuses | +3% Magic Power, +2% Draw Accuracy |
| `warlock_damnation_notable` **Eternal Torment** | Notable | Mana/DoT scaling | +5 Volatility Max |
| `warlock_synergy_hub` **Center of Shadow** | Notable | Spell/DoT | +3% Magic Power, +3 Volatility Max |
| `warlock_keystone_1` | Keystone | Dark Caster | +8% Magic Power, +1 Magic Charge |

**Overlay descriptions**:
- **Dark Arcana**: *"Dark knowledge amplifies the raw magnitude of your hex constructs."*
- **Cursed Knowledge**: *"Forbidden study sharpens both glyph precision and hex potency."*

### TEMPEST ARM — "Mobile Mage" (Cast Speed)

| Node | Type | Base Stats | Hexcode Overlay |
|---|---|---|---|
| Squall notable | Notable | Projectile + Spell | +3% Cast Speed |
| Tailwind notable | Notable | Movement + Projectile Speed | +5% Cast Speed |
| Cyclone notable | Notable | AoE Spell Damage | +3% Magic Power, +2% Cast Speed |
| Maelstrom notable | Notable | Multi-hit | +3% Cast Speed |
| Synergy hub | Notable | Spell + Projectile | +5% Cast Speed |
| Keystone | Keystone | Speed caster | +10% Cast Speed, +5% Magic Power |

**Overlay descriptions**:
- Squall notable: *"Storm winds hasten your hex deployment."*
- Keystone: *"Lightning reflexes allow near-instant hex materialization."*

### LICH ARM — "Tanky Dark Mage" (Sustained Channeling)

| Node | Type | Base Stats | Hexcode Overlay |
|---|---|---|---|
| Notable nodes (×4) | Notable | DoT + Defense + ES | +3-5 Volatility Max each |
| Synergy hub | Notable | DoT sustained | +8 Volatility Max |
| Keystone | Keystone | Tanky mage | +1 Magic Charge, +15 Volatility Max |

**Overlay descriptions**:
- Keystone: *"Undying will sustains multiple hexes simultaneously. Your volatility reserves deepen beyond mortal limits."*

### Total Hexcode Budget from Full Skill Tree Allocation

A player who fully invests in magic arms can accumulate (approximate):

| Stat | WATER | WARLOCK | TEMPEST | LICH | Total Possible |
|---|---|---|---|---|---|
| Volatility Max | +31 | +11 | — | +30+ | ~72 |
| Magic Power | +4.5% | +19% | +8% | — | ~31.5% |
| Draw Accuracy | +10% | +2% | — | — | ~12% |
| Cast Speed | — | — | +28% | — | ~28% |
| Magic Charges | +1 | +1 | — | +1 | +3 (base 1 = 4 total) |

These stack with attribute grants and gear modifiers for the full magic build.

### Config Format

```yaml
# skill-tree-hexcode.yml
# Overlay applied when Hexcode plugin detected. Appends modifiers to base nodes.

overlay:
  water_frostbite_notable:
    append_modifiers:
      - { stat: VOLATILITY_MAX, value: 5, type: FLAT }
    description: "Your hex glyphs carry greater arcane weight, expanding your volatility reserves."

  water_keystone_2:
    append_modifiers:
      - { stat: MAGIC_CHARGES, value: 1, type: FLAT }
      - { stat: VOLATILITY_MAX, value: 15, type: FLAT }
    description: "Your mana pool fuels an additional active hex. Volatility budget deepens substantially, at the cost of higher mana consumption."

  warlock_hex_notable:
    append_modifiers:
      - { stat: MAGIC_POWER, value: 5, type: PERCENT }
    description: "Dark knowledge amplifies the raw magnitude of your hex constructs."

  # ... (all nodes listed above)
```

---

## Part 8: What Changes Where

### Changes in TrailOfOrbis (We Control)

| Change | Module | Complexity | Priority |
|--------|--------|------------|----------|
| Force Hexcode IDLE on world drain | `TrailOfOrbis.java` | Low | CRITICAL |
| Exclude `Hexcode_` items from loot discovery | `DynamicLootRegistry` | Low | CRITICAL |
| Inject HexStaff/HexBook components into magic gear | `GearManager` | Medium | HIGH |
| Reclassify SPELLBOOK as off-hand | `WeaponType` / `EquipmentType` | Low | HIGH |
| Spell damage branch in RPGDamageSystem | `combat/RPGDamageSystem` | Medium | HIGH |
| Apply ComputedStats to EntityStatMap | New `StatMapBridge` | Medium | HIGH |
| Add 5 new magic stats to ComputedStats | `attributes/ComputedStats` | Low | HIGH |
| Add magic gear modifiers to modifier pool | `gear/modifiers/` config | Low | HIGH |
| Add attribute grants for magic stats | `config.yml` + `AttributeCalculator` | Low | MEDIUM |
| Skill tree Hexcode overlay config | New `skill-tree-hexcode.yml` | Medium | MEDIUM |
| Skill tree overlay loader (soft dep) | `SkillTreeManager` | Low | MEDIUM |
| Damage type mapping config | `config.yml` + new `SpellDamageMapper` | Low | MEDIUM |
| Ailment trigger from spell damage | `combat/RPGDamageSystem` | Low | MEDIUM |
| Realm pedestal spawning (% chance) | `maps/` biome gen | Low | MEDIUM |
| Erode+Shock amplification cap | `config.yml` + `RPGDamageCalculator` | Low | LOW |

### Changes Riprod Should Make in Hexcode

| Change | Why | Complexity |
|--------|-----|------------|
| Add `DrainPlayerFromWorldEvent` listener | Prevent crash on realm transitions | Low |
| Add caster UUID to damage MetaKey | Let ToO identify spell caster | Low |
| Add `AddPlayerToWorldEvent` listener | Validate session refs after teleport | Low |
| Consider `EntitySource` for damage | Better pipeline integration (optional) | Medium |

### Shared Constants (New Shared File or Convention)

Both mods need to agree on:
- MetaKey name for caster UUID: `"hexcode:caster_uuid"`
- Damage source prefix: `"hex_"` (already used by Hexcode)
- Stat modifier keys: `"rpg_max_mana"`, `"rpg_volatility"`, etc.

---

## Part 9: Implementation Order

### Phase 1: Safety (Prevent Crashes)
1. Force Hexcode IDLE on `DrainPlayerFromWorldEvent` (BUG-1, BUG-2)
2. Exclude `Hexcode_` prefix items from `DynamicLootRegistry` (BUG-3)
3. Coordinate with Riprod: add `DrainPlayerFromWorldEvent` listener + caster UUID MetaKey

### Phase 2: RPG Magic Gear (Zero Hexcode Changes)
4. Detect Hexcode plugin at startup (soft dependency check)
5. DynamicLootRegistry: include Hexcode staff/book items in magic weapon pool (when detected)
6. DynamicLootRegistry: exclude `Hexcode_` template items from random drops
7. Loot generation: use Hexcode items as BASE items for RPG magic gear
8. During loot gen: inject `HexStaff` metadata via `withMetadata("HexStaff", CODEC, comp)`
9. During loot gen: inject `HexBook` metadata with `maxGlyphs` scaled by rarity (4→15)
10. Add magic-specific RPG modifiers to modifier pool (spellDamage, mana, volatilityMax, etc.)
11. Add hex tooltip sections to `RichTooltipFormatter` (cast info for staffs, capacity for books)
12. Test: Hexcode staff drops as RPG gear → hex interactions work → RPG stats apply → tooltip shows both

### Phase 3: Mana Unification
13. Add `StatMapBridge` to apply ComputedStats → EntityStatMap
14. Apply `maxMana` from ComputedStats to EntityStatMap
15. Test: ToO mana (WATER attribute) + Hexcode armor mana + staff mana = unified pool

### Phase 4: Stat Scaling
16. Add 5 new magic stats to ComputedStats (volatilityMax, magicPower, magicCharges, drawAccuracy, castSpeed)
17. Add new stat types to modifier enum (for gear and skill tree)
18. Add attribute grants in config (WATER → volatility/magicPower, FIRE → magicPower, LIGHTNING → castSpeed)
19. Apply to EntityStatMap via StatMapBridge (soft dependency on Hexcode stat indices)
20. Test: WATER points → more mana + higher volatility budget in hex casting

### Phase 5: Skill Tree Hexcode Overlay
21. Create `skill-tree-hexcode.yml` overlay config with modifier additions for magic nodes
22. Implement overlay loader in SkillTreeManager (load after base tree, append modifiers)
23. WATER arm: add volatilityMax + drawAccuracy to notables/keystones
24. WARLOCK arm: add magicPower to notables/keystones
25. TEMPEST arm: add castSpeed to notables/keystones
26. LICH arm: add volatilityMax + magicCharges to notables/keystones
27. Update node description text when overlay active (hex-themed descriptions)
28. Test: allocate Water keystone → see volatilityMax increase in EntityStatMap → Hexcode sees larger budget

### Phase 6: Damage Integration
29. Add spell damage branch to `RPGDamageSystem` (detect `hex_` prefix on EnvironmentSource)
30. Create damage type mapping config (hex_bolt→LIGHTNING, hex_combust→FIRE, etc.)
31. Apply spell damage scaling, elemental resistances, crit from caster stats
32. Trigger ToO ailments from spell damage (fire spells → burn chance, ice → freeze, etc.)
33. Test: Hexcode bolt → ToO resistances → correct damage → ailment proc

### Phase 7: Polish
34. Death recap shows spell name and caster identity
35. Combat indicators show spell damage type colors
36. Erode+Shock amplification cap (configurable)
37. Realm pedestal spawning (% chance when Hexcode detected)
38. Comprehensive integration test suite

---

## Part 10: Design Decisions (RESOLVED 2026-04-26)

### Decision 1: RPG Magic Gear = Hex Magic (RESOLVED)

**Decision**: Use Hexcode's own items (13 staffs, 6 books) as BASE ITEMS for our RPG loot system. No custom templates, no interaction overrides, no Hexcode code changes needed.

**How it works** (verified from CasterInventory.java):
1. `DynamicLootRegistry` discovers Hexcode staff/book items as weapons
2. Loot generation uses Hexcode items as base (inherits interactions, tags, stat modifiers)
3. We inject `HexStaff`/`HexBook` metadata onto the ItemStack via `withMetadata()`
4. `GearUtils.setGearData()` adds RPG modifiers (level, rarity, prefixes, suffixes)
5. CasterInventory reads our injected metadata FIRST (line 112) — never hits asset lookup
6. Result: item has hex interactions + hex metadata + RPG stats + base Hytale stat modifiers

**Staff progression comes from Hexcode's item tiers** — each staff tier has different `Magic_Power`, `Volatility`, `MagicCharges` in its `Weapon.StatModifiers` JSON. RPG modifiers add spell damage, mana, etc. on top.

**Book capacity scales with RPG rarity**: Common=4, Uncommon=6, Rare=8, Epic=10, Legendary=12, Mythic=15

**Template items excluded**: Items named `Template_*` excluded from loot pool (templates, not droppable items).

**Vanilla staffs**: Do NOT get hex capability. RPG hex-staffs (based on Hexcode items) are strictly better. Natural upgrade path via loot.

**Pedestal/Obelisk tiers stay Hexcode-owned** — world blocks, not player gear. Realm pedestal tier scales with realm difficulty (Decision 6).

### Hexcode Progression (Discovered Post-Analysis)

Hexcode has a full **equipment tier progression** (not XP-based):
- **13 staff tiers**: Crude → Copper → Bronze → Iron → Thorium → Cobalt → Adamantite → Mithril → Onyxium + 4 special (Arcane/Astral/Fire/Ice)
- **6 book variants**: 6-12 MaxGlyphs capacity (elemental themes + generic + arcane)
- **5 pedestal tiers**: 2-8 obelisk slots, range 5-35 (Template → Thorium → Fire → Arcane → Void)
- **4 obelisk types**: Seeker (Common), Accuracy (Rare), Efficiency (Epic), Import/Export (Legendary)
- **8 armor mana tiers**: 5-770 mana (Wood → Prisma, exponential scaling)
- **8 armor volatility tiers**: 1-91 volatility (matching armor materials)

This progression aligns naturally with our RPG loot system — both use material-based tier curves.

### Decision 2: Stats Affecting Hex Magic (RESOLVED)

**Decision**: Comprehensive stat mapping. All relevant existing stats affect hex magic. 5 new stats added. Soft dependency — works without Hexcode.

**Existing stats that affect hexes:**
- `maxMana`, `manaRegen`, `manaCostReduction` — mana pool and cost
- `spellDamage`, `spellDamagePercent` — hex damage scaling
- `allElementalDamagePercent` — elemental hex scaling
- `statusEffectChance`, `statusEffectDuration` — ailment proc from spells
- `spellPenetration` — bypass magic resist
- `spellEchoChance` — chance to double-cast
- `dotDamagePercent` — hex DoT scaling (Drain, Erode)
- Per-element `flatDamage`, `percentDamage`, `resistance`, `penetration`

**New stats:**
- `volatilityMax` — max glyph budget per cast (from WATER grants + gear)
- `magicPower` — direct hex effect magnitude multiplier (from WATER/FIRE + gear)
- `magicCharges` — concurrent active spell limit (level-based: base 1, +1/25 levels)
- `drawAccuracy` — bonus to glyph draw quality (gear modifier)
- `castSpeed` — affects cast decay rate (from LIGHTNING + gear)

### Decision 3: Level Gating (RESOLVED)

**Decision**: Level-gated via equipment requirements. Magic weapons (staff/wand/spellbook) are RPG gear with level requirements — same system as all other gear. No special hex-specific gating needed; it's free from Decision 1. Hexcode's own progression (material tiers) naturally maps to our level tiers — higher material = higher level requirement.

### Decision 4: Spells in Realms (RESOLVED — Non-Issue)

**Decision**: No special handling needed. `MONSTER_DAMAGE` is monster→player only (confirmed). Spell damage goes through `RPGDamageSystem` which already applies mob armor, resistances, and elemental defense. Spells naturally work in realms at any tier.

### Decision 5: Mob Imbuements (RESOLVED — Deferred)

**Decision**: Architecture supports it (weapon imbuement system exists in Hexcode). Implementation deferred to future work. When ready: pre-configure elite/boss mob weapons with hex imbuements.

### Decision 6: Pedestals in Realms (RESOLVED)

**Decision**: When Hexcode is detected, realm biome generation includes a configurable % chance to spawn a pedestal block. Players can modify spells mid-run at these fixed locations.

```yaml
hexcode:
  realm-pedestal-spawn-chance: 15  # % per realm
```

Requires BUG-1 fix (force IDLE on realm exit) to prevent session leaks.

---

## Appendix: Research Evidence Summary

| Research Area | Agent | Key Finding |
|---|---|---|
| Hexcode Damage | #1 | All damage uses `EnvironmentSource("hex_xxx")` + `DamageSystems.executeDamage()` |
| Hexcode Stats | #2 | 3 custom stats via `EntityStatType.getAssetMap().getIndex()`, armor patched via reflection |
| Hexcode Effects | #3 | 12 effects, no resistance mechanics, Burning NOT IMPLEMENTED |
| Hexcode Entities | #4 | No `DrainPlayerFromWorldEvent` listener, `MountOrphanReaperSystem` as failsafe |
| ToO Damage | #5 | 7-phase pipeline, intercepts ALL damage, `EnvironmentSource` → simplified path |
| ToO Attributes | #6 | 140+ ComputedStats, maxMana exists but NOT in EntityStatMap |
| ToO Ailments | #7 | 4 ailments, chance-based, public API via `AilmentTracker.applyAilment()` |
| ToO Gear | #8 | HexBook=OTHER (invisible), HexStaff=WEAPON (loot risk), namespace isolated |
| World Transitions | #9 | Hexcode CRAFTING+teleport=crash, 3 cleanup pathways in ToO, 0 in Hexcode |
| Hexcode Items | #10 | HexBook=off-hand spell storage, HexStaff=main-hand cast focus, both required to cast |
| Hexcode Progression | #10→#13 | **CORRECTED**: Full equipment tier progression (13 staffs, 6 books, 5 pedestals, 4 obelisks, 8 armor tiers) |
| Hexcode Mob Casting | #10 | Only PlayerHexRoot exists, mobs can trigger imbuements but not actively cast |
| ToO Weapon Types | #11 | 18 weapon types: STAFF, WAND, SPELLBOOK already exist (35 magic items in DB) |
| ToO Realm Modifiers | #11 | 13 modifiers, MONSTER_DAMAGE = monster→player only (confirmed) |
| ToO Skill Tree | #12 | 600+ nodes, 14 arms, 4 magic arms (WATER/WARLOCK/TEMPEST/LICH), config-driven |
| Interaction survival | #14 | `ItemRegistryService` copies base item via `new Item(base)` → asset map → equipment system reads interactions |
| Weapon.StatModifiers | #15 | Hytale reads from asset map not metadata → copy constructor preserves `weapon` field → modifiers apply natively |
| Component codecs | #16 | HexStaff: 2 fields PascalCase. HexBook: 5 fields. BuilderCodec, extras ignored, no compile dep needed |
| Plugin detection | #17 | `PluginManager.get().getPlugin("Riprod:hexcode")` — safe during `start()`, all mod items in asset map |

## Appendix B: Resolved Implementation Unknowns

All 4 implementation unknowns verified from actual source code (2026-04-26):

### Interaction Survival on rpg_gear_xxx Items
`ItemRegistryService.createAndRegisterSync(baseItem, customId)` calls `new Item(baseItem)` which copies ALL fields including interactions and weapon config. The copy is registered in `Item.getAssetMap()` under the custom ID. Hytale's equipment ECS system reads interactions from the registered Item asset. Our `ItemDefinitionBuilder` strips container interactions (crash prevention) but combat/equipment interactions are preserved — confirmed by our own comment at line 143: *"Weapon/armor combat interactions come from the equipment system, not the item definition, so stripping these is safe."*

### Weapon.StatModifiers Application
`StatModifiersManager.recalculateEntityStatModifiers()` calls `itemStack.getItem()` → `Item.getAssetMap().getAsset(itemId)`. For `rpg_gear_xxx`, this returns the registered copy which has `weapon.getStatModifiers()` copied from the base Hexcode staff. Magic_Power, Volatility, MagicCharges apply natively via Hytale's stat system. No double-counting risk: base stats from weapon JSON (native) + bonus stats from RPG attributes/skills (our StatMapBridge) stack additively and intentionally.

### Writing Hexcode Metadata Without Compile-Time Dependency
Both components use Hytale's `BuilderCodec` with PascalCase BSON keys. Extra fields are ignored during deserialization. We write compatible BSON directly:
- HexStaff: `{"StyleId": "ring", "CastDecayRate": 0.05}`
- HexBook: `{"Hexes": [], "MaxCapacity": N, "BookId": "", "HexNames": {}, "HexColors": {}}`
Metadata key strings: `"HexStaff"` and `"HexBook"` (from CasterInventory constants).

### Plugin Detection and Load Order
`PluginManager.get().getPlugin(new PluginIdentifier("Riprod", "hexcode"))` returns the loaded plugin or null. Safe to call during `TrailOfOrbis.start()` where `GearManager.initialize()` → `DynamicLootRegistry.discoverItems()` already runs. At this lifecycle point, all plugins have completed their `start()` and all mod items are in `Item.getAssetMap()`.
