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
 * using a 3-tier attribution system.
 *
 * <h3>Attribution Tiers</h3>
 * <ol>
 *   <li><b>Fresh ThreadLocal</b> — For direct glyph damage. The ThreadLocal set by
 *       HexCastEvent is timestamped; only consumed if &lt; 5ms old (same synchronous
 *       invoke() chain). Zero false positives regardless of player count.</li>
 *   <li><b>Construct Registry</b> — For construct tick damage. HexEntityTracker
 *       pre-registers construct entities with their caster UUID. Lookup by proximity.</li>
 *   <li><b>Projectile Registry</b> — For projectile-delivered damage. Same pattern
 *       as constructs — projectile entities are tracked and matched by proximity.</li>
 * </ol>
 */
public class HexDamageAttributionSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Construct damage source IDs — skip Tier 1 for these (they fire from construct ticks). */
    private static final Set<String> CONSTRUCT_SOURCES = Set.of(
            "hex_ensnare", "hex_glaciate", "hex_phase");

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

        // Fast path: skip non-EnvironmentSource damage (melee, ranged, fall, etc.)
        if (!(damage.getSource() instanceof Damage.EnvironmentSource envSource)) {
            return;
        }

        // Fast path: skip non-hex environment damage (lava, drowning, etc.)
        String sourceType = envSource.getType();
        if (sourceType == null || !sourceType.startsWith("hex_")) {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // === TIER 1: Fresh ThreadLocal from HexCastEvent ===
        // Only for non-construct sources. Timestamp ensures we don't consume stale
        // values from a previous cast (e.g., a projectile that traveled for seconds).
        if (!CONSTRUCT_SOURCES.contains(sourceType)) {
            Ref<EntityStore> casterRef = HexCastEventInterceptor.getFreshCaster();
            if (casterRef != null) {
                PlayerRef playerRef = store.getComponent(casterRef, PlayerRef.getComponentType());
                if (playerRef != null) {
                    damage.setSource(new Damage.EntitySource(casterRef));
                    HexCastEventInterceptor.consumeCaster();
                    LOGGER.atInfo().log("[HexAttrib] T1-Direct: %s → %s",
                            sourceType, playerRef.getUuid().toString().substring(0, 8));
                    return;
                }
            }
        }

        // === TIER 2: Construct Registry ===
        if (CONSTRUCT_SOURCES.contains(sourceType)) {
            UUID casterUuid = HexCasterRegistry.findConstructCaster(targetRef, store, sourceType);
            if (casterUuid != null) {
                Ref<EntityStore> casterEntityRef = HexCasterRegistry.resolveEntityRef(casterUuid, store);
                if (casterEntityRef != null) {
                    damage.setSource(new Damage.EntitySource(casterEntityRef));
                    LOGGER.atInfo().log("[HexAttrib] T2-Construct: %s → %s",
                            sourceType, casterUuid.toString().substring(0, 8));
                    return;
                }
            }
        }

        // === TIER 3: Projectile Registry ===
        UUID projCaster = HexCasterRegistry.findProjectileCaster(targetRef, store);
        if (projCaster != null) {
            Ref<EntityStore> casterEntityRef = HexCasterRegistry.resolveEntityRef(projCaster, store);
            if (casterEntityRef != null) {
                damage.setSource(new Damage.EntitySource(casterEntityRef));
                LOGGER.atInfo().log("[HexAttrib] T3-Projectile: %s → %s",
                        sourceType, projCaster.toString().substring(0, 8));
                return;
            }
        }

        // === FALLBACK: Recent casters (last resort) ===
        Ref<EntityStore> recentCaster = HexCastEventInterceptor.findRecentCaster(store);
        if (recentCaster != null && recentCaster.isValid()) {
            PlayerRef playerRef = store.getComponent(recentCaster, PlayerRef.getComponentType());
            if (playerRef != null) {
                damage.setSource(new Damage.EntitySource(recentCaster));
                LOGGER.atInfo().log("[HexAttrib] T4-Recent: %s → %s",
                        sourceType, playerRef.getUuid().toString().substring(0, 8));
                return;
            }
        }

        LOGGER.atFine().log("[HexAttrib] No caster found for %s", sourceType);
    }
}
