package io.github.larsonix.trailoforbis.combat.deathrecap;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ECS system that displays death recap messages when players die.
 *
 * <p>Extends {@link DeathSystems.OnDeathSystem} to react when a player dies.
 * Retrieves the killing blow from {@link DeathRecapTracker} and formats
 * a detailed breakdown using {@link DeathRecapFormatter}.
 *
 * <p>This system runs after the death is processed, showing the player
 * exactly what killed them and how the damage was calculated.
 */
public class DeathRecapSystem extends DeathSystems.OnDeathSystem {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;

    public DeathRecapSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        // Only process player deaths
        return Player.getComponentType();
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeathComponent deathComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Get death recap tracker
        DeathRecapTracker tracker = plugin.getDeathRecapTracker();
        if (tracker == null) {
            return;
        }

        DeathRecapConfig config = tracker.getConfig();
        if (!config.isEnabled()) {
            return;
        }

        // Get player reference
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        String playerName = playerRef.getUsername();

        // Get the damage history BEFORE getKillingBlow (which clears the history)
        List<CombatSnapshot> history = tracker.getDamageHistory(playerId);

        // Get the killing blow snapshot (also clears history from tracker)
        Optional<CombatSnapshot> killingBlow = tracker.getKillingBlow(playerId);
        if (killingBlow.isEmpty()) {
            LOGGER.at(Level.FINE).log("No death recap data for %s - damage may have been from untracked source", playerName);
            return;
        }

        CombatSnapshot snapshot = killingBlow.get();

        // Format and send the death recap message with damage chain
        try {
            Message recap = DeathRecapFormatter.format(snapshot, history, config);
            playerRef.sendMessage(recap);

            LOGGER.at(Level.INFO).log("Sent death recap to %s: killed by %s (%.1f dmg, %d hits in chain)",
                playerName, snapshot.attackerName(), snapshot.finalDamage(), history.size());
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to send death recap to %s", playerName);
        }
    }
}
