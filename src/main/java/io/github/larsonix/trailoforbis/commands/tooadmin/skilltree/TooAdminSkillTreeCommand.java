package io.github.larsonix.trailoforbis.commands.tooadmin.skilltree;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin skill tree command collection.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin skilltree inspect &lt;player&gt;</li>
 *   <li>/tooadmin skilltree reset &lt;player&gt;</li>
 *   <li>/tooadmin skilltree allocate &lt;player&gt; &lt;node&gt;</li>
 *   <li>/tooadmin skilltree allocateall &lt;player&gt;</li>
 *   <li>/tooadmin skilltree deallocate &lt;player&gt; &lt;node&gt;</li>
 * </ul>
 */
public final class TooAdminSkillTreeCommand extends AbstractCommandCollection {

    public TooAdminSkillTreeCommand(TrailOfOrbis plugin) {
        super("skilltree", "Admin skill tree commands");
        this.addAliases("st");

        this.addSubCommand(new TooAdminSkillTreeInspectCommand(plugin));
        this.addSubCommand(new TooAdminSkillTreeResetCommand(plugin));
        this.addSubCommand(new TooAdminSkillTreeAllocateCommand(plugin));
        this.addSubCommand(new TooAdminSkillTreeAllocateAllCommand(plugin));
        this.addSubCommand(new TooAdminSkillTreeDeallocateCommand(plugin));
    }
}
