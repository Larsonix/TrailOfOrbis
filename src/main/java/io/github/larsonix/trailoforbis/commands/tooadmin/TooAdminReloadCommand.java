package io.github.larsonix.trailoforbis.commands.tooadmin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Admin command to reload plugin configuration.
 *
 * <p>Usage: /tooadmin reload
 */
public class TooAdminReloadCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;

    public TooAdminReloadCommand(TrailOfOrbis plugin) {
        super("reload", "Reload plugin configuration");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        ConfigService configService = ServiceRegistry.get(ConfigService.class).orElse(null);
        if (configService == null) {
            sender.sendMessage(Message.raw("ConfigService not available !").color(MessageColors.ERROR));
            return;
        }

        try {
            boolean success = configService.reloadConfigs();
            if (success) {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                    .insert(Message.raw("Configuration reloaded successfully.").color(MessageColors.SUCCESS)));

                plugin.getLogger().atInfo().log("[TooAdmin] %s reloaded configuration", sender.getUsername());
            } else {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                    .insert(Message.raw("Configuration reload failed! Check logs.").color(MessageColors.ERROR)));
            }
        } catch (Exception e) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Error reloading config : " + e.getMessage()).color(MessageColors.ERROR)));
            plugin.getLogger().atSevere().withCause(e).log("[TooAdmin] Config reload failed");
        }
    }
}
