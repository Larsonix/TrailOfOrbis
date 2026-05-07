package io.github.larsonix.trailoforbis.ui.hud;

import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.utils.MultiHudWrapper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.EnergyShieldTracker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the combined health + energy shield bar HUD lifecycle.
 *
 * <p>Hides the native {@link HudComponent#Health} and replaces it with a
 * custom HyUI HUD that renders both health (opaque red) and shield (40%
 * opacity cyan) as native ProgressBars. Uses {@link au.ellie.hyui.builders.HudBuilder}
 * for proper MultipleHUD integration (same pattern as {@link XpBarHudManager}).
 *
 * <p>Creative mode players keep the vanilla health bar — our HUD is never
 * shown in creative.
 *
 * @see EnergyShieldHud
 */
public class EnergyShieldHudManager implements PersistentHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<UUID, HyUIHud> activeHuds = new ConcurrentHashMap<>();
    private final Map<UUID, Float> healthRatios = new ConcurrentHashMap<>();
    private final EnergyShieldTracker shieldTracker;
    private final AttributeManager attributeManager;

    @Nullable private HudToggleService hudToggleService;

    public EnergyShieldHudManager(
            @Nonnull EnergyShieldTracker shieldTracker,
            @Nonnull AttributeManager attributeManager) {
        this.shieldTracker = shieldTracker;
        this.attributeManager = attributeManager;
    }

    public void setHudToggleService(@Nullable HudToggleService service) {
        this.hudToggleService = service;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHOW / REMOVE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Shows the combined health+shield HUD, hiding the native health bar.
     * Must be called from the world thread. Skips creative mode players.
     */
    public void showHud(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Player player) {
        if (player.getGameMode() == GameMode.Creative) {
            return;
        }

        discardStaleHud(playerId);

        try {
            player.getHudManager().hideHudComponents(playerRef, HudComponent.Health);

            HyUIHud hud = EnergyShieldHud.create(
                playerRef, shieldTracker, this::getMaxShield, this::getHealthRatio);
            activeHuds.put(playerId, hud);
            if (hudToggleService != null) hudToggleService.applyToggleState(playerId, hud);
            LOGGER.atInfo().log("Showed health+shield HUD for player %s",
                playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to show health+shield HUD for player %s",
                playerId.toString().substring(0, 8));
            // Re-show native health bar on failure
            try {
                player.getHudManager().showHudComponents(playerRef, HudComponent.Health);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Removes the HUD and re-shows the native health bar.
     */
    public void removeHud(@Nonnull UUID playerId,
                          @Nullable Player player, @Nullable PlayerRef playerRef) {
        HyUIHud hud = activeHuds.remove(playerId);
        healthRatios.remove(playerId);
        if (hud == null) {
            return;
        }

        try {
            hud.remove();
            if (player != null && playerRef != null) {
                player.getHudManager().showHudComponents(playerRef, HudComponent.Health);
            }
            LOGGER.atFine().log("Removed health+shield HUD for player %s",
                playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove health+shield HUD for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    /**
     * Removes all active HUDs. Called during plugin shutdown.
     */
    public void removeAllHuds() {
        int count = activeHuds.size();
        if (count > 0) {
            LOGGER.atInfo().log("Removing all health+shield HUDs (%d total)", count);
            for (UUID playerId : Set.copyOf(activeHuds.keySet())) {
                HyUIHud hud = activeHuds.remove(playerId);
                if (hud != null) {
                    try {
                        hud.remove();
                    } catch (Exception ignored) {}
                }
            }
        }
        healthRatios.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UPDATES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Updates the health ratio for a player. Called from damage pipeline
     * and regeneration system.
     */
    public void updateHealthRatio(@Nonnull UUID playerId, float healthRatio) {
        healthRatios.put(playerId, healthRatio);
        triggerRefresh(playerId);
    }

    /**
     * Notifies that shield values changed for a player.
     */
    public void notifyShieldChanged(@Nonnull UUID playerId) {
        triggerRefresh(playerId);
    }

    /**
     * Triggers a safe full rerender of the HUD for a player.
     *
     * <p>Uses {@link HudRefreshHelper#safeRefreshWithToggle} for atomic Clear+Append.
     */
    private void triggerRefresh(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.get(playerId);
        if (hud == null) {
            return;
        }

        try {
            HudRefreshHelper.safeRefreshWithToggle(hud, playerId, hudToggleService);
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log(
                "Failed to trigger shield HUD refresh for player %s",
                playerId.toString().substring(0, 8));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CREATIVE MODE
    // ═══════════════════════════════════════════════════════════════════

    public void onCreativeModeEnter(@Nonnull UUID playerId,
                                    @Nonnull Player player,
                                    @Nonnull PlayerRef playerRef) {
        // Use discardStaleHud() instead of removeHud() — safe path that cancels
        // the refresh task without sending any packets to the client. During game mode
        // switches the client UI state may be transitional, so avoid hide()/Set commands.
        discardStaleHud(playerId);
        healthRatios.remove(playerId);

        // Re-show vanilla health bar (safe — HudManager survives the reset)
        try {
            player.getHudManager().showHudComponents(playerRef, HudComponent.Health);
        } catch (Exception ignored) {}
    }

    public void onCreativeModeExit(@Nonnull UUID playerId,
                                   @Nonnull PlayerRef playerRef,
                                   @Nonnull Player player) {
        showHud(playerId, playerRef, player);
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD TRANSITIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Discards stale HUD during world transitions WITHOUT sending packets.
     *
     * <p>Cancels the refresh task directly via reflection. HyUI's {@code hud.remove()}
     * early-returns when {@code getStore()} returns null during transitions, skipping
     * {@code refreshTask.cancel()}.
     */
    public void discardStaleHud(@Nonnull UUID playerId) {
        HyUIHud hud = activeHuds.remove(playerId);
        if (hud == null) {
            return;
        }

        HudRefreshHelper.cancelRefreshTask(hud);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    private float getMaxShield(@Nonnull UUID playerId) {
        ComputedStats stats = attributeManager.getStats(playerId);
        if (stats == null) {
            return 0f;
        }
        float baseShield = stats.getEnergyShield();
        float effectiveness = stats.getShieldEffectivenessPercent();
        return baseShield * (1f + effectiveness / 100f);
    }

    private float getHealthRatio(@Nonnull UUID playerId) {
        return healthRatios.getOrDefault(playerId, 1.0f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    public HyUIHud getHud(@Nonnull UUID playerId) {
        return activeHuds.get(playerId);
    }

    public boolean hasHud(@Nonnull UUID playerId) {
        return activeHuds.containsKey(playerId);
    }

    public int getActiveCount() {
        return activeHuds.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PersistentHud INTERFACE
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    @Override
    public String hudName() {
        return "energy-shield";
    }

    @Override
    public void discardStale(@Nonnull UUID playerId) {
        discardStaleHud(playerId);
    }

    @Override
    public boolean isActive(@Nonnull UUID playerId) {
        return hasHud(playerId);
    }

    @Override
    public void restore(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Store<EntityStore> store) {
        restore(playerId, playerRef, store, null);
    }

    @Override
    public void restore(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef,
                        @Nonnull Store<EntityStore> store, @Nullable Player player) {
        if (player != null) {
            // Event Player available — bypass getReference() null check.
            // The event Player is guaranteed valid at PlayerReadyEvent time.
            if (player.getGameMode() == GameMode.Creative) {
                return;
            }
            showHud(playerId, playerRef, player);

            // Direct MultiHud registration — bypasses HyUI safeAdd()'s internal
            // getReference() check which returns null during early world transitions.
            HyUIHud hud = activeHuds.get(playerId);
            if (hud != null) {
                MultiHudWrapper.setCustomHud(player, playerRef, hud.name, hud);
            }
            return;
        }

        // Safety net path — resolve Player from store. By 1-3s after transition,
        // getReference() should be valid.
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        Player resolved = store.getComponent(entityRef, Player.getComponentType());
        if (resolved == null || resolved.getGameMode() == GameMode.Creative) {
            return;
        }
        showHud(playerId, playerRef, resolved);
    }

    @Override
    public void removeOnDisconnect(@Nonnull UUID playerId) {
        // Pass null for Player/PlayerRef since entity is invalid during disconnect
        removeHud(playerId, null, null);
    }

    @Override
    public void shutdown() {
        removeAllHuds();
    }
}
