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
 * Shortcut command for /stats that delegates to /too stats.
 *
 * <p>Usage: /stats
 */
public final class StatsShortcutCommand extends OpenPlayerCommand {

    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;

    public StatsShortcutCommand(TrailOfOrbis plugin) {
        super("stats", "View your complete RPG stats (shortcut for /too stats)");
        this.addAliases("stat");
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
        // Open the Stats UI page directly
        ServiceRegistry.get(UIService.class).ifPresent(ui -> ui.openStatsPage(player, store, ref));
    }
}
