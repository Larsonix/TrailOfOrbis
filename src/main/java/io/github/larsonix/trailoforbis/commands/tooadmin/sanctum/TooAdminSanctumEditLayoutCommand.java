package io.github.larsonix.trailoforbis.commands.tooadmin.sanctum;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Opens a special sanctum instance for layout editing.
 *
 * <p>Usage: /tooadmin sanctum editlayout
 *
 * <p>This opens a sanctum with all nodes spawned at their current procedural positions.
 * You can then move nodes in-game and use /tooadmin sanctum exportlayout to save their positions.
 */
public final class TooAdminSanctumEditLayoutCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminSanctumEditLayoutCommand(TrailOfOrbis plugin) {
        super("editlayout", "Open a sanctum for layout editing");
        this.addAliases("edit");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        SkillSanctumManager sanctumManager = plugin.getSkillSanctumManager();
        if (sanctumManager == null || !sanctumManager.isEnabled()) {
            sender.sendMessage(Message.raw("Skill Sanctum system is not enabled !").color(MessageColors.ERROR));
            return;
        }

        UUID playerId = sender.getUuid();

        // Check if already in a sanctum
        if (sanctumManager.hasActiveSanctum(playerId)) {
            sender.sendMessage(Message.raw("You are already in a sanctum! Use /sanctum exit first.").color(MessageColors.ERROR));
            return;
        }

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Opening layout editor sanctum...").color(MessageColors.WHITE)));

        sender.sendMessage(Message.raw("All nodes will be spawned at their current positions.").color(MessageColors.GRAY));
        sender.sendMessage(Message.raw("Move nodes using game tools, then use /tooadmin sanctum exportlayout to save.").color(MessageColors.GRAY));

        // Open the sanctum normally - all nodes are spawned
        sanctumManager.openSanctum(sender).thenAccept(success -> {
            if (success) {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[Layout Editor] ").color("#9966FF"))
                    .insert(Message.raw("Sanctum opened for editing !").color(MessageColors.SUCCESS)));
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("  1. ").color(MessageColors.GRAY))
                    .insert(Message.raw("Move nodes to desired positions").color(MessageColors.WHITE)));
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("  2. ").color(MessageColors.GRAY))
                    .insert(Message.raw("Run /tooadmin sanctum exportlayout to save").color(MessageColors.WHITE)));
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("  3. ").color(MessageColors.GRAY))
                    .insert(Message.raw("Exit with /sanctum exit").color(MessageColors.WHITE)));
            } else {
                sender.sendMessage(Message.raw("Failed to open layout editor sanctum.").color(MessageColors.ERROR));
            }
        });
    }
}
