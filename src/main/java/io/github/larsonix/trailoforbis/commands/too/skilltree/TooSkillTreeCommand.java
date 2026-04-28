package io.github.larsonix.trailoforbis.commands.too.skilltree;

import io.github.larsonix.trailoforbis.commands.base.OpenCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Command collection for skill tree management.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/too skilltree view - Open skill tree UI/map</li>
 *   <li>/too skilltree allocate &lt;node&gt; - Allocate a node</li>
 *   <li>/too skilltree deallocate &lt;node&gt; - Deallocate a node</li>
 *   <li>/too skilltree respec - Full respec</li>
 *   <li>/too skilltree info &lt;node&gt; - Node info</li>
 *   <li>/too skilltree list - List allocated nodes</li>
 * </ul>
 */
public final class TooSkillTreeCommand extends OpenCommandCollection {

    public TooSkillTreeCommand(TrailOfOrbis plugin) {
        super("skilltree", "Manage your passive skill tree");
        this.addAliases("skills", "tree", "st");

        this.addSubCommand(new TooSkillTreeViewCommand(plugin));
        this.addSubCommand(new TooSkillTreeAllocateCommand(plugin));
        this.addSubCommand(new TooSkillTreeDeallocateCommand(plugin));
        this.addSubCommand(new TooSkillTreeRespecCommand(plugin));
        this.addSubCommand(new TooSkillTreeInfoCommand(plugin));
        this.addSubCommand(new TooSkillTreeListCommand(plugin));
    }
}
