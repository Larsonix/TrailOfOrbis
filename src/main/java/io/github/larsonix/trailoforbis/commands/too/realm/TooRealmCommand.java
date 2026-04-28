package io.github.larsonix.trailoforbis.commands.too.realm;

import io.github.larsonix.trailoforbis.commands.base.OpenCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Command collection for player-facing realm commands.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/too realm exit - Exit the current realm</li>
 *   <li>/too realm info - Show current realm info</li>
 * </ul>
 */
public final class TooRealmCommand extends OpenCommandCollection {

    public TooRealmCommand(TrailOfOrbis plugin) {
        super("realm", "Realm commands");
        this.addAliases("realms");

        this.addSubCommand(new TooRealmExitCommand(plugin));
        this.addSubCommand(new TooRealmInfoCommand(plugin));
    }
}
