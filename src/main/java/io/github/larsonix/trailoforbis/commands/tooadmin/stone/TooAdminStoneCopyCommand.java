package io.github.larsonix.trailoforbis.commands.tooadmin.stone;

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
import io.github.larsonix.trailoforbis.stones.StoneItemData;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Copies the held stone's type to the clipboard.
 *
 * <p>Usage: /tooadmin stone copy
 *
 * <p>Copies the stone type to the player's clipboard.
 * The instance ID is NOT copied - the target item keeps its own instance ID.
 *
 * <p>Use {@code /tooadmin stone paste} to change another stone to this type.
 *
 * @see TooAdminStonePasteCommand
 * @see CopyPasteClipboard
 */
public class TooAdminStoneCopyCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminStoneCopyCommand(TrailOfOrbis plugin) {
        super("copy", "Copy held stone's type to clipboard");
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

        Optional<StoneItemData> stoneDataOpt = StoneUtils.readStoneData(heldItem);
        if (stoneDataOpt.isEmpty()) {
            sender.sendMessage(Message.raw("This item is not a stone.").color(MessageColors.ERROR));
            return;
        }

        StoneItemData stoneData = stoneDataOpt.get();
        StoneType stoneType = stoneData.stoneType();

        // Copy to clipboard
        CopyPasteClipboard.getInstance().copyStone(sender.getUuid(), stoneData);

        // Success message with details
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Copied stone to clipboard : ").color(MessageColors.SUCCESS))
            .insert(Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()))
            .insert(Message.raw(" (" + stoneType.getRarity().name() + ")").color(MessageColors.GRAY)));
    }

}
