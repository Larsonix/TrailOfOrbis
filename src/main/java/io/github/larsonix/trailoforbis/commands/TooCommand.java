package io.github.larsonix.trailoforbis.commands;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.commands.base.OpenCommandCollection;
import io.github.larsonix.trailoforbis.commands.too.*;
import io.github.larsonix.trailoforbis.commands.too.attr.TooAttrCommand;
import io.github.larsonix.trailoforbis.commands.too.combat.TooCombatCommand;
import io.github.larsonix.trailoforbis.commands.too.realm.TooRealmCommand;
import io.github.larsonix.trailoforbis.commands.too.sanctum.TooSanctumCommand;
import io.github.larsonix.trailoforbis.commands.too.skilltree.TooSkillTreeCommand;

/**
 * Root command collection for Trail of Orbis player commands.
 *
 * <p>This command provides the main entry point for all player-facing RPG commands.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/too stats - View complete stats (UI page)</li>
 *   <li>/too attr - Attribute management collection</li>
 *   <li>/too skilltree - Skill tree management collection</li>
 *   <li>/too sanctum - Sanctum enter/exit collection</li>
 *   <li>/too realm - Realm info/exit collection</li>
 *   <li>/too combat - Combat-related commands (detail mode toggle)</li>
 * </ul>
 */
public final class TooCommand extends OpenCommandCollection {

    public TooCommand(TrailOfOrbis plugin) {
        super("too", "Trail of Orbis RPG commands");
        this.addAliases("trailofoorbis", "orbis");

        // Register player subcommands
        this.addSubCommand(new TooStatsCommand(plugin));
        this.addSubCommand(new TooAttrCommand(plugin));
        this.addSubCommand(new TooSkillTreeCommand(plugin));
        this.addSubCommand(new TooSanctumCommand(plugin));
        this.addSubCommand(new TooRealmCommand(plugin));
        this.addSubCommand(new TooCombatCommand(plugin));
    }
}
