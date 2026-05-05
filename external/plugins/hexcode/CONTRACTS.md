# Hexcode — API Contract

## Version Tracking

| Layer | Version | Hash | Notes |
|-------|---------|------|-------|
| Baseline (vendor/) | v0.6.0 | — | Source our bridges were coded against |
| Deployed (server) | v0.6.0 | — | `Hexcode-0.6.0 (1).jar` |
| Source HEAD (external/) | v0.6.6 | `be0e97e` | 30 commits ahead of deployed |

## Reflected API Surface

Our `HexcodeCompat.java` and `StatMapBridge.java` reflect into these classes/methods:

| Class (FQCN) | Method/Field | Type | Bridge File |
|--------------|-------------|------|-------------|
| `com.riprod.hexcode.core.common.hexcaster.component.HexcasterComponent` | `getComponentType()` | static | HexcodeCompat.java |
| `com.riprod.hexcode.core.common.hexcaster.component.HexcasterComponent` | `getState()` | instance | HexcodeCompat.java |
| `com.riprod.hexcode.core.common.hexcaster.component.HexcasterComponent` | `requestStateChange(HexState)` | instance | HexcodeCompat.java |
| `com.riprod.hexcode.state.HexState` | enum constants (index 0 = IDLE) | enum | HexcodeCompat.java |
| `com.riprod.hexcode.core.common.construct.component.HexEffectsComponent` | `getComponentType()` | static | HexcodeCompat.java |
| `com.riprod.hexcode.core.common.construct.component.HexEffectsComponent` | `getEffects()` | instance | HexcodeCompat.java |
| `com.riprod.hexcode.core.state.execution.component.HexContext` | `getCasterRef()` | instance | HexcodeCompat.java |
| `com.riprod.hexcode.builtin.glyphs.projectile.component.ProjectileState` | `getComponentType()` | static | HexcodeCompat.java |
| `com.riprod.hexcode.builtin.glyphs.projectile.component.ProjectileState` | `getHexContext()` | instance | HexcodeCompat.java |
| `com.riprod.hexcode.builtin.glyphs.shatter.component.ShatterState` | `getComponentType()` | static | HexcodeCompat.java |
| `com.riprod.hexcode.builtin.glyphs.shatter.component.ShatterState` | `getHexContext()` | instance | HexcodeCompat.java |
| `com.riprod.hexcode.core.common.stats.HexcodeEntityStatTypes` | `getVolatility()` | static | StatMapBridge.java |
| `com.riprod.hexcode.core.common.stats.HexcodeEntityStatTypes` | `getMagicPower()` | static | StatMapBridge.java |
| `com.riprod.hexcode.core.common.stats.HexcodeEntityStatTypes` | `getMagicCharges()` | static | StatMapBridge.java |

## Watched Files (for diff on update)

```
src/main/java/com/riprod/hexcode/core/common/hexcaster/component/HexcasterComponent.java
src/main/java/com/riprod/hexcode/state/HexState.java
src/main/java/com/riprod/hexcode/core/common/construct/component/HexEffectsComponent.java
src/main/java/com/riprod/hexcode/core/state/execution/component/HexContext.java
src/main/java/com/riprod/hexcode/builtin/glyphs/projectile/component/ProjectileState.java
src/main/java/com/riprod/hexcode/builtin/glyphs/shatter/component/ShatterState.java
src/main/java/com/riprod/hexcode/core/common/stats/HexcodeEntityStatTypes.java
```

## Last Verification

- **Date**: 2026-05-04
- **Result**: ALL SAFE — zero changes to reflected API surface between v0.6.0 and v0.6.6
- **Upgrading deployed JAR to v0.6.6 is safe** for our integration
- **New in v0.6.6**: HexcasterComponent gained `trainingPackOverride` field + 3 methods (get/set/consume) — no impact on our reflection targets. ImbuementData gained `setHexFromValue()` and `resolveHex()` — no impact. HexBookComponent codec now uses `HexFieldCodec.PLAYER` instead of `Hex.CODEC`, and filters empty pages on save — no impact on our BSON injection.
