# Loot4Everyone

## Identity

| Field | Value |
|-------|-------|
| Author | MimStar ([MimStar](https://github.com/MimStar)) |
| Repository | https://github.com/MimStar/Loot4Everyone-Hytale |
| License | Unknown |
| Category | Plugin |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | `v1.3.6` / `9beec7d` |
| Last Updated | 2026-05-01 |
| Cloned From | main |

## What It Does

Loot distribution mod for Hytale. Ensures all party members receive loot from mob kills, not just the player who lands the killing blow.

## What We Use

- **Per-player chest API** — realm reward chests use L4E's player-instanced containers when available
- **Loot distribution compat** — our loot pipeline accounts for L4E's per-player distribution

## Our Integration

| File | Purpose |
|------|---------|
| `src/.../compat/Loot4EveryoneBridge.java` | Reflection-based bridge (~180 LOC), per-player chest API |
| `src/.../maps/rewards/RewardChestManager.java` | Uses bridge conditionally for realm reward chests |
| `src/.../loot/container/ContainerLootInterceptor.java` | Container loot compat with L4E instancing |

## Known Issues

- Soft dependency — standalone RewardChest system used if L4E is absent
- Need to verify container loot interception doesn't conflict with L4E instancing

## Update Instructions

```bash
./external/scripts/update-externals.sh loot4everyone
```
