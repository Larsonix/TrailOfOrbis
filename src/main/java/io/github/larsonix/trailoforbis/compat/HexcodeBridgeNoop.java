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
 * No-op implementation of {@link HexcodeBridge} used when Hexcode is not installed.
 *
 * <p>All methods are safe no-ops — zero Hexcode imports, zero side effects.
 * This class is always safe to load regardless of classpath.
 */
final class HexcodeBridgeNoop implements HexcodeBridge {

    @Override public boolean isLoaded() { return false; }
    @Nullable @Override public String getVersion() { return null; }

    @Override public void registerEcsSystems(@Nonnull ComponentRegistryProxy<EntityStore> registry) {}

    @Override public void forceIdleIfCasting(@Nonnull Holder<EntityStore> holder) {}
    @Override public boolean isCurrentlyCasting(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) { return false; }

    @Override public boolean isTrackingInitialized() { return false; }
    @Override public void initializeHexTracking() {}

    @Nullable @Override public ComponentType<EntityStore, ?> getHexEffectsComponentType() { return null; }
    @Nullable @Override public ComponentType<EntityStore, ?> getProjectileStateComponentType() { return null; }
    @Nullable @Override public ComponentType<EntityStore, ?> getShatterStateComponentType() { return null; }

    @Nullable @Override public Ref<EntityStore> extractConstructCasterRef(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) { return null; }
    @Nullable @Override public Ref<EntityStore> extractProjectileCasterRef(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) { return null; }

    @Override public void resolveStatIndices() {}
    @Override public int getVolatilityStatIndex() { return Integer.MIN_VALUE; }
    @Override public int getMagicPowerStatIndex() { return Integer.MIN_VALUE; }
    @Override public int getMagicChargesStatIndex() { return Integer.MIN_VALUE; }

    @Override public void initializeHexAssetMaps() {}
    @Override public void registerHexAsset(@Nonnull String customItemId, @Nonnull String baseItemId, boolean isHexStaff) {}
    @Override public void diagCheckHexAsset(@Nonnull String itemId) {}
    @Override public void cacheGlyphBasePowers() {}
    @Override public float getGlyphBasePower(@Nonnull String sourceType) { return 0f; }
}
