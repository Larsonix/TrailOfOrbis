package io.github.larsonix.trailoforbis.commands.tooadmin.stats;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin stats command collection for debug stat manipulation.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin stats set &lt;player&gt; &lt;stat&gt; &lt;value&gt; - Force stat to exact value</li>
 *   <li>/tooadmin stats add &lt;player&gt; &lt;stat&gt; &lt;value&gt; - Add bonus to stat</li>
 *   <li>/tooadmin stats remove &lt;player&gt; &lt;stat&gt; - Remove override for stat</li>
 *   <li>/tooadmin stats clear &lt;player&gt; - Clear all overrides</li>
 *   <li>/tooadmin stats list [category] - List available stat names</li>
 *   <li>/tooadmin stats inspect &lt;player&gt; - Full computed stats dump</li>
 * </ul>
 */
public final class TooAdminStatsCommand extends AbstractCommandCollection {

    public TooAdminStatsCommand(TrailOfOrbis plugin) {
        super("stats", "Debug stat override commands");

        this.addSubCommand(new TooAdminStatsSetCommand(plugin));
        this.addSubCommand(new TooAdminStatsAddCommand(plugin));
        this.addSubCommand(new TooAdminStatsRemoveCommand(plugin));
        this.addSubCommand(new TooAdminStatsClearCommand(plugin));
        this.addSubCommand(new TooAdminStatsListCommand());
        this.addSubCommand(new TooAdminStatsInspectCommand(plugin));
    }
}
