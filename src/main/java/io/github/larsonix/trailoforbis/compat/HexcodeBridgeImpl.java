package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.assetstore.codec.AssetCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// Direct Hexcode imports — this class is ONLY loaded when Hexcode is present.
// Java's lazy class loading guarantees this: HexcodeCompatManager never references
// this class unless PluginManager confirmed Hexcode is installed.
import com.riprod.hexcode.api.event.HexCastEvent;
import com.riprod.hexcode.core.common.hexcaster.component.HexcasterComponent;
import com.riprod.hexcode.state.HexState;
import com.riprod.hexcode.core.common.construct.component.HexEffectsComponent;
import com.riprod.hexcode.core.common.construct.component.HexStatus;
import com.riprod.hexcode.core.state.execution.component.HexContext;
import com.riprod.hexcode.builtin.glyphs.projectile.component.ProjectileState;
import com.riprod.hexcode.builtin.glyphs.shatter.component.ShatterState;
import com.riprod.hexcode.core.common.hexstaff.component.HexStaffAsset;
import com.riprod.hexcode.core.common.hexbook.component.HexBookAsset;
import com.riprod.hexcode.core.common.stats.HexcodeEntityStatTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Real Hexcode integration with direct Java imports — zero reflection for core operations.
 *
 * <p><b>Class-loading safety:</b> This class imports Hexcode classes directly.
 * It is ONLY instantiated by {@link HexcodeCompatManager} after confirming Hexcode
 * is present via {@code PluginManager.get().getPlugin()}. Java's lazy class loading
 * ensures this class is never loaded when Hexcode is absent.
 *
 * <p>The only remaining reflection is for asset map registration, which accesses
 * {@code DefaultAssetMap} internals (private fields) and asset cloning. This is
 * Hytale API fragility, not Hexcode fragility.
 */
final class HexcodeBridgeImpl implements HexcodeBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Entity tracking state
    private volatile boolean trackingInitialized = false;

    // Stat indices (resolved once via direct Hexcode API)
    private volatile int volatilityIndex = Integer.MIN_VALUE;
    private volatile int magicPowerIndex = Integer.MIN_VALUE;
    private volatile int magicChargesIndex = Integer.MIN_VALUE;
    private volatile boolean statIndicesResolved = false;

    // Asset map state
    private volatile boolean assetMapsInitialized = false;

    HexcodeBridgeImpl() {
        LOGGER.atInfo().log("[HexcodeBridge] Direct Hexcode integration active (compileOnly — zero reflection)");
    }

    // ── Detection ──

    @Override
    public boolean isLoaded() {
        return true; // This impl only exists when Hexcode is loaded
    }

    @Nullable
    @Override
    public String getVersion() {
        // Version detected by HexcodeCompatManager from plugin manifest
        return null; // Manager provides the version string
    }

    // ── ECS System Registration ──

    @Override
    public void registerEcsSystems(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        // HexCastEvent handler — writes to HexCastStateStore, delegates echo to HexSpellEchoService
        registry.registerSystem(new HexCastEventHandler(HexCastEvent.class));
        LOGGER.atInfo().log("[HexcodeBridge] Registered HexCastEventHandler (unified state store)");

        // Initialize hex entity tracking components
        initializeHexTracking();

        // HexEntityTracker — watches for construct/projectile entities
        registry.registerSystem(new HexEntityTracker());
        LOGGER.atInfo().log("[HexcodeBridge] Registered HexEntityTracker");

        // HexDamageAttributor — 4-tier caster attribution backed by HexCastStateStore
        registry.registerSystem(new HexDamageAttributor());
        LOGGER.atInfo().log("[HexcodeBridge] Registered HexDamageAttributor");

        // CastingAuraInjector + tick system
        CastingAuraInjector.initialize();
        registry.registerSystem(new CastingAuraTickSystem());
        LOGGER.atInfo().log("[HexcodeBridge] Registered CastingAuraTickSystem");

        // Patch exploitable glyphs with cost-scaled volatility wrappers.
        // Must run AFTER Hexcode's BuiltinPlugin.register() (which runs during setup()).
        // Our start() runs after all plugins' setup(), so this is guaranteed.
        io.github.larsonix.trailoforbis.compat.glyph.HexGlyphPatcher.patchAll();
    }

    // ── Casting State Management ──

    @Override
    public void forceIdleIfCasting(@Nonnull Holder<EntityStore> holder) {
        try {
            HexcasterComponent hexcaster = holder.getComponent(HexcasterComponent.getComponentType());
            if (hexcaster == null) return;

            HexState currentState = hexcaster.getState();
            if (currentState == HexState.IDLE) return;

            hexcaster.requestStateChange(HexState.IDLE);
            LOGGER.atFine().log("[HexcodeBridge] Reset Hexcode state from %s to IDLE on world drain", currentState);
        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeBridge] Failed to reset Hexcode state: %s", e.getMessage());
        }
    }

    @Override
    public boolean isCurrentlyCasting(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        try {
            HexcasterComponent hexcaster = store.getComponent(playerRef, HexcasterComponent.getComponentType());
            if (hexcaster == null) return false;
            return hexcaster.getState() != HexState.IDLE;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Entity Tracking ──

    @Override
    public boolean isTrackingInitialized() {
        return trackingInitialized;
    }

    @Override
    public void initializeHexTracking() {
        if (trackingInitialized) return;

        // Direct component type access — no reflection needed
        boolean constructOk = HexEffectsComponent.getComponentType() != null;
        boolean projectileOk = ProjectileState.getComponentType() != null;
        boolean shatterOk = ShatterState.getComponentType() != null;

        LOGGER.atInfo().log("[HexcodeBridge] Entity tracking initialized — constructs=%s, projectiles=%s, shatter=%s",
                constructOk ? "OK" : "MISSING",
                projectileOk ? "OK" : "MISSING",
                shatterOk ? "OK" : "MISSING");

        trackingInitialized = true;
    }

    @Nullable
    @Override
    public ComponentType<EntityStore, ?> getHexEffectsComponentType() {
        return HexEffectsComponent.getComponentType();
    }

    @Nullable
    @Override
    public ComponentType<EntityStore, ?> getProjectileStateComponentType() {
        return ProjectileState.getComponentType();
    }

    @Nullable
    @Override
    public ComponentType<EntityStore, ?> getShatterStateComponentType() {
        return ShatterState.getComponentType();
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public Ref<EntityStore> extractConstructCasterRef(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef) {

        try {
            HexEffectsComponent hexEffects = store.getComponent(entityRef, HexEffectsComponent.getComponentType());
            if (hexEffects == null) return null;

            Map<?, HexStatus<?>> effects = hexEffects.getEffects();
            if (effects == null || effects.isEmpty()) return null;

            for (HexStatus<?> status : effects.values()) {
                HexContext ctx = status.getHexContext();
                if (ctx == null) continue;
                Ref<EntityStore> casterRef = ctx.getCasterRef();
                if (casterRef != null && casterRef.isValid()) {
                    return casterRef;
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeBridge] Failed to extract construct caster: %s", e.getMessage());
        }
        return null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public Ref<EntityStore> extractProjectileCasterRef(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef) {

        // Try ProjectileState first
        try {
            ProjectileState projState = store.getComponent(entityRef, ProjectileState.getComponentType());
            if (projState != null) {
                HexContext ctx = projState.getHexContext();
                if (ctx != null) {
                    Ref<EntityStore> ref = ctx.getCasterRef();
                    if (ref != null && ref.isValid()) return ref;
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeBridge] ProjectileState read failed: %s", e.getMessage());
        }

        // Try ShatterState
        try {
            ShatterState shatterState = store.getComponent(entityRef, ShatterState.getComponentType());
            if (shatterState != null) {
                HexContext ctx = shatterState.getHexContext();
                if (ctx != null) {
                    Ref<EntityStore> ref = ctx.getCasterRef();
                    if (ref != null && ref.isValid()) return ref;
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeBridge] ShatterState read failed: %s", e.getMessage());
        }

        return null;
    }

    // ── Stat Index Resolution ──

    @Override
    public void resolveStatIndices() {
        if (statIndicesResolved) return;

        try {
            volatilityIndex = HexcodeEntityStatTypes.getVolatility();
            magicPowerIndex = HexcodeEntityStatTypes.getMagicPower();
            magicChargesIndex = HexcodeEntityStatTypes.getMagicCharges();

            LOGGER.atInfo().log("[HexcodeBridge] Stat indices resolved: Volatility=%d, MagicPower=%d, MagicCharges=%d",
                    volatilityIndex, magicPowerIndex, magicChargesIndex);
        } catch (Exception e) {
            LOGGER.atWarning().log("[HexcodeBridge] Failed to resolve stat indices: %s", e.getMessage());
        }

        statIndicesResolved = true;
    }

    @Override public int getVolatilityStatIndex() { return volatilityIndex; }
    @Override public int getMagicPowerStatIndex() { return magicPowerIndex; }
    @Override public int getMagicChargesStatIndex() { return magicChargesIndex; }

    // ── Asset Map Registration ──
    // This section still uses reflection for DefaultAssetMap internals (Hytale private API).
    // The Hexcode-specific parts (HexStaffAsset, HexBookAsset) use direct imports.

    @Override
    public void initializeHexAssetMaps() {
        if (assetMapsInitialized) return;

        try {
            DefaultAssetMap<?, ?> staffMap = HexStaffAsset.getAssetMap();
            DefaultAssetMap<?, ?> bookMap = HexBookAsset.getAssetMap();
            LOGGER.atInfo().log("[HexcodeBridge] Hex asset maps initialized (staff=%s, book=%s)",
                    staffMap != null, bookMap != null);
        } catch (Exception e) {
            LOGGER.atWarning().log("[HexcodeBridge] Failed to initialize hex asset maps: %s", e.getMessage());
        }

        assetMapsInitialized = true;
    }

    @Override
    public void registerHexAsset(
            @Nonnull String customItemId,
            @Nonnull String baseItemId,
            boolean isHexStaff) {

        if (!assetMapsInitialized) return;

        try {
            DefaultAssetMap<String, ?> assetMap = isHexStaff
                    ? HexStaffAsset.getAssetMap()
                    : HexBookAsset.getAssetMap();
            if (assetMap == null) return;

            String assetTypeName = isHexStaff ? "HexStaffAsset" : "HexBookAsset";

            // 1. Resolve the base asset via public API
            Object baseAsset = assetMap.getAsset(baseItemId);

            if (baseAsset == null) {
                // Vanilla base items have no hex asset. Fall back to a known Hexcode item.
                String[] fallbacks = isHexStaff
                        ? new String[]{"Hexstaff_Basic_Crude", "Hexstaff_Basic_Copper", "Hexstaff_Basic_Iron"}
                        : new String[]{"Hex_Book", "Fire_Hexbook", "Arcane_Hexbook"};

                for (String fallbackId : fallbacks) {
                    baseAsset = assetMap.getAsset(fallbackId);
                    if (baseAsset != null) {
                        LOGGER.atFine().log("[HexcodeBridge] Using fallback %s '%s' for vanilla base '%s'",
                                assetTypeName, fallbackId, baseItemId);
                        break;
                    }
                }

                if (baseAsset == null) {
                    LOGGER.atFine().log("[HexcodeBridge] No %s found (base '%s', no fallback), skipping '%s'",
                            assetTypeName, baseItemId, customItemId);
                    return;
                }
            }

            // 2. Register via DefaultAssetMap.putAll() — Hytale reflection (not Hexcode)
            Object assetStore = isHexStaff ? HexStaffAsset.getAssetStore() : HexBookAsset.getAssetStore();
            java.lang.reflect.Method getCodecMethod = assetStore.getClass().getMethod("getCodec");
            @SuppressWarnings("rawtypes")
            AssetCodec codec = (AssetCodec) getCodecMethod.invoke(assetStore);

            Object clonedAsset = cloneAssetWithId(baseAsset, customItemId);
            Map<String, Object> loadedAssets = new HashMap<>();
            loadedAssets.put(customItemId, clonedAsset);

            java.lang.reflect.Method putAllMethod = DefaultAssetMap.class.getDeclaredMethod("putAll",
                    String.class, AssetCodec.class, Map.class, Map.class, Map.class);
            putAllMethod.setAccessible(true);

            putAllMethod.invoke(assetMap,
                    "TrailOfOrbis:TrailOfOrbis",
                    codec,
                    loadedAssets,
                    Collections.emptyMap(),
                    Collections.emptyMap());

            // 3. Verify
            Object verified = assetMap.getAsset(customItemId);
            LOGGER.atFine().log("[HexcodeBridge] Registered %s '%s' → '%s' [%s]",
                    assetTypeName, customItemId, baseItemId,
                    verified != null ? "OK" : "FAILED");

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[HexcodeBridge] Failed to register hex asset '%s' (base '%s')",
                    customItemId, baseItemId);
        }
    }

    @Override
    public void diagCheckHexAsset(@Nonnull String itemId) {
        try {
            DefaultAssetMap<String, ?> staffMap = HexStaffAsset.getAssetMap();
            if (staffMap == null) return;

            Object result = staffMap.getAsset(itemId);
            LOGGER.atFine().log("[HexcodeBridge-DIAG] Check '%s': %s", itemId,
                    result != null ? "FOUND" : "NOT FOUND");
        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeBridge-DIAG] Check '%s': ERROR %s", itemId, e.getMessage());
        }
    }

    // ── Internal Helpers ──

    /**
     * Creates a shallow copy of a HexStaffAsset/HexBookAsset with a different ID.
     * Still uses reflection on the asset class (Hytale API, not Hexcode-specific).
     */
    private static Object cloneAssetWithId(@Nonnull Object original, @Nonnull String newId) throws Exception {
        Class<?> assetClass = original.getClass();

        java.lang.reflect.Constructor<?> ctor = assetClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object clone = ctor.newInstance();

        for (Field field : assetClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) continue;
            field.setAccessible(true);
            field.set(clone, field.get(original));
        }

        Field idField = assetClass.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(clone, newId);

        return clone;
    }

    // ── Glyph Base Power Cache ──

    /** Maps hex damage source type (e.g., "hex_bolt") → GlyphAsset.basePower. */
    private final Map<String, Float> glyphBasePowerCache = new HashMap<>();

    /**
     * Known damage slot names per glyph type.
     * Each entry maps a GlyphAsset ID to the slot that controls damage output.
     * Only glyphs with player-modifiable damage slots need baselines.
     *
     * Verified from source (v0.7.0):
     *   Bolt     → "power"     (readSlot × magicPowerMultiplier)
     *   Gust     → "magnitude" (explosion damage via ExplosionUtils)
     *   Ensnare  → "damage"    (spike damage in construct ticks)
     *   Phase    → "intensity" (crush damage on phase restore)
     *
     * Glaciate: "offset" slot controls spawn height → velocity → damage.
     *   Higher offset → higher fall velocity → more damage. Normalized by slot default
     *   like any other glyph (default 10.0, same as HexGlyphPatcher).
     * Shatter has no damage slot (spawns projectile shard entities).
     */
    private static final Map<String, String> GLYPH_DAMAGE_SLOT_NAMES = Map.of(
        "Bolt", "power",
        "Gust", "magnitude",
        "Ensnare", "damage",
        "Phase", "intensity",
        "Glaciate", "offset"
    );

    @Override
    public void cacheGlyphBasePowers() {
        glyphBasePowerCache.clear();
        try {
            var assetMap = com.riprod.hexcode.core.common.glyphs.registry.GlyphAsset.getAssetMap();
            if (assetMap == null) {
                LOGGER.atWarning().log("[HexBridge] GlyphAsset.getAssetMap() returned null");
                return;
            }

            Map<String, ?> assets = assetMap.getAssetMap();
            if (assets == null || assets.isEmpty()) {
                LOGGER.atWarning().log("[HexBridge] GlyphAsset map is empty");
                return;
            }

            StringBuilder summary = new StringBuilder();
            for (var entry : assets.entrySet()) {
                String glyphId = entry.getKey();
                Object asset = entry.getValue();
                if (!(asset instanceof com.riprod.hexcode.core.common.glyphs.registry.GlyphAsset glyphAsset)) continue;

                // Check if this glyph has a known damage slot
                String slotName = GLYPH_DAMAGE_SLOT_NAMES.get(glyphId);
                if (slotName == null) continue; // Not a damage glyph

                // Read the slot's default value from the asset
                com.riprod.hexcode.core.common.glyphs.registry.SlotAsset slot = glyphAsset.getSlot(slotName);
                if (slot == null) {
                    LOGGER.atWarning().log("[HexBridge] Glyph '%s' has no '%s' slot", glyphId, slotName);
                    continue;
                }

                Double defaultValue = slot.getDefaultValue();
                float baseline = defaultValue != null ? defaultValue.floatValue() : 0f;
                if (baseline <= 0) {
                    LOGGER.atWarning().log("[HexBridge] Glyph '%s' slot '%s' has no/zero default value", glyphId, slotName);
                    continue;
                }

                // Map glyph ID to hex_ source type: "Bolt" → "hex_bolt"
                String sourceType = "hex_" + glyphId.toLowerCase();
                glyphBasePowerCache.put(sourceType, baseline);
                if (!summary.isEmpty()) summary.append(", ");
                summary.append(sourceType).append("=").append(String.format("%.1f", baseline))
                    .append(" (slot '").append(slotName).append("')");
            }

            LOGGER.atInfo().log("[HexBridge] Cached %d glyph damage baselines from slot defaults: %s",
                glyphBasePowerCache.size(), summary);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[HexBridge] Failed to cache glyph baselines");
        }
    }

    @Override
    public float getGlyphBasePower(@Nonnull String sourceType) {
        Float cached = glyphBasePowerCache.get(sourceType);
        if (cached != null) return cached;

        // Lazy populate: if cache is empty (startup timing), try to fill it now
        if (glyphBasePowerCache.isEmpty()) {
            LOGGER.atInfo().log("[HexBridge] Lazy-loading glyph baseline cache (was empty at startup)");
            cacheGlyphBasePowers();
            cached = glyphBasePowerCache.get(sourceType);
            if (cached != null) return cached;
        }

        return 0f;
    }
}
