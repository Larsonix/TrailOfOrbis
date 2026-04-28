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
import io.github.larsonix.trailoforbis.commands.tooadmin.clipboard.CopyPasteClipboard;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Copies the held gear's stats to the clipboard.
 *
 * <p>Usage: /tooadmin gear copy
 *
 * <p>Copies all gear properties (level, rarity, quality, prefixes, suffixes,
 * corrupted state, implicit) to the player's clipboard. The instance ID is
 * NOT copied - the target item keeps its own instance ID.
 *
 * <p>Use {@code /tooadmin gear paste} to apply the copied stats to another item.
 *
 * @see TooAdminGearPasteCommand
 * @see CopyPasteClipboard
 */
public class TooAdminGearCopyCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminGearCopyCommand(TrailOfOrbis plugin) {
        super("copy", "Copy held gear's stats to clipboard");
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
        if (heldItem == null || heldItem.isEmpty()) {
            sender.sendMessage(Message.raw("You must hold an item !").color(MessageColors.ERROR));
            return;
        }

        Optional<GearData> gearDataOpt = GearUtils.readGearData(heldItem);
        if (gearDataOpt.isEmpty()) {
            sender.sendMessage(Message.raw("This item is not RPG gear.").color(MessageColors.ERROR));
            return;
        }

        GearData gearData = gearDataOpt.get();

        // Copy to clipboard
        CopyPasteClipboard.getInstance().copyGear(sender.getUuid(), gearData);

        // Success message with details
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Copied gear to clipboard : ").color(MessageColors.SUCCESS))
            .insert(Message.raw("Lv" + gearData.level() + " ").color(MessageColors.WHITE))
            .insert(Message.raw(gearData.rarity().name()).color(gearData.rarity().getHexColor()))
            .insert(Message.raw(" (Q" + gearData.quality()).color(MessageColors.GRAY))
            .insert(Message.raw(", " + gearData.modifierCount() + " mods").color(MessageColors.GRAY))
            .insert(Message.raw(gearData.corrupted() ? ", corrupted" : "").color(MessageColors.ERROR))
            .insert(Message.raw(")").color(MessageColors.GRAY)));
    }

}
