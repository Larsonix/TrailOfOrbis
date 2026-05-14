package io.github.larsonix.trailoforbis.compat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;

/**
 * Rewrites hex spell damage source from EnvironmentSource to EntitySource(caster)
 * using a 4-tier attribution system backed by {@link HexCastStateStore}.
 *
 * <h3>Attribution Tiers</h3>
 * <ol>
 *   <li><b>Fresh ThreadLocal</b> — For direct glyph damage (&lt;5ms, same invoke chain)</li>
 *   <li><b>Construct Registry</b> — Proximity + executionId fallback for construct ticks</li>
 *   <li><b>Projectile Registry</b> — Proximity + executionId fallback for projectile hits</li>
 *   <li><b>Recent Caster</b> — Most recent cast from persistent store (30s TTL)</li>
 * </ol>
 */
public class HexDamageAttributor extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Construct damage source IDs — loaded from hexcode-spells.yml config. */
    private static volatile Set<String> constructSources = Set.of(
            "hex_ensnare", "hex_glaciate", "hex_phase");

    /** Load construct sources from config. Called after ConfigManager is ready. */
    public static void loadConstructSources(@Nullable HexcodeSpellConfig config) {
        if (config != null) {
            Set<String> fromConfig = config.getConstructSources();
            if (!fromConfig.isEmpty()) {
                constructSources = fromConfig;
                LOGGER.atFine().log("[HexAttrib] Construct sources loaded from config: %s", fromConfig);
            }
        }
    }

    /** Returns whether the given source type is a construct damage source. */
    public static boolean isConstructSource(@Nullable String sourceType) {
        return sourceType != null && constructSources.contains(sourceType);
    }

    private static volatile long lastCleanupMs = 0;
    private static final long CLEANUP_INTERVAL_MS = 30_000L;

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull Damage damage) {

        if (!(damage.getSource() instanceof Damage.EnvironmentSource envSource)) {
            return;
        }

        String sourceType = envSource.getType();
        if (sourceType == null || !sourceType.startsWith("hex_")) {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // Throttled cleanup
        if (HexEntityRegistry.getConstructCount() + HexEntityRegistry.getProjectileCount() > 200) {
            long now = System.currentTimeMillis();
            if (now - lastCleanupMs > CLEANUP_INTERVAL_MS) {
                lastCleanupMs = now;
                HexEntityRegistry.cleanupStale();
            }
        }

        // === TIER 1: Fresh ThreadLocal from HexCastEvent ===
        if (!constructSources.contains(sourceType)) {
            HexCastStateStore.CastRecord freshCast = HexCastStateStore.getFreshCast();
            if (freshCast != null) {
                PlayerRef playerRef = store.getComponent(freshCast.casterRef(), PlayerRef.getComponentType());
                if (playerRef != null) {
                    damage.setSource(new Damage.EntitySource(freshCast.casterRef()));
                    HexCastStateStore.consumeCurrentCast();
                    HexcodeCompatManager.get().recordTier1Hit();
                    LOGGER.atFine().log("[HexAttrib] T1-Direct: %s → %s",
                            sourceType, playerRef.getUuid().toString().substring(0, 8));
                    return;
                }
            }
        }

        // === TIER 2: Construct Registry ===
        if (constructSources.contains(sourceType)) {
            UUID casterUuid = HexEntityRegistry.findConstructCaster(targetRef, store, sourceType);
            if (casterUuid != null) {
                Ref<EntityStore> casterEntityRef = HexEntityRegistry.resolveEntityRef(casterUuid, store);
                if (casterEntityRef != null) {
                    damage.setSource(new Damage.EntitySource(casterEntityRef));
                    HexcodeCompatManager.get().recordTier2Hit();
                    LOGGER.atFine().log("[HexAttrib] T2-Construct: %s → %s",
                            sourceType, casterUuid.toString().substring(0, 8));
                    return;
                }
            }
        }

        // === TIER 3: Projectile Registry ===
        UUID projCaster = HexEntityRegistry.findProjectileCaster(targetRef, store);
        if (projCaster != null) {
            Ref<EntityStore> casterEntityRef = HexEntityRegistry.resolveEntityRef(projCaster, store);
            if (casterEntityRef != null) {
                damage.setSource(new Damage.EntitySource(casterEntityRef));
                HexcodeCompatManager.get().recordTier3Hit();
                LOGGER.atFine().log("[HexAttrib] T3-Projectile: %s → %s",
                        sourceType, projCaster.toString().substring(0, 8));
                return;
            }
        }

        // === TIER 4: Recent casters (last resort) ===
        Ref<EntityStore> recentCaster = HexCastStateStore.findRecentCaster(store);
        if (recentCaster != null && recentCaster.isValid()) {
            PlayerRef playerRef = store.getComponent(recentCaster, PlayerRef.getComponentType());
            if (playerRef != null) {
                damage.setSource(new Damage.EntitySource(recentCaster));
                HexcodeCompatManager.get().recordTier4Hit();
                LOGGER.atFine().log("[HexAttrib] T4-Recent: %s → %s",
                        sourceType, playerRef.getUuid().toString().substring(0, 8));
                return;
            }
        }

        HexcodeCompatManager.get().recordUnattributed();
        LOGGER.atFine().log("[HexAttrib] No caster found for %s", sourceType);
    }
}
