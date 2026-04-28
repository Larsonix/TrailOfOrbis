package io.github.larsonix.trailoforbis.commands.too.sanctum;

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
 * Enters the Skill Sanctum.
 *
 * <p>Usage: /too sanctum enter
 */
public final class TooSanctumEnterCommand extends OpenPlayerCommand {
    private final TrailOfOrbis plugin;

    public TooSanctumEnterCommand(TrailOfOrbis plugin) {
        super("enter", "Enter the Skill Sanctum");
        this.addAliases("open", "go");
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
            player.sendMessage(Message.raw("The Skill Sanctum is not available.").color(MessageColors.ERROR));
            return;
        }

        UUID playerId = player.getUuid();

        // Check if already in sanctum
        if (sanctumManager.hasActiveSanctum(playerId)) {
            player.sendMessage(Message.raw("You are already in the Skill Sanctum.").color(MessageColors.WARNING));
            player.sendMessage(Message.raw("Use /too sanctum exit to leave.").color(MessageColors.GRAY));
            return;
        }

        // Open sanctum
        player.sendMessage(Message.raw("Opening Skill Sanctum...").color(MessageColors.INFO));

        sanctumManager.openSanctum(player).thenAccept(success -> {
            if (success) {
                player.sendMessage(Message.empty()
                    .insert(Message.raw("Welcome to your ").color(MessageColors.GOLD))
                    .insert(Message.raw("Skill Sanctum").color("#9966FF"))
                    .insert(Message.raw(" !").color(MessageColors.GOLD)));
                player.sendMessage(Message.raw("Walk among the orbs and press F to allocate skill points.").color(MessageColors.GRAY));
                player.sendMessage(Message.raw("Use /too sanctum exit when you're done.").color(MessageColors.GRAY));
            } else {
                player.sendMessage(Message.raw("Failed to open Skill Sanctum. Please try again.").color(MessageColors.ERROR));
            }
        });
    }
}
