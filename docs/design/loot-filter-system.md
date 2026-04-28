# Loot Filter System — Detailed Design Document

## 1. Overview

### Problem
Players accumulate massive amounts of RPG gear during realm runs and overworld exploration. Without filtering, inventories fill with Common/Uncommon junk that must be manually discarded. This is especially painful at higher levels where only Epic+ gear is relevant.

### Solution
A per-player loot filter system that silently prevents pickup of unwanted RPG gear. Two modes serve different player types:

1. **Quick filter** — One-command rarity threshold for casual players (`/lf quick rare`). No rules, no profiles, just "block everything below X rarity."
2. **Custom profiles** — Ordered rules with first-match-wins evaluation, AND logic within rules, mixed ALLOW/BLOCK actions. Inspired by Last Epoch's in-game filter, for power users.

Progressive disclosure: simple path for the 60% who just want to hide junk, full power available but never forced.

### Scope
- **RPG gear only** — items with `GearData` (weapons, armor, shields). Vanilla items (wood, food, ores) always pick up normally.
- **Ground drops only** — chest loot and reward chests are unaffected (players open those manually).

### Non-Goals
- No per-player visual changes on items (Hytale ECS is global — no per-player rendering)
- No sound/beam/minimap alerts (no per-player entity visual control)
- No item preview in filter rules (would require item generation at config time)

---

## 2. Hytale API Research: Item Pickup

### Ground Truth: Neither Pickup Path Fires a Cancellable Event

Decompiled source analysis (March 2026) reveals that **no ground-drop item in Hytale fires `InteractivelyPickupItemEvent`**. The original design assumption was wrong.

#### Two Pickup Paths in `PlayerItemEntityPickupSystem.tick()`

**Source**: `com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem`

The system queries entities with `ItemComponent` + `TransformComponent`, excluding `Interactable`, `PickupItemComponent`, and `PreventPickup`.

**Path A: Interaction-Based** (line 124–151)
- **Trigger**: `item.getInteractions().get(InteractionType.Pickup) != null`
- **Code path**: Builds an `InteractionChain` → executes `PickupItemInteraction.firstRun()`
- **What `PickupItemInteraction` does**: Calls `playerComponent.giveItem()` **directly** (line 61 of `PickupItemInteraction.java`). It does **NOT** call `ItemUtils.interactivelyPickupItem()`.
- **Event fired**: NONE
- **Cancellable**: NO

**Path B: Proximity-Based** (line 154–194)
- **Trigger**: No `Pickup` interaction defined
- **Code path**: Spatial query for nearest player → `playerComponent.giveItem()` directly
- **Event fired**: NONE
- **Cancellable**: NO

**Both paths bypass `InteractivelyPickupItemEvent` entirely.** The item goes straight into the player's inventory via `Player.giveItem()`.

#### Where `InteractivelyPickupItemEvent` Actually Fires

`ItemUtils.interactivelyPickupItem()` is the **only** method that dispatches `InteractivelyPickupItemEvent`. It is called from exactly two places, neither of which involves ground-drop pickup:

| Caller | Context | When It Fires |
|--------|---------|---------------|
| `BlockHarvestUtils` (line 800) | Block mining drops | Player breaks a block → drops go directly to inventory via this event |
| `FarmingUtil` (line 328) | Crop harvesting | Player harvests a crop → drops go directly to inventory via this event |

These are **block-to-inventory** flows, not **ground-entity-to-inventory** flows. Ground item entities never pass through `ItemUtils.interactivelyPickupItem()`.

#### `InteractivelyPickupItemEvent` API (for reference)

**Source**: `com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent`

```java
public class InteractivelyPickupItemEvent extends CancellableEcsEvent {
    private ItemStack itemStack;
    public ItemStack getItemStack();
    public void setItemStack(ItemStack itemStack);
    // Inherited: isCancelled(), setCancelled(boolean)
}
```

When cancelled, `ItemUtils.interactivelyPickupItem()` calls `dropItem()` → `throwItem()` to re-drop the item at the player's location with a 1.5s pickup delay.

#### `InventoryChangeEvent` — Post-Pickup (NOT Cancellable)

**Updated 2026-03-26**: Event changed from `LivingEntityInventoryChangeEvent` to `InventoryChangeEvent` (ECS event). Now requires `EntityEventSystem` registration instead of `EventRegistry.registerGlobal()`.

**Source**: `com.hypixel.hytale.server.core.inventory.InventoryChangeEvent`

Extends `EntityEvent`, **not** `CancellableEcsEvent`. Fires AFTER the item is already in inventory. Useful for detection but **cannot prevent pickup**.

### Chosen Strategy: Custom Pickup Interaction

Since neither built-in path fires a cancellable event, we must inject our own. **We register a custom `Pickup` interaction on all RPG gear items** that routes through `ItemUtils.interactivelyPickupItem()`, giving us the cancellable `InteractivelyPickupItemEvent`.

#### How It Works

1. **At item registration** (`ItemSyncService`): When building RPG gear item definitions via `ItemDefinitionBuilder`, add a `Pickup` interaction pointing to our custom interaction ID (e.g., `"*TrailOfOrbis_FilteredPickup"`).

2. **Custom interaction class** (`FilteredPickupInteraction extends SimpleInstantInteraction`): Instead of calling `playerComponent.giveItem()` directly (like vanilla `PickupItemInteraction`), it calls `ItemUtils.interactivelyPickupItem()`:

```java
@Override
protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
    Ref<EntityStore> ref = context.getEntity();
    CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
    Ref<EntityStore> targetRef = context.getTargetEntity();
    if (targetRef == null || !targetRef.isValid()) {
        context.getState().state = InteractionState.Failed;
        return;
    }

    ItemComponent itemComponent = commandBuffer.getComponent(targetRef, ItemComponent.getComponentType());
    if (itemComponent == null) {
        context.getState().state = InteractionState.Failed;
        return;
    }

    TransformComponent transform = commandBuffer.getComponent(targetRef, TransformComponent.getComponentType());
    ItemStack itemStack = itemComponent.getItemStack();
    if (!ItemStack.isEmpty(itemStack)) {
        Vector3d origin = transform != null ? transform.getPosition() : null;
        // THIS is the key call — routes through the cancellable event
        ItemUtils.interactivelyPickupItem(ref, itemStack, origin, commandBuffer);
        itemComponent.setRemovedByPlayerPickup(true);
        commandBuffer.removeEntity(targetRef, RemoveReason.REMOVE);
    }
}
```

3. **Event listener** (`LootFilterPickupSystem`): Listens for `InteractivelyPickupItemEvent` as an `EntityEventSystem`. When the filter says BLOCK, calls `event.setCancelled(true)`. The engine re-drops the item automatically via `ItemUtils.dropItem()`.

4. **Path A activation**: Because RPG gear items now have a `Pickup` interaction, `PlayerItemEntityPickupSystem` takes Path A (interaction-based) instead of Path B (direct proximity). Path A executes our custom interaction, which calls `interactivelyPickupItem()`, which dispatches the event, which our listener can cancel.

#### Why This Approach

| Approach | Pre-pickup? | Per-player? | Visual flicker? | Complexity |
|----------|-------------|-------------|-----------------|------------|
| **Custom Pickup interaction** (chosen) | ✅ Yes | ✅ Yes | ❌ None | Medium — one interaction class + item registration change |
| `PreventPickup` component | ✅ Yes | ❌ Per-entity only | ❌ None | Low but wrong — can't filter per-player |
| `InventoryChangeEvent` (ECS event) | ❌ Post-pickup | ✅ Yes | ✅ Yes — item appears then drops | Medium — must remove from inventory + re-drop |
| Tick system scanning inventory | ❌ Post-pickup | ✅ Yes | ✅ Yes — delayed | High — polling, timing issues |

#### Risk: Interaction Override on Base Items

Our custom `Pickup` interaction is set on the **RPG gear item definition** (the `rpg_gear_xxx` custom item), not on the base vanilla item. This means:
- ✅ RPG gear dropped on the ground → routes through our filtered pickup
- ✅ Vanilla items (wood, food, non-RPG weapons) → unaffected, use default pickup
- ⚠️ If vanilla base item already has a `Pickup` interaction, our override replaces it. This is fine — our interaction does the same thing (pickup) but adds the event dispatch.

**Need to verify in-game**: That `ItemDefinitionBuilder` allows setting/overriding the `Interactions` map on custom items. If it doesn't expose this, we may need to use the `InteractionModule` to register the interaction differently.

#### Fallback: Post-Pickup Removal

If custom interaction injection proves impossible (API limitation), fall back to:

1. Listen for `InventoryChangeEvent` (ECS event — requires `EntityEventSystem`, not `EventRegistry`)
2. Detect blocked RPG gear via `GearUtils.isRpgGear()` + filter evaluation
3. Remove item from player inventory via `Player.removeItem()` or slot manipulation
4. Re-drop at player's feet via `ItemComponent.generateItemDrop()`
5. **Downside**: Brief visual flicker (item appears in hotbar then drops)
6. **Mitigation**: Execute removal in the same tick — may be fast enough to be imperceptible

#### Other Relevant Components

| Component | Purpose | Relevance |
|-----------|---------|-----------|
| `PreventPickup` | Singleton marker — prevents ALL pickup of an entity | Per-entity, not per-player. Could be used to delay pickup temporarily but wrong tool for per-player filters. |
| `PickupItemComponent` | Animation entity during pickup (flight to player) | Read-only — item already committed at this point |
| `ItemComponent` | Core item data + pickup delay/throttle/radius | `pickupDelay` default: 0.5s regular, 1.5s player-dropped. `setRemovedByPlayerPickup(true)` marks entity for removal. |
| `DespawnComponent` | Lifetime/despawn tracking | Adjusted when partial pickups change stack size |

---

## 3. Data Model

### 3.1 FilterAction

```
Package: io.github.larsonix.trailoforbis.lootfilter.model
File: FilterAction.java
```

Simple enum:
```java
public enum FilterAction {
    ALLOW,
    BLOCK
}
```

No additional fields needed. Used by both `FilterRule.action` and `FilterProfile.defaultAction`.

---

### 3.2 ConditionType

```
File: ConditionType.java
```

```java
public enum ConditionType {
    MIN_RARITY("Minimum Rarity"),
    MAX_RARITY("Maximum Rarity"),
    EQUIPMENT_SLOT("Equipment Slot"),
    WEAPON_TYPE("Weapon Type"),
    ARMOR_MATERIAL("Armor Material"),
    ITEM_LEVEL_RANGE("Item Level Range"),
    QUALITY_RANGE("Quality Range"),
    REQUIRED_MODIFIERS("Required Modifiers"),
    MODIFIER_VALUE_RANGE("Modifier Value Range"),
    MIN_MODIFIER_COUNT("Minimum Modifier Count"),
    IMPLICIT_CONDITION("Weapon Implicit"),
    CORRUPTION_STATE("Corruption State");

    private final String displayName;
    // constructor + getter
}
```

Used as the JSON discriminator field when serializing `FilterCondition` records.

---

### 3.3 CorruptionFilter

```
File: CorruptionFilter.java
```

```java
public enum CorruptionFilter {
    CORRUPTED_ONLY,
    NOT_CORRUPTED,
    EITHER
}
```

---

### 3.4 FilterCondition (Sealed Interface + 12 Records)

```
File: FilterCondition.java
```

This is the core of the filtering engine. A sealed interface with 12 concrete record implementations. Each record:
- Stores condition parameters
- Implements `matches(GearData, EquipmentType)` with self-contained evaluation logic
- Implements `describe()` for human-readable summaries (used by commands, UI, and test output)
- Returns its `ConditionType` for serialization

```java
public sealed interface FilterCondition permits
    FilterCondition.MinRarity,
    FilterCondition.MaxRarity,
    FilterCondition.EquipmentSlotCondition,
    FilterCondition.WeaponTypeCondition,
    FilterCondition.ArmorMaterialCondition,
    FilterCondition.ItemLevelRange,
    FilterCondition.QualityRange,
    FilterCondition.RequiredModifiers,
    FilterCondition.ModifierValueRange,
    FilterCondition.ImplicitCondition,
    FilterCondition.MinModifierCount,
    FilterCondition.CorruptionStateCondition {

    ConditionType type();
    boolean matches(GearData gearData, EquipmentType equipmentType);
    String describe();
}
```

#### Condition Implementation Details

**MinRarity** — `rarity >= threshold`
```java
record MinRarity(GearRarity threshold) implements FilterCondition {
    ConditionType type() { return ConditionType.MIN_RARITY; }
    boolean matches(GearData g, EquipmentType e) {
        return g.rarity().ordinal() >= threshold.ordinal();
    }
    String describe() { return threshold.displayName() + " or better"; }
    // e.g., "Epic or better"
}
```
Note: `GearRarity` enum order is COMMON → UNCOMMON → RARE → EPIC → LEGENDARY → MYTHIC → UNIQUE, so ordinal comparison works directly.

**MaxRarity** — `rarity <= threshold`
```java
record MaxRarity(GearRarity threshold) implements FilterCondition {
    // Same pattern, flipped comparison
    boolean matches(GearData g, EquipmentType e) {
        return g.rarity().ordinal() <= threshold.ordinal();
    }
    String describe() { return threshold.displayName() + " or worse"; }
    // e.g., "Rare or worse"
}
```

**EquipmentSlotCondition** — slot membership check
```java
record EquipmentSlotCondition(Set<String> slots) implements FilterCondition {
    // slots: "weapon", "head", "chest", "legs", "hands", "off_hand"
    boolean matches(GearData g, EquipmentType e) {
        return slots.contains(e.getSlotName());
    }
    String describe() {
        return String.join(", ", slots.stream()
            .map(s -> capitalize(s.replace("_", " ")))
            .toList());
    }
    // e.g., "Chest, Legs"
}
```
Depends on: `EquipmentType.getSlotName()` which returns one of the 6 slot strings. Need to verify this method exists or create a mapping from `EquipmentType.getCategory()` + `EquipmentType.getArmorSlot()`.

**Mapping logic if `getSlotName()` doesn't exist**:
- `Category.WEAPON` → "weapon"
- `Category.OFFHAND` → "off_hand"
- `Category.ARMOR` + `ArmorSlot.HEAD` → "head"
- `Category.ARMOR` + `ArmorSlot.CHEST` → "chest"
- `Category.ARMOR` + `ArmorSlot.LEGS` → "legs"
- `Category.ARMOR` + `ArmorSlot.HANDS` → "hands"

**WeaponTypeCondition** — weapon type membership
```java
record WeaponTypeCondition(Set<WeaponType> types) implements FilterCondition {
    boolean matches(GearData g, EquipmentType e) {
        // Only applies to weapons — non-weapons don't match
        if (e.getCategory() != EquipmentType.Category.WEAPON) return false;
        return types.contains(e.getWeaponType());
    }
    String describe() {
        return types.stream().map(WeaponType::displayName)
            .collect(Collectors.joining(", "));
    }
    // e.g., "Sword, Dagger"
}
```
Uses existing `WeaponType` enum (18 types: SWORD, DAGGER, AXE, etc.)

**ArmorMaterialCondition** — armor material membership
```java
record ArmorMaterialCondition(Set<ArmorMaterial> materials) implements FilterCondition {
    boolean matches(GearData g, EquipmentType e) {
        if (e.getCategory() != EquipmentType.Category.ARMOR) return false;
        return materials.contains(e.getArmorMaterial());
    }
    String describe() {
        return materials.stream().map(ArmorMaterial::displayName)
            .collect(Collectors.joining(", "));
    }
    // e.g., "Leather, Plate"
}
```
Uses existing `ArmorMaterial` enum (CLOTH, LEATHER, PLATE, WOOD, SPECIAL)

**ItemLevelRange** — `min <= level <= max`
```java
record ItemLevelRange(int min, int max) implements FilterCondition {
    // Compact constructor: swap if min > max, clamp to 1-1_000_000
    boolean matches(GearData g, EquipmentType e) {
        return g.level() >= min && g.level() <= max;
    }
    String describe() {
        if (min == max) return "Level " + min;
        return "Level " + min + "–" + max;
    }
    // e.g., "Level 13–16"
}
```

**QualityRange** — `min <= quality <= max`
```java
record QualityRange(int min, int max) implements FilterCondition {
    // Compact constructor: swap if min > max, clamp to 1-101
    boolean matches(GearData g, EquipmentType e) {
        return g.quality() >= min && g.quality() <= max;
    }
    String describe() {
        if (max >= 101) return "Quality " + min + "+";
        if (min <= 1) return "Quality ≤" + max;
        return "Quality " + min + "–" + max;
    }
    // e.g., "Quality 80+"
}
```

**RequiredModifiers** — "at least N of these modifier IDs present on the item"
```java
record RequiredModifiers(Set<String> modifierIds, int minCount) implements FilterCondition {
    // modifierIds: e.g., {"sharp", "crit_chance", "max_health"}
    // minCount: e.g., 1 (at least one of these must be present)
    boolean matches(GearData g, EquipmentType e) {
        long matchCount = g.allModifiers().stream()
            .map(GearModifier::id)
            .filter(modifierIds::contains)
            .count();
        return matchCount >= minCount;
    }
    String describe() {
        String mods = String.join(", ", modifierIds);
        if (minCount == 1 && modifierIds.size() == 1) return "Has: " + mods;
        if (minCount >= modifierIds.size()) return "Has all: " + mods;
        return "Has " + minCount + "+ of: " + mods;
    }
    // e.g., "Has: crit_chance" or "Has 2+ of: sharp, max_health, dodge"
}
```
This mirrors Last Epoch's "Affix" condition with "at least N of these affixes" logic.

Note on performance: `allModifiers()` returns a combined list of prefixes + suffixes (max 6 items). The stream + filter is negligible cost at this size.

**ModifierValueRange** — "specific modifier with roll value in range"
```java
record ModifierValueRange(String modifierId, double minValue, double maxValue)
    implements FilterCondition {
    // modifierId: e.g., "crit_chance"
    // minValue/maxValue: e.g., 15.0 / 100.0 (meaning "crit_chance >= 15%")
    // Compact constructor: swap if min > max

    ConditionType type() { return ConditionType.MODIFIER_VALUE_RANGE; }

    boolean matches(GearData g, EquipmentType e) {
        return g.allModifiers().stream()
            .filter(m -> m.id().equals(modifierId))
            .anyMatch(m -> m.value() >= minValue && m.value() <= maxValue);
    }
    String describe() {
        if (maxValue >= 999_999) return modifierId + " ≥ " + formatValue(minValue);
        if (minValue <= 0) return modifierId + " ≤ " + formatValue(maxValue);
        return modifierId + " " + formatValue(minValue) + "–" + formatValue(maxValue);
    }
    // e.g., "crit_chance ≥ 15" or "max_health 50–100"

    private static String formatValue(double v) {
        return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
    }
}
```

This condition enables power users to filter on **modifier roll values**, not just presence. A player can express "I want crit_chance, but only if the roll is 15% or higher." Multiple `ModifierValueRange` conditions on the same rule means AND: "crit_chance >= 15 AND max_health >= 50."

Note on performance: Same as `RequiredModifiers` — streams over max 6 modifiers. Negligible.

**ImplicitCondition** — weapon implicit roll quality and damage type
```java
record ImplicitCondition(double minPercentile, Set<String> damageTypes)
    implements FilterCondition {
    // minPercentile: 0.0-1.0 (0.0 = any roll, 0.8 = top 20% rolls)
    // damageTypes: e.g., {"physical_damage"} or empty = any type
    // Compact constructor: clamp percentile to [0.0, 1.0], defensive copy damageTypes

    ConditionType type() { return ConditionType.IMPLICIT_CONDITION; }

    boolean matches(GearData g, EquipmentType e) {
        // Non-weapons have no implicit — condition doesn't match
        if (g.implicit() == null) return false;

        // Filter by damage type if specified
        if (!damageTypes.isEmpty()
            && !damageTypes.contains(g.implicit().damageType())) {
            return false;
        }

        // Filter by roll quality (percentile within the weapon's own range)
        return g.implicit().rollPercentile() >= minPercentile;
    }

    String describe() {
        List<String> parts = new ArrayList<>();
        if (!damageTypes.isEmpty()) {
            parts.add(damageTypes.stream()
                .map(WeaponImplicit::damageTypeDisplayName)
                .collect(Collectors.joining("/")));
        }
        if (minPercentile > 0.0) {
            parts.add("roll ≥ " + (int)(minPercentile * 100) + "%");
        }
        if (parts.isEmpty()) return "Has weapon implicit";
        return "Implicit: " + String.join(", ", parts);
    }
    // e.g., "Implicit: Physical Damage, roll ≥ 80%"
    // e.g., "Implicit: roll ≥ 50%"
    // e.g., "Has weapon implicit"
}
```

This condition operates on `WeaponImplicit`, which is a separate field from explicit modifiers (prefixes/suffixes). Key design choices:

- **Percentile-based**, not absolute value: A level 5 sword implicit range is ~20–30, a level 20 is ~150–200. Absolute thresholds would be level-dependent and confusing. Percentile ("top 20% of rolls") is universally meaningful because `WeaponImplicit.rollPercentile()` normalizes the roll within its own range.
- **Damage type filtering**: Lets players specify "physical only" or "spell only" weapons. This is separate from `WeaponType` — a staff is spell damage, a sword is physical, but this condition works even if the player hasn't set a weapon type filter.
- **Non-weapons automatically don't match**: `g.implicit() == null` for armor/shields, so this condition acts as an implicit "weapons only" gate when used in a rule.
- **`minPercentile: 0.0` with empty `damageTypes`** = "is a weapon" (has any implicit). Useful as a simpler alternative to listing all 18 weapon types.

**MinModifierCount** — total modifiers >= threshold
```java
record MinModifierCount(int count) implements FilterCondition {
    // count: 0-6
    boolean matches(GearData g, EquipmentType e) {
        return g.modifierCount() >= count;
    }
    String describe() { return count + "+ modifiers"; }
    // e.g., "4+ modifiers"
}
```
`GearData.modifierCount()` returns `prefixes.size() + suffixes.size()`.

**CorruptionStateCondition** — corruption filter
```java
record CorruptionStateCondition(CorruptionFilter filter) implements FilterCondition {
    boolean matches(GearData g, EquipmentType e) {
        return switch (filter) {
            case CORRUPTED_ONLY -> g.corrupted();
            case NOT_CORRUPTED -> !g.corrupted();
            case EITHER -> true;
        };
    }
    String describe() {
        return switch (filter) {
            case CORRUPTED_ONLY -> "Corrupted only";
            case NOT_CORRUPTED -> "Not corrupted";
            case EITHER -> "Any corruption";
        };
    }
}
```

#### Condition Interaction Matrix

When multiple conditions are present on a rule (AND logic), some combinations are redundant or contradictory:

| Combination | Valid? | Note |
|-------------|--------|------|
| MinRarity(RARE) + MaxRarity(EPIC) | ✅ | Range: Rare-Epic only |
| MinRarity(EPIC) + MaxRarity(RARE) | ❌ | Impossible — never matches anything |
| WeaponType + ArmorMaterial | ❌ | Impossible — item can't be both |
| WeaponType + EquipmentSlot(weapon) | ✅ | Redundant but harmless |
| EquipmentSlot(head,chest) + ArmorMaterial(PLATE) | ✅ | Plate head/chest only |
| RequiredModifiers({crit}) + ModifierValueRange(crit, 15, ∞) | ✅ | Redundant presence check, but harmless |
| Multiple ModifierValueRange on same modifier | ✅ | AND'd — effective range is intersection |
| ImplicitCondition + WeaponType | ✅ | Both apply — weapon type AND good implicit roll |
| ImplicitCondition + ArmorMaterial | ❌ | Impossible — armor has no implicit |
| ImplicitCondition(physical) + WeaponType(STAFF) | ❌ | Staffs are spell damage — never matches |

**Validation strategy**: We do NOT prevent contradictory combinations. They simply result in rules that never match — which is harmless. This keeps validation simple and doesn't frustrate users who are experimenting. The UI can show a warning ("This rule will never match any items") but won't block saving.

---

### 3.5 FilterRule

```
File: FilterRule.java
```

```java
public record FilterRule(
    String name,
    boolean enabled,
    FilterAction action,
    List<FilterCondition> conditions
) {
    // Compact constructor:
    // - name defaults to "New Rule" if blank
    // - conditions defensively copied via List.copyOf()
    // - enabled defaults to true

    public boolean matches(GearData gearData, EquipmentType equipmentType) {
        if (!enabled) return false;
        return conditions.stream().allMatch(c -> c.matches(gearData, equipmentType));
    }

    /** One-line human-readable summary for list views and commands. */
    public String describeSummary() {
        String condText = conditions.isEmpty()
            ? "Everything"
            : conditions.stream().map(FilterCondition::describe)
                .collect(Collectors.joining(", "));
        return condText + " → " + action.name();
    }
    // e.g., "Epic or better, Leather, Chest/Legs, Level 13–16, Has: max_health → ALLOW"

    /**
     * Per-condition pass/fail breakdown for /lf test output.
     * Returns a list of "✓ condition" or "✗ condition (reason)" strings.
     */
    public List<String> describeMatch(GearData gearData, EquipmentType equipmentType) {
        return conditions.stream().map(c -> {
            boolean pass = c.matches(gearData, equipmentType);
            return (pass ? "✓ " : "✗ ") + c.describe();
        }).toList();
    }
}
```

**Design notes**:
- `enabled` field allows toggling rules without deleting them (like Last Epoch's diamond toggle)
- Empty conditions list = matches everything = catch-all rule (intentional, not a bug)
- Disabled rules are skipped during evaluation (not just "don't match")
- `describeSummary()` powers `/lf list`, `/lf status`, and rule list in UI
- `describeMatch()` powers `/lf test` — shows per-condition pass/fail for debugging filters

---

### 3.6 FilterProfile

```
File: FilterProfile.java
```

Immutable class with builder pattern (following `SkillTreeData` pattern):

```java
public final class FilterProfile {
    private final String id;                    // UUID string, auto-generated
    private final String name;                  // User-defined, e.g., "Farming Mode"
    private final FilterAction defaultAction;   // ALLOW or BLOCK when no rule matches
    private final List<FilterRule> rules;       // Ordered, first-match-wins
    private final Instant createdAt;
    private final Instant lastModified;

    // Private constructor — use builder
    private FilterProfile(String id, String name, FilterAction defaultAction,
                          List<FilterRule> rules, Instant createdAt, Instant lastModified) {
        this.id = id;
        this.name = name;
        this.defaultAction = defaultAction;
        this.rules = List.copyOf(rules);  // Defensive copy
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }

    // --- Evaluation ---

    public FilterAction evaluate(GearData gearData, EquipmentType equipmentType) {
        for (FilterRule rule : rules) {
            if (rule.matches(gearData, equipmentType)) {
                return rule.action();
            }
        }
        return defaultAction;
    }

    /**
     * Detailed evaluation trace for /lf test. Returns the matching rule index,
     * per-condition pass/fail for each rule attempted, and final result.
     */
    public EvaluationTrace evaluateWithTrace(GearData gearData, EquipmentType equipmentType) {
        List<RuleTrace> traces = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            FilterRule rule = rules.get(i);
            if (!rule.enabled()) continue;
            List<String> details = rule.describeMatch(gearData, equipmentType);
            boolean matched = rule.matches(gearData, equipmentType);
            traces.add(new RuleTrace(i + 1, rule.name(), matched, rule.action(), details));
            if (matched) {
                return new EvaluationTrace(traces, rule.action(), i + 1);
            }
        }
        return new EvaluationTrace(traces, defaultAction, -1);
    }

    // EvaluationTrace and RuleTrace are simple inner records for structured output

    // --- Immutable update methods ---

    public FilterProfile withName(String newName) { ... }
    public FilterProfile withDefaultAction(FilterAction action) { ... }
    public FilterProfile withRules(List<FilterRule> newRules) { ... }
    public FilterProfile withAddedRule(FilterRule rule) { ... }
    public FilterProfile withRemovedRule(int index) { ... }
    public FilterProfile withMovedRule(int from, int to) { ... }
    public FilterProfile withUpdatedRule(int index, FilterRule rule) { ... }

    // --- Builder ---

    public static Builder builder() { return new Builder(); }
    public Builder toBuilder() { ... }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String name = "New Filter";
        private FilterAction defaultAction = FilterAction.ALLOW;
        private List<FilterRule> rules = new ArrayList<>();
        private Instant createdAt = Instant.now();
        private Instant lastModified = Instant.now();

        // setter methods returning Builder for chaining
        public FilterProfile build() { ... }
    }

    // --- Getters ---
    // Standard getters for all fields
}
```

**Key design decisions**:
- `id` is a UUID string (not java.util.UUID) for simpler JSON serialization
- `rules` is `List.copyOf()` — guaranteed immutable, preserves order
- All `with*()` methods update `lastModified` to `Instant.now()`
- `withMovedRule(from, to)` handles reordering since HyUI has no drag-and-drop
- `evaluateWithTrace()` powers the `/lf test` command — full rule-by-rule breakdown

---

### 3.7 PlayerFilterState

```
File: PlayerFilterState.java
```

Top-level per-player container — this is what gets serialized to JSON in the database:

```java
public final class PlayerFilterState {
    private final UUID playerId;
    private final List<FilterProfile> profiles;
    private final String activeProfileId;       // null = no active profile
    private final boolean filteringEnabled;     // Global on/off toggle
    private final GearRarity quickFilterRarity; // null = not using quick mode
    private final Instant lastModified;

    // Private constructor — use builder
    // profiles: List.copyOf() for immutability

    // --- Evaluation (called by LootFilterManager) ---

    /**
     * Quick filter takes priority over profiles.
     * If quickFilterRarity is set: simple rarity threshold.
     * Otherwise: delegate to active profile.
     */
    public FilterAction evaluate(GearData gearData, EquipmentType equipmentType) {
        // Quick filter mode — simple rarity threshold
        if (quickFilterRarity != null) {
            return gearData.rarity().ordinal() >= quickFilterRarity.ordinal()
                ? FilterAction.ALLOW : FilterAction.BLOCK;
        }

        // Custom profile mode
        Optional<FilterProfile> profile = getActiveProfile();
        if (profile.isEmpty()) return FilterAction.ALLOW;
        return profile.get().evaluate(gearData, equipmentType);
    }

    public boolean hasActiveFilter() {
        return quickFilterRarity != null || getActiveProfile().isPresent();
    }

    // --- Query methods ---

    public Optional<FilterProfile> getActiveProfile() {
        if (activeProfileId == null) return Optional.empty();
        return profiles.stream()
            .filter(p -> p.getId().equals(activeProfileId))
            .findFirst();
    }

    public Optional<FilterProfile> getProfileByName(String name) { ... }
    public Optional<FilterProfile> getProfileById(String id) { ... }
    public int getProfileCount() { ... }
    public boolean isUsingQuickFilter() { return quickFilterRarity != null; }

    // --- Immutable update methods ---

    public PlayerFilterState withFilteringEnabled(boolean enabled) { ... }
    public PlayerFilterState withActiveProfileId(String id) { ... }
    public PlayerFilterState withQuickFilterRarity(GearRarity rarity) { ... }
    // Setting quickFilterRarity clears activeProfileId (mutually exclusive)
    // Setting activeProfileId clears quickFilterRarity (mutually exclusive)
    public PlayerFilterState withAddedProfile(FilterProfile profile) { ... }
    public PlayerFilterState withRemovedProfile(String profileId) { ... }
    public PlayerFilterState withUpdatedProfile(FilterProfile updated) { ... }
    // withUpdatedProfile replaces the profile with matching id

    // --- Builder ---
    public static Builder builder() { ... }
    public Builder toBuilder() { ... }

    public static final class Builder { ... }
}
```

**Quick filter vs. custom profiles are mutually exclusive**:
- Setting `quickFilterRarity` clears `activeProfileId`
- Setting `activeProfileId` clears `quickFilterRarity`
- This prevents confusion — only one mode is active at a time
- `/lf status` clearly shows which mode is in use

**Edge cases handled by `withRemovedProfile()`**:
- If the removed profile was the active profile → sets `activeProfileId` to null
- If the removed profile was the only profile → state becomes empty (no profiles)

**Edge cases handled by `withActiveProfileId()`**:
- If the ID doesn't match any profile → throws IllegalArgumentException
- If null → clears active profile (filtering still "enabled" but no profile to evaluate = all items pass through)

---

## 4. Database Schema

Add to `src/main/resources/db/schema.sql`:

```sql
-- Loot Filter Data
CREATE TABLE IF NOT EXISTS rpg_loot_filters (
    uuid VARCHAR(36) PRIMARY KEY,
    filter_data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uuid) REFERENCES rpg_players(uuid) ON DELETE CASCADE
);
```

**Design rationale**: Single row per player with JSON blob. Not normalized because:
- Filter data is only ever loaded/saved as a complete unit (never query individual rules)
- Avoids 4-level join: `loot_filters → profiles → rules → conditions`
- Matches existing pattern (`rpg_skill_tree.allocated_nodes` = JSON Set)
- Simplifies the repository (one get, one upsert)
- Max JSON size for a player with 10 profiles × 50 rules × 50 conditions ≈ ~1MB worst case — well within TEXT column limits (realistically never approached)

---

## 5. Repository

```
File: LootFilterRepository.java
Package: io.github.larsonix.trailoforbis.lootfilter.repository
```

### Gson Serialization

The `FilterCondition` sealed interface needs a custom Gson `TypeAdapter` because Gson doesn't natively handle sealed types or polymorphic records.

```
File: FilterConditionTypeAdapter.java
```

**Serialization format** (one example per condition type):
```json
{"type": "MIN_RARITY", "threshold": "RARE"}
{"type": "MAX_RARITY", "threshold": "EPIC"}
{"type": "EQUIPMENT_SLOT", "slots": ["weapon", "head"]}
{"type": "WEAPON_TYPE", "types": ["SWORD", "STAFF", "DAGGER"]}
{"type": "ARMOR_MATERIAL", "materials": ["LEATHER", "PLATE"]}
{"type": "ITEM_LEVEL_RANGE", "min": 13, "max": 16}
{"type": "QUALITY_RANGE", "min": 80, "max": 101}
{"type": "REQUIRED_MODIFIERS", "modifierIds": ["sharp", "max_health"], "minCount": 1}
{"type": "MODIFIER_VALUE_RANGE", "modifierId": "crit_chance", "minValue": 15.0, "maxValue": 100.0}
{"type": "IMPLICIT_CONDITION", "minPercentile": 0.8, "damageTypes": ["physical_damage"]}
{"type": "MIN_MODIFIER_COUNT", "count": 4}
{"type": "CORRUPTION_STATE", "filter": "NOT_CORRUPTED"}
```

**Adapter approach**: Read `type` field first → switch on `ConditionType` → deserialize remaining fields into the correct record type.

**Full PlayerFilterState JSON structure**:
```json
{
  "playerId": "550e8400-e29b-41d4-a716-446655440000",
  "filteringEnabled": true,
  "quickFilterRarity": null,
  "activeProfileId": "a1b2c3d4-...",
  "lastModified": "2026-03-02T10:30:00Z",
  "profiles": [
    {
      "id": "a1b2c3d4-...",
      "name": "Farming Mode",
      "defaultAction": "BLOCK",
      "createdAt": "2026-03-01T...",
      "lastModified": "2026-03-02T...",
      "rules": [
        {
          "name": "Good leather",
          "enabled": true,
          "action": "ALLOW",
          "conditions": [
            {"type": "ARMOR_MATERIAL", "materials": ["LEATHER"]},
            {"type": "ITEM_LEVEL_RANGE", "min": 13, "max": 16},
            {"type": "REQUIRED_MODIFIERS", "modifierIds": ["max_health"], "minCount": 1}
          ]
        }
      ]
    }
  ]
}
```

**Quick filter state example** (casual player, no profiles):
```json
{
  "playerId": "...",
  "filteringEnabled": true,
  "quickFilterRarity": "RARE",
  "activeProfileId": null,
  "lastModified": "...",
  "profiles": []
}
```

### Repository Class

```java
public final class LootFilterRepository {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final DataManager dataManager;
    private final ConcurrentHashMap<UUID, PlayerFilterState> cache = new ConcurrentHashMap<>();
    private final Gson gson;  // Configured with FilterConditionTypeAdapter

    // --- Public API ---

    public Optional<PlayerFilterState> get(UUID uuid);
    // 1. Check cache
    // 2. If miss, load from DB
    // 3. Cache result
    // 4. Return

    public PlayerFilterState getOrCreate(UUID uuid);
    // Returns cached/DB state, or creates empty state (no profiles, filtering disabled)

    public void save(PlayerFilterState state);
    // 1. Update cache
    // 2. UPSERT to DB (serialized JSON)

    public void saveAll();
    // Batch save all cached entries (called on shutdown)
    // Uses transaction: disable autocommit → batch addBatch → executeBatch → commit

    public void evict(UUID uuid);
    // Remove from cache only (called on player disconnect AFTER save)

    public void clearCache();
    // Called on shutdown after saveAll()

    // --- SQL ---
    // H2:         MERGE INTO rpg_loot_filters (uuid, filter_data, last_modified) KEY(uuid) VALUES (?, ?, ?)
    // MySQL:      INSERT INTO rpg_loot_filters (...) VALUES (?, ?, ?)
    //             ON DUPLICATE KEY UPDATE filter_data = VALUES(filter_data), last_modified = VALUES(last_modified)
    // PostgreSQL: INSERT INTO rpg_loot_filters (...) VALUES (?, ?, ?)
    //             ON CONFLICT (uuid) DO UPDATE SET filter_data = EXCLUDED.filter_data, last_modified = EXCLUDED.last_modified
}
```

**Thread safety**: `ConcurrentHashMap` for cache. Gson is thread-safe for serialization. Database writes use connection-per-operation from HikariCP pool. No additional synchronization needed.

---

## 6. Config

```
File: src/main/resources/config/loot-filter.yml
```

```yaml
loot-filter:
  # Master switch for entire loot filter system
  enabled: true

  # Per-player limits
  max-profiles-per-player: 10
  max-rules-per-profile: 50
  max-conditions-per-rule: 50

  # Defaults for new player states
  defaults:
    # What happens when no rule matches in a new profile
    default-action: ALLOW
    # New players start with filtering off
    filtering-enabled: false

  # Feedback when items are blocked
  feedback:
    # none / chat / action-bar
    mode: chat
    # every = log each blocked item / summary = periodic count
    detail: summary
    # Seconds between summary messages (only used when detail: summary)
    summary-interval: 5

  # Starter presets — players can copy these as starting profiles
  presets:
    - name: "Block Common"
      default-action: ALLOW
      rules:
        - name: "Hide Common junk"
          action: BLOCK
          conditions:
            - type: MAX_RARITY
              threshold: COMMON

    - name: "Rare+ Only"
      default-action: BLOCK
      rules:
        - name: "Keep Rare and above"
          action: ALLOW
          conditions:
            - type: MIN_RARITY
              threshold: RARE

    - name: "Epic+ Endgame"
      default-action: BLOCK
      rules:
        - name: "Keep Epic and above"
          action: ALLOW
          conditions:
            - type: MIN_RARITY
              threshold: EPIC
```

```
File: LootFilterConfig.java
Package: io.github.larsonix.trailoforbis.lootfilter.config
```

Standard YAML config bean loaded by ConfigManager:

```java
public final class LootFilterConfig {
    private boolean enabled = true;
    private int maxProfilesPerPlayer = 10;
    private int maxRulesPerProfile = 50;
    private int maxConditionsPerRule = 50;
    private FilterAction defaultAction = FilterAction.ALLOW;
    private boolean defaultFilteringEnabled = false;

    // Feedback settings
    private String feedbackMode = "chat";       // "none", "chat", "action-bar"
    private String feedbackDetail = "summary";  // "every", "summary"
    private int feedbackSummaryInterval = 5;    // seconds

    // Presets loaded from YAML into FilterProfile objects at init time
    private List<PresetConfig> presets = new ArrayList<>();

    // Getters + setters (ConfigManager uses reflection/SnakeYAML bean mapping)

    public static final class PresetConfig {
        private String name;
        private String defaultAction;
        private List<PresetRuleConfig> rules;
        // Getters + setters

        /** Convert this config preset into a FilterProfile (with a fresh UUID). */
        public FilterProfile toProfile() { ... }
    }

    public static final class PresetRuleConfig {
        private String name;
        private String action;
        private List<Map<String, Object>> conditions;
        // Parsed into FilterCondition objects via ConditionType discriminator
    }
}
```

---

## 7. Equipment Type Resolution

```
File: EquipmentTypeResolver.java
Package: io.github.larsonix.trailoforbis.lootfilter.evaluation
```

The pickup system receives an `ItemStack` and `GearData`. We need to determine the `EquipmentType` for condition evaluation. `GearData` stores `baseItemId` (the original Hytale item ID before custom ID assignment, e.g., `"Weapon_Sword_Iron"`).

```java
public final class EquipmentTypeResolver {

    public static EquipmentType resolve(GearData gearData) {
        String baseItemId = gearData.baseItemId();
        if (baseItemId == null || baseItemId.isEmpty()) {
            return EquipmentType.UNKNOWN;
        }

        // Try weapon type resolution first
        Optional<WeaponType> weaponType = WeaponType.fromItemId(baseItemId);
        if (weaponType.isPresent()) {
            return EquipmentType.resolve(weaponType.get(), null, null);
        }

        // Try armor resolution
        Optional<ArmorMaterial> material = ArmorMaterial.fromItemId(baseItemId);
        if (material.isPresent()) {
            ArmorSlot slot = resolveArmorSlot(baseItemId);
            return EquipmentType.resolve(null, material.get(), slot);
        }

        return EquipmentType.UNKNOWN;
    }

    private static EquipmentType.ArmorSlot resolveArmorSlot(String itemId) {
        String lower = itemId.toLowerCase();
        if (lower.contains("head") || lower.contains("helmet")) return ArmorSlot.HEAD;
        if (lower.contains("chest")) return ArmorSlot.CHEST;
        if (lower.contains("legs") || lower.contains("legging")) return ArmorSlot.LEGS;
        if (lower.contains("hands") || lower.contains("gauntlet") || lower.contains("glove")) return ArmorSlot.HANDS;
        return null;
    }
}
```

**Key dependency**: Uses existing `WeaponType.fromItemId()` and `ArmorMaterial.fromItemId()` static methods which parse item ID string patterns. These already exist and are well-tested.

**Need to verify**: Does `EquipmentType.resolve()` already exist as a static method, or do we need to add a slot name helper? The exploration revealed `EquipmentType` has category/slot fields but we need to confirm the exact static factory API.

---

## 8. LootFilterManager

```
File: LootFilterManager.java
Package: io.github.larsonix.trailoforbis.lootfilter
```

Central manager class. Follows the existing Manager pattern (constructed in `start()`, `initialize()` for config-dependent setup, `shutdown()` for cleanup).

```java
public final class LootFilterManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LootFilterRepository repository;
    private final LootFilterConfig config;

    // Constructor: receives DataManager + LootFilterConfig
    // Creates LootFilterRepository internally

    // === Lifecycle ===

    public void initialize();
    // Called after construction. Logs startup message.

    public void shutdown();
    // Saves all cached data, clears cache, logs shutdown.

    // === Player Lifecycle ===

    public void onPlayerJoin(UUID playerId);
    // Pre-loads player's filter state into cache.
    // Called from PlayerReadyEvent handler.

    public void onPlayerDisconnect(UUID playerId);
    // Saves state to DB, then evicts from cache.
    // Called from PlayerDisconnectEvent handler.

    // === Fast-Path Evaluation (called on every pickup) ===

    public boolean isFilteringEnabled(UUID playerId);
    // Quick check: is there an active filter for this player?
    // Returns false if: system disabled, player has no state, filtering off, no active filter.
    // "Active filter" = quickFilterRarity is set OR activeProfileId is set.

    public FilterAction evaluate(UUID playerId, GearData gearData, EquipmentType equipmentType);
    // Full evaluation path. Called ONLY if isFilteringEnabled() returned true.
    // Delegates to PlayerFilterState.evaluate() which handles both quick and custom modes.
    // Returns ALLOW if anything goes wrong (fail-open).

    // === Quick Filter (casual players) ===

    public void setQuickFilter(UUID playerId, GearRarity minRarity);
    // Sets quick filter mode. Enables filtering automatically.
    // Clears activeProfileId (mutually exclusive with custom profiles).

    public void clearQuickFilter(UUID playerId);
    // Disables quick filter mode without disabling filtering entirely.
    // Player reverts to custom profile mode (or no filter if no profile active).

    // === Profile Management (called from UI and commands) ===

    public PlayerFilterState getState(UUID playerId);
    // Returns current state from cache. Creates empty if missing.

    public void saveState(PlayerFilterState state);
    // Validates limits (max profiles, max rules, max conditions) → saves to repository.

    public void toggleFiltering(UUID playerId);
    // Flips filteringEnabled boolean.

    public void setFilteringEnabled(UUID playerId, boolean enabled);
    // Explicit set.

    public void setActiveProfile(UUID playerId, String profileId);
    // Switches active profile. Validates profile exists.
    // Clears quickFilterRarity (mutually exclusive with quick filter).

    public void createProfile(UUID playerId, String name);
    // Creates empty profile with given name.
    // Validates max-profiles-per-player limit.

    public void createProfileFromPreset(UUID playerId, String presetName);
    // Copies a config preset into the player's profile list.
    // Validates preset exists and max-profiles-per-player limit.

    public void deleteProfile(UUID playerId, String profileId);
    // Removes profile. Clears activeProfileId if it was the active one.

    public void saveProfile(UUID playerId, FilterProfile updated);
    // Replaces existing profile (matched by id) in state.
    // Validates max-rules-per-profile and max-conditions-per-rule limits.

    // === Presets ===

    public List<String> getPresetNames();
    // Returns names of available presets from config.

    public Optional<FilterProfile> getPreset(String name);
    // Returns a preset by name, instantiated as a FilterProfile with a fresh UUID.

    // === Config Access ===

    public LootFilterConfig getConfig();
    // For UI to read limits.

    public boolean isSystemEnabled();
    // Master switch from config.
}
```

**Fail-open design**: If any error occurs during evaluation (null state, missing profile, exception), return `FilterAction.ALLOW`. Never accidentally prevent pickup due to a bug.

**Performance**: The hot path is `isFilteringEnabled()` + `evaluate()`. Both are pure cache reads (ConcurrentHashMap lookup) + in-memory object traversal. No DB, no allocations, no I/O. Expected cost: <1μs for typical profiles.

---

## 9. Block Feedback System

```
File: BlockFeedbackService.java
Package: io.github.larsonix.trailoforbis.lootfilter.feedback
```

Provides player feedback when items are blocked by their filter. Configured via `loot-filter.yml`.

```java
public final class BlockFeedbackService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LootFilterConfig config;

    // Per-player block counters for summary mode
    private final ConcurrentHashMap<UUID, AtomicInteger> blockCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastSummaryTime = new ConcurrentHashMap<>();

    // === Called from LootFilterPickupSystem when an item is blocked ===

    public void onItemBlocked(UUID playerId, GearData gearData, PlayerRef playerRef) {
        if (config.getFeedbackMode().equals("none")) return;

        if (config.getFeedbackDetail().equals("every")) {
            // Immediate per-item feedback
            String msg = formatBlockMessage(gearData);
            sendFeedback(playerRef, msg);
        } else {
            // Summary mode — accumulate count, send periodically
            blockCounts.computeIfAbsent(playerId, k -> new AtomicInteger(0)).incrementAndGet();
            maybeSendSummary(playerId, playerRef);
        }
    }

    private void maybeSendSummary(UUID playerId, PlayerRef playerRef) {
        long now = System.currentTimeMillis();
        long last = lastSummaryTime.getOrDefault(playerId, 0L);
        int intervalMs = config.getFeedbackSummaryInterval() * 1000;

        if (now - last >= intervalMs) {
            int count = blockCounts.getOrDefault(playerId, new AtomicInteger(0)).getAndSet(0);
            if (count > 0) {
                sendFeedback(playerRef, "[Filter] Blocked " + count + " item" + (count > 1 ? "s" : ""));
                lastSummaryTime.put(playerId, now);
            }
        }
    }

    private void sendFeedback(PlayerRef playerRef, String message) {
        switch (config.getFeedbackMode()) {
            case "chat" -> ChatUtils.sendMessage(playerRef, message);
            case "action-bar" -> ChatUtils.sendActionBar(playerRef, message);
            // "none" handled above
        }
    }

    private String formatBlockMessage(GearData gearData) {
        // e.g., "[Filter] Blocked: Common Iron Sword (Lv3)"
        return "[Filter] Blocked: " + gearData.rarity().displayName() + " "
            + gearData.displayName() + " (Lv" + gearData.level() + ")";
    }

    public void onPlayerDisconnect(UUID playerId) {
        blockCounts.remove(playerId);
        lastSummaryTime.remove(playerId);
    }
}
```

**Why summary mode is default**: In a dense realm run, blocking 20+ items per second would spam chat. Summary mode ("Blocked 47 items in the last 5s") is informative without being overwhelming. Players who want per-item detail can switch to `every` mode.

---

## 10. ECS Event Handler

```
File: LootFilterPickupSystem.java
Package: io.github.larsonix.trailoforbis.lootfilter.system
```

### Prerequisite: FilteredPickupInteraction

The `LootFilterPickupSystem` only works because `FilteredPickupInteraction` (Section 2) routes RPG gear pickup through `ItemUtils.interactivelyPickupItem()`, which dispatches `InteractivelyPickupItemEvent`. Without that custom interaction, this event never fires for ground items.

```
File: FilteredPickupInteraction.java
Package: io.github.larsonix.trailoforbis.lootfilter.system
```

Extends `SimpleInstantInteraction`. Registered as `"*TrailOfOrbis_FilteredPickup"` via `InteractionModule`. Set as the `Pickup` interaction on all RPG gear item definitions in `ItemSyncService`. See Section 2 for full implementation.

### Event System Pattern

Following `CraftingConversionSystem` pattern — extends `EntityEventSystem<EntityStore, InteractivelyPickupItemEvent>`.

```java
public final class LootFilterPickupSystem
    extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LootFilterManager filterManager;
    private final BlockFeedbackService feedbackService;

    public LootFilterPickupSystem(LootFilterManager filterManager,
                                   BlockFeedbackService feedbackService) {
        super(InteractivelyPickupItemEvent.class);
        this.filterManager = filterManager;
        this.feedbackService = feedbackService;
    }

    @Override
    public void handle(
            int index,
            ArchetypeChunk<EntityStore> archetypeChunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            InteractivelyPickupItemEvent event) {

        // 1. Already cancelled by another system? Skip.
        if (event.isCancelled()) return;

        // 2. Only filter RPG gear
        ItemStack itemStack = event.getItemStack();
        if (!GearUtils.isRpgGear(itemStack)) return;

        // 3. Get player UUID from the entity that triggered the event
        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID playerId = playerRef.getUuid();

        // 4. Quick check: is filtering enabled for this player?
        if (!filterManager.isFilteringEnabled(playerId)) return;

        // 5. Read gear data from the item
        Optional<GearData> gearOpt = GearUtils.getGear(itemStack);
        if (gearOpt.isEmpty()) return;
        GearData gearData = gearOpt.get();

        // 6. Resolve equipment type
        EquipmentType equipType = EquipmentTypeResolver.resolve(gearData);

        // 7. Evaluate filter
        FilterAction action = filterManager.evaluate(playerId, gearData, equipType);

        // 8. Block pickup if needed
        if (action == FilterAction.BLOCK) {
            event.setCancelled(true);
            // The item stays on the ground — InteractivelyPickupItemEvent cancellation
            // causes ItemUtils to re-drop the item instead of adding to inventory

            // 9. Notify player
            feedbackService.onItemBlocked(playerId, gearData, playerRef);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Match entities that have PlayerRef component (players picking up items)
        return Query.of(PlayerRef.getComponentType());
    }
}
```

### Registration

In `TrailOfOrbis.start()`:
```java
getEntityStoreRegistry().registerSystem(
    new LootFilterPickupSystem(lootFilterManager, blockFeedbackService));
```

### Thread Safety

- `handle()` runs on the world tick thread (same thread as all other ECS systems)
- `filterManager.isFilteringEnabled()` and `filterManager.evaluate()` read from `ConcurrentHashMap` — safe for concurrent reads
- No writes happen during evaluation — filter state is only modified via UI/commands (which also run on the world thread for player-initiated actions)

---

## 11. Commands

```
File: LfCommand.java
Package: io.github.larsonix.trailoforbis.lootfilter.command
```

Following `StatsShortcutCommand` pattern:

```java
public final class LfCommand extends AbstractPlayerCommand {

    private final LootFilterManager filterManager;

    public LfCommand(LootFilterManager filterManager) {
        super("lf", "Loot filter controls");
        this.addAliases("lootfilter");
        this.filterManager = filterManager;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
                           Ref<EntityStore> ref, PlayerRef player, World world) {
        String[] args = context.getArgs();

        if (args.length == 0) {
            openFilterUI(player, store, ref);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "toggle" -> handleToggle(player);
            case "on"     -> handleEnable(player);
            case "off"    -> handleDisable(player);
            case "quick"  -> handleQuickFilter(player, args);
            case "switch" -> handleSwitch(player, args);
            case "list"   -> handleList(player);
            case "status" -> handleStatus(player);
            case "test"   -> handleTest(player, store, ref);
            case "preset" -> handlePreset(player, args);
            default       -> sendUsage(player);
        }
    }
}
```

### Command Responses

**`/lf`** — Opens the Loot Filter UI page (delegates to UIService)

**`/lf toggle`**
```
[Loot Filter] Filtering enabled. (Active: "Farming Mode", 5 rules)
[Loot Filter] Filtering disabled.
```

**`/lf on` / `/lf off`**
```
[Loot Filter] Filtering enabled.
[Loot Filter] Filtering disabled.
```

**`/lf quick <rarity>`** — Quick filter for casual players
```
/lf quick rare
[Loot Filter] Quick filter: blocking everything below Rare.

/lf quick epic
[Loot Filter] Quick filter: blocking everything below Epic.

/lf quick off
[Loot Filter] Quick filter disabled.

/lf quick
[Loot Filter] Usage: /lf quick <common|uncommon|rare|epic|legendary|off>
```
Sets the quick filter rarity threshold. Automatically enables filtering. Mutually exclusive with custom profiles — switching to quick filter deactivates any active profile, and vice versa.

**`/lf switch <name>`** — Switch to a custom profile (disables quick filter)
```
[Loot Filter] Switched to profile "Farming Mode". (5 rules, default: BLOCK)
[Loot Filter] No profile found with name "xyz". Use /lf list to see profiles.
```

**`/lf list`** — Shows all profiles with human-readable rule summaries
```
[Loot Filter] Your profiles:
  > Farming Mode (5 rules) [ACTIVE]
      #1 ALLOW: Epic or better, Leather, Chest/Legs, Level 13–16, Has: max_health
      #2 ALLOW: Rare or better, Sword/Dagger, crit_chance ≥ 15
      ...
    Boss Run (3 rules)
    Full Loot (0 rules)
```
Uses `FilterRule.describeSummary()` to show a one-line description of each rule in the active profile.

**`/lf status`**
```
[Loot Filter] Status: ENABLED (Quick Filter: Rare+)
  Blocking everything below Rare.
```
or
```
[Loot Filter] Status: ENABLED (Profile: "Farming Mode")
  5 rules, default: BLOCK
```
or
```
[Loot Filter] Status: DISABLED
  No active filter.
```

**`/lf test`** — Test filter against held item
```
/lf test    ← while holding an RPG gear item

[Loot Filter] Testing: Epic Leather Chest (Lv15, Quality 82, 4 mods)
  Rule #1 "Good leather":
    ✓ Epic or better
    ✓ Leather
    ✓ Chest, Legs
    ✓ Level 13–16
    ✓ Has: max_health
    → ALLOWED by rule #1

/lf test    ← with a blocked item

[Loot Filter] Testing: Common Iron Sword (Lv3, Quality 12, 1 mod)
  Rule #1 "Good leather":
    ✓ Common or better (any rarity passes MinRarity check... wait)
    ✗ Leather (Sword ≠ Leather)
  Rule #2 "Endgame staffs":
    ✗ Sword, Dagger (Sword ∉ {Staff})
  No rule matched → BLOCKED (default action)

/lf test    ← with quick filter active

[Loot Filter] Testing: Common Iron Sword (Lv3)
  Quick filter: Rare+ → BLOCKED (Common < Rare)

/lf test    ← not holding an RPG item

[Loot Filter] You must be holding an RPG gear item to test.
```
Uses `FilterProfile.evaluateWithTrace()` to show exactly which rules were checked and why the item passed or failed each one. Essential for debugging complex filters.

**`/lf preset <name>`** — Copy a preset into your profiles
```
/lf preset "Rare+ Only"
[Loot Filter] Created profile "Rare+ Only" from preset. Use /lf switch "Rare+ Only" to activate.

/lf preset
[Loot Filter] Available presets: Block Common, Rare+ Only, Epic+ Endgame

/lf preset "nonexistent"
[Loot Filter] Unknown preset. Available: Block Common, Rare+ Only, Epic+ Endgame
```

### Command Registration

In `TrailOfOrbis.start()`:
```java
getCommandRegistry().registerCommand(new LfCommand(lootFilterManager));
```

---

## 12. UI Design

```
File: LootFilterPage.java
Package: io.github.larsonix.trailoforbis.lootfilter.ui
```

### UX Research Summary

Based on analysis of Last Epoch, PoE/FilterBlade, Grim Dawn, Torchlight Infinite, and Diablo 4:

1. **Presets-first onboarding** — FilterBlade's strictness slider serves 90% of users. Empty state kills adoption.
2. **Progressive disclosure** — Quick filter (1 step) → Presets (2 steps) → Custom rules (advanced). Each tier is a valid endpoint.
3. **Test mode is essential** — Immediate visual feedback when tweaking rules.
4. **Rule ordering must be explicit** — Numbered list with up/down controls. No ambiguity.
5. **AND logic must be stated clearly** — "ALL conditions must match" visible in rule editor.
6. **No external tools ever** — Everything in-game.

### Access Points

**1. Commands**: `/lf`, `/lootfilter` — opens the filter page directly.

**2. Persistent navigation button on all RPG pages**: Add a "Filter" button to the footer navigation row of StatsPage, AttributePage, and the Loot Filter page itself (for symmetry).

Current footer pattern (3 buttons, 210×53px each, 20px gaps):
```
[Close]  [Attributes/Stats]  [Skill Tree]
```

Updated footer (4 buttons, narrowed to 160×53px to fit):
```
[Close]  [Stats/Attr]  [Skill Tree]  [Filter]
```

Total width: 160×4 + 20×3 = 700px (fits within all page widths).

This means modifying `StatsPage.buildNavigationFooter()`, `AttributePage.buildNavigationFooter()`, and the SkillTree page navigation (if applicable). The "Filter" button opens `LootFilterPage` via `UIService.openLootFilterPage()`.

**On the LootFilterPage itself**, the footer mirrors the pattern:
```
[Close]  [Stats]  [Attributes]  [Skill Tree]
```
(Replaces "Filter" with other pages since we're already on it.)

### Page Architecture

Single HyUI page with 3 view states (not traditional tabs — the Home view is the landing page, and the user drills into Rules/Edit Rule for a specific profile):

```
Home (default)  →  Rules (for a profile)  →  Edit Rule (single rule)
     ↑                    ↑                         |
     └────────────────────┴─────────────────────────┘
                    ← Back navigation
```

Container: 750px wide, height varies by view (500–650px). Uses `decorated-container` with dynamic height via `container.withAnchor()`.

### View 1: Home (Default Landing Page)

This is what the player sees first. **Presets and quick filter are above the fold. Profile management is below.**

```
┌─────────────────────────────────────────────────────────┐
│  Loot Filter                                       [X]  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ── Quick Filter ────────────────────────────────────   │
│                                                         │
│  Block everything below:                                │
│  [OFF] [Uncommon] [Rare] [Epic] [Legendary]             │
│                              ↑ currently selected       │
│                                                         │
│  ── Start From a Preset ─────────────────────────────   │
│                                                         │
│  [Block Common]  [Rare+ Only]  [Epic+ Endgame]         │
│                                                         │
│  ── Your Profiles ───────────────────────────────────   │
│                                                         │
│  ┌─ Farming Mode ─────────── 5 rules ── ACTIVE ─────┐  │
│  │  Default: BLOCK              [Edit] [Activate] [X]│  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌─ Boss Run ─────────────── 3 rules ────────────────┐  │
│  │  Default: BLOCK              [Edit] [Activate] [X]│  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌─ Full Loot ────────────── 0 rules ────────────────┐  │
│  │  Default: ALLOW              [Edit] [Activate] [X]│  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│              [+ New Profile]  [+ From Preset]           │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  Filtering: [ON]  [OFF]                                 │
├─────────────────────────────────────────────────────────┤
│  [Close]    [Stats]    [Attributes]    [Skill Tree]     │
└─────────────────────────────────────────────────────────┘
```

#### Home View Details

**Quick Filter Section**:
- Row of 5 `small-secondary-button` toggle buttons: OFF, Uncommon, Rare, Epic, Legendary
- Selected button highlighted with bright background (`#3a6a3a` green-ish), others dim (`#1a1a2e`)
- Selecting any rarity automatically enables filtering and sets `quickFilterRarity`
- Selecting "OFF" clears the quick filter (reverts to profile mode or no filter)
- If a custom profile is active, the quick filter row shows "OFF" selected and a note: "Using profile: Farming Mode"
- **Mutually exclusive**: selecting a quick filter rarity deactivates any custom profile, selecting a profile deactivates quick filter

**Presets Section**:
- 3 `small-secondary-button` buttons, one per config preset
- Click → creates a personal copy of the preset and opens the Rules view for it
- Only shown if player has < max profiles (hide when at limit)
- Subtle description below: "Creates a copy you can customize"

**Your Profiles Section**:
- Scrollable list if > 4 profiles (use `TopScrolling` layout)
- Each profile row:
  - Name (bold-ish, larger font) + rule count + "ACTIVE" badge (green) if active
  - Default action label: "Default: BLOCK" in red or "Default: ALLOW" in green
  - [Edit] → switches to Rules view for this profile
  - [Activate] → sets as active profile, clears quick filter, refreshes page
  - [X] → delete (with inline confirmation: row transforms to "Delete? [Yes] [No]")
- Active profile has a distinct row background (`#1a2a1a` dark green tint)
- [+ New Profile] creates empty profile named "Filter N"

**Footer**:
- Filtering ON/OFF: Two `small-secondary-button` toggles. Green when ON, dim when OFF.
- This is the global `filteringEnabled` toggle — separate from quick filter/profile selection.

**Empty State** (new player, no profiles, no quick filter):
- Quick filter section still visible (always)
- Presets section still visible (always)
- "Your Profiles" section shows: "No profiles yet. Try a quick filter above, or start from a preset!"
- This ensures the landing page is NEVER empty

### View 2: Rules (for a specific profile)

Reached by clicking [Edit] on a profile in the Home view.

```
┌─────────────────────────────────────────────────────────┐
│  Loot Filter › Farming Mode                        [X]  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Default action: [ALLOW] [BLOCK]                        │
│                                                         │
│  ── Rules (first match wins) ────────────────────────   │
│                                                         │
│  #1 ● Good leather                            ALLOW    │
│     Leather, Chest/Legs, Level 13–16, Has: max_health   │
│                                 [Edit] [▲] [▼] [X]     │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   │
│  #2 ● Endgame staffs                          ALLOW    │
│     Staff, Lvl 17–20, Rare+, Quality 80+               │
│                                 [Edit] [▲] [▼] [X]     │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   │
│  #3 ● Top implicit weapons                    ALLOW    │
│     Implicit: Physical, roll ≥ 80%                      │
│                                 [Edit] [▲] [▼] [X]     │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   │
│  #4 ○ Disabled rule                           BLOCK    │
│     Common only                                         │
│                                 [Edit] [▲] [▼] [X]     │
│                                                         │
│            [+ Add Rule]                                 │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  [← Back]                                               │
├─────────────────────────────────────────────────────────┤
│  [Close]    [Stats]    [Attributes]    [Skill Tree]     │
└─────────────────────────────────────────────────────────┘
```

#### Rules View Details

**Header breadcrumb**: "Loot Filter › Farming Mode" — shows which profile is being edited.

**Default action toggle**: Two `small-secondary-button` buttons. ALLOW in green when selected, BLOCK in red when selected. Changes are saved immediately (no separate save step for default action).

**Rule list**:
- Scrollable via `TopScrolling` when > 5-6 rules
- Each rule row has two lines:
  - **Line 1**: `#N` (number) + `●`/`○` (enabled/disabled dot) + rule name + action (ALLOW green / BLOCK red)
  - **Line 2**: Condition summary from `FilterRule.describeSummary()` (truncated if very long)
  - **Action buttons**: [Edit] [▲] [▼] [X] — all `small-tertiary-button`
- `●`/`○` dot is a clickable toggle (enable/disable rule without opening editor)
- Clicking the dot toggles `rule.enabled` and refreshes the page
- [▲] / [▼] call `profile.withMovedRule(from, to)` — disabled at top/bottom respectively
- [X] removes rule with inline confirmation ("Delete? [Yes] [No]" replacing the action buttons)
- [Edit] switches to the Edit Rule view for that rule
- [+ Add Rule] creates a new empty rule (named "Rule N") and opens Edit Rule view for it

**Ordering explanation**: The section header "Rules (first match wins)" makes the evaluation model explicit. Players see that rule #1 is checked before #2.

**← Back**: Returns to Home view. All changes to rule order, enabled state, and default action are already saved (immediate-save model).

### View 3: Edit Rule

Reached by clicking [Edit] on a rule in the Rules view, or [+ Add Rule].

```
┌─────────────────────────────────────────────────────────┐
│  Loot Filter › Farming Mode › Rule #1              [X]  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Rule name: Good leather              [Rename]          │
│                                                         │
│  Action: [ALLOW] [BLOCK]       Enabled: [ON] [OFF]     │
│                                                         │
│  ── Conditions (ALL must match) ─────────────────────   │
│                                                         │
│  Armor Material                                  [X]    │
│  [Cloth] [LEATHER] [Plate] [Wood] [Special]             │
│                                                         │
│  Equipment Slot                                  [X]    │
│  [Weapon] [Head] [CHEST] [LEGS] [Hands] [Off-hand]     │
│                                                         │
│  Item Level Range                                [X]    │
│  [- 13 +]  to  [- 16 +]                                │
│                                                         │
│  Required Modifiers (1+ of these)                [X]    │
│  [Damage ▼] [Defense] [Elemental] [Utility]             │
│  □ sharp  □ heavy  ■ max_health  □ dodge                │
│  Min matches: [- 1 +]                                   │
│                                                         │
│  ── Add Condition ───────────────────────────────────   │
│  [+ Rarity] [+ Quality] [+ Weapon Type]                 │
│  [+ Mod Values] [+ Mod Count] [+ Implicit]              │
│  [+ Corruption]                                         │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  [← Back]                              [Save] [Cancel]  │
├─────────────────────────────────────────────────────────┤
│  [Close]    [Stats]    [Attributes]    [Skill Tree]     │
└─────────────────────────────────────────────────────────┘
```

#### Edit Rule View Details

**Breadcrumb**: "Loot Filter › Farming Mode › Rule #1" — full navigation context.

**Rule name**: Displayed as text + [Rename] button. Rename triggers a chat input flow:
1. Player clicks [Rename]
2. Chat message: "[Loot Filter] Type the new rule name in chat:"
3. Player types name
4. Captured via `PlayerChatEvent` with a pending state flag
5. Page refreshes with new name
6. Chat message is cancelled (not broadcast)

**Action toggle**: ALLOW (green bg when selected) / BLOCK (red bg when selected).

**Enabled toggle**: ON (green) / OFF (dim). Disabled rules are skipped during evaluation.

**Conditions section**:
- Header: "Conditions (ALL must match)" — makes AND logic explicit
- Each condition rendered with its type-specific controls (see below)
- [X] button on each condition to remove it
- Changes are held in memory until [Save] or [Cancel]

**Add Condition buttons**:
- Only show condition types NOT already present on the rule (except `ModifierValueRange` and `RequiredModifiers` which can have multiple instances)
- Each button creates the condition with sensible defaults and adds it to the editor
- Condition types grouped by usage (rarity/quality first, then slots/types, then modifiers, then utility)

**Footer**: [← Back] discards changes and returns to Rules view. [Save] validates and saves, returns to Rules view. [Cancel] = same as Back.

#### Condition Type Controls

Each condition type has a specific control layout within the Edit Rule view:

**MinRarity / MaxRarity**:
```
Minimum Rarity                                      [X]
[Common] [Uncommon] [Rare] [EPIC] [Legendary] [Mythic] [Unique]
```
Row of 7 toggle buttons. Selected one highlighted, others dim. Only one can be selected.
Colors match rarity: Common=grey, Uncommon=green, Rare=blue, Epic=purple, Legendary=orange, Mythic=red, Unique=gold.

**EquipmentSlot**:
```
Equipment Slot                                      [X]
[Weapon] [Head] [CHEST] [LEGS] [Hands] [Off-hand]
```
Multi-select toggles. Selected ones highlighted. At least one must be selected (or remove the condition).

**WeaponType**:
```
Weapon Type                                         [X]
[Sword] [Dagger] [Axe] [Mace] [Staff] [Spear]
[Bow] [Crossbow] [Wand] [Longsword] [Greatsword] ...
```
Multi-select toggle grid. Two rows of 6-9 buttons (depends on total weapon count). Selected highlighted.

**ArmorMaterial**:
```
Armor Material                                      [X]
[Cloth] [LEATHER] [Plate] [Wood] [Special]
```
Multi-select toggles. Same pattern as EquipmentSlot.

**ItemLevelRange**:
```
Item Level                                          [X]
Min: [- 13 +]   Max: [- 16 +]
```
Two number inputs using the AttributePage ±1 button pattern. Clamped to 1–MAX_LEVEL.

**QualityRange**:
```
Quality                                             [X]
Min: [- 80 +]   Max: [- 101 +]
```
Same ±1 pattern. Clamped to 1–101. "+5" button for faster adjustment.

**RequiredModifiers** (complex — needs category sub-tabs):
```
Required Modifiers (1+ of these)                    [X]
[Damage] [Defense] [Elemental] [Utility] [Ailment]
□ sharp  □ heavy  ■ max_health  □ dodge  □ regen
□ block  □ evasion ...
Min matches: [- 1 +]
```
- Category tabs filter the modifier grid (e.g., "Damage" shows offensive mods only)
- `■` = selected, `□` = unselected (toggle on click)
- Category assignment derived from modifier stat types in `gear-modifiers.yml`
- "Min matches" defaults to 1 (at least one of the selected mods must be present)
- Grid is scrollable if category has many modifiers

**ModifierValueRange**:
```
Modifier Value                                      [X]
Modifier: [← crit_chance →]
Min value: [- 15 +]   Max value: [- 100 +]
```
- Modifier selector: left/right arrows cycle through known modifier IDs (from `gear-modifiers.yml`)
- Or: button opens a picker list grouped by category (same categories as RequiredModifiers)
- Min/Max value inputs with ±1 and ±5 buttons
- Multiple `ModifierValueRange` conditions can exist on one rule (each for a different modifier)

**ImplicitCondition**:
```
Weapon Implicit                                     [X]
Damage type: [Any] [Physical] [Spell]
Min roll: [- 80 +] %
```
- Damage type: 3-way toggle (Any = empty set, Physical = {"physical_damage"}, Spell = {"spell_damage"})
- Min roll: percentile 0–100 with ±5 steps. 0 = any roll (just "is a weapon")
- Note below: "Filters by how good the base damage roll is (0% = worst, 100% = best)"

**MinModifierCount**:
```
Minimum Modifiers                                   [X]
At least: [- 4 +] modifiers
```
Single number input, clamped 0–6.

**CorruptionState**:
```
Corruption                                          [X]
[Corrupted Only] [Not Corrupted] [Either]
```
3-way exclusive toggle. "Either" is the default (condition has no effect but serves as a placeholder).

### Modifier Category Mapping

For the RequiredModifiers and ModifierValueRange condition pickers in the Edit Rule view, modifiers are grouped into 8 categories. These categories are **statically defined** based on the `stat` field of each modifier in `gear-modifiers.yml`, aligning with the existing `StatCategory` enum used by the stats page (Offense, Defense, Resources, Movement) plus finer-grained splits for filter UX.

#### Category Definitions

```
File: ModifierFilterCategory.java
Package: io.github.larsonix.trailoforbis.lootfilter.model
```

```java
public enum ModifierFilterCategory {
    DAMAGE("Damage", "#f44336"),         // Core + spell + elemental damage
    CRITICAL("Critical", "#ff7043"),      // Crit chance, crit multiplier
    PENETRATION("Penetration", "#e91e63"),// Armor/spell/elemental penetration
    AILMENT("Ailment", "#ab47bc"),        // Ailment application chance + ailment damage + DoT
    DEFENSE("Defense", "#2196f3"),        // Armor, evasion, block, resistances, damage reduction
    RESOURCES("Resources", "#4caf50"),    // Health/mana/stamina pools + regen + recovery
    MOVEMENT("Movement", "#ff9800"),      // All speed stats
    UTILITY("Utility", "#78909c");        // Leech, steal, accuracy, thorns, conversion, signature, conditional

    private final String displayName;
    private final String color;
    // constructor + getters
}
```

#### Complete Modifier-to-Category Mapping (95 modifiers)

Derived from `gear-modifiers.yml` (March 2026). 47 prefixes + 48 suffixes.

**DAMAGE** (16 modifiers) — Raw damage output

| Modifier ID | Display Name | Stat | Type | Slot |
|-------------|-------------|------|------|------|
| `sharp` | Sharp | physical_damage | flat | weapon, hands |
| `heavy` | Heavy | physical_damage_percent | percent | weapon, hands |
| `brutal` | Brutal | melee_damage_percent | percent | weapon, hands |
| `keen` | Keen | projectile_damage_percent | percent | weapon, hands |
| `arcane` | Arcane | spell_damage | flat | weapon, hands |
| `empowered` | Empowered | spell_damage_percent | percent | weapon, hands |
| `blazing` | Blazing | fire_damage | flat | weapon, hands |
| `frozen` | Frozen | water_damage | flat | weapon, hands |
| `shocking` | Shocking | lightning_damage | flat | weapon, hands |
| `chaotic` | Chaotic | void_damage | flat | weapon, hands |
| `earthen` | Earthen | earth_damage | flat | weapon, hands |
| `grounded` | Grounded | earth_damage_percent | percent | weapon, hands |
| `gusting` | Gusting | wind_damage | flat | weapon, hands |
| `tempestuous` | Tempestuous | wind_damage_percent | percent | weapon, hands |
| `elemental` | Elemental | all_elemental_damage_percent | percent | weapon, hands |
| `swift` | Swift | attack_speed_percent | percent | weapon, hands |

**CRITICAL** (2 modifiers) — Crit stats

| Modifier ID | Display Name | Stat | Type | Slot |
|-------------|-------------|------|------|------|
| `precise` | Precise | crit_chance | percent | weapon, hands |
| `deadly` | Deadly | crit_multiplier | percent | weapon, hands |

**PENETRATION** (3 modifiers) — Bypass enemy defenses

| Modifier ID | Display Name | Stat | Type | Slot |
|-------------|-------------|------|------|------|
| `piercing` | Piercing | armor_penetration | flat | weapon, hands |
| `searing` | Searing | fire_penetration | percent | weapon, hands |
| `nullifying` | Nullifying | spell_penetration | flat | weapon, hands |

**AILMENT** (11 modifiers) — Ailment application + ailment damage

| Modifier ID | Display Name | Stat | Type | Slot |
|-------------|-------------|------|------|------|
| `igniting` | Igniting | ignite_chance | percent | weapon |
| `chilling` | Chilling | freeze_chance | percent | weapon |
| `electrifying` | Electrifying | shock_chance | percent | weapon |
| `of_scorching` | of Scorching | burn_damage | flat | weapon |
| `of_frostbite` | of Frostbite | freeze_damage | flat | weapon |
| `of_voltage` | of Voltage | shock_damage | flat | weapon |
| `of_venom` | of Venom | poison_damage | flat | weapon |
| `of_toxicity` | of Toxicity | dot_damage_percent | percent | weapon |
| `of_incineration` | of Incineration | burn_damage_percent | percent | weapon |
| `of_frostburn` | of Frostburn | frost_damage_percent | percent | weapon |
| `of_amplification` | of Amplification | shock_damage_percent | percent | weapon |

**DEFENSE** (21 modifiers) — Damage mitigation, resistances, block

| Modifier ID | Display Name | Stat | Type | Slot |
|-------------|-------------|------|------|------|
| `blocking` | Blocking | passive_block_chance | percent | shield |
| `bulwark` | Bulwark | block_heal_percent | percent | shield |
| `guardian` | Guardian's | shield_effectiveness_percent | percent | shield |
| `ironclad` | Ironclad | block_damage_reduction | percent | shield, armor |
| `enduring` | Enduring | stamina_drain_reduction | percent | shield, armor |
| `immortal` | Immortal | crit_nullify_chance | percent | armor |
| `resilient` | Resilient | critical_reduction | percent | armor |
| `fireproof` | Fireproof | fire_resistance | percent | armor |
| `frostward` | Frostward | water_resistance | percent | armor |
| `insulated` | Insulated | lightning_resistance | percent | armor |
| `voidward` | Voidward | void_resistance | percent | armor |
| `stoneward` | Stoneward | earth_resistance | percent | armor |
| `windward` | Windward | wind_resistance | percent | armor |
| `of_the_fortress` | of the Fortress | armor | flat | armor |
| `of_iron_skin` | of Iron Skin | armor_percent | percent | armor |
| `of_stone` | of Stone | physical_resistance | percent | armor |
| `of_evasion` | of Evasion | evasion | flat | armor |
| `of_the_shadow` | of the Shadow | dodge_chance | percent | armor |
| `of_the_hearth` | of the Hearth | burn_threshold | flat | armor |
| `of_warmth` | of Warmth | freeze_threshold | flat | armor |
| `of_insulation` | of Insulation | shock_threshold | flat | armor |

**RESOURCES** (16 modifiers) — Health/mana/stamina pools, regen, recovery

| Modifier ID | Display Name | Stat | Type | Slot |
|-------------|-------------|------|------|------|
| `of_the_whale` | of the Whale | max_health | flat | armor |
| `of_vitality` | of Vitality | max_health_percent | percent | armor |
| `of_the_sage` | of the Sage | max_mana | flat | armor |
| `of_wisdom` | of Wisdom | max_mana_percent | percent | armor |
| `of_endurance` | of Endurance | max_stamina | flat | armor |
| `of_the_barrier` | of the Barrier | energy_shield | flat | armor |
| `of_regeneration` | of Regeneration | health_regen | flat | armor |
| `of_restoration` | of Restoration | health_regen_percent | percent | armor |
| `of_recovery` | of Recovery | health_recovery_percent | percent | armor |
| `of_the_arcane` | of the Arcane | mana_regen | flat | armor |
| `of_vigor` | of Vigor | stamina_regen | flat | armor |
| `of_stamina` | of Stamina | max_stamina_percent | percent | armor |
| `of_alacrity` | of Alacrity | stamina_regen_percent | percent | armor |
| `of_second_wind` | of Second Wind | stamina_regen_start_delay | percent | armor |
| `of_stability` | of Stability | knockback_resistance | percent | armor |
| `of_fortitude` | of Fortitude | physical_resistance | percent | armor |

**MOVEMENT** (6 modifiers) — Speed stats (legs-only prefixes)

| Modifier ID | Display Name | Stat | Type | Slot |
|-------------|-------------|------|------|------|
| `fleet` | Fleet | movement_speed_percent | percent | legs |
| `striding` | Striding | walk_speed_percent | percent | legs |
| `sprinting` | Sprinting | sprint_speed_bonus | percent | legs |
| `leaping` | Leaping | jump_force_bonus | flat | legs |
| `climbing` | Climbing | climb_speed_bonus | flat | legs |
| `sneaking` | Sneaking | crouch_speed_percent | percent | legs |

**UTILITY** (20 modifiers) — Everything else: leech, steal, accuracy, thorns, conversion, conditional, signature

| Modifier ID | Display Name | Stat | Type | Slot |
|-------------|-------------|------|------|------|
| `thorned` | Thorned | thorns_damage | flat | shield |
| `barbed` | Barbed | thorns_damage_percent | percent | shield |
| `reflecting` | Reflecting | reflect_damage_percent | percent | shield |
| `velocity` | Velocity | projectile_speed_percent | percent | weapon |
| `trajectory` | Trajectory | projectile_gravity_percent | percent | weapon |
| `vampiric` | Vampiric | life_leech | percent | weapon |
| `siphoning` | Siphoning | mana_leech | percent | weapon |
| `of_the_vampire` | of the Vampire | life_steal | percent | weapon |
| `of_the_mana_thief` | of the Mana Thief | mana_steal | percent | weapon |
| `of_efficiency` | of Efficiency | mana_cost_reduction | percent | armor |
| `of_signature_mastery` | of Signature Mastery | signature_energy_max_percent | percent | weapon, hands |
| `of_momentum` | of Momentum | signature_energy_per_hit | flat | weapon, hands |
| `of_accuracy` | of Accuracy | accuracy | flat | weapon, hands |
| `of_focus` | of Focus | accuracy_percent | percent | weapon, hands |
| `of_parrying` | of Parrying | parry_chance | percent | weapon |
| `of_the_cat` | of the Cat | fall_damage_reduction | percent | legs |
| `of_the_berserker` | of the Berserker | damage_at_low_life | percent | weapon |
| `of_execution` | of Execution | execute_damage_percent | percent | weapon |
| `of_hypothermia` | of Hypothermia | damage_vs_frozen_percent | percent | weapon |
| `of_galvanization` | of Galvanization | damage_vs_shocked_percent | percent | weapon |

**Elemental Conversion** (6 suffixes, weapon-only) — placed under UTILITY since they're niche build-defining mods:

| Modifier ID | Display Name | Stat | Slot |
|-------------|-------------|------|------|
| `of_flame_infusion` | of Flame Infusion | fire_conversion | weapon |
| `of_frost_infusion` | of Frost Infusion | water_conversion | weapon |
| `of_storm_infusion` | of Storm Infusion | lightning_conversion | weapon |
| `of_earth_infusion` | of Earth Infusion | earth_conversion | weapon |
| `of_gale_infusion` | of Gale Infusion | wind_conversion | weapon |
| `of_void_infusion` | of Void Infusion | void_conversion | weapon |

#### Category Assignment Logic

Categories are **not auto-derived at runtime**. They are assigned by a static `Map<String, ModifierFilterCategory>` built at initialization time in `LootFilterManager`. The mapping rule is:

```java
// Built from gear-modifiers.yml stat field at startup
private static ModifierFilterCategory categorize(String stat) {
    // Damage: any stat containing "damage" (except DoT/ailment), plus attack_speed
    if (stat.contains("damage") && !stat.contains("burn_") && !stat.contains("freeze_")
        && !stat.contains("shock_") && !stat.contains("poison_") && !stat.contains("dot_")
        && !stat.contains("thorns") && !stat.contains("reflect")
        && !stat.contains("damage_at_") && !stat.contains("damage_vs_")
        && !stat.contains("execute_") && !stat.contains("damage_reduction")
        && !stat.contains("block_damage"))
        return DAMAGE;
    if (stat.equals("attack_speed_percent")) return DAMAGE;

    // Critical
    if (stat.startsWith("crit_") && !stat.contains("nullify") && !stat.contains("reduction"))
        return CRITICAL;

    // Penetration
    if (stat.contains("penetration")) return PENETRATION;

    // Movement
    if (stat.contains("speed") || stat.contains("sprint") || stat.contains("jump")
        || stat.contains("climb") || stat.contains("crouch") || stat.contains("walk")
        || stat.contains("run_speed"))
        return MOVEMENT;

    // Ailment (application + damage)
    if (stat.contains("ignite_") || stat.contains("freeze_chance") || stat.contains("shock_chance")
        || stat.contains("burn_damage") || stat.contains("freeze_damage")
        || stat.contains("shock_damage") || stat.contains("poison_") || stat.contains("dot_")
        || stat.contains("frost_damage") || stat.contains("incineration")
        || stat.contains("amplification"))
        return AILMENT;

    // Resources: pools + regen
    if (stat.startsWith("max_") || stat.contains("regen") || stat.contains("energy_shield")
        || stat.equals("health_recovery_percent") || stat.equals("stamina_regen_start_delay"))
        return RESOURCES;

    // Defense: armor, evasion, block, resistance, threshold, reduction
    if (stat.contains("armor") || stat.contains("evasion") || stat.contains("dodge")
        || stat.contains("block") || stat.contains("resistance") || stat.contains("threshold")
        || stat.equals("physical_resistance") || stat.contains("shield_effectiveness")
        || stat.contains("stamina_drain") || stat.contains("crit_nullify")
        || stat.contains("critical_reduction") || stat.contains("knockback"))
        return DEFENSE;

    // Everything else → Utility
    return UTILITY;
}
```

**Why static, not dynamic**: The stat field alone is insufficient for correct categorization in edge cases (e.g., `physical_resistance` appears on both `of_stone` in DEFENSE and `of_fortitude` in RESOURCES). A static map loaded from config gives us full control. The `categorize()` function above is the **default** — it can be overridden per-modifier in `loot-filter.yml`:

```yaml
loot-filter:
  category-overrides:
    of_stability: RESOURCES    # knockback_resistance is more "survivability" than "armor"
    of_fortitude: RESOURCES    # low-weight phys resist belongs with sustain
    of_the_cat: MOVEMENT       # fall damage reduction is movement-adjacent
```

#### Alignment with Stats Page

The 8 filter categories map cleanly to the 6 `StatCategory` values used by the stats page:

| Filter Category | Stats Page Tab | Rationale |
|-----------------|----------------|-----------|
| DAMAGE | Offense | Direct overlap |
| CRITICAL | Offense | Crit is offense on stats page |
| PENETRATION | Offense | Penetration is offense on stats page |
| AILMENT | Offense | Ailment damage is offense on stats page |
| DEFENSE | Defense | Direct overlap |
| RESOURCES | Base (Resources) | Direct overlap |
| MOVEMENT | Movement | Direct overlap |
| UTILITY | Split across tabs | Leech/steal → Offense, accuracy → Offense, thorns → Defense |

The finer-grained split (8 vs 6) is necessary because the filter picker shows all modifier IDs in a grid, and "Offense" with 50+ modifiers would be unusable. Splitting into Damage/Critical/Penetration/Ailment keeps each tab to 2-20 modifiers.

#### UI Tab Rendering

In the Edit Rule view, the modifier picker renders as:

```
[Damage] [Critical] [Penetration] [Ailment] [Defense] [Resources] [Movement] [Utility]
□ sharp  □ heavy  ■ blazing  □ frozen  □ shocking  □ chaotic  □ earthen  □ gusting
□ arcane □ empowered □ elemental □ swift □ keen □ brutal □ tempestuous □ grounded
```

Each tab button is a `small-tertiary-button` with the category's color as background when selected. The modifier grid below updates to show only modifiers in that category. Selected modifiers (■) are highlighted regardless of which tab is active — switching tabs doesn't deselect modifiers from other categories.

### Page State Machine

```
LootFilterPage instance fields:
  - currentView: HOME | RULES | EDIT_RULE
  - selectedProfileId: String (which profile's rules we're viewing)
  - editingRuleIndex: int (which rule index we're editing, -1 if new)
  - pendingRule: FilterRule (in-memory working copy during editing)
  - pendingConditions: List<FilterCondition> (mutable working list)
  - awaitingChatRename: boolean (waiting for chat input for rule name)
  - deleteConfirmProfileId: String (which profile row shows delete confirmation)
  - deleteConfirmRuleIndex: int (which rule row shows delete confirmation)
```

**Transitions**:
- Home → Rules: click [Edit] on a profile → set `selectedProfileId`, switch to RULES view
- Rules → Edit Rule: click [Edit] on a rule → set `editingRuleIndex`, copy rule to `pendingRule`, switch to EDIT_RULE view
- Rules → Edit Rule (new): click [+ Add Rule] → set `editingRuleIndex = -1`, create empty `pendingRule`, switch to EDIT_RULE view
- Edit Rule → Rules: [Save] or [Cancel] / [← Back]
- Rules → Home: [← Back]
- Any → close: [X] or [Close] footer button

**Save model**:
- **Home view**: Quick filter and profile activation changes save immediately (via `LootFilterManager`)
- **Rules view**: Rule ordering, enable/disable toggles, and default action changes save immediately
- **Edit Rule view**: Changes are held in `pendingRule` until [Save] is clicked. [Cancel] / [← Back] discards.

This split mirrors the "dangerous vs. safe" distinction: reordering rules is safe (can undo with ▲/▼), but editing conditions could leave a rule in a broken state, so we buffer those.

### HyUI Implementation Notes

- All buttons use `secondary-button` or `small-secondary-button` / `small-tertiary-button` classes with inline styling (per HyUI constraint rules)
- Toggle state indicated by `background-color` change (selected: brighter themed color, unselected: `#1a1a2e` dark)
- Labels only use `color` and `font-size` — layout via wrapper `<div>` elements
- Condition summary text from `FilterCondition.describe()` — rendered as `<p>` in wrapper `<div>`
- Page HTML generated dynamically in Java (like StatsPage/AttributePage pattern) — not a static .ui file
- Each view transition calls `ctx.updatePage(true)` to rebuild the HTML
- Container height adjusted via `container.withAnchor(new HyUIAnchor().setWidth(750).setHeight(currentViewHeight))` on view switch
- Profile rows in scrollable `TopScrolling` div when > 4 profiles
- Rule rows in scrollable `TopScrolling` div when > 5 rules
- Condition area in Edit Rule view scrollable when > 3 conditions
- Native scrollbar: `data-hyui-scrollbar-style="Common.ui DefaultScrollbarStyle"`
- Rule name rename via chat: set `awaitingChatRename = true`, register one-shot `PlayerChatEvent` listener, cancel the chat message, update name, refresh page

### Container Dimensions

| View | Width | Height | Scrollable? |
|------|-------|--------|-------------|
| Home (few profiles) | 750 | 500 | No |
| Home (many profiles) | 750 | 600 | Profile list scrolls |
| Rules (few rules) | 750 | 500 | No |
| Rules (many rules) | 750 | 600 | Rule list scrolls |
| Edit Rule (few conditions) | 750 | 550 | No |
| Edit Rule (many conditions) | 750 | 650 | Condition area scrolls |

### Color Palette

| Element | Color | Hex |
|---------|-------|-----|
| Section headers | Gold | `#FFD700` |
| ALLOW action | Green | `#4CAF50` |
| BLOCK action | Red | `#E53935` |
| Active profile badge | Green | `#4CAF50` |
| Active profile row bg | Dark green tint | `#1a2a1a` |
| Selected toggle | Bright themed | `#3a6a3a` (green) or `#6a3a3a` (red) |
| Unselected toggle | Dark | `#1a1a2e` |
| Disabled rule text | Dim grey | `#666666` |
| Enabled rule dot `●` | White | `#ffffff` |
| Disabled rule dot `○` | Grey | `#666666` |
| Condition type label | Light blue | `#96a9be` |
| Rarity colors | Match gear rarity | Per `GearRarity.getColor()` |

### Files Modified for Navigation Button

| File | Change |
|------|--------|
| `ui/stats/StatsPage.java` | Add "Filter" button to nav footer, narrow buttons to 160px |
| `ui/attributes/AttributePage.java` | Add "Filter" button to nav footer, narrow buttons to 160px |
| `sanctum/ui/` (if nav footer exists) | Add "Filter" button |

Button dimensions change from `210×53` to `160×53` across all pages to fit 4 buttons.

---

## 13. Integration Points

### TrailOfOrbis.java — Fields

```java
private LootFilterManager lootFilterManager;
private BlockFeedbackService blockFeedbackService;
```

### TrailOfOrbis.java — start() (after GearManager init, before command registration)

```java
// Loot Filter system
LootFilterConfig lootFilterConfig = configManager.loadConfig("loot-filter.yml", LootFilterConfig.class);
lootFilterManager = new LootFilterManager(dataManager, lootFilterConfig);
lootFilterManager.initialize();
blockFeedbackService = new BlockFeedbackService(lootFilterConfig);

// Register ECS system
getEntityStoreRegistry().registerSystem(
    new LootFilterPickupSystem(lootFilterManager, blockFeedbackService));
```

### TrailOfOrbis.java — Command registration section

```java
getCommandRegistry().registerCommand(new LfCommand(lootFilterManager));
```

### PlayerJoinListener — onPlayerReady()

```java
if (lootFilterManager != null) {
    lootFilterManager.onPlayerJoin(uuid);
}
```

### PlayerJoinListener — onPlayerDisconnect()

```java
if (lootFilterManager != null) {
    lootFilterManager.onPlayerDisconnect(uuid);
}
if (blockFeedbackService != null) {
    blockFeedbackService.onPlayerDisconnect(uuid);
}
```

### TrailOfOrbis.java — shutdown() (in reverse init order)

```java
if (lootFilterManager != null) {
    lootFilterManager.shutdown();
}
```

### UIService / UIManager

Add to `UIService` interface:
```java
void openLootFilterPage(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref);
```

Implement in `UIManager`:
```java
@Override
public void openLootFilterPage(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref) {
    new LootFilterPage(lootFilterManager, player, store, ref).open();
}
```

### Files Modified (summary)

| File | Change |
|------|--------|
| `TrailOfOrbis.java` | Fields + init + command + player events + shutdown |
| `db/schema.sql` | Add `rpg_loot_filters` table |
| `api/services/UIService.java` | Add `openLootFilterPage()` |
| `ui/UIManager.java` | Implement `openLootFilterPage()` |
| `listeners/PlayerJoinListener.java` | Add loot filter join/disconnect hooks |
| `config/loot-filter.yml` | New config file |

---

## 14. Edge Cases & Gotchas

### Custom Pickup Interaction (Critical)
The loot filter depends on `FilteredPickupInteraction` being registered as the `Pickup` interaction on all RPG gear items. **Must verify in-game** that:
1. `ItemDefinitionBuilder` allows setting `Interactions` map entries on custom items
2. `PlayerItemEntityPickupSystem` takes Path A (interaction-based) for items with our custom interaction
3. `InteractivelyPickupItemEvent` fires and `setCancelled(true)` causes re-drop instead of pickup
4. Re-dropped items don't create infinite pickup loops (the 1.5s `pickupDelay` on player-dropped items should prevent this)

If interaction injection is impossible, fall back to `InventoryChangeEvent` (ECS event) post-pickup removal (Section 2, Fallback Strategy). **Updated 2026-03-26**: Event changed from `LivingEntityInventoryChangeEvent` to `InventoryChangeEvent` (ECS event).

### Player Disconnects While Editing UI
- `LootFilterPage` instance holds pending (unsaved) edits
- On disconnect, page instance is GC'd → unsaved changes lost
- This is by design — same as attribute allocation cancellation
- The last-saved state persists in the repository

### Profile Deleted While Active
- `PlayerFilterState.withRemovedProfile()` clears `activeProfileId` if it matches
- `isFilteringEnabled()` returns false when no active profile and no quick filter → all items pass through
- Player sees: filtering was on but now no profile is active → effectively disabled

### Empty Rules List
- Profile with no rules → every item falls through to `defaultAction`
- If `defaultAction = BLOCK` → blocks ALL RPG gear (aggressive but valid)
- If `defaultAction = ALLOW` → allows ALL RPG gear (no-op filter)

### Rule with No Conditions
- Matches everything — acts as catch-all
- Useful for "catch-all BLOCK at the bottom" pattern (like Last Epoch)

### Non-RPG Gear Items
- `GearUtils.isRpgGear()` is checked first in the pickup system
- Non-gear items (vanilla wood, food, etc.) never reach the filter
- This means even with `defaultAction = BLOCK`, vanilla items always pick up normally

### Quick Filter + Profile Mutual Exclusivity
- Setting quick filter clears active profile
- Setting active profile clears quick filter
- `/lf status` always shows which mode is active
- Prevents confusing state where both are "active" but only one evaluates

### Quick Filter with Filtering Disabled
- `/lf quick rare` automatically enables filtering
- `/lf off` disables filtering but preserves the quick filter rarity setting
- `/lf on` re-enables with the preserved quick filter
- This means quick filter "remembers" the rarity across toggle cycles

### Concurrent Modification
- Player opens UI on one device while another plugin modifies their filter → not possible (single-player per UUID)
- Two ECS systems processing the same pickup event → our system checks `event.isCancelled()` first
- State modification during evaluation → impossible (evaluation is read-only on immutable objects)

### Database Limits
- JSON blob max size: TEXT column = unlimited in H2, 65KB in MySQL (use MEDIUMTEXT if needed), unlimited in PostgreSQL
- Worst case: 10 profiles × 50 rules × 50 conditions ≈ ~1MB → safe for TEXT in H2/PostgreSQL, needs MEDIUMTEXT for MySQL (16MB limit)
- Realistically: a "giga complicated" player will have maybe 3 profiles × 20 rules × 5 conditions ≈ ~50KB

### Config Hot-Reload
- Not supported initially. Config is read once on `start()`.
- If needed later: add `/lf reload` admin command that re-reads config

### Performance Under Load
- 100 players, each with active filter, all picking up items simultaneously
- Each evaluation: ~1 ConcurrentHashMap lookup + ~50 rule checks × ~50 condition checks = ~2,500 comparisons max
- All comparisons are enum ordinals, set membership, integer range — O(1) each
- ModifierValueRange: streams over max 6 modifiers per check — still O(1) effectively
- Total: <5μs per evaluation worst case, negligible server impact
- Quick filter mode: ~100ns (single ordinal comparison, no rule iteration)

### Feedback Spam Prevention
- Summary mode batches blocked items into periodic counts
- If player disconnects during a summary interval, pending count is lost (acceptable)
- Action-bar mode auto-clears after Hytale's default display duration

---

## 15. File Inventory

### New Files (~20)

| # | File | Package Suffix | Purpose |
|---|------|---------------|---------|
| 1 | `FilterAction.java` | `model` | ALLOW/BLOCK enum |
| 2 | `ConditionType.java` | `model` | 12-value condition type enum |
| 3 | `CorruptionFilter.java` | `model` | Corruption filter enum |
| 4 | `FilterCondition.java` | `model` | Sealed interface + 12 records (with describe()) |
| 5 | `FilterRule.java` | `model` | Rule record with matches(), describeSummary(), describeMatch() |
| 6 | `FilterProfile.java` | `model` | Profile with evaluation, tracing, builder |
| 7 | `PlayerFilterState.java` | `model` | Per-player state with quick filter + builder |
| 8 | `ModifierFilterCategory.java` | `model` | 8-value enum for modifier picker tabs |
| 9 | `LootFilterConfig.java` | `config` | YAML config bean (with feedback + presets + category overrides) |
| 10 | `FilterConditionTypeAdapter.java` | `repository` | Gson sealed type adapter |
| 11 | `LootFilterRepository.java` | `repository` | DB + cache persistence |
| 12 | `EquipmentTypeResolver.java` | `evaluation` | ItemStack → EquipmentType (NOTE: already exists at `gear/conversion/`) |
| 13 | `LootFilterManager.java` | *(root)* | Lifecycle, CRUD, evaluation, quick filter, presets, modifier category map |
| 14 | `BlockFeedbackService.java` | `feedback` | Player notification on blocked items |
| 15 | `FilteredPickupInteraction.java` | `system` | Custom Pickup interaction that routes through cancellable event |
| 16 | `LootFilterPickupSystem.java` | `system` | ECS event handler for InteractivelyPickupItemEvent |
| 17 | `LfCommand.java` | `command` | /lf chat command (toggle, quick, test, preset, etc.) |
| 18 | `LootFilterPage.java` | `ui` | HyUI page (deferred) |
| 19 | `loot-filter.yml` | *(config resource)* | Default config with feedback + presets + category overrides |
| 20 | `schema.sql` update | *(db resource)* | New table (edit, not new file) |

All under base package: `io.github.larsonix.trailoforbis.lootfilter`

### Modified Files (7)

| File | Change |
|------|--------|
| `TrailOfOrbis.java` | Fields, init, command, events, shutdown, interaction registration |
| `gear/item/ItemSyncService.java` | Add `Pickup` interaction to RPG gear item definitions |
| `db/schema.sql` | New table |
| `api/services/UIService.java` | New method |
| `ui/UIManager.java` | New method impl |
| `listeners/PlayerJoinListener.java` | Join/disconnect hooks |
| *(config/ directory)* | New loot-filter.yml |

---

## 16. Implementation Sequence

Each step is independently compilable:

| Step | What | Depends On | Verification |
|------|------|------------|--------------|
| 1 | Model classes (9 files: enums incl. ModifierFilterCategory, FilterCondition, FilterRule, FilterProfile, PlayerFilterState) | Nothing | Unit tests for matches(), describe(), evaluate() |
| 2 | LootFilterConfig + loot-filter.yml (with feedback + presets + category overrides) | Nothing | Config loads correctly |
| 3 | FilterConditionTypeAdapter | Model classes | JSON round-trip tests for all 12 types |
| 4 | LootFilterRepository | Models, Adapter, DataManager | Save/load tests |
| 5 | schema.sql update | Nothing | H2 creates table |
| 6 | EquipmentTypeResolver update (if needed — already exists at `gear/conversion/`) | Models, GearData | Verify `getSlot()` method exists for filter conditions |
| 7 | LootFilterManager (with quick filter + presets + modifier category map) | Repository, Config | Profile CRUD, quick filter, preset tests, category lookup |
| 8 | BlockFeedbackService | Config | Unit tests for throttling logic |
| 9 | FilteredPickupInteraction + ItemSyncService changes | InteractionModule API | Compiles — requires in-game verification |
| 10 | LootFilterPickupSystem | Manager, FeedbackService, GearUtils | Compiles (depends on step 9 working in-game) |
| 11 | LfCommand (with quick, test, preset subcommands) | Manager | Command parsing tests |
| 12 | Integration (TrailOfOrbis changes) | All above | `./gradlew clean build` |
| 13 | **In-game interaction test** (CRITICAL GATE) | Deployed build | Verify: (a) custom Pickup interaction can be set on ItemDefinitionBuilder, (b) event fires, (c) cancellation re-drops item. If any fail → implement fallback strategy. |
| 14 | In-game command test | Deployed + step 13 pass | /lf quick, /lf test, /lf status |
| 15 | LootFilterPage (UI) — deferred | Manager, Models | Visual verification in-game |
| 16 | Unit tests | All above | Full test suite passes |

---

## 17. Test Plan

### Unit Tests

| Test Class | What It Tests |
|------------|---------------|
| `FilterConditionTest` | Each of 12 condition types' `matches()` with positive/negative cases |
| `FilterConditionDescribeTest` | Each of 12 types' `describe()` output formatting |
| `FilterRuleTest` | AND logic, empty conditions, disabled rules, describeSummary(), describeMatch() |
| `FilterProfileTest` | First-match-wins, default action fallback, empty rules, mixed ALLOW/BLOCK, evaluateWithTrace() |
| `PlayerFilterStateTest` | Profile CRUD, active profile management, quick filter mode, mutual exclusivity, edge cases |
| `FilterConditionTypeAdapterTest` | JSON round-trip for all 12 condition types including ModifierValueRange and ImplicitCondition |
| `LootFilterRepositoryTest` | Cache behavior (mock DataManager) |
| `EquipmentTypeResolverTest` | Known item IDs → correct EquipmentType |
| `LootFilterManagerTest` | Evaluate path, toggle, quick filter, profile management, preset loading |
| `BlockFeedbackServiceTest` | Feedback throttling, summary interval, mode switching |

### In-Game Tests

| Test | Expected Result |
|------|-----------------|
| Drop RPG gear, no filter active | Normal pickup |
| `/lf quick rare` then walk over Common item | Blocked, feedback shown |
| `/lf quick rare` then walk over Rare item | Normal pickup |
| `/lf quick off` then walk over Common item | Normal pickup |
| Create profile, default BLOCK, no rules | ALL RPG gear blocked |
| Add ALLOW rule: MinRarity EPIC | Common/Uncommon/Rare blocked, Epic+ allowed |
| Add ALLOW rule: LeatherArmor + Level 13-16 | Only matching leather armor picks up |
| Add ALLOW rule with ModifierValueRange: crit_chance >= 15 | Only crit items with high rolls pass |
| Add ALLOW rule with ImplicitCondition: minPercentile 0.8 | Only weapons with top 20% implicit rolls pass |
| Add ALLOW rule with ImplicitCondition: physical_damage only | Only physical weapons pass, spell weapons blocked |
| Multiple rules: first-match-wins | Earlier BLOCK overrides later ALLOW |
| Disable a rule | Rule is skipped during evaluation |
| `/lf toggle` | On/off confirmed in chat |
| `/lf switch "Boss Run"` | Profile switches, quick filter cleared, confirmed in chat |
| `/lf quick rare` after profile active | Quick filter active, profile deactivated |
| `/lf list` | All profiles listed with rule summaries and active indicator |
| `/lf test` while holding RPG item | Full rule-by-rule trace shown |
| `/lf test` while holding vanilla item | "Not RPG gear" message |
| `/lf preset "Rare+ Only"` | Profile created from preset |
| `/lf status` with quick filter | Shows "Quick Filter: Rare+" |
| `/lf status` with profile | Shows profile name and rule count |
| Disconnect + reconnect | Filter state persisted (quick filter and profiles) |
| Vanilla items (wood, food) | Always pick up regardless of filter |
| Block feedback in summary mode | "Blocked 12 items" after interval |
| Block feedback in every mode | Per-item messages |
| Block feedback mode: none | No messages |
