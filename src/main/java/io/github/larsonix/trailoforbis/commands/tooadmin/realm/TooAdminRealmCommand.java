package io.github.larsonix.trailoforbis.commands.tooadmin.realm;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin realm command collection.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin realm open &lt;biome&gt; &lt;size&gt; &lt;level&gt;</li>
 *   <li>/tooadmin realm list</li>
 *   <li>/tooadmin realm info [realmId]</li>
 *   <li>/tooadmin realm exit [player]</li>
 *   <li>/tooadmin realm close &lt;realmId&gt;</li>
 * </ul>
 */
public final class TooAdminRealmCommand extends AbstractCommandCollection {

    public TooAdminRealmCommand(TrailOfOrbis plugin) {
        super("realm", "Admin realm commands");
        this.addAliases("realms");

        this.addSubCommand(new TooAdminRealmOpenCommand(plugin));
        this.addSubCommand(new TooAdminRealmListCommand(plugin));
        this.addSubCommand(new TooAdminRealmInfoCommand(plugin));
        this.addSubCommand(new TooAdminRealmExitCommand(plugin));
        this.addSubCommand(new TooAdminRealmCloseCommand(plugin));
    }
}
