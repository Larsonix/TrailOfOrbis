package io.github.larsonix.trailoforbis.commands.shortcuts;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.UIService;

import javax.annotation.Nonnull;

/**
 * Shortcut command for /attr that opens the Attribute UI directly.
 *
 * <p>For subcommands (allocate, unallocate, reset), use /too attr.
 *
 * <p>Usage: /attr, /attributes, /attribute
 */
public final class AttrShortcutCommand extends OpenPlayerCommand {

    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;

    public AttrShortcutCommand(TrailOfOrbis plugin) {
        super("attr", "Open attribute allocation (use /too attr for subcommands)");
        this.addAliases("attributes", "attribute");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        ServiceRegistry.get(UIService.class).ifPresent(ui -> ui.openAttributePage(player, store, ref));
    }
}
