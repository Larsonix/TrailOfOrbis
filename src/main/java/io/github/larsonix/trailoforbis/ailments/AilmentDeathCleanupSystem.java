package io.github.larsonix.trailoforbis.ailments;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * ECS death system that clears ailment state and visuals when an entity dies.
 *
 * <p>Without this, DoT ailments (burn, poison) continue ticking on dead entities,
 * and screen overlay effects (red tint from burn, green from poison) persist
 * through the death screen because Hytale's DeathComponent auto-cleanup does
 * NOT clear custom EntityEffects that produce screen overlays.
 *
 * <p>Cleans up three tracking systems:
 * <ul>
 *   <li>{@link AilmentTracker} — per-entity ailment state (burn/freeze/shock/poison)</li>
 *   <li>{@link AilmentEffectManager} — native EntityEffect visual tracking</li>
 *   <li>{@link AilmentImmunityTracker} — per-element immunity timers</li>
 * </ul>
 */
public class AilmentDeathCleanupSystem extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;

    public AilmentDeathCleanupSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Process all entity deaths — ailment state is in AilmentTracker (HashMap),
        // not ECS components, so we can't filter by component type here.
        return Query.any();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Resolve entity UUID using same pattern as AilmentTickSystem
        UUID entityUuid = resolveEntityUuid(ref, store);
        if (entityUuid == null) {
            return;
        }

        // Clean up ailment state (stops DoT ticking)
        AilmentTracker tracker = plugin.getAilmentTracker();
        if (tracker != null) {
            tracker.cleanup(entityUuid);
        }

        // Clean up visual effect tracking (clears screen overlays)
        AilmentEffectManager effectManager = plugin.getAilmentEffectManager();
        if (effectManager != null) {
            effectManager.removeAllVisuals(ref, entityUuid, store);
        }

        // Clean up immunity timers
        AilmentImmunityTracker immunityTracker = plugin.getAilmentImmunityTracker();
        if (immunityTracker != null) {
            immunityTracker.cleanup(entityUuid);
        }
    }

    /**
     * Resolves the UUID of the dying entity.
     * Uses same resolution order as {@link AilmentTickSystem}: PlayerRef → UUIDComponent.
     */
    @Nullable
    private UUID resolveEntityUuid(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            return playerRef.getUuid();
        }

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            return uuidComponent.getUuid();
        }

        return null;
    }
}
