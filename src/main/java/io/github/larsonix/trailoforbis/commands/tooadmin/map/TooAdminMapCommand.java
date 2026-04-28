package io.github.larsonix.trailoforbis.commands.tooadmin.map;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin map command collection.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin map info - Show held map's data</li>
 *   <li>/tooadmin map copy - Copy held map's data to clipboard</li>
 *   <li>/tooadmin map paste - Paste clipboard data onto held map</li>
 * </ul>
 */
public final class TooAdminMapCommand extends AbstractCommandCollection {

    public TooAdminMapCommand(TrailOfOrbis plugin) {
        super("map", "Admin map commands");

        this.addSubCommand(new TooAdminMapInfoCommand(plugin));
        this.addSubCommand(new TooAdminMapCopyCommand(plugin));
        this.addSubCommand(new TooAdminMapPasteCommand(plugin));
    }
}
