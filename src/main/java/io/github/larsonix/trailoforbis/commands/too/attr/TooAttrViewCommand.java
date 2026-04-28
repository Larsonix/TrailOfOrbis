package io.github.larsonix.trailoforbis.commands.too.attr;

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
 * Opens the attribute allocation UI page.
 *
 * <p>Usage: /too attr view
 */
public final class TooAttrViewCommand extends OpenPlayerCommand {
    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;

    public TooAttrViewCommand(TrailOfOrbis plugin) {
        super("view", "View your attributes");
        this.addAliases("open", "show");
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
        UIService uiService = ServiceRegistry.require(UIService.class);
        uiService.openAttributePage(player, store, ref);
    }
}
