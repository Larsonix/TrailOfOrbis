package io.github.larsonix.trailoforbis.commands.too.combat;

import io.github.larsonix.trailoforbis.commands.base.OpenCommandCollection;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

/**
 * Command collection for combat-related player commands.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/too combat detail [on|off] - Toggle detailed damage breakdown in chat</li>
 * </ul>
 */
public final class TooCombatCommand extends OpenCommandCollection {

    public TooCombatCommand(TrailOfOrbis plugin) {
        super("combat", "Combat-related commands");

        this.addSubCommand(new TooCombatDetailCommand(plugin));
    }
}
