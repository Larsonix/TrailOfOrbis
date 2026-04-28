package io.github.larsonix.trailoforbis.commands.tooadmin.sanctum;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin sanctum command collection.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin sanctum editlayout &lt;nodeId&gt; &lt;x&gt; &lt;y&gt;</li>
 *   <li>/tooadmin sanctum exportlayout</li>
 *   <li>/tooadmin sanctum generatetemplate</li>
 * </ul>
 */
public final class TooAdminSanctumCommand extends AbstractCommandCollection {

    public TooAdminSanctumCommand(TrailOfOrbis plugin) {
        super("sanctum", "Admin sanctum commands");

        this.addSubCommand(new TooAdminSanctumEditLayoutCommand(plugin));
        this.addSubCommand(new TooAdminSanctumExportLayoutCommand(plugin));
        this.addSubCommand(new TooAdminSanctumGenerateTemplateCommand(plugin));
        this.addSubCommand(new TooAdminSanctumTestLineCommand());
        this.addSubCommand(new DebugShapeResearchCommand());
    }
}
