package io.github.larsonix.trailoforbis.commands.tooadmin.loot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.loot.DynamicLootRegistry;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Rescans Hytale's item registry for droppable equipment.
 *
 * <p>Usage: /tooadmin loot rescan
 *
 * <p>This is useful if a mod was hot-loaded after the server started,
 * or to refresh the discovery cache after config changes.
 */
public class TooAdminLootRescanCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminLootRescanCommand(TrailOfOrbis plugin) {
        super("rescan", "Rescan item registry for droppable items");
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
        DynamicLootRegistry registry = plugin.getGearManager().getDynamicLootRegistry();

        if (registry == null) {
            sender.sendMessage(Message.raw("Loot registry not available !")
                    .color(MessageColors.ERROR));
            return;
        }

        sender.sendMessage(Message.raw("Rescanning item registry...")
                .color(MessageColors.INFO));

        int previousCount = registry.getTotalItemCount();

        // Rescan
        registry.discoverItems();

        int newCount = registry.getTotalItemCount();
        int diff = newCount - previousCount;

        String diffStr = diff > 0 ? "+" + diff : String.valueOf(diff);
        sender.sendMessage(Message.raw(String.format(
                "Rescan complete! Found %d items (%s)", newCount, diffStr))
                .color(MessageColors.SUCCESS));

        // Show hint
        sender.sendMessage(Message.raw("Use /tooadmin loot stats for details.")
                .color(MessageColors.GRAY));
    }
}
