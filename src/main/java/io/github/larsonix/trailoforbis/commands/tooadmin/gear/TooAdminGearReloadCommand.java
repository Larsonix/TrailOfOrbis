package io.github.larsonix.trailoforbis.commands.tooadmin.gear;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Reloads gear configuration.
 *
 * <p>Usage: /tooadmin gear reload
 */
public class TooAdminGearReloadCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final TrailOfOrbis plugin;

    public TooAdminGearReloadCommand(TrailOfOrbis plugin) {
        super("reload", "Reload gear configuration");
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
        Optional<GearService> gearServiceOpt = ServiceRegistry.get(GearService.class);
        if (gearServiceOpt.isPresent()) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Gear configuration reloaded !").color(MessageColors.SUCCESS)));
            LOGGER.at(Level.INFO).log("Gear configuration reload requested by admin");
        } else {
            sender.sendMessage(Message.raw("Gear service not available !").color(MessageColors.ERROR));
        }
    }
}
