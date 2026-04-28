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
import io.github.larsonix.trailoforbis.stones.StoneItemData;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Shows information about the held stone item.
 *
 * <p>Usage: /tooadmin stone info
 *
 * <p>Displays all stone properties including:
 * <ul>
 *   <li>Stone type and display name</li>
 *   <li>Rarity</li>
 *   <li>Target type (gear only, map only, or both)</li>
 *   <li>Description</li>
 *   <li>Whether it works on corrupted items</li>
 * </ul>
 */
public class TooAdminStoneInfoCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminStoneInfoCommand(TrailOfOrbis plugin) {
        super("info", "Show held stone's data");
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

        // Header
        sender.sendMessage(Message.raw("=== Stone Info ===").color(MessageColors.GOLD));

        // Stone type
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Type : ").color(MessageColors.GRAY))
            .insert(Message.raw(stoneType.name()).color(MessageColors.WHITE)));

        // Display name
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Name : ").color(MessageColors.GRAY))
            .insert(Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor())));

        // Rarity
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Rarity : ").color(MessageColors.GRAY))
            .insert(Message.raw(stoneType.getRarity().name()).color(stoneType.getRarity().getHexColor())));

        // Target type
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Target : ").color(MessageColors.GRAY))
            .insert(Message.raw(stoneType.getTargetType().getDisplayName()).color(MessageColors.WHITE)));

        // Description
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Effect : ").color(MessageColors.GRAY))
            .insert(Message.raw(stoneType.getDescription()).color(MessageColors.WHITE)));

        // Works on corrupted
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Works on corrupted : ").color(MessageColors.GRAY))
            .insert(Message.raw(stoneType.worksOnCorrupted() ? "Yes" : "No")
                .color(stoneType.worksOnCorrupted() ? MessageColors.SUCCESS : MessageColors.ERROR)));

        // Native item ID
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Native ID : ").color(MessageColors.GRAY))
            .insert(Message.raw(stoneData.getNativeItemId()).color(MessageColors.GRAY)));
    }

}
