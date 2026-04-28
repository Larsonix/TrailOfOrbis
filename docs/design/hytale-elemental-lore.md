# Hytale Elemental Lore - Official Canon Research

**Research Date**: February 10, 2026
**Sources**: Official Hytale blog, Hytale Fandom Wiki, Hytale Documentation Wiki, Decompiled Game Files

---

## The 6 Canonical Elements

According to official Hytale lore, there are **exactly 6 elements** in the world of Orbis:

| Element | Deity | Representation | Color |
|---------|-------|----------------|-------|
| **Earth** | Gaia | Ponytail 1 | Green/Brown |
| **Wind** | Gaia | Ponytail 2 | White/Grey |
| **Water** | Gaia | Ponytail 3 | Blue |
| **Fire** | Gaia | Ponytail 4 | Red/Orange |
| **Lightning** | Gaia | Ponytail 5 | Yellow |
| **Void** | Varyn | The 6th element | Purple/Black |

### The Divine Division

**Gaia** - The goddess of Orbis, worshipped by Humans, Kweebecs, Ferans, and other races. She controls **5 of the 6 elements**: Earth, Wind, Water, Fire, and Lightning. In concept art, Gaia is depicted with five ponytails, each with a colored pearl representing one of her elements. These five elements compose life on Orbis.

**Varyn** - The primary antagonist, not native to Orbis. He controls the **6th element: Void**. He uses Void magic to corrupt NPCs and bring them under his control as "Void Spawn". The Outlanders worship him.

### Visual Representation

The six elements are represented by **Runes** - ancient symbols that appear throughout Orbis. A concept art piece shows a circle of six elemental runes, confirming all six elements are part of the world's magical foundation.

**Gaian Portals** are surrounded by the 5 elemental runes that Gaia controls (excluding Void).

---

## Elements in Current Game Code

### Damage Types (Server/Entity/Damage/)

| File | Type | Notes |
|------|------|-------|
| Fire.json | Elemental | ✅ Matches lore |
| Ice.json | Elemental | ⚠️ Lore says "Water" |
| Poison.json | Standalone | ❌ Not in canonical 6 |

**Missing from damage types:**
- Earth (lore element)
- Wind (lore element)
- Lightning (lore element)
- Void (lore element)

### Elemental Spirits (NPCs)

| Spirit | Element | Zone |
|--------|---------|------|
| Spirit_Ember | Fire | All zones |
| Spirit_Frost | Ice/Water | Zone 3 (Borea) |
| Spirit_Thunder | Lightning | Unknown |
| Spirit_Root | Earth/Nature | Zone 1 (Emerald Grove) |

**No Spirit exists for:**
- Wind
- Void

### Elemental Dragons (Bosses)

| Dragon | Element | Status |
|--------|---------|--------|
| Dragon_Fire | Fire | In game |
| Dragon_Frost | Ice/Water | In game |
| Dragon_Void | Void | Model exists, role unclear |

### Elemental Golems

| Golem | Element | Special |
|-------|---------|---------|
| Golem_Firesteel | Fire | Immunity_Fire |
| Golem_Crystal | Crystal? | Not canonical element |

---

## Discrepancies: Lore vs. Code

### "Water" vs "Ice"

The lore describes **Water** as Gaia's element, but the game uses **Ice** as the damage type. This suggests:
1. Ice is the combat manifestation of Water
2. Water element may encompass both liquid water and frozen ice
3. Future updates may add distinct Water damage

### Poison - Not a Canonical Element

Poison exists as a damage type (`Poison.json`) but is **NOT** part of the 6 canonical elements. It should be treated as a **status effect** or **secondary damage type**, not a primary elemental category.

### Missing Elements in Combat

The following lore elements have NO damage types yet:
- **Earth** - Spirit_Root exists but no Earth damage type
- **Wind** - No spirits, no damage type, no NPCs
- **Lightning** - Spirit_Thunder exists but no Lightning damage type
- **Void** - Entire Void faction exists but no Void damage type

---

## Zones and Elements (Speculative)

Based on game design, zones appear to have elemental themes:

| Zone | Region | Suspected Element |
|------|--------|-------------------|
| Zone 1 | Emerald Grove | Earth |
| Zone 2 | Howling Sands | Fire |
| Zone 3 | Borea | Water/Ice |
| Zone 4 | Devastated Lands | Void? |

**Note**: This is speculative based on biome design, not confirmed canon.

---

## Alterverses and Elements

Each element appears to be connected to specific **Alterverses** (alternate dimensions). Concept art shows planets with elemental themes, suggesting:
- Each Alterverse may be dominated by one element
- Orbis is special because Gaia can manipulate all five natural elements
- The Void may originate from a Void-dominated Alterverse

---

## Recommendations for TrailOfOrbis

### Use the 6 Canonical Elements

```
FIRE      - Gaia's element, fully implemented
WATER     - Gaia's element, use "Ice" as combat variant
LIGHTNING - Gaia's element, Spirit_Thunder exists
EARTH     - Gaia's element, Spirit_Root exists
WIND      - Gaia's element, not yet in game
VOID      - Varyn's element, Void faction exists
```

### Do NOT Use

- ❌ **Poison** - Not a canonical element (treat as status effect)
- ❌ **Cold** - Use "Ice" or "Water" instead
- ❌ **Chaos** - Use "Void" (the canonical name)
- ❌ **Nature** - Covered by "Earth" element
- ❌ **Crystal** - Not a canonical element

### Future-Proofing

Since Hytale is in early access and the lore team confirmed elements are "canon foundations unlikely to change", our plugin should:

1. **Use all 6 canonical elements** from day one
2. **Name them exactly as lore defines**: Fire, Water, Lightning, Earth, Wind, Void
3. **Keep Poison as a status effect**, not an element
4. **Treat Ice as Water's combat form** (Water damage type internally, "Frost/Ice" for flavor)

---

## Source References

### Official Blog
- "A look at Hytale's Lore and Philosophy" (Jan 8, 2026)
  - Confirms Gaia and Varyn are still canon
  - Lore is designed as "archaeology" - fragments to discover
  - "Cursebreaker" narrative hint

### Wiki Sources
- Hytale Fandom Wiki: Elements page
- Hytale Documentation Wiki: Element page
- Both confirm 6 elements with rune representations

### Community Sources
- HytaleHub FAQ (2021): "Gaia controls 5 of the 6 elements and is worshiped by many of Orbis's peoples. Varyn has power of the element of void."

### Game Files
- `/Assets/Server/Entity/Damage/` - Fire.json, Ice.json, Poison.json
- `/Assets/Server/NPC/Roles/Elemental/Spirit/` - 4 elemental spirits
- `/Assets/Server/NPC/Roles/Boss/` - Dragon_Fire, Dragon_Frost
- `/Assets/Server/Models/Elemental/` - Dragon_Void model exists

---

## Rune System (Bonus)

The game has a runic alphabet with 24 runes (see `Assets/Server/WordLists/Runes.json`):

```
feyun, urox, thuris, ansur, katapo, kinas, geboan, zunjo,
latal, naudiz, issa, jeran, eihwas, pertho, algas, solas,
tyrin, berkan, woz, manala, lagus, inguz, othaka, digas
```

These appear to be Hytale's equivalent of Elder Futhark runes, likely used for:
- Elemental rune symbols
- Magical inscriptions
- Procedural name generation

---

## Summary

**The 6 Elements of Orbis:**

1. 🟤 **EARTH** - Gaia's domain, stability and growth
2. ⚪ **WIND** - Gaia's domain, freedom and movement
3. 🔵 **WATER** - Gaia's domain, flow and adaptability (Ice in combat)
4. 🔴 **FIRE** - Gaia's domain, destruction and renewal
5. 🟡 **LIGHTNING** - Gaia's domain, power and energy
6. 🟣 **VOID** - Varyn's domain, corruption and entropy

This is the canonical foundation. Build the RPG plugin's elemental system on this.
