# PartyPro — API Contract

## Version Tracking

| Layer | Version | Hash | Notes |
|-------|---------|------|-------|
| Baseline (vendor/) | v0.9.0 | — | Decompiled JAR — full class source |
| Deployed (server) | v0.9.0 | — | `partypro-0.9.0.jar` |
| Source HEAD (external/) | — | `28b8ff4` | API docs only (2 commits) — NOT real source |

## Source Reliability

The GitHub repo (`Tsum0ri/PartyPro_Hytale`) contains only API documentation, not the plugin source. Our bridge was coded from the **vendor/ decompilation** of the deployed JAR. Contract verification must be done against vendor/, not external/.

## Reflected API Surface

Our `PartyProReflectionBridge.java` reflects into these classes/methods:

| Class (FQCN) | Method/Field | Type | Signature |
|--------------|-------------|------|-----------|
| `me.tsumori.partypro.api.PartyProAPI` | `isAvailable()` | static | `() -> boolean` |
| `me.tsumori.partypro.api.PartyProAPI` | `getInstance()` | static | `() -> PartyProAPI` |
| `me.tsumori.partypro.api.PartyProAPI` | `isInParty(UUID)` | instance | `(UUID) -> boolean` |
| `me.tsumori.partypro.api.PartyProAPI` | `areInSameParty(UUID, UUID)` | instance | `(UUID, UUID) -> boolean` |
| `me.tsumori.partypro.api.PartyProAPI` | `getPartyByPlayer(UUID)` | instance | `(UUID) -> PartySnapshot` |
| `me.tsumori.partypro.api.PartyProAPI` | `getPartyMembers(UUID)` | instance | `(UUID) -> Set<UUID>` |
| `me.tsumori.partypro.api.PartyProAPI` | `getOnlinePartyMembers(UUID)` | instance | `(UUID) -> Set<UUID>` |
| `me.tsumori.partypro.api.PartyProAPI` | `getPartyLeader(UUID)` | instance | `(UUID) -> UUID` |
| `me.tsumori.partypro.api.PartyProAPI` | `setPlayerCustomText(UUID, String, String)` | instance | `(uuid, key, text)` |
| `me.tsumori.partypro.api.PartyProAPI` | `clearPlayerCustomText(UUID)` | instance | `(UUID) -> void` |
| `me.tsumori.partypro.api.PartyProAPI` | `registerListener(PartyEventListener)` | instance | `(listener) -> void` |
| `me.tsumori.partypro.api.PartyProAPI` | `unregisterListener(PartyEventListener)` | instance | `(listener) -> void` |
| `me.tsumori.partypro.api.PartyEventListener` | (interface) | dynamic proxy | Implemented via `Proxy.newProxyInstance()` |
| `me.tsumori.partypro.api.PartySnapshot` | `id()` | instance | `() -> UUID` |
| `me.tsumori.partypro.api.PartySnapshot` | `leader()` | instance | `() -> UUID` |
| `me.tsumori.partypro.api.PartySnapshot` | `getAllMembers()` | instance | `() -> Set<UUID>` |
| `me.tsumori.partypro.api.PartySnapshot` | `pvpEnabled()` | instance | `() -> boolean` |

## Watched Files (in vendor/PartyPro/decompiled/)

```
me/tsumori/partypro/api/PartyProAPI.java
me/tsumori/partypro/api/PartyEventListener.java
me/tsumori/partypro/api/PartySnapshot.java
```

## Last Verification

- **Date**: 2026-05-03
- **Result**: Aligned — vendor/ and deployed are both v0.9.0
- **Cannot auto-verify from external/** — GitHub repo is docs-only. Must re-decompile JAR on version bump.
