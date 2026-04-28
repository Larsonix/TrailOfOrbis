package io.github.larsonix.trailoforbis.commands.tooadmin.loot;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin loot command collection.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin loot stats - Show discovered item statistics</li>
 *   <li>/tooadmin loot rescan - Rescan item registry for droppable items</li>
 * </ul>
 */
public final class TooAdminLootCommand extends AbstractCommandCollection {

    public TooAdminLootCommand(TrailOfOrbis plugin) {
        super("loot", "Admin loot discovery commands");

        this.addSubCommand(new TooAdminLootStatsCommand(plugin));
        this.addSubCommand(new TooAdminLootRescanCommand(plugin));
    }
}
