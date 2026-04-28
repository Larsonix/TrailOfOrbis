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
 * Sets the held gear's level.
 *
 * <p>Usage: /tooadmin gear setlevel &lt;level&gt;
 */
public final class TooAdminGearSetLevelCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<Integer> levelArg;

    public TooAdminGearSetLevelCommand(TrailOfOrbis plugin) {
        super("setlevel", "Set held gear's level");
        this.addAliases("level");
        this.plugin = plugin;

        levelArg = this.withRequiredArg("level", "Gear level (1-" + GearData.MAX_LEVEL + ")", ArgTypes.INTEGER);
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

        Integer level = levelArg.get(context);
        if (level < 1 || level > GearData.MAX_LEVEL) {
            sender.sendMessage(Message.raw("Level must be between 1 and " + GearData.MAX_LEVEL + " !").color(MessageColors.ERROR));
            return;
        }

        GearData oldData = gearDataOpt.get();
        GearData newData = oldData.withLevel(level);
        ItemStack newItem = GearUtils.setGearData(heldItem, newData);

        HeldItemHelper.setHeldItem(sender, store, ref, newItem);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Set gear level to ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(level)).color(MessageColors.WHITE)));
    }

}
