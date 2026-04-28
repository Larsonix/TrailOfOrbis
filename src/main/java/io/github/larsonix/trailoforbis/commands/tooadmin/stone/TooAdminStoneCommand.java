package io.github.larsonix.trailoforbis.commands.tooadmin.stone;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin stone command collection.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin stone info - Show held stone's data</li>
 *   <li>/tooadmin stone copy - Copy held stone's type to clipboard</li>
 *   <li>/tooadmin stone paste - Paste clipboard stone type onto held stone</li>
 * </ul>
 */
public final class TooAdminStoneCommand extends AbstractCommandCollection {

    public TooAdminStoneCommand(TrailOfOrbis plugin) {
        super("stone", "Admin stone commands");

        this.addSubCommand(new TooAdminStoneInfoCommand(plugin));
        this.addSubCommand(new TooAdminStoneCopyCommand(plugin));
        this.addSubCommand(new TooAdminStonePasteCommand(plugin));
        this.addSubCommand(new TooAdminStoneStatusCommand(plugin));
    }
}
