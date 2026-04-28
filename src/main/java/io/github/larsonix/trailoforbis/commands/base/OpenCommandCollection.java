package io.github.larsonix.trailoforbis.commands.base;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Base class for command collections that should be usable by ALL players.
 *
 * <p>Hytale's command system auto-generates permission nodes for every command
 * and only OP'd players have these permissions by default. By overriding
 * {@code canGeneratePermission()} to return {@code false}, we disable the
 * auto-generated permission check.
 *
 * <p>Admin command collections should NOT extend this class — they should use
 * {@code this.requirePermission("too.admin")} on {@link AbstractCommandCollection}.
 *
 * @see OpenPlayerCommand
 */
public abstract class OpenCommandCollection extends AbstractCommandCollection {

    protected OpenCommandCollection(String name, String description) {
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
