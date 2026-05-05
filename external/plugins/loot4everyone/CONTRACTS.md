# Loot4Everyone — API Contract

## Version Tracking

| Layer | Version | Hash | Notes |
|-------|---------|------|-------|
| Baseline (vendor/) | v1.3.0 | — | Source bridges were originally coded against |
| Deployed (server) | v1.3.6 | — | `Loot4Everyone-1.3.6.jar` |
| Source HEAD (external/) | v1.3.6+ | `9beec7d` | 1 commit past 1.3.6 (README) |

## Reflected API Surface

Our `Loot4EveryoneBridge.java` reflects into these classes/methods:

### Core API (v1.3.0+)

| Class (FQCN) | Method/Field | Type | Signature |
|--------------|-------------|------|-----------|
| `org.mimstar.plugin.Loot4Everyone` | `get()` | static | `() -> Loot4Everyone` |
| `org.mimstar.plugin.Loot4Everyone` | `getlootChestTemplateResourceType()` | instance | `() -> ResourceType<ChunkStore, LootChestTemplate>` |
| `org.mimstar.plugin.Loot4Everyone` | `getPlayerLootcomponentType()` | instance | `() -> ComponentType<EntityStore, PlayerLoot>` |
| `org.mimstar.plugin.resources.LootChestTemplate` | `saveTemplate(int, int, int, List, String)` | instance | `(x, y, z, items, dropList)` |
| `org.mimstar.plugin.resources.LootChestTemplate` | `removeTemplate(int, int, int)` | instance | `(x, y, z)` |
| `org.mimstar.plugin.resources.LootChestTemplate` | `hasTemplate(int, int, int)` | instance | `(x, y, z) -> boolean` |
| `org.mimstar.plugin.components.PlayerLoot` | `setInventory(int, int, int, String, List)` | instance | `(x, y, z, worldName, items)` |
| `org.mimstar.plugin.components.PlayerLoot` | `resetChest(int, int, int, String)` | instance | `(x, y, z, worldName)` |

### World Config API (v1.3.6+ — optional, graceful degradation)

| Class (FQCN) | Method/Field | Type | Signature |
|--------------|-------------|------|-----------|
| `org.mimstar.plugin.Loot4Everyone` | `getLootChestConfigResourceType()` | instance | `() -> ResourceType<ChunkStore, LootChestConfig>` |
| `org.mimstar.plugin.resources.LootChestConfig` | `setCanPlayerBreakLootChests(boolean)` | instance | `(breakable)` |
| `org.mimstar.plugin.resources.LootChestConfig` | `setParticlesAppear(boolean)` | instance | `(show)` |
| `org.mimstar.plugin.resources.LootChestConfig` | `setParticlesColor(String)` | instance | `(hexColor)` — format: `"#rrggbbaa"` |
| `org.mimstar.plugin.resources.LootChestConfig` | `setMessageAppear(boolean)` | instance | `(show)` |

### Also Used (ContainerLootInterceptor — not in bridge)

| Class (FQCN) | Method/Field | Type | Used In |
|--------------|-------------|------|---------|
| `org.mimstar.plugin.resources.LootChestTemplate` | `hasTemplate(int, int, int)` | instance | ContainerLootInterceptor.java (inline reflection) |

## Watched Files (for diff on update)

```
src/main/java/org/mimstar/plugin/Loot4Everyone.java
src/main/java/org/mimstar/plugin/resources/LootChestTemplate.java
src/main/java/org/mimstar/plugin/components/PlayerLoot.java
src/main/java/org/mimstar/plugin/resources/LootChestConfig.java
```

## Last Verification

- **Date**: 2026-05-04
- **Result**: ALL SAFE — all reflected methods verified present in v1.3.6 source
- **New**: Added LootChestConfig API (v1.3.6+) for realm world configuration
- **vendor/ should be updated to v1.3.6** to match deployed
