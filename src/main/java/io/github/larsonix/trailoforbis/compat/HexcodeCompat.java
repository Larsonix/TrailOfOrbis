package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

/**
 * Soft-dependency bridge to the Hexcode spell-crafting mod.
 *
 * <p>All access to Hexcode classes is via reflection — zero compile-time dependency.
 * Resolved once at startup, cached in static fields, reused on every drain event.
 *
 * <p>If Hexcode is not loaded or its API changes, all operations are no-ops.
 */
public final class HexcodeCompat {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final PluginIdentifier HEXCODE_ID = new PluginIdentifier("Riprod", "Hexcode");

    // Detection state
    private static volatile boolean initialized = false;
    private static volatile boolean hexcodeLoaded = false;

    // Cached reflection handles (resolved once during initialize())
    private static ComponentType<EntityStore, ?> hexcasterComponentType;
    private static Method getStateMethod;
    private static Method requestStateChangeMethod;
    private static Object idleState; // HexState.IDLE enum constant

    // Hex asset map reflection handles for registering RPG items
    // These allow pedestal detection, casting particles, and glyph colors to work
    // with RPG item IDs (rpg_gear_xxx) instead of only original Hexcode item IDs.
    @Nullable private static Map<String, Object> hexStaffAssetInternalMap;
    @Nullable private static StampedLock hexStaffAssetMapLock;
    @Nullable private static Map<String, Object> hexBookAssetInternalMap;
    @Nullable private static StampedLock hexBookAssetMapLock;
    @Nullable private static Method hexStaffGetAssetMapMethod;
    @Nullable private static Method hexBookGetAssetMapMethod;
    private static volatile boolean hexAssetMapsInitialized = false;

    // Hex entity tracking reflection handles — for construct/projectile caster attribution
    @Nullable private static ComponentType<EntityStore, ?> hexEffectsComponentType;
    @Nullable private static ComponentType<EntityStore, ?> projectileStateComponentType;
    @Nullable private static ComponentType<EntityStore, ?> shatterStateComponentType;
    @Nullable private static Method hexEffectsGetEffectsMethod;
    @Nullable private static Method hexStatusGetHexContextMethod;
    @Nullable private static Method hexContextGetCasterRefMethod;
    @Nullable private static Method projectileStateGetHexContextMethod;
    @Nullable private static Method shatterStateGetHexContextMethod;
    private static volatile boolean hexTrackingInitialized = false;

    private HexcodeCompat() {}

    /**
     * Detects whether Hexcode is loaded and caches reflection handles.
     *
     * <p>Call once during {@code TrailOfOrbis.start()}, after all plugins have loaded.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        boolean detected = PluginManager.get().getPlugin(HEXCODE_ID) != null;
        if (!detected) {
            LOGGER.atInfo().log("[HexcodeCompat] Hexcode not detected — compatibility features disabled");
            initialized = true;
            return;
        }

        // Hexcode plugin is present — resolve reflection handles
        try {
            // 1. Load HexcasterComponent class
            Class<?> hexcasterClass = Class.forName(
                    "com.riprod.hexcode.core.common.hexcaster.component.HexcasterComponent");

            // 2. Get the static ComponentType via getComponentType()
            Method getComponentTypeMethod = hexcasterClass.getMethod("getComponentType");
            @SuppressWarnings("unchecked")
            ComponentType<EntityStore, ?> compType =
                    (ComponentType<EntityStore, ?>) getComponentTypeMethod.invoke(null);

            if (compType == null) {
                LOGGER.atWarning().log("[HexcodeCompat] HexcasterComponent.getComponentType() returned null — "
                        + "Hexcode may not be fully initialized");
                initialized = true;
                return;
            }
            hexcasterComponentType = compType;

            // 3. Cache getState() and requestStateChange() methods
            getStateMethod = hexcasterClass.getMethod("getState");
            requestStateChangeMethod = hexcasterClass.getMethod("requestStateChange",
                    Class.forName("com.riprod.hexcode.state.HexState"));

            // 4. Resolve HexState.IDLE enum constant (ordinal 0)
            Class<?> hexStateClass = Class.forName("com.riprod.hexcode.state.HexState");
            Object[] enumConstants = hexStateClass.getEnumConstants();
            if (enumConstants == null || enumConstants.length == 0) {
                LOGGER.atWarning().log("[HexcodeCompat] HexState enum has no constants");
                initialized = true;
                return;
            }
            idleState = enumConstants[0]; // IDLE is the first constant

            hexcodeLoaded = true;
            LOGGER.atInfo().log("[HexcodeCompat] Hexcode detected — world transition safety enabled");

        } catch (ClassNotFoundException e) {
            LOGGER.atWarning().log("[HexcodeCompat] Hexcode plugin present but classes not found: %s", e.getMessage());
        } catch (Exception e) {
            LOGGER.atWarning().log("[HexcodeCompat] Failed to resolve Hexcode reflection handles: %s", e.getMessage());
        }

        initialized = true;
    }

    /**
     * Whether Hexcode is loaded and reflection handles are ready.
     */
    public static boolean isLoaded() {
        return hexcodeLoaded;
    }

    /**
     * Forces a player's Hexcode state to IDLE if they are in a non-IDLE state.
     *
     * <p>Call this during {@code DrainPlayerFromWorldEvent} to prevent crashes when
     * Hexcode's CraftingSystem tries to tick pedestal references in a new world.
     *
     * <p>No-op if Hexcode is not loaded or the player has no HexcasterComponent.
     *
     * @param holder The entity holder from the drain event
     */
    public static void forceIdleIfCasting(Holder<EntityStore> holder) {
        if (!hexcodeLoaded) {
            return;
        }

        try {
            // Get the HexcasterComponent from the holder
            Object hexcaster = holder.getComponent(hexcasterComponentType);
            if (hexcaster == null) {
                return;
            }

            // Check current state
            Object currentState = getStateMethod.invoke(hexcaster);
            if (currentState == idleState) {
                return;
            }

            // Force transition to IDLE
            requestStateChangeMethod.invoke(hexcaster, idleState);
            LOGGER.atFine().log("[HexcodeCompat] Reset Hexcode state from %s to IDLE on world drain", currentState);

        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeCompat] Failed to reset Hexcode state: %s", e.getMessage());
        }
    }

    /**
     * Checks if a player is currently in a non-IDLE Hexcode state (casting, drawing, crafting).
     *
     * <p>Used to identify the caster of a hex spell: during glyph execution on the
     * single-threaded world tick, only the executing player has a non-IDLE state.
     *
     * @param store The entity store
     * @param playerRef The player entity reference
     * @return true if the player has a HexcasterComponent in non-IDLE state
     */
    public static boolean isCurrentlyCasting(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef) {

        if (!hexcodeLoaded) {
            return false;
        }

        try {
            Object hexcaster = store.getComponent(playerRef, hexcasterComponentType);
            if (hexcaster == null) {
                return false;
            }

            Object currentState = getStateMethod.invoke(hexcaster);
            return currentState != idleState;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // HEX ENTITY TRACKING (construct/projectile caster attribution)
    // =========================================================================

    /**
     * Initializes reflection handles for reading caster identity from Hexcode's
     * construct and projectile entities. Call once during startup, after Hexcode
     * has registered its components.
     */
    @SuppressWarnings("unchecked")
    public static void initializeHexTracking() {
        if (hexTrackingInitialized || !hexcodeLoaded) {
            return;
        }

        try {
            // HexEffectsComponent — on construct entities
            Class<?> hexEffectsClass = Class.forName(
                    "com.riprod.hexcode.core.common.construct.component.HexEffectsComponent");
            Method getCtMethod = hexEffectsClass.getMethod("getComponentType");
            hexEffectsComponentType = (ComponentType<EntityStore, ?>) getCtMethod.invoke(null);
            hexEffectsGetEffectsMethod = hexEffectsClass.getMethod("getEffects");

            // HexStatus — getHexContext()
            Class<?> hexStatusClass = Class.forName(
                    "com.riprod.hexcode.core.common.construct.component.HexStatus");
            hexStatusGetHexContextMethod = hexStatusClass.getMethod("getHexContext");

            // HexContext — getCasterRef()
            Class<?> hexContextClass = Class.forName(
                    "com.riprod.hexcode.core.state.execution.component.HexContext");
            hexContextGetCasterRefMethod = hexContextClass.getMethod("getCasterRef");

            LOGGER.atInfo().log("[HexcodeCompat] Hex construct tracking initialized (HexEffectsComponent)");
        } catch (Exception e) {
            LOGGER.atWarning().log("[HexcodeCompat] Failed to init construct tracking: %s", e.getMessage());
        }

        try {
            // ProjectileState — on projectile entities
            Class<?> projStateClass = Class.forName(
                    "com.riprod.hexcode.builtin.glyphs.projectile.component.ProjectileState");
            Method getCtMethod = projStateClass.getMethod("getComponentType");
            projectileStateComponentType = (ComponentType<EntityStore, ?>) getCtMethod.invoke(null);
            projectileStateGetHexContextMethod = projStateClass.getMethod("getHexContext");

            LOGGER.atInfo().log("[HexcodeCompat] Hex projectile tracking initialized (ProjectileState)");
        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeCompat] ProjectileState not available: %s", e.getMessage());
        }

        try {
            // ShatterState — on shatter shard entities
            Class<?> shatterClass = Class.forName(
                    "com.riprod.hexcode.builtin.glyphs.shatter.component.ShatterState");
            Method getCtMethod = shatterClass.getMethod("getComponentType");
            shatterStateComponentType = (ComponentType<EntityStore, ?>) getCtMethod.invoke(null);
            shatterStateGetHexContextMethod = shatterClass.getMethod("getHexContext");

            LOGGER.atInfo().log("[HexcodeCompat] Hex shatter tracking initialized (ShatterState)");
        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeCompat] ShatterState not available: %s", e.getMessage());
        }

        hexTrackingInitialized = true;
    }

    /**
     * Extracts the caster's entity ref from a HexEffectsComponent on the given entity.
     * Returns the first valid caster ref found in the component's active effects map.
     *
     * @param store The entity store
     * @param entityRef The entity to check
     * @return The caster's entity ref, or null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Ref<EntityStore> extractConstructCasterRef(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef) {

        if (hexEffectsComponentType == null || hexEffectsGetEffectsMethod == null) {
            return null;
        }

        try {
            Object hexEffects = store.getComponent(entityRef, hexEffectsComponentType);
            if (hexEffects == null) {
                return null;
            }

            java.util.Map<?, ?> effects = (java.util.Map<?, ?>) hexEffectsGetEffectsMethod.invoke(hexEffects);
            if (effects == null || effects.isEmpty()) {
                return null;
            }

            // Take the first effect's caster — all effects on the same construct
            // entity come from the same caster
            for (Object hexStatus : effects.values()) {
                Ref<EntityStore> casterRef = extractCasterFromHexStatus(hexStatus);
                if (casterRef != null) {
                    return casterRef;
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[HexcodeCompat] Failed to extract construct caster: %s", e.getMessage());
        }
        return null;
    }

    /**
     * Extracts the caster's entity ref from a ProjectileState or ShatterState component.
     *
     * @param store The entity store
     * @param entityRef The projectile/shatter entity
     * @return The caster's entity ref, or null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Ref<EntityStore> extractProjectileCasterRef(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef) {

        // Try ProjectileState first
        if (projectileStateComponentType != null && projectileStateGetHexContextMethod != null) {
            try {
                Object projState = store.getComponent(entityRef, projectileStateComponentType);
                if (projState != null) {
                    Object hexContext = projectileStateGetHexContextMethod.invoke(projState);
                    if (hexContext != null) {
                        return extractCasterFromHexContext(hexContext);
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("[HexcodeCompat] ProjectileState read failed: %s", e.getMessage());
            }
        }

        // Try ShatterState
        if (shatterStateComponentType != null && shatterStateGetHexContextMethod != null) {
            try {
                Object shatterState = store.getComponent(entityRef, shatterStateComponentType);
                if (shatterState != null) {
                    Object hexContext = shatterStateGetHexContextMethod.invoke(shatterState);
                    if (hexContext != null) {
                        return extractCasterFromHexContext(hexContext);
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("[HexcodeCompat] ShatterState read failed: %s", e.getMessage());
            }
        }

        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Ref<EntityStore> extractCasterFromHexStatus(@Nonnull Object hexStatus) {
        try {
            if (hexStatusGetHexContextMethod == null) return null;
            Object hexContext = hexStatusGetHexContextMethod.invoke(hexStatus);
            if (hexContext == null) return null;
            return extractCasterFromHexContext(hexContext);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Ref<EntityStore> extractCasterFromHexContext(@Nonnull Object hexContext) {
        try {
            if (hexContextGetCasterRefMethod == null) return null;
            Object ref = hexContextGetCasterRefMethod.invoke(hexContext);
            if (ref instanceof Ref<?> r && r.isValid()) {
                return (Ref<EntityStore>) r;
            }
        } catch (Exception e) {
            // Caster may have disconnected — ref is invalid
        }
        return null;
    }

    /** Whether hex entity tracking is initialized. */
    public static boolean isTrackingInitialized() {
        return hexTrackingInitialized;
    }

    /** Returns the HexEffectsComponent ComponentType, or null. */
    @Nullable
    public static ComponentType<EntityStore, ?> getHexEffectsComponentType() {
        return hexEffectsComponentType;
    }

    /** Returns the ProjectileState ComponentType, or null. */
    @Nullable
    public static ComponentType<EntityStore, ?> getProjectileStateComponentType() {
        return projectileStateComponentType;
    }

    /** Returns the ShatterState ComponentType, or null. */
    @Nullable
    public static ComponentType<EntityStore, ?> getShatterStateComponentType() {
        return shatterStateComponentType;
    }

    // =========================================================================
    // HEX ASSET MAP REGISTRATION
    // =========================================================================

    /**
     * Initializes reflection access to Hexcode's HexStaffAsset and HexBookAsset maps.
     *
     * <p>Call once during startup, after Hexcode has finished loading its assets.
     * This allows us to register RPG item IDs in those maps so that Hexcode's
     * pedestal detection, casting particles, idle colors, and session glyph colors
     * all work with our custom item IDs.
     */
    public static void initializeHexAssetMaps() {
        if (hexAssetMapsInitialized || !hexcodeLoaded) {
            return;
        }

        try {
            // HexStaffAsset
            Class<?> hexStaffAssetClass = Class.forName(
                    "com.riprod.hexcode.core.common.hexstaff.component.HexStaffAsset");
            hexStaffGetAssetMapMethod = hexStaffAssetClass.getMethod("getAssetMap");
            Object staffAssetMap = hexStaffGetAssetMapMethod.invoke(null);
            if (staffAssetMap instanceof DefaultAssetMap<?, ?>) {
                resolveAssetMapInternals(staffAssetMap, "HexStaffAsset",
                        (map, lock) -> { hexStaffAssetInternalMap = map; hexStaffAssetMapLock = lock; });
            }

            // HexBookAsset
            Class<?> hexBookAssetClass = Class.forName(
                    "com.riprod.hexcode.core.common.hexbook.component.HexBookAsset");
            hexBookGetAssetMapMethod = hexBookAssetClass.getMethod("getAssetMap");
            Object bookAssetMap = hexBookGetAssetMapMethod.invoke(null);
            if (bookAssetMap instanceof DefaultAssetMap<?, ?>) {
                resolveAssetMapInternals(bookAssetMap, "HexBookAsset",
                        (map, lock) -> { hexBookAssetInternalMap = map; hexBookAssetMapLock = lock; });
            }

            hexAssetMapsInitialized = true;
            LOGGER.atInfo().log("[HexcodeCompat] Hex asset maps initialized (staff=%s, book=%s)",
                    hexStaffAssetInternalMap != null, hexBookAssetInternalMap != null);

        } catch (ClassNotFoundException e) {
            LOGGER.atFine().log("[HexcodeCompat] Hex asset classes not found: %s", e.getMessage());
        } catch (Exception e) {
            LOGGER.atWarning().log("[HexcodeCompat] Failed to initialize hex asset maps: %s", e.getMessage());
        }

        hexAssetMapsInitialized = true;
    }

    /**
     * Resolves the internal map and lock from a DefaultAssetMap via reflection.
     */
    @SuppressWarnings("unchecked")
    private static void resolveAssetMapInternals(
            @Nonnull Object assetMap,
            @Nonnull String name,
            @Nonnull AssetMapConsumer consumer) {
        try {
            Field mapField = DefaultAssetMap.class.getDeclaredField("assetMap");
            mapField.setAccessible(true);
            Map<String, Object> internalMap = (Map<String, Object>) mapField.get(assetMap);

            Field lockField = DefaultAssetMap.class.getDeclaredField("assetMapLock");
            lockField.setAccessible(true);
            StampedLock lock = (StampedLock) lockField.get(assetMap);

            consumer.accept(internalMap, lock);
        } catch (Exception e) {
            LOGGER.atWarning().log("[HexcodeCompat] Could not resolve %s internals: %s", name, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface AssetMapConsumer {
        void accept(Map<String, Object> map, StampedLock lock);
    }

    /**
     * Registers an RPG item ID in Hexcode's hex asset maps, sharing the same
     * asset as the base Hexcode item.
     *
     * <p>Uses {@code DefaultAssetMap.putAll()} via reflection to properly register
     * in BOTH internal tracking structures ({@code assetMap} and {@code assetChainMap}).
     * Raw {@code Map.put()} into {@code assetMap} alone is insufficient — the entry
     * gets lost during subsequent asset system operations.
     *
     * @param customItemId The RPG custom item ID (e.g., "rpg_gear_xxxxx")
     * @param baseItemId The original base item ID (e.g., "Weapon_Wand_Wood")
     * @param isHexStaff true if staff/wand, false if book
     */
    public static void registerHexAsset(
            @Nonnull String customItemId,
            @Nonnull String baseItemId,
            boolean isHexStaff) {

        if (!hexcodeLoaded || !hexAssetMapsInitialized) {
            return;
        }

        try {
            Method getAssetMapMethod = isHexStaff ? hexStaffGetAssetMapMethod : hexBookGetAssetMapMethod;
            if (getAssetMapMethod == null) return;

            Object assetMapObj = getAssetMapMethod.invoke(null);
            if (!(assetMapObj instanceof DefaultAssetMap<?, ?> defaultAssetMap)) return;

            String assetTypeName = isHexStaff ? "HexStaffAsset" : "HexBookAsset";

            // 1. Resolve the base asset via public API
            Method getAssetMethod = DefaultAssetMap.class.getMethod("getAsset", Object.class);
            Object baseAsset = getAssetMethod.invoke(defaultAssetMap, baseItemId);

            if (baseAsset == null) {
                // Vanilla base items have no hex asset. Fall back to a known Hexcode item.
                String[] fallbacks = isHexStaff
                        ? new String[]{"Hexstaff_Basic_Crude", "Hexstaff_Basic_Copper", "Hexstaff_Basic_Iron"}
                        : new String[]{"Hex_Book", "Fire_Hexbook", "Arcane_Hexbook"};

                for (String fallbackId : fallbacks) {
                    baseAsset = getAssetMethod.invoke(defaultAssetMap, fallbackId);
                    if (baseAsset != null) {
                        LOGGER.atInfo().log("[HexcodeCompat] Using fallback %s '%s' for vanilla base '%s'",
                                assetTypeName, fallbackId, baseItemId);
                        break;
                    }
                }

                if (baseAsset == null) {
                    LOGGER.atFine().log("[HexcodeCompat] No %s found (base '%s', no fallback), "
                            + "skipping registration for '%s'", assetTypeName, baseItemId, customItemId);
                    return;
                }
            }

            // 2. Register via DefaultAssetMap.putAll() — updates BOTH assetMap and assetChainMap.
            //    Raw Map.put() into assetMap alone doesn't persist through asset lifecycle operations.
            registerViaPutAll(defaultAssetMap, customItemId, baseAsset, assetTypeName);

            // 3. Verify via public API
            Object verified = getAssetMethod.invoke(defaultAssetMap, customItemId);
            LOGGER.atInfo().log("[HexcodeCompat] Registered %s '%s' → '%s' [VERIFY: %s]",
                    assetTypeName, customItemId, baseItemId,
                    verified != null ? "FOUND" : "NULL — putAll registration failed!");

            // 4. Also do a raw assetMap field check to compare
            Field rawMapField = DefaultAssetMap.class.getDeclaredField("assetMap");
            rawMapField.setAccessible(true);
            Map<?, ?> rawMap = (Map<?, ?>) rawMapField.get(defaultAssetMap);
            Object rawResult = rawMap.get(customItemId);
            LOGGER.atInfo().log("[HexcodeCompat] RAW assetMap.get('%s')=%s, assetMap.size=%d, assetMap.class=%s",
                    customItemId,
                    rawResult != null ? "FOUND" : "NULL",
                    rawMap.size(),
                    rawMap.getClass().getSimpleName());

            // 5. Check assetChainMap too
            Field chainMapField = DefaultAssetMap.class.getDeclaredField("assetChainMap");
            chainMapField.setAccessible(true);
            Map<?, ?> chainMap = (Map<?, ?>) chainMapField.get(defaultAssetMap);
            Object chainResult = chainMap.get(customItemId);
            LOGGER.atInfo().log("[HexcodeCompat] assetChainMap.get('%s')=%s",
                    customItemId, chainResult != null ? "FOUND (chain len=" + ((Object[]) chainResult).length + ")" : "NULL");

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[HexcodeCompat] Failed to register hex asset '%s' (base '%s')",
                    customItemId, baseItemId);
        }
    }

    /**
     * Diagnostic: checks if a specific item ID is still in the HexStaffAsset map.
     * Call this at any time to verify registration persisted.
     */
    public static void diagCheckHexAsset(@Nonnull String itemId) {
        if (!hexcodeLoaded) return;
        try {
            if (hexStaffGetAssetMapMethod == null) return;
            Object assetMapObj = hexStaffGetAssetMapMethod.invoke(null);
            if (!(assetMapObj instanceof DefaultAssetMap<?, ?> dam)) return;

            Method getAssetMethod = DefaultAssetMap.class.getMethod("getAsset", Object.class);
            Object result = getAssetMethod.invoke(dam, itemId);

            // Also check raw field
            Field rawMapField = DefaultAssetMap.class.getDeclaredField("assetMap");
            rawMapField.setAccessible(true);
            Map<?, ?> rawMap = (Map<?, ?>) rawMapField.get(dam);
            Object rawResult = rawMap.get(itemId);

            // Also check particles on the asset
            String particleInfo = "N/A";
            if (result != null) {
                try {
                    Method getParticles = result.getClass().getMethod("getCastingAuraParticles");
                    Object particles = getParticles.invoke(result);
                    if (particles == null) {
                        particleInfo = "NULL";
                    } else if (particles instanceof Object[] arr) {
                        particleInfo = "length=" + arr.length;
                        // Log each particle's SystemId
                        for (int i = 0; i < arr.length; i++) {
                            try {
                                Method getSysId = arr[i].getClass().getMethod("getSystemId");
                                Object sysId = getSysId.invoke(arr[i]);
                                LOGGER.atInfo().log("[HexcodeCompat-DIAG]   particle[%d]: systemId='%s'", i, sysId);
                            } catch (Exception ignored) {}
                        }
                    } else {
                        particleInfo = "type=" + particles.getClass().getSimpleName();
                    }
                    // Also check the asset ID
                    Method getId = result.getClass().getMethod("getId");
                    Object assetId = getId.invoke(result);
                    particleInfo += ", assetId=" + assetId;
                } catch (Exception pe) {
                    particleInfo = "ERROR: " + pe.getMessage();
                }
            }

            LOGGER.atInfo().log("[HexcodeCompat-DIAG] Check '%s': publicAPI=%s, rawMap=%s, mapSize=%d, particles=%s",
                    itemId,
                    result != null ? "FOUND" : "NULL",
                    rawResult != null ? "FOUND" : "NULL",
                    rawMap.size(),
                    particleInfo);
        } catch (Exception e) {
            LOGGER.atInfo().log("[HexcodeCompat-DIAG] Check '%s': ERROR %s", itemId, e.getMessage());
        }
    }

    /**
     * Registers an asset in a DefaultAssetMap using the protected putAll() method.
     * This properly updates both assetMap and assetChainMap, making the registration
     * durable across asset system operations.
     */
    @SuppressWarnings("unchecked")
    private static void registerViaPutAll(
            @Nonnull DefaultAssetMap<?, ?> assetMap,
            @Nonnull String customId,
            @Nonnull Object asset,
            @Nonnull String assetTypeName) throws Exception {

        // Get the AssetStore to obtain the codec (required by putAll)
        Method getAssetStoreMethod;
        if (assetTypeName.equals("HexStaffAsset")) {
            Class<?> cls = Class.forName("com.riprod.hexcode.core.common.hexstaff.component.HexStaffAsset");
            getAssetStoreMethod = cls.getMethod("getAssetStore");
        } else {
            Class<?> cls = Class.forName("com.riprod.hexcode.core.common.hexbook.component.HexBookAsset");
            getAssetStoreMethod = cls.getMethod("getAssetStore");
        }
        Object assetStore = getAssetStoreMethod.invoke(null);
        Method getCodecMethod = assetStore.getClass().getMethod("getCodec");
        Object codec = getCodecMethod.invoke(assetStore);

        // Clone the asset with a new ID via reflection
        Object clonedAsset = cloneAssetWithId(asset, customId, assetTypeName);

        // Call putAll on the DefaultAssetMap
        // protected void putAll(String packKey, AssetCodec codec, Map loadedAssets, Map pathMap, Map childrenMap)
        Method putAllMethod = DefaultAssetMap.class.getDeclaredMethod("putAll",
                String.class, // packKey
                com.hypixel.hytale.assetstore.codec.AssetCodec.class, // codec
                Map.class,    // loadedAssets
                Map.class,    // loadedKeyToPathMap
                Map.class);   // loadedAssetChildren
        putAllMethod.setAccessible(true);

        Map<String, Object> loadedAssets = new java.util.HashMap<>();
        loadedAssets.put(customId, clonedAsset);

        putAllMethod.invoke(assetMap,
                "TrailOfOrbis:TrailOfOrbis",  // packKey
                codec,
                loadedAssets,
                java.util.Collections.emptyMap(),  // no paths
                java.util.Collections.emptyMap()); // no children
    }

    /**
     * Creates a shallow copy of a HexStaffAsset/HexBookAsset with a different ID.
     */
    private static Object cloneAssetWithId(
            @Nonnull Object original,
            @Nonnull String newId,
            @Nonnull String assetTypeName) throws Exception {

        Class<?> assetClass = original.getClass();

        // Use the private no-arg constructor
        java.lang.reflect.Constructor<?> ctor = assetClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object clone = ctor.newInstance();

        // Copy all declared fields from original to clone
        for (java.lang.reflect.Field field : assetClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) continue;
            field.setAccessible(true);
            field.set(clone, field.get(original));
        }

        // Override the ID
        java.lang.reflect.Field idField = assetClass.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(clone, newId);

        return clone;
    }
}
