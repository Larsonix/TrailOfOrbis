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
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.gear.util.EquipmentSectionIds;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ECS event system that triggers stat recalculation when a player switches
 * weapon (hotbar) or utility (offhand) slots.
 *
 * <p>This system listens for {@link SwitchActiveSlotEvent} and recalculates
 * player stats when the active slot changes in:
 * <ul>
 *   <li>Hotbar (section ID -1) - weapon/tool switching</li>
 *   <li>Utility (section ID -5) - offhand item switching</li>
 * </ul>
 *
 * <p>The stat recalculation picks up any stat modifiers from the newly
 * equipped weapon/utility item and applies them to the player's ECS components.
 *
 * @see EquipmentSectionIds
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
        // Only handle hotbar and utility section switches
        int sectionId = event.getInventorySectionId();
        if (sectionId != EquipmentSectionIds.HOTBAR && sectionId != EquipmentSectionIds.UTILITY) {
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

        LOGGER.at(Level.FINE).log("Recalculated stats for %s after %s slot change",
                uuid, sectionName);
    }
}
