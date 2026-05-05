# Hexcode

## Identity

| Field | Value |
|-------|-------|
| Author | Riprod ([itsriprod](https://github.com/itsriprod)) |
| Repository | https://github.com/itsriprod/hexcode |
| License | Unknown |
| Category | Plugin |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | `be0e97e` (v0.6.6) |
| Deployed Version | v0.6.0 |
| Last Updated | 2026-05-04 |
| Cloned From | main |

## What It Does

Visual spell-crafting system for Hytale. Players discover glyphs, arrange them into hexes (directed graphs), then cast spells through gesture-based drawing. Features 50+ glyphs, 4 obelisk types, 3 casting styles, 26 construct handlers, and a pedestal-anchored crafting system.

## What We Use

- **Spell damage pipeline** — route Hexcode spell damage through our RPG damage calculator (elemental resistances, crit, etc.)
- **Mana stat sync** — our attribute system computes maxMana, Hexcode reads from EntityStatMap
- **Ailment bridging** — Hexcode's Freeze/Ignite effects trigger our ailment system (burn, freeze)
- **Item classification** — HexStaff excluded from RPG loot tables via DynamicLootRegistry
- **Armor stat injection awareness** — both mods patch ItemArmor via reflection, need compat

## Our Integration

| File | Purpose |
|------|---------|
| `src/.../compat/HexcodeCompat.java` | Reflection-based bridge (~600 LOC) — state cleanup, damage attribution, asset map |
| `src/.../compat/HexcodeItemConfig.java` | Item classification for Hexcode items |
| `src/.../compat/HexcodeSkillTreeOverlay.java` | Skill tree overlay for magic nodes |
| `src/.../compat/HexcodeSpellConfig.java` | Spell scaling configuration |
| `src/.../compat/CastingAuraInjector.java` | Casting aura visual injection |
| `src/.../compat/HexcodeMetadataInjector.java` | BSON metadata injection (staff/book) |
| `src/.../compat/HexcodePedestalPlacer.java` | Pedestal placement in realms |
| `src/.../compat/HexDamageAttributionSystem.java` | 3-tier damage caster attribution |
| `src/.../compat/HexCastEventInterceptor.java` | HexCastEvent → ThreadLocal caster |
| `src/.../compat/HexCasterRegistry.java` | Construct/projectile caster registry |
| `src/.../compat/HexEntityTracker.java` | HolderSystem tracking hex entities |
| `src/.../compat/StatMapBridge.java` | Mana/stat sync to Hexcode's EntityStatMap |
| `docs/design/hexcode-compatibility-layer.md` | Full 6-phase integration design doc |

## Known Issues

- Armor stat injection conflict — both mods patch ItemArmor via reflection
- CRAFTING state persists across world transitions (no DrainPlayerFromWorldEvent listener in Hexcode)
- Casting state + teleport can leave orphaned constructs in wrong world

## Collaboration

Riprod is a friend of the project owner. Hexcode IS the magic system of Trail of Orbis's RPG world. Goal is full, seamless integration — not just compatibility.

## Update Instructions

```bash
./external/scripts/update-externals.sh hexcode
```
