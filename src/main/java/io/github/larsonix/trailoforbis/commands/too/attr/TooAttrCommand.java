package io.github.larsonix.trailoforbis.commands.too.attr;

import io.github.larsonix.trailoforbis.commands.base.OpenCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Command collection for attribute management.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/too attr view - View attributes (opens UI)</li>
 *   <li>/too attr allocate &lt;type&gt; - Allocate 1 point to an attribute</li>
 *   <li>/too attr unallocate [type] [amount] - Unallocate points</li>
 *   <li>/too attr reset - Full reset with confirmation</li>
 * </ul>
 */
public final class TooAttrCommand extends OpenCommandCollection {

    public TooAttrCommand(TrailOfOrbis plugin) {
        super("attr", "Manage your attributes");
        this.addAliases("attributes", "attribute");

        this.addSubCommand(new TooAttrViewCommand(plugin));
        this.addSubCommand(new TooAttrAllocateCommand(plugin));
        this.addSubCommand(new TooAttrUnallocateCommand(plugin));
        this.addSubCommand(new TooAttrResetCommand(plugin));
    }
}
