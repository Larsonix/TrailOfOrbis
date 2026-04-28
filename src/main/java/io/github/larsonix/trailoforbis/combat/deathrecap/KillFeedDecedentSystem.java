package io.github.larsonix.trailoforbis.combat.deathrecap;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Kill feed event system that provides contextual death messages for the victim.
 *
 * <p>When a player dies, this system intercepts the {@link KillFeedEvent.DecedentMessage}
 * and replaces the vanilla "PlayerName" message with a contextual message like
 * "Scorched to death by [Elite] Fire Trork (Lv45)".
 *
 * <p>Runs AFTER vanilla {@link PlayerSystems.KillFeedDecedentEventSystem} to override
 * its default message.
 */
public class KillFeedDecedentSystem
    extends EntityEventSystem<EntityStore, KillFeedEvent.DecedentMessage> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    private final TrailOfOrbis plugin;

    public KillFeedDecedentSystem(@Nonnull TrailOfOrbis plugin) {
        super(KillFeedEvent.DecedentMessage.class);
        this.plugin = plugin;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, PlayerSystems.KillFeedDecedentEventSystem.class)
        );
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Only process player deaths (entities with PlayerRef)
        return playerRefType;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull KillFeedEvent.DecedentMessage event
    ) {
        DeathRecapTracker tracker = plugin.getDeathRecapTracker();
        if (tracker == null || !tracker.getConfig().isKillFeedEnabled()) {
            return;
        }

        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        if (playerRef == null) {
            return;
        }

        CombatSnapshot snapshot = tracker.peekLastDamage(playerRef.getUuid());
        if (snapshot == null) {
            return;
        }

        boolean contextual = tracker.getConfig().isKillFeedContextual();
        Message contextualMessage = DeathMessageBuilder.buildDecedentMessage(snapshot, contextual);
        if (contextualMessage != null) {
            event.setMessage(contextualMessage);
        }
    }
}
