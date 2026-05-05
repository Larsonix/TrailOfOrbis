package io.github.larsonix.trailoforbis.gear.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ECS event system that handles game mode transitions for the RPG plugin.
 *
 * <p>When a player switches to Creative mode:
 * <ul>
 *   <li>Gear requirement checks are bypassed</li>
 *   <li>All RPG stat modifiers are removed from ECS (health, mana, movement)</li>
 *   <li>Energy shield HUD is removed, vanilla health bar restored</li>
 *   <li>XP bar HUD is removed</li>
 * </ul>
 *
 * <p>When a player switches back to Adventure/Survival:
 * <ul>
 *   <li>Gear requirements are re-enabled</li>
 *   <li>Full stat recalculation + ECS application is triggered</li>
 *   <li>Energy shield HUD and XP bar HUD are restored</li>
 * </ul>
 *
 * @see ChangeGameModeEvent
 * @see StatsApplicationSystem#removeAllRpgModifiers
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
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        // Toggle gear requirement bypass
        ServiceRegistry.get(GearService.class).ifPresent(gearService -> {
            if (newMode == GameMode.Creative) {
                gearService.addRequirementBypass(uuid);
            } else {
                gearService.removeRequirementBypass(uuid);
            }
        });

        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        Player player = ref != null ? store.getComponent(ref, Player.getComponentType()) : null;

        if (newMode == GameMode.Creative) {
            // === ENTERING CREATIVE ===

            // Remove all RPG stat modifiers (health, mana, stamina, movement)
            // so Hytale manages this player's stats natively.
            if (ref != null) {
                StatsApplicationSystem.removeAllRpgModifiers(store, ref, playerRef);
            }

            // Toggle HUDs: remove energy shield + XP bar, restore vanilla health bar
            if (rpg != null && player != null) {
                if (rpg.getEnergyShieldHudManager() != null) {
                    rpg.getEnergyShieldHudManager().onCreativeModeEnter(uuid, player, playerRef);
                }
                if (rpg.getXpBarHudManager() != null) {
                    // Use discardStaleHud — game mode switch resets HUD managers,
                    // removeHud()'s hide() would send Set commands to cleared elements.
                    rpg.getXpBarHudManager().discardStaleHud(uuid);
                }
            }

            // Clean up energy shield tracker state
            if (rpg != null && rpg.getEnergyShieldTracker() != null) {
                rpg.getEnergyShieldTracker().cleanupPlayer(uuid);
            }

        } else {
            // === LEAVING CREATIVE ===
            // CRITICAL: ChangeGameModeEvent fires via synchronous store.invoke()
            // BEFORE Player.setGameModeInternal() updates the game mode field.
            // player.getGameMode() still returns Creative here, so our guards in
            // StatsApplicationSystem and EnergyShieldHudManager.showHud() would
            // reject the work. Defer everything to world.execute() which runs
            // AFTER setGameModeInternal() commits the new game mode.
            var world = store.getExternalData().getWorld();
            world.execute(() -> {
                // Reapply RPG stats — game mode is now committed, guards will pass
                ServiceRegistry.get(AttributeService.class).ifPresent(attrService ->
                    attrService.recalculateStats(uuid));

                // Restore HUDs
                if (rpg != null) {
                    // Energy shield HUD — showHud() checks game mode, which is now correct
                    if (rpg.getEnergyShieldHudManager() != null) {
                        // Re-resolve Player from store (ref may have moved after event)
                        if (ref != null && ref.isValid()) {
                            Player p = store.getComponent(ref, Player.getComponentType());
                            if (p != null) {
                                rpg.getEnergyShieldHudManager().showHud(uuid, playerRef, p);
                            }
                        }
                    }
                    // XP bar HUD
                    if (rpg.getXpBarHudManager() != null) {
                        rpg.getXpBarHudManager().showHud(uuid, playerRef, store);
                    }
                }
            });
        }

        LOGGER.at(Level.INFO).log("Game mode changed to %s for player %s — RPG stats %s",
            newMode, uuid, newMode == GameMode.Creative ? "removed" : "reapplied");
    }
}
