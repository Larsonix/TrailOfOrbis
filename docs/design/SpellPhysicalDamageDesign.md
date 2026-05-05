# Spell vs Physical Damage — Core Design

## The Question

Every attack in Trail of Orbis has a **base damage type**: either Physical or one of the 6 Elements. On top of that base, additional elemental damage can be added from attributes, gear, and conversion. How should defenses interact with each type?

## ARPG Industry Research

### Path of Exile 1 — Pure Separation
- **Armor**: Reduces ONLY physical hit damage. Zero effect on elemental.
- **Resistances**: Per-element, capped at 75%. ONLY defense against elemental damage.
- **Formula**: `DR = AR / (AR + 10 × DMG)` — better vs many small hits than one big hit.
- **Result**: Complete split. Armor builds ignore elemental threats. Resistance capping is mandatory.
- **Design philosophy**: Different threat types demand different defenses. Players must layer.

### Path of Exile 2 — Opt-In Hybrid
- **Default**: Same as PoE1 — armor only vs physical.
- **New mechanic**: Explicit mods/keystones grant "X% of Armour applies to Elemental Damage."
- **When armor applies to elemental**: Applied BEFORE resistances, as a separate reduction step.
- **Chain**: `elemental hit → armor reduction (if opted in) → resistance reduction → final`
- **Design philosophy**: Pure separation as baseline, with investment-gated crossover.

### Last Epoch — Armor Protects Everything (Reduced Efficiency)
- **Armor**: Reduces ALL hit damage types, but Physical gets full efficiency (cap 85%), elemental gets 70% efficiency (cap 59.5%).
- **Resistances**: Per-type (including Physical Resistance), applied on top of armor.
- **Penetration**: Enemies gain +1% penetration per level, capping at 75% at level 75.
- **Result**: Armor is always useful. Players still need resistances, but armor is a universal safety net.
- **Design philosophy**: Accessible, forgiving. Armor never feels wasted.

### Grim Dawn — Pure Separation
- **Armor**: Reduces ONLY Physical and Pierce damage (two physical subtypes). 70% absorption rate.
- **Resistances**: Per-type for all 11 damage types. Each capped independently.
- **Result**: Identical philosophy to PoE1. Physical has a unique defense layer (armor), elemental types rely purely on resistance.
- **Design philosophy**: Same as PoE — threat diversity requires defense diversity.

### Diablo 4 — Armor Reduces Everything Equally
- **Armor**: Flat % reduction applied to ALL incoming damage, regardless of type.
- **Resistances**: Additional per-element reduction on top of armor.
- **Result**: Armor is always the best stat. Individual resistances are secondary.
- **Design philosophy**: Simplicity. Every defense investment always helps.

## Comparison Matrix

| Model | Armor vs Physical | Armor vs Elemental | Element Resist | Build Diversity | Complexity |
|-------|------------------|--------------------|---------------|----------------|------------|
| **PoE1 / Grim Dawn** | Full | None | Primary defense | High | Low |
| **PoE2** | Full | Opt-in (investment) | Primary defense | Highest | Medium |
| **Last Epoch** | Full (85% cap) | 70% efficiency (59.5% cap) | Stacks on top | Medium | Low |
| **Diablo 4** | Full | Full (same rate) | Stacks on top | Low | Lowest |

## What Each Model Creates For Players

### Pure Separation (PoE1/GD)
- "I'm fighting fire mages → I NEED fire resistance or I die"
- "Armor is great vs melee warriors but does nothing vs casters"
- Creates **counter-preparation**: players gear specifically for the threat they'll face
- Risk: players with no resistance get punished hard by elemental threats

### Universal Armor (LE/D4)
- "Armor always helps, resistance is a bonus"
- Armor becomes the universal priority stat
- Less reason to diversify defenses
- Risk: armor-stacking builds trivialize all content, individual resistances feel optional

---

## Our Design Decision: Pure Separation

Trail of Orbis uses the **PoE1/Grim Dawn model** — armor reduces physical damage only, resistances reduce elemental damage only.

### Why This Is Right For Us

**1. Our 6-element attribute system demands it.**
Each attribute grants resistance to its own element: Fire attribute → Fire resist, Water → Water resist, etc. If armor (Earth's domain) also reduced elemental damage, investing in Fire/Water/Lightning attributes for resistance would be devalued. Pure separation makes every attribute's defensive contribution unique and necessary.

**2. Realm preparation becomes meaningful.**
Players see a Desert realm map → know Fire mobs → gear for Fire resistance. An Ice realm → Water resistance. This strategic preparation layer only works if the defense matters. Universal armor would let players ignore realm themes defensively.

**3. Earth keeps its identity.**
Earth grants armor — the primary physical defense. With pure separation, Earth is THE answer to physical threats, while other elements answer their own threat types. If armor also reduced elemental, Earth would become the god-attribute for all defense, collapsing the 6-element balance.

**4. Mob types become mechanically distinct.**
Right now, ALL mobs are effectively physical attackers (armor reduces everything). With this model:
- **Warrior mob** → physical base → armor is your defense
- **Fire mage mob** → fire base → fire resistance is your defense
- **Mixed mob** (physical base + elemental added) → need both armor AND resistance

This makes mob encounters tactically different, not just cosmetically different.

**5. Gear modifiers for resistance become relevant.**
Currently, rolling "+12% Fire Resistance" on gear is nearly worthless because flat elemental from mobs is a tiny fraction of total damage. When mobs deal elemental BASE damage, that fire resist roll becomes the difference between life and death.

**6. Skill tree defensive nodes diversify.**
Armor nodes (Earth branch) vs resistance nodes (each element's branch) become genuine strategic choices, not "armor always, resistance maybe."

### Edge Cases and Safety Nets

**Q: What about early game players with no resistance?**
A: Early-game mobs (level 1-15) are primarily physical. Elemental casters appear gradually as players level and gain attribute points. By the time a player faces a fire mage, they have enough Fire attribute (from natural progression) to have baseline resistance.

**Q: What if a player enters a fire realm with zero fire resist?**
A: They take full fire base damage (armor doesn't help). This is intentional — it communicates "you need fire resistance for this content." The realm map UI already shows the biome type. Players can bring fire resistance gear, allocate Fire attribute points, or use skill tree resistance nodes.

**Q: Does physical resistance still exist?**
A: Yes. Physical Resistance is a separate stat that reduces physical damage AFTER armor. Same as PoE. So physical defense has two layers (armor + phys resist), elemental has one layer (element resist). This is balanced because physical attackers are more common.

**Q: What about PvP?**
A: A spell-damage player (staff/wand) deals elemental base → opponent needs resistance. A melee player deals physical base → opponent needs armor. PvP builds must consider BOTH offense AND defense type, creating real counter-play.

---

## The Damage Model

### Three Axes

Every damage event has three independent properties:

1. **Delivery method** (how the damage reaches the target):
   - MELEE — direct physical contact
   - PROJECTILE — ranged delivery
   - AREA — area of effect
   - SPELL — magical delivery

2. **Base damage type** (what KIND of damage the base is):
   - PHYSICAL — reduced by Armor, then Physical Resistance
   - FIRE / WATER / LIGHTNING / EARTH / WIND / VOID — reduced by that element's Resistance

3. **Added damage** (extra damage layered on top of the base):
   - Flat elemental from attributes, gear, conversion
   - Each added element is reduced by its own resistance independently

### Defense Application Order

```
For each damage channel in the hit:

  Physical channel:
    1. Armor Penetration reduces effective armor
    2. Armor formula: effectiveArmor / (effectiveArmor + scale × attackerLevel + constant)
    3. Physical Resistance (capped at 75%) reduces remainder
    → Final physical damage

  Elemental channel (per element):
    1. Elemental Penetration reduces effective resistance
    2. Element Resistance (capped at 75%) reduces damage
    → Final elemental damage

  True Damage:
    → Bypasses all defenses
```

### Examples

**Warrior mob (physical base + fire added):**
```
Base: 100 Physical
Added: 20 Fire (from MobElementResolver detecting fire keywords)

vs Player with 40% armor reduction, 50% fire resist:
  Physical: 100 × (1 - 0.40) = 60
  Fire:      20 × (1 - 0.50) = 10
  Total: 70
```

**Fire Mage mob (fire base + no physical):**
```
Base: 100 Fire (converted from physical via 100% fire conversion)
Added: 0

vs Player with 40% armor reduction, 50% fire resist:
  Physical: 0 (no physical damage → armor does nothing)
  Fire:    100 × (1 - 0.50) = 50
  Total: 50

vs Player with 40% armor reduction, 0% fire resist:
  Physical: 0
  Fire:    100 × (1 - 0.00) = 100
  Total: 100 (full damage! armor doesn't help!)
```

**Hybrid caster mob (50% conversion):**
```
Base: 50 Physical + 50 Lightning (50% conversion)
Added: 15 Lightning (from elemental stats)

vs Player with 40% armor reduction, 30% lightning resist:
  Physical:  50 × (1 - 0.40) = 30
  Lightning: 65 × (1 - 0.30) = 45.5
  Total: 75.5
```

---

## Implementation: What Changes

### What Already Works (no changes needed)
- `DamageDistribution` already has physical + per-element slots
- `applyDefenses()` already applies armor ONLY to physical, resistance ONLY to elemental
- `applyConversion()` (Step 3) already moves physical → elemental with 100% cap
- `ComputedStats` already has `fireConversion`, `waterConversion`, etc. (all 6)
- Player spell damage (I4 fix) already places base in elemental slot
- `MobElementResolver` already detects mob elements from NPC keywords/groups

### What Needs to Change

**1. Caster mobs set conversion stats** (core change):
When `MobStatGenerator` builds a CASTER archetype mob with a detected element, set the conversion stat on `ComputedStats` (e.g., 100% fire conversion for a Fire Mage). The existing Step 3 in the calculator converts their physical base to elemental automatically.

**2. Mixed elemental mobs use partial conversion** (optional):
A fire warrior might have 30% fire conversion — some physical base becomes fire, rest stays physical. Configurable per archetype/element combo.

**3. `MobStats.toComputedStats()` forwards conversion** (plumbing):
Add conversion fields to `MobStats` or set them during `toComputedStats()` based on archetype + element.

**4. `mob-elements.yml` keyword gaps filled** (config):
Add missing keywords: "incandescent" → FIRE, "sand" → EARTH. Add overrides for Outlander Sorcerer/Cultist/Priest → VOID, Trork Shaman → EARTH.

**5. Archetype stored on `MobScalingComponent`** (plumbing):
Currently archetype is computed transiently during scaling. Store it on the component so it's available at combat time for conversion decisions.

### What Does NOT Change
- `RPGDamageCalculator.applyDefenses()` — already correct
- `DamageDistribution` — already has the right structure
- Player spell damage path — already works (I4 fix)
- Gear stat system — resistance modifiers already exist
- Skill tree — resistance nodes already exist
- Attribute grants — already grant per-element resistance

---

## Impact on Player Builds

| Build Archetype | Primary Defense | Secondary Defense | Weak Against |
|----------------|-----------------|-------------------|--------------|
| Earth Tank (armor stacker) | High armor vs physical | Some resistance from gear | Pure elemental casters |
| Fire Mage (Water + Fire invest) | Water resist (ES) + Fire resist | Moderate armor from gear | Physical brutes with no element |
| Lightning Assassin (Wind + Lightning) | Evasion (Wind) + Lightning resist | Low armor | Slow heavy physical hitters |
| Balanced Hybrid | Moderate armor + spread resists | Moderate across all | Nothing specifically, but no peak defense |
| Void Warlock (Void + Water) | ES (Water) + Void resist | Life steal sustain | Physical damage (low armor), non-Void elements |

This creates the kind of meaningful defensive choices that make gearing interesting.

---

## Summary

- **Physical base damage** → Armor is primary defense (Earth's domain)
- **Elemental base damage** → Element-specific Resistance is primary defense (each element's domain)
- **Added elemental** → Same as elemental base (resistance handles it)
- **The calculator already implements this.** The missing piece is letting mobs deal elemental base damage through the conversion system.
- **Implementation is surgical**: store archetype on component, set conversion on caster mobs, fill keyword gaps.
