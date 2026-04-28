package io.github.larsonix.trailoforbis.commands.tooadmin.give;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin command collection for giving items directly to players.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin give gear &lt;level&gt; [rarity] [quality] - Generate gear on held item</li>
 *   <li>/tooadmin give map &lt;level&gt; [rarity] [quality] - Give a realm map</li>
 *   <li>/tooadmin give stone &lt;type&gt; - Give a currency stone</li>
 * </ul>
 */
public final class TooAdminGiveCommand extends AbstractCommandCollection {

    public TooAdminGiveCommand(TrailOfOrbis plugin) {
        super("give", "Give RPG items to yourself");

        this.addSubCommand(new TooAdminGiveGearCommand(plugin));
        this.addSubCommand(new TooAdminGiveMapCommand(plugin));
        this.addSubCommand(new TooAdminGiveStoneCommand());
    }
}
