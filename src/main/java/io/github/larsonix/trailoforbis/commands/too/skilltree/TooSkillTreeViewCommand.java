package io.github.larsonix.trailoforbis.commands.too.skilltree;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Toggles entry/exit from the 3D Skill Sanctum.
 *
 * <p>If the player is outside the sanctum, this command enters it.
 * If the player is inside the sanctum, this command exits it.
 *
 * <p>Usage: /too skilltree view
 * <p>Aliases: /skilltree view, /skilltree, /st view
 */
public final class TooSkillTreeViewCommand extends OpenPlayerCommand {
    private final TrailOfOrbis plugin;

    public TooSkillTreeViewCommand(TrailOfOrbis plugin) {
        super("view", "Toggle the 3D Skill Sanctum");
        this.addAliases("open", "map", "show", "toggle");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        SkillSanctumManager sanctumManager = plugin.getSkillSanctumManager();
        if (sanctumManager == null || !sanctumManager.isEnabled()) {
            player.sendMessage(Message.raw("Skill Sanctum is not available.").color(MessageColors.ERROR));
            return;
        }

        UUID playerId = player.getUuid();

        if (sanctumManager.hasActiveSanctum(playerId)) {
            // Exit sanctum
            sanctumManager.closeSanctum(playerId);
            player.sendMessage(Message.raw("Exited Skill Sanctum.").color(MessageColors.SUCCESS));
        } else {
            // Enter sanctum
            player.sendMessage(Message.raw("Opening Skill Sanctum...").color(MessageColors.INFO));
            sanctumManager.openSanctum(player).thenAccept(success -> {
                if (success) {
                    player.sendMessage(Message.raw("Welcome to your Skill Sanctum !").color(MessageColors.GOLD));
                } else {
                    player.sendMessage(Message.raw("Failed to open Skill Sanctum.").color(MessageColors.ERROR));
                }
            });
        }
    }
}
