package io.github.larsonix.trailoforbis.commands.tooadmin.gear;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.commands.tooadmin.HeldItemHelper;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Shows information about the held item's gear data.
 *
 * <p>Usage: /tooadmin gear info
 */
public class TooAdminGearInfoCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminGearInfoCommand(TrailOfOrbis plugin) {
        super("info", "Show held item's gear data");
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
        ItemStack heldItem = HeldItemHelper.getHeldItem(sender, store, ref);
        if (heldItem == null) {
            sender.sendMessage(Message.raw("You must hold an item !").color(MessageColors.ERROR));
            return;
        }

        Optional<GearData> gearDataOpt = GearUtils.readGearData(heldItem);
        if (gearDataOpt.isEmpty()) {
            sender.sendMessage(Message.raw("This item is not RPG gear.").color(MessageColors.ERROR));
            return;
        }

        GearData gearData = gearDataOpt.get();

        // Get tooltip formatter
        GearService gearService = ServiceRegistry.get(GearService.class).orElse(null);
        if (gearService != null) {
            Message tooltip = gearService.buildRichTooltip(gearData, sender.getUuid());
            sender.sendMessage(tooltip);
        } else {
            // Fallback to basic info
            sender.sendMessage(Message.raw("Level : " + gearData.level()).color(MessageColors.INFO));
            sender.sendMessage(Message.raw("Rarity : " + gearData.rarity().getHytaleQualityId()).color(MessageColors.INFO));
            sender.sendMessage(Message.raw("Quality : " + gearData.quality() + "%").color(MessageColors.INFO));
            sender.sendMessage(Message.raw("Prefixes : " + gearData.prefixes().size()).color(MessageColors.INFO));
            sender.sendMessage(Message.raw("Suffixes : " + gearData.suffixes().size()).color(MessageColors.INFO));
        }
    }

}
