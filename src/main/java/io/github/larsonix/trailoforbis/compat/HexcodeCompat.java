package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Static facade for Hexcode compatibility — delegates to {@link HexcodeCompatManager}.
 *
 * <p>This class exists so the 35+ callsites across the codebase that use
 * {@code HexcodeCompat.isLoaded()}, {@code HexcodeCompat.forceIdleIfCasting()}, etc.
 * continue to work without changes. All method bodies are one-line delegations
 * to the new manager/bridge pattern.
 *
 * <p>New code should use {@link HexcodeCompatManager#get()} directly.
 *
 * @see HexcodeCompatManager
 * @see HexcodeBridge
 */
public final class HexcodeCompat {

    private HexcodeCompat() {}

    /** Call once during startup. Delegates to {@link HexcodeCompatManager#initialize()}. */
    public static void initialize() {
        HexcodeCompatManager.initialize();
    }

    /** Whether Hexcode is loaded and initialized. */
    public static boolean isLoaded() {
        try {
            return HexcodeCompatManager.get().isLoaded();
        } catch (IllegalStateException e) {
            return false; // Manager not yet initialized
        }
    }

    /** Whether hex entity tracking is initialized. */
    public static boolean isTrackingInitialized() {
        try {
            return HexcodeCompatManager.get().bridge().isTrackingInitialized();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Forces player's Hexcode state to IDLE on world drain. */
    public static void forceIdleIfCasting(@Nonnull Holder<EntityStore> holder) {
        try {
            HexcodeCompatManager.get().bridge().forceIdleIfCasting(holder);
        } catch (IllegalStateException e) {
            // Manager not initialized — no-op
        }
    }

    /** Checks if player is in a non-IDLE Hexcode state. */
    public static boolean isCurrentlyCasting(@Nonnull Store<EntityStore> store,
                                              @Nonnull Ref<EntityStore> playerRef) {
        try {
            return HexcodeCompatManager.get().bridge().isCurrentlyCasting(store, playerRef);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Initializes hex entity tracking. */
    public static void initializeHexTracking() {
        try {
            HexcodeCompatManager.get().bridge().initializeHexTracking();
        } catch (IllegalStateException e) {
            // Manager not initialized — no-op
        }
    }

    /** Returns HexEffectsComponent type, or null. */
    @Nullable
    public static ComponentType<EntityStore, ?> getHexEffectsComponentType() {
        try {
            return HexcodeCompatManager.get().bridge().getHexEffectsComponentType();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Returns ProjectileState type, or null. */
    @Nullable
    public static ComponentType<EntityStore, ?> getProjectileStateComponentType() {
        try {
            return HexcodeCompatManager.get().bridge().getProjectileStateComponentType();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Returns ShatterState type, or null. */
    @Nullable
    public static ComponentType<EntityStore, ?> getShatterStateComponentType() {
        try {
            return HexcodeCompatManager.get().bridge().getShatterStateComponentType();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Extracts caster ref from a construct entity. */
    @Nullable
    public static Ref<EntityStore> extractConstructCasterRef(
            @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) {
        try {
            return HexcodeCompatManager.get().bridge().extractConstructCasterRef(store, entityRef);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Extracts caster ref from a projectile/shatter entity. */
    @Nullable
    public static Ref<EntityStore> extractProjectileCasterRef(
            @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) {
        try {
            return HexcodeCompatManager.get().bridge().extractProjectileCasterRef(store, entityRef);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** Initializes hex asset map access. */
    public static void initializeHexAssetMaps() {
        try {
            HexcodeCompatManager.get().bridge().initializeHexAssetMaps();
        } catch (IllegalStateException e) {
            // Manager not initialized — no-op
        }
    }

    /** Caches basePower from all GlyphAssets for ratio-based damage replacement. */
    public static void cacheGlyphBasePowers() {
        try {
            HexcodeCompatManager.get().bridge().cacheGlyphBasePowers();
        } catch (IllegalStateException e) {
            // Manager not initialized — no-op
        }
    }

    /**
     * Returns the cached basePower for a hex damage source type (e.g., "hex_bolt").
     * Returns 0 if unknown or Hexcode not loaded.
     */
    public static float getGlyphBasePower(@Nonnull String sourceType) {
        try {
            return HexcodeCompatManager.get().bridge().getGlyphBasePower(sourceType);
        } catch (IllegalStateException e) {
            return 0f;
        }
    }

    /** Registers RPG item in Hexcode's asset maps. */
    public static void registerHexAsset(@Nonnull String customItemId,
                                         @Nonnull String baseItemId, boolean isHexStaff) {
        try {
            HexcodeCompatManager.get().bridge().registerHexAsset(customItemId, baseItemId, isHexStaff);
        } catch (IllegalStateException e) {
            // Manager not initialized — no-op
        }
    }

    /** Diagnostic check for hex asset registration. */
    public static void diagCheckHexAsset(@Nonnull String itemId) {
        try {
            HexcodeCompatManager.get().bridge().diagCheckHexAsset(itemId);
        } catch (IllegalStateException e) {
            // Manager not initialized — no-op
        }
    }
}
