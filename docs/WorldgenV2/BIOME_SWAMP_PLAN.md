# Swamp Biome — Planning Document

**Status:** NOT YET BUILT — stub exists in generation script
**Last updated:** April 20, 2026

---

## Current Stub Mob Pool (realm-mobs.yml)

| Mob | Weight | Status |
|-----|--------|--------|
| Fen_Stalker | 20% | ✅ Confirmed (Jungle) |
| Ghoul | 20% | ✅ Confirmed (Swamp/Corrupted) |
| Wraith | 20% | ✅ Confirmed (Swamp) |
| Crocodile | 15% | ✅ Confirmed (Jungle/Beach) |
| Snake_Marsh | 15% | ✅ Confirmed (Jungle) |
| Toad_Rhino | 10% | ✅ Confirmed (Jungle) |
| **Boss: Wraith_Lantern** | — | ✅ Confirmed (Swamp) |

## Mobs to Add — Confirmed Working April 20, 2026

| Mob | Category | Visual | Why Swamp |
|-----|----------|--------|-----------|
| **Zombie_Aberrant** | Undead/Zombie | Mutated/deformed zombie | "Aberrant" = swamp mutation, twisted by toxic water |
| **Zombie_Aberrant_Big** | Undead/Zombie | Large mutant variant | Massive swamp horror emerging from the muck |
| **Zombie_Aberrant_Small** | Undead/Zombie | Small mutant swarm | Swarm of tiny mutants (untested but same category) |

These were confirmed spawnable in Caverns testing (April 20, 2026). Their mutated/deformed aesthetic is PERFECT for a toxic swamp — the swamp water mutates everything it touches.

## Additional Swamp Candidates (untested)

| Mob | Category | Potential |
|-----|----------|-----------|
| Zombie (base) | Undead/Zombie | Generic swamp zombie |
| Zombie_Frost | Undead/Zombie | Wrong theme (ice) |
| Zombie_Burnt | Undead/Zombie | Wrong theme (fire) |
| Zombie_Sand | Undead/Zombie | Wrong theme (sand) |

## Proposed Full Swamp Pool

| Mob | Weight | Role |
|-----|--------|------|
| Ghoul | 18% | Fast melee undead |
| Zombie_Aberrant | 16% | Mutated swamp zombie |
| Wraith | 15% | Ghostly ranged |
| Fen_Stalker | 14% | Swamp horror stalker |
| Crocodile | 12% | Ambush from water |
| Zombie_Aberrant_Big | 10% | Large swamp mutant (rare heavy) |
| Snake_Marsh | 8% | Venomous swamp snake |
| Toad_Rhino | 7% | Massive swamp amphibian |
| **Boss: Wraith_Lantern** | — | Spectral lord of the swamp |

## Theme

**"The Rotting Marsh"** — toxic water mutates the dead into aberrant zombies. Fen Stalkers stalk through poisoned fog. Wraiths drift between dead trees. Crocodiles and toads lurk in the shallows. The Wraith_Lantern commands it all from the deepest bog.

## Terrain Notes

- Stub already in generation script with `signature_type="waterlogged"` (Water_Source, Y -5 to -1)
- Surface: Soil_Mud, Soil_Dirt, Soil_Grass (FieldFunction zones)
- Trees: Ash, Willow, Poisoned
- Twisted Wood/Poisoned as undergrowth
- Dense fog, green particles

## Related Files

- `docs/WorldgenV2/FACTION_CREATURE_DATABASE.md` — full mob inventory
- `docs/WorldgenV2/BIOME_ASSET_REFERENCE.md` — structure/prefab reference
