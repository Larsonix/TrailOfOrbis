# Realm System Implementation Plan

> **Master reference for autonomous Claude Code execution.**
> After /clear, paste the autonomous execution prompt from the bottom of this file.

---

## Current Implementation Status

**Last Updated:** 2026-01-26

### Phase Summary

| Phase | Description | Status | Progress |
|-------|-------------|--------|----------|
| 0 | Prerequisites & Assets | COMPLETE | 100% |
| 1 | Foundation | COMPLETE | 100% |
| 2 | Stone System | PARTIAL | 40% |
| 3 | Instance Management | COMPLETE | 100% |
| 4 | Gameplay Systems | COMPLETE | 100% |
| 5 | Map Item & Drops | IN PROGRESS | 20% |
| 6 | UI & Polish | NOT STARTED | 0% |
| 7 | Database & Analytics | NOT STARTED | 0% |
| 8 | Testing & Validation | PARTIAL | 60% |

---

## Detailed Status by File

### Phase 2: Stone System (40% Complete)

**COMPLETED:**
- [x] `stones/ModifiableItem.java` - Sealed interface for gear/maps
- [x] `stones/ModifiableItemBuilder.java` - Builder interface
- [x] `stones/ItemModifier.java` - Common modifier interface
- [x] `stones/ItemTargetType.java` - GEAR_ONLY, MAP_ONLY, BOTH enum

**REMAINING:**
- [ ] `stones/StoneType.java` - All stone definitions enum
- [ ] `stones/StoneActions.java` - Effect implementations
- [ ] `stones/StoneService.java` - Public stone API
- [ ] `stones/StoneResult.java` - Sealed result type
- [ ] `stones/StoneDropListener.java` - Stone drops from mobs

---

### Phase 4: Gameplay Systems (100% Complete)

**BATCH 1 - COMPLETED (Spawning Basics):**
- [x] `maps/spawning/WeightedMob.java` - Weighted mob entry record (~200 lines)
- [x] `maps/spawning/BiomeMobPool.java` - Per-biome mob pools (~350 lines)
- [x] `maps/spawning/RealmMobCalculator.java` - Spawn count calculations (483 lines)
- [x] `maps/core/RealmCompletionTracker.java` - Kill/progress tracking (488 lines)

**BATCH 2 - COMPLETED (Modifier System):**
- [x] `maps/modifiers/RealmModifierPool.java` - Weighted two-tier selection (389 lines)
- [x] `maps/modifiers/RealmModifierRoller.java` - Value rolling with reroll support (401 lines)

**BATCH 3 - COMPLETED (ECS Components & Spawning):**
- [x] `maps/components/RealmMobComponent.java` - Marks mobs in realm (245 lines)
- [x] `maps/components/RealmPlayerComponent.java` - Marks players in realm (315 lines)
- [x] `maps/spawning/RealmMobSpawner.java` - Wave spawning system (611 lines)

**BATCH 4 - COMPLETED (Systems & Listeners):**
- [x] `maps/systems/RealmTimerSystem.java` - EntityTickingSystem timeout (200 lines)
- [x] `maps/systems/RealmModifierSystem.java` - HolderSystem stat modifiers (220 lines)
- [x] `maps/listeners/RealmMobDeathListener.java` - DeathSystems.OnDeathSystem (224 lines)

**BATCH 5 - COMPLETED (Rewards & Integration):**
- [x] `maps/reward/RealmRewardCalculator.java` - Calculate completion rewards (214 lines)
- [x] `maps/reward/RealmRewardService.java` - Distribute to players (233 lines)
- [x] `maps/reward/RealmRewardResult.java` - Reward result record (bonus file)
- [x] `maps/integration/RealmLootIntegration.java` - IIQ/IIR hooks for loot system (314 lines)
- [x] `maps/integration/RealmLevelingIntegration.java` - XP bonus application (258 lines)
- [x] `maps/integration/RealmScalingIntegration.java` - Fixed mob levels in realms (331 lines)

---

### Phase 5: Map Item & Drops (20% Complete)

**COMPLETED:**
- [x] `maps/items/RealmMapUtils.java` - ItemStack read/write utilities (352 lines)
- [x] `maps/items/RealmMapGenerator.java` - Generate new maps (550 lines)

**REMAINING:**
- [ ] `maps/listeners/RealmMapDropListener.java` - Map drops from mobs
- [ ] `maps/listeners/RealmMapUseListener.java` - Map item activation
- [ ] `maps/listeners/RealmEntryListener.java` - Portal entry handling
- [ ] `maps/listeners/RealmExitListener.java` - Exit/disconnect handling
- [ ] `maps/listeners/RealmPlayerDeathListener.java` - Player death in realm
- [ ] `maps/components/RealmMapComponent.java` - Item component for maps
- [ ] `maps/items/RealmMapItem.java` - Item type registration
- [ ] `maps/stones/MapStoneActions.java` - Cartographer, Horizon, Explorer stones

---

### Phase 6: UI & Polish (0% Complete)

**ALL REMAINING:**
- [ ] `maps/ui/RealmProgressUI.java` - In-realm progress display
- [ ] `maps/ui/RealmMapTooltipBuilder.java` - Map item tooltips
- [ ] `maps/systems/RealmProgressSystem.java` - Progress updates ECS system
- [ ] `maps/ui/RealmCompletionUI.java` - Completion screen

---

### Phase 7: Database & Analytics (0% Complete)

**ALL REMAINING:**
- [ ] `maps/database/RealmCompletionDAO.java` - Completion records
- [ ] `maps/database/RealmStatisticsDAO.java` - Aggregate stats
- [ ] `maps/database/RealmDatabaseSchema.java` - Table definitions

---

### Phase 8: Testing (60% Complete)

**EXISTING TESTS:**
- [x] `maps/config/` - Config loader tests
- [x] `maps/core/` - RealmMapData, RealmBiomeType, RealmCompletionTracker tests
- [x] `maps/instance/` - Instance lifecycle tests
- [x] `maps/modifiers/RealmModifierPoolTest.java` - Pool selection tests
- [x] `maps/modifiers/RealmModifierRollerTest.java` - Roller tests
- [x] `maps/spawning/WeightedMobTest.java` - Weighted mob tests
- [x] `maps/spawning/BiomeMobPoolTest.java` - Biome pool tests
- [x] `maps/spawning/RealmMobCalculatorTest.java` - Calculator tests
- [x] `maps/templates/` - Template tests
- [x] `maps/integration/RealmLootIntegrationTest.java` - Loot integration tests
- [x] `maps/integration/RealmLevelingIntegrationTest.java` - Leveling integration tests
- [x] `maps/integration/RealmScalingIntegrationTest.java` - Scaling integration tests

**REMAINING TESTS:**
- [ ] `maps/components/` - ECS component tests
- [ ] `maps/systems/` - ECS system tests (mock-heavy)
- [ ] `maps/listeners/` - Listener tests (mock-heavy)
- [ ] `maps/reward/RealmRewardCalculatorTest.java` - Reward calculation tests
- [ ] `maps/reward/RealmRewardServiceTest.java` - Service tests
- [ ] `maps/items/RealmMapGeneratorTest.java` - (Phase 5)
- [ ] `maps/items/RealmMapUtilsTest.java` - (Phase 5)
- [ ] Full integration tests

---

## Implementation Order (Remaining Work)

### NEXT: Phase 5 - Map Item & Drops

Full item system implementation:

```
maps/components/RealmMapComponent.java    # Item component for maps
maps/listeners/RealmMapDropListener.java  # Map drops from mobs
maps/listeners/RealmMapUseListener.java   # Map item activation
maps/listeners/RealmEntryListener.java    # Portal entry handling
maps/listeners/RealmExitListener.java     # Exit/disconnect handling
maps/listeners/RealmPlayerDeathListener.java
maps/items/RealmMapItem.java              # Item type registration
maps/items/RealmMapGenerator.java         # Generate new maps
maps/items/RealmMapUtils.java             # ItemStack read/write utilities
maps/stones/MapStoneActions.java          # Cartographer, Horizon, Explorer stones
```

### Phase 6: UI & Polish

Progress display and tooltips.

### Phase 7: Database

Persistence layer.

---

## Key Reference Files

### Documentation
- `maps/docs/01-overview.md` - System architecture
- `maps/docs/02-realm-maps.md` - Map mechanics
- `maps/docs/03-modifiers.md` - Modifier system
- `maps/docs/04-world-generation.md` - Templates
- `maps/docs/05-mob-spawning.md` - Spawning algorithms
- `maps/docs/06-currency.md` - Stones and economy
- `maps/docs/07-architecture.md` - Code architecture
- `maps/docs/09-implementation-roadmap.md` - Full roadmap

### Configuration
- `src/main/resources/config/realms.yml` - Main config
- `src/main/resources/config/realm-modifiers.yml` - Modifier pool
- `src/main/resources/config/realm-mobs.yml` - Mob pools per biome

### Existing Implementation (Reference)
- `maps/core/RealmMapData.java` - Map data structure (673 lines)
- `maps/spawning/RealmMobCalculator.java` - Spawn calculations
- `maps/spawning/BiomeMobPool.java` - Biome mob pools
- `maps/instance/RealmInstance.java` - Active realm state
- `maps/RealmsManager.java` - Central coordinator

### Hytale API Patterns
- `.claude/rules/hytale-api-patterns.md` - Quick API reference
- `/home/larsonix/work/Hytale-Decompiled-Full-Game/.index/` - Searchable indexes

---

## Quality Standards

### Code Quality
- Match existing patterns in the codebase
- Use HytaleLogger, never System.out
- Use EventRegistry.register(), not @EventHandler annotations
- Immutable records where possible
- Builder patterns for complex objects
- Full JavaDoc for public APIs

### Testing
- Unit tests for each new class
- Integration tests for system interactions
- Run `./gradlew test` after each batch
- All tests must pass before proceeding

### Error Handling
- Fail fast with clear error messages
- Use Optional for nullable returns
- Validate inputs at API boundaries
- Log errors with HytaleLogger.atSevere()

---

## Autonomous Execution Prompt

**Copy everything below this line after /clear:**

---

```
AUTONOMOUS IMPLEMENTATION MODE - REALM SYSTEM

Execute the remaining realm system implementation with zero intervention needed. Work until complete or context limit.

CRITICAL INSTRUCTIONS:

1. READ FIRST, ALWAYS:
   - Start by reading docs/REALM_IMPLEMENTATION_PLAN.md for current status
   - Read relevant existing code to understand patterns
   - Read maps/docs/ documentation when implementing new features

2. DOCUMENTATION ON UNCERTAINTY:
   - When uncertain about Hytale APIs, ALWAYS search /home/larsonix/work/Hytale-Decompiled-Full-Game/.index/
   - Use CLASS_INDEX.txt for class lookups
   - Use METHOD_INDEX.txt for method lookups
   - Read .claude/rules/hytale-api-patterns.md for common patterns

3. QUALITY OVER SPEED:
   - Take time to write clean, well-tested code
   - Run ./gradlew test after each batch
   - Fix issues before proceeding to next batch

4. TRACK EVERYTHING:
   - Use TodoWrite constantly to track progress
   - Before major milestones, update docs/REALM_IMPLEMENTATION_PLAN.md with current status
   - This ensures recovery after compaction

5. SELF-CORRECT:
   - If tests fail, investigate and fix
   - If approach doesn't work, step back and try another
   - Never leave broken code

6. COMPACTION RESILIENCE:
   - Update REALM_IMPLEMENTATION_PLAN.md status after completing each batch
   - Mark files as [x] completed in the checklist
   - This is your recovery point

7. NO QUESTIONS:
   - Make reasonable decisions autonomously
   - Only stop if something is truly ambiguous

QUALITY STANDARDS:
- Match existing code patterns
- Comprehensive tests (aim for 90%+ coverage of logic)
- Proper error handling and logging (HytaleLogger)
- JavaDoc for public APIs
- Follow Hytale event patterns (EventRegistry.register)

CURRENT STATE: Read docs/REALM_IMPLEMENTATION_PLAN.md for exact status

START BY:
1. Read docs/REALM_IMPLEMENTATION_PLAN.md
2. Identify next incomplete batch
3. Read relevant documentation in maps/docs/
4. Implement the batch
5. Write tests
6. Run tests
7. Update plan file
8. Continue to next batch
```

---

## Update Log

| Date | Update |
|------|--------|
| 2026-01-25 | Initial plan created. Phase 1, 3 complete. Phase 4 Batch 1 complete. Tests fixed. |
| 2026-01-26 | **Major status correction**: Phase 4 was actually 85% complete (not 30%). Batches 2-4 fully implemented. Batch 5 rewards done (reward/ package), only 3 integration files remain. Updated all checkboxes and line counts. |
| 2026-01-26 | **Phase 4 COMPLETE**: Created 3 integration classes (RealmLootIntegration, RealmLevelingIntegration, RealmScalingIntegration) with full test coverage (63 tests). Added configurable multiplier clamps to RealmsConfig (xpMultiplierMin/Max, iiqMultiplierMin/Max, iirMultiplierMin/Max). Testing now at 60%. |
| 2026-01-26 | **Phase 5 Started**: Created RealmMapUtils.java (352 lines) with 40 unit tests. Foundation for reading/writing RealmMapData to ItemStack metadata. |
| 2026-01-26 | **Phase 5 Batch 2**: Created RealmMapGenerator.java (550 lines) with 48 unit tests. Full procedural map generation with rarity rolling, quality, biome selection, size weighting, and modifier generation. |

---

*This document is the single source of truth for realm implementation status.*
