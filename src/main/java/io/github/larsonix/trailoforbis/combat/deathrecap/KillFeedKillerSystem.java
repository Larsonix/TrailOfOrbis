package io.github.larsonix.trailoforbis.combat.deathrecap;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Kill feed event system that provides contextual kill messages for the attacker.
 *
 * <p>When a player kills another entity, this system intercepts the
 * {@link KillFeedEvent.KillerMessage} and replaces the vanilla "PlayerName" message
 * with a contextual message like "You slew PlayerName with a critical fire strike!".
 *
 * <p>Only fires when the killer is a player (query matches PlayerRef).
 * Runs AFTER vanilla {@link PlayerSystems.KillFeedKillerEventSystem}.
 */
public class KillFeedKillerSystem
    extends EntityEventSystem<EntityStore, KillFeedEvent.KillerMessage> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    private final ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();

    private final TrailOfOrbis plugin;

    public KillFeedKillerSystem(@Nonnull TrailOfOrbis plugin) {
        super(KillFeedEvent.KillerMessage.class);
        this.plugin = plugin;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, PlayerSystems.KillFeedKillerEventSystem.class)
        );
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Only fires when the killer is a player
        return playerRefType;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull KillFeedEvent.KillerMessage event
    ) {
        DeathRecapTracker tracker = plugin.getDeathRecapTracker();
        if (tracker == null || !tracker.getConfig().isKillFeedEnabled()) {
            return;
        }

        // Get the target (victim) entity info
        Ref<EntityStore> targetRef = event.getTargetRef();
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // Get killer's PlayerRef to look up the last damage they dealt
        PlayerRef killerRef = archetypeChunk.getComponent(index, playerRefType);
        if (killerRef == null) {
            return;
        }

        // Get the victim's name from the target ref
        String targetName;
        PlayerRef targetPlayerRef = store.getComponent(targetRef, playerRefType);
        if (targetPlayerRef != null) {
            targetName = targetPlayerRef.getUsername();
        } else {
            // For mob targets, use the existing message content as fallback
            Message existingMessage = event.getMessage();
            if (existingMessage != null) {
                // The vanilla system already set this to the entity's display name
                return; // Keep vanilla message for mob kills — our contextual messages
                        // are most valuable for PvP where the killer sees their own message
            }
            return;
        }

        // For PvP kills, look up the victim's last damage to get snapshot data
        CombatSnapshot snapshot = tracker.peekLastDamage(targetPlayerRef.getUuid());
        if (snapshot == null) {
            return;
        }

        boolean contextual = tracker.getConfig().isKillFeedContextual();
        Message killerMessage = DeathMessageBuilder.buildKillerMessage(snapshot, targetName, contextual);
        if (killerMessage != null) {
            event.setMessage(killerMessage);
        }
    }
}
