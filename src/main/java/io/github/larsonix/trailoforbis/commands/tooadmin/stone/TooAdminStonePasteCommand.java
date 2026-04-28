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
 * Pastes clipboard stone type onto the held stone.
 *
 * <p>Usage: /tooadmin stone paste
 *
 * <p>Changes the held stone to the type copied to clipboard.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Must have copied stone data in clipboard (use /tooadmin stone copy)</li>
 *   <li>Must be holding an existing stone item</li>
 * </ul>
 *
 * @see TooAdminStoneCopyCommand
 * @see CopyPasteClipboard
 */
public class TooAdminStonePasteCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminStonePasteCommand(TrailOfOrbis plugin) {
        super("paste", "Paste clipboard stone type onto held stone");
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
        // Check clipboard first
        Optional<StoneItemData> clipboardOpt = CopyPasteClipboard.getInstance().getCopiedStone(sender.getUuid());
        if (clipboardOpt.isEmpty()) {
            sender.sendMessage(Message.raw("Clipboard is empty! Use /tooadmin stone copy first.").color(MessageColors.ERROR));
            return;
        }

        ItemStack heldItem = HeldItemHelper.getHeldItem(sender, store, ref);
        if (heldItem == null || heldItem.isEmpty()) {
            sender.sendMessage(Message.raw("You must hold an item !").color(MessageColors.ERROR));
            return;
        }

        // Check if held item is a stone
        Optional<StoneItemData> existingOpt = StoneUtils.readStoneData(heldItem);
        if (existingOpt.isEmpty()) {
            sender.sendMessage(Message.raw("This item is not a stone. Paste only works on existing stones.").color(MessageColors.ERROR));
            return;
        }

        StoneItemData existing = existingOpt.get();
        StoneItemData clipboard = clipboardOpt.get();
        StoneType newType = clipboard.stoneType();

        // Build new StoneItemData with the clipboard's type
        StoneItemData newData = new StoneItemData(newType);

        // Write to item
        ItemStack newItem = StoneUtils.writeStoneData(heldItem, newData);

        // Update held item
        HeldItemHelper.setHeldItem(sender, store, ref, newItem);

        // Success message
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Pasted stone type : ").color(MessageColors.SUCCESS))
            .insert(Message.raw(newType.getDisplayName()).color(newType.getHexColor()))
            .insert(Message.raw(" (" + newType.getRarity().name() + ")").color(MessageColors.GRAY)));
    }

}
