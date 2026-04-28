package io.github.larsonix.trailoforbis.commands.tooadmin.entity;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Admin entity command collection for dynamic entity discovery.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/tooadmin entity stats - Show discovered entity statistics</li>
 *   <li>/tooadmin entity rescan - Rescan NPC registry for roles</li>
 *   <li>/tooadmin entity classify &lt;roleName&gt; - Show classification details for a role</li>
 * </ul>
 */
public final class TooAdminEntityCommand extends AbstractCommandCollection {

    public TooAdminEntityCommand(TrailOfOrbis plugin) {
        super("entity", "Admin entity discovery commands");

        this.addSubCommand(new TooAdminEntityStatsCommand(plugin));
        this.addSubCommand(new TooAdminEntityRescanCommand(plugin));
        this.addSubCommand(new TooAdminEntityClassifyCommand(plugin));
    }
}
