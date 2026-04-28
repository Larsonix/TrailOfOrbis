package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearService;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ECS event system that toggles gear requirement bypass when a player's game mode changes.
 *
 * <p>When a player switches to Creative mode, all gear requirement checks
 * (level + attributes) are bypassed. Switching back to Adventure mode
 * re-enables requirement enforcement.
 *
 * <p>This is controlled by the {@code creativeModeBypassRequirements} config toggle.
 * The system only manages the bypass set — the actual bypass logic lives in
 * {@link io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator}.
 *
 * @see ChangeGameModeEvent
 * @see GearService#addRequirementBypass(UUID)
 * @see GearService#removeRequirementBypass(UUID)
 */
public final class GameModeChangeSystem extends EntityEventSystem<EntityStore, ChangeGameModeEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    public GameModeChangeSystem() {
        super(ChangeGameModeEvent.class);
        this.playerRefType = PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return playerRefType;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull ChangeGameModeEvent event
    ) {
        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        GameMode newMode = event.getGameMode();

        ServiceRegistry.get(GearService.class).ifPresent(gearService -> {
            if (newMode == GameMode.Creative) {
                gearService.addRequirementBypass(uuid);
            } else {
                gearService.removeRequirementBypass(uuid);
            }
            LOGGER.at(Level.FINE).log("Game mode changed to %s for player %s", newMode, uuid);
        });
    }
}
