package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Contract for the Hexcode compatibility layer.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@code HexcodeBridgeImpl} — real integration with direct Hexcode imports</li>
 *   <li>{@code HexcodeBridgeNoop} — no-op when Hexcode is absent</li>
 * </ul>
 *
 * <p><b>No Hexcode types in signatures.</b> This interface uses only Hytale types
 * and Java primitives, so it can be safely loaded regardless of whether Hexcode
 * is present. The implementations handle type-specific access internally.
 *
 * @see HexcodeCompatManager
 */
public interface HexcodeBridge {

    // ── Detection ──

    /** Whether Hexcode is loaded and fully initialized. */
    boolean isLoaded();

    /** Hexcode version string (e.g., "0.6.7"), or null if not loaded. */
    @Nullable
    String getVersion();

    // ── ECS System Registration ──

    /**
     * Registers all Hexcode ECS systems (event interceptor, entity tracker,
     * damage attribution, casting aura). Keeps Hexcode class references
     * isolated from the main plugin class.
     *
     * @param registry The entity store registry from the plugin
     */
    void registerEcsSystems(@Nonnull ComponentRegistryProxy<EntityStore> registry);

    // ── Casting State Management ──

    /**
     * Forces a player's Hexcode state to IDLE if they are in a non-IDLE state.
     * Called during DrainPlayerFromWorldEvent to prevent crashes when Hexcode's
     * CraftingSystem tries to tick pedestal references in a new world.
     */
    void forceIdleIfCasting(@Nonnull Holder<EntityStore> holder);

    /**
     * Checks if a player is currently in a non-IDLE Hexcode state
     * (casting, drawing, crafting).
     */
    boolean isCurrentlyCasting(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef);

    // ── Entity Tracking ──

    /** Whether hex entity tracking (construct/projectile components) is initialized. */
    boolean isTrackingInitialized();

    /** Initializes component handles for construct/projectile caster extraction. */
    void initializeHexTracking();

    /** Returns the HexEffectsComponent ComponentType, or null. */
    @Nullable
    ComponentType<EntityStore, ?> getHexEffectsComponentType();

    /** Returns the ProjectileState ComponentType, or null. */
    @Nullable
    ComponentType<EntityStore, ?> getProjectileStateComponentType();

    /** Returns the ShatterState ComponentType, or null. */
    @Nullable
    ComponentType<EntityStore, ?> getShatterStateComponentType();

    /**
     * Extracts the caster's entity ref from a HexEffectsComponent on a construct entity.
     * Returns the first valid caster ref found in the component's active effects map.
     */
    @Nullable
    Ref<EntityStore> extractConstructCasterRef(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef);

    /**
     * Extracts the caster's entity ref from a ProjectileState or ShatterState component.
     */
    @Nullable
    Ref<EntityStore> extractProjectileCasterRef(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef);

    // ── Stat Index Resolution ──

    /** Resolves Hexcode stat indices (Volatility, MagicPower, MagicCharges). */
    void resolveStatIndices();

    /** Returns the Volatility stat index, or Integer.MIN_VALUE if unavailable. */
    int getVolatilityStatIndex();

    /** Returns the MagicPower stat index, or Integer.MIN_VALUE if unavailable. */
    int getMagicPowerStatIndex();

    /** Returns the MagicCharges stat index, or Integer.MIN_VALUE if unavailable. */
    int getMagicChargesStatIndex();

    // ── Asset Map Registration ──

    /** Initializes access to Hexcode's HexStaffAsset and HexBookAsset maps. */
    void initializeHexAssetMaps();

    /**
     * Registers an RPG item ID in Hexcode's hex asset maps, sharing the same
     * asset as the base Hexcode item.
     *
     * @param customItemId The RPG custom item ID (e.g., "rpg_gear_xxxxx")
     * @param baseItemId The original base item ID (e.g., "Weapon_Wand_Wood")
     * @param isHexStaff true if staff/wand, false if book
     */
    void registerHexAsset(@Nonnull String customItemId, @Nonnull String baseItemId, boolean isHexStaff);

    /** Diagnostic: checks if a specific item ID is still in the HexStaffAsset map. */
    void diagCheckHexAsset(@Nonnull String itemId);

    // ── Glyph Base Power Cache ──

    /** Caches basePower from all GlyphAssets for ratio-based damage replacement. */
    void cacheGlyphBasePowers();

    /**
     * Returns the cached basePower for a hex damage source type (e.g., "hex_bolt").
     * Used to compute the hex processing ratio: vanillaDamage / basePower.
     *
     * @param sourceType The hex damage source type (from EnvironmentSource)
     * @return The glyph's basePower, or 0 if unknown
     */
    float getGlyphBasePower(@Nonnull String sourceType);
}
