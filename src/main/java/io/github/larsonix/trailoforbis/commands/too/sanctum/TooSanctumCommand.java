package io.github.larsonix.trailoforbis.commands.too.sanctum;

import io.github.larsonix.trailoforbis.commands.base.OpenCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Command collection for Skill Sanctum management.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/too sanctum enter - Enter the Skill Sanctum</li>
 *   <li>/too sanctum exit - Exit the Skill Sanctum</li>
 * </ul>
 */
public final class TooSanctumCommand extends OpenCommandCollection {

    public TooSanctumCommand(TrailOfOrbis plugin) {
        super("sanctum", "Enter or exit the Skill Sanctum");

        this.addSubCommand(new TooSanctumEnterCommand(plugin));
        this.addSubCommand(new TooSanctumExitCommand(plugin));
    }
}
