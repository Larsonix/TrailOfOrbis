# Gear System Analysis & Implementation Guide

Comprehensive analysis comparing the initial design vision with Hytale's actual capabilities, providing clear recommendations on implementation approach.

**Date**: 2026-01-23
**Based On**: 8 research documents + initial design document

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [What You Can Do (Fully Supported)](#what-you-can-do-fully-supported)
3. [What You Should Do Differently](#what-you-should-do-differently)
4. [What You Shouldn't Do](#what-you-shouldnt-do)
5. [New Possibilities Discovered](#new-possibilities-discovered)
6. [Limitations & Workarounds](#limitations--workarounds)
7. [Recommended Implementation Order](#recommended-implementation-order)
8. [Technical Quick Reference](#technical-quick-reference)

---

## Executive Summary

### The Good News

Your initial design is **almost entirely feasible**. Hytale's architecture is surprisingly well-suited for an RPG gear system:

| Core Feature | Feasibility | Notes |
|--------------|-------------|-------|
| Custom item data (level, rarity, quality, modifiers) | ✅ Fully supported | BsonDocument metadata on ItemStack |
| Level-gated equipment | ✅ Fully supported | Cancellable events for equip blocking |
| Custom loot drops | ✅ Fully supported | Full control over mob drops with metadata |
| Stat modifiers on gear | ✅ Fully supported | Native StatModifier system + custom injection |
| Combat stat integration | ✅ Fully supported | Damage events are cancellable and modifiable |
| Crafting restrictions | ✅ Fully supported | Pre/Post crafting events are cancellable |
| Visual rarity indicators | ✅ Mostly supported | Native ItemQuality system for colors, tooltips, particles |
| Durability system | ✅ Vanilla handled | Native durability + repair, we only set initial max |

### Key Insight

Hytale already has a **quality/rarity system** (`ItemQuality`) with colored text, styled tooltips, slot backgrounds, and drop particles. Your RPG rarity system should **extend this native system** rather than reinvent it.

---

## What You Can Do (Fully Supported)

### 1. Item Data Storage

**Your Design**: Store level, rarity, quality, modifiers in item data
**Reality**: Exactly as designed

```java
// Store all RPG data in ItemStack.metadata (BsonDocument)
itemStack = itemStack
    .withMetadata("RPG:Level", Codec.INTEGER, 25)
    .withMetadata("RPG:Rarity", Codec.STRING, "Epic")
    .withMetadata("RPG:Quality", Codec.INTEGER, 78)
    .withMetadata("RPG:Prefixes", MODIFIER_LIST_CODEC, prefixes)
    .withMetadata("RPG:Suffixes", MODIFIER_LIST_CODEC, suffixes);
```

**Bonus**: Items with different RPG stats won't stack automatically (metadata is compared in `isStackableWith()`).

---

### 2. Level-Gated Equipment

**Your Design**: Block equipping gear above player level
**Reality**: Two cancellable interception points

| Slot Type | Event | How to Block |
|-----------|-------|--------------|
| Weapons/Utilities | `SwitchActiveSlotEvent` | `event.setCancelled(true)` |
| Armor | Custom `SlotFilter` | Return `false` from `test()` |

```java
// Weapons/Utilities - straightforward
eventRegistry.register(SwitchActiveSlotEvent.class, event -> {
    ItemStack item = getItemFromSlot(event);
    Integer requiredLevel = item.getFromMetadataOrNull("RPG:Level", Codec.INTEGER);

    if (requiredLevel != null && playerLevel < requiredLevel) {
        event.setCancelled(true);
        sendMessage(player, "Requires level " + requiredLevel);
    }
});
```

---

### 3. Custom Loot Drops

**Your Design**: Generate gear with random stats on mob death
**Reality**: Full control via death systems and drop events

**Key discoveries**:
- `ItemDrop.metadata` is a BsonDocument - attach full RPG stats directly
- `DamageData` tracks ALL attackers with damage contribution - perfect for party loot
- `DropItemEvent.Drop` is cancellable - can intercept/modify any drop
- `DeathComponent.getDeathInfo()` gives killer info for level-based scaling

```java
// Generate custom loot on mob death
eventRegistry.register(NPCDeathEvent.class, event -> {
    Damage.Source source = event.getDeathComponent().getDeathInfo().getSource();

    if (source instanceof Damage.EntitySource entitySource) {
        int playerLevel = getPlayerLevel(entitySource.ref());
        int mobLevel = getMobLevel(event.getEntity());

        // Generate gear with your rarity/quality/modifier system
        ItemStack gear = gearGenerator.generate(mobLevel, playerLevel);

        // Spawn with physics
        ItemComponent.generateItemDrop(store, gear, position, vx, vy, vz);
    }
});
```

---

### 4. Combat Stat Integration

**Your Design**: Modifier bonuses affect damage
**Reality**: Multiple injection points, all viable

**Best approach**: Register a custom `DamageEventSystem` in the Filter group:

```java
public class RpgCombatSystem extends DamageEventSystem {
    @Override
    public void handle(..., Damage damage) {
        // Get attacker's gear bonuses
        float attackBonus = getAttackerRpgBonus(damage.getSource());

        // Get defender's gear defense
        float defenseReduction = getDefenderRpgDefense(chunk, index);

        // Modify damage
        float newDamage = (damage.getAmount() + attackBonus) * (1 - defenseReduction);
        damage.setAmount(newDamage);
    }
}
```

**Key discovery**: `Damage.getMetaStore()` lets you attach custom data to damage events - useful for tracking RPG-specific damage sources.

---

### 5. Crafting Restrictions

**Your Design**: Crafting limited to Common/Uncommon, max 50% quality
**Reality**: Exactly as designed via events

| Event | When | Effect if Cancelled |
|-------|------|---------------------|
| `CraftRecipeEvent.Pre` | Before materials removed | Entire craft prevented, materials kept |
| `CraftRecipeEvent.Post` | After materials consumed | Output prevented, materials LOST |

```java
// Block high-tier crafting
eventRegistry.register(CraftRecipeEvent.Pre.class, event -> {
    if (isRpgRecipe(event.getCraftedRecipe())) {
        // Only allow Common/Uncommon crafting
        // Cancel higher tiers
    }
});

// Generate custom output with capped quality
eventRegistry.register(CraftRecipeEvent.Post.class, event -> {
    if (isRpgRecipe(event.getCraftedRecipe())) {
        ItemStack rpgItem = generateCraftedGear(recipe, playerLevel);
        // Quality capped at 50% for crafted items
        giveItem(player, rpgItem);
        event.setCancelled(true); // Prevent default output
    }
});
```

---

### 6. Durability System

**Your Design**: Rarity affects max durability
**Reality**: Vanilla Hytale handles everything - we just set initial max durability

```java
// ItemStack has native durability fields
double currentDurability = itemStack.getDurability();
double maxDurability = itemStack.getMaxDurability();

// We only need to set initial max durability based on rarity
double baseDurability = item.getItem().getMaxDurability();  // From vanilla item definition
double rarityMultiplier = getRarityDurabilityMultiplier(rarity); // 1.0x - 5.0x

// Create item with calculated max durability
itemStack = itemStack.withRestoredDurability(baseDurability * rarityMultiplier);
```

**Key Point**: Vanilla Hytale handles:
- Durability degradation (`DamageSystems.DamageArmor`)
- Broken item behavior (`BrokenPenalties`)
- Repair mechanics
- Visual indicators

We do NOT need custom durability logic - just set initial `maxDurability` when generating gear.

---

## What You Should Do Differently

### 1. Use Native ItemQuality for Rarity Visuals

**Your Design**: Create custom rarity color system
**Better Approach**: Extend Hytale's `ItemQuality` system

Hytale already has:
- Quality-based text colors (`#c9d2dd`, `#3e9049`, `#2770b7`, `#8b339e`, `#bb8a2c`)
- Quality-based tooltip textures (per-quality frames)
- Quality-based slot backgrounds
- Quality-based drop particle effects

**Recommendation**: Create custom `ItemQuality` JSON files for your RPG tiers:

```json
// Assets/Server/Item/Qualities/Mythic.json
{
  "QualityValue": 10,
  "TextColor": "#FF0000",
  "LocalizationKey": "server.rpg.qualities.Mythic",
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipMythic.png",
  "SlotTexture": "UI/ItemQualities/Slots/SlotMythic.png",
  "VisibleQualityLabel": true,
  "ItemEntityConfig": {
    "ParticleSystemId": "Drop_Mythic"
  }
}
```

**Benefits**:
- Automatic tooltip styling
- Automatic slot background coloring
- Automatic drop particle effects
- Automatic text coloring
- No custom rendering code needed

---

### 2. Store RPG Data Separately from Native Stats

**Your Design**: Implied mixing of systems
**Better Approach**: Clear separation with namespace

```java
// Your RPG data - prefixed with "RPG:"
"RPG:Level", "RPG:Rarity", "RPG:Quality", "RPG:Prefixes", "RPG:Suffixes"

// Native Hytale data - untouched
ItemWeapon.statModifiers, ItemArmor.damageResistanceValues
```

**Why**: Hytale's native stat system still works for vanilla items. Your RPG layer adds on top without breaking base game.

---

### 3. Tooltip Content via Description Override

**Your Design**: Rich custom tooltips
**Reality**: Limited server-side tooltip API

**Best approach**: Format all RPG stats into the description field:

```java
// Build description with all RPG stats
String desc = buildRpgDescription(metadata);
// "Damage: 45-67\n+12 Strength\n+5% Crit Chance\n\nRequires Level 25"

ItemGridSlot slot = new ItemGridSlot(itemStack)
    .setDescription(desc);
```

**Limitation**: No rich text formatting in tooltips (no colored stats). Use clear formatting instead:
```
+12 Strength
+5% Critical Strike Chance
-10% Movement Speed

Requires Level 25
```

---

### 4. Combat Text for Stat Feedback

**Your Design**: Visual feedback for stats
**Better Approach**: Use `CombatTextUIComponent` for damage numbers

Hytale has floating combat text with customizable:
- Font size
- Text color (per damage type)
- Position offset
- Animation events

Color your damage numbers by damage type:
- Physical: white
- Fire: orange
- Ice: blue
- Critical: gold/larger

---

## What You Shouldn't Do

### 1. Don't Reinvent the Wheel

| Don't Do | Do Instead |
|----------|------------|
| Create custom rarity color rendering | Use `ItemQuality.textColor` |
| Build custom tooltip frames | Use `ItemQuality.itemTooltipTexture` |
| Implement custom slot highlighting | Use `ItemQuality.slotTexture` |
| Create custom drop particles | Use `ItemEntityConfig.particleSystemId` |
| Build custom durability tracking | Use native `ItemStack.durability` |

### 2. Don't Use Annotations for Events

```java
// WRONG - @EventHandler doesn't work in Hytale
@EventHandler
public void onDamage(DamageEvent event) { }

// CORRECT - Use EventRegistry.register()
eventRegistry.register(Damage.class, event -> { });
```

### 3. Don't Store References to Entities Long-Term

```java
// WRONG - Entity references can become stale
private Map<Player, RpgData> playerData;

// CORRECT - Use UUIDs
private Map<UUID, RpgData> playerData;
```

### 4. Don't Block the Main Thread for Stat Calculations

Heavy calculations (modifier stacking, damage formulas) should be efficient. The damage pipeline runs every tick for combat.

### 5. Don't Forget Metadata Persistence

ItemStack metadata is automatically serialized, BUT:
- Use proper Codecs for complex objects
- Keep keys consistent (`RPG:` prefix)
- Don't store non-serializable data

---

## New Possibilities Discovered

### 1. Armor Can Increase Outgoing Damage

**Discovery**: `ItemArmor.damageEnhancementValues` exists

This means armor can have **offensive stats**:
- "Berserker Armor: +15% Physical Damage"
- "Pyromancer Robes: +20% Fire Damage"

```java
// Armor that makes attacks stronger
damageEnhancementValues: {
    "Physical": [{ "Amount": 0.15, "CalculationType": "Multiplicative" }]
}
```

### 2. Damage Cause Inheritance Chain

**Discovery**: Damage types inherit from parents

```
Slash → Physical → All
Fire → Elemental → All
```

This means:
- Resistance to "Physical" blocks both Slash and Blunt
- You can create sub-types (Fire vs Ice vs Lightning) under "Elemental"
- Generic "All" resistance exists for total damage reduction

### 3. Multiple Attackers Tracked for Party Loot

**Discovery**: `DamageData` tracks all damage contributors

```java
Object2DoubleMap<Ref<EntityStore>> contributors = damageData.getDamageSuffered();
// Key: attacker reference
// Value: total damage dealt

// Calculate loot shares based on contribution
for (var entry : contributors.object2DoubleEntrySet()) {
    double share = entry.getDoubleValue() / totalDamage;
    // Award XP/loot proportionally
}
```

**Implications**:
- Party loot distribution based on contribution
- Support role tracking (who tanked, who DPS'd)
- Kill credit for assists

### 4. Recipe Metadata Passes Through to Output

**Discovery**: `MaterialQuantity.metadata` flows to crafted items

```json
{
  "Output": [{
    "ItemId": "Weapon_Sword_Iron",
    "Metadata": {
      "rpg": { "craftedBy": true, "baseTier": 1 }
    }
  }]
}
```

This means you can define base RPG properties in recipe JSON.

### 5. Conditional Item Visuals

**Discovery**: `ItemAppearanceCondition` allows state-based visuals

```json
// Low durability warning
{
  "Condition": { "Min": 0.0, "Max": 0.25 },
  "Particles": [{ "SystemId": "Damage_Smoke" }],
  "ModelVFXId": "Item_Damaged"
}
```

**Implications**:
- Enchanted weapons could glow based on charge
- Damaged items show visual wear
- Buff-enhanced gear could shimmer

### 6. Bench Tier Affects Crafting

**Discovery**: Higher bench tiers reduce crafting time and add slots

You could:
- Better benches = better stat rolls
- Higher tiers unlock rare recipes
- Tier bonuses to crafted item quality

---

## Limitations & Workarounds

### 1. No Rich Text in Tooltips

**Limitation**: Tooltip content is plain text, no colored stats

**Workaround**: Use clear formatting and rely on the colored item name (via ItemQuality) for rarity indication:

```
[EPIC] Blade of the Phoenix    ← Name colored via ItemQuality
───────────────────────────
45-67 Fire Damage
+12 Strength
+5% Critical Strike

Requires Level 25
```

### 2. No Custom Tooltip Layouts

**Limitation**: Client renders fixed tooltip layout

**Workaround**: Pack all info into the description field. Consider using:
- Line separators (`───────────`)
- Consistent formatting
- Section headers

### 3. Model Tinting Requires Assets

**Limitation**: Can't dynamically tint item models without pre-defined gradient sets

**Workaround**:
- Use drop particles for rarity indication (already supported)
- Use slot backgrounds for inventory indication
- Accept that held items won't have colored tints unless you create custom assets

### 4. Custom Particles Require Asset Files

**Limitation**: Can't create new particle effects purely server-side

**Workaround**:
- Use existing particle systems with color tinting
- `ParticleUtil.spawnParticleEffect(systemId, position, scale, COLOR)`
- Vanilla has many reusable effects: fire, ice, sparkles, etc.

### 5. Tooltip Translation Keys

**Limitation**: Dynamic stat values in tooltips need translation key workarounds

**Workaround**: Build description strings server-side rather than using translation keys:

```java
// Instead of translation keys with params (limited support)
// Just build the string directly
String desc = String.format("+%d Strength", strengthValue);
```

---

## Recommended Implementation Order

Based on dependencies and complexity:

### Phase 1: Core Foundation
1. **Item Metadata System** - Define data structures, codecs, storage keys
2. **Custom ItemQuality Files** - Create RPG rarity tiers (Common→Mythic)
3. **Gear Data Classes** - GearModifier, GearRarity, GearStats POJOs

### Phase 2: Equipment Mechanics
4. **Level Gating** - SwitchActiveSlotEvent + SlotFilter for equip blocking
5. **Stat Application** - Hook into StatModifiersManager for gear bonuses
6. **Durability Integration** - Set initial maxDurability based on rarity (vanilla handles the rest)

### Phase 3: Combat Integration
7. **RPG Damage System** - Custom DamageEventSystem in Filter group
8. **Combat Modifiers** - Apply strength/dexterity bonuses to damage
9. **Elemental Damage** - Custom DamageCauses for fire/ice/lightning

### Phase 4: Loot Generation
10. **Drop Interception** - Hook mob death systems
11. **Gear Generator** - Rarity rolls, modifier selection, value ranges
12. **Loot Tables** - Define drop pools per mob type

### Phase 5: Crafting Integration
13. **Crafting Events** - Pre/Post event handlers
14. **Crafting Restrictions** - Common/Uncommon only, quality caps
15. **Bench Integration** - Tier bonuses for crafted quality

### Phase 6: Visuals & Polish
16. **Tooltip Formatting** - Build description strings with stats
17. **Drop Effects** - Quality-based particles (mostly free via ItemQuality)
18. **Combat Text** - Damage type coloring

### Phase 7: Reroll Stones (Optional)
19. **Stone Items** - Quality Stone, Modifier Stone, etc.
20. **Reroll UI/Logic** - Consume stone, regenerate stats

---

## Technical Quick Reference

### Key Classes to Know

| Class | Purpose | Location |
|-------|---------|----------|
| `ItemStack` | Item instance with metadata | `.../inventory/ItemStack.java` |
| `BsonDocument` | Custom data storage | Standard BSON |
| `ItemQuality` | Rarity visuals (tooltip, color, particles) | `.../item/config/ItemQuality.java` |
| `SwitchActiveSlotEvent` | Weapon/utility equip (cancellable) | `.../events/ecs/SwitchActiveSlotEvent.java` |
| `SlotFilter` | Armor slot validation | `.../container/filter/SlotFilter.java` |
| `Damage` | Damage event (cancellable, modifiable) | `.../damage/Damage.java` |
| `StatModifiersManager` | Entity stat calculation | `.../entity/StatModifiersManager.java` |
| `CraftRecipeEvent` | Crafting events (Pre/Post) | `.../events/ecs/CraftRecipeEvent.java` |
| `ItemComponent` | Dropped item spawning | `.../entity/item/ItemComponent.java` |
| `DamageData` | Attack tracking for loot | `.../npc/util/DamageData.java` |
| `Message` | Colored text formatting | `.../core/Message.java` |

### Metadata Keys Convention

```java
public static class MetadataKeys {
    public static final String LEVEL = "RPG:Level";
    public static final String RARITY = "RPG:Rarity";
    public static final String QUALITY = "RPG:Quality";
    public static final String PREFIXES = "RPG:Prefixes";
    public static final String SUFFIXES = "RPG:Suffixes";
    public static final String IS_UNIQUE = "RPG:IsUnique";
    public static final String UNIQUE_ID = "RPG:UniqueId";
    // Note: Durability uses native ItemStack.maxDurability field, not custom metadata
}
```

### Rarity → Quality Mapping

| RPG Rarity | Hytale Quality | QualityValue | TextColor |
|------------|----------------|--------------|-----------|
| Common | Common | 1 | `#c9d2dd` |
| Uncommon | Uncommon | 2 | `#3e9049` |
| Rare | Rare | 3 | `#2770b7` |
| Epic | Epic | 4 | `#8b339e` |
| Legendary | Legendary | 5 | `#bb8a2c` |
| Mythic | Custom | 10 | `#FF0000` (define) |

### Event Registration Pattern

```java
// Always use EventRegistry, never annotations
getEventRegistry().register(EventClass.class, event -> {
    // Check conditions
    if (shouldCancel) {
        event.setCancelled(true);
        return;
    }
    // Modify as needed
    event.setAmount(newAmount);
});
```

---

## Summary

Your initial design was well-conceived. The main adjustments are:

1. **Leverage ItemQuality** for visual rarity indicators instead of building custom rendering
2. **Use native durability** fields rather than custom tracking
3. **Accept tooltip limitations** and format stats as plain text descriptions
4. **Embrace event-driven architecture** with cancellable events for blocking/modification
5. **Consider new possibilities** like armor offensive stats and damage inheritance chains

The Hytale modding architecture is surprisingly complete for RPG mechanics. Most of your vision is directly implementable with minimal workarounds.

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial analysis based on 8 research documents |
| 1.1 | 2026-01-23 | Clarified durability/repair handled by vanilla Hytale. Updated metadata keys (removed DURABILITY_MAX). |
