package io.github.larsonix.trailoforbis.commands.tooadmin.gear;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin gear command collection.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin gear info</li>
 *   <li>/tooadmin gear setlevel &lt;level&gt;</li>
 *   <li>/tooadmin gear setrarity &lt;rarity&gt;</li>
 *   <li>/tooadmin gear setquality &lt;quality&gt;</li>
 *   <li>/tooadmin gear reload</li>
 *   <li>/tooadmin gear copy - Copy held gear stats to clipboard</li>
 *   <li>/tooadmin gear paste - Paste clipboard stats onto held gear</li>
 * </ul>
 *
 * <p>Note: To generate gear on held item, use {@code /tooadmin give gear <level> <rarity> <quality>}.
 */
public final class TooAdminGearCommand extends AbstractCommandCollection {

    public TooAdminGearCommand(TrailOfOrbis plugin) {
        super("gear", "Admin gear commands");

        this.addSubCommand(new TooAdminGearInfoCommand(plugin));
        this.addSubCommand(new TooAdminGearSetLevelCommand(plugin));
        this.addSubCommand(new TooAdminGearSetRarityCommand(plugin));
        this.addSubCommand(new TooAdminGearSetQualityCommand(plugin));
        this.addSubCommand(new TooAdminGearReloadCommand(plugin));
        this.addSubCommand(new TooAdminGearCopyCommand(plugin));
        this.addSubCommand(new TooAdminGearPasteCommand(plugin));
    }
}
