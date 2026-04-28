package io.github.larsonix.trailoforbis.commands.tooadmin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Admin command to show reset confirmation for a player's attributes.
 *
 * <p>Usage: /tooadmin reset &lt;player&gt;
 */
public final class TooAdminResetCommand extends AbstractPlayerCommand {
    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;

    public TooAdminResetCommand(TrailOfOrbis plugin) {
        super("reset", "Show reset confirmation for player attributes");
        this.plugin = plugin;
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        String targetName = targetArg.get(context);

        AttributeService service = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (service == null) {
            sender.sendMessage(Message.raw("AttributeService not available !").color(MessageColors.ERROR));
            return;
        }

        PlayerDataRepository repo = service.getPlayerDataRepository();
        Optional<PlayerData> targetData = AdminCommandHelper.resolvePlayer(targetName, repo);

        if (targetData.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        PlayerData data = targetData.get();
        int totalAllocated = data.getTotalAllocatedPoints();

        // Show warning with current values
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("This will reset all attributes for ").color(MessageColors.WARNING))
            .insert(Message.raw(targetName).color(MessageColors.WHITE))
            .insert(Message.raw(":").color(MessageColors.WARNING)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  FIRE : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getFire())).color(AttributeType.FIRE.getHexColor()))
            .insert(Message.raw("  WATER : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getWater())).color(AttributeType.WATER.getHexColor()))
            .insert(Message.raw("  LIGHTNING : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getLightning())).color(AttributeType.LIGHTNING.getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  EARTH : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getEarth())).color(AttributeType.EARTH.getHexColor()))
            .insert(Message.raw("  WIND : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getWind())).color(AttributeType.WIND.getHexColor()))
            .insert(Message.raw("  VOID : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getVoidAttr())).color(AttributeType.VOID.getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Total points to refund : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(totalAllocated)).color(MessageColors.WHITE)));

        sender.sendMessage(Message.raw("Type '/tooadmin resetconfirm " + targetName + "' to proceed.")
            .color(MessageColors.WARNING));
    }
}
