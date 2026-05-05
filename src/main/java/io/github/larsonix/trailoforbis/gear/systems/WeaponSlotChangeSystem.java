package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.SwitchActiveSlotEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncManager;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.gear.util.EquipmentSectionIds;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ECS event system that triggers stat recalculation when a player switches hotbar slots.
 *
 * <p>This system listens for {@link SwitchActiveSlotEvent} and recalculates
 * player stats when the active slot changes in the hotbar (section ID -1).
 *
 * <p><b>IMPORTANT:</b> Utility (offhand) slot switches are intentionally excluded.
 * Hytale fires {@code SwitchActiveSlotEvent} via synchronous {@code store.invoke()}
 * <i>before</i> updating the inventory's active slot field. Reading
 * {@code inventory.getUtilityItem()} during this event returns the OLD item,
 * causing stale stat calculations. Utility slot tracking is handled by
 * {@link HotbarSlotTrackingSystem} which uses tick-based polling and always
 * reads the correct post-update state.
 *
 * @see EquipmentSectionIds
 * @see HotbarSlotTrackingSystem (handles both hotbar AND utility slot changes)
 * @see io.github.larsonix.trailoforbis.listeners.EquipmentChangeListener
 */
public class WeaponSlotChangeSystem extends EntityEventSystem<EntityStore, SwitchActiveSlotEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    public WeaponSlotChangeSystem() {
        super(SwitchActiveSlotEvent.class);
        this.playerRefType = PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Only process events for entities with PlayerRef (players)
        return playerRefType;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull SwitchActiveSlotEvent event
    ) {
        // Only handle hotbar section switches.
        // Utility (offhand) is excluded: SwitchActiveSlotEvent fires BEFORE the
        // inventory's activeUtilitySlot is updated, so reading the utility item
        // here returns stale data. Utility tracking is handled by
        // HotbarSlotTrackingSystem's tick-based approach which reads post-update state.
        int sectionId = event.getInventorySectionId();
        if (sectionId != EquipmentSectionIds.HOTBAR) {
            return;
        }

        // Skip if switching to the same slot (no actual change)
        int previousSlot = event.getPreviousSlot();
        byte newSlot = event.getNewSlot();
        if (previousSlot == newSlot) {
            return;
        }

        // Get PlayerRef from the entity
        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        String sectionName = EquipmentSectionIds.getSectionName(sectionId);
        LOGGER.at(Level.FINE).log("%s slot change detected for player %s: slot %d -> %d",
                sectionName, uuid, previousSlot, newSlot);

        // Get attribute service
        Optional<AttributeService> attributeServiceOpt = ServiceRegistry.get(AttributeService.class);
        if (attributeServiceOpt.isEmpty()) {
            LOGGER.at(Level.WARNING).log("AttributeService not available for slot change recalculation");
            return;
        }

        AttributeService attributeService = attributeServiceOpt.get();

        // Recalculate stats (will pick up new weapon/utility stats).
        // recalculateStats() internally applies to ECS via its callback when changed.
        ComputedStats newStats = attributeService.recalculateStats(uuid);
        if (newStats == null) {
            LOGGER.at(Level.WARNING).log("Failed to recalculate stats for %s after %s slot change",
                    uuid, sectionName);
            return;
        }

        // Sync animation speed to match new attack speed stat
        ServiceRegistry.get(AnimationSpeedSyncManager.class)
                .ifPresent(m -> m.syncAnimationSpeed(uuid));

        // Mark gear tooltips dirty so requirement colors update on the next flush.
        // The tooltipRefreshCallback covers stat-change cases, but switching between
        // two items with identical stats (or two empty slots) won't trigger it.
        // Explicit dirty-marking ensures tooltips always reflect the active slot.
        ServiceRegistry.get(GearService.class).ifPresent(svc -> {
            if (svc instanceof GearManager mgr) {
                ItemSyncCoordinator coordinator = mgr.getSyncCoordinator();
                if (coordinator != null) {
                    coordinator.markEquipmentDirty(uuid);
                }
            }
        });

        LOGGER.at(Level.FINE).log("Recalculated stats for %s after %s slot change",
                uuid, sectionName);
    }
}
