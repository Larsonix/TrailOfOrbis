package io.github.larsonix.trailoforbis.commands.base;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;

/**
 * Base class for player-facing commands that should be usable by ALL players.
 *
 * <p>Hytale's command system auto-generates permission nodes for every command
 * (e.g. "command.too.stats") and only OP'd players have these permissions by default.
 * By overriding {@code canGeneratePermission()} to return {@code false}, we disable
 * the auto-generated permission check, making the command accessible to everyone.
 *
 * <p>Admin commands should NOT extend this class — they should use
 * {@code this.requirePermission("too.admin")} instead.
 *
 * @see OpenCommandCollection
 */
public abstract class OpenPlayerCommand extends AbstractPlayerCommand {

    protected OpenPlayerCommand(String name, String description) {
        super(name, description);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }
}
