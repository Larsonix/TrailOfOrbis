# Mountains Biome — Planning Document

**Status:** NOT YET BUILT — requires design decision before implementation.
**Last updated:** April 20, 2026

---

## The Core Decision

Goblins are a **DUNGEON faction** — their 97 prefabs are underground tunnel modules (corridors, T-junctions, rooms, mine shafts, a multi-story "Goblin City"). They do NOT have surface structures like Trork (tents, watchtowers) or Outlander (houses, forts).

**Two viable approaches:**

### Approach A: Mountain Interior (Recommended)

The arena IS the inside of a goblin mine within a mountain. Uses `signature_type="ceiling"` like Caverns, but with a distinctly artificial/mined feel rather than natural crystal cave.

**Differences from Caverns:**
- Caverns = natural crystal cave, organic pillars (3D noise), wildlife
- Mountains = carved mine shafts, structured corridors, goblin infrastructure

**Advantages:**
- Authentic use of Goblin_Lair structures (houses, mine corridors, rooms)
- Klops_Basalt structures (54 prefabs) as worker housing
- Narrative: "assault the goblin mine from within"
- Unique feel: artificial underground vs Caverns' natural underground
- Goblin_Duke 3-phase boss fight in his throne room (Boss Room prefab exists)

**Challenges:**
- Need to verify which Goblin_Lair pieces have PrefabSpawnerBlocks vs are safe individual prefabs
- Overlaps conceptually with Caverns (both underground) — differentiation through artificial vs natural
- May need different density pattern (flat tunnels vs Caverns' organic 3D noise)

**Key structures to test (via PrefabUtil.paste):**
- `Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Basalt_1-4` — 4 basalt goblin houses
- `Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Stone_1-4` — 4 stone goblin houses
- `Dungeon/Goblin_Lair/Prefabs_Goblin/Mushrooms/Small` — decorative mushrooms
- `Cave/Nodes/Rock_Stone/Goblin` — 3 goblin mining nodes (Cave category = likely safe)
- `Cave/Klops/Basalt/Cave/Cave_001-002` — 2 Klops cave rooms
- `Cave/Klops/Basalt/Main/Klops_Basalt_Main_001-005` — 5 main halls

### Approach B: Open Mountain Surface (Simpler)

Standard open-air arena on a rocky mountain. Goblins are combat-only enemies with no faction structures (like Desert's fire undead). Mountain identity comes from terrain and geology.

**Advantages:**
- Simpler to implement (no ceiling complexity)
- No structure verification needed
- Clear differentiation from Caverns

**Disadvantages:**
- Goblins have no visible presence (just mobs)
- Less narrative depth than other faction biomes
- Wastes the rich Goblin_Lair prefab library

---

## Mob Pool (Confirmed Working)

| Role | Weight | Style | Notes |
|------|--------|-------|-------|
| Goblin_Scrapper | 25% | Basic melee | Faction grunt |
| Goblin_Lobber | 20% | Ranged projectiles | Bombs in tight spaces |
| Goblin_Miner | 15% | Pickaxe fighter | Mining worker |
| Goblin_Thief | 15% | Stealth attacker | Ambush |
| Goblin_Ogre | 15% | Heavy brute | Tank |
| Bear_Grizzly | 10% | Heavy tank | Mountain wildlife |
| **Boss: Goblin_Duke** | — | 3-phase fight | Phase 1 → 2 → 3 Fast/Slow |

**Unassigned Goblin roles available:**
- Goblin_Scavenger (+Battleaxe, +Sword variants) — could add variety
- Goblin_Hermit — could be rare encounter
- Goblin_Sentry — perimeter guard (currently Trork only but Goblin version exists conceptually)

**Additional mobs to consider:**
- Golem_Crystal_Thunder — thunder elemental, fits mountain storms
- Spirit_Thunder — thunder spirit, mountain elemental
- Bat — cave/mine ambient creature

---

## Terrain Concept

### If Approach A (Interior):
- `surface_materials`: `Rock_Stone`, `Rock_Basalt`
- `boundary_type`: `textured` (carved mine walls)
- `signature_type`: `ceiling` with LOWER ceiling than Caverns (claustrophobic mine vs cathedral cave)
- `ceiling_y`: ~95 (vs Caverns 110) — tighter, more oppressive
- NO 3D volumetric noise (mines are structured, not organic)
- Flat floor with minimal terrain noise (man-made, leveled)
- Mine cart rails via `path_material`?

### If Approach B (Surface):
- `surface_materials`: `Rock_Stone`, `Rock_Basalt`
- `boundary_type`: `textured` (craggy mountain cliffs)
- `signature_type`: `elevated_center` or new "peaked" type
- High terrain noise (dramatic mountain peaks)
- No ceiling

---

## Structure Testing Required

Before building Mountains, test these via server deployment:

1. **Safe (Cave/ category):**
   - `Cave/Nodes/Rock_Stone/Goblin` (3 prefabs) — goblin mining nodes
   - `Cave/Klops/Basalt/Cave/Cave_001-002` — Klops cave rooms

2. **Needs verification (Dungeon/ category — may have PrefabSpawnerBlocks):**
   - `Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Basalt_1-4` — individual houses?
   - `Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Stone_1-4` — individual houses?
   - `Dungeon/Goblin_Lair/Prefabs_Goblin/Mushrooms/Small` — decorative?
   - `Dungeon/Goblin_Lair/Prefabs_Goblin/Cap/Dead_End` — corridor end?
   - `Dungeon/Goblin_Lair/Prefabs_Goblin/Cap/Hidden_Treasure` — treasure room?

3. **Almost certainly UNSAFE (compound/layout):**
   - Anything with "Layout" in the path
   - Anything with "Entrance" in the path
   - Room/Boss, Room/Large2_Goblin_City (multi-part)
   - Mine corridors (connected system)

---

## Atmosphere Concept

| Parameter | Approach A (Interior) | Approach B (Surface) |
|-----------|----------------------|---------------------|
| Environment | `Zone1_Underground` | `Env_Zone3_Overground` |
| Fog distance | [20, 50] (tight mine) | [60, 150] (mountain air) |
| Fog color | `#1a1510` (dusty brown) | `#a0b0c0` (mountain mist) |
| Particles | `Dust_Cave_Flies` (#a08040 — mine dust) | `Ash` (#c0c0d0 — mountain snow) |
| Tints | Brown/amber mine tones | Gray/blue mountain tones |

---

## TODO Before Building

- [ ] **Decision:** Approach A (interior mine) or Approach B (open mountain)?
- [ ] **Test:** Deploy `Cave/Nodes/Rock_Stone/Goblin` via RealmStructurePlacer to verify safe
- [ ] **Test:** Deploy `Dungeon/Goblin_Lair/Prefabs_Goblin/Houses/Basalt_1` via PrefabUtil.paste() — does it crash?
- [ ] **Test:** Deploy `Cave/Klops/Basalt/Main/Klops_Basalt_Main_001` via PrefabUtil.paste()
- [ ] **Design:** If interior, how to differentiate from Caverns visually (artificial mine vs natural cave)
- [ ] **Design:** Goblin_Duke 3-phase boss encounter flow
- [ ] **Verify:** Golem_Crystal_Thunder and Spirit_Thunder spawn successfully

---

## Related Files

- `docs/WorldgenV2/FACTION_CREATURE_DATABASE.md` — full mob/structure inventory
- `docs/WorldgenV2/BIOME_CAVERNS.md` — reference for ceiling implementation
- `src/.../maps/spawning/RealmStructurePlacer.java` — where to add MOUNTAINS entries
- `src/.../maps/spawning/BossStructurePlacer.java` — where to add MOUNTAINS boss camps
- `src/main/resources/config/realm-mobs.yml` — mob pool config
