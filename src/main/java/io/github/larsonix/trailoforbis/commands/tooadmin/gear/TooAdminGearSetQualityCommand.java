package io.github.larsonix.trailoforbis.commands.tooadmin.gear;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.commands.tooadmin.HeldItemHelper;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Sets the held gear's quality.
 *
 * <p>Usage: /tooadmin gear setquality &lt;quality&gt;
 */
public final class TooAdminGearSetQualityCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<Integer> qualityArg;

    public TooAdminGearSetQualityCommand(TrailOfOrbis plugin) {
        super("setquality", "Set held gear's quality");
        this.addAliases("quality");
        this.plugin = plugin;

        qualityArg = this.withRequiredArg("quality", "Quality (0-100)", ArgTypes.INTEGER);
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

        Integer quality = qualityArg.get(context);
        if (quality < 0 || quality > 100) {
            sender.sendMessage(Message.raw("Quality must be between 0 and 100 !").color(MessageColors.ERROR));
            return;
        }

        GearData oldData = gearDataOpt.get();
        GearData newData = oldData.withQuality(quality);
        ItemStack newItem = GearUtils.setGearData(heldItem, newData);

        HeldItemHelper.setHeldItem(sender, store, ref, newItem);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Set gear quality to ").color(MessageColors.SUCCESS))
            .insert(Message.raw(quality + "%").color(MessageColors.WHITE)));
    }

}
