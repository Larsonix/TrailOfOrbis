package io.github.larsonix.trailoforbis.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Central ECS event system for {@link InventoryChangeEvent}.
 *
 * <p>Replaces the removed {@code LivingEntityInventoryChangeEvent} IEvent.
 * The new {@code InventoryChangeEvent} is an ECS event that must be handled
 * via {@link EntityEventSystem}, not via {@code EventRegistry.registerGlobal()}.
 *
 * <p>This system dispatches to registered handlers in order, passing the
 * {@link Player} component and the event. Handlers are registered at construction
 * time and called in registration order.
 *
 * @see InventoryChangeEvent
 */
public class InventoryChangeEventSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final List<BiConsumer<Player, InventoryChangeEvent>> handlers = new ArrayList<>();

    // Diagnostic counter — tracks how many events fire per second (reset periodically)
    private int eventCount = 0;
    private long lastLogTime = 0;

    public InventoryChangeEventSystem() {
        super(InventoryChangeEvent.class);
    }

    /**
     * Registers a handler that will be called for every player inventory change.
     *
     * <p>Handlers receive the Player component and the InventoryChangeEvent.
     * They are called in registration order.
     *
     * @param handler The handler to register
     */
    public void addHandler(@Nonnull BiConsumer<Player, InventoryChangeEvent> handler) {
        handlers.add(handler);
    }

    /**
     * Registers a handler at the FRONT of the handler list (highest priority).
     * Use for handlers that need to intercept events before all others.
     *
     * @param handler The handler to register
     */
    public void addFirstHandler(@Nonnull BiConsumer<Player, InventoryChangeEvent> handler) {
        handlers.add(0, handler);
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InventoryChangeEvent event) {

        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            return;
        }

        // DIAGNOSTIC: Log event rate to detect feedback loops
        eventCount++;
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= 2000) { // every 2 seconds
            if (eventCount > 5) { // only log if suspicious volume
                LOGGER.atInfo().log("[DIAG] InventoryChangeEvent fired %d times in last 2s (container: %s)",
                    eventCount, event.getItemContainer() != null ? event.getItemContainer().getClass().getSimpleName() : "null");
            }
            eventCount = 0;
            lastLogTime = now;
        }

        for (BiConsumer<Player, InventoryChangeEvent> handler : handlers) {
            try {
                handler.accept(player, event);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in inventory change handler");
            }
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
