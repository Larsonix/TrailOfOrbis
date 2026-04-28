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
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumInstance;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Exits the Skill Sanctum.
 *
 * <p>Usage: /too sanctum exit
 */
public final class TooSanctumExitCommand extends OpenPlayerCommand {
    private final TrailOfOrbis plugin;

    public TooSanctumExitCommand(TrailOfOrbis plugin) {
        super("exit", "Exit the Skill Sanctum");
        this.addAliases("leave", "close");
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

        // Check if in sanctum
        if (!sanctumManager.hasActiveSanctum(playerId)) {
            player.sendMessage(Message.raw("You are not in the Skill Sanctum.").color(MessageColors.WARNING));
            return;
        }

        // Get instance for skill point summary
        SkillSanctumInstance instance = sanctumManager.getSanctumInstance(playerId);
        int allocatedCount = instance != null ? instance.getAllocatedNodeCount() : 0;

        // Close sanctum (this will teleport player back)
        boolean closed = sanctumManager.closeSanctum(playerId);

        if (closed) {
            player.sendMessage(Message.empty()
                .insert(Message.raw("Exited Skill Sanctum. ").color(MessageColors.SUCCESS))
                .insert(Message.raw("(" + allocatedCount + " nodes allocated)").color(MessageColors.GRAY)));
        } else {
            player.sendMessage(Message.raw("Failed to exit Skill Sanctum.").color(MessageColors.ERROR));
        }
    }
}
