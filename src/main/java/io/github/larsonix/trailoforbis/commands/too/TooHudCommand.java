package io.github.larsonix.trailoforbis.commands.too;

import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.ui.hud.HudRefreshHelper;
import io.github.larsonix.trailoforbis.ui.hud.HudToggleService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Toggles all custom RPG HUDs on/off for the player.
 *
 * <p>Usage: {@code /hud}
 *
 * <p>Hytale's F8 key only toggles vanilla HUD components. This command
 * provides an equivalent toggle for our custom HUDs (XP bar, energy shield,
 * realm combat, skill points, skill node details).
 */
public class TooHudCommand extends OpenPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooHudCommand(@Nonnull TrailOfOrbis plugin) {
        super("hud", "Toggle custom HUD visibility");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {

        HudToggleService toggleService = plugin.getHudToggleService();
        if (toggleService == null) {
            player.sendMessage(Message.raw("HUD toggle not available."));
            return;
        }

        UUID playerId = player.getUuid();
        boolean nowHidden = toggleService.toggle(playerId);

        if (nowHidden) {
            hideAll(playerId);
            player.sendMessage(Message.raw("HUDs hidden. Type /hud to show them again.").color("#888888"));
        } else {
            unhideAll(playerId);
            player.sendMessage(Message.raw("HUDs visible.").color("#55FF55"));
        }
    }

    private void hideAll(@Nonnull UUID playerId) {
        applyToAll(playerId, true);
    }

    private void unhideAll(@Nonnull UUID playerId) {
        applyToAll(playerId, false);
    }

    private void applyToAll(@Nonnull UUID playerId, boolean hide) {
        // XP Bar
        var xpManager = plugin.getXpBarHudManager();
        if (xpManager != null) {
            applyToHud(xpManager.getHud(playerId), hide);
        }

        // Energy Shield
        var shieldManager = plugin.getEnergyShieldHudManager();
        if (shieldManager != null) {
            applyToHud(shieldManager.getHud(playerId), hide);
        }

        // Skill Sanctum HUDs (Skill Points + Skill Node Detail)
        SkillSanctumManager sanctum = plugin.getSkillSanctumManager();
        if (sanctum != null) {
            var pointsManager = sanctum.getSkillPointHudManager();
            if (pointsManager != null) {
                applyToHud(pointsManager.getHud(playerId), hide);
            }
            var nodeManager = sanctum.getSkillNodeHudManager();
            if (nodeManager != null) {
                applyToHud(nodeManager.getActiveHud(playerId), hide);
            }
        }

        // Realm HUDs (Combat, Victory, Defeat)
        RealmsManager realms = RealmsManager.get();
        if (realms != null) {
            RealmHudManager realmHuds = realms.getHudManager();
            if (realmHuds != null) {
                realmHuds.applyToggle(playerId, hide);
            }
        }
    }

    private void applyToHud(@Nullable HyUIHud hud, boolean hide) {
        if (hud == null) return;
        HudRefreshHelper.safeSetVisibility(hud, !hide);
    }
}
