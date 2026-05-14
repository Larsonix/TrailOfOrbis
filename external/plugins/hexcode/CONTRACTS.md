# Hexcode — API Contract

## Version Tracking

| Layer | Version | Hash | Notes |
|-------|---------|------|-------|
| Baseline (vendor/) | v0.6.0 | — | Original analysis source |
| compileOnly JAR | v0.7.0 | — | `libs/hexcode-0.7.0.jar` |
| Deployed (server) | v0.6.7 | — | `Hexcode-0.6.7 (1).jar` — pending v0.7.0 deploy |
| Source HEAD (external/) | v0.7.0 | `d4cee69` | 63 commits ahead of v0.6.0 |

## Integration Method: compileOnly (Direct Imports)

**As of May 9 2026**, the Hexcode integration uses `compileOnly` Gradle dependency with direct Java imports. **Zero reflection for core operations.** If Hexcode renames or removes any class/method, the **build fails immediately** with a clear compiler error — no more silent runtime degradation.

The only remaining reflection is for `DefaultAssetMap` internals (Hytale private API, not Hexcode).

## Direct Import Surface

Our `HexcodeBridgeImpl.java` and ECS system files import these Hexcode classes directly:

| Class (FQCN) | Used In | Purpose |
|--------------|---------|---------|
| `com.riprod.hexcode.core.common.hexcaster.component.HexcasterComponent` | HexcodeBridgeImpl, CastingAuraInjector | Casting state detection + reset |
| `com.riprod.hexcode.state.HexState` | HexcodeBridgeImpl, CastingAuraInjector | IDLE/CASTING enum comparison |
| `com.riprod.hexcode.core.common.construct.component.HexEffectsComponent` | HexcodeBridgeImpl, HexEntityTracker | Construct entity detection |
| `com.riprod.hexcode.core.common.construct.component.HexStatus` | HexcodeBridgeImpl, HexEntityTracker | Caster extraction from constructs |
| `com.riprod.hexcode.core.state.execution.component.HexContext` | HexcodeBridgeImpl, HexEntityTracker | getCasterRef() for attribution |
| `com.riprod.hexcode.builtin.glyphs.projectile.component.ProjectileState` | HexcodeBridgeImpl, HexEntityTracker | Projectile entity detection |
| `com.riprod.hexcode.builtin.glyphs.shatter.component.ShatterState` | HexcodeBridgeImpl, HexEntityTracker | Shatter entity detection |
| `com.riprod.hexcode.core.common.hexstaff.component.HexStaffAsset` | HexcodeBridgeImpl, CastingAuraInjector | Asset map registration + particles |
| `com.riprod.hexcode.core.common.hexbook.component.HexBookAsset` | HexcodeBridgeImpl | Book asset registration |
| `com.riprod.hexcode.core.common.stats.HexcodeEntityStatTypes` | HexcodeBridgeImpl | Stat index resolution |
| `com.riprod.hexcode.api.event.HexCastEvent` | HexCastEventInterceptor | Spell cast interception (v0.7.0: getContext() returns HexContext) |
| `com.riprod.hexcode.core.state.casting.component.HexcasterCastingComponent` | CastingAuraInjector | Casting root ref for particles |

## Watched Files (for diff on Hexcode update)

Any change to these files requires rebuilding TrailOfOrbis (compileOnly will catch breaks):

```
src/main/java/com/riprod/hexcode/core/common/hexcaster/component/HexcasterComponent.java
src/main/java/com/riprod/hexcode/state/HexState.java
src/main/java/com/riprod/hexcode/core/common/construct/component/HexEffectsComponent.java
src/main/java/com/riprod/hexcode/core/state/execution/component/HexContext.java
src/main/java/com/riprod/hexcode/builtin/glyphs/projectile/component/ProjectileState.java
src/main/java/com/riprod/hexcode/builtin/glyphs/shatter/component/ShatterState.java
src/main/java/com/riprod/hexcode/core/common/stats/HexcodeEntityStatTypes.java
src/main/java/com/riprod/hexcode/api/event/HexCastEvent.java
src/main/java/com/riprod/hexcode/core/state/casting/component/HexcasterCastingComponent.java
```

## Glyph Patching (Spell Balance)

We register wrapper GlyphHandlers via `GlyphRegistry.register()` for glyphs whose user-controllable slots don't scale volatility cost:

| Glyph | Slot | Default | Wrapper File |
|-------|------|---------|-------------|
| Bolt | power | 5.0 | `compat/glyph/CostScaledGlyphWrapper.java` |
| Gust | magnitude | 20.0 | same |
| Projectile | speed | 30.0 | same |

**Watched for patching** (if Hexcode adds new damage glyphs without cost scaling):
```
src/main/java/com/riprod/hexcode/core/common/glyphs/registry/GlyphRegistry.java
src/main/java/com/riprod/hexcode/core/common/glyphs/component/GlyphHandler.java
src/main/java/com/riprod/hexcode/core/state/execution/component/VolatilityTracker.java
```

## Last Verification

- **Date**: 2026-05-14
- **Method**: compileOnly build (direct imports, zero reflection) + grep audits
- **Result**: BUILD SUCCESSFUL — all classes resolve, all tests pass, glyph patches active
- **v0.7.0 migration**: CastingEventData removed, replaced by HexContext.getPowerMultiplier()/getVolatilityTracker(). HexCastEvent.getWielderRef() replaced by getContext().getCasterRef().
- **New**: ExecutionId-based damage attribution (deterministic fallback for stale construct refs)
- **Removed**: hex_combust damage source (CombustGlyph deleted in v0.7.0)
