# PartyPro

## Identity

| Field | Value |
|-------|-------|
| Author | Tsum0ri ([Tsum0ri](https://github.com/Tsum0ri)) |
| Repository | https://github.com/Tsum0ri/PartyPro_Hytale |
| License | Unknown |
| Category | Plugin |

## Tracking

| Field | Value |
|-------|-------|
| Current Version | `28b8ff4` |
| Last Updated | 2026-05-01 |
| Cloned From | main |

## What It Does

Party system for Hytale — allows players to form groups, see party member health/status, and coordinate gameplay. Provides APIs for other mods to query party membership.

## What We Use

- **Party membership queries** — check if players are in the same party for XP sharing
- **Party HUD sync** — display party member health/level on our HUD
- **XP sharing** — distribute XP gains among nearby party members

## Our Integration

| File | Purpose |
|------|---------|
| `src/.../compat/party/PartyBridge.java` | Abstraction layer over party system |
| `src/.../compat/party/PartyProReflectionBridge.java` | Reflection-based bridge to PartyPro API |
| `src/.../compat/party/NoOpPartyBridge.java` | Fallback when PartyPro isn't loaded |
| `src/.../compat/party/PartyIntegrationManager.java` | Detection and bridge initialization |
| `src/.../compat/party/PartyHudSync.java` | Party HUD data sync |
| `src/.../compat/party/PartyConfig.java` | Party feature configuration |
| `src/.../leveling/xp/SimplePartyXpSharing.java` | XP distribution among party members |

## Known Issues

- Integration is via reflection (no compile-time dependency) — API changes in PartyPro can silently break things
- Need to verify party API stability on each update

## Update Instructions

```bash
./external/scripts/update-externals.sh partypro
```
