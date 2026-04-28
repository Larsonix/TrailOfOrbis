package io.github.larsonix.trailoforbis.maps.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmPlayerComponent;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.ui.RealmDefeatHud;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * ECS tick system that handles realm timeout checking.
 *
 * <p>This system monitors players in realms and checks for:
 * <ul>
 *   <li>Time limit expiration - Kicks players and closes realm</li>
 *   <li>Warning messages as time runs low</li>
 *   <li>Idle timeout for inactive players</li>
 * </ul>
 *
 * <p>Time limits are configured per realm size in {@code realms.yml}.
 *
 * <p><b>Important:</b> This system processes players, not realms directly.
 * Realm cleanup is triggered when the last player's timer expires or they exit.
 *
 * @see RealmPlayerComponent
 * @see RealmsManager
 */
public class RealmTimerSystem extends EntityTickingSystem<EntityStore> {

    private final TrailOfOrbis plugin;

    /**
     * Time accumulator for periodic checks (we don't need to check every tick).
     * Resets after each check interval.
     */
    private float checkAccumulator = 0;

    /**
     * How often to check timers (seconds).
     */
    private static final float CHECK_INTERVAL = 1.0f;

    /**
     * Warning thresholds in seconds remaining.
     */
    private static final int[] WARNING_THRESHOLDS = {300, 120, 60, 30, 10};

    // Component types for query
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private ComponentType<EntityStore, RealmPlayerComponent> realmPlayerType;
    private Archetype<EntityStore> playerQuery = null;

    /**
     * Creates a new realm timer system.
     *
     * @param plugin The TrailOfOrbis plugin instance
     */
    public RealmTimerSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.playerRefType = PlayerRef.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Lazy initialization to ensure component types are registered
        if (playerQuery == null) {
            realmPlayerType = RealmPlayerComponent.getComponentType();
            playerQuery = Archetype.of(playerRefType, realmPlayerType);
        }
        return playerQuery;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Accumulate time and only process periodically
        checkAccumulator += dt;
        if (checkAccumulator < CHECK_INTERVAL) {
            return;
        }

        // Only reset on first entity of this tick batch
        if (index == 0) {
            checkAccumulator = 0;
        }

        // Get entity reference
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        // Get player and realm components
        PlayerRef playerRef = store.getComponent(entityRef, playerRefType);
        RealmPlayerComponent realmComp = store.getComponent(entityRef, realmPlayerType);

        if (playerRef == null || realmComp == null || !realmComp.isInRealm()) {
            return;
        }

        UUID realmId = realmComp.getRealmId();
        if (realmId == null) {
            return;
        }

        // Get realm instance and config
        RealmsManager manager = plugin.getRealmsManager();
        if (manager == null) {
            return;
        }

        Optional<RealmInstance> realmOpt = manager.getRealm(realmId);
        if (realmOpt.isEmpty()) {
            // Realm was closed - clean up player component
            commandBuffer.removeComponent(entityRef, realmPlayerType);
            return;
        }
        RealmInstance realm = realmOpt.get();

        // Utility biomes (skill sanctum) have no timer — infinite duration
        if (realm.getBiome().isUtilityBiome()) {
            return;
        }

        // If already in defeat phase, check if it's time to teleport out
        if (realmComp.isInDefeatPhase()) {
            int defeatElapsed = realmComp.getDefeatPhaseElapsedSeconds();
            if (defeatElapsed >= RealmDefeatHud.getDefeatPhaseSeconds()) {
                // Defeat phase over — teleport out
                handleDefeatPhaseComplete(playerRef, manager);
            }
            return;
        }

        // Check time limit (per-instance timeout from map data + size + modifiers)
        int timeLimitSeconds = (int) realm.getTimeout().toSeconds();
        int elapsedSeconds = realmComp.getElapsedSeconds();
        int remainingSeconds = timeLimitSeconds - elapsedSeconds;

        if (remainingSeconds <= 0) {
            // Time's up — enter defeat phase (show HUD, wait 10s before teleport)
            startDefeatPhase(playerRef, realmComp, realm, manager);
            return;
        }

        // Check for warning messages
        for (int threshold : WARNING_THRESHOLDS) {
            // Send warning at exact threshold (within 1 second tolerance)
            if (remainingSeconds <= threshold && remainingSeconds > threshold - 1) {
                sendTimeWarning(playerRef, remainingSeconds);
                break;
            }
        }
    }

    /**
     * Starts the defeat phase — shows defeat HUD and marks component.
     *
     * <p>The player stays in the realm for {@link RealmDefeatHud#getDefeatPhaseSeconds()}
     * seconds to see the defeat banner before being teleported out.
     */
    private void startDefeatPhase(
            @Nonnull PlayerRef playerRef,
            @Nonnull RealmPlayerComponent realmComp,
            @Nonnull RealmInstance realm,
            @Nonnull RealmsManager manager) {

        // Mark defeat phase started
        realmComp.startDefeatPhase();

        // Send chat message
        playerRef.sendMessage(Message.raw("§c§lTime's Up ! §7You will be teleported out shortly."));

        // Show defeat HUD (replaces combat HUD)
        try {
            RealmHudManager hudManager = manager.getHudManager();
            hudManager.showDefeatHud(playerRef.getUuid(), playerRef, realm);
        } catch (Exception e) {
            // HUD failure is non-fatal — player will still be teleported after the phase
        }
    }

    /**
     * Handles the end of the defeat phase — teleports the player out.
     */
    private void handleDefeatPhaseComplete(
            @Nonnull PlayerRef playerRef,
            @Nonnull RealmsManager manager) {

        manager.exitRealm(playerRef.getUuid());
    }

    /**
     * Sends a time warning message to the player.
     *
     * @param playerRef The player
     * @param remainingSeconds Seconds remaining
     */
    private void sendTimeWarning(@Nonnull PlayerRef playerRef, int remainingSeconds) {
        String timeStr;
        if (remainingSeconds >= 60) {
            int minutes = remainingSeconds / 60;
            timeStr = minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            timeStr = remainingSeconds + " seconds";
        }

        String color = remainingSeconds <= 30 ? "§c" : (remainingSeconds <= 60 ? "§e" : "§a");
        playerRef.sendMessage(Message.raw(color + timeStr + " remaining !"));
    }
}
